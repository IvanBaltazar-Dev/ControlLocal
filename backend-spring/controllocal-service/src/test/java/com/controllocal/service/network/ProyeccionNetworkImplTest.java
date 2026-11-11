package com.controllocal.service.network;

import com.controllocal.domain.colaboracion.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.colaboracion.*;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.controllocal.domain.colaboracion.LadoColaboracion.*;

/** Contratos con fuentes legítimas sintéticas explícitas; la integración crea concesiones Core reales. */
class ProyeccionNetworkImplTest {
    private final RequerimientoService demandas=mock(RequerimientoService.class);
    private final RequerimientoClienteRepository dm=mock(RequerimientoClienteRepository.class);
    private final CaptacionRepository em=mock(CaptacionRepository.class);
    private final AccesoEncargo acceso=mock(AccesoEncargo.class);
    private final FuenteDeNetwork fuente=mock(FuenteDeNetwork.class);
    private final NetworkService network=mock(NetworkService.class);
    private final FuenteOfertaConcedida concedida=mock(FuenteOfertaConcedida.class);
    private final Alcances alcances=mock(Alcances.class);
    private final IdentidadColaboracionRepository identidades=mock(IdentidadColaboracionRepository.class);
    private final SolicitudColaboracionRepository solicitudes=mock(SolicitudColaboracionRepository.class);
    private final CanalColaboracionRepository canales=mock(CanalColaboracionRepository.class);
    private final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    private final Actor agenteD=new Actor(1,10,100,Actor.AGENTE),brokerD=new Actor(1,11,101,Actor.BROKER),agenteE=new Actor(2,20,200,Actor.AGENTE),brokerE=new Actor(2,21,201,Actor.BROKER);
    private final Instant ahora=Instant.parse("2026-10-29T12:00:00Z");
    private final String oferta="00000000-0000-0000-0000-000000000010",version="00000000-0000-0000-0000-000000000011";
    private final ProyeccionNetwork.Recursos r=new ProyeccionNetwork.Recursos(1,4,100,3,2,9,200,6,oferta,5);
    private final RequerimientoClienteRepository.ContextoDemanda contextoD=mock(RequerimientoClienteRepository.ContextoDemanda.class);
    private final CaptacionRepository.ContextoActualEncargo contextoE=mock(CaptacionRepository.ContextoActualEncargo.class);
    private final IdentidadColaboracionRepository.Identidad identidadD=mock(IdentidadColaboracionRepository.Identidad.class),identidadE=mock(IdentidadColaboracionRepository.Identidad.class);
    private ProyeccionNetworkImpl provider;
    private SolicitudColaboracion s;
    @BeforeEach void montar() {
        provider=new ProyeccionNetworkImpl(demandas,dm,em,acceso,fuente,network,concedida,alcances,identidades,solicitudes,canales,json,Clock.fixed(ahora,ZoneOffset.UTC),mock(com.controllocal.service.encargo.EncargoService.class),mock(com.controllocal.service.encargo.PactosDeEncargo.class),mock(AccesoFuenteDeInvitacion.class));
        when(contextoD.getResponsable()).thenReturn(100L);when(contextoD.getVersion()).thenReturn(3L);when(contextoD.getEstado()).thenReturn("A");
        when(contextoE.getResponsable()).thenReturn(200L);when(contextoE.getVersion()).thenReturn(6L);
        when(dm.contexto(1,4)).thenReturn(Optional.of(contextoD));when(em.contextoActual(2,9)).thenReturn(Optional.of(contextoE));
        when(acceso.contexto(eq(9L),any())).thenReturn(contextoE);
        when(alcances.alcanza(any(),any())).thenAnswer(i->{Actor a=i.getArgument(0);Long rol=i.getArgument(1);return rol!=null&&(a.esAgente()?a.idRolOperativo()==rol:a.esBroker()&&(a.idOrganizacion()==1?rol==100:rol==200));});
        when(identidades.modalidad(anyLong())).thenReturn(Optional.of("EMPRESARIAL"));
        when(demandas.autoridadParaColaboracion(anyLong(),anyLong(),any(),any())).thenAnswer(i->{Actor a=i.getArgument(3);if(a.idOrganizacion()!=1||!alcances.alcanza(a,100L))throw new AccesoNoAutorizadoException();return new RequerimientoService.AutoridadLadoDemanda(4,1,100,3,"A",null,null,Set.of("operacion","tiposAdmitidos","zona","criterios"));});
        when(network.resolverReferencia(eq(oferta),any())).thenReturn(Optional.of(new FuenteDeNetwork.ReferenciaAutorizada(2,9,oferta,version,5,200,201L,PoliticaProyeccionNetworkV1.VERSION,"RED_COLABORATIVA",Set.of("ENCARGO:operacion"),"a".repeat(64),"b".repeat(64))));
        when(fuente.consultarAutoridadPropia(eq(9L),any())).thenAnswer(i->{Actor a=i.getArgument(1);if(a.idOrganizacion()!=2||!alcances.alcanza(a,200L))throw new AccesoNoAutorizadoException();return new FuenteDeNetwork.AutoridadActual(2,9,200,201L,false,true,Set.of());});
        var cap=mock(NetworkService.Capacidad.class);when(cap.autoridadDecidir()).thenReturn(true);when(network.capacidadSobre(eq(9L),any())).thenReturn(cap);
        when(fuente.consultarPropia(eq(9L),anySet(),any(),any())).thenReturn(new FuenteDeNetwork.FuentePropia(2,9,200,201L,false,6,1,PoliticaProyeccionNetworkV1.VERSION,"RED_COLABORATIVA",Map.of("ENCARGO:operacion","VENTA"),"a".repeat(64),"b".repeat(64),true,Set.of()));
        when(demandas.proyectarCamposColaboracion(eq(4L),eq(3L),anySet(),anySet(),any())).thenReturn(Map.of("operacion","VENTA"));
        when(identidadD.getNombreProfesional()).thenReturn("Profesional D");when(identidadD.getModalidad()).thenReturn("EMPRESARIAL");when(identidadD.getInmobiliaria()).thenReturn("Agencia D");
        when(identidadE.getNombreProfesional()).thenReturn("Profesional E");when(identidadE.getModalidad()).thenReturn("EMPRESARIAL");when(identidadE.getInmobiliaria()).thenReturn("Agencia E");
        when(identidades.deResponsable(1,100,LocalDate.of(2026,10,29))).thenReturn(Optional.of(identidadD));when(identidades.deResponsable(2,200,LocalDate.of(2026,10,29))).thenReturn(Optional.of(identidadE));
        s=SolicitudColaboracion.nueva(1,2,4,9,100,200,100,3,6,5,oferta,json.objetoCanonico(alcance()),"a".repeat(64),ahora);ReflectionTestUtils.setField(s,"id",7L);s.transicionarA("P");
        when(solicitudes.leer(eq(s.getReferencia()),anyLong())).thenReturn(Optional.of(s));
        when(demandas.verificarCamposColaboracion(any(),any())).thenReturn(new RequerimientoService.VerificacionCamposDemanda(4,4,100,"A",null,null,hash(Map.of("operacion","VENTA"))));
        when(concedida.verificarCamposColaboracion(any(),any())).thenReturn(new FuenteOfertaConcedida.Verificacion(2,9,200,7,hash(Map.of("ENCARGO:operacion","VENTA")),true,Set.of()));
    }
    @Test void ofertaSeResuelvePorRefOpacaYVersionPropiaSinIDsExternosNiFicha() {
        assertEquals(r,provider.resolverOferta(oferta,4,agenteD));verify(network).resolverReferencia(oferta,agenteD);verify(acceso,never()).leer(anyLong(),any());
    }
    @Test void leerNoDeclaraDecidirYLaParteActualEsExplicita() {
        assertEquals(DEMANDA,provider.exigirPartePropia(r,null,ProyeccionNetwork.Accion.CONSULTAR,agenteD).lado());
        assertEquals(ENCARGO,provider.exigirPartePropia(r,null,ProyeccionNetwork.Accion.CONSULTAR,agenteE).lado());
        assertThrows(AccesoNoAutorizadoException.class,()->provider.exigirPartePropia(r,ENCARGO,ProyeccionNetwork.Accion.CONSULTAR,agenteD));
    }
    @Test void mismaOrganizacionSeleccionaElLadoConAutoridadActualSinPrimerOrgNiExcepcionDeProxy() {
        Actor actor=new Actor(1,30,300,Actor.AGENTE);
        var mismos=new ProyeccionNetwork.Recursos(1,4,100,3,1,9,300,6,oferta,5);
        when(contextoE.getResponsable()).thenReturn(300L);
        doReturn(new FuenteDeNetwork.AutoridadActual(1,9,300,101L,false,true,Set.of())).when(fuente).consultarAutoridadPropia(9,actor);
        var parte=provider.exigirPartePropia(mismos,null,ProyeccionNetwork.Accion.CONSULTAR,actor);
        assertEquals(ENCARGO,parte.lado());assertEquals(300,parte.responsable());
        verify(demandas,never()).autoridadParaColaboracion(anyLong(),anyLong(),any(),any());
    }
    @Test void scopeNoAdmitePresupuestoContactosNotasEdicionNiOtraFinalidad() {
        var a=alcance();var malo=new AlcanceColaboracion(a.destinatarios(),a.acciones(),Map.of(DEMANDA,Set.of("presupuesto"),ENCARGO,Set.of("ENCARGO:operacion")),a.finalidad(),a.desde(),a.hasta());
        assertThrows(ReglaNegocioException.class,()->provider.validarAlcance(r,malo,brokerD));
        var editar=new AlcanceColaboracion(a.destinatarios(),Set.of("EDITAR"),a.campos(),a.finalidad(),a.desde(),a.hasta());assertThrows(ReglaNegocioException.class,()->provider.validarAlcance(r,editar,brokerD));
    }
    @Test void snapshotPropioUsaDecisorRealYSolicitudExactaYOmitirCanalNoBloqueaOtrosCampos() {
        var d=provider.snapshotPartePropia(r,s.getReferencia().toString(),DEMANDA,alcance(),brokerD);
        assertEquals("Profesional D",d.campos().get("nombreProfesional"));assertEquals("VENTA",d.campos().get("operacion"));assertFalse(d.campos().containsKey("canalComercial"));
        verify(demandas).proyectarCamposColaboracion(4,3,Set.of("operacion"),Set.of(),brokerD);verify(fuente,never()).consultarPropia(anyLong(),anySet(),any(),any());verifyNoInteractions(canales);
        assertThrows(AccesoNoAutorizadoException.class,()->provider.snapshotPartePropia(r,s.getReferencia().toString(),ENCARGO,alcance(),brokerD));
    }
    @Test void snapshotDeEncargoNoEtiquetaUnaFuenteNuevaConLaVersionObservadaAnterior() {
        doReturn(new FuenteDeNetwork.FuentePropia(2,9,200,201L,false,7,1,
                PoliticaProyeccionNetworkV1.VERSION,"RED_COLABORATIVA",Map.of("ENCARGO:operacion","VENTA"),
                "a".repeat(64),"b".repeat(64),true,Set.of()))
                .when(fuente).consultarPropia(eq(9L),anySet(),any(),eq(brokerE));
        assertThrows(ConflictoException.class,
                ()->provider.snapshotPartePropia(r,s.getReferencia().toString(),ENCARGO,alcance(),brokerE));
        assertEquals(6,contextoE.getVersion());
    }
    @Test void consultaBaseInelegibleDevuelveFalseSinOcultarUnFalloTecnico() {
        when(network.resolverReferencia(oferta,agenteD)).thenReturn(Optional.empty());assertFalse(provider.revalidar(r,alcance(),agenteD));
        when(network.resolverReferencia(oferta,agenteD)).thenThrow(new IllegalStateException("SQL"));assertThrows(IllegalStateException.class,()->provider.revalidar(r,alcance(),agenteD));
    }
    @Test void nuevaVersionOfertaNoReutilizaConcesionAnterior() {
        when(network.resolverReferencia(oferta,agenteD)).thenReturn(Optional.of(new FuenteDeNetwork.ReferenciaAutorizada(2,9,oferta,version,6,200,201L,PoliticaProyeccionNetworkV1.VERSION,"RED_COLABORATIVA",Set.of("ENCARGO:operacion"),"a".repeat(64),"b".repeat(64))));
        assertFalse(provider.revalidar(r,alcance(),agenteD));
    }
    @Test void foreignSoloVerificaHashesDeSnapshotsSeleccionadosSinActorFalsoNiLecturaDeValores() {
        var snapshots=snapshots();var proofs=proofs(snapshots);clearInvocations(demandas,fuente);
        assertTrue(provider.revalidarSnapshots(r,alcance(),snapshots,proofs,agenteE));
        verify(demandas).verificarCamposColaboracion(proofs.get(DEMANDA),agenteE);verify(concedida).verificarCamposColaboracion(proofs.get(ENCARGO),agenteE);
        verify(demandas,never()).proyectarCamposColaboracion(anyLong(),anyLong(),anySet(),anySet(),any());verify(fuente,never()).consultarPropia(anyLong(),anySet(),any(),any());
    }
    @Test void nombreCambiadoNoSeMezclaConSnapshotAprobado() {
        var snapshots=snapshots();var proofs=proofs(snapshots);when(identidadD.getNombreProfesional()).thenReturn("Nombre nuevo");
        assertFalse(provider.revalidarSnapshots(r,alcance(),snapshots,proofs,agenteE));
    }
    @Test void versionPrivadaActualDistintaNoInvalidaSubsetIdentico() {
        var snapshots=snapshots();var proofs=proofs(snapshots);
        assertTrue(provider.revalidarSnapshots(r,alcance(),snapshots,proofs,agenteD));
    }
    @Test void fuenteAusenteOTecnicaNoSeRepresentaComoConcesionVacia() {
        var snapshots=snapshots();var proofs=proofs(snapshots);when(concedida.verificarCamposColaboracion(any(),any())).thenReturn(null);
        assertThrows(IllegalStateException.class,()->provider.revalidarSnapshots(r,alcance(),snapshots,proofs,agenteD));
    }
    @Test void proofDeOtraVersionOHashNoAcreditaElSnapshot() {
        var snapshots=snapshots();var proofs=new EnumMap<LadoColaboracion,ReferenciaParteConcedida>(proofs(snapshots));
        proofs.put(DEMANDA,ReferenciasConcedidasDePrueba.demanda(1,4,100,3,Set.of("operacion"),Set.of()));
        assertThrows(IllegalStateException.class,()->provider.revalidarSnapshots(r,alcance(),snapshots,proofs,agenteD));
    }
    private AlcanceColaboracion alcance(){return new AlcanceColaboracion(Set.of(DEMANDA,ENCARGO),Set.of("CONSULTAR"),Map.of(DEMANDA,Set.of("operacion","nombreProfesional","canalComercial"),ENCARGO,Set.of("ENCARGO:operacion","nombreProfesional","inmobiliaria","canalComercial")),"RED_COLABORATIVA",ahora.minusSeconds(1),ahora.plusSeconds(3600));}
    private Map<LadoColaboracion,ProyeccionNetwork.SnapshotParte> snapshots(){return Map.of(DEMANDA,provider.snapshotPartePropia(r,s.getReferencia().toString(),DEMANDA,alcance(),brokerD),ENCARGO,provider.snapshotPartePropia(r,s.getReferencia().toString(),ENCARGO,alcance(),brokerE));}
    private Map<LadoColaboracion,ReferenciaParteConcedida> proofs(Map<LadoColaboracion,ProyeccionNetwork.SnapshotParte> ss) {
        var a=alcance();Map<LadoColaboracion,ReferenciaParteConcedida> m=new EnumMap<>(LadoColaboracion.class);
        for(var lado:r.lados()){boolean d=lado==DEMANDA;m.put(lado,ReferenciasConcedidasDePrueba.de("00000000-0000-0000-0000-000000000001",0,s.getReferencia().toString(),0,d?1:2,lado,d?1:2,d?4:9,d?100:200,d?3:6,PoliticaColaboracionBilateral.camposDelRecurso(lado,a.campos().get(lado)),Set.of(),a.finalidad(),a.desde(),a.hasta(),hash(ss.get(lado).campos())));}return m;
    }
    private String hash(Map<String,Object> m){return Idempotencia.huellaExacta(json.objetoCanonico(m));}
}
