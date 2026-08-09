package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Los invariantes economicos que NO puede sostener el service.</b>
 *
 * <p>{@code CicloComisionTest} blinda las reglas en memoria; esto blinda las
 * que solo PostgreSQL puede garantizar frente a cualquier escritura —incluida
 * una migracion futura o un {@code UPDATE} manual—:
 *
 * <ul>
 *   <li>el vocabulario de {@code estado} y de {@code tipo} de movimiento;</li>
 *   <li>que un movimiento nunca sea de importe cero o negativo;</li>
 *   <li>que el reparto cuadre con la bruta ({@code parte_agente +
 *       parte_empresa = monto_bruto});</li>
 *   <li>que no exista liquidacion sin contrato ni contrato con dos.</li>
 * </ul>
 *
 * <p>La segunda mitad no mira el DDL sino los DATOS: comprueba sobre las filas
 * reales que el estado almacenado sigue cuadrando con el saldo de movimientos.
 * Es la comprobacion que faltaba, porque una letra guardada sin movimientos no
 * demuestra por si sola la situacion economica; corriendo despues de un E2E
 * detecta cualquier deriva entre {@code P/R/C} y lo efectivamente cobrado.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InvariantesComisionIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        propiedades.add("spring.datasource.username", () -> System.getenv().getOrDefault(
                "TEST_DB_USER", "controllocal"));
        propiedades.add("spring.datasource.password", () -> System.getenv().getOrDefault(
                "TEST_DB_PASSWORD", "controllocal"));
    }

    /**
     * Neto cobrado por liquidacion: los cobros suman y las reversiones restan.
     * El signo lo pone el TIPO del movimiento, nunca el importe (que el DDL
     * obliga a ser positivo).
     */
    private static final String SALDOS = """
            with mov as (
                select id_comision_liquidacion,
                       sum(case when tipo = 'C' then monto
                                when tipo = 'R' then -monto else 0 end) cobrado,
                       sum(case when tipo = 'P' then monto else 0 end) pagado_agente
                  from comision_movimiento
                 group by id_comision_liquidacion),
            saldo as (
                select l.id_comision_liquidacion id, l.estado, l.monto_bruto,
                       coalesce(l.parte_agente, 0) parte_agente,
                       coalesce(mov.cobrado, 0) cobrado,
                       coalesce(mov.pagado_agente, 0) pagado_agente
                  from comision_liquidacion l
                  left join mov on mov.id_comision_liquidacion = l.id_comision_liquidacion)
            """;

    @Autowired JdbcTemplate jdbc;

    // ------------------------------------------------------------------
    // Lo que garantiza el esquema
    // ------------------------------------------------------------------

    @Test
    @DisplayName("el DDL vigente conserva los CHECK del modelo economico")
    void losCheckSiguenEnPie() {
        assertTrue(definicion("ck_comision_estado").matches(".*'P'.*'R'.*'C'.*'A'.*"),
                "el vocabulario de estado de la liquidacion cambio: " + definicion("ck_comision_estado"));
        assertTrue(definicion("ck_movimiento_tipo").matches(".*'C'.*'P'.*'A'.*'R'.*"),
                "el vocabulario de tipo de movimiento cambio: " + definicion("ck_movimiento_tipo"));
        // Un movimiento de cero o negativo convertiria el signo en un dato de
        // importe; el signo lo pone el TIPO (R resta), nunca el numero.
        assertTrue(definicion("ck_movimiento_monto").contains("monto > "),
                "un movimiento dejo de exigir importe positivo");
        // El reparto no puede inventar ni perder dinero respecto de la bruta.
        assertTrue(definicion("ck_comision_montos").contains("parte_agente + parte_empresa"),
                "el reparto dejo de cuadrar contra la bruta");
    }

    @Test
    @DisplayName("una liquidacion cuelga de un contrato y solo puede haber una")
    void unaLiquidacionPorContrato() {
        assertEquals("NO", jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                 where table_name = 'comision_liquidacion'
                   and column_name = 'id_contrato_alquiler'
                """, String.class), "una liquidacion sin contrato dejaria de ser imposible");
        assertTrue(definicion("uq_comision_contrato").startsWith("UNIQUE"),
                "un contrato podria acumular dos liquidaciones");
        // El tenant viaja en la FK compuesta: una liquidacion no puede colgar
        // del contrato de otra organizacion.
        assertTrue(definicion("fk_comision_contrato_org").contains("organizacion_id"),
                "la liquidacion dejo de estar atada al tenant de su contrato");
    }

    // ------------------------------------------------------------------
    // Lo que dicen los datos reales
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ninguna liquidacion cobro mas que su bruta, ni menos que cero")
    void elCobradoVivaDentroDeLaBruta() {
        assertEquals(List.of(), incumplen(
                "cobrado < 0 or cobrado > monto_bruto",
                "liquidaciones con el cobrado fuera del rango [0, bruta]"));
    }

    @Test
    @DisplayName("ningun agente cobro mas de la parte que le asignaron")
    void elPagoAlAgenteNoSuperaSuParte() {
        assertEquals(List.of(), incumplen(
                "pagado_agente > parte_agente",
                "liquidaciones con mas pagado al agente que su parte asignada"));
    }

    @Test
    @DisplayName("el estado almacenado cuadra con el saldo de movimientos")
    void elEstadoCuadraConElSaldo() {
        // A queda fuera a proposito: la anulacion es una decision expresa, no
        // una derivada, y conserva como evidencia lo que se hubiera cobrado.
        assertEquals(List.of(), incumplen(
                "(estado = 'P' and cobrado <> 0)"
                        + " or (estado = 'R' and (cobrado <= 0 or cobrado >= monto_bruto))"
                        + " or (estado = 'C' and cobrado <> monto_bruto)",
                "liquidaciones cuyo estado no se deriva de su saldo"));
    }

    @Test
    @DisplayName("ninguna comision viva cuelga de un contrato anulado")
    void sinComisionVivaEnContratoAnulado() {
        List<String> huerfanas = jdbc.queryForList("""
                select 'liquidacion ' || l.id_comision_liquidacion
                     || ' en ' || l.estado || ' de contrato anulado ' || c.id_contrato_alquiler
                  from comision_liquidacion l
                  join contrato_alquiler c on c.id_contrato_alquiler = l.id_contrato_alquiler
                 where c.estado_contrato = 'A' and l.estado <> 'A'
                 order by 1
                """, String.class);
        assertEquals(List.of(), huerfanas,
                "anular el contrato tiene que arrastrar su liquidacion");
    }

    // ------------------------------------------------------------------

    private String definicion(String constraint) {
        List<String> definiciones = jdbc.queryForList(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = ?",
                String.class, constraint);
        assertEquals(1, definiciones.size(), "no existe la restriccion " + constraint);
        return definiciones.getFirst();
    }

    private List<String> incumplen(String condicion, String queSeBusca) {
        return jdbc.queryForList(SALDOS + """
                select queSeBusca || ': liquidacion ' || id || ' estado=' || estado
                     || ' bruta=' || monto_bruto || ' cobrado=' || cobrado
                     || ' parteAgente=' || parte_agente || ' pagadoAgente=' || pagado_agente
                  from saldo where
                """.replace("queSeBusca", "'" + queSeBusca + "'")
                + condicion + " order by id", String.class);
    }
}
