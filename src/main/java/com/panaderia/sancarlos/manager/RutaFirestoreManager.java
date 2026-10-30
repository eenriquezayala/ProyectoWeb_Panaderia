package com.panaderia.sancarlos.manager;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.panaderia.sancarlos.utils.FirebaseAdminUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldValue;
import com.panaderia.sancarlos.utils.ConexionBD;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.TimeUnit;
import com.google.cloud.firestore.DocumentSnapshot;

public class RutaFirestoreManager {

    public List<Map<String, Object>> consultarRutas()
            throws Exception {

        Firestore db = FirebaseAdminUtil.obtenerFirestore();

        List<QueryDocumentSnapshot> documentos = db
                .collection("rutas")
                .limit(50)
                .get()
                .get(15, TimeUnit.SECONDS)
                .getDocuments();

        List<Map<String, Object>> rutas
                = new ArrayList<>();

        for (QueryDocumentSnapshot documento : documentos) {

            Map<String, Object> ruta
                    = new LinkedHashMap<>();

            ruta.put("idRuta", documento.getId());

            ruta.put(
                    "idDespachoOracle",
                    documento.getLong("idDespachoOracle")
            );

            ruta.put(
                    "idVendedor",
                    documento.getLong("idVendedor")
            );

            ruta.put(
                    "idVehiculo",
                    documento.get("idVehiculo")
            );

            ruta.put(
                    "estado",
                    documento.getString("estado")
            );

            ruta.put(
                    "version",
                    documento.getLong("version")
            );

            rutas.add(ruta);
        }

        return rutas;
    }

    public String registrarRutaPendiente(int idDespachoOracle)
            throws Exception {

        if (idDespachoOracle <= 0) {
            throw new IllegalArgumentException(
                    "Identificador de despacho invalido"
            );
        }

        String sql
                = "SELECT D.ID_PEDIDO, S.NOMBRE AS SUCURSAL "
                + "FROM DESPACHO D "
                + "JOIN PEDIDO P ON P.ID_PEDIDO = D.ID_PEDIDO "
                + "JOIN SUCURSAL S ON S.ID_SUCURSAL = P.ID_SUCURSAL "
                + "WHERE D.ID_DESPACHO = ? "
                + "AND D.ESTADO = 'DESPACHADO'";

        long idPedido;
        String sucursal;

        try (Connection conexion = ConexionBD.obtenerConexion(); PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setInt(1, idDespachoOracle);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    throw new IllegalArgumentException(
                            "El despacho no existe o no esta pendiente"
                    );
                }

                idPedido = rs.getLong("ID_PEDIDO");
                sucursal = rs.getString("SUCURSAL");
            }
        }

        Firestore db = FirebaseAdminUtil.obtenerFirestore();

        String idRuta = "despacho_" + idDespachoOracle;

        DocumentReference referencia = db
                .collection("rutas")
                .document(idRuta);

        Map<String, Object> datos = new LinkedHashMap<>();

        datos.put("idDespachoOracle", (long) idDespachoOracle);
        datos.put("idPedidoOracle", idPedido);
        datos.put("sucursal", sucursal);

        datos.put("idVendedor", null);
        datos.put("idVehiculo", null);

        datos.put("estado", "PENDIENTE_ASIGNACION");
        datos.put("version", 1L);
        datos.put("actualizadoEn", FieldValue.serverTimestamp());

        // No sobrescribe una ruta que ya existe.
        referencia.create(datos).get(15, TimeUnit.SECONDS);

        return idRuta;
    }

    public List<Map<String, Object>> consultarRutasDeVendedor(
            int idUsuario) throws Exception {

        if (idUsuario <= 0) {
            throw new IllegalArgumentException(
                    "Usuario invalido"
            );
        }

        Firestore db = FirebaseAdminUtil.obtenerFirestore();

        // Buscar solamente las rutas asignadas a este usuario.
        List<QueryDocumentSnapshot> documentos = db
                .collection("rutas")
                .whereEqualTo("idVendedor", (long) idUsuario)
                .limit(50)
                .get()
                .get(15, TimeUnit.SECONDS)
                .getDocuments();

        List<Map<String, Object>> rutas = new ArrayList<>();

        for (QueryDocumentSnapshot documento : documentos) {

            Map<String, Object> ruta = new LinkedHashMap<>();

            ruta.put("idRuta", documento.getId());

            ruta.put(
                    "idDespachoOracle",
                    documento.getLong("idDespachoOracle")
            );

            ruta.put(
                    "idVendedor",
                    documento.getLong("idVendedor")
            );

            ruta.put(
                    "idVehiculo",
                    documento.get("idVehiculo")
            );

            ruta.put(
                    "estado",
                    documento.getString("estado")
            );

            ruta.put(
                    "version",
                    documento.getLong("version")
            );

            rutas.add(ruta);
        }

        return rutas;
    }

    public long asignarVendedor(
            int idDespachoOracle,
            int idVendedor,
            long versionEsperada) throws Exception {

        if (idDespachoOracle <= 0
                || idVendedor <= 0
                || versionEsperada <= 0) {
            throw new IllegalArgumentException(
                    "Datos de asignacion invalidos");
        }

        // Validar el vendedor y el despacho en Oracle.
        // Ambos deben pertenecer a la misma sucursal.
        String sql
                = "SELECT S.NOMBRE AS SUCURSAL "
                + "FROM DESPACHO D "
                + "JOIN PEDIDO P ON P.ID_PEDIDO = D.ID_PEDIDO "
                + "JOIN SUCURSAL S ON S.ID_SUCURSAL = P.ID_SUCURSAL "
                + "JOIN USUARIO U ON U.ID_SUCURSAL = S.ID_SUCURSAL "
                + "JOIN ROL R ON R.ID_ROL = U.ID_ROL "
                + "WHERE D.ID_DESPACHO = ? "
                + "AND D.ESTADO = 'DESPACHADO' "
                + "AND U.ID_USUARIO = ? "
                + "AND U.ESTADO = 1 "
                + "AND R.NOMBRE = 'VENDEDOR_RUTA' "
                + "AND R.ESTADO = 1 "
                + "AND S.ESTADO = 1";

        String sucursalOracle;

        try (Connection conexion = ConexionBD.obtenerConexion(); PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setInt(1, idDespachoOracle);
            ps.setInt(2, idVendedor);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    throw new IllegalArgumentException(
                            "El despacho o vendedor no es valido, "
                            + "no esta activo o pertenece a otra sucursal");
                }

                sucursalOracle = rs.getString("SUCURSAL");
            }
        }

        final String sucursalValidada = sucursalOracle;

        Firestore db = FirebaseAdminUtil.obtenerFirestore();

        DocumentReference referencia = db
                .collection("rutas")
                .document("despacho_" + idDespachoOracle);

        // La transaccion comprueba el estado y la version
        // antes de realizar cualquier modificacion.
        long nuevaVersion = db.runTransaction(transaccion -> {

            DocumentSnapshot documento
                    = transaccion.get(referencia).get();

            if (!documento.exists()) {
                throw new IllegalStateException(
                        "La ruta no existe en Firestore");
            }

            Long despachoGuardado
                    = documento.getLong("idDespachoOracle");

            Long versionActual
                    = documento.getLong("version");

            String sucursalGuardada
                    = documento.getString("sucursal");

            String estadoActual
                    = documento.getString("estado");

            Object vendedorActual
                    = documento.get("idVendedor");

            Object vehiculoActual
                    = documento.get("idVehiculo");

            if (despachoGuardado == null
                    || despachoGuardado.longValue() != idDespachoOracle
                    || sucursalGuardada == null
                    || !sucursalValidada.equalsIgnoreCase(
                            sucursalGuardada.trim())) {

                throw new IllegalStateException(
                        "Los datos de la ruta no coinciden con Oracle");
            }

            if (versionActual == null
                    || versionActual.longValue() != versionEsperada) {

                throw new IllegalStateException(
                        "La ruta fue modificada. Actualiza la consulta");
            }

            if (!"PENDIENTE_ASIGNACION".equals(estadoActual)
                    || vendedorActual != null
                    || vehiculoActual != null) {

                throw new IllegalStateException(
                        "La ruta ya no permite una primera asignacion");
            }

            Map<String, Object> cambios = new LinkedHashMap<>();

            cambios.put("idVendedor", (long) idVendedor);
            cambios.put("estado", "PENDIENTE_VEHICULO");
            cambios.put("version", versionActual + 1L);
            cambios.put(
                    "actualizadoEn",
                    FieldValue.serverTimestamp()
            );

            transaccion.update(referencia, cambios);

            return versionActual + 1L;

        }).get(30, TimeUnit.SECONDS);

        // =========================================
        // NUEVO: NOTIFICACION AUTOMATICA FCM
        // =========================================
        // En este punto, Firestore ya confirmo
        // correctamente la asignacion del vendedor.
        try {

            NotificacionRutaFcmManager notificaciones
                    = new NotificacionRutaFcmManager();

            int aceptadosPorFcm
                    = notificaciones.notificarRutaAsignada(
                            idVendedor,
                            idDespachoOracle
                    );

            java.util.logging.Logger
                    .getLogger(RutaFirestoreManager.class.getName())
                    .info(
                            "Avisos FCM aceptados para despacho "
                            + idDespachoOracle + ": "
                            + aceptadosPorFcm
                    );

        } catch (Exception e) {

            // Si Firebase FCM falla, NO deshacer
            // la asignacion de la ruta.
            // La transaccion ya fue confirmada.
            // No registrar tokens FCM privados.
            java.util.logging.Logger
                    .getLogger(RutaFirestoreManager.class.getName())
                    .warning(
                            "La ruta fue asignada, pero no se pudo "
                            + "enviar el aviso FCM del despacho "
                            + idDespachoOracle
                    );
        }

        // Devolver la nueva version de la ruta
        // que ya fue confirmada en Firestore.
        return nuevaVersion;
    }

    public long asignarVehiculo(
            int idDespachoOracle,
            String idVehiculo,
            long versionRutaEsperada,
            long versionVehiculoEsperada) throws Exception {

        if (idDespachoOracle <= 0
                || idVehiculo == null
                || !idVehiculo.matches("[A-Za-z0-9_-]{1,80}")
                || versionRutaEsperada <= 0
                || versionVehiculoEsperada <= 0) {

            throw new IllegalArgumentException(
                    "Datos de asignacion invalidos");
        }

        Firestore db = FirebaseAdminUtil.obtenerFirestore();

        String idRuta = "despacho_" + idDespachoOracle;

        DocumentReference referenciaRuta = db
                .collection("rutas")
                .document(idRuta);

        DocumentReference referenciaVehiculo = db
                .collection("vehiculos")
                .document(idVehiculo);

        return db.runTransaction(transaccion -> {

            // Primero leer ambos documentos.
            DocumentSnapshot ruta
                    = transaccion.get(referenciaRuta).get();

            DocumentSnapshot vehiculo
                    = transaccion.get(referenciaVehiculo).get();

            if (!ruta.exists() || !vehiculo.exists()) {
                throw new IllegalStateException(
                        "La ruta o el vehiculo no existe");
            }

            Long despachoGuardado
                    = ruta.getLong("idDespachoOracle");

            Long vendedorAsignado
                    = ruta.getLong("idVendedor");

            Long versionRuta
                    = ruta.getLong("version");

            Long versionVehiculo
                    = vehiculo.getLong("version");

            // Comprobar que se trata del despacho correcto.
            if (despachoGuardado == null
                    || despachoGuardado.longValue()
                    != idDespachoOracle) {

                throw new IllegalStateException(
                        "Los datos del despacho no coinciden");
            }

            // Debe existir un vendedor previamente asignado.
            if (vendedorAsignado == null
                    || vendedorAsignado <= 0) {

                throw new IllegalStateException(
                        "La ruta no tiene vendedor asignado");
            }

            // Comprobar las versiones de ambos documentos.
            if (versionRuta == null
                    || versionVehiculo == null
                    || versionRuta.longValue()
                    != versionRutaEsperada
                    || versionVehiculo.longValue()
                    != versionVehiculoEsperada) {

                throw new IllegalStateException(
                        "Los datos fueron modificados. Actualiza la consulta");
            }

            // La ruta debe estar esperando un vehiculo.
            if (!"PENDIENTE_VEHICULO".equals(
                    ruta.getString("estado"))
                    || ruta.get("idVehiculo") != null) {

                throw new IllegalStateException(
                        "La ruta no permite asignar otro vehiculo");
            }

            // El vehiculo debe estar activo y disponible.
            if (!Boolean.TRUE.equals(
                    vehiculo.getBoolean("activo"))
                    || !"DISPONIBLE".equals(
                            vehiculo.getString("estado"))
                    || vehiculo.get("idRuta") != null) {

                throw new IllegalStateException(
                        "El vehiculo no esta disponible");
            }

            // Preparar actualizacion de la ruta.
            Map<String, Object> cambiosRuta
                    = new LinkedHashMap<>();

            cambiosRuta.put("idVehiculo", idVehiculo);
            cambiosRuta.put("estado", "LISTA_PARA_ENTREGA");
            cambiosRuta.put("version", versionRuta + 1L);
            cambiosRuta.put(
                    "actualizadoEn",
                    FieldValue.serverTimestamp()
            );

            // Preparar reserva del vehiculo.
            Map<String, Object> cambiosVehiculo
                    = new LinkedHashMap<>();

            cambiosVehiculo.put("estado", "ASIGNADO");
            cambiosVehiculo.put("idRuta", idRuta);
            cambiosVehiculo.put("version", versionVehiculo + 1L);
            cambiosVehiculo.put(
                    "actualizadoEn",
                    FieldValue.serverTimestamp()
            );

            // Ambas escrituras pertenecen a la misma
            // transaccion: se aplican juntas o ninguna.
            transaccion.update(
                    referenciaRuta,
                    cambiosRuta
            );

            transaccion.update(
                    referenciaVehiculo,
                    cambiosVehiculo
            );

            return versionRuta + 1L;

        }).get(30, TimeUnit.SECONDS);
    }

    public long reportarEntrega(
            int idDespachoOracle,
            int idVendedorAutenticado,
            long versionEsperada) throws Exception {

        if (idDespachoOracle <= 0
                || idVendedorAutenticado <= 0
                || versionEsperada <= 0) {

            throw new IllegalArgumentException(
                    "Datos de entrega invalidos");
        }

        Firestore db = FirebaseAdminUtil.obtenerFirestore();

        String idRuta = "despacho_" + idDespachoOracle;

        DocumentReference referenciaRuta = db
                .collection("rutas")
                .document(idRuta);

        return db.runTransaction(transaccion -> {

            // Primero consultar la ruta.
            DocumentSnapshot ruta
                    = transaccion.get(referenciaRuta).get();

            if (!ruta.exists()) {
                throw new IllegalStateException(
                        "La ruta no existe");
            }

            Long despachoGuardado
                    = ruta.getLong("idDespachoOracle");

            Long vendedorAsignado
                    = ruta.getLong("idVendedor");

            Long versionActual
                    = ruta.getLong("version");

            String estadoActual
                    = ruta.getString("estado");

            String idVehiculo
                    = ruta.getString("idVehiculo");

            // Comprobar identidad del despacho.
            if (despachoGuardado == null
                    || despachoGuardado.longValue()
                    != idDespachoOracle) {

                throw new IllegalStateException(
                        "El despacho no coincide");
            }

            // Solo el vendedor asignado puede reportar.
            if (vendedorAsignado == null
                    || vendedorAsignado.longValue()
                    != idVendedorAutenticado) {

                throw new IllegalStateException(
                        "Esta ruta no pertenece al vendedor");
            }

            // Impedir modificaciones con versiones antiguas.
            if (versionActual == null
                    || versionActual.longValue()
                    != versionEsperada) {

                throw new IllegalStateException(
                        "La ruta fue modificada. Actualiza las rutas");
            }

            // No aceptar entregas duplicadas o rutas incompletas.
            if (!"LISTA_PARA_ENTREGA".equals(estadoActual)) {

                throw new IllegalStateException(
                        "La ruta no permite reportar la entrega");
            }

            if (idVehiculo == null
                    || !idVehiculo.matches("[A-Za-z0-9_-]{1,80}")) {

                throw new IllegalStateException(
                        "La ruta no tiene un vehiculo valido");
            }

            DocumentReference referenciaVehiculo = db
                    .collection("vehiculos")
                    .document(idVehiculo);

            // Leer el vehiculo antes de cualquier escritura.
            DocumentSnapshot vehiculo
                    = transaccion.get(referenciaVehiculo).get();

            if (!vehiculo.exists()) {
                throw new IllegalStateException(
                        "El vehiculo no existe");
            }

            Long versionVehiculo
                    = vehiculo.getLong("version");

            if (!"ASIGNADO".equals(vehiculo.getString("estado"))
                    || !idRuta.equals(vehiculo.getString("idRuta"))
                    || versionVehiculo == null
                    || versionVehiculo <= 0) {

                throw new IllegalStateException(
                        "El vehiculo ya no coincide con la ruta");
            }

            // Actualizar el estado de la entrega.
            Map<String, Object> cambiosRuta
                    = new LinkedHashMap<>();

            cambiosRuta.put("estado", "ENTREGA_REPORTADA");
            cambiosRuta.put("version", versionActual + 1L);

            cambiosRuta.put(
                    "entregaReportadaEn",
                    FieldValue.serverTimestamp()
            );

            cambiosRuta.put(
                    "actualizadoEn",
                    FieldValue.serverTimestamp()
            );

            // Liberar el vehiculo para nuevas asignaciones.
            Map<String, Object> cambiosVehiculo
                    = new LinkedHashMap<>();

            cambiosVehiculo.put("estado", "DISPONIBLE");

            cambiosVehiculo.put(
                    "idRuta",
                    FieldValue.delete()
            );

            cambiosVehiculo.put(
                    "version",
                    versionVehiculo + 1L
            );

            cambiosVehiculo.put(
                    "actualizadoEn",
                    FieldValue.serverTimestamp()
            );

            // Aplicar ambas actualizaciones en una transaccion.
            transaccion.update(
                    referenciaRuta,
                    cambiosRuta
            );

            transaccion.update(
                    referenciaVehiculo,
                    cambiosVehiculo
            );

            return versionActual + 1L;

        }).get(30, TimeUnit.SECONDS);
    }

}
