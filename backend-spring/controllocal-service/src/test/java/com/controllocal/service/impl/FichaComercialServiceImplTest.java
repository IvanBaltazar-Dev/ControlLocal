package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.Prospeccion;
import com.controllocal.domain.comercial.RequerimientoCliente;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.DetalleClienteRepository;
import com.controllocal.persistence.repositorio.InteraccionComercialRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.RequerimientoClienteRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValoresDePropiedad;
import com.controllocal.service.Actor;
import com.controllocal.service.FichaComercialService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FichaComercialServiceImplTest {

    private static final long ORG = 1L;
    private static final Actor ADMIN = new Actor(ORG, 1L, 1L, "TENANT_ADMIN");
    private static final Actor AGENTE = new Actor(ORG, 10L, 10L, "AGENTE");
    private static final Actor BROKER = new Actor(ORG, 20L, 20L, "BROKER");

    @Mock private DetalleClienteRepository clientes;
    @Mock private PersonaRolRepository roles;
    @Mock private RequerimientoClienteRepository requerimientos;
    @Mock private ProspeccionRepository prospecciones;
    @Mock private CaptacionRepository captaciones;
    @Mock private OportunidadComercialRepository oportunidades;
    @Mock private InteraccionComercialRepository interacciones;
    @Mock private VisitaRepository visitas;
    @Mock private SolicitudAlquilerRepository solicitudes;
    @Mock private ContratoAlquilerRepository contratos;
    @Mock private Alcances alcances;

    private FichaComercialServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new FichaComercialServiceImpl(
                clientes, roles, requerimientos, prospecciones, captaciones,
                oportunidades, interacciones, visitas, solicitudes, contratos, alcances,
                lectorSinGobernados());
        lenient().when(alcances.de(ADMIN)).thenReturn(new Alcances.Alcance(ORG, true, List.of()));
        lenient().when(alcances.de(AGENTE)).thenReturn(new Alcances.Alcance(ORG, false, List.of(10L)));
        lenient().when(alcances.de(BROKER)).thenReturn(new Alcances.Alcance(ORG, false, List.of(31L)));
    }

    @Test
    void fichaClienteCargaRequerimientosYDejaElRestoPendiente() {
        DetalleCliente cliente = cliente(101L, "Comercial Andina");
        RequerimientoCliente requerimiento = requerimiento(501L, cliente, "Retail", RequerimientoCliente.ACTIVO);
        stubCliente(cliente);
        when(requerimientos.listarPorCliente(ORG, 101L)).thenReturn(List.of(requerimiento));

        FichaComercialService.FichaCliente ficha =
                servicio.fichaCliente(101L, 99, AGENTE);

        assertEquals("Comercial Andina", ficha.cliente().nombre());
        assertTrue(ficha.requerimientoActivo());
        assertEquals("/oportunidad-form?clienteId=101", ficha.ctaRuta());
        assertEquals(8, ficha.sections().size());
        assertEquals(1, ficha.sections().get("requerimientos").totalRecords());
        assertEquals(8, ficha.sections().get("requerimientos").pageSize());
        assertEquals(-1, ficha.sections().get("propiedades").totalRecords());
        assertEquals(0, ficha.sections().get("propiedades").page());
    }

    @Test
    void fichaClienteNoOfreceCtaAlBroker() {
        DetalleCliente cliente = cliente(101L, "Cliente");
        RequerimientoCliente requerimiento = requerimiento(501L, cliente, "Retail", RequerimientoCliente.ACTIVO);
        OportunidadComercial oportunidad = oportunidad(701L, cliente, agente(31L, "Equipo"), null);
        stubCliente(cliente);
        when(requerimientos.listarPorCliente(ORG, 101L)).thenReturn(List.of(requerimiento));
        when(oportunidades.listarFichaPorCliente(ORG, 101L)).thenReturn(List.of(oportunidad));

        FichaComercialService.FichaCliente ficha =
                servicio.fichaCliente(101L, 8, BROKER);

        assertTrue(ficha.requerimientoActivo());
        assertEquals("", ficha.ctaRuta());
    }

    @Test
    void fichaPropietarioCargaLocalesYResumeProspeccionesYCaptaciones() {
        PersonaRol propietario = propietario(201L, "Inversiones Sur");
        Propiedad local = local(301L, propietario, "LOC-301");
        DetalleAgente agente = agente(10L, "Agente Uno");
        Captacion captacion = captacion(401L, local, agente, null);
        Prospeccion prospeccion = prospeccion(402L, local, agente);
        stubPropietario(propietario);
        when(captaciones.listarFichaPorPropietario(ORG, 201L)).thenReturn(List.of(captacion));
        when(prospecciones.listarFichaPorPropietario(ORG, 201L)).thenReturn(List.of(prospeccion));

        FichaComercialService.FichaPropietario ficha =
                servicio.fichaPropietario(201L, 8, AGENTE);

        assertEquals(0, ficha.propietario().cantidadLocales());
        assertEquals(7, ficha.sections().size());
        assertEquals(1, ficha.sections().get("locales").items().size());
        assertEquals(1, ficha.sections().get("prospecciones").totalRecords());
        assertTrue(ficha.sections().get("prospecciones").items().isEmpty());
        assertEquals(1, ficha.sections().get("captaciones").totalRecords());
        assertEquals(-1, ficha.sections().get("cierres").totalRecords());
    }

    @Test
    void agenteVeSoloSuHistoriaOcultaNombreYNoVeSeccionAgentes() {
        DetalleCliente cliente = cliente(101L, "Cliente");
        OportunidadComercial propia = oportunidad(701L, cliente, agente(10L, "Propio"), null);
        OportunidadComercial ajena = oportunidad(702L, cliente, agente(99L, "Ajeno"), null);
        stubCliente(cliente);
        when(oportunidades.listarFichaPorCliente(ORG, 101L)).thenReturn(List.of(propia, ajena));

        FichaComercialService.SeccionFicha oportunidadesVisibles =
                servicio.seccionCliente(101L, " OPORTUNIDADES ", 1, 8, AGENTE);
        FichaComercialService.SeccionFicha agentesVisibles =
                servicio.seccionCliente(101L, "agentes", 1, 8, AGENTE);

        assertEquals(1, oportunidadesVisibles.totalRecords());
        assertEquals("-", oportunidadesVisibles.items().getFirst().agente());
        assertEquals(0, agentesVisibles.totalRecords());
        assertTrue(agentesVisibles.items().isEmpty());
    }

    @Test
    void brokerSoloVeHistoriaDeSuEquipo() {
        DetalleCliente cliente = cliente(101L, "Cliente");
        OportunidadComercial visible = oportunidad(701L, cliente, agente(31L, "Equipo"), null);
        OportunidadComercial oculta = oportunidad(702L, cliente, agente(32L, "Fuera"), null);
        stubCliente(cliente);
        when(oportunidades.listarFichaPorCliente(ORG, 101L)).thenReturn(List.of(visible, oculta));

        FichaComercialService.SeccionFicha seccion =
                servicio.seccionCliente(101L, "oportunidades", 1, 8, BROKER);

        assertEquals(1, seccion.totalRecords());
        assertEquals("Equipo", seccion.items().getFirst().agente());
    }

    @Test
    void solicitudMuestraSuMonedaRealYNoAsumeUsdCuandoFalta() {
        DetalleCliente cliente = cliente(101L, "Cliente");
        DetalleAgente agente = agente(10L, "Agente Uno");
        OportunidadComercial oportunidad = oportunidad(701L, cliente, agente, null);
        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        ReflectionTestUtils.setField(solicitud, "id", 801L);
        solicitud.setCodigoSolicitud("SOL-801");
        solicitud.setOportunidad(oportunidad);
        solicitud.setAgente(agente);
        solicitud.setMontoPropuesto(new BigDecimal("7000"));
        solicitud.setMoneda("PEN");
        stubCliente(cliente);
        when(solicitudes.listarFichaPorCliente(ORG, 101L)).thenReturn(List.of(solicitud));

        FichaComercialService.FilaFicha conMoneda =
                servicio.seccionCliente(101L, "solicitudes", 1, 8, ADMIN).items().getFirst();
        assertEquals("Oferta PEN 7000", conMoneda.subtitulo());

        solicitud.setMoneda(null);
        FichaComercialService.FilaFicha sinMoneda =
                servicio.seccionCliente(101L, "solicitudes", 1, 8, ADMIN).items().getFirst();
        assertEquals("Oferta Moneda no definida 7000", sinMoneda.subtitulo());
    }

    @Test
    void brokerPuedeEntrarPorCaptacionQueRevisa() {
        DetalleCliente cliente = cliente(101L, "Cliente");
        Captacion captacion = captacion(401L, local(301L, propietario(201L, "Prop"), "LOC"),
                agente(32L, "Fuera"), broker(20L));
        OportunidadComercial oportunidad =
                oportunidad(701L, cliente, agente(32L, "Fuera"), captacion);
        stubCliente(cliente);
        when(oportunidades.listarFichaPorCliente(ORG, 101L)).thenReturn(List.of(oportunidad));

        FichaComercialService.SeccionFicha seccion =
                servicio.seccionCliente(101L, "oportunidades", 1, 8, BROKER);

        assertEquals(1, seccion.totalRecords());
    }

    @Test
    void brokerSinHistoriaVisibleRecibe403() {
        DetalleCliente cliente = cliente(101L, "Cliente");
        stubCliente(cliente);

        assertThrows(AccesoNoAutorizadoException.class,
                () -> servicio.fichaCliente(101L, 8, BROKER));
    }

    @Test
    void idDeOtroTenantSeComportaComo404YNoConsultaHistoria() {
        when(clientes.buscarFicha(ORG, 999L)).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class,
                () -> servicio.fichaCliente(999L, 8, ADMIN));
        verifyNoInteractions(requerimientos, interacciones, visitas, solicitudes, contratos);
    }

    @Test
    void seccionInvalidaConservaMensajesExactos() {
        DetalleCliente cliente = cliente(101L, "Cliente");
        PersonaRol propietario = propietario(201L, "Propietario");
        stubCliente(cliente);
        stubPropietario(propietario);

        ReglaNegocioException errorCliente = assertThrows(ReglaNegocioException.class,
                () -> servicio.seccionCliente(101L, "desconocida", 1, 8, AGENTE));
        ReglaNegocioException errorPropietario = assertThrows(ReglaNegocioException.class,
                () -> servicio.seccionPropietario(201L, "desconocida", 1, 8, AGENTE));

        assertEquals("Seccion de ficha de cliente no valida.", errorCliente.getMessage());
        assertEquals("Seccion de ficha de propietario no valida.", errorPropietario.getMessage());
    }

    @Test
    void paginaYTamanoSeNormalizanYOrdenanPorFechaDescendente() {
        DetalleCliente cliente = cliente(101L, "Cliente");
        List<RequerimientoCliente> filas = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            RequerimientoCliente r = requerimiento(id, cliente, "R" + id, RequerimientoCliente.PAUSADO);
            ReflectionTestUtils.setField(r, "fechaActualizacion",
                    OffsetDateTime.parse("2026-07-%02dT10:00:00Z".formatted(id)));
            filas.add(r);
        }
        stubCliente(cliente);
        when(requerimientos.listarPorCliente(ORG, 101L)).thenReturn(filas);

        FichaComercialService.SeccionFicha primera =
                servicio.seccionCliente(101L, "requerimientos", 0, 100, ADMIN);
        FichaComercialService.SeccionFicha segunda =
                servicio.seccionCliente(101L, "requerimientos", 2, 3, ADMIN);

        assertEquals(1, primera.page());
        assertEquals(8, primera.pageSize());
        assertEquals("10", primera.items().getFirst().id());
        assertEquals(List.of("7", "6", "5"),
                segunda.items().stream().map(FichaComercialService.FilaFicha::id).toList());
    }

    @Test
    void cierreUsaTextoYRutaCongelados() {
        DetalleCliente cliente = cliente(101L, "Cliente Cierre");
        DetalleAgente agente = agente(10L, "Agente Uno");
        Captacion captacion = captacion(401L,
                local(301L, propietario(201L, "Propietario"), "LOC-301"), agente, null);
        OportunidadComercial oportunidad = oportunidad(701L, cliente, agente, captacion);
        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        ReflectionTestUtils.setField(solicitud, "id", 801L);
        solicitud.setCodigoSolicitud("SOL-801");
        solicitud.setOportunidad(oportunidad);
        solicitud.setAgente(agente);
        ContratoAlquiler contrato = new ContratoAlquiler();
        ReflectionTestUtils.setField(contrato, "id", 901L);
        ReflectionTestUtils.setField(contrato, "estadoContrato", "V");
        contrato.setSolicitud(solicitud);
        contrato.setOportunidad(oportunidad);
        contrato.setFechaCierre(LocalDate.of(2026, 7, 29));
        stubCliente(cliente);
        when(solicitudes.listarFichaPorCliente(ORG, 101L)).thenReturn(List.of(solicitud));
        when(contratos.listarFichaPorCliente(ORG, 101L)).thenReturn(List.of(contrato));

        FichaComercialService.FilaFicha fila =
                servicio.seccionCliente(101L, "cierres", 1, 8, AGENTE).items().getFirst();

        assertEquals("Cierre", fila.proceso());
        assertEquals("Cliente Cierre alquilo este local. Contrato vigente.", fila.subtitulo());
        assertEquals("solicitud-detail/SOL-801", fila.ruta());
        assertEquals("green", fila.tono());
    }

    @Test
    void todasLasSeccionesAdmitidasResponden() {
        stubCliente(cliente(101L, "Cliente"));
        stubPropietario(propietario(201L, "Propietario"));

        for (String seccion : List.of("requerimientos", "propiedades", "oportunidades",
                "interacciones", "visitas", "solicitudes", "cierres", "agentes")) {
            assertEquals(seccion,
                    servicio.seccionCliente(101L, seccion, 1, 8, ADMIN).section());
        }
        for (String seccion : List.of("locales", "prospecciones", "captaciones",
                "oportunidades", "solicitudes", "cierres", "agentes")) {
            assertEquals(seccion,
                    servicio.seccionPropietario(201L, seccion, 1, 8, ADMIN).section());
        }
    }

    private void stubCliente(DetalleCliente cliente) {
        when(clientes.buscarFicha(ORG, cliente.getId())).thenReturn(Optional.of(cliente));
    }

    private void stubPropietario(PersonaRol propietario) {
        when(roles.buscarPropietario(ORG, propietario.getId())).thenReturn(Optional.of(propietario));
    }

    private static DetalleCliente cliente(long id, String nombre) {
        PersonaRol rol = rol(id, nombre);
        DetalleCliente cliente = new DetalleCliente();
        ReflectionTestUtils.setField(cliente, "id", id);
        cliente.setRol(rol);
        cliente.setRubroComercial("Retail");
        return cliente;
    }

    private static PersonaRol propietario(long id, String nombre) {
        return rol(id, nombre);
    }

    private static PersonaRol rol(long id, String nombre) {
        Persona persona = new Persona();
        persona.setNombresORazonSocial(nombre);
        persona.setEstado("A");
        PersonaRol rol = new PersonaRol();
        ReflectionTestUtils.setField(rol, "id", id);
        rol.setPersona(persona);
        return rol;
    }

    private static DetalleAgente agente(long id, String nombre) {
        DetalleAgente agente = new DetalleAgente();
        ReflectionTestUtils.setField(agente, "id", id);
        agente.setRol(rol(id, nombre));
        agente.setCodigoAgente("AGE-" + id);
        agente.setEstadoOperativo("D");
        return agente;
    }

    private static DetalleBroker broker(long id) {
        DetalleBroker broker = new DetalleBroker();
        ReflectionTestUtils.setField(broker, "id", id);
        broker.setRol(rol(id, "Broker"));
        return broker;
    }

    private static RequerimientoCliente requerimiento(
            long id, DetalleCliente cliente, String rubro, String estado) {
        RequerimientoCliente requerimiento = new RequerimientoCliente();
        ReflectionTestUtils.setField(requerimiento, "id", id);
        requerimiento.setCliente(cliente);
        requerimiento.setRubro(rubro);
        requerimiento.setEstado(estado);
        return requerimiento;
    }

    private static Propiedad local(long id, PersonaRol propietario, String codigo) {
        Propiedad propiedad = new Propiedad();
        ReflectionTestUtils.setField(propiedad, "id", id);
        propiedad.iniciarDisponible();
        propiedad.setCodigo(codigo);
        propiedad.setDireccion("Av. E3 " + id);
        propiedad.setDistrito("Miraflores");
        propiedad.setRolPropietario(propietario);
        return propiedad;
    }

    private static Captacion captacion(long id, Propiedad propiedad, DetalleAgente agente,
                                       DetalleBroker broker) {
        Captacion captacion = new Captacion();
        ReflectionTestUtils.setField(captacion, "id", id);
        ReflectionTestUtils.setField(captacion, "estado", "A");
        captacion.setCodigoCaptacion("CAP-" + id);
        captacion.setFechaCaptacion(LocalDate.of(2026, 7, 1));
        captacion.setPropiedad(propiedad);
        captacion.setAgente(agente);
        ReflectionTestUtils.setField(captacion, "brokerRevisor", broker);
        return captacion;
    }

    private static Prospeccion prospeccion(long id, Propiedad propiedad, DetalleAgente agente) {
        Prospeccion prospeccion = new Prospeccion();
        ReflectionTestUtils.setField(prospeccion, "id", id);
        ReflectionTestUtils.setField(prospeccion, "estado", "P");
        ReflectionTestUtils.setField(prospeccion, "fechaRegistro",
                OffsetDateTime.parse("2026-07-01T10:00:00Z"));
        prospeccion.setCodigoProspeccion("PRO-" + id);
        prospeccion.setPropiedad(propiedad);
        prospeccion.setAgente(agente);
        return prospeccion;
    }

    private static OportunidadComercial oportunidad(
            long id, DetalleCliente cliente, DetalleAgente agente, Captacion captacion) {
        OportunidadComercial oportunidad = new OportunidadComercial();
        ReflectionTestUtils.setField(oportunidad, "id", id);
        ReflectionTestUtils.setField(oportunidad, "estado", "A");
        oportunidad.setCodigoOportunidad("OP-" + id);
        oportunidad.setCliente(cliente);
        oportunidad.setAgente(agente);
        oportunidad.setCaptacion(captacion);
        oportunidad.setFechaRegistro(OffsetDateTime.parse("2026-07-20T10:00:00Z"));
        return oportunidad;
    }

    /**
     * Un lector que no encuentra ningun valor gobernado.
     *
     * <p>Estas pruebas no afirman nada sobre el rubro; solo necesitan que el
     * servicio pueda construir su ficha. Devolver lotes vacios —y no un mock
     * sin respuestas— es lo correcto: {@code ValoresDePropiedad.vacio()}
     * significa "no se sabe nada de esta propiedad", que es un estado legitimo
     * del dominio, mientras que un null seria un fallo del andamiaje
     * disfrazado de dato.
     */
    private static LectorPorAutoridad lectorSinGobernados() {
        LectorPorAutoridad lector = mock(LectorPorAutoridad.class);
        lenient().when(lector.de(anyLong(), any())).thenReturn(ValoresDePropiedad.vacio());
        lenient().when(lector.deVarias(anyLong(), any())).thenReturn(Map.of());
        lenient().when(lector.gobernadosDeVarias(any())).thenReturn(Map.of());
        return lector;
    }
}
