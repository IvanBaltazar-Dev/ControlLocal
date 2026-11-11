package com.controllocal.service.colaboracion;

import com.controllocal.domain.auditoria.*;
import com.controllocal.domain.colaboracion.*;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.controllocal.domain.colaboracion.LadoColaboracion.*;
import static com.controllocal.service.colaboracion.ColaboracionService.*;
import static com.controllocal.service.colaboracion.ProyeccionNetwork.Accion.*;

class ColaboracionServiceImplTest {
    private final SolicitudColaboracionRepository solicitudes=mock(SolicitudColaboracionRepository.class);
    private final DecisionColaboracionRepository decisiones=mock(DecisionColaboracionRepository.class);
    private final ConcesionColaboracionRepository concesiones=mock(ConcesionColaboracionRepository.class);
    private final ProyeccionNetwork network=mock(ProyeccionNetwork.class);
    private final ContextosService contextos=mock(ContextosService.class);
    private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    private final UsuarioOrganizacionRepository miembros=mock(UsuarioOrganizacionRepository.class);
    private final ComandoIdempotenteRepository registros=mock(ComandoIdempotenteRepository.class);
    private final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    private final Transiciones transiciones=mock(Transiciones.class);
    private final EntityManager em=mock(EntityManager.class);
    private final Documentos documentos=new Documentos(new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    private final Map<Long,SolicitudColaboracion> ss=new LinkedHashMap<>();
    private final Map<Long,DecisionColaboracion> ds=new LinkedHashMap<>();
    private final Map<Long,ConcesionColaboracion> cs=new LinkedHashMap<>();
    private final Map<String,ComandoIdempotente> recibos=new HashMap<>();
    private final List<EventoDominio> outbox=new ArrayList<>();
    private final Actor agente=new Actor(1,10,100,Actor.AGENTE),contraparte=new Actor(2,20,200,Actor.AGENTE),
            brokerD=new Actor(1,11,101,Actor.BROKER),brokerE=new Actor(2,21,201,Actor.BROKER),admin=new Actor(1,12,102,Actor.TENANT_ADMIN);
    private static final Instant AHORA=Instant.parse("2026-10-28T12:00:00Z");
    private ProyeccionNetwork.Recursos recursos;
    private boolean individual;
    private ColaboracionServiceImpl servicio;

    private long primarios(){return outbox.stream().filter(e->!"COLABORACION_LADO_DISPONIBLE".equals(e.getTipo())).count();}
    @BeforeEach void montar() {
        recursos=new ProyeccionNetwork.Recursos(1,4,100,3,2,9,200,6,"oferta-anonima",5);
        servicio=new ColaboracionServiceImpl(solicitudes,decisiones,concesiones,network,contextos,cuentas,miembros,
                registros,new ComandosIdempotentes(registros),documentos,eventos,transiciones,em,Clock.fixed(AHORA,ZoneOffset.UTC),mock(com.controllocal.service.soporte.Alcances.class));
        var cuenta=mock(CuentaAcceso.class);
        when(miembros.cuentasActivasDePersona(anyLong(),anyLong())).thenReturn(List.of(77L));
        when(cuentas.bloquearCambioAcceso(anyLong())).thenReturn(Optional.of(cuenta));
        when(contextos.exigirContexto(anyString(),anyLong(),any())).thenAnswer(i->sesion(i.getArgument(2)));
        when(network.resolverOferta(anyString(),anyLong(),any())).thenAnswer(i->recursos);
        when(network.revalidar(any(),any(),any())).thenReturn(true);
        when(network.revalidarSnapshots(any(),any(),anyMap(),anyMap(),any())).thenReturn(true);
        when(network.exigirPartePropia(any(),any(),any(),any())).thenAnswer(i->{
            Actor a=i.getArgument(3);LadoColaboracion lado=i.getArgument(1);
            if(lado==null)lado=a.idOrganizacion()==recursos.organizacionDemanda()?DEMANDA:ENCARGO;
            return new ProyeccionNetwork.ParteActual(lado==DEMANDA?recursos.organizacionDemanda():recursos.organizacionEncargo(),
                    lado==DEMANDA?recursos.responsableDemanda():recursos.responsableEncargo(),lado==DEMANDA?3:6,individual,lado);
        });
        when(network.snapshotPartePropia(any(),anyString(),any(),any(),any())).thenAnswer(i->{
            LadoColaboracion lado=i.getArgument(2);AlcanceColaboracion alcance=i.getArgument(3);
            Map<String,Object> datos=new LinkedHashMap<>();
            for(String campo:alcance.campos().getOrDefault(lado,Set.of()))datos.put(campo,campo.equals("tipo")?"DEPARTAMENTO":"Dato propio permitido");
            return new ProyeccionNetwork.SnapshotParte(datos,lado==DEMANDA?3:6);
        });
        doAnswer(i->{((SolicitudColaboracion)i.getArgument(0)).transicionarA(i.getArgument(1));return null;}).when(transiciones).iniciar(any(),anyString());
        doAnswer(i->{((SolicitudColaboracion)i.getArgument(0)).transicionarA(i.getArgument(2));return null;}).when(transiciones).aplicar(any(SolicitudColaboracion.class),anyLong(),anyString(),any(),anyString(),any(LocalDate.class));
        when(solicitudes.saveAndFlush(any())).thenAnswer(i->{SolicitudColaboracion s=i.getArgument(0);if(s.getId()==null)id(s,ss.size()+1);else ReflectionTestUtils.setField(s,"version",s.getVersion()+1);ss.put(s.getId(),s);return s;});
        when(solicitudes.leer(any(),anyLong())).thenAnswer(i->buscar(i.getArgument(0),i.getArgument(1)));
        when(solicitudes.bloquear(any(),anyLong())).thenAnswer(i->buscar(i.getArgument(0),i.getArgument(1)));
        when(solicitudes.leerId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(ss.get(i.getArgument(0))));
        when(solicitudes.bloquearId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(ss.get(i.getArgument(0))));
        when(decisiones.saveAndFlush(any())).thenAnswer(i->{DecisionColaboracion d=i.getArgument(0);id(d,ds.size()+1);ds.put(d.getId(),d);return d;});
        when(decisiones.actual(anyLong(),anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(ds.get(i.getArgument(0))));
        when(decisiones.historial(anyLong(),anyLong())).thenAnswer(i->new ArrayList<>(ds.values()));
        when(concesiones.saveAndFlush(any())).thenAnswer(i->{ConcesionColaboracion c=i.getArgument(0);if(c.getId()==null)id(c,cs.size()+1);else ReflectionTestUtils.setField(c,"version",c.getVersion()+1);cs.put(c.getId(),c);return c;});
        when(concesiones.deSolicitud(anyLong(),anyLong())).thenAnswer(i->cs.values().stream().filter(c->c.getSolicitud().equals(i.getArgument(0))).findFirst());
        when(concesiones.leer(any(),anyLong())).thenAnswer(i->buscarConcesion(i.getArgument(0)));
        when(concesiones.bloquear(any(),anyLong())).thenAnswer(i->buscarConcesion(i.getArgument(0)));
        when(concesiones.solicitudDe(any(),anyLong())).thenAnswer(i->buscarConcesion(i.getArgument(0)).map(ConcesionColaboracion::getSolicitud));
        when(registros.findByOrganizacionIdAndClaveIdempotencia(anyLong(),anyString())).thenAnswer(i->Optional.ofNullable(recibos.get(i.getArgument(0)+"/"+i.getArgument(1))));
        when(registros.save(any())).thenAnswer(i->{ComandoIdempotente c=i.getArgument(0);recibos.put(c.getOrganizacionId()+"/"+c.getClaveIdempotencia(),c);return c;});
        when(eventos.saveAndFlush(any())).thenAnswer(i->{EventoDominio e=i.getArgument(0);id(e,outbox.size()+1);outbox.add(e);return e;});
    }
    @Test void ceroOUnaDecisionNoConcedenYDosSi() {
        var r=solicitar();assertNull(r.concesion());assertEquals("PENDIENTE",r.estado());
        assertFalse(servicio.consultarAcuerdo(r.solicitud(),agente).efectiva());
        var d=decidir(DEMANDA,Decision.ACEPTAR,alcance(),brokerD,"d");assertNull(d.concesion());assertTrue(cs.isEmpty());
        var e=decidir(ENCARGO,Decision.ACEPTAR,alcance(),brokerE,"e");
        assertNotNull(e.concesion());assertEquals("APROBADA",e.estado());assertEquals(1,cs.size());
        var consulta=servicio.consultarAcuerdo(r.solicitud(),agente);assertTrue(consulta.efectiva());assertNotNull(consulta.datosConcedidos());
        verify(network).snapshotPartePropia(any(),anyString(),eq(DEMANDA),any(),eq(brokerD));
        verify(network).snapshotPartePropia(any(),anyString(),eq(ENCARGO),any(),eq(brokerE));
    }
    @Test void gobiernoYAgenteEmpresarialNoDeciden() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.solicitar(datos(),admin,prueba(admin),"g",origen()));
        solicitar();assertThrows(AccesoNoAutorizadoException.class,()->decidir(DEMANDA,Decision.ACEPTAR,alcance(),agente,"d"));
        assertTrue(ds.isEmpty());assertTrue(cs.isEmpty());
    }
    @Test void independienteDecideSuLadoSinBrokerFicticio() {
        individual=true;solicitar();decidir(DEMANDA,Decision.ACEPTAR,alcance(),agente,"d");
        var finalizado=decidir(ENCARGO,Decision.ACEPTAR,alcance(),contraparte,"e");assertNotNull(finalizado.concesion());
    }
    @Test void mismoBrokerPuedeDecidirDosLadosPeroNuncaConUnaSolaDecision() {
        recursos=new ProyeccionNetwork.Recursos(1,4,100,3,1,9,200,6,"oferta-anonima",5);
        solicitar();decidir(DEMANDA,Decision.ACEPTAR,alcance(),brokerD,"d");assertTrue(cs.isEmpty());
        decidir(ENCARGO,Decision.ACEPTAR,alcance(),brokerD,"e");assertEquals(1,cs.size());assertEquals(2,ds.size());
    }
    @Test void brokerAjenoALaParteNoDecideAunqueConozcaReferencia() {
        solicitar();assertThrows(AccesoNoAutorizadoException.class,()->decidir(DEMANDA,Decision.ACEPTAR,alcance(),brokerE,"otro"));assertTrue(ds.isEmpty());
    }
    @Test void limitarConservaSoloLaInterseccionYNoAdmiteCampoAdicional() {
        solicitar();var reducido=new AlcanceColaboracion(alcance().destinatarios(),Set.of("CONSULTAR"),Map.of(DEMANDA,Set.of("tipo"),ENCARGO,Set.of("tipo")),"RED_COLABORATIVA",AHORA.minusSeconds(5),AHORA.plusSeconds(100));
        decidir(DEMANDA,Decision.LIMITAR,reducido,brokerD,"d");var result=decidir(ENCARGO,Decision.ACEPTAR,alcance(),brokerE,"e");
        assertEquals(reducido,result.alcance());assertFalse(result.alcance().campos().get(ENCARGO).contains("precioPedido"));
    }
    @Test void ampliacionYVersionOmitidaNoPersistenDecision() {
        solicitar();var ampliado=new AlcanceColaboracion(alcance().destinatarios(),Set.of("CONSULTAR","EDITAR"),alcance().campos(),"RED_COLABORATIVA",alcance().desde(),alcance().hasta());
        assertThrows(ReglaNegocioException.class,()->decidir(DEMANDA,Decision.LIMITAR,ampliado,brokerD,"d"));
        var s=solicitud();var d=new DatosDecision(s.getReferencia().toString(),DEMANDA,Decision.ACEPTAR,alcance(),"Revisión",null,0L,3L);
        assertThrows(ReglaNegocioException.class,()->servicio.decidirPorParte(d,brokerD,prueba(brokerD),"null",origen()));assertTrue(ds.isEmpty());
    }
    @Test void rechazoConservaHistoriaSinConcesionYNuevaDecisionNoReabre() {
        solicitar();var r=decidir(ENCARGO,Decision.RECHAZAR,null,brokerE,"rechazo");assertEquals("RECHAZADA",r.estado());assertTrue(cs.isEmpty());
        assertThrows(ConflictoException.class,()->decidir(DEMANDA,Decision.ACEPTAR,alcance(),brokerD,"posterior"));assertEquals(1,ds.size());
    }
    @Test void motivosDeContrapartePermanecenOcultosEnConsulta() {
        var r=solicitar();decidir(ENCARGO,Decision.LIMITAR,alcance(),brokerE,"e");
        var acuerdo=servicio.consultarAcuerdo(r.solicitud(),agente);assertNull(acuerdo.decisiones().getFirst().motivo());
        assertNull(acuerdo.datosConcedidos());assertNull(acuerdo.concesion());
    }
    @Test void brokerNoRecibeForeignPorSupervisarNiPorHaberAprobado() {
        conceder();assertNull(servicio.consultarAcuerdo(solicitud().getReferencia().toString(),brokerD).datosConcedidos());
    }
    @Test void revocarCortaSalidaYReplayNoReejecutaNiRehabilitaGrant() {
        conceder();var c=concesion();var d=new DatosRevocacion(c.getReferencia().toString(),DEMANDA,alcance(),"Fin colaboración",c.getVersion(),0L,3L);
        var primero=servicio.revocarPropia(d,brokerD,prueba(brokerD),"revocar",origen());
        assertEquals(primero,servicio.revocarPropia(d,brokerD,prueba(brokerD),"revocar",origen()));
        var acuerdo=servicio.consultarAcuerdo(solicitud().getReferencia().toString(),agente);assertFalse(acuerdo.efectiva());assertNull(acuerdo.datosConcedidos());
        assertEquals(4,primarios());assertEquals(4,outbox.stream().filter(e->"COLABORACION_LADO_DISPONIBLE".equals(e.getTipo())).count());
    }
    @Test void resultadoOriginalSeRecuperaSinRecrearSolicitudYPierdeLecturaAlPerderAutoridad() {
        var primero=solicitar();assertEquals(primero,servicio.solicitar(datos(),agente,prueba(agente),"sol",origen()));assertEquals(1,ss.size());assertEquals(1,primarios());
        assertEquals(primero,servicio.consultarResultado("solicitar_colaboracion","sol",agente).orElseThrow());
        doThrow(new AccesoNoAutorizadoException()).when(network).exigirPartePropia(any(),isNull(),eq(CONSULTAR),eq(agente));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultarResultado("solicitar_colaboracion","sol",agente));
    }
    @Test void otraEntradaConMismaClaveEsConflictoSinOtroRegistro() {
        solicitar();var d=new DatosSolicitud(4L,3L,"otra-oferta-opaca",5L,alcance());
        assertThrows(ConflictoException.class,()->servicio.solicitar(d,agente,prueba(agente),"sol",origen()));assertEquals(1,ss.size());
    }
    @Test void falloDelGuardDeSnapshotsNoSeConvierteEnDatoVacioNiForeign() {
        conceder();doThrow(new IllegalStateException("Fallo técnico")).when(network).revalidarSnapshots(any(),any(),anyMap(),anyMap(),eq(agente));
        assertThrows(IllegalStateException.class,()->servicio.consultarAcuerdo(solicitud().getReferencia().toString(),agente));
    }
    @Test void perdidaDeElegibilidadEnConsultaEntregaHistoriaSinForeignNiExcepcionQueReviertaSafety() {
        conceder();when(network.revalidar(any(),any(),any())).thenReturn(false);
        var a=servicio.consultarAcuerdo(solicitud().getReferencia().toString(),agente);
        assertFalse(a.efectiva());assertNull(a.datosConcedidos());assertNotNull(a.concesion());assertEquals("APROBADA",a.estado());
        verify(network,never()).revalidarSnapshots(any(),any(),anyMap(),anyMap(),any());
    }
    @Test void cambioDeSubsetConcedidoRetiraSoloElEgresoConservandoAntecedenteYFalloTecnicoSigueError() {
        conceder();when(network.revalidarSnapshots(any(),any(),anyMap(),anyMap(),any())).thenReturn(false);
        var a=servicio.consultarAcuerdo(solicitud().getReferencia().toString(),agente);
        assertFalse(a.efectiva());assertNull(a.datosConcedidos());assertNotNull(a.concesion());assertEquals(1,cs.size());
    }
    @Test void brokerNoDeclaraEfectivaUnaConcesionCuyoSubsetYaCambio() {
        conceder();when(network.revalidarSnapshots(any(),any(),anyMap(),anyMap(),any())).thenReturn(false);
        var a=servicio.consultarAcuerdo(solicitud().getReferencia().toString(),brokerD);
        assertFalse(a.efectiva());assertNull(a.datosConcedidos());assertNotNull(a.concesion());
        assertEquals("APROBADA",a.estado());assertEquals(1,cs.size());
        verify(network).revalidarSnapshots(any(),any(),anyMap(),anyMap(),eq(brokerD));
    }
    @Test void versionRecargadaTrasBloqueoNoCompletaLaVersionQueObservoElDecisor() {
        solicitar();var s=solicitud();int eventosAntes=outbox.size(),recibosAntes=recibos.size();
        doAnswer(i->{ReflectionTestUtils.setField(s,"version",1L);return null;}).when(em).refresh(s);
        assertThrows(ConflictoException.class,()->decidir(DEMANDA,Decision.ACEPTAR,alcance(),brokerD,"decision-cas-observado"));
        assertTrue(ds.isEmpty());assertTrue(cs.isEmpty());assertEquals(eventosAntes,outbox.size());assertEquals(recibosAntes,recibos.size());
        verify(em).refresh(s);
    }
    @Test void concesionPersistidaNoExtiendeLaVigenciaAprobadaPorLasDosDecisiones() {
        conceder();var a=alcance();var extendido=new AlcanceColaboracion(a.destinatarios(),a.acciones(),a.campos(),a.finalidad(),a.desde(),a.hasta().plusSeconds(1));
        ReflectionTestUtils.setField(concesion(),"alcance",documentos.objetoCanonico(extendido));clearInvocations(network);
        assertThrows(IllegalStateException.class,()->servicio.consultarAcuerdo(solicitud().getReferencia().toString(),agente));
        verify(network,never()).revalidarSnapshots(any(),any(),anyMap(),anyMap(),any());
    }
    @Test void concesionPersistidaNoAgregaCamposAusentesEnLasDecisiones() {
        conceder();var a=alcance();var campos=new EnumMap<LadoColaboracion,Set<String>>(LadoColaboracion.class);campos.putAll(a.campos());
        var demanda=new HashSet<>(campos.get(DEMANDA));demanda.add("campo-no-aprobado-test");campos.put(DEMANDA,demanda);
        ReflectionTestUtils.setField(concesion(),"alcance",documentos.objetoCanonico(new AlcanceColaboracion(a.destinatarios(),a.acciones(),campos,a.finalidad(),a.desde(),a.hasta())));
        clearInvocations(network);assertThrows(IllegalStateException.class,()->servicio.consultarAcuerdo(solicitud().getReferencia().toString(),agente));
        verify(network,never()).revalidarSnapshots(any(),any(),anyMap(),anyMap(),any());
    }
    @Test void revocacionDuranteLaEntregaImpideDatosAunqueLaEntidadEstabaCaliente() {
        conceder();var c=concesion();int[] comprobaciones={0};
        doAnswer(i->{if(++comprobaciones[0]==2)c.revocar(ENCARGO,2,201,"Retirada simultánea",AHORA);return true;})
                .when(network).revalidarSnapshots(any(),any(),anyMap(),anyMap(),eq(agente));
        var a=servicio.consultarAcuerdo(solicitud().getReferencia().toString(),agente);assertFalse(a.efectiva());assertNull(a.datosConcedidos());
    }
    @Test void snapshotDeOtroRecursoOVersionNoSeAdjuntaALaDecision() {
        solicitar();doReturn(new ProyeccionNetwork.SnapshotParte(Map.of("tipo","DEPARTAMENTO"),99)).when(network).snapshotPartePropia(any(),anyString(),eq(DEMANDA),any(),eq(brokerD));
        assertThrows(ConflictoException.class,()->decidir(DEMANDA,Decision.ACEPTAR,alcance(),brokerD,"d"));assertTrue(ds.isEmpty());
    }
    @Test void metadatosDeCapacidadNoPreparanNiLeenSnapshotExtranjero() {
        conceder();clearInvocations(network);
        var meta=servicio.consultarMetadatosPorConcesion(concesion().getReferencia().toString(),agente);
        assertEquals("APROBADA",meta.estado());assertEquals(DEMANDA,meta.parteActual().lado());
        verify(network,never()).snapshotPartePropia(any(),anyString(),any(),any(),any());
        verify(network,never()).revalidarSnapshots(any(),any(),anyMap(),anyMap(),any());
    }
    @Test void previewDeDecisionEsPropioExactoYSinEfecto() {
        solicitar();var d=new DatosDecision(solicitud().getReferencia().toString(),DEMANDA,Decision.ACEPTAR,alcance(),"Revisado",solicitud().getVersion(),0L,3L);
        var previa=servicio.previsualizarDecision(d,brokerD);
        assertEquals(d,previa.datos());assertEquals(Map.of("tipo","DEPARTAMENTO"),previa.datosPropios().campos());
        verify(network).snapshotPartePropia(any(),anyString(),eq(DEMANDA),any(),eq(brokerD));
        verify(network,never()).snapshotPartePropia(any(),anyString(),eq(ENCARGO),any(),any());assertTrue(ds.isEmpty());assertEquals(1,primarios());
    }
    @Test void rechazoNoNecesitaPreviewDeCamposParaRetirarSolicitud() {
        solicitar();var d=new DatosDecision(solicitud().getReferencia().toString(),DEMANDA,Decision.RECHAZAR,null,"No procede",solicitud().getVersion(),0L,3L);
        assertNull(servicio.previsualizarDecision(d,brokerD).datosPropios());
        verify(network,never()).snapshotPartePropia(any(),anyString(),any(),any(),any());assertTrue(ds.isEmpty());
    }
    private Resultado solicitar(){return servicio.solicitar(datos(),agente,prueba(agente),"sol",origen());}
    private void conceder(){solicitar();decidir(DEMANDA,Decision.ACEPTAR,alcance(),brokerD,"d");decidir(ENCARGO,Decision.ACEPTAR,alcance(),brokerE,"e");}
    private Resultado decidir(LadoColaboracion lado,Decision decision,AlcanceColaboracion a,Actor actor,String clave){return servicio.decidirPorParte(new DatosDecision(solicitud().getReferencia().toString(),lado,decision,a,"Motivo propio",solicitud().getVersion(),0L,lado==DEMANDA?3L:6L),actor,prueba(actor),clave,origen());}
    private DatosSolicitud datos(){return new DatosSolicitud(4L,3L,"oferta-anonima",5L,alcance());}
    private AlcanceColaboracion alcance(){return new AlcanceColaboracion(Set.of(DEMANDA,ENCARGO),Set.of("CONSULTAR"),Map.of(DEMANDA,Set.of("tipo"),ENCARGO,Set.of("tipo","precioPedido")),"RED_COLABORATIVA",AHORA.minusSeconds(10),AHORA.plusSeconds(3600));}
    private SolicitudColaboracion solicitud(){return ss.values().iterator().next();}private ConcesionColaboracion concesion(){return cs.values().iterator().next();}
    private Optional<SolicitudColaboracion> buscar(UUID ref,long org){return ss.values().stream().filter(s->s.getReferencia().equals(ref)).filter(s->s.getOrganizacionId()==org || s.getOrganizacionEncargo()==org).findFirst();}
    private Optional<ConcesionColaboracion> buscarConcesion(UUID ref){return cs.values().stream().filter(c->c.getReferencia().equals(ref)).findFirst();}
    private static void id(Object objeto,long id){ReflectionTestUtils.setField(objeto,"id",id);}
    private static Procedencia origen(){return Procedencia.deCabecera("API");}
    private ContextoSesion prueba(Actor a){return ContextoSesion.de(sesion(a));}
    private ContextosService.SesionAccesoDTO sesion(Actor a){return new ContextosService.SesionAccesoDTO("sesion-"+a.idPersona(),1,new ContextosService.IdentidadAutenticada(77,"fixture","fixture@example.test","Fixture"),new ContextosService.Contexto("ctx",a.idOrganizacion(),a.idPersona(),a.idRolOperativo(),a.rolEfectivo(),individual?"INDIVIDUAL":"EMPRESARIAL","Prueba"),AHORA.minusSeconds(30),AHORA.plusSeconds(10000),EstadoDeAcceso.SIN_RESTRICCIONES);}
}
