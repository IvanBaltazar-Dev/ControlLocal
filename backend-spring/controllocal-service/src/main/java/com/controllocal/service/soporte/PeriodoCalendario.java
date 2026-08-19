package com.controllocal.service.soporte;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * El periodo contra el que se mide una meta: un <b>mes de calendario</b>, con su
 * inicio, su fin, cuanto lleva recorrido y cuanto mide.
 *
 * <h2>Por que no vale la ventana movil</h2>
 *
 * <p>{@code /indicadores/resumen} lleva desde la v1 un parametro {@code periodo}
 * con valores 7d/15d/1m/3m/1y que resuelve a {@code hoy - dias + 1}. Sirve para
 * las series y los agregados, y ahi se queda.
 *
 * <p>Para el ritmo <b>no sirve</b>, y no es cuestion de gusto. D-E2-2 §4 pide
 * {@code metaEsperadaAHoy = meta x transcurridos / dias}. En una ventana movil
 * los transcurridos son <i>siempre</i> los dias totales, asi que
 * {@code metaEsperadaAHoy} seria siempre igual a la meta y el semaforo diria
 * rojo todos los dias del mes menos el ultimo. La formula queda tautologica: se
 * midio el 2026-08-19 y por eso el periodo cambia de naturaleza aqui.
 *
 * <p><b>Las dos semanticas no comparten parametro.</b> La ventana movil sigue
 * siendo {@code periodo}; el mes de calendario viaja aparte y con nombre propio.
 * Un mismo nombre para dos cosas distintas es como se llega a que nadie sepa que
 * mide un numero.
 *
 * <h2>Los transcurridos incluyen hoy</h2>
 *
 * <p>El 19 de agosto de 2026 el corte es <b>19 de 31</b>, no 18. El dia en curso
 * cuenta porque el trabajo de hoy ya esta contado en {@code actual}: descontarlo
 * del denominador y no del numerador inflaria el ritmo un dia entero.
 *
 * <p>Un mes ya cerrado tiene {@code transcurridos == dias}: el ritmo de julio no
 * se sigue proyectando en agosto. Un mes futuro tiene cero, y con cero no se
 * proyecta nada —lo cubre {@link PoliticaDeRitmo}—.
 */
public record PeriodoCalendario(int anio, int mes, LocalDate desde, LocalDate hasta,
                                int diasTranscurridos, int diasTotales) {

    /** El mes de {@code hoy}. Es el defecto de la pantalla. */
    public static PeriodoCalendario deHoy(LocalDate hoy) {
        return de(YearMonth.from(hoy), hoy);
    }

    /**
     * Un mes cualquiera, situado respecto de {@code hoy}.
     *
     * <p>{@code hoy} es un parametro y no {@code LocalDate.now()} para que las
     * pruebas puedan situar el corte donde quieran sin esperar al dia 19.
     */
    public static PeriodoCalendario de(YearMonth mes, LocalDate hoy) {
        LocalDate desde = mes.atDay(1);
        LocalDate hasta = mes.atEndOfMonth();
        int total = mes.lengthOfMonth();

        int transcurridos;
        if (hoy.isBefore(desde)) {
            transcurridos = 0;                    // todavia no ha empezado
        } else if (hoy.isAfter(hasta)) {
            transcurridos = total;                // ya cerro
        } else {
            transcurridos = hoy.getDayOfMonth();  // en curso: el dia de hoy cuenta
        }
        return new PeriodoCalendario(mes.getYear(), mes.getMonthValue(), desde, hasta,
                transcurridos, total);
    }

    /**
     * Interpreta el mes pedido por el cable en formato {@code AAAA-MM}.
     *
     * <p>Vacio, nulo o ilegible es el mes en curso: la pantalla se abre sin
     * elegir nada y tiene que funcionar. Un mes ilegible <b>no</b> es un error
     * del que valga la pena colgar una peticion de lectura.
     */
    public static PeriodoCalendario desde(String valor, LocalDate hoy) {
        if (valor == null || valor.isBlank()) {
            return deHoy(hoy);
        }
        try {
            return de(YearMonth.parse(valor.trim()), hoy);
        } catch (DateTimeParseException e) {
            return deHoy(hoy);
        }
    }

    /** {@code AAAA-MM}, que es como viaja y como vuelve. */
    public String codigo() {
        return "%04d-%02d".formatted(anio, mes);
    }

    /** Si el corte cae dentro: el mes ni cerro ni esta por empezar. */
    public boolean enCurso() {
        return diasTranscurridos > 0 && diasTranscurridos < diasTotales;
    }

    /** Si el mes ya termino. Lo usa el ritmo para no proyectar el pasado. */
    public boolean cerrado() {
        return diasTranscurridos >= diasTotales;
    }

    /** Dias que faltan para que cierre. Cero si ya cerro. */
    public int diasRestantes() {
        return Math.max(0, diasTotales - diasTranscurridos);
    }

    /** Si una fecha cae dentro del mes. */
    public boolean contiene(LocalDate fecha) {
        return fecha != null && !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }

    /** El mes anterior, para la variacion comparable. */
    public PeriodoCalendario anterior(LocalDate hoy) {
        return de(YearMonth.of(anio, mes).minusMonths(1), hoy);
    }
}
