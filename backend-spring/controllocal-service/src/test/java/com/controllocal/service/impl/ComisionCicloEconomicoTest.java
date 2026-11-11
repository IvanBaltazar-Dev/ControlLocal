package com.controllocal.service.impl;

import com.controllocal.domain.comercial.TipoHitoContractual;
import com.controllocal.domain.comercial.ComisionMovimiento;
import com.controllocal.service.ComisionService.*;
import com.controllocal.service.encargo.EncargoService.Tramo;
import com.controllocal.service.encargo.PactosDeEncargo.*;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.excepcion.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class ComisionCicloEconomicoTest {
    @Test void ajusteHistoricoSinReglaSeConservaPeroNoSeInterpretaComoDinero() {
        var b=new ComisionServiceImplTest.Banco();b.confirmar();b.abrir();
        var legado=new ComisionMovimiento();legado.setOrganizacionId(1L);legado.setLiquidacion(b.raiz());
        legado.setTipo(ComisionMovimiento.AJUSTE);legado.setMonto(new BigDecimal("999"));legado.setMoneda("USD");
        b.libro.add(legado);
        assertEquals("SIN_COBROS",b.ficha().cobro().estado());assertTrue(b.ficha().cobro().ingresosRecibidos().isEmpty());
        assertEquals(1,b.libro.size());assertSame(legado,b.libro.getFirst());
    }
    @Test void ordenDeCargaDeHitosNoEligeFuenteNiDuplicaLaEvaluacion() {
        var b=new ComisionServiceImplTest.Banco();b.hechos.add(minuta(b));b.confirmar();var a=b.abrir();
        Collections.reverse(b.hechos);var mismo=b.abrir();
        assertEquals(a.id(),mismo.id());assertEquals(a.version(),mismo.version());assertEquals(1,b.historia.size());
        assertEquals(a.calculo(),mismo.calculo());assertEquals(a.devengo(),mismo.devengo());
    }
    @Test void antesDelDevengoNoSeGuardaUnAnticipoComoCobro() {
        var b=new ComisionServiceImplTest.Banco();b.abrir();
        assertThrows(ReglaNegocioException.class,()->b.cobrar("5000","anticipo"));
        assertTrue(b.libro.isEmpty());assertEquals("SIN_COBROS",b.ficha().cobro().estado());
    }
    @Test void fechaHistoricaPreDevengoSigueBloqueadaAunqueHoyYaEsteDevengada() {
        var b=new ComisionServiceImplTest.Banco();b.confirmar();b.abrir();
        var d=b.cobro("5000",ComisionServiceImplTest.Banco.HOY.minusDays(2),null);
        assertThrows(ReglaNegocioException.class,()->b.sut.registrarCobro(d,b.broker,b.sid,"historico",ComisionServiceImplTest.Banco.PROC));
        assertTrue(b.libro.isEmpty());assertFalse(b.recibosGuardados.containsKey("1|historico"));
    }
    @Test void cobroPostDevengoAntesDeExigibilidadSePermiteSiAcreditaFondos() {
        var b=new ComisionServiceImplTest.Banco();
        var r=b.regla;
        b.regla=new ReglaComision(r.tipoComision(),r.baseCalculo(),r.valorComision(),r.monedaComision(),r.tratamientoIgv(),
                r.motivoSinComision(),r.hitoDevengo(),r.parteObligada(),r.beneficiario(),"Fecha expresa","TEST",List.of(),
                new EjecucionPacto(new CondicionDevengo(TipoHitoContractual.ESCRITURA),
                        new CondicionExigibilidad(null,ComisionServiceImplTest.Banco.HOY.plusDays(5),null,null,null)));
        b.confirmar();var antes=b.abrir();assertEquals(Boolean.FALSE,antes.exigibilidad().exigible());
        assertFalse(b.sut.gestionParaRadar(ComisionServiceImplTest.Banco.PACTO,ComisionServiceImplTest.Banco.CTX,b.broker).saldoExigiblePendiente());
        b.cobrar("5000","fondos");assertEquals("PARCIAL",b.ficha().cobro().estado());
        assertEquals(Boolean.FALSE,b.ficha().exigibilidad().exigible());
    }
    @Test void dosVencimientosNoImplicanDosDevengos() {
        var b=new ComisionServiceImplTest.Banco();
        b.regla=ComisionServiceImplTest.Banco.reglaFija(List.of(tramo("MINUTA","5000",null),tramo("ESCRITURA","10000",null)));
        b.hechos.add(minuta(b));var f=b.abrir();
        assertEquals(2,b.posiciones.size());assertEquals("PENDIENTE",f.devengo().estado());
        assertTrue(f.tramos().stream().allMatch(t->"PENDIENTE".equals(t.devengo().estado())));
        assertThrows(ReglaNegocioException.class,()->b.cobrar("5000","no-devengado"));
        b.confirmar();var posterior=b.abrir();
        assertEquals("DEVENGADO",posterior.devengo().estado());assertEquals(2,b.posiciones.size());
        assertEquals(new BigDecimal("15000.00"),posterior.devengo().cuantiaAcreditada().importe());
    }
    @Test void devengosParcialesSoloNacenDeCondicionesExpresasNoDelNumeroDeTramos() {
        var b=new ComisionServiceImplTest.Banco();
        var em=new EjecucionPacto(new CondicionDevengo(TipoHitoContractual.MINUTA),new CondicionExigibilidad(TipoHitoContractual.MINUTA,null,null,null,null));
        var ee=ComisionServiceImplTest.Banco.ejecucion();
        b.regla=ComisionServiceImplTest.Banco.reglaFija(List.of(tramo("MINUTA","5000",em),tramo("ESCRITURA","10000",ee)));
        b.hechos.add(minuta(b));var f=b.abrir();assertEquals("PARCIAL",f.devengo().estado());
        assertEquals(new BigDecimal("5000.00"),f.devengo().cuantiaAcreditada().importe());
        assertThrows(ReglaNegocioException.class,()->b.cobrar("5000.01","exceso"));
        var d=b.cobro("5000",ComisionServiceImplTest.Banco.HOY,f.tramos().getFirst().id());
        b.sut.registrarCobro(d,b.broker,b.sid,"minuta",ComisionServiceImplTest.Banco.PROC);
        assertEquals(new BigDecimal("5000.00"),b.ficha().cobro().ingresosRecibidos().getFirst().importe());
        assertEquals(1,b.libro.size());assertEquals(2,b.posiciones.size());
        assertFalse(b.sut.gestionParaRadar(ComisionServiceImplTest.Banco.PACTO,ComisionServiceImplTest.Banco.CTX,b.broker).saldoExigiblePendiente(),"El tramo futuro no se convierte en deuda exigible porque quede saldo total.");
        b.confirmar();b.abrir();assertTrue(b.sut.gestionParaRadar(ComisionServiceImplTest.Banco.PACTO,ComisionServiceImplTest.Banco.CTX,b.broker).saldoExigiblePendiente());
    }
    @Test void tramoAjenoNoSeImputaNiDuplicaEnSaldoGlobal() {
        var b=new ComisionServiceImplTest.Banco();b.confirmar();b.abrir();
        var d=b.cobro("5000",ComisionServiceImplTest.Banco.HOY,999L);
        assertThrows(NoEncontradoException.class,()->b.sut.registrarCobro(d,b.broker,b.sid,"ajeno",ComisionServiceImplTest.Banco.PROC));
        assertTrue(b.libro.isEmpty());
    }
    @Test void calendarioNoEjecutableConservaLiteralSinInventarCincoOQuinceDias() {
        var b=new ComisionServiceImplTest.Banco();var r=b.regla;
        b.regla=new ReglaComision(r.tipoComision(),r.baseCalculo(),r.valorComision(),r.monedaComision(),r.tratamientoIgv(),
                r.motivoSinComision(),r.hitoDevengo(),r.parteObligada(),r.beneficiario(),"Cinco días después","TEST",List.of(),
                new EjecucionPacto(new CondicionDevengo(TipoHitoContractual.ESCRITURA),
                        new CondicionExigibilidad(TipoHitoContractual.ESCRITURA,null,5,"Días",null)));
        b.confirmar();var f=b.abrir();
        assertNull(f.exigibilidad().exigible());assertNull(f.exigibilidad().fechaAcreditada());
        assertEquals("Cinco días después",f.calendario().getFirst().condicionLiteral());
        assertEquals(List.of("CONDICION_EXIGIBILIDAD_NO_EJECUTABLE"),f.exigibilidad().faltantes());
        assertTrue(b.sut.gestionParaRadar(ComisionServiceImplTest.Banco.PACTO,ComisionServiceImplTest.Banco.CTX,b.broker).basePendiente());
        assertTrue(b.libro.isEmpty());
    }
    @Test void ceroPactadoNoSeConvierteEnRecepcionNiMovimientoDeCero() {
        var b=new ComisionServiceImplTest.Banco();var r=b.regla;
        b.regla=new ReglaComision("F","N",BigDecimal.ZERO,"PEN","N","Sin comisión pactada",r.hitoDevengo(),r.parteObligada(),
                r.beneficiario(),r.exigibilidad(),r.procedencia(),List.of(),r.ejecucion());
        b.confirmar();var f=b.abrir();
        assertEquals(new BigDecimal("0.00"),f.calculo().cuantia().importe());assertEquals("SIN_COBROS",f.cobro().estado());
        assertThrows(ReglaNegocioException.class,()->b.cobrar("0","cero"));assertTrue(b.libro.isEmpty());
    }
    @Test void historiaEconomicaAnteriorNoSeReescribeAlConfirmarOtroHito() {
        var b=new ComisionServiceImplTest.Banco();var antes=b.abrir();String snapshot=b.historia.getFirst().getResultado();
        b.confirmar();var despues=b.abrir();
        assertEquals(snapshot,b.historia.getFirst().getResultado());assertEquals("PENDIENTE",antes.devengo().estado());
        assertEquals("DEVENGADO",despues.devengo().estado());assertEquals(2,b.historia.size());assertTrue(b.libro.isEmpty());
    }
    private Tramo tramo(String hito,String importe,EjecucionPacto ejecucion){return new Tramo(hito,new BigDecimal(importe),null,"PEN","Según pacto",ejecucion);}

    @Test void radarNoDistribuyeUnCobroGlobalEntreTramosSinEvidencia(){
        var b=new ComisionServiceImplTest.Banco();
        var em=new EjecucionPacto(new CondicionDevengo(TipoHitoContractual.MINUTA),new CondicionExigibilidad(TipoHitoContractual.MINUTA,null,null,null,null));
        b.regla=ComisionServiceImplTest.Banco.reglaFija(List.of(tramo("MINUTA","5000",em),tramo("ESCRITURA","10000",ComisionServiceImplTest.Banco.ejecucion())));
        b.hechos.add(minuta(b));b.confirmar();b.abrir();b.cobrar("5000","fondos-globales");
        var g=b.sut.gestionParaRadar(ComisionServiceImplTest.Banco.PACTO,ComisionServiceImplTest.Banco.CTX,b.broker);
        assertNull(g.saldoExigiblePendiente());assertEquals(List.of("COBROS_SIN_TRAMO_ACREDITADO"),g.faltantes());assertEquals(1,b.libro.size());
    }
    private FuenteHitoConfirmado minuta(ComisionServiceImplTest.Banco b){return new FuenteHitoConfirmado(ComisionServiceImplTest.Banco.CTX,
            new ReferenciaHito(10,11),TipoHitoContractual.MINUTA,ComisionServiceImplTest.Banco.HOY.minusDays(1),
            ComisionServiceImplTest.Banco.AHORA,ComisionServiceImplTest.Banco.PACTO,b.fuente.referencia(),null,ComisionServiceImplTest.Banco.PROC);}
}
