package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.persistence.repositorio.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnunciosDeLosEncargosTest {
    final PublicacionRepository anuncios=mock(PublicacionRepository.class);
    final HistoriaPublicacionRepository historia=mock(HistoriaPublicacionRepository.class);
    final AnunciosDeLosEncargos lector=new AnunciosDeLosEncargos(anuncios,historia);
    Captacion encargo(){var c=new Captacion();ReflectionTestUtils.setField(c,"id",40L);c.setOrganizacionId(1L);c.setMotivoOperacion("A");var p=new Propiedad();ReflectionTestUtils.setField(p,"id",10L);p.setOrganizacionId(1L);c.setPropiedad(p);return c;}
    Publicacion anuncio(){var p=new Publicacion();ReflectionTestUtils.setField(p,"id",50L);p.setOrganizacionId(1L);p.setIdEncargo(40L);p.setIdPropiedad(10L);p.setEstado("B");return p;}
    @Test void sinComposicionNoConsultaUnaClaveNulaEnMapaInmutable(){var c=encargo();when(historia.versionesComposiciones(eq(1L),anyList())).thenReturn(Map.of());when(anuncios.deEncargos(eq(1L),anyCollection())).thenReturn(List.of(anuncio()));var f=lector.deEncargos(1,List.of(c)).get(40L).getFirst();assertEquals(50L,f.id());assertNull(f.composicion());assertNull(f.versionComposicion());}
    @Test void conComposicionConservaReferenciaYVersionActuales(){var c=encargo();ReflectionTestUtils.setField(c,"idComposicionActual",900L);when(historia.versionesComposiciones(1,List.of(900L))).thenReturn(Map.of(900L,3L));when(anuncios.deEncargos(eq(1L),anyCollection())).thenReturn(List.of(anuncio()));var f=lector.deEncargos(1,List.of(c)).get(40L).getFirst();assertEquals(900L,f.composicion());assertEquals(3L,f.versionComposicion());}
    @Test void composicionSinVersionConservadaEsFalloNoUnCeroInventado(){var c=encargo();ReflectionTestUtils.setField(c,"idComposicionActual",900L);when(historia.versionesComposiciones(1,List.of(900L))).thenReturn(Map.of());when(anuncios.deEncargos(eq(1L),anyCollection())).thenReturn(List.of(anuncio()));assertThrows(IllegalStateException.class,()->lector.deEncargos(1,List.of(c)));}
}
