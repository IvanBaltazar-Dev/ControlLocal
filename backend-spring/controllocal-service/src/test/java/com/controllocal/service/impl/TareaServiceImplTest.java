package com.controllocal.service.impl;

import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comercial.Tarea;
import com.controllocal.domain.comercial.Visita;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.CandidatoTarea;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.ReportePropietarioRepository;
import com.controllocal.persistence.repositorio.RequerimientoClienteRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.TareaRepository;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.TareaService.FichaTarea;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.LectorPorAutoridad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Blinda el motor de la bandeja (F7): deriva, reconcilia, enriquece, ordena y
 * corta. Es el service que mas pedia estos tests —el E2E lo cubre de punta a
 * punta, pero no regla por regla—.
 *
 * <p>Las <b>cinco trampas</b> del §5 del contrato, todas fijadas aqui:
 * <ol>
 *   <li>lo que bloquea la creacion no es solo lo abierto: una <b>CANCELADA
 *       bloquea para siempre</b> — cancelar mata, no pospone;</li>
 *   <li>solo se auto-resuelven las {@code entidad_tipo} de {@code ENTIDADES_AUTO};
 *       una tarea sobre cualquier otra entidad no se cierra sola nunca;</li>
 *   <li>la deduplicacion es <b>por ENTIDAD</b>, no por tipo de tarea: dos
 *       disparadores sobre la misma solicitud dan UNA tarea;</li>
 *   <li>{@code diasSinAccion} se cuenta desde el <b>plazo real de la entidad</b>,
 *       no desde que se creo la tarea (con la fecha de la tarea daria 0);</li>
 *   <li>la bandeja corta en <b>10</b> y descarta el resto <b>en silencio</b>
 *       (D-F7-2, bug congelado).</li>
 * </ol>
 *
 * <p>Y la regla de alcance que la hace unica: la bandeja es <b>estrictamente
 * personal del AGENTE</b>; es el unico recurso del sistema donde el ADMIN no
 * entra.
 */
class TareaServiceImplTest {

    private static final long ORG = 1L;
    private static final long ROL_AGENTE = 30L;

    private final TareaRepository tareas = mock(TareaRepository.class);
    private final ProspeccionRepository prospecciones = mock(ProspeccionRepository.class);
    private final SolicitudAlquilerRepository solicitudes = mock(SolicitudAlquilerRepository.class);
    private final VisitaRepository visitas = mock(VisitaRepository.class);
    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final ContratoAlquilerRepository contratos = mock(ContratoAlquilerRepository.class);
    private final ReportePropietarioRepository reportes = mock(ReportePropietarioRepository.class);
    private final RequerimientoClienteRepository requerimientos = mock(RequerimientoClienteRepository.class);
    private final OportunidadComercialRepository oportunidades = mock(OportunidadComercialRepository.class);
    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final LectorPorAutoridad lector = mock(LectorPorAutoridad.class);

    private final TareaServiceImpl service = new TareaServiceImpl(tareas, prospecciones, solicitudes,
            visitas, captaciones, contratos, reportes, requerimientos, oportunidades, agentes,
            lector);

    private final Actor agente = new Actor(ORG, 3L, ROL_AGENTE, "AGENTE");
    private final Actor broker = new Actor(ORG, 2L, 20L, "BROKER");
    private final Actor admin = new Actor(ORG, 1L, 10L, "TENANT_ADMIN");

    /** Lo que el reconcile ha ido guardando; se relee como si fuera la tabla. */
    private final List<Tarea> tabla = new ArrayList<>();

    @BeforeEach
    void sinDisparadores() {
        // Todos los disparadores en vacio: cada test enciende solo el suyo.
        when(prospecciones.paraRecontactar(anyLong(), anyLong(), any())).thenReturn(List.of());
        when(solicitudes.porEstadoDelAgente(anyLong(), anyLong(), anyString())).thenReturn(List.of());
        when(contratos.conComisionListaParaCobro(anyLong(), anyLong())).thenReturn(List.of());
        when(visitas.queExigenAccion(anyLong(), anyLong(), any())).thenReturn(List.of());
        when(captaciones.activasDelAgente(anyLong(), anyLong())).thenReturn(List.of());
        when(captaciones.activasConLocalDisponible(anyLong(), anyLong())).thenReturn(List.of());
        when(agentes.getReferenceById(ROL_AGENTE)).thenReturn(detalleAgente());

        when(tareas.porAgente(ORG, ROL_AGENTE)).thenReturn(tabla);
        when(tareas.save(any(Tarea.class))).thenAnswer(inv -> {
            Tarea guardada = inv.getArgument(0);
            if (guardada.getId() == null) {
                guardada.setId((long) (tabla.size() + 100));
                tabla.add(guardada);
            }
            return guardada;
        });
    }

    // ------------------------------------------------------------------
    // Alcance: el unico recurso sin ADMIN
    // ------------------------------------------------------------------

    @Test
    void laBandejaEsEstrictamentePersonalDelAGENTENiBrokerNiAdminEntran() {
        assertThrows(AccesoNoAutorizadoException.class, () -> service.bandejaDe(broker));
        assertThrows(AccesoNoAutorizadoException.class, () -> service.bandejaDe(admin));
    }

    // ------------------------------------------------------------------
    // Derivacion y reconcile
    // ------------------------------------------------------------------

    @Test
    void unDisparadorVigenteCreaSuTareaYLaDevuelveEnLaBandeja() {
        when(prospecciones.paraRecontactar(anyLong(), anyLong(), any()))
                .thenReturn(List.of(candidato(11L, "PRO-0001", LocalDate.now().minusDays(9), null)));

        List<FichaTarea> bandeja = service.bandejaDe(agente);

        assertEquals(1, bandeja.size());
        FichaTarea tarea = bandeja.get(0);
        assertEquals(Tarea.RECONTACTO, tarea.tipo());
        assertEquals("PROSPECCION", tarea.entidadTipo());
        assertEquals("PRO-0001", tarea.entidadCodigo());
        assertEquals(Tarea.ALTA, tarea.prioridad());
        assertEquals("prospeccion-detail/11", tarea.rutaResolver());
        assertEquals("Recontacta o evalua descartar la prospeccion PRO-0001.", tarea.descripcion());
    }

    @Test
    void unSegundoPaseNoDuplicaLaTareaDeLaMismaEntidad() {
        when(prospecciones.paraRecontactar(anyLong(), anyLong(), any()))
                .thenReturn(List.of(candidato(11L, "PRO-0001", LocalDate.now().minusDays(9), null)));

        service.bandejaDe(agente);
        service.bandejaDe(agente);

        assertEquals(1, tabla.size());
    }

    @Test
    void laDeduplicacionEsPorENTIDADNoPorTipoDeTarea() {
        // Trampa 3: la clave del reconcile es (entidadTipo, entidadId) — el
        // TIPO de tarea no entra. Una SEGUIMIENTO abierta sobre la solicitud 5
        // bloquea la SUBIR_DOCUMENTOS que el disparador 4 justificaria ahora.
        tabla.add(tareaExistente("SOLICITUD_ALQUILER", 5L, Tarea.PENDIENTE));
        when(solicitudes.porEstadoDelAgente(ORG, ROL_AGENTE, SolicitudAlquiler.OBSERVADA))
                .thenReturn(List.of(candidato(5L, "SOL-0001", LocalDate.now().minusDays(2), null)));

        List<FichaTarea> bandeja = service.bandejaDe(agente);

        assertEquals(1, bandeja.size());
        assertEquals(Tarea.SEGUIMIENTO, bandeja.get(0).tipo());
        assertEquals(1, tabla.size());
        verify(tareas, never()).save(any(Tarea.class));
    }

    @Test
    void dosDisparadoresSobreLaMISMAEntidadEnUNPaseSiCreanDosFilas() {
        // Cara B de la trampa 3, y conviene saberla: `bloqueadas` se calcula
        // ANTES del bucle de alta y no se realimenta, asi que la dedup protege
        // entre lecturas, no dentro de una. Hoy es inalcanzable —los siete
        // disparadores no comparten entidad: una solicitud no puede estar
        // APROBADA y OBSERVADA a la vez— pero deja de serlo en cuanto alguien
        // anada un octavo disparador sobre una entidad ya cubierta.
        CandidatoTarea solicitud = candidato(5L, "SOL-0001", LocalDate.now().minusDays(2), null);
        when(solicitudes.porEstadoDelAgente(ORG, ROL_AGENTE, SolicitudAlquiler.APROBADA))
                .thenReturn(List.of(solicitud));
        when(solicitudes.porEstadoDelAgente(ORG, ROL_AGENTE, SolicitudAlquiler.OBSERVADA))
                .thenReturn(List.of(solicitud));

        assertEquals(2, service.bandejaDe(agente).size());
    }

    @Test
    void cuandoElDisparadorDesapareceLaTareaSeDaPorHECHA() {
        when(prospecciones.paraRecontactar(anyLong(), anyLong(), any()))
                .thenReturn(List.of(candidato(11L, "PRO-0001", LocalDate.now().minusDays(9), null)));
        service.bandejaDe(agente);

        // El agente recontacto: el disparador ya no aplica.
        when(prospecciones.paraRecontactar(anyLong(), anyLong(), any())).thenReturn(List.of());
        List<FichaTarea> bandeja = service.bandejaDe(agente);

        assertTrue(bandeja.isEmpty());
        assertEquals(Tarea.COMPLETADA, tabla.get(0).getEstado());
    }

    @Test
    void unaTareaSobreUnaEntidadFUERADeEntidadesAutoNoSeCierraSolaNUNCA() {
        // Trampa 2: OPORTUNIDAD no esta en ENTIDADES_AUTO. Aunque no haya
        // disparador que la justifique, sigue abierta en la bandeja.
        tabla.add(tareaExistente("OPORTUNIDAD", 7L, Tarea.PENDIENTE));

        List<FichaTarea> bandeja = service.bandejaDe(agente);

        assertEquals(1, bandeja.size());
        assertEquals(Tarea.PENDIENTE, tabla.get(0).getEstado());
    }

    @Test
    void cancelarMATAlaTareaDeEsaEntidadParaSIEMPRE() {
        // Trampa 1: cancelar NO pospone. Aunque el disparador siga vigente, el
        // reconcile no vuelve a crearla.
        tabla.add(tareaExistente("PROSPECCION", 11L, Tarea.CANCELADA));
        when(prospecciones.paraRecontactar(anyLong(), anyLong(), any()))
                .thenReturn(List.of(candidato(11L, "PRO-0001", LocalDate.now().minusDays(9), null)));

        List<FichaTarea> bandeja = service.bandejaDe(agente);

        assertTrue(bandeja.isEmpty());
        assertEquals(1, tabla.size());
        verify(tareas, never()).save(any(Tarea.class));
    }

    // ------------------------------------------------------------------
    // Enriquecimiento
    // ------------------------------------------------------------------

    @Test
    void diasSinAccionSeCuentaDesdeElPlazoDeLaENTIDADNoDesdeLaTarea() {
        // Trampa 4: es el error mas facil al portar. La tarea nace hoy; si el
        // contador saliera de ella daria 0 en vez de 9.
        when(prospecciones.paraRecontactar(anyLong(), anyLong(), any()))
                .thenReturn(List.of(candidato(11L, "PRO-0001", LocalDate.now().minusDays(9), null)));

        assertEquals(9, service.bandejaDe(agente).get(0).diasSinAccion());
    }

    @Test
    void unPlazoEnElFuturoNoDaDiasNegativos() {
        when(visitas.queExigenAccion(anyLong(), anyLong(), any()))
                .thenReturn(List.of(candidato(21L, "", LocalDate.now().plusDays(2), Visita.PROGRAMADA)));

        assertEquals(0, service.bandejaDe(agente).get(0).diasSinAccion());
    }

    @Test
    void unaVisitaProximaEsMEDIAYUnaVencidaOCaidaEsALTA() {
        when(visitas.queExigenAccion(anyLong(), anyLong(), any())).thenReturn(List.of(
                candidato(21L, "", LocalDate.now().plusDays(2), Visita.PROGRAMADA),
                candidato(22L, "", LocalDate.now().minusDays(1), Visita.PROGRAMADA),
                candidato(23L, "", LocalDate.now().plusDays(1), Visita.NO_REALIZADA)));

        List<FichaTarea> bandeja = service.bandejaDe(agente);

        assertEquals(Tarea.ALTA, buscar(bandeja, 22L).prioridad());
        assertEquals(Tarea.ALTA, buscar(bandeja, 23L).prioridad());
        assertEquals("Visita no realizada: reprograma o descarta.", buscar(bandeja, 23L).descripcion());
        assertEquals(Tarea.MEDIA, buscar(bandeja, 21L).prioridad());
        assertEquals("visitas?focus=21", buscar(bandeja, 21L).rutaResolver());
    }

    @Test
    void laSolicitudEnrutaPorCODIGOYLaProspeccionPorID() {
        when(solicitudes.porEstadoDelAgente(ORG, ROL_AGENTE, SolicitudAlquiler.APROBADA))
                .thenReturn(List.of(candidato(5L, "SOL-260715103000", null, null)));

        assertEquals("solicitud-detail/SOL-260715103000",
                service.bandejaDe(agente).get(0).rutaResolver());
    }

    @Test
    void sinCodigoLaSolicitudCaeAlIdYLaCaptacionASuLista() {
        when(solicitudes.porEstadoDelAgente(ORG, ROL_AGENTE, SolicitudAlquiler.APROBADA))
                .thenReturn(List.of(candidato(5L, "", null, null)));
        when(captaciones.activasDelAgente(anyLong(), anyLong()))
                .thenReturn(List.of(candidato(9L, "", LocalDate.now().minusDays(30), null)));
        when(reportes.ultimoPorCaptaciones(anyLong(), anyCollection())).thenReturn(List.of());

        List<FichaTarea> bandeja = service.bandejaDe(agente);

        assertEquals("solicitud-detail/5", buscar(bandeja, 5L).rutaResolver());
        assertEquals("captaciones", buscar(bandeja, 9L).rutaResolver());
    }

    // ------------------------------------------------------------------
    // Orden y corte
    // ------------------------------------------------------------------

    @Test
    void ordenaALTAPrimeroYAIgualPrioridadLoMasRezagado() {
        when(visitas.queExigenAccion(anyLong(), anyLong(), any())).thenReturn(List.of(
                candidato(21L, "", LocalDate.now().plusDays(2), Visita.PROGRAMADA)));   // MEDIA
        when(prospecciones.paraRecontactar(anyLong(), anyLong(), any())).thenReturn(List.of(
                candidato(11L, "PRO-0001", LocalDate.now().minusDays(3), null),          // ALTA,  3 dias
                candidato(12L, "PRO-0002", LocalDate.now().minusDays(20), null)));       // ALTA, 20 dias

        List<FichaTarea> bandeja = service.bandejaDe(agente);

        assertEquals(12L, bandeja.get(0).entidadId());
        assertEquals(11L, bandeja.get(1).entidadId());
        assertEquals(21L, bandeja.get(2).entidadId());
    }

    @Test
    void laBandejaDevuelveTODASlasTareasAbiertas() {
        // Descongelado 2026-08-08 (era D-F7-2). La v1 cortaba en 10 y descartaba
        // el resto en silencio: sin total ni marca de truncado, el agente veia
        // la misma bandeja con 10 tareas que con 40, y las 30 que faltaban no
        // aparecian en ninguna parte. Las tareas se creaban igual, asi que lo
        // que se perdia no era trabajo: era saber que existia.
        List<CandidatoTarea> muchas = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            muchas.add(candidato((long) i, "PRO-" + i, LocalDate.now().minusDays(8 + i), null));
        }
        when(prospecciones.paraRecontactar(anyLong(), anyLong(), any())).thenReturn(muchas);

        List<FichaTarea> bandeja = service.bandejaDe(agente);

        assertEquals(40, bandeja.size(), "ninguna se descarta");
        assertEquals(40, tabla.size(), "y siguen siendo las mismas que se crearon");
        // El orden ya NO lo decide "la mas rezagada primero".
        //
        // Antes ganaba la de 48 dias sin accion, simplemente por ser la mas
        // vieja. La politica de despacho (E2.2) topa la antiguedad en 12 dias a
        // proposito: pasado el tope, esperar mas no suma, porque si no lo mas
        // viejo copa el foco para siempre y nada nuevo entra nunca.
        //
        // Aqui las 40 estan vencidas y 37 de ellas superan el tope, asi que
        // empatan a peso. Con empate manda el criterio 6 -- estabilidad -- y sin
        // orden previo desempata el id, que es determinista: gana la primera que
        // se creo de entre las empatadas (i = 4, la primera con 12 dias).
        assertEquals(4L, bandeja.get(0).entidadId(),
                "con la antiguedad topada, la mas vieja deja de ganar por serlo");
        assertEquals(bandeja.stream().map(FichaTarea::entidadId).toList(),
                service.bandejaDe(agente).stream().map(FichaTarea::entidadId).toList(),
                "y dos llamadas seguidas con los mismos datos devuelven el mismo orden");
    }

    // ------------------------------------------------------------------
    // Cancelar y efecto 7 de la cascada de F4
    // ------------------------------------------------------------------

    @Test
    void cancelarUnaTareaAjenaOInexistenteRompeConElMensajeDelCable() {
        when(tareas.buscarFicha(ORG, 77L)).thenReturn(Optional.empty());
        assertEquals("Tarea no encontrada.",
                assertThrows(ReglaNegocioException.class, () -> service.cancelar(77L, agente))
                        .getMessage());

        Tarea deOtro = tareaExistente("PROSPECCION", 11L, Tarea.PENDIENTE);
        ReflectionTestUtils.setField(deOtro.getAgente(), "id", 99L);
        when(tareas.buscarFicha(ORG, 77L)).thenReturn(Optional.of(deOtro));
        assertEquals("La tarea no pertenece al agente.",
                assertThrows(ReglaNegocioException.class, () -> service.cancelar(77L, agente))
                        .getMessage());
    }

    @Test
    void cancelarDejaLaTareaEnCANCELADA() {
        Tarea propia = tareaExistente("PROSPECCION", 11L, Tarea.PENDIENTE);
        when(tareas.buscarFicha(ORG, 77L)).thenReturn(Optional.of(propia));

        service.cancelar(77L, agente);

        assertEquals(Tarea.CANCELADA, propia.getEstado());
        verify(tareas).save(propia);
    }

    @Test
    void resolverDeEntidadCierraTodasLasAbiertasDeEsaEntidad() {
        // Efecto 7 de la cascada de F4: el contrato da por hechas las tareas de
        // oportunidad, solicitud, captacion y local.
        Tarea una = tareaExistente("SOLICITUD_ALQUILER", 5L, Tarea.PENDIENTE);
        Tarea otra = tareaExistente("SOLICITUD_ALQUILER", 5L, Tarea.EN_PROCESO);
        when(tareas.abiertasDeEntidad(ORG, "SOLICITUD_ALQUILER", 5L)).thenReturn(List.of(una, otra));

        service.resolverDeEntidad("SOLICITUD_ALQUILER", 5L, agente);

        assertEquals(Tarea.COMPLETADA, una.getEstado());
        assertEquals(Tarea.COMPLETADA, otra.getEstado());
        assertNotNull(una.getFechaCompletada());
    }

    @Test
    void resolverDeEntidadConDatosVaciosNoHaceNada() {
        service.resolverDeEntidad(null, 5L, agente);
        service.resolverDeEntidad("SOLICITUD_ALQUILER", null, agente);
        service.resolverDeEntidad("SOLICITUD_ALQUILER", 0L, agente);

        verify(tareas, never()).abiertasDeEntidad(anyLong(), anyString(), anyLong());
    }

    // ------------------------------------------------------------------
    // Disparador 6: reporte periodico al propietario
    // ------------------------------------------------------------------

    @Test
    void elReporteAlPropietarioSePideCada15DiasContadosDesdeElUltimo() {
        when(captaciones.activasDelAgente(anyLong(), anyLong())).thenReturn(List.of(
                candidato(9L, "CAP-0001", LocalDate.now().minusDays(60), null),   // reporte viejo
                candidato(8L, "CAP-0002", LocalDate.now().minusDays(60), null))); // reporte reciente
        when(reportes.ultimoPorCaptaciones(anyLong(), anyCollection())).thenReturn(List.of(
                new Object[]{9L, LocalDate.now().minusDays(20)},
                new Object[]{8L, LocalDate.now().minusDays(3)}));

        List<FichaTarea> bandeja = service.bandejaDe(agente);

        assertEquals(1, bandeja.size());
        assertEquals(9L, bandeja.get(0).entidadId());
        assertEquals(Tarea.REPORTE_PROPIETARIO, bandeja.get(0).tipo());
        assertEquals("Reporta avances al propietario de la captacion CAP-0001.",
                bandeja.get(0).descripcion());
    }

    @Test
    void sinReportePrevioElRelojArrancaEnLaFechaDeCaptacion() {
        when(captaciones.activasDelAgente(anyLong(), anyLong())).thenReturn(List.of(
                candidato(9L, "CAP-0001", LocalDate.now().minusDays(30), null),
                candidato(8L, "CAP-0002", LocalDate.now().minusDays(2), null)));
        when(reportes.ultimoPorCaptaciones(anyLong(), anyCollection())).thenReturn(List.of());

        List<FichaTarea> bandeja = service.bandejaDe(agente);

        assertEquals(1, bandeja.size());
        assertEquals(9L, bandeja.get(0).entidadId());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static FichaTarea buscar(List<FichaTarea> bandeja, long entidadId) {
        return bandeja.stream()
                .filter(t -> t.entidadId() == entidadId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("sin tarea para la entidad " + entidadId));
    }

    private Tarea tareaExistente(String entidadTipo, long entidadId, String estado) {
        Tarea tarea = new Tarea();
        tarea.setOrganizacionId(ORG);
        tarea.setTipo(Tarea.SEGUIMIENTO);
        tarea.setEntidadTipo(entidadTipo);
        tarea.setEntidadId(entidadId);
        tarea.setAgente(detalleAgente());
        tarea.setDescripcion("descripcion previa");
        tarea.setFechaProgramada(OffsetDateTime.now());
        tarea.nacer(Tarea.MEDIA);
        tarea.setEstado(estado);
        tarea.setId(77L);
        return tarea;
    }

    /**
     * Proyeccion de disparador. Implementacion real y no un mock a proposito:
     * estos candidatos se construyen DENTRO de un {@code when(...)}, y un mock
     * anidado ahi deja a Mockito con un stubbing a medias.
     */
    private record Candidato(Long entidadId, String entidadCodigo, LocalDate fechaPlazo, String marca)
            implements CandidatoTarea {

        @Override
        public Long getEntidadId() {
            return entidadId;
        }

        @Override
        public String getEntidadCodigo() {
            return entidadCodigo;
        }

        @Override
        public LocalDate getFechaPlazo() {
            return fechaPlazo;
        }

        @Override
        public String getMarca() {
            return marca;
        }
    }

    private static CandidatoTarea candidato(Long id, String codigo, LocalDate plazo, String marca) {
        return new Candidato(id, codigo, plazo, marca);
    }

    private static DetalleAgente detalleAgente() {
        Persona persona = new Persona();
        persona.setNombresORazonSocial("Valentina Mora");
        PersonaRol rol = new PersonaRol();
        rol.setPersona(persona);
        rol.setTipoRol(TipoRol.AGENTE);

        DetalleAgente detalle = new DetalleAgente();
        detalle.setOrganizacionId(ORG);
        detalle.setRol(rol);
        detalle.setCodigoAgente("AGE-001");
        ReflectionTestUtils.setField(detalle, "id", ROL_AGENTE);
        return detalle;
    }
}
