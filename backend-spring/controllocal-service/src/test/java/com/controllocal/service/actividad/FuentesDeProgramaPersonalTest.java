package com.controllocal.service.actividad;
import com.controllocal.service.*;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.soporte.AccesoOportunidad;
import com.controllocal.service.excepcion.*;
import com.controllocal.persistence.repositorio.ActividadInformeC7Repository;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FuentesDeProgramaPersonalTest {
    final ActividadInformeC7Repository fuentes=mock(ActividadInformeC7Repository.class);
    final AccesoOportunidad acceso=mock(AccesoOportunidad.class);
    final InteraccionService interacciones=mock(InteraccionService.class);
    final CompromisosDeSeguimiento compromisos=mock(CompromisosDeSeguimiento.class);
    final GeneradorProgramaPersonal generador=new GeneradorProgramaPersonal(mock(EncargoService.class),mock(ProspeccionService.class),mock(AvanceDeReporte.class),mock(InformesC7.class),mock(IndicadorService.class),interacciones,mock(OportunidadService.class),fuentes,mock(SeguimientoDeProspeccion.class),acceso,compromisos);
    @Test void unCompromisoCambiadoImpideHidratarElResumen(){doThrow(new ConflictoException("Fuente cambiada")).when(compromisos).exigirFuente(5,1,a);assertThrows(ConflictoException.class,()->generador.exigirEntrega(prueba("COMPROMISO_R27",1),a));verifyNoInteractions(interacciones);}
    final Actor a=new Actor(1,2,3,"AGENTE");
    ProgramasPersonales.PruebaEntrega prueba(String tipo,long version){return new ProgramasPersonales.PruebaEntrega(List.of(new ProgramasPersonales.Recurso(tipo,5,version)),List.of(),List.of());}
    @Test void elCambioDeVersionDeVisitaInvalidaElResultadoAnterior(){when(fuentes.fuenteActividad(1,"VISITA",5)).thenReturn(Optional.of(new ActividadInformeC7Repository.FuenteActividad("VISITA",5,2,7)));assertThrows(ConflictoException.class,()->generador.exigirEntrega(prueba("VISITA",1),a));verifyNoInteractions(interacciones);}
    @Test void unaOfertaNoDaAccesoSinLaOportunidadActual(){when(fuentes.fuenteActividad(1,"OFERTA",5)).thenReturn(Optional.of(new ActividadInformeC7Repository.FuenteActividad("OFERTA",5,1,7)));doThrow(new AccesoNoAutorizadoException()).when(acceso).contextoParaNegociacion(7,a);assertThrows(AccesoNoAutorizadoException.class,()->generador.exigirEntrega(prueba("OFERTA",1),a));verifyNoInteractions(interacciones);}
    @Test void laConsultaDeFuenteMantieneElTenant(){when(fuentes.fuenteActividad(1,"VISITA",5)).thenReturn(Optional.empty());assertThrows(NoEncontradoException.class,()->generador.exigirEntrega(prueba("VISITA",1),a));verifyNoInteractions(acceso,interacciones);}
}
