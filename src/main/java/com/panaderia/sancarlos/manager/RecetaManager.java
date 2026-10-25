package com.panaderia.sancarlos.manager;

import com.panaderia.sancarlos.model.AgregarIngredienteRequest;
import com.panaderia.sancarlos.model.CrearRecetaRequest;
import com.panaderia.sancarlos.model.DetalleReceta;
import com.panaderia.sancarlos.model.Receta;
import com.panaderia.sancarlos.model.RespuestaOperacion;
import com.panaderia.sancarlos.utils.ConexionBD;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Types;

import java.util.ArrayList;
import java.util.List;

import oracle.jdbc.OracleTypes;

public class RecetaManager {

    // =========================================
    // CONSULTAR RECETAS
    // =========================================
    public List<Receta> consultarRecetas()
            throws Exception {

        List<Receta> recetas = new ArrayList<>();

        String sql =
                "{ call PKG_RECETA.CONSULTAR_RECETAS(?) }";

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

                    Receta receta = new Receta();

                    receta.setIdReceta(
                            rs.getInt("ID_RECETA")
                    );

                    receta.setNombre(
                            rs.getString("RECETA")
                    );

                    receta.setProducto(
                            rs.getString("PRODUCTO")
                    );

                    receta.setRendimiento(
                            rs.getDouble("RENDIMIENTO")
                    );

                    receta.setDescripcion(
                            rs.getString("DESCRIPCION")
                    );

                    recetas.add(receta);
                }
            }
        }

        return recetas;
    }


    // =========================================
    // CONSULTAR DETALLE DE RECETA
    // =========================================
    public List<DetalleReceta> consultarDetalle(
            int idReceta) throws Exception {

        List<DetalleReceta> detalles =
                new ArrayList<>();

        String sql =
                "{ call PKG_RECETA.CONSULTAR_DETALLE(?, ?) }";

        try (
            Connection conexion =
                    ConexionBD.obtenerConexion();

            CallableStatement cs =
                    conexion.prepareCall(sql)
        ) {

            cs.setInt(1, idReceta);

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

                    DetalleReceta detalle =
                            new DetalleReceta();

                    detalle.setInsumo(
                            rs.getString("INSUMO")
                    );

                    detalle.setCantidad(
                            rs.getDouble("CANTIDAD")
                    );

                    detalle.setUnidadMedida(
                            rs.getString("UNIDAD_MEDIDA")
                    );

                    detalles.add(detalle);
                }
            }
        }

        return detalles;
    }


    // =========================================
    // CREAR RECETA
    // =========================================
    public RespuestaOperacion crearReceta(
            CrearRecetaRequest request)
            throws Exception {

        String sql =
                "{ call PKG_RECETA.CREAR_RECETA"
                + "(?, ?, ?, ?, ?, ?) }";

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

            cs.setString(
                    2,
                    request.getNombre()
            );

            cs.setDouble(
                    3,
                    request.getRendimiento()
            );

            cs.setString(
                    4,
                    request.getDescripcion()
            );

            cs.registerOutParameter(
                    5,
                    Types.NUMERIC
            );

            cs.registerOutParameter(
                    6,
                    Types.VARCHAR
            );

            cs.execute();

            int idSalida = cs.getInt(5);
            String mensaje = cs.getString(6);

            if (idSalida > 0) {

                conexion.commit();

                return new RespuestaOperacion(
                        true,
                        idSalida,
                        mensaje
                );

            } else {

                conexion.rollback();

                return new RespuestaOperacion(
                        false,
                        0,
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
    // AGREGAR INGREDIENTE
    // =========================================
    public RespuestaOperacion agregarIngrediente(
            int idReceta,
            AgregarIngredienteRequest request)
            throws Exception {

        String sql =
                "{ call PKG_RECETA.AGREGAR_INGREDIENTE"
                + "(?, ?, ?, ?, ?) }";

        Connection conexion = null;
        CallableStatement cs = null;

        try {

            conexion =
                    ConexionBD.obtenerConexion();

            conexion.setAutoCommit(false);

            cs = conexion.prepareCall(sql);

            cs.setInt(
                    1,
                    idReceta
            );

            cs.setInt(
                    2,
                    request.getIdInsumo()
            );

            cs.setDouble(
                    3,
                    request.getCantidad()
            );

            cs.registerOutParameter(
                    4,
                    Types.NUMERIC
            );

            cs.registerOutParameter(
                    5,
                    Types.VARCHAR
            );

            cs.execute();

            int idSalida = cs.getInt(4);
            String mensaje = cs.getString(5);

            if (idSalida > 0) {

                conexion.commit();

                return new RespuestaOperacion(
                        true,
                        idSalida,
                        mensaje
                );

            } else {

                conexion.rollback();

                return new RespuestaOperacion(
                        false,
                        0,
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