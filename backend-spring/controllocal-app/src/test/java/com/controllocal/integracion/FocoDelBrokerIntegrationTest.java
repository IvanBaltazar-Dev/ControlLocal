package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.service.Actor;
import com.controllocal.service.FocoDelBrokerService;
import com.controllocal.service.FocoDelBrokerService.AsuntoDelBroker;
import com.controllocal.service.TareaService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.soporte.EstadoDelHecho;
import com.controllocal.service.soporte.InterpretacionDelAsunto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>El broker tiene sus propios asuntos, no la bandeja del agente</b>
 * (D-E2-5, E2.5).
 *
 * <p>Lo que se protege aquí es la frontera, no el contenido:
 *
 * <blockquote>Cada rol ve lo que él tiene que decidir, nunca lo que otro tiene
 * que hacer.</blockquote>
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FocoDelBrokerIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        propiedades.add("spring.datasource.username", () -> System.getenv().getOrDefault(
                "TEST_DB_USER", "controllocal"));
        propiedades.add("spring.datasource.password", () -> System.getenv().getOrDefault(
                "TEST_DB_PASSWORD", "controllocal"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired FocoDelBrokerService focoDelBroker;
    @Autowired TareaService tareas;

    // ==================================================================
    // La frontera
    // ==================================================================

    /**
     * <b>El gate de D-E2-5.</b> `GET /tareas` es del agente y sigue siéndolo: lo
     * que E2.5 añade es una bandeja distinta, no una puerta a la existente.
     */
    @Test
    @DisplayName("la bandeja del agente sigue cerrada al broker")
    void laBandejaDelAgenteSigueCerradaAlBroker() {
        assertThrows(AccesoNoAutorizadoException.class, () -> tareas.bandejaDe(broker()),
                "la bandeja no es un tablero de control: es la lista de cosas que un AGENTE "
                        + "tiene que hacer, y eso no cambia porque el broker tenga la suya");
        assertThrows(AccesoNoAutorizadoException.class, () -> tareas.bandejaDe(admin()));
    }

    @Test
    @DisplayName("y el agente no recibe los asuntos del broker")
    void elAgenteNoRecibeLosAsuntosDelBroker() {
        assertTrue(focoDelBroker.de(agente()).isEmpty(),
                "aprobar una captacion o firmar una evaluacion no es del agente");
    }

    /**
     * <b>El TENANT_ADMIN no tiene asuntos, y es deliberado.</b>
     *
     * <p>Desde D-F4-5 no decide ninguna operación comercial. Darle asuntos que no
     * puede resolver es la definición de un tablero de control.
     */
    @Test
    @DisplayName("el TENANT_ADMIN no tiene asuntos: puede auditar, no decidir")
    void elAdminNoTieneAsuntos() {
        assertTrue(focoDelBroker.de(admin()).isEmpty());
    }

    // ==================================================================
    // GATE · los dos focos no comparten identidad
    // ==================================================================

    /**
     * <b>D-E2-1 §7.1, aprendido a golpes.</b>
     *
     * <p>El mismo encargo puede estar en las dos colas y son <b>dos asuntos
     * distintos</b>: uno dice «recontacta», el otro dice «aprueba». Con un id
     * compartido, la regla del hogar único los trataría como el mismo y el
     * encargo saldría dos veces en el broker — en su cola y como fecha suelta en
     * la agenda.
     */
    @Test
    @DisplayName("el foco del broker y el del agente no comparten ni un id")
    void losDosFocosNoCompartenIds() {
        Set<String> delBroker = focoDelBroker.de(broker()).stream()
                .map(AsuntoDelBroker::id)
                .collect(Collectors.toSet());
        Set<String> delAgente = tareas.bandejaDe(agente()).stream()
                .map(t -> t.entidadTipo() + ":" + t.entidadId())
                .collect(Collectors.toSet());

        Set<String> compartidos = delBroker.stream().filter(delAgente::contains)
                .collect(Collectors.toSet());

        assertTrue(compartidos.isEmpty(),
                "un id compartido hace que el mismo encargo salga dos veces: " + compartidos);
        assertTrue(delBroker.stream().allMatch(id -> id.endsWith(FocoDelBrokerService.SUFIJO_BROKER)),
                "el id lleva el rol que lo mira, y por eso no puede colisionar");
    }

    // ==================================================================
    // El mismo motor
    // ==================================================================

    @Test
    @DisplayName("cada asunto del broker llega interpretado, con la misma capa del agente")
    void cadaAsuntoLlegaInterpretado() {
        List<AsuntoDelBroker> asuntos = focoDelBroker.de(broker());
        if (asuntos.isEmpty()) {
            return; // sin escenario no hay nada que comprobar
        }
        for (AsuntoDelBroker asunto : asuntos) {
            assertNotNull(asunto.interpretacion(), asunto.tipo());
            assertFalse(asunto.interpretacion().comoEsta().hechos().isEmpty(),
                    "«como esta» sin un solo hecho no dice como esta");
            assertTrue(asunto.interpretacion().comoEsta().hechos().size()
                            <= InterpretacionDelAsunto.MAXIMO_HECHOS,
                    "tres vinetas, sin parrafos");
            assertTrue(asunto.interpretacion().comoEsta().hechos().stream()
                            .anyMatch(h -> h.estado() == EstadoDelHecho.FALTA),
                    "un asunto del broker siempre tiene algo que falta: su decision");
            assertNotNull(asunto.destino(), "sin destino no se puede resolver");
        }
    }

    @Test
    @DisplayName("ningun codigo tecnico en el texto que el broker lee")
    void ningunCodigoTecnicoVisible() {
        for (AsuntoDelBroker asunto : focoDelBroker.de(broker())) {
            asunto.interpretacion().comoEsta().hechos().forEach(hecho ->
                    assertFalse(InterpretacionDelAsunto.llevaCodigoTecnico(hecho.texto()),
                            "quien opera identifica la operacion por la direccion y la persona: "
                                    + hecho.texto()));
        }
    }

    @Test
    @DisplayName("dos lecturas seguidas devuelven el mismo orden")
    void elOrdenEsDeterminista() {
        List<String> primera = focoDelBroker.de(broker()).stream().map(AsuntoDelBroker::id).toList();
        List<String> segunda = focoDelBroker.de(broker()).stream().map(AsuntoDelBroker::id).toList();

        assertTrue(primera.equals(segunda),
                "la misma politica de despacho del agente, con su criterio de estabilidad");
    }

    // ------------------------------------------------------------------

    private Actor broker() {
        return actorCon("""
                select r.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_broker b join persona_rol r on r.id_persona_rol = b.id_persona_rol
                 limit 1
                """, Actor.BROKER);
    }

    private Actor agente() {
        return actorCon("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 limit 1
                """, Actor.AGENTE);
    }

    private Actor admin() {
        Actor unBroker = broker();
        return new Actor(unBroker.idOrganizacion(), unBroker.idPersona(),
                unBroker.idRolOperativo(), Actor.TENANT_ADMIN);
    }

    private Actor actorCon(String consulta, String rol) {
        Map<String, Object> fila = jdbc.queryForList(consulta).stream().findFirst().orElseThrow();
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), rol);
    }
}
