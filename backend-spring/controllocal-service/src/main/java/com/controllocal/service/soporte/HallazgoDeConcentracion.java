package com.controllocal.service.soporte;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * <b>El hallazgo del broker: dónde está el cuello del equipo</b> (D-E2-2 §9.1,
 * D-E2-5, E2.5).
 *
 * <h2>Por qué la media no sirve</h2>
 * <pre>
 *   Equipo en cartera: 38 de media        ← parece razonable
 *   Valentina 52 · Andrea 47 · Carlos 41 · Luis 12
 * </pre>
 *
 * <blockquote>El cuello está concentrado en un agente; el resto del equipo se
 * mantiene estable.</blockquote>
 *
 * <p><b>Vale precisamente porque la media lo esconde.</b> Un 38 de equipo no
 * pide ninguna acción hasta que se abre y se ve que uno solo arrastra el
 * número.
 *
 * <h2>Hereda la regla de E2.4 sin excepción</h2>
 * «Luis 12» es un <b>dato</b>. Que el cuello esté en una persona <b>mientras el
 * resto se sostiene</b> es una conclusión: relaciona la distribución con la
 * media, y por eso es un hallazgo y no un renglón de tabla.
 *
 * <p>Si no hay concentración, <b>no hay hallazgo</b>. No se rellena con «el
 * equipo está equilibrado»: un hallazgo de relleno enseña a no leerlos, igual
 * que una lectura de relleno.
 *
 * <h2>Y no es un ranking</h2>
 * D-E2-2 §6.1 prohíbe el ranking en el pulso del equipo. Esto es otra cosa: el
 * hallazgo <b>nombra al agente</b> porque una concentración sin sujeto no se
 * puede resolver, y va dirigido a quien puede actuar. Lo que no existe es la
 * tabla de posiciones para todos.
 */
public final class HallazgoDeConcentracion {

    /**
     * Cuántas veces por debajo de la mediana tiene que estar alguien para que
     * su caso sea una concentración y no una variación normal.
     *
     * <p>La mitad. Con un umbral más suave, cualquier equipo de cuatro produce
     * un «hallazgo» todas las semanas y el bloque deja de mirarse.
     */
    private static final double FACTOR = 0.5;

    /**
     * Cuántas veces por encima de la mediana tiene que estar alguien para que
     * <b>acapare</b>.
     *
     * <p>El triple. Que uno lleve un tercio más que el resto es reparto normal;
     * que lleve tres veces más es un cuello igual de real que el del rezagado,
     * sólo que por el otro extremo.
     */
    private static final double FACTOR_ACAPARA = 3.0;

    /** Por debajo de esto no hay equipo del que hablar. */
    private static final int MINIMO_DE_AGENTES = 3;

    private HallazgoDeConcentracion() {
    }

    /** Lo que aporta cada agente a una medida del equipo. */
    public record Aporte(long idAgente, String nombre, long valor) {
    }

    /**
     * El hallazgo, o {@code null} si no hay concentración.
     *
     * @param cuerpo la conclusión, ya redactada
     * @param rezagado quién arrastra el número; se nombra porque sin sujeto no
     *                 se puede resolver
     */
    public record Concentracion(String titulo, String cuerpo, long idRezagado,
                                String rezagado, long valorRezagado, long medianaDelResto) {
    }

    /**
     * <b>¿Hay un cuello, y está en una sola persona?</b>
     *
     * <p>Compara al último con la <b>mediana del resto</b> y no con la media del
     * total. La media incluye al rezagado y él mismo la arrastra hacia abajo, así
     * que compararse contra ella disimula justo lo que se busca; la mediana del
     * resto dice «así va el equipo cuando él no cuenta».
     *
     * @param medida cómo se llama lo que se está midiendo, para poder decirlo
     */
    public static Concentracion de(List<Aporte> aportes, String medida) {
        if (aportes == null || aportes.size() < MINIMO_DE_AGENTES) {
            return null;
        }
        List<Aporte> ordenados = new ArrayList<>(aportes);
        ordenados.sort(Comparator.comparingLong(Aporte::valor).thenComparingLong(Aporte::idAgente));

        Aporte ultimo = ordenados.get(0);
        Aporte primero = ordenados.get(ordenados.size() - 1);

        // UNO ACAPARA: la mediana del resto, sin contarlo a el.
        //
        // Este caso faltaba, y lo destapo el dato real: un equipo de cuatro donde
        // uno lleva 107 captaciones y los otros tres, cero. Mirar solo al ultimo
        // no encontraba nada -- comparaba 0 contra una mediana de 0 -- cuando la
        // concentracion era evidente por el otro lado. «Concentracion de cartera
        // de un agente» es justo esto.
        long medianaSinElPrimero = mediana(ordenados.subList(0, ordenados.size() - 1));
        if (primero.valor() > 0 && primero.valor() >= Math.max(1, medianaSinElPrimero) * FACTOR_ACAPARA) {
            String cuerpo = "Casi toda la cartera esta en manos de " + primero.nombre()
                    + "; el resto del equipo apenas mueve " + medianaSinElPrimero + ".";
            return new Concentracion("Dónde está concentrada la cartera", cuerpo,
                    primero.idAgente(), primero.nombre(), primero.valor(), medianaSinElPrimero);
        }

        // UNO SE QUEDA ATRAS: el caso de D-E2-2 §9.1 (Luis 12 contra 47).
        long medianaDelResto = mediana(ordenados.subList(1, ordenados.size()));
        if (medianaDelResto > 0 && ultimo.valor() <= medianaDelResto * FACTOR) {
            String cuerpo = "El cuello esta concentrado en " + ultimo.nombre()
                    + "; el resto del equipo se mantiene alrededor de " + medianaDelResto + ".";
            return new Concentracion("Dónde está el cuello del equipo", cuerpo,
                    ultimo.idAgente(), ultimo.nombre(), ultimo.valor(), medianaDelResto);
        }

        // Sin concentracion no hay hallazgo. El bloque no existe; no se rellena
        // con "el equipo esta equilibrado".
        return null;
    }

    /**
     * La mediana de una lista <b>ya ordenada</b> por valor.
     *
     * <p>Se usa la mediana y no la media porque la media incluye al extremo que
     * se esta midiendo, y el mismo la arrastra: compararse contra ella disimula
     * justo lo que se busca.
     */
    private static long mediana(List<Aporte> ordenados) {
        List<Long> valores = ordenados.stream().map(Aporte::valor).sorted().toList();
        return valores.isEmpty() ? 0 : valores.get(valores.size() / 2);
    }
}
