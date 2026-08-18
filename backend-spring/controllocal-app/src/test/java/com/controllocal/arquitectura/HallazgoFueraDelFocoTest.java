package com.controllocal.arquitectura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>Un hallazgo no vuelve a la bandeja</b> (E2.3).
 *
 * <h2>Qué separa</h2>
 * <pre>
 *   una TAREA     «hay algo que debes resolver»   -> compite por los 5 del foco
 *   un HALLAZGO   «encontré algo que mirar»        -> superficie propia
 * </pre>
 *
 * <p>Una coincidencia de cartera puede ser extraordinariamente valiosa sin ser
 * una obligación. Mientras vivió dentro de la bandeja competía con una solicitud
 * pendiente y un seguimiento vencido — y les ganaba, porque la política de
 * despacho la trata como ocasión, que lo es. El agente abría su Inicio y
 * encontraba veintidós sugerencias por encima de lo que le reclamaba algo.
 *
 * <h2>Por qué el gate mira un IMPORT y no una lista de tipos</h2>
 * Porque la forma de recaer no es escribir el nombre de la tarea: es volver a
 * llamar al matcher desde el motor de la bandeja. <b>Si el servicio de tareas no
 * puede evaluar una coincidencia, no puede convertirla en tarea</b> — la
 * dependencia es la que hace posible el error, y quitarla es lo que lo impide.
 *
 * <p>Es más fuerte que prohibir el literal {@code PROPONER_OPORTUNIDAD}: ese
 * sigue existiendo, y debe, porque hay tareas históricas con ese tipo que V63
 * retiró y su vocabulario tiene que seguir siendo legible.
 */
class HallazgoFueraDelFocoTest {

    private static final String MOTOR_DE_LA_BANDEJA =
            "backend-spring/controllocal-service/src/main/java/com/controllocal/service/impl/"
                    + "TareaServiceImpl.java";

    /** Lo que el motor de la bandeja no puede volver a conocer. */
    private static final List<String> PROHIBIDO = List.of(
            "CoincidenciaCartera",
            "HallazgoService",
            "RequerimientoClienteRepository");

    @Test
    @DisplayName("el motor de la bandeja no conoce el matcher, asi que no puede derivar hallazgos")
    void laBandejaNoPuedeVolverAEvaluarCoincidencias() {
        String motor = leer(rutaDelRepo().resolve(MOTOR_DE_LA_BANDEJA));

        List<String> hallados = PROHIBIDO.stream()
                .filter(nombre -> motor.contains("import ") && importa(motor, nombre))
                .toList();

        if (!hallados.isEmpty()) {
            fail("""
                    El motor de la bandeja volvio a depender del matcher: %s

                    Una coincidencia de cartera NO es una tarea. Si `TareaServiceImpl` puede
                    evaluar coincidencias, tarde o temprano alguien vuelve a meterlas en
                    `foco[]` -- y volveran a ganarle el puesto a lo que de verdad reclama una
                    accion, porque la politica las trata como ocasion y lo son.

                    Los hallazgos se calculan en `HallazgoService`, con la MISMA evidencia de
                    `CoincidenciaCartera`, y viajan en `hallazgos[]`. Si hace falta un
                    descubrimiento nuevo, se anade alli (E2.3).
                    """.formatted(hallados));
        }
    }

    /**
     * <b>Y la bandeja no vuelve a emitir ese tipo de tarea.</b>
     *
     * <p>El import es la puerta; esto es la cerradura. Alguien podria construir
     * la derivada a mano, sin importar el matcher, copiando un puntaje de otro
     * sitio — y eso seria peor todavia, porque seria un segundo matcher.
     */
    @Test
    @DisplayName("ningun disparador de la bandeja emite PROPONER_OPORTUNIDAD")
    void ningunDisparadorEmiteCoincidencias() {
        String motor = leer(rutaDelRepo().resolve(MOTOR_DE_LA_BANDEJA));

        List<String> lineas = motor.lines().toList();
        for (int i = 0; i < lineas.size(); i++) {
            String linea = lineas.get(i);
            if (esComentario(linea)) {
                continue;
            }
            if (linea.contains("Tarea.PROPONER_OPORTUNIDAD")) {
                fail("""
                        %s:%d emite PROPONER_OPORTUNIDAD como tarea.

                        Ese tipo existe solo para que las tareas historicas que V63 retiro
                        sigan siendo legibles. Ninguna nueva debe crearse: una coincidencia
                        de cartera es un hallazgo, no una obligacion (E2.3).
                        """.formatted(MOTOR_DE_LA_BANDEJA, i + 1));
            }
        }
    }

    // ------------------------------------------------------------------

    /** Un import de verdad, no una mencion en un comentario. */
    private static boolean importa(String fuente, String nombre) {
        return fuente.lines()
                .map(String::trim)
                .anyMatch(linea -> linea.startsWith("import ") && linea.contains("." + nombre + ";"));
    }

    private static boolean esComentario(String linea) {
        String limpia = linea.trim();
        return limpia.startsWith("//") || limpia.startsWith("*") || limpia.startsWith("/*");
    }

    private static String leer(Path fichero) {
        try {
            return Files.readString(fichero, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + fichero, e);
        }
    }

    private static Path rutaDelRepo() {
        Path directorio = Path.of("").toAbsolutePath();
        while (directorio != null) {
            if (Files.isRegularFile(directorio.resolve(MOTOR_DE_LA_BANDEJA))) {
                return directorio;
            }
            directorio = directorio.getParent();
        }
        throw new IllegalStateException("No se encontro " + MOTOR_DE_LA_BANDEJA
                + " subiendo desde " + Path.of("").toAbsolutePath());
    }
}
