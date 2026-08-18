package com.kairos.conversacion;

import com.kairos.brox.ClienteBrox;
import com.kairos.brox.SesionBrox;

import java.util.List;
import java.util.Map;

/**
 * <b>KAIROS: un operador, no un chatbot encima de BROX.</b>
 *
 * <h2>Lo que hace, entero</h2>
 * <pre>
 *   frase ──▶ Interprete ──▶ accion + datos
 *                                │
 *                                ├─ ¿esta accion existe para esta sesion?  GET /capacidades
 *                                ├─ ¿que sabe ya el borrador?              GET /captura/{id}
 *                                ├─ ¿que falta?                            lo dice BROX
 *                                └─ invoca la MISMA operacion que la pantalla
 * </pre>
 * <b>No hay una quinta capa.</b> Este adaptador no valida un metraje, no decide
 * si un terreno lleva dormitorios, no sabe cuando un encargo esta vivo y no
 * escribe una sola sentencia. Todo eso vive en BROX, al otro lado de una API.
 *
 * <h2>Las cuatro cosas que no se negocian</h2>
 * <ol>
 *   <li><b>No toca la base de BROX.</b> Ni una consulta. Todo por
 *       {@link ClienteBrox}, que es HTTP.</li>
 *   <li><b>El actor sigue siendo una persona.</b> KAIROS no tiene cuenta: usa
 *       el token de quien conversa, con sus permisos y su alcance.</li>
 *   <li><b>Un dato que no se sabe se declara faltante.</b> Nunca se rellena con
 *       el caso frecuente.</li>
 *   <li><b>La autonomia la declara BROX.</b> Un turno propone o ejecuta segun
 *       lo que diga la capacidad, no segun lo que diga un prompt.</li>
 * </ol>
 *
 * <h2>Por que la respuesta no trae frases</h2>
 * Porque lo que compone la frase es la capa de conversacion, con su modelo y su
 * tono, y necesita los hechos <b>sin envolver</b> para poder decirlos de otra
 * manera en voz que por escrito. Lo que viaja son datos y codigos.
 */
public interface Kairos {

    /**
     * Lo que llega en un turno.
     *
     * @param conversacionId    obligatorio: sin el, lo que se escriba en BROX
     *                          queda sin poder explicarse
     * @param mensajeId         el mensaje del canal. Es la clave de
     *                          idempotencia natural: un webhook reenviado trae
     *                          el mismo, y por eso no duplica nada
     * @param confirmado        la persona dijo que si a lo propuesto. Llega en
     *                          un turno APARTE del que lo propuso
     */
    record Turno(String conversacionId, String turnoId, String mensajeId, String texto,
                 Long idBorrador, boolean confirmado) {
    }

    /** Como termino el turno. Es un codigo: la frase la compone quien habla. */
    enum Desenlace {
        /** Se respondio una consulta. No se escribio nada. */
        RESPONDIDO,
        /** Falta algo. La siguiente pregunta va en {@code captura} o en {@code falta}. */
        PREGUNTA,
        /** Hay bastante para ejecutar, y BROX dice que lo confirma una persona. */
        PROPUESTA,
        /** La operacion corrio en BROX. */
        EJECUTADO,
        /** No se reconocio ninguna accion en la frase. */
        NO_ENTENDIDO,
        /** BROX no ofrece esa capacidad a esta sesion. La razon es el rol. */
        SIN_PERMISO,
        /** BROX la clasifica como HUMANO: no la ejecuta un agente en ningun caso. */
        SOLO_HUMANO
    }

    /** Lo que produjo el turno. Cada accion rellena lo suyo: un turno hace una cosa. */
    record Resultado(Map<String, Object> propiedad,
                     List<ClienteBrox.Coincidencia> coincidencias,
                     List<ClienteBrox.Persona> personas,
                     ClienteBrox.Persona persona,
                     ClienteBrox.Interaccion interaccion,
                     ClienteBrox.EstadoCaptura captura,
                     ClienteBrox.Ejecucion ejecucion) {

        public static final Resultado NADA =
                new Resultado(null, null, null, null, null, null, null);

        public Resultado conCaptura(ClienteBrox.EstadoCaptura captura) {
            return new Resultado(propiedad, coincidencias, personas, persona, interaccion,
                    captura, ejecucion);
        }
    }

    /**
     * @param comprendido   lo que se saco de la frase. Permite decir "entendi
     *                      esto" y que la persona lo corrija antes de confirmar
     * @param noComprendido trozos que parecian significar algo y no se
     *                      convirtieron. Se declaran en vez de descartarse
     * @param falta         lo que BROX dice que impide ejecutar
     * @param opciones      valores admitidos, cuando la pregunta los tiene
     * @param motivo        codigo. Nunca una frase para mostrar
     */
    record Respuesta(String conversacionId, String turnoId, Accion accion, Desenlace desenlace,
                     String motivo, Map<String, String> comprendido, List<String> noComprendido,
                     List<String> falta, List<String> opciones, boolean confirmaUnaPersona,
                     Resultado resultado) {
    }

    /** Motivos. Codigos, no frases. */
    String SIN_TEXTO = "SIN_TEXTO";
    String SIN_ACCION_RECONOCIDA = "SIN_ACCION_RECONOCIDA";
    String CAPACIDAD_NO_DISPONIBLE = "CAPACIDAD_NO_DISPONIBLE";
    String RESERVADA_A_UNA_PERSONA = "RESERVADA_A_UNA_PERSONA";
    String FALTAN_DATOS = "FALTAN_DATOS";
    String CONFIRMA_UNA_PERSONA = "CONFIRMA_UNA_PERSONA";
    String SIN_COINCIDENCIAS = "SIN_COINCIDENCIAS";
    String VARIAS_COINCIDENCIAS = "VARIAS_COINCIDENCIAS";
    String YA_EXISTE = "YA_EXISTE";
    String SIN_BORRADOR_EN_CURSO = "SIN_BORRADOR_EN_CURSO";

    Respuesta turno(Turno turno, SesionBrox sesion);
}
