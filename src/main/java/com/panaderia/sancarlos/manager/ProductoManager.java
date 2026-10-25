package com.panaderia.sancarlos.manager;

import com.panaderia.sancarlos.model.Producto;
import com.panaderia.sancarlos.utils.ConexionBD;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Types;

import java.util.ArrayList;
import java.util.List;
import com.panaderia.sancarlos.model.RespuestaOperacion;
import oracle.jdbc.OracleTypes;

/**
 *
 * @author edwar
 */
public class ProductoManager {

    public List<Producto> consultarProductos(String tipo) throws Exception {

        List<Producto> productos = new ArrayList<>();

        String sql
                = "{ call PKG_PRODUCTO.CONSULTAR_PRODUCTOS(?, ?) }";

        try (
                Connection conexion = ConexionBD.obtenerConexion(); CallableStatement cs = conexion.prepareCall(sql)) {

            if (tipo == null || tipo.trim().isEmpty()) {
                cs.setNull(1, Types.VARCHAR);
            } else {
                cs.setString(1, tipo);
            }

            cs.registerOutParameter(
                    2,
                    OracleTypes.CURSOR
            );

            cs.execute();

            try (
                    ResultSet rs
                    = (ResultSet) cs.getObject(2)) {

                while (rs.next()) {

                    Producto producto = new Producto();

                    producto.setIdProducto(
                            rs.getInt("ID_PRODUCTO")
                    );

                    producto.setCodigo(
                            rs.getString("CODIGO")
                    );

                    producto.setNombre(
                            rs.getString("NOMBRE")
                    );

                    producto.setTipo(
                            rs.getString("TIPO")
                    );

                    producto.setUnidadMedida(
                            rs.getString("UNIDAD_MEDIDA")
                    );

                    producto.setExistenciaMinima(
                            rs.getDouble("EXISTENCIA_MINIMA")
                    );

                    producto.setEstado(
                            rs.getInt("ESTADO")
                    );

                    producto.setExistencia(
                            rs.getDouble("EXISTENCIA")
                    );

                    productos.add(producto);
                }
            }
        }

        return productos;
    }

    public Producto consultarProducto(int idProducto) throws Exception {

        String sql
                = "{ call PKG_PRODUCTO.CONSULTAR_PRODUCTO(?, ?) }";

        try (
                Connection conexion = ConexionBD.obtenerConexion(); CallableStatement cs = conexion.prepareCall(sql)) {

            cs.setInt(1, idProducto);

            cs.registerOutParameter(
                    2,
                    OracleTypes.CURSOR
            );

            cs.execute();

            try (
                    ResultSet rs
                    = (ResultSet) cs.getObject(2)) {

                if (rs.next()) {

                    Producto producto = new Producto();

                    producto.setIdProducto(
                            rs.getInt("ID_PRODUCTO")
                    );

                    producto.setCodigo(
                            rs.getString("CODIGO")
                    );

                    producto.setNombre(
                            rs.getString("NOMBRE")
                    );

                    producto.setTipo(
                            rs.getString("TIPO")
                    );

                    producto.setUnidadMedida(
                            rs.getString("UNIDAD_MEDIDA")
                    );

                    producto.setExistenciaMinima(
                            rs.getDouble("EXISTENCIA_MINIMA")
                    );

                    producto.setEstado(
                            rs.getInt("ESTADO")
                    );

                    producto.setExistencia(
                            rs.getDouble("EXISTENCIA")
                    );

                    return producto;
                }
            }
        }

        return null;
    }

    public RespuestaOperacion crearProducto(
            Producto producto) throws Exception {

        String sql
                = "{ call PKG_PRODUCTO.CREAR_PRODUCTO(?, ?, ?, ?, ?, ?, ?) }";

        Connection conexion = null;
        CallableStatement cs = null;

        try {

            conexion = ConexionBD.obtenerConexion();

            // Java controlará la transacción
            conexion.setAutoCommit(false);

            cs = conexion.prepareCall(sql);

            cs.setString(
                    1,
                    producto.getCodigo()
            );

            cs.setString(
                    2,
                    producto.getNombre()
            );

            cs.setString(
                    3,
                    producto.getTipo()
            );

            cs.setString(
                    4,
                    producto.getUnidadMedida()
            );

            cs.setDouble(
                    5,
                    producto.getExistenciaMinima()
            );

            cs.registerOutParameter(
                    6,
                    Types.NUMERIC
            );

            cs.registerOutParameter(
                    7,
                    Types.VARCHAR
            );

            cs.execute();

            int idSalida
                    = cs.getInt(6);

            String mensaje
                    = cs.getString(7);

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

    public RespuestaOperacion actualizarProducto(
            int idProducto,
            Producto producto) throws Exception {

        String sql
                = "{ call PKG_PRODUCTO.ACTUALIZAR_PRODUCTO(?, ?, ?, ?, ?, ?) }";

        Connection conexion = null;
        CallableStatement cs = null;

        try {

            conexion = ConexionBD.obtenerConexion();
            conexion.setAutoCommit(false);

            cs = conexion.prepareCall(sql);

            cs.setInt(1, idProducto);
            cs.setString(2, producto.getCodigo());
            cs.setString(3, producto.getNombre());
            cs.setString(4, producto.getUnidadMedida());
            cs.setDouble(5, producto.getExistenciaMinima());

            cs.registerOutParameter(
                    6,
                    Types.VARCHAR
            );

            cs.execute();

            String mensaje = cs.getString(6);

            boolean exito
                    = "Producto actualizado correctamente"
                            .equals(mensaje);

            if (exito) {

                conexion.commit();

                return new RespuestaOperacion(
                        true,
                        idProducto,
                        mensaje
                );

            } else {

                conexion.rollback();

                return new RespuestaOperacion(
                        false,
                        idProducto,
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

    public RespuestaOperacion cambiarEstado(
            int idProducto,
            int estado) throws Exception {

        String sql
                = "{ call PKG_PRODUCTO.CAMBIAR_ESTADO(?, ?, ?) }";

        Connection conexion = null;
        CallableStatement cs = null;

        try {

            conexion = ConexionBD.obtenerConexion();
            conexion.setAutoCommit(false);

            cs = conexion.prepareCall(sql);

            cs.setInt(1, idProducto);
            cs.setInt(2, estado);

            cs.registerOutParameter(
                    3,
                    Types.VARCHAR
            );

            cs.execute();

            String mensaje = cs.getString(3);

            boolean exito
                    = "Producto activado correctamente".equals(mensaje)
                    || "Producto desactivado correctamente".equals(mensaje);

            if (exito) {

                conexion.commit();

                return new RespuestaOperacion(
                        true,
                        idProducto,
                        mensaje
                );

            } else {

                conexion.rollback();

                return new RespuestaOperacion(
                        false,
                        idProducto,
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
