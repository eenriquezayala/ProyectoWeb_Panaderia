package com.panaderia.sancarlos.manager;

import com.panaderia.sancarlos.model.CrearUsuarioRequest;
import com.panaderia.sancarlos.model.LoginRequest;
import com.panaderia.sancarlos.model.LoginJwtRespuesta;
import com.panaderia.sancarlos.utils.JwtUtil;
import com.panaderia.sancarlos.model.RespuestaOperacion;
import com.panaderia.sancarlos.model.Usuario;
import com.panaderia.sancarlos.utils.ConexionBD;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Types;

import java.util.ArrayList;
import java.util.List;

import oracle.jdbc.OracleTypes;

import org.mindrot.jbcrypt.BCrypt;

public class UsuarioManager {

    // =========================================
    // CONSULTAR USUARIOS
    // =========================================
    public List<Usuario> consultarUsuarios()
            throws Exception {

        List<Usuario> usuarios
                = new ArrayList<>();

        String sql
                = "{ call PKG_USUARIO.CONSULTAR_USUARIOS(?) }";

        try (
                Connection conexion
                = ConexionBD.obtenerConexion(); CallableStatement cs
                = conexion.prepareCall(sql)) {

            cs.registerOutParameter(
                    1,
                    OracleTypes.CURSOR
            );

            cs.execute();

            try (
                    ResultSet rs
                    = (ResultSet) cs.getObject(1)) {

                while (rs.next()) {

                    Usuario usuario
                            = new Usuario();

                    usuario.setIdUsuario(
                            rs.getInt("ID_USUARIO")
                    );

                    usuario.setUsuario(
                            rs.getString("USUARIO")
                    );

                    usuario.setNombre(
                            rs.getString("NOMBRE")
                    );

                    usuario.setRol(
                            rs.getString("ROL")
                    );

                    usuario.setSucursal(
                            rs.getString("SUCURSAL")
                    );

                    usuario.setEstado(
                            rs.getInt("ESTADO")
                    );

                    usuarios.add(usuario);
                }
            }
        }

        return usuarios;
    }

    // =========================================
    // CREAR USUARIO
    // =========================================
    public RespuestaOperacion crearUsuario(
            CrearUsuarioRequest request)
            throws Exception {

        String sql
                = "{ call PKG_USUARIO.CREAR_USUARIO"
                + "(?, ?, ?, ?, ?, ?, ?) }";

        Connection conexion = null;
        CallableStatement cs = null;

        try {

            conexion
                    = ConexionBD.obtenerConexion();

            conexion.setAutoCommit(false);

            // Convertir contraseña en hash BCrypt
            String hash
                    = BCrypt.hashpw(
                            request.getContrasena(),
                            BCrypt.gensalt(12)
                    );

            cs = conexion.prepareCall(sql);

            cs.setString(
                    1,
                    request.getUsuario()
            );

            cs.setString(
                    2,
                    hash
            );

            cs.setString(
                    3,
                    request.getNombre()
            );

            cs.setInt(
                    4,
                    request.getIdRol()
            );

            if (request.getIdSucursal() == null) {

                cs.setNull(
                        5,
                        Types.NUMERIC
                );

            } else {

                cs.setInt(
                        5,
                        request.getIdSucursal()
                );
            }

            cs.registerOutParameter(
                    6,
                    Types.NUMERIC
            );

            cs.registerOutParameter(
                    7,
                    Types.VARCHAR
            );

            cs.execute();

            int idSalida
                    = cs.getInt(6);

            String mensaje
                    = cs.getString(7);

            if (idSalida > 0) {

                conexion.commit();

                return new RespuestaOperacion(
                        true,
                        idSalida,
                        mensaje
                );

            } else {

                conexion.rollback();

                return new RespuestaOperacion(
                        false,
                        0,
                        mensaje
                );
            }

        } catch (Exception e) {

            if (conexion != null) {

                try {
                    conexion.rollback();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            throw e;

        } finally {

            if (cs != null) {

                try {
                    cs.close();
                } catch (Exception e) {
                }
            }

            if (conexion != null) {

                try {
                    conexion.close();
                } catch (Exception e) {
                }
            }
        }
    }

    // =========================================
    // LOGIN
    // =========================================
    public LoginJwtRespuesta login(
            LoginRequest request)
            throws Exception {

        // Evitar errores 500 cuando falten las credenciales.
        if (request == null
                || request.getUsuario() == null
                || request.getUsuario().trim().isEmpty()
                || request.getContrasena() == null
                || request.getContrasena().isEmpty()) {
            return new LoginJwtRespuesta(false,
                    "Usuario o contraseña incorrectos", null);
        }

        String sql
                = "{ call "
                + "PKG_USUARIO.OBTENER_USUARIO_LOGIN"
                + "(?, ?) }";

        try (
                Connection conexion
                = ConexionBD.obtenerConexion(); CallableStatement cs
                = conexion.prepareCall(sql)) {

            cs.setString(
                    1,
                    request.getUsuario()
            );

            cs.registerOutParameter(
                    2,
                    OracleTypes.CURSOR
            );

            cs.execute();

            try (
                    ResultSet rs
                    = (ResultSet) cs.getObject(2)) {

                // Usuario no encontrado
                if (!rs.next()) {

                    return new LoginJwtRespuesta(
                            false,
                            "Usuario o contraseña incorrectos",
                            null
                    );
                }

                String hash
                        = rs.getString(
                                "CONTRASENA_HASH"
                        );

                // Validar contraseña contra BCrypt
                boolean correcta
                        = BCrypt.checkpw(
                                request.getContrasena(),
                                hash
                        );

                if (!correcta) {

                    return new LoginJwtRespuesta(
                            false,
                            "Usuario o contraseña incorrectos",
                            null
                    );
                }

                Usuario usuario
                        = new Usuario();

                usuario.setIdUsuario(
                        rs.getInt("ID_USUARIO")
                );

                usuario.setUsuario(
                        rs.getString("USUARIO")
                );

                usuario.setNombre(
                        rs.getString("NOMBRE")
                );

                usuario.setRol(
                        rs.getString("ROL")
                );

                int idSucursal
                        = rs.getInt("ID_SUCURSAL");

                if (!rs.wasNull()) {
                    usuario.setIdSucursal(
                            idSucursal
                    );
                }

                usuario.setSucursal(
                        rs.getString("SUCURSAL")
                );

                usuario.setEstado(1);

                // El token solo se crea después de validar BCrypt.
                LoginJwtRespuesta respuesta = new LoginJwtRespuesta(
                        true, "Inicio de sesión correcto", usuario);
                respuesta.setToken(JwtUtil.generarToken(rs.getInt("ID_USUARIO")));
                respuesta.setExpiraEnSegundos(JwtUtil.DURACION_SEGUNDOS);
                return respuesta;
            }
        }
    }

    public List<java.util.Map<String, Object>> consultarRoles()
            throws Exception {

        List<java.util.Map<String, Object>> roles
                = new ArrayList<>();

        String sql
                = "SELECT ID_ROL, NOMBRE, DESCRIPCION "
                + "FROM ROL "
                + "WHERE ESTADO = 1 "
                + "ORDER BY ID_ROL";

        try (
                Connection conexion
                = ConexionBD.obtenerConexion(); java.sql.PreparedStatement ps
                = conexion.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                java.util.Map<String, Object> rol
                        = new java.util.HashMap<>();

                rol.put(
                        "idRol",
                        rs.getInt("ID_ROL")
                );

                rol.put(
                        "nombre",
                        rs.getString("NOMBRE")
                );

                rol.put(
                        "descripcion",
                        rs.getString("DESCRIPCION")
                );

                roles.add(rol);
            }
        }

        return roles;
    }

    public List<java.util.Map<String, Object>> consultarSucursales()
            throws Exception {

        List<java.util.Map<String, Object>> sucursales
                = new ArrayList<>();

        String sql
                = "SELECT ID_SUCURSAL, NOMBRE, DIRECCION, TELEFONO "
                + "FROM SUCURSAL "
                + "WHERE ESTADO = 1 "
                + "ORDER BY ID_SUCURSAL";

        try (
                Connection conexion
                = ConexionBD.obtenerConexion(); java.sql.PreparedStatement ps
                = conexion.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                java.util.Map<String, Object> sucursal
                        = new java.util.HashMap<>();

                sucursal.put(
                        "idSucursal",
                        rs.getInt("ID_SUCURSAL")
                );

                sucursal.put(
                        "nombre",
                        rs.getString("NOMBRE")
                );

                sucursal.put(
                        "direccion",
                        rs.getString("DIRECCION")
                );

                sucursal.put(
                        "telefono",
                        rs.getString("TELEFONO")
                );

                sucursales.add(sucursal);
            }
        }

        return sucursales;
    }
}
