package com.controllocal.service.materialia;

import com.controllocal.persistence.materialia.RegistroTratamientoMaterialIaRepository;
import com.controllocal.persistence.materialia.RegistroTratamientoMaterialIaRepository.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.documentos.SeleccionInmueblePieza;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.AvisosTratamientoMaterialIa.*;
import com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa.*;
import com.controllocal.service.materialia.FuentesMaterialIa.DestinoTratamiento;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Artefacto existente: verificación histórica y fuente de uso SINTÉTICA; sin política positiva productiva. */
class EvidenciasTratamientoMaterialIaArtefactoTest {
    final Actor actual=new Actor(1,30,40,Actor.AGENTE);
    final RegistroTratamientoMaterialIaRepository repo=mock(RegistroTratamientoMaterialIaRepository.class);
    final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    final UsuarioOrganizacionRepository miembros=mock(UsuarioOrganizacionRepository.class);
    final SeleccionInmueblePieza fisica=mock(SeleccionInmueblePieza.class);
    final ContextosService contextos=mock(ContextosService.class);
    final ContextosDeActividad actividad=mock(ContextosDeActividad.class);
    final AvisosTratamientoMaterialIa fuente=mock(AvisosTratamientoMaterialIa.class);
    final Documentos json=new Documentos(JsonMapper.builder().findAndAddModules().build());
    final AmbitoTratamiento ambito=new AmbitoTratamiento(new Solicitud(UUID.randomUUID(),Accion.MEJORAR_FOTO,
            new Recurso("PROPIEDAD","9"),"FUENTE_TEST",UUID.randomUUID(),null),"a".repeat(64),Set.of("FOTO_TEST"),"b".repeat(64));
    final UUID evento=UUID.randomUUID(),presentacion=UUID.randomUUID(),avisoId=UUID.randomUUID();
    EvidenciasTratamientoMaterialIaImpl servicio;
    DecisionFila decision;Presentacion presentada;Aviso aviso;Contenido contenido;
    Map<String,Object> datos;

    @BeforeEach void preparar(){
        servicio=new EvidenciasTratamientoMaterialIaImpl(repo,contextos,actividad,miembros,cuentas,fisica,mock(AccesoEncargo.class),json,List.of(fuente));
        when(miembros.cuentasActivasDePersona(1,30)).thenReturn(List.of(11L));
        when(cuentas.vista(11)).thenReturn(Optional.of(new CuentaAccesoFila(11,"TEST","test@example.invalid",Instant.EPOCH,
                "TEST","A",null,false,false)));
        String alcance=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("organizacion",1L,"recurso",ambito.solicitud().recurso(),"finalidad",Accion.MEJORAR_FOTO)));
        var s=ambito.solicitud();var historico=new AmbitoTratamiento(new Solicitud(null,s.accion(),s.recurso(),s.versionOrigen(),s.fotoOriginal(),null),ambito.huellaFuente(),ambito.categorias(),ambito.huellaPayload());
        String contextoHistorico=json.objetoCanonico(historico);
        presentada=new Presentacion(presentacion,7,1,alcance,"SID_HISTORICO_NO_ES_PERMISO",0,avisoId,contextoHistorico,Idempotencia.huellaExacta(contextoHistorico));
        String condiciones="CONDICIONES SINTÉTICAS TEST, sin proveedor real.";
        contenido=new Contenido("AVISO_TEST",1,"SINTÉTICO",Set.of(Accion.MEJORAR_FOTO),Set.of("FOTO_TEST"),"LEGAL_TEST","COND_TEST",condiciones,
                "FUENTE_TEST",Idempotencia.huellaExacta(condiciones),Set.of(new DestinoTratamiento("TEST","TEST","1")));
        var avisoPersistido=json.comoMapa(json.objetoCanonico(contenido));
        avisoPersistido.put("destinos",contenido.destinos().stream().toList());
        String avisoJson=json.objetoCanonico(avisoPersistido);aviso=new Aviso(avisoId,"AVISO_TEST",1,Idempotencia.huellaExacta(avisoJson),avisoJson);
        datos=new LinkedHashMap<>();datos.put("cuenta",7L);datos.put("organizacion",1L);datos.put("evento",evento);datos.put("presentacion",presentacion);
        datos.put("version",1L);datos.put("decision","ACEPTAR");datos.put("aviso",avisoId);datos.put("huellaAviso",aviso.huella());
        datos.put("huellaFuente",ambito.huellaFuente());datos.put("huellaPayload",ambito.huellaPayload());
        decision=decision(json.objetoCanonico(datos),1);responder();
        when(fuente.legitimacionUsoArtefacto(ambito,contenido,actual)).thenReturn(Optional.of(new Legitimacion("USO_TEST","FUENTE_TEST","c".repeat(64),
                s.recurso(),Set.of(Accion.MEJORAR_FOTO),Set.of("FOTO_TEST"),contenido.proveedorLegal(),contenido.condicionesVersion())));
    }
    private DecisionFila decision(String datos,long version){return new DecisionFila(evento,7,1,presentada.alcance(),version,presentacion,"ACEPTAR",avisoId,datos,Idempotencia.huellaExacta(datos),Instant.EPOCH);}
    private void responder(){when(repo.eventosDeOrganizacion(1,evento)).thenReturn(List.of(decision));when(repo.presentacion(presentacion,7,1)).thenReturn(Optional.of(presentada));when(repo.aviso(avisoId)).thenReturn(Optional.of(aviso));}
    private void verificar(){servicio.revalidarArtefacto(evento.toString(),ambito,actual);}

    @Test void laAutoraHistoricaNoDebeTenerCuentaActivaNiPrestarSuPreferencia(){
        assertEquals(EstadoUsoArtefacto.PERMITIDO,servicio.evaluarUsoArtefacto(evento.toString(),ambito,actual));
        assertDoesNotThrow(this::verificar);verify(cuentas,never()).vista(7);verify(repo,never()).ultima(anyLong(),anyLong(),anyString());
        verify(fuente,never()).vigente(any(),any());verify(fuente,never()).legitimacionActual(any(),any(),any());verifyNoInteractions(contextos);
    }
    @ParameterizedTest @ValueSource(strings={"DATOS","AMBITO","AVISO","VERSION","CUENTA"})
    void noAceptaUnHistoricoAlteradoAunqueLaFuenteActualSeaPositiva(String campo){
        switch(campo){
            case "DATOS" -> decision=new DecisionFila(evento,7,1,presentada.alcance(),1,presentacion,"ACEPTAR",avisoId,"{}",decision.huella(),Instant.EPOCH);
            case "AMBITO" -> presentada=new Presentacion(presentacion,7,1,presentada.alcance(),presentada.sesion(),0,avisoId,"{}",presentada.huellaAmbito());
            case "AVISO" -> aviso=new Aviso(avisoId,aviso.referencia(),aviso.version(),aviso.huella(),"{}");
            case "VERSION" -> decision=decision(json.objetoCanonico(datos),2);
            case "CUENTA" -> {datos.put("cuenta",999);decision=decision(json.objetoCanonico(datos),1);}
        }
        responder();assertThrows(IllegalStateException.class,()->servicio.evaluarUsoArtefacto(evento.toString(),ambito,actual));
        assertThrows(IllegalStateException.class,this::verificar);verifyNoInteractions(fuente);
    }
    @Test void unaReferenciaAmbiguaNoEligeLaPrimeraCuenta(){
        when(repo.eventosDeOrganizacion(1,evento)).thenReturn(List.of(decision,decision));
        assertEquals(EstadoUsoArtefacto.PERMISO_INSUFICIENTE,servicio.evaluarUsoArtefacto(evento.toString(),ambito,actual));
        assertThrows(AccesoNoAutorizadoException.class,this::verificar);verifyNoInteractions(fuente);
    }
    @Test void baseDeUsoAusenteDeniegaSinReciclarPermisoDeTransferencia(){
        when(fuente.legitimacionUsoArtefacto(any(),any(),any())).thenReturn(Optional.empty());
        assertEquals(EstadoUsoArtefacto.CAPACIDAD_DESHABILITADA,servicio.evaluarUsoArtefacto(evento.toString(),ambito,actual));
        assertThrows(CapacidadDeshabilitadaException.class,this::verificar);verify(fuente,never()).legitimacionActual(any(),any(),any());
    }
    @Test void laPerdidaActualDelRecursoImpideInclusoLeerElHistorico(){
        doThrow(new AccesoNoAutorizadoException()).when(fisica).exigirLecturaHistorica(1,9,actual);
        assertEquals(EstadoUsoArtefacto.PERMISO_INSUFICIENTE,servicio.evaluarUsoArtefacto(evento.toString(),ambito,actual));
        assertThrows(AccesoNoAutorizadoException.class,this::verificar);verifyNoInteractions(repo,fuente);
    }
    @Test void evaluacionNoDisfrazaUnFalloTecnicoDeLaFuenteComoDenegacion(){
        when(fuente.legitimacionUsoArtefacto(any(),any(),any())).thenThrow(new IllegalStateException("FALLO TECNICO TEST"));
        assertThrows(IllegalStateException.class,()->servicio.evaluarUsoArtefacto(evento.toString(),ambito,actual));
        assertThrows(IllegalStateException.class,this::verificar);
    }
    @Test void evaluacionPropagaFalloDePersistenciaEnLugarDeResultadoVacio(){
        when(repo.eventosDeOrganizacion(1,evento)).thenThrow(new org.springframework.dao.CannotAcquireLockException("FALLO PG TEST"));
        assertThrows(org.springframework.dao.CannotAcquireLockException.class,()->servicio.evaluarUsoArtefacto(evento.toString(),ambito,actual));
        assertThrows(org.springframework.dao.CannotAcquireLockException.class,this::verificar);verifyNoInteractions(fuente);
    }
    @Test void evaluacionDeAusenciaDevuelvePermisoInsuficientePeroExigirConservaLaExcepcion(){
        doThrow(new NoEncontradoException("RECURSO TEST")).when(fisica).exigirLecturaHistorica(1,9,actual);
        assertEquals(EstadoUsoArtefacto.PERMISO_INSUFICIENTE,servicio.evaluarUsoArtefacto(evento.toString(),ambito,actual));
        assertThrows(NoEncontradoException.class,this::verificar);verifyNoInteractions(repo,fuente);
    }
}
