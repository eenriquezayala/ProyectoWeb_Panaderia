package com.panaderia.sancarlos.controller;

import com.panaderia.sancarlos.manager.RutaFirestoreManager;
import com.panaderia.sancarlos.model.SesionAutenticada;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.ws.rs.core.CacheControl;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import javax.ws.rs.PUT;
import java.util.logging.Logger;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.POST;
import javax.ws.rs.Consumes;

@Path("rutas")
@Produces(MediaType.APPLICATION_JSON)
public class RutaController {

    private static final Logger LOGGER
            = Logger.getLogger(RutaController.class.getName());

    private final RutaFirestoreManager manager
            = new RutaFirestoreManager();

    @GET
    public Response consultarRutas(
            @Context SecurityContext seguridad) {

        if (seguridad == null
                || !(seguridad.getUserPrincipal() instanceof SesionAutenticada)) {

            return respuestaError(
                    401,
                    "Debes iniciar sesion"
            );
        }

        SesionAutenticada sesion
                = (SesionAutenticada) seguridad.getUserPrincipal();

        try {

            // El administrador puede consultar todas las rutas.
            if (seguridad.isUserInRole("ADMINISTRADOR")) {

                return Response.ok(
                        manager.consultarRutas()
                ).build();
            }

            // El vendedor solo consulta sus propias rutas.
            if (seguridad.isUserInRole("VENDEDOR_RUTA")) {

                return Response.ok(
                        manager.consultarRutasDeVendedor(
                                sesion.getIdUsuario()
                        )
                ).build();
            }

            // Cualquier otro rol queda bloqueado.
            return respuestaError(
                    403,
                    "No tienes permiso para consultar rutas"
            );

        } catch (Exception e) {

            LOGGER.log(
                    Level.SEVERE,
                    "Error al consultar rutas en Firestore",
                    e
            );

            return respuestaError(
                    503,
                    "No fue posible consultar las rutas"
            );
        }
    }

    /**
     * Catálogo de vehículos activos y disponibles para el formulario web.
     * Solo administradores. No permite escribir en Firestore.
     * Usa Firebase Admin SDK, ya inicializado por el proyecto.
     */
    @GET
    @Path("vehiculos-disponibles")
    public Response consultarVehiculosDisponibles(
            @Context SecurityContext seguridad) {

        if (seguridad == null
                || !(seguridad.getUserPrincipal() instanceof SesionAutenticada)) {
            return respuestaError(401, "Debes iniciar sesion");
        }
        if (!seguridad.isUserInRole("ADMINISTRADOR")) {
            return respuestaError(403, "No tienes permiso para consultar vehiculos");
        }

        try {
            List<Map<String, Object>> vehiculos = new ArrayList<>();

            // Se filtra en Java para evitar la necesidad de índices compuestos.
            for (DocumentSnapshot doc : FirestoreClient.getFirestore()
                    .collection("vehiculos").get().get().getDocuments()) {

                if (!Boolean.TRUE.equals(doc.getBoolean("activo"))
                        || !"DISPONIBLE".equalsIgnoreCase(doc.getString("estado"))) {
                    continue;
                }
                Object numeroVersion = doc.get("version");
                if (!(numeroVersion instanceof Number)) {
                    continue;
                }
                Number numero = (Number) numeroVersion;
                long version = numero.longValue();
                if (version <= 0 || numero.doubleValue() != (double) version) {
                    continue;
                }
                String idVehiculo = doc.getId();
                if (idVehiculo == null || idVehiculo.isEmpty()
                        || idVehiculo.length() > 120 || idVehiculo.contains("/")) {
                    continue;
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("idVehiculo", idVehiculo);
                item.put("nombre", doc.getString("nombre"));
                item.put("placa", doc.getString("placa"));
                item.put("tipo", doc.getString("tipo"));
                item.put("version", version);
                vehiculos.add(item);
            }

            vehiculos.sort(Comparator.comparing(
                    v -> String.valueOf(v.get("nombre")),
                    String.CASE_INSENSITIVE_ORDER));

            CacheControl sinCache = new CacheControl();
            sinCache.setNoStore(true);
            return Response.ok(vehiculos).cacheControl(sinCache).build();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al consultar vehiculos disponibles", e);
            return respuestaError(503, "No fue posible consultar los vehiculos");
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registrarRuta(
            Map<String, Object> datos,
            @Context SecurityContext seguridad) {

        // Validar sesión JWT
        if (seguridad == null
                || !(seguridad.getUserPrincipal() instanceof SesionAutenticada)) {

            return respuestaError(
                    401,
                    "Debes iniciar sesion"
            );
        }

        // Solo administradores
        if (!seguridad.isUserInRole("ADMINISTRADOR")) {

            return respuestaError(
                    403,
                    "No tienes permiso para registrar rutas"
            );
        }

        // Validar el identificador recibido
        Object valor = datos == null
                ? null
                : datos.get("idDespachoOracle");

        if (!(valor instanceof Number)) {
            return respuestaError(
                    400,
                    "Debes proporcionar idDespachoOracle"
            );
        }

        Number numero = (Number) valor;
        long id = numero.longValue();

        if (id <= 0
                || id > Integer.MAX_VALUE
                || numero.doubleValue() != id) {

            return respuestaError(
                    400,
                    "Identificador de despacho invalido"
            );
        }

        try {
            String idRuta = manager.registrarRutaPendiente(
                    (int) id
            );

            Map<String, Object> resultado = new HashMap<>();
            resultado.put("exito", true);
            resultado.put("idRuta", idRuta);
            resultado.put(
                    "mensaje",
                    "Ruta registrada correctamente"
            );

            return Response.status(201)
                    .entity(resultado)
                    .build();

        } catch (IllegalArgumentException e) {

            return respuestaError(409, e.getMessage());

        } catch (Exception e) {

            LOGGER.log(
                    Level.SEVERE,
                    "Error al registrar ruta",
                    e
            );

            return respuestaError(
                    503,
                    "No fue posible registrar la ruta"
            );
        }
    }

    private Response respuestaError(
            int estado,
            String mensaje) {

        Map<String, Object> respuesta = new HashMap<>();

        respuesta.put("exito", false);
        respuesta.put("mensaje", mensaje);

        return Response.status(estado)
                .entity(respuesta)
                .build();
    }

    @PUT
    @Path("{idDespachoOracle}/asignar-vendedor")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response asignarVendedor(
            @PathParam("idDespachoOracle") int idDespachoOracle,
            Map<String, Object> datos,
            @Context SecurityContext seguridad) {

        // Comprobar que exista una sesion autenticada.
        if (seguridad == null
                || !(seguridad.getUserPrincipal() instanceof SesionAutenticada)) {

            return Response.status(401)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje", "Debes iniciar sesion"))
                    .build();
        }

        // Solo los administradores pueden asignar rutas.
        if (!seguridad.isUserInRole("ADMINISTRADOR")) {

            return Response.status(403)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje",
                            "No tienes permiso para asignar rutas"))
                    .build();
        }

        // Validar que el cuerpo JSON contenga los datos.
        if (datos == null
                || datos.get("idVendedor") == null
                || datos.get("versionEsperada") == null) {

            return Response.status(400)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje",
                            "Faltan idVendedor o versionEsperada"))
                    .build();
        }

        try {

            // Convertir los valores JSON a enteros.
            int idVendedor = Integer.parseInt(
                    String.valueOf(datos.get("idVendedor"))
            );

            long versionEsperada = Long.parseLong(
                    String.valueOf(datos.get("versionEsperada"))
            );

            // El Manager valida Oracle y actualiza Firestore.
            long versionNueva = manager.asignarVendedor(
                    idDespachoOracle,
                    idVendedor,
                    versionEsperada
            );

            Map<String, Object> respuesta
                    = new LinkedHashMap<>();

            respuesta.put("exito", true);
            respuesta.put(
                    "idRuta",
                    "despacho_" + idDespachoOracle
            );
            respuesta.put("idVendedor", idVendedor);
            respuesta.put("estado", "PENDIENTE_VEHICULO");
            respuesta.put("version", versionNueva);
            respuesta.put(
                    "mensaje",
                    "Vendedor asignado correctamente"
            );

            return Response.ok(respuesta).build();

        } catch (IllegalArgumentException e) {

            // Datos invalidos o vendedor no autorizado.
            return Response.status(400)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje", e.getMessage()))
                    .build();

        } catch (Exception e) {

            // Las excepciones de una transaccion pueden
            // venir envueltas en ExecutionException.
            Throwable causa = e;

            while (causa != null) {

                if (causa instanceof IllegalStateException) {

                    return Response.status(409)
                            .entity(
                                    java.util.Collections.singletonMap(
                                            "mensaje",
                                            causa.getMessage()
                                    )
                            )
                            .build();
                }

                causa = causa.getCause();
            }

            Logger.getLogger(RutaController.class.getName())
                    .log(
                            Level.SEVERE,
                            "Error al asignar vendedor a ruta",
                            e
                    );

            return Response.status(503)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje",
                            "No fue posible asignar la ruta"))
                    .build();
        }
    }

    @PUT
    @Path("{idDespachoOracle}/asignar-vehiculo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response asignarVehiculo(
            @PathParam("idDespachoOracle") int idDespachoOracle,
            Map<String, Object> datos,
            @Context SecurityContext seguridad) {

        // Comprobar la autenticacion.
        if (seguridad == null
                || !(seguridad.getUserPrincipal() instanceof SesionAutenticada)) {

            return Response.status(401)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje", "Debes iniciar sesion"))
                    .build();
        }

        // Solo ADMINISTRADOR puede asignar vehiculos.
        if (!seguridad.isUserInRole("ADMINISTRADOR")) {

            return Response.status(403)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje",
                            "No tienes permiso para asignar vehiculos"))
                    .build();
        }

        // Comprobar que el cuerpo JSON tenga los campos.
        if (datos == null
                || datos.get("idVehiculo") == null
                || datos.get("versionRutaEsperada") == null
                || datos.get("versionVehiculoEsperada") == null) {

            return Response.status(400)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje",
                            "Faltan datos para asignar el vehiculo"))
                    .build();
        }

        try {

            Object valorVehiculo = datos.get("idVehiculo");

            if (!(valorVehiculo instanceof String)) {
                throw new IllegalArgumentException(
                        "El identificador del vehiculo debe ser texto");
            }

            String idVehiculo
                    = ((String) valorVehiculo).trim();

            long versionRutaEsperada = Long.parseLong(
                    String.valueOf(
                            datos.get("versionRutaEsperada"))
            );

            long versionVehiculoEsperada = Long.parseLong(
                    String.valueOf(
                            datos.get("versionVehiculoEsperada"))
            );

            // El Manager comprueba los estados y versiones
            // y actualiza los dos documentos en una transaccion.
            long versionNueva = manager.asignarVehiculo(
                    idDespachoOracle,
                    idVehiculo,
                    versionRutaEsperada,
                    versionVehiculoEsperada
            );

            Map<String, Object> respuesta
                    = new LinkedHashMap<>();

            respuesta.put("exito", true);
            respuesta.put(
                    "idRuta",
                    "despacho_" + idDespachoOracle
            );
            respuesta.put("idVehiculo", idVehiculo);
            respuesta.put("estado", "LISTA_PARA_ENTREGA");
            respuesta.put("version", versionNueva);
            respuesta.put(
                    "mensaje",
                    "Vehiculo asignado correctamente"
            );

            return Response.ok(respuesta).build();

        } catch (IllegalArgumentException e) {

            return Response.status(400)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje", e.getMessage()))
                    .build();

        } catch (Exception e) {

            // Las excepciones de la transaccion pueden
            // venir envueltas en ExecutionException.
            Throwable causa = e;

            while (causa != null) {

                if (causa instanceof IllegalStateException) {

                    return Response.status(409)
                            .entity(
                                    java.util.Collections.singletonMap(
                                            "mensaje",
                                            causa.getMessage()
                                    )
                            )
                            .build();
                }

                causa = causa.getCause();
            }

            java.util.logging.Logger
                    .getLogger(RutaController.class.getName())
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Error al asignar vehiculo a ruta",
                            e
                    );

            return Response.status(503)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje",
                            "No fue posible asignar el vehiculo"))
                    .build();
        }
    }

    @PUT
    @Path("{idDespachoOracle}/reportar-entrega")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response reportarEntrega(
            @PathParam("idDespachoOracle") int idDespachoOracle,
            Map<String, Object> datos,
            @Context SecurityContext seguridad) {

        // La identidad debe provenir de una sesion JWT valida.
        if (seguridad == null
                || !(seguridad.getUserPrincipal() instanceof SesionAutenticada)) {

            return Response.status(401)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje", "Debes iniciar sesion"))
                    .build();
        }

        // Solo el rol VENDEDOR_RUTA puede reportar entregas.
        if (!seguridad.isUserInRole("VENDEDOR_RUTA")) {

            return Response.status(403)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje",
                            "No tienes permiso para reportar entregas"))
                    .build();
        }

        if (datos == null
                || datos.get("versionEsperada") == null) {

            return Response.status(400)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje",
                            "Falta la versionEsperada"))
                    .build();
        }

        // Obtener el vendedor del JWT, nunca del cuerpo JSON.
        SesionAutenticada sesion
                = (SesionAutenticada) seguridad.getUserPrincipal();

        try {

            long versionEsperada = Long.parseLong(
                    String.valueOf(datos.get("versionEsperada"))
            );

            long versionNueva = manager.reportarEntrega(
                    idDespachoOracle,
                    sesion.getIdUsuario(),
                    versionEsperada
            );

            Map<String, Object> respuesta
                    = new LinkedHashMap<>();

            respuesta.put("exito", true);
            respuesta.put(
                    "idRuta",
                    "despacho_" + idDespachoOracle
            );
            respuesta.put(
                    "estado",
                    "ENTREGA_REPORTADA"
            );
            respuesta.put("version", versionNueva);
            respuesta.put(
                    "mensaje",
                    "Entrega reportada correctamente"
            );

            return Response.ok(respuesta).build();

        } catch (IllegalArgumentException e) {

            return Response.status(400)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje",
                            "Datos de entrega invalidos"))
                    .build();

        } catch (Exception e) {

            // Buscar conflictos aunque vengan envueltos
            // en una excepcion de la transaccion.
            Throwable causa = e;

            while (causa != null) {

                if (causa instanceof IllegalStateException) {

                    return Response.status(409)
                            .entity(
                                    java.util.Collections.singletonMap(
                                            "mensaje",
                                            causa.getMessage()
                                    )
                            )
                            .build();
                }

                causa = causa.getCause();
            }

            java.util.logging.Logger
                    .getLogger(RutaController.class.getName())
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Error al reportar entrega",
                            e
                    );

            return Response.status(503)
                    .entity(java.util.Collections.singletonMap(
                            "mensaje",
                            "No fue posible reportar la entrega"))
                    .build();
        }
    }

}
