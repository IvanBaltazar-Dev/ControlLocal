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
     */
    List<Capacidad> capacidades(SesionBrox sesion);

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

    /** Una pregunta, ya resuelta por BROX contra su catalogo. KAIROS solo la dice. */
    record Pregunta(String clave, String rotulo, String tipoDato, String unidad,
                    List<String> opciones, boolean obligatoria, String ayuda) {
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

    List<EstadoCaptura> capturasEnCurso(SesionBrox sesion);

    EstadoCaptura captura(SesionBrox sesion, long idBorrador);

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

    List<Coincidencia> buscarPropiedades(SesionBrox sesion, String texto);

    Map<String, Object> propiedad(SesionBrox sesion, long idPropiedad);

    List<Persona> buscarClientes(SesionBrox sesion, String texto);

    List<Persona> buscarPropietarios(SesionBrox sesion, String texto);

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
    List<String> distritos(SesionBrox sesion);

    /**
     * Que se pregunta para un tipo de propiedad, derivado del catalogo del
     * tenant. Incluye los atributos comunes y los que esa organizacion se creo.
     */
    List<Pregunta> catalogoDe(SesionBrox sesion, String tipoPropiedad);

    /**
     * Los resultados que admite una interaccion de ese contexto.
     *
     * <p>El vocabulario depende del contexto y lo decide BROX. KAIROS lo pide
     * para poder ofrecer la lista exacta en vez de deducir un resultado del
     * tono de la frase, que es como se acaba anotando "muy bien" donde el
     * dominio esperaba ACEPTA_CAPTAR.
     */
    List<String> resultadosDeInteraccion(SesionBrox sesion, String contexto);
}
