package com.panaderia.sancarlos.controller;

import com.panaderia.sancarlos.manager.PedidoManager;
import com.panaderia.sancarlos.model.AgregarProductoPedidoRequest;
import com.panaderia.sancarlos.model.CrearPedidoRequest;
import com.panaderia.sancarlos.model.RespuestaOperacion;
import com.panaderia.sancarlos.model.SesionAutenticada;
import java.security.Principal;
import java.util.NoSuchElementException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@Path("pedidos")
@Produces(MediaType.APPLICATION_JSON)
public class PedidoController {

    private final PedidoManager manager = new PedidoManager();

    @Context
    private SecurityContext seguridad;

    private SesionAutenticada sesion() {
        Principal principal = seguridad == null ? null : seguridad.getUserPrincipal();
        if (!(principal instanceof SesionAutenticada)) {
            throw new SecurityException("Sesion no disponible");
        }
        return (SesionAutenticada) principal;
    }

    // Para ADMINISTRADOR se devuelve null: no hay restriccion de sucursal.
    private Integer sucursalRestringida(SesionAutenticada s) {
        if (!s.esSucursal()) return null;
        if (s.getIdSucursal() == null || s.getIdSucursal() <= 0) {
            throw new SecurityException("El usuario no tiene sucursal activa");
        }
        return s.getIdSucursal();
    }

    @GET
    public Response consultarPedidos() {
        try {
            SesionAutenticada s = sesion();
            return Response.ok(s.esSucursal()
                    ? manager.consultarPedidosSucursal(sucursalRestringida(s))
                    : manager.consultarPedidos()).build();
        } catch (SecurityException e) {
            return error(401, 0, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, 0, "Error al consultar pedidos");
        }
    }

    @GET
    @Path("{id}/detalle")
    public Response consultarDetalle(@PathParam("id") int id) {
        if (id <= 0) return error(400, id, "ID de pedido invalido");
        try {
            SesionAutenticada s = sesion();
            return Response.ok(s.esSucursal()
                    ? manager.consultarDetalleSucursal(id, sucursalRestringida(s))
                    : manager.consultarDetalle(id)).build();
        } catch (NoSuchElementException e) {
            return error(404, id, "Pedido no encontrado");
        } catch (SecurityException e) {
            return error(401, id, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, id, "Error al consultar detalle");
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response crearPedido(CrearPedidoRequest request) {
        if (request == null) return error(400, 0, "Cuerpo JSON requerido");
        try {
            SesionAutenticada s = sesion();
            int idSucursal;
            if (s.esSucursal()) {
                idSucursal = sucursalRestringida(s);
                // Si el cliente envia una sucursal distinta, rechazamos el intento.
                if (request.getIdSucursal() > 0
                        && request.getIdSucursal() != idSucursal) {
                    return error(403, 0, "No puede crear pedidos en otra sucursal");
                }
            } else {
                idSucursal = request.getIdSucursal();
            }
            // El usuario responsable SIEMPRE es el del JWT, no el del JSON.
            RespuestaOperacion respuesta = manager.crearPedido(
                    request, s.getIdUsuario(), idSucursal);
            return Response.status(respuesta.isExito() ? 201 : 400)
                    .entity(respuesta).build();
        } catch (IllegalArgumentException e) {
            return error(400, 0, e.getMessage());
        } catch (SecurityException e) {
            return error(401, 0, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, 0, "Error interno al crear pedido");
        }
    }

    @POST
    @Path("{id}/productos")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response agregarProducto(
            @PathParam("id") int id, AgregarProductoPedidoRequest request) {
        try {
            SesionAutenticada s = sesion();
            RespuestaOperacion respuesta = manager.agregarProducto(
                    id, request, sucursalRestringida(s));
            return Response.status(respuesta.isExito() ? 201 : 400)
                    .entity(respuesta).build();
        } catch (NoSuchElementException e) {
            return error(404, id, "Pedido no encontrado");
        } catch (IllegalArgumentException e) {
            return error(400, id, e.getMessage());
        } catch (SecurityException e) {
            return error(401, id, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, id, "Error interno al agregar producto");
        }
    }

    @PUT
    @Path("{id}/enviar")
    public Response enviarPedido(@PathParam("id") int id) {
        try {
            SesionAutenticada s = sesion();
            RespuestaOperacion respuesta = manager.enviarPedido(
                    id, sucursalRestringida(s));
            return Response.status(respuesta.isExito() ? 200 : 400)
                    .entity(respuesta).build();
        } catch (NoSuchElementException e) {
            return error(404, id, "Pedido no encontrado");
        } catch (IllegalArgumentException e) {
            return error(400, id, e.getMessage());
        } catch (SecurityException e) {
            return error(401, id, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, id, "Error interno al enviar pedido");
        }
    }

    private Response error(int codigo, int id, String mensaje) {
        return Response.status(codigo)
                .entity(new RespuestaOperacion(false, id, mensaje))
                .build();
    }
}
