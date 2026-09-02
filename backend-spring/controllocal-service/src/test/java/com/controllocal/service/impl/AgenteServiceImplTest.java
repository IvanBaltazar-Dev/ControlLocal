package com.controllocal.service.impl;

import com.controllocal.domain.organizacion.UsuarioOrganizacion;
import com.controllocal.domain.persona.CredencialUsuario;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.SupervisionAgente;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.ComisionGeneradaPorMoneda;
import com.controllocal.persistence.query.ConteoPorAgente;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.query.MovimientoComisionPorMoneda;
import com.controllocal.persistence.query.RepartoComisionPorMoneda;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.CredencialUsuarioRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AgenteService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.UsuariosInternos;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgenteServiceImplTest {

    private static final long ORG = 1L;
    private static final long BROKER = 20L;
    private static final long ADMIN = 10L;
    private static final long AGENTE = 30L;

    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final DetalleBrokerRepository brokers = mock(DetalleBrokerRepository.class);
    private final SupervisionAgenteRepository supervisiones =
            mock(SupervisionAgenteRepository.class);
    private final CredencialUsuarioRepository credenciales =
            mock(CredencialUsuarioRepository.class);
    private final PersonaRepository personas = mock(PersonaRepository.class);
    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final OportunidadComercialRepository oportunidades =
            mock(OportunidadComercialRepository.class);
    private final UsuariosInternos usuarios = mock(UsuariosInternos.class);
    private final SolicitudAlquilerRepository solicitudes =
            mock(SolicitudAlquilerRepository.class);
    private final ContratoAlquilerRepository contratos = mock(ContratoAlquilerRepository.class);

    private final AgenteServiceImpl service = new AgenteServiceImpl(
            agentes, brokers, supervisiones, credenciales, personas,
            captaciones, oportunidades, solicitudes, contratos, usuarios);

    private final Actor broker = new Actor(ORG, 2L, BROKER, "BROKER");
    private final Actor admin = new Actor(ORG, 1L, ADMIN, "TENANT_ADMIN");
    private final Actor agenteActor = new Actor(ORG, 3L, AGENTE, "AGENTE");

    @Test
    void elBrokerListaSoloSuPaginaYLosContadoresLleganAgrupados() {
        prepararBroker(BROKER, 2L, false, "rsalas");
        DetalleAgente agente = agente(AGENTE, 3L);
        // `sinScope=false` es lo que acota el conjunto al equipo del broker: la
        // consulta es una sola y el rol decide el parametro, no el metodo.
        when(agentes.buscar(eq(ORG), eq(false), eq(BROKER), isNull(), isNull(), isNull(),
                isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(agente)));
        when(usuarios.credencialesPorPersona(ORG, List.of(3L)))
                .thenReturn(Map.of(3L,
                        credencial(agente.getRol().getPersona(), "vmora", "A")));
        when(captaciones.contarEnCarteraPorAgentes(ORG, List.of(AGENTE)))
                .thenReturn(List.of(conteo(AGENTE, 4)));
        when(oportunidades.contarActivasPorAgentes(ORG, List.of(AGENTE)))
                .thenReturn(List.of(conteo(AGENTE, 2)));

        var ficha = service.listar(1, 50, broker).items().getFirst();

        assertEquals(4, ficha.captacionesActivas());
        assertEquals(2, ficha.operacionesActivas());
        verify(agentes).buscar(eq(ORG), eq(false), eq(BROKER), isNull(), isNull(), isNull(),
                isNull(), any(Pageable.class));
    }

    @Test
    void elAdminPaginaElCatalogoCompleto() {
        prepararBroker(ADMIN, 1L, true, "admin@controllocal.test");
        when(agentes.buscar(eq(ORG), eq(true), anyLong(), isNull(), isNull(), isNull(),
                isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertEquals(0, service.listar(1, 50, admin).total());
        verify(agentes).buscar(eq(ORG), eq(true), anyLong(), isNull(), isNull(), isNull(),
                isNull(), any(Pageable.class));
    }

    /**
     * Los cuatro filtros son ADITIVOS: llegan tal cual a la consulta, en
     * mayuscula los codigos, y el texto recortado. Sin ellos, la llamada es
     * identica a la de antes de que existieran.
     */
    @Test
    void losFiltrosDelCatalogoViajanALaConsulta() {
        prepararBroker(ADMIN, 1L, true, "admin@controllocal.test");
        when(agentes.buscar(eq(ORG), eq(true), anyLong(), eq("mora"), eq("A"), eq("D"),
                eq("Miraflores"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listar(new AgenteService.FiltrosAgente("  mora  ", "a", "d", "Miraflores", 1, 50),
                admin);

        verify(agentes).buscar(eq(ORG), eq(true), anyLong(), eq("mora"), eq("A"), eq("D"),
                eq("Miraflores"), any(Pageable.class));
    }

    @Test
    void unAgenteNoPuedeEntrarAlCatalogoDeAgentes() {
        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.listar(1, 50, agenteActor));
    }

    // ------------------------------------------------------------------
    // Ficha individual (extension aditiva)
    // ------------------------------------------------------------------

    /**
     * Lo que esta ficha demuestra: las cuatro magnitudes del dinero salen
     * SEPARADAS y los dos saldos son diferencias derivadas. Generada 10.000 y
     * cobrada 6.000 dejan 4.000 por cobrar; asignada 3.000 y pagada 1.000
     * dejan 2.000 por pagarle.
     */
    @Test
    void laFichaSeparaComisionGeneradaCobradaAsignadaYPagada() {
        prepararFicha();
        when(contratos.comisionesGeneradasDeAgente(ORG, AGENTE))
                .thenReturn(List.of(generada("PEN", "10000.00")));
        when(contratos.repartosDeAgente(ORG, AGENTE))
                .thenReturn(List.of(reparto("PEN", "3000.00")));
        when(contratos.movimientosDeAgente(ORG, AGENTE))
                .thenReturn(List.of(movimiento("PEN", "6000.00", "1000.00")));

        var comisiones = service.ficha(AGENTE, admin).comisiones();

        assertEquals(new BigDecimal("10000.00"), comisiones.generada().getFirst().monto());
        assertEquals(new BigDecimal("6000.00"), comisiones.cobrada().getFirst().monto());
        assertEquals(new BigDecimal("4000.00"), comisiones.pendienteCobro().getFirst().monto());
        assertEquals(new BigDecimal("3000.00"), comisiones.asignadaAgente().getFirst().monto());
        assertEquals(new BigDecimal("1000.00"), comisiones.pagadaAgente().getFirst().monto());
        assertEquals(new BigDecimal("2000.00"),
                comisiones.pendientePagoAgente().getFirst().monto());
    }

    /** Un cobro por encima de lo generado no publica un pendiente negativo. */
    @Test
    void elSaldoPendienteNuncaEsNegativo() {
        prepararFicha();
        when(contratos.comisionesGeneradasDeAgente(ORG, AGENTE))
                .thenReturn(List.of(generada("USD", "1000.00")));
        when(contratos.movimientosDeAgente(ORG, AGENTE))
                .thenReturn(List.of(movimiento("USD", "1500.00", "0.00")));

        var comisiones = service.ficha(AGENTE, admin).comisiones();

        assertEquals(BigDecimal.ZERO, comisiones.pendienteCobro().getFirst().monto());
    }

    /** Los conteos llegan con su descripcion, no con la letra suelta. */
    @Test
    void laFichaDescribeLosEstadosDeCadaMaquina() {
        prepararFicha();
        when(captaciones.contarPorEstadoDeAgente(ORG, AGENTE))
                .thenReturn(List.of(conteoEstado("A", 3)));
        when(solicitudes.contarPorEstadoDeAgente(ORG, AGENTE))
                .thenReturn(List.of(conteoEstado("C", 2)));

        var ficha = service.ficha(AGENTE, admin);

        assertEquals("Activa", ficha.captaciones().getFirst().descripcion());
        assertEquals(3, ficha.captaciones().getFirst().total());
        assertEquals("Cerrada", ficha.solicitudes().getFirst().descripcion());
    }

    /** El BROKER solo abre la ficha de los agentes que supervisa HOY. */
    @Test
    void elBrokerNoAbreLaFichaDeUnAgenteAjeno() {
        prepararBroker(BROKER, 2L, false, "rsalas");
        when(agentes.buscarFicha(ORG, AGENTE)).thenReturn(Optional.of(agente(AGENTE, 3L)));
        when(supervisiones.buscarActivaPorAgente(ORG, AGENTE))
                .thenReturn(Optional.of(supervision(99L)));

        assertThrows(AccesoNoAutorizadoException.class, () -> service.ficha(AGENTE, broker));
    }

    @Test
    void laFichaDeUnAgenteInexistenteResponde404() {
        prepararBroker(ADMIN, 1L, true, "admin@controllocal.test");
        when(agentes.buscarFicha(ORG, 999L)).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class, () -> service.ficha(999L, admin));
    }

    /** Sin supervision vigente la ficha no inventa un broker: viaja nula. */
    @Test
    void unAgenteSinSupervisorVigenteTieneFichaSinBroker() {
        prepararFicha();
        when(supervisiones.buscarActivaPorAgente(ORG, AGENTE)).thenReturn(Optional.empty());

        assertNull(service.ficha(AGENTE, admin).supervision());
    }

    private void prepararFicha() {
        prepararBroker(ADMIN, 1L, true, "admin@controllocal.test");
        DetalleAgente agente = agente(AGENTE, 3L);
        when(agentes.buscarFicha(ORG, AGENTE)).thenReturn(Optional.of(agente));
        when(usuarios.credencial(ORG, 3L))
                .thenReturn(credencial(agente.getRol().getPersona(), "vmora", "A"));
        when(supervisiones.buscarActivaPorAgente(ORG, AGENTE))
                .thenReturn(Optional.of(supervision(BROKER)));
        when(contratos.cierresDeAgente(eq(ORG), eq(AGENTE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }

    private static SupervisionAgente supervision(long idBroker) {
        SupervisionAgente supervision = new SupervisionAgente();
        supervision.setOrganizacionId(ORG);
        supervision.setIdRolBroker(idBroker);
        supervision.setIdRolAgente(AGENTE);
        supervision.setFechaAsignacion(LocalDate.of(2024, 2, 1));
        supervision.setMotivo("Asignacion inicial por registro de agente.");
        return supervision;
    }

    private static ConteoPorEstado conteoEstado(String estado, long total) {
        return new ConteoPorEstado() {
            @Override public String getEstado() { return estado; }
            @Override public long getTotal() { return total; }
        };
    }

    private static ComisionGeneradaPorMoneda generada(String moneda, String monto) {
        return new ComisionGeneradaPorMoneda() {
            @Override public String getMoneda() { return moneda; }
            @Override public BigDecimal getMonto() { return new BigDecimal(monto); }
        };
    }

    private static RepartoComisionPorMoneda reparto(String moneda, String parte) {
        return new RepartoComisionPorMoneda() {
            @Override public String getMoneda() { return moneda; }
            @Override public BigDecimal getParteAgente() { return new BigDecimal(parte); }
        };
    }

    private static MovimientoComisionPorMoneda movimiento(String moneda, String cobrado,
                                                          String pagado) {
        return new MovimientoComisionPorMoneda() {
            @Override public String getMoneda() { return moneda; }
            @Override public BigDecimal getMontoCobrado() { return new BigDecimal(cobrado); }
            @Override public BigDecimal getMontoPagadoAgente() { return new BigDecimal(pagado); }
        };
    }

    @Test
    void elAltaExigeLosTresCamposDelCableAntesDeValidarSupervisor() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new AgenteService.DatosAgente(
                        "", null, null, null, null, null,
                        "nuevo", "clave", null, null, null, null, null), admin));

        assertEquals("Nombre, usuario y contrasena del agente son obligatorios.",
                error.getMessage());
    }

    /**
     * D-S0-17 fila 17: el alta cambio de dueno. Antes la hacia el broker y el
     * administrador estaba EXPRESAMENTE excluido; ahora es al reves. Este test
     * fijaba la regla vieja y ahora fija la nueva — la comprobacion no
     * desaparece, se da la vuelta.
     */
    @Test
    void elBrokerYaNoRegistraAgentes() {
        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.registrar(datosAlta(), broker));

        verifyNoInteractions(supervisiones);
    }

    @Test
    void elAltaExigeElBrokerSupervisorPorqueElAdministradorNoSupervisaANadie() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new AgenteService.DatosAgente(
                        "Agente Nuevo", null, null, "1234", null, null,
                        "anuevo", "Temporal2026", "Lima", null, null, null, null), admin));

        assertEquals("Debe indicar el broker que supervisara al agente.",
                error.getMessage());
    }

    @Test
    void elAdminCreaLosDosRolesDetalleYSupervisionInicialAtomicos() {
        prepararBroker(BROKER, 2L, false, "rsalas");
        when(agentes.countByOrganizacionId(ORG)).thenReturn(6L);
        Persona persona = persona(3L, "Agente Nuevo");
        PersonaRol rol = rol(AGENTE, persona, TipoRol.AGENTE);
        CredencialUsuario credencial = credencial(persona, "anuevo", "A");
        when(usuarios.registrar(eq(ORG), eq(TipoRol.AGENTE), eq("N"), eq("D"),
                eq("1234"), eq("Agente Nuevo"), any(), any(), eq("anuevo"),
                eq("Temporal2026"), eq("A")))
                .thenReturn(new UsuariosInternos.Alta(persona, rol, credencial, membresia()));
        when(agentes.save(any(DetalleAgente.class))).thenAnswer(inv -> {
            DetalleAgente guardado = inv.getArgument(0);
            ReflectionTestUtils.setField(guardado, "id", AGENTE);
            return guardado;
        });

        var creado = service.registrar(datosAlta(), admin);

        assertEquals("AGE-007", creado.codigoAgente());
        assertEquals("D", creado.estadoOperativo());
        assertEquals(0, creado.captacionesActivas());
        assertEquals(0, creado.operacionesActivas());
        ArgumentCaptor<SupervisionAgente> supervision =
                ArgumentCaptor.forClass(SupervisionAgente.class);
        verify(supervisiones).save(supervision.capture());
        assertEquals(BROKER, supervision.getValue().getIdRolBroker());
        assertEquals(AGENTE, supervision.getValue().getIdRolAgente());
        assertEquals("Asignacion inicial por registro de agente.",
                supervision.getValue().getMotivo());
    }

    @Test
    void documentoCortoNoDisparaLaValidacionDePropietarios() {
        prepararBroker(BROKER, 2L, false, "rsalas");
        when(agentes.countByOrganizacionId(ORG)).thenReturn(0L);
        Persona persona = persona(3L, "Agente Nuevo");
        persona.setNumeroDocumento("1234");
        PersonaRol rol = rol(AGENTE, persona, TipoRol.AGENTE);
        when(usuarios.registrar(eq(ORG), eq(TipoRol.AGENTE), eq("N"), eq("D"),
                eq("1234"), any(), any(), any(), any(), any(), any()))
                .thenReturn(new UsuariosInternos.Alta(persona, rol,
                        credencial(persona, "anuevo", "A"), membresia()));
        when(agentes.save(any(DetalleAgente.class))).thenAnswer(inv -> {
            DetalleAgente guardado = inv.getArgument(0);
            ReflectionTestUtils.setField(guardado, "id", AGENTE);
            return guardado;
        });

        service.registrar(datosAlta(), admin);

        verify(usuarios).registrar(eq(ORG), eq(TipoRol.AGENTE), eq("N"), eq("D"),
                eq("1234"), any(), any(), any(), any(), any(), any());
    }

    /**
     * Fila 18: la edicion paso a ser gobierno. El filtro de supervision que
     * habia aqui protegia al broker de tocar agentes ajenos; ahora el broker no
     * edita NINGUN agente, ni el suyo, asi que la proteccion se sustituye por
     * una mas fuerte y este test la fija.
     */
    @Test
    void elBrokerYaNoEditaAgentes() {
        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.actualizar(AGENTE, datosAlta(), broker));

        verifyNoInteractions(agentes);
    }

    @Test
    void elPutSoloTocaLosCamposPermitidosYRespondeContadoresEnCero() {
        prepararBroker(BROKER, 2L, false, "rsalas");
        DetalleAgente agente = agente(AGENTE, 3L);
        CredencialUsuario credencial =
                credencial(agente.getRol().getPersona(), "vmora", "A");
        when(agentes.buscarFicha(ORG, AGENTE)).thenReturn(Optional.of(agente));
        // D-P0-13: este cuerpo trae `estado` y `estadoOperativo`, o sea cambia
        // la ELEGIBILIDAD del agente (D-P0-7), asi que el caso de uso toma la
        // fila `detalle_agente` para serializarse con los traspasos en curso.
        // Sin declararlo, la edicion caeria en «Agente no encontrado.» y esta
        // prueba mediria el candado en vez de los campos que dice medir.
        when(agentes.bloquearParaGobierno(ORG, AGENTE)).thenReturn(Optional.of(agente));
        when(supervisiones.buscarActivaPorAgente(ORG, AGENTE))
                .thenReturn(Optional.of(supervision(BROKER, AGENTE)));
        when(usuarios.credencial(ORG, 3L)).thenReturn(credencial);

        var ficha = service.actualizar(AGENTE, new AgenteService.DatosAgente(
                "Valentina Editada", "J", "R", "99999999999",
                "999111222", "editada@test.pe", "ignorado", "ignorada",
                "Nueva zona", "AGE-999", "I", "L", null), admin);

        assertEquals("Valentina Editada", ficha.nombre());
        assertEquals("D", ficha.tipoDocumento());
        assertEquals("11111111", ficha.numeroDocumento());
        assertEquals("vmora", ficha.usuario());
        assertEquals("AGE-001", ficha.codigoAgente());
        assertEquals("I", ficha.estadoAdministrativo());
        assertEquals("L", ficha.estadoOperativo());
        assertEquals(0, ficha.captacionesActivas());
        assertEquals(0, ficha.operacionesActivas());
    }

    @Test
    void unTipoDePersonaInvalidoConservaElMensajeV1() {
        prepararBroker(BROKER, 2L, false, "rsalas");

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new AgenteService.DatosAgente(
                        "Agente", "X", "D", "1234", null, null,
                        "nuevo", "clave", null, null, null, null, BROKER), admin));

        assertEquals("Valor invalido: X", error.getMessage());
    }

    private void prepararBroker(long id, long idPersona,
                                boolean esAdmin, String usuario) {
        DetalleBroker detalle = broker(id, idPersona, esAdmin);
        when(brokers.buscarFicha(ORG, id)).thenReturn(Optional.of(detalle));
        when(usuarios.credencial(ORG, idPersona))
                .thenReturn(credencial(detalle.getRol().getPersona(), usuario, "A"));
    }

    private static AgenteService.DatosAgente datosAlta() {
        return new AgenteService.DatosAgente(
                "Agente Nuevo", null, null, "1234", null, null,
                "anuevo", "Temporal2026", "Lima", null, null, null, BROKER);
    }

    private static DetalleBroker broker(long id, long idPersona, boolean esAdmin) {
        DetalleBroker broker = new DetalleBroker();
        broker.setOrganizacionId(ORG);
        broker.setRol(rol(id, persona(idPersona, "Broker"), TipoRol.BROKER));
        broker.setCodigoBroker(esAdmin ? "BRK-ADM-001" : "BRK-001");
        broker.setFechaDesignacion(LocalDate.of(2024, 1, 1));
        broker.setEsAdministrador(esAdmin);
        ReflectionTestUtils.setField(broker, "id", id);
        return broker;
    }

    private static DetalleAgente agente(long id, long idPersona) {
        DetalleAgente agente = new DetalleAgente();
        agente.setOrganizacionId(ORG);
        agente.setRol(rol(id, persona(idPersona, "Valentina Mora"), TipoRol.AGENTE));
        agente.setCodigoAgente("AGE-001");
        agente.setZonaAsignada("Lima");
        agente.setFechaIngreso(LocalDate.of(2024, 2, 1));
        agente.setEstadoOperativo("D");
        ReflectionTestUtils.setField(agente, "id", id);
        return agente;
    }

    private static SupervisionAgente supervision(long idBroker, long idAgente) {
        SupervisionAgente supervision = new SupervisionAgente();
        supervision.setOrganizacionId(ORG);
        supervision.setIdRolBroker(idBroker);
        supervision.setIdRolAgente(idAgente);
        supervision.setFechaAsignacion(LocalDate.of(2024, 1, 1));
        supervision.setMotivo("Asignacion");
        return supervision;
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

    /** El alta devuelve tambien la membresia desde el Bloque 5 (D-S0-8). */
    private static UsuarioOrganizacion membresia() {
        UsuarioOrganizacion membresia = new UsuarioOrganizacion();
        membresia.setOrganizacionId(ORG);
        membresia.setRol(UsuarioOrganizacion.ROL_AGENTE);
        return membresia;
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

    private static ConteoPorAgente conteo(long id, long total) {
        return new ConteoPorAgente() {
            @Override
            public Long getIdAgente() {
                return id;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
