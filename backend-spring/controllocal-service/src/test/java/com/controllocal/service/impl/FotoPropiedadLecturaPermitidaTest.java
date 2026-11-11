package com.controllocal.service.impl;

import com.controllocal.domain.inmueble.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** C1: el predicado no transforma una denegación esperada en una avería de la proyección. */
class FotoPropiedadLecturaPermitidaTest {
    private final Actor actor=new Actor(7,11,13,Actor.AGENTE);
    private final FotoPropiedadRepository fotos=mock(FotoPropiedadRepository.class);
    private final AutoridadDePropiedad autoridad=mock(AutoridadDePropiedad.class);
    private final FotoPropiedadServiceImpl servicio=new FotoPropiedadServiceImpl(fotos,
            mock(PropiedadRepository.class),autoridad,mock(Transiciones.class));
    private final UUID original=UUID.randomUUID();

    @Test void consultaSoloOriginalActivoDeLaPropiedadYOrganizacionPermitidas(){
        var propiedad=mock(Propiedad.class);var foto=mock(FotoPropiedad.class);
        when(autoridad.buscarParaLectura(actor,23)).thenReturn(Optional.of(propiedad));
        when(foto.getArchivoRef()).thenReturn(original);when(fotos.deLaPropiedad(7,23)).thenReturn(List.of(foto));
        assertTrue(servicio.originalPermitidoParaLectura(23,original,actor));
        assertFalse(servicio.originalPermitidoParaLectura(23,UUID.randomUUID(),actor));
        verify(fotos,times(2)).deLaPropiedad(7,23);
    }
    @Test void noConsultaFotosCuandoLaPropiedadNoEsLegible(){
        when(autoridad.buscarParaLectura(actor,23)).thenReturn(Optional.empty());
        assertFalse(servicio.originalPermitidoParaLectura(23,original,actor));verifyNoInteractions(fotos);
    }
    @Test void laDenegacionDelAlcanceNoSaleDeLaFronteraTransaccional(){
        when(autoridad.buscarParaLectura(actor,23)).thenThrow(new AccesoNoAutorizadoException());
        assertFalse(servicio.originalPermitidoParaLectura(23,original,actor));verifyNoInteractions(fotos);
    }
    @Test void unaAveriaNoSeDevuelveComoAusenciaDeFotos(){
        when(autoridad.buscarParaLectura(actor,23)).thenThrow(new IllegalStateException("Fallo TEST del lector C1"));
        assertThrows(IllegalStateException.class,()->servicio.originalPermitidoParaLectura(23,original,actor));
        verifyNoInteractions(fotos);
    }
}
