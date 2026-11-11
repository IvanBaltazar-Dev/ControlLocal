package com.controllocal.service.impl;

import com.controllocal.domain.comercial.ComisionLiquidacion;
import com.controllocal.service.Actor;
import com.controllocal.service.ComisionService.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unidad de fondos/gestión. Los antecedentes P/R se reconstruyen sólo para lectura. */
class CicloComisionTest {
    private static final String REPROGRAMAR=ComisionServiceImpl.REPROGRAMAR,CONCLUIR=ComisionServiceImpl.CONCLUIR;
    private ComisionServiceImplTest.Banco devengada(){var b=new ComisionServiceImplTest.Banco();b.confirmar();b.abrir();return b;}
    private BigDecimal ingreso(FichaComision f,String moneda){return f.cobro().ingresosRecibidos().stream().filter(d->moneda.equals(d.moneda())).findFirst().orElseThrow().importe();}
    private DatosReprogramacion reprogramacion(ComisionServiceImplTest.Banco b,LocalDate fecha,String motivo){return new DatosReprogramacion(b.raiz().getId(),b.raiz().getVersion(),fecha,motivo);}
    private DatosConclusion conclusion(ComisionServiceImplTest.Banco b,String motivo){return new DatosConclusion(b.raiz().getId(),b.raiz().getVersion(),motivo);}
    private Map<String,Object> mapa(ComisionServiceImplTest.Banco b,Object datos){return b.json.convertValue(datos,new TypeReference<Map<String,Object>>(){});}

    @Test @SuppressWarnings("unchecked") void reciboAlteradoNoReemplazaElImporteDelMovimientoInmutable() {
        var b=devengada();b.cobrar("100","original");
        var comando=b.recibosGuardados.get("1|original");var docs=new Documentos(b.json);
        var contenido=docs.comoMapa(comando.getResultado());var resultado=(Map<String,Object>)contenido.get("resultado");
        ((Map<String,Object>)resultado.get("importe")).put("importe",new BigDecimal("101.00"));
        ReflectionTestUtils.setField(comando,"resultado",docs.objetoCanonico(contenido));
        assertThrows(IllegalStateException.class,()->b.sut.consultarResultadoCobro("original",b.broker));
        assertEquals(new BigDecimal("100.00"),b.libro.getFirst().getMonto());assertEquals(1,b.libro.size());
    }
    @Test void sinCobrosConservaSaldoCompletoYRepartoDesconocido() {
        var b=devengada();var f=b.ficha();assertEquals("SIN_COBROS",f.cobro().estado());
        assertEquals(new BigDecimal("15000.00"),f.cobro().saldoInformativoSobreCalculo().importe());
        assertTrue(f.cobro().ingresosRecibidos().isEmpty());assertNull(f.reparto().agente());assertNull(f.reparto().saldoPagoAgente());
        assertFalse(f.gestion().concluida());assertNull(f.gestion().registradoEn());assertTrue(b.libro.isEmpty());
    }
    @Test void unPagoPactadoRecibidoEnDosDepositosNoCreaTramos() {
        var b=devengada();b.cobrar("5000","deposito1");assertEquals("PARCIAL",b.ficha().cobro().estado());
        assertEquals(new BigDecimal("10000.00"),b.ficha().cobro().saldoInformativoSobreCalculo().importe());
        b.cobrar("10000","deposito2");var f=b.ficha();assertEquals("COBRADO",f.cobro().estado());
        assertEquals(new BigDecimal("15000.00"),ingreso(f,"PEN"));assertEquals(2,b.libro.size());
        assertTrue(b.posiciones.isEmpty());assertTrue(f.tramos().isEmpty());assertNull(b.raiz().getEstadoLegado());
    }
    @Test void mismaClaveDevuelveCobroOriginalAunqueCambienSaldoYVersionActuales() {
        var b=devengada();var d=b.cobro("5000");var a=b.sut.registrarCobro(d,b.broker,b.sid,"repetido",ComisionServiceImplTest.Banco.PROC);
        b.cobrar("1000","otro");
        assertEquals(a,b.sut.registrarCobro(d,b.broker,b.sid,"repetido",ComisionServiceImplTest.Banco.PROC));
        assertEquals(a,b.sut.consultarResultadoCobro("repetido",b.broker).orElseThrow());
        assertEquals(2,b.libro.size());assertEquals(new BigDecimal("6000.00"),ingreso(b.ficha(),"PEN"));
    }
    @Test void mismaClaveOtroContenidoEsConflictoNoOtroCobro() {
        var b=devengada();b.cobrar("5000","repetido");
        assertThrows(ConflictoException.class,()->b.cobrar("6000","repetido"));assertEquals(1,b.libro.size());
    }
    @Test void dosDepositosIgualesConClavesDistintasSonDosHechos() {
        var b=devengada();b.cobrar("5000","a");b.cobrar("5000","b");
        assertEquals(2,b.libro.size());assertEquals(new BigDecimal("10000.00"),ingreso(b.ficha(),"PEN"));
    }
    @Test void cobroNoSuperaSaldoPendienteDevengado() {
        var b=devengada();b.cobrar("5000","a");
        assertThrows(ReglaNegocioException.class,()->b.cobrar("10000.01","exceso"));assertEquals(1,b.libro.size());
    }
    @Test void reversionHistoricaReduceIngresosSinBorrarNiReinterpretarFilas() {
        var b=devengada();b.cobrar("5000","a");var r=b.historico("R","1000","PEN");String tipo=r.getTipo();
        assertEquals(new BigDecimal("4000.00"),ingreso(b.ficha(),"PEN"));
        assertEquals(new BigDecimal("11000.00"),b.ficha().cobro().saldoInformativoSobreCalculo().importe());
        assertEquals(tipo,r.getTipo());assertEquals(2,b.libro.size());
    }
    @Test void reversoHistoricoSinCobroOSuperiorAlRecibidoEsFalloTecnicoNoDeuda() {
        var b=devengada();b.historico("R","1","PEN");assertThrows(IllegalStateException.class,b::ficha);
        var otro=devengada();otro.cobrar("5000","a");otro.historico("R","5000.01","PEN");
        assertThrows(IllegalStateException.class,otro::ficha);assertEquals(2,otro.libro.size());
    }
    @Test void terminalLegadoNoImpideOtroCobroParcialTrasRectificacionMayor() {
        var b=new ComisionServiceImplTest.Banco();b.porcentaje();b.confirmar();b.abrir();b.cobrar("15000","total");
        ReflectionTestUtils.setField(b.raiz(),"estado",ComisionLiquidacion.COBRADA); // Antecedente técnico, no nueva transición.
        b.rectificarVenta("666666.67",12,22);b.abrir();b.cobrar("1000","nuevo");
        assertEquals("PARCIAL",b.ficha().cobro().estado());assertEquals(new BigDecimal("16000.00"),ingreso(b.ficha(),"PEN"));
        assertEquals(new BigDecimal("4000.00"),b.ficha().cobro().saldoInformativoSobreCalculo().importe());
        assertEquals(ComisionLiquidacion.COBRADA,b.raiz().getEstadoLegado());assertEquals(2,b.libro.size());
    }
    @Test void pagoHistoricoConservaParteYBaseOriginalSinExponerNuevoEfectorSaliente() {
        var b=devengada();var r=b.sut.asignarReparto(b.reparto("5000"),b.broker,b.sid,"asignar",ComisionServiceImplTest.Banco.PROC);
        b.historico("P","3000","PEN");var f=b.ficha();
        assertEquals(new BigDecimal("3000.00"),f.reparto().pagadoAgente());assertEquals(new BigDecimal("2000.00"),f.reparto().saldoPagoAgente());
        assertEquals(r.reparto().evaluacionBase(),f.reparto().evaluacionBase());assertEquals("PEN",f.reparto().moneda());
    }
    @Test void pagoHistoricoNoSeCuentaComoIngresoNiExigeCobroFicticio() {
        var b=devengada();b.sut.asignarReparto(b.reparto("5000"),b.broker,b.sid,"asignar",ComisionServiceImplTest.Banco.PROC);
        b.historico("P","1000","PEN");assertTrue(b.ficha().cobro().ingresosRecibidos().isEmpty());
        assertEquals(new BigDecimal("1000.00"),b.ficha().reparto().pagadoAgente());assertEquals(1,b.libro.size());
    }
    @Test void repartoAdmiteParcialPeroNoBajaDeLoHistoricamentePagado() {
        var b=devengada();b.cobrar("5000","cobro");
        b.sut.asignarReparto(b.reparto("5000"),b.broker,b.sid,"asignar",ComisionServiceImplTest.Banco.PROC);b.historico("P","3000","PEN");
        assertThrows(ReglaNegocioException.class,()->b.sut.validarReparto(b.reparto("2999.99"),b.broker));
        var r=b.sut.asignarReparto(b.reparto("6000"),b.broker,b.sid,"reparto2",ComisionServiceImplTest.Banco.PROC);
        assertEquals(new BigDecimal("6000.00"),r.reparto().agente());assertEquals(new BigDecimal("9000.00"),r.reparto().empresa());
    }
    @Test void repartoCerradoTrasCobroTotalYFueraDeBrutaONegativoRechazados() {
        var b=devengada();assertThrows(ReglaNegocioException.class,()->b.sut.validarReparto(b.reparto("-1"),b.broker));
        assertThrows(ReglaNegocioException.class,()->b.sut.validarReparto(b.reparto("15000.01"),b.broker));b.cobrar("15000","total");
        assertThrows(ReglaNegocioException.class,()->b.sut.validarReparto(b.reparto("5000"),b.broker));
    }
    @Test void codigoAnuladoHistoricoNoSustituyeGuardasActualesDeDerechoYFondos() {
        var b=devengada();ReflectionTestUtils.setField(b.raiz(),"estado",ComisionLiquidacion.ANULADA);
        b.cobrar("1","real");assertEquals(new BigDecimal("1.00"),ingreso(b.ficha(),"PEN"));
        assertEquals(ComisionLiquidacion.ANULADA,b.raiz().getEstadoLegado());
    }
    @Test void fechaAusenteFuturaMonedaIncompatibleYCeroNoProducenDinero() {
        var b=devengada();assertThrows(ReglaNegocioException.class,()->b.sut.validarCobro(b.cobro("1",null,null),b.broker));
        assertThrows(ReglaNegocioException.class,()->b.sut.validarCobro(b.cobro("1",ComisionServiceImplTest.Banco.HOY.plusDays(1),null),b.broker));
        assertThrows(ReglaNegocioException.class,()->b.cobrar("0","cero"));var d=b.cobro("1");
        var usd=new DatosCobro(d.comision(),d.versionEsperada(),d.tramo(),d.monto(),"USD",d.fecha(),d.formaPago(),
                d.parteObligada(),d.beneficiario(),d.destino(),d.referenciaExterna(),d.evidencias(),d.observacion());
        assertThrows(ReglaNegocioException.class,()->b.sut.validarCobro(usd,b.broker));assertTrue(b.libro.isEmpty());
    }
    @Test void evidenciaOIdentidadRevocadaDeniegaValidacionEfectoYRecibo() {
        var b=devengada();var a=b.cobrar("100","cobro");doThrow(new AccesoNoAutorizadoException()).when(b.referencias).exigirLectura(any(),any(),any(),any());
        assertThrows(AccesoNoAutorizadoException.class,()->b.sut.validarCobro(b.cobro("1"),b.broker));
        assertThrows(AccesoNoAutorizadoException.class,()->b.cobrar("1","otro"));
        assertThrows(AccesoNoAutorizadoException.class,()->b.sut.consultarResultadoCobro("cobro",b.broker));
        assertEquals(1,b.libro.size());assertEquals(1,a.movimiento());
    }
    @Test void cobroReautorizaPartesDestinoYEvidenciasOriginalesTipadas() {
        var b=devengada();var d=b.cobro("100");b.sut.registrarCobro(d,b.broker,b.sid,"c",ComisionServiceImplTest.Banco.PROC);
        verify(b.referencias).exigirLectura(eq(ComisionServiceImplTest.Banco.CTX),eq(ComisionServiceImplTest.Banco.PACTO),
                argThat(v->v.parteObligada()!=null&&v.beneficiario()!=null&&v.destino()!=null&&v.evidencias().size()==1),eq(b.broker));
    }
    @Test void concluirConParcialNoCondonaNiImpideOtroCobroONiegaElPacto() {
        var b=devengada();b.cobrar("5000","uno");var antes=b.ficha();
        var r=b.sut.concluirGestion(conclusion(b,"Gestión concluida con lo recibido"),b.broker,b.sid,"fin",ComisionServiceImplTest.Banco.PROC);
        assertTrue(r.gestion().concluida());assertNull(r.gestion().proximaRevision());
        assertEquals(antes.calculo(),b.ficha().calculo());assertEquals(antes.devengo(),b.ficha().devengo());assertEquals(antes.cobro(),b.ficha().cobro());
        assertEquals(1,b.historia.size());b.cobrar("1000","posterior");assertTrue(b.ficha().gestion().concluida());
        assertNull(b.ficha().gestion().proximaRevision());assertEquals(new BigDecimal("6000.00"),ingreso(b.ficha(),"PEN"));assertEquals(2,b.libro.size());
    }
    @Test void reprogramacionExplicitaConservaAntecedenteOriginalYCasInclusoConDatosIguales() {
        var b=devengada();var d=reprogramacion(b,ComisionServiceImplTest.Banco.HOY.plusDays(4),"Acordado con el obligado");
        var a=b.sut.reprogramarGestion(d,b.broker,b.sid,"g1",ComisionServiceImplTest.Banco.PROC);
        assertEquals(a,b.sut.reprogramarGestion(d,b.broker,b.sid,"g1",ComisionServiceImplTest.Banco.PROC));
        var siguiente=b.sut.reprogramarGestion(reprogramacion(b,d.proximaRevision(),d.motivo()),b.broker,b.sid,"g2",ComisionServiceImplTest.Banco.PROC);
        assertTrue(siguiente.version()>a.version());assertEquals("g2",b.raiz().getClaveUltimaGestion());
        var docs=new Documentos(b.json);assertEquals("g1",docs.comoMapa(b.recibosGuardados.get("1|g2").getResultado()).get("claveAntecedente"));
        assertEquals(a,b.sut.consultarResultadoGestion(REPROGRAMAR,"g1",b.broker).orElseThrow());
        assertThrows(ConflictoException.class,()->b.sut.reprogramarGestion(reprogramacion(b,d.proximaRevision().plusDays(1),d.motivo()),b.broker,b.sid,"g1",ComisionServiceImplTest.Banco.PROC));
        assertThrows(ConflictoException.class,()->b.sut.validarReprogramacion(d,b.broker));assertTrue(b.libro.isEmpty());
    }
    @Test void gestionExigeFechaMotivoCasYAutoridadSinImportarCadenciaNiExigibilidad() {
        var b=devengada();long id=b.raiz().getId(),v=b.raiz().getVersion();
        assertThrows(ReglaNegocioException.class,()->b.sut.validarReprogramacion(new DatosReprogramacion(id,v,null,"Motivo"),b.broker));
        assertThrows(ReglaNegocioException.class,()->b.sut.validarConclusion(new DatosConclusion(id,v," "),b.broker));
        assertThrows(ReglaNegocioException.class,()->b.sut.validarConclusion(new DatosConclusion(id,null,"Motivo"),b.broker));
        assertThrows(AccesoNoAutorizadoException.class,()->b.sut.validarConclusion(new DatosConclusion(id,v,"Motivo"),new Actor(1,7,5,Actor.AGENTE)));
        var r=b.sut.reprogramarGestion(reprogramacion(b,ComisionServiceImplTest.Banco.HOY.plusDays(9),"Fecha elegida"),b.broker,b.sid,"g",ComisionServiceImplTest.Banco.PROC);
        assertEquals(ComisionServiceImplTest.Banco.HOY.plusDays(9),r.gestion().proximaRevision());assertEquals(ComisionServiceImplTest.Banco.HOY.minusDays(1),b.ficha().exigibilidad().fechaAcreditada());
        assertTrue(b.libro.isEmpty());
    }
    @Test void gestionReautorizadaNoRecuperaReciboConSesionOCompetenciaRevocadas() {
        var b=devengada();var d=conclusion(b,"Sin otra gestión");b.sut.concluirGestion(d,b.broker,b.sid,"fin",ComisionServiceImplTest.Banco.PROC);
        doThrow(new AccesoNoAutorizadoException()).when(b.acceso).iniciar(eq(b.broker),eq(b.sid),eq(3L));
        assertThrows(AccesoNoAutorizadoException.class,()->b.sut.concluirGestion(d,b.broker,b.sid,"fin",ComisionServiceImplTest.Banco.PROC));
        b.supervisados.clear();assertThrows(AccesoNoAutorizadoException.class,()->b.sut.consultarResultadoGestion(CONCLUIR,"fin",b.broker));
        assertTrue(b.libro.isEmpty());
    }
    @Test void rectificacionMenorConservaCalculosCobrosYRepartoOriginalSinDevoluciones() {
        var b=new ComisionServiceImplTest.Banco();b.porcentaje();b.confirmar();b.abrir();
        var reparto=b.sut.asignarReparto(b.reparto("5000"),b.broker,b.sid,"reparto",ComisionServiceImplTest.Banco.PROC);
        b.cobrar("15000","total");String original=b.historia.getLast().getResultado();int previas=b.historia.size();
        b.rectificarVenta("333333.33",12,22);var despues=b.abrir();
        assertEquals(new BigDecimal("10000.00"),despues.calculo().cuantia().importe());assertEquals(new BigDecimal("15000.00"),ingreso(despues,"PEN"));
        assertEquals(0,despues.cobro().saldoInformativoSobreCalculo().importe().signum());assertEquals(reparto.reparto(),despues.reparto());
        assertEquals(original,b.historia.get(previas-1).getResultado());assertEquals(previas+1,b.historia.size());
        assertEquals(1,b.libro.size());assertEquals("C",b.libro.getFirst().getTipo());assertEquals(1,b.raices.size());
    }
    @Test void ingresosHistoricosDeMonedasDistintasSeConservanSinConversionNiSaldoInventado() {
        var b=devengada();b.historico("C","100","PEN");b.historico("C","20","USD");var f=b.ficha();
        assertEquals(new BigDecimal("100.00"),ingreso(f,"PEN"));assertEquals(new BigDecimal("20.00"),ingreso(f,"USD"));
        assertEquals(2,f.cobro().ingresosRecibidos().size());assertNull(f.cobro().saldoInformativoSobreCalculo().importe());assertNull(f.cobro().estado());
        assertThrows(ReglaNegocioException.class,()->b.cobrar("1","incomparable"));assertEquals(2,b.libro.size());
        b.historico("C","1","XYZ");assertThrows(IllegalStateException.class,b::ficha);
    }
    @Test void jsonYMapRechazanTipoAntiguoAntesODespuesInclusoSiElMapperIgnoraDesconocidos() throws Exception {
        var b=devengada();var datos=mapa(b,b.cobro("100"));
        for(Object tipo:new Object[]{"P","R",null})for(boolean primero:new boolean[]{true,false}) {
            Map<String,Object> entrada=new LinkedHashMap<>();if(primero)entrada.put("tipo",tipo);entrada.putAll(datos);if(!primero)entrada.put("tipo",tipo);
            assertThrows(IllegalArgumentException.class,()->b.json.convertValue(entrada,DatosCobro.class));
            String json=b.json.writeValueAsString(entrada);assertThrows(JsonProcessingException.class,()->b.json.readValue(json,DatosCobro.class));
            assertThrows(ReglaNegocioException.class,()->b.sut.previsualizarActuacion("registrar_cobro",entrada,b.broker));
        }
        assertNotNull(b.json.convertValue(datos,DatosCobro.class));assertTrue(b.libro.isEmpty());
        for(Object gesto:List.of(conclusion(b,"Motivo"),reprogramacion(b,ComisionServiceImplTest.Banco.HOY,"Motivo"))) {
            var mapa=mapa(b,gesto);mapa.put("tipo","P");
            if(gesto instanceof DatosConclusion)assertThrows(IllegalArgumentException.class,()->b.json.convertValue(mapa,DatosConclusion.class));
            else assertThrows(IllegalArgumentException.class,()->b.json.convertValue(mapa,DatosReprogramacion.class));
        }
    }
    @Test void resumenHumanoIncluyeRecibidoGestionPactoYCalculoExactosSinEfectos() {
        var b=devengada();b.cobrar("5000","recibido");var d=conclusion(b,"Con lo recibido");
        long version=b.raiz().getVersion();var resumen=b.sut.previsualizarActuacion(CONCLUIR,mapa(b,d),b.broker);
        var ficha=(FichaComision)resumen.get("comision");assertEquals(b.ficha(),ficha);
        assertEquals(new BigDecimal("5000.00"),ingreso(ficha,"PEN"));assertFalse(ficha.gestion().concluida());
        assertNotNull(resumen.get("pacto"));assertEquals(d,resumen.get("datos"));assertEquals(version,b.raiz().getVersion());
        assertEquals(1,b.libro.size());assertFalse(b.recibosGuardados.containsKey("1|fin"));
        var contador=new AtomicInteger();doAnswer(i->{if(contador.incrementAndGet()==2)ReflectionTestUtils.setField(b.raiz(),"version",version+1);return null;}).when(b.em).refresh(any(ComisionLiquidacion.class));
        assertThrows(ConflictoException.class,()->b.sut.previsualizarActuacion(CONCLUIR,mapa(b,d),b.broker));
    }
    @Test void resumenNoConvierteFalloTecnicoEnPendienteNiEnEntradaInvalida() {
        var b=devengada();var datos=mapa(b,conclusion(b,"Motivo"));
        doThrow(new IllegalStateException("Fallo técnico TEST")).when(b.hitos).consultarContexto(any(),any());
        assertThrows(IllegalStateException.class,()->b.sut.previsualizarActuacion(CONCLUIR,datos,b.broker));
        assertFalse(b.raiz().isGestionConcluida());assertTrue(b.libro.isEmpty());
    }
}

