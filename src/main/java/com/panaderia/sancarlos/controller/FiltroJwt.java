package com.panaderia.sancarlos.controller;

import com.panaderia.sancarlos.model.SesionAutenticada;
import com.panaderia.sancarlos.manager.TokenRevocadoManager;
import com.panaderia.sancarlos.utils.ConexionBD;
import com.panaderia.sancarlos.utils.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

/** Valida el JWT, la cuenta y el rol activo, y aplica permisos por ruta. */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class FiltroJwt implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) {
        final String metodo = ctx.getMethod().toUpperCase(Locale.ROOT);
        String ruta = ctx.getUriInfo().getPath(false);
        ruta = ruta == null ? "" : ruta.replaceAll("^/+|/+$", "");

        // El login es publico. OPTIONS corresponde al preflight de CORS.
        if ("OPTIONS".equals(metodo)
                || ("POST".equals(metodo) && "usuarios/login".equals(ruta))) {
            return;
        }

        String cabecera = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (cabecera == null
                || !cabecera.regionMatches(true, 0, "Bearer ", 0, 7)) {
            ctx.abortWith(error(401, "Falta token Bearer"));
            return;
        }

        String token = cabecera.substring(7).trim();
        if (token.isEmpty()) {
            ctx.abortWith(error(401, "Token vacio"));
            return;
        }

        final int idUsuario;
        try {
            idUsuario = JwtUtil.validarToken(token);
        } catch (ExpiredJwtException e) {
            ctx.abortWith(error(401, "Sesion expirada; inicie sesion nuevamente"));
            return;
        } catch (JwtException | IllegalArgumentException e) {
            ctx.abortWith(error(401, "Token invalido"));
            return;
        } catch (IllegalStateException e) {
            ctx.abortWith(error(500, "Configuracion JWT incompleta"));
            return;
        }

        // Importante: un token revocado no puede usar ninguna ruta, incluido logout.
        try {
            if (new TokenRevocadoManager().estaRevocado(token)) {
                ctx.abortWith(error(401, "Sesion cerrada: token revocado"));
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Fallar cerrado: si Oracle no responde no permitir el acceso.
            ctx.abortWith(error(500, "No fue posible verificar la revocacion JWT"));
            return;
        }

        final SesionAutenticada sesion;
        try {
            sesion = buscarSesion(idUsuario);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.abortWith(error(500, "Error al validar sesion con Oracle"));
            return;
        }
        if (sesion == null) {
            ctx.abortWith(error(401, "Usuario, rol o sucursal inactiva"));
            return;
        }

        if (!permitido(metodo, ruta, sesion.getRol())) {
            ctx.abortWith(error(403, "No tiene permiso para esta operacion"));
            return;
        }

        // Mantener propiedades por compatibilidad con codigo existente.
        ctx.setProperty("idUsuarioAutenticado", sesion.getIdUsuario());
        ctx.setProperty("idSucursalAutenticada", sesion.getIdSucursal());
        ctx.setProperty("rolAutenticado", sesion.getRol());

        final SecurityContext anterior = ctx.getSecurityContext();
        ctx.setSecurityContext(new SecurityContext() {
            @Override
            public java.security.Principal getUserPrincipal() {
                return sesion;
            }
            @Override
            public boolean isUserInRole(String rol) {
                return rol != null && sesion.getRol().equalsIgnoreCase(rol);
            }
            @Override
            public boolean isSecure() {
                return anterior != null && anterior.isSecure();
            }
            @Override
            public String getAuthenticationScheme() {
                return "Bearer";
            }
        });
    }

    private boolean permitido(String metodo, String ruta, String rol) {
        // Logout para cualquier cuenta activa, sin importar su rol.
        if ("POST".equals(metodo) && "usuarios/logout".equals(ruta)) {
            return true;
        }

        if ("ADMINISTRADOR".equals(rol)) {
            return true;
        }

        // Catalogos publicos para cualquier cuenta autenticada.
        if ("GET".equals(metodo) && (
                "usuarios/roles".equals(ruta)
                || "usuarios/sucursales".equals(ruta)
                || "productos".equals(ruta)
                || ruta.startsWith("productos/"))) {
            return true;
        }

        if ("PRODUCCION".equals(rol)) {
            if (ruta.equals("recetas") || ruta.startsWith("recetas/")) return true;
            if (ruta.equals("inventario") || ruta.startsWith("inventario/")) return true;
            if (ruta.equals("producciones") || ruta.startsWith("producciones/")) return true;
            // PRODUCCION: solo las cuatro operaciones existentes de despachos.
            if ("GET".equals(metodo)
                    && ("despachos".equals(ruta)
                    || ruta.matches("despachos/[0-9]+/detalle"))) return true;
            if ("POST".equals(metodo) && "despachos".equals(ruta)) return true;
            if ("PUT".equals(metodo)
                    && ruta.matches("despachos/[0-9]+/recibir")) return true;
            // PRODUCCION consulta pedidos; no los edita.
            return "GET".equals(metodo) && (
                    "pedidos".equals(ruta)
                    || ruta.matches("pedidos/[0-9]+/detalle"));
        }

        if ("SUCURSAL".equals(rol)) {
            if ("GET".equals(metodo)) {
                return "pedidos".equals(ruta)
                        || ruta.matches("pedidos/[0-9]+/detalle")
                        // Las consultas de despachos se filtran por sucursal en Manager.
                        || "despachos".equals(ruta)
                        || ruta.matches("despachos/[0-9]+/detalle");
            }
            if ("POST".equals(metodo)) {
                return "pedidos".equals(ruta)
                        || ruta.matches("pedidos/[0-9]+/productos");
            }
            return "PUT".equals(metodo)
                    && ruta.matches("pedidos/[0-9]+/enviar");
        }

        // Por defecto, denegar.
        return false;
    }

    private SesionAutenticada buscarSesion(int idUsuario) throws Exception {
        String sql = "SELECT U.USUARIO, R.NOMBRE AS ROL, U.ID_SUCURSAL "
                + "FROM USUARIO U INNER JOIN ROL R ON U.ID_ROL = R.ID_ROL "
                + "LEFT JOIN SUCURSAL S ON U.ID_SUCURSAL = S.ID_SUCURSAL "
                + "WHERE U.ID_USUARIO = ? AND U.ESTADO = 1 AND R.ESTADO = 1 "
                + "AND (U.ID_SUCURSAL IS NULL OR S.ESTADO = 1) "
                + "AND (R.NOMBRE <> 'SUCURSAL' OR U.ID_SUCURSAL IS NOT NULL)";

        try (Connection cn = ConexionBD.obtenerConexion();
                PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int idSucursal = rs.getInt("ID_SUCURSAL");
                Integer sucursal = rs.wasNull() ? null : idSucursal;
                String rol = rs.getString("ROL");
                if (rol == null) return null;
                return new SesionAutenticada(idUsuario,
                        rs.getString("USUARIO"),
                        rol.trim().toUpperCase(Locale.ROOT), sucursal);
            }
        }
    }

    private static Response error(int status, String mensaje) {
        Map<String, Object> json = new HashMap<>();
        json.put("exito", false);
        json.put("mensaje", mensaje);
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(json)
                .build();
    }
}
