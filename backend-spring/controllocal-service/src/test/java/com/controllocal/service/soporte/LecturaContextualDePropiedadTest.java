package com.controllocal.service.soporte;

import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.AsignacionResponsablePropiedadRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LecturaContextualDePropiedadTest {
    private static final Actor AGENTE = new Actor(1L, 2L, 3L, "AGENTE");
    private static final Actor BROKER = new Actor(1L, 4L, 5L, "BROKER");
    private static final Actor ADMIN = new Actor(1L, 6L, 7L, "TENANT_ADMIN");
    private final SupervisionAgenteRepository supervisiones = mock(SupervisionAgenteRepository.class);
    private final PropiedadRepository propiedades = mock(PropiedadRepository.class);
    private final Alcances alcances = new Alcances(supervisiones);
    private final AutoridadDePropiedad autoridad = new AutoridadDePropiedad(
            mock(DetalleAgenteRepository.class), mock(AsignacionResponsablePropiedadRepository.class),
            alcances, mock(ElegibilidadDeResponsable.class), propiedades);

    @Test void gobiernoNoAbreLecturaComercialPeroConservaSuPuertaExpresa() {
        assertThrows(AccesoNoAutorizadoException.class, () -> alcances.de(ADMIN));
        assertThrows(AccesoNoAutorizadoException.class, () -> autoridad.buscarParaLectura(ADMIN, 20L));
        assertFalse(autoridad.puedeLeer(ADMIN, 20L));
        assertTrue(alcances.paraGobiernoDeReasignacion(ADMIN).global());
        assertTrue(alcances.alcanzaParaGobiernoDeReasignacion(ADMIN, 40L));
        assertFalse(alcances.alcanza(ADMIN, 40L));
        assertThrows(AccesoNoAutorizadoException.class, () -> alcances.paraGobiernoDeReasignacion(AGENTE));
        verifyNoInteractions(propiedades);
    }

    @Test void lecturaNoUsaResponsableFisicoHistoricoComoGrant() {
        Propiedad p = mock(Propiedad.class);
        when(p.getId()).thenReturn(20L);
        when(p.getIdRolResponsable()).thenReturn(AGENTE.idRolOperativo());
        when(propiedades.permiteLectura(1L, 20L, "{3}")).thenReturn(false);
        assertFalse(autoridad.puedeLeerHistoriaDeLaPropiedad(AGENTE, p));
        verify(propiedades).permiteLectura(1L, 20L, "{3}");
    }

    @Test void supervisionSeReleeEnCadaAutorizacionYLaRelacionEsEscalar() {
        when(supervisiones.agentesSupervisados(1L, 5L)).thenReturn(List.of(3L), List.of());
        when(propiedades.permiteLectura(1L, 20L, "{3}")).thenReturn(true);
        assertTrue(autoridad.puedeLeer(BROKER, 20L));
        assertFalse(autoridad.puedeLeer(BROKER, 20L));
        verify(propiedades).permiteLectura(1L, 20L, "{-1}");
        verify(supervisiones, times(2)).agentesSupervisados(1L, 5L);
    }

    @Test void leerDatosFisicosNoConcedeLaHistoriaCompletaDeOtroResponsable() {
        Propiedad p = mock(Propiedad.class);
        when(p.getId()).thenReturn(20L);
        when(p.getIdRolResponsable()).thenReturn(99L);
        when(p.tieneResponsable()).thenReturn(true);
        when(propiedades.permiteLectura(1L, 20L, "{3}")).thenReturn(true);
        assertTrue(autoridad.puedeLeer(AGENTE, 20L));
        assertFalse(autoridad.puedeLeerHistoriaDeLaPropiedad(AGENTE, p));
        when(p.getIdRolResponsable()).thenReturn(3L);
        assertTrue(autoridad.puedeLeerHistoriaDeLaPropiedad(AGENTE, p));
    }

    @Test void brokerConLecturaFisicaNoHeredaHistoriaDeOtroEquipo() {
        Propiedad p = mock(Propiedad.class);
        when(p.getId()).thenReturn(20L);
        when(p.getIdRolResponsable()).thenReturn(99L);
        when(p.tieneResponsable()).thenReturn(true);
        when(supervisiones.agentesSupervisados(1L, 5L)).thenReturn(List.of(3L));
        when(propiedades.permiteLectura(1L, 20L, "{3}")).thenReturn(true);
        assertFalse(autoridad.puedeLeerHistoriaDeLaPropiedad(BROKER, p));
        when(p.getIdRolResponsable()).thenReturn(3L);
        assertTrue(autoridad.puedeLeerHistoriaDeLaPropiedad(BROKER, p));
        when(p.tieneResponsable()).thenReturn(false);
        assertTrue(autoridad.puedeLeerHistoriaDeLaPropiedad(BROKER, p));
        when(propiedades.permiteLectura(1L, 20L, "{3}")).thenReturn(false);
        assertFalse(autoridad.puedeLeerHistoriaDeLaPropiedad(BROKER, p));
    }

    @Test void busquedaDeFichaUsaElMismoFiltroAntesDeHidratar() {
        when(propiedades.buscarParaLectura(1L, 20L, "{3}")).thenReturn(Optional.empty());
        assertTrue(autoridad.buscarParaLectura(AGENTE, 20L).isEmpty());
        verify(propiedades).buscarParaLectura(1L, 20L, "{3}");
        verify(propiedades, never()).findByOrganizacionIdAndId(anyLong(), anyLong());
    }
}
