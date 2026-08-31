package com.controllocal.arquitectura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Una prueba no elige "un agente" con un {@code limit 1} sin {@code order
 * by}.</b>
 *
 * <h2>Por que existe, con la medida delante</h2>
 * {@code detalle_agente} <b>no</b> contiene solo agentes de la organizacion de
 * la semilla. Varias clases de integracion montan sus propios tenants y les
 * crean agentes, y esas filas <b>sobreviven</b> a la corrida: estas pruebas
 * confirman, no van en una transaccion que se deshace. Medido el 2026-08-31
 * sobre la instancia dedicada del cierre, al terminar: la organizacion de la
 * semilla con <b>15</b> agentes y otras tres organizaciones con <b>1</b> cada
 * una.
 *
 * <p>Un {@code select … from detalle_agente … limit 1} <b>sin orden</b> no
 * elige el primero: elige el que el plan devuelva primero, que depende del
 * orden <b>fisico</b> de la tabla y cambia entre corridas. El dia que devuelve
 * un agente de un tenant vecino sin cartera, la clase falla por una consulta
 * vacia que no habla de lo que la prueba mide — {@code
 * EmptyResultDataAccessException} en
 * {@code InterpretacionDelInicioIntegrationTest.unaFechaAusenteSeDeclara}, el
 * 2026-08-30.
 *
 * <h2>Por que un gate y no una revision</h2>
 * Porque ya se intento a mano y <b>se quedo corto</b>. El 2026-08-31 se
 * corrigieron cinco sitios y se declaro el barrido cerrado; habia <b>once</b>.
 * Los seis que faltaban eran identicos a los cinco arreglados. Un barrido cuyo
 * universo depende de una lista escrita a mano vuelve a quedarse corto en
 * cuanto alguien copia y pega un ayudante mas — que es exactamente como
 * nacieron los once.
 *
 * <p>Asi que la lista no la escribe nadie: la produce el recorrido del arbol.
 * Si manana aparece un ayudante nuevo con el defecto, este gate lo encuentra
 * sin que haya que acordarse de nada.
 */
class ConsultasDeAgenteDeterministasTest {

    private static final Path RAIZ =
            Path.of("src", "test", "java", "com", "controllocal");

    /** Este fichero queda fuera del barrido: ver {@link #elDetectorDetecta}. */
    private static final String FICHERO_DEL_GATE = "ConsultasDeAgenteDeterministasTest.java";

    /**
     * Las tablas cuyo contenido <b>cruza tenants</b>, que son las que hacen
     * peligroso el {@code limit 1} sin orden. No es "toda consulta con limit":
     * elegir sin orden una fila de una tabla que la propia prueba acaba de
     * poblar es legitimo y no se toca.
     */
    private static final List<String> TABLAS_COMPARTIDAS =
            List.of("detalle_agente", "persona_rol");

    @Test
    @DisplayName("ninguna prueba elige un agente con un limit 1 sin order by")
    void ningunaConsultaDeAgenteEsNoDeterminista() throws IOException {
        List<Path> ficheros = ficherosJava();

        // Control de universo: si el recorrido no encuentra ficheros, el verde
        // de abajo no significaria nada. Es el error que este gate existe para
        // no repetir.
        assertFalse(ficheros.isEmpty(),
                "el gate no encontro ningun fichero de prueba bajo " + RAIZ.toAbsolutePath()
                        + ". Un barrido que no ha mirado nada no es un barrido");

        List<String> hallazgos = new ArrayList<>();
        for (Path fichero : ficheros) {
            for (String bloque : bloquesDeTexto(Files.readString(fichero))) {
                if (esNoDeterminista(bloque)) {
                    hallazgos.add(RAIZ.relativize(fichero).toString());
                }
            }
        }

        assertEquals(List.of(), hallazgos,
                "Estas consultas eligen una fila de una tabla que cruza tenants con `limit 1` y "
                        + "SIN `order by`, asi que la fila que sale depende del orden fisico de "
                        + "la tabla y cambia entre corridas. Anade un `order by` por clave "
                        + "primaria. Y no lo arregles solo en las que aparecen aqui: vuelve a "
                        + "correr este gate, que es quien sabe cuantas hay.");
    }

    /**
     * <b>El control positivo del propio gate.</b>
     *
     * <p>Un detector roto no dice "estoy roto": dice <b>cero</b>. Y cero es
     * indistinguible de "todo en orden", que es justo como una comprobacion
     * deja de comprobar sin que nadie se entere. Asi que se le da de comer un
     * caso que <b>tiene</b> que detectar y dos que <b>no</b>, y se exige que
     * los distinga — con el <b>mismo</b> {@code esNoDeterminista} que usa la
     * prueba de arriba. Si fueran dos implementaciones, esto no probaria nada
     * sobre aquella.
     *
     * <p>Y por eso este fichero se excluye del recorrido: contiene a proposito
     * el defecto, asi que sin la exclusion el gate se denunciaria a si mismo
     * con un rojo que no se puede arreglar — y un gate asi acaba borrado.
     */
    @Test
    @DisplayName("el detector distingue el defecto de lo correcto")
    void elDetectorDetecta() {
        String defectuosa = "select a.id_persona_rol, r.organizacion_id\n"
                + "  from detalle_agente a join persona_rol r"
                + " on r.id_persona_rol = a.id_persona_rol\n"
                + " limit 1\n";
        String corregida = "select a.id_persona_rol, r.organizacion_id\n"
                + "  from detalle_agente a join persona_rol r"
                + " on r.id_persona_rol = a.id_persona_rol\n"
                + " order by a.id_persona_rol limit 1\n";
        String ajena = "select id_propiedad from propiedad where organizacion_id = ?\n"
                + " limit 1\n";

        assertTrue(esNoDeterminista(defectuosa),
                "el detector NO ve el defecto que este gate existe para encontrar. Mientras esto "
                        + "sea asi, el verde de la otra prueba no significa nada");
        assertFalse(esNoDeterminista(corregida),
                "y no puede marcar como defecto una consulta ya ordenada, o el gate se vuelve "
                        + "ruido y alguien lo apaga");
        assertFalse(esNoDeterminista(ajena),
                "ni una consulta sobre una tabla que no cruza tenants: el alcance del gate son "
                        + TABLAS_COMPARTIDAS);
    }

    /**
     * <b>Y el lector de bloques lee de verdad.</b>
     *
     * <p>La otra mitad silenciosa: si {@code bloquesDeTexto} devolviera siempre
     * la lista vacia, el barrido recorreria los ficheros, no encontraria nada y
     * daria verde. El universo tiene que medirse, no suponerse.
     */
    @Test
    @DisplayName("el barrido lee bloques de verdad, y son muchos")
    void elBarridoLeeBloques() throws IOException {
        int bloques = 0;
        for (Path fichero : ficherosJava()) {
            bloques += bloquesDeTexto(Files.readString(fichero)).size();
        }
        assertTrue(bloques > 100,
                "el arbol de pruebas tiene cientos de bloques SQL; si aqui salen " + bloques
                        + ", el lector de bloques no esta leyendo y el barrido no mira nada");
    }

    // ==================================================================

    private static List<Path> ficherosJava() throws IOException {
        assertTrue(Files.isDirectory(RAIZ), "No se encontro " + RAIZ.toAbsolutePath());
        try (Stream<Path> ficheros = Files.walk(RAIZ)) {
            return ficheros
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals(FICHERO_DEL_GATE))
                    .sorted()
                    .toList();
        }
    }

    /**
     * El defecto, en una sola definicion: nombra una tabla que cruza tenants,
     * corta con {@code limit 1} y no dice en que orden.
     */
    private static boolean esNoDeterminista(String bloque) {
        String sql = bloque.toLowerCase(Locale.ROOT);
        if (!sql.contains("limit 1") || sql.contains("order by")) {
            return false;
        }
        return TABLAS_COMPARTIDAS.stream().anyMatch(sql::contains);
    }

    /** Los bloques de texto de un fuente Java. */
    private static List<String> bloquesDeTexto(String fuente) {
        String marca = "\"\"\"";
        List<String> bloques = new ArrayList<>();
        int desde = 0;
        while (true) {
            int abre = fuente.indexOf(marca, desde);
            if (abre < 0) {
                return bloques;
            }
            int cierra = fuente.indexOf(marca, abre + marca.length());
            if (cierra < 0) {
                throw new UncheckedIOException(
                        new IOException("bloque de texto sin cerrar: el fuente no compila"));
            }
            bloques.add(fuente.substring(abre + marca.length(), cierra));
            desde = cierra + marca.length();
        }
    }
}
