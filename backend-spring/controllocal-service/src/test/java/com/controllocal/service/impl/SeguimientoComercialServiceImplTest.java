package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.Prospeccion;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.SeguimientoComercialService.Fila;
import com.controllocal.service.SeguimientoComercialService.Filtros;
import com.controllocal.service.SeguimientoComercialService.Resultado;
import com.controllocal.service.soporte.Alcances;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Blinda E4-seguimiento: la forma de las cinco filas, las rutas y los aliases,
 * el techo de 8, la independencia de {@code counts} y {@code options}, y las dos
 * reglas de alcance que se pierden facil (el AGENTE no alcanza por captacion y
 * el cierre depende de su solicitud).
 */
class SeguimientoComercialServiceImplTest {

    private static final long ORG = 1L;
    private static final long AGENTE = 10L;
    private static final long OTRO_AGENTE = 11L;
    private static final long BROKER = 20L;

    private final ProspeccionRepository prospecciones = mock(ProspeccionRepository.class);
    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final OportunidadComercialRepository oportunidades =
            mock(OportunidadComercialRepository.class);
    private final SolicitudAlquilerRepository solicitudes = mock(SolicitudAlquilerRepository.class);
    private final ContratoAlquilerRepository contratos = mock(ContratoAlquilerRepository.class);
    private final Alcances alcances = mock(Alcances.class);

    private final SeguimientoComercialServiceImpl service = new SeguimientoComercialServiceImpl(
            prospecciones, captaciones, oportunidades, solicitudes, contratos, alcances);

    private final Actor admin = new Actor(ORG, 1L, 2L, "TENANT_ADMIN");
    private final Actor broker = new Actor(ORG, 3L, BROKER, "BROKER");
    private final Actor agente = new Actor(ORG, 4L, AGENTE, "AGENTE");

    @BeforeEach
    void vacio() {
        when(alcances.de(admin)).thenReturn(new Alcances.Alcance(ORG, true, List.of()));
        when(alcances.de(broker)).thenReturn(new Alcances.Alcance(ORG, false, List.of(AGENTE)));
        when(alcances.de(agente)).thenReturn(new Alcances.Alcance(ORG, false, List.of(AGENTE)));
        when(prospecciones.listarSeguimiento(anyLong(), anyBoolean(), anyCollection()))
                .thenReturn(List.of());
        when(captaciones.listarSeguimiento(anyLong())).thenReturn(List.of());
        when(oportunidades.listarSeguimiento(anyLong(), anyBoolean(), anyBoolean(), anyCollection()))
                .thenReturn(List.of());
        when(solicitudes.listarSeguimiento(anyLong(), anyBoolean(), anyBoolean(), anyCollection()))
                .thenReturn(List.of());
        when(contratos.listarSeguimiento(anyLong())).thenReturn(List.of());
    }

    // ---------- forma de las filas ----------

    @Test
    void lasCincoEtapasLlevanSuIconoTonoYRuta() {
        escenarioCompleto();

        Map<String, Fila> porProceso = service.listar(filtros("Todos", 1, 8), admin).items().stream()
                .collect(Collectors.toMap(Fila::proceso, Function.identity()));

        assertEquals("store", porProceso.get("Prospeccion").icono());
        assertEquals("blue", porProceso.get("Prospeccion").tono());
        assertEquals("prospeccion-detail/500", porProceso.get("Prospeccion").ruta());

        assertEquals("pin", porProceso.get("Captacion").icono());
        assertEquals("captacion-detail/CAP-400", porProceso.get("Captacion").ruta());

        assertEquals("target", porProceso.get("Oportunidad").icono());
        assertEquals("info", porProceso.get("Oportunidad").tono());
        assertEquals("oportunidad-detail/700", porProceso.get("Oportunidad").ruta());

        assertEquals("fileText", porProceso.get("Solicitud").icono());
        assertEquals("gray", porProceso.get("Solicitud").tono());
        assertEquals("solicitud-detail/SOL-800", porProceso.get("Solicitud").ruta());

        assertEquals("checkCircle", porProceso.get("Cierre").icono());
        assertEquals("green", porProceso.get("Cierre").tono());
        assertEquals("solicitud-detail/SOL-800", porProceso.get("Cierre").ruta());
    }

    @Test
    void soloLaCaptacionPendienteYLaSolicitudEnRevisionTraenRutaDeRevision() {
        DetalleAgente responsable = agente(AGENTE, "Valeria Mora");
        Propiedad local = local(300L, propietario(200L, "Propietaria Uno"));
        Captacion pendiente = captacion(400L, local, responsable, "P");
        Captacion activa = captacion(401L, local, responsable, "A");
        when(captaciones.listarSeguimiento(ORG)).thenReturn(List.of(pendiente, activa));
        SolicitudAlquiler enRevision = solicitud(800L, oportunidad(700L,
                cliente(600L, "Cliente"), responsable, activa), responsable, "E");
        when(solicitudes.listarSeguimiento(anyLong(), anyBoolean(), anyBoolean(), anyCollection()))
                .thenReturn(List.of(enRevision));

        Map<String, Fila> porCodigo = service.listar(filtros("Todos", 1, 8), admin).items().stream()
                .collect(Collectors.toMap(Fila::codigo, Function.identity()));

        assertEquals("captacion-review/CAP-400", porCodigo.get("CAP-400").rutaRevision());
        assertEquals("", porCodigo.get("CAP-401").rutaRevision());
        assertEquals("evaluacion/SOL-800", porCodigo.get("SOL-800").rutaRevision());
    }

    @Test
    void elClienteYElMontoSoloViajanDondeElCableLosLleva() {
        escenarioCompleto();

        Map<String, Fila> porProceso = service.listar(filtros("Todos", 1, 8), admin).items().stream()
                .collect(Collectors.toMap(Fila::proceso, Function.identity()));

        assertEquals("-", porProceso.get("Prospeccion").cliente());
        assertNull(porProceso.get("Prospeccion").clienteId());
        assertEquals("", porProceso.get("Prospeccion").monto());
        assertEquals("-", porProceso.get("Captacion").cliente());
        assertEquals("Cliente Uno", porProceso.get("Oportunidad").cliente());
        assertEquals(600L, porProceso.get("Oportunidad").clienteId());
        assertEquals("", porProceso.get("Oportunidad").monto());
        assertEquals("2500.00", porProceso.get("Solicitud").monto(), "sin quitar los decimales");
        assertEquals("2500.00", porProceso.get("Cierre").monto());
    }

    @Test
    void laVigenciaDeLaCaptacionUsaElFormatoLegibleDelCable() {
        DetalleAgente responsable = agente(AGENTE, "Valeria Mora");
        Propiedad local = local(300L, propietario(200L, "Propietaria Uno"));
        Captacion conVigencia = captacion(400L, local, responsable, "A");
        conVigencia.setFechaFinVigencia(LocalDate.of(2026, 12, 31));
        Captacion sinVigencia = captacion(401L, local, responsable, "A");
        when(captaciones.listarSeguimiento(ORG)).thenReturn(List.of(conVigencia, sinVigencia));

        Map<String, Fila> porCodigo = service.listar(filtros("Captacion", 1, 8), admin).items()
                .stream().collect(Collectors.toMap(Fila::codigo, Function.identity()));

        assertEquals("Vigente hasta 31 Dec 2026", porCodigo.get("CAP-400").ultimoHito());
        assertEquals("Captada el 01 Jul 2026", porCodigo.get("CAP-401").ultimoHito());
    }

    @Test
    void elHitoDeLaProspeccionPrefiereLaCaptacionYLuegoLaFechaMasAvanzada() {
        DetalleAgente responsable = agente(AGENTE, "Valeria Mora");
        Propiedad local = local(300L, propietario(200L, "Propietaria Uno"));
        Prospeccion sinNada = prospeccion(500L, local, responsable);
        Prospeccion conContacto = prospeccion(501L, local, responsable);
        ReflectionTestUtils.setField(conContacto, "fechaContacto", LocalDate.of(2026, 7, 10));
        Prospeccion conPropuesta = prospeccion(502L, local, responsable);
        ReflectionTestUtils.setField(conPropuesta, "fechaContacto", LocalDate.of(2026, 7, 10));
        ReflectionTestUtils.setField(conPropuesta, "fechaPropuesta", LocalDate.of(2026, 7, 20));
        Prospeccion captada = prospeccion(503L, local, responsable);
        ReflectionTestUtils.setField(captada, "captacion",
                captacion(400L, local, responsable, "A"));
        when(prospecciones.listarSeguimiento(anyLong(), anyBoolean(), anyCollection()))
                .thenReturn(List.of(sinNada, conContacto, conPropuesta, captada));

        Map<String, Fila> porCodigo = service.listar(filtros("Prospeccion", 1, 8), admin).items()
                .stream().collect(Collectors.toMap(Fila::codigo, Function.identity()));

        assertEquals("Prospecto", porCodigo.get("PRO-500").ultimoHito());
        assertEquals("Contacto 2026-07-10", porCodigo.get("PRO-501").ultimoHito());
        assertEquals("Propuesta entregada 2026-07-20", porCodigo.get("PRO-502").ultimoHito());
        assertEquals("CAP-400", porCodigo.get("PRO-503").ultimoHito());
    }

    // ---------- propietario por mapa ----------

    @Test
    void elPropietarioDeOportunidadYCierreSaleDelMapaDeCaptaciones() {
        escenarioCompleto();

        List<Fila> items = service.listar(filtros("Todos", 1, 8), admin).items();

        assertTrue(items.stream().allMatch(f -> "Propietaria Uno".equals(f.propietario())),
                "las cinco filas resuelven el mismo propietario");
        assertTrue(items.stream().allMatch(f -> Long.valueOf(200L).equals(f.propietarioId())));
    }

    @Test
    void sinCaptacionEnElMapaElPropietarioViajaComoGuion() {
        DetalleAgente responsable = agente(AGENTE, "Valeria Mora");
        // Un local SIN propietario: el mapa no lo indexa y la fila cae al relleno.
        Propiedad local = local(300L, null);
        Captacion captacion = captacion(400L, local, responsable, "A");
        when(captaciones.listarSeguimiento(ORG)).thenReturn(List.of(captacion));
        when(oportunidades.listarSeguimiento(anyLong(), anyBoolean(), anyBoolean(), anyCollection()))
                .thenReturn(List.of(oportunidad(700L, cliente(600L, "Cliente Uno"),
                        responsable, captacion)));

        Map<String, Fila> porProceso = service.listar(filtros("Todos", 1, 8), admin).items().stream()
                .collect(Collectors.toMap(Fila::proceso, Function.identity()));

        assertEquals("-", porProceso.get("Oportunidad").propietario());
        assertNull(porProceso.get("Oportunidad").propietarioId());
    }

    // ---------- alcance ----------

    @Test
    void elAgenteNoAlcanzaPorCaptacionAunqueLaCaptacionSeaSuya() {
        DetalleAgente propio = agente(AGENTE, "Valeria Mora");
        DetalleAgente ajeno = agente(OTRO_AGENTE, "Otro Agente");
        Propiedad local = local(300L, propietario(200L, "Propietaria Uno"));
        Captacion suya = captacion(400L, local, propio, "A");
        when(captaciones.listarSeguimiento(ORG)).thenReturn(List.of(suya));
        // Oportunidad de OTRO agente sobre una captacion del agente que consulta.
        when(oportunidades.listarSeguimiento(anyLong(), anyBoolean(), anyBoolean(), anyCollection()))
                .thenReturn(List.of(oportunidad(700L, cliente(600L, "Cliente"), ajeno, suya)));

        Resultado resultado = service.listar(filtros("Todos", 1, 8), agente);

        assertEquals(1, resultado.counts().captacion());
        assertEquals(0, resultado.counts().oportunidad(),
                "el AGENTE ve lo suyo y nada mas (la captacion no amplia su alcance)");
    }

    @Test
    void elBrokerSiAlcanzaLaOportunidadAjenaSobreUnaCaptacionDeSuEquipo() {
        DetalleAgente equipo = agente(AGENTE, "Valeria Mora");
        DetalleAgente ajeno = agente(OTRO_AGENTE, "Otro Agente");
        Propiedad local = local(300L, propietario(200L, "Propietaria Uno"));
        Captacion delEquipo = captacion(400L, local, equipo, "A");
        when(captaciones.listarSeguimiento(ORG)).thenReturn(List.of(delEquipo));
        when(oportunidades.listarSeguimiento(anyLong(), anyBoolean(), anyBoolean(), anyCollection()))
                .thenReturn(List.of(oportunidad(700L, cliente(600L, "Cliente"), ajeno, delEquipo)));

        assertEquals(1, service.listar(filtros("Todos", 1, 8), broker).counts().oportunidad());
    }

    @Test
    void unCierreCuyaSolicitudNoEstaEnAlcanceDesapareceDeLaLista() {
        DetalleAgente ajeno = agente(OTRO_AGENTE, "Otro Agente");
        Propiedad local = local(300L, propietario(200L, "Propietaria Uno"));
        Captacion ajena = captacion(400L, local, ajeno, "A");
        SolicitudAlquiler solicitud = solicitud(800L,
                oportunidad(700L, cliente(600L, "Cliente"), ajeno, ajena), ajeno, "C");
        when(contratos.listarSeguimiento(ORG)).thenReturn(List.of(contrato(900L, solicitud, "V")));

        assertEquals(0, service.listar(filtros("Todos", 1, 8), agente).counts().cierre());
        assertEquals(1, service.listar(filtros("Todos", 1, 8), admin).counts().cierre());
    }

    // ---------- filtros, conteos y opciones ----------

    @Test
    void losConteosIgnoranElFiltroDeProcesoPeroAplicanLosDemas() {
        escenarioCompleto();

        Resultado resultado = service.listar(filtros("Solicitud", 1, 8), admin);

        assertEquals(1, resultado.items().size(), "items si respeta el proceso");
        assertEquals(5, resultado.counts().todos(), "counts no");
        assertEquals(1, resultado.counts().prospeccion());
        assertEquals(1, resultado.counts().cierre());
    }

    @Test
    void unFiltroDeAgenteRecortaTambienLosConteos() {
        escenarioCompleto();

        Resultado resultado = service.listar(new Filtros("Todos", "", "inexistente", "", "", "", 1, 8),
                admin);

        assertEquals(0, resultado.items().size());
        assertEquals(0, resultado.counts().todos());
        assertFalse(resultado.options().agentes().isEmpty(),
                "las opciones no llevan filtros: siguen ofreciendo todo");
    }

    @Test
    void lasOpcionesDescartanElRellenoYOrdenanSinDistinguirMayusculas() {
        DetalleAgente unoA = agente(AGENTE, "alberto");
        DetalleAgente unoB = agente(OTRO_AGENTE, "Beatriz");
        Propiedad local = local(300L, propietario(200L, "Propietaria Uno"));
        when(captaciones.listarSeguimiento(ORG)).thenReturn(List.of(
                captacion(400L, local, unoB, "A"),
                captacion(401L, local, unoA, "A"),
                captacion(402L, local, unoA, "A")));

        Resultado resultado = service.listar(filtros("Todos", 1, 8), admin);

        assertEquals(List.of("alberto", "Beatriz"), resultado.options().agentes());
        assertEquals(List.of("Activa"), resultado.options().estados());
        assertFalse(resultado.options().propietarios().contains("-"));
    }

    @Test
    void laBusquedaLibreBarreLosOchoCamposDeTexto() {
        escenarioCompleto();

        for (String termino : List.of("PRO-500", "Cliente Uno", "Av. 300", "Miraflores",
                "Valeria", "Propietaria", "Activa", "oportunidad")) {
            assertFalse(service.listar(new Filtros("Todos", termino, "", "", "", "", 1, 8), admin)
                            .items().isEmpty(),
                    "la busqueda deberia encontrar '" + termino + "'");
        }
    }

    @Test
    void elTamanoDePaginaTieneTechoDeOchoYLaPaginaMinimoUno() {
        escenarioCompleto();

        Resultado grande = service.listar(filtros("Todos", 1, 50), admin);
        assertEquals(8, grande.pageSize(), "8 es techo, no solo defecto");

        Resultado negativa = service.listar(filtros("Todos", -3, 8), admin);
        assertEquals(1, negativa.page());
    }

    @Test
    void unaPaginaFueraDeRangoDevuelveVacioSinRomperElTotal() {
        escenarioCompleto();

        Resultado resultado = service.listar(filtros("Todos", 9, 8), admin);

        assertTrue(resultado.items().isEmpty());
        assertEquals(5, resultado.totalRecords());
    }

    /**
     * El {@code .reversed()} del cable invierte TAMBIEN el tratamiento de los
     * nulos: el {@code nullsLast} de dentro se vuelve nulls-<b>first</b> al
     * reversar, asi que una fila sin fecha encabeza la lista. Es contraintuitivo
     * y es lo que hace la v1; queda fijado aqui para que nadie lo "arregle".
     */
    @Test
    void elOrdenEsFechaDescendenteYLasFilasSinFechaEncabezan() {
        DetalleAgente responsable = agente(AGENTE, "Valeria Mora");
        Propiedad local = local(300L, propietario(200L, "Propietaria Uno"));
        Captacion reciente = captacion(400L, local, responsable, "A");
        reciente.setFechaCaptacion(LocalDate.of(2026, 7, 25));
        Captacion antigua = captacion(401L, local, responsable, "A");
        antigua.setFechaCaptacion(LocalDate.of(2026, 1, 5));
        Captacion sinFecha = captacion(402L, local, responsable, "A");
        sinFecha.setFechaCaptacion(null);
        when(captaciones.listarSeguimiento(ORG)).thenReturn(List.of(antigua, sinFecha, reciente));

        List<String> codigos = service.listar(filtros("Todos", 1, 8), admin).items().stream()
                .map(Fila::codigo).toList();

        assertEquals(List.of("CAP-402", "CAP-400", "CAP-401"), codigos);
    }

    // ---------- escenario y fabricas ----------

    /** Una fila de cada etapa sobre el mismo local, propietario, agente y cliente. */
    private void escenarioCompleto() {
        DetalleAgente responsable = agente(AGENTE, "Valeria Mora");
        DetalleCliente cliente = cliente(600L, "Cliente Uno");
        Propiedad local = local(300L, propietario(200L, "Propietaria Uno"));
        Captacion captacion = captacion(400L, local, responsable, "A");
        OportunidadComercial oportunidad = oportunidad(700L, cliente, responsable, captacion);
        SolicitudAlquiler solicitud = solicitud(800L, oportunidad, responsable, "G");

        when(prospecciones.listarSeguimiento(anyLong(), anyBoolean(), anyCollection()))
                .thenReturn(List.of(prospeccion(500L, local, responsable)));
        when(captaciones.listarSeguimiento(ORG)).thenReturn(List.of(captacion));
        when(oportunidades.listarSeguimiento(anyLong(), anyBoolean(), anyBoolean(), anyCollection()))
                .thenReturn(List.of(oportunidad));
        when(solicitudes.listarSeguimiento(anyLong(), anyBoolean(), anyBoolean(), anyCollection()))
                .thenReturn(List.of(solicitud));
        when(contratos.listarSeguimiento(ORG))
                .thenReturn(List.of(contrato(900L, solicitud, "V")));
    }

    private static Filtros filtros(String proceso, int pagina, int tamano) {
        return new Filtros(proceso, "", "", "", "", "", pagina, tamano);
    }

    private static PersonaRol rol(long id, String nombre) {
        Persona persona = new Persona();
        persona.setNombresORazonSocial(nombre);
        PersonaRol rol = new PersonaRol();
        ReflectionTestUtils.setField(rol, "id", id);
        rol.setPersona(persona);
        return rol;
    }

    private static PersonaRol propietario(long id, String nombre) {
        return rol(id, nombre);
    }

    private static DetalleAgente agente(long id, String nombre) {
        DetalleAgente agente = new DetalleAgente();
        ReflectionTestUtils.setField(agente, "id", id);
        agente.setRol(rol(id, nombre));
        return agente;
    }

    private static DetalleCliente cliente(long id, String nombre) {
        DetalleCliente cliente = new DetalleCliente();
        ReflectionTestUtils.setField(cliente, "id", id);
        cliente.setRol(rol(id, nombre));
        return cliente;
    }

    private static Propiedad local(long id, PersonaRol propietario) {
        Propiedad propiedad = new Propiedad();
        ReflectionTestUtils.setField(propiedad, "id", id);
        propiedad.setCodigo("LOC-" + id);
        propiedad.setDireccion("Av. " + id);
        propiedad.setDistrito("Miraflores");
        propiedad.setRolPropietario(propietario);
        return propiedad;
    }

    private static Captacion captacion(long id, Propiedad local, DetalleAgente agente, String estado) {
        Captacion captacion = new Captacion();
        ReflectionTestUtils.setField(captacion, "id", id);
        ReflectionTestUtils.setField(captacion, "estado", estado);
        captacion.setCodigoCaptacion("CAP-" + id);
        captacion.setFechaCaptacion(LocalDate.of(2026, 7, 1));
        captacion.setPropiedad(local);
        captacion.setAgente(agente);
        return captacion;
    }

    private static Prospeccion prospeccion(long id, Propiedad local, DetalleAgente agente) {
        Prospeccion prospeccion = new Prospeccion();
        ReflectionTestUtils.setField(prospeccion, "id", id);
        ReflectionTestUtils.setField(prospeccion, "estado", "P");
        prospeccion.setCodigoProspeccion("PRO-" + id);
        prospeccion.setPropiedad(local);
        prospeccion.setAgente(agente);
        return prospeccion;
    }

    private static OportunidadComercial oportunidad(long id, DetalleCliente cliente,
                                                    DetalleAgente agente, Captacion captacion) {
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

    private static SolicitudAlquiler solicitud(long id, OportunidadComercial oportunidad,
                                               DetalleAgente agente, String estado) {
        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        ReflectionTestUtils.setField(solicitud, "id", id);
        ReflectionTestUtils.setField(solicitud, "estado", estado);
        solicitud.setCodigoSolicitud("SOL-" + id);
        solicitud.setOportunidad(oportunidad);
        solicitud.setAgente(agente);
        solicitud.setFechaRegistro(LocalDate.of(2026, 7, 21));
        solicitud.setMontoPropuesto(new BigDecimal("2500.00"));
        return solicitud;
    }

    private static ContratoAlquiler contrato(long id, SolicitudAlquiler solicitud, String estado) {
        ContratoAlquiler contrato = new ContratoAlquiler();
        ReflectionTestUtils.setField(contrato, "id", id);
        ReflectionTestUtils.setField(contrato, "estadoContrato", estado);
        contrato.setSolicitud(solicitud);
        contrato.setOportunidad(solicitud.getOportunidad());
        contrato.setFechaCierre(LocalDate.of(2026, 7, 29));
        return contrato;
    }
}
