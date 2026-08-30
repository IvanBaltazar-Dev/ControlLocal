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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>«¿A qué tipos aplica esta clave?» tiene UNA autoridad</b> (V86).
 *
 * <h2>Qué defecto cierra</h2>
 * Tenía dos. {@code catalogo_atributo_tipo} —una fila por tipo, con su
 * exigencia— y {@code catalogo_atributo.aplica_todos}, un booleano que
 * contestaba <b>antes</b> de mirar las filas. El cortocircuito estaba en las
 * dos consultas del repositorio, en los dos {@code aplicaA} del dominio y en
 * tres cuerpos PL/pgSQL.
 *
 * <p>Dos autoridades para la misma pregunta divergen. Aquí divergían además en
 * la peor dirección: el campo no sabe decir <b>de qué tipo</b> habla, así que
 * retirar una clave de UNO obligaba a cambiarle la forma a la clave entera —y
 * eso es exactamente lo que 5B tuvo que hacerle a {@code area_terreno}.
 *
 * <h2>Qué vigila, y por qué en un gate y no en una nota</h2>
 * <ol>
 *   <li><b>Ningún código de producción decide aplicabilidad por el campo.</b>
 *       El campo sobrevive por compatibilidad, así que no basta con haberlo
 *       quitado hoy: un {@code if (aplicaTodos)} vuelve en una línea y no falla
 *       nada, porque en el catálogo actual las tres claves que lo llevan
 *       responden igual por los dos caminos. Sería verde durante meses.</li>
 *   <li><b>BROX Web y KAIROS lo preguntan por la misma puerta.</b> El North
 *       Star no pide que los dos canales acierten: pide que reciban <b>la misma
 *       definición del Core</b>. Dos puertas distintas contestarían igual el
 *       día que se escriben y distinto el día que una cambia.</li>
 * </ol>
 *
 * <p>Lo que la base garantiza —que el campo no pueda ponerse sin las siete
 * filas ni quedarse puesto al quitarlas— no cabe aquí: lo prueban
 * {@code SujetoDelDatoIntegrationTest} contra PostgreSQL y
 * {@code gate-modelo-universal.sql}. Este gate cubre lo que ellos no ven, que
 * es el código.
 */
class AutoridadDeAplicabilidadTest {

    /**
     * El campo solo puede aparecer donde se DECLARA, nunca donde se decide.
     *
     * <p>{@code CatalogoAtributo} es la entidad: tiene la columna, su getter y
     * su setter, y ahí el nombre es inevitable. En cualquier otro sitio del
     * código de producción es una segunda autoridad.
     */
    private static final String ENTIDAD = "CatalogoAtributo.java";

    /** Los métodos que responden la pregunta. Viven en la entidad, y solo ahí. */
    private static final List<String> METODOS = List.of(
            "public boolean aplicaA(String tipoPropiedad)",
            "public boolean aplicaA(String tipoPropiedad, String tipoOperacion)");

    @Test
    @DisplayName("ningun codigo de produccion decide la aplicabilidad por `aplicaTodos`")
    void ningunCodigoDeProduccionDecidePorElCampo() throws IOException {
        List<Path> fuentes = fuentesDeProduccion();

        // Control positivo. Si el barrido dejara de encontrar ficheros —una ruta
        // mal, un modulo renombrado—, el resto de este gate saldria verde sin
        // haber mirado nada, que es justo la forma de fallo que no puede tener.
        assertTrue(fuentes.size() > 100,
                "el barrido encontro " + fuentes.size() + " fuentes de produccion: son "
                        + "demasiado pocas para ser el reactor entero, asi que su cero no "
                        + "significaria nada");
        assertTrue(fuentes.stream().anyMatch(f -> f.getFileName().toString().equals(ENTIDAD))
                        && nombraElCampo(leer(fuentes.stream()
                                .filter(f -> f.getFileName().toString().equals(ENTIDAD))
                                .findFirst().orElseThrow())),
                "el barrido no encuentra `aplicaTodos` en el CODIGO de " + ENTIDAD + ", donde SI "
                        + "esta -- la columna, su getter y su setter. El patron no caza, asi que "
                        + "un cero en los demas ficheros no probaria nada");

        List<String> intrusos = new ArrayList<>();
        for (Path fuente : fuentes) {
            if (fuente.getFileName().toString().equals(ENTIDAD)) {
                continue;
            }
            if (nombraElCampo(leer(fuente))) {
                intrusos.add(relativa(fuente));
            }
        }

        assertEquals(List.of(), intrusos, """
                `aplica_todos` volvio al codigo de produccion fuera de la entidad que lo
                declara. Es un RESUMEN de las filas por tipo, no una autoridad: leerlo para
                decidir a que tipos aplica una clave reintroduce la doble autoridad que V86
                cerro, y no rompe nada el dia que se escribe -- las tres claves que lo llevan
                responden igual por los dos caminos, asi que el defecto viaja en verde.

                La aplicabilidad se pregunta a `catalogo_atributo_tipo` (o, para el ENCARGO,
                a `catalogo_atributo_operacion`).
                """);
    }

    @Test
    @DisplayName("los dos `aplicaA` viven en la entidad, y solo ahi")
    void laPreguntaSeResponderEnUnSoloSitio() throws IOException {
        Path entidad = fuentesDeProduccion().stream()
                .filter(f -> f.getFileName().toString().equals(ENTIDAD))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No se encontro " + ENTIDAD));
        String texto = leer(entidad);

        for (String metodo : METODOS) {
            assertTrue(texto.contains(metodo),
                    "falta " + metodo + " en " + ENTIDAD + ". Si se renombro, este gate deja "
                            + "de vigilar la firma que existe para vigilar.");
        }

        // Y ninguno de los dos mira el campo. Se comprueba sobre el CUERPO, no
        // sobre el fichero: el javadoc de la columna lo nombra a proposito.
        for (String metodo : METODOS) {
            String cuerpo = cuerpoDe(texto, metodo);
            assertFalse(cuerpo.toLowerCase(java.util.Locale.ROOT).contains("aplicatodos"),
                    metodo + " vuelve a cortocircuitar por `aplicaTodos`. Con el campo delante, "
                            + "una clave que lo lleve aplica a un tipo que su catalogo no "
                            + "declara -- y retirarla de uno solo deja de ser posible.");
        }
    }

    /**
     * <b>Los dos canales piden la definición a la misma puerta.</b>
     *
     * <p>{@code GET /captura/definicion} es lo que el SPA llama para pintar el
     * alta y el editor, y lo que {@code ClienteBroxHttp} llama para que KAIROS
     * sepa qué preguntar. Mientras sea una sola puerta, la aplicabilidad que
     * reciben es la misma por construcción; el día que uno se traiga la suya,
     * dejarán de estarlo sin que nada falle.
     */
    @Test
    @DisplayName("BROX Web y KAIROS piden la definicion por la misma puerta del Core")
    void losDosCanalesPreguntanPorLaMismaPuerta() {
        // Sin barra inicial: el SPA la compone sobre la base del API
        // (`captura/definicion`) y KAIROS la escribe absoluta
        // (`/captura/definicion`). Es la MISMA puerta; exigir la barra habria
        // dejado el gate rojo por una diferencia de composicion.
        String puerta = "captura/definicion";

        Path clienteKairos = rutaDelRepo(
                "kairos-service/src/main/java/com/kairos/brox/ClienteBroxHttp.java");
        assertTrue(Files.isRegularFile(clienteKairos),
                "no se encontro " + clienteKairos + ": sin el cliente de KAIROS este gate no "
                        + "puede comparar las dos superficies, y su verde no significaria nada");
        assertTrue(leer(clienteKairos).contains(puerta),
                "KAIROS ya no pide la definicion a " + puerta + ". Si se trajo su propia idea "
                        + "de que aplica a cada tipo, BROX Web y KAIROS dejaron de recibir la "
                        + "misma definicion del Core.");

        Path servicioSpa = rutaDelRepo("frontend-angular/src/app/core/api/captura.service.ts");
        assertTrue(Files.isRegularFile(servicioSpa),
                "no se encontro " + servicioSpa + ": sin el servicio del SPA este gate no "
                        + "puede comparar las dos superficies");
        assertTrue(leer(servicioSpa).contains(puerta),
                "el SPA ya no pide la definicion a " + puerta);

        // Y ninguno de los dos guarda su propia tabla de aplicabilidad.
        for (Path superficie : List.of(clienteKairos, servicioSpa)) {
            String texto = leer(superficie);
            assertFalse(texto.contains("aplicaTodos") || texto.contains("aplica_todos"),
                    superficie.getFileName() + " nombra `aplica_todos`. Ese campo es interno "
                            + "del catalogo y no viaja por el cable: si aparece en una "
                            + "superficie, es que alguien esta decidiendo aplicabilidad fuera "
                            + "del Core.");
        }
    }

    // ==================================================================
    // Soporte
    // ==================================================================

    /**
     * <b>¿Nombra el campo el CODIGO de este fichero?</b>
     *
     * <p>Se descartan las lineas de comentario —las que empiezan por
     * {@code *}, {@code /*} o {@code //}— y no por comodidad: el javadoc de la
     * columna tiene que poder decir que el campo existe y que dejo de ser
     * autoridad, y el de las consultas tiene que poder contar por que se le
     * quito. Prohibir la PALABRA obligaria a explicar el cambio sin nombrarlo,
     * que es como se pierde el porque.
     *
     * <p>Un comentario al final de una linea de codigo SI cuenta: el barrido
     * prefiere un rojo de mas a un verde que no ha mirado.
     *
     * <p><b>Sin distinguir mayusculas</b>, y no por comodidad. La primera
     * version comparaba contra {@code "aplicaTodos"} exacto, y el sabotaje del
     * 2026-08-30 la dejo VERDE: el consumidor mas probable de todos --el getter
     * de la entidad, {@code definicion.isAplicaTodos()}-- lleva la A mayuscula
     * y no casaba. El gate habria dejado pasar exactamente la reaparicion que
     * existe para cazar.
     */
    private static boolean nombraElCampo(String fichero) {
        return fichero.lines()
                .map(String::strip)
                .filter(linea -> !linea.startsWith("*") && !linea.startsWith("/*")
                        && !linea.startsWith("//"))
                .map(linea -> linea.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(linea -> linea.contains("aplicatodos") || linea.contains("aplica_todos"));
    }

    /** El cuerpo de un metodo, desde su firma hasta la llave que la cierra. */
    private static String cuerpoDe(String fichero, String firma) {
        int inicio = fichero.indexOf(firma);
        if (inicio < 0) {
            return "";
        }
        int profundidad = 0;
        for (int i = fichero.indexOf('{', inicio); i >= 0 && i < fichero.length(); i++) {
            char c = fichero.charAt(i);
            if (c == '{') {
                profundidad++;
            } else if (c == '}') {
                profundidad--;
                if (profundidad == 0) {
                    return fichero.substring(inicio, i + 1);
                }
            }
        }
        return fichero.substring(inicio);
    }

    private static List<Path> fuentesDeProduccion() throws IOException {
        Path backend = rutaDelRepo("backend-spring");
        List<Path> fuentes = new ArrayList<>();
        try (Stream<Path> modulos = Files.list(backend)) {
            for (Path modulo : modulos.filter(Files::isDirectory).toList()) {
                Path main = modulo.resolve("src/main/java");
                if (!Files.isDirectory(main)) {
                    continue;
                }
                try (Stream<Path> ficheros = Files.walk(main)) {
                    fuentes.addAll(ficheros
                            .filter(f -> f.getFileName().toString().endsWith(".java"))
                            .toList());
                }
            }
        }
        return fuentes;
    }

    private static String relativa(Path fichero) {
        return rutaDelRepo("").relativize(fichero).toString().replace('\\', '/');
    }

    /**
     * La raiz del repositorio, buscada subiendo desde el directorio de trabajo
     * en vez de asumir cuantos niveles hay. Mismo criterio que
     * {@code FronteraDeAutoridadEnElSpaTest}: surefire fija el directorio en el
     * basedir del modulo, que cambia segun se lance el reactor o un modulo.
     */
    private static Path rutaDelRepo(String rutaRelativa) {
        Path directorio = Path.of("").toAbsolutePath();
        while (directorio != null) {
            if (Files.isDirectory(directorio.resolve("frontend-angular/src"))
                    && Files.isDirectory(directorio.resolve("backend-spring"))) {
                return directorio.resolve(rutaRelativa);
            }
            directorio = directorio.getParent();
        }
        throw new IllegalStateException("No se encontro la raiz del repositorio subiendo desde "
                + Path.of("").toAbsolutePath() + ". Sin ella este gate no vigila nada.");
    }

    private static String leer(Path fichero) {
        try {
            return Files.readString(fichero, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + fichero, e);
        }
    }
}
