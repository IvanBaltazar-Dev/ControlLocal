package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.comercial.EvaluacionSolicitud;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.EvaluacionSolicitudRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.EvaluacionService.DatosEvaluacion;
import com.controllocal.service.EvaluacionService.FichaEvaluacion;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AccesoSolicitud;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Blinda los mensajes del cable de EvaluacionRest y las tres reglas que
 * sorprenden: el tipo se DERIVA del resultado (pero el request lo exige
 * presente), solo cabe una evaluacion FINAL por solicitud y el broker debe
 * supervisar al agente responsable.
 *
 * <p>Cubre tambien la mejora MEJ-01: la decision del broker mueve la
 * solicitud por Transiciones y deja fila en historial_estado.
 */
class EvaluacionServiceImplTest {

    private static final long ORG = 1L;
    private static final long SOLICITUD = 5L;

    private final EvaluacionSolicitudRepository evaluaciones = mock(EvaluacionSolicitudRepository.class);
    private final SolicitudAlquilerRepository solicitudes = mock(SolicitudAlquilerRepository.class);
    private final DetalleBrokerRepository brokers = mock(DetalleBrokerRepository.class);
    private final AccesoSolicitud acceso = mock(AccesoSolicitud.class);
    private final Alcances alcances = mock(Alcances.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);

    private final EvaluacionServiceImpl service = new EvaluacionServiceImpl(
            evaluaciones, solicitudes, brokers, acceso, alcances, new Transiciones(historial),
            mock(AlertaService.class));

    /** rsalas: broker que supervisa al rol de agente 30. */
    private final Actor broker = new Actor(ORG, 2L, 20L, "BROKER");
    private final Actor admin = new Actor(ORG, 1L, 10L, "TENANT_ADMIN");

    // ------------------------------------------------------------------
    // Mensajes del contrato (paridad v1)
    // ------------------------------------------------------------------

    @Test
    void sinDatosOSinSolicitudRespondeElMensajeV1() {
        ReglaNegocioException sinDatos = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(null, broker));
        assertEquals("Los datos de la evaluacion son obligatorios.", sinDatos.getMessage());

        ReglaNegocioException sinSolicitud = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosEvaluacion("F", "A", null, null), broker));
        assertEquals("Los datos de la evaluacion son obligatorios.", sinSolicitud.getMessage());
    }

    @Test
    void elTipoDeEvaluacionSeIgnoraComoVALORPeroElCableLoExigePRESENTE() {
        // Rareza del cable: el DTO v1 parsea tipoEvaluacion antes de que la BL
        // lo pise, asi que mandarlo vacio es un 400 aunque luego no importe.
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosEvaluacion(null, "A", null, SOLICITUD), broker));
        assertEquals("Valor invalido para tipo de evaluacion: null", error.getMessage());
        verifyNoInteractions(solicitudes);
    }

    @Test
    void conLosDosCamposMalGanaElMensajeDelTipo() {
        // Mismo orden que Dtos.EvaluacionRequest.aEntidad: tipo y luego resultado.
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosEvaluacion("Z", "Q", null, SOLICITUD), broker));
        assertEquals("Valor invalido para tipo de evaluacion: Z", error.getMessage());
    }

    @Test
    void unResultadoFueraDelVocabularioRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosEvaluacion("F", "Q", null, SOLICITUD), broker));
        assertEquals("Valor invalido para resultado de evaluacion: Q", error.getMessage());
    }

    @Test
    void unaSolicitudInexistenteRespondeElMensajeV1() {
        when(solicitudes.buscarFicha(ORG, SOLICITUD)).thenReturn(Optional.empty());

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosEvaluacion("F", "A", null, SOLICITUD), broker));
        assertEquals("Solicitud no encontrada para evaluacion.", error.getMessage());
    }

    @Test
    void unBrokerSinDetalleRespondeElMensajeV1() {
        when(solicitudes.buscarFicha(ORG, SOLICITUD)).thenReturn(Optional.of(solicitudEnRevision()));
        when(brokers.findById(20L)).thenReturn(Optional.empty());

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosEvaluacion("F", "A", null, SOLICITUD), broker));
        assertEquals("Broker responsable no encontrado.", error.getMessage());
    }

    @Test
    void elBrokerDebeSupervisarAlAgenteResponsable() {
        prepararEvaluacionValida();
        when(alcances.alcanza(broker, 30L)).thenReturn(false);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosEvaluacion("F", "A", null, SOLICITUD), broker));
        assertEquals("El broker no supervisa al agente responsable de esta solicitud.",
                error.getMessage());
        verifyNoInteractions(historial);
    }

    /**
     * D-S0-17 fila 13, la mas sensible de las 18: firmar una evaluacion es la
     * decision que desemboca en contrato y comision, y el gobierno dejo de
     * poder hacerlo. Antes tenia exencion de supervision; ahora no hay exencion
     * que dar, porque el gate ni siquiera le deja llegar (403).
     */
    @Test
    void elAdminYaNoTieneExencionDeSupervision() {
        prepararEvaluacionValida();
        when(alcances.alcanza(admin, 30L)).thenReturn(false);

        assertThrows(ReglaNegocioException.class, () -> service.registrar(
                new DatosEvaluacion("F", "A", null, SOLICITUD), admin));

        verifyNoInteractions(historial);
    }

    @Test
    void unResultadoConLaCajaCambiadaNoPasa() {
        // El cable compara el codigo EXACTO (CodigoEnum.fromCodigo), a
        // diferencia de los gates de comision, que si normalizan.
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosEvaluacion("F", "a", null, SOLICITUD), broker));
        assertEquals("Valor invalido para resultado de evaluacion: a", error.getMessage());
    }

    @Test
    void soloCabeUnaEvaluacionFinalPorSolicitud() {
        prepararEvaluacionValida();
        when(evaluaciones.existeFinalDe(ORG, SOLICITUD)).thenReturn(true);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosEvaluacion("F", "A", null, SOLICITUD), broker));
        assertEquals("Solo puede existir una evaluacion final por solicitud.", error.getMessage());
    }

    @Test
    void unaObservacionNoChocaConLaFinalYaExistente() {
        // La restriccion es sobre el tipo FINAL: observar tantas veces como
        // haga falta es justo el ciclo de subsanacion.
        prepararEvaluacionValida();
        when(evaluaciones.existeFinalDe(ORG, SOLICITUD)).thenReturn(true);

        assertEquals("O", service.registrar(
                new DatosEvaluacion("F", "O", "Falta el sustento", SOLICITUD), broker).tipoEvaluacion());
    }

    // ------------------------------------------------------------------
    // Tipo derivado + efecto sobre la solicitud
    // ------------------------------------------------------------------

    @Test
    void aprobarDerivaTipoFinalMueveLaSolicitudAAprobadaYAudita() {
        SolicitudAlquiler solicitud = prepararEvaluacionValida();

        FichaEvaluacion ficha = service.registrar(
                new DatosEvaluacion("P", "A", "Todo conforme", SOLICITUD), broker);

        // El tipo del request ("P" preliminar) se pisa con el derivado.
        assertEquals(EvaluacionSolicitud.FINAL, ficha.tipoEvaluacion());
        assertEquals("A", ficha.resultado());
        assertEquals(20L, ficha.idBroker());
        assertEquals("Rodrigo Salas", ficha.brokerNombre());
        assertNotNull(ficha.fechaEvaluacion());
        assertEquals(SolicitudAlquiler.APROBADA, solicitud.estadoActual());

        HistorialEstado evento = eventoAuditado();
        assertEquals("SOLICITUD_ALQUILER", evento.getEntidadTipo());
        assertEquals("E", evento.getEstadoAnterior());
        assertEquals("A", evento.getEstadoNuevo());
        assertEquals(2L, evento.getIdActor());
        assertEquals("BROKER", evento.getTipoRolActor());
        assertEquals("Solicitud aprobada por el broker. Todo conforme", evento.getMotivo());
    }

    @Test
    void rechazarDerivaTipoFinalYDejaLaSolicitudRechazada() {
        SolicitudAlquiler solicitud = prepararEvaluacionValida();

        FichaEvaluacion ficha = service.registrar(
                new DatosEvaluacion("F", "R", null, SOLICITUD), broker);

        assertEquals(EvaluacionSolicitud.FINAL, ficha.tipoEvaluacion());
        assertEquals(SolicitudAlquiler.RECHAZADA, solicitud.estadoActual());
    }

    @Test
    void observarDerivaTipoObservacionYDevuelveLaSolicitudAlAgente() {
        SolicitudAlquiler solicitud = prepararEvaluacionValida();

        FichaEvaluacion ficha = service.registrar(
                new DatosEvaluacion("F", "O", "Falta el sustento economico", SOLICITUD), broker);

        assertEquals(EvaluacionSolicitud.OBSERVACION, ficha.tipoEvaluacion());
        // OBSERVADA es justo el estado desde el que el agente puede reenviar.
        assertEquals(SolicitudAlquiler.OBSERVADA, solicitud.estadoActual());
        assertEquals("O", eventoAuditado().getEstadoNuevo());
    }

    // ------------------------------------------------------------------
    // Lectura: alcance del recurso
    // ------------------------------------------------------------------

    @Test
    void laEvaluacionDeOtroBrokerResponde404NoUn403() {
        // La v1 filtra su propia lista, asi que "no es tuya" y "no existe"
        // responden lo mismo.
        when(evaluaciones.buscarFicha(ORG, 9L, false, 20L)).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class, () -> service.obtener(9L, broker));
    }

    @Test
    void elHistorialDeUnaSolicitudPasaPorElAlcanceDeLaSolicitud() {
        // Este si lo ve el agente dueno: el alcance es el de la solicitud.
        when(acceso.conAcceso(SOLICITUD, broker)).thenReturn(solicitudEnRevision());
        when(evaluaciones.porSolicitud(ORG, SOLICITUD)).thenReturn(java.util.List.of());

        assertEquals(0, service.historialDeSolicitud(SOLICITUD, broker).size());
        verify(acceso).conAcceso(SOLICITUD, broker);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private SolicitudAlquiler prepararEvaluacionValida() {
        SolicitudAlquiler solicitud = solicitudEnRevision();
        when(solicitudes.buscarFicha(ORG, SOLICITUD)).thenReturn(Optional.of(solicitud));
        when(solicitudes.save(any(SolicitudAlquiler.class))).thenAnswer(inv -> inv.getArgument(0));
        when(brokers.findById(20L)).thenReturn(Optional.of(detalleBroker(20L, "Rodrigo Salas")));
        when(brokers.findById(10L)).thenReturn(Optional.of(detalleBroker(10L, "Administrador")));
        when(alcances.alcanza(broker, 30L)).thenReturn(true);
        when(evaluaciones.save(any(EvaluacionSolicitud.class))).thenAnswer(inv -> {
            EvaluacionSolicitud guardada = inv.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", 90L);
            return guardada;
        });
        return solicitud;
    }

    private static SolicitudAlquiler solicitudEnRevision() {
        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        solicitud.setOrganizacionId(ORG);
        solicitud.setCodigoSolicitud("SOL-260715103000");
        solicitud.setFechaRegistro(LocalDate.of(2026, 7, 15));
        solicitud.setMontoPropuesto(new BigDecimal("9000.00"));
        solicitud.setAgente(detalleAgente(30L, "Valentina Mora"));
        new Transiciones(mock(HistorialEstadoRepository.class))
                .iniciar(solicitud, SolicitudAlquiler.EN_REVISION);
        ReflectionTestUtils.setField(solicitud, "id", SOLICITUD);
        return solicitud;
    }

    private static DetalleBroker detalleBroker(long idRol, String nombre) {
        DetalleBroker broker = new DetalleBroker();
        broker.setOrganizacionId(ORG);
        broker.setRol(personaRol(nombre, TipoRol.BROKER));
        broker.setCodigoBroker("BRK-001");
        ReflectionTestUtils.setField(broker, "id", idRol);
        return broker;
    }

    private static DetalleAgente detalleAgente(long idRol, String nombre) {
        DetalleAgente detalle = new DetalleAgente();
        detalle.setRol(personaRol(nombre, TipoRol.AGENTE));
        detalle.setCodigoAgente("AGE-001");
        ReflectionTestUtils.setField(detalle, "id", idRol);
        return detalle;
    }

    private static PersonaRol personaRol(String nombre, TipoRol tipo) {
        Persona persona = new Persona();
        persona.setNombresORazonSocial(nombre);
        PersonaRol rol = new PersonaRol();
        rol.setPersona(persona);
        rol.setTipoRol(tipo);
        return rol;
    }

    private HistorialEstado eventoAuditado() {
        ArgumentCaptor<HistorialEstado> evento = ArgumentCaptor.forClass(HistorialEstado.class);
        verify(historial).save(evento.capture());
        return evento.getValue();
    }
}
