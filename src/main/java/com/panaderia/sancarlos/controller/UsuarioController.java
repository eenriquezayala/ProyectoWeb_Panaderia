package com.panaderia.sancarlos.controller;

import com.panaderia.sancarlos.manager.UsuarioManager;
import com.panaderia.sancarlos.manager.TokenRevocadoManager;
import com.panaderia.sancarlos.model.SesionAutenticada;
import com.panaderia.sancarlos.model.CrearUsuarioRequest;
import com.panaderia.sancarlos.model.LoginRequest;
import com.panaderia.sancarlos.model.LoginJwtRespuesta;
import com.panaderia.sancarlos.model.RespuestaOperacion;
import com.panaderia.sancarlos.model.Usuario;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("usuarios")
public class UsuarioController {

    private final UsuarioManager manager
            = new UsuarioManager();

    // =========================================
    // GET /usuarios
    // =========================================
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultarUsuarios() {

        try {

            List<Usuario> usuarios
                    = manager.consultarUsuarios();

            return Response
                    .ok(usuarios)
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            return Response
                    .status(
                            Response.Status.INTERNAL_SERVER_ERROR
                    )
                    .entity(
                            "{\"mensaje\":"
                            + "\"Error al consultar usuarios\"}"
                    )
                    .build();
        }
    }

    // =========================================
    // POST /usuarios
    // =========================================
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response crearUsuario(
            CrearUsuarioRequest request) {

        try {

            RespuestaOperacion respuesta
                    = manager.crearUsuario(request);

            if (respuesta.isExito()) {

                return Response
                        .status(
                                Response.Status.CREATED
                        )
                        .entity(respuesta)
                        .build();
            }

            return Response
                    .status(
                            Response.Status.BAD_REQUEST
                    )
                    .entity(respuesta)
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            RespuestaOperacion respuesta
                    = new RespuestaOperacion(
                            false,
                            0,
                            "Error interno al crear usuario"
                    );

            return Response
                    .status(
                            Response.Status.INTERNAL_SERVER_ERROR
                    )
                    .entity(respuesta)
                    .build();
        }
    }

    // =========================================
    // POST /usuarios/login
    // =========================================
    @POST
    @Path("login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(
            LoginRequest request) {

        try {

            LoginJwtRespuesta respuesta
                    = manager.login(request);

            if (!respuesta.isExito()) {

                return Response
                        .status(
                                Response.Status.UNAUTHORIZED
                        )
                        .entity(respuesta)
                        .build();
            }

            return Response
                    .ok(respuesta)
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            LoginJwtRespuesta respuesta
                    = new LoginJwtRespuesta(
                            false,
                            "Error interno al iniciar sesión",
                            null
                    );

            return Response
                    .status(
                            Response.Status.INTERNAL_SERVER_ERROR
                    )
                    .entity(respuesta)
                    .build();
        }
    }
    // =========================================
// GET /usuarios/roles
// =========================================

    @GET
    @Path("roles")
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultarRoles() {

        try {

            return Response
                    .ok(manager.consultarRoles())
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            return Response
                    .status(
                            Response.Status.INTERNAL_SERVER_ERROR
                    )
                    .entity(
                            "{\"mensaje\":\"Error al consultar roles\"}"
                    )
                    .build();
        }
    }

// =========================================
// GET /usuarios/sucursales
// =========================================
    @GET
    @Path("sucursales")
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultarSucursales() {

        try {

            return Response
                    .ok(manager.consultarSucursales())
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            return Response
                    .status(
                            Response.Status.INTERNAL_SERVER_ERROR
                    )
                    .entity(
                            "{\"mensaje\":\"Error al consultar sucursales\"}"
                    )
                    .build();
        }
    }
    // =========================================
    // POST /usuarios/logout (exige Bearer JWT valido)
    // =========================================
    @POST
    @Path("logout")
    @Produces(MediaType.APPLICATION_JSON)
    public Response logout(@Context HttpHeaders headers,
                           @Context SecurityContext seguridad) {
        try {
            if (seguridad == null
                    || !(seguridad.getUserPrincipal() instanceof SesionAutenticada)) {
                return Response.status(401)
                        .entity(new RespuestaOperacion(false, 0,
                                "Sesion no disponible"))
                        .build();
            }
            SesionAutenticada sesion =
                    (SesionAutenticada) seguridad.getUserPrincipal();

            String autorizacion = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
            if (autorizacion == null
                    || !autorizacion.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return Response.status(401)
                        .entity(new RespuestaOperacion(false, 0,
                                "Falta token Bearer"))
                        .build();
            }
            String token = autorizacion.substring(7).trim();
            new TokenRevocadoManager().revocarToken(token, sesion.getIdUsuario());

            return Response.ok(new RespuestaOperacion(
                    true, sesion.getIdUsuario(),
                    "Sesion cerrada correctamente")).build();

        } catch (SecurityException | IllegalArgumentException e) {
            return Response.status(401)
                    .entity(new RespuestaOperacion(false, 0, e.getMessage()))
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500)
                    .entity(new RespuestaOperacion(false, 0,
                            "Error al cerrar sesion"))
                    .build();
        }
    }
}
