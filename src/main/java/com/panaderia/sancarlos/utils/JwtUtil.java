package com.panaderia.sancarlos.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Paths;

/** JWT HS256. La clave secreta no se guarda en el repositorio. */
public final class JwtUtil {

    private JwtUtil() { }

    public static final long DURACION_SEGUNDOS = 3600L; // Una hora
    private static final String ISSUER = "panaderia-san-carlos";
    private static final String ENV = "PANADERIA_JWT_SECRET_BASE64";
    
private static final Dotenv CONFIG = Dotenv.configure()
        .directory(
            Paths.get(
                System.getProperty("user.home"),
                ".panaderia-san-carlos"
            ).toString()
        )
        .ignoreIfMissing()
        .load();

    private static Key clave() {
        String secreto = CONFIG.get(ENV);
        if (secreto == null || secreto.trim().isEmpty()) {
            throw new IllegalStateException(
                "Falta configurar la variable de entorno " + ENV);
        }
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secreto.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("La clave JWT debe estar en Base64", e);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException(
                "La clave JWT necesita al menos 32 bytes aleatorios");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public static String generarToken(int idUsuario) {
        if (idUsuario <= 0) {
            throw new IllegalArgumentException("ID de usuario invalido");
        }
        long ahora = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(Integer.toString(idUsuario))
                .setId(UUID.randomUUID().toString())
                .setIssuer(ISSUER)
                .setIssuedAt(new Date(ahora))
                .setExpiration(new Date(ahora + DURACION_SEGUNDOS * 1000L))
                .signWith(clave(), SignatureAlgorithm.HS256)
                .compact();
    }

    private static Claims claimsVerificados(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(clave())
                .requireIssuer(ISSUER)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public static int validarToken(String token) {
        Claims claims = claimsVerificados(token);
        int idUsuario = Integer.parseInt(claims.getSubject());
        if (idUsuario <= 0) {
            throw new IllegalArgumentException("ID de token invalido");
        }
        return idUsuario;
    }

    /** Expiración firmada del JWT, en milisegundos desde 1970 (UTC). */
    public static long obtenerExpiracionEnMillis(String token) {
        Date expiracion = claimsVerificados(token).getExpiration();
        if (expiracion == null) {
            throw new IllegalArgumentException("Token sin expiracion");
        }
        return expiracion.getTime();
    }
}
