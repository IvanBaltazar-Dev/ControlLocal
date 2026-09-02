package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.AlertaService.FichaAlerta;
import com.controllocal.service.Pagina;
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
import static org.junit.jupiter.api.Assertions.fail;

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
 *
 * <h2>El escenario es COMPARTIDO, y eso obliga a dos reglas</h2>
 *
 * <p>La base de pruebas no se recrea entre corridas, así que la segunda corrida
 * es la normal: cada prueba empieza sobre lo que dejó la anterior. Medido el
 * 2026-09-02 sobre {@code controllocal_repositorios}: el agente de la
 * prospección sujeto acumulaba <b>517 alertas activas</b>, 476 de ellas
 * {@code CAPTACION_CREADA} que emiten otras suites al registrar captaciones, y
 * quedaba <b>un</b> aviso {@code SIN_RESPUESTA} activo de la prospección sujeto
 * heredado de la corrida anterior de esta misma clase.
 *
 * <ol>
 *   <li><b>La lectura de la campana se pagina hasta agotar el total.</b> El
 *       camino real ordena por fecha de generación descendente, así que un
 *       aviso emitido ayer sale de la primera página en cuanto hay cien más
 *       nuevos. Preguntando solo por {@code listar(1, 100, …)}, el paso 1 de
 *       {@link #campanaYColaConvergenEnElMismoHecho()} cayó el 2026-09-02
 *       —«una prospeccion vencida tiene que avisar en la campana ==&gt; expected
 *       true but was false»— y pasó al reintentar, sin ningún cambio de
 *       producto entre medias. El {@code assertFalse} del paso 3 tenía el
 *       defecto simétrico y más silencioso: se cumplía aunque el aviso siguiera
 *       activo, con solo estar por debajo de la posición 100.</li>
 *   <li><b>Ninguna de las dos pruebas sobrevive a su corrida.</b> Cada una
 *       fotografía al entrar los avisos de recontacto activos del tenant y
 *       retira en {@code finally} los que no estaban en esa foto. Lo que el
 *       barrido <b>cierra</b> no se reabre: cerrar es la conducta que se mide,
 *       no montaje que devolver.</li>
 * </ol>
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConvergenciaCampanaColaIntegrationTest {

    /** El maximo que acepta el service: pedir mas lo capa en 100 igualmente. */
    private static final int TAMANO_PAGINA = 100;

    /** Tope del recorrido de la campana. 50 x 100 = 5.000 avisos activos. */
    private static final int TOPE_PAGINAS = 50;

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
        List<Long> avisosDeEntrada = avisosDeRecontactoActivos(actor.idOrganizacion());
        try {
            // 0) PRECONDICION, y no es teorica: el 2026-09-02 la prospeccion
            //    sujeto (PRO-0002, recontacto 2026-07-01) entraba con UN aviso
            //    activo heredado. No es un aviso equivocado —esa prospeccion
            //    lleva meses vencida, y tenerlo activo es la conducta correcta—
            //    pero mientras exista, `existeActivaDe` impide emitir y el paso
            //    1 mediria un aviso ajeno en vez de la emision. El ciclo se
            //    empieza desde cero, igual que el sistema lo empezo el dia que
            //    esa prospeccion vencio por primera vez.
            descartarAvisos(avisosActivosDe(sujeto.id()));
            assertEquals(0, avisosActivos(sujeto.id()),
                    "el ciclo se mide desde cero: aqui no puede quedar ningun aviso previo");

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
            restaurar(sujeto, original, avisosDeEntrada);
        }
    }

    /**
     * <p>Esta prueba <b>no monta nada</b>: mide el tenant tal como está. Lo que
     * sí hace es disparar el barrido, y el barrido escribe —por eso se retira
     * lo que haya creado. Sin esa retirada dejaba vivo el aviso de la
     * prospección sujeto y era ella quien hacía fallar a la otra prueba en la
     * corrida siguiente (el aviso ya existía, no se emitía otro, y el viejo
     * quedaba fuera de la primera página de la campana).
     *
     * <p>No se inventa un aviso previo para tener algo que medir: si el tenant
     * no tiene ninguno, «ninguno huérfano» se cumple sobre cero y es la
     * respuesta correcta. La prueba afirma una propiedad del conjunto, y un
     * conjunto vacío la cumple sin mentir.
     */
    @Test
    @DisplayName("ningun aviso activo de recontacto sobrevive a su motivo")
    void laCampanaNoGuardaAvisosQueYaNoAplican() {
        Actor actor = elegirProspeccion().actor();
        List<Long> avisosDeEntrada = avisosDeRecontactoActivos(actor.idOrganizacion());
        try {
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
        } finally {
            descartarAvisos(creadosDesde(actor.idOrganizacion(), avisosDeEntrada));
        }
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

    /**
     * Devuelve la fila como estaba y <b>retira lo que la prueba abrió</b>: los
     * avisos de recontacto activos que no estaban en la foto de entrada.
     *
     * <p>Antes esto descartaba «todos los activos de la prospección», que es
     * otra cosa: cerraba también lo heredado —y se quedaba corto con lo que el
     * barrido, que recorre el tenant entero, pudiera haber emitido para otra
     * prospección—. El 2026-09-02 solo PRO-0002 era recontactable en el tenant,
     * así que hoy es un aviso; la retirada no depende de que siga siendo así.
     */
    private void restaurar(Prospeccion sujeto, LocalDate original, List<Long> avisosDeEntrada) {
        jdbc.update("update prospeccion set fecha_recontacto = ? where id_prospeccion = ?",
                original, sujeto.id());
        descartarAvisos(creadosDesde(sujeto.actor().idOrganizacion(), avisosDeEntrada));
    }

    /** Los avisos de recontacto ACTIVOS del tenant, ahora mismo. */
    private List<Long> avisosDeRecontactoActivos(long idOrganizacion) {
        return jdbc.queryForList("""
                select id_alerta from alerta
                 where organizacion_id = ?
                   and entidad_tipo = 'PROSPECCION'
                   and tipo = 'SIN_RESPUESTA'
                   and estado = 'A'
                 order by id_alerta
                """, Long.class, idOrganizacion);
    }

    /** Los de UNA prospección, para la precondición del ciclo. */
    private List<Long> avisosActivosDe(long idProspeccion) {
        return jdbc.queryForList("""
                select id_alerta from alerta
                 where entidad_tipo = 'PROSPECCION'
                   and entidad_id = ?
                   and tipo = 'SIN_RESPUESTA'
                   and estado = 'A'
                 order by id_alerta
                """, Long.class, idProspeccion);
    }

    /** Lo que hay activo ahora y no estaba al entrar: eso lo creó la prueba. */
    private List<Long> creadosDesde(long idOrganizacion, List<Long> avisosDeEntrada) {
        return avisosDeRecontactoActivos(idOrganizacion).stream()
                .filter(id -> !avisosDeEntrada.contains(id))
                .toList();
    }

    /**
     * Los cierra como los cierra el sistema —DESCARTADA y con su fecha, que es
     * lo que exige {@code ck_alerta_resolucion}—, no los borra: la tabla es
     * append-only para la auditoría y una prueba no puede ser la excepción.
     */
    private void descartarAvisos(List<Long> ids) {
        for (Long id : ids) {
            jdbc.update("""
                    update alerta set estado = 'D', fecha_resolucion = now()
                     where id_alerta = ? and estado = 'A'
                    """, id);
        }
    }

    /**
     * Se pregunta por <b>el camino de lectura real</b> de la campana, no por la
     * tabla: lo que se blinda es lo que el usuario ve.
     *
     * <p><b>Y se recorren las páginas hasta agotar el total</b>, porque cien ya
     * no caben. El camino real ordena por fecha de generación descendente, y el
     * agente de la prospección sujeto llevaba 517 avisos activos el 2026-09-02
     * —476 {@code CAPTACION_CREADA} de otras suites—, así que un aviso emitido
     * en una corrida anterior queda fuera de la primera página. Mirando solo la
     * primera, esto respondía «no está» sobre un aviso que sí estaba: falso
     * negativo en el paso 1 y, peor, {@code assertFalse} del paso 3 cumpliéndose
     * por posición en vez de por estado. Paginar quita la dependencia de cuántos
     * avisos ajenos acumule el agente, que es una cantidad que nadie controla.
     */
    private boolean enLaCampana(Actor actor, long idProspeccion) {
        long vistos = 0;
        for (int pagina = 1; pagina <= TOPE_PAGINAS; pagina++) {
            Pagina<FichaAlerta> lote = alertas.listar(pagina, TAMANO_PAGINA, actor);
            for (FichaAlerta ficha : lote.items()) {
                if (esDeRecontacto(ficha) && ficha.entidadId() != null
                        && ficha.entidadId() == idProspeccion) {
                    return true;
                }
            }
            vistos += lote.items().size();
            if (lote.items().isEmpty() || vistos >= lote.total()) {
                return false;
            }
        }
        // Tope de seguridad: un bucle de lectura no puede quedarse dando vueltas
        // en silencio. Si se llega aqui la prueba FALLA y dice por que, en vez
        // de contestar "no esta" sin haber terminado de mirar.
        return fail("la campana del agente pasa de " + (TOPE_PAGINAS * TAMANO_PAGINA)
                + " avisos activos: el recorrido se corto antes de agotarla, asi que "
                + "esta respuesta no significa nada");
    }

    private boolean esDeRecontacto(FichaAlerta ficha) {
        return "SIN_RESPUESTA".equals(ficha.tipo())
                && "PROSPECCION".equals(ficha.entidadTipo());
    }

    /**
     * La cola <b>no</b> tiene el equivalente de la primera página, y se ha
     * comprobado antes de darlo por bueno: {@code bandejaDe} devuelve TODAS las
     * tareas abiertas del agente —{@code TareaRepository.porAgente} no recibe
     * {@code Pageable} y {@code PoliticaDeDespacho.despachar} ordena y devuelve
     * la lista entera—. El recorte a cinco asuntos vive en la pantalla, después
     * de esta frontera.
     */
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
