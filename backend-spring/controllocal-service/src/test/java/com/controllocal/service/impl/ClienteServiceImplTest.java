package com.controllocal.service.impl;

import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.persistence.repositorio.DetalleClienteRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ClienteService.FiltrosCliente;
import com.controllocal.service.ClienteService.ResumenClientes;
import com.controllocal.service.ClienteService.DatosCliente;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.Pagina;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Autorizaciones;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Guarda de la BANDEJA de clientes: los filtros aditivos y el KPI que la
 * pantalla Angular necesita para dejar de descargar la cartera y filtrar en
 * memoria. El resto de casos de uso de clientes (alta, edicion, baja, alcance
 * del broker) los cubre el E2E de F3.
 */
class ClienteServiceImplTest {

    private static final long ORG = 1L;
    private static final Actor AGENTE = new Actor(ORG, 10L, 20L, "AGENTE");
    private static final Actor BROKER = new Actor(ORG, 11L, 21L, "BROKER");

    private final DetalleClienteRepository clientes = mock(DetalleClienteRepository.class);
    private final PersonaRepository personas = mock(PersonaRepository.class);
    private final PersonaRolRepository roles = mock(PersonaRolRepository.class);
    private final OportunidadComercialRepository oportunidades = mock(OportunidadComercialRepository.class);
    private final Alcances alcances = mock(Alcances.class);
    // D-27: el alta exige autorizacion. Aqui va simulada para que estos tests
    // sigan comprobando lo suyo; la autorizacion tiene su propia suite.
    private final Autorizaciones autorizaciones = mock(Autorizaciones.class);

    private final ClienteServiceImpl service =
            new ClienteServiceImpl(clientes, personas, roles, oportunidades, alcances, autorizaciones);

    @Test void identidadParaFiltrarNoHidrataDatosPersonales(){
        when(clientes.personaContextual(ORG,7)).thenReturn(Optional.of(107L));
        assertEquals(Optional.of(107L),service.personaLegible(7,AGENTE));
        verify(clientes,never()).buscarFicha(anyLong(),anyLong());verifyNoInteractions(personas);
    }
    @Test void filtroFueraDelEquipoEsEmptyPeroDetalleDirectoConserva403SinHidratar(){
        when(clientes.personaContextual(ORG,7)).thenReturn(Optional.of(107L));
        when(alcances.de(BROKER)).thenReturn(new Alcances.Alcance(ORG,false,List.of(31L)));
        when(oportunidades.idsClienteDelEquipo(ORG,List.of(31L))).thenReturn(List.of(8L));
        assertTrue(service.personaLegible(7,BROKER).isEmpty());
        assertThrows(AccesoNoAutorizadoException.class,()->service.obtener(7,BROKER));
        verify(clientes,never()).buscarFicha(anyLong(),anyLong());
    }
    @Test void unFalloTecnicoDelFiltroNoEsUnaPoblacionVacia(){
        when(clientes.personaContextual(ORG,7)).thenThrow(new IllegalStateException("DB"));
        assertThrows(IllegalStateException.class,()->service.personaLegible(7,AGENTE));
    }

    @Test
    void gobiernoIdentidadYBandasFuturasNoObtienenCatalogoNiMutacionesComerciales() {
        var cliente = mock(DetalleCliente.class);
        when(clientes.buscarFicha(ORG, 7L)).thenReturn(Optional.of(cliente));
        when(clientes.personaContextual(ORG,7L)).thenReturn(Optional.of(107L));
        var filtros = new FiltrosCliente(null, null, null, null, 1, 10);
        var datos = new DatosCliente(null, null, null, null, null, null, null, null, null, null);
        for (String banda : new String[]{Actor.TENANT_ADMIN, "IDENTIDAD", "BROX_PLATFORM", "FUTURO"}) {
            Actor actor = new Actor(ORG, 11L, 21L, banda);
            assertThrows(AccesoNoAutorizadoException.class, () -> service.listar(1, 10, actor));
            assertThrows(AccesoNoAutorizadoException.class, () -> service.listar(filtros, actor));
            assertThrows(AccesoNoAutorizadoException.class, () -> service.resumen(filtros, actor));
            assertThrows(AccesoNoAutorizadoException.class, () -> service.obtener(7L, actor));
            assertThrows(AccesoNoAutorizadoException.class, () -> service.autorizacion(7L, actor));
            assertThrows(AccesoNoAutorizadoException.class, () -> service.registrar(null, actor));
            assertThrows(AccesoNoAutorizadoException.class, () -> service.actualizar(7L, datos, actor));
            assertThrows(AccesoNoAutorizadoException.class, () -> service.desactivar(7L, actor));
        }
        verifyNoInteractions(cliente, personas, roles, oportunidades, alcances, autorizaciones);
    }

    @Test
    void laFronteraDeTenantPrecedeALaNegativaDeGobiernoEnElDetalle() {
        Actor gobierno = new Actor(ORG, 11L, 21L, Actor.TENANT_ADMIN);
        assertThrows(NoEncontradoException.class, () -> service.obtener(7L, gobierno));
        verify(clientes).personaContextual(ORG,7L);
        verify(clientes,org.mockito.Mockito.never()).buscarFicha(anyLong(),anyLong());
    }

    /**
     * Los filtros bajan a SQL normalizados y la pagina se recorta EN LA BASE;
     * la ficha completa se pide solo para los ids de esa pagina.
     */
    @Test
    void losFiltrosBajanASqlYSoloSeCarganLasFichasDeLaPagina() {
        when(clientes.idsBandeja(anyLong(), any(), any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(7L));
        when(clientes.contarBandeja(anyLong(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(42L);
        when(clientes.fichasPorIds(anyLong(), any())).thenReturn(List.of());

        Pagina<?> pagina = service.listar(
                new FiltrosCliente("  retail  ", "j", "Cafeteria", "a", 3, 20), AGENTE);

        // Texto recortado, codigos en mayuscula, pagina 3 de 20 => offset 40.
        verify(clientes).idsBandeja(ORG, "retail", "J", "A", "Cafeteria", true, List.of(-1L), 20, 40);
        verify(clientes).fichasPorIds(ORG, List.of(7L));
        assertEquals(42, pagina.total());
    }

    /** El total sale del MISMO conjunto y con los MISMOS argumentos que la pagina. */
    @Test
    void elTotalCuentaElMismoConjuntoQuePagina() {
        when(clientes.idsBandeja(anyLong(), any(), any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(clientes.contarBandeja(anyLong(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(0L);

        service.listar(new FiltrosCliente("retail", null, null, "A", 1, 10), AGENTE);

        verify(clientes).contarBandeja(ORG, "retail", null, "A", null, true, List.of(-1L));
        // Sin candidatos no se pide ninguna ficha.
        verify(clientes, never()).fichasPorIds(anyLong(), any());
    }

    /** Un filtro en blanco es "sin filtro", no una busqueda de cadena vacia. */
    @Test
    void losFiltrosEnBlancoViajanComoNulos() {
        when(clientes.idsBandeja(anyLong(), any(), any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(clientes.contarBandeja(anyLong(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(0L);

        service.listar(new FiltrosCliente("   ", "", "  ", "", 1, 10), AGENTE);

        verify(clientes).idsBandeja(ORG, null, null, null, null, true, List.of(-1L), 10, 0);
    }

    /**
     * El BROKER es el unico rol acotado (catalogo compartido, contrato F3 §2):
     * su conjunto llega como ids y viaja DENTRO de la consulta, no se filtra
     * despues.
     */
    @Test
    void elBrokerConsultaAcotadoPorLosClientesDeSuEquipo() {
        when(alcances.de(BROKER)).thenReturn(new Alcances.Alcance(ORG, false, List.of(21L)));
        when(oportunidades.idsClienteDelEquipo(anyLong(), any())).thenReturn(List.of(5L, 9L));
        when(clientes.idsBandeja(anyLong(), any(), any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(clientes.contarBandeja(anyLong(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(0L);

        service.listar(new FiltrosCliente(null, null, null, null, 1, 10), BROKER);

        verify(clientes).idsBandeja(ORG, null, null, null, null, false, List.of(5L, 9L), 10, 0);
    }

    /** Un broker que no supervisa clientes no consulta: devuelve vacio y ya. */
    @Test
    void elBrokerSinEquipoNoLlegaAConsultar() {
        when(alcances.de(BROKER)).thenReturn(new Alcances.Alcance(ORG, false, List.of(21L)));
        when(oportunidades.idsClienteDelEquipo(anyLong(), any())).thenReturn(List.of());

        Pagina<?> pagina = service.listar(new FiltrosCliente(null, null, null, null, 1, 10), BROKER);

        assertEquals(0, pagina.total());
        assertTrue(pagina.items().isEmpty());
        verify(clientes, never()).idsBandeja(anyLong(), any(), any(), any(), any(), anyBoolean(), any(),
                anyInt(), anyInt());
    }

    /**
     * El resumen cuenta los cubos, no filtra por uno: el estado viaja NULO
     * aunque la lista lo lleve. Si no, los KPI no cuadrarian con la lista al
     * cambiar de estado.
     */
    @Test
    void elResumenNoFiltraPorEstadoPorqueEsUnoDeSusCubos() {
        // El KPI se arma ANTES: kpi() stubbea su propio mock y, dentro de un
        // thenReturn, Mockito lo leeria como stubbing anidado.
        var fila = kpi(10, 7, 6, 5);
        when(clientes.resumenBandeja(anyLong(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(fila);
        when(clientes.rubrosDisponibles(anyLong(), anyBoolean(), any())).thenReturn(List.of("Retail"));

        ResumenClientes resumen = service.resumen(
                new FiltrosCliente("retail", "N", null, "A", 1, 1), AGENTE);

        verify(clientes).resumenBandeja(ORG, "retail", "N", null, null, true, List.of(-1L));
        assertEquals(10, resumen.total());
        assertEquals(7, resumen.activos());
        assertEquals(3, resumen.inactivos(), "los inactivos son el resto, no una cuarta consulta");
        assertEquals(6, resumen.contactoAutorizado());
        assertEquals(5, resumen.usoDatoAutorizado());
        assertEquals(List.of("Retail"), resumen.rubros());
    }

    /** Sin conjunto no hay KPI que pedir. */
    @Test
    void elResumenDelBrokerSinEquipoEsCeroSinConsultar() {
        when(alcances.de(BROKER)).thenReturn(new Alcances.Alcance(ORG, false, List.of(21L)));
        when(oportunidades.idsClienteDelEquipo(anyLong(), any())).thenReturn(List.of());

        ResumenClientes resumen = service.resumen(
                new FiltrosCliente(null, null, null, null, 1, 1), BROKER);

        assertEquals(0, resumen.total());
        assertTrue(resumen.rubros().isEmpty());
        verify(clientes, never()).resumenBandeja(anyLong(), any(), any(), any(), any(), anyBoolean(), any());
        verifyNoInteractions(personas, roles);
    }

    private static com.controllocal.persistence.query.ResumenClientes kpi(
            long total, long activos, long contacto, long uso) {
        com.controllocal.persistence.query.ResumenClientes fila =
                mock(com.controllocal.persistence.query.ResumenClientes.class);
        when(fila.getTotal()).thenReturn(total);
        when(fila.getActivos()).thenReturn(activos);
        when(fila.getContactoAutorizado()).thenReturn(contacto);
        when(fila.getUsoDatoAutorizado()).thenReturn(uso);
        return fila;
    }
}
