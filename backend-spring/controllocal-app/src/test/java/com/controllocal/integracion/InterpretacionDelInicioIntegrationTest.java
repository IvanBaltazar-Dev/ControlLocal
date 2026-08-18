package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.service.Actor;
import com.controllocal.service.TareaService;
import com.controllocal.service.TareaService.FichaTarea;
import com.controllocal.service.soporte.EstadoDelHecho;
import com.controllocal.service.soporte.InterpretacionDelAsunto;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Hecho;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Renglon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>La capa de interpretación, contra datos reales</b> (D-E2-1 §10, E2.4).
 *
 * <p>Los tests unitarios blindan las reglas de redacción sobre frases de
 * laboratorio. Esto comprueba lo otro: que <b>lo que de verdad sale de la base</b>
 * las cumple. Una regla que solo se verifica sobre un fixture escrito a mano no
 * ha visto nunca un expediente real.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InterpretacionDelInicioIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        propiedades.add("spring.datasource.username", () -> System.getenv().getOrDefault(
                "TEST_DB_USER", "controllocal"));
        propiedades.add("spring.datasource.password", () -> System.getenv().getOrDefault(
                "TEST_DB_PASSWORD", "controllocal"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired TareaService tareas;

    /**
     * Las palabras que D-E2-1 §10.3.2 rechaza.
     *
     * <p>Todo contraste sale de la base de la organización — el rango real de la
     * zona, tu media de propuestas por visita, el plazo de recontacto de tu casa.
     * <b>Ninguna estadística del sector.</b> Es lo que hace que el dato pese: es
     * comprobable.
     */
    private static final List<String> PROHIBIDAS =
            List.of("sector", "mercado nacional", "industria", "benchmark", "promedio del mercado");

    // ==================================================================

    @Test
    @DisplayName("cada asunto llega interpretado: como esta, expediente y lectura")
    void cadaAsuntoLlegaInterpretado() {
        List<FichaTarea> bandeja = tareas.bandejaDe(actorAgente());
        assertFalse(bandeja.isEmpty(), "el escenario exige una bandeja con asuntos");

        for (FichaTarea asunto : bandeja) {
            assertNotNull(asunto.interpretacion(),
                    "un asunto sin interpretar obliga a la pantalla a deducirla: " + asunto.tipo());
            assertNotNull(asunto.interpretacion().comoEsta());
            assertFalse(asunto.interpretacion().comoEsta().hechos().isEmpty(),
                    "«como esta» sin un solo hecho no dice como esta: " + asunto.tipo());
            assertTrue(asunto.interpretacion().comoEsta().hechos().size()
                            <= InterpretacionDelAsunto.MAXIMO_HECHOS,
                    "tres vinetas, sin parrafos");
        }
    }

    @Test
    @DisplayName("un hecho resuelto sale verde aunque el asunto este en rojo")
    void elEstadoEsDelHechoYNoDelAsunto() {
        List<FichaTarea> bandeja = tareas.bandejaDe(actorAgente());

        // El asunto que espera al broker es de prioridad ALTA y aun asi su primer
        // hecho es un HECHO: la parte del agente esta cumplida. Si el estado se
        // dedujera del tono, saldria en rojo (D-E2-1 seccion 10.1).
        FichaTarea esperandoAOtro = bandeja.stream()
                .filter(t -> !t.dependeDeMi())
                .findFirst()
                .orElse(null);
        if (esperandoAOtro == null) {
            return; // sin ese escenario no hay nada que comprobar
        }
        List<Hecho> hechos = esperandoAOtro.interpretacion().comoEsta().hechos();
        assertTrue(hechos.get(0).estado() == EstadoDelHecho.HECHO,
                "lo que ya esta va primero, y va en verde: " + hechos);
    }

    @Test
    @DisplayName("el orden de los hechos es narrativo: lo que esta, lo que falta, lo que frena")
    void elOrdenEsNarrativo() {
        for (FichaTarea asunto : tareas.bandejaDe(actorAgente())) {
            List<Hecho> hechos = asunto.interpretacion().comoEsta().hechos();
            int posicionDelFreno = -1;
            int posicionDeLoQueFalta = -1;
            for (int i = 0; i < hechos.size(); i++) {
                if (hechos.get(i).estado() == EstadoDelHecho.FRENO) posicionDelFreno = i;
                if (hechos.get(i).estado() == EstadoDelHecho.FALTA) posicionDeLoQueFalta = i;
            }
            if (posicionDelFreno >= 0 && posicionDeLoQueFalta >= 0) {
                assertTrue(posicionDeLoQueFalta < posicionDelFreno,
                        "la consecuencia va DESPUES de su causa; al reves se lee como una "
                                + "alarma con contexto detras, y nadie lee el contexto: " + hechos);
            }
        }
    }

    @Test
    @DisplayName("ningun codigo tecnico llega al texto visible")
    void ningunCodigoTecnicoEnLoVisible() {
        List<String> colados = new java.util.ArrayList<>();
        for (FichaTarea asunto : tareas.bandejaDe(actorAgente())) {
            var interpretacion = asunto.interpretacion();
            for (Hecho hecho : interpretacion.comoEsta().hechos()) {
                if (InterpretacionDelAsunto.llevaCodigoTecnico(hecho.texto())) {
                    colados.add("hecho: " + hecho.texto());
                }
            }
            for (Renglon renglon : interpretacion.expediente()) {
                if (InterpretacionDelAsunto.llevaCodigoTecnico(renglon.valor())) {
                    colados.add("expediente/" + renglon.rotulo() + ": " + renglon.valor());
                }
            }
            if (InterpretacionDelAsunto.llevaCodigoTecnico(interpretacion.lectura())) {
                colados.add("lectura: " + interpretacion.lectura());
            }
        }
        if (!colados.isEmpty()) {
            fail("""
                    Hay codigos tecnicos en el texto que se lee:

                    %s

                    Quien opera identifica la operacion por la DIRECCION y la PERSONA, no
                    por un consecutivo, y el codigo ocupa el sitio de algo que si se usa.
                    Siguen vivos donde hacen falta -- busqueda, soporte, la ficha real --
                    pero no en el Inicio (D-E2-1 seccion 10.3.3).
                    """.formatted(String.join("\n", colados)));
        }
    }

    @Test
    @DisplayName("la lectura sintetiza y no recita ninguno de los cuatro renglones")
    void laLecturaNoRecita() {
        for (FichaTarea asunto : tareas.bandejaDe(actorAgente())) {
            var interpretacion = asunto.interpretacion();
            if (interpretacion.lectura() == null) {
                continue; // sin nada que concluir, no se rellena
            }
            assertFalse(
                    InterpretacionDelAsunto.recita(interpretacion.lectura(),
                            interpretacion.expediente()),
                    "«" + interpretacion.lectura() + "» repite un renglon: si lo recita no "
                            + "aporta nada, el usuario lo tiene dos centimetros mas abajo");
        }
    }

    @Test
    @DisplayName("ninguna comparacion invoca al sector: todo sale de la casa")
    void ningunaEstadisticaDelSector() {
        List<String> colados = new java.util.ArrayList<>();
        for (FichaTarea asunto : tareas.bandejaDe(actorAgente())) {
            var interpretacion = asunto.interpretacion();
            List<String> textos = new java.util.ArrayList<>();
            interpretacion.comoEsta().hechos().forEach(h -> textos.add(h.texto()));
            interpretacion.expediente().forEach(r -> textos.add(r.valor()));
            textos.add(interpretacion.lectura());

            for (String texto : textos) {
                if (texto == null) continue;
                String enMinusculas = texto.toLowerCase(Locale.ROOT);
                PROHIBIDAS.stream().filter(enMinusculas::contains)
                        .forEach(palabra -> colados.add("\"" + palabra + "\" en: " + texto));
            }
        }
        assertTrue(colados.isEmpty(),
                "Todo contraste sale de la base de la organizacion, nunca del sector: es lo que "
                        + "lo hace comprobable y lo que hace que el dato pese. " + colados);
    }

    @Test
    @DisplayName("el expediente son cuatro renglones, o ninguno; nunca cuatro guiones")
    void elExpedienteEsCuatroONinguno() {
        for (FichaTarea asunto : tareas.bandejaDe(actorAgente())) {
            List<Renglon> expediente = asunto.interpretacion().expediente();
            assertTrue(expediente.isEmpty()
                            || expediente.size() == InterpretacionDelAsunto.RENGLONES_DEL_EXPEDIENTE,
                    "un expediente en blanco dice «no hay historial»; cuatro guiones dicen «lo hay "
                            + "y no lo cargue»: " + asunto.tipo() + " -> " + expediente.size());
            for (Renglon renglon : expediente) {
                assertNotNull(renglon.valor());
                assertFalse(renglon.valor().isBlank(),
                        "un renglon vacio es peor que no estar: " + renglon.rotulo());
            }
        }
    }

    // ------------------------------------------------------------------

    private Actor actorAgente() {
        Map<String, Object> fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 limit 1
                """).stream().findFirst().orElseThrow();
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.AGENTE);
    }
}
