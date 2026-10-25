package com.panaderia.sancarlos.controller;

import com.panaderia.sancarlos.manager.ProductoManager;
import com.panaderia.sancarlos.model.Producto;

import java.util.List;
import com.panaderia.sancarlos.model.RespuestaOperacion;
import com.panaderia.sancarlos.model.EstadoRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.PathParam;
import javax.ws.rs.PUT;

/**
 *
 * @author edwar
 */
@Path("productos")
public class ProductoController {

    private final ProductoManager manager
            = new ProductoManager();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultarProductos(
            @QueryParam("tipo") String tipo) {

        try {

            List<Producto> productos
                    = manager.consultarProductos(tipo);

            return Response
                    .ok(productos)
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            String mensaje
                    = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'")
                    : "Error interno del servidor";

            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(
                            "{\"error\":\"" + mensaje + "\"}"
                    )
                    .build();
        }
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultarProducto(
            @PathParam("id") int id) {

        try {

            Producto producto
                    = manager.consultarProducto(id);

            if (producto == null) {

                return Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(
                                "{\"mensaje\":\"Producto no encontrado\"}"
                        )
                        .build();
            }

            return Response
                    .ok(producto)
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            return Response
                    .status(
                            Response.Status.INTERNAL_SERVER_ERROR
                    )
                    .entity(
                            "{\"mensaje\":\"Error al consultar producto\"}"
                    )
                    .build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response crearProducto(
            Producto producto) {

        try {

            RespuestaOperacion respuesta
                    = manager.crearProducto(producto);

            if (respuesta.isExito()) {

                return Response
                        .status(Response.Status.CREATED)
                        .entity(respuesta)
                        .build();
            }

            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(respuesta)
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            RespuestaOperacion respuesta
                    = new RespuestaOperacion(
                            false,
                            0,
                            "Error interno al crear producto"
                    );

            return Response
                    .status(
                            Response.Status.INTERNAL_SERVER_ERROR
                    )
                    .entity(respuesta)
                    .build();
        }
    }

    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response actualizarProducto(
            @PathParam("id") int id,
            Producto producto) {

        try {

            RespuestaOperacion respuesta
                    = manager.actualizarProducto(
                            id,
                            producto
                    );

            if (respuesta.isExito()) {

                return Response
                        .ok(respuesta)
                        .build();
            }

            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(respuesta)
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            RespuestaOperacion respuesta
                    = new RespuestaOperacion(
                            false,
                            id,
                            "Error interno al actualizar producto"
                    );

            return Response
                    .status(
                            Response.Status.INTERNAL_SERVER_ERROR
                    )
                    .entity(respuesta)
                    .build();
        }
    }

    @PUT
    @Path("{id}/estado")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response cambiarEstado(
            @PathParam("id") int id,
            EstadoRequest request) {

        try {

            RespuestaOperacion respuesta
                    = manager.cambiarEstado(
                            id,
                            request.getEstado()
                    );

            if (respuesta.isExito()) {

                return Response
                        .ok(respuesta)
                        .build();
            }

            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(respuesta)
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            RespuestaOperacion respuesta
                    = new RespuestaOperacion(
                            false,
                            id,
                            "Error interno al cambiar estado"
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
