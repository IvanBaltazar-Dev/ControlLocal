package com.controllocal.service.impl;

import com.controllocal.domain.seguridad.CuentaAcceso;
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
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class AgenteServiceImplTest {

    private static final long ORG = 1L;
    private static final long BROKER = 20L;
    private static final long ADMIN = 10L;
    private static final long AGENTE = 30L;

    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final DetalleBrokerRepository brokers = mock(DetalleBrokerRepository.class);
    private final SupervisionAgenteRepository supervisiones =
            mock(SupervisionAgenteRepository.class);
    private final PersonaRepository personas = mock(PersonaRepository.class);
    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final OportunidadComercialRepository oportunidades =
            mock(OportunidadComercialRepository.class);
    private final UsuariosInternos usuarios = mock(UsuariosInternos.class);
    private final SolicitudAlquilerRepository solicitudes =
            mock(SolicitudAlquilerRepository.class);
    private final ContratoAlquilerRepository contratos = mock(ContratoAlquilerRepository.class);

    private final AgenteServiceImpl service = new AgenteServiceImpl(
            agentes, brokers, supervisiones, personas,
            captaciones, oportunidades, solicitudes, contratos, usuarios);

    private final Actor broker = new Actor(ORG, 2L, BROKER, "BROKER");
    private final Actor admin = new Actor(ORG, 1L, ADMIN, "TENANT_ADMIN");
    private final Actor agenteActor = new Actor(ORG, 3L, AGENTE, "AGENTE");
    private final Map<Long,String> estadosLocales=new HashMap<>();

    @BeforeEach void membresias() {
        when(usuarios.estadoLocalDelRol(anyLong(),anyLong(),any())).thenAnswer(i->estadosLocales.getOrDefault(i.getArgument(1),"A"));
        when(usuarios.estadosLocalesDelRol(anyLong(),anyCollection(),any())).thenAnswer(i->{
            Map<Long,String> r=new HashMap<>();for(Long id:(Collection<Long>)i.getArgument(1))r.put(id,estadosLocales.getOrDefault(id,"A"));return r;
        });
    }

    @Test
    void elBrokerListaSoloSuPaginaYLosContadoresLleganAgrupados() {
        prepararBroker(BROKER, 2L, false, "rsalas");
        DetalleAgente agente = agente(AGENTE, 3L);
        // `sinScope=false` es lo que acota el conjunto al equipo del broker: la
        // consulta es una sola y el rol decide el parametro, no el metodo.
        when(agentes.buscar(eq(ORG), eq(false), eq(BROKER), isNull(), isNull(), isNull(),
                isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(agente)));
        when(usuarios.cuentasPorPersona(ORG, List.of(3L)))
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

    @Test
    void elPadronDeGobiernoOmiteActividadSinConsultarCartera() {
        DetalleAgente fila = agente(AGENTE, 3L);
        when(agentes.buscar(eq(ORG), eq(true), anyLong(), isNull(), isNull(), isNull(),
                isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(fila)));
        when(usuarios.cuentasPorPersona(ORG, List.of(3L)))
                .thenReturn(Map.of(3L, credencial(fila.getRol().getPersona(), "vmora", "A")));

        var proyeccion = service.listar(1, 50, admin).items().getFirst();

        assertNull(proyeccion.captacionesActivas());
        assertNull(proyeccion.operacionesActivas());
        verifyNoInteractions(captaciones, oportunidades, solicitudes, contratos);
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

    @Test
    void elPadronExigeUnaAtribucionExplicitaInclusoSinHttp() {
        assertThrows(AccesoNoAutorizadoException.class, () -> service.listar(1, 50,
                new Actor(ORG, 1L, ADMIN, "IDENTIDAD")));
        assertThrows(AccesoNoAutorizadoException.class, () -> service.listar(1, 50,
                new Actor(ORG, 1L, ADMIN, null)));
        verifyNoInteractions(agentes);
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
                .thenReturn(List.of(generada("PEN", "10000.00","4000.00")));
        when(contratos.repartosDeAgente(ORG, AGENTE))
                .thenReturn(List.of(reparto("PEN", "3000.00","2000.00")));
        when(contratos.movimientosDeAgente(ORG, AGENTE))
                .thenReturn(List.of(movimiento("PEN", "6000.00", "1000.00")));

        var comisiones = service.ficha(AGENTE, broker).comisiones();

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
                .thenReturn(List.of(generada("USD", "1000.00","0")));
        when(contratos.movimientosDeAgente(ORG, AGENTE))
                .thenReturn(List.of(movimiento("USD", "1500.00", "0.00")));

        var comisiones = service.ficha(AGENTE, broker).comisiones();

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

        var ficha = service.ficha(AGENTE, broker);

        assertEquals("Aprobado", ficha.captaciones().getFirst().descripcion());
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
        prepararBroker(BROKER, 2L, false, "rsalas");
        when(agentes.buscarFicha(ORG, 999L)).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class, () -> service.ficha(999L, broker));
    }

    /** Sin supervisión vigente no subsiste la autoridad comercial de otro equipo. */
    @Test
    void sinSupervisionVigenteElBrokerNoObtieneElExpediente() {
        prepararFicha();
        when(supervisiones.buscarActivaPorAgente(ORG, AGENTE)).thenReturn(Optional.empty());

        assertThrows(AccesoNoAutorizadoException.class, () -> service.ficha(AGENTE, broker));
    }

    @Test
    void administrarNoPermiteConsultarCarteraNiComisiones() {
        assertThrows(AccesoNoAutorizadoException.class, () -> service.ficha(AGENTE, admin));
        verifyNoInteractions(agentes, captaciones, oportunidades, solicitudes, contratos);
    }

    @Test
    void elPerfilDeGobiernoSeSeleccionaExactamenteSinConsultarCartera() {
        var fila=agente(AGENTE,3L);when(agentes.buscarFicha(ORG,AGENTE)).thenReturn(Optional.of(fila));
        when(usuarios.cuenta(ORG,3L)).thenReturn(credencial(fila.getRol().getPersona(),"vmora","A"));
        var perfil=service.consultarParaGobierno(AGENTE,admin);
        assertEquals(AGENTE,perfil.id());assertNull(perfil.captacionesActivas());assertNull(perfil.operacionesActivas());
        verifyNoInteractions(captaciones,oportunidades,solicitudes,contratos);
        assertThrows(AccesoNoAutorizadoException.class,()->service.consultarParaGobierno(AGENTE,broker));
    }

    private void prepararFicha() {
        prepararBroker(BROKER, 2L, false, "rsalas");
        DetalleAgente agente = agente(AGENTE, 3L);
        when(agentes.buscarFicha(ORG, AGENTE)).thenReturn(Optional.of(agente));
        when(usuarios.cuenta(ORG, 3L))
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

    @Test void sobrerecibidoDeUnaComisionNoCompensaElSaldoDeOtra(){
        prepararFicha();
        when(contratos.comisionesGeneradasDeAgente(ORG,AGENTE))
                .thenReturn(List.of(generada("PEN","20000","10000")));
        when(contratos.movimientosDeAgente(ORG,AGENTE))
                .thenReturn(List.of(movimiento("PEN","15000","0")));
        assertEquals(new BigDecimal("10000"),service.ficha(AGENTE,broker).comisiones().pendienteCobro().getFirst().monto());
    }
    @Test void basePendienteNoSeTransformaEnCeroYLaCoberturaSigueVisible(){
        prepararFicha();var fila=org.mockito.Mockito.mock(ComisionGeneradaPorMoneda.class);
        when(fila.getMoneda()).thenReturn("PEN");when(fila.getDeterminados()).thenReturn(0L);
        when(fila.getPendientes()).thenReturn(1L);when(fila.getSaldoDeterminados()).thenReturn(0L);
        when(fila.getSaldoPendientes()).thenReturn(1L);
        when(contratos.comisionesGeneradasDeAgente(ORG,AGENTE)).thenReturn(List.of(fila));
        var datos=service.ficha(AGENTE,broker).comisiones();
        assertNull(datos.generada().getFirst().monto());assertEquals(1L,datos.generada().getFirst().pendientes());
        assertNull(datos.pendienteCobro().getFirst().monto());
    }
    @Test void corrupcionDeUnMovimientoNoSeOcultaConUnMaximoCero(){
        prepararFicha();var fila=org.mockito.Mockito.mock(MovimientoComisionPorMoneda.class);
        when(fila.getAnomalia()).thenReturn(true);
        when(contratos.movimientosDeAgente(ORG,AGENTE)).thenReturn(List.of(fila));
        assertThrows(IllegalStateException.class,()->service.ficha(AGENTE,broker));
    }
    private static ComisionGeneradaPorMoneda generada(String moneda, String monto,String saldo) {
        return new ComisionGeneradaPorMoneda() {
            @Override public String getMoneda() { return moneda; }
            @Override public BigDecimal getMonto() { return new BigDecimal(monto); }
            @Override public BigDecimal getSaldoInformativoSobreCalculo(){return new BigDecimal(saldo);}
            @Override public Long getDeterminados(){return 1L;}
            @Override public Long getPendientes(){return 0L;}
            @Override public Long getSaldoDeterminados(){return 1L;}
            @Override public Long getSaldoPendientes(){return 0L;}
        };
    }

    private static RepartoComisionPorMoneda reparto(String moneda, String parte,String saldo) {
        return new RepartoComisionPorMoneda() {
            @Override public String getMoneda() { return moneda; }
            @Override public BigDecimal getParteAgente() { return new BigDecimal(parte); }
            @Override public BigDecimal getSaldoPagoAgente(){return new BigDecimal(saldo);}
            @Override public Long getDeterminados(){return 1L;}
            @Override public Long getPendientes(){return 0L;}
            @Override public Long getSaldoDeterminados(){return 1L;}
            @Override public Long getSaldoPendientes(){return 0L;}
        };
    }

    private static MovimientoComisionPorMoneda movimiento(String moneda, String cobrado,
                                                          String pagado) {
        return new MovimientoComisionPorMoneda() {
            @Override public String getMoneda() { return moneda; }
            @Override public BigDecimal getMontoCobrado() { return new BigDecimal(cobrado); }
            @Override public BigDecimal getMontoPagadoAgente() { return new BigDecimal(pagado); }
            @Override public Boolean getAnomalia(){return false;}
        };
    }



    /**
     * D-S0-17 fila 17: el alta cambio de dueno. Antes la hacia el broker y el
     * administrador estaba EXPRESAMENTE excluido; ahora es al reves. Este test
     * fijaba la regla vieja y ahora fija la nueva — la comprobacion no
     * desaparece, se da la vuelta.
     */








    /**
     * Fila 18: la edicion paso a ser gobierno. El filtro de supervision que
     * habia aqui protegia al broker de tocar agentes ajenos; ahora el broker no
     * edita NINGUN agente, ni el suyo, asi que la proteccion se sustituye por
     * una mas fuerte y este test la fija.
     */
    @Test
    void elBrokerYaNoEditaAgentes() {
        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.actualizar(AGENTE, datosEdicion(), broker));

        verifyNoInteractions(agentes);
    }

    @Test
    void elPutSoloTocaLosCamposPermitidosYRespondeContadoresEnCero() {
        prepararBroker(BROKER, 2L, false, "rsalas");
        DetalleAgente agente = agente(AGENTE, 3L);
        CuentaAcceso credencial =
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
        when(usuarios.cuenta(ORG, 3L)).thenReturn(credencial);

        var ficha = service.actualizar(AGENTE, new AgenteService.DatosAgente(
                "Valentina Editada", "999111222", "editada@test.pe", "Nueva zona", "L"), admin);

        assertEquals("Valentina Editada", ficha.nombre());
        assertEquals("D", ficha.tipoDocumento());
        assertEquals("11111111", ficha.numeroDocumento());
        assertEquals("vmora", ficha.usuario());
        assertEquals("AGE-001", ficha.codigoAgente());
        assertEquals("A", ficha.estadoAdministrativo());
        assertEquals("L", ficha.estadoOperativo());
        assertNull(ficha.captacionesActivas());
        assertNull(ficha.operacionesActivas());
        verifyNoInteractions(captaciones, oportunidades, solicitudes, contratos);
    }



    private void prepararBroker(long id, long idPersona,
                                boolean esAdmin, String usuario) {
        DetalleBroker detalle = broker(id, idPersona, esAdmin);
        when(brokers.buscarFicha(ORG, id)).thenReturn(Optional.of(detalle));
        when(usuarios.cuenta(ORG, idPersona))
                .thenReturn(credencial(detalle.getRol().getPersona(), usuario, "A"));
    }

    private static AgenteService.DatosAgente datosEdicion() {
        return new AgenteService.DatosAgente(
                "Agente Nuevo", null, null, "Lima", null);
    }

    private static DetalleBroker broker(long id, long idPersona, boolean esAdmin) {
        DetalleBroker broker = new DetalleBroker();
        broker.setOrganizacionId(ORG);
        broker.setRol(rol(id, persona(idPersona, "Broker"), TipoRol.BROKER));
        broker.setCodigoBroker(esAdmin ? "BRK-ADM-001" : "BRK-001");
        broker.setFechaDesignacion(LocalDate.of(2024, 1, 1));
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


    private static CuentaAcceso credencial(
            Persona persona, String usuario, String estado) {
        CuentaAcceso credencial = new CuentaAcceso();
        credencial.setId(90L);
        credencial.setCorreoPropio(usuario + "@cuenta.example");
        credencial.setCorreoConfirmadoEn(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        credencial.setNombreUsuario(usuario);
        credencial.setContrasenaHash("pbkdf2$100000$x$x");
        credencial.setEstado(estado);
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
