package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.inmueble.ObservacionMercado;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.persistence.repositorio.ObservacionMercadoRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ObservacionMercadoService.DatosObservacion;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** RF013/AC054/CAT-E09: hecho observado separado del pacto y de sus precios. */
class ObservacionMercadoServiceImplTest {
    private static final Actor AGENTE = new Actor(7, 11, 13, Actor.AGENTE);
    private static final Actor BROKER = new Actor(7, 17, 19, Actor.BROKER);
    private static final long PROPIEDAD = 23;
    private static final LocalDate HOY_LIMA = LocalDate.of(2026, 10, 28);
    private static final Procedencia PANTALLA = Procedencia.deLaPantalla();
    private static final Procedencia ORIGEN = Procedencia.deAgente("API", "TEST-KAIROS", null, null,
            "test-conversacion", "test-turno", "test-mensaje", "Registrar lo observado sin precio")
            .invocando("registrar_observacion_mercado");
    private final ObservacionMercadoRepository observaciones = mock(ObservacionMercadoRepository.class);
    private final AutoridadDePropiedad autoridad = mock(AutoridadDePropiedad.class);
    private final PersonaRolRepository roles = mock(PersonaRolRepository.class);
    private final ComandoIdempotenteRepository registros = mock(ComandoIdempotenteRepository.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Map<String, ComandoIdempotente> recibos = new HashMap<>();
    private final AtomicLong ids = new AtomicLong(40);
    private final ObservacionMercadoServiceImpl servicio = new ObservacionMercadoServiceImpl(
            observaciones, autoridad, roles, new ComandosIdempotentes(registros), registros, json,
            Clock.fixed(Instant.parse("2026-10-29T02:30:00Z"), ZoneOffset.UTC));

    @BeforeEach void contexto() {
        when(autoridad.puedeLeer(AGENTE, PROPIEDAD)).thenReturn(true);
        when(observaciones.saveAndFlush(any(ObservacionMercado.class))).thenAnswer(invocacion -> {
            ObservacionMercado fila = invocacion.getArgument(0);
            ReflectionTestUtils.setField(fila, "id", ids.incrementAndGet());
            return fila;
        });
        when(registros.findByOrganizacionIdAndClaveIdempotencia(anyLong(), anyString())).thenAnswer(invocacion ->
                Optional.ofNullable(recibos.get(identidad(invocacion.getArgument(0), invocacion.getArgument(1)))));
        when(registros.save(any(ComandoIdempotente.class))).thenAnswer(invocacion -> {
            ComandoIdempotente fila = invocacion.getArgument(0);
            recibos.put(identidad(fila.getOrganizacionId(), fila.getClaveIdempotencia()), fila);
            return fila;
        });
    }

    @Test void importeDesconocidoPermaneceNullConFuenteFechaOperacionYProcedencia() throws Exception {
        var resultado = servicio.registrar(datos(null, null), AGENTE, null, ORIGEN);
        assertNull(resultado.importe());
        assertNull(resultado.moneda());
        assertEquals(HOY_LIMA, resultado.fechaObservada());
        assertEquals("VENTA", resultado.operacion());
        assertEquals("PORTAL_TEST", resultado.fuente());
        assertEquals(AGENTE.idRolOperativo(), resultado.idRolActor());
        var captura = ArgumentCaptor.forClass(ObservacionMercado.class);
        verify(observaciones).saveAndFlush(captura.capture());
        var fila = captura.getValue();
        assertEquals(AGENTE.idOrganizacion(), fila.getOrganizacionId());
        assertEquals(PROPIEDAD, fila.getIdPropiedad());
        assertNull(fila.getImporte());
        assertNull(fila.getMoneda());
        assertEquals(json.readTree(json.writeValueAsString(ORIGEN)), json.readTree(fila.getProcedencia()));
        assertThrows(IllegalStateException.class, () -> fila.conProcedencia("{}"));
        verifyNoInteractions(registros, roles);
    }

    @Test void ceroDeclaradoEsDistintoDeNoConocerElImporte() {
        var resultado = servicio.registrar(datos(BigDecimal.ZERO, "PEN"), AGENTE, null, PANTALLA);
        assertEquals(BigDecimal.ZERO, resultado.importe());
        assertEquals("PEN", resultado.moneda());
    }

    @ParameterizedTest @ValueSource(strings = {"", " "})
    void importeAusenteNoGeneraMonedaOCeroPorUnaEntradaVacia(String moneda) {
        var resultado = servicio.registrar(datos(null, moneda), AGENTE, null, PANTALLA);
        assertNull(resultado.importe());
        assertNull(resultado.moneda());
    }

    @Test void monedaSinImporteSeRechazaAntesDePersistir() {
        assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(datos(null, "USD"), AGENTE, null, PANTALLA));
        verifyNoInteractions(observaciones, registros);
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "ZZZ"})
    void importeNoInventaMonedaSiFaltaOEsInvalida(String moneda) {
        assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(datos(new BigDecimal("100"), moneda), AGENTE, null, PANTALLA));
        verifyNoInteractions(observaciones, registros);
    }

    @Test void importeConMonedaNullNoFabricaUnaMoneda() {
        assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(datos(new BigDecimal("100"), null), AGENTE, null, PANTALLA));
        verifyNoInteractions(observaciones, registros);
    }

    @Test void importeNegativoNoSeGuardaComoObservado() {
        assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(datos(new BigDecimal("-1"), "PEN"), AGENTE, null, PANTALLA));
        verifyNoInteractions(observaciones, registros);
    }

    @Test void fechaLocalDeLimaAdmiteHoyAunqueUtcYaSeaElDiaSiguiente() {
        assertEquals(HOY_LIMA, servicio.registrar(datos(null, null), AGENTE, null, PANTALLA).fechaObservada());
        var mananaLocal = new DatosObservacion(PROPIEDAD, HOY_LIMA.plusDays(1), "VENTA", null, null,
                "Portal TEST", "Fecha que en UTC ya comenzó");
        assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(mananaLocal, AGENTE, null, PANTALLA));
        verify(observaciones, times(1)).saveAndFlush(any(ObservacionMercado.class));
    }

    @Test void fuenteFechaYOperacionSiguenSiendoExplicitasAunqueNoSeConozcaImporte() {
        List<DatosObservacion> incompletas = List.of(
                new DatosObservacion(PROPIEDAD, null, "VENTA", null, null, "PORTAL", null),
                new DatosObservacion(PROPIEDAD, HOY_LIMA, null, null, null, "PORTAL", null),
                new DatosObservacion(PROPIEDAD, HOY_LIMA, "VENTA", null, null, null, null));
        for (DatosObservacion datos : incompletas)
            assertThrows(ReglaNegocioException.class, () -> servicio.registrar(datos, AGENTE, null, PANTALLA));
        verifyNoInteractions(observaciones, registros);
    }

    @ParameterizedTest @ValueSource(strings = {Actor.BROKER, Actor.TENANT_ADMIN, "BROX_PLATFORM", "REGULADOR"})
    void otroRolNoRegistraNiPreleeElInmueble(String banda) {
        Actor actor = new Actor(7, 17, 19, banda);
        assertThrows(AccesoNoAutorizadoException.class,
                () -> servicio.registrar(datos(null, null), actor, "clave-test", PANTALLA));
        verifyNoInteractions(autoridad, observaciones, registros, roles);
    }

    @ParameterizedTest @ValueSource(strings = {Actor.TENANT_ADMIN, "BROX_PLATFORM", "REGULADOR"})
    void gobiernoOReguladorNoRecibenLaSerieDelTenant(String banda) {
        Actor actor = new Actor(7, 17, 19, banda);
        assertThrows(AccesoNoAutorizadoException.class, () -> servicio.listarDe(PROPIEDAD, actor));
        verifyNoInteractions(autoridad, observaciones, registros, roles);
    }

    @Test void brokerConLecturaVigenteConsultaLaSerieSinAdquirirRegistro() {
        when(autoridad.puedeLeer(BROKER, PROPIEDAD)).thenReturn(true);
        when(observaciones.findByIdPropiedadOrderByFechaObservadaDescIdDesc(PROPIEDAD)).thenReturn(List.of());
        assertTrue(servicio.listarDe(PROPIEDAD, BROKER).isEmpty());
        assertThrows(AccesoNoAutorizadoException.class,
                () -> servicio.registrar(datos(null, null), BROKER, null, PANTALLA));
        verify(autoridad).puedeLeer(BROKER, PROPIEDAD);
        verify(observaciones).findByIdPropiedadOrderByFechaObservadaDescIdDesc(PROPIEDAD);
        verifyNoMoreInteractions(observaciones, autoridad);
    }

    @Test void mismoIdDePersonaYRolEnOtroTenantNoHeredaLecturaNiResultadoPorNombre() {
        Actor otroTenant = new Actor(71, AGENTE.idPersona(), AGENTE.idRolOperativo(), Actor.AGENTE);
        assertThrows(NoEncontradoException.class, () -> servicio.listarDe(PROPIEDAD, otroTenant));
        assertThrows(NoEncontradoException.class,
                () -> servicio.registrar(datos(null, null), otroTenant, "clave-test", PANTALLA));
        verifyNoInteractions(observaciones, registros, roles);
    }

    @Test void perderAlcanceNoSeConvierteEnSerieVaciaNiEnAltaAutorizada() {
        when(autoridad.puedeLeer(AGENTE, PROPIEDAD)).thenReturn(false);
        assertThrows(NoEncontradoException.class, () -> servicio.listarDe(PROPIEDAD, AGENTE));
        assertThrows(NoEncontradoException.class,
                () -> servicio.registrar(datos(null, null), AGENTE, null, PANTALLA));
        verifyNoInteractions(observaciones, registros, roles);
    }

    @Test void falloSqlDeLaSerieSePropagaSinPresentarseComoVacio() {
        var fallo = new IllegalStateException("Fallo SQL TEST");
        when(observaciones.findByIdPropiedadOrderByFechaObservadaDescIdDesc(PROPIEDAD)).thenThrow(fallo);
        assertSame(fallo, assertThrows(IllegalStateException.class, () -> servicio.listarDe(PROPIEDAD, AGENTE)));
        verifyNoInteractions(roles);
    }

    @Test void nombresSeResuelvenPorLosRolesDeLaSerieAutorizadaEnUnaConsulta() {
        var fila = new ObservacionMercado(7L, PROPIEDAD, HOY_LIMA, "V", null, null,
                "PORTAL", "Observación TEST", AGENTE.idRolOperativo());
        var anterior = new ObservacionMercado(7L, PROPIEDAD, HOY_LIMA.minusDays(1), "V", null, null,
                "CARTEL", "Otra observación TEST", AGENTE.idRolOperativo());
        ReflectionTestUtils.setField(fila, "id", 41L);
        ReflectionTestUtils.setField(anterior, "id", 42L);
        Persona persona = mock(Persona.class);
        PersonaRol rol = mock(PersonaRol.class);
        when(persona.getNombresORazonSocial()).thenReturn("Nombre TEST repetible");
        when(rol.getPersona()).thenReturn(persona);
        when(rol.getId()).thenReturn(AGENTE.idRolOperativo());
        when(observaciones.findByIdPropiedadOrderByFechaObservadaDescIdDesc(PROPIEDAD)).thenReturn(List.of(fila, anterior));
        when(roles.findAllById(List.of(AGENTE.idRolOperativo()))).thenReturn(List.of(rol));
        var resultado = servicio.listarDe(PROPIEDAD, AGENTE);
        assertEquals(2, resultado.size());
        assertTrue(resultado.stream().allMatch(o -> "Nombre TEST repetible".equals(o.actorNombre())));
        verify(roles).findAllById(List.of(AGENTE.idRolOperativo()));
        verifyNoMoreInteractions(roles);
    }

    @Test void reintentoEntregaElSnapshotOriginalSinVolverAGuardarNiCompletarNombres() {
        var datos = datos(null, null);
        var original = servicio.registrar(datos, AGENTE, "observacion-test", ORIGEN);
        assertNull(original.actorNombre());
        var repetida = servicio.registrar(datos, AGENTE, "observacion-test", ORIGEN);
        assertEquals(original, repetida);
        verify(autoridad, times(2)).puedeLeer(AGENTE, PROPIEDAD);
        verify(observaciones, times(1)).saveAndFlush(any(ObservacionMercado.class));
        verifyNoMoreInteractions(observaciones);
        verifyNoInteractions(roles);
        assertEquals(1, recibos.size());
        verify(registros, times(2)).bloquearIdentidad(anyLong());
        var recibo = recibos.values().iterator().next();
        assertEquals(AGENTE.idOrganizacion(), recibo.getOrganizacionId());
        assertEquals(original.id(), recibo.getEntidadId());
        assertEquals("REGISTRAR_OBSERVACION_MERCADO", recibo.getTipoComando());
        assertEquals("OBSERVACION_MERCADO", recibo.getEntidadTipo());
    }

    @Test void revocarLecturaAntesDelReplayImpideEntregarElOriginalYConsultarSuRecibo() {
        var datos = datos(null, null);
        servicio.registrar(datos, AGENTE, "observacion-test", ORIGEN);
        clearInvocations(observaciones, registros, autoridad);
        when(autoridad.puedeLeer(AGENTE, PROPIEDAD)).thenReturn(false);
        assertThrows(NoEncontradoException.class,
                () -> servicio.registrar(datos, AGENTE, "observacion-test", ORIGEN));
        verifyNoInteractions(observaciones, registros);
        assertEquals(1, recibos.size());
    }

    private static DatosObservacion datos(BigDecimal importe, String moneda) {
        return new DatosObservacion(PROPIEDAD, HOY_LIMA, "VENTA", importe, moneda,
                "Portal TEST", "Referencia aportada por el profesional TEST");
    }

    private static String identidad(long organizacion, String clave) {
        return organizacion + "\u0000" + clave;
    }
}
