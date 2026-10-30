
package com.panaderia.sancarlos.manager;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.panaderia.sancarlos.utils.FirebaseAdminUtil;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class NotificacionRutaFcmManager {

    private static final Logger LOGGER =
            Logger.getLogger(
                    NotificacionRutaFcmManager.class.getName()
            );

    public int notificarRutaAsignada(
            int idVendedor,
            int idDespachoOracle) throws Exception {

        if (idVendedor <= 0 || idDespachoOracle <= 0) {
            throw new IllegalArgumentException(
                    "Datos de la ruta invalidos"
            );
        }

        // Firebase Admin se inicializa desde el backend.
        Firestore db =
                FirebaseAdminUtil.obtenerFirestore();

        // Buscar solo dispositivos del vendedor asignado.
        QuerySnapshot resultado =
                db.collection("dispositivos_fcm")
                        .whereEqualTo(
                                "idUsuario",
                                (long) idVendedor
                        )
                        .get()
                        .get(15, TimeUnit.SECONDS);

        int enviados = 0;

        for (DocumentSnapshot dispositivo :
                resultado.getDocuments()) {

            // No enviar a dispositivos inactivos.
            if (!Boolean.TRUE.equals(
                    dispositivo.getBoolean("activo"))) {
                continue;
            }

            String tokenFcm =
                    dispositivo.getString("token");

            if (tokenFcm == null
                    || tokenFcm.trim().isEmpty()) {
                continue;
            }

            Message mensaje = Message.builder()
                    .setToken(tokenFcm)
                    .setNotification(
                            Notification.builder()
                                    .setTitle(
                                            "Nueva ruta asignada"
                                    )
                                    .setBody(
                                            "Tienes una nueva ruta " +
                                            "de entrega. Abre la " +
                                            "aplicacion para consultar " +
                                            "los detalles."
                                    )
                                    .build()
                    )
                    .putData(
                            "tipo",
                            "RUTA_ASIGNADA"
                    )
                    .putData(
                            "idDespachoOracle",
                            String.valueOf(idDespachoOracle)
                    )
                    .build();

            try {

                FirebaseMessaging.getInstance()
                        .send(mensaje);

                enviados++;

            } catch (FirebaseMessagingException e) {

                // No registrar el token privado.
                // Un fallo de FCM no debe cancelar
                // una asignacion ya realizada.
                LOGGER.warning(
                        "No se pudo enviar aviso FCM " +
                        "para el despacho " +
                        idDespachoOracle
                );
            }
        }

        return enviados;
    }
}
