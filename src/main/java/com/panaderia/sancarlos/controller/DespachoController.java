package com.panaderia.sancarlos.controller;

import com.panaderia.sancarlos.manager.DespachoManager;
import com.panaderia.sancarlos.model.CrearDespachoRequest;
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

@Path("despachos")
@Produces(MediaType.APPLICATION_JSON)
public class DespachoController {

    private final DespachoManager manager = new DespachoManager();

    @Context
    private SecurityContext seguridad;

    // Identidad verificada por FiltroJwt contra Oracle.
    private SesionAutenticada sesion() {
        Principal principal = seguridad == null
                ? null : seguridad.getUserPrincipal();
        if (!(principal instanceof SesionAutenticada)) {
            throw new SecurityException("Sesion no disponible");
        }
        return (SesionAutenticada) principal;
    }

    private int sucursalAutenticada(SesionAutenticada s) {
        Integer id = s.getIdSucursal();
        if (id == null || id <= 0) {
            throw new SecurityException("El usuario no tiene sucursal activa");
        }
        return id;
    }

    // GET /webapi/despachos
    @GET
    public Response consultarDespachos() {
        try {
            SesionAutenticada s = sesion();
            return Response.ok(s.esSucursal()
                    ? manager.consultarDespachosSucursal(sucursalAutenticada(s))
                    : manager.consultarDespachos()).build();
        } catch (SecurityException e) {
            return error(401, 0, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, 0, "Error interno al consultar despachos");
        }
    }

    // GET /webapi/despachos/{id}/detalle
    @GET
    @Path("{id}/detalle")
    public Response consultarDetalle(@PathParam("id") int id) {
        if (id <= 0) return error(400, id, "ID de despacho invalido");
        try {
            SesionAutenticada s = sesion();
            return Response.ok(s.esSucursal()
                    ? manager.consultarDetalleSucursal(id, sucursalAutenticada(s))
                    : manager.consultarDetalle(id)).build();
        } catch (NoSuchElementException e) {
            // No revelar si el despacho existe en otra sucursal.
            return error(404, id, "Despacho no encontrado");
        } catch (SecurityException e) {
            return error(401, id, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, id, "Error al consultar detalle del despacho");
        }
    }

    // POST /webapi/despachos (solo ADMINISTRADOR / PRODUCCION)
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registrarDespacho(CrearDespachoRequest request) {
        try {
            SesionAutenticada s = sesion();
            // Nunca confiar en idUsuario del JSON: usar el JWT.
            RespuestaOperacion respuesta = manager.registrarDespacho(
                    request, s.getIdUsuario());
            return Response.status(respuesta.isExito() ? 201 : 400)
                    .entity(respuesta).build();
        } catch (IllegalArgumentException e) {
            return error(400, 0, e.getMessage());
        } catch (SecurityException e) {
            return error(401, 0, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, 0, "Error interno al registrar despacho");
        }
    }

    // PUT /webapi/despachos/{id}/recibir (ADMINISTRADOR / PRODUCCION)
    @PUT
    @Path("{id}/recibir")
    public Response marcarRecibido(@PathParam("id") int id) {
        if (id <= 0) return error(400, id, "ID de despacho invalido");
        try {
            sesion(); // Exigir contexto autenticado tambien aqui.
            RespuestaOperacion respuesta = manager.marcarRecibido(id);
            return Response.status(respuesta.isExito() ? 200 : 400)
                    .entity(respuesta).build();
        } catch (IllegalArgumentException e) {
            return error(400, id, e.getMessage());
        } catch (SecurityException e) {
            return error(401, id, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, id, "Error interno al confirmar recepcion");
        }
    }

    private Response error(int codigo, int id, String mensaje) {
        return Response.status(codigo)
                .entity(new RespuestaOperacion(false, id, mensaje))
                .build();
    }
}
