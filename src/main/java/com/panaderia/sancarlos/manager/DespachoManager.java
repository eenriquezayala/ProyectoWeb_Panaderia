
package com.panaderia.sancarlos.manager;

import com.panaderia.sancarlos.model.CrearDespachoRequest;
import com.panaderia.sancarlos.model.Despacho;
import com.panaderia.sancarlos.model.DetalleDespacho;
import com.panaderia.sancarlos.model.RespuestaOperacion;
import com.panaderia.sancarlos.utils.ConexionBD;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import oracle.jdbc.OracleTypes;

public class DespachoManager {

    // =========================================
    // CONSULTAR DESPACHOS
    // =========================================
    public List<Despacho> consultarDespachos()
            throws Exception {

        List<Despacho> lista = new ArrayList<>();

        String sql =
            "{ call PKG_DESPACHO.CONSULTAR_DESPACHOS(?) }";

        try (
            Connection conexion = ConexionBD.obtenerConexion();
            CallableStatement cs = conexion.prepareCall(sql)
        ) {

            cs.registerOutParameter(1, OracleTypes.CURSOR);
            cs.execute();

            try (ResultSet rs = (ResultSet) cs.getObject(1)) {

                while (rs.next()) {

                    Despacho despacho = new Despacho();

                    despacho.setIdDespacho(
                        rs.getInt("ID_DESPACHO")
                    );

                    despacho.setIdPedido(
                        rs.getInt("ID_PEDIDO")
                    );

                    despacho.setSucursal(
                        rs.getString("SUCURSAL")
                    );

                    Timestamp fecha =
                        rs.getTimestamp("FECHA_DESPACHO");

                    despacho.setFechaDespacho(
                        fecha != null ? fecha.toString() : null
                    );

                    despacho.setEstado(
                        rs.getString("ESTADO")
                    );

                    despacho.setObservaciones(
                        rs.getString("OBSERVACIONES")
                    );

                    lista.add(despacho);
                }
            }
        }

        return lista;
    }

    // =========================================
    // CONSULTAR DETALLE
    // =========================================
    public List<DetalleDespacho> consultarDetalle(
            int idDespacho) throws Exception {

        List<DetalleDespacho> lista = new ArrayList<>();

        String sql =
            "{ call PKG_DESPACHO.CONSULTAR_DETALLE(?, ?) }";

        try (
            Connection conexion = ConexionBD.obtenerConexion();
            CallableStatement cs = conexion.prepareCall(sql)
        ) {

            cs.setInt(1, idDespacho);

            cs.registerOutParameter(2, OracleTypes.CURSOR);
            cs.execute();

            try (ResultSet rs = (ResultSet) cs.getObject(2)) {

                while (rs.next()) {

                    DetalleDespacho detalle =
                        new DetalleDespacho();

                    detalle.setProducto(
                        rs.getString("PRODUCTO")
                    );

                    detalle.setCantidad(
                        rs.getInt("CANTIDAD")
                    );

                    lista.add(detalle);
                }
            }
        }

        return lista;
    }

    // =========================================
    // CONSULTAR DESPACHOS DE UNA SUCURSAL
    // =========================================
    public List<Despacho> consultarDespachosSucursal(
            int idSucursal) throws Exception {

        if (idSucursal <= 0) {
            throw new IllegalArgumentException("Sucursal invalida");
        }

        List<Despacho> lista = new ArrayList<>();
        // Filtrar desde Oracle, NO traer todos y filtrar en Java.
        String sql =
                "SELECT D.ID_DESPACHO, D.ID_PEDIDO, "
                + "S.NOMBRE AS SUCURSAL, D.FECHA_DESPACHO, "
                + "D.ESTADO, D.OBSERVACIONES "
                + "FROM DESPACHO D "
                + "INNER JOIN PEDIDO P ON D.ID_PEDIDO = P.ID_PEDIDO "
                + "INNER JOIN SUCURSAL S ON P.ID_SUCURSAL = S.ID_SUCURSAL "
                + "WHERE P.ID_SUCURSAL = ? "
                + "ORDER BY D.ID_DESPACHO DESC";

        try (Connection conexion = ConexionBD.obtenerConexion();
                java.sql.PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idSucursal);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Despacho d = new Despacho();
                    d.setIdDespacho(rs.getInt("ID_DESPACHO"));
                    d.setIdPedido(rs.getInt("ID_PEDIDO"));
                    d.setSucursal(rs.getString("SUCURSAL"));
                    Timestamp fecha = rs.getTimestamp("FECHA_DESPACHO");
                    d.setFechaDespacho(fecha == null ? null : fecha.toString());
                    d.setEstado(rs.getString("ESTADO"));
                    d.setObservaciones(rs.getString("OBSERVACIONES"));
                    lista.add(d);
                }
            }
        }
        return lista;
    }

    // =========================================
    // CONSULTAR DETALLE SOLO SI PERTENECE A LA SUCURSAL
    // =========================================
    public List<DetalleDespacho> consultarDetalleSucursal(
            int idDespacho, int idSucursal) throws Exception {

        if (idDespacho <= 0 || idSucursal <= 0) {
            throw new IllegalArgumentException("Identificadores invalidos");
        }

        String sqlPermiso =
                "SELECT 1 FROM DESPACHO D "
                + "INNER JOIN PEDIDO P ON D.ID_PEDIDO = P.ID_PEDIDO "
                + "WHERE D.ID_DESPACHO = ? AND P.ID_SUCURSAL = ?";

        String sqlDetalle =
                "SELECT P.NOMBRE AS PRODUCTO, DD.CANTIDAD "
                + "FROM DETALLE_DESPACHO DD "
                + "INNER JOIN PRODUCTO P ON DD.ID_PRODUCTO = P.ID_PRODUCTO "
                + "WHERE DD.ID_DESPACHO = ? ORDER BY P.NOMBRE";

        List<DetalleDespacho> lista = new ArrayList<>();
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            try (java.sql.PreparedStatement permiso =
                    conexion.prepareStatement(sqlPermiso)) {
                permiso.setInt(1, idDespacho);
                permiso.setInt(2, idSucursal);
                try (ResultSet rs = permiso.executeQuery()) {
                    if (!rs.next()) {
                        // Igual respuesta para inexistente y ajeno (404).
                        throw new NoSuchElementException("Despacho no encontrado");
                    }
                }
            }

            try (java.sql.PreparedStatement detalle =
                    conexion.prepareStatement(sqlDetalle)) {
                detalle.setInt(1, idDespacho);
                try (ResultSet rs = detalle.executeQuery()) {
                    while (rs.next()) {
                        DetalleDespacho d = new DetalleDespacho();
                        d.setProducto(rs.getString("PRODUCTO"));
                        d.setCantidad(rs.getInt("CANTIDAD"));
                        lista.add(d);
                    }
                }
            }
        }
        return lista;
    }

    // =========================================
    // REGISTRAR DESPACHO
    // =========================================
    public RespuestaOperacion registrarDespacho(
            CrearDespachoRequest request, int idUsuarioAutenticado)
            throws Exception {

        if (request == null ||
            request.getIdPedido() <= 0 ||
            idUsuarioAutenticado <= 0) {

            throw new IllegalArgumentException(
                "Debe indicar un pedido y usuario validos"
            );
        }

        String sql =
            "{ call PKG_DESPACHO.REGISTRAR_DESPACHO"
            + "(?, ?, ?, ?, ?) }";

        try (Connection conexion =
                ConexionBD.obtenerConexion()) {

            conexion.setAutoCommit(false);

            try (CallableStatement cs =
                    conexion.prepareCall(sql)) {

                cs.setInt(1, request.getIdPedido());
                cs.setInt(2, idUsuarioAutenticado);
                cs.setString(3, request.getObservaciones());

                cs.registerOutParameter(4, Types.NUMERIC);
                cs.registerOutParameter(5, Types.VARCHAR);

                cs.execute();

                int idSalida = cs.getInt(4);
                String mensaje = cs.getString(5);

                boolean exito =
                    idSalida > 0 &&
                    "Despacho registrado correctamente"
                        .equals(mensaje);

                if (exito) {
                    conexion.commit();
                } else {
                    conexion.rollback();
                }

                return new RespuestaOperacion(
                    exito,
                    exito ? idSalida : 0,
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

    // =========================================
    // MARCAR DESPACHO COMO RECIBIDO
    // =========================================
    public RespuestaOperacion marcarRecibido(
            int idDespacho) throws Exception {

        if (idDespacho <= 0) {
            throw new IllegalArgumentException(
                "El ID del despacho debe ser mayor a cero"
            );
        }

        String sql =
            "{ call PKG_DESPACHO.MARCAR_RECIBIDO(?, ?) }";

        try (Connection conexion =
                ConexionBD.obtenerConexion()) {

            conexion.setAutoCommit(false);

            try (CallableStatement cs =
                    conexion.prepareCall(sql)) {

                cs.setInt(1, idDespacho);

                cs.registerOutParameter(2, Types.VARCHAR);

                cs.execute();

                String mensaje = cs.getString(2);

                boolean exito =
                    "Despacho recibido correctamente"
                        .equals(mensaje);

                if (exito) {
                    conexion.commit();
                } else {
                    conexion.rollback();
                }

                return new RespuestaOperacion(
                    exito,
                    idDespacho,
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
}
