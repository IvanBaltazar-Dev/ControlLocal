package com.kairos.herramientas;

import com.kairos.herramientas.Herramienta.Estado;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <b>El catalogo de D-K-1 §3, como codigo.</b>
 *
 * <h2>Por que existe siendo ya un documento</h2>
 * Porque una tabla en un documento no impide que alguien anada una llamada al
 * Core sin decir por que caso de uso entra ni que se confirma. Aqui cada
 * herramienta tiene que declarar las cuatro cosas o no compila, y las que
 * todavia no se implementan se declaran <b>igual</b>: el dia que se implementen
 * no se vuelve a decidir si publicar sale al exterior.
 *
 * <h2>Lo que este catalogo NO es</h2>
 * <ul>
 *   <li><b>No es la autorizacion.</b> Quien puede lo decide el Core con el
 *       token de la persona; {@code permiso} es la celda de la matriz copiada
 *       para poder compararla, no una comprobacion.</li>
 *   <li><b>No es la lista de capacidades de la sesion.</b> Esa la publica el
 *       Core —cuando exista {@code GET /capacidades}, que hoy responde 404— y
 *       solo puede <b>restringir</b>: puede exigir confirmacion donde el
 *       catalogo no la exige, y no puede quitarla donde si.</li>
 *   <li><b>No es un segundo motor.</b> Ninguna herramienta tiene logica: cada
 *       una nombra un metodo y una ruta que ya existen.</li>
 * </ul>
 *
 * <h2>Lo que este catalogo declara y D-K-1 §3 no</h2>
 * {@link #BUSCAR_PERSONA} y {@link #REGISTRAR_PROPIETARIO} las invoca KAIROS
 * desde antes de este catalogo, y {@link #CAPTURAS_EN_CURSO} es la lectura con
 * la que se retoma un borrador. Estan declaradas porque <b>omitirlas seria
 * mentir por ausencia</b>: el catalogo tiene que decir todo lo que KAIROS sabe
 * invocar, no solo lo que el documento previo alcanzo a nombrar.
 *
 * <p>Por esa misma regla estan las <b>tres lecturas de contrato</b>
 * —{@link #CONSULTAR_CAPACIDADES}, {@link #CATALOGO_DE_CAPTURA} y
 * {@link #RESULTADOS_DE_INTERACCION}—, que no son intenciones que nadie
 * exprese: son lo que KAIROS le pregunta al Core para poder preguntar bien.
 * Faltaban, y su ausencia tenia dos consecuencias medibles: tres llamadas
 * salian sin {@code X-Herramienta}, y la del vocabulario de interacciones
 * viajaba etiquetada como {@code registrar_interaccion} —una lectura firmada
 * con el nombre de una escritura—.
 */
public final class Herramientas {

    // ==================================================================
    // 3.1 Lectura. Nunca necesitan confirmacion: no escriben.
    // ==================================================================

    /**
     * Buscar en la cartera por texto.
     *
     * <p>Va por {@code /locales} y no por {@code /propiedades} —que es la que
     * nombra D-K-1— porque es la ruta que KAIROS invoca hoy. <b>Desde el
     * 2026-09-02 las dos comparten motor de busqueda</b>
     * ({@code MotorBusquedaInmobiliaria}), asi que el texto encuentra lo mismo;
     * lo que cambia es la forma de la respuesta. Declararla con la ruta del
     * documento y llamar a otra habria hecho del catalogo un adorno.
     */
    public static final Herramienta BUSCAR_PROPIEDADES = new Herramienta(
            "buscar_propiedades", "GET", "/locales", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    /** La ficha completa por el modelo universal: titulares, atributos y encargos. */
    public static final Herramienta ESTADO_DE_PROPIEDAD = new Herramienta(
            "estado_de_propiedad", "GET", "/propiedades/{id}", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    /** No esta en D-K-1 §3. Se declara porque KAIROS ya la invoca. */
    public static final Herramienta BUSCAR_PERSONA = new Herramienta(
            "buscar_persona", "GET", "/clientes", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    /**
     * <b>Que encaja con que, y por que.</b>
     *
     * <p>La ruta lleva {@code {origen}} porque el mismo caso de uso
     * —{@code CoincidenciaCartera}— tiene <b>tres puertas</b> en el Core, y las
     * tres devuelven la misma forma:
     * <pre>
     *   GET /clientes/{id}/coincidencias        cliente    → propiedades
     *   GET /captaciones/{idOrCodigo}/coincidencias  encargo → clientes
     *   GET /prospecciones/{id}/coincidencias   prospeccion → clientes
     * </pre>
     * Declararlas como tres herramientas habria multiplicado por tres una
     * decision que es una: leer coincidencias no escribe nada y lo alcanza
     * quien alcanza el origen.
     */
    public static final Herramienta BUSCAR_COINCIDENCIAS = new Herramienta(
            "buscar_coincidencias", "GET", "/{origen}/{id}/coincidencias", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    /**
     * Los criterios cumplidos e incumplidos de UNA coincidencia.
     *
     * <p><b>Es la misma lectura</b> que {@link #BUSCAR_COINCIDENCIAS}: el Core
     * devuelve la explicacion dentro de cada item, asi que explicar no es una
     * consulta nueva sino quedarse con la que la frase nombra. Una ruta propia
     * para explicar habria significado evaluar el match dos veces, y la segunda
     * evaluacion es la que empieza a discrepar de la primera.
     */
    public static final Herramienta EXPLICAR_MATCH = new Herramienta(
            "explicar_match", "GET", "/{origen}/{id}/coincidencias", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    /**
     * <b>El foco del Inicio</b>: lo que hay que resolver hoy, con la politica de
     * despacho del Core.
     *
     * <p>Va por {@code /dashboard} y no por {@code /tareas} a proposito:
     * {@code /tareas} es <b>solo del AGENTE</b> —el unico recurso sin acceso de
     * ADMIN—, y {@code que_resolver_hoy} tiene que responderle tambien al
     * broker, cuyo foco son <i>sus</i> asuntos y no la bandeja de nadie
     * (D-E2-5). El dashboard compone las dos colas y los hallazgos con el
     * alcance de cada rol.
     *
     * <p><b>Este GET escribe.</b> La bandeja se deriva y se reconcilia antes de
     * devolverse: no es una lectura pura aunque sea un GET, y por eso no se
     * llama «por si acaso» en cada turno.
     */
    public static final Herramienta QUE_RESOLVER_HOY = new Herramienta(
            "que_resolver_hoy", "GET", "/dashboard", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    /** No esta en D-K-1 §3: es la lectura con la que se retoma lo que quedo a medias. */
    public static final Herramienta CAPTURAS_EN_CURSO = new Herramienta(
            "capturas_en_curso", "GET", "/captura", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    // ------------------------------------------------------------------
    // Lecturas de contrato: lo que KAIROS pregunta para poder preguntar bien
    // ------------------------------------------------------------------

    /**
     * <b>Que puede hacer esta sesion</b>, segun el Core.
     *
     * <p>KAIROS la invoca en <b>todos</b> los turnos, antes de decidir nada. No
     * esta en D-K-1 §3 porque no es una intencion que nadie exprese; se declara
     * porque sale por el cable, y una llamada que sale en cada turno sin figurar
     * en el catalogo es exactamente lo que el catalogo existe para impedir.
     *
     * <p><b>El Core no la sirve todavia.</b> Su ausencia se lee como «no
     * publicado» —manda entonces este catalogo y decide el Core con su 403—, y
     * nunca como «no puedes»: leerla como negativa dejaria a KAIROS sin poder
     * atender un solo turno. Lo dice {@code ClienteBrox.capacidades}.
     */
    public static final Herramienta CONSULTAR_CAPACIDADES = new Herramienta(
            "consultar_capacidades", "GET", "/capacidades", Herramienta.SIN_FILA,
            Confirmacion.NUNCA, Estado.SIN_PUERTA_EN_EL_CORE);

    /**
     * <b>Que se pregunta para un tipo de propiedad</b>, derivado del catalogo del
     * tenant.
     *
     * <p>La invoca el interprete, no el adaptador: es lo que le permite
     * reconocer «3 dormitorios» sin llevar una matriz «tipo → campos» propia. Y
     * es la misma ruta con la que se piden los distritos, porque los distritos
     * son las opciones de una de sus preguntas.
     */
    public static final Herramienta CATALOGO_DE_CAPTURA = new Herramienta(
            "catalogo_de_captura", "GET", "/captura/definicion", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    /**
     * <b>Que resultados admite una interaccion de ese contexto.</b>
     *
     * <p><b>El Core no la sirve.</b> {@code GET /interacciones/resultados} choca
     * con {@code GET /interacciones/{id}}, que espera un numero, y responde
     * <b>400</b>. La lista vacia se lee como «no publicado» y KAIROS manda lo
     * que la frase dijo para que sea BROX quien lo rechace con su explicacion —
     * nunca inventarse la lista.
     *
     * <p>Se declara aparte de {@link #REGISTRAR_INTERACCION} porque es otra cosa:
     * una <b>lectura</b> de vocabulario. Iba etiquetada con el nombre de la
     * escritura, y un rastro que firma una consulta como si fuera un alta
     * responde mal a la unica pregunta que existe para responder.
     */
    public static final Herramienta RESULTADOS_DE_INTERACCION = new Herramienta(
            "resultados_de_interaccion", "GET", "/interacciones/resultados", Herramienta.SIN_FILA,
            Confirmacion.NUNCA, Estado.SIN_PUERTA_EN_EL_CORE);

    // ==================================================================
    // 3.2 Escritura reversible. Se ejecutan y se avisan.
    // ==================================================================

    /**
     * Abrir el alta conversacional. <b>No escribe nada del negocio</b>: anota lo
     * conocido y responde que falta.
     */
    public static final Herramienta ABRIR_SESION_CAPTURA = new Herramienta(
            "abrir_sesion_captura", "POST", "/captura", Herramienta.TODOS,
            Confirmacion.AVISA, Estado.VIVA);

    /**
     * Contestar una pregunta del alta. Misma ruta que abrirla; lo que las
     * distingue es que esta lleva {@code idBorrador}.
     *
     * <p>D-K-1 §3.2 la nombra {@code POST …/respuestas} y esa ruta no existe:
     * el motor del Core es <b>un solo</b> {@code POST /captura} que abre o
     * continua. Se declara la que existe.
     */
    public static final Herramienta RESPONDER_CAPTURA = new Herramienta(
            "responder_captura", "POST", "/captura", Herramienta.TODOS,
            Confirmacion.AVISA, Estado.VIVA);

    /** Una nota de un contacto. Se corrige editandola. */
    public static final Herramienta REGISTRAR_INTERACCION = new Herramienta(
            "registrar_interaccion", "POST", "/interacciones", Herramienta.AGENTE,
            Confirmacion.AVISA, Estado.VIVA);

    /** No esta en D-K-1 §3. Se declara porque KAIROS ya la invoca. */
    public static final Herramienta REGISTRAR_PROPIETARIO = new Herramienta(
            "registrar_propietario", "POST", "/propietarios", Herramienta.AGENTE,
            Confirmacion.AVISA, Estado.VIVA);

    // ==================================================================
    // 3.3 Escritura sensible. SIEMPRE la confirma una persona.
    // ==================================================================

    /**
     * Ejecutar el alta: crea la propiedad, su titularidad y —si se declaro
     * alguna operacion— sus encargos.
     *
     * <p>Es la unica sensible implementada, y es la que prueba que la regla 4
     * se sostiene: el turno que completa el borrador <b>propone</b>, y ejecutar
     * exige otro turno con la persona diciendo que si.
     */
    public static final Herramienta CONFIRMAR_CAPTURA = new Herramienta(
            "confirmar_captura", "POST", "/captura/{id}/ejecutar", Herramienta.AGENTE,
            Confirmacion.SIEMPRE, Estado.VIVA);

    /** Sale al exterior: el anuncio lo ve gente de fuera de la organizacion. */
    public static final Herramienta PUBLICAR_ENCARGO = new Herramienta(
            "publicar_encargo", "POST", "/encargos/{idEncargo}/publicaciones", Herramienta.AGENTE,
            Confirmacion.SIEMPRE, Estado.DECLARADA);

    /** Compromete un numero frente al cliente: es un hito de la serie economica. */
    public static final Herramienta PRESENTAR_OFERTA = new Herramienta(
            "presentar_oferta", "POST", "/locales/{id}/precios", Herramienta.AGENTE,
            Confirmacion.SIEMPRE, Estado.DECLARADA);

    /** Es una firma del broker: quien entra a cartera. */
    public static final Herramienta DECIDIR_CAPTACION = new Herramienta(
            "decidir_captacion", "POST", "/captaciones/{id}/decision", Herramienta.BROKER,
            Confirmacion.SIEMPRE, Estado.DECLARADA);

    /** Es una firma del broker: la decision que desemboca en contrato y comision. */
    public static final Herramienta EVALUAR_SOLICITUD = new Herramienta(
            "evaluar_solicitud", "POST", "/evaluaciones", Herramienta.BROKER,
            Confirmacion.SIEMPRE, Estado.DECLARADA);

    /** Termina una relacion comercial y devuelve el inmueble al mercado o no. */
    public static final Herramienta CERRAR_ENCARGO = new Herramienta(
            "cerrar_encargo", "POST", "/captaciones/{id}/cierre", Herramienta.BROKER,
            Confirmacion.SIEMPRE, Estado.DECLARADA);

    /** Es dinero: evidencia economica de cobro, pago al agente o reversion. */
    public static final Herramienta REGISTRAR_COBRO = new Herramienta(
            "registrar_cobro", "POST", "/contratos/{idContrato}/comision/movimientos",
            Herramienta.BROKER, Confirmacion.SIEMPRE, Estado.DECLARADA);

    /** Sale al exterior: lo recibe el propietario. */
    public static final Herramienta ENVIAR_REPORTE = new Herramienta(
            "enviar_reporte", "POST", "/captaciones/{idCaptacion}/reportes-propietario",
            Herramienta.AGENTE, Confirmacion.SIEMPRE, Estado.DECLARADA);

    // ==================================================================

    private static final Map<String, Herramienta> POR_NOMBRE = indexar(List.of(
            BUSCAR_PROPIEDADES, ESTADO_DE_PROPIEDAD, BUSCAR_PERSONA, BUSCAR_COINCIDENCIAS,
            EXPLICAR_MATCH, QUE_RESOLVER_HOY, CAPTURAS_EN_CURSO,
            CONSULTAR_CAPACIDADES, CATALOGO_DE_CAPTURA, RESULTADOS_DE_INTERACCION,
            ABRIR_SESION_CAPTURA, RESPONDER_CAPTURA, REGISTRAR_INTERACCION, REGISTRAR_PROPIETARIO,
            CONFIRMAR_CAPTURA, PUBLICAR_ENCARGO, PRESENTAR_OFERTA, DECIDIR_CAPTACION,
            EVALUAR_SOLICITUD, CERRAR_ENCARGO, REGISTRAR_COBRO, ENVIAR_REPORTE));

    private Herramientas() {
    }

    /** Todas, en el orden de D-K-1 §3: primero las que leen. */
    public static List<Herramienta> todas() {
        return List.copyOf(POR_NOMBRE.values());
    }

    /** Las que KAIROS sabe invocar hoy, sirva el Core la ruta o no. */
    public static List<Herramienta> implementadas() {
        return POR_NOMBRE.values().stream().filter(Herramienta::implementada).toList();
    }

    /** Las declaradas y todavia no implementadas: existen para no volver a decidirlas. */
    public static List<Herramienta> declaradas() {
        return POR_NOMBRE.values().stream()
                .filter(h -> h.estado() == Estado.DECLARADA)
                .toList();
    }

    /**
     * Las que KAIROS invoca y el Core no sirve. <b>Es la lista de lo que le
     * falta al Core</b>, sacada del catalogo y no de un documento aparte.
     */
    public static List<Herramienta> sinPuertaEnElCore() {
        return POR_NOMBRE.values().stream()
                .filter(Herramienta::sinPuertaEnElCore)
                .toList();
    }

    public static Optional<Herramienta> de(String nombre) {
        return nombre == null ? Optional.empty()
                : Optional.ofNullable(POR_NOMBRE.get(nombre.trim()));
    }

    private static Map<String, Herramienta> indexar(List<Herramienta> herramientas) {
        Map<String, Herramienta> indice = new LinkedHashMap<>();
        for (Herramienta herramienta : herramientas) {
            Herramienta repetida = indice.put(herramienta.nombre(), herramienta);
            if (repetida != null) {
                throw new IllegalStateException(
                        "Dos herramientas con el nombre \"" + herramienta.nombre() + "\": el "
                                + "nombre es el identificador con el que se pide y con el que se "
                                + "audita, y repetirlo hace que una de las dos no se pueda nombrar.");
            }
        }
        // unmodifiableMap y no Map.copyOf: el orden de declaracion es el de
        // D-K-1 §3 —primero lo que lee, luego lo reversible, luego lo sensible—
        // y Map.copyOf no lo conserva.
        return Collections.unmodifiableMap(indice);
    }
}
