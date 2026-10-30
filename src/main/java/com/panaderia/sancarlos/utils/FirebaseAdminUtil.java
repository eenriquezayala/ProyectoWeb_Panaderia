
package com.panaderia.sancarlos.utils;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;

public final class FirebaseAdminUtil {

    private FirebaseAdminUtil() {
    }

    public static synchronized Firestore obtenerFirestore()
            throws IOException {

        if (FirebaseApp.getApps().isEmpty()) {

            String directorio = Paths.get(
                    System.getProperty("user.home"),
                    ".panaderia-san-carlos"
            ).toString();

            Dotenv dotenv = Dotenv.configure()
                    .directory(directorio)
                    .ignoreIfMissing()
                    .load();

            String ruta = System.getenv(
                    "PANADERIA_FIREBASE_CREDENTIALS_PATH"
            );

            if (ruta == null || ruta.trim().isEmpty()) {
                ruta = dotenv.get(
                    "PANADERIA_FIREBASE_CREDENTIALS_PATH"
                );
            }

            if (ruta == null || ruta.trim().isEmpty()) {
                throw new IOException(
                    "Falta configurar la ruta privada de Firebase"
                );
            }

            try (FileInputStream archivo =
                    new FileInputStream(ruta)) {

                FirebaseOptions opciones =
                        FirebaseOptions.builder()
                        .setCredentials(
                            GoogleCredentials.fromStream(archivo)
                        )
                        .setProjectId("lamigajasancarlos")
                        .build();

                FirebaseApp.initializeApp(opciones);
            }
        }

        return FirestoreClient.getFirestore();
    }
}
