package com.panaderia.sancarlos.controller;

import com.panaderia.sancarlos.utils.ConexionBD;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
/**
 *
 * @author edwar
 */
@Path("prueba-bd")
public class PruebaBDController {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response probarConexion() {

        try (
            Connection conexion = ConexionBD.obtenerConexion();
            Statement statement = conexion.createStatement();
            ResultSet resultado = statement.executeQuery(
                    "SELECT USER FROM DUAL"
            )
        ) {

            if (resultado.next()) {

                String usuario = resultado.getString(1);

                String respuesta =
                        "{"
                        + "\"conexion\":\"correcta\","
                        + "\"usuario\":\"" + usuario + "\""
                        + "}";

                return Response.ok(respuesta).build();
            }

            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"No se obtuvo respuesta de Oracle\"}")
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            String mensaje = e.getMessage()
                    .replace("\"", "'");

            String respuesta =
                    "{"
                    + "\"conexion\":\"error\","
                    + "\"mensaje\":\"" + mensaje + "\""
                    + "}";

            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(respuesta)
                    .build();
        }
    }
}
