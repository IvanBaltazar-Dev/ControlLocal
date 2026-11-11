package com.controllocal.service.captura;

import com.controllocal.domain.captura.BorradorCaptura;
import com.controllocal.domain.inmueble.AplicacionAtributo;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Exigencia;
import com.controllocal.persistence.repositorio.BorradorCapturaRepository;
import com.controllocal.persistence.repositorio.CatalogoAtributoRepository;
import com.controllocal.persistence.repositorio.DistritoRepository;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.GobiernoCatalogoCapturaService;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.captura.MotorDeCaptura.Avance;
import com.controllocal.service.captura.MotorDeCaptura.Respuesta;
import com.controllocal.service.captura.SuficienciaInmueble.Contexto;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AtributosDeEncargo;
import com.controllocal.service.soporte.AtributosGobernados;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** MAT-01/02, CAT-E08/E11/E39 y CAT18–20; PostgreSQL/CAS real en la suite T4. */
class MotorDeCapturaImplTest {
    private static final Actor AGENTE = new Actor(41, 51, 61, Actor.AGENTE);
    private static final Procedencia PANTALLA = Procedencia.deLaPantalla();
    private final BorradorCapturaRepository borradores = mock(BorradorCapturaRepository.class);
    private final CatalogoAtributoRepository definiciones = mock(CatalogoAtributoRepository.class);
    private final PropiedadUniversalService propiedades = mock(PropiedadUniversalService.class);
    private final AtributosGobernados gobierno = mock(AtributosGobernados.class);
    private final AtributosDeEncargo condiciones = mock(AtributosDeEncargo.class);
    private final DistritoRepository distritos = mock(DistritoRepository.class);
    private final EventoDominioRepository eventos = mock(EventoDominioRepository.class);
    private final GobiernoCatalogoCapturaService catalogos = mock(GobiernoCatalogoCapturaService.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Documentos documentos = new Documentos(mapper);
    private final SuficienciaInmueble suficiencia = new SuficienciaInmueble();
    private final SuficienciaRegistrada registrada = new SuficienciaRegistrada(mock(LectorPorAutoridad.class), mapper,
            suficiencia, catalogos);
    private BorradorCaptura borrador;
    private MotorDeCapturaImpl motor;

    @BeforeEach void preparar() {
        motor = motor(preparacion("2026-10-28T15:00:00Z"));
        borrador = BorradorCaptura.abrir(AGENTE.idOrganizacion(), "CAP-00001", AGENTE.idRolOperativo(),
                MotorDeCaptura.REGISTRAR_PROPIEDAD, "SPA", null);
        ReflectionTestUtils.setField(borrador, "id", 71L);
        ReflectionTestUtils.setField(borrador, "version", 7);
        ReflectionTestUtils.setField(borrador, "creadoEn", OffsetDateTime.parse("2026-10-25T15:00:00Z"));
        when(borradores.findByOrganizacionIdAndIdAndIdPersonaRol(41, 71, 61)).thenReturn(Optional.of(borrador));
        when(borradores.save(any())).thenAnswer(invocacion -> {
            BorradorCaptura guardado = invocacion.getArgument(0);
            if (guardado.getId() == null) ReflectionTestUtils.setField(guardado, "id", 72L);
            return guardado;
        });
        when(borradores.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(catalogos.opcionesVigentes(anyLong(), anyString(), nullable(String.class)))
                .thenReturn(new GobiernoCatalogoCapturaService.OpcionesCatalogo(null, null, List.of(), "SIN_CATALOGO"));
    }

    @Test void parcialDeDepartamentoEnSurcoConservaBorradorYPreparacionSinFisicoNiEncargo() {
        var estado = motor.avanzar(new Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD, null, null,
                Map.of("tipoPropiedad", Respuesta.conocida("DEPARTAMENTO"), "distrito", Respuesta.conocida("Surco")), null),
                PANTALLA, AGENTE);
        assertNotNull(estado.idBorrador());
        assertFalse(estado.listoParaEjecutar());
        assertFalse(estado.suficiencia().suficiente());
        assertEquals("PREPARACION_CONTACTO", estado.retorno().grupos().getFirst().resultado());
        assertEquals("SIN_REFERENCIAS_ELEGIBLES", estado.retorno().referenciaPrecio().estado());
        assertNull(estado.idEntidad());
        verifyNoInteractions(propiedades);
    }

    @Test void confirmarFisicoNoConvierteOperacionesObservadasEnMandatos() {
        borrador.anotar(documentos.objeto(Map.of("tipoPropiedad", "DEPARTAMENTO", "distrito", "Surco",
                "interiorUnidad", "501", "direccion", "Av. Prueba 123", "operaciones", "VENTA,ALQUILER",
                "importe:VENTA", 180000, "moneda:VENTA", "USD", "importe:ALQUILER", 2500, "moneda:ALQUILER", "PEN")), "[]");
        when(propiedades.registrar(any(), eq(AGENTE)))
                .thenReturn(new PropiedadUniversalService.ResultadoRegistro(81L, "PROP-81", List.of(), false));
        var ejecucion = motor.ejecutar(71, 7, "registro-c2-test", PANTALLA, AGENTE);
        var comando = ArgumentCaptor.forClass(PropiedadUniversalService.ComandoRegistro.class);
        verify(propiedades).registrar(comando.capture(), eq(AGENTE));
        assertTrue(comando.getValue().operaciones().isEmpty());
        assertTrue(ejecucion.idsEncargos().isEmpty());
        assertEquals(BorradorCaptura.EJECUTADO, borrador.getEstado());
    }

    @Test void cadaEscrituraExistenteExigeLaVersionObservadaSinSuplantarla() {
        assertThrows(ReglaNegocioException.class, () -> motor.avanzar(new Avance(null, 71L, null, Map.of(), null), PANTALLA, AGENTE));
        assertThrows(ConflictoException.class, () -> motor.avanzar(new Avance(null, 71L, 6, Map.of(), null), PANTALLA, AGENTE));
        assertThrows(ConflictoException.class, () -> motor.descartar(71, 6, PANTALLA, AGENTE));
        assertThrows(ConflictoException.class, () -> motor.posponerRevision(71, 6, LocalDate.of(2026, 10, 29), "Acuerdo", PANTALLA, AGENTE));
        verify(borradores, never()).saveAndFlush(any());
        verifyNoInteractions(eventos, propiedades);
    }

    @Test void otroActorOTenantNoObtieneLaCapturaYAdministrarNoPermiteOperarla() {
        assertThrows(NoEncontradoException.class, () -> motor.consultar(71, new Actor(41, 52, 62, Actor.AGENTE)));
        assertThrows(NoEncontradoException.class, () -> motor.consultar(71, new Actor(42, 51, 61, Actor.AGENTE)));
        assertThrows(AccesoNoAutorizadoException.class, () -> motor.consultar(71, new Actor(41, 51, 61, Actor.TENANT_ADMIN)));
    }

    @Test void noSaberConservaElFaltantePeroRetiraLaInsistenciaDelMismoDato() {
        borrador.anotar(documentos.objeto(Map.of("tipoPropiedad", "DEPARTAMENTO", "distrito", "Surco", "direccion", "Av. Prueba 123")), "[]");
        var estado = motor.avanzar(new Avance(null, 71L, 7, Map.of("interiorUnidad",
                new Respuesta(null, EstadoDato.FALTANTE, "declaracion", null, null, null, null, true)), null), PANTALLA, AGENTE);
        assertTrue(estado.faltante().contains("interiorUnidad"));
        assertNull(estado.siguiente());
        assertTrue(estado.metadatos().get("interiorUnidad").noSabe());
        assertFalse(estado.retorno().preguntasPertinentes().contains("interiorUnidad"));
    }

    @Test void posponerRespetaMotivoYEdadYVuelveEnLaFechaElegida() {
        borrador.anotar(documentos.objeto(Map.of("tipoPropiedad", "DEPARTAMENTO", "distrito", "Surco")), "[]");
        var estado = motor.posponerRevision(71, 7, LocalDate.of(2026, 10, 29), "  Retomar por acuerdo  ", PANTALLA, AGENTE);
        assertEquals("POSPUESTA", estado.retorno().revision().atencion());
        assertEquals(3, estado.retorno().revision().diasDesdeDeteccion());
        assertEquals("Retomar por acuerdo", estado.retorno().revision().motivoPosposicion());
        assertTrue(estado.retorno().preguntasPertinentes().isEmpty());
        assertTrue(estado.retorno().revision().acciones().isEmpty());
        var alVencer = motor(preparacion("2026-10-29T15:00:00Z")).consultar(71, AGENTE);
        assertEquals("COMPLETAR_O_DESCARTAR", alVencer.retorno().revision().atencion());
        assertEquals(4, alVencer.retorno().revision().diasDesdeDeteccion());
        assertFalse(alVencer.retorno().revision().acciones().isEmpty());
    }

    @Test void posponerRechazaHoyPasadoOSinMotivoAntesDeModificarElBorrador() {
        assertThrows(ReglaNegocioException.class, () -> motor.posponerRevision(71, 7, LocalDate.of(2026, 10, 28), "Acuerdo", PANTALLA, AGENTE));
        assertThrows(ReglaNegocioException.class, () -> motor.posponerRevision(71, 7, LocalDate.of(2026, 10, 27), "Acuerdo", PANTALLA, AGENTE));
        assertThrows(ReglaNegocioException.class, () -> motor.posponerRevision(71, 7, LocalDate.of(2026, 10, 29), " ", PANTALLA, AGENTE));
        assertNull(borrador.getRevisionPospuestaHasta());
        verify(borradores, never()).saveAndFlush(any());
        verifyNoInteractions(eventos);
    }

    @Test void descarteConservaElAntecedenteYRetiraPreguntasYAcciones() {
        borrador.anotar(documentos.objeto(Map.of("tipoPropiedad", "DEPARTAMENTO", "distrito", "Surco")), "[]");
        var estado = motor.descartar(71, 7, PANTALLA, AGENTE);
        assertEquals(BorradorCaptura.DESCARTADO, estado.estado());
        assertEquals("Surco", estado.conocido().get("distrito"));
        assertTrue(estado.faltante().isEmpty());
        assertNull(estado.retorno().revision());
        assertTrue(estado.retorno().preguntasPertinentes().isEmpty());
        assertTrue(estado.retorno().grupos().stream().allMatch(grupo -> grupo.acciones().isEmpty()));
        verify(borradores, never()).delete(any());
    }

    @Test void falloDePreparacionSeDistingueYNoRevierteElAvanceCorrecto() {
        PreparacionDeCaptura fallida = mock(PreparacionDeCaptura.class);
        when(fallida.preparar(anyMap(), anyMap(), any(), anyList(), any(), anyBoolean()))
                .thenThrow(new IllegalStateException("Preparación interrumpida"));
        var estado = motor(fallida).avanzar(new Avance(null, 71L, 7,
                Map.of("distrito", Respuesta.conocida("Surco")), null), PANTALLA, AGENTE);
        assertEquals("FALLO_TECNICO", estado.retorno().estado());
        assertEquals("NO_EVALUADA", estado.retorno().referenciaPrecio().estado());
        assertEquals("Surco", documentos.comoMapa(borrador.getDatosConocidos()).get("distrito"));
        verify(borradores).saveAndFlush(borrador);
        verify(eventos).save(any());
        verifyNoInteractions(propiedades);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "5", "12"})
    void sugerenciasDeAscensoresAdmitenCualquierEnteroNoNegativo(String valor) {
        atributo("ascensores", "ENTERO", "D").setValorMinimo(java.math.BigDecimal.ZERO);
        var estado = motor.avanzar(new Avance(null, 71L, 7,
                Map.of("tipoPropiedad", Respuesta.conocida("DEPARTAMENTO"), "ascensores", Respuesta.conocida(valor)), null), PANTALLA, AGENTE);
        assertEquals(valor, estado.conocido().get("ascensores"));
        var pregunta = motor.definicion(null, "DEPARTAMENTO", null, null, AGENTE).delTipo().stream()
                .filter(p -> "ascensores".equals(p.clave())).findFirst().orElseThrow();
        assertEquals("ENTERO_SUGERIDO", pregunta.control());
        assertEquals(List.of("0", "1", "2", "3", "4"), pregunta.opciones().stream().map(MotorDeCaptura.Opcion::valor).toList());
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "1.5", "Otro"})
    void unaSugerenciaNoConvierteTextoNiFraccionesEnCantidad(String valor) {
        atributo("ascensores", "ENTERO", "D").setValorMinimo(java.math.BigDecimal.ZERO);
        assertThrows(ReglaNegocioException.class, () -> motor.avanzar(new Avance(null, 71L, 7,
                Map.of("ascensores", Respuesta.conocida(valor)), null), PANTALLA, AGENTE));
        verify(borradores, never()).saveAndFlush(any());
    }

    @Test void zonificacionFueraDelCatalogoConservaFuentePropuestaEIncidenciaSinValorConocido() {
        atributo("zonificacion", "LISTA", "T");
        when(catalogos.validarOpcion(eq(41L), eq("zonificacion"), eq("NO-FIGURA"), any(), eq("TEST-LIMA")))
                .thenReturn(new GobiernoCatalogoCapturaService.ValidacionOpcion(false, "OPCION_NO_LOCALIZADA", null, null, null, null));
        var contexto = new Contexto("TERRENO", null, null, null, Map.of(), Set.of(), "TEST-LIMA");
        var estado = motor.avanzar(new Avance(null, 71L, 7, Map.of("zonificacion",
                new Respuesta("NO-FIGURA", EstadoDato.CONOCIDO, "documento-municipal-test", null, "CAT:P:90:3", "3", null, false)), contexto), PANTALLA, AGENTE);
        assertFalse(estado.conocido().containsKey("zonificacion"));
        var meta = estado.metadatos().get("zonificacion");
        assertEquals(EstadoDato.FALTANTE, meta.estado());
        assertEquals("NO-FIGURA", meta.valorEnRevision());
        assertEquals("documento-municipal-test", meta.evidenciaRef());
        assertEquals("OPCION_NO_LOCALIZADA", meta.incidencia());
    }

    @Test void zonificacionValidadaUsaOpcionesRealesDeJurisdiccionYSuVersionSinOtro() {
        atributo("zonificacion", "LISTA", "T");
        when(catalogos.opcionesVigentes(41, "zonificacion", "TEST-LIMA"))
                .thenReturn(new GobiernoCatalogoCapturaService.OpcionesCatalogo("CAT:P:90:3", 3L,
                        List.of(new GobiernoCatalogoCapturaService.OpcionCatalogo("TEST-Z", "Zona documentada TEST")), null));
        when(catalogos.validarOpcion(41, "zonificacion", "TEST-Z", "CAT:P:90:3", "TEST-LIMA"))
                .thenReturn(new GobiernoCatalogoCapturaService.ValidacionOpcion(true, null, "CAT:P:90:3", "TEST-Z", "Zona documentada TEST", 3L));
        var contexto = new Contexto("TERRENO", null, null, null, Map.of(), Set.of(), "TEST-LIMA");
        var estado = motor.avanzar(new Avance(null, 71L, 7, Map.of("zonificacion",
                new Respuesta("TEST-Z", EstadoDato.CONOCIDO, "documento-municipal-test", null,
                        "CAT:P:90:3", "3", LocalDate.of(2026, 10, 27), false)), contexto), PANTALLA, AGENTE);
        assertEquals("TEST-Z", estado.conocido().get("zonificacion"));
        assertEquals("CAT:P:90:3", estado.metadatos().get("zonificacion").referenciaCatalogo());
        assertEquals("3", estado.metadatos().get("zonificacion").versionDefinicion());
        var pregunta = motor.definicion(null, "TERRENO", null, "TEST-LIMA", AGENTE).delTipo().stream()
                .filter(p -> "zonificacion".equals(p.clave())).findFirst().orElseThrow();
        assertEquals("CATALOGO_CERRADO", pregunta.control());
        assertEquals(List.of(new MotorDeCaptura.Opcion("TEST-Z", "Zona documentada TEST")), pregunta.opciones());
        assertFalse(pregunta.obligatoria(), "La zonificación del terreno no bloquea guardar o identificar.");
    }

    @Test void unaDefinicionFisicaCambiadaNoGuardaLaRespuestaDeLaVersionAnterior() {
        atributo("ascensores", "ENTERO", "D");
        assertThrows(ConflictoException.class, () -> motor.avanzar(new Avance(null, 71L, 7,
                Map.of("ascensores", new Respuesta("5", EstadoDato.CONOCIDO, "documento-test", null,
                        null, "0", null, false)), null), PANTALLA, AGENTE));
        verify(borradores, never()).saveAndFlush(any());
        verifyNoInteractions(eventos);
    }

    @Test void contextoYRespuestasNoPuedenDeclararDosTiposOUsoDistintos() {
        var contexto = new Contexto("CASA", "VIVIENDA", "Casa TEST", null, Map.of(), Set.of());
        assertThrows(ReglaNegocioException.class, () -> motor.avanzar(new Avance(null, 71L, 7,
                Map.of("tipoPropiedad", Respuesta.conocida("TERRENO")), contexto), PANTALLA, AGENTE));
        assertThrows(ReglaNegocioException.class, () -> motor.avanzar(new Avance(null, 71L, 7,
                Map.of("uso", Respuesta.conocida("COMERCIAL")), contexto), PANTALLA, AGENTE));
    }

    private CatalogoAtributo atributo(String clave, String tipoDato, String tipoPropiedad) {
        var atributo = new CatalogoAtributo();
        atributo.setClave(clave); atributo.setRotulo(clave); atributo.setTipoDato(tipoDato);
        atributo.setAplicaciones(Set.of(new AplicacionAtributo(tipoPropiedad, Exigencia.OPC)));
        ReflectionTestUtils.setField(atributo, "versionDefinicion", 1L);
        var actual = mock(CatalogoAtributoRepository.DefinicionActual.class);
        when(actual.getVersion()).thenReturn(1L);
        when(definiciones.actualParaCaptura(41, clave)).thenReturn(Optional.of(actual));
        when(definiciones.bloquearActualParaCaptura(41, clave)).thenReturn(Optional.of(actual));
        when(gobierno.definicionDe(41, clave)).thenReturn(atributo);
        when(gobierno.aplicablesA(41, tipoPropiedad)).thenReturn(List.of(atributo));
        return atributo;
    }
    private PreparacionDeCaptura preparacion(String instante) {
        return new PreparacionDeCaptura(mapper, Clock.fixed(Instant.parse(instante), ZoneOffset.UTC));
    }
    private MotorDeCapturaImpl motor(PreparacionDeCaptura preparacion) {
        return new MotorDeCapturaImpl(borradores, propiedades, gobierno, condiciones, documentos, distritos,
                suficiencia, registrada, preparacion, eventos, catalogos,
                new ValidadorRespuestaCaptura(gobierno, condiciones, catalogos, definiciones));
    }
}
