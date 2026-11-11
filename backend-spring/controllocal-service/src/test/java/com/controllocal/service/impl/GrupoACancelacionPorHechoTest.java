package com.controllocal.service.impl;

import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comercial.Tarea;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.query.CandidatoTarea;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.actividad.SeguimientoDeProspeccion;
import com.controllocal.service.materialia.ResultadosMaterialIa;
import com.controllocal.service.soporte.InterpreteDeLaBandeja;
import com.controllocal.service.soporte.FuentesAsuntos;
import com.controllocal.service.soporte.AutoridadAsuntos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regresión acotada D6-10: reproduce el cuarto rojo de HTTP186 con la API de
 * producto previa a Grupo A. El repositorio simulado conserva filas, pero no
 * demuestra autorización, SQL, exclusión mutua ni concurrencia PostgreSQL.
 */
class GrupoACancelacionPorHechoTest {
    private static final long ORG = 1, AGENTE = 30, SOLICITUD = 5;
    private final Actor actor = new Actor(ORG, 3, AGENTE, "AGENTE");
    private final TareaRepository tareas = mock(TareaRepository.class);
    private final ContextosDeActividad contexto = mock(ContextosDeActividad.class);
    private final SeguimientoDeProspeccion seguimiento = mock(SeguimientoDeProspeccion.class);
    private final SolicitudAlquilerRepository solicitudes = mock(SolicitudAlquilerRepository.class);
    private final VisitaRepository visitas = mock(VisitaRepository.class);
    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final ContratoAlquilerRepository contratos = mock(ContratoAlquilerRepository.class);
    private final ReporteAvanceRepository reportes = mock(ReporteAvanceRepository.class);
    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final InterpreteDeLaBandeja interprete = mock(InterpreteDeLaBandeja.class);
    private final ResultadosMaterialIa material = mock(ResultadosMaterialIa.class);
    private final FuentesAsuntos fuentes=mock(FuentesAsuntos.class);
    private final AutoridadAsuntos autoridad=mock(AutoridadAsuntos.class);
    private final List<Tarea> tabla = new ArrayList<>();
    private final AtomicReference<String> estado = new AtomicReference<>(SolicitudAlquiler.OBSERVADA);
    private final DetalleAgente responsable = new DetalleAgente();
    private final TareaServiceImpl service = new TareaServiceImpl(tareas, contexto, solicitudes,
            visitas, captaciones, contratos, reportes, agentes, interprete, seguimiento, material,fuentes,autoridad);

    @BeforeEach void fuenteConUnaSolicitudObservada() {
        MontajeAsuntosC7.tareas(tareas,tabla,responsable,actor,solicitudes,fuentes,autoridad);
        responsable.setOrganizacionId(ORG);
        ReflectionTestUtils.setField(responsable, "id", AGENTE);
        when(contexto.hoy()).thenReturn(LocalDate.of(2026, 11, 4));
        when(contexto.ahora()).thenReturn(LocalDate.of(2026,11,4).atTime(12,0).atZone(java.time.ZoneId.of("America/Lima")).toInstant());
        when(seguimiento.necesidadesVencidas(actor)).thenReturn(List.of());
        when(seguimiento.necesidadesActuales(actor)).thenReturn(List.of());
        when(seguimiento.marcasActuales(actor)).thenReturn(Set.of());
        when(material.avisosDeCaducidad(actor)).thenReturn(List.of());
        when(contratos.conComisionListaParaCobro(ORG, AGENTE)).thenReturn(List.of());
        when(visitas.queExigenAccion(eq(ORG), eq(AGENTE))).thenReturn(List.of());
        when(captaciones.activasDelAgente(ORG, AGENTE)).thenReturn(List.of());
        when(captaciones.activasConLocalDisponible(ORG, AGENTE)).thenReturn(List.of());
        when(solicitudes.porEstadoDelAgente(eq(ORG), eq(AGENTE), anyString())).thenAnswer(inv -> {
            String solicitado = inv.getArgument(2);
            return Objects.equals(solicitado, estado.get())
                    ? List.of(new Candidato(SOLICITUD, "SOL-GRUPO-A", null,
                    SolicitudAlquiler.OBSERVADA.equals(solicitado)
                            ? "2026-11-03T14:00:00Z" : "2026-11-04T14:00:00Z"))
                    : List.of();
        });
        when(agentes.getReferenceById(AGENTE)).thenReturn(responsable);
        when(tareas.buscarFicha(eq(ORG), anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(1);
            return tabla.stream().filter(t -> Objects.equals(id, t.getId())).findFirst();
        });
        when(tareas.save(any(Tarea.class))).thenAnswer(inv -> {
            Tarea t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(100L + tabla.size());
                tabla.add(t);
            }
            return t;
        });
    }

    @Test void aprobarDespuesDeCancelarObservacionGeneraOtraOcurrenciaVinculada() {
        long cancelada = cancelarLaObservacionActual();
        estado.set(SolicitudAlquiler.APROBADA);

        var nueva = service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor);
        assertEquals(1, nueva.size(),
                "La aprobación es un hecho nuevo: la cancelación de subsanación no puede ocultarlo.");
        assertEquals(Tarea.SEGUIMIENTO, nueva.getFirst().tipo());
        assertNotEquals(cancelada, nueva.getFirst().id());
        var filaNueva = tabla.stream().filter(t -> t.getId().equals(nueva.getFirst().id())).findFirst().orElseThrow();
        assertEquals(cancelada, filaNueva.getIdAntecedente());
        assertEquals(Tarea.CANCELADA, tabla.getFirst().getEstado());
        assertEquals(1, service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).size(), "Releer la aprobación no duplica.");
        assertEquals(2, tabla.size(), "La cancelación original permanece en la historia.");
    }

    @Test void releerLaMismaObservacionCanceladaNoLaReabre() {
        long cancelada = cancelarLaObservacionActual();
        assertTrue(service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).isEmpty());
        assertEquals(1, tabla.size());
        assertEquals(cancelada, tabla.getFirst().getId());
        assertEquals(Tarea.CANCELADA, tabla.getFirst().getEstado());
    }

    private long cancelarLaObservacionActual() {
        var observacion = service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor);
        assertEquals(1, observacion.size());
        assertEquals(Tarea.SUBIR_DOCUMENTOS, observacion.getFirst().tipo());
        long id = observacion.getFirst().id();
        service.cancelar(id, actor);
        assertTrue(service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).isEmpty());
        return id;
    }

    private record Candidato(Long entidadId, String entidadCodigo, LocalDate fechaPlazo, String marca)
            implements CandidatoTarea {
        public java.time.LocalTime getHoraPlazo(){return null;}
        public java.time.OffsetDateTime getDetectadoEn(){return null;}
        public Long getEntidadId() { return entidadId; }
        public String getEntidadCodigo() { return entidadCodigo; }
        public LocalDate getFechaPlazo() { return fechaPlazo; }
        public String getMarca() { return marca; }
    }
}
