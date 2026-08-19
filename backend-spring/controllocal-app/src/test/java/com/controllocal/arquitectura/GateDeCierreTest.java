package com.controllocal.arquitectura;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Un reactor que omite en silencio los tests de PostgreSQL no es un gate de
 * cierre.</b>
 *
 * <p>Los seis tests de integracion llevan
 * {@code @EnabledIfEnvironmentVariable(named = "TEST_DB_URL")}, asi que sin esa
 * variable JUnit los SALTA sin decir nada y Maven termina en verde. Eso ya
 * costo caro una vez: las migraciones V31, V37 y V38 introdujeron tres
 * columnas {@code estado} con la palabra completa —rompiendo el invariante de
 * codigo unitario que {@code RepositorioEstadosIntegrationTest} existe para
 * proteger— y el build siguio pasando durante todo el bloque de seguridad
 * porque ese gate nunca llego a ejecutarse. Lo arreglo V40.
 *
 * <p>Esta clase cierra el agujero por dentro: cuando la corrida se declara DE
 * CIERRE ({@code CONTROLLOCAL_CIERRE=1}, que pone {@code Verificar-Cierre.ps1}),
 * la ausencia de {@code TEST_DB_URL} deja de ser un salto silencioso y pasa a
 * ser un fallo. En una corrida normal de desarrollo no molesta.
 *
 * <p>Vive en el modulo {@code app} porque es donde estan los tests que vigila.
 */
class GateDeCierreTest {

    private static final String CIERRE = "CONTROLLOCAL_CIERRE";
    private static final String URL = "TEST_DB_URL";

    @Test
    void unaCorridaDeCierreNoPuedeOmitirLosTestsDePostgreSql() {
        if (!"1".equals(System.getenv(CIERRE))) {
            return;
        }
        String url = System.getenv(URL);
        assertTrue(url != null && !url.isBlank(),
                "Corrida de cierre sin " + URL + ": los tests de integracion se habrian saltado "
                        + "en silencio y el verde no significaria nada. Exporta "
                        + URL + "=jdbc:postgresql://localhost:5433/controllocal_repositorios "
                        + "o no declares la corrida como de cierre.");
        assertTrue(url.startsWith("jdbc:postgresql:"),
                URL + " tiene que apuntar a PostgreSQL real, no a otro motor: " + url);
    }

    /**
     * La guarda solo sirve si sigue cubriendo TODOS los tests que dependen de
     * la base. Si alguien anade un sexto test de integracion, esta prueba lo
     * obliga a declararlo aqui —y por tanto a pensar si la corrida de cierre
     * lo cubre— en vez de dejarlo saltando en silencio para siempre.
     */
    @Test
    void todoTestDeIntegracionDependeDeLaMismaVariableYEstaInventariado() throws IOException {
        Path raiz = Path.of("src", "test", "java", "com", "controllocal", "integracion");
        assertTrue(Files.isDirectory(raiz), "No se encontro " + raiz.toAbsolutePath());

        List<String> conBaseDeDatos;
        try (Stream<Path> ficheros = Files.list(raiz)) {
            conBaseDeDatos = ficheros
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .filter(f -> contiene(f, "@EnabledIfEnvironmentVariable"))
                    .map(f -> f.getFileName().toString().replace(".java", ""))
                    .sorted()
                    .toList();
        }

        assertEquals(List.of(
                        // D-E4-3 — una autoridad persistente por clave publicada.
                        "AutoridadDelDatoIntegrationTest",
                        "BusquedaLocalesIntegrationTest",
                        // E0.2 — el historico economico contra PostgreSQL real.
                        "FocoDelBrokerIntegrationTest",
                        "HistoricoPrecioIntegrationTest",
                        "IdentidadDelBrokerIntegrationTest",
                        "InterpretacionDelInicioIntegrationTest",
                        "InvariantesComisionIntegrationTest",
                        // D-E4-1 — las tres piezas del nucleo universal contra
                        // PostgreSQL: titularidad, atributos gobernados y outbox.
                        "NucleoUniversalIntegrationTest",
                        "OcupacionInmuebleIntegrationTest",
                        "PadronDeGobiernoIntegrationTest",
                        // D-E4-1 / D-E4-2 — los 15 escenarios de aceptacion de la
                        // propiedad universal y la captura. Es el unico que COMETE
                        // de verdad, en tenants propios: cuatro de sus invariantes
                        // (cuotas diferidas, idempotencia, rollback y encargos
                        // simultaneos) no existen dentro de una transaccion que se
                        // deshace.
                        "PropiedadUniversalIntegrationTest",
                        "RepositorioEstadosIntegrationTest",
                        "SimulacroRecuperacionIntegrationTest",
                        "VocabularioPersistidoIntegrationTest"),
                conBaseDeDatos,
                "Cambio el inventario de tests de integracion. Actualiza esta lista y comprueba "
                        + "que Verificar-Cierre.ps1 sigue exigiendo que TODOS se ejecuten.");

        try (Stream<Path> ficheros = Files.list(raiz)) {
            List<String> conOtraVariable = ficheros
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .filter(f -> contiene(f, "@EnabledIfEnvironmentVariable"))
                    .filter(f -> !contiene(f, "named = \"" + URL + "\""))
                    .map(f -> f.getFileName().toString())
                    .toList();
            assertEquals(List.of(), conOtraVariable,
                    "Un test de integracion se activa con otra variable: la corrida de cierre "
                            + "no lo cubriria.");
        }
    }

    /** El script de cierre tiene que seguir existiendo y exigiendo la variable. */
    @Test
    void elScriptDeCierreExigeLaBaseDeIntegracion() {
        Path script = Path.of("..", "verificacion", "Verificar-Cierre.ps1");
        assertTrue(Files.isRegularFile(script),
                "Falta verificacion/Verificar-Cierre.ps1, que es el comando de cierre.");
        String texto = leer(script);
        assertTrue(texto.contains(URL), "El script de cierre no menciona " + URL);
        assertTrue(texto.contains(CIERRE),
                "El script de cierre no exporta " + CIERRE + ", asi que la guarda no se activa.");
        assertFalse(texto.contains("-DskipTests"),
                "Una corrida de cierre no puede saltarse los tests.");
    }

    private static boolean contiene(Path fichero, String fragmento) {
        return leer(fichero).contains(fragmento);
    }

    private static String leer(Path fichero) {
        try {
            return Files.readString(fichero);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + fichero, e);
        }
    }
}
