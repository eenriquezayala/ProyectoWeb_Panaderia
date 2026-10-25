package com.panaderia.sancarlos.manager;

import com.panaderia.sancarlos.utils.ConexionBD;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import oracle.jdbc.OracleTypes;

public class ReportesManager {

    // Reutilizamos Map porque los reportes son solo de lectura,
    // y cada consulta devuelve columnas diferentes.
    private List<Map<String, Object>> convertir(ResultSet rs)
            throws SQLException {

        List<Map<String, Object>> filas = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();

        while (rs.next()) {
            Map<String, Object> fila = new LinkedHashMap<>();

            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String clave = aCamelCase(meta.getColumnLabel(i));
                Object valor = rs.getObject(i);

                if (valor instanceof Timestamp) {
                    valor = valor.toString();
                } else if (valor instanceof java.sql.Date) {
                    valor = valor.toString();
                }

                fila.put(clave, valor);
            }

            filas.add(fila);
        }
        return filas;
    }

    private String aCamelCase(String columna) {
        String[] partes = columna.toLowerCase(Locale.ROOT).split("_");
        StringBuilder salida = new StringBuilder(partes[0]);

        for (int i = 1; i < partes.length; i++) {
            if (!partes[i].isEmpty()) {
                salida.append(Character.toUpperCase(partes[i].charAt(0)));
                salida.append(partes[i].substring(1));
            }
        }
        return salida.toString();
    }

    private List<Map<String, Object>> consultarFechas(
            String procedimiento, String desde, String hasta)
            throws Exception {

        if (desde == null || hasta == null
                || desde.trim().isEmpty() || hasta.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Debe enviar desde y hasta en formato yyyy-MM-dd");
        }

        LocalDate fechaDesde;
        LocalDate fechaHasta;
        try {
            fechaDesde = LocalDate.parse(desde.trim());
            fechaHasta = LocalDate.parse(hasta.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                "Las fechas deben tener formato yyyy-MM-dd", e);
        }

        if (fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException(
                "La fecha desde no puede ser posterior a hasta");
        }

        // procedimiento solo recibe nombres constantes definidos abajo.
        String sql = "{ call PKG_REPORTES." + procedimiento + "(?, ?, ?) }";

        try (
            Connection conexion = ConexionBD.obtenerConexion();
            CallableStatement cs = conexion.prepareCall(sql)
        ) {
            cs.setDate(1, java.sql.Date.valueOf(fechaDesde));
            cs.setDate(2, java.sql.Date.valueOf(fechaHasta));
            cs.registerOutParameter(3, OracleTypes.CURSOR);
            cs.execute();

            try (ResultSet rs = (ResultSet) cs.getObject(3)) {
                return convertir(rs);
            }
        }
    }

    public List<Map<String, Object>> pedidosPorSucursal(
            String desde, String hasta) throws Exception {
        return consultarFechas("CONSULTAR_PEDIDOS_SUCURSAL", desde, hasta);
    }

    public List<Map<String, Object>> panesMasSolicitados(
            String desde, String hasta) throws Exception {
        return consultarFechas("CONSULTAR_PANES_SOLICITADOS", desde, hasta);
    }

    public List<Map<String, Object>> produccionDiaria(
            String desde, String hasta) throws Exception {
        return consultarFechas("CONSULTAR_PRODUCCION_DIARIA", desde, hasta);
    }

    public List<Map<String, Object>> despachosPorSucursal(
            String desde, String hasta) throws Exception {
        return consultarFechas("CONSULTAR_DESPACHOS_SUCURSAL", desde, hasta);
    }

    public List<Map<String, Object>> inventarioBajo() throws Exception {
        String sql = "{ call PKG_REPORTES.CONSULTAR_INVENTARIO_BAJO(?) }";

        try (
            Connection conexion = ConexionBD.obtenerConexion();
            CallableStatement cs = conexion.prepareCall(sql)
        ) {
            cs.registerOutParameter(1, OracleTypes.CURSOR);
            cs.execute();

            try (ResultSet rs = (ResultSet) cs.getObject(1)) {
                return convertir(rs);
            }
        }
    }
}
