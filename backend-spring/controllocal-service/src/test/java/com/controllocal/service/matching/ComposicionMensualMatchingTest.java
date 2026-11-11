package com.controllocal.service.matching;

import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.soporte.ValorLogico;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.controllocal.service.matching.ContratoMatching.*;
import static com.controllocal.service.matching.ContratoMatching.Conclusion.*;
import static org.junit.jupiter.api.Assertions.*;

class ComposicionMensualMatchingTest {
    private static final Instant CORTE = Instant.parse("2026-10-28T15:00:00Z");
    private static final ContextoValor TOTAL = contexto("TOTAL_MENSUAL_OBLIGATORIO", "PEN", "MENSUAL", "una");
    private final MotorMatchingDeterminista motor = new MotorMatchingDeterminista();

    @Test void mensualIncluyeRentaMantenimientoYCargoFijoPeroNoDuplicaIncluido() {
        Dato total = total(List.of(componente("renta", "2100", TratamientoMensual.EXIGIBLE),
                componente("mantenimiento", "200", TratamientoMensual.EXIGIBLE),
                componente("seguridad", "50", TratamientoMensual.EXIGIBLE),
                componente("incluido", "100", TratamientoMensual.INCLUIDO_EN_OTRO)));
        assertEquals(0, new BigDecimal("2350").compareTo(total.valor().numero()));
        assertFalse(total.intervalo()); assertEquals(CUMPLE, comparar(total, "2350").conclusion());
    }

    @Test void descuentoSoloEntraSiEsFirmeYNoTieneSignoInventado() {
        Dato total = total(List.of(componente("renta", "2400", TratamientoMensual.EXIGIBLE),
                componente("descuento-aceptado", "100", TratamientoMensual.DESCUENTO_FIRME)));
        assertEquals(0, new BigDecimal("2300").compareTo(total.valor().numero()));
        assertEquals(INCUMPLE, comparar(total, "2299.99").conclusion());
    }

    @Test void mantenimientoDesconocidoNoEsCeroNiBloqueaDemostrarExcesoConocido() {
        var mantenimiento = new Dato("mantenimiento", TipoDato.IMPORTE, null, null, false,
                contexto("MANTENIMIENTO", "PEN", "MENSUAL", "una"), meta(EstadoDato.FALTANTE, null));
        Dato total = total(List.of(componente("renta", "2400", TratamientoMensual.EXIGIBLE),
                new ComponenteMensual("mantenimiento", mantenimiento, TratamientoMensual.EXIGIBLE)));
        assertTrue(total.intervalo()); assertNull(total.hasta());
        assertEquals(NO_DETERMINADO, comparar(total, "2500").conclusion());
        assertEquals(Causa.DATO_FALTANTE, comparar(total, "2500").causa());
        assertEquals(INCUMPLE, comparar(total, "2300").conclusion());
    }

    @Test void descuentoFirmeConMontoDesconocidoNoSeInventaComoCero() {
        var descuento = new Dato("descuento", TipoDato.IMPORTE, null, null, false,
                contexto("DESCUENTO", "PEN", "MENSUAL", "una"), meta(EstadoDato.FALTANTE, null));
        Dato total = total(List.of(componente("renta", "2400", TratamientoMensual.EXIGIBLE),
                new ComponenteMensual("descuento", descuento, TratamientoMensual.DESCUENTO_FIRME)));
        assertTrue(total.intervalo()); assertNull(total.valor());
        assertEquals(NO_DETERMINADO, comparar(total, "2000").conclusion());
        assertEquals(CUMPLE, comparar(total, "2400").conclusion());
    }

    @Test void monedasPorComponenteNoSeSumanNiSeReetiquetan() {
        var usd = new Dato("mantenimiento", TipoDato.IMPORTE, ValorLogico.deImporte(new BigDecimal("100"), "USD"), null, false,
                contexto("MANTENIMIENTO", "USD", "MENSUAL", "una"), conocido());
        Dato total = total(List.of(componente("renta", "2000", TratamientoMensual.EXIGIBLE),
                new ComponenteMensual("mantenimiento", usd, TratamientoMensual.EXIGIBLE)));
        assertEquals(Causa.MONEDA_NO_COMPARABLE, comparar(total, "2500").causa());
        assertNull(total.valor());
    }

    @Test void noProrrateaGarantiaAnualNiCruzaConfiguracion() {
        var anual = new Dato("cargo", TipoDato.IMPORTE, importe("1200"), null, false,
                contexto("CARGO_ANUAL", "PEN", "ANUAL", "una"), conocido());
        assertEquals(Causa.PERIODO_INCOMPATIBLE, total(List.of(new ComponenteMensual("anual", anual, TratamientoMensual.EXIGIBLE))).metadatos().causa());
        var otra = new Dato("cargo", TipoDato.IMPORTE, importe("100"), null, false,
                contexto("RENTA", "PEN", "MENSUAL", "otra"), conocido());
        assertEquals(Causa.CONFIGURACION_INCOMPATIBLE, total(List.of(new ComponenteMensual("otra", otra, TratamientoMensual.EXIGIBLE))).metadatos().causa());
    }

    @Test void componenteDuplicadoNoSumaDosVecesYConflictoNoSePromedia() {
        ComponenteMensual renta = componente("renta", "2400", TratamientoMensual.EXIGIBLE);
        assertEquals(0, new BigDecimal("2400").compareTo(total(List.of(renta, renta)).valor().numero()));
        assertThrows(IllegalStateException.class, () -> total(List.of(renta, componente("renta", "2200", TratamientoMensual.EXIGIBLE))));
    }

    @Test void sinComponentesExigiblesNoInventaMensualidadGratis() {
        assertEquals(Causa.DATO_FALTANTE, total(List.of()).metadatos().causa());
        assertEquals(Causa.DATO_FALTANTE, total(List.of(componente("incluido", "0", TratamientoMensual.INCLUIDO_EN_OTRO))).metadatos().causa());
        assertEquals(0, total(List.of(componente("renta", "0", TratamientoMensual.EXIGIBLE))).valor().numero().signum());
    }

    @Test void intervaloEconomicoPermiteConclusionesAcotadasYNoUnaMediaFicticia() {
        Dato variable = new Dato("cargo", TipoDato.IMPORTE, importe("100"), importe("200"), true,
                contexto("CARGO_FIJO_DECLARADO", "PEN", "MENSUAL", "una"), conocido());
        Dato total = total(List.of(componente("renta", "2200", TratamientoMensual.EXIGIBLE),
                new ComponenteMensual("cargo", variable, TratamientoMensual.EXIGIBLE)));
        assertEquals(CUMPLE, comparar(total, "2400").conclusion());
        assertEquals(NO_DETERMINADO, comparar(total, "2350").conclusion());
        assertEquals(INCUMPLE, comparar(total, "2299.99").conclusion());
    }

    @Test void conflictoActivoEnComponenteNoSeDisfrazaDeRango() {
        Dato conflictivo = new Dato("cargo", TipoDato.IMPORTE, importe("100"), null, false,
                contexto("CARGO", "PEN", "MENSUAL", "una"), meta(EstadoDato.CONOCIDO, Causa.CONFLICTO));
        assertEquals(Causa.CONFLICTO, total(List.of(componente("renta", "2000", TratamientoMensual.EXIGIBLE),
                new ComponenteMensual("cargo", conflictivo, TratamientoMensual.EXIGIBLE))).metadatos().causa());
    }

    @Test void listaDeCargosConocidosNoAcreditaQueSeanTodosLosExigibles() {
        var incompleto = ComposicionMensualMatching.total("total", List.of(componente("renta", "2000", TratamientoMensual.EXIGIBLE)),
                false, TOTAL, conocido(), CORTE);
        assertEquals(NO_DETERMINADO, comparar(incompleto, "2500").conclusion());
        assertEquals(INCUMPLE, comparar(incompleto, "1900").conclusion());
    }

    @Test void unaTarifaPorMetroNoEsUnaCotaMensualDelObjetoNiSeMultiplicaPorArea() {
        var tarifa=new Dato("renta",TipoDato.IMPORTE,importe("10"),null,false,
                new ContextoValor("PRECIO_ALQUILER","m2","PEN","MENSUAL","ALQUILER","una"),conocido());
        for(boolean completos:List.of(false,true)) {
            var total=ComposicionMensualMatching.total("total",List.of(new ComponenteMensual("tarifa",tarifa,TratamientoMensual.EXIGIBLE)),
                    completos,TOTAL,conocido(),CORTE);
            assertNull(total.valor());assertNull(total.hasta());assertEquals(EstadoDato.FALTANTE,total.metadatos().estado());
            assertEquals(NO_DETERMINADO,comparar(total,"6").conclusion());assertEquals(Causa.UNIDAD_INCOMPATIBLE,comparar(total,"6").causa());
            assertEquals(0,new BigDecimal("10").compareTo(tarifa.valor().numero()));assertEquals("m2",tarifa.contexto().unidad());
        }
    }

    /** Estos fixtures declaran la lista completa; el caso incompleto anterior la niega explícitamente. */
    private Dato total(List<ComponenteMensual> componentes) { return ComposicionMensualMatching.total("total", componentes, true, TOTAL, conocido(), CORTE); }
    private ResultadoCondicion comparar(Dato dato, String maximo) {
        var c = new Condicion("total", TipoDato.IMPORTE, OperadorCriterio.MAXIMO, importe(maximo), null, false, TOTAL, conocido());
        return motor.evaluarCriterio(new Criterio("presupuesto", "presupuesto", Exigencia.OBLIGATORIO, null, List.of(c)), List.of(dato), CORTE);
    }
    private static ComponenteMensual componente(String ref, String monto, TratamientoMensual tratamiento) {
        return new ComponenteMensual(ref, new Dato(ref, TipoDato.IMPORTE, importe(monto), null, false,
                contexto(ref, "PEN", "MENSUAL", "una"), conocido()), tratamiento);
    }
    private static ValorLogico importe(String n) { return ValorLogico.deImporte(new BigDecimal(n), "PEN"); }
    private static ContextoValor contexto(String concepto, String moneda, String periodo, String config) { return new ContextoValor(concepto, null, moneda, periodo, "ALQUILER", config); }
    private static MetadatosDato conocido() { return meta(EstadoDato.CONOCIDO, null); }
    private static MetadatosDato meta(EstadoDato estado, Causa causa) { return new MetadatosDato(estado, causa, "CAT:D/1/1", 1L, "e", "fuente TEST", null, null, null); }
}
