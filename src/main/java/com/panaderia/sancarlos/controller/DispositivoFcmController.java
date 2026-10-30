package com.panaderia.sancarlos.controller;

import com.panaderia.sancarlos.manager.RegistroFcmManager;
import com.panaderia.sancarlos.model.SesionAutenticada;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@Path("dispositivos-fcm")
public class DispositivoFcmController {

    private static final Logger LOGGER
            = Logger.getLogger(
                    DispositivoFcmController.class.getName()
            );

    private final RegistroFcmManager manager
            = new RegistroFcmManager();

    @POST
    @Path("registrar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registrarDispositivo(
            Map<String, Object> datos,
            @Context SecurityContext seguridad) {

        // La sesion debe haber sido validada por FiltroJwt.
        if (seguridad == null
                || !(seguridad.getUserPrincipal() instanceof SesionAutenticada)) {

            return Response.status(401)
                    .entity(Collections.singletonMap(
                            "mensaje",
                            "Debes iniciar sesion"))
                    .build();
        }

        // Por ahora la app movil esta destinada
        // exclusivamente a vendedores de ruta.
        if (!seguridad.isUserInRole("VENDEDOR_RUTA")) {

            return Response.status(403)
                    .entity(Collections.singletonMap(
                            "mensaje",
                            "No tienes permiso para registrar dispositivos"))
                    .build();
        }

        // Validar los datos recibidos.
        if (datos == null
                || !(datos.get("token") instanceof String)) {

            return Response.status(400)
                    .entity(Collections.singletonMap(
                            "mensaje",
                            "Falta un token FCM valido"))
                    .build();
        }

        String tokenFcm
                = ((String) datos.get("token")).trim();

        if (tokenFcm.isEmpty()
                || tokenFcm.length() > 4096) {

            return Response.status(400)
                    .entity(Collections.singletonMap(
                            "mensaje",
                            "Token FCM invalido"))
                    .build();
        }

        // El ID del vendedor se obtiene de la sesion,
        // nunca del JSON enviado desde Android.
        SesionAutenticada sesion
                = (SesionAutenticada) seguridad.getUserPrincipal();

        try {

            manager.registrarDispositivo(
                    sesion.getIdUsuario(),
                    tokenFcm
            );

            return Response.ok(
                    Collections.singletonMap(
                            "mensaje",
                            "Dispositivo registrado correctamente"
                    )
            ).build();

        } catch (IllegalArgumentException e) {

            return Response.status(400)
                    .entity(Collections.singletonMap(
                            "mensaje",
                            "Datos del dispositivo invalidos"))
                    .build();

        } catch (Exception e) {

            // No registrar ni devolver el token FCM.
            LOGGER.log(
                    Level.SEVERE,
                    "Error al registrar dispositivo FCM",
                    e
            );

            return Response.status(503)
                    .entity(Collections.singletonMap(
                            "mensaje",
                            "No fue posible registrar el dispositivo"))
                    .build();
        }
    }

    @POST
    @Path("desvincular")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response desvincularDispositivo(
            Map<String, Object> datos,
            @Context SecurityContext seguridad) {

        if (seguridad == null
                || !(seguridad.getUserPrincipal() instanceof SesionAutenticada)) {

            return Response.status(401)
                    .entity(Collections.singletonMap(
                            "mensaje",
                            "Debes iniciar sesion"))
                    .build();
        }

        if (!seguridad.isUserInRole("VENDEDOR_RUTA")) {

            return Response.status(403)
                    .entity(Collections.singletonMap(
                            "mensaje",
                            "No tienes permiso para desvincular dispositivos"))
                    .build();
        }

        if (datos == null
                || !(datos.get("token") instanceof String)) {

            return Response.status(400)
                    .entity(Collections.singletonMap(
                            "mensaje",
                            "Falta un token FCM valido"))
                    .build();
        }

        String tokenFcm
                = ((String) datos.get("token")).trim();

        if (tokenFcm.isEmpty()
                || tokenFcm.length() > 4096) {

            return Response.status(400)
                    .entity(Collections.singletonMap(
                            "mensaje",
                            "Token FCM invalido"))
                    .build();
        }

        SesionAutenticada sesion
                = (SesionAutenticada) seguridad.getUserPrincipal();

        try {

            manager.desvincularDispositivo(
                    sesion.getIdUsuario(),
                    tokenFcm
            );

            // Respuesta uniforme aunque el dispositivo
            // ya no estuviera asociado a este vendedor.
            return Response.ok(
                    Collections.singletonMap(
                            "mensaje",
                            "Solicitud de desvinculacion procesada"
                    )
            ).build();

        } catch (IllegalArgumentException e) {

            return Response.status(400)
                    .entity(Collections.singletonMap(
                            "mensaje",
                            "Datos del dispositivo invalidos"))
                    .build();

        } catch (Exception e) {

            LOGGER.log(
                    Level.SEVERE,
                    "Error al desvincular dispositivo FCM",
                    e
            );

            return Response.status(503)
                    .entity(Collections.singletonMap(
                            "mensaje",
                            "No fue posible desvincular el dispositivo"))
                    .build();
        }
    }
}
