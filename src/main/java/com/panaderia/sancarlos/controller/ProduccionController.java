package com.panaderia.sancarlos.controller;

import com.panaderia.sancarlos.manager.ProduccionManager;
import com.panaderia.sancarlos.model.GenerarProduccionRequest;
import com.panaderia.sancarlos.model.RespuestaOperacion;
import com.panaderia.sancarlos.model.SesionAutenticada;
import java.security.Principal;
import java.sql.SQLException;
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

@Path("producciones")
@Produces(MediaType.APPLICATION_JSON)
public class ProduccionController {

    private final ProduccionManager manager = new ProduccionManager();

    @Context
    private SecurityContext seguridad;

    // Nunca tomar el responsable del cuerpo JSON.
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

    // GET /producciones
    @GET
    public Response consultarProducciones() {
        try {
            return Response.ok(manager.consultarProducciones()).build();
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, 0, "Error al consultar producciones");
        }
    }

    // GET /producciones/{id}/detalle
    @GET
    @Path("{id}/detalle")
    public Response consultarDetalle(@PathParam("id") int id) {
        if (id <= 0) return error(400, id, "ID de produccion invalido");
        try {
            return Response.ok(manager.consultarDetalle(id)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, id, "Error al consultar detalle");
        }
    }

    // POST /producciones
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response generarProduccion(GenerarProduccionRequest request) {
        if (request == null) return error(400, 0, "Cuerpo JSON requerido");
        try {
            int idUsuario = idUsuarioAutenticado();
            RespuestaOperacion respuesta = manager.generarProduccion(request, idUsuario);
            return Response.status(respuesta.isExito() ? 201 : 400)
                    .entity(respuesta).build();
        } catch (SecurityException e) {
            return error(401, 0, e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(400, 0, "Fecha o datos invalidos. Use yyyy-MM-dd");
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, 0, "Error interno al generar produccion");
        }
    }

    // PUT /producciones/{id}/iniciar : no necesita Body
    @PUT
    @Path("{id}/iniciar")
    public Response iniciarProduccion(@PathParam("id") int id) {
        if (id <= 0) return error(400, id, "ID de produccion invalido");
        try {
            int idUsuario = idUsuarioAutenticado();
            RespuestaOperacion respuesta = manager.iniciarProduccion(id, idUsuario);
            return Response.status(respuesta.isExito() ? 200 : 400)
                    .entity(respuesta).build();
        } catch (SecurityException e) {
            return error(401, id, e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(400, id, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, id, "Error interno al iniciar produccion");
        }
    }

    // PUT /producciones/{id}/finalizar : no necesita Body
    @PUT
    @Path("{id}/finalizar")
    public Response finalizarProduccion(@PathParam("id") int id) {
        if (id <= 0) return error(400, id, "ID de produccion invalido");
        try {
            int idUsuario = idUsuarioAutenticado();
            RespuestaOperacion respuesta = manager.finalizarProduccion(id, idUsuario);
            return Response.status(respuesta.isExito() ? 200 : 400)
                    .entity(respuesta).build();
        } catch (SecurityException e) {
            return error(401, id, e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(400, id, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, id, "Error interno al finalizar produccion");
        }
    }

    // Se preserva la planificacion de insumos existente.
    @GET
    @Path("{id}/plan-insumos")
    public Response consultarPlanInsumos(@PathParam("id") int id) {
        if (id <= 0) return error(400, id, "ID de produccion invalido");
        try {
            return Response.ok(manager.consultarPlanInsumos(id)).build();
        } catch (SQLException e) {
            e.printStackTrace();
            int codigoOracle = Math.abs(e.getErrorCode());
            if (codigoOracle == 20050) {
                return error(404, id, "Produccion no encontrada");
            }
            if (codigoOracle == 20051) {
                return error(409, id,
                        "El plan solo aplica a producciones PENDIENTES");
            }
            return error(500, id, "Error al consultar el plan");
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, id, "Error al consultar el plan");
        }
    }

    private Response error(int codigo, int id, String mensaje) {
        return Response.status(codigo)
                .entity(new RespuestaOperacion(false, id, mensaje))
                .build();
    }
}
