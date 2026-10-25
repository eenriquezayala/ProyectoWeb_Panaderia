package com.panaderia.sancarlos.controller;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.PathParam;
/**
 *
 * @author edwar
 */

@Path("prueba")
public class PruebaController {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response prueba() {

        String respuesta =
                "{\"mensaje\":\"API Panaderia San Carlos funcionando\"}";

        return Response
                .ok(respuesta)
                .build();
    }
    
    
}