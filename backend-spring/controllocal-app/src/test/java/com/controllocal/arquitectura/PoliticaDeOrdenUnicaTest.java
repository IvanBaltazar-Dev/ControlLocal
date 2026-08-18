package com.controllocal.arquitectura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>Una sola política de despacho, y vive en el dominio</b> (E2.2).
 *
 * <h2>Qué impide</h2>
 * Que el SPA vuelva a decidir <b>qué urge y en qué orden</b>. El backend entrega
 * la bandeja y las señales ya ordenadas; la pantalla las recorre.
 *
 * <p>El estado del que E2.2 saca al producto era este: el servicio ordenaba por
 * dos criterios, el SPA por otros dos —
 * {@code .sort((a, b) => a.prioridad - b.prioridad || b.valor - a.valor)}— y el
 * diseño declaraba seis. Tres opiniones sobre lo mismo, y ninguna sabía de las
 * otras. Un orden que discrepa no falla: enseña las cosas en un orden distinto
 * del que el dominio decidió, y nadie lo nota.
 *
 * <h2>Qué SÍ puede ordenar el SPA</h2>
 * Lo que no es despacho: una tabla que el usuario ordena por una columna, un
 * selector alfabético, un ranking que ya llega calculado. La diferencia no es el
 * verbo sino el objeto — <b>ordenar por urgencia es una decisión de negocio;
 * ordenar por nombre es una preferencia de lectura.</b>
 *
 * <p>Por eso este gate no prohíbe {@code .sort(}: prohíbe ordenar por los campos
 * con los que se decide la urgencia. Prohibir el verbo entero sería tan
 * inservible como no prohibir nada, porque el primer caso legítimo obligaría a
 * quitarlo.
 */
class PoliticaDeOrdenUnicaTest {

    /**
     * Ordenar por estos campos ES decidir el despacho.
     *
     * <p>Cada patrón busca una comparación —{@code a.x - b.x}, {@code b.x - a.x}—
     * dentro de un fichero del SPA. Es deliberadamente estrecho: caza la forma en
     * que esto se escribe de verdad en TypeScript, y no toda mención del campo.
     */
    private static final List<Campo> CAMPOS = List.of(
            new Campo("prioridad", "la prioridad la clasifica el dominio (E1)"),
            new Campo("diasSinAccion", "la antiguedad es el criterio 5 de la politica"),
            new Campo("nivelAtencion", "el nivel de atencion lo clasifica PoliticaComercial"),
            new Campo("fechaVencimiento", "la ventana temporal es el criterio 2"),
            new Campo("dependeDeMi", "de quien depende es el criterio 1"));

    private record Campo(String nombre, String porque) {

        /** {@code a.campo - b.campo} o {@code b.campo - a.campo}, con cualquier receptor. */
        Pattern comparacion() {
            String n = Pattern.quote(nombre);
            return Pattern.compile("\\w+\\." + n + "\\s*-\\s*\\w+\\." + n);
        }
    }

    @Test
    @DisplayName("el SPA no ordena por ningun campo con el que se decide la urgencia")
    void elSpaNoTieneSuPropiaPoliticaDeOrden() {
        List<String> hallazgos = new ArrayList<>();

        for (Path fuente : fuentesDelSpa()) {
            List<String> lineas = leer(fuente);
            for (int i = 0; i < lineas.size(); i++) {
                String linea = lineas.get(i);
                if (esComentario(linea)) {
                    continue;
                }
                int numero = i + 1;
                for (Campo campo : CAMPOS) {
                    if (campo.comparacion().matcher(linea).find()) {
                        hallazgos.add("  %s:%d  ordena por `%s`  (%s)"
                                .formatted(relativa(fuente), numero, campo.nombre(), campo.porque()));
                    }
                }
            }
        }

        if (!hallazgos.isEmpty()) {
            fail("""
                    El SPA volvio a decidir el orden por urgencia.

                    %s

                    El orden del foco y de las senales lo decide UNA politica, y vive en el
                    dominio: `PoliticaDeDespacho` para los asuntos, `IndicadorServiceImpl`
                    para las senales. El backend entrega la coleccion ya ordenada y la
                    pantalla la recorre.

                    Si hace falta un criterio nuevo, se anade a la politica y se prueba alli
                    -- donde un test puede demostrar que mueve al ganador --, no en un
                    comparador del cliente que nadie puede contradecir.

                    Ordenar por nombre, por fecha de creacion o por una columna que el
                    usuario elige NO es despacho y no cae aqui.
                    """.formatted(String.join("\n", hallazgos)));
        }
    }

    // ------------------------------------------------------------------

    /**
     * Un comentario que cite la forma prohibida no es la forma prohibida.
     *
     * <p>Hace falta porque el sitio donde se retiró el comparador lleva escrito
     * cuál era, y explicar por qué se fue es más útil que borrar el rastro. El
     * gate del SPA de D-E4-3 aprendió lo contrario —allí se reescribió el
     * comentario— y la diferencia es real: allí lo prohibido era **nombrar** una
     * tabla; aquí lo prohibido es **ejecutar** una comparación.
     */
    private static boolean esComentario(String linea) {
        String limpia = linea.trim();
        return limpia.startsWith("//") || limpia.startsWith("*") || limpia.startsWith("/*");
    }

    private static List<Path> fuentesDelSpa() {
        Path raiz = rutaDelRepo("frontend-angular/src");
        try (Stream<Path> ficheros = Files.walk(raiz)) {
            return ficheros.filter(Files::isRegularFile)
                    .filter(PoliticaDeOrdenUnicaTest::esFuente)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo recorrer " + raiz, e);
        }
    }

    private static boolean esFuente(Path fichero) {
        String nombre = fichero.getFileName().toString().toLowerCase(Locale.ROOT);
        return nombre.endsWith(".ts") || nombre.endsWith(".html");
    }

    private static List<String> leer(Path fichero) {
        try {
            return Files.readAllLines(fichero, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + fichero, e);
        }
    }

    private static String relativa(Path fichero) {
        return rutaDelRepo("").relativize(fichero).toString().replace('\\', '/');
    }

    private static Path rutaDelRepo(String rutaRelativa) {
        Path directorio = Path.of("").toAbsolutePath();
        while (directorio != null) {
            if (Files.isDirectory(directorio.resolve("frontend-angular/src"))) {
                return directorio.resolve(rutaRelativa);
            }
            directorio = directorio.getParent();
        }
        throw new IllegalStateException("No se encontro frontend-angular/src subiendo desde "
                + Path.of("").toAbsolutePath() + ". Sin el SPA este gate no vigila nada.");
    }
}
