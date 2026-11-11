package com.controllocal.arquitectura;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate solicitado por el titular en C0: una sesión nueva necesita el paquete
 * privado y la versión de autoridad del corte. No prueba por sí solo toda la
 * congruencia funcional ni autentica un consentimiento humano.
 */
class ContinuidadDocumentalTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MANIFIESTO = "docs/1INF50/continuidad/contrato-activo.json";
    private static final Set<String> OPERATIVAS = Set.of(
            "docs/1INF50/CONTINUIDAD-PROYECTO.md",
            "docs/1INF50/continuidad/mapa-secciones.json");
    private static final List<String> OBLIGATORIAS = List.of(
            "AGENTS.md",
            "docs/1INF50/CONTINUIDAD-PROYECTO.md",
            "docs/1INF50/MAPA-DOCUMENTAL-BROX.md",
            "docs/1INF50/CONTRATOS-COMPARTIDOS-C0.md",
            "docs/1INF50/BROX-KAIROS-catalogo-operativo.md",
            "docs/1INF50/BROX-ampliacion-comercial.md",
            "docs/1INF50/BROX-plan-de-desarrollo.md",
            "docs/1INF50/BROX_KAIROS_directriz_clientes_independientes.md",
            "docs/ai/matriz-operacion-rol.md");

    @Test
    void elCorteUsaFuentesPrivadasExistentesYConLaVersionDeclarada() {
        assertDoesNotThrow(() -> validar(raizProyecto()));
    }

    @Test
    void unaFuenteCambiadaExigeReconciliarLaVersionDelCorte(@TempDir Path raiz) throws Exception {
        preparar(raiz);
        Files.writeString(raiz.resolve(OBLIGATORIAS.get(4)), "otro contrato", StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> validar(raiz));
    }

    @Test
    void unPaqueteAusenteNoSeSustituyePorUnaAprobacionInventada(@TempDir Path raiz) throws Exception {
        preparar(raiz);
        Files.delete(raiz.resolve(OBLIGATORIAS.get(4)));
        assertThrows(IllegalStateException.class, () -> validar(raiz));
    }

    @Test
    void unaDecisionPendienteDelCorteImpideDeclararSuContratoListo(@TempDir Path raiz) throws Exception {
        ObjectNode registro = preparar(raiz);
        registro.withArray("pendientesDecisionDelCorte").add("cambio funcional sin consentimiento");
        guardar(raiz, registro);
        assertThrows(IllegalStateException.class, () -> validar(raiz));
    }

    @Test
    void elRegistroNoPuedeLeerFuentesFueraDelProyecto(@TempDir Path raiz) throws Exception {
        ObjectNode registro = preparar(raiz);
        ((ObjectNode) registro.withArray("fuentes").get(0)).put("ruta", "../fuera.md");
        guardar(raiz, registro);
        assertThrows(IllegalStateException.class, () -> validar(raiz));
    }

    @Test
    void unRegistroSinOrigenDelConsentimientoSeRechaza(@TempDir Path raiz) throws Exception {
        ObjectNode registro = preparar(raiz);
        registro.remove("autorizacion");
        guardar(raiz, registro);
        assertThrows(IllegalStateException.class, () -> validar(raiz));
    }

    @Test
    void actualizarElEstadoNoPermiteExcluirUnaAutoridadDelControl(@TempDir Path raiz) throws Exception {
        ObjectNode registro = preparar(raiz);
        Files.writeString(raiz.resolve(OBLIGATORIAS.get(1)),
                "Estado actualizado; MAPA-DOCUMENTAL-BROX.md", StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> validar(raiz));
        ((ObjectNode) registro.withArray("fuentes").get(4)).put("control", "ESTADO_OPERATIVO");
        guardar(raiz, registro);
        assertThrows(IllegalStateException.class, () -> validar(raiz));
    }

    private static void validar(Path raiz) throws IOException {
        Path base = raiz.toAbsolutePath().normalize();
        Path manifiesto = base.resolve(MANIFIESTO);
        exigir(Files.isRegularFile(manifiesto), "Falta el paquete privado de continuidad: " + manifiesto);
        JsonNode registro = JSON.readTree(Files.readString(manifiesto, StandardCharsets.UTF_8));
        exigir("BROX-CONTINUIDAD-1".equals(registro.path("version").asText()), "Versión de continuidad desconocida");
        exigir(!registro.path("corte").asText().isBlank(), "Falta identificar el corte autorizado");
        exigir(!registro.path("autorizacion").path("origen").asText().isBlank(), "Falta origen del consentimiento del titular");
        JsonNode pendientes = registro.path("pendientesDecisionDelCorte");
        exigir(pendientes.isArray() && pendientes.isEmpty(), "Hay decisiones pendientes en el contrato del corte");
        JsonNode fuentes = registro.path("fuentes");
        exigir(fuentes.isArray(), "Falta inventario de fuentes");
        Set<String> encontradas = new HashSet<>();
        for (JsonNode fuente : fuentes) {
            String relativa = fuente.path("ruta").asText();
            Path archivo = base.resolve(relativa).normalize();
            exigir(!relativa.isBlank() && !Path.of(relativa).isAbsolute() && archivo.startsWith(base), "Fuente fuera del proyecto");
            exigir(encontradas.add(relativa), "Fuente duplicada: " + relativa);
            exigir(Files.isRegularFile(archivo), "Fuente no localizada: " + relativa);
            boolean operativa = OPERATIVAS.contains(relativa);
            exigir((operativa ? "ESTADO_OPERATIVO" : "HUELLA_NORMATIVA")
                    .equals(fuente.path("control").asText("HUELLA_NORMATIVA")),
                    "Control de fuente incorrecto: " + relativa);
            if (!operativa) {
                exigir(huella(archivo).equals(fuente.path("sha256").asText()), "Fuente cambió; reconciliar el corte: " + relativa);
            }
        }
        exigir(encontradas.containsAll(OBLIGATORIAS), "Faltan fuentes propietarias obligatorias");
        String entrada = Files.readString(base.resolve("AGENTS.md"), StandardCharsets.UTF_8);
        exigir(entrada.contains("docs/1INF50/CONTINUIDAD-PROYECTO.md"), "AGENTS no remite a la continuidad");
        String continuidad = Files.readString(base.resolve(OBLIGATORIAS.get(1)), StandardCharsets.UTF_8);
        exigir(continuidad.contains("MAPA-DOCUMENTAL-BROX.md"), "La continuidad no remite al mapa de fuentes");
    }

    private static ObjectNode preparar(Path raiz) throws IOException {
        ObjectNode registro = JSON.createObjectNode();
        registro.put("version", "BROX-CONTINUIDAD-1").put("corte", "C0");
        registro.putObject("autorizacion").put("origen", "fixture del gate, sin consentimiento comercial");
        registro.putArray("pendientesDecisionDelCorte");
        var fuentes = registro.putArray("fuentes");
        for (String ruta : OBLIGATORIAS) {
            Path archivo = raiz.resolve(ruta);
            Files.createDirectories(archivo.getParent());
            String contenido = ruta.equals("AGENTS.md") ? "docs/1INF50/CONTINUIDAD-PROYECTO.md"
                    : ruta.endsWith("CONTINUIDAD-PROYECTO.md") ? "MAPA-DOCUMENTAL-BROX.md" : "fuente del fixture";
            Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
            fuentes.addObject().put("ruta", ruta).put("sha256", huella(archivo))
                    .put("control", OPERATIVAS.contains(ruta) ? "ESTADO_OPERATIVO" : "HUELLA_NORMATIVA");
        }
        guardar(raiz, registro);
        return registro;
    }

    private static void guardar(Path raiz, ObjectNode registro) throws IOException {
        Path archivo = raiz.resolve(MANIFIESTO);
        Files.createDirectories(archivo.getParent());
        Files.writeString(archivo, JSON.writeValueAsString(registro), StandardCharsets.UTF_8);
    }

    private static String huella(Path archivo) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archivo)));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("SHA-256 no disponible", imposible);
        }
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw new IllegalStateException(mensaje);
    }

    private static Path raizProyecto() {
        Path actual = Path.of("").toAbsolutePath();
        while (actual != null && !Files.isDirectory(actual.resolve("backend-spring"))) actual = actual.getParent();
        assertTrue(actual != null, "No se localizó la raíz del proyecto");
        return actual;
    }
}
