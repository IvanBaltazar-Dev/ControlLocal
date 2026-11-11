package com.controllocal.service.impl;

import com.controllocal.service.*;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FocoServiceImplTest {
    private final IndicadorService indicadores = mock(IndicadorService.class);
    private final TareaService tareas = mock(TareaService.class);
    private final HallazgoService hallazgos = mock(HallazgoService.class);
    private final FocoDelBrokerService asuntos = mock(FocoDelBrokerService.class);
    private final com.controllocal.service.soporte.PendientesComercialesRadar actuaciones = mock(com.controllocal.service.soporte.PendientesComercialesRadar.class);
    private final FocoServiceImpl foco = new FocoServiceImpl(indicadores, tareas, hallazgos, asuntos, actuaciones);
    private final Actor agente = new Actor(1, 11, 21, Actor.AGENTE);
    private final Actor broker = new Actor(1, 12, 22, Actor.BROKER);

    @BeforeEach void preparar() {
        when(hallazgos.de(any())).thenReturn(List.of());
        when(asuntos.de(org.mockito.ArgumentMatchers.eq(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas()),any())).thenReturn(List.of());
        when(tareas.bandejaDe(org.mockito.ArgumentMatchers.eq(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas()),any())).thenReturn(List.of());
    }

    @Test void elBrokerRecibeSusAsuntosSinConsultarTrabajoPrivadoDelAgente() {
        var propio = mock(FocoDelBrokerService.AsuntoDelBroker.class);
        when(asuntos.de(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),broker)).thenReturn(List.of(propio));
        var resultado = foco.de(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),null, broker);
        assertTrue(resultado.bandeja().isEmpty());
        assertEquals(List.of(propio), resultado.asuntosDelBroker());
        verifyNoInteractions(tareas);
    }

    @Test void conservaElOrdenYElTotalDeLaBandejaSinAplicarLaPaginaVisible() {
        var primera = mock(TareaService.FichaTarea.class);
        var segunda = mock(TareaService.FichaTarea.class);
        when(tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente)).thenReturn(List.of(primera, segunda));
        assertEquals(List.of(primera, segunda), foco.de(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),null, agente).bandeja());
        verify(asuntos).de(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);
    }

    @Test void elFiltroDePeriodoDelDashboardSeConserva() {
        var resumen = mock(IndicadorService.Resumen.class);
        when(indicadores.resumen("2026-10", agente)).thenReturn(resumen);
        assertSame(resumen, foco.de(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),"2026-10", agente).indicadores());
        verify(indicadores).resumen("2026-10", agente);
    }

    @Test void laDenegacionNoSeConvierteEnUnaBandejaVacia() {
        when(asuntos.de(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),broker)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class, () -> foco.de(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),null, broker));
        verifyNoInteractions(tareas);
    }

    @Test void fuentesActualesSeEntreganSinReordenarYUnFalloNoSeOcultaComoVacio(){
        var a=mock(com.controllocal.service.soporte.PendientesComercialesRadar.Actuacion.class);
        when(actuaciones.de(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),broker)).thenReturn(List.of(a));
        assertEquals(List.of(a),foco.de(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),null,broker).actuaciones());
        when(actuaciones.de(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),broker)).thenThrow(new IllegalStateException("FUENTE_NO_DISPONIBLE"));
        assertThrows(IllegalStateException.class,()->foco.de(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),null,broker));
    }
}
