package com.controllocal.arquitectura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>Una cabecera que el SPA manda y CORS no permite no falla: desaparece la
 * peticion entera.</b>
 *
 * <h2>El fallo que este gate existe para impedir</h2>
 * {@code ConfiguracionSeguridad} declara los encabezados permitidos de forma
 * explicita —nada de {@code "*"}, que seria relajar CORS— y durante meses la
 * lista fue {@code Authorization, Content-Type}. Mientras tanto el SPA mandaba
 * {@code Idempotency-Key} en los comandos de contrato. El navegador respondia
 * al preflight con 200 y despues <b>tumbaba el POST</b> con
 * {@code net::ERR_FAILED}: sin cuerpo, sin codigo de estado y sin nada en el
 * log del servidor, porque la peticion nunca llego.
 *
 * <h2>Por que ninguna prueba lo vio</h2>
 * El spec del SPA comprueba que la cabecera viaja usando
 * {@code HttpTestingController}, que intercepta ANTES del navegador y <b>no
 * cruza CORS</b>. Afirmaba una verdad —la cabecera se pone— sobre un camino que
 * en produccion no existia. Y el backend tampoco podia verlo: en sus pruebas no
 * hay preflight.
 *
 * <p>Es un fallo de FRONTERA, y las fronteras solo se comprueban desde fuera de
 * las dos piezas. De ahi este gate, que lee el SPA de verdad y la configuracion
 * de verdad, igual que {@link MatrizOperacionRolTest} lee el markdown y los
 * controladores.
 */
class CabecerasDelSpaPermitidasTest {

    /**
     * Cabeceras que el SPA pone a mano. {@code Authorization} y
     * {@code Content-Type} las pone Angular y ya estan permitidas; lo que este
     * gate busca son las <b>propias</b>: la de idempotencia y las de
     * procedencia.
     */
    private static final Pattern CABECERA = Pattern.compile(
            "['\"](Idempotency-Key|X-[A-Za-z][A-Za-z0-9-]*)['\"]\\s*:");

    @Test
    @DisplayName("cada cabecera que el SPA manda esta permitida por CORS")
    void ningunaCabeceraDelSpaSeQuedaFueraDeCors() {
        Set<String> mandadas = cabecerasQueMandaElSpa();
        Set<String> permitidas = cabecerasPermitidas();

        Set<String> fuera = new TreeSet<>(mandadas);
        permitidas.forEach(fuera::remove);

        if (!fuera.isEmpty()) {
            fail("""
                    El SPA manda cabeceras que CORS no permite: %s

                    El navegador NO devuelve un error legible: responde al preflight con 200
                    y despues tumba la peticion con net::ERR_FAILED. No hay cuerpo, no hay
                    estado y no hay nada en el log del servidor, porque la peticion no llega.

                    Se arregla anadiendolas a `setAllowedHeaders` en ConfiguracionSeguridad.
                    NO se arregla con "*": la lista es explicita a proposito.

                    Permitidas hoy: %s
                    """.formatted(fuera, permitidas));
        }
    }

    // ------------------------------------------------------------------

    private static Set<String> cabecerasQueMandaElSpa() {
        Set<String> encontradas = new LinkedHashSet<>();
        Path raiz = rutaDelRepo("frontend-angular/src");
        try (Stream<Path> ficheros = Files.walk(raiz)) {
            ficheros.filter(Files::isRegularFile)
                    .filter(fichero -> fichero.getFileName().toString().endsWith(".ts"))
                    // Un spec declara cabeceras para AFIRMAR sobre ellas, no para
                    // mandarlas. Incluirlo haria que este gate exigiera permitir
                    // cabeceras que nadie manda de verdad.
                    .filter(fichero -> !fichero.getFileName().toString().endsWith(".spec.ts"))
                    .forEach(fichero -> {
                        Matcher encontrado = CABECERA.matcher(leer(fichero));
                        while (encontrado.find()) {
                            encontradas.add(encontrado.group(1));
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo recorrer " + raiz, e);
        }
        return encontradas;
    }

    /**
     * Las que declara {@code ConfiguracionSeguridad}. Se leen del fuente y no
     * levantando el contexto de Spring porque el gate tiene que correr en la
     * fase rapida, junto a los demas de arquitectura.
     */
    private static Set<String> cabecerasPermitidas() {
        String fuente = leer(rutaDelRepo(
                "backend-spring/controllocal-web/src/main/java/com/controllocal/web/"
                        + "seguridad/ConfiguracionSeguridad.java"));
        Matcher llamada = Pattern.compile("setAllowedHeaders\\(List\\.of\\(([^)]*)\\)\\)")
                .matcher(fuente);
        if (!llamada.find()) {
            fail("No se encontro `setAllowedHeaders(List.of(...))` en ConfiguracionSeguridad. "
                    + "Si la configuracion de CORS cambio de forma, este gate hay que "
                    + "actualizarlo -- no borrarlo: la frontera sigue existiendo.");
        }
        Set<String> permitidas = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Matcher literal = Pattern.compile("\"([^\"]+)\"").matcher(llamada.group(1));
        while (literal.find()) {
            permitidas.add(literal.group(1));
        }
        return permitidas;
    }

    private static String leer(Path fichero) {
        try {
            return Files.readString(fichero, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + fichero, e);
        }
    }

    /** Misma busqueda de raiz que {@link FronteraDeAutoridadEnElSpaTest}. */
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
