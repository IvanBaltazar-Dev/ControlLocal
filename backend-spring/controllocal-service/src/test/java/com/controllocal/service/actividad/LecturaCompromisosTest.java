package com.controllocal.service.actividad;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.soporte.*;
import com.controllocal.service.excepcion.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class LecturaCompromisosTest {
    final CompromisoSeguimientoRepository repo=mock(CompromisoSeguimientoRepository.class);
    final SeguimientoProspeccionRepository fuentes=mock(SeguimientoProspeccionRepository.class);
    final SeguimientoDeProspeccion seguimiento=mock(SeguimientoDeProspeccion.class);
    final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
    final Alcances alcances=mock(Alcances.class);
    final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules());
    final CompromisosDeSeguimiento servicio=new CompromisosDeSeguimiento(repo,fuentes,seguimiento,contexto,mock(ContextosDeInteraccion.class),mock(InteraccionComercialRepository.class),alcances,json,mock(ComandoIdempotenteRepository.class),mock(ComandosIdempotentes.class),mock(EventoDominioRepository.class));
    final Actor a=new Actor(1,2,3,"AGENTE");final Instant ahora=Instant.parse("2026-11-10T15:00:00Z");
    final CompromisoSeguimientoRepository.Fila cab=new CompromisoSeguimientoRepository.Fila(5,UUID.randomUUID(),6,7,3,0,"{\"versionSeguimiento\":1}",ahora,ahora);
    @BeforeEach void montar(){when(fuentes.contexto(1,7,false)).thenReturn(Optional.of(new SeguimientoProspeccionRepository.Contexto(7,3,8,null,"P",false,false)));when(alcances.alcanza(a,3L)).thenReturn(true);when(repo.deProspeccion(1,7)).thenReturn(List.of(cab));}
    @Test void autorizaLaFuenteAntesDeLeerElDetalleDelCompromiso(){doThrow(new AccesoNoAutorizadoException()).when(seguimiento).exigirLecturaVersion(6,1,7,a);assertThrows(AccesoNoAutorizadoException.class,()->servicio.listar(7,a));verify(repo,never()).obtener(anyLong(),any(),anyBoolean());verify(repo,never()).contenidoVersion(anyLong(),anyLong(),anyLong());}
    @Test void perderElContextoImpideInclusoConsultarLasCabeceras(){doThrow(new AccesoNoAutorizadoException()).when(contexto).exigirOperador(a);assertThrows(AccesoNoAutorizadoException.class,()->servicio.listar(7,a));verifyNoInteractions(repo);}
    @Test void unaRevocacionDespuesDeLaLecturaImpideEntregarLaLista(){
        var c=new CompromisosDeSeguimiento.Contenido(1,InteraccionService.OrigenActuacion.INTENCION_PROFESIONAL,LocalDate.of(2026,11,10),CatalogoInteracciones.TipoActividad.LLAMADA,"Detalle privado TEST",List.of(),List.of(),null,null,null);
        var f=new CompromisoSeguimientoRepository.Fila(cab.id(),cab.referencia(),6,7,3,0,json.objetoCanonico(c),ahora,ahora);when(repo.obtener(1,cab.referencia(),false)).thenReturn(Optional.of(f));
        doNothing().doThrow(new AccesoNoAutorizadoException()).when(seguimiento).exigirLecturaVersion(6,1,7,a);assertThrows(AccesoNoAutorizadoException.class,()->servicio.listar(7,a));
    }
}
