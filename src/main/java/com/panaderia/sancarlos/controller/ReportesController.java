package com.panaderia.sancarlos.controller;

import com.panaderia.sancarlos.manager.ReportesManager;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("reportes")
@Produces(MediaType.APPLICATION_JSON)
public class ReportesController {

    private final ReportesManager manager = new ReportesManager();

    private interface ConsultaConFechas {
        List<Map<String, Object>> consultar(
            String desde, String hasta) throws Exception;
    }

    private Response ejecutarConFechas(
            String desde, String hasta, ConsultaConFechas consulta) {
        try {
            return Response.ok(consulta.consultar(desde, hasta)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Collections.singletonMap("mensaje", e.getMessage()))
                .build();
        } catch (Exception e) {
            e.printStackTrace();
            return error500();
        }
    }

    private Response error500() {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(Collections.singletonMap(
                "mensaje", "Error interno al consultar el reporte"))
            .build();
    }

    @GET
    @Path("pedidos-por-sucursal")
    public Response pedidosPorSucursal(
            @QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta) {
        return ejecutarConFechas(desde, hasta, manager::pedidosPorSucursal);
    }

    @GET
    @Path("panes-mas-solicitados")
    public Response panesMasSolicitados(
            @QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta) {
        return ejecutarConFechas(desde, hasta, manager::panesMasSolicitados);
    }

    @GET
    @Path("produccion-diaria")
    public Response produccionDiaria(
            @QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta) {
        return ejecutarConFechas(desde, hasta, manager::produccionDiaria);
    }

    @GET
    @Path("despachos-por-sucursal")
    public Response despachosPorSucursal(
            @QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta) {
        return ejecutarConFechas(desde, hasta, manager::despachosPorSucursal);
    }

    @GET
    @Path("inventario-bajo")
    public Response inventarioBajo() {
        try {
            return Response.ok(manager.inventarioBajo()).build();
        } catch (Exception e) {
            e.printStackTrace();
            return error500();
        }
    }
}
