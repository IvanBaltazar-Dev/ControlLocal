package com.controllocal.service.soporte;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El semaforo del ritmo, que bajo de la maqueta al dominio en E2.6.
 *
 * <p>Las pruebas estan escritas contra los <b>casos que D-E2-2 describe con
 * numeros</b>, para que el documento y el codigo digan lo mismo y se note si
 * alguno de los dos se mueve.
 */
class RitmoTest {

    private static final LocalDate DIA_19 = LocalDate.of(2026, 8, 19);

    private static PeriodoCalendario mesEnDia(int dia) {
        return PeriodoCalendario.de(YearMonth.of(2026, 8), LocalDate.of(2026, 8, dia));
    }

    // ------------------------------------------------------------------
    // Lo que mide: proyeccion, no consumo
    // ------------------------------------------------------------------

    /** El ejemplo literal de D-E2-2 §4, adaptado al mes real de 31 dias. */
    @Test
    @DisplayName("dia 5 de 31 con 5 de meta 15: va MUY por encima, es EN_RITMO y no 'cerca'")
    void elSemaforoMideRitmoNoPorcentajeConsumado() {
        Ritmo r = Ritmo.de(5, 15, mesEnDia(5));

        assertEquals(Ritmo.Estado.EN_RITMO, r.estado(),
                "Consumio un tercio de la meta, pero proyecta 31: llega de sobra.");
        assertEquals(33, r.porcentajeMeta(), "El consumo si es un tercio...");
        assertEquals(31, r.proyeccionCierre(), "...y la proyeccion es el doble de la meta.");
        assertEquals(10, r.faltante());
    }

    @Test
    @DisplayName("con el ritmo justo para llegar, es EN_RITMO")
    void llegarJustoEsEnRitmo() {
        // Dia 19 de 31, meta 31, lleva 19: proyecta exactamente 31.
        Ritmo r = Ritmo.de(19, 31, mesEnDia(19));

        assertEquals(Ritmo.Estado.EN_RITMO, r.estado());
        assertEquals(31, r.proyeccionCierre());
        assertEquals(100, r.porcentajeProyectado());
    }

    @Test
    @DisplayName("proyectar por debajo del 85 % de la meta es FUERA_DE_RITMO")
    void unaBrechaGrandeEsRoja() {
        // Dia 19 de 31, meta 40, lleva 10: proyecta 16, el 40 %.
        Ritmo r = Ritmo.de(10, 40, mesEnDia(19));

        assertEquals(Ritmo.Estado.FUERA_DE_RITMO, r.estado());
        assertEquals(16, r.proyeccionCierre());
        assertEquals(30, r.faltante());
    }

    @Test
    @DisplayName("entre el 85 % y el 100 % de proyeccion es ATENCION")
    void unaDesviacionRecuperableEsAmbar() {
        // Dia 19 de 31, meta 24, lleva 13: proyecta 21, el 88 %.
        Ritmo r = Ritmo.de(13, 24, mesEnDia(19));

        assertEquals(Ritmo.Estado.ATENCION, r.estado());
        assertEquals(88, r.porcentajeProyectado());
    }

    @Test
    @DisplayName("metaEsperadaAHoy es la meta prorrateada por los dias transcurridos")
    void loEsperadoAHoySeProrratea() {
        assertEquals(18, Ritmo.de(0, 30, mesEnDia(19)).metaEsperadaAHoy(),
                "30 x 19 / 31 = 18,4 -> 18");
    }

    // ------------------------------------------------------------------
    // Las dos guardas contra el rojo que nadie se creeria
    // ------------------------------------------------------------------

    @Test
    @DisplayName("al principio del mes lo peor posible es ATENCION, aunque proyecte cero")
    void alArrancarNoHayRojo() {
        // Dia 2 de 31 (6 % del periodo, por debajo del 15 %): sin recorrido no
        // se sentencia. Un rojo que sale siempre el dia 2 ensena a ignorarlo.
        Ritmo r = Ritmo.de(0, 30, mesEnDia(2));

        assertEquals(Ritmo.Estado.ATENCION, r.estado());
        assertEquals(0, r.proyeccionCierre());
    }

    @Test
    @DisplayName("pasado el arranque, la misma cifra ya es FUERA_DE_RITMO")
    void pasadoElArranqueSiHayRojo() {
        assertEquals(Ritmo.Estado.FUERA_DE_RITMO, Ritmo.de(0, 30, mesEnDia(19)).estado());
    }

    @Test
    @DisplayName("a una unidad de la meta nunca es rojo: es una firma de distancia")
    void aUnaUnidadDeLaMetaNoEsRojo() {
        // Dia 31 de 31, meta 5, lleva 4: proyecta 4, el 80 %, que seria rojo.
        Ritmo r = Ritmo.de(4, 5, mesEnDia(31));

        assertEquals(Ritmo.Estado.ATENCION, r.estado());
        assertEquals(1, r.faltante());
    }

    // ------------------------------------------------------------------
    // Sin cadencia: metas pequenas no se reparten por dias
    // ------------------------------------------------------------------

    @Test
    @DisplayName("con meta 2 no se prorratea: no todos los dias se firma un contrato")
    void unaMetaPequenaNoTieneCadenciaDiaria() {
        // Dia 26 de 31: quedan 5 dias, menos de un cuarto del mes.
        Ritmo r = Ritmo.de(0, 2, mesEnDia(26));

        assertTrue(r.sinCadencia());
        assertNull(r.metaEsperadaAHoy(),
                "Decir 'hoy deberias llevar 1,2 contratos' inventa una cadencia que no existe.");
        assertEquals(Ritmo.Estado.FUERA_DE_RITMO, r.estado(),
                "Sin margen para conseguirlo, la brecha ya necesita intervencion.");
    }

    @Test
    @DisplayName("sin cadencia y con mas de un cuarto del mes por delante, todavia es ATENCION")
    void sinCadenciaConMasDeUnCuartoPorDelanteEsAmbar() {
        // Dia 19 de 31: quedan 12 dias, mas de un cuarto. Todavia da tiempo.
        assertEquals(Ritmo.Estado.ATENCION, Ritmo.de(0, 2, mesEnDia(19)).estado());
    }

    @Test
    @DisplayName("sin cadencia, cumplir la meta es EN_RITMO aunque quede mes por delante")
    void sinCadenciaCumplirEsSuficiente() {
        assertEquals(Ritmo.Estado.EN_RITMO, Ritmo.de(2, 2, mesEnDia(5)).estado());
    }

    @Test
    @DisplayName("sin cadencia y con margen todavia, es ATENCION")
    void sinCadenciaConMargenEsAmbar() {
        // Dia 5 de 31: queda mucho mas de un cuarto del mes.
        assertEquals(Ritmo.Estado.ATENCION, Ritmo.de(0, 2, mesEnDia(5)).estado());
    }

    // ------------------------------------------------------------------
    // El cuarto estado, que no es decorativo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sin meta no hay cero: hay SIN_META, y los campos derivados son nulos")
    void sinMetaNoSeRellenaConCero() {
        Ritmo r = Ritmo.sinMeta(19);

        assertEquals(Ritmo.Estado.SIN_BASE, r.estado());
        assertEquals(Ritmo.Motivo.SIN_META, r.motivo());
        assertEquals(19, r.actual(), "Lo conseguido si se sabe y se dice.");
        assertNull(r.metaPeriodo());
        assertNull(r.metaEsperadaAHoy());
        assertNull(r.porcentajeMeta());
        assertNull(r.faltante());
        assertFalse(r.concluye());
    }

    @Test
    @DisplayName("una meta de cero tambien es 'sin meta': cero no se compara")
    void unaMetaDeCeroNoDaSemaforo() {
        assertEquals(Ritmo.Motivo.SIN_META, Ritmo.de(3, 0, mesEnDia(19)).motivo());
    }

    @Test
    @DisplayName("una meta de equipo incompleta no se compara: daria una brecha a favor")
    void laCoberturaIncompletaNoSeCompara() {
        Ritmo r = Ritmo.coberturaIncompleta(19);

        assertEquals(Ritmo.Estado.SIN_BASE, r.estado());
        assertEquals(Ritmo.Motivo.COBERTURA_INCOMPLETA, r.motivo());
        assertNull(r.metaPeriodo(),
                "Sumar solo las metas que hay daria un objetivo mas bajo que el real.");
    }

    @Test
    @DisplayName("un mes futuro tiene meta pero no recorrido que proyectar")
    void unMesFuturoNoConcluye() {
        PeriodoCalendario septiembre = PeriodoCalendario.de(YearMonth.of(2026, 9), DIA_19);
        Ritmo r = Ritmo.de(0, 20, septiembre);

        assertEquals(Ritmo.Estado.SIN_BASE, r.estado());
        assertEquals(Ritmo.Motivo.PERIODO_SIN_RECORRIDO, r.motivo());
        assertEquals(20, r.metaPeriodo(), "La meta si se sabe: lo que falta es el recorrido.");
        assertNull(r.proyeccionCierre());
    }

    @Test
    @DisplayName("cuando concluye, el motivo es NINGUNO")
    void unEstadoQueConcluyeNoLlevaMotivo() {
        Ritmo r = Ritmo.de(19, 24, mesEnDia(19));

        assertEquals(Ritmo.Motivo.NINGUNO, r.motivo());
        assertTrue(r.concluye());
    }

    // ------------------------------------------------------------------
    // Los umbrales viven en la politica, no aqui
    // ------------------------------------------------------------------

    @Test
    @DisplayName("los cinco umbrales del ritmo salen de PoliticaComercial")
    void losUmbralesTienenUnSoloDueno() {
        // Si alguien cambia un valor, tiene que cambiarlo en la politica y subir
        // su version. Esta prueba es el recordatorio, no una copia: compara con
        // la politica, no con un literal.
        assertEquals(100, PoliticaComercial.RITMO_LLEGA.valor());
        assertEquals(85, PoliticaComercial.RITMO_CERCA.valor());
        assertEquals(15, PoliticaComercial.RITMO_ARRANQUE.valor());
        assertEquals(3, PoliticaComercial.RITMO_VOLUMEN_MINIMO.valor());
        assertEquals(5, PoliticaComercial.MUESTRA_MINIMA.valor());

        assertTrue(PoliticaComercial.tieneCadencia(PoliticaComercial.RITMO_VOLUMEN_MINIMO.valor()));
        assertFalse(PoliticaComercial.tieneCadencia(
                PoliticaComercial.RITMO_VOLUMEN_MINIMO.valor() - 1));
        assertTrue(PoliticaComercial.enArranque(mesEnDia(2)));
        assertFalse(PoliticaComercial.enArranque(mesEnDia(19)));
    }
}
