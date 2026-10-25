package com.panaderia.sancarlos.manager;

import com.panaderia.sancarlos.utils.ConexionBD;
import com.panaderia.sancarlos.utils.JwtUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Guarda solo el hash SHA-256 del token; nunca persiste el JWT original. */
public class TokenRevocadoManager {

    private static String hashToken(String token) throws Exception {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token requerido");
        }
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
        StringBuilder resultado = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int valor = b & 0xff;
            resultado.append(Character.forDigit(valor >>> 4, 16));
            resultado.append(Character.forDigit(valor & 0x0f, 16));
        }
        return resultado.toString();
    }

    /** Consulta en Oracle, en cada petición, si el token fue revocado. */
    public boolean estaRevocado(String token) throws Exception {
        String sql = "SELECT 1 FROM TOKEN_JWT_REVOCADO WHERE TOKEN_HASH = ?";
        try (Connection cn = ConexionBD.obtenerConexion();
                PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, hashToken(token));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Invalida este JWT (no todas las sesiones del usuario). */
    public void revocarToken(String token, int idUsuario) throws Exception {
        if (idUsuario <= 0) {
            throw new IllegalArgumentException("Usuario invalido");
        }

        // Verifica firma, emisor y vencimiento antes de registrar la revocación.
        if (JwtUtil.validarToken(token) != idUsuario) {
            throw new SecurityException("Token no corresponde al usuario");
        }
        long expiraEnMs = JwtUtil.obtenerExpiracionEnMillis(token);

        String sql = "INSERT INTO TOKEN_JWT_REVOCADO "
                + "(TOKEN_HASH, ID_USUARIO, EXPIRA_EN_MS) VALUES (?, ?, ?)";
        try (Connection cn = ConexionBD.obtenerConexion()) {
            cn.setAutoCommit(false);
            try (PreparedStatement ps = cn.prepareStatement(sql)) {
                ps.setString(1, hashToken(token));
                ps.setInt(2, idUsuario);
                ps.setLong(3, expiraEnMs);
                ps.executeUpdate();
                cn.commit();
            } catch (SQLException e) {
                try {
                    cn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                // Revocación concurrente del mismo token: idempotente.
                if (e.getErrorCode() != 1) {
                    throw e;
                }
            } catch (Exception e) {
                try {
                    cn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }
}
