package com.controllocal.service.impl;

import com.controllocal.persistence.query.ConteoPorAgente;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.service.*;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.soporte.Alcances;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.List;
import java.util.Map;
import static com.controllocal.service.matching.ContratoMatching.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Un mismo resultado tipado por Demanda/version; concentración mantiene su finalidad propia. */
class HallazgoServiceImplTest {
    private static final Actor AGENTE=new Actor(1,2,3,Actor.AGENTE);
    private static final Actor BROKER=new Actor(1,4,5,Actor.BROKER);
    private static final Actor GOBIERNO=new Actor(1,4,8,Actor.TENANT_ADMIN);
    private final CaptacionRepository captaciones=mock(CaptacionRepository.class);
    private final Alcances alcances=mock(Alcances.class);
    private final DetalleAgenteRepository agentes=mock(DetalleAgenteRepository.class);
    private final CoincidenciaService coincidencias=mock(CoincidenciaService.class);
    private final HallazgoServiceImpl servicio=new HallazgoServiceImpl(captaciones,alcances,agentes,coincidencias);

    @Test void gobiernoNoAdquiereLecturaComercialPorAusenciaDeHallazgos() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.de(GOBIERNO));
        verifyNoInteractions(captaciones,alcances,agentes,coincidencias);
    }
    @Test void conservaCadaDemandaSinEscogerMejorClienteNiConvertirEnPorcentaje() {
        var primera=aptitud("A",2,2);var segunda=aptitud("B",0,0);
        when(coincidencias.aptitudesPropias(AGENTE)).thenReturn(List.of(
                new CoincidenciaService.Aptitud(9,1,11,3,"ENC-11",primera),new CoincidenciaService.Aptitud(10,2,11,3,"ENC-11",segunda)));
        var resultado=servicio.de(AGENTE);
        assertEquals(2,resultado.size());assertEquals(List.of(10L,9L),resultado.stream().map(HallazgoService.Hallazgo::idDemanda).toList());
        assertTrue(resultado.stream().anyMatch(h->h.aptitud()==primera));assertTrue(resultado.stream().anyMatch(h->h.aptitud()==segunda));
        assertTrue(resultado.stream().allMatch(h->h.porQue()==null&&h.destino().isEmpty()));
        verifyNoInteractions(captaciones,alcances,agentes);
    }
    @Test void concentracionDelBrokerNoSeConvierteEnAptitudDeMatching() {
        var roles=List.of(3L,4L,6L);when(alcances.de(BROKER)).thenReturn(new Alcances.Alcance(1,false,roles));
        var a=conteo(3,100);var b=conteo(4,0);var c=conteo(6,0);
        when(captaciones.contarEnCarteraPorAgentes(1,roles)).thenReturn(List.of(a,b,c));when(agentes.findAllById(roles)).thenReturn(List.of());
        var hallazgos=servicio.de(BROKER);assertEquals(1,hallazgos.size());var hallazgo=hallazgos.getFirst();
        assertEquals(HallazgoService.CONCENTRACION_DEL_EQUIPO,hallazgo.tipo());assertNull(hallazgo.aptitud());assertEquals("agentes",hallazgo.destino());
        assertNotNull(hallazgo.porQue());verifyNoInteractions(coincidencias);
    }
    @Test void equipoMenorAlMinimoVigenteNoInventaConcentracion() {
        when(alcances.de(BROKER)).thenReturn(new Alcances.Alcance(1,false,List.of(3L,4L)));
        assertTrue(servicio.de(BROKER).isEmpty());verifyNoInteractions(captaciones,agentes,coincidencias);
    }
    @Test void errorTecnicoDeLecturaNoSeDevuelveComoSinHallazgos() {
        when(coincidencias.aptitudesPropias(AGENTE)).thenThrow(new DataAccessResourceFailureException("TEST"));
        assertThrows(DataAccessResourceFailureException.class,()->servicio.de(AGENTE));
    }
    private static CoincidenciaService.Coincidencia aptitud(String ref,long s,long w){return new CoincidenciaService.Coincidencia(ref,3,
            CoincidenciaService.Capa.L0,Categoria.COMERCIAL_CONFIRMADA,Conclusion.CUMPLE,Conclusion.CUMPLE,s,0,w,List.of(),Map.of(),CoberturaCriterios.COMPLETA,null);}
    private static ConteoPorAgente conteo(long rol,long total){var c=mock(ConteoPorAgente.class);when(c.getIdAgente()).thenReturn(rol);when(c.getTotal()).thenReturn(total);return c;}
}