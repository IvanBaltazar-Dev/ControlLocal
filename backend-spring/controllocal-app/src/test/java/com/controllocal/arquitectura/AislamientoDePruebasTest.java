package com.controllocal.arquitectura;

import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Una prueba de integracion no puede escribir en la base de desarrollo.</b>
 *
 * <p>El 18 y el 19 de agosto de 2026 corrio contra {@code controllocal_dev} y
 * dejo 162 propiedades, 120 captaciones y 184 hitos de precio. No fue un
 * descuido aislado: nada lo impedia. Cada test leia {@code TEST_DB_URL} por su
 * cuenta y le pasaba a Spring lo que hubiera dentro.
 *
 * <p>El dano no fue de datos sino de <b>evidencia</b>. La cabecera del Inicio
 * paso a decir «125 cosas necesitan tu atencion» —120 eran captaciones de
 * prueba— y la unica celda con muestra del contraste de renta paso a tener 42
 * filas a 7000 y 21 a 7500, que parecen 63 observaciones y son dos.
 *
 * <p>{@link BaseDeDatosDePruebas} cierra la puerta. Esta clase comprueba que
 * <b>siga cerrada</b>: que ninguna prueba la rodee, y que la guarda distinga de
 * verdad una base de pruebas de una que no lo es. Sin este gate, la guarda dura
 * hasta la proxima prueba escrita copiando y pegando otra —que es exactamente
 * como se escribieron las catorce que hay—.
 */
class AislamientoDePruebasTest {

    private static final Path RAIZ =
            Path.of("src", "test", "java", "com", "controllocal", "integracion");

    // ------------------------------------------------------------------
    // Que nadie rodee la guarda
    // ------------------------------------------------------------------

    /**
     * Leer la variable a mano es exactamente lo que permitio el incidente: la
     * prueba recibe la url sin que nadie mire a donde apunta.
     */
    @Test
    void ningunaPruebaDeIntegracionLeeLaVariablePorSuCuenta() throws IOException {
        List<String> culpables = pruebas(f -> contiene(f, "getenv(\"" + BaseDeDatosDePruebas.VARIABLE + "\")"));

        assertEquals(List.of(), culpables,
                "Estas pruebas leen " + BaseDeDatosDePruebas.VARIABLE + " directamente y se saltan "
                        + "la guarda: podrian correr contra controllocal_dev. Usa "
                        + "BaseDeDatosDePruebas.registrar(propiedades).");
    }

    /**
     * La otra forma de rodearla: registrar el origen de datos a mano, con la url
     * sacada de cualquier otro sitio.
     */
    @Test
    void ningunaPruebaDeIntegracionRegistraSuPropioOrigenDeDatos() throws IOException {
        List<String> culpables = pruebas(f -> contiene(f, "\"spring.datasource.url\""));

        assertEquals(List.of(), culpables,
                "Estas pruebas registran spring.datasource.url por su cuenta. El origen de datos "
                        + "de una prueba de integracion sale de BaseDeDatosDePruebas y de ningun "
                        + "otro sitio.");
    }

    /**
     * Y la guarda solo protege lo que toca: si una prueba de integracion no pasa
     * por ella, no esta protegida aunque las demas lo esten.
     */
    @Test
    void todaPruebaDeIntegracionPasaPorLaGuarda() throws IOException {
        List<String> sinGuarda = pruebas(f -> contiene(f, "@EnabledIfEnvironmentVariable")
                && !contiene(f, "BaseDeDatosDePruebas.registrar"));

        assertEquals(List.of(), sinGuarda,
                "Estas pruebas dependen de PostgreSQL y no pasan por BaseDeDatosDePruebas.");
    }

    // ------------------------------------------------------------------
    // Que la guarda distinga de verdad
    // ------------------------------------------------------------------

    /** El caso que ocurrio. Es el que no puede volver a pasar. */
    @Test
    void laBaseDeDesarrolloSeRechaza() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> BaseDeDatosDePruebas.validar("jdbc:postgresql://localhost:5433/controllocal_dev"));

        assertTrue(e.getMessage().contains("controllocal_dev"),
                "El mensaje tiene que nombrar la base rechazada: " + e.getMessage());
        assertTrue(e.getMessage().contains("controllocal_repositorios"),
                "El mensaje tiene que decir cual usar, o se resolvera quitando la guarda: "
                        + e.getMessage());
    }

    /** Con parametros detras, el nombre sigue siendo el mismo. */
    @Test
    void laBaseDeDesarrolloSeRechazaTambienConParametrosEnLaUrl() {
        assertThrows(IllegalStateException.class, () -> BaseDeDatosDePruebas.validar(
                "jdbc:postgresql://localhost:5433/controllocal_dev?ssl=false&ApplicationName=x"));
    }

    /** La que si es de pruebas pasa, con y sin parametros. */
    @Test
    void laBaseDelReactorSeAcepta() {
        assertDoesNotThrow(() -> BaseDeDatosDePruebas.validar(
                "jdbc:postgresql://localhost:5433/controllocal_repositorios"));
        assertDoesNotThrow(() -> BaseDeDatosDePruebas.validar(
                "jdbc:postgresql://localhost:5433/controllocal_repositorios?ssl=false"));
    }

    /** Las efimeras de testcontainers y CI, que no se pueden listar de antemano. */
    @Test
    void lasBasesEfimerasSeAceptan() {
        for (String base : List.of("test_abc123", "tc_9f2", "controllocal_ci_1421",
                "algo_pruebas", "rama_test", "nightly_ci")) {
            assertDoesNotThrow(
                    () -> BaseDeDatosDePruebas.validar("jdbc:postgresql://localhost:5433/" + base),
                    "Deberia aceptarse como base efimera: " + base);
        }
    }

    /**
     * La guarda deniega por defecto. Una base nueva no hereda permiso porque el
     * nombre suene bien.
     */
    @Test
    void unaBaseDesconocidaSeRechaza() {
        for (String base : List.of("controllocal", "produccion", "controllocal_prod",
                "controllocal_demo", "postgres")) {
            assertThrows(IllegalStateException.class,
                    () -> BaseDeDatosDePruebas.validar("jdbc:postgresql://localhost:5433/" + base),
                    "No deberia aceptarse sin declararla: " + base);
        }
    }

    /** Ni otro motor, ni una url sin base. */
    @Test
    void unaUrlQueNoEsPostgreSqlONoNombraBaseSeRechaza() {
        assertThrows(IllegalStateException.class,
                () -> BaseDeDatosDePruebas.validar("jdbc:h2:mem:controllocal_test"));
        assertThrows(IllegalStateException.class,
                () -> BaseDeDatosDePruebas.validar("jdbc:postgresql://localhost:5433/"));
    }

    /**
     * Y la base de desarrollo no puede colarse en la lista de autorizadas, que
     * es el atajo evidente cuando la guarda estorba.
     */
    @Test
    void laListaDeAutorizadasNoPuedeIncluirLaBaseDeDesarrollo() {
        assertTrue(BaseDeDatosDePruebas.AUTORIZADAS.stream().noneMatch(b -> b.contains("_dev")),
                "Una base de desarrollo no puede estar autorizada: "
                        + BaseDeDatosDePruebas.AUTORIZADAS);
    }

    // ------------------------------------------------------------------

    private static List<String> pruebas(java.util.function.Predicate<Path> criterio)
            throws IOException {
        assertTrue(Files.isDirectory(RAIZ), "No se encontro " + RAIZ.toAbsolutePath());
        try (Stream<Path> ficheros = Files.walk(RAIZ)) {
            return ficheros
                    .filter(f -> f.getFileName().toString().endsWith("IntegrationTest.java"))
                    .filter(criterio)
                    .map(f -> f.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    private static boolean contiene(Path fichero, String texto) {
        try {
            return Files.readString(fichero).contains(texto);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
