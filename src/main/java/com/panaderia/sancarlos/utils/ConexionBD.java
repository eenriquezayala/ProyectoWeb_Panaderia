
package com.panaderia.sancarlos.utils;

import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexionBD {

    // Carpeta privada donde se guarda nuestro archivo .env
    private static final String CARPETA_CONFIG =
            Paths.get(
                    System.getProperty("user.home"),
                    ".panaderia-san-carlos"
            ).toString();

    // Cargar configuracion una sola vez
    private static final Dotenv CONFIG = Dotenv.configure()
            .directory(CARPETA_CONFIG)
            .ignoreIfMissing()
            .load();

    static {
        try {
            Class.forName("oracle.jdbc.OracleDriver");
            System.out.println("Driver Oracle cargado correctamente");
        } catch (ClassNotFoundException e) {
            System.err.println("No se pudo cargar el driver Oracle");
            e.printStackTrace();
        }
    }

    private static String obtenerConfiguracion(String clave)
            throws SQLException {

        String valor = CONFIG.get(clave);

        if (valor == null || valor.trim().isEmpty()) {
            throw new SQLException(
                    "Falta configurar " + clave
                    + " en el archivo .env"
            );
        }

        return valor;
    }

    public static Connection obtenerConexion() throws SQLException {

        String url = obtenerConfiguracion("PANADERIA_DB_URL");

        String usuario = obtenerConfiguracion("PANADERIA_DB_USER");

        String contrasena =
                obtenerConfiguracion("PANADERIA_DB_PASSWORD");

        return DriverManager.getConnection(
                url,
                usuario,
                contrasena
        );
    }
}
