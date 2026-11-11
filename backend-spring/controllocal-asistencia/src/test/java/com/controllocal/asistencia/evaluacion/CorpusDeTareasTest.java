package com.controllocal.asistencia.evaluacion;

import com.controllocal.asistencia.brox.ClienteBrox;
import com.controllocal.asistencia.brox.SesionBrox;
import com.controllocal.asistencia.brox.Traza;
import com.controllocal.asistencia.brox.Vocabulario;
import com.controllocal.asistencia.catalogo.CatalogoReal;
import com.controllocal.asistencia.catalogo.DefinicionComunicacionDePrueba;
import com.controllocal.asistencia.interpretacion.Interprete;
import com.controllocal.asistencia.interpretacion.InterpreteDeterminista;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * RC-K-013/005/006/018: tareas sintéticas de texto con un resultado esperado propio.
 *
 * La interpretación usa definiciones controladas que devuelve el puerto Core;
 * no escribe, no cambia contexto y no llama a un modelo. La lista de resultados
 * no se genera leyendo la salida del intérprete. Una tarea puede tener varios
 * pasos: el denominador son las tareas, nunca el número de mensajes.
 *
 * Este banco de regresión no prueba RBAC real, commit, móvil, voz o un proveedor
 * LLM. Si un fallo se repara usando el caso, ya no es una muestra ciega de esa
 * reparación; la promoción de un modelo exige su evaluación reservada propia.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorpusDeTareasTest {

    private static final ObjectMapper JSON = new ObjectMapper().enable(
            com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final String RUTA = "/evaluacion/tareas-es-v1.json";
    private static final Set<String> LECTURAS = Set.of("distritos", "catalogoDe", "captura", "definicionDeInteracciones");
    /** Claves monetarias del contrato público Core, no cualquier texto que parezca numérico. */
    private static final Set<String> IMPORTES_CORE = clavesDeImporteCore();
    private final List<Evaluacion> evaluaciones = new ArrayList<>();
    private JsonNode corpus;

    private record Evaluacion(JsonNode tarea, List<String> fallos) { }

    @BeforeAll
    void evaluarUnaVezElPerfilControlado() throws IOException {
        try (InputStream recurso = getClass().getResourceAsStream(RUTA)) {
            if (recurso == null) {
                throw new IllegalStateException("Falta el banco RC-K-013: " + RUTA);
            }
            corpus = JSON.readTree(recurso);
        }
        for (JsonNode tarea : corpus.path("tareas")) {
            evaluaciones.add(evaluar(tarea));
        }
    }

    @TestFactory
    Stream<DynamicTest> cadaTareaConservaSuResultadoYSusLimites() {
        return evaluaciones.stream().map(e -> DynamicTest.dynamicTest(
                e.tarea().path("id").asText() + " · " + e.tarea().path("objetivo").asText(),
                () -> assertEquals(List.of(), e.fallos(),
                        "Tarea " + e.tarea().path("id").asText() + ": " + e.fallos())));
    }

    @Test
    void elBancoTieneTareasContextoParticionesYResultadosNoSoloMensajes() {
        assertTrue(evaluaciones.size() >= 200, "RC-K-013 exige al menos 200 tareas");
        assertEquals(240, evaluaciones.size(), "La migración C3 conserva todos los IDs del banco controlado");
        Set<String> ids = new HashSet<>();
        Set<String> objetivosYEntradas = new HashSet<>();
        Set<String> flujos = new HashSet<>();
        Set<String> ambiguedades = new HashSet<>();
        Set<String> espacios = new HashSet<>();
        int multiplesPasos = 0;
        for (Evaluacion evaluacion : evaluaciones) {
            JsonNode tarea = evaluacion.tarea();
            assertTrue(ids.add(tarea.path("id").asText()), "ID repetido");
            assertTrue(objetivosYEntradas.add(tarea.path("objetivo").asText()
                    + tarea.path("pasos").toString()), "Tarea repetida");
            assertFalse(tarea.path("objetivo").asText().isBlank());
            assertEquals("EVALUACION_CONTROLADA", tarea.path("particion").asText());
            assertEquals("TEXTO", tarea.path("modalidad").asText());
            assertTrue(tarea.path("contexto").path("idOrganizacion").asLong() > 0);
            assertTrue(tarea.path("contexto").path("idPersona").asLong() > 0);
            assertTrue(tarea.path("fuentesPermitidas").isArray());
            assertFalse(tarea.path("fuentesPermitidas").isEmpty());
            assertTrue(tarea.path("efectosProhibidos").toString().contains("APROBACION_HUMANA"));
            assertTrue(tarea.path("pasos").isArray() && !tarea.path("pasos").isEmpty());
            tarea.path("pasos").forEach(p -> {
                assertTrue(p.has("esperado"));
                assertTrue(p.path("esperado").has("accion"));
                assertTrue(p.path("esperado").path("datos").isObject());
                assertTrue(p.path("esperado").path("noEntendido").isArray());
            });
            flujos.add(tarea.path("flujo").asText());
            ambiguedades.add(tarea.path("ambiguedad").asText());
            espacios.add(tarea.path("contexto").path("espacio").asText());
            multiplesPasos += tarea.path("pasos").size() > 1 ? 1 : 0;
        }
        assertTrue(multiplesPasos > 0, "El banco distingue tareas de mensajes");
        assertTrue(flujos.containsAll(Set.of("CAPTURA_INICIAL", "CAPTURA_CONTEXTUAL", "CONSULTA",
                "MATCHING", "PERSONA", "INTERACCION", "CONTINUIDAD", "FOCO", "SEGURIDAD")));
        assertTrue(ambiguedades.containsAll(Set.of("INEQUIVOCA", "ACLARACION", "ADVERSARIAL", "FUERA_PERFIL")));
        assertEquals(Set.of("INDIVIDUAL", "EMPRESARIAL"), espacios);
        assertFalse(corpus.path("perfil").path("entrenamiento").asBoolean());
        assertFalse(corpus.path("perfil").path("evaluacionCiega").asBoolean());
        assertTrue(corpus.path("perfil").path("proveedor").isNull());
    }

    @Test
    void lasMetricasSeparanInequivocasAclaracionesYSeguridad() {
        long inequívocas = contar("INEQUIVOCA");
        assertEquals(168, inequívocas, "Una migración contractual no reduce el denominador ni reclasifica tareas");
        long correctas = evaluaciones.stream().filter(e -> clase(e, "INEQUIVOCA") && e.fallos().isEmpty()).count();
        assertTrue(inequívocas > 0);
        double tasa = (double) correctas / inequívocas;
        assertTrue(tasa >= corpus.path("perfil").path("objetivoInequivocas").asDouble(),
                "RC-K-013: " + correctas + "/" + inequívocas + " tareas inequívocas correctas; "
                        + "las ambiguas/adversariales no compensan este denominador");
        for (String clase : List.of("ACLARACION", "ADVERSARIAL", "FUERA_PERFIL")) {
            long total = contar(clase);
            assertTrue(total > 0);
            List<String> fallos = evaluaciones.stream().filter(e -> clase(e, clase) && !e.fallos().isEmpty())
                    .map(e -> e.tarea().path("id").asText()).toList();
            assertEquals(List.of(), fallos, "No tolerar un fallo de fidelidad/seguridad mediante promedio: " + clase);
        }
    }

    private long contar(String clase) {
        return evaluaciones.stream().filter(e -> clase(e, clase)).count();
    }

    private static boolean clase(Evaluacion evaluacion, String clase) {
        return clase.equals(evaluacion.tarea().path("ambiguedad").asText());
    }

    private Evaluacion evaluar(JsonNode tarea) {
        List<String> fallos = new ArrayList<>();
        JsonNode c = tarea.path("contexto");
        SesionBrox sesion = new SesionBrox(c.path("idOrganizacion").asLong(),
                c.path("idPersona").asLong(), c.path("idRolOperativo").asLong(),
                c.path("rolEfectivo").asText(), c.path("identidadSesion").asText(), Long.MAX_VALUE);
        ClienteBrox puerto = mock(ClienteBrox.class);
        when(puerto.distritos(any(), any())).thenReturn(CatalogoReal.distritos());
        when(puerto.catalogoDe(any(), anyString(), any())).thenAnswer(i -> CatalogoReal.catalogoDe(i.getArgument(1)));
        when(puerto.definicionDeInteracciones(any(),any())).thenReturn(DefinicionComunicacionDePrueba.opciones());
        AtomicReference<ClienteBrox.EstadoCaptura> activo = new AtomicReference<>();
        when(puerto.captura(any(), anyLong(), any())).thenAnswer(i -> activo.get());
        Interprete interprete = new InterpreteDeterminista(puerto);
        int numero = 0;
        for (JsonNode paso : tarea.path("pasos")) {
            numero++;
            String id = tarea.path("id").asText();
            Traza traza = new Traza("API", "KAIROS", "determinista", "C0-V1", id,
                    id + "-" + numero, id + "-mensaje-" + numero, null, null);
            activo.set(paso.has("borrador") ? estado(paso.path("borrador")) : null);
            Interprete.Lectura leida;
            try {
                leida = paso.path("modo").asText().equals("CAPTURA")
                        ? interprete.leerEnCaptura(paso.path("texto").asText(),
                            paso.path("borrador").path("idBorrador").asLong(), sesion, traza)
                        : interprete.leer(paso.path("texto").asText(), sesion, traza);
            } catch (RuntimeException error) {
                fallos.add("Paso " + numero + ": excepción " + error.getClass().getSimpleName());
                continue;
            }
            JsonNode esperado = paso.path("esperado");
            comparar(fallos, numero, "accion", nullable(esperado.path("accion")),
                    leida.accion() == null ? null : leida.accion().name());
            compararDatos(fallos, numero, mapa(esperado.path("datos")), leida.datos());
            comparar(fallos, numero, "noEntendido", lista(esperado.path("noEntendido")), leida.noEntendido());
            comparar(fallos, numero, "motivo", nullable(esperado.path("motivo")), leida.motivo());
        }
        mockingDetails(puerto).getInvocations().forEach(llamada -> {
            String metodo = llamada.getMethod().getName();
            if (!LECTURAS.contains(metodo)) {
                fallos.add("Interpretación invocó una capacidad no permitida: " + metodo);
                return;
            }
            Object[] argumentos = llamada.getArguments();
            if (argumentos.length < 2 || argumentos[0] != sesion) {
                fallos.add("La lectura no conservó la sesión server-side: " + metodo);
                return;
            }
            if (!(argumentos[argumentos.length - 1] instanceof Traza t)
                    || !t.conversacionId().equals(tarea.path("id").asText())
                    || t.herramienta() == null || t.herramienta().isBlank()) {
                fallos.add("Lectura sin traza de herramienta/tarea: " + metodo);
            } else {
                String herramienta = switch(metodo) {
                    case "captura" -> "consultar_captura";
                    case "definicionDeInteracciones" -> "definicion_de_interacciones";
                    default -> "catalogo_de_captura";
                };
                if (!herramienta.equals(t.herramienta())) {
                    fallos.add("Herramienta de lectura incorrecta: " + metodo + "=" + t.herramienta());
                }
            }
        });
        return new Evaluacion(tarea, List.copyOf(fallos));
    }

    private static ClienteBrox.EstadoCaptura estado(JsonNode b) {
        String clave = b.path("claveActiva").asText();
        List<String> faltantes = new ArrayList<>();
        faltantes.add(clave);
        faltantes.addAll(lista(b.path("otrosFaltantes")));
        Map<String, Object> conocido = new LinkedHashMap<>();
        conocido.put("tipoPropiedad", b.path("tipoPropiedad").asText());
        b.path("conocido").fields().forEachRemaining(e -> conocido.put(e.getKey(), e.getValue().asText()));
        ClienteBrox.Pregunta pregunta = new ClienteBrox.Pregunta(clave, clave, "OPERACION", null, "SELECTOR", b.path("tipoDato").asText(), null, List.of(), "ALT", true, null, 0, null, null);
        return new ClienteBrox.EstadoCaptura(b.path("idBorrador").asLong(), "CAP-00077", "REGISTRAR_PROPIEDAD", "E", "API", conocido, faltantes, pregunta, false, null, null, null, 4, Map.of(), Map.of(), Map.of(), Map.of(), List.of());
    }

    private static String nullable(JsonNode nodo) {
        return nodo.isMissingNode() || nodo.isNull() ? null : nodo.asText();
    }

    private static Map<String, String> mapa(JsonNode nodo) {
        Map<String, String> mapa = new LinkedHashMap<>();
        nodo.fields().forEachRemaining(e -> mapa.put(e.getKey(), e.getValue().asText()));
        return mapa;
    }

    private static List<String> lista(JsonNode nodo) {
        List<String> resultado = new ArrayList<>();
        nodo.forEach(n -> resultado.add(n.asText()));
        return resultado;
    }

    private static void comparar(List<String> fallos, int paso, String campo, Object esperado, Object actual) {
        if (!java.util.Objects.equals(esperado, actual)) {
            fallos.add("Paso " + paso + " " + campo + ": esperado=" + esperado + ", actual=" + actual);
        }
    }

    private static Set<String> clavesDeImporteCore() {
        Set<String> claves = new HashSet<>();
        Vocabulario.OPERACIONES.forEach(op -> claves.add(Vocabulario.IMPORTE + ":" + op));
        claves.add(Vocabulario.CUOTA_MANTENIMIENTO);
        return Set.copyOf(claves);
    }

    /** Valor decimal exacto: la escala textual no es una regla de precisión monetaria. */
    private static void compararDatos(List<String> fallos, int paso, Map<String, String> esperado,
                                     Map<String, String> actual) {
        if (actual == null || !esperado.keySet().equals(actual.keySet())) {
            comparar(fallos, paso, "datos", esperado, actual);
            return;
        }
        esperado.forEach((clave, valor) -> {
            String recibido = actual.get(clave);
            if("datosComerciales".equals(clave)) {
                try {
                    comparar(fallos,paso,"datos."+clave,JSON.readTree(valor),JSON.readTree(recibido));
                } catch(Exception invalido) {
                    fallos.add("Paso "+paso+": datosComerciales no conserva JSON válido");
                }
                return;
            }
            if (IMPORTES_CORE.contains(clave)) {
                try {
                    if (recibido != null && new BigDecimal(valor).compareTo(new BigDecimal(recibido)) == 0) {
                        return;
                    }
                } catch (NumberFormatException invalido) {
                    // Un importe no decimal no se salva mediante coerción o redondeo.
                }
            }
            comparar(fallos, paso, "datos." + clave, valor, recibido);
        });
    }
}
