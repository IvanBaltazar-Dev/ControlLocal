package com.controllocal.service.captura;

import com.controllocal.service.Actor;
import com.controllocal.service.soporte.Procedencia;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <b>Que intenta hacer el usuario, que sabemos, que falta y que se pregunta
 * ahora</b> (D-E4-2).
 *
 * <h2>Por que esto es backend y no un formulario</h2>
 * <pre>
 *   Angular ────┐
 *               ├── service/captura ──▶ casos de uso
 *   KAIROS  ────┘
 * </pre>
 * Las reglas de que se pregunta para cada tipo de propiedad salen del catalogo
 * y viven <b>aqui</b>. Si vivieran en Angular habria dos copias divergiendo
 * desde el primer dia, y la tercera llegaria con el canal de WhatsApp. Angular
 * <i>representa</i> la pregunta; no la decide. KAIROS hara exactamente lo mismo.
 *
 * <h2>Lo que el motor NO es</h2>
 * No es un asistente ni un chat. No entiende lenguaje natural, no propone
 * valores y no adivina. Recibe pares clave/valor y responde tres cosas: que
 * tiene, que le falta y cual es la siguiente pregunta. Comprender la frase
 * <i>"un depa en Miraflores a 180 mil"</i> es trabajo de KAIROS; convertirlo en
 * un alta correcta es trabajo de esto.
 *
 * <h2>El borrador es el estado, no la conversacion</h2>
 * Cada avance se persiste en {@code borrador_captura}. Es lo que permite que
 * una captura empezada por KAIROS la termine alguien en la pantalla, y que
 * perder el contexto del modelo no cueste los ocho datos que ya se habian
 * dictado. El historial conversacional es de KAIROS y puede perderse; el
 * estado transaccional es de BROX y no.
 */
public interface MotorDeCaptura {

    /** La unica intencion de esta tanda. Crece con los casos de uso. */
    String REGISTRAR_PROPIEDAD = "REGISTRAR_PROPIEDAD";

    /**
     * Una pregunta concreta, ya resuelta contra el catalogo.
     *
     * <p>{@code tipoDato} y {@code unidad} vienen de {@code catalogo_atributo}
     * para que el cliente sepa <b>como</b> pedirlo — un numero con teclado
     * numerico, un si/no con un interruptor — sin tener que saberse la tabla.
     * {@code opciones} solo viene cuando el valor es de un conjunto cerrado.
     */
    record Pregunta(String clave, String rotulo, String familia, String control, String tipoDato,
                    String unidad, List<String> opciones, boolean obligatoria, String ayuda,
                    int orden, Restricciones restricciones) {

        /** A qué familia pertenece. Decide qué se conserva al cambiar la selección. */
        public static final String FAMILIA_COMUN = "COMUN";
        /** Las que deciden el plan: hasta responderlas no hay nada más que preguntar. */
        public static final String FAMILIA_APERTURA = "APERTURA";
        public static final String FAMILIA_TIPO = "TIPO";
        public static final String FAMILIA_OPERACION = "OPERACION";

        /** Constructor breve: deriva el control y no declara restricciones. */
        public Pregunta(String clave, String rotulo, String tipoDato, String unidad,
                        List<String> opciones, boolean obligatoria, String ayuda) {
            this(clave, rotulo, FAMILIA_COMUN, controlDe(tipoDato, unidad, opciones), tipoDato,
                    unidad, opciones, obligatoria, ayuda, 0, null);
        }

        /** La misma pregunta, colocada en su familia y su orden. */
        public Pregunta en(String familia, int orden) {
            return new Pregunta(clave, rotulo, familia, control, tipoDato, unidad, opciones,
                    obligatoria, ayuda, orden, restricciones);
        }

        /**
         * <b>Cómo se pinta</b>, derivado de qué es. Va aparte de
         * {@code tipoDato} a propósito: aquel es el tipo del DOMINIO —lo que la
         * base guarda— y este es el control de la interfaz. Un `DECIMAL` con
         * unidad `moneda` y un `DECIMAL` con unidad `m2` se guardan igual y se
         * piden distinto.
         *
         * <p>Que lo derive el backend es lo que impide que Angular acabe con un
         * {@code if (clave === 'piso')}: el cliente conoce controles genéricos,
         * nunca qué campo pertenece a qué tipo de propiedad.
         */
        public static String controlDe(String tipoDato, String unidad, List<String> opciones) {
            // Va antes que SELECTOR porque tambien trae opciones. La diferencia
            // es que admite mas de una, y de ahi salen los dos encargos de una
            // propiedad que se ofrece para venta y para alquiler.
            if ("LISTA_MULTIPLE".equals(tipoDato)) {
                return "SELECTOR_MULTIPLE";
            }
            // Un control compuesto: busca personas ya registradas, admite
            // varias y reparte cuotas. El cliente lo dibuja porque el contrato
            // se lo pide, no porque reconozca la clave `titulares`.
            if ("TITULARES".equals(tipoDato)) {
                return "TITULARES";
            }
            if (opciones != null && !opciones.isEmpty()) {
                return "SELECTOR";
            }
            if ("BOOLEANO".equals(tipoDato)) {
                return "INTERRUPTOR";
            }
            if ("moneda".equals(unidad)) {
                return "MONEDA";
            }
            if ("ENTERO".equals(tipoDato)) {
                return "ENTERO";
            }
            if ("DECIMAL".equals(tipoDato)) {
                return "DECIMAL";
            }
            return "TEXTO";
        }
    }

    /**
     * Los límites de un valor, para que el cliente no se los invente.
     *
     * <p>Todo es opcional: un campo sin restricciones declaradas viaja con
     * {@code null} y el cliente solo valida lo que el contrato afirme. Lo que
     * NO puede hacer es deducirlas del nombre del campo.
     */
    record Restricciones(java.math.BigDecimal minimo, java.math.BigDecimal maximo,
                         Integer longitudMaxima, Integer decimales) {
    }

    /**
     * El estado de una captura.
     *
     * @param conocido          lo que ya se sabe, por clave
     * @param faltante          lo que falta, en el orden en que se preguntara
     * @param siguiente         la primera de {@code faltante}, ya resuelta;
     *                          {@code null} cuando no falta nada
     * @param listoParaEjecutar {@code true} cuando el caso de uso ya puede
     *                          correr. Es la respuesta a "¿tenemos suficiente?"
     * @param idEntidad         lo que produjo, una vez ejecutado
     */
    record EstadoCaptura(Long idBorrador, String codigo, String intencion, String estado,
                         String canal, Map<String, Object> conocido, List<String> faltante,
                         Pregunta siguiente, boolean listoParaEjecutar, String entidadTipo,
                         Long idEntidad, LocalDateTime actualizadoEn) {
    }

    /** Lo que produjo una captura ejecutada. */
    /**
     * <b>Qué se pregunta para un tipo + operación, en tres familias.</b>
     *
     * <p>Existe para que el cliente no tenga que saberlo. Sin esto, Angular
     * necesitaría su propia matriz «tipo → campos» y KAIROS otra, y las dos
     * empezarían a divergir de la real —que es el catálogo— desde el primer día.
     * La regla es que <b>el backend decide qué dato aplica</b> y el cliente solo
     * representa la pregunta.
     *
     * <p>Las tres familias no son una comodidad de maquetación: son distintas de
     * verdad y se comportan distinto al cambiar la selección.
     *
     * <ul>
     *   <li>{@code comunes} — identidad, ubicación y titularidad. Las tiene un
     *       terreno igual que un departamento, así que <b>sobreviven</b> a un
     *       cambio de tipo o de operación.</li>
     *   <li>{@code delTipo} — lo que depende del tipo físico: dormitorios en una
     *       vivienda, rubro en un local, zonificación en un terreno. Al cambiar
     *       el tipo, <b>lo que ya no aplica se descarta</b>: dejarlo oculto con
     *       su valor guardaría el rubro de un terreno.</li>
     *   <li>{@code deLaOperacion} — <b>un bloque por encargo</b>: el importe, su
     *       moneda y la exclusividad. El importe existe en las dos operaciones
     *       pero no significa lo mismo, y por eso {@code rotulo} viene
     *       calculado: «Precio de venta» o «Renta mensual», nunca un genérico
     *       que obligue a adivinar.</li>
     * </ul>
     *
     * <p><b>Por qué {@code deLaOperacion} es una lista de bloques y no una
     * lista plana de preguntas.</b> Una propiedad que se ofrece para venta y
     * para alquiler tiene dos condiciones económicas independientes, y en una
     * lista plana el cliente tendría que partir la clave {@code importe:VENTA}
     * por el {@code :} para saber a cuál pertenece cada campo. Eso es conocer
     * la estructura interna de la clave — exactamente lo que las tres familias
     * existen para evitar. Con bloques, el cliente pinta una sección por bloque
     * con el rótulo que el bloque trae, y no necesita saber ni cuántos hay.
     */
    record DefinicionCaptura(String intencion, String tipoPropiedad, List<String> operaciones,
                             List<Pregunta> comunes, List<Pregunta> delTipo,
                             List<BloqueOperacion> deLaOperacion) {

        /** Todas, en el orden en que se presentan. */
        public List<Pregunta> todas() {
            return java.util.stream.Stream.concat(
                            java.util.stream.Stream.concat(comunes.stream(),
                                    deLaOperacion.stream().flatMap(bloque -> bloque.preguntas().stream())),
                            delTipo.stream())
                    .toList();
        }

        /** Las claves que este tipo + operación admite. Lo demás no se envía. */
        public java.util.Set<String> clavesValidas() {
            return todas().stream().map(Pregunta::clave)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        }
    }

    /**
     * <b>La condición económica de UN encargo</b>, con su nombre puesto.
     *
     * <p>Es lo que hace que «venta y alquiler» no necesite ninguna rama nueva:
     * una propiedad con dos operaciones declaradas devuelve dos bloques
     * idénticos en forma y distintos en rótulo, y la pantalla los pinta como
     * dos secciones sin saber que son dos.
     *
     * @param operacion VENTA o ALQUILER — el vocabulario del dominio, sin
     *                  valores combinados
     * @param rotulo    cómo se titula la sección: «Condición de venta»
     * @param preguntas el importe, su moneda y la exclusividad, con las claves
     *                  ya calificadas ({@code importe:VENTA})
     */
    record BloqueOperacion(String operacion, String rotulo, List<Pregunta> preguntas) {
    }

    record Ejecucion(Long idBorrador, Long idPropiedad, String codigoPropiedad,
                     List<Long> idsEncargos, boolean reintento) {
    }

    /**
     * Abre una captura o continua una existente incorporando lo que se acaba de
     * saber.
     *
     * <p>Es idempotente en el sentido util: enviar dos veces el mismo dato deja
     * el borrador igual. Lo que <b>no</b> hace es ejecutar nada — para eso esta
     * {@link #ejecutar}, y separarlos es deliberado: el canal conversacional
     * tiene que poder confirmar antes de escribir.
     *
     * @param idBorrador  {@code null} para empezar uno nuevo
     * @param datos       pares clave/valor; las claves estructurales estan en
     *                    {@link GuionRegistroPropiedad} y las de atributo salen
     *                    del catalogo
     * @param procedencia por donde entro. Si trae conversacion, el borrador se
     *                    queda con ella: es lo que permite retomar diciendo
     *                    "sigamos con lo de ayer" en vez de con un id
     */
    EstadoCaptura avanzar(String intencion, Long idBorrador, Map<String, String> datos,
                          Procedencia procedencia, Actor actor);

    /** Donde se quedo una captura. */
    EstadoCaptura consultar(long idBorrador, Actor actor);

    /**
     * Los campos que aplican a un tipo + operación, sin abrir ningún borrador.
     *
     * <p>Es lo que un formulario necesita: la lista completa de una vez, no una
     * pregunta cada vez. {@link #avanzar} sirve a un canal conversacional, que
     * pregunta de una en una; una pantalla las pinta todas y por eso pide esto.
     * Los dos leen del mismo catálogo, así que no pueden discrepar.
     *
     * @param operaciones una o varias separadas por coma: {@code "VENTA"},
     *                    {@code "VENTA,ALQUILER"}. Con dos, la respuesta trae
     *                    dos bloques económicos y una sola ficha física —que es
     *                    justo lo que el modelo universal afirma: la propiedad
     *                    se registra una vez y se encarga dos
     */
    DefinicionCaptura definicion(String intencion, String tipoPropiedad, String operaciones,
                                 Actor actor);

    /**
     * <b>Lo que hay que decidir antes de que exista un plan de preguntas.</b>
     *
     * <p>Para registrar una propiedad son dos: el <b>tipo</b>, que decide qué
     * más hay que preguntar —un terreno no tiene dormitorios—, y la
     * <b>operación</b>, que decide si el importe que viene después es un precio
     * de venta o una renta mensual. Sin las dos, {@link #definicion} no puede
     * responder.
     *
     * <p>Existe para que el cliente no tenga que saberse cuáles son. Sin esto,
     * una pantalla escribiría «primero el tipo, luego la operación» y KAIROS lo
     * escribiría otra vez; el día que una intención nueva abriera con otra
     * pregunta, habría que cambiar los dos. El cliente pinta lo que le llega,
     * en el orden en que le llega, y lo único que necesita saber es que al
     * final tiene con qué pedir la definición.
     */
    List<Pregunta> apertura(String intencion, Actor actor);

    /** Lo que el tenant tiene a medias. Un borrador es de la organizacion, no de quien tecleo. */
    List<EstadoCaptura> enCurso(Actor actor);

    /**
     * Ejecuta el caso de uso de la intencion con lo que el borrador sabe.
     *
     * @param claveIdempotencia del cliente; un reintento no duplica nada
     * @throws com.controllocal.service.excepcion.ReglaNegocioException si
     *         todavia falta algo, con la lista de lo que falta
     */
    Ejecucion ejecutar(long idBorrador, String claveIdempotencia, Procedencia procedencia,
                       Actor actor);

    /** Abandonada a proposito. No se borra: que alguien la empezara tambien es un hecho. */
    EstadoCaptura descartar(long idBorrador, Actor actor);
}
