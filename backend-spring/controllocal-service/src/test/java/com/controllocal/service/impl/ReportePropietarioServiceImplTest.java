package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ReportePropietario;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.InteraccionComercialRepository;
import com.controllocal.persistence.repositorio.MotivoNoContinuidadRepository;
import com.controllocal.persistence.repositorio.ReportePropietarioRepository;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ReportePropietarioService.DatosReporte;
import com.controllocal.service.ReportePropietarioService.FichaReporte;
import com.controllocal.service.ReportePropietarioService.ResumenAvance;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Blinda E2: alcance por tenant/rol, resumen derivado, defaults y mensajes del
 * ReportesPropietarioRest v1. Los valores que envia el cliente para los tres
 * derivados se prueban expresamente como no autoritativos.
 */
class ReportePropietarioServiceImplTest {

    private static final long ORG = 1L;
    private static final long ID_CAPTACION = 9L;
    private static final long ID_AGENTE = 30L;

    private final ReportePropietarioRepository reportes =
            mock(ReportePropietarioRepository.class);
    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final InteraccionComercialRepository interacciones =
            mock(InteraccionComercialRepository.class);
    private final VisitaRepository visitas = mock(VisitaRepository.class);
    private final MotivoNoContinuidadRepository motivos =
            mock(MotivoNoContinuidadRepository.class);
    private final Alcances alcances = mock(Alcances.class);

    private final ReportePropietarioServiceImpl service =
            new ReportePropietarioServiceImpl(
                    reportes, captaciones, interacciones, visitas, motivos, alcances);

    private final Actor agente = new Actor(ORG, 3L, ID_AGENTE, "AGENTE");
    private final Actor otroAgente = new Actor(ORG, 4L, 31L, "AGENTE");
    private final Actor broker = new Actor(ORG, 2L, 23L, "BROKER");
    private final Actor admin = new Actor(ORG, 1L, 20L, "TENANT_ADMIN");

    @Test
    void listarFiltraPorTenantYCaptacionYMapeaLaFormaCongelada() {
        Captacion captacion = visiblePara(admin);
        ReportePropietario reporte = reporte(captacion, 77L);
        when(reportes.listarPorCaptacion(ORG, ID_CAPTACION)).thenReturn(List.of(reporte));

        List<FichaReporte> resultado = service.listar(ID_CAPTACION, admin);

        assertEquals(1, resultado.size());
        FichaReporte ficha = resultado.getFirst();
        assertEquals(77L, ficha.id());
        assertEquals(ID_CAPTACION, ficha.idCaptacion());
        assertEquals(ID_AGENTE, ficha.idAgente());
        assertEquals("E", ficha.canalEnvio());
        assertNotNull(ficha.fechaCreacion());
        verify(reportes).listarPorCaptacion(ORG, ID_CAPTACION);
    }

    @Test
    void unaCaptacionDeOtroTenantEs404AntesDeEvaluarElRol() {
        when(captaciones.buscarFicha(ORG, ID_CAPTACION)).thenReturn(Optional.empty());

        NoEncontradoException error = assertThrows(NoEncontradoException.class,
                () -> service.listar(ID_CAPTACION, admin));

        assertEquals("Captacion no encontrado.", error.getMessage());
        verifyNoInteractions(alcances, reportes);
    }

    @Test
    void unaCaptacionFueraDelEquipoEs403() {
        Captacion captacion = captacion();
        when(captaciones.buscarFicha(ORG, ID_CAPTACION)).thenReturn(Optional.of(captacion));
        when(alcances.alcanza(broker, ID_AGENTE)).thenReturn(false);

        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.preview(ID_CAPTACION, null, null, broker));

        verifyNoInteractions(interacciones, visitas, motivos);
    }

    @Test
    void previewUsaRangoInclusivoYTraduceLasObjecionesPorFrecuencia() {
        visiblePara(broker);
        LocalDate desde = LocalDate.of(2026, 7, 1);
        LocalDate hasta = LocalDate.of(2026, 7, 15);
        OffsetDateTime desdeInstante = inicio(desde);
        OffsetDateTime hastaExclusiva = inicio(hasta.plusDays(1));
        when(interacciones.contarParaReporte(
                ORG, ID_CAPTACION, desdeInstante, hastaExclusiva)).thenReturn(4L);
        when(visitas.contarRealizadasParaReporte(
                ORG, ID_CAPTACION, desde, hasta)).thenReturn(2L);
        when(motivos.contarParaReporte(
                ORG, ID_CAPTACION, desdeInstante, hastaExclusiva))
                .thenReturn(List.of(new Object[]{"P", 3L}, new Object[]{"U", 1L}));

        ResumenAvance resumen =
                service.preview(ID_CAPTACION, desde, hasta, broker);

        assertEquals(4, resumen.consultas());
        assertEquals(2, resumen.visitas());
        assertEquals("Precio (3), Ubicacion (1)", resumen.objeciones());
    }

    @Test
    void previewSinObjecionesDevuelveCadenaVacia() {
        visiblePara(agente);
        OffsetDateTime desde = inicio(LocalDate.of(1, 1, 1));
        OffsetDateTime hastaExclusiva = inicio(LocalDate.of(9999, 12, 31));
        when(interacciones.contarParaReporte(
                ORG, ID_CAPTACION, desde, hastaExclusiva))
                .thenReturn(0L);
        when(visitas.contarRealizadasParaReporte(
                ORG, ID_CAPTACION, LocalDate.of(1, 1, 1),
                LocalDate.of(9999, 12, 30)))
                .thenReturn(0L);
        when(motivos.contarParaReporte(
                ORG, ID_CAPTACION, desde, hastaExclusiva))
                .thenReturn(List.of());

        ResumenAvance resumen =
                service.preview(ID_CAPTACION, null, null, agente);

        assertEquals("", resumen.objeciones());
    }

    @Test
    void registrarSinCuerpoConservaElMensajeV1() {
        propia();

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(ID_CAPTACION, null, agente));

        assertEquals("Los datos del reporte son obligatorios.", error.getMessage());
        verifyNoInteractions(interacciones, visitas, motivos, reportes);
    }

    @Test
    void registrarValidaElPeriodoAntesDeConsultarLaActividad() {
        propia();
        DatosReporte datos = datos(
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 1), "E");

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(ID_CAPTACION, datos, agente));

        assertEquals("El fin del periodo no puede ser anterior al inicio.",
                error.getMessage());
        verifyNoInteractions(interacciones, visitas, motivos, reportes);
    }

    @Test
    void registrarRechazaUnCanalDesconocidoConElMensajeExacto() {
        propia();

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(ID_CAPTACION, datos(null, null, " X "), agente));

        assertEquals("Canal de envío no válido:  X ", error.getMessage());
        verifyNoInteractions(interacciones, visitas, motivos, reportes);
    }

    @Test
    void registrarIgnoraLosDerivadosDelClienteYAplicaEmailPorDefecto() {
        Captacion captacion = propia();
        OffsetDateTime desde = inicio(LocalDate.of(1, 1, 1));
        OffsetDateTime hastaExclusiva = inicio(LocalDate.of(9999, 12, 31));
        when(interacciones.contarParaReporte(
                ORG, ID_CAPTACION, desde, hastaExclusiva))
                .thenReturn(5L);
        when(visitas.contarRealizadasParaReporte(
                ORG, ID_CAPTACION, LocalDate.of(1, 1, 1),
                LocalDate.of(9999, 12, 30)))
                .thenReturn(2L);
        when(motivos.contarParaReporte(
                ORG, ID_CAPTACION, desde, hastaExclusiva))
                .thenReturn(List.<Object[]>of(new Object[]{"C", 2L}));
        when(reportes.saveAndFlush(any(ReportePropietario.class))).thenAnswer(invocacion -> {
            ReportePropietario guardado = invocacion.getArgument(0);
            ReflectionTestUtils.setField(guardado, "id", 88L);
            return guardado;
        });
        DatosReporte manipulados = new DatosReporte(
                null, null, -999, -888, "Texto del cliente",
                "Ajustar precio", " ");

        FichaReporte creado =
                service.registrar(ID_CAPTACION, manipulados, agente);

        assertEquals(88L, creado.id());
        assertEquals(5, creado.consultasReportadas());
        assertEquals(2, creado.visitasReportadas());
        assertEquals("Condiciones del contrato (2)", creado.objecionesFrecuentes());
        assertEquals("Ajustar precio", creado.ajustesRecomendados());
        assertEquals("E", creado.canalEnvio());
        assertEquals(captacion.getAgente().getId(), creado.idAgente());
        assertNotNull(creado.fechaCreacion());

        ArgumentCaptor<ReportePropietario> captor =
                ArgumentCaptor.forClass(ReportePropietario.class);
        verify(reportes).saveAndFlush(captor.capture());
        assertEquals(ORG, captor.getValue().getOrganizacionId());
    }

    @Test
    void elAgenteNoPuedeRegistrarSobreLaCaptacionDeOtroAgente() {
        when(captaciones.buscarFicha(ORG, ID_CAPTACION))
                .thenReturn(Optional.of(captacion()));

        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.registrar(
                        ID_CAPTACION, datos(null, null, "E"), otroAgente));

        verifyNoInteractions(interacciones, visitas, motivos, reportes);
    }

    @Test
    void niBrokerNiAdminPuedenSaltarElGateDelServiceParaRegistrar() {
        when(captaciones.buscarFicha(ORG, ID_CAPTACION))
                .thenReturn(Optional.of(captacion()));

        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.registrar(ID_CAPTACION, datos(null, null, "E"), broker));
        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.registrar(ID_CAPTACION, datos(null, null, "E"), admin));

        verifyNoInteractions(interacciones, visitas, motivos, reportes);
    }

    private Captacion visiblePara(Actor actor) {
        Captacion captacion = captacion();
        when(captaciones.buscarFicha(ORG, ID_CAPTACION))
                .thenReturn(Optional.of(captacion));
        when(alcances.alcanza(actor, ID_AGENTE)).thenReturn(true);
        return captacion;
    }

    private Captacion propia() {
        Captacion captacion = captacion();
        when(captaciones.buscarFicha(ORG, ID_CAPTACION))
                .thenReturn(Optional.of(captacion));
        return captacion;
    }

    private static Captacion captacion() {
        Captacion captacion = new Captacion();
        ReflectionTestUtils.setField(captacion, "id", ID_CAPTACION);
        DetalleAgente agente = new DetalleAgente();
        ReflectionTestUtils.setField(agente, "id", ID_AGENTE);
        captacion.setAgente(agente);
        captacion.setOrganizacionId(ORG);
        return captacion;
    }

    private static ReportePropietario reporte(Captacion captacion, long id) {
        ReportePropietario reporte = new ReportePropietario();
        ReflectionTestUtils.setField(reporte, "id", id);
        reporte.setOrganizacionId(ORG);
        reporte.setCaptacion(captacion);
        reporte.setAgente(captacion.getAgente());
        reporte.setFechaReporte(LocalDate.of(2026, 7, 29));
        reporte.setConsultasReportadas(4);
        reporte.setVisitasReportadas(2);
        reporte.setCanalEnvio("E");
        reporte.setFechaCreacion(OffsetDateTime.now());
        return reporte;
    }

    private static DatosReporte datos(LocalDate inicio, LocalDate fin, String canal) {
        return new DatosReporte(inicio, fin, 0, 0, null, null, canal);
    }

    private static OffsetDateTime inicio(LocalDate fecha) {
        return fecha.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
