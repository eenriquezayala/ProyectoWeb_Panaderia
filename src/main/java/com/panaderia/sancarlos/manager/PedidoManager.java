package com.panaderia.sancarlos.manager;

import com.panaderia.sancarlos.model.AgregarProductoPedidoRequest;
import com.panaderia.sancarlos.model.CrearPedidoRequest;
import com.panaderia.sancarlos.model.DetallePedido;
import com.panaderia.sancarlos.model.Pedido;
import com.panaderia.sancarlos.model.RespuestaOperacion;
import com.panaderia.sancarlos.utils.ConexionBD;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import oracle.jdbc.OracleTypes;

/**
 * Gestiona pedidos. Para SUCURSAL, toda lectura se filtra en SQL y
 * toda escritura comprueba la pertenencia en la MISMA transaccion.
 */
public class PedidoManager {

    // ADMINISTRADOR y PRODUCCION: pueden consultar todos.
    public List<Pedido> consultarPedidos() throws Exception {
        String sql = "{ call PKG_PEDIDO.CONSULTAR_PEDIDOS(?) }";
        try (Connection cn = ConexionBD.obtenerConexion();
                CallableStatement cs = cn.prepareCall(sql)) {
            cs.registerOutParameter(1, OracleTypes.CURSOR);
            cs.execute();
            try (ResultSet rs = (ResultSet) cs.getObject(1)) {
                return leerPedidos(rs);
            }
        }
    }

    // SUCURSAL: filtra en Oracle ANTES de enviar datos al cliente.
    public List<Pedido> consultarPedidosSucursal(int idSucursal) throws Exception {
        String sql = "SELECT PE.ID_PEDIDO, S.NOMBRE AS SUCURSAL, "
                + "PE.FECHA_PEDIDO, PE.FECHA_REQUERIDA, PE.ESTADO, PE.OBSERVACIONES "
                + "FROM PEDIDO PE INNER JOIN SUCURSAL S "
                + "ON PE.ID_SUCURSAL = S.ID_SUCURSAL "
                + "WHERE PE.ID_SUCURSAL = ? ORDER BY PE.ID_PEDIDO DESC";
        try (Connection cn = ConexionBD.obtenerConexion();
                PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, idSucursal);
            try (ResultSet rs = ps.executeQuery()) {
                return leerPedidos(rs);
            }
        }
    }

    private List<Pedido> leerPedidos(ResultSet rs) throws SQLException {
        List<Pedido> lista = new ArrayList<>();
        while (rs.next()) {
            Pedido p = new Pedido();
            p.setIdPedido(rs.getInt("ID_PEDIDO"));
            p.setSucursal(rs.getString("SUCURSAL"));
            if (rs.getTimestamp("FECHA_PEDIDO") != null) {
                p.setFechaPedido(rs.getTimestamp("FECHA_PEDIDO").toString());
            }
            if (rs.getDate("FECHA_REQUERIDA") != null) {
                p.setFechaRequerida(rs.getDate("FECHA_REQUERIDA").toString());
            }
            p.setEstado(rs.getString("ESTADO"));
            p.setObservaciones(rs.getString("OBSERVACIONES"));
            lista.add(p);
        }
        return lista;
    }

    // ADMINISTRADOR y PRODUCCION: pueden consultar cualquier detalle.
    public List<DetallePedido> consultarDetalle(int idPedido) throws Exception {
        String sql = "{ call PKG_PEDIDO.CONSULTAR_DETALLE(?, ?) }";
        try (Connection cn = ConexionBD.obtenerConexion();
                CallableStatement cs = cn.prepareCall(sql)) {
            cs.setInt(1, idPedido);
            cs.registerOutParameter(2, OracleTypes.CURSOR);
            cs.execute();
            try (ResultSet rs = (ResultSet) cs.getObject(2)) {
                return leerDetalles(rs);
            }
        }
    }

    // SUCURSAL: comprueba pertenencia y devuelve solo detalle del pedido propio.
    public List<DetallePedido> consultarDetalleSucursal(
            int idPedido, int idSucursal) throws Exception {
        String sqlComprobar = "SELECT 1 FROM PEDIDO "
                + "WHERE ID_PEDIDO = ? AND ID_SUCURSAL = ?";
        String sqlDetalle = "SELECT P.NOMBRE AS PRODUCTO, "
                + "D.CANTIDAD_MANANA, D.CANTIDAD_RESTO, D.CANTIDAD_TARDE, "
                + "(D.CANTIDAD_MANANA + D.CANTIDAD_RESTO + D.CANTIDAD_TARDE) AS TOTAL "
                + "FROM DETALLE_PEDIDO D "
                + "INNER JOIN PEDIDO PE ON PE.ID_PEDIDO = D.ID_PEDIDO "
                + "INNER JOIN PRODUCTO P ON P.ID_PRODUCTO = D.ID_PRODUCTO "
                + "WHERE D.ID_PEDIDO = ? AND PE.ID_SUCURSAL = ? "
                + "ORDER BY P.NOMBRE";
        try (Connection cn = ConexionBD.obtenerConexion()) {
            try (PreparedStatement ps = cn.prepareStatement(sqlComprobar)) {
                ps.setInt(1, idPedido);
                ps.setInt(2, idSucursal);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new NoSuchElementException(
                                "Pedido no encontrado en su sucursal");
                    }
                }
            }
            try (PreparedStatement ps = cn.prepareStatement(sqlDetalle)) {
                ps.setInt(1, idPedido);
                ps.setInt(2, idSucursal);
                try (ResultSet rs = ps.executeQuery()) {
                    return leerDetalles(rs);
                }
            }
        }
    }

    private List<DetallePedido> leerDetalles(ResultSet rs) throws SQLException {
        List<DetallePedido> detalles = new ArrayList<>();
        while (rs.next()) {
            DetallePedido d = new DetallePedido();
            d.setProducto(rs.getString("PRODUCTO"));
            d.setCantidadManana(rs.getInt("CANTIDAD_MANANA"));
            d.setCantidadResto(rs.getInt("CANTIDAD_RESTO"));
            d.setCantidadTarde(rs.getInt("CANTIDAD_TARDE"));
            d.setTotal(rs.getInt("TOTAL"));
            detalles.add(d);
        }
        return detalles;
    }

    // Nunca utilizar idUsuario o idSucursal proporcionados por el cuerpo JSON.
    public RespuestaOperacion crearPedido(
            CrearPedidoRequest request, int idUsuario, int idSucursal)
            throws Exception {
        if (request == null || idUsuario <= 0 || idSucursal <= 0
                || request.getFechaRequerida() == null) {
            throw new IllegalArgumentException(
                    "Debe indicar una sucursal y fechaRequerida validas");
        }
        final java.sql.Date fecha;
        try {
            fecha = java.sql.Date.valueOf(request.getFechaRequerida());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "fechaRequerida debe tener formato yyyy-MM-dd");
        }

        String sql = "{ call PKG_PEDIDO.CREAR_PEDIDO(?, ?, ?, ?, ?, ?) }";
        try (Connection cn = ConexionBD.obtenerConexion()) {
            cn.setAutoCommit(false);
            try {
                try (CallableStatement cs = cn.prepareCall(sql)) {
                    cs.setInt(1, idSucursal);
                    cs.setInt(2, idUsuario);
                    cs.setDate(3, fecha);
                    cs.setString(4, request.getObservaciones());
                    cs.registerOutParameter(5, Types.NUMERIC);
                    cs.registerOutParameter(6, Types.VARCHAR);
                    cs.execute();
                    int idCreado = cs.getInt(5);
                    String mensaje = cs.getString(6);
                    if (idCreado > 0) {
                        cn.commit();
                        return new RespuestaOperacion(true, idCreado, mensaje);
                    }
                    cn.rollback();
                    return new RespuestaOperacion(false, 0, mensaje);
                }
            } catch (Exception e) {
                cn.rollback();
                throw e;
            }
        }
    }

    public RespuestaOperacion agregarProducto(
            int idPedido, AgregarProductoPedidoRequest request,
            Integer idSucursalPermitida) throws Exception {
        if (idPedido <= 0 || request == null) {
            throw new IllegalArgumentException("Pedido y producto obligatorios");
        }
        String sql = "{ call PKG_PEDIDO.AGREGAR_PRODUCTO(?, ?, ?, ?, ?, ?) }";
        try (Connection cn = ConexionBD.obtenerConexion()) {
            cn.setAutoCommit(false);
            try {
                // Bloqueo de la fila antes de ejecutar el procedimiento PL/SQL.
                bloquearPedido(cn, idPedido, idSucursalPermitida);
                try (CallableStatement cs = cn.prepareCall(sql)) {
                    cs.setInt(1, idPedido);
                    cs.setInt(2, request.getIdProducto());
                    cs.setInt(3, request.getManana());
                    cs.setInt(4, request.getResto());
                    cs.setInt(5, request.getTarde());
                    cs.registerOutParameter(6, Types.VARCHAR);
                    cs.execute();
                    String mensaje = cs.getString(6);
                    boolean exito = "Producto agregado correctamente".equals(mensaje);
                    if (exito) cn.commit(); else cn.rollback();
                    return new RespuestaOperacion(exito, idPedido, mensaje);
                }
            } catch (Exception e) {
                cn.rollback();
                throw e;
            }
        }
    }

    public RespuestaOperacion enviarPedido(
            int idPedido, Integer idSucursalPermitida) throws Exception {
        if (idPedido <= 0) {
            throw new IllegalArgumentException("ID de pedido invalido");
        }
        String sql = "{ call PKG_PEDIDO.ENVIAR_PEDIDO(?, ?) }";
        try (Connection cn = ConexionBD.obtenerConexion()) {
            cn.setAutoCommit(false);
            try {
                bloquearPedido(cn, idPedido, idSucursalPermitida);
                try (CallableStatement cs = cn.prepareCall(sql)) {
                    cs.setInt(1, idPedido);
                    cs.registerOutParameter(2, Types.VARCHAR);
                    cs.execute();
                    String mensaje = cs.getString(2);
                    boolean exito = "Pedido enviado correctamente".equals(mensaje);
                    if (exito) cn.commit(); else cn.rollback();
                    return new RespuestaOperacion(exito, idPedido, mensaje);
                }
            } catch (Exception e) {
                cn.rollback();
                throw e;
            }
        }
    }

    /**
     * Bloquea el pedido en Oracle hasta el COMMIT/ROLLBACK.
     * Para SUCURSAL, solo se obtiene la fila si pertenece a esa sucursal.
     * Un pedido ajeno y uno inexistente se tratan igual: HTTP 404.
     */
    private void bloquearPedido(Connection cn, int idPedido,
            Integer idSucursalPermitida) throws SQLException {
        String sql = idSucursalPermitida == null
                ? "SELECT ID_PEDIDO FROM PEDIDO WHERE ID_PEDIDO = ? FOR UPDATE"
                : "SELECT ID_PEDIDO FROM PEDIDO "
                + "WHERE ID_PEDIDO = ? AND ID_SUCURSAL = ? FOR UPDATE";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            if (idSucursalPermitida != null) {
                ps.setInt(2, idSucursalPermitida);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new NoSuchElementException(
                            "Pedido no encontrado en su sucursal");
                }
            }
        }
    }
}
