package com.kairos.brox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * La implementacion HTTP del puerto. <b>La unica clase de KAIROS que sabe que
 * BROX tiene rutas.</b>
 *
 * <h2>Por que concentrar aqui todas las rutas</h2>
 * Porque el contrato va a versionarse y va a cambiar. Con las rutas repartidas
 * por el adaptador, subir de version obligaria a peinar toda la conversacion;
 * concentradas, es este fichero. Y porque asi la prueba de que KAIROS no toca
 * la base de BROX se reduce a leer una clase.
 *
 * <h2>Lo que hace en cada llamada, sin excepcion</h2>
 * <ol>
 *   <li>manda el token <b>de la persona</b>, no uno de servicio;</li>
 *   <li>manda la traza —canal, agente, modelo, conversacion, turno, mensaje—
 *       para que el hecho quede explicado en BROX;</li>
 *   <li>deja que BROX decida. Un 403 se propaga: no se reinterpreta ni se
 *       intenta por otra ruta.</li>
 * </ol>
 */
@Component
public class ClienteBroxHttp implements ClienteBrox {

    private final RestClient http;

    public ClienteBroxHttp(RestClient.Builder builder,
                           @Value("${brox.url:http://localhost:8090/controllocal/Api}") String base) {
        this.http = builder.baseUrl(base).build();
    }

    // ------------------------------------------------------------------
    // Capacidades
    // ------------------------------------------------------------------

    @Override
    public List<Capacidad> capacidades(SesionBrox sesion) {
        return lista(http.get().uri("/capacidades").headers(conSesion(sesion)));
    }

    // ------------------------------------------------------------------
    // Captura
    // ------------------------------------------------------------------

    @Override
    public EstadoCaptura avanzarCaptura(SesionBrox sesion, String intencion, Long idBorrador,
                                        Map<String, String> datos, Traza traza) {
        return http.post().uri("/captura")
                .headers(conSesion(sesion))
                .headers(conTraza(traza))
                .body(Map.of("intencion", intencion, "idBorrador", idBorrador, "datos", datos))
                .retrieve()
                .body(EstadoCaptura.class);
    }

    @Override
    public List<EstadoCaptura> capturasEnCurso(SesionBrox sesion) {
        return lista(http.get().uri("/captura").headers(conSesion(sesion)));
    }

    @Override
    public EstadoCaptura captura(SesionBrox sesion, long idBorrador) {
        return http.get().uri("/captura/{id}", idBorrador).headers(conSesion(sesion))
                .retrieve().body(EstadoCaptura.class);
    }

    @Override
    public Ejecucion ejecutarCaptura(SesionBrox sesion, long idBorrador, String claveIdempotencia,
                                     Traza traza) {
        return http.post().uri("/captura/{id}/ejecutar", idBorrador)
                .headers(conSesion(sesion))
                .headers(conTraza(traza))
                .header("Idempotency-Key", claveIdempotencia)
                .retrieve()
                .body(Ejecucion.class);
    }

    // ------------------------------------------------------------------
    // Lecturas
    // ------------------------------------------------------------------

    @Override
    public List<Coincidencia> buscarPropiedades(SesionBrox sesion, String texto) {
        return lista(http.get()
                .uri(uri -> uri.path("/locales").queryParam("texto", texto)
                        .queryParam("tamano", 10).build())
                .headers(conSesion(sesion)));
    }

    @Override
    public Map<String, Object> propiedad(SesionBrox sesion, long idPropiedad) {
        return http.get().uri("/propiedades/{id}", idPropiedad).headers(conSesion(sesion))
                .retrieve().body(MAPA);
    }

    @Override
    public List<Persona> buscarClientes(SesionBrox sesion, String texto) {
        return lista(http.get()
                .uri(uri -> uri.path("/clientes").queryParam("texto", texto)
                        .queryParam("tamano", 10).build())
                .headers(conSesion(sesion)));
    }

    @Override
    public List<Persona> buscarPropietarios(SesionBrox sesion, String texto) {
        return lista(http.get()
                .uri(uri -> uri.path("/propietarios").queryParam("texto", texto)
                        .queryParam("tamano", 5).build())
                .headers(conSesion(sesion)));
    }

    // ------------------------------------------------------------------
    // Escrituras
    // ------------------------------------------------------------------

    @Override
    public Persona registrarPropietario(SesionBrox sesion, Map<String, String> datos, Traza traza) {
        return http.post().uri("/propietarios")
                .headers(conSesion(sesion))
                .headers(conTraza(traza))
                .header("Idempotency-Key", traza.claveIdempotencia())
                .body(datos)
                .retrieve()
                .body(Persona.class);
    }

    @Override
    public Interaccion registrarInteraccion(SesionBrox sesion, Map<String, Object> datos,
                                            Traza traza) {
        return http.post().uri("/interacciones")
                .headers(conSesion(sesion))
                .headers(conTraza(traza))
                .header("Idempotency-Key", traza.claveIdempotencia())
                .body(datos)
                .retrieve()
                .body(Interaccion.class);
    }

    // ------------------------------------------------------------------
    // Vocabulario del tenant
    // ------------------------------------------------------------------

    @Override
    public List<String> distritos(SesionBrox sesion) {
        return lista(http.get().uri("/locales/distritos").headers(conSesion(sesion)));
    }

    @Override
    public List<Pregunta> catalogoDe(SesionBrox sesion, String tipoPropiedad) {
        return lista(http.get().uri("/propiedades/catalogo/{tipo}", tipoPropiedad)
                .headers(conSesion(sesion)));
    }

    @Override
    public List<String> resultadosDeInteraccion(SesionBrox sesion, String contexto) {
        return lista(http.get()
                .uri(uri -> uri.path("/interacciones/resultados")
                        .queryParam("contexto", contexto).build())
                .headers(conSesion(sesion)));
    }

    // ------------------------------------------------------------------

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>>
            MAPA = new org.springframework.core.ParameterizedTypeReference<>() {
    };

    /**
     * El token de la persona. <b>Siempre el suyo</b>: KAIROS no tiene cuenta, y
     * si algun dia esta linea mandara un token de servicio, la conversacion se
     * habria convertido en un agujero de autorizacion.
     */
    private static Consumer<HttpHeaders> conSesion(SesionBrox sesion) {
        return cabeceras -> cabeceras.setBearerAuth(sesion.token());
    }

    private static Consumer<HttpHeaders> conTraza(Traza traza) {
        return cabeceras -> traza.cabeceras().forEach(cabeceras::add);
    }

    private <T> List<T> lista(RestClient.RequestHeadersSpec<?> peticion) {
        @SuppressWarnings("unchecked")
        List<T> respuesta = peticion.retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<T>>() {
                });
        return respuesta == null ? List.of() : respuesta;
    }
}
