package com.controllocal.service.soporte;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * <b>El único dueño del orden del foco</b> (D-E2-1 §3, E2.2).
 *
 * <h2>Qué decide</h2>
 * Cuál de los asuntos abiertos merece cada uno de los cinco puestos del Inicio,
 * y en qué orden. Determinista, del dominio, y <b>nunca visible como
 * puntuación</b>: el orden se demuestra ordenando.
 *
 * <pre>
 *   1 · ¿Se puede hacer algo ahora?   solo compite lo que DEPENDE DE MI
 *   2 · Ventana temporal              menos margen, mas peso
 *   3 · Ventana de oportunidad        algo acaba de moverse
 *   4 · Desbloqueo                    resolverlo permite continuar un proceso
 *   5 · Antiguedad accionable         gana turno poco a poco, con tope
 *   6 · Estabilidad                   ante empate se conserva el orden anterior
 * </pre>
 *
 * <h2>Por qué una clase con nombre y no un {@code Comparator} suelto</h2>
 * Porque el orden es una <b>decisión de negocio</b>, y una decisión de negocio
 * tiene que poder nombrarse, probarse y encontrarse. Repartida en un
 * {@code sorted(...)} del servicio y otro del cliente, deja de ser una política
 * y pasa a ser dos opiniones — que es exactamente el estado del que E2.2 viene a
 * sacar al producto: el SPA ordenaba con
 * {@code a.prioridad - b.prioridad || b.valor - a.valor} mientras el backend
 * ordenaba por otra cosa.
 *
 * <h2>El criterio 6 es el que la hace usable</h2>
 * Sin estabilidad, dos asuntos equivalentes se intercambian en cada recarga y el
 * usuario deja de fiarse del 01–05. Por eso el desempate final no es el id ni el
 * azar: es <b>dónde estaba antes</b>, y solo cuando no hay orden previo se cae a
 * un desempate determinista por identificador. Dos llamadas seguidas con los
 * mismos datos devuelven siempre lo mismo.
 *
 * <h2>Lo que NO hace</h2>
 * No recorta a cinco. Devuelve la colección entera ya ordenada y es la pantalla
 * la que enseña los primeros; lo demás vive en la cola completa. Recortar aquí
 * escondería asuntos sin decirlo, que es el fallo que D-F7-2 dejó documentado.
 */
public final class PoliticaDeDespacho {

    /** Cuántos asuntos ocupan el foco del Inicio. La cola no tiene tope. */
    public static final int ASUNTOS_EN_FOCO = 5;

    // ------------------------------------------------------------------
    // Los pesos. Están aquí y en ningún otro sitio.
    //
    // Son los mismos del prototipo aprobado (`docs/ai/prototipos/nucleo-brox.js`,
    // §13), y se copian con sus valores a propósito: cambiarlos "de paso" al
    // portarlos habría movido el orden que el diseño ya validó a ojo.
    // ------------------------------------------------------------------

    /** Peso máximo de la ventana temporal, cuando vence hoy o ya venció. */
    private static final int VENTANA_MAXIMA = 26;
    /** Cuánto cae el peso por cada día de margen que queda. */
    private static final int CAIDA_POR_DIA = 2;
    /** Una ocasión que acaba de abrirse pesa esto. */
    private static final int PESO_OCASION = 30;
    /** Desbloquear un proceso detenido pesa esto. */
    private static final int PESO_DESBLOQUEO = 30;
    /** Tope de la antigüedad, para que esperar no mande sola. */
    private static final int TOPE_ANTIGUEDAD = 12;

    private PoliticaDeDespacho() {
    }

    /**
     * Los hechos que la política mira. <b>Solo estos</b>: si un asunto necesita
     * un séptimo criterio, se añade aquí y se prueba, no se cuela en el
     * comparador de quien llama.
     *
     * @param id           identidad estable, para el desempate determinista
     * @param dependeDeMi  criterio 1
     * @param diasDeMargen días que faltan para el vencimiento; negativo si ya
     *                     venció, {@code null} si el asunto no tiene plazo
     * @param esOcasion    criterio 3
     * @param desbloquea   criterio 4
     * @param diasEsperando criterio 5
     */
    public record Asunto(long id, boolean dependeDeMi, Integer diasDeMargen,
                         boolean esOcasion, boolean desbloquea, int diasEsperando) {
    }

    /**
     * El peso de un asunto. <b>No se publica</b>: existe para ordenar y para que
     * un test pueda afirmar que un criterio movió al ganador.
     *
     * <p>Un asunto que no depende de mí pesa cero y además queda fuera del foco
     * por {@link #compiten}: el peso no es la forma de excluirlo —un peso 0
     * seguiría compitiendo con otro peso 0— sino la forma de no premiarlo.
     */
    public static int peso(Asunto asunto) {
        if (!asunto.dependeDeMi()) {
            return 0;
        }
        int peso = 0;
        if (asunto.diasDeMargen() != null) {
            // Vencido o venciendo hoy pesa el máximo; a partir de ahí baja, y no
            // baja de cero: un vencimiento a tres meses no resta, simplemente
            // deja de sumar.
            peso += Math.max(0, VENTANA_MAXIMA - Math.max(0, asunto.diasDeMargen()) * CAIDA_POR_DIA);
        }
        if (asunto.esOcasion()) {
            peso += PESO_OCASION;
        }
        if (asunto.desbloquea()) {
            peso += PESO_DESBLOQUEO;
        }
        peso += Math.min(TOPE_ANTIGUEDAD, Math.max(0, asunto.diasEsperando()));
        return peso;
    }

    /**
     * <b>Criterio 1.</b> Lo que espera al interesado, al propietario, al broker o
     * a documentación no ocupa un lugar del foco — sigue en la cola, visible,
     * pero no compite.
     */
    public static boolean compite(Asunto asunto) {
        return asunto.dependeDeMi();
    }

    public static <T> List<T> compiten(List<T> asuntos, java.util.function.Function<T, Asunto> comoAsunto) {
        return asuntos.stream().filter(a -> compite(comoAsunto.apply(a))).toList();
    }

    /**
     * <b>La colección entera, en el orden definitivo.</b>
     *
     * <p>Lo que depende de mí va primero, ordenado por peso; lo que no, va
     * después y también ordenado, porque la cola completa se lee y merece un
     * orden estable. Así el cliente puede pintar los cinco primeros sin saber
     * nada de la política, y la cola sigue siendo la misma lista.
     *
     * @param ordenPrevio ids en el orden en que se entregaron la última vez.
     *                    Es el criterio 6; vacío o {@code null} en la primera
     *                    carga, y entonces desempata el id.
     */
    public static <T> List<T> despachar(List<T> asuntos,
                                        java.util.function.Function<T, Asunto> comoAsunto,
                                        List<Long> ordenPrevio) {
        List<Long> previo = ordenPrevio == null ? List.of() : ordenPrevio;
        List<T> ordenados = new ArrayList<>(asuntos);
        ordenados.sort(comparador(comoAsunto, previo));
        return List.copyOf(ordenados);
    }

    /** El comparador, expuesto para que un test lo pueda ejercer directamente. */
    public static <T> Comparator<T> comparador(java.util.function.Function<T, Asunto> comoAsunto,
                                               List<Long> ordenPrevio) {
        List<Long> previo = ordenPrevio == null ? List.of() : ordenPrevio;
        return Comparator
                // Criterio 1: primero todo lo accionable.
                .comparing((T t) -> comoAsunto.apply(t).dependeDeMi() ? 0 : 1)
                // Criterios 2 a 5, resumidos en el peso. Mayor peso, antes.
                .thenComparing(t -> peso(comoAsunto.apply(t)), Comparator.reverseOrder())
                // Criterio 6: donde estaba antes. Lo que no estaba entra al final
                // de los empatados, no delante: entrar nuevo no adelanta a quien
                // ya llevaba su puesto.
                .thenComparingInt(t -> posicionPrevia(previo, comoAsunto.apply(t).id()))
                // Y cuando no hay pasado, un desempate que no depende del reloj
                // ni del orden en que la base devolvió las filas.
                .thenComparingLong(t -> comoAsunto.apply(t).id());
    }

    private static int posicionPrevia(List<Long> ordenPrevio, long id) {
        int posicion = ordenPrevio.indexOf(id);
        return posicion == -1 ? Integer.MAX_VALUE : posicion;
    }

    /**
     * Los días de margen hasta un vencimiento. {@code null} cuando el asunto no
     * tiene plazo — que no es lo mismo que «vence hoy», y por eso no se rellena
     * con cero.
     */
    public static Integer margenHasta(LocalDate vencimiento, LocalDate hoy) {
        return vencimiento == null ? null : (int) ChronoUnit.DAYS.between(hoy, vencimiento);
    }
}
