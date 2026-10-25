package com.panaderia.sancarlos.controller;

import com.panaderia.sancarlos.manager.InventarioManager;
import com.panaderia.sancarlos.model.AjusteInventarioRequest;
import com.panaderia.sancarlos.model.EntradaInventarioRequest;
import com.panaderia.sancarlos.model.RespuestaOperacion;
import com.panaderia.sancarlos.model.SesionAutenticada;
import java.security.Principal;
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

@Path("inventario")
@Produces(MediaType.APPLICATION_JSON)
public class InventarioController {

    private final InventarioManager manager = new InventarioManager();

    @Context
    private SecurityContext seguridad;

    // El FiltroJwt coloca la identidad VERIFICADA en el SecurityContext.
    private int idUsuarioAutenticado() {
        Principal principal = seguridad == null ? null : seguridad.getUserPrincipal();
        if (!(principal instanceof SesionAutenticada)) {
            throw new SecurityException("Sesion no disponible");
        }
        int id = ((SesionAutenticada) principal).getIdUsuario();
        if (id <= 0) {
            throw new SecurityException("Sesion invalida");
        }
        return id;
    }

    // GET /inventario
    @GET
    public Response consultarInventario() {
        try {
            return Response.ok(manager.consultarInventario()).build();
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, 0, "Error al consultar inventario");
        }
    }

    // GET /inventario/{id}/movimientos
    @GET
    @Path("{id}/movimientos")
    public Response consultarMovimientos(@PathParam("id") int id) {
        if (id <= 0) return error(400, id, "ID de producto invalido");
        try {
            return Response.ok(manager.consultarMovimientos(id)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, id, "Error al consultar movimientos");
        }
    }

    // POST /inventario/entradas
    @POST
    @Path("entradas")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registrarEntrada(EntradaInventarioRequest request) {
        if (request == null) return error(400, 0, "Cuerpo JSON requerido");
        try {
            int idUsuario = idUsuarioAutenticado();
            RespuestaOperacion respuesta = manager.registrarEntrada(request, idUsuario);
            return Response.status(respuesta.isExito() ? 201 : 400)
                    .entity(respuesta).build();
        } catch (SecurityException e) {
            return error(401, 0, e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(400, request.getIdProducto(), e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, request.getIdProducto(),
                    "Error interno al registrar entrada");
        }
    }

    // PUT /inventario/ajustes
    @PUT
    @Path("ajustes")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registrarAjuste(AjusteInventarioRequest request) {
        if (request == null) return error(400, 0, "Cuerpo JSON requerido");
        try {
            int idUsuario = idUsuarioAutenticado();
            RespuestaOperacion respuesta = manager.registrarAjuste(request, idUsuario);
            return Response.status(respuesta.isExito() ? 200 : 400)
                    .entity(respuesta).build();
        } catch (SecurityException e) {
            return error(401, 0, e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(400, request.getIdProducto(), e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, request.getIdProducto(),
                    "Error interno al registrar ajuste");
        }
    }

    private Response error(int codigo, int id, String mensaje) {
        return Response.status(codigo)
                .entity(new RespuestaOperacion(false, id, mensaje))
                .build();
    }
}
