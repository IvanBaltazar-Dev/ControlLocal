package com.controllocal.arquitectura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Una prueba no elige una fila de una tabla compartida sin desempatar.</b>
 *
 * <h2>Que se vigila, y por que esas tablas</h2>
 * {@code detalle_agente}, {@code persona_rol} y {@code detalle_broker} <b>no</b>
 * contienen solo filas de la organizacion de la semilla. Varias clases de
 * integracion montan sus propios tenants con sus propios agentes, y esas filas
 * <b>sobreviven</b> a la corrida: estas pruebas confirman, no van en una
 * transaccion que se deshace. Medido el 2026-08-31 sobre la instancia dedicada
 * del cierre, al terminar: la organizacion de la semilla con <b>15</b> agentes y
 * otras tres organizaciones con <b>1</b> cada una.
 *
 * <p>Una consulta que <b>recorta filas</b> sobre esas tablas y no dice en que
 * orden no elige "la primera": elige la que el plan devuelva primero, que
 * depende del orden <b>fisico</b> y cambia entre corridas. El dia que devuelve
 * un agente de un tenant vecino sin cartera, la clase falla por una consulta
 * vacia que no habla de lo que la prueba mide.
 *
 * <h2>La regla es el CONCEPTO, no la forma del defecto de ayer</h2>
 * La primera version de este gate comprobaba, literalmente, que el bloque no
 * contuviera {@code "limit 1"} sin contener {@code "order by"}. Se le escapaban
 * cuatro cosas y cazaba una de mas:
 * <pre>
 *   limit  1                  dos espacios, y el substring ya no casa
 *   fetch first 1 row only    la forma estandar
 *   limit ?                   el recorte por parametro
 *   order by count(*) desc    hay `order by`, y NO desempata nada
 *   limit 10                  "limit 10".contains("limit 1") -&gt; rojo FALSO
 * </pre>
 * Asi que la pregunta ya no es "¿aparece este texto?" sino las dos que
 * importan: <b>¿recorta filas?</b> y <b>¿desempata?</b>
 *
 * <p><b>Como se decide "desempata", y cual es su limite.</b> Se mira la cola del
 * {@code order by}, se le quitan los <b>parentesis con su contenido</b> y se
 * exige que quede alguna columna {@code id_*}. Quitar los parentesis no es un
 * detalle: {@code FocoDelBrokerIntegrationTest.broker()} ordenaba por
 * {@code (select count(*) …) desc} — dentro del parentesis hay un
 * {@code id_rol_broker}, asi que un patron ingenuo lo habria dado por bueno
 * cuando no desempata nada. Es una aproximacion, no un analizador de SQL: no
 * comprueba que la columna sea UNICA. Lo que descarta es lo que se ha visto
 * fallar de verdad — ordenar por un agregado, o no ordenar.
 *
 * <h2>Por que un gate y no una revision</h2>
 * Porque ya se intento a mano y <b>se quedo corto</b>. El 2026-08-31 se
 * corrigieron cinco sitios y se declaro el barrido cerrado; habia <b>once</b>.
 * Los seis que faltaban eran identicos a los cinco arreglados. Un barrido cuyo
 * universo lo escribe una persona vuelve a quedarse corto en cuanto alguien
 * copia y pega un ayudante mas — que es exactamente como nacieron los once.
 */
class ConsultasDeAgenteDeterministasTest {

    /**
     * <b>El reactor entero, no un modulo.</b> La primera version recorria
     * {@code src/test/java} relativo al basedir, es decir <b>solo</b>
     * {@code controllocal-app}: un gate cuyo argumento es que nadie escribe la
     * lista, con parte del arbol fuera de la lista.
     */
    private static final Path RAIZ_REACTOR = Path.of("..");

    /** Este fichero queda fuera del barrido: ver {@link #elDetectorDetecta}. */
    private static final String FICHERO_DEL_GATE = "ConsultasDeAgenteDeterministasTest.java";

    /**
     * Las tablas cuyo contenido <b>cruza tenants</b>, que son las que hacen
     * peligroso recortar sin orden. No es "toda consulta con limit": recortar
     * sin orden una tabla que la propia prueba acaba de poblar es legitimo.
     */
    private static final List<String> TABLAS_COMPARTIDAS =
            List.of("detalle_agente", "persona_rol", "detalle_broker");

    /** {@code limit N}, {@code limit ?} y {@code fetch first/next … row(s) only}. */
    private static final Pattern RECORTA_FILAS = Pattern.compile(
            "\\blimit\\s+(?:\\d+\\b|\\?)"
                    + "|\\bfetch\\s+(?:first|next)\\b[^;]{0,40}?\\brows?\\s+only\\b");

    /** Desde el ultimo {@code order by} hasta el final de la sentencia. */
    private static final Pattern COLA_DEL_ORDEN = Pattern.compile("\\border\\s+by\\b(.*)$");

    /** Una columna identificadora: lo minimo que puede desempatar. */
    private static final Pattern COLUMNA_ID = Pattern.compile("\\bid_[a-z_]+\\b");

    @Test
    @DisplayName("ninguna prueba recorta una tabla compartida sin desempatar")
    void ningunaConsultaCompartidaEsNoDeterminista() throws IOException {
        List<Path> ficheros = ficherosJava();

        // Control de universo: si el recorrido no encuentra ficheros, el verde
        // de abajo no significaria nada. Es el error que este gate existe para
        // no repetir.
        assertFalse(ficheros.isEmpty(),
                "el gate no encontro ningun fuente bajo " + RAIZ_REACTOR.toAbsolutePath()
                        + ". Un barrido que no ha mirado nada no es un barrido");

        List<String> hallazgos = new ArrayList<>();
        for (Path fichero : ficheros) {
            for (String sql : sentenciasSql(Files.readString(fichero))) {
                if (esNoDeterminista(sql)) {
                    hallazgos.add(fichero.getFileName().toString());
                }
            }
        }

        assertEquals(List.of(), hallazgos,
                "Estas consultas recortan filas de una tabla que cruza tenants SIN desempatar, "
                        + "asi que la fila que sale depende del orden fisico de la tabla y cambia "
                        + "entre corridas. Anade al `order by` una columna identificadora, y "
                        + "FUERA de parentesis: dentro no desempata nada. Y no lo arregles solo "
                        + "en las que aparecen aqui: vuelve a correr este gate, que es quien sabe "
                        + "cuantas hay.");
    }

    /**
     * <b>El control positivo del propio gate.</b>
     *
     * <p>Un detector roto no dice "estoy roto": dice <b>cero</b>. Y cero es
     * indistinguible de "todo en orden", que es justo como una comprobacion deja
     * de comprobar sin que nadie se entere. Asi que se le dan casos que
     * <b>tiene</b> que detectar y casos que <b>no</b>, con el <b>mismo</b>
     * {@code esNoDeterminista} que usa la prueba de arriba. Si fueran dos
     * implementaciones, esto no probaria nada sobre aquella.
     *
     * <p>Los primeros son las evasiones reales que la version anterior no veia,
     * y el {@code limit 10} el falso positivo que si producia.
     */
    @Test
    @DisplayName("el detector mide el concepto, no la forma del defecto de ayer")
    void elDetectorDetecta() {
        String origen = "select a.id_persona_rol from detalle_agente a "
                + "join persona_rol r on r.id_persona_rol = a.id_persona_rol ";

        // Lo que TIENE que detectar.
        assertTrue(esNoDeterminista(origen + "limit 1"), "el caso base");
        assertTrue(esNoDeterminista(origen + "limit  1"),
                "dos espacios: la version anterior comparaba el substring `limit 1` y esto se le "
                        + "escapaba entero");
        assertTrue(esNoDeterminista(origen + "fetch first 1 row only"),
                "la forma estandar de recortar tambien recorta");
        assertTrue(esNoDeterminista(origen + "limit ?"),
                "recortar por parametro sigue siendo recortar");
        assertTrue(esNoDeterminista(origen + "order by count(*) desc limit 1"),
                "hay `order by` y NO desempata: ordenar por un agregado deja el empate sin "
                        + "resolver, que es el defecto entero");
        assertTrue(esNoDeterminista(origen
                        + "order by (select count(*) from supervision_agente s2 "
                        + "where s2.id_rol_broker = a.id_persona_rol) desc limit 1"),
                "una columna id DENTRO de un parentesis no desempata la consulta de fuera. Es el "
                        + "caso real de FocoDelBrokerIntegrationTest.broker()");
        assertTrue(esNoDeterminista(origen + "limit 10"),
                "recortar 10 sin orden es tan indeterminado como recortar 1");

        // Lo que NO puede marcar.
        assertFalse(esNoDeterminista(origen + "order by a.id_persona_rol limit 1"),
                "una consulta ya desempatada no es un hallazgo, o el gate se vuelve ruido y "
                        + "alguien lo apaga");
        assertFalse(esNoDeterminista(origen + "order by count(*) desc, a.id_persona_rol limit 1"),
                "ordenar por un agregado y DESPUES por la clave si desempata");
        assertFalse(esNoDeterminista(
                        "select id_propiedad from propiedad where organizacion_id = ? limit 1"),
                "ni una consulta sobre una tabla que no cruza tenants: el alcance son "
                        + TABLAS_COMPARTIDAS);
        assertFalse(esNoDeterminista(origen + "order by a.id_persona_rol"),
                "sin recorte no hay eleccion que desempatar: la consulta devuelve todo");
    }

    /**
     * <b>El lector lee las dos formas de escribir SQL en este arbol.</b>
     *
     * <p>La primera version solo abria bloques de texto. En el mismo arbol hay
     * sentencias escritas como literal simple —con precedente vivo en
     * {@code PropiedadUniversalIntegrationTest}—, y ninguna infringia; pero un
     * gate cuyo argumento es que el universo no lo escribe nadie no puede
     * dejarse fuera media forma de escribir una consulta.
     *
     * <p><b>Ningun numero escrito a mano.</b> La version anterior exigia
     * "{@code > 100} bloques": un suelo inventado, que solo podia aflojar y que
     * ademas estaba lejos del real. Y la siguiente idea —"todo fuente que
     * contenga {@code select} tiene que dar una sentencia"— tampoco servia: la
     * palabra aparece dentro de {@code SELECTOR} y de "selector alfabetico" en
     * dos gates que no tocan SQL, asi que producia dos rojos falsos.
     *
     * <p>Lo que si es solido son <b>dos medidas del propio arbol</b>:
     * <ol>
     *   <li>un control positivo <b>sintetico</b>, que le da al lector una
     *       fuente con las dos formas y exige que saque las dos;</li>
     *   <li>y una <b>comparacion</b>: leer el reactor con las dos vias tiene que
     *       dar mas sentencias que leerlo solo con bloques. Si alguien rompe la
     *       via de los literales, las dos cifras se igualan y esto se pone rojo
     *       — sin que nadie tenga que saber cuantas sentencias hay.</li>
     * </ol>
     */
    @Test
    @DisplayName("el lector no se deja fuera ninguna forma de escribir SQL")
    void elLectorLeeLasDosFormas() throws IOException {
        // 1. Control positivo sintetico: las dos formas, en una fuente de tres
        //    lineas, y las dos tienen que salir.
        String fuenteDePrueba = "class X { String a = \"\"\"\n"
                + "select 1 from detalle_agente\n"
                + "\"\"\"; String b = \"select 2 \" + \"from persona_rol\"; }";
        List<String> delControl = sentenciasSql(fuenteDePrueba);
        assertEquals(2, delControl.size(),
                "el lector tiene que sacar la del bloque Y la del literal partido. Saco: "
                        + delControl);

        // 2. Medida del arbol: la via de los literales aporta de verdad.
        int conLasDosVias = 0;
        int soloBloques = 0;
        for (Path fichero : ficherosJava()) {
            String fuente = Files.readString(fichero);
            conLasDosVias += sentenciasSql(fuente).size();
            List<String> deBloques = new ArrayList<>();
            extraerBloques(fuente, deBloques);
            soloBloques += deBloques.size();
        }
        assertTrue(soloBloques > 0,
                "el lector no saco ni una sentencia de un bloque de texto en todo el reactor: "
                        + "eso no es un arbol sin SQL, es un lector roto");
        assertTrue(conLasDosVias > soloBloques,
                "leer con las dos vias tiene que dar MAS sentencias que leer solo bloques, "
                        + "porque este arbol escribe SQL de las dos formas. Salio "
                        + conLasDosVias + " y " + soloBloques + ": si se igualan, la via de los "
                        + "literales dejo de leer y media forma de escribir una consulta se "
                        + "quedo fuera del universo del gate");
    }

    /**
     * <b>Y el barrido cubre mas de un modulo.</b> Si {@link #RAIZ_REACTOR}
     * dejara de resolver al reactor —un cambio de layout, una invocacion desde
     * otro directorio—, el gate seguiria verde mirando la mitad. Esto lo
     * convierte en rojo.
     */
    @Test
    @DisplayName("el barrido llega a todos los modulos con pruebas, no solo al suyo")
    void elBarridoCubreElReactor() throws IOException {
        List<String> modulos = ficherosJava().stream()
                .map(ConsultasDeAgenteDeterministasTest::moduloDe)
                .distinct()
                .sorted()
                .toList();
        assertTrue(modulos.size() > 1,
                "el barrido solo alcanzo " + modulos + ". Con un unico modulo cubierto, este gate "
                        + "estaria dando por barrido un arbol que no ha visto");
    }

    // ==================================================================

    /** El modulo del reactor al que pertenece un fuente. */
    private static String moduloDe(Path fichero) {
        Path absoluta = fichero.toAbsolutePath().normalize();
        for (int i = 1; i < absoluta.getNameCount(); i++) {
            if (absoluta.getName(i).toString().equals("src")) {
                return absoluta.getName(i - 1).toString();
            }
        }
        return "?";
    }

    private static List<Path> ficherosJava() throws IOException {
        assertTrue(Files.isDirectory(RAIZ_REACTOR),
                "No se encontro " + RAIZ_REACTOR.toAbsolutePath());
        try (Stream<Path> ficheros = Files.walk(RAIZ_REACTOR)) {
            return ficheros
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/test/java/"))
                    .filter(p -> !p.getFileName().toString().equals(FICHERO_DEL_GATE))
                    .sorted()
                    .toList();
        }
    }

    /**
     * El defecto, en una sola definicion y en dos preguntas: <b>¿recorta filas
     * de una tabla compartida?</b> y <b>¿desempata?</b>
     */
    private static boolean esNoDeterminista(String sentencia) {
        String sql = normalizada(sentencia);
        if (TABLAS_COMPARTIDAS.stream().noneMatch(sql::contains)) {
            return false;
        }
        if (!RECORTA_FILAS.matcher(sql).find()) {
            return false;
        }
        return !desempata(sql);
    }

    /**
     * ¿Queda alguna columna identificadora en el {@code order by}, <b>fuera</b>
     * de cualquier parentesis?
     */
    private static boolean desempata(String sql) {
        Matcher orden = COLA_DEL_ORDEN.matcher(sql);
        if (!orden.find()) {
            return false;
        }
        return COLUMNA_ID.matcher(sinParentesis(orden.group(1))).find();
    }

    /** Quita los parentesis y su contenido, tantas veces como haga falta. */
    private static String sinParentesis(String texto) {
        String anterior;
        String actual = texto;
        do {
            anterior = actual;
            actual = anterior.replaceAll("\\([^()]*\\)", " ");
        } while (!actual.equals(anterior));
        return actual;
    }

    /** Minusculas y un solo espacio: el doble espacio dejo de ser una evasion. */
    private static String normalizada(String sentencia) {
        return sentencia.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * Las sentencias SQL de un fuente Java, en <b>las dos</b> formas en que este
     * arbol las escribe: bloques de texto y literales simples, incluidos los
     * partidos en varias lineas con {@code +}.
     */
    static List<String> sentenciasSql(String fuente) {
        List<String> sentencias = new ArrayList<>();
        String resto = extraerBloques(fuente, sentencias);
        extraerLiterales(resto, sentencias);
        return sentencias;
    }

    /** Bloques de texto; devuelve el fuente sin ellos. */
    private static String extraerBloques(String fuente, List<String> acumulador) {
        String marca = "\"\"\"";
        StringBuilder restante = new StringBuilder();
        int desde = 0;
        while (true) {
            int abre = fuente.indexOf(marca, desde);
            if (abre < 0) {
                restante.append(fuente, desde, fuente.length());
                return restante.toString();
            }
            int cierra = fuente.indexOf(marca, abre + marca.length());
            if (cierra < 0) {
                throw new IllegalStateException("bloque de texto sin cerrar: el fuente no compila");
            }
            restante.append(fuente, desde, abre);
            anadirSiEsSql(fuente.substring(abre + marca.length(), cierra), acumulador);
            desde = cierra + marca.length();
        }
    }

    /**
     * Literales simples, uniendo los que se concatenan con {@code +}. Sin unir,
     * una consulta partida en tres lineas pareceria tres trozos sueltos y el
     * detector no veria el {@code from} y el {@code limit} en la misma
     * sentencia.
     */
    private static void extraerLiterales(String fuente, List<String> acumulador) {
        Matcher literal = Pattern.compile("\"(?:[^\"\\\\\\n]|\\\\.)*\"").matcher(fuente);
        StringBuilder unido = new StringBuilder();
        int finAnterior = -1;
        while (literal.find()) {
            String pieza = fuente.substring(literal.start() + 1, literal.end() - 1);
            boolean continua = finAnterior >= 0
                    && fuente.substring(finAnterior, literal.start()).matches("[\\s]*\\+[\\s]*");
            if (!continua) {
                anadirSiEsSql(unido.toString(), acumulador);
                unido.setLength(0);
            }
            unido.append(pieza);
            finAnterior = literal.end();
        }
        anadirSiEsSql(unido.toString(), acumulador);
    }

    private static void anadirSiEsSql(String texto, List<String> acumulador) {
        String bajo = texto.toLowerCase(Locale.ROOT);
        if (bajo.contains("select") || bajo.contains("insert into")
                || bajo.contains("update ") || bajo.contains("delete from")) {
            acumulador.add(texto);
        }
    }
}
