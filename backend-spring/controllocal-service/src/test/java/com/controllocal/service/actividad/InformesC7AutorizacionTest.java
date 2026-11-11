package com.controllocal.service.actividad;
import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.planes.DerechosDeInformesC7;
import com.controllocal.service.planes.DerechosDeInformesC7.Composicion;
import com.controllocal.domain.planes.PlanBrox;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.controllocal.persistence.repositorio.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Revalidar fuentes al entregar también aplica al informe directo, no sólo al workerP0. */
class InformesC7AutorizacionTest {
    final DerechosDeInformesC7 derechos=mock(DerechosDeInformesC7.class);
    final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
    final Alcances alcances=mock(Alcances.class);
    final InteraccionService interacciones=mock(InteraccionService.class);
    final InformesC7 servicio=new InformesC7(derechos,contexto,mock(MedicionesC7.class),mock(RendimientoComercialService.class),mock(ComisionService.class),alcances,
        mock(ActividadInformeC7Repository.class),interacciones,mock(IdentidadColaboracionRepository.class),mock(EncargoService.class),mock(SeguimientoDeProspeccion.class),mock(DesenlacesDeOportunidad.class),mock(AccesoOportunidad.class),mock(com.controllocal.service.encargo.AccesoEncargo.class),mock(ProspeccionService.class),mock(CompromisoSeguimientoRepository.class),mock(CompromisosDeSeguimiento.class));
    final Actor a=new Actor(1,11,21,"AGENTE");final Instant corte=Instant.parse("2026-11-10T12:00:00Z");
    final InformesC7.Consulta q=new InformesC7.Consulta(Composicion.OPERACION_ACTUAL,null,null,null,null);
    MedicionInteraccion hecho(long version){return new MedicionInteraccion(71,version,21,LocalDate.of(2026,11,10),corte.minusSeconds(1),null,null,List.of(),List.of());}
    @BeforeEach void preparar(){when(contexto.ahora()).thenReturn(corte);when(contexto.hoy()).thenReturn(LocalDate.of(2026,11,10));when(alcances.de(a)).thenReturn(new Alcances.Alcance(1,false,List.of(21L)));
        when(derechos.exigir(Composicion.OPERACION_ACTUAL,a,corte)).thenReturn(new DerechosDeInformesC7.Derechos(PlanBrox.FREE,Set.of(Composicion.OPERACION_ACTUAL),false,null,0));
        when(interacciones.indicadores(a)).thenReturn(new LecturaIndicadores(List.of(hecho(2)),Set.of()));}
    @Test void unaFuenteRevocadaDespuesDelCalculoImpideLaEntrega(){doThrow(new AccesoNoAutorizadoException()).when(interacciones).exigirVersiones(List.of(new ReferenciaVersion(71,2)),a,FinalidadLectura.INDICADORES);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.preparar(q,a));}
    @Test void unaRectificacionConcurrenteNoPermiteCombinarVersiones(){when(interacciones.indicadores(a)).thenReturn(new LecturaIndicadores(List.of(hecho(2)),Set.of()),new LecturaIndicadores(List.of(hecho(3)),Set.of()));
        assertThrows(ConflictoException.class,()->servicio.preparar(q,a));}
    @Test void fuentesEstablesPermitenInformeFreeSinComprarleUnPlanSuperior(){var informe=servicio.preparar(q,a);assertEquals("FREE",informe.plan());assertEquals("PROPIO",informe.ambito());assertEquals(List.of(new ReferenciaVersion(71,2)),informe.fuentesInteracciones());}
    @Test void resumenRecurrenteConservaLaVentanaExpresaAunqueCruceElMes(){
        when(derechos.exigir(Composicion.RESUMEN_MENSUAL,a,corte)).thenReturn(new DerechosDeInformesC7.Derechos(PlanBrox.ONE_PRO,Set.of(Composicion.RESUMEN_MENSUAL),true,null,0));
        assertDoesNotThrow(()->servicio.validarRecurrencia(new InformesC7.Consulta(Composicion.RESUMEN_MENSUAL,null,null,null,null),14,a));
    }
}
