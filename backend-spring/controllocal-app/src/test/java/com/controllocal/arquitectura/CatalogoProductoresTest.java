package com.controllocal.arquitectura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Capa 2 del gate de productores: EVIDENCIA.</b>
 *
 * <p>La capa 1 ({@code VocabularioPersistidoIntegrationTest}) comprueba contra
 * PostgreSQL que no exista un código admitido sin clasificar. Esta comprueba
 * que la clasificación <b>diga algo</b>:
 *
 * <ul>
 *   <li>cada fila usa una de las CINCO clases y ninguna inventada;</li>
 *   <li>un {@code PRODUCIDO} <b>nombra a su productor</b> — sin eso el catálogo
 *       solo afirmaría que alguien dijo que existe, que es exactamente lo que
 *       este bloque vino a terminar;</li>
 *   <li>un {@code DEPRECADO} o {@code RESERVADO_*} justifica por qué se
 *       conserva sin producirse;</li>
 *   <li>no hay pares duplicados: una misma {@code (tabla.columna, código)} no
 *       puede tener dos clasificaciones.</li>
 * </ul>
 *
 * <p>No necesita base de datos: corre siempre, también en un build de
 * desarrollo. La que sí la necesita es la capa 1.
 */
public class CatalogoProductoresTest {

    /** `| tabla.columna | codigo | CLASE | evidencia |` */
    private static final Pattern FILA = Pattern.compile(
            "^\\|\\s*([a-z_]+\\.[a-z_]+)\\s*\\|\\s*([A-Z])\\s*\\|\\s*([A-Z_]+)\\s*\\|\\s*(.*?)\\s*\\|$");

    private static final Set<String> CLASES = Set.of(
            "PRODUCIDO", "DERIVADO", "RESERVADO_COMPATIBILIDAD", "RESERVADO_FUTURO", "DEPRECADO");

    /** Mínimo de evidencia útil: "sí" o "ver arriba" no sirven. */
    private static final int EVIDENCIA_MINIMA = 15;

    public record Fila(String columna, String codigo, String clase, String evidencia) {
        public String clave() {
            return columna + "." + codigo;
        }
    }

    @Test
    @DisplayName("toda fila usa una de las cinco clases y no hay pares duplicados")
    void elCatalogoEstaBienFormado() throws IOException {
        List<Fila> filas = leerCatalogo();
        assertTrue(filas.size() >= 100,
                "el catalogo perdio filas: se esperaban al menos 100, hay " + filas.size());

        List<String> claseInvalida = filas.stream()
                .filter(f -> !CLASES.contains(f.clase()))
                .map(f -> f.clave() + " -> " + f.clase())
                .toList();
        assertEquals(List.of(), claseInvalida,
                "clasificacion fuera de las cinco acordadas");

        Set<String> vistas = new LinkedHashSet<>();
        List<String> duplicadas = new ArrayList<>();
        for (Fila f : filas) {
            if (!vistas.add(f.clave())) {
                duplicadas.add(f.clave());
            }
        }
        assertEquals(List.of(), duplicadas,
                "un par (columna, codigo) con dos clasificaciones: el catalogo se contradice");
    }

    @Test
    @DisplayName("PRODUCIDO nombra a su productor; el resto justifica por que no lo tiene")
    void cadaClasificacionTraeSuEvidencia() throws IOException {
        List<String> sinEvidencia = leerCatalogo().stream()
                .filter(f -> f.evidencia().length() < EVIDENCIA_MINIMA)
                .map(f -> f.clave() + " [" + f.clase() + "] -> \"" + f.evidencia() + "\"")
                .toList();

        assertEquals(List.of(), sinEvidencia,
                "un PRODUCIDO sin productor nombrado no demuestra nada, y un RESERVADO sin "
                        + "justificacion es un huerfano disfrazado");
    }

    /**
     * Las cuatro vías tienen que seguir escritas en el catálogo. No es
     * decoración: la cuarta se añadió porque {@code token_acceso.REVOCADO}
     * pareció huérfano al buscar solo las tres primeras, y quien repita el
     * barrido sin ella volverá a equivocarse igual.
     */
    @Test
    @DisplayName("el catalogo documenta las cuatro vias de produccion")
    void lasCuatroViasSiguenDocumentadas() throws IOException {
        String texto = Files.readString(catalogo());
        for (String via : List.of("setter/entidad", "service/Transiciones",
                "SQL/Flyway/función BD", "@Modifying JPQL")) {
            assertTrue(texto.contains(via), "falta la via de produccion: " + via);
        }
    }

    /** Los estados que este tramo va a implementar siguen marcados como tales. */
    @Test
    @DisplayName("los tres huerfanos pendientes no aparecen disfrazados de PRODUCIDO")
    void losPendientesSiguenDeclaradosComoPendientes() throws IOException {
        List<Fila> filas = leerCatalogo();
        for (String clave : List.of("captacion.estado.V", "solicitud_alquiler.estado.D",
                "oportunidad_comercial.estado.X")) {
            Fila fila = filas.stream().filter(f -> f.clave().equals(clave)).findFirst()
                    .orElseThrow(() -> new AssertionError("falta del catalogo: " + clave));
            assertFalse("PRODUCIDO".equals(fila.clase()),
                    clave + " figura como PRODUCIDO pero todavia no tiene productor. Si ya se "
                            + "implemento, actualiza el catalogo con la evidencia.");
        }
    }

    public static List<Fila> leerCatalogo() throws IOException {
        List<Fila> filas = new ArrayList<>();
        for (String linea : Files.readAllLines(catalogo())) {
            Matcher m = FILA.matcher(linea.trim());
            if (m.matches()) {
                filas.add(new Fila(m.group(1), m.group(2), m.group(3), m.group(4)));
            }
        }
        return filas;
    }

    public static Path catalogo() {
        return List.of(
                        Path.of("docs", "ai", "catalogo-productores-canonico.md"),
                        Path.of("..", "docs", "ai", "catalogo-productores-canonico.md"),
                        Path.of("..", "..", "docs", "ai", "catalogo-productores-canonico.md"))
                .stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontro docs/ai/catalogo-productores-canonico.md"));
    }
}
