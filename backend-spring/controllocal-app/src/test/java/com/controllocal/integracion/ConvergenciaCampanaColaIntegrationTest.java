package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.AlertaService.FichaAlerta;
import com.controllocal.service.TareaService;
import com.controllocal.service.TareaService.FichaTarea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>La campana y la cola no pueden contradecirse</b> sobre el mismo hecho.
 *
 * <h2>El defecto que esto cierra</h2>
 *
 * <p>El recontacto vencido tiene dos productores: el barrido de la campana
 * ({@code AlertaServiceImpl.sincronizarRecontacto}) y el cuarto disparador de la
 * bandeja. Los dos leen el <b>mismo</b> plazo de {@code PoliticaComercial}, pero
 * solo uno reconciliaba: la tarea se auto-completa al leer la bandeja y el aviso
 * se quedaba activo para siempre. Resultado en pantalla el 2026-08-20: la
 * campana enseñaba PRO-0003, PRO-0005, PRO-0011 y PRO-0017 mientras la cola iba
 * por PRO-0002 y cuatro visitas. Dos representaciones activas del mismo hecho,
 * diciendo cosas distintas.
 *
 * <h2>Por qué en integración y no solo en unitarias</h2>
 *
 * <p>Las unitarias de {@code AlertaServiceImplTest} blindan las cuatro
 * transiciones con mocks. Lo que no pueden ver es lo único que importaba aquí:
 * que <b>las dos consultas reales, contra las mismas filas, converjan</b>. El
 * defecto no estaba en ninguna de las dos ramas — estaba entre ellas.
 *
 * <h2>Sobre la fila que se toca</h2>
 *
 * <p>Se mueve la fecha de recontacto de UNA prospección y se devuelve a su valor
 * al terminar, gane o pierda la prueba. Es la forma de recorrer el ciclo entero
 * —vence, se contacta, vuelve a vencer— sin depender de que el seed traiga por
 * casualidad las tres situaciones.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConvergenciaCampanaColaIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AlertaService alertas;
    @Autowired TareaService tareas;

    @Test
    @DisplayName("el ciclo entero: vence, se contacta, vuelve a vencer")
    void campanaYColaConvergenEnElMismoHecho() {
        Prospeccion sujeto = elegirProspeccion();
        LocalDate original = sujeto.fechaRecontacto();
        Actor actor = sujeto.actor();
        try {
            // 1) VENCIDA: tiene que estar en los dos sitios.
            moverRecontacto(sujeto.id(), LocalDate.now().minusDays(30));
            alertas.sincronizarRecontacto(actor);

            assertTrue(enLaCampana(actor, sujeto.id()),
                    "una prospeccion vencida tiene que avisar en la campana");
            assertTrue(enLaCola(actor, sujeto.id()),
                    "y estar en la cola: son el mismo hecho");
            long primerAviso = idDelAviso(sujeto.id());

            // 2) BARRIDOS REPETIDOS: abrir la campana tres veces no duplica.
            alertas.sincronizarRecontacto(actor);
            alertas.sincronizarRecontacto(actor);
            assertEquals(1, avisosActivos(sujeto.id()),
                    "cada lectura sincroniza; ninguna puede dejar un aviso de mas");

            // 3) SE REGISTRA CONTACTO: la fecha de recontacto se va al futuro, y
            //    el hecho deja de existir. Tiene que desaparecer de LOS DOS.
            moverRecontacto(sujeto.id(), LocalDate.now().plusDays(7));
            alertas.sincronizarRecontacto(actor);

            assertEquals("D", estadoDelAviso(primerAviso),
                    "se cierra como DESCARTADA: lo cerro el sistema, no una persona");
            assertEquals(0, avisosActivos(sujeto.id()),
                    "y no queda ninguno activo para esa prospeccion");
            assertFalse(enLaCola(actor, sujeto.id()),
                    "la tarea se auto-completa al reconciliar");
            assertFalse(enLaCampana(actor, sujeto.id()),
                    "y el aviso ya no puede seguir activo diciendo que si");

            // 4) VUELVE A VENCER: ciclo nuevo, aviso nuevo. El viejo no se
            //    reabre — perderia que hubo un contacto en medio.
            moverRecontacto(sujeto.id(), LocalDate.now().minusDays(30));
            alertas.sincronizarRecontacto(actor);

            assertTrue(enLaCampana(actor, sujeto.id()));
            assertTrue(enLaCola(actor, sujeto.id()));
            assertEquals(1, avisosActivos(sujeto.id()));
            assertNotEquals(primerAviso, idDelAviso(sujeto.id()),
                    "un vencimiento nuevo es un hecho nuevo, con su propio aviso");
            assertEquals("D", estadoDelAviso(primerAviso), "el del ciclo anterior sigue cerrado");
        } finally {
            restaurar(sujeto.id(), original);
        }
    }

    @Test
    @DisplayName("ningun aviso activo de recontacto sobrevive a su motivo")
    void laCampanaNoGuardaAvisosQueYaNoAplican() {
        Actor actor = elegirProspeccion().actor();
        alertas.sincronizarRecontacto(actor);

        // Todo aviso SIN_RESPUESTA activo del tenant tiene que tener detras una
        // prospeccion que de verdad siga vencida. Es la comprobacion que la
        // pantalla no puede hacer y que no debe hacer: el backend entrega el
        // hecho ya reconciliado.
        List<Map<String, Object>> huerfanos = jdbc.queryForList("""
                select a.id_alerta, a.entidad_id
                  from alerta a
                 where a.organizacion_id = ?
                   and a.estado = 'A'
                   and a.tipo = 'SIN_RESPUESTA'
                   and a.entidad_tipo = 'PROSPECCION'
                   and not exists (
                       select 1 from prospeccion p
                        where p.id_prospeccion = a.entidad_id
                          and p.organizacion_id = a.organizacion_id
                          and p.fecha_recontacto is not null
                          and p.fecha_recontacto <= ?
                          and p.estado not in ('T', 'D'))
                """, actor.idOrganizacion(), LocalDate.now().minusDays(7));

        assertTrue(huerfanos.isEmpty(),
                "avisos activos sin motivo vivo: " + huerfanos);
    }

    // ------------------------------------------------------------------

    private record Prospeccion(long id, LocalDate fechaRecontacto, Actor actor) {
    }

    /**
     * Una prospección con agente, y el actor que la ve. Se elige la más antigua
     * para que el escenario sea estable entre ejecuciones.
     */
    private Prospeccion elegirProspeccion() {
        Map<String, Object> fila = jdbc.queryForList("""
                select p.id_prospeccion, p.fecha_recontacto, p.organizacion_id,
                       r.id_persona, r.id_persona_rol
                  from prospeccion p
                  join detalle_agente a on a.id_persona_rol = p.id_rol_agente
                  join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 where p.estado not in ('T', 'D')
                 order by p.id_prospeccion
                 limit 1
                """).stream().findFirst().orElseThrow(
                () -> new IllegalStateException("el escenario exige al menos una prospeccion viva"));

        java.sql.Date fecha = (java.sql.Date) fila.get("fecha_recontacto");
        return new Prospeccion(
                ((Number) fila.get("id_prospeccion")).longValue(),
                fecha == null ? null : fecha.toLocalDate(),
                new Actor(((Number) fila.get("organizacion_id")).longValue(),
                        ((Number) fila.get("id_persona")).longValue(),
                        ((Number) fila.get("id_persona_rol")).longValue(), Actor.AGENTE));
    }

    private void moverRecontacto(long idProspeccion, LocalDate cuando) {
        jdbc.update("update prospeccion set fecha_recontacto = ? where id_prospeccion = ?",
                cuando, idProspeccion);
    }

    /** Devuelve la fila como estaba, y limpia lo que la prueba haya abierto. */
    private void restaurar(long idProspeccion, LocalDate original) {
        jdbc.update("update prospeccion set fecha_recontacto = ? where id_prospeccion = ?",
                original, idProspeccion);
        jdbc.update("""
                update alerta set estado = 'D', fecha_resolucion = now()
                 where entidad_tipo = 'PROSPECCION' and entidad_id = ?
                   and tipo = 'SIN_RESPUESTA' and estado = 'A'
                """, idProspeccion);
    }

    /**
     * Se pregunta por <b>el camino de lectura real</b> de la campana, no por la
     * tabla: lo que se blinda es lo que el usuario ve. Cien caben de sobra en el
     * escenario, y si algun dia no cupieran la prueba fallaria en vez de pasar
     * por descuido.
     */
    private boolean enLaCampana(Actor actor, long idProspeccion) {
        return alertas.listar(1, 100, actor).items().stream()
                .anyMatch(f -> esDeRecontacto(f) && f.entidadId() != null
                        && f.entidadId() == idProspeccion);
    }

    private boolean esDeRecontacto(FichaAlerta ficha) {
        return "SIN_RESPUESTA".equals(ficha.tipo())
                && "PROSPECCION".equals(ficha.entidadTipo());
    }

    private boolean enLaCola(Actor actor, long idProspeccion) {
        List<FichaTarea> bandeja = tareas.bandejaDe(actor);
        return bandeja.stream().anyMatch(t -> "PROSPECCION".equals(t.entidadTipo())
                && t.entidadId() != null && t.entidadId() == idProspeccion);
    }

    private int avisosActivos(long idProspeccion) {
        Integer n = jdbc.queryForObject("""
                select count(*) from alerta
                 where entidad_tipo = 'PROSPECCION' and entidad_id = ?
                   and tipo = 'SIN_RESPUESTA' and estado = 'A'
                """, Integer.class, idProspeccion);
        return n == null ? 0 : n;
    }

    private long idDelAviso(long idProspeccion) {
        Long id = jdbc.queryForObject("""
                select id_alerta from alerta
                 where entidad_tipo = 'PROSPECCION' and entidad_id = ?
                   and tipo = 'SIN_RESPUESTA' and estado = 'A'
                 order by id_alerta desc limit 1
                """, Long.class, idProspeccion);
        return id == null ? -1 : id;
    }

    private String estadoDelAviso(long idAlerta) {
        return jdbc.queryForObject("select estado from alerta where id_alerta = ?",
                String.class, idAlerta);
    }
}
