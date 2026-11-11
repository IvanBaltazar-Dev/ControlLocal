package com.controllocal.service.soporte;

import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.comercial.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.persistence.repositorio.FuentesAsuntosRepository.*;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** C7: identidad, conservación y selección de fuentes; no sustituye PostgreSQL ni autoridad real. */
class FuentesAsuntosTest {
    private final Actor actor=new Actor(1,3,30,"AGENTE"),otro=new Actor(1,4,40,"BROKER");
    private final FuentesAsuntosRepository repo=mock(FuentesAsuntosRepository.class);
    private final AutoridadAsuntos autoridad=mock(AutoridadAsuntos.class);
    private final EntityManager em=mock(EntityManager.class);
    private final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    private final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules());
    private final FuentesAsuntos fuentes=new FuentesAsuntos(repo,autoridad,json,em,eventos);
    private static final Recurso SOL=new Recurso("SOLICITUD_ALQUILER",5);
    private static final Instant FECHA=Instant.parse("2026-11-04T12:00:00Z");
    private final List<EventoDominio> historia=new ArrayList<>();
    private final AtomicReference<RevisionDocumento> ultima=new AtomicReference<>();
    @BeforeEach void montaje(){
        when(autoridad.legibles(anyCollection(),any())).thenAnswer(i->Set.copyOf(i.<Collection<AutoridadAsuntos.Consulta>>getArgument(0)));
        when(repo.ultimaRevisionDocumento(1,7)).thenAnswer(i->Optional.ofNullable(ultima.get()));
        when(eventos.saveAndFlush(any(EventoDominio.class))).thenAnswer(i->{
            EventoDominio e=i.getArgument(0);ReflectionTestUtils.setField(e,"id",100L+historia.size());historia.add(e);
            if("DOCUMENTO_SOLICITUD_REVISADO".equals(e.getTipo()))ultima.set(new RevisionDocumento(e.getId(),e.getCargaUtil()));
            return e;
        });
    }
    private FuentesAsuntos.Causa causa(long responsable,Instant fecha,long hecho){return new FuentesAsuntos.Causa(1,SOL,"SEGUIMIENTO_SOLICITUD",
            new HechoAsunto("HISTORIAL_ESTADO",hecho,null),FuentesAsuntos.Origen.de("ESTADO_SOLICITUD",5),fecha,null,responsable,"A");}
    @Test void identidadMaterialNoCambiaPorReasignacionNiPorFechaDeDeteccion(){
        var a=causa(30,FECHA,11);var b=causa(31,FECHA.plusSeconds(60),11);
        assertEquals(fuentes.huella(1,a),fuentes.huella(1,b));
        assertNotEquals(fuentes.huella(1,a),fuentes.huella(1,causa(30,FECHA,12)));
        assertNotEquals(fuentes.huella(1,a),fuentes.huella(2,a));
        assertEquals(a,fuentes.leer(fuentes.guardar(a)));assertNull(a.hecho().version());
    }
    @Test void fuenteDeOtroRecursoNoSeConvierteEnAviso(){
        when(repo.resolver(1,"EVALUACION_SOLICITUD","7")).thenReturn(Optional.of(new Fuente(new HechoAsunto("EVALUACION_SOLICITUD",7,null),new Recurso("SOLICITUD_ALQUILER",6),FECHA,null,null)));
        assertThrows(ConflictoException.class,()->fuentes.resolver(SOL,"SOLICITUD_EVALUADA",FuentesAsuntos.Origen.de("EVALUACION_SOLICITUD",7),actor));
        verify(repo,never()).contextos(anyLong(),anyCollection());verifyNoInteractions(eventos);
    }
    @Test void falloTecnicoDeFuenteNoEquivaleAPoblacionVacia(){
        when(repo.resolverLote(eq(1L),eq("ESTADO_SOLICITUD"),anyCollection())).thenThrow(new IllegalStateException("DB no disponible TEST"));
        assertThrows(IllegalStateException.class,()->fuentes.vigentes(List.of(causa(30,FECHA,11)),actor));
    }
    @Test void revocacionDePermisoExcluyeSinConsultarLaFuentePrivada(){
        when(autoridad.legibles(anyCollection(),eq(actor))).thenReturn(Set.of());
        assertTrue(fuentes.vigentes(List.of(causa(30,FECHA,11)),actor).isEmpty());
        verify(repo,never()).resolverLote(anyLong(),anyString(),anyCollection());
    }
    @Test void cierreDerivaSolicitudYEncargoDeHitoSinPedirLecturaSolicitud(){
        when(repo.hitoCierre(1,10)).thenReturn(Optional.of(new HitoCierre(10,20,30,40L,"CONTRATO_ALQUILER","[99]",actor.idRolOperativo())));
        var cierre=fuentes.cierreConfirmado(10,actor);
        assertEquals(Map.of("CAPTACION",Set.of(20L),"OPORTUNIDAD",Set.of(30L),"SOLICITUD_ALQUILER",Set.of(40L)),cierre.recursos());
        verify(autoridad).exigirDecisionEncargo(20,actor);verify(autoridad,never()).legibles(anyCollection(),any());
    }
    @Test void escrituraSóloResuelveUnidadesExactasYRechazaRepeticiones(){
        when(repo.hitoCierre(1,10)).thenReturn(Optional.of(new HitoCierre(10,20,30,null,"ESCRITURA","[99,100]",actor.idRolOperativo())));
        assertEquals(Set.of(99L,100L),fuentes.cierreConfirmado(10,actor).recursos().get("INMUEBLE"));
        when(repo.hitoCierre(1,10)).thenReturn(Optional.of(new HitoCierre(10,20,30,null,"ESCRITURA","[99,99]",actor.idRolOperativo())));
        assertThrows(IllegalStateException.class,()->fuentes.cierreConfirmado(10,actor));
    }
    @Test void actorAjenoNoUsaConfirmacionDeOtroNiOcultaDenegacionDeDecision(){
        when(repo.hitoCierre(1,10)).thenReturn(Optional.of(new HitoCierre(10,20,30,null,"CONTRATO_ALQUILER",null,30)));
        assertThrows(AccesoNoAutorizadoException.class,()->fuentes.cierreConfirmado(10,otro));
        doThrow(new AccesoNoAutorizadoException()).when(autoridad).exigirDecisionEncargo(20,actor);
        assertThrows(AccesoNoAutorizadoException.class,()->fuentes.cierreConfirmado(10,actor));
    }
    @Test void revisionExactaSeReintentaSinDuplicarPeroABACreaTresHechos(){
        var d=documento("Primera observación");var a=fuentes.registrarRevisionDocumento(d,actor);
        assertEquals(a,fuentes.registrarRevisionDocumento(d,actor));assertEquals(1,historia.size());
        d.setObservaciones("Observación material diferente");var b=fuentes.registrarRevisionDocumento(d,actor);
        d.setObservaciones("Primera observación");var c=fuentes.registrarRevisionDocumento(d,actor);
        assertNotEquals(a,b);assertNotEquals(a,c);assertEquals(3,historia.size());
        assertEquals(101L,((Number)json.comoMapa(historia.getLast().getCargaUtil()).get("antecedente")).longValue());
    }
    @Test void mismaRevisionDeOtroActorConservaAutoriaYConstanciaSeparada(){
        var d=documento("Sustento observado");var material=fuentes.registrarRevisionDocumento(d,actor);
        assertEquals(material,fuentes.registrarRevisionDocumento(d,otro));assertEquals(2,historia.size());
        assertEquals("DOCUMENTO_SOLICITUD_REVISADO",historia.getFirst().getTipo());assertEquals(30L,historia.getFirst().getIdPersonaRol());
        assertEquals("REVISION_DOCUMENTO_REITERADA",historia.getLast().getTipo());assertEquals(40L,historia.getLast().getIdPersonaRol());
        when(repo.tieneConstanciaReiteracion(1,7,40,100)).thenReturn(true);
        assertEquals(material,fuentes.registrarRevisionDocumento(d,otro));assertEquals(2,historia.size());
    }
    private DocumentoSolicitud documento(String observacion){
        var s=new SolicitudAlquiler();s.setOrganizacionId(1L);ReflectionTestUtils.setField(s,"id",5L);
        var d=new DocumentoSolicitud();d.setOrganizacionId(1L);ReflectionTestUtils.setField(d,"id",7L);d.setSolicitud(s);
        d.setObservaciones(observacion);d.setResultadoRevision("O");return d;
    }
}
