package com.panaderia.sancarlos.manager;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.panaderia.sancarlos.utils.FirebaseAdminUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;

public class RegistroFcmManager {

    public void registrarDispositivo(
            int idUsuario,
            String tokenFcm) throws Exception {

        if (idUsuario <= 0
                || tokenFcm == null
                || tokenFcm.trim().isEmpty()
                || tokenFcm.length() > 4096) {

            throw new IllegalArgumentException(
                    "Datos del dispositivo invalidos");
        }

        String token = tokenFcm.trim();

        // Usar una huella como ID, sin exponer
        // el token en la ruta del documento.
        MessageDigest digest
                = MessageDigest.getInstance("SHA-256");

        byte[] hash = digest.digest(
                token.getBytes(StandardCharsets.UTF_8)
        );

        StringBuilder identificador = new StringBuilder();

        for (byte b : hash) {
            identificador.append(
                    String.format("%02x", b & 0xff)
            );
        }

        Firestore db
                = FirebaseAdminUtil.obtenerFirestore();

        Map<String, Object> datos
                = new LinkedHashMap<>();

        datos.put("idUsuario", (long) idUsuario);
        datos.put("token", token);
        datos.put("plataforma", "ANDROID");
        datos.put("activo", true);
        datos.put(
                "actualizadoEn",
                FieldValue.serverTimestamp()
        );

        db.collection("dispositivos_fcm")
                .document(identificador.toString())
                .set(datos)
                .get(15, TimeUnit.SECONDS);
    }

    public boolean desvincularDispositivo(
            int idUsuario,
            String tokenFcm) throws Exception {

        if (idUsuario <= 0
                || tokenFcm == null
                || tokenFcm.trim().isEmpty()
                || tokenFcm.length() > 4096) {

            throw new IllegalArgumentException(
                    "Datos del dispositivo invalidos");
        }

        String token = tokenFcm.trim();

        // Obtener la misma huella SHA-256 utilizada
        // cuando se registro el dispositivo.
        MessageDigest digest
                = MessageDigest.getInstance("SHA-256");

        byte[] hash = digest.digest(
                token.getBytes(StandardCharsets.UTF_8)
        );

        StringBuilder identificador = new StringBuilder();

        for (byte b : hash) {
            identificador.append(
                    String.format("%02x", b & 0xff)
            );
        }

        Firestore db = FirebaseAdminUtil.obtenerFirestore();

        DocumentReference referencia
                = db.collection("dispositivos_fcm")
                        .document(identificador.toString());

        // La transaccion comprueba el propietario antes
        // de eliminar la asociacion del dispositivo.
        return db.runTransaction(transaccion -> {

            DocumentSnapshot documento
                    = transaccion.get(referencia).get();

            if (!documento.exists()) {
                return false;
            }

            Long propietario
                    = documento.getLong("idUsuario");

            if (propietario == null
                    || propietario.longValue() != idUsuario) {

                return false;
            }

            transaccion.delete(referencia);

            return true;

        }).get(15, TimeUnit.SECONDS);
    }
}
