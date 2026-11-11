package com.controllocal.service.presupuesto;

import com.controllocal.domain.planes.*;
import com.controllocal.domain.presupuesto.*;
import com.controllocal.domain.presupuesto.UnidadesDeConsumo.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class B0PrecisionYOfertaTest {
    private static BigDecimal n(String s){return new BigDecimal(s);}
    private static CotizacionDePeriodo mejora(BigDecimal financiado,BigDecimal nuevo,Instant desde,Instant hasta,Instant corte){
        return CotizacionDePeriodo.mejoraConLineas(nuevo,List.of(new CotizacionDePeriodo.LineaFinanciada(financiado,desde,hasta)),corte);
    }
    @Test void ofertaAprobadaNoMultiplicaBroker(){
        assertEquals(n("75.00"),PlanBrox.TEAM.precioUsd());assertEquals(21000,PlanBrox.TEAM.capacidadTotal());
        assertEquals(39000,PlanBrox.GROWTH.capacidadTotal());assertEquals(57000,PlanBrox.SCALE.capacidadTotal());
        assertEquals(75000,PlanBrox.PRIME.capacidadTotal());assertEquals(3000,PlanBrox.PRIME.bolsaBroker());
        assertEquals(12000,PlanBrox.PRIME.reservaAsignable());assertEquals(n("5.00"),PlanBrox.precioBrokerExtraUsd());
        assertEquals(0,PlanBrox.FREE.capacidadTotal());assertFalse(PlanBrox.FREE.pagado());
        assertTrue(PlanBrox.ONE_PRO.proPersonal());assertFalse(PlanBrox.ONE.proPersonal());
    }
    @Test void mejoraOneEn31DiasConDiezPendientes(){
        var c=mejora(n("15"),n("29"),Instant.parse("2026-10-01T15:00:00Z"),
                Instant.parse("2026-11-01T15:00:00Z"),Instant.parse("2026-10-22T15:00:00Z"));
        assertEquals(n("4.84"),c.abonoTemporalUsd());assertEquals(n("24.16"),c.totalUsd());
    }
    @Test void mejoraTeamYCiclo30Dias(){
        Instant desde=Instant.parse("2026-10-01T15:00:00Z"),hasta=Instant.parse("2026-11-01T15:00:00Z"),corte=Instant.parse("2026-10-22T15:00:00Z");
        var c=mejora(n("75"),n("150"),desde,hasta,corte);
        assertEquals(n("24.19"),c.abonoTemporalUsd());assertEquals(n("125.81"),c.totalUsd());
        assertEquals(n("1.61"),CotizacionDePeriodo.brokerAdicional(desde,hasta,corte));
        var treinta=mejora(n("15"),n("29"),desde,Instant.parse("2026-10-31T15:00:00Z"),Instant.parse("2026-10-21T15:00:00Z"));
        assertEquals(n("5.00"),treinta.abonoTemporalUsd());assertEquals(n("24.00"),treinta.totalUsd());
    }
    @Test void conservaFraccionDeDiaYRedondeaDescuentoUnaVez(){
        Instant desde=Instant.parse("2026-10-01T00:00:00Z"),hasta=desde.plusSeconds(1000),corte=desde.plusSeconds(677);
        var c=mejora(n("15"),n("29"),desde,hasta,corte);
        assertEquals(n("4.85"),c.abonoTemporalUsd());assertEquals(n("24.15"),c.totalUsd());
        assertEquals(c.periodoNuevoUsd(),c.abonoTemporalUsd().add(c.totalUsd()));
        assertThrows(IllegalArgumentException.class,()->mejora(n("15"),n("1"),desde,hasta,corte));
    }
    @Test void variasLineasCompartenUnSoloRedondeoDelAbono(){
        Instant desde=Instant.parse("2026-10-01T00:00:00Z"),hasta=desde.plusSeconds(2),corte=desde.plusSeconds(1);
        var lineas=List.of(new CotizacionDePeriodo.LineaFinanciada(n("0.01"),desde,hasta),new CotizacionDePeriodo.LineaFinanciada(n("0.01"),desde,hasta));
        var c=CotizacionDePeriodo.mejoraConLineas(n("1"),lineas,corte);
        assertEquals(n("0.01"),c.abonoTemporalUsd());assertEquals(n("0.99"),c.totalUsd());
    }
    @Test void serializaListasOriginalesSinConfundirlasConUnObjetoDeRevision(){
        var documentos=new com.controllocal.service.soporte.Documentos(new com.fasterxml.jackson.databind.ObjectMapper());
        var unidades=List.of(new Medicion("entrada","u",n("1.000000000001"),null));
        String texto=documentos.lista(unidades);assertTrue(texto.startsWith("["));
        assertEquals(n("1.000000000001"),documentos.comoObjeto(texto,Medicion[].class)[0].cantidad());
        String publico=documentos.objetoCanonico(new com.controllocal.service.presupuesto.PresupuestoIaService.Admision(java.util.UUID.randomUUID(),"RESERVADA"));
        assertFalse(publico.contains("costo"));assertFalse(publico.contains("tarifa"));assertFalse(publico.contains("modelo"));
    }
    @Test void gbDecimalAprobadoConservaUltimoByteYAvisosExactos(){
        assertEquals(2_000_000_000L,PlanBrox.FREE.almacenamientoBytes());assertEquals(120_000_000_000L,PlanBrox.PRIME.almacenamientoBytes());
        assertEquals(AlmacenamientoComercial.Aviso.NORMAL,AlmacenamientoComercial.aviso(1_599_999_999L,2_000_000_000L));
        assertEquals(AlmacenamientoComercial.Aviso.UMBRAL_80,AlmacenamientoComercial.aviso(1_600_000_000L,2_000_000_000L));
        assertEquals(AlmacenamientoComercial.Aviso.UMBRAL_95,AlmacenamientoComercial.aviso(1_900_000_000L,2_000_000_000L));
        assertEquals(AlmacenamientoComercial.Aviso.UMBRAL_95,AlmacenamientoComercial.aviso(1_999_999_999L,2_000_000_000L));
        assertEquals(AlmacenamientoComercial.Aviso.LIMITE,AlmacenamientoComercial.aviso(2_000_000_000L,2_000_000_000L));
        assertEquals(AlmacenamientoComercial.Aviso.UMBRAL_95,AlmacenamientoComercial.aviso(Long.MAX_VALUE-1,Long.MAX_VALUE));
        assertThrows(ArithmeticException.class,()->AlmacenamientoComercial.bytesDeGb(Long.MAX_VALUE));
    }
    @Test void aniversarioOriginalNoSePierdeEnFebrero(){
        Instant enero=ZonedDateTime.of(2026,1,31,10,0,0,0,CalendarioComercial.ZONA).toInstant();
        Instant febrero=CalendarioComercial.siguienteMes(enero,31),marzo=CalendarioComercial.siguienteMes(febrero,31);
        assertEquals(28,febrero.atZone(CalendarioComercial.ZONA).getDayOfMonth());assertEquals(31,marzo.atZone(CalendarioComercial.ZONA).getDayOfMonth());
        Instant bisiesto=ZonedDateTime.of(2024,2,29,10,0,0,0,CalendarioComercial.ZONA).toInstant();
        assertEquals(28,CalendarioComercial.vencimientoBoost(bisiesto).atZone(CalendarioComercial.ZONA).getDayOfMonth());
        assertFalse(CalendarioComercial.contiene(enero,febrero,febrero));assertTrue(CalendarioComercial.contiene(enero,febrero,enero));
    }
    @Test void liquidacionDoceDecimalesHalfEvenSinEnterizarDebito(){
        assertEquals(n("1.000000000000"),PrecisionCapacidad.liquidar(n("1.0000000000005")));
        assertEquals(n("1.000000000002"),PrecisionCapacidad.liquidar(n("1.0000000000015")));
        BigDecimal saldo=n("3000").subtract(PrecisionCapacidad.liquidar(n("12.345678901234")));
        assertEquals(n("2987.654321098766"),saldo);
        assertThrows(IllegalArgumentException.class,()->PrecisionCapacidad.liquidar(n("-0.1")));
    }
    @Test void porcentajesBoostEnterosConRemanenteDistinguido(){
        var p=CapacidadPublica.de(n("2987.654321098766"),n("3000"),n("4987.654321098766"));
        assertEquals(99,p.porcentajeMensual());assertEquals(4987,p.boostEntero());assertTrue(p.financiacionDisponible());
        var pequeno=CapacidadPublica.de(n("0.001"),n("3000"),n("0.000000000001"));
        assertTrue(pequeno.menosDeUnoPorCiento());assertTrue(pequeno.menosDeUnCredito());assertTrue(pequeno.financiacionDisponible());
        assertFalse(CapacidadPublica.de(n("0"),n("3000"),n("0")).financiacionDisponible());
        assertEquals(100,CapacidadPublica.de(n("3000"),n("3000"),n("0")).porcentajeMensual());
    }
    @Test void cacheSubconjuntoSeRestaDelTotalAntesDeAplicarTarifa(){
        // Tarifas artificiales de prueba, NO tabla operativa ni calibración IA.
        var m=List.of(new Medicion("entrada","unidad",n("100"),null),new Medicion("cache","unidad",n("40"),"entrada"));
        var t=List.of(new Tarifa("entrada","unidad",n("2"),n("100")),new Tarifa("cache","unidad",n("1"),n("100")));
        assertEquals(0,n("1.6").compareTo(UnidadesDeConsumo.costoSinCuantizar(m,t)));
        assertEquals(n("6.400000000000"),UnidadesDeConsumo.creditosLiquidados(m,t,n("0.25")));
        assertEquals(n("100"),m.getFirst().cantidad());
    }
    @Test void rechazaMedicionIncompatibleDuplicadaCircularOSubconjuntoMayor(){
        var t=List.of(new Tarifa("a","u",n("1"),n("1")),new Tarifa("b","u",n("1"),n("1")));
        assertThrows(IllegalArgumentException.class,()->UnidadesDeConsumo.costoSinCuantizar(List.of(new Medicion("a","u",n("1"),null),new Medicion("b","u",n("2"),"a")),t));
        assertThrows(IllegalArgumentException.class,()->UnidadesDeConsumo.costoSinCuantizar(List.of(new Medicion("a","u",n("1"),"b"),new Medicion("b","u",n("1"),"a")),t));
        assertThrows(IllegalArgumentException.class,()->UnidadesDeConsumo.costoSinCuantizar(List.of(new Medicion("a","u",n("1"),null),new Medicion("a","u",n("1"),null)),t));
        assertThrows(IllegalArgumentException.class,()->UnidadesDeConsumo.costoSinCuantizar(List.of(new Medicion("a","otra",n("1"),null)),t));
    }
}
