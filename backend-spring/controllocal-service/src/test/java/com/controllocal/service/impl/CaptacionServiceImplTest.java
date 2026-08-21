package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ReasignacionCaptacion;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.FotoPropiedadRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.ReasignacionCaptacionRepository;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValoresGobernados;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.CaptacionService.DatosCaptacion;
import com.controllocal.service.CaptacionService.FichaCaptacion;
import com.controllocal.service.CaptacionService.FiltrosCaptacion;
import com.controllocal.service.CaptacionService.FiltrosPendientes;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Blinda la revision del broker (MEJ-03: observacion obligatoria en O/R),
 * el invariante <b>un encargo vivo por (local, OPERACION)</b>, el reenvio O-&gt;P
 * y la reasignacion como EVENTO de actor (sin transicion de estado).
 *
 * <p><b>El invariante cambio en D-E4-1</b>, y no por comodidad. Era "una sola
 * captacion ACTIVA por local", y tenia sentido mientras una propiedad solo
 * pudiera alquilarse. Con la venta dentro del modelo, esa regla prohibe
 * justamente el caso que el modelo universal existe para admitir: la misma casa
 * en venta y en alquiler a la vez, cada una con su precio y su historico. Lo
 * que sigue prohibido —y ahora tambien en PENDIENTE y OBSERVADA, no solo en
 * ACTIVA— es un segundo encargo de la MISMA operacion.
 */
class CaptacionServiceImplTest {

    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final ReasignacionCaptacionRepository reasignaciones = mock(ReasignacionCaptacionRepository.class);
    private final PropiedadRepository propiedades = mock(PropiedadRepository.class);
    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final DetalleBrokerRepository brokers = mock(DetalleBrokerRepository.class);
    private final FotoPropiedadRepository fotos = mock(FotoPropiedadRepository.class);
    private final Alcances alcances = mock(Alcances.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);

    private final CaptacionServiceImpl service = new CaptacionServiceImpl(
            captaciones, reasignaciones, propiedades, agentes, brokers, fotos,
            alcances, new Transiciones(historial), mock(AlertaService.class),
            lectorSinGobernados());

    /** Organizacion de legado: el tenant que el backend resuelve para la sesion (V6). */
    private static final long ORG = 1L;

    /** rsalas: organizacion 1, persona 2, rol operativo (persona_rol) 23. */
    private final Actor broker = new Actor(ORG, 2L, 23L, "BROKER");
    /** vmora: persona 3, rol operativo 30 (duena de la captacion del fixture). */
    private final Actor agente = new Actor(ORG, 3L, 30L, "AGENTE");

    // ------------------------------------------------------------------
    // Filtros aditivos de la bandeja Angular
    // ------------------------------------------------------------------

    @Test
    void listarBajaEstadoAgenteYBusquedaAlWhereSinCargaMasiva() {
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, true, List.of()));
        when(captaciones.buscar(anyLong(), anyBoolean(), anyCollection(), any(), any(), any(),
                any(Pageable.class))).thenReturn(Page.empty());

        service.listar(new FiltrosCaptacion("A", 30L, "larco", 2, 25), broker);

        ArgumentCaptor<Pageable> paginacion = ArgumentCaptor.forClass(Pageable.class);
        verify(captaciones).buscar(eq(ORG), eq(true), anyCollection(), eq("A"), eq(30L),
                eq("larco"), paginacion.capture());
        assertEquals(1, paginacion.getValue().getPageNumber());
        assertEquals(25, paginacion.getValue().getPageSize());
    }

    @Test
    void pendientesBajaEstadoAgenteBusquedaYPaginaAlWhere() {
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, true, List.of()));
        when(captaciones.pendientes(anyLong(), anyBoolean(), anyCollection(), any(), any(), any(),
                any(Pageable.class))).thenReturn(Page.empty());

        service.pendientes(new FiltrosPendientes("O", 30L, "miraflores", 3, 15), broker);

        ArgumentCaptor<Pageable> paginacion = ArgumentCaptor.forClass(Pageable.class);
        verify(captaciones).pendientes(eq(ORG), eq(true), anyCollection(), eq("O"), eq(30L),
                eq("miraflores"), paginacion.capture());
        assertEquals(2, paginacion.getValue().getPageNumber());
        assertEquals(15, paginacion.getValue().getPageSize());
    }

    // ------------------------------------------------------------------
    // Decision del broker (MEJ-03)
    // ------------------------------------------------------------------

    @Test
    void observarSinObservacionRespondeElMensajeMej03() {
        captacionEnEstado(Captacion.PENDIENTE_REVISION);
        when(brokers.findById(23L)).thenReturn(Optional.of(detalleBroker(23L)));
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.decidir(9L, "O", "  ", broker));
        assertEquals("La observacion de revision es obligatorio.", error.getMessage());
        verifyNoInteractions(historial);
    }

    @Test
    void aprobarActivaLaCaptacionYAuditaConElBroker() {
        captacionEnEstado(Captacion.PENDIENTE_REVISION);
        when(brokers.findById(23L)).thenReturn(Optional.of(detalleBroker(23L)));
        // Sin otro encargo de ALQUILER vivo sobre el mismo local. Que exista
        // uno de VENTA no estorbaria: la exclusion es POR OPERACION (D-E4-1).
        when(captaciones.encargoVivoDe(ORG, 7L, "A")).thenReturn(Optional.empty());

        FichaCaptacion ficha = service.decidir(9L, "A", "Sin observaciones", broker);

        assertEquals("A", ficha.estado());
        assertEquals(23L, ficha.idBrokerRevisor());
        HistorialEstado evento = eventoAuditado();
        assertEquals("CAPTACION", evento.getEntidadTipo());
        assertEquals("P", evento.getEstadoAnterior());
        assertEquals("A", evento.getEstadoNuevo());
        assertEquals(2L, evento.getIdActor());
        assertEquals("BROKER", evento.getTipoRolActor());
        assertEquals("Captacion aprobada por el broker.", evento.getMotivo());
    }

    @Test
    void aprobarConOtroEncargoVivoDeLaMismaOperacionSeRechaza() {
        captacionEnEstado(Captacion.PENDIENTE_REVISION);
        when(brokers.findById(23L)).thenReturn(Optional.of(detalleBroker(23L)));
        when(captaciones.encargoVivoDe(ORG, 7L, "A")).thenReturn(Optional.of(otroEncargo(99L, "A")));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.decidir(9L, "A", null, broker));

        assertTrue(error.getMessage().contains("ALQUILER"), error.getMessage());
        assertTrue(error.getMessage().contains("CAP-0099"),
                "el mensaje dice CUAL es el encargo que estorba: " + error.getMessage());
    }

    @Test
    void soloSeDecideUnaCaptacionPendienteUObservada() {
        captacionEnEstado(Captacion.ACTIVA);
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.decidir(9L, "A", null, broker));
        assertEquals("La captacion debe estar pendiente de revision u observada.", error.getMessage());
    }

    @Test
    void unaDecisionDesconocidaRespondeElMensajeV1() {
        captacionEnEstado(Captacion.OBSERVADA);
        when(brokers.findById(23L)).thenReturn(Optional.of(detalleBroker(23L)));
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.decidir(9L, "X", "obs", broker));
        assertEquals("Decision no valida.", error.getMessage());
    }

    // ------------------------------------------------------------------
    // Alta y edicion del agente
    // ------------------------------------------------------------------

    @Test
    void registrarSinCodigoRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(datos(null), agente));
        assertEquals("El codigo de captacion es obligatorio.", error.getMessage());
    }

    @Test
    void registrarConOtroEncargoVivoDeLaMismaOperacionSeRechaza() {
        when(agentes.findById(30L)).thenReturn(Optional.of(detalleAgente(30L, "Valentina Mora")));
        when(propiedades.findByOrganizacionIdAndId(ORG, 7L)).thenReturn(Optional.of(propiedad(7L)));
        when(captaciones.encargoVivoDe(ORG, 7L, "A")).thenReturn(Optional.of(otroEncargo(99L, "A")));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(datos("CAP-0100"), agente));

        assertTrue(error.getMessage().contains("ALQUILER"), error.getMessage());
        assertTrue(error.getMessage().contains("OTRA operacion si es posible"),
                "el mensaje tiene que ensenar que venta y alquiler pueden convivir: "
                        + error.getMessage());
    }

    @Test
    void unEncargoSinOperacionNoSePuedeRegistrar() {
        // D-E4-1: se acabo el defecto silencioso a alquiler. Sin operacion
        // declarada, el encargo no se abre -- no se sabe si el titular quiere
        // vender o alquilar, que es lo primero que hay que saber.
        DatosCaptacion sinOperacion = new DatosCaptacion("CAP-0100", LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10), LocalDate.of(2027, 7, 10), new BigDecimal("100.00"),
                "Encargo sin operacion", 7L, 30L, null, 3, Boolean.TRUE);

        when(agentes.findById(30L)).thenReturn(Optional.of(detalleAgente(30L, "Valentina Mora")));
        when(propiedades.findByOrganizacionIdAndId(ORG, 7L)).thenReturn(Optional.of(propiedad(7L)));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(sinOperacion, agente));
        assertTrue(error.getMessage().contains("VENTA o ALQUILER"), error.getMessage());
    }

    @Test
    void editarUnaObservadaLaReenviaAPendienteYLoAudita() {
        captacionEnEstado(Captacion.OBSERVADA, agente);

        FichaCaptacion ficha = service.actualizar(9L, datos("CAP-0009"), agente);

        assertEquals("P", ficha.estado());
        HistorialEstado evento = eventoAuditado();
        assertEquals("O", evento.getEstadoAnterior());
        assertEquals("P", evento.getEstadoNuevo());
        assertEquals("Reenvio a revision tras corregir observaciones.", evento.getMotivo());
    }

    @Test
    void noSeEditaUnaCaptacionActiva() {
        captacionEnEstado(Captacion.ACTIVA, agente);
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.actualizar(9L, datos("CAP-0009"), agente));
        assertEquals("Solo se puede editar una captacion pendiente u observada.", error.getMessage());
    }

    // ------------------------------------------------------------------
    // Reasignacion: evento de actor, NO transicion
    // ------------------------------------------------------------------

    @Test
    void reasignarCambiaElResponsableSinTransicionarEstado() {
        Captacion cap = captacionEnEstado(Captacion.ACTIVA);
        DetalleAgente nuevo = detalleAgente(31L, "Javier Ruiz");
        when(agentes.findById(31L)).thenReturn(Optional.of(nuevo));
        when(alcances.alcanza(broker, 31L)).thenReturn(true);
        when(brokers.findById(23L)).thenReturn(Optional.of(detalleBroker(23L)));

        FichaCaptacion ficha = service.reasignar(9L, 31L, "Balance de cartera", broker);

        assertEquals("A", ficha.estado());
        assertEquals(31L, ficha.idAgente());
        assertEquals(nuevo, cap.getAgente());
        ArgumentCaptor<ReasignacionCaptacion> evento = ArgumentCaptor.forClass(ReasignacionCaptacion.class);
        verify(reasignaciones).save(evento.capture());
        assertEquals(30L, evento.getValue().getAgenteAnterior().getId());
        assertEquals(31L, evento.getValue().getAgenteNuevo().getId());
        assertEquals(23L, evento.getValue().getBroker().getId());
        assertEquals("Balance de cartera", evento.getValue().getMotivo());
        // El evento hereda el tenant de la captacion reasignada.
        assertEquals(ORG, evento.getValue().getOrganizacionId());
        // La reasignacion NO es una transicion: historial_estado queda intacto.
        verifyNoInteractions(historial);
    }

    @Test
    void reasignarAUnAgenteFueraDeSuCarteraRespondeElMensajeV1() {
        captacionEnEstado(Captacion.ACTIVA);
        when(agentes.findById(31L)).thenReturn(Optional.of(detalleAgente(31L, "Lucia Torres")));
        when(alcances.alcanza(broker, 31L)).thenReturn(false);
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.reasignar(9L, 31L, "Cambio de zona", broker));
        assertEquals("El broker no supervisa al agente responsable de esta operacion.", error.getMessage());
    }

    @Test
    void reasignarAlMismoAgenteRespondeElMensajeV1() {
        captacionEnEstado(Captacion.ACTIVA);
        when(agentes.findById(30L)).thenReturn(Optional.of(detalleAgente(30L, "Valentina Mora")));
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.reasignar(9L, 30L, "Sin cambio real", broker));
        assertEquals("La captacion ya esta asignada a ese agente.", error.getMessage());
    }

    // ------------------------------------------------------------------
    // Cierre
    // ------------------------------------------------------------------

    @Test
    void cerrarExigeUnaCaptacionActiva() {
        captacionEnEstado(Captacion.PENDIENTE_REVISION);
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.cerrar(9L, "Contrato firmado", broker));
        assertEquals("La captacion debe estar ACTIVA.", error.getMessage());
    }

    @Test
    void cerrarRegistraLaFechaYMotivoSinAlterarElFinDelEncargo() {
        Captacion cap = captacionEnEstado(Captacion.ACTIVA);
        LocalDate finEncargo = cap.getFechaFinVigencia();

        FichaCaptacion ficha = service.cerrar(9L, "Contrato firmado", broker);

        assertEquals("C", ficha.estado());
        assertEquals(finEncargo, cap.getFechaFinVigencia());
        assertEquals(LocalDate.now(), ficha.fechaCierre());
        assertEquals("M", ficha.motivoCierre());
        assertEquals("Contrato firmado", ficha.detalleMotivoCierre());
        HistorialEstado evento = eventoAuditado();
        assertEquals("A", evento.getEstadoAnterior());
        assertEquals("C", evento.getEstadoNuevo());
        assertEquals("Contrato firmado", evento.getMotivo());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private Captacion captacionEnEstado(String estado) {
        return captacionEnEstado(estado, broker);
    }

    /** Captacion id 9 del agente 30 sobre el local 7, visible para el actor dado. */
    private Captacion captacionEnEstado(String estado, Actor actor) {
        Captacion cap = new Captacion();
        cap.setOrganizacionId(ORG);
        cap.setCodigoCaptacion("CAP-0009");
        cap.setFechaCaptacion(LocalDate.of(2026, 7, 1));
        cap.setFechaInicioVigencia(LocalDate.of(2026, 7, 1));
        cap.setFechaFinVigencia(LocalDate.of(2027, 7, 1));
        cap.setComisionPactada(new BigDecimal("4250.00"));
        cap.setExclusividad(Boolean.TRUE);
        // La operacion ya no tiene defecto en la entidad (D-E4-1): un encargo
        // de prueba sin declararla seria un encargo que la BD no admitiria.
        cap.setMotivoOperacion("A");
        cap.setPropiedad(propiedad(7L));
        cap.setAgente(detalleAgente(30L, "Valentina Mora"));
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(cap, estado);
        ReflectionTestUtils.setField(cap, "id", 9L);
        when(captaciones.buscarFicha(ORG, 9L)).thenReturn(Optional.of(cap));
        when(alcances.alcanza(actor, 30L)).thenReturn(true);
        when(fotos.findByIdPropiedadOrderByOrdenAscIdAsc(anyLong())).thenReturn(List.of());
        return cap;
    }

    /** Otro encargo vivo sobre el mismo local, para probar la exclusion por operacion. */
    private static Captacion otroEncargo(long id, String operacion) {
        Captacion otro = new Captacion();
        otro.setOrganizacionId(ORG);
        otro.setCodigoCaptacion("CAP-%04d".formatted(id));
        otro.setMotivoOperacion(operacion);
        otro.setPropiedad(propiedad(7L));
        ReflectionTestUtils.setField(otro, "id", id);
        return otro;
    }

    private static DatosCaptacion datos(String codigo) {
        return new DatosCaptacion(codigo, LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10), LocalDate.of(2027, 7, 10),
                new BigDecimal("100.00"), "Encargo de alquiler", 7L, 30L, "A", 3, Boolean.TRUE);
    }

    private static Propiedad propiedad(long id) {
        Propiedad propiedad = new Propiedad();
        propiedad.setOrganizacionId(ORG);
        propiedad.setCodigo("LOC-0100");
        propiedad.setDireccion("Av. Prueba 123");
        propiedad.setDistrito("Miraflores");
        propiedad.setMetraje(new BigDecimal("120.00"));
        propiedad.setPrecioReferencial(new BigDecimal("8500.00"));
        propiedad.setMonedaReferencial("PEN");
        propiedad.iniciarDisponible();
        ReflectionTestUtils.setField(propiedad, "id", id);
        return propiedad;
    }

    private static DetalleAgente detalleAgente(long idRol, String nombre) {
        DetalleAgente detalle = new DetalleAgente();
        detalle.setRol(rolConPersona(nombre, TipoRol.AGENTE));
        detalle.setCodigoAgente("AGE-001");
        ReflectionTestUtils.setField(detalle, "id", idRol);
        return detalle;
    }

    private static DetalleBroker detalleBroker(long idRol) {
        DetalleBroker detalle = new DetalleBroker();
        detalle.setRol(rolConPersona("Ricardo Salas", TipoRol.BROKER));
        ReflectionTestUtils.setField(detalle, "id", idRol);
        return detalle;
    }

    private static PersonaRol rolConPersona(String nombre, TipoRol tipo) {
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

    /**
     * Un lector que no encuentra ningun valor gobernado.
     *
     * <p>Estas pruebas no afirman nada sobre el rubro; solo necesitan que el
     * servicio pueda construir su ficha. Devolver lotes vacios —y no un mock
     * sin respuestas— es lo correcto: {@code ValoresGobernados.vacio()}
     * significa "no se sabe nada de esta propiedad", que es un estado legitimo
     * del dominio, mientras que un null seria un fallo del andamiaje
     * disfrazado de dato.
     */
    private static LectorPorAutoridad lectorSinGobernados() {
        LectorPorAutoridad lector = mock(LectorPorAutoridad.class);
        lenient().when(lector.de(anyLong(), any())).thenReturn(ValoresGobernados.vacio());
        lenient().when(lector.deVarias(anyLong(), any())).thenReturn(Map.of());
        lenient().when(lector.gobernadosDeVarias(any())).thenReturn(Map.of());
        return lector;
    }
}
