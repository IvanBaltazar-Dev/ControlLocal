package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.comercial.*;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.organizacion.Organizacion;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.ComisionService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.encargo.EncargoService.Tramo;
import com.controllocal.service.encargo.PactosDeEncargo;
import com.controllocal.service.encargo.PactosDeEncargo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.formalizacion.HitosConfirmados;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.formalizacion.ReferenciasDeMovimiento;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unidad: repositorios/sesión son dobles; AccesoEncargo/Alcances y aritmética son reales. */
class ComisionServiceImplTest {
    @Test void radarNoConvierteBaseFaltanteEnCeroNiConfundeDevengoConCobro(){
        var b=new Banco();b.abrir();assertEquals(false,b.sut.gestionParaRadar(Banco.PACTO,Banco.CTX,b.broker).saldoExigiblePendiente());
        b.confirmar();b.abrir();var g=b.sut.gestionParaRadar(Banco.PACTO,Banco.CTX,b.broker);
        assertFalse(g.basePendiente());assertEquals(true,g.saldoExigiblePendiente());assertEquals(Banco.HOY.minusDays(1),g.revisarEn());assertTrue(b.libro.isEmpty());
        var sinBase=new Banco();sinBase.porcentaje();sinBase.fuente=null;sinBase.fuentes.clear();sinBase.abrir();
        assertTrue(sinBase.sut.gestionParaRadar(Banco.PACTO,Banco.CTX,sinBase.broker).basePendiente());
    }
    @Test void radarEconomicoExigeDecisionAntesDeLeerLaEvaluacionYRevalidaSuVersion(){
        var b=new Banco();b.confirmar();b.abrir();clearInvocations(b.comisiones);
        assertThrows(AccesoNoAutorizadoException.class,()->b.sut.gestionParaRadar(Banco.PACTO,Banco.CTX,new Actor(1,7,5,Actor.AGENTE)));
        verifyNoInteractions(b.comisiones);when(b.comisiones.versionActualParaRadar(1,1)).thenReturn(999L);
        assertThrows(ConflictoException.class,()->b.sut.gestionParaRadar(Banco.PACTO,Banco.CTX,b.broker));
    }
    @Test void cacheMonetariaDistintaDeLaEvaluacionEsFalloTecnicoNoOtroImporte() {
        var b=new Banco();b.abrir();ReflectionTestUtils.setField(b.raiz(),"montoBruto",new BigDecimal("999"));
        assertThrows(IllegalStateException.class,b::ficha);assertEquals(1,b.historia.size());assertTrue(b.libro.isEmpty());
    }
    @Test void contratoRealQueApareceTrasElHitoSeVinculaALaMismaComisionPendiente() {
        for(var op:OperacionInmobiliaria.values()) {
            var b=new Banco(op);var antes=b.abrir();b.confirmar();
            if(op==OperacionInmobiliaria.ALQUILER) {
                b.contratoAlquiler=50L;var contrato=mock(ContratoAlquiler.class);when(contrato.getId()).thenReturn(50L);
                when(b.em.getReference(ContratoAlquiler.class,50L)).thenReturn(contrato);
            } else b.contratoVenta=60L;
            var despues=b.abrir();assertEquals(antes.id(),despues.id());assertEquals(1,b.raices.size());
            if(op==OperacionInmobiliaria.ALQUILER)assertEquals(50L,b.raiz().getContrato().getId().longValue());
            else assertEquals(60L,b.raiz().getContratoVenta().longValue());
            assertEquals(2,b.historia.size());assertTrue(b.libro.isEmpty());
        }
    }
    @Test void selectorDeNetosCoincideConLaGuardaCanonicaDelRecurso() {
        var b=new Banco();var c=b.acceso.leer(3,b.broker);var agente=new Actor(1,7,5,Actor.AGENTE);
        for(var actor:List.of(b.broker,agente,new Actor(1,7,19,Actor.TENANT_ADMIN)))
            assertEquals(b.acceso.puedeDecidir(c,actor),b.acceso.paraDecision(actor).rolesAgente().contains(c.getAgente().getId()));
        b.individual=true;
        assertTrue(b.acceso.puedeDecidir(c,agente));assertEquals(List.of(5L),b.acceso.paraDecision(agente).rolesAgente());
    }
    @Test void pactoDeOtraRevisionNoSeEtiquetaConLaReferenciaSolicitada() {
        var b=new Banco();
        doReturn(new PactoFirmado(new ReferenciaPacto(3,31),2,
                new AcreditacionPacto(10,11,12,"a".repeat(64),"b".repeat(64),"c".repeat(64)),b.regla))
                .when(b.pactos).consultarPactoFirmado(any(),any());
        assertThrows(IllegalStateException.class,()->b.abrir());assertTrue(b.raices.isEmpty());
    }
    @Test void fuenteDeOtraRevisionNoSirveComoBaseDelHitoIdentificado() {
        var b=new Banco();b.porcentaje();b.confirmar();
        doReturn(b.fuente("600000",new ReferenciaFuenteEconomica("REVISION_C5",3,12)))
                .when(b.hitos).consultarFuente(any(),any(),any());
        assertThrows(IllegalStateException.class,()->b.abrir());assertTrue(b.raices.isEmpty());
    }
    @Test void pactoFijoSinContextoDevuelveImporteSinFabricarExpedienteNiHito() {
        var b=new Banco();var f=b.sut.consultar(Banco.PACTO,null,b.broker);
        assertNull(f.id());assertNull(f.contexto());assertEquals(new BigDecimal("15000.00"),f.calculo().cuantia().importe());
        assertEquals("PENDIENTE",f.devengo().estado());assertNull(f.devengo().cuantiaAcreditada().importe());
        assertEquals("SIN_COBROS",f.cobro().estado());assertTrue(f.tramos().isEmpty());
        verify(b.comisiones,never()).saveAndFlush(any());verifyNoInteractions(b.movimientos);
    }
    @Test void evaluarSinHitoNoAcreditaDevengoNiDeposito() {
        var b=new Banco();var f=b.abrir();
        assertEquals("PENDIENTE",f.devengo().estado());assertEquals("SIN_COBROS",f.cobro().estado());
        assertEquals(1,b.raices.size());assertEquals(1,b.historia.size());assertTrue(b.libro.isEmpty());assertTrue(b.posiciones.isEmpty());
        assertNull(f.reparto().agente());assertNull(f.reparto().empresa());assertNull(f.reparto().saldoPagoAgente());
    }
    @Test void escrituraConfirmadaDevengaSinFabricarCobro() {
        var b=new Banco();b.confirmar();var f=b.abrir();
        assertEquals("DEVENGADO",f.devengo().estado());assertEquals(new BigDecimal("15000.00"),f.devengo().cuantiaAcreditada().importe());
        assertEquals("SIN_COBROS",f.cobro().estado());assertTrue(b.libro.isEmpty());assertEquals(1,b.historia.size());
        verify(b.evaluaciones).enlazarHito(1,1,3,20,21);
    }
    @Test void lecturaNoModificaLaEvaluacionNiFabricaOtraVersion() {
        var b=new Banco();b.confirmar();var a=b.abrir();long v=a.version();
        var f=b.sut.consultar(Banco.PACTO,Banco.CTX,b.broker);
        assertEquals(v,f.version());assertEquals(1,b.historia.size());assertTrue(b.libro.isEmpty());
    }
    @Test void basePorcentualDesconocidaQuedaPendienteNoCeroNiAnuncio() {
        var b=new Banco();b.porcentaje();b.fuente=null;b.fuentes.clear();var f=b.abrir();
        assertNull(f.calculo().cuantia().importe());assertEquals("PEN",f.calculo().cuantia().moneda());
        assertNull(f.cobro().saldoInformativoSobreCalculo().importe());assertEquals(List.of("PRECIO_VENTA_FINAL"),f.calculo().faltantes());
        assertTrue(b.libro.isEmpty());
    }
    @Test void actorCorporativoNoDecideDineroAunqueLeaSuEncargo() {
        var b=new Banco();var agente=new Actor(1,7,5,Actor.AGENTE);
        assertNotNull(b.acceso.leer(3,agente));
        assertThrows(AccesoNoAutorizadoException.class,()->b.sut.evaluar(Banco.CTX,null,agente,b.sid,"corporativo",Banco.PROC));
        assertTrue(b.raices.isEmpty());assertTrue(b.historia.isEmpty());assertTrue(b.libro.isEmpty());
    }
    @Test void independienteDecideSuPropioContextoSinBrokerFicticio() {
        var b=new Banco();b.individual=true;var independiente=new Actor(1,7,5,Actor.AGENTE);
        var f=b.sut.evaluar(Banco.CTX,null,independiente,b.sid,"independiente",Banco.PROC);
        assertNotNull(f.id());assertEquals("PENDIENTE",f.devengo().estado());
    }
    @Test void gobiernoYBrokerSinEquipoNoObtienenOperacionFinanciera() {
        var b=new Banco();
        assertThrows(AccesoNoAutorizadoException.class,()->b.sut.consultar(Banco.PACTO,null,new Actor(1,7,19,Actor.TENANT_ADMIN)));
        b.supervisados.clear();
        assertThrows(AccesoNoAutorizadoException.class,()->b.sut.consultar(Banco.PACTO,null,b.broker));
        assertTrue(b.raices.isEmpty());
    }
    @Test void contextoDeOtraOrganizacionNoCreaRaiz() {
        var b=new Banco();
        assertThrows(NoEncontradoException.class,()->b.sut.evaluar(Banco.CTX,null,new Actor(999,7,18,Actor.BROKER),b.sid,"foreign",Banco.PROC));
        assertTrue(b.raices.isEmpty());assertTrue(b.recibosGuardados.isEmpty());
    }
    @Test void faltaClaveNoTieneFallbackAleatorio() {
        var b=new Banco();
        assertThrows(ReglaNegocioException.class,()->b.sut.evaluar(Banco.CTX,null,b.broker,b.sid,null,Banco.PROC));
        assertTrue(b.raices.isEmpty());assertTrue(b.recibosGuardados.isEmpty());
    }
    @Test void versionAusenteYObsoletaNoSeConviertenEnCero() {
        var b=new Banco();var f=b.abrir();
        assertThrows(ReglaNegocioException.class,()->b.sut.validarReparto(new DatosReparto(f.id(),null,BigDecimal.ONE,null),b.broker));
        assertThrows(ConflictoException.class,()->b.sut.validarReparto(new DatosReparto(f.id(),f.version()+1,BigDecimal.ONE,null),b.broker));
        assertNull(b.raiz().getMontoAgente());
    }
    @Test void mismaEntradaEvaluadaNoDuplicaRaizTramosNiHistoria() {
        var b=new Banco();var a=b.abrir();var r=b.sut.evaluar(Banco.CTX,null,b.broker,b.sid,"otra-evaluacion",Banco.PROC);
        assertEquals(a.id(),r.id());assertEquals(a.version(),r.version());assertEquals(1,b.raices.size());assertEquals(1,b.historia.size());
    }
    @Test void replayDevuelveElResultadoOriginalNoLaFichaActual() {
        var b=new Banco();var a=b.abrir();b.confirmar();var actual=b.abrir();
        var anterior=b.sut.evaluar(Banco.CTX,null,b.broker,b.sid,"eval-1",Banco.PROC);
        assertEquals(a,anterior);assertEquals("PENDIENTE",anterior.devengo().estado());
        assertEquals("DEVENGADO",actual.devengo().estado());assertEquals(2,b.historia.size());
    }
    @Test void reciboNoImponeCasDeEscrituraPeroReautorizaActualidad() {
        var b=new Banco();b.confirmar();var f=b.abrir();var d=b.reparto("5000");
        var r=b.sut.asignarReparto(d,b.broker,b.sid,"reparto",Banco.PROC);
        b.sut.asignarReparto(b.reparto("6000"),b.broker,b.sid,"reparto2",Banco.PROC);
        assertEquals(r,b.sut.consultarResultadoReparto("reparto",b.broker).orElseThrow());
        b.supervisados.clear();
        assertThrows(AccesoNoAutorizadoException.class,()->b.sut.consultarResultadoReparto("reparto",b.broker));
    }
    @Test void sesionRevocadaSeDeniegaAntesDeRecuperarElRecibo() {
        var b=new Banco();b.abrir();
        doThrow(new AccesoNoAutorizadoException()).when(b.acceso).iniciar(eq(b.broker),eq(b.sid),eq(3L));
        assertThrows(AccesoNoAutorizadoException.class,()->b.sut.evaluar(Banco.CTX,null,b.broker,b.sid,"eval-1",Banco.PROC));
        assertEquals(1,b.historia.size());
    }
    @Test void fuenteDeLaMinutaNoSeEtiquetaConLaRevisionPosteriorDeEscritura() {
        var b=new Banco();b.porcentaje();
        b.regla=b.conDevengo(TipoHitoContractual.MINUTA);
        var fuenteMinuta=b.fuente("500000",new ReferenciaFuenteEconomica("REVISION_C5",3,10));
        b.fuentes.put(fuenteMinuta.referencia(),fuenteMinuta);
        b.hechos.add(new FuenteHitoConfirmado(Banco.CTX,new ReferenciaHito(20,21),TipoHitoContractual.MINUTA,
                Banco.HOY.minusDays(1),Banco.AHORA,Banco.PACTO,fuenteMinuta.referencia(),null,Banco.PROC));
        b.fuente=b.fuente("490000",new ReferenciaFuenteEconomica("REVISION_C5",3,11));b.fuentes.put(b.fuente.referencia(),b.fuente);
        var f=b.abrir();assertEquals(new BigDecimal("15000.00"),f.calculo().cuantia().importe());
        assertEquals(fuenteMinuta.referencia(),f.fuenteFinal());assertEquals(10L,b.historia.getFirst().getRevisionFuente().longValue());
    }
    @Test void paginaUsaLotesYRevalidaElAlcanceSinConsultarUnaFichaPorFila() {
        var b=new Banco();b.abrir();var contrato=mock(ContratoAlquiler.class);when(contrato.getId()).thenReturn(50L);
        b.raiz().setContrato(contrato);
        when(b.comisiones.porContratosEnAlcance(anyLong(),anyCollection(),anyCollection())).thenAnswer(i->
                ((Collection<?>)i.getArgument(2)).contains(5L)?List.of(b.raiz()):List.of());
        when(b.evaluaciones.findByOrganizacionIdAndIdIn(anyLong(),anyCollection())).thenReturn(List.copyOf(b.historia));
        when(b.movimientos.findByOrganizacionIdAndLiquidacionIdIn(anyLong(),anyCollection())).thenReturn(List.of());
        when(b.tramos.findByOrganizacionIdAndComisionIn(anyLong(),anyCollection())).thenReturn(List.of());
        clearInvocations(b.comisiones,b.evaluaciones,b.movimientos,b.tramos,b.hitos);
        var pagina=b.sut.porContratosAlquiler(List.of(50L),b.broker);
        assertEquals(Set.of(50L),pagina.keySet());assertEquals(b.raiz().getId(),pagina.get(50L).id());
        verify(b.comisiones,times(2)).porContratosEnAlcance(eq(1L),eq(List.of(50L)),eq(List.of(5L)));
        verify(b.evaluaciones).findByOrganizacionIdAndIdIn(1,List.of(1L));
        verify(b.movimientos).findByOrganizacionIdAndLiquidacionIdIn(1,List.of(1L));
        verify(b.tramos).findByOrganizacionIdAndComisionIn(1,List.of(1L));verifyNoInteractions(b.hitos);
        doAnswer(i->{
            boolean visible=((Collection<?>)i.getArgument(2)).contains(5L);b.supervisados.clear();
            return visible?List.of(b.raiz()):List.of();
        }).when(b.comisiones).porContratosEnAlcance(anyLong(),anyCollection(),anyCollection());
        assertThrows(ConflictoException.class,()->b.sut.porContratosAlquiler(List.of(50L),b.broker));
    }
    @Test void agenteCorporativoNoRecibeNetoNiSuReconstruccionPeroBrokerEIndependienteSi() {
        var b=new Banco();b.confirmar();b.abrir();
        b.sut.asignarReparto(b.reparto("5000"),b.broker,b.sid,"reparto",Banco.PROC);
        b.sut.reprogramarGestion(new DatosReprogramacion(b.raiz().getId(),b.raiz().getVersion(),Banco.HOY.plusDays(2),"Motivo privado"),
                b.broker,b.sid,"gestion",Banco.PROC);
        var agente=new Actor(1,7,5,Actor.AGENTE);
        var reducido=b.sut.consultar(Banco.PACTO,Banco.CTX,agente);
        assertEquals(new Reparto(null,null,null,null,null,null),reducido.reparto());
        assertNull(reducido.gestion().motivo());assertEquals(Banco.HOY.plusDays(2),reducido.gestion().proximaRevision());
        assertEquals(new BigDecimal("15000.00"),reducido.calculo().cuantia().importe());
        assertEquals(new BigDecimal("5000.00"),b.ficha().reparto().agente());
        assertEquals(new BigDecimal("10000.00"),b.ficha().reparto().empresa());
        b.individual=true;
        var propio=b.sut.consultar(Banco.PACTO,Banco.CTX,agente);
        assertEquals(new BigDecimal("5000.00"),propio.reparto().agente());
        assertEquals(new BigDecimal("10000.00"),propio.reparto().empresa());
        assertEquals("Motivo privado",propio.gestion().motivo());assertNotNull(propio.reparto().evaluacionBase());
    }

    /** Banco compartido únicamente por las tres unidades reales de esta familia. No acredita integración/auth. */
    static final class Banco {
        static final LocalDate HOY=LocalDate.of(2026,10,31);
        static final OffsetDateTime AHORA=OffsetDateTime.parse("2026-10-31T15:00:00Z");
        static final ReferenciaContextoEconomico CTX=new ReferenciaContextoEconomico(3);
        static final ReferenciaPacto PACTO=new ReferenciaPacto(3,30);
        static final Procedencia PROC=Procedencia.deLaPantalla();
        final Actor broker=new Actor(1,8,18,Actor.BROKER);
        final ContextoSesion sid=mock(ContextoSesion.class);
        final ComisionLiquidacionRepository comisiones=mock(ComisionLiquidacionRepository.class);
        final ComisionMovimientoRepository movimientos=mock(ComisionMovimientoRepository.class);
        final ComisionEvaluacionRepository evaluaciones=mock(ComisionEvaluacionRepository.class);
        final ComisionTramoRepository tramos=mock(ComisionTramoRepository.class);
        final PactosDeEncargo pactos=mock(PactosDeEncargo.class);
        final HitosConfirmados hitos=mock(HitosConfirmados.class);
        final ReferenciasDeMovimiento referencias=mock(ReferenciasDeMovimiento.class);
        final ComandoIdempotenteRepository recibos=mock(ComandoIdempotenteRepository.class);
        final EntityManager em=mock(EntityManager.class);
        final AlertaService alertas=mock(AlertaService.class);
        final List<Long> supervisados=new ArrayList<>(List.of(5L));
        final Map<Long,ComisionLiquidacion> raices=new LinkedHashMap<>();
        final List<ComisionMovimiento> libro=new ArrayList<>();
        final List<ComisionEvaluacion> historia=new ArrayList<>();
        final List<ComisionTramo> posiciones=new ArrayList<>();
        final Map<String,ComandoIdempotente> recibosGuardados=new HashMap<>();
        final Map<ReferenciaFuenteEconomica,FuenteEconomicaFinal> fuentes=new HashMap<>();
        final List<FuenteHitoConfirmado> hechos=new ArrayList<>();
        final AccesoEncargo acceso;
        final ComisionServiceImpl sut;
        final ObjectMapper json=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        ReglaComision regla=reglaFija(List.of());
        FuenteEconomicaFinal fuente;
        final OperacionInmobiliaria operacion;
        Long contratoAlquiler,contratoVenta;
        boolean individual=false;
        long n;
        Banco(){this(OperacionInmobiliaria.VENTA);}
        Banco(OperacionInmobiliaria operacion) {
            this.operacion=operacion;
            if(operacion==OperacionInmobiliaria.ALQUILER)regla=conDevengo(TipoHitoContractual.CONTRATO_ALQUILER);
            var captaciones=mock(CaptacionRepository.class);var supervisiones=mock(SupervisionAgenteRepository.class);
            var organizaciones=mock(OrganizacionRepository.class);var o=mock(Organizacion.class);
            when(o.estaActiva()).thenReturn(true);when(o.getModalidad()).thenAnswer(i->individual?"INDIVIDUAL":"EMPRESARIAL");
            when(organizaciones.findById(1L)).thenReturn(Optional.of(o));
            when(supervisiones.agentesSupervisados(1,18)).thenAnswer(i->List.copyOf(supervisados));
            var alcances=new Alcances(supervisiones);
            var ca=new Captacion();ReflectionTestUtils.setField(ca,"id",3L);ca.setOrganizacionId(1L);ca.setMotivoOperacion(operacion.codigo());
            var agente=mock(DetalleAgente.class);when(agente.getId()).thenReturn(5L);ca.setAgente(agente);
            var cab=mock(CaptacionRepository.ContextoActualEncargo.class);
            when(cab.getResponsable()).thenReturn(5L);when(cab.getVersion()).thenReturn(0L);when(cab.getCuenta()).thenReturn(7L);
            when(captaciones.contextoActual(1,3)).thenReturn(Optional.of(cab));
            when(captaciones.buscarFicha(1,3)).thenReturn(Optional.of(ca));
            when(captaciones.bloquearParaEscritura(1,3)).thenReturn(Optional.of(ca));
            acceso=spy(new AccesoEncargo(captaciones,alcances,organizaciones,mock(UsuarioOrganizacionRepository.class),
                    mock(CuentaAccesoRepository.class),mock(ContextosService.class)));
            doReturn(new AccesoEncargo.Inicio(5L)).when(acceso).iniciar(any(),any(),eq(3L));
            when(pactos.consultarPactoFirmado(any(),any())).thenAnswer(i->new PactoFirmado(i.getArgument(0),1,
                    new AcreditacionPacto(10,11,12,"a".repeat(64),"b".repeat(64),"c".repeat(64)),regla));
            fuente=fuente(operacion==OperacionInmobiliaria.VENTA?"500000":"1000",new ReferenciaFuenteEconomica("REVISION_C5",3,11));fuentes.put(fuente.referencia(),fuente);
            when(hitos.consultarContexto(any(),any())).thenAnswer(i->contexto());
            when(hitos.consultarFuente(any(),any(),any())).thenAnswer(i->{
                var f=fuentes.get(i.getArgument(1));if(f==null)throw new NoEncontradoException("Fuente");return f;
            });
            when(comisiones.findByOrganizacionIdAndContextoEconomico(anyLong(),anyLong())).thenAnswer(i->
                    raices.values().stream().filter(c->Objects.equals(c.getOrganizacionId(),i.getArgument(0))
                            &&Objects.equals(c.getContextoEconomico(),i.getArgument(1))).findFirst());
            when(comisiones.findByOrganizacionIdAndId(anyLong(),anyLong())).thenAnswer(i->porId(i.getArgument(0),i.getArgument(1)));
            when(comisiones.versionActualParaRadar(anyLong(),anyLong())).thenAnswer(i->porId(i.getArgument(0),i.getArgument(1)).map(ComisionLiquidacion::getVersion).orElse(null));
            when(comisiones.bloquear(anyLong(),anyLong())).thenAnswer(i->porId(i.getArgument(0),i.getArgument(1)));
            when(comisiones.saveAndFlush(any())).thenAnswer(i->{
                ComisionLiquidacion c=i.getArgument(0);if(c.getId()==null)c.setId((long)raices.size()+1);
                else ReflectionTestUtils.setField(c,"version",c.getVersion()+1);
                raices.put(c.getId(),c);return c;
            });
            when(evaluaciones.saveAndFlush(any())).thenAnswer(i->{ComisionEvaluacion e=i.getArgument(0);
                ReflectionTestUtils.setField(e,"id",(long)historia.size()+1);historia.add(e);return e;});
            when(evaluaciones.findByOrganizacionIdAndComisionAndId(anyLong(),anyLong(),anyLong())).thenAnswer(i->
                    historia.stream().filter(e->e.getOrganizacionId()==(long)i.getArgument(0)&&e.getComision()==(long)i.getArgument(1)
                            &&e.getId()==(long)i.getArgument(2)).findFirst());
            when(evaluaciones.findByOrganizacionIdAndComisionAndHuella(anyLong(),anyLong(),anyString())).thenAnswer(i->
                    historia.stream().filter(e->e.getOrganizacionId()==(long)i.getArgument(0)&&e.getComision()==(long)i.getArgument(1)
                            &&e.getHuella().equals(i.getArgument(2))).findFirst());
            when(tramos.findByOrganizacionIdAndComisionAndRevisionPactoOrderByIndicePactoAsc(anyLong(),anyLong(),anyLong())).thenAnswer(i->
                    posiciones.stream().filter(t->t.getOrganizacionId()==(long)i.getArgument(0)&&t.getComision()==(long)i.getArgument(1)
                            &&t.getRevisionPacto()==(long)i.getArgument(2)).toList());
            when(tramos.findByOrganizacionIdAndComisionAndId(anyLong(),anyLong(),anyLong())).thenAnswer(i->
                    posiciones.stream().filter(t->t.getOrganizacionId()==(long)i.getArgument(0)&&t.getComision()==(long)i.getArgument(1)
                            &&t.getId()==(long)i.getArgument(2)).findFirst());
            when(tramos.save(any())).thenAnswer(i->{ComisionTramo t=i.getArgument(0);
                ReflectionTestUtils.setField(t,"id",(long)posiciones.size()+1);posiciones.add(t);return t;});
            when(movimientos.findByOrganizacionIdAndLiquidacionIdOrderByFechaAscIdAsc(anyLong(),anyLong())).thenAnswer(i->
                    libro.stream().filter(m->m.getOrganizacionId()==(long)i.getArgument(0)&&m.getLiquidacion().getId()==(long)i.getArgument(1)).toList());
            when(movimientos.findByOrganizacionIdAndId(anyLong(),anyLong())).thenAnswer(i->
                    libro.stream().filter(m->m.getOrganizacionId()==(long)i.getArgument(0)&&m.getId()==(long)i.getArgument(1)).findFirst());
            when(movimientos.saveAndFlush(any())).thenAnswer(i->{ComisionMovimiento m=i.getArgument(0);
                ReflectionTestUtils.setField(m,"id",(long)libro.size()+1);libro.add(m);return m;});
            when(recibos.findByOrganizacionIdAndClaveIdempotencia(anyLong(),anyString())).thenAnswer(i->
                    Optional.ofNullable(recibosGuardados.get(i.getArgument(0)+"|"+i.getArgument(1))));
            when(recibos.save(any())).thenAnswer(i->{ComandoIdempotente c=i.getArgument(0);
                recibosGuardados.put(c.getOrganizacionId()+"|"+c.getClaveIdempotencia(),c);return c;});
            var docs=new Documentos(json);
            sut=new ComisionServiceImpl(comisiones,movimientos,evaluaciones,tramos,pactos,hitos,referencias,acceso,alcances,
                    new ComandosIdempotentes(recibos),recibos,docs,json,
                    alertas,em,mock(com.controllocal.persistence.repositorio.EventoDominioRepository.class),Clock.fixed(AHORA.toInstant(),ZoneOffset.UTC));
        }
        Optional<ComisionLiquidacion> porId(long org,long id){return Optional.ofNullable(raices.get(id)).filter(c->c.getOrganizacionId()==org);}
        ContextoEconomico contexto(){return new ContextoEconomico(CTX,1,3,operacion,null,null,contratoAlquiler,contratoVenta,PACTO,fuente,hechos);}
        FichaComision abrir(){return sut.evaluar(CTX,null,broker,sid,"eval-"+(++n),PROC);}
        ComisionLiquidacion raiz(){return raices.values().iterator().next();}
        FichaComision ficha(){return sut.consultar(PACTO,CTX,broker);}
        void confirmar(){hechos.add(new FuenteHitoConfirmado(CTX,new ReferenciaHito(20,21),regla.ejecucion().devengo().hito(),
                HOY.minusDays(1),AHORA,PACTO,fuente==null?null:fuente.referencia(),null,PROC));}
        void porcentaje(){regla=new ReglaComision("P","V",new BigDecimal("0.03"),"PEN","N",null,
                "Escritura","Obligado TEST","Beneficiario TEST","Escritura","TEST",List.of(),ejecucion());}
        ReglaComision conDevengo(TipoHitoContractual tipo){return new ReglaComision(regla.tipoComision(),regla.baseCalculo(),regla.valorComision(),
                regla.monedaComision(),regla.tratamientoIgv(),regla.motivoSinComision(),tipo.name(),regla.parteObligada(),
                regla.beneficiario(),tipo.name(),regla.procedencia(),regla.tramos(),
                new EjecucionPacto(new CondicionDevengo(tipo),new CondicionExigibilidad(tipo,null,null,null,null)));}
        static EjecucionPacto ejecucion(){return new EjecucionPacto(new CondicionDevengo(TipoHitoContractual.ESCRITURA),
                new CondicionExigibilidad(TipoHitoContractual.ESCRITURA,null,null,null,null));}
        static ReglaComision reglaFija(List<Tramo> tramos){return new ReglaComision("F","N",new BigDecimal("15000"),"PEN","N",null,
                "Escritura","Obligado TEST","Beneficiario TEST","Escritura","TEST",tramos,ejecucion());}
        FuenteEconomicaFinal fuente(String importe,ReferenciaFuenteEconomica ref){return new FuenteEconomicaFinal(ref,null,null,null,
                operacion==OperacionInmobiliaria.VENTA?new OfertaService.Dinero(new BigDecimal(importe),"PEN","PRECIO_VENTA",null):null,
                operacion==OperacionInmobiliaria.ALQUILER?new OfertaService.Dinero(new BigDecimal(importe),"PEN","RENTA_MENSUAL","MENSUAL"):null,List.of(),PROC);}
        DatosReparto reparto(String monto){return new DatosReparto(raiz().getId(),raiz().getVersion(),new BigDecimal(monto),null);}
        DatosCobro cobro(String monto){return cobro(monto,HOY,null);}
        DatosCobro cobro(String monto,LocalDate fecha,Long tramo){
            var obligado=new SujetoDeclarado(new ReferenciaSujeto("CAMPO_PACTO",null,PACTO,null,"parteObligada"),"Obligado TEST");
            var beneficiario=new SujetoDeclarado(new ReferenciaSujeto("CAMPO_PACTO",null,PACTO,null,"beneficiario"),"Beneficiario TEST");
            return new DatosCobro(raiz().getId(),raiz().getVersion(),tramo,new BigDecimal(monto),"PEN",fecha,"TRANSFERENCIA",
                    obligado,beneficiario,new DestinoPago("Destino TEST",beneficiario),"TEST",
                    List.of(new ReferenciaDocumento(new UUID(0,1),1,"d".repeat(64))),null);
        }
        ResultadoCobro cobrar(String monto,String clave){return sut.registrarCobro(cobro(monto),broker,sid,clave,PROC);}
        /** Antecedente técnico explícito para lectura; NO ejecuta comandos P/R/A actuales. */
        ComisionMovimiento historico(String tipo,String monto,String moneda) {
            var m=new ComisionMovimiento();m.setOrganizacionId(1L);m.setLiquidacion(raiz());m.setTipo(tipo);
            m.setMonto(new BigDecimal(monto).setScale(2));m.setMoneda(moneda);m.setFecha(HOY.minusDays(1));m.setFechaCreacion(AHORA);
            ReflectionTestUtils.setField(m,"id",(long)libro.size()+1);libro.add(m);return m;
        }
        void rectificarVenta(String importe,long revisionFuente,long revisionHito) {
            var previo=hechos.getFirst();
            fuente=fuente(importe,new ReferenciaFuenteEconomica("REVISION_C5",3,revisionFuente));fuentes.put(fuente.referencia(),fuente);
            hechos.clear();hechos.add(new FuenteHitoConfirmado(CTX,new ReferenciaHito(previo.referencia().id(),revisionHito),
                    previo.tipo(),previo.fechaEfectiva(),AHORA,PACTO,fuente.referencia(),previo.referencia(),PROC));
        }
    }
}
