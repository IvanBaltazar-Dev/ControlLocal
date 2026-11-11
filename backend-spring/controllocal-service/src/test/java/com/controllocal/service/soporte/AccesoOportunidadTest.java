package com.controllocal.service.soporte;

import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.excepcion.*;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.comercial.OportunidadComercial;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Autoridad F3/C1 actual: AGENTE por actuación, BROKER por Encargo; gobierno no cartera. */
class AccesoOportunidadTest {
    final OportunidadComercialRepository repo=mock(OportunidadComercialRepository.class);
    final SupervisionAgenteRepository supervisiones=mock(SupervisionAgenteRepository.class);
    final EntityManager entidades=mock(EntityManager.class);
    final ContextosDeActividad ctx=new ContextosDeActividad(mock(CuentaAccesoRepository.class),mock(UsuarioOrganizacionRepository.class),mock(ContextosService.class),repo);
    final AccesoOportunidad acceso=new AccesoOportunidad(repo,new Alcances(supervisiones),ctx,entidades);
    final Actor agente=new Actor(1,3,30,Actor.AGENTE),otro=new Actor(1,4,40,Actor.AGENTE),broker=new Actor(1,2,20,Actor.BROKER),gobierno=new Actor(1,1,10,Actor.TENANT_ADMIN);
    @BeforeEach void montar(){
        when(repo.contextoOperativoActual(anyLong(),anyLong(),anyLong(),anyString(),any())).thenReturn(true);
        when(supervisiones.agentesSupervisados(1,20)).thenReturn(List.of(40L));
        var c=mock(OportunidadComercialRepository.ContextoOportunidad.class);
        when(c.getId()).thenReturn(8L);when(c.getOrganizacion()).thenReturn(1L);when(c.getVersion()).thenReturn(0L);
        when(c.getEncargo()).thenReturn(50L);when(c.getResponsable()).thenReturn(30L);when(c.getResponsableEncargo()).thenReturn(40L);
        when(c.getEstado()).thenReturn(OportunidadComercial.ABIERTA);when(c.getOperacion()).thenReturn("V");when(c.getCliente()).thenReturn(null);
        when(c.getPropiedad()).thenReturn(90L);when(c.getPersona()).thenReturn(100L);
        when(repo.contexto(1,8)).thenReturn(Optional.of(c));
    }
    @Test void agenteLeeSuActuacion(){assertEquals(8,acceso.contextoParaNegociacion(8,agente).id());}
    @Test void captarElInmuebleNoDaLaActuacionDeOtroAgente(){assertThrows(AccesoNoAutorizadoException.class,()->acceso.contextoParaNegociacion(8,otro));}
    @Test void brokerLeePorSuEncargoSupervisado(){assertEquals(50,acceso.contextoParaNegociacion(8,broker).idEncargo());}
    @Test void perderSupervisionRetiraLecturaActual(){
        when(supervisiones.agentesSupervisados(1,20)).thenReturn(List.of());assertThrows(AccesoNoAutorizadoException.class,()->acceso.contextoParaNegociacion(8,broker));
    }
    @Test void gobiernoNoHeredaCartera(){assertThrows(AccesoNoAutorizadoException.class,()->acceso.contextoParaNegociacion(8,gobierno));}
    @Test void otroTenantNoRecibeLaProyeccion(){
        assertThrows(NoEncontradoException.class,()->acceso.contextoParaNegociacion(8,new Actor(2,13,130,Actor.AGENTE)));
    }
    @Test void unaBajaActualNoSeSustituyePorEntidadGestionada(){
        when(repo.contextoOperativoActual(anyLong(),anyLong(),anyLong(),anyString(),any())).thenReturn(false);
        assertThrows(AccesoNoAutorizadoException.class,()->acceso.contextoParaNegociacion(8,agente));
    }
    @Test void metadataNoIncluyeElExpedientePersonalDelInteresado(){
        var c=acceso.contextoParaNegociacion(8,agente);assertEquals(100,c.interesado().idPersona());assertNull(c.interesado().idCliente());
    }
    @Test void unBrokerLectorNoSeConvierteEnEscritor(){
        assertThrows(AccesoNoAutorizadoException.class,()->acceso.bloquearActividad(8,0,broker));verify(repo,never()).bloquear(anyLong(),anyLong());
    }
    @Test void selectorTipadoConservaLaMismaProyeccionPermitida(){
        assertEquals(acceso.contextoParaNegociacion(8,agente),acceso.buscarParaLectura(8,agente).orElseThrow());
        assertEquals(acceso.contextoParaNegociacion(8,broker),acceso.buscarParaLectura(8,broker).orElseThrow());
    }
    @Test void selectorFueraDeAmbitoODeTenantNoExponeMetadata(){
        assertTrue(acceso.buscarParaLectura(8,otro).isEmpty());
        assertTrue(acceso.buscarParaLectura(8,new Actor(2,13,130,Actor.AGENTE)).isEmpty());
        when(supervisiones.agentesSupervisados(1,20)).thenReturn(List.of());
        assertTrue(acceso.buscarParaLectura(8,broker).isEmpty());
    }
    @Test void selectorNoConcedeGobiernoNiUnContextoDadoDeBaja(){
        assertTrue(acceso.buscarParaLectura(8,gobierno).isEmpty());
        when(repo.contextoOperativoActual(anyLong(),anyLong(),anyLong(),anyString(),any())).thenReturn(false);
        assertTrue(acceso.buscarParaLectura(8,agente).isEmpty());
    }
    @Test void selectorNoConvierteUnFalloTecnicoEnAusenciaAutorizada(){
        var fallo=new IllegalStateException("Fallo técnico de proyección TEST");
        when(repo.contexto(1,8)).thenThrow(fallo);
        assertSame(fallo,assertThrows(IllegalStateException.class,()->acceso.buscarParaLectura(8,agente)));
    }
}
