package com.kairos.brox;

import java.util.List;
import java.util.Map;

/**
 * <b>La unica puerta de KAIROS hacia el negocio.</b>
 *
 * <h2>La regla que esta interfaz existe para hacer imposible de olvidar</h2>
 * <pre>
 *   WhatsApp → KAIROS → API de BROX → BROX Core → PostgreSQL
 * </pre>
 * Nunca {@code KAIROS → PostgreSQL}. KAIROS no tiene el driver de la base de
 * BROX en su {@code pom.xml}, y esa ausencia es deliberada: sin driver, el
 * atajo de "leer una tabla solo para esta consulta" no esta a un import de
 * distancia, esta a una dependencia nueva que alguien tendria que justificar.
 *
 * <h2>Lo que KAIROS NO sabe, y no debe aprender</h2>
 * Que atributos aplican a un departamento, que falta para publicar, cuando un
 * encargo esta vivo, si un rol puede aprobar, o que significa VENTA. Todo eso
 * lo decide BROX y KAIROS lo <b>pregunta</b>. Si algun dia una respuesta de esta
 * interfaz se completara aqui con una regla propia, habria dos motores de
 * registro — y el segundo, el de KAIROS, seria el que nadie prueba contra la
 * base.
 *
 * <h2>Todos los metodos llevan {@link Traza}, sin excepcion</h2>
 * Incluidas las lecturas. No es simetria por gusto: la procedencia es lo que
 * permite responder «de que turno salio esto», y una firma sin traza es la
 * puerta por la que la siguiente llamada se escribe sin ella. Que el Core hoy
 * solo consuma las cabeceras en {@code /captura} y {@code /propiedades} no
 * cambia de que lado esta la obligacion: mandarla es de KAIROS, leerla es del
 * Core.
 *
 * <h2>Por que es una interfaz y no la implementacion directa</h2>
 * Para que el resto de KAIROS se pueda probar sin levantar BROX. Las pruebas
 * del adaptador usan un doble de esta interfaz y siguen comprobando lo que
 * importa: que no se infiere una operacion, que lo sensible no se ejecuta solo,
 * que no se duplica una persona.
 */
public interface ClienteBrox {

    // ------------------------------------------------------------------
    // Capacidades: que se puede hacer, con que permisos y con que autonomia
    // ------------------------------------------------------------------

    /**
     * Lo que <b>esta sesion</b> puede hacer, declarado por BROX.
     *
     * <p><b>La autonomia no se decide aqui.</b> Que una publicacion la confirme
     * una persona es una regla del negocio, no una preferencia del asistente, y
     * por eso llega declarada en vez de estar escrita en un prompt. El dia que
     * BROX cambie de opinion sobre una operacion, KAIROS obedece sin
     * desplegarse.
     *
     * <p><b>Una lista vacia significa «BROX no lo publica», no «no puedes».</b>
     * {@code GET /capacidades} todavia no existe en el Core —responde 404— y
     * tratar su ausencia como una negativa dejaria a KAIROS sin poder hacer
     * absolutamente nada, que no es lo que el Core esta diciendo. Con catalogo
     * ausente manda el catalogo de {@code com.kairos.herramientas} y decide el
     * Core con el 403 de siempre; con catalogo publicado, lo que no esta en el
     * no se intenta.
     */
    List<Capacidad> capacidades(SesionBrox sesion, Traza traza);

    /**
     * @param nombre     identificador estable de la capacidad
     * @param operacion  la operacion REST que invoca
     * @param roles      bandas que pueden pedirla
     * @param autonomia  {@code AUTO} se ejecuta sola · {@code CONFIRMA} se
     *                   prepara y la confirma una persona · {@code HUMANO} no la
     *                   ejecuta un agente en ningun caso
     */
    record Capacidad(String nombre, String operacion, List<String> roles, String autonomia) {

        public static final String AUTO = "AUTO";
        public static final String CONFIRMA = "CONFIRMA";
        public static final String HUMANO = "HUMANO";

        public boolean laConfirmaUnaPersona() {
            return CONFIRMA.equals(autonomia);
        }

        public boolean laPuedePedirUnAgente() {
            return !HUMANO.equals(autonomia);
        }
    }

    // ------------------------------------------------------------------
    // Captura: el motor de registro vive en BROX (§6)
    // ------------------------------------------------------------------

    /** Un valor admitido con su nombre. KAIROS dice el rotulo y devuelve el valor. */
    record Opcion(String valor, String rotulo) {
    }

    /**
     * Una pregunta, ya resuelta por BROX contra su catalogo. KAIROS solo la dice.
     *
     * <p>Desde el Corte 0B viene con lo que hace falta para decirla: su
     * vocabulario con rotulos, para que forma parte y cuanto hace falta. KAIROS
     * no completa nada de esto -- si tuviera que hacerlo, tendria su propia
     * matriz inmobiliaria y divergiria de BROX Web desde el primer atributo que
     * alguien anadiera.
     *
     * <p><b>Lo que este record NO trae: {@code restricciones}.</b> El Core las
     * publica —{@code minimo}, {@code maximo}, {@code longitudMaxima},
     * {@code decimales}— y KAIROS las descarta al deserializar. No es una
     * decision, es una carencia, y esta escrita aqui para que se lea como tal:
     * quien quiera decir «tiene que ser un entero mayor que cero» todavia no
     * tiene con que. Validar no cambia —lo hace BROX al anotar—, pero preguntar
     * mejor si.
     */
    record Pregunta(String clave, String rotulo, String seccion, String familia, String control,
                    String tipoDato, String unidad, List<Opcion> opciones, String exigencia,
                    boolean obligatoria, String ayuda, int orden) {
    }

    /**
     * Lo que BROX pregunta para un tipo, y opcionalmente para sus operaciones.
     *
     * <p>Sin operaciones vienen {@code deLaOperacion} vacio: la propiedad es la
     * cosa fisica y la operacion vive en el Encargo.
     */
    record DefinicionCaptura(String intencion, String tipoPropiedad, List<String> operaciones,
                             List<Pregunta> comunes, List<Pregunta> delTipo,
                             List<BloqueOperacion> deLaOperacion) {
    }

    /** Las preguntas economicas de UNA operacion, con su rotulo ya puesto. */
    record BloqueOperacion(String operacion, String rotulo, List<Pregunta> preguntas) {
    }

    /** Donde va una captura, segun BROX. KAIROS no calcula ni un solo campo de esto. */
    record EstadoCaptura(Long idBorrador, String codigo, String intencion, String estado,
                         Map<String, Object> conocido, List<String> faltante, Pregunta siguiente,
                         boolean listoParaEjecutar, Long idEntidad) {
    }

    record Ejecucion(Long idBorrador, Long idPropiedad, String codigoPropiedad,
                     List<Long> idsEncargos, boolean reintento) {
    }

    EstadoCaptura avanzarCaptura(SesionBrox sesion, String intencion, Long idBorrador,
                                 Map<String, String> datos, Traza traza);

    List<EstadoCaptura> capturasEnCurso(SesionBrox sesion, Traza traza);

    EstadoCaptura captura(SesionBrox sesion, long idBorrador, Traza traza);

    /**
     * Corre el caso de uso. La clave de idempotencia sale del mensaje del canal:
     * un webhook reenviado trae el mismo identificador, asi que reintentar y
     * acertar a la primera son indistinguibles.
     */
    Ejecucion ejecutarCaptura(SesionBrox sesion, long idBorrador, String claveIdempotencia,
                              Traza traza);

    // ------------------------------------------------------------------
    // Lecturas
    // ------------------------------------------------------------------

    /** Una coincidencia de cartera, tal como la devuelve la busqueda de BROX. */
    record Coincidencia(Long id, String codigo, String direccion, String distrito) {
    }

    record Persona(Long id, String nombre, String tipoDocumento, String numeroDocumento,
                   String telefono) {
    }

    record Interaccion(Long id, String contexto, String resultado, String canalContacto) {
    }

    List<Coincidencia> buscarPropiedades(SesionBrox sesion, String texto, Traza traza);

    Map<String, Object> propiedad(SesionBrox sesion, long idPropiedad, Traza traza);

    List<Persona> buscarClientes(SesionBrox sesion, String texto, Traza traza);

    List<Persona> buscarPropietarios(SesionBrox sesion, String texto, Traza traza);

    // ------------------------------------------------------------------
    // Matching: que encaja con que, y POR QUE
    // ------------------------------------------------------------------

    /**
     * Las tres puertas del mismo caso de uso.
     *
     * <p>El Core evalua el mismo {@code CoincidenciaCartera} en los dos
     * sentidos y devuelve <b>la misma forma</b> por las tres; lo que cambia es
     * de que se parte. Un enum en vez de tres metodos porque son un solo caso de
     * uso: tres metodos habrian invitado a que el tercero se escribiera distinto.
     *
     * @param recurso el segmento de la ruta del Core
     */
    enum OrigenDeCoincidencias {
        /** Cliente → propiedades compatibles. Es el sentido de un requerimiento. */
        CLIENTE("clientes"),
        /** Encargo → clientes compatibles. Accionable: se puede proponer. */
        CAPTACION("captaciones"),
        /** Prospeccion → clientes. Senal temprana, antes de que exista encargo. */
        PROSPECCION("prospecciones");

        private final String recurso;

        OrigenDeCoincidencias(String recurso) {
            this.recurso = recurso;
        }

        public String recurso() {
            return recurso;
        }
    }

    /**
     * <b>Por que encaja, en numeros y en criterios.</b>
     *
     * <p>Es lo que convierte un porcentaje en una frase util: <i>«cumple 4 de 5;
     * falta confirmar la potencia electrica»</i>. KAIROS <b>no compone la
     * frase</b> —eso es de quien habla— ni recalcula el puntaje: cuenta lo que
     * el Core dijo y lo entrega separado.
     *
     * <h2>{@code porConfirmar} todavia no llega, y por eso esta aqui</h2>
     * El matcher del Core devuelve hoy dos cubos, {@code cumple} y
     * {@code noCumple}, y descarta en silencio el criterio que <b>no aplica</b>
     * por falta de dato. Ese tercer cubo es justo el que hace explicable un
     * match —«no es que falle: es que nadie ha dicho la potencia»— y esta
     * cambiandose en otro paquete. Cuando llegue, esta clase ya lo lee; mientras
     * no llegue, {@code porConfirmar} viene vacio y {@code evaluados} vale
     * {@code cumple + noCumple}, que es exactamente lo que el Core esta
     * evaluando hoy.
     *
     * @param puntaje    el del Core. No se recalcula
     * @param cumplidos  cuantos criterios cumple
     * @param evaluados  sobre cuantos. Nunca incluye los que el Core descarto
     */
    record Explicacion(int puntaje, int cumplidos, int evaluados, List<String> cumple,
                       List<String> noCumple, List<String> porConfirmar) {

        public static Explicacion de(int puntaje, List<String> cumple, List<String> noCumple,
                                     List<String> porConfirmar) {
            List<String> si = lista(cumple);
            List<String> no = lista(noCumple);
            List<String> falta = lista(porConfirmar);
            return new Explicacion(puntaje, si.size(), si.size() + no.size() + falta.size(),
                    si, no, falta);
        }

        private static List<String> lista(List<String> valores) {
            return valores == null ? List.of() : List.copyOf(valores);
        }
    }

    /**
     * Un item del matching, con la forma congelada del cable.
     *
     * <p>{@code porConfirmar} no lo manda el Core todavia: llega {@code null} y
     * se lee como lista vacia. <b>Ausente y vacio son lo mismo aqui</b> —no hay
     * criterio pendiente— y por eso se pueden confundir sin perder nada; el dia
     * que el Core lo mande, esta clase no cambia.
     */
    record CoincidenciaDeCartera(String tipo, Long id, String codigo, String titulo,
                                 String subtitulo, String distrito, String renta, String area,
                                 String frente, int puntaje, List<String> cumple,
                                 List<String> noCumple, List<String> porConfirmar, Long clienteId,
                                 Long captacionId, String proponerRuta) {

        public Explicacion explicacion() {
            return Explicacion.de(puntaje, cumple, noCumple, porConfirmar);
        }
    }

    /** La pagina de coincidencias. {@code origen} dice cual de las tres puertas la produjo. */
    record CoincidenciasDeCartera(String origen, int total, int page, int pageSize,
                                  List<CoincidenciaDeCartera> items) {

        public CoincidenciasDeCartera {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    CoincidenciasDeCartera coincidencias(SesionBrox sesion, OrigenDeCoincidencias origen,
                                         String idOrCodigo, int pagina, int tamano, Traza traza);

    // ------------------------------------------------------------------
    // El foco del Inicio
    // ------------------------------------------------------------------

    /**
     * Algo que <b>hay que resolver</b>. Sale de la bandeja del agente.
     *
     * <p>{@code lectura} es la sintesis que redacta el Core —la que relaciona
     * dos hechos y dice para que sirve atenderlo—. Viene ya escrita a proposito:
     * si la compusiera KAIROS, la pantalla diria una cosa y la conversacion
     * otra sobre el mismo asunto.
     */
    record Asunto(String id, String tipo, String entidadTipo, Long entidadId, String entidadCodigo,
                  String descripcion, String prioridad, Integer diasSinAccion, boolean dependeDeMi,
                  String rutaResolver, String lectura) {
    }

    /**
     * Algo que <b>solo el broker</b> puede decidir.
     *
     * <p>Va aparte de {@link Asunto} porque no es la bandeja del agente vista
     * por otro rol: son decisiones de otra naturaleza (D-E2-5). Juntarlas seria
     * ofrecerle a cada uno lo que el otro tiene que hacer.
     */
    record AsuntoDelBroker(String id, String tipo, String entidadTipo, Long entidadId,
                           String entidadCodigo, String destino, int diasEsperando,
                           String lectura) {
    }

    /**
     * Algo que <b>vale la pena mirar</b>. Una tarea reclama; un hallazgo propone.
     *
     * <p>Trae {@code cumple}/{@code noCumple} porque sale del mismo matcher: es
     * la <b>segunda puerta</b> a la misma explicacion, y por eso la construye el
     * mismo {@link Explicacion#de}. Con dos construcciones distintas, la frase
     * de un hallazgo y la de una coincidencia empezarian a contar distinto.
     */
    record Hallazgo(String id, String tipo, String titulo, String porQue, int puntaje,
                    List<String> cumple, List<String> noCumple, List<String> porConfirmar,
                    String destino, Long idCliente, Long idCaptacion, String codigoCaptacion) {

        public Explicacion explicacion() {
            return Explicacion.de(puntaje, cumple, noCumple, porConfirmar);
        }
    }

    /**
     * El foco del Inicio, con el alcance de quien pregunta.
     *
     * <p>Tres colecciones y no una filtrada: la bandeja es del AGENTE —el broker
     * la recibe vacia y eso no es un 403—, los asuntos son del BROKER, y los
     * hallazgos los tienen los dos con naturaleza distinta segun el rol.
     */
    record Foco(String ambito, int pendientesDeAtencion, List<Asunto> bandeja, int totalBandeja,
                List<AsuntoDelBroker> asuntosDelBroker, List<Hallazgo> hallazgos) {

        public Foco {
            bandeja = bandeja == null ? List.of() : List.copyOf(bandeja);
            asuntosDelBroker = asuntosDelBroker == null ? List.of() : List.copyOf(asuntosDelBroker);
            hallazgos = hallazgos == null ? List.of() : List.copyOf(hallazgos);
        }

        /** Nada que resolver <b>hoy</b>. No es lo mismo que no tener trabajo. */
        public boolean vacio() {
            return bandeja.isEmpty() && asuntosDelBroker.isEmpty() && hallazgos.isEmpty();
        }
    }

    /**
     * Lo que hay que resolver hoy.
     *
     * <p><b>Este GET escribe</b>: la bandeja se deriva y se reconcilia antes de
     * devolverse. No es una lectura pura aunque lo parezca, y por eso no se
     * llama de adorno.
     */
    Foco foco(SesionBrox sesion, Traza traza);

    // ------------------------------------------------------------------
    // Escrituras
    // ------------------------------------------------------------------

    Persona registrarPropietario(SesionBrox sesion, Map<String, String> datos, Traza traza);

    Interaccion registrarInteraccion(SesionBrox sesion, Map<String, Object> datos, Traza traza);

    // ------------------------------------------------------------------
    // Vocabulario del tenant
    // ------------------------------------------------------------------

    /**
     * Los distritos dados de alta en la organizacion.
     *
     * <p>KAIROS los necesita para reconocer "en Miraflores" en una frase, y no
     * puede llevar una lista propia: los distritos son datos del tenant y una
     * copia aqui envejeceria en silencio, reconociendo distritos que ya no
     * existen e ignorando los nuevos.
     */
    List<String> distritos(SesionBrox sesion, Traza traza);

    /**
     * Que se pregunta para un tipo de propiedad, derivado del catalogo del
     * tenant. Incluye los atributos comunes y los que esa organizacion se creo.
     *
     * <p>Desde el Corte 0B sale de {@code GET /captura/definicion} <b>sin
     * operaciones</b>, que es la misma maquina que usa BROX Web. Antes habia un
     * endpoint aparte, {@code /propiedades/catalogo/{tipo}}, que respondia a la
     * misma pregunta con la mitad de los campos: dos productores de la misma
     * autoridad divergen, y de hecho ya lo hacian en el {@code orden}.
     *
     * <p><b>Comunes + del tipo, y las comunes no salen de la tabla.</b>
     * {@code latitud} y {@code longitud} las aporta {@code GuionRegistroPropiedad}
     * —Java escrito a mano—, asi que toda afirmacion sobre lo que KAIROS ve se
     * mide desde aqui y nunca desde {@code catalogo_atributo}: una consulta SQL
     * al catalogo no ve la mitad de lo que este metodo devuelve.
     */
    List<Pregunta> catalogoDe(SesionBrox sesion, String tipoPropiedad, Traza traza);

    /**
     * La definicion completa, con los bloques economicos de las operaciones que
     * se declaren. Sin operaciones, solo la cosa fisica.
     */
    DefinicionCaptura definicionDe(SesionBrox sesion, String tipoPropiedad, String operaciones,
                                   Traza traza);

    /**
     * Los resultados que admite una interaccion de ese contexto.
     *
     * <p>El vocabulario depende del contexto y lo decide BROX. KAIROS lo pide
     * para poder ofrecer la lista exacta en vez de deducir un resultado del
     * tono de la frase, que es como se acaba anotando "muy bien" donde el
     * dominio esperaba ACEPTA_CAPTAR.
     *
     * <p><b>Hoy devuelve vacio siempre</b>, y no porque no haya vocabulario: es
     * que el Core no lo publica. {@code GET /interacciones/resultados} choca con
     * {@code GET /interacciones/{id}} y responde 400. Lista vacia significa «no
     * publicado», y lo que KAIROS hace entonces es mandar lo que la frase dijo y
     * dejar que BROX lo rechace con su explicacion — nunca inventarse la lista.
     */
    List<String> resultadosDeInteraccion(SesionBrox sesion, String contexto, Traza traza);
}
