package com.controllocal.service.disponibilidad;

import com.controllocal.service.matching.MotorMatchingDeterminista;
import com.controllocal.service.matching.ContratoMatching.Causa;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import static com.controllocal.service.disponibilidad.DisponibilidadContextualService.PrecisionHecho.*;
import static com.controllocal.service.disponibilidad.SelectorConfirmacionVigente.*;
import static com.controllocal.service.matching.ContratoMatching.Conclusion.*;
import static org.junit.jupiter.api.Assertions.*;

class SelectorConfirmacionVigenteTest {
    @Test void secuenciaNoEsFechaDeHechoYAntecedenteNoReiniciaFrescura() {
        Hecho actual = instante(1, 1, true, "2026-10-28T15:00:00Z");
        Hecho ingresadoDespues = instante(2, 2, false, "2026-10-27T15:00:00Z");
        var s = seleccionar(List.of(actual, ingresadoDespues));
        assertEquals(2, s.versionSecuencial()); assertEquals(1, s.vigente().id());
        assertEquals(actual.baseFrescura(), s.paraMatching().confirmadaEn());
        assertFalse(s.paraMatching().indisponibilidadConocida());
    }

    @Test void antecedenteDisponibleNoReviveUnaIndisponibilidadPosterior() {
        var s = seleccionar(List.of(instante(1, 1, false, "2026-10-28T15:00:00Z"),
                instante(2, 2, true, "2026-10-27T15:00:00Z")));
        assertTrue(s.paraMatching().indisponibilidadConocida());
        assertEquals(INCUMPLE, new MotorMatchingDeterminista().disponibilidad(s.paraMatching(), Instant.parse("2026-10-29T15:00:00Z")).conclusion());
    }

    @Test void fechaSinHoraConservaSuBaseConservadoraYPrecision() {
        var h = fecha(1, 1, true, "2026-10-28", "2026-10-28T15:00:00Z");
        var s = seleccionar(List.of(h));
        assertEquals(Instant.parse("2026-10-28T05:00:00Z"), s.paraMatching().confirmadaEn());
        assertEquals(FECHA, s.vigente().precision()); assertEquals(LocalDate.of(2026, 10, 28), s.vigente().fechaSinHora());
    }

    @Test void solapadasCompatiblesNoExtiendenUnaBaseSinAcreditar() {
        var precisa = instante(1, 1, true, "2026-10-28T15:00:00Z");
        var sinHora = fecha(2, 2, true, "2026-10-28", "2026-10-28T18:00:00Z");
        var s = seleccionar(List.of(precisa, sinHora));
        assertNull(s.indeterminacion()); assertEquals(1, s.vigente().id());
        assertEquals(precisa.baseFrescura(), s.paraMatching().confirmadaEn());
    }

    @Test void solapadasContradictoriasSonConflictoNoUltimoInsertGana() {
        var precisa = instante(1, 1, true, "2026-10-28T15:00:00Z");
        var sinHora = fecha(2, 2, false, "2026-10-28", "2026-10-28T18:00:00Z");
        var s = seleccionar(List.of(precisa, sinHora));
        assertEquals(Causa.CONFLICTO, s.indeterminacion()); assertNull(s.vigente());
        var e = new MotorMatchingDeterminista().disponibilidad(s.paraMatching(), Instant.parse("2026-10-28T19:00:00Z"));
        assertEquals(NO_DETERMINADO, e.conclusion()); assertEquals(Causa.CONFLICTO, e.causa());
        assertEquals(s, seleccionar(List.of(sinHora, precisa)));
    }

    @Test void posteriorAcreditadaResuelveConservandoAmbosAntecedentes() {
        var a = instante(1, 1, true, "2026-10-28T15:00:00Z");
        var b = fecha(2, 2, false, "2026-10-28", "2026-10-28T18:00:00Z");
        var c = instante(3, 3, false, "2026-10-28T19:00:00Z");
        var s = seleccionar(List.of(a, b, c));
        assertNull(s.indeterminacion()); assertEquals(3, s.vigente().id()); assertTrue(s.paraMatching().indisponibilidadConocida());
        assertEquals(3, s.versionSecuencial());
    }

    @Test void registradoEnSoloAcotaNoSeUsaComoFechaDelHecho() {
        var viejaFecha = fecha(1, 1, false, "2026-10-28", "2026-10-28T15:00:00Z");
        var posterior = instante(2, 2, true, "2026-10-28T16:00:00Z");
        var s = seleccionar(List.of(viejaFecha, posterior));
        assertNull(s.indeterminacion()); assertEquals(2, s.vigente().id());
        assertNotEquals(viejaFecha.registradoEn(), viejaFecha.baseFrescura());
    }

    @Test void limiteFinDeDiaEsExclusivoAunqueRegistroSeaAlDiaSiguiente() {
        var diaAnterior = fecha(1, 1, false, "2026-10-28", "2026-10-29T17:00:00Z");
        var siguienteInicio = instante(2, 2, true, "2026-10-29T05:00:00Z");
        var s = seleccionar(List.of(diaAnterior, siguienteInicio));
        assertEquals(2, s.vigente().id()); assertNull(s.indeterminacion());
    }

    @Test void dosAfirmacionesContrariasEnMismoInstanteNoSeResuelvenPorId() {
        var s = seleccionar(List.of(instante(1, 1, true, "2026-10-28T15:00:00Z"),
                instante(999, 2, false, "2026-10-28T15:00:00Z")));
        assertEquals(Causa.CONFLICTO, s.indeterminacion());
    }

    @Test void ausenciaNoEsUnaConfirmacionDisponibleNiUnContadorUno() {
        var s = seleccionar(List.of()); assertEquals(0, s.versionSecuencial());
        assertNull(s.vigente()); assertNull(s.paraMatching());
    }

    @Test void hechoPersistidoFuturoEsFalloNoFaltanteNiCero() {
        var futuro = new Hecho(1, 1, true, INSTANTE, Instant.parse("2026-10-29T15:00:00Z"), null, Instant.parse("2026-10-28T15:00:00Z"));
        assertThrows(IllegalStateException.class, () -> seleccionar(List.of(futuro)));
    }

    private static Hecho instante(long id,long version,boolean disponible,String t) {
        Instant hecho = Instant.parse(t);
        return new Hecho(id,version,disponible,INSTANTE,hecho,null,Instant.parse("2026-10-29T22:00:00Z"));
    }
    private static Hecho fecha(long id,long version,boolean disponible,String d,String registro) {
        LocalDate dia = LocalDate.parse(d);
        return new Hecho(id,version,disponible,FECHA,dia.atStartOfDay(ZoneId.of("America/Lima")).toInstant(),dia,Instant.parse(registro));
    }
}
