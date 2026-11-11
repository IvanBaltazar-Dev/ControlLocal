package com.controllocal.service.captura;

import com.controllocal.service.captura.MotorDeCaptura.MetadatoRespuesta;
import com.controllocal.service.captura.SuficienciaInmueble.Contexto;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** CAT18–20, MAT-01/02 y plan IU45–62: preparación determinista sin mercado ficticio. */
class PreparacionDeCapturaTest {
    private static final OffsetDateTime DETECCION = OffsetDateTime.parse("2026-10-28T04:30:00Z");
    private final PreparacionDeCaptura preparacion = conReloj("2026-10-28T05:30:00Z");

    @Test void cadaClaveAprobadaPerteneceAUnSoloGrupoDeLosDieciseis() throws Exception {
        var grupos = preparacion.definiciones();
        assertEquals(16, grupos.size());
        Map<String, String> porClave = new LinkedHashMap<>();
        grupos.forEach(grupo -> grupo.claves().forEach(clave ->
                assertNull(porClave.put(clave, grupo.codigo()), "Clave duplicada: " + clave)));
        assertEquals(126, porClave.size());

        Path raiz = Path.of("").toAbsolutePath();
        while (raiz != null && !Files.isDirectory(raiz.resolve("backend-spring"))) raiz = raiz.getParent();
        assertNotNull(raiz, "El paquete privado de autoridad acompaña esta comprobación.");
        var lineas = Files.readAllLines(raiz.resolve("docs/1INF50/matriz-recompensas-captura.csv"));
        Map<String, String> aprobado = new LinkedHashMap<>();
        for (String linea : lineas.subList(1, lineas.size())) {
            if (linea.isBlank()) continue;
            int separador = linea.indexOf(',');
            String grupo = linea.substring(separador + 1).replaceFirst("^\"", "");
            aprobado.put(linea.substring(0, separador), grupo.substring(0, 4));
        }
        assertEquals(aprobado, porClave, "La asignación deriva de la matriz aprobada, sin grupo genérico.");
    }

    @Test void departamentoEnSurcoEntregaContactoYFaltantesPertinentesSinPrecio() {
        var retorno = preparacion.preparar(Map.of("tipoPropiedad", "DEPARTAMENTO", "distrito", "Surco"),
                Map.of(), null, List.of("tipoPropiedad", "distrito"), DETECCION, true);
        var contacto = grupo(retorno, "CU01");
        assertEquals("PREPARACION_CONTACTO", contacto.resultado());
        assertEquals("Surco", contacto.datos().get("distrito"));
        assertEquals("DEPARTAMENTO", contacto.datos().get("tipoPropiedad"));
        assertTrue(contacto.acciones().contains("PREPARAR_PRIMER_CONTACTO"));
        assertEquals(List.of("contexto.operacion", "direccion", "interiorUnidad"), retorno.preguntasPertinentes());
        assertEquals("SIN_REFERENCIAS_ELEGIBLES", retorno.referenciaPrecio().estado());
        assertFalse(contacto.datos().containsKey("precio"));
        assertEquals(List.of("tipoPropiedad", "distrito"), retorno.cambios());
    }

    @Test void respuestasConocidasYNoSaberNoVuelvenAPreguntarseEnElMismoContexto() {
        var metadatos = Map.of("direccion", noSabe(), "interiorUnidad", noSabe());
        var contexto = new Contexto("DEPARTAMENTO", null, null, "VENTA", Map.of(), Set.of());
        var retorno = preparacion.preparar(Map.of("tipoPropiedad", "DEPARTAMENTO", "distrito", "Surco"),
                metadatos, contexto, List.of(), DETECCION, true);
        assertEquals(List.of("metraje_total"), retorno.preguntasPertinentes());
        assertEquals("VENTA", grupo(retorno, "CU01").datos().get("operacionObservada"));
    }

    @Test void areasSeConservanSeparadasConConceptoSinCrearSumaNiConversion() {
        var conocidos = Map.<String, Object>of("tipoPropiedad", "CASA", "area_terreno", 120,
                "metraje_construido", 200, "distrito", "Surco");
        var metadatos = Map.of("area_terreno", area(SuficienciaInmueble.AREA_TERRENO),
                "metraje_construido", area(SuficienciaInmueble.AREA_CONSTRUIDA));
        var retorno = preparacion.preparar(conocidos, metadatos, null, List.of("metraje_construido"), DETECCION, false);
        var ficha = grupo(retorno, "CU02");
        assertEquals(120, ficha.datos().get("area_terreno"));
        assertEquals(200, ficha.datos().get("metraje_construido"));
        assertFalse(ficha.datos().containsKey("metraje_total"));
        assertEquals(Map.of("area_terreno", "AREA_TERRENO", "metraje_construido", "AREA_CONSTRUIDA"),
                ficha.datos().get("conceptos"));
        assertNull(retorno.revision(), "Identificación suficiente retira la revisión de completitud.");
    }

    @Test void ceroAscensoresSigueSiendoUnDatoDeRecorridoUtil() {
        var retorno = preparacion.preparar(Map.of("tipoPropiedad", "DEPARTAMENTO", "piso", 5, "ascensores", 0),
                Map.of(), null, List.of("piso", "ascensores"), DETECCION, true);
        var visita = grupo(retorno, "CU04");
        assertEquals("RECORRIDO_VISITA", visita.resultado());
        assertEquals(0, visita.datos().get("ascensores"));
        assertEquals(5, visita.datos().get("piso"));
    }

    @ParameterizedTest
    @CsvSource({"2026-10-28T04:59:00Z,0,BAJA", "2026-10-28T05:00:00Z,1,MEDIA",
            "2026-10-29T05:00:00Z,2,MEDIA", "2026-10-30T05:00:00Z,3,COMPLETAR_O_DESCARTAR",
            "2026-11-02T05:00:00Z,6,COMPLETAR_O_DESCARTAR"})
    void unaRevisionAgrupadaRespetaLaFechaOriginalYElDiaLocalDeLima(String instante, long dias, String atencion) {
        var retorno = conReloj(instante).preparar(Map.of("tipoPropiedad", "DEPARTAMENTO"), Map.of(),
                null, List.of("descripcion"), DETECCION, true);
        assertEquals(dias, retorno.revision().diasDesdeDeteccion());
        assertEquals(atencion, retorno.revision().atencion());
        assertEquals(List.of("CONTINUAR_CAPTURA", "POSPONER_REVISION_PROPIA", "DESCARTAR_CAPTURA"),
                retorno.revision().acciones());
        assertTrue(retorno.preguntasPertinentes().size() <= 3);
    }

    @Test void recargarOCambiarUnDatoIrrelevanteNoReiniciaLaEdad() {
        var reloj = conReloj("2026-10-30T05:00:00Z");
        var primera = reloj.preparar(Map.of("tipoPropiedad", "DEPARTAMENTO"), Map.of(), null,
                List.of(), DETECCION, true);
        var segunda = reloj.preparar(Map.of("tipoPropiedad", "DEPARTAMENTO", "descripcion", "Comentario"),
                Map.of(), null, List.of("descripcion"), DETECCION, true);
        assertEquals(primera.revision(), segunda.revision());
        assertEquals(3, segunda.revision().diasDesdeDeteccion());
    }

    @Test void cadaClaveConocidaProduceSuGrupoYSuSalidaSinUnFormularioPorCampo() {
        Map<String, Object> conocidos = preparacion.definiciones().stream()
                .flatMap(grupo -> grupo.claves().stream()).collect(Collectors.toMap(clave -> clave, clave -> "dato"));
        var retorno = preparacion.preparar(conocidos, Map.of(), null, List.of(), DETECCION, false);
        assertEquals(16, retorno.grupos().size());
        assertTrue(retorno.grupos().stream().allMatch(grupo -> !grupo.resultado().isBlank() && !grupo.datos().isEmpty()));
    }

    private static PreparacionDeCaptura conReloj(String instante) {
        return new PreparacionDeCaptura(new ObjectMapper(), Clock.fixed(Instant.parse(instante), ZoneOffset.UTC));
    }
    private static MetadatoRespuesta noSabe() {
        return new MetadatoRespuesta(EstadoDato.FALTANTE, "declaracion", null, null, null, null, true);
    }
    private static MetadatoRespuesta area(String concepto) {
        return new MetadatoRespuesta(EstadoDato.CONOCIDO, "documento", concepto, null, "1", null, false);
    }
    private static RetornoCaptura.Grupo grupo(RetornoCaptura retorno, String codigo) {
        return retorno.grupos().stream().filter(grupo -> codigo.equals(grupo.codigo())).findFirst().orElseThrow();
    }
}
