package com.controllocal.service.soporte;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El periodo contra el que se mide la meta.
 *
 * <p>La prueba que da sentido a todo el archivo es
 * {@link #laVentanaMovilHabriaHechoTautologicoElRitmo()}: es el defecto que se
 * midio el 2026-08-19 y la razon por la que este tipo existe.
 */
class PeriodoCalendarioTest {

    private static final LocalDate DIA_19 = LocalDate.of(2026, 8, 19);

    @Test
    @DisplayName("el 19 de agosto de 2026 el corte es 19 de 31")
    void elCorteDelMesEnCurso() {
        PeriodoCalendario agosto = PeriodoCalendario.deHoy(DIA_19);

        assertEquals("2026-08", agosto.codigo());
        assertEquals(LocalDate.of(2026, 8, 1), agosto.desde());
        assertEquals(LocalDate.of(2026, 8, 31), agosto.hasta());
        assertEquals(19, agosto.diasTranscurridos());
        assertEquals(31, agosto.diasTotales());
        assertEquals(12, agosto.diasRestantes());
        assertTrue(agosto.enCurso());
        assertFalse(agosto.cerrado());
    }

    @Test
    @DisplayName("el dia de hoy cuenta como transcurrido, porque su trabajo ya cuenta en actual")
    void elDiaEnCursoSeIncluye() {
        // El dia 1 son 1 de 31, no 0: descontarlo del denominador y no del
        // numerador inflaria el ritmo un dia entero.
        assertEquals(1, PeriodoCalendario.deHoy(LocalDate.of(2026, 8, 1)).diasTranscurridos());
        assertEquals(31, PeriodoCalendario.deHoy(LocalDate.of(2026, 8, 31)).diasTranscurridos());
    }

    @Test
    @DisplayName("un mes cerrado tiene todos sus dias transcurridos")
    void unMesPasadoNoSeSigueProyectando() {
        PeriodoCalendario julio = PeriodoCalendario.de(YearMonth.of(2026, 7), DIA_19);

        assertEquals(31, julio.diasTranscurridos());
        assertEquals(31, julio.diasTotales());
        assertEquals(0, julio.diasRestantes());
        assertTrue(julio.cerrado());
        assertFalse(julio.enCurso());
    }

    @Test
    @DisplayName("un mes futuro no lleva nada recorrido")
    void unMesFuturoNoTieneRecorrido() {
        PeriodoCalendario septiembre = PeriodoCalendario.de(YearMonth.of(2026, 9), DIA_19);

        assertEquals(0, septiembre.diasTranscurridos());
        assertEquals(30, septiembre.diasTotales());
        assertFalse(septiembre.enCurso());
    }

    /**
     * El defecto que motivo E2.6, escrito como prueba para que no vuelva.
     *
     * <p>Con la ventana movil del parametro {@code periodo} —{@code hoy - 30 + 1}
     * a {@code hoy}— los dias transcurridos son SIEMPRE los totales, asi que
     * {@code metaEsperadaAHoy = meta x transcurridos / dias} da siempre la meta
     * entera. El semaforo diria rojo todos los dias del mes menos el ultimo.
     */
    @Test
    @DisplayName("la ventana movil habria hecho tautologico el ritmo; el mes de calendario no")
    void laVentanaMovilHabriaHechoTautologicoElRitmo() {
        PeriodoCalendario agosto = PeriodoCalendario.deHoy(DIA_19);

        int metaEsperadaConCalendario =
                Math.round(30f * agosto.diasTranscurridos() / agosto.diasTotales());
        int metaEsperadaConVentanaMovil = Math.round(30f * 30 / 30);

        assertEquals(30, metaEsperadaConVentanaMovil,
                "En una ventana movil lo esperado a hoy es toda la meta, siempre.");
        assertEquals(18, metaEsperadaConCalendario,
                "Con mes de calendario, el dia 19 de 31 se espera algo mas de la mitad.");
    }

    @Test
    @DisplayName("un mes ilegible o ausente es el mes en curso, no un error")
    void elMesSeInterpretaConIndulgencia() {
        // La pantalla se abre sin elegir nada y tiene que funcionar. Un mes mal
        // escrito no merece colgar una peticion de lectura.
        for (String valor : new String[]{null, "", "   ", "agosto", "2026-13", "2026/08"}) {
            assertEquals("2026-08", PeriodoCalendario.desde(valor, DIA_19).codigo(),
                    "Deberia caer en el mes en curso: '" + valor + "'");
        }
        assertEquals("2026-05", PeriodoCalendario.desde("2026-05", DIA_19).codigo());
        assertEquals("2026-05", PeriodoCalendario.desde("  2026-05  ", DIA_19).codigo());
    }

    @Test
    @DisplayName("febrero de un ano bisiesto mide 29 dias")
    void elCalendarioEsElDeVerdad() {
        assertEquals(29, PeriodoCalendario.de(YearMonth.of(2028, 2), DIA_19).diasTotales());
        assertEquals(28, PeriodoCalendario.de(YearMonth.of(2026, 2), DIA_19).diasTotales());
    }

    @Test
    @DisplayName("el mes anterior cruza el ano sin ayuda")
    void elMesAnteriorDeEneroEsDiciembre() {
        PeriodoCalendario enero = PeriodoCalendario.de(YearMonth.of(2026, 1), DIA_19);

        assertEquals("2025-12", enero.anterior(DIA_19).codigo());
    }

    @Test
    @DisplayName("contiene solo las fechas del mes, con los bordes dentro")
    void losBordesEstanDentro() {
        PeriodoCalendario agosto = PeriodoCalendario.deHoy(DIA_19);

        assertTrue(agosto.contiene(LocalDate.of(2026, 8, 1)));
        assertTrue(agosto.contiene(LocalDate.of(2026, 8, 31)));
        assertFalse(agosto.contiene(LocalDate.of(2026, 7, 31)));
        assertFalse(agosto.contiene(LocalDate.of(2026, 9, 1)));
        assertFalse(agosto.contiene(null));
    }
}
