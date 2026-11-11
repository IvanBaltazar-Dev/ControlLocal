package com.controllocal.service.soporte;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AvanceDeMetaTest {
    private final LocalDate hoy=LocalDate.of(2026,11,10);
    private final List<LocalDate> agenda=List.of(hoy.minusDays(1),hoy,hoy.plusDays(2),hoy.plusDays(6));
    @Test void sinMetaNoSeConvierteEnObjetivoCero(){var r=AvanceDeMeta.de(0L,0,null,agenda,hoy);assertEquals(0L,r.actual());assertNull(r.metaPeriodo());assertNull(r.faltante());assertNull(r.porcentajeMeta());assertEquals("SIN_META",r.estadoCalculo());}
    @Test void metaCeroExpresaSeDistingueDeAusencia(){var r=AvanceDeMeta.de(0L,0,0L,agenda,hoy);assertEquals(0L,r.metaPeriodo());assertEquals(0L,r.faltante());assertNull(r.porcentajeMeta());assertEquals(BigDecimal.ZERO,r.esperadoHastaFechaCorte().stripTrailingZeros());}
    @Test void datosInsuficientesNoSonCeroObservado(){var r=AvanceDeMeta.de(null,2,8L,agenda,hoy);assertNull(r.actual());assertEquals(2,r.realizadoConocido());assertNull(r.faltante());assertEquals(8L,r.metaPeriodo());assertEquals("SIN_EVIDENCIA_SUFICIENTE",r.estadoCalculo());}
    @Test void sinCalendarioSeConservanHechosYBrechaSinInventarDias(){var r=AvanceDeMeta.de(2L,2,8L,null,hoy);assertEquals(6L,r.faltante());assertNull(r.esperadoHastaFechaCorte());assertNull(r.necesarioPorFechaRestante());assertEquals("SIN_CALENDARIO_DECLARADO",r.estadoCalculo());}
    @Test void agendaExpresaCuentaSoloSusFechas(){var r=AvanceDeMeta.de(2L,2,8L,agenda,hoy);assertEquals(2,r.fechasHastaCorte());assertEquals(2,r.fechasRestantes());assertEquals(new BigDecimal("4.0000"),r.esperadoHastaFechaCorte());assertEquals(new BigDecimal("3.0000"),r.necesarioPorFechaRestante());}
    @Test void metaPequenaNoActivaUnUmbralNiCadenciaFicticia(){var r=AvanceDeMeta.de(0L,0,2L,agenda,hoy);assertEquals(new BigDecimal("1.0000"),r.esperadoHastaFechaCorte());assertEquals(new BigDecimal("1.0000"),r.necesarioPorFechaRestante());}
    @Test void terminarCalendarioNoInventaUnDiaAdicional(){var r=AvanceDeMeta.de(1L,1,8L,agenda,hoy.plusMonths(1));assertNull(r.necesarioPorFechaRestante());assertEquals(7L,r.faltante());assertEquals("CALENDARIO_TERMINADO",r.estadoCalculo());}
    @Test void superacionNoSeTruncaAlCienPorCientoNiCreaFaltanteNegativo(){var r=AvanceDeMeta.de(12L,12,8L,agenda,hoy);assertEquals(new BigDecimal("150.00"),r.porcentajeMeta());assertEquals(0L,r.faltante());}
    @Test void diasFuturosNoSonEventosRealizados(){var r=AvanceDeMeta.de(0L,0,8L,List.of(hoy.plusDays(1),hoy.plusDays(2)),hoy);assertEquals(0,r.fechasHastaCorte());assertEquals(new BigDecimal("0.0000"),r.esperadoHastaFechaCorte());assertEquals(0L,r.actual());}
    @Test void calendarioNoSePonderaRepitiendoUnaFecha(){assertThrows(IllegalArgumentException.class,()->AvanceDeMeta.de(0L,0,8L,List.of(hoy,hoy),hoy));}
    @Test void importesGrandesNoDesbordanElPorcentaje(){var r=AvanceDeMeta.de(Long.MAX_VALUE,Long.MAX_VALUE,1L,agenda,hoy);assertEquals(new BigDecimal("922337203685477580700.00"),r.porcentajeMeta());assertEquals(0L,r.faltante());}
    @Test void catalogoNoAceptaCPSFNiDeduceOperacion(){assertThrows(com.controllocal.service.excepcion.ReglaNegocioException.class,()->IndicadorMetaComercial.exigir("C"));assertThrows(com.controllocal.service.excepcion.ReglaNegocioException.class,()->IndicadorMetaComercial.OPORTUNIDAD_APERTURA.operacion(null));assertThrows(com.controllocal.service.excepcion.ReglaNegocioException.class,()->IndicadorMetaComercial.PROSPECCION_EFECTIVA_SALIENTE.operacion("VENTA"));}
}
