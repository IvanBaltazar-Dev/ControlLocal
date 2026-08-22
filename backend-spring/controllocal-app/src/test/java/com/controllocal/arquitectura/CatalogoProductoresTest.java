package com.controllocal.arquitectura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
 *   <li>ese nombre <b>existe en el código</b> (V76): hasta aquí la evidencia
 *       solo tenía que ser larga, así que una fila podía nombrar
 *       {@code SolicitudServiceImpl.reenviar} —un método que no existe, se
 *       llama {@code reenviarAEvaluacion}— y el gate la daba por buena;</li>
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

    /**
     * Trinquete: el catálogo solo puede crecer.
     *
     * <p>Se sube al número real cada vez que se amplía. Un umbral holgado —era
     * {@code >= 100} con 114 filas escritas— deja sitio para que catorce filas
     * desaparezcan sin que nada avise, que es justo el accidente contra el que
     * existe este recuento.
     */
    private static final int FILAS_MINIMAS = 112;

    /**
     * {@code MfaServiceImpl}, {@code TokenAcceso}: CamelCase de dos jorobas o
     * más. Deja fuera a propósito las palabras sueltas en mayúscula («Seed»,
     * «Baja») y a las siglas ({@code SQL}, {@code DESTINO_SOLICITUD}), que no
     * son símbolos.
     */
    private static final Pattern CLASE_CITADA =
            Pattern.compile("\\b([A-Z][a-z0-9]+(?:[A-Z][a-z0-9]+)+)\\b");

    /** {@code Tarea.completar}, {@code Personas.nueva}: tipo y método. */
    private static final Pattern METODO_CITADO =
            Pattern.compile("\\b([A-Z][A-Za-z0-9]*)\\.([a-z][A-Za-z0-9]*)\\b");

    /** {@code V28__autorizacion_datos_personales.sql}: la vía SQL/Flyway. */
    private static final Pattern MIGRACION_CITADA =
            Pattern.compile("\\b(V\\d+__[a-z0-9_]+\\.sql)\\b");

    /**
     * {@code marcarAgotadaSiConsumioSuUltimaAccion}: el método sin su clase.
     * Vale como evidencia —el nombre es único de sobra— pero tiene que existir.
     */
    private static final Pattern METODO_SUELTO =
            Pattern.compile("\\b([a-z][a-z0-9]*(?:[A-Z][A-Za-z0-9]*)+)\\b");

    public record Fila(String columna, String codigo, String clase, String evidencia) {
        public String clave() {
            return columna + "." + codigo;
        }
    }

    @Test
    @DisplayName("toda fila usa una de las cinco clases y no hay pares duplicados")
    void elCatalogoEstaBienFormado() throws IOException {
        List<Fila> filas = leerCatalogo();
        assertTrue(filas.size() >= FILAS_MINIMAS,
                "el catalogo perdio filas: se esperaban al menos " + FILAS_MINIMAS
                        + ", hay " + filas.size());

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
     * <b>El productor tiene que existir</b> (V76).
     *
     * <p>Un PRODUCIDO afirma que <i>hay código que escribe ese valor</i>. Si el
     * nombre que da no aparece en el árbol, la afirmación no se puede
     * comprobar y el catálogo pasa a ser una lista de buenas intenciones: es
     * lo que pasaba con {@code SolicitudServiceImpl.reenviar}, que se llama
     * {@code reenviarAEvaluacion} desde que se separó del alta.
     *
     * <p>Se comprueban <b>todas</b> las clases, no solo PRODUCIDO: un
     * DEPRECADO que se justifica citando el sustituto miente igual si el
     * sustituto no existe.
     */
    @Test
    @DisplayName("cada simbolo citado como evidencia existe en el codigo")
    void losProductoresCitadosExisten() throws IOException {
        Map<String, Path> clases = clasesDelArbol();
        Set<String> migraciones = migracionesDelArbol();
        List<String> fantasmas = new ArrayList<>();

        for (Fila fila : leerCatalogo()) {
            String texto = limpiar(fila.evidencia());

            Matcher migracion = MIGRACION_CITADA.matcher(texto);
            while (migracion.find()) {
                if (!migraciones.contains(migracion.group(1))) {
                    fantasmas.add(fila.clave() + " cita la migracion " + migracion.group(1)
                            + ", que no esta en db/migration");
                }
            }

            Matcher metodo = METODO_CITADO.matcher(texto);
            while (metodo.find()) {
                Path fuente = clases.get(metodo.group(1));
                if (fuente == null) {
                    fantasmas.add(fila.clave() + " cita la clase " + metodo.group(1)
                            + ", que no existe en src/main");
                } else if (!declara(fuente, metodo.group(2))) {
                    fantasmas.add(fila.clave() + " cita " + metodo.group(1) + "."
                            + metodo.group(2) + ", que " + metodo.group(1) + " no declara");
                }
            }

            Matcher clase = CLASE_CITADA.matcher(texto);
            while (clase.find()) {
                if (!clases.containsKey(clase.group(1))) {
                    fantasmas.add(fila.clave() + " cita la clase " + clase.group(1)
                            + ", que no existe en src/main");
                }
            }

            Matcher suelto = METODO_SUELTO.matcher(texto);
            while (suelto.find()) {
                if (!metodosDelArbol().contains(suelto.group(1))) {
                    fantasmas.add(fila.clave() + " cita el metodo " + suelto.group(1)
                            + ", que nadie declara en src/main");
                }
            }
        }

        assertEquals(List.of(), fantasmas,
                "el catalogo nombra productores que no estan en el codigo: o se renombraron sin "
                        + "actualizar la evidencia, o nunca existieron");
    }

    /**
     * <b>Un PRODUCIDO sin ningun nombre propio no es evidencia</b> (V76).
     *
     * <p>«Baja de organizacion» tiene longitud de sobra y no dice quién la
     * hace. Siete filas estaban así, y dos de ellas resultaron no tener
     * productor en absoluto: la frase describía una operación que el sistema
     * nunca implementó.
     */
    @Test
    @DisplayName("todo PRODUCIDO nombra al menos un simbolo comprobable")
    void ningunProducidoSeJustificaSoloConPalabras() throws IOException {
        List<String> sinSimbolo = leerCatalogo().stream()
                .filter(f -> "PRODUCIDO".equals(f.clase()))
                .filter(f -> !citaAlgunSimbolo(limpiar(f.evidencia())))
                .map(f -> f.clave() + " -> \"" + f.evidencia() + "\"")
                .toList();

        assertEquals(List.of(), sinSimbolo,
                "un PRODUCIDO tiene que nombrar la clase, el metodo o la migracion que escribe "
                        + "el valor; una frase en prosa no se puede comprobar contra el codigo");
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

    // ------------------------------------------------------------------

    private static boolean citaAlgunSimbolo(String texto) {
        return CLASE_CITADA.matcher(texto).find()
                || METODO_CITADO.matcher(texto).find()
                || MIGRACION_CITADA.matcher(texto).find()
                || METODO_SUELTO.matcher(texto).find();
    }

    /**
     * Markdown fuera, y el sufijo {@code .java} también: los adornos no son
     * parte del nombre, y {@code Alerta.java} nombra la clase {@code Alerta},
     * no un método llamado {@code java}.
     */
    private static String limpiar(String evidencia) {
        return evidencia.replace("`", " ").replace("**", " ").replace("*", " ")
                .replace(".java", " ");
    }

    /**
     * ¿Declara la clase ese miembro? Se busca el nombre seguido de paréntesis
     * —así vale para un método y para el constructor— sin analizar Java: esto
     * es un gate de documentación, no un compilador.
     */
    private static boolean declara(Path fuente, String miembro) {
        try {
            String codigo = Files.readString(fuente);
            return Pattern.compile("\\b" + Pattern.quote(miembro) + "\\s*\\(").matcher(codigo).find();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + fuente, e);
        }
    }

    /** Todo fichero .java de produccion del reactor, en orden estable. */
    private static List<Path> fuentesDelArbol() throws IOException {
        try (Stream<Path> arbol = Files.walk(raiz())) {
            return arbol.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    /**
     * Nombre simple -> fichero, de todo el código de produccion del reactor.
     *
     * <p>Incluye los tipos <b>anidados</b>. No es un detalle: {@code
     * EstadoProspeccion} no tiene fichero propio —vive dentro de {@code
     * EstadosDominio}— y buscando solo nombres de fichero el gate lo daba por
     * inexistente, que es un falso positivo tan inutil como un falso negativo.
     */
    private static Map<String, Path> clasesDelArbol() throws IOException {
        Map<String, Path> clases = new LinkedHashMap<>();
        Pattern declaracion = Pattern.compile(
                "\\b(?:class|interface|enum|record)\\s+([A-Z][A-Za-z0-9_]*)");
        for (Path fuente : fuentesDelArbol()) {
            String nombre = fuente.getFileName().toString();
            clases.putIfAbsent(nombre.substring(0, nombre.length() - 5), fuente);
            Matcher anidada = declaracion.matcher(Files.readString(fuente));
            while (anidada.find()) {
                clases.putIfAbsent(anidada.group(1), fuente);
            }
        }
        return clases;
    }

    /** Cache: leer el arbol entero una vez por ejecucion, no por token. */
    private static Set<String> metodos;

    /**
     * Todo identificador declarado o invocado como {@code nombre(} en el
     * codigo de produccion. Es deliberadamente laxo —no distingue declaracion
     * de llamada— porque lo que comprueba es que el nombre <b>signifique
     * algo</b> en el arbol, no dónde está escrito.
     */
    private static Set<String> metodosDelArbol() {
        if (metodos != null) {
            return metodos;
        }
        Set<String> encontrados = new LinkedHashSet<>();
        Pattern invocacion = Pattern.compile("\\b([a-z][A-Za-z0-9_]*)\\s*\\(");
        try {
            for (Path fuente : fuentesDelArbol()) {
                Matcher m = invocacion.matcher(Files.readString(fuente));
                while (m.find()) {
                    encontrados.add(m.group(1));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo recorrer el arbol de fuentes", e);
        }
        metodos = encontrados;
        return metodos;
    }

    private static Set<String> migracionesDelArbol() throws IOException {
        Path migraciones = raiz().resolve(Path.of("controllocal-app", "src", "main", "resources",
                "db", "migration"));
        try (Stream<Path> archivos = Files.list(migraciones)) {
            return archivos.map(p -> p.getFileName().toString()).collect(LinkedHashSet::new,
                    Set::add, Set::addAll);
        }
    }

    /** La raiz del reactor, mire desde donde mire el runner. */
    private static Path raiz() {
        return Stream.of(Path.of("."), Path.of(".."), Path.of("..", ".."),
                        Path.of("backend-spring"))
                .filter(p -> Files.isRegularFile(p.resolve(Path.of("controllocal-app", "pom.xml"))))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontro la raiz del reactor (controllocal-app/pom.xml)"));
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
