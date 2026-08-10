package com.controllocal.service.impl;

import com.controllocal.domain.persona.CredencialUsuario;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.ReasignacionAgenteBroker;
import com.controllocal.domain.persona.SupervisionAgente;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.ConteoPorBroker;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.ReasignacionAgenteBrokerRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AsignacionService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.PoliticaComercial;
import com.controllocal.service.soporte.UsuariosInternos;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsignacionServiceImplTest {

    private static final long ORG = 1L;
    private static final long ADMIN = 10L;
    private static final long ANTERIOR = 20L;
    private static final long DESTINO = 21L;
    private static final long AGENTE = 30L;

    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final DetalleBrokerRepository brokers = mock(DetalleBrokerRepository.class);
    private final SupervisionAgenteRepository supervisiones =
            mock(SupervisionAgenteRepository.class);
    private final ReasignacionAgenteBrokerRepository reasignaciones =
            mock(ReasignacionAgenteBrokerRepository.class);
    private final UsuariosInternos usuarios = mock(UsuariosInternos.class);
    private final AsignacionServiceImpl service = new AsignacionServiceImpl(
            agentes, brokers, supervisiones, reasignaciones, usuarios);

    private final Actor admin = new Actor(ORG, 1L, ADMIN, "TENANT_ADMIN");
    private final Actor brokerActor = new Actor(ORG, 2L, ANTERIOR, "BROKER");

    @Test
    void todosLosCasosDeUsoExigenAdministrador() {
        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.agentes(brokerActor));
        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.brokers(brokerActor));
        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.historial(brokerActor));
        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.reasignar(null, brokerActor));
    }

    @Test
    void listaAgentesConSuSupervisorVigente() {
        prepararAdmin();
        DetalleAgente agente = agente("D", "A");
        when(agentes.listarFichas(ORG)).thenReturn(List.of(agente));
        when(usuarios.credencialesPorPersona(ORG, List.of(3L)))
                .thenReturn(Map.of(3L,
                        credencial(agente.getRol().getPersona(), "vmora", "A")));
        when(supervisiones.activasPorAgentes(ORG, List.of(AGENTE)))
                .thenReturn(List.of(supervision(ANTERIOR)));
        when(brokers.listarFichas(ORG))
                .thenReturn(List.of(broker(ANTERIOR, 2L, "Ricardo", false)));

        var ficha = service.agentes(admin).getFirst();

        assertEquals("Valentina", ficha.nombre());
        assertEquals("Ricardo", ficha.brokerActual());
        assertEquals("A", ficha.estadoAdministrativo());
    }

    @Test
    void listaBrokersConConteoAgrupado() {
        prepararAdmin();
        DetalleBroker destino = broker(DESTINO, 4L, "Patricia", false);
        when(brokers.listarFichas(ORG)).thenReturn(List.of(destino));
        when(usuarios.credencialesPorPersona(ORG, List.of(4L)))
                .thenReturn(Map.of(4L,
                        credencial(destino.getRol().getPersona(), "psoto", "A")));
        when(supervisiones.contarActivasPorBrokers(ORG, List.of(DESTINO)))
                .thenReturn(List.of(conteo(DESTINO, 5)));

        var ficha = service.brokers(admin).getFirst();

        assertEquals(5, ficha.agentesACargo());
        assertEquals("Patricia", ficha.nombre());
    }

    @Test
    void historialConservaAnteriorNuevoAdministradorYFechaHora() {
        prepararAdmin();
        ReasignacionAgenteBroker evento = evento();
        when(agentes.listarFichas(ORG)).thenReturn(List.of(agente("D", "A")));
        when(brokers.listarFichas(ORG)).thenReturn(List.of(
                broker(ADMIN, 1L, "Administrador", true),
                broker(ANTERIOR, 2L, "Ricardo", false),
                broker(DESTINO, 4L, "Patricia", false)));
        when(reasignaciones.findByOrganizacionIdOrderByIdDesc(ORG))
                .thenReturn(List.of(evento));

        var ficha = service.historial(admin).getFirst();

        assertEquals("Valentina", ficha.agenteNombre());
        assertEquals("Ricardo", ficha.brokerAnteriorNombre());
        assertEquals("Patricia", ficha.brokerNuevoNombre());
        assertEquals("Administrador", ficha.brokerAdministradorNombre());
        assertNotNull(ficha.fechaCambio());
    }

    @Test
    void reasignarExigeAgenteYBrokerDestino() {
        prepararAdmin();

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.reasignar(
                        new AsignacionService.DatosReasignacion(null, null, "x"),
                        admin));

        assertEquals("El agente y el broker destino son obligatorios.",
                error.getMessage());
    }

    @Test
    void reasignarExigeMotivo() {
        prepararAdmin();

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.reasignar(
                        new AsignacionService.DatosReasignacion(
                                AGENTE, DESTINO, " "), admin));

        assertTrue(error.getMessage().contains("al menos "
                        + PoliticaComercial.MOTIVO_REASIGNACION.valor() + " caracteres"),
                error.getMessage());
    }

    /**
     * E1: la longitud minima del motivo vivia SOLO en el formulario de Angular,
     * asi que un POST directo colaba un "ok" en el historial. Ahora la exige el
     * servicio, que es donde la regla no se puede saltar.
     */
    @Test
    void reasignarRechazaUnMotivoDemasiadoCorto() {
        prepararAdmin();

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.reasignar(
                        new AsignacionService.DatosReasignacion(AGENTE, DESTINO, "ok"), admin));

        assertTrue(error.getMessage().contains("caracteres"), error.getMessage());
    }

    @Test
    void elAdministradorNoPuedeSerDestino() {
        prepararAdmin();

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.reasignar(datos(ADMIN), admin));

        assertEquals("El broker administrador no requiere asignacion "
                + "de agentes para supervisar.", error.getMessage());
    }

    @Test
    void elAgenteDebeEstarActivoYDisponible() {
        prepararDestino();
        DetalleAgente inactivo = agente("D", "I");
        when(agentes.buscarFicha(ORG, AGENTE)).thenReturn(Optional.of(inactivo));
        when(usuarios.credencial(ORG, 3L))
                .thenReturn(credencial(inactivo.getRol().getPersona(), "vmora", "I"));

        assertEquals("El agente debe estar ACTIVO.",
                assertThrows(ReglaNegocioException.class,
                        () -> service.reasignar(datos(DESTINO), admin)).getMessage());

        DetalleAgente licencia = agente("L", "A");
        when(agentes.buscarFicha(ORG, AGENTE)).thenReturn(Optional.of(licencia));
        when(usuarios.credencial(ORG, 3L))
                .thenReturn(credencial(licencia.getRol().getPersona(), "vmora", "A"));

        assertEquals("El agente debe estar DISPONIBLE.",
                assertThrows(ReglaNegocioException.class,
                        () -> service.reasignar(datos(DESTINO), admin)).getMessage());
    }

    @Test
    void noReasignaAlSupervisorActual() {
        prepararDestino();
        prepararAgenteDisponible();
        when(supervisiones.buscarActivaPorAgente(ORG, AGENTE))
                .thenReturn(Optional.of(supervision(DESTINO)));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.reasignar(datos(DESTINO), admin));

        assertEquals("El agente ya esta asignado a ese broker supervisor.",
                error.getMessage());
    }

    @Test
    void reasignacionCierraAnteriorAbreNuevaYEscribeEventoEnLaMismaOperacion() {
        prepararDestino();
        DetalleAgente agente = prepararAgenteDisponible();
        SupervisionAgente anterior = supervision(ANTERIOR);
        when(supervisiones.buscarActivaPorAgente(ORG, AGENTE))
                .thenReturn(Optional.of(anterior));
        when(brokers.listarFichas(ORG)).thenReturn(List.of(
                broker(ADMIN, 1L, "Administrador", true),
                broker(ANTERIOR, 2L, "Ricardo", false),
                broker(DESTINO, 4L, "Patricia", false)));
        when(reasignaciones.save(any(ReasignacionAgenteBroker.class)))
                .thenAnswer(inv -> {
                    ReasignacionAgenteBroker guardado = inv.getArgument(0);
                    ReflectionTestUtils.setField(guardado, "id", 99L);
                    guardado.setFechaCambio(OffsetDateTime.parse(
                            "2026-07-29T10:30:00-05:00"));
                    return guardado;
                });

        var resultado = service.reasignar(datos(DESTINO), admin);

        assertEquals(99L, resultado.id());
        assertEquals(ANTERIOR, resultado.idBrokerAnterior());
        assertEquals(DESTINO, resultado.idBrokerNuevo());
        // V36: el autor ya no cabe en una columna que apunta a `detalle_broker`.
        // Administrar dejó de ser una variedad de broker, así que el evento
        // guarda la persona y su banda, y este campo del cable congelado viaja
        // vacío (el JSON omite nulos).
        assertNull(resultado.idBrokerAdministrador());
        assertNotNull(anterior.getFechaFin());
        verify(supervisiones).flush();

        ArgumentCaptor<SupervisionAgente> supervisionesGuardadas =
                ArgumentCaptor.forClass(SupervisionAgente.class);
        verify(supervisiones, times(2)).save(supervisionesGuardadas.capture());
        SupervisionAgente nueva = supervisionesGuardadas.getAllValues().get(1);
        assertEquals(DESTINO, nueva.getIdRolBroker());
        assertEquals(AGENTE, nueva.getIdRolAgente());
        assertNull(nueva.getFechaFin());

        ArgumentCaptor<ReasignacionAgenteBroker> evento =
                ArgumentCaptor.forClass(ReasignacionAgenteBroker.class);
        verify(reasignaciones).save(evento.capture());
        assertEquals(ANTERIOR, evento.getValue().getIdRolBrokerAnterior());
        assertEquals(agente.getId(), evento.getValue().getIdRolAgente());
        // El autor del evento: persona + banda, no un rol de broker (V36).
        assertNull(evento.getValue().getIdRolBrokerAdministrador());
        assertEquals(admin.idPersona(), evento.getValue().getIdPersonaActor());
        assertEquals("TENANT_ADMIN", evento.getValue().getTipoRolActor());
    }

    private void prepararAdmin() {
        DetalleBroker admin = broker(ADMIN, 1L, "Administrador", true);
        when(brokers.buscarFicha(ORG, ADMIN)).thenReturn(Optional.of(admin));
        when(usuarios.credencial(ORG, 1L))
                .thenReturn(credencial(admin.getRol().getPersona(),
                        "admin@controllocal.test", "A"));
    }

    private void prepararDestino() {
        prepararAdmin();
        DetalleBroker destino = broker(DESTINO, 4L, "Patricia", false);
        when(brokers.buscarFicha(ORG, DESTINO)).thenReturn(Optional.of(destino));
        when(usuarios.credencial(ORG, 4L))
                .thenReturn(credencial(destino.getRol().getPersona(), "psoto", "A"));
    }

    private DetalleAgente prepararAgenteDisponible() {
        DetalleAgente agente = agente("D", "A");
        when(agentes.buscarFicha(ORG, AGENTE)).thenReturn(Optional.of(agente));
        when(usuarios.credencial(ORG, 3L))
                .thenReturn(credencial(agente.getRol().getPersona(), "vmora", "A"));
        return agente;
    }

    private static AsignacionService.DatosReasignacion datos(long destino) {
        return new AsignacionService.DatosReasignacion(
                AGENTE, destino, "Redistribucion de cartera");
    }

    private static DetalleAgente agente(String operativo, String administrativo) {
        DetalleAgente agente = new DetalleAgente();
        agente.setOrganizacionId(ORG);
        agente.setRol(rol(AGENTE, persona(3L, "Valentina"), TipoRol.AGENTE));
        agente.setCodigoAgente("AGE-001");
        agente.setFechaIngreso(LocalDate.of(2024, 1, 1));
        agente.setEstadoOperativo(operativo);
        ReflectionTestUtils.setField(agente, "id", AGENTE);
        return agente;
    }

    private static DetalleBroker broker(long id, long idPersona,
                                        String nombre, boolean admin) {
        DetalleBroker broker = new DetalleBroker();
        broker.setOrganizacionId(ORG);
        broker.setRol(rol(id, persona(idPersona, nombre), TipoRol.BROKER));
        broker.setCodigoBroker(admin ? "BRK-ADM-001" : "BRK-" + id);
        broker.setZona("Lima");
        broker.setFechaDesignacion(LocalDate.of(2024, 1, 1));
        broker.setEsAdministrador(admin);
        ReflectionTestUtils.setField(broker, "id", id);
        return broker;
    }

    private static SupervisionAgente supervision(long idBroker) {
        SupervisionAgente supervision = new SupervisionAgente();
        supervision.setOrganizacionId(ORG);
        supervision.setIdRolBroker(idBroker);
        supervision.setIdRolAgente(AGENTE);
        supervision.setFechaAsignacion(LocalDate.of(2024, 1, 1));
        supervision.setMotivo("Asignacion previa");
        return supervision;
    }

    private static ReasignacionAgenteBroker evento() {
        ReasignacionAgenteBroker evento = new ReasignacionAgenteBroker();
        evento.setOrganizacionId(ORG);
        evento.setIdRolAgente(AGENTE);
        evento.setIdRolBrokerAnterior(ANTERIOR);
        evento.setIdRolBrokerNuevo(DESTINO);
        evento.setIdRolBrokerAdministrador(ADMIN);
        evento.setMotivo("Redistribucion");
        evento.setFechaCambio(OffsetDateTime.parse("2026-07-29T10:30:00-05:00"));
        ReflectionTestUtils.setField(evento, "id", 99L);
        return evento;
    }

    private static Persona persona(long id, String nombre) {
        Persona persona = new Persona();
        persona.setOrganizacionId(ORG);
        persona.setTipoPersona("N");
        persona.setTipoDocumento("D");
        persona.setNumeroDocumento("11111111");
        persona.setNombresORazonSocial(nombre);
        persona.setEstado("A");
        ReflectionTestUtils.setField(persona, "id", id);
        return persona;
    }

    private static PersonaRol rol(long id, Persona persona, TipoRol tipo) {
        PersonaRol rol = new PersonaRol();
        rol.setOrganizacionId(ORG);
        rol.setPersona(persona);
        rol.setTipoRol(tipo);
        rol.setVigenciaDesde(LocalDate.of(2024, 1, 1));
        ReflectionTestUtils.setField(rol, "id", id);
        return rol;
    }

    private static CredencialUsuario credencial(
            Persona persona, String usuario, String estado) {
        CredencialUsuario credencial = new CredencialUsuario();
        credencial.setOrganizacionId(ORG);
        credencial.setRol(rol(90L, persona, TipoRol.USUARIO_INTERNO));
        credencial.setNombreUsuario(usuario);
        credencial.setContrasenaHash("pbkdf2$100000$x$x");
        credencial.setEstadoAdministrativo(estado);
        return credencial;
    }

    private static ConteoPorBroker conteo(long id, long total) {
        return new ConteoPorBroker() {
            @Override
            public Long getIdBroker() {
                return id;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
