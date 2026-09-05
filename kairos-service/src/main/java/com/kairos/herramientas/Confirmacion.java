package com.kairos.herramientas;

/**
 * <b>Cuanto tiene que decir una persona antes de que la herramienta corra.</b>
 *
 * <p>Son los tres niveles de D-K-1 §3, uno por seccion del catalogo, y estan
 * aqui porque la regla 4 —<i>lo irreversible lo confirma una persona</i>— no
 * puede vivir en un prompt: un prompt no falla el build.
 *
 * <h2>El criterio, dicho una vez</h2>
 * Si la accion <b>sale de la organizacion</b>, <b>firma algo</b>, <b>mueve
 * dinero</b> o <b>no se puede deshacer con otra accion del sistema</b>, es
 * {@link #SIEMPRE}. Si se corrige editando, cancelando o descartando, es
 * {@link #AVISA}. Si no escribe nada, es {@link #NUNCA}.
 *
 * <h2>Esto es un SUELO, no la ultima palabra</h2>
 * BROX puede exigir mas —declarando {@code CONFIRMA} en la capacidad—, y
 * entonces se confirma aunque el catalogo diga {@link #AVISA}. Lo que BROX
 * <b>no</b> puede es exigir menos: una capacidad declarada {@code AUTO} no
 * apaga la confirmacion de una herramienta {@link #SIEMPRE}. La composicion va
 * siempre hacia el lado estricto, porque el fallo de conceder de mas se paga
 * con una publicacion, una firma o un cobro que nadie pidio, y el de exigir de
 * mas se paga con un turno.
 */
public enum Confirmacion {

    /** Lectura. No escribe: no hay nada que confirmar. */
    NUNCA,

    /**
     * Escritura reversible. Se ejecuta y se dice que se hizo.
     *
     * <p>La reversibilidad no es una opinion: es que existe <b>otra accion del
     * sistema</b> que la deshace —editar la nota, cancelar la visita, descartar
     * el borrador—. Sin esa segunda accion, la herramienta no es AVISA.
     */
    AVISA,

    /**
     * Escritura sensible. Exige un turno <b>aparte</b> en el que la persona
     * diga que si.
     *
     * <p>Aparte y no en el mismo: si la confirmacion pudiera llegar en el turno
     * que propone, bastaria una frase ambigua para publicar, firmar o cobrar.
     */
    SIEMPRE;

    /** {@code true} si hace falta un turno de la persona diciendo que si. */
    public boolean exigeTurnoDeConfirmacion() {
        return this == SIEMPRE;
    }

    /** La mas estricta de las dos. Nunca la mas comoda. */
    public Confirmacion mas(Confirmacion otra) {
        return otra == null || compareTo(otra) >= 0 ? this : otra;
    }
}
