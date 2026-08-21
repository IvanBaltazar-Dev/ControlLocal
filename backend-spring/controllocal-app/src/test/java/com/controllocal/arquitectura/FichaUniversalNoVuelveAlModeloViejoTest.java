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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>La ficha universal no puede volver al modelo viejo</b> (D-E4-1, D-A-1).
 *
 * <h2>Que vigila, y por que no basta con que las pruebas esten verdes</h2>
 * {@code /propiedades/:id} leia {@code GET /locales/{id}} — un propietario, un
 * precio, ninguna operacion— y por eso no podia ensenar una propiedad en venta
 * <b>y</b> en alquiler sin elegir uno de los dos precios y llamarlo "el precio".
 *
 * <p>La ficha ya no hace eso. Este gate impide que vuelva a hacerlo, porque la
 * regresion no seria un fallo de test: seria una linea que compila, funciona y
 * deshace el modelo en silencio.
 *
 * <h2>Las cinco cosas que rompen el build</h2>
 * <pre>
 *   1. la ficha consume GET /locales/{id}
 *   2. la ficha consume /locales/{id}/publicaciones
 *   3. la ficha importa local-detail (o local-detail resucita)
 *   4. la ficha traduce tipos de propiedad en Angular
 *   5. la ficha agrupa economicos o publicaciones por OPERACION en vez de por idEncargo
 * </pre>
 *
 * <p>La quinta es la sutil, y la que una prueba de venta+alquiler no detecta:
 * con un encargo de cada, agrupar por operacion y listar por id dan el mismo
 * resultado. Solo se rompe cuando hay <b>tres alquileres sucesivos</b>, que es
 * exactamente lo que el modelo permite —V50 prohibe dos <b>vivos</b> de la misma
 * operacion, no que hayan existido varios—.
 *
 * <p>Vive en el backend por el mismo motivo que {@link FronteraDeAutoridadEnElSpaTest}:
 * es donde esta el gate de cierre, que es donde tiene que fallar.
 */
class FichaUniversalNoVuelveAlModeloViejoTest {

    /**
     * Una importacion de la ficha heredada: entre comillas y detras de un
     * {@code from} o un {@code import(}. En prosa el nombre puede aparecer, y
     * debe: el comentario que cuenta de donde vino una pieza es lo que evita
     * que alguien la devuelva alli.
     */
    private static final Pattern IMPORTA_DETALLE_HEREDADO =
            Pattern.compile("(from|import\\()\\s*['\"`][^'\"`]*features/local-detail");

    /** Los ficheros de la ficha universal, que es lo que este gate protege. */
    private static final String FICHA = "frontend-angular/src/app/features/propiedad-detail";

    // ==================================================================

    @Test
    @DisplayName("la ficha no consume ningun endpoint del modelo heredado")
    void laFichaNoLlamaAlModeloViejo() {
        List<String> hallazgos = new ArrayList<>();
        // `locales/…` dentro de una plantilla de URL. Se busca la forma en que
        // el ApiClient las escribe, no la palabra suelta: "locales" aparece
        // legitimamente en prosa y en rotulos.
        Pattern llamada = Pattern.compile("['\"`]locales/|LocalesService");

        for (Path fuente : ficheros(FICHA)) {
            List<String> lineas = leer(fuente);
            for (int i = 0; i < lineas.size(); i++) {
                Matcher encontrado = llamada.matcher(lineas.get(i));
                if (encontrado.find()) {
                    hallazgos.add("  %s:%d  %s".formatted(relativa(fuente), i + 1, lineas.get(i).trim()));
                }
            }
        }

        if (!hallazgos.isEmpty()) {
            fail("""
                    La ficha universal volvio a leer el modelo heredado.

                    %s

                    `GET /locales/{id}` devuelve UN propietario, UN precio y ninguna
                    operacion: es el modelo que el universal vino a sustituir. Una propiedad
                    en venta y en alquiler no se puede pintar desde ahi sin elegir uno de los
                    dos precios y llamarlo "el precio".

                    La ficha lee `GET /propiedades/{id}` (PropiedadesService.consultar) y las
                    publicaciones por `GET /encargos/{idEncargo}/publicaciones`
                    (EncargosService), que es donde viven de verdad.
                    """.formatted(String.join("\n", hallazgos)));
        }
    }

    @Test
    @DisplayName("local-detail sigue borrado y nadie lo importa")
    void elDetalleHeredadoNoResucita() {
        Path carpeta = rutaDelRepo("frontend-angular/src/app/features/local-detail");
        if (Files.exists(carpeta)) {
            fail("""
                    `features/local-detail/` volvio a existir.

                    Es la ficha del modelo heredado. Se borro cuando `/propiedades/:id` paso a
                    leer el modelo universal, y su unica pieza propia -- el editor de
                    publicaciones-- vive ahora en `shared/publicaciones/`, colgando del
                    ENCARGO y no del local.
                    """);
        }

        List<String> hallazgos = new ArrayList<>();
        for (Path fuente : ficheros("frontend-angular/src")) {
            List<String> lineas = leer(fuente);
            for (int i = 0; i < lineas.size(); i++) {
                String linea = lineas.get(i);
                // Una IMPORTACION, no una mencion en prosa. Contar la prosa haria
                // que el gate castigara justo lo que hay que conservar: el
                // comentario que explica de donde vino una pieza y por que se movio.
                if (IMPORTA_DETALLE_HEREDADO.matcher(linea).find()) {
                    hallazgos.add("  %s:%d  %s".formatted(relativa(fuente), i + 1, linea.trim()));
                }
            }
        }
        if (!hallazgos.isEmpty()) {
            fail("Alguien volvio a importar `features/local-detail`:\n\n"
                    + String.join("\n", hallazgos));
        }
    }

    /**
     * <b>Lo que SI se puede agrupar por operacion.</b>
     *
     * <p>La historia comercial responde «cuantas veces estuvo en alquiler», y esa
     * pregunta es por operacion por definicion. Agregar para leer no es fusionar:
     * cada cifra de esa agregacion sigue apuntando a su {@code idEncargo}, y la
     * calcula el Core, no la pantalla.
     *
     * <p>Lo que no se puede agrupar asi es la lista de ENCARGOS y la de
     * PUBLICACIONES, que son los objetos con identidad propia.
     */
    private static final Pattern ITERA_OBJETOS_CON_IDENTIDAD =
            Pattern.compile("(?i)of\\s+[^;)]*\\b(encargos|vivos|cerrados|publicaciones|anuncios)\\b");

    @Test
    @DisplayName("la ficha no agrupa encargos ni anuncios por operacion: la identidad es idEncargo")
    void laFichaNoAgrupaPorOperacion() {
        List<String> hallazgos = new ArrayList<>();
        // `track` es lo que fija la identidad de un bloque repetido en Angular.
        // Con `track encargo.operacion` tres alquileres sucesivos colapsan en uno.
        Pattern trackPorOperacion = Pattern.compile("track\\s+\\w+\\.operacion\\b");
        Pattern agrupaPorOperacion = Pattern.compile("groupBy\\s*\\(\\s*\\w*\\.?operacion");

        for (Path fuente : ficheros(FICHA)) {
            List<String> lineas = leer(fuente);
            for (int i = 0; i < lineas.size(); i++) {
                String linea = lineas.get(i);
                boolean sospechoso = agrupaPorOperacion.matcher(linea).find()
                        // Un `track … .operacion` solo es un problema si lo que se
                        // recorre son encargos o anuncios. Sobre la historia
                        // comercial es exactamente lo correcto.
                        || (trackPorOperacion.matcher(linea).find()
                            && ITERA_OBJETOS_CON_IDENTIDAD.matcher(linea).find());
                if (sospechoso) {
                    hallazgos.add("  %s:%d  %s".formatted(relativa(fuente), i + 1, linea.trim()));
                }
            }
        }

        if (!hallazgos.isEmpty()) {
            fail("""
                    La ficha agrupa encargos por OPERACION.

                    %s

                    La identidad de un bloque es `idEncargo`, nunca `operacion`. Una propiedad
                    puede haber tenido tres encargos de ALQUILER a lo largo del tiempo -- 2024
                    cerrado, 2025 cerrado, 2026 vigente--: lo que la base prohibe
                    (uq_captacion_viva_por_operacion, V50) es dos VIVOS de la misma operacion,
                    no que hayan existido varios.

                    Agrupados por operacion serian un bloque con tres precios dentro y una
                    linea temporal que no significa nada, y sus tres historicos economicos --
                    y sus publicaciones-- se fundirian.

                    Ojo: esto NO lo detecta una prueba de venta+alquiler. Con un encargo de
                    cada, agrupar y listar dan el mismo resultado.
                    """.formatted(String.join("\n", hallazgos)));
        }
    }

    /**
     * La otra mitad, en positivo: los bloques de encargo <b>se rastrean por su
     * id</b>. Si dejaran de hacerlo, o bien desaparecieron o bien alguien les
     * cambio la identidad.
     */
    @Test
    @DisplayName("los encargos se rastrean por idEncargo")
    void losEncargosSeRastreanPorSuId() {
        String plantilla = String.join("\n", leer(rutaDelRepo(FICHA + "/propiedad-detail.html")));
        if (!plantilla.contains("track encargo.idEncargo")) {
            fail("""
                    Los bloques de encargo dejaron de rastrearse por `idEncargo`.

                    La identidad de un bloque es el encargo, no su operacion ni su
                    posicion: tres alquileres sucesivos son tres bloques con tres
                    historicos y tres series de anuncios.
                    """);
        }
    }

    /**
     * El rotulo del tipo lo publica el backend ({@code tipoRotulo}); la ficha lo
     * pinta. Un {@code switch} que convierta {@code LOCAL} en «Local comercial»
     * seria la matriz «tipo → texto» viviendo en la interfaz, y con dos
     * interfaces habria dos (D-A-1 §6).
     *
     * <p>{@link FronteraDeAutoridadEnElSpaTest} ya lo vigila en todo el SPA. Aqui
     * se afirma lo contrario y en positivo: que la ficha <b>usa</b> el rotulo que
     * le mandan. Si dejara de usarlo, es que empezo a componerlo.
     */
    @Test
    @DisplayName("la ficha pinta el rotulo del tipo que publica el backend")
    void laFichaUsaElRotuloDelBackend() {
        String plantilla = String.join("\n", leer(rutaDelRepo(FICHA + "/propiedad-detail.html")));
        if (!plantilla.contains("tipoRotulo")) {
            fail("""
                    La ficha dejo de pintar `tipoRotulo`.

                    O bien ya no ensena el tipo -- y entonces falta un dato--, o bien lo compone
                    ella a partir de `tipoPropiedad`, que es la matriz del catalogo reescrita en
                    Angular. El rotulo ya viene escrito desde el Core.
                    """);
        }
    }

    // ------------------------------------------------------------------

    private static List<Path> ficheros(String rutaRelativa) {
        Path raiz = rutaDelRepo(rutaRelativa);
        if (!Files.isDirectory(raiz)) {
            throw new IllegalStateException("No existe " + raiz + ": este gate vigila la ficha universal.");
        }
        try (Stream<Path> ficheros = Files.walk(raiz)) {
            return ficheros.filter(Files::isRegularFile).filter(FichaUniversalNoVuelveAlModeloViejoTest::esFuente).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo recorrer " + raiz, e);
        }
    }

    private static boolean esFuente(Path fichero) {
        String nombre = fichero.getFileName().toString().toLowerCase(Locale.ROOT);
        // Un spec afirma contra el contrato y para eso necesita literales.
        if (nombre.endsWith(".spec.ts")) {
            return false;
        }
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

    /** Mismo criterio que {@link FronteraDeAutoridadEnElSpaTest}: subir hasta encontrar el SPA. */
    private static Path rutaDelRepo(String rutaRelativa) {
        Path directorio = Path.of("").toAbsolutePath();
        while (directorio != null) {
            if (Files.isDirectory(directorio.resolve("frontend-angular/src"))) {
                return directorio.resolve(rutaRelativa);
            }
            directorio = directorio.getParent();
        }
        throw new IllegalStateException("No se encontro frontend-angular/src subiendo desde "
                + Path.of("").toAbsolutePath());
    }
}
