package com.controllocal.service.soporte;

/**
 * <b>Qué clase de hecho es cada línea de «CÓMO ESTÁ»</b> (D-E2-1 §10.1, E2.4).
 *
 * <h2>El problema que resuelve</h2>
 * Los tres hechos de un asunto compartían el tono del asunto, así que uno en
 * rojo pintaba de rojo también sus buenas noticias:
 *
 * <pre>
 *   ● 1 observación pendiente        (rojo)
 *   ● Metraje ya corregido           (rojo -- y es una BUENA noticia)
 *   ● Publicación bloqueada          (rojo)
 * </pre>
 *
 * De un vistazo eso dice «aquí todo va mal», que es falso y desmoraliza. Un
 * hecho resuelto y un hecho que frena <b>no son el mismo tipo de hecho</b> y no
 * pueden compartir marca.
 *
 * <h2>El vocabulario es de cinco y NO crece</h2>
 * <pre>
 *   HECHO  ✓ verde   ya resuelto, nadie vuelve sobre ello
 *   FALTA  ○ ambar   falta que alguien lo haga -- es LO ACCIONABLE
 *   PLAZO  ⏱ rojo    corre el tiempo, con fecha
 *   FRENO  ⊘ rojo    la CONSECUENCIA de lo que falta: que queda parado
 *   DATO   – gris    contexto, ni bueno ni malo
 * </pre>
 *
 * <p><b>Lo decide el dominio, no la pantalla.</b> Angular mapea estado → marca y
 * color, y nada más; si dedujera el estado del tono del asunto volvería
 * exactamente el problema de arriba.
 *
 * <p>Que sea un enum y no una cadena es la mitad de la garantía: un sexto estado
 * no se puede colar sin decidirlo, y {@code VocabularioDeLaInterpretacionTest}
 * es la otra mitad.
 */
public enum EstadoDelHecho {

    /** Ya está resuelto. Sale en verde aunque el asunto esté en rojo. */
    HECHO,

    /** Falta que alguien lo haga. Es lo accionable. */
    FALTA,

    /** Corre el tiempo, y lleva fecha. */
    PLAZO,

    /**
     * Qué queda parado por lo que falta.
     *
     * <p>Es el único que va en tinta plena: la marca roja ya carga la alarma, y
     * subir además el peso hacía que la consecuencia compitiera con el titular
     * de la recomendación, que es lo único que se pide leer primero.
     */
    FRENO,

    /** Contexto. Ni bueno ni malo, y por eso gris. */
    DATO
}
