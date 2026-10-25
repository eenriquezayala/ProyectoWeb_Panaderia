package com.panaderia.sancarlos.manager;

import com.panaderia.sancarlos.model.AjusteInventarioRequest;
import com.panaderia.sancarlos.model.EntradaInventarioRequest;
import com.panaderia.sancarlos.model.Inventario;
import com.panaderia.sancarlos.model.MovimientoInventario;
import com.panaderia.sancarlos.model.RespuestaOperacion;
import com.panaderia.sancarlos.utils.ConexionBD;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Types;

import java.util.ArrayList;
import java.util.List;

import oracle.jdbc.OracleTypes;

public class InventarioManager {

    // =========================================
    // CONSULTAR INVENTARIO
    // =========================================
    public List<Inventario> consultarInventario()
            throws Exception {

        List<Inventario> inventario =
                new ArrayList<>();

        String sql =
                "{ call PKG_INVENTARIO.CONSULTAR_INVENTARIO(?) }";

        try (
            Connection conexion =
                    ConexionBD.obtenerConexion();

            CallableStatement cs =
                    conexion.prepareCall(sql)
        ) {

            cs.registerOutParameter(
                    1,
                    OracleTypes.CURSOR
            );

            cs.execute();

            try (
                ResultSet rs =
                        (ResultSet) cs.getObject(1)
            ) {

                while (rs.next()) {

                    Inventario item =
                            new Inventario();

                    item.setIdProducto(
                            rs.getInt("ID_PRODUCTO")
                    );

                    item.setCodigo(
                            rs.getString("CODIGO")
                    );

                    item.setNombre(
                            rs.getString("NOMBRE")
                    );

                    item.setTipo(
                            rs.getString("TIPO")
                    );

                    item.setUnidadMedida(
                            rs.getString("UNIDAD_MEDIDA")
                    );

                    item.setExistencia(
                            rs.getDouble("EXISTENCIA")
                    );

                    item.setExistenciaMinima(
                            rs.getDouble("EXISTENCIA_MINIMA")
                    );

                    item.setEstadoInventario(
                            rs.getString("ESTADO_INVENTARIO")
                    );

                    inventario.add(item);
                }
            }
        }

        return inventario;
    }


    // =========================================
    // CONSULTAR MOVIMIENTOS
    // =========================================
    public List<MovimientoInventario> consultarMovimientos(
            int idProducto) throws Exception {

        List<MovimientoInventario> movimientos =
                new ArrayList<>();

        String sql =
                "{ call PKG_INVENTARIO.CONSULTAR_MOVIMIENTOS(?, ?) }";

        try (
            Connection conexion =
                    ConexionBD.obtenerConexion();

            CallableStatement cs =
                    conexion.prepareCall(sql)
        ) {

            cs.setInt(1, idProducto);

            cs.registerOutParameter(
                    2,
                    OracleTypes.CURSOR
            );

            cs.execute();

            try (
                ResultSet rs =
                        (ResultSet) cs.getObject(2)
            ) {

                while (rs.next()) {

                    MovimientoInventario movimiento =
                            new MovimientoInventario();

                    movimiento.setIdMovimiento(
                            rs.getInt("ID_MOVIMIENTO")
                    );

                    movimiento.setProducto(
                            rs.getString("PRODUCTO")
                    );

                    movimiento.setTipo(
                            rs.getString("TIPO")
                    );

                    movimiento.setCantidad(
                            rs.getDouble("CANTIDAD")
                    );

                    movimiento.setSaldoAnterior(
                            rs.getDouble("SALDO_ANTERIOR")
                    );

                    movimiento.setSaldoNuevo(
                            rs.getDouble("SALDO_NUEVO")
                    );

                    movimiento.setFecha(
                            rs.getTimestamp("FECHA")
                    );

                    movimientos.add(movimiento);
                }
            }
        }

        return movimientos;
    }


    // =========================================
    // REGISTRAR ENTRADA
    // =========================================
    public RespuestaOperacion registrarEntrada(
            EntradaInventarioRequest request, int idUsuario)
            throws Exception {

        if (request == null || idUsuario <= 0) {
            throw new IllegalArgumentException("Solicitud o usuario invalido");
        }

        String sql =
                "{ call PKG_INVENTARIO.REGISTRAR_ENTRADA"
                + "(?, ?, ?, ?) }";

        Connection conexion = null;
        CallableStatement cs = null;

        try {

            conexion =
                    ConexionBD.obtenerConexion();

            conexion.setAutoCommit(false);

            cs = conexion.prepareCall(sql);

            cs.setInt(
                    1,
                    request.getIdProducto()
            );

            cs.setDouble(
                    2,
                    request.getCantidad()
            );

            cs.setInt(
                    3,
                    idUsuario
            );

            cs.registerOutParameter(
                    4,
                    Types.VARCHAR
            );

            cs.execute();

            String mensaje =
                    cs.getString(4);

            boolean exito =
                    "Entrada registrada correctamente"
                            .equals(mensaje);

            if (exito) {

                conexion.commit();

                return new RespuestaOperacion(
                        true,
                        request.getIdProducto(),
                        mensaje
                );

            } else {

                conexion.rollback();

                return new RespuestaOperacion(
                        false,
                        request.getIdProducto(),
                        mensaje
                );
            }

        } catch (Exception e) {

            if (conexion != null) {
                try {
                    conexion.rollback();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            throw e;

        } finally {

            if (cs != null) {
                try {
                    cs.close();
                } catch (Exception e) {
                }
            }

            if (conexion != null) {
                try {
                    conexion.close();
                } catch (Exception e) {
                }
            }
        }
    }


    // =========================================
    // REGISTRAR AJUSTE
    // =========================================
    public RespuestaOperacion registrarAjuste(
            AjusteInventarioRequest request, int idUsuario)
            throws Exception {

        if (request == null || idUsuario <= 0) {
            throw new IllegalArgumentException("Solicitud o usuario invalido");
        }

        String sql =
                "{ call PKG_INVENTARIO.REGISTRAR_AJUSTE"
                + "(?, ?, ?, ?) }";

        Connection conexion = null;
        CallableStatement cs = null;

        try {

            conexion =
                    ConexionBD.obtenerConexion();

            conexion.setAutoCommit(false);

            cs = conexion.prepareCall(sql);

            cs.setInt(
                    1,
                    request.getIdProducto()
            );

            cs.setDouble(
                    2,
                    request.getNuevaExistencia()
            );

            cs.setInt(
                    3,
                    idUsuario
            );

            cs.registerOutParameter(
                    4,
                    Types.VARCHAR
            );

            cs.execute();

            String mensaje =
                    cs.getString(4);

            boolean exito =
                    "Ajuste registrado correctamente"
                            .equals(mensaje);

            if (exito) {

                conexion.commit();

                return new RespuestaOperacion(
                        true,
                        request.getIdProducto(),
                        mensaje
                );

            } else {

                conexion.rollback();

                return new RespuestaOperacion(
                        false,
                        request.getIdProducto(),
                        mensaje
                );
            }

        } catch (Exception e) {

            if (conexion != null) {
                try {
                    conexion.rollback();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            throw e;

        } finally {

            if (cs != null) {
                try {
                    cs.close();
                } catch (Exception e) {
                }
            }

            if (conexion != null) {
                try {
                    conexion.close();
                } catch (Exception e) {
                }
            }
        }
    }
}