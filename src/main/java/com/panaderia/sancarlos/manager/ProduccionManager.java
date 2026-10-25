
package com.panaderia.sancarlos.manager;

import com.panaderia.sancarlos.model.Produccion;
import com.panaderia.sancarlos.model.DetalleProduccion;
import com.panaderia.sancarlos.model.GenerarProduccionRequest;
import com.panaderia.sancarlos.model.RespuestaOperacion;
import com.panaderia.sancarlos.utils.ConexionBD;
import com.panaderia.sancarlos.model.PlanInsumo;
import com.panaderia.sancarlos.model.PlanInsumosRespuesta;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import oracle.jdbc.OracleTypes;

public class ProduccionManager {

    // Convertir fechas de Oracle a String
    private String leerFecha(ResultSet rs, String columna)
            throws SQLException {

        Timestamp fecha = rs.getTimestamp(columna);
        return fecha == null ? null : fecha.toString();
    }

    // ==========================================
    // CONSULTAR PRODUCCIONES
    // ==========================================
    public List<Produccion> consultarProducciones()
            throws Exception {

        List<Produccion> lista = new ArrayList<>();

        String sql =
            "{ call PKG_PRODUCCION.CONSULTAR_PRODUCCIONES(?) }";

        try (
            Connection conexion = ConexionBD.obtenerConexion();
            CallableStatement cs = conexion.prepareCall(sql)
        ) {

            cs.registerOutParameter(1, OracleTypes.CURSOR);
            cs.execute();

            try (ResultSet rs = (ResultSet) cs.getObject(1)) {

                while (rs.next()) {

                    Produccion p = new Produccion();

                    p.setIdProduccion(
                        rs.getInt("ID_PRODUCCION")
                    );

                    p.setFechaProduccion(
                        leerFecha(rs, "FECHA_PRODUCCION")
                    );

                    p.setEstado(rs.getString("ESTADO"));

                    p.setFechaInicio(
                        leerFecha(rs, "FECHA_INICIO")
                    );

                    p.setFechaFin(
                        leerFecha(rs, "FECHA_FIN")
                    );

                    lista.add(p);
                }
            }
        }

        return lista;
    }

    // ==========================================
    // CONSULTAR DETALLE
    // ==========================================
    public List<DetalleProduccion> consultarDetalle(
            int idProduccion) throws Exception {

        List<DetalleProduccion> lista = new ArrayList<>();

        String sql =
            "{ call PKG_PRODUCCION.CONSULTAR_DETALLE(?, ?) }";

        try (
            Connection conexion = ConexionBD.obtenerConexion();
            CallableStatement cs = conexion.prepareCall(sql)
        ) {

            cs.setInt(1, idProduccion);
            cs.registerOutParameter(2, OracleTypes.CURSOR);

            cs.execute();

            try (ResultSet rs = (ResultSet) cs.getObject(2)) {

                while (rs.next()) {

                    DetalleProduccion detalle =
                            new DetalleProduccion();

                    detalle.setProducto(
                        rs.getString("PRODUCTO")
                    );

                    detalle.setCantidadProgramada(
                        rs.getInt("CANTIDAD_PROGRAMADA")
                    );

                    detalle.setCantidadProducida(
                        rs.getInt("CANTIDAD_PRODUCIDA")
                    );

                    lista.add(detalle);
                }
            }
        }

        return lista;
    }

    // ==========================================
    // GENERAR PRODUCCION
    // ==========================================
    public RespuestaOperacion generarProduccion(
            GenerarProduccionRequest request, int idUsuario)
            throws Exception {

        if (request == null
                || request.getFecha() == null
                || idUsuario <= 0) {

            throw new IllegalArgumentException(
                "Debe indicar una fecha valida y una sesion activa"
            );
        }

        // Formato esperado: yyyy-MM-dd
        java.sql.Date fecha =
            java.sql.Date.valueOf(request.getFecha());

        String sql =
            "{ call PKG_PRODUCCION.GENERAR_PRODUCCION"
            + "(?, ?, ?, ?) }";

        try (Connection conexion =
                ConexionBD.obtenerConexion()) {

            conexion.setAutoCommit(false);

            try (CallableStatement cs =
                    conexion.prepareCall(sql)) {

                cs.setDate(1, fecha);
                cs.setInt(2, idUsuario);

                cs.registerOutParameter(3, Types.NUMERIC);
                cs.registerOutParameter(4, Types.VARCHAR);

                cs.execute();

                int id = cs.getInt(3);
                String mensaje = cs.getString(4);

                boolean exito =
                    id > 0 &&
                    "Produccion generada correctamente"
                        .equals(mensaje);

                if (exito) {
                    conexion.commit();
                } else {
                    conexion.rollback();
                }

                return new RespuestaOperacion(
                    exito,
                    exito ? id : 0,
                    mensaje
                );

            } catch (Exception e) {
                try {
                    conexion.rollback();
                } catch (SQLException ex) {
                    e.addSuppressed(ex);
                }
                throw e;
            }
        }
    }

    // ==========================================
    // METODO REUTILIZABLE PARA INICIAR/FINALIZAR
    // ==========================================
    private RespuestaOperacion ejecutarAccion(
            int idProduccion,
            int idUsuario,
            String procedimiento,
            String mensajeExito) throws Exception {

        if (idProduccion <= 0 || idUsuario <= 0) {
            throw new IllegalArgumentException(
                "Los identificadores deben ser mayores a cero"
            );
        }

        String sql = "{ call PKG_PRODUCCION."
                + procedimiento + "(?, ?, ?) }";

        try (Connection conexion =
                ConexionBD.obtenerConexion()) {

            conexion.setAutoCommit(false);

            try (CallableStatement cs =
                    conexion.prepareCall(sql)) {

                cs.setInt(1, idProduccion);
                cs.setInt(2, idUsuario);
                cs.registerOutParameter(3, Types.VARCHAR);

                cs.execute();

                String mensaje = cs.getString(3);
                boolean exito = mensajeExito.equals(mensaje);

                if (exito) {
                    conexion.commit();
                } else {
                    conexion.rollback();
                }

                return new RespuestaOperacion(
                    exito, idProduccion, mensaje
                );

            } catch (Exception e) {
                try {
                    conexion.rollback();
                } catch (SQLException ex) {
                    e.addSuppressed(ex);
                }
                throw e;
            }
        }
    }

    // ==========================================
    // INICIAR PRODUCCION
    // ==========================================
    public RespuestaOperacion iniciarProduccion(
            int idProduccion,
            int idUsuario) throws Exception {

        return ejecutarAccion(
            idProduccion,
            idUsuario,
            "INICIAR_PRODUCCION",
            "Produccion iniciada correctamente"
        );
    }

    // ==========================================
    // FINALIZAR PRODUCCION
    // ==========================================
    public RespuestaOperacion finalizarProduccion(
            int idProduccion,
            int idUsuario) throws Exception {

        return ejecutarAccion(
            idProduccion,
            idUsuario,
            "FINALIZAR_PRODUCCION",
            "Produccion finalizada correctamente"
        );
    }
    
    
public PlanInsumosRespuesta consultarPlanInsumos(
        int idProduccion) throws Exception {

    if (idProduccion <= 0) {
        throw new IllegalArgumentException(
            "El ID de produccion debe ser mayor a cero"
        );
    }

    String sql =
        "{ call PKG_PRODUCCION.CONSULTAR_PLAN_INSUMOS(?, ?, ?) }";

    try (
        Connection conexion = ConexionBD.obtenerConexion();
        CallableStatement cs = conexion.prepareCall(sql)
    ) {

        cs.setInt(1, idProduccion);
        cs.registerOutParameter(2, Types.NUMERIC);
        cs.registerOutParameter(3, OracleTypes.CURSOR);

        cs.execute();

        int productosSinReceta = cs.getInt(2);

        List<PlanInsumo> insumos = new ArrayList<>();

        boolean faltanInsumos = false;

        try (ResultSet rs = (ResultSet) cs.getObject(3)) {

            while (rs.next()) {

                PlanInsumo insumo = new PlanInsumo();

                insumo.setIdInsumo(
                    rs.getInt("ID_INSUMO")
                );

                insumo.setCodigo(
                    rs.getString("CODIGO")
                );

                insumo.setNombre(
                    rs.getString("INSUMO")
                );

                insumo.setUnidadMedida(
                    rs.getString("UNIDAD_MEDIDA")
                );

                insumo.setCantidadNecesaria(
                    rs.getDouble("CANTIDAD_NECESARIA")
                );

                insumo.setExistenciaActual(
                    rs.getDouble("EXISTENCIA_ACTUAL")
                );

                insumo.setCantidadFaltante(
                    rs.getDouble("CANTIDAD_FALTANTE")
                );

                insumo.setEstadoInsumo(
                    rs.getString("ESTADO_INSUMO")
                );

                if (!"SUFICIENTE".equals(
                        insumo.getEstadoInsumo())) {

                    faltanInsumos = true;
                }

                insumos.add(insumo);
            }
        }

        PlanInsumosRespuesta respuesta =
            new PlanInsumosRespuesta();

        respuesta.setIdProduccion(idProduccion);

        respuesta.setProductosSinRecetaCompleta(
            productosSinReceta
        );

        respuesta.setInsumos(insumos);

        boolean puedeIniciar =
            productosSinReceta == 0
            && !insumos.isEmpty()
            && !faltanInsumos;

        respuesta.setPuedeIniciar(puedeIniciar);

        String mensaje;

        if (productosSinReceta > 0) {
            mensaje = "Hay productos sin receta completa";
        } else if (insumos.isEmpty()) {
            mensaje = "No hay insumos calculados";
        } else if (faltanInsumos) {
            mensaje = "Faltan insumos para iniciar la produccion";
        } else {
            mensaje = "Hay insumos suficientes para iniciar la produccion";
        }

        respuesta.setMensaje(mensaje);

        return respuesta;
    }
}

}
