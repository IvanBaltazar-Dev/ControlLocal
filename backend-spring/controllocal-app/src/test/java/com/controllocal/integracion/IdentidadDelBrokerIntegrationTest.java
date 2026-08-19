package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>Un broker puede entrar, y su sesión resuelve al rol correcto</b>
 * (cierre de E2.5).
 *
 * <h2>Por qué existe este test</h2>
 * Al comprobar E2.5 a ojo di por hecho que los brokers de {@code detalle_broker}
 * <b>no tenían credenciales</b>, porque `credencial_usuario` no tiene ninguna
 * fila con su {@code id_persona_rol}. Era un error de lectura mío: el seed sigue
 * el modelo Party-Role, y la credencial cuelga del rol {@code USUARIO_INTERNO}
 * de <b>la misma persona</b>, no del rol de negocio.
 *
 * <pre>
 *   persona 2 ──┬── USUARIO_INTERNO #2   ← aquí vive la credencial (rsalas)
 *               └── BROKER          #23  ← aquí vive el alcance (4 agentes)
 * </pre>
 *
 * <p>La prueba visual salió porque el token de `rsalas` resuelve a 23. Pero
 * «salió» no es «está garantizado»: mientras nadie lo afirme, el día que el seed
 * cambie o alguien añada un broker suelto, la pantalla del broker se quedará
 * vacía y parecerá un fallo del foco.
 *
 * <p>Esto lo convierte en una afirmación: <b>todo broker con equipo tiene una
 * identidad con la que entrar, y esa identidad resuelve a SU rol de broker.</b>
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IdentidadDelBrokerIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("todo broker con equipo tiene credencial para entrar")
    void todoBrokerConEquipoPuedeEntrar() {
        List<Map<String, Object>> huerfanos = jdbc.queryForList("""
                select b.id_persona_rol as rol_broker, r.id_persona
                  from detalle_broker b
                  join persona_rol r on r.id_persona_rol = b.id_persona_rol
                 where exists (select 1 from supervision_agente s
                                where s.id_rol_broker = b.id_persona_rol
                                  and s.fecha_fin is null)
                   and not exists (select 1
                                     from persona_rol r2
                                     join credencial_usuario c on c.id_persona_rol = r2.id_persona_rol
                                    where r2.id_persona = r.id_persona
                                      and c.estado_administrativo = 'A')
                """);

        if (!huerfanos.isEmpty()) {
            fail("""
                    Hay brokers con equipo y sin ninguna identidad con la que entrar: %s

                    La credencial cuelga del rol USUARIO_INTERNO de la MISMA persona, no del
                    rol de negocio (modelo Party-Role). Un broker sin ella no puede abrir su
                    Inicio, y su pantalla se vera vacia como si el foco estuviera roto.
                    """.formatted(huerfanos));
        }
    }

    /**
     * <b>Y la identidad resuelve a SU rol de broker, no a otro.</b>
     *
     * <p>Es lo que sostiene el alcance: {@code Alcances.de(actor)} usa
     * {@code idRolOperativo}, y si ese id fuera el del {@code USUARIO_INTERNO} en
     * vez del {@code BROKER}, {@code agentesSupervisados} no encontraría nunca a
     * nadie y el foco saldría vacío para todos.
     */
    @Test
    @DisplayName("cada credencial de broker resuelve a un unico rol BROKER, y es el suyo")
    void laIdentidadResuelveAlRolCorrecto() {
        List<Map<String, Object>> brokers = jdbc.queryForList("""
                select c.nombre_usuario,
                       r.id_persona,
                       (select count(*) from persona_rol rb
                         where rb.id_persona = r.id_persona and rb.tipo_rol = 'BROKER') as roles_broker,
                       (select min(rb.id_persona_rol) from persona_rol rb
                         where rb.id_persona = r.id_persona and rb.tipo_rol = 'BROKER') as rol_broker
                  from credencial_usuario c
                  join persona_rol r on r.id_persona_rol = c.id_persona_rol
                 where exists (select 1 from persona_rol rb
                                join detalle_broker b on b.id_persona_rol = rb.id_persona_rol
                               where rb.id_persona = r.id_persona and rb.tipo_rol = 'BROKER')
                """);

        assertFalse(brokers.isEmpty(), "el escenario exige al menos un broker con credencial");

        for (Map<String, Object> fila : brokers) {
            long rolesBroker = ((Number) fila.get("roles_broker")).longValue();
            assertEquals(1L, rolesBroker,
                    "una persona con dos roles BROKER haria ambiguo el alcance de su sesion: "
                            + fila.get("nombre_usuario"));

            Long rolBroker = ((Number) fila.get("rol_broker")).longValue();
            Long conDetalle = jdbc.queryForObject(
                    "select count(*) from detalle_broker where id_persona_rol = ?",
                    Long.class, rolBroker);
            assertTrue(conDetalle != null && conDetalle == 1,
                    "el rol al que resuelve tiene que ser un broker de verdad: " + rolBroker);
        }
    }

    /**
     * <b>Al menos un broker de desarrollo sirve para mirar la pantalla.</b>
     *
     * <p>Sin esto, comprobar a ojo cualquier superficie del broker depende de que
     * alguien encuentre por casualidad el usuario correcto — que es exactamente
     * lo que pasó al cerrar E2.5.
     */
    @Test
    @DisplayName("existe un broker de desarrollo con equipo, credencial y asuntos que decidir")
    void hayUnBrokerConElQueMirarLaPantalla() {
        Long utiles = jdbc.queryForObject("""
                select count(*)
                  from detalle_broker b
                  join persona_rol r on r.id_persona_rol = b.id_persona_rol
                 where exists (select 1 from supervision_agente s
                                where s.id_rol_broker = b.id_persona_rol and s.fecha_fin is null)
                   and exists (select 1
                                 from persona_rol r2
                                 join credencial_usuario c on c.id_persona_rol = r2.id_persona_rol
                                where r2.id_persona = r.id_persona
                                  and c.estado_administrativo = 'A')
                """, Long.class);

        assertTrue(utiles != null && utiles > 0,
                "hace falta al menos un broker con equipo Y credencial para poder mirar su "
                        + "Inicio; sin el, cualquier comprobacion visual de una pantalla de "
                        + "broker depende de la suerte");
    }
}
