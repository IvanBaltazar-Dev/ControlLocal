package com.controllocal.arquitectura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>La cartera se busca por un solo sitio</b> (2026-09-02).
 *
 * <h2>Por que existe, con nombre y fecha</h2>
 * Habia dos motores de busqueda sobre la MISMA tabla. {@code GET /locales}
 * usaba la reescritura por conjunto de candidatos que cerro RC-003 -una rama
 * por tabla, cada una con su indice trigrama, unidas por UNION-; y
 * {@code GET /propiedades}, que es el listado que el producto usa de verdad,
 * llevaba una consulta paralela: un unico OR de cuatro {@code like} cruzando
 * tres tablas, exactamente el predicado que RC-003 documento como Seq Scan.
 *
 * <p>Nadie lo escribio para hacer algo distinto. El listado universal nacio
 * despues, resolvio el mismo problema otra vez y <b>dejo de enterarse</b> de lo
 * que el primero habia aprendido: ni un indice trigrama, ni el plan
 * personalizado, ni el conteo sobre el mismo conjunto. El sintoma fue que un
 * p95 verde en {@code locales-busqueda} no decia nada del universal, y nadie
 * estaba midiendo el universal.
 *
 * <p>Eso no se arregla arreglando el segundo: el siguiente listado inmobiliario
 * que aparezca volveria a escribir el tercero. Lo que lo cierra es <b>que no
 * pueda haber un segundo</b>.
 *
 * <h2>Que vigila</h2>
 * <ol>
 *   <li>que el predicado de texto sobre la cartera este escrito UNA vez, en
 *       {@code MotorBusquedaInmobiliaria};</li>
 *   <li>que los dos servicios que publican un listado inmobiliario dependan de
 *       ese motor, para que ninguno pueda volver a llevar el suyo dentro.</li>
 * </ol>
 *
 * <h2>Lo que NO vigila, y por que</h2>
 * Las busquedas de oportunidades, visitas, interacciones, solicitudes y
 * contratos usan la misma <i>estrategia</i> sobre <b>otras tablas</b> y con
 * alcance por rol. Comparten forma, no consulta, y unificarlas es otra
 * conversacion —mas grande— que este gate no prejuzga.
 */
class UnSoloMotorDeBusquedaTest {

    private static final Path RAIZ_REACTOR = Path.of("..");

    private static final String MOTOR = "MotorBusquedaInmobiliaria.java";

    /**
     * La huella de una busqueda de texto sobre la cartera.
     *
     * <p>Son las tres columnas de {@code propiedad} por las que se busca, ya
     * normalizadas con {@code lower(...)}. No hay forma de escribir un filtro de
     * texto sobre el inmueble sin nombrar al menos una, y no aparecen en ninguna
     * otra consulta del sistema: por eso sirven de deteccion sin falsos
     * positivos.
     */
    private static final List<String> COLUMNAS_DE_TEXTO =
            List.of("lower(p.codigo)", "lower(p.direccion)", "lower(p.distrito)");

    @Test
    @DisplayName("el predicado de texto de la cartera esta escrito en UN solo fichero")
    void unSoloPredicadoDeTexto() throws IOException {
        List<Path> conBusqueda = ficherosDeProduccion().stream()
                .filter(UnSoloMotorDeBusquedaTest::buscaTextoSobreLaCartera)
                .toList();

        // El control positivo va primero, y no es ceremonia: si el patron
        // dejara de casar -porque el motor cambia de forma-, este gate pasaria
        // a estar verde vigilando nada, que es la unica manera de que un gate
        // mienta sin que se note.
        assertTrue(conBusqueda.stream().anyMatch(p -> p.getFileName().toString().equals(MOTOR)),
                "El gate no encuentra el predicado NI SIQUIERA en " + MOTOR
                        + ": el patron dejo de casar y esta comprobacion ya no vigila nada.");

        List<String> intrusos = conBusqueda.stream()
                .map(p -> p.getFileName().toString())
                .filter(nombre -> !nombre.equals(MOTOR))
                .sorted()
                .toList();
        assertEquals(List.of(), intrusos,
                "Hay una SEGUNDA busqueda de texto sobre la cartera fuera de " + MOTOR + ": "
                        + intrusos + ". La cartera se busca por un solo sitio; si a este listado "
                        + "le falta algo, lo que se amplia es el criterio del motor "
                        + "(CriterioBusquedaInmobiliaria), no una consulta nueva.");
    }

    @Test
    @DisplayName("los dos listados inmobiliarios consumen el motor, no una consulta propia")
    void losDosListadosUsanElMotor() {
        JavaClasses clases = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.controllocal");

        for (String servicio : List.of("LocalComercialServiceImpl", "PropiedadUniversalServiceImpl")) {
            classes().that().haveSimpleName(servicio)
                    .should().dependOnClassesThat().haveSimpleName("MotorBusquedaInmobiliaria")
                    .because(servicio + " publica un listado inmobiliario, y el listado "
                            + "inmobiliario lo resuelve el motor comun. Si deja de depender de el "
                            + "es que ha vuelto a llevar su propia busqueda dentro.")
                    .check(clases);
        }
    }

    // ------------------------------------------------------------------

    private static boolean buscaTextoSobreLaCartera(Path fichero) {
        String fuente = contenido(fichero).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (!fuente.contains("like")) {
            return false;
        }
        return COLUMNAS_DE_TEXTO.stream().anyMatch(fuente::contains);
    }

    private static String contenido(Path fichero) {
        try {
            return Files.readString(fichero, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + fichero, e);
        }
    }

    private static List<Path> ficherosDeProduccion() throws IOException {
        assertTrue(Files.isDirectory(RAIZ_REACTOR),
                "No se encontro el reactor en " + RAIZ_REACTOR.toAbsolutePath());
        try (Stream<Path> ficheros = Files.walk(RAIZ_REACTOR)) {
            return ficheros
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .sorted()
                    .toList();
        }
    }
}
