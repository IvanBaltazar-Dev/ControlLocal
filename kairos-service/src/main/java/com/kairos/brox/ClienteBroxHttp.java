package com.kairos.brox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
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
 *   <li>manda la traza —canal, agente, modelo, conversacion, turno, mensaje y
 *       herramienta— para que el hecho quede explicado en BROX;</li>
 *   <li>deja que BROX decida. Un 403 se propaga: no se reinterpreta ni se
 *       intenta por otra ruta.</li>
 * </ol>
 *
 * <h2>Las dos rutas que el Core todavia no tiene</h2>
 * {@code GET /capacidades} responde <b>404</b> y
 * {@code GET /interacciones/resultados} responde <b>400</b> —choca con
 * {@code GET /interacciones/{id}}, que espera un numero—. Las dos se traducen
 * aqui a <b>lista vacia</b>, que en el puerto significa «BROX no lo publica».
 * Propagar la excepcion habria dejado a KAIROS sin poder atender un solo turno
 * por dos endpoints que no son suyos; inventarse las respuestas habria sido
 * peor. Lo que KAIROS hace con «no publicado» esta en cada metodo del puerto, y
 * nunca es suponer.
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
    public List<Capacidad> capacidades(SesionBrox sesion, Traza traza) {
        try {
            return lista(http.get().uri("/capacidades").headers(conSesion(sesion))
                    .headers(conTraza(traza)));
        } catch (HttpClientErrorException.NotFound noExiste) {
            // El Core no publica su catalogo de capacidades. Vacio = "no
            // declarado", nunca "no puedes": quien decide sigue siendo el 403.
            return List.of();
        }
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
                .body(cuerpoDeAvance(intencion, idBorrador, datos))
                .retrieve()
                .body(EstadoCaptura.class);
    }

    /**
     * {@code Map.of} rechaza valores nulos, y {@code idBorrador} es nulo en el
     * primer turno de toda captura: abrir una es exactamente no tener borrador
     * todavia. Con {@code Map.of} el alta conversacional se caia con un
     * {@code NullPointerException} en su primer paso.
     */
    private static Map<String, Object> cuerpoDeAvance(String intencion, Long idBorrador,
                                                      Map<String, String> datos) {
        Map<String, Object> cuerpo = new java.util.LinkedHashMap<>();
        cuerpo.put("intencion", intencion);
        cuerpo.put("idBorrador", idBorrador);
        cuerpo.put("datos", datos == null ? Map.of() : datos);
        return cuerpo;
    }

    @Override
    public List<EstadoCaptura> capturasEnCurso(SesionBrox sesion, Traza traza) {
        return lista(http.get().uri("/captura").headers(conSesion(sesion))
                .headers(conTraza(traza)));
    }

    @Override
    public EstadoCaptura captura(SesionBrox sesion, long idBorrador, Traza traza) {
        return http.get().uri("/captura/{id}", idBorrador).headers(conSesion(sesion))
                .headers(conTraza(traza))
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
    public List<Coincidencia> buscarPropiedades(SesionBrox sesion, String texto, Traza traza) {
        return lista(http.get()
                .uri(uri -> uri.path("/locales").queryParam("texto", texto)
                        .queryParam("tamano", 10).build())
                .headers(conSesion(sesion)).headers(conTraza(traza)));
    }

    @Override
    public Map<String, Object> propiedad(SesionBrox sesion, long idPropiedad, Traza traza) {
        return http.get().uri("/propiedades/{id}", idPropiedad).headers(conSesion(sesion))
                .headers(conTraza(traza))
                .retrieve().body(MAPA);
    }

    @Override
    public List<Persona> buscarClientes(SesionBrox sesion, String texto, Traza traza) {
        return lista(http.get()
                .uri(uri -> uri.path("/clientes").queryParam("texto", texto)
                        .queryParam("tamano", 10).build())
                .headers(conSesion(sesion)).headers(conTraza(traza)));
    }

    @Override
    public List<Persona> buscarPropietarios(SesionBrox sesion, String texto, Traza traza) {
        return lista(http.get()
                .uri(uri -> uri.path("/propietarios").queryParam("texto", texto)
                        .queryParam("tamano", 5).build())
                .headers(conSesion(sesion)).headers(conTraza(traza)));
    }

    // ------------------------------------------------------------------
    // Matching
    // ------------------------------------------------------------------

    /**
     * Las tres puertas se piden igual: cambia el recurso, no la forma.
     *
     * <p>Se mandan {@code page} y {@code page_size} —no {@code pagina} y
     * {@code tamano}— porque son los que ganan en el Core cuando llegan los dos
     * juegos de nombres, y asi KAIROS pide con los mismos que el SPA.
     */
    @Override
    public CoincidenciasDeCartera coincidencias(SesionBrox sesion, OrigenDeCoincidencias origen,
                                                String idOrCodigo, int pagina, int tamano,
                                                Traza traza) {
        return http.get()
                .uri(uri -> uri.path("/{recurso}/{id}/coincidencias")
                        .queryParam("page", Math.max(1, pagina))
                        .queryParam("page_size", Math.max(1, tamano))
                        .build(origen.recurso(), idOrCodigo))
                .headers(conSesion(sesion))
                .headers(conTraza(traza))
                .retrieve()
                .body(CoincidenciasDeCartera.class);
    }

    // ------------------------------------------------------------------
    // El foco del Inicio
    // ------------------------------------------------------------------

    /**
     * El dashboard trae mas de lo que KAIROS puede decir en un turno —siete
     * bloques de indicadores, series por mes, embudos—. Aqui se queda con las
     * tres colecciones del foco y con dos cifras; el resto se descarta a
     * proposito: un asistente que recitara catorce KPI no estaria respondiendo
     * «que resuelvo hoy».
     */
    @Override
    public Foco foco(SesionBrox sesion, Traza traza) {
        RespuestaDelInicio respuesta = http.get()
                .uri(uri -> uri.path("/dashboard").queryParam("tamano", 5).build())
                .headers(conSesion(sesion))
                .headers(conTraza(traza))
                .retrieve()
                .body(RespuestaDelInicio.class);
        return respuesta == null ? new Foco(null, 0, List.of(), 0, List.of(), List.of())
                : respuesta.aFoco();
    }

    /** La forma del cable de {@code GET /dashboard}, solo en lo que KAIROS usa. */
    private record RespuestaDelInicio(Indicadores indicadores, PaginaDeTareas bandeja,
                                      List<HallazgoDelCable> hallazgos,
                                      List<AsuntoDelCable> focoDelBroker) {

        Foco aFoco() {
            return new Foco(
                    indicadores == null ? null : indicadores.ambito(),
                    indicadores == null ? 0 : indicadores.pendientesDeAtencion(),
                    bandeja == null ? List.of() : bandeja.aAsuntos(),
                    bandeja == null ? 0 : bandeja.totalRecords(),
                    focoDelBroker == null ? List.of()
                            : focoDelBroker.stream().map(AsuntoDelCable::aAsunto).toList(),
                    hallazgos == null ? List.of()
                            : hallazgos.stream().map(HallazgoDelCable::aHallazgo).toList());
        }
    }

    private record Indicadores(String ambito, int pendientesDeAtencion) {
    }

    private record PaginaDeTareas(List<TareaDelCable> items, int totalRecords) {

        List<Asunto> aAsuntos() {
            return items == null ? List.of() : items.stream().map(TareaDelCable::aAsunto).toList();
        }
    }

    private record TareaDelCable(Long id, String tipo, String entidadTipo, Long entidadId,
                                 String entidadCodigo, String rutaResolver, String descripcion,
                                 String prioridad, Integer diasSinAccion, boolean dependeDeMi,
                                 Interpretacion interpretacion) {

        Asunto aAsunto() {
            return new Asunto(id == null ? null : String.valueOf(id), tipo, entidadTipo, entidadId,
                    entidadCodigo, descripcion, prioridad, diasSinAccion, dependeDeMi,
                    rutaResolver, Interpretacion.lecturaDe(interpretacion));
        }
    }

    private record AsuntoDelCable(String id, String tipo, String entidadTipo, Long entidadId,
                                  String entidadCodigo, String destino, int diasEsperando,
                                  Interpretacion interpretacion) {

        AsuntoDelBroker aAsunto() {
            return new AsuntoDelBroker(id, tipo, entidadTipo, entidadId, entidadCodigo, destino,
                    diasEsperando, Interpretacion.lecturaDe(interpretacion));
        }
    }

    /**
     * La sintesis que redacta el Core. Solo se lee {@code lectura}: el resto de
     * la interpretacion —el expediente renglon a renglon— es material de
     * pantalla.
     */
    private record Interpretacion(String lectura) {

        static String lecturaDe(Interpretacion interpretacion) {
            return interpretacion == null ? null : interpretacion.lectura();
        }
    }

    private record HallazgoDelCable(String id, String tipo, String titulo, String porQue,
                                    int puntaje, List<String> cumple, List<String> noCumple,
                                    List<String> porConfirmar, String destino, Long idCliente,
                                    Long idCaptacion, String codigoCaptacion) {

        Hallazgo aHallazgo() {
            return new Hallazgo(id, tipo, titulo, porQue, puntaje, cumple, noCumple, porConfirmar,
                    destino, idCliente, idCaptacion, codigoCaptacion);
        }
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

    /**
     * <b>Los distritos salen del motor de captura, que es quien decide cuales
     * admite el campo.</b>
     *
     * <p>Aqui habia un {@code GET /locales/distritos} que <b>no existe</b>: el
     * router del Core lo resuelve como {@code GET /locales/{id}} y responde
     * <b>400</b> porque «distritos» no es un numero. Con eso, toda frase que
     * mencionara un distrito reventaba antes de llegar a ninguna parte — y son
     * casi todas: «registra un depa en Miraflores».
     *
     * <p>Se piden de donde el Core si los publica: la pregunta {@code distrito}
     * de {@code GET /captura/definicion}, con sus opciones. Y ademas es <b>la
     * lista correcta</b>: es la misma contra la que el alta va a validar. Las
     * otras candidatas —{@code /propiedades/filtros} y los {@code /resumen}—
     * publican los distritos <b>con cartera</b>, que es un subconjunto: uno
     * recien dado de alta no aparece, y KAIROS habria dejado de reconocer justo
     * el distrito nuevo.
     *
     * <p>{@link #TIPO_CON_LO_COMUN} es el tipo que se usa para preguntarlo.
     * Vale cualquiera —{@code comunes} no depende del tipo, y una prueba lo
     * comprueba sobre los siete—; se elige OTRO porque es el catalogo mas corto
     * y pedir el mas largo para leer una sola pregunta seria pagar de mas en
     * cada frase.
     */
    @Override
    public List<String> distritos(SesionBrox sesion, Traza traza) {
        DefinicionCaptura definicion = definicionDe(sesion, TIPO_CON_LO_COMUN, null, traza);
        if (definicion == null || definicion.comunes() == null) {
            return List.of();
        }
        return definicion.comunes().stream()
                .filter(pregunta -> Vocabulario.DISTRITO.equals(pregunta.clave()))
                .flatMap(pregunta -> pregunta.opciones() == null
                        ? java.util.stream.Stream.<Opcion>empty() : pregunta.opciones().stream())
                .map(Opcion::valor)
                .filter(valor -> valor != null && !valor.isBlank())
                .toList();
    }

    /** El tipo con el que se pide lo que es comun a los siete. Ver {@link #distritos}. */
    static final String TIPO_CON_LO_COMUN = "OTRO";

    /**
     * La definicion fisica: comunes + lo del tipo, sin ningun bloque economico.
     *
     * <p><b>Sin {@code operaciones} a proposito.</b> KAIROS pregunta «que tiene
     * un departamento» antes de que exista ninguna intencion comercial, y
     * mandar VENTA o ALQUILER «para que no falle» seria fingir una decision que
     * el usuario no ha tomado -- y recibir un bloque de precio que nadie pidio.
     */
    @Override
    public List<Pregunta> catalogoDe(SesionBrox sesion, String tipoPropiedad, Traza traza) {
        DefinicionCaptura definicion = definicionDe(sesion, tipoPropiedad, null, traza);
        List<Pregunta> todas = new ArrayList<>(definicion.comunes());
        todas.addAll(definicion.delTipo());
        return List.copyOf(todas);
    }

    @Override
    public DefinicionCaptura definicionDe(SesionBrox sesion, String tipoPropiedad,
                                          String operaciones, Traza traza) {
        return http.get()
                .uri(uri -> {
                    var construida = uri.path("/captura/definicion")
                            .queryParam("tipoPropiedad", tipoPropiedad);
                    if (operaciones != null && !operaciones.isBlank()) {
                        construida = construida.queryParam("operaciones", operaciones);
                    }
                    return construida.build();
                })
                .headers(conSesion(sesion))
                .headers(conTraza(traza))
                .retrieve()
                .body(DefinicionCaptura.class);
    }

    @Override
    public List<String> resultadosDeInteraccion(SesionBrox sesion, String contexto, Traza traza) {
        try {
            return lista(http.get()
                    .uri(uri -> uri.path("/interacciones/resultados")
                            .queryParam("contexto", contexto).build())
                    .headers(conSesion(sesion)).headers(conTraza(traza)));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest noExiste) {
            // La ruta no existe: el router la resuelve como /interacciones/{id}
            // y responde 400 porque "resultados" no es un numero. Vacio = "no
            // publicado", y KAIROS no se inventa el vocabulario del dominio.
            return List.of();
        }
    }

    // ------------------------------------------------------------------

    private static final ParameterizedTypeReference<Map<String, Object>> MAPA =
            new ParameterizedTypeReference<>() {
            };

    /**
     * El token de la persona. <b>Siempre el suyo</b>: KAIROS no tiene cuenta, y
     * si algun dia esta linea mandara un token de servicio, la conversacion se
     * habria convertido en un agujero de autorizacion.
     */
    private static Consumer<HttpHeaders> conSesion(SesionBrox sesion) {
        return cabeceras -> cabeceras.setBearerAuth(sesion.token());
    }

    /**
     * La procedencia, en <b>todas</b> las llamadas.
     *
     * <p>Sin traza no se llama: una peticion sin procedencia es un hecho que
     * despues no se puede atribuir a ningun turno, y ese es justo el estado que
     * D-K-1 §5 existe para impedir.
     */
    private static Consumer<HttpHeaders> conTraza(Traza traza) {
        if (traza == null) {
            throw new IllegalArgumentException(
                    "Una llamada a BROX sin traza no se puede explicar despues: de que "
                            + "conversacion salio, de que turno y por encargo de quien.");
        }
        return cabeceras -> traza.cabeceras().forEach(cabeceras::add);
    }

    private <T> List<T> lista(RestClient.RequestHeadersSpec<?> peticion) {
        @SuppressWarnings("unchecked")
        List<T> respuesta = peticion.retrieve()
                .body(new ParameterizedTypeReference<List<T>>() {
                });
        return respuesta == null ? List.of() : respuesta;
    }
}
