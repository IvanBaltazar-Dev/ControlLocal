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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
 *   <li>que el reparto cuadre con la base histórica que lo autorizó;</li>
 *   <li>identidad de contexto C5 o contrato histórico y sus unicidades.</li>
 * </ul>
 *
 * <p>La segunda mitad no mira el DDL sino los DATOS: comprueba sobre las filas
 * reales y sus fuentes exactas. C5 no reinterpreta estados históricos ni hace
 * desaparecer cobros porque una rectificación reduzca la cuantía vigente.
 * La caché de la raíz no sustituye la evaluación y el movimiento acreditados.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InvariantesComisionIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
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
                select l.id_comision_liquidacion id, l.id_contexto_economico, l.estado, l.monto_bruto,
                       coalesce(l.parte_agente, 0) parte_agente,
                       coalesce(mov.cobrado, 0) cobrado,
                       coalesce(mov.pagado_agente, 0) pagado_agente
                  from comision_liquidacion l
                  left join mov on mov.id_comision_liquidacion = l.id_comision_liquidacion)
            """;

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;

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
        assertEquals(1,jdbc.queryForObject("""
                select count(*) from pg_trigger where tgname='base_reparto_comision_c5'
                  and tgrelid='comision_liquidacion'::regclass and tgenabled='O'
                """,Integer.class),"C5 requiere la guarda del reparto contra su evaluación histórica");
    }

    @Test
    @DisplayName("contexto C5 y contrato histórico conservan identidad, tenant y unicidad")
    void unaLiquidacionPorContextoOContratoHistorico() {
        assertEquals("YES", jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                 where table_name = 'comision_liquidacion'
                   and column_name = 'id_contrato_alquiler'
                """, String.class), "una venta no exige un ContratoAlquiler ficticio");
        assertTrue(definicion("uq_comision_contrato").startsWith("UNIQUE"),
                "un contrato podria acumular dos liquidaciones");
        // El tenant viaja en la FK compuesta: una liquidacion no puede colgar
        // del contrato de otra organizacion.
        assertTrue(definicion("fk_comision_contrato_org").contains("organizacion_id"),
                "la liquidacion dejo de estar atada al tenant de su contrato");
        assertTrue(definicion("uq_c5_comision_contexto").contains("organizacion_id, id_contexto_economico"));
        assertTrue(definicion("ck_c5_comision_fuente").contains("id_contexto_economico IS NOT NULL"));
    }

    @Test
    @DisplayName("retirar el alquiler obligatorio no permite una comisión sin origen")
    void unaRaizSinOrigenNoPuedeExistir() {
        var tx=new TransactionTemplate(transacciones);
        tx.executeWithoutResult(estado -> {
            try {
                assertThrows(DataIntegrityViolationException.class,() -> jdbc.update("""
                        insert into comision_liquidacion(organizacion_id)
                        select min(id_organizacion) from organizacion
                        """),"sin contexto C5 ni contrato histórico no hay comisión identificada");
            } finally { estado.setRollbackOnly(); }
        });
    }

    // ------------------------------------------------------------------
    // Lo que dicen los datos reales
    // ------------------------------------------------------------------

    @Test
    @DisplayName("una rectificación conserva cobros sustentados en su evaluación original")
    void cadaCobroConservaSuBaseExactaSinCompararloConElCalculoPosterior() {
        assertEquals(List.of(),jdbc.queryForList("""
                select m.id_comision_movimiento from comision_movimiento m
                join comision_liquidacion l using(organizacion_id,id_comision_liquidacion)
                left join comision_evaluacion e on e.organizacion_id=m.organizacion_id
                  and e.id_comision_liquidacion=m.id_comision_liquidacion
                  and e.id_comision_evaluacion=m.id_comision_evaluacion
                where l.id_contexto_economico is not null and m.tipo='C' and (
                  e.id_comision_evaluacion is null
                  or e.resultado::jsonb#>>'{calculo,cuantia,importe}' is null
                  or e.resultado::jsonb#>>'{calculo,cuantia,moneda}' is distinct from m.moneda
                  or (select coalesce(sum(case when anterior.tipo='C' then anterior.monto
                                      when anterior.tipo='R' then -anterior.monto else 0 end),0)
                      from comision_movimiento anterior
                      where anterior.organizacion_id=m.organizacion_id
                        and anterior.id_comision_liquidacion=m.id_comision_liquidacion
                        and anterior.moneda=m.moneda
                        and anterior.id_comision_movimiento<=m.id_comision_movimiento)
                     > (e.resultado::jsonb#>>'{calculo,cuantia,importe}')::numeric)
                order by m.id_comision_movimiento
                """,Long.class));
        // El contrato histórico mantiene su regla anterior; no se migra a
        // una evaluación C5 inventada ni se mezclan monedas en esta prueba.
        assertEquals(List.of(), incumplen(
                "id_contexto_economico is null and (cobrado < 0 or cobrado > monto_bruto)",
                "liquidaciones históricas fuera del rango acreditado"));
        assertEquals(0L,jdbc.queryForObject("""
                select count(*) from (
                  select organizacion_id,id_comision_liquidacion,moneda from comision_movimiento
                  group by organizacion_id,id_comision_liquidacion,moneda
                  having sum(case when tipo='C' then monto when tipo='R' then -monto else 0 end)<0
                ) negativos
                """,Long.class),"no se compensan fondos negativos entre comisiones o monedas");
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
                "id_contexto_economico is null and ((estado = 'P' and cobrado <> 0)"
                        + " or (estado = 'R' and (cobrado <= 0 or cobrado >= monto_bruto))"
                        + " or (estado = 'C' and cobrado <> monto_bruto))",
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
                 where l.id_contexto_economico is null and c.estado_contrato = 'A' and l.estado <> 'A'
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
