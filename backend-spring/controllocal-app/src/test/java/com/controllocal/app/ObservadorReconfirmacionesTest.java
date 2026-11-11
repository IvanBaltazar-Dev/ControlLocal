package com.controllocal.app;

import com.controllocal.persistence.repositorio.ReconfirmacionesDisponibilidadRepository;
import com.controllocal.persistence.repositorio.ReconfirmacionesDisponibilidadRepository.Destinatario;
import com.controllocal.service.Actor;
import com.controllocal.service.disponibilidad.ReconfirmacionesDisponibilidad;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObservadorReconfirmacionesTest {
    final ReconfirmacionesDisponibilidadRepository repo=mock(ReconfirmacionesDisponibilidadRepository.class);
    final ReconfirmacionesDisponibilidad servicio=mock(ReconfirmacionesDisponibilidad.class);
    final ObservadorReconfirmaciones observador=new ObservadorReconfirmaciones(repo,servicio);
    @Test void paginaDestinatariosRealesYNoSeDetienePorUnaRevocacion(){
        when(repo.destinatarios(0,100)).thenReturn(List.of(new Destinatario(1,11,21),new Destinatario(2,12,22)));
        when(repo.destinatarios(22,100)).thenReturn(List.of(new Destinatario(3,13,23)));
        when(repo.destinatarios(23,100)).thenReturn(List.of());
        var revocado=new Actor(1,11,21,Actor.AGENTE);doThrow(new AccesoNoAutorizadoException()).when(servicio).sincronizar(revocado);
        observador.observar();verify(servicio).sincronizar(revocado);
        verify(servicio).sincronizar(new Actor(2,12,22,Actor.AGENTE));verify(servicio).sincronizar(new Actor(3,13,23,Actor.AGENTE));
        verifyNoMoreInteractions(servicio);
    }
    @Test void falloPorDestinatarioConservaLaRecuperacionDeLaSiguienteObservacion(){
        var actor=new Actor(1,11,21,Actor.AGENTE);
        when(repo.destinatarios(0,100)).thenReturn(List.of(new Destinatario(1,11,21)));
        when(repo.destinatarios(21,100)).thenReturn(List.of());
        doThrow(new IllegalStateException("INCIDENTE_TEST")).doNothing().when(servicio).sincronizar(actor);
        assertDoesNotThrow(observador::observar);assertDoesNotThrow(observador::observar);
        verify(servicio,times(2)).sincronizar(actor);verify(repo,times(2)).destinatarios(0,100);
    }
    @Test void falloDeLaConsultaNoSePresentaComoCeroSolicitudes(){
        when(repo.destinatarios(0,100)).thenThrow(new IllegalStateException("BASE_NO_DISPONIBLE"));
        assertThrows(IllegalStateException.class,observador::observar);verifyNoInteractions(servicio);
    }
    @Test void noInventaUnActorCuandoNoHayDestinatarios(){
        when(repo.destinatarios(0,100)).thenReturn(List.of());observador.observar();verifyNoInteractions(servicio);
    }
    @Test void interrupcionRespetaLaParadaSinAdelantarUnCursor(){
        Thread.currentThread().interrupt();try{observador.observar();verifyNoInteractions(repo,servicio);}finally{Thread.interrupted();}
    }
}
