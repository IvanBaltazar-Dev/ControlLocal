package com.kairos.catalogo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>El fixture contra el catalogo vivo.</b>
 *
 * <h2>Para que existe una prueba que casi nunca corre</h2>
 * Porque el riesgo de grabar el catalogo es que <b>envejezca en silencio</b>:
 * BROX anade una clave, cambia una unidad o mueve un atributo de tipo, y las
 * pruebas de KAIROS siguen verdes contra una foto de hace meses. Eso es
 * exactamente lo que pasaba con el catalogo escrito a mano, y cambiar un doble
 * inventado por una foto vieja no lo arreglaria: solo lo haria mas creible.
 *
 * <p>Asi que la foto tiene un vencimiento comprobable. Cuando hay un Core al que
 * preguntar —{@code BROX_URL} y {@code BROX_TOKEN} en el entorno— esta prueba
 * pide los siete tipos y compara. Si algo difiere, <b>dice la clave</b>: no
 * «los catalogos no coinciden», sino {@code delTipo[14].unidad: grabado "m²",
 * vivo "m2"}.
 *
 * <h2>Por que va desactivada por defecto</h2>
 * Porque las pruebas de KAIROS corren sin BROX levantado. Una prueba que
 * necesita red no puede ser la que decide si el build pasa; lo que decide es la
 * bateria contra el fixture. Esta es el <b>gate de vencimiento</b> del fixture y
 * se ejecuta cuando hay Core:
 *
 * <pre>
 *   BROX_URL=http://localhost:8090/controllocal/Api BROX_TOKEN=&lt;jwt&gt; mvn -o test
 * </pre>
 *
 * <h2>Donde NO se puede correr, y como se comprueba entonces</h2>
 * <b>Dentro del harness de agentes no corre</b>: sus JVM no levantan la conexion
 * de loopback y las siete peticiones mueren con {@code Unable to establish
 * loopback connection} aunque {@code curl} alcance el mismo Core sin problema.
 * No es un fallo de esta prueba ni del Core, y por eso se dice aqui en vez de
 * dejar que el siguiente lo descubra depurando.
 *
 * <p>Ahi la comparacion se hace igual, por fuera: se piden los siete tipos con
 * {@code curl} y se comparan contra los ficheros grabados. Medido asi el
 * <b>2026-09-05</b>, los siete coinciden.
 */
@EnabledIfEnvironmentVariable(named = "BROX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BROX_TOKEN", matches = ".+")
class CatalogoVivoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static List<String> tipos() {
        return CatalogoReal.TIPOS;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tipos")
    @DisplayName("el catalogo grabado dice lo mismo que el vivo, clave por clave")
    void elFixtureNoHaEnvejecido(String tipo) {
        JsonNode grabado = CatalogoReal.arbolDe(tipo);
        JsonNode vivo = pedir(tipo);

        Optional<String> diferencia = primeraDiferencia("", grabado, vivo);
        assertTrue(diferencia.isEmpty(),
                () -> "El catalogo grabado de " + tipo + " ya no dice lo que dice BROX: "
                        + diferencia.orElse("") + ". Se vuelve a grabar en "
                        + CatalogoReal.ruta(tipo) + ", no se edita a mano.");
        assertEquals(tipo, vivo.path("tipoPropiedad").asText(),
                "el Core devuelve el tipo canonico aunque se pida por su letra");
    }

    private static JsonNode pedir(String tipo) {
        String base = System.getenv("BROX_URL").replaceAll("/+$", "");
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create(base + "/captura/definicion?tipoPropiedad=" + tipo))
                .header("Authorization", "Bearer " + System.getenv("BROX_TOKEN"))
                .header("X-Canal", "API")
                .header("X-Agente", "KAIROS-TEST")
                .header("X-Conversacion", "gate-catalogo")
                .header("X-Turno", tipo)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try (HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build()) {
            HttpResponse<String> respuesta =
                    cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, respuesta.statusCode(),
                    "BROX no respondio la definicion de " + tipo + ": " + respuesta.body());
            return MAPPER.readTree(respuesta.body());
        } catch (InterruptedException interrumpida) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpida al pedir el catalogo de " + tipo,
                    interrumpida);
        } catch (Exception fallo) {
            throw new IllegalStateException(
                    "No se pudo pedir el catalogo vivo de " + tipo + " a BROX_URL: "
                            + fallo.getMessage(), fallo);
        }
    }

    /**
     * La <b>primera</b> diferencia, con su ruta.
     *
     * <p>Se devuelve una y no todas a proposito: una lista de cuarenta
     * diferencias no se lee, y la primera casi siempre explica el resto —un
     * atributo insertado desplaza todos los que le siguen—.
     */
    static Optional<String> primeraDiferencia(String ruta, JsonNode grabado, JsonNode vivo) {
        if (grabado.isObject() && vivo.isObject()) {
            List<String> claves = new ArrayList<>();
            grabado.fieldNames().forEachRemaining(claves::add);
            vivo.fieldNames().forEachRemaining(clave -> {
                if (!claves.contains(clave)) {
                    claves.add(clave);
                }
            });
            for (String clave : claves) {
                Optional<String> diferencia = primeraDiferencia(ruta + "." + clave,
                        grabado.path(clave), vivo.path(clave));
                if (diferencia.isPresent()) {
                    return diferencia;
                }
            }
            return Optional.empty();
        }
        if (grabado.isArray() && vivo.isArray()) {
            if (grabado.size() != vivo.size()) {
                return Optional.of(ruta + ": grabado tiene " + grabado.size() + " elementos y vivo "
                        + vivo.size() + " (" + soloEn(grabado, vivo) + ")");
            }
            for (int i = 0; i < grabado.size(); i++) {
                Optional<String> diferencia = primeraDiferencia(ruta + "[" + i + "]",
                        grabado.get(i), vivo.get(i));
                if (diferencia.isPresent()) {
                    return diferencia;
                }
            }
            return Optional.empty();
        }
        if (!grabado.equals(vivo)) {
            return Optional.of(ruta + ": grabado " + grabado + ", vivo " + vivo);
        }
        return Optional.empty();
    }

    /** Que claves estan en uno y no en el otro. Es lo que se quiere leer primero. */
    private static String soloEn(JsonNode grabado, JsonNode vivo) {
        List<String> grabadas = claves(grabado);
        List<String> vivas = claves(vivo);
        List<String> nuevas = new ArrayList<>(vivas);
        nuevas.removeAll(grabadas);
        List<String> idas = new ArrayList<>(grabadas);
        idas.removeAll(vivas);
        return "nuevas en vivo: " + nuevas + " · ya no estan: " + idas;
    }

    private static List<String> claves(JsonNode lista) {
        List<String> claves = new ArrayList<>();
        for (Iterator<JsonNode> it = lista.elements(); it.hasNext(); ) {
            JsonNode elemento = it.next();
            claves.add(elemento.path("clave").asText(elemento.toString()));
        }
        return claves;
    }
}
