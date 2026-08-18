package com.controllocal.service;

import java.util.List;

/**
 * <b>Lo que BROX encontró y vale la pena mirar</b> (E2.3).
 *
 * <h2>Qué separa de una tarea</h2>
 * <pre>
 *   una TAREA     dice «hay algo que debes resolver»
 *   un HALLAZGO   dice «encontré algo que vale la pena mirar»
 * </pre>
 *
 * <p>Una coincidencia de cartera puede ser extraordinariamente valiosa
 * <b>sin ser una obligación</b>. Mientras viajó dentro de la bandeja compitió
 * con una solicitud pendiente, una captación por decidir y un seguimiento
 * vencido — y con la política de despacho ganándolas por ser una ocasión, el
 * agente abría su Inicio y encontraba veintidós sugerencias por encima de lo que
 * de verdad le reclamaba algo.
 *
 * <p>Ese es el fallo que E2.3 cierra, y no se arregla bajando su peso: se
 * arregla sacándola de la colección equivocada. Un hallazgo que no se atiende no
 * deja nada a medias; una solicitud sin cerrar, sí.
 *
 * <h2>Mismo motor, otra salida</h2>
 * La evidencia es exactamente la que ya produce
 * {@code CoincidenciaCartera.evaluar}: el mismo puntaje, los mismos criterios
 * cumplidos y los mismos incumplidos que el panel de coincidencias enseña. <b>No
 * hay un segundo matcher</b>, y no lo habrá aquí: si el matching cambia, cambia
 * en un sitio.
 */
public interface HallazgoService {

    /** Qué clase de descubrimiento es. Hoy solo hay uno; el vocabulario es cerrado. */
    String COINCIDENCIA_DE_CARTERA = "COINCIDENCIA_DE_CARTERA";

    /**
     * Un descubrimiento, con su evidencia y su destino.
     *
     * @param id         identidad <b>estable</b>: la misma coincidencia tiene el
     *                   mismo id entre recargas, para que la pantalla pueda
     *                   descartarla, marcarla como vista o no repetirla. Se
     *                   compone de los dos extremos que la producen, no de un
     *                   contador ni de un hash del texto
     * @param titulo     de qué va, en el lenguaje de quien lo lee
     * @param porQue     <b>la interpretación, que es del dominio</b>: por qué
     *                   vale la pena mirarlo. Si esta frase se compusiera en
     *                   Angular, KAIROS necesitaría escribir la suya
     * @param puntaje    el mismo que el panel de coincidencias
     * @param cumple     criterios que encajan, ya redactados
     * @param noCumple   los que no; viajan porque un hallazgo con un pero
     *                   declarado se decide mejor que uno que solo presume
     * @param destino    ruta REAL del SPA donde se actúa
     */
    record Hallazgo(String id, String tipo, String titulo, String porQue,
                    int puntaje, List<String> cumple, List<String> noCumple,
                    String destino, Long idCliente, Long idCaptacion, String codigoCaptacion) {
    }

    /**
     * Los hallazgos vigentes del actor, del mejor al peor.
     *
     * <p>Se calculan al pedirlos y <b>no se guardan</b>: un hallazgo es una
     * lectura del estado de hoy, no un hecho ocurrido. Persistirlo obligaría a
     * mantenerlo al día cada vez que cambie un requerimiento o se retire un
     * local, y un hallazgo obsoleto es peor que ninguno — manda a proponer algo
     * que ya no encaja.
     */
    List<Hallazgo> de(Actor actor);
}
