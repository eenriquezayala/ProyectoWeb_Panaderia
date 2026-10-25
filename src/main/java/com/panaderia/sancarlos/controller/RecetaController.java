package com.panaderia.sancarlos.controller;

import com.panaderia.sancarlos.manager.RecetaManager;
import com.panaderia.sancarlos.model.AgregarIngredienteRequest;
import com.panaderia.sancarlos.model.CrearRecetaRequest;
import com.panaderia.sancarlos.model.DetalleReceta;
import com.panaderia.sancarlos.model.Receta;
import com.panaderia.sancarlos.model.RespuestaOperacion;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("recetas")
public class RecetaController {

    private final RecetaManager manager =
            new RecetaManager();


    // =========================================
    // GET /recetas
    // =========================================
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultarRecetas() {

        try {

            List<Receta> recetas =
                    manager.consultarRecetas();

            return Response
                    .ok(recetas)
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            return Response
                    .status(
                        Response.Status.INTERNAL_SERVER_ERROR
                    )
                    .entity(
                        "{\"mensaje\":\"Error al consultar recetas\"}"
                    )
                    .build();
        }
    }


    // =========================================
    // GET /recetas/{id}/detalle
    // =========================================
    @GET
    @Path("{id}/detalle")
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultarDetalle(
            @PathParam("id") int id) {

        try {

            List<DetalleReceta> detalles =
                    manager.consultarDetalle(id);

            return Response
                    .ok(detalles)
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            return Response
                    .status(
                        Response.Status.INTERNAL_SERVER_ERROR
                    )
                    .entity(
                        "{\"mensaje\":\"Error al consultar detalle\"}"
                    )
                    .build();
        }
    }


    // =========================================
    // POST /recetas
    // =========================================
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response crearReceta(
            CrearRecetaRequest request) {

        try {

            RespuestaOperacion respuesta =
                    manager.crearReceta(request);

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

            RespuestaOperacion respuesta =
                    new RespuestaOperacion(
                            false,
                            0,
                            "Error interno al crear receta"
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
    // POST /recetas/{id}/ingredientes
    // =========================================
    @POST
    @Path("{id}/ingredientes")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response agregarIngrediente(
            @PathParam("id") int id,
            AgregarIngredienteRequest request) {

        try {

            RespuestaOperacion respuesta =
                    manager.agregarIngrediente(
                            id,
                            request
                    );

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

            RespuestaOperacion respuesta =
                    new RespuestaOperacion(
                            false,
                            0,
                            "Error interno al agregar ingrediente"
                    );

            return Response
                    .status(
                        Response.Status.INTERNAL_SERVER_ERROR
                    )
                    .entity(respuesta)
                    .build();
        }
    }
}