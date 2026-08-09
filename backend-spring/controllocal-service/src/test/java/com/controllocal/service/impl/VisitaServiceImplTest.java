package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.MotivoNoContinuidad;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.Visita;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.PlanDeConsulta;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.persistence.repositorio.MotivoNoContinuidadRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.VisitaService.DatosVisita;
import com.controllocal.service.VisitaService.DesenlaceVisita;
import com.controllocal.service.VisitaService.FichaVisita;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Blinda la maquina de la visita y sus mensajes. Ojo con cuales: el cable
 * responde con la guarda del BL ("No se puede {accion} una visita {estado}."),
 * no con la del modelo, porque el BL corta antes.
 *
 * <p>Cubre tambien el efecto lateral menos obvio del contrato: un desenlace de
 * NO CONTINUIDAD cierra la oportunidad en la misma transaccion.
 */
class VisitaServiceImplTest {

    private static final long ORG = 1L;

    private final VisitaRepository visitas = mock(VisitaRepository.class);
    private final OportunidadComercialRepository oportunidades = mock(OportunidadComercialRepository.class);
    private final MotivoNoContinuidadRepository motivos = mock(MotivoNoContinuidadRepository.class);
    private final Alcances alcances = mock(Alcances.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);

    private final VisitaServiceImpl service = new VisitaServiceImpl(
            visitas, oportunidades, motivos, alcances, new Transiciones(historial), new PlanDeConsulta());

    private final Actor agente = new Actor(ORG, 3L, 30L, "AGENTE");
    private final Actor broker = new Actor(ORG, 2L, 20L, "BROKER");

    // ------------------------------------------------------------------
    // Alta
    // ------------------------------------------------------------------

    @Test
    void programarSinDatosRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.programar(null, agente));
        assertEquals("Los datos de la visita son obligatorios.", error.getMessage());
    }

    @Test
    void elAltaExigeQueLaOportunidadSeaDelPropioAgente() {
        // Sin alcance de broker: la comparacion es directa con el rol del actor.
        OportunidadComercial ajena = oportunidad(99L);
        when(oportunidades.buscarFicha(ORG, 8L)).thenReturn(Optional.of(ajena));

        assertThrows(AccesoNoAutorizadoException.class, () -> service.programar(
                new DatosVisita(8L, LocalDate.now(), LocalTime.of(16, 0), null), agente));
    }

    @Test
    void programarSinFechaUHoraRespondeElMensajeV1() {
        when(oportunidades.buscarFicha(ORG, 8L)).thenReturn(Optional.of(oportunidad(30L)));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.programar(new DatosVisita(8L, LocalDate.now(), null, null), agente));
        assertEquals("La visita debe tener fecha, hora y estado.", error.getMessage());
    }

    @Test
    void noSeAgendaSobreUnaOportunidadCerrada() {
        OportunidadComercial cerrada = oportunidad(30L);
        new Transiciones(mock(HistorialEstadoRepository.class))
                .aplicar(cerrada, 8L, OportunidadComercial.NO_CONTINUA, null, null);
        when(oportunidades.buscarFicha(ORG, 8L)).thenReturn(Optional.of(cerrada));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> service.programar(
                new DatosVisita(8L, LocalDate.now(), LocalTime.of(16, 0), null), agente));
        assertEquals("La oportunidad comercial debe estar ABIERTA.", error.getMessage());
    }

    @Test
    void elAltaQuedaProgramadaSinAuditarElAlta() {
        when(oportunidades.buscarFicha(ORG, 8L)).thenReturn(Optional.of(oportunidad(30L)));
        when(visitas.save(any(Visita.class))).thenAnswer(inv -> {
            Visita guardada = inv.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", 4L);
            return guardada;
        });

        FichaVisita ficha = service.programar(
                new DatosVisita(8L, LocalDate.of(2026, 7, 30), LocalTime.of(16, 0), "Primera visita"), agente);

        assertEquals("P", ficha.estado());
        assertEquals(8L, ficha.idOportunidad());
        assertEquals("OP-0001", ficha.codigoOportunidad());
        assertEquals(30L, ficha.idAgente());
        // Los datos del cliente y del local se derivan de la oportunidad.
        assertEquals(50L, ficha.idCliente());
        assertEquals("CAP-0001", ficha.codigoCaptacion());
        assertEquals("Av. Larco 123", ficha.direccionLocal());
        verifyNoInteractions(historial);

        ArgumentCaptor<Visita> guardada = ArgumentCaptor.forClass(Visita.class);
        verify(visitas).save(guardada.capture());
        assertEquals(ORG, guardada.getValue().getOrganizacionId());
    }

    // ------------------------------------------------------------------
    // Agenda: mensajes de guarda del BL
    // ------------------------------------------------------------------

    @Test
    void reprogramarSinFechaUHoraRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.reprogramar(4L, null, LocalTime.of(9, 0), agente));
        assertEquals("La nueva fecha y hora son obligatorias para reprogramar.", error.getMessage());
    }

    @Test
    void noSeReprogramaUnaVisitaCancelada() {
        visitaEnEstado(Visita.CANCELADA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.reprogramar(4L, LocalDate.now(), LocalTime.of(9, 0), agente));
        assertEquals("No se puede reprogramar una visita cancelada.", error.getMessage());
        verifyNoInteractions(historial);
    }

    @Test
    void noSeMarcaComoRealizadaUnaVisitaYaRealizada() {
        visitaEnEstado(Visita.REALIZADA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.marcarRealizada(4L, agente));
        assertEquals("No se puede marcar como realizada una visita realizada.", error.getMessage());
    }

    @Test
    void reprogramarMueveLaAgendaYAudita() {
        visitaEnEstado(Visita.PROGRAMADA);

        FichaVisita ficha = service.reprogramar(4L, LocalDate.of(2026, 8, 5), LocalTime.of(10, 30), agente);

        assertEquals("G", ficha.estado());
        assertEquals(LocalDate.of(2026, 8, 5), ficha.fechaVisita());
        assertEquals(LocalTime.of(10, 30), ficha.horaVisita());
        HistorialEstado evento = eventoAuditado();
        assertEquals("VISITA", evento.getEntidadTipo());
        assertEquals("P", evento.getEstadoAnterior());
        assertEquals("G", evento.getEstadoNuevo());
        assertEquals(3L, evento.getIdActor());
    }

    @Test
    void cancelarGuardaElMotivoEnObservaciones() {
        visitaEnEstado(Visita.PROGRAMADA);

        FichaVisita ficha = service.cancelar(4L, "  El cliente viaja  ", agente);

        assertEquals("C", ficha.estado());
        assertEquals("El cliente viaja", ficha.observaciones());
        assertEquals("El cliente viaja", eventoAuditado().getMotivo());
    }

    @Test
    void cancelarSinMotivoRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.cancelar(4L, " ", agente));
        assertEquals("El motivo de cancelacion es obligatorio.", error.getMessage());
    }

    @Test
    void noRealizadaSinMotivoRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.marcarNoRealizada(4L, null, agente));
        assertEquals("El motivo de la visita no realizada es obligatorio.", error.getMessage());
    }

    // ------------------------------------------------------------------
    // Desenlace
    // ------------------------------------------------------------------

    @Test
    void elDesenlaceExigeUnaVisitaRealizada() {
        visitaEnEstado(Visita.PROGRAMADA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> service.registrarResultado(
                4L, new DesenlaceVisita("I", null, null, 4, null, null, null), agente));
        assertEquals("Solo una visita realizada y sin resultado admite registrar el desenlace.",
                error.getMessage());
    }

    @Test
    void elDesenlaceEsIrrepetible() {
        visitaEnEstado(Visita.REALIZADA);
        service.registrarResultado(4L, new DesenlaceVisita("I", null, null, 4, null, null, null), agente);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> service.registrarResultado(
                4L, new DesenlaceVisita("S", null, null, 3, null, null, null), agente));
        assertEquals("Solo una visita realizada y sin resultado admite registrar el desenlace.",
                error.getMessage());
    }

    @Test
    void elDesenlaceGuardaLaOpinionDelCliente() {
        visitaEnEstado(Visita.REALIZADA);

        FichaVisita ficha = service.registrarResultado(4L,
                new DesenlaceVisita("I", "Le gusto el frente", null, 5, "P", "A", "V"), agente);

        assertEquals("I", ficha.resultado());
        assertEquals("Le gusto el frente", ficha.observaciones());
        assertEquals(5, ficha.nivelInteres());
        assertEquals("P", ficha.objecionPrincipal());
        assertEquals("A", ficha.opinionPrecio());
        assertEquals("V", ficha.proximaAccion());
        // El desenlace NO es una transicion: la visita sigue realizada.
        assertEquals("R", ficha.estado());
        verifyNoInteractions(historial);
    }

    @Test
    void unResultadoInvalidoRespondeElMensajeDelEnumV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> service.registrarResultado(
                4L, new DesenlaceVisita("XYZ", null, null, null, null, null, null), agente));
        assertEquals("Codigo invalido para ResultadoInteraccion: XYZ", error.getMessage());
    }

    @Test
    void noSeRegistraNivelDeInteresEnUnDesenlaceDeNoContinuidad() {
        visitaEnEstado(Visita.REALIZADA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> service.registrarResultado(
                4L, new DesenlaceVisita("N", null, "P", 2, null, null, null), agente));
        assertEquals("No se debe registrar nivel de interes cuando el resultado es de no continuidad.",
                error.getMessage());
    }

    @Test
    void unDesenlaceDeNoContinuidadExigeLaRazon() {
        visitaEnEstado(Visita.REALIZADA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> service.registrarResultado(
                4L, new DesenlaceVisita("N", "No le convence", null, null, null, null, null), agente));
        assertEquals("Debe indicar el motivo de no continuidad cuando el cliente no continua.",
                error.getMessage());
    }

    @Test
    void unDesenlaceDeNoContinuidadCierraLaOportunidad() {
        Visita visita = visitaEnEstado(Visita.REALIZADA);

        FichaVisita ficha = service.registrarResultado(4L,
                new DesenlaceVisita("N", "Prefiere otra zona", "U", null, null, null, null), agente);

        assertEquals("N", ficha.resultado());
        OportunidadComercial oportunidad = visita.getOportunidad();
        assertEquals("N", oportunidad.estadoActual());
        assertEquals("Ubicacion", oportunidad.getMotivoCierre());

        ArgumentCaptor<MotivoNoContinuidad> motivo = ArgumentCaptor.forClass(MotivoNoContinuidad.class);
        verify(motivos).save(motivo.capture());
        assertEquals("U", motivo.getValue().getRazonPrincipal());
        assertEquals(ORG, motivo.getValue().getOrganizacionId());

        // Una sola fila de auditoria: la de la oportunidad (la visita no transiciona).
        HistorialEstado evento = eventoAuditado();
        assertEquals("OPORTUNIDAD", evento.getEntidadTipo());
        assertEquals("N", evento.getEstadoNuevo());
        assertEquals("Ubicacion", evento.getMotivo());
    }

    // ------------------------------------------------------------------
    // Agenda por mes y alcance
    // ------------------------------------------------------------------

    @Test
    void elMesFueraDeRangoRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.mes(1999, 5, agente));
        assertEquals("El mes solicitado no es valido.", error.getMessage());
        assertEquals("El mes solicitado no es valido.",
                assertThrows(ReglaNegocioException.class, () -> service.mes(2026, 13, agente)).getMessage());
    }

    @Test
    void elBrokerAlcanzaLaVisitaPorLaCaptacionDeSuEquipo() {
        visitaEnEstado(Visita.PROGRAMADA);
        when(alcances.supervisados(ORG, 20L)).thenReturn(List.of(30L));

        assertEquals("P", service.obtener(4L, broker).estado());
    }

    @Test
    void elBrokerNoAlcanzaLaVisitaDeUnaCaptacionAjena() {
        visitaEnEstado(Visita.PROGRAMADA);
        when(alcances.supervisados(ORG, 20L)).thenReturn(List.of(77L));

        assertThrows(AccesoNoAutorizadoException.class, () -> service.obtener(4L, broker));
    }

    @Test
    void laVisitaCanceladaLimpiaCualquierDesenlacePrevio() {
        Visita visita = visitaEnEstado(Visita.PROGRAMADA);
        visita.registrarDesenlace("I", null, null, 4, "P", "A", "V");

        FichaVisita ficha = service.cancelar(4L, "Se cayo la cita", agente);

        assertNull(ficha.resultado());
        assertNull(ficha.nivelInteres());
        assertNull(ficha.objecionPrincipal());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /** Visita 4 del agente 30, sobre la oportunidad 8 (captacion del mismo agente). */
    private Visita visitaEnEstado(String estado) {
        Visita visita = new Visita();
        visita.setOrganizacionId(ORG);
        visita.setOportunidad(oportunidad(30L));
        visita.setAgente(detalleAgente(30L, "Valentina Mora"));
        visita.setFechaVisita(LocalDate.of(2026, 7, 30));
        visita.setHoraVisita(LocalTime.of(16, 0));
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(visita, estado);
        ReflectionTestUtils.setField(visita, "id", 4L);
        when(visitas.buscarFicha(ORG, 4L)).thenReturn(Optional.of(visita));
        when(visitas.save(any(Visita.class))).thenAnswer(inv -> inv.getArgument(0));
        when(oportunidades.save(any(OportunidadComercial.class))).thenAnswer(inv -> inv.getArgument(0));
        return visita;
    }

    private static OportunidadComercial oportunidad(long idRolAgente) {
        Propiedad propiedad = new Propiedad();
        propiedad.setOrganizacionId(ORG);
        propiedad.setDireccion("Av. Larco 123");
        propiedad.setDistrito("Miraflores");
        propiedad.setMetraje(new BigDecimal("120.00"));
        ReflectionTestUtils.setField(propiedad, "id", 9L);

        Captacion captacion = new Captacion();
        captacion.setOrganizacionId(ORG);
        captacion.setCodigoCaptacion("CAP-0001");
        captacion.setPropiedad(propiedad);
        captacion.setAgente(detalleAgente(idRolAgente, "Valentina Mora"));
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(captacion, Captacion.ACTIVA);
        ReflectionTestUtils.setField(captacion, "id", 7L);

        OportunidadComercial oportunidad = new OportunidadComercial();
        oportunidad.setOrganizacionId(ORG);
        oportunidad.setCodigoOportunidad("OP-0001");
        oportunidad.setCliente(detalleCliente(50L, "Mariana Delgado"));
        oportunidad.setCaptacion(captacion);
        oportunidad.setAgente(detalleAgente(idRolAgente, "Valentina Mora"));
        new Transiciones(mock(HistorialEstadoRepository.class))
                .iniciar(oportunidad, OportunidadComercial.ABIERTA);
        ReflectionTestUtils.setField(oportunidad, "id", 8L);
        return oportunidad;
    }

    private static DetalleCliente detalleCliente(long idRol, String nombre) {
        DetalleCliente cliente = new DetalleCliente();
        cliente.setOrganizacionId(ORG);
        cliente.setRol(personaRol(nombre, TipoRol.CLIENTE));
        ReflectionTestUtils.setField(cliente, "id", idRol);
        return cliente;
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
