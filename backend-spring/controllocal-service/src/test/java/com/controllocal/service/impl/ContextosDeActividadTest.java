package com.controllocal.service.impl;

import com.controllocal.service.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Prueba el consumidor canónico de auth; no reemplaza las integraciones reales C1 de ContextosService. */
class ContextosDeActividadTest {
    ActividadDePrueba h;
    @BeforeEach void montar(){h=new ActividadDePrueba();}
    @Test void operadorAgenteYBrokerUsanRolActual(){assertDoesNotThrow(()->h.contextos.exigirOperador(h.agente));assertDoesNotThrow(()->h.contextos.exigirOperador(h.broker));}
    @Test void gobiernoNoOperaNiPorTenerEmpresa(){assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.exigirOperador(h.gobierno));}
    @Test void brokerNoEsAgenteParaEscribir(){assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.exigirAgente(h.broker));}
    @Test void definicionInocuaAdmiteGobiernoActualSinDarleOperacion(){
        when(h.oportunidades.contextoGobiernoParaMetadata(eq(1L),eq(9L),eq(3L),any())).thenReturn(true);
        assertDoesNotThrow(()->h.contextos.exigirMetadata(h.gobierno));assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.exigirOperador(h.gobierno));
    }
    @Test void unAdminSinPertenenciaVigenteNoObtieneNiMetadata(){assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.exigirMetadata(h.gobierno));}
    @Test void rolDesconocidoNoHeredaPermisos(){assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.exigirMetadata(new Actor(1,7,1,"FUTURO")));}
    @Test void bajaLocalActualNoSeSustituyePorUnActorCacheado(){
        when(h.oportunidades.contextoOperativoActual(anyLong(),anyLong(),anyLong(),anyString(),any())).thenReturn(false);
        assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.exigirOperador(h.agente));
    }
    @Test void cuentaSeBloqueaAntesDeSesion(){
        h.contextos.iniciar(h.agente,h.sid);var orden=inOrder(h.cuentas,h.sesiones);
        orden.verify(h.cuentas).bloquearCambioAcceso(99);orden.verify(h.sesiones).exigirContexto(h.sid.referenciaSesion(),h.sid.revisionContexto(),h.agente);
    }
    @Test void pertenenciaAusenteNoSeVuelveCuentaPorDefecto(){
        when(h.miembros.cuentasActivasDePersona(1,7)).thenReturn(List.of());assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.iniciar(h.agente,h.sid));verifyNoInteractions(h.sesiones);
    }
    @Test void pertenenciasAmbiguasNoEligenLaPrimera(){
        when(h.miembros.cuentasActivasDePersona(1,7)).thenReturn(List.of(99L,100L));assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.iniciar(h.agente,h.sid));verifyNoInteractions(h.sesiones);
    }
    @Test void sesionRevisadaNoEsLaQueElClienteVio(){
        var otra=new ContextoSesion(h.sid.identidad(),h.sid.expiraEnEpochSeconds()-1,h.sid.referenciaSesion(),h.sid.revisionContexto());
        assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.iniciar(h.agente,otra));
    }
    @Test void sesionRevocadaNoAutorizaPorContextoDto(){
        doThrow(new CredencialesInvalidasException()).when(h.sesiones).exigirContexto(anyString(),anyLong(),any());
        assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.iniciar(h.agente,h.sid));
    }
    @Test void restriccionesPasswordOMfaSeDeleganALaGuardaCanonica(){
        // ContextosServiceImpl.exigirContexto140–148 ya rechaza ambas restricciones.
        doThrow(new AccesoNoAutorizadoException()).when(h.sesiones).exigirContexto(anyString(),anyLong(),any());
        assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.iniciar(h.agente,h.sid));
    }
    @Test void lecturaProtegidaTomaCuentaAntesDeSidSinConvertirlaEnEscritura(){
        when(h.cuentas.bloquearLecturaContexto(99)).thenReturn(java.util.Optional.of(new com.controllocal.domain.seguridad.CuentaAcceso()));
        when(h.sesiones.exigirContextoConLecturaProtegida(h.sid.referenciaSesion(),h.sid.revisionContexto(),h.agente)).thenReturn(h.sesion);
        h.contextos.iniciarConLecturaProtegida(h.agente,h.sid);var orden=inOrder(h.cuentas,h.sesiones);
        orden.verify(h.cuentas).bloquearLecturaContexto(99);
        orden.verify(h.sesiones).exigirContextoConLecturaProtegida(h.sid.referenciaSesion(),h.sid.revisionContexto(),h.agente);
        verify(h.cuentas,never()).bloquearCambioAcceso(anyLong());
        verify(h.sesiones,never()).exigirContexto(anyString(),anyLong(),any());
    }
    @Test void lecturaProtegidaNoConvierteGobiernoOSupervisionEnAgente(){
        assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.iniciarConLecturaProtegida(h.gobierno,h.sid));
        assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.iniciarConLecturaProtegida(h.broker,h.sid));
        verifyNoInteractions(h.cuentas,h.sesiones);
    }
    @Test void lecturaProtegidaConservaLaDenegacionDeLaGuardaCanonica(){
        when(h.cuentas.bloquearLecturaContexto(99)).thenReturn(java.util.Optional.of(new com.controllocal.domain.seguridad.CuentaAcceso()));
        doThrow(new CredencialesInvalidasException()).when(h.sesiones).exigirContextoConLecturaProtegida(anyString(),anyLong(),any());
        assertThrows(AccesoNoAutorizadoException.class,()->h.contextos.iniciarConLecturaProtegida(h.agente,h.sid));
        verify(h.cuentas,never()).bloquearCambioAcceso(anyLong());
    }
}
