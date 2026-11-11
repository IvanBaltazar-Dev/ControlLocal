package com.controllocal.service.presupuesto;

import com.controllocal.persistence.presupuesto.*;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository.*;
import com.controllocal.persistence.presupuesto.TrabajoManualB0Repository.*;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.DerechosDePlanB0;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** T1 con repositorios/autoridad simulados. Números TEST, sin regla comercial real o aceptación SQL. */
class PresupuestoManualB0Test {
    final PresupuestoIaRepository repo=mock(PresupuestoIaRepository.class);
    final TrabajoManualB0Repository manual=mock(TrabajoManualB0Repository.class);
    final ContextoPresupuestoB0 contexto=mock(ContextoPresupuestoB0.class);
    final AutorizadorRecursoIa recurso=mock(AutorizadorRecursoIa.class);
    final AtribucionesPlataforma plataforma=mock(AtribucionesPlataforma.class);
    final DerechosDePlanB0 derechos=mock(DerechosDePlanB0.class);
    final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules());
    final Instant ahora=Instant.parse("2026-11-11T14:00:00Z");
    final Actor actor=new Actor(17,7,9,Actor.AGENTE);
    final ContextoSesion sid=mock(ContextoSesion.class);
    final UUID id=UUID.randomUUID(),regla=UUID.randomUUID(),fuente=UUID.randomUUID();
    final SesionPlataforma fin=new SesionPlataforma("FIN_TEST",1,UUID.randomUUID());
    final SolicitudManual solicitud=new SolicitudManual(id,regla,"TEST_V1",new Recurso("INVESTIGACION_MERCADO",id.toString(),"INVESTIGACION_PUNTUAL","a".repeat(64)),"b".repeat(64),new BigDecimal("8"),new BigDecimal("4"));
    PresupuestoIaService sut;
    Operacion operacion;
    Manual metadatos;
    @BeforeEach void montar(){
        sut=new PresupuestoIaService(repo,contexto,List.of(recurso),json,derechos,false,Clock.fixed(ahora,ZoneOffset.UTC));
        ReflectionTestUtils.setField(sut,"manual",manual);ReflectionTestUtils.setField(sut,"atribuciones",plataforma);ReflectionTestUtils.setField(sut,"manualHabilitado",true);
        when(recurso.atiende(PresupuestoIaService.PERFIL_MANUAL,"INVESTIGACION_MERCADO")).thenReturn(true);
        when(repo.bloquearOrganizacion(17)).thenReturn(new Organizacion("EMPRESARIAL",true));
        when(repo.cicloVigente(17,ahora)).thenReturn(Optional.of(new Ciclo(UUID.randomUUID(),"ONE",ahora.minusSeconds(60),ahora.plusSeconds(600),false,BigDecimal.TEN,11,0,0)));
        when(repo.fuentesElegibles(eq(17L),eq(7L),eq(false),any(),eq(ahora),eq(true))).thenReturn(List.of(new Fuente(fuente,"MENSUAL_AGENTE",7L,new BigDecimal("100"),BigDecimal.ZERO,BigDecimal.ZERO,ahora.minusSeconds(60),ahora.plusSeconds(600))));
        when(manual.regla(regla,"TEST_V1",true)).thenReturn(Optional.of(regla()));
        when(repo.operacion(17L,id)).thenAnswer(i->Optional.ofNullable(operacion));
        when(manual.manual(17,id,false)).thenAnswer(i->Optional.ofNullable(metadatos));
        when(manual.manual(17,id,true)).thenAnswer(i->Optional.ofNullable(metadatos));
        doAnswer(i->{operacion=i.getArgument(0);metadatos=new Manual(regla,"TEST_V1",solicitud.rondasPrevistas(),null);return null;}).when(manual).insertar(any(),eq(regla),eq("TEST_V1"),any());
        when(repo.imputaciones(17L,id)).thenAnswer(i->List.of(new Imputacion(fuente,operacion.maximoCreditos(),null,1)));
        when(plataforma.exigirMercado(eq(fin),eq(Atribucion.FINANCIERO_PLATFORM),any(),any())).thenReturn(new Actual(91,fin.concesion(),null,null,AtribucionesPlataforma.FINALIDAD_MERCADO,null,null,null,RecursoMercado.MERCADO_MANUAL_I0));
        when(repo.iniciar(eq(17L),eq(id),any())).thenAnswer(i->{cambiar("EN_PROCESO",null);return true;});
        doAnswer(i->{cambiar(i.getArgument(2),i.getArgument(5));metadatos=new Manual(regla,"TEST_V1",solicitud.rondasPrevistas(),i.getArgument(3));return null;}).when(manual).cerrar(eq(17L),eq(id),any(),any(),any(),any(),any());
    }
    Regla regla(){return new Regla(regla,"TEST_V1","RONDA_COMPLETA",new BigDecimal("0.5"),new BigDecimal("100"),BigDecimal.ZERO,BigDecimal.ZERO,ahora.minusSeconds(60),ahora.plusSeconds(600),UUID.randomUUID(),91,"Sólo TEST","c".repeat(64),true,"SOLICITANTE");}
    void admitir(){assertEquals("RESERVADA",sut.admitirManual(solicitud,actor,sid).estado());}
    void cambiar(String estado,String hash){var o=operacion;operacion=new Operacion(o.organizacion(),o.id(),o.persona(),o.rol(),o.perfil(),o.versionPerfil(),o.tipo(),o.recurso(),o.finalidad(),o.origen(),o.peticion(),estado,o.maximoCreditos(),o.maximoTecnico(),o.fondo(),o.admitida(),hash,o.tecnica(),o.caso(),o.cuenta(),o.concesion(),o.autorizacion());}
    ConciliacionManual conciliacion(String unidades){return new ConciliacionManual(17,id,new BigDecimal(unidades),"EVIDENCIA_SINTETICA_TEST");}
    @Test void apagadoNoReservaNiExigeComprarUnProveedor(){ReflectionTestUtils.setField(sut,"manualHabilitado",false);assertThrows(CapacidadDeshabilitadaException.class,this::admitir);assertNull(operacion);verify(repo,never()).retenerFuente(anyLong(),any(),any());}
    @Test void sinReglaVigenteNoAdmiteTrabajo(){when(manual.regla(regla,"TEST_V1",true)).thenReturn(Optional.empty());assertThrows(CapacidadDeshabilitadaException.class,this::admitir);assertNull(operacion);}
    @Test void maximoNoAceptadoNoSeCorrigeSilenciosamente(){var s=new SolicitudManual(id,regla,"TEST_V1",solicitud.recurso(),solicitud.huellaContenido(),solicitud.rondasPrevistas(),BigDecimal.TEN);assertThrows(ConflictoException.class,()->sut.admitirManual(s,actor,sid));assertNull(operacion);}
    @Test void sinSaldoNoCreaReserva(){when(repo.fuentesElegibles(eq(17L),eq(7L),eq(false),any(),eq(ahora),eq(true))).thenReturn(List.of());assertThrows(ReglaNegocioException.class,this::admitir);assertNull(operacion);}
    @Test void reservaReutilizaLaFuenteYElReplayNoDuplica(){admitir();admitir();verify(repo).retenerFuente(17L,fuente,new BigDecimal("4.000000000000"));verify(repo).imputar(17L,id,fuente,new BigDecimal("4.000000000000"),1);assertNull(operacion.fondo());assertNull(operacion.maximoTecnico());verify(repo,never()).retenerFondo(any(),any());}
    @Test void mismoComandoNoAceptaOtrosDatos(){admitir();var s=new SolicitudManual(id,regla,"TEST_V1",solicitud.recurso(),"d".repeat(64),solicitud.rondasPrevistas(),solicitud.maximoAceptado());assertThrows(ConflictoException.class,()->sut.admitirManual(s,actor,sid));}
    @Test void rondasAcreditadasLiquidanUnaVezYLiberanElSobranteConocido(){admitir();sut.iniciarManual(solicitud,actor);var d=conciliacion("3");assertEquals("LIQUIDADA",sut.conciliarManual(d,fin).estado());assertEquals("LIQUIDADA",sut.conciliarManual(d,fin).estado());verify(repo).liquidarFuente(eq(17L),eq(id),any(),eq(new BigDecimal("1.500000000000")));verify(manual).cerrar(eq(17L),eq(id),eq("LIQUIDADA"),eq(new BigDecimal("3")),eq(new BigDecimal("1.500000000000")),any(),eq(ahora));verify(repo,never()).liquidarFondo(any(),any(),any());}
    @Test void excesoNoConsumeNiSueltaLaReserva(){admitir();assertThrows(ReglaNegocioException.class,()->sut.conciliarManual(conciliacion("9"),fin));assertEquals("RESERVADA",operacion.estado());verify(repo,never()).liquidarFuente(any(),any(),any(),any());}
    @Test void faltaDeEvidenciaNoSeConvierteEnTrabajoCero(){admitir();doThrow(new AccesoNoAutorizadoException()).when(recurso).exigirEvidenciaManual(anyLong(),any(),any(),any(),any());assertThrows(AccesoNoAutorizadoException.class,()->sut.conciliarManual(conciliacion("0"),fin));assertEquals("RESERVADA",operacion.estado());}
    @Test void cancelacionAcreditadaAntesDeInicioLiberaSinCostos(){admitir();assertTrue(sut.cancelarManualNoIniciada(17,id,"CANCELACION_TEST"));assertFalse(sut.cancelarManualNoIniciada(17,id,"CANCELACION_TEST"));verify(repo).liquidarFuente(eq(17L),eq(id),any(),eq(com.controllocal.domain.presupuesto.PrecisionCapacidad.CERO));assertEquals("CANCELADA",operacion.estado());}
    @Test void inicioInciertoNoSeCancelaComoSiNoHubieraTrabajo(){admitir();assertTrue(sut.iniciarManual(solicitud,actor));assertFalse(sut.iniciarManual(solicitud,actor));assertThrows(ConflictoException.class,()->sut.cancelarManualNoIniciada(17,id,"CANCELACION_TEST"));verify(repo,never()).liquidarFuente(any(),any(),any(),any());}
    @Test void bajaDeOrganizacionNoImpideConciliarTrabajoAnteriorAcreditado(){admitir();when(repo.bloquearOrganizacion(17)).thenReturn(new Organizacion("EMPRESARIAL",false));assertEquals("LIQUIDADA",sut.conciliarManual(conciliacion("3"),fin).estado());}
    @Test void unaFraccionDeRondaNoSeAdmiteAunqueTengaSaldo(){
        var s=new SolicitudManual(id,regla,"TEST_V1",solicitud.recurso(),solicitud.huellaContenido(),new BigDecimal("1.5"),new BigDecimal("0.75"));
        assertThrows(ReglaNegocioException.class,()->sut.admitirManual(s,actor,sid));assertNull(operacion);verify(repo,never()).retenerFuente(anyLong(),any(),any());
    }
    @Test void rondaInterrumpidaNoSeProrrateaNiLiberaUnaReservaIncierta(){
        admitir();sut.iniciarManual(solicitud,actor);
        assertThrows(ReglaNegocioException.class,()->sut.conciliarManual(conciliacion("0.5"),fin));
        assertEquals("EN_PROCESO",operacion.estado());verify(repo,never()).liquidarFuente(anyLong(),any(),any(),any());
    }
    @Test void cotaDeRondasFraccionariaNoSeConfiguraComoSiFueraTiempo(){
        var r=new ReglaManual(regla,"TEST_V1",new BigDecimal("0.5"),new BigDecimal("2.5"),ahora,ahora.plusSeconds(60),"Sólo TEST",false,FinanciacionManual.SOLICITANTE);
        assertThrows(ReglaNegocioException.class,()->sut.configurarReglaManual(r,UUID.randomUUID(),fin));verify(manual,never()).insertarRegla(any());
    }
    @Test void declaracionSinAcreditacionDelPropietarioNoLiquidaRondaCompleta(){
        admitir();sut.iniciarManual(solicitud,actor);
        doThrow(new ConflictoException("La ronda no conserva revisión y entrega acreditadas.")).when(recurso).exigirEvidenciaManual(anyLong(),any(),any(),any(),any());
        assertThrows(ConflictoException.class,()->sut.conciliarManual(conciliacion("1"),fin));
        assertEquals("EN_PROCESO",operacion.estado());verify(repo,never()).liquidarFuente(anyLong(),any(),any(),any());
    }
    @Test void cotaOcupadaSeDistingueDeFalloSqlSinReservarCreditos(){
        var r=regla();when(manual.regla(regla,"TEST_V1",true)).thenReturn(Optional.of(new Regla(r.id(),r.version(),r.unidad(),r.creditosPorRonda(),r.cota(),new BigDecimal("95"),BigDecimal.ZERO,r.desde(),r.hasta(),r.decision(),r.responsable(),r.evidencia(),r.huella(),true,r.financiacion())));
        assertTrue(assertThrows(ReglaNegocioException.class,this::admitir).getMessage().contains("capacidad operativa"));
        assertNull(operacion);verify(repo,never()).retenerFuente(anyLong(),any(),any());
    }
    @Test void mantenimientoBroxNoInventaTarifaNiSeCargaAUnProfesional(){
        var capturada=new java.util.concurrent.atomic.AtomicReference<Regla>();
        doAnswer(i->{capturada.set(i.getArgument(0));return null;}).when(manual).insertarRegla(any());
        when(manual.regla(regla,"TEST_V1",true)).thenAnswer(i->Optional.ofNullable(capturada.get()));
        var datos=new ReglaManual(regla,"TEST_V1",null,new BigDecimal("100"),ahora.minusSeconds(60),ahora.plusSeconds(600),"TEST CORP SIN GASTO",true,FinanciacionManual.MANTENIMIENTO_BROX);
        sut.configurarReglaManual(datos,UUID.randomUUID(),fin);assertNull(capturada.get().creditosPorRonda());assertEquals("MANTENIMIENTO_BROX",capturada.get().financiacion());
        assertThrows(CapacidadDeshabilitadaException.class,this::admitir);assertNull(operacion);verify(repo,never()).retenerFuente(anyLong(),any(),any());
    }
    @Test void mantenimientoNoUsaCeroComoTarifaAusente(){
        var datos=new ReglaManual(regla,"TEST_V1",BigDecimal.ZERO,BigDecimal.TEN,ahora,ahora.plusSeconds(60),"TEST",false,FinanciacionManual.MANTENIMIENTO_BROX);
        assertThrows(ReglaNegocioException.class,()->sut.configurarReglaManual(datos,UUID.randomUUID(),fin));verify(manual,never()).insertarRegla(any());
    }
}
