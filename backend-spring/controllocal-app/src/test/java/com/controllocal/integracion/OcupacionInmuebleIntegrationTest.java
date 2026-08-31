package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Un inmueble no puede tener dos contratos vivos a la vez.</b>
 *
 * <p>Es el invariante que hace imposible la "ventana falsa de disponibilidad"
 * de la renovacion: si el contrato anterior y su sucesor pudieran estar los dos
 * en {@code D}/{@code V}, revisar el anterior devolveria al mercado un local
 * que el sucesor sigue ocupando.
 *
 * <p><b>Por que se prueba contra PostgreSQL y no con mocks.</b>
 * {@code uq_contrato_vivo_por_propiedad} es un indice PARCIAL, y PostgreSQL no
 * puede diferir su validacion: se evalua en el momento del {@code INSERT}. Por
 * eso importa el ORDEN dentro de la transaccion de renovacion —el anterior
 * pasa a {@code R} ANTES de que nazca el sucesor— y eso solo se demuestra
 * ejecutandolo de verdad. Un mock diria que si a cualquier orden.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OcupacionInmuebleIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("el indice parcial de ocupacion existe y cubre solo D/V")
    void elIndiceDeOcupacionEstaEnPie() {
        String definicion = jdbc.queryForObject("""
                select indexdef from pg_indexes
                 where schemaname = 'public' and indexname = 'uq_contrato_vivo_por_propiedad'
                """, String.class);

        assertTrue(definicion != null && definicion.startsWith("CREATE UNIQUE INDEX"),
                "la ocupacion tiene que ser UNIQUE: " + definicion);
        assertTrue(definicion.contains("organizacion_id") && definicion.contains("id_propiedad"),
                "la unicidad es por tenant y propiedad: " + definicion);
        // Parcial: un contrato P (borrador) o terminado no ocupa nada, y si el
        // indice los incluyera bloquearia altas perfectamente validas.
        assertTrue(definicion.contains("WHERE") && definicion.contains("'D'")
                        && definicion.contains("'V'"),
                "el indice debe filtrar por estado vivo: " + definicion);
    }

    @Test
    @DisplayName("ninguna propiedad tiene hoy dos contratos vivos")
    void losDatosRespetanLaOcupacion() {
        List<String> duplicadas = jdbc.queryForList("""
                select organizacion_id || ':' || id_propiedad
                  from contrato_alquiler
                 where estado_contrato in ('D', 'V')
                 group by organizacion_id, id_propiedad
                having count(*) > 1
                """, String.class);

        assertEquals(List.of(), duplicadas);
    }

    /**
     * La demostracion directa: se intenta dejar dos contratos vivos sobre la
     * misma propiedad y PostgreSQL lo impide. Se hace con SQL sobre una fila ya
     * existente para no montar toda la cascada comercial; lo que se prueba es
     * el guardian, no el caso de uso.
     */
    @Test
    @DisplayName("PostgreSQL rechaza un segundo contrato vivo sobre la misma propiedad")
    void dosContratosVivosNoCaben() {
        Long id = jdbc.query("""
                select id_contrato_alquiler from contrato_alquiler
                 where estado_contrato in ('D', 'V')
                 order by id_contrato_alquiler limit 1
                """, rs -> rs.next() ? rs.getLong(1) : null);
        if (id == null) {
            // Sin contrato vivo no hay fila que clonar. Antes esto era un
            // `return` a secas: la prueba pasaba en verde SIN comprobar nada, y
            // no de vez en cuando -- medido el 2026-08-31 sobre la instancia
            // dedicada, al terminar una corrida completa habia 0 contratos
            // vivos, asi que esta rama es la que se toma SIEMPRE en el reactor.
            //
            // Montar la cascada comercial entera aqui seria construir el caso de
            // uso para probar el guardian, que es justo lo que esta clase dice
            // que no hace. Lo que si se puede afirmar sin montarla -- y se
            // afirma, en vez de no afirmar nada -- es que el candado sigue
            // puesto: si el indice parcial desapareciera, el invariante no lo
            // estaria sosteniendo nadie y este verde seria una mentira.
            String definicion = jdbc.query("""
                    select indexdef from pg_indexes
                     where schemaname = 'public'
                       and indexname = 'uq_contrato_vivo_por_propiedad'
                    """, rs -> rs.next() ? rs.getString(1) : null);
            assertNotNull(definicion,
                    "no hay contrato vivo que duplicar Y ademas ha desaparecido el indice que "
                            + "impide la duplicacion. Sin ninguna de las dos cosas, esta prueba "
                            + "no sostiene el invariante");
            String normalizada = definicion.toUpperCase().replaceAll("\\s+", " ");
            assertTrue(normalizada.contains("UNIQUE"),
                    "el guardian tiene que seguir siendo UNIQUE: " + definicion);
            // Y PARCIAL, que es la mitad que de verdad decide. Comprobar solo
            // que existe y que dice UNIQUE dejaba pasar una migracion futura que
            // tocara el WHERE: un unico sobre (organizacion, propiedad) SIN
            // predicado prohibiria tambien los contratos ya terminados, y uno
            // con el predicado estrechado dejaria de prohibir los vivos. El
            // invariante no es "hay un unique": es "hay un unique que aplica
            // exactamente a los contratos vivos".
            assertTrue(normalizada.contains("WHERE"),
                    "el guardian tiene que ser PARCIAL, o estaria prohibiendo tambien dos "
                            + "contratos terminados sobre la misma propiedad: " + definicion);
            assertTrue(normalizada.contains("ESTADO_CONTRATO"),
                    "y su predicado tiene que mirar el estado del contrato: " + definicion);
            for (String vivo : List.of("'D'", "'V'")) {
                assertTrue(normalizada.contains(vivo),
                        "el predicado tiene que seguir cubriendo el estado vivo " + vivo
                                + ": si un estado vivo se cae del WHERE, dos contratos de ese "
                                + "estado caben sobre la misma propiedad y nadie lo impide. "
                                + definicion);
            }
            return;
        }
        // Clonar la fila cambiando solo la clave: colisiona en la propiedad.
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into contrato_alquiler (organizacion_id, id_oportunidad, id_solicitud,
                       fecha_cierre, estado_contrato, fecha_inicio_contrato, fecha_fin_contrato,
                       renta_contractual, moneda, id_rol_agente_cierre, id_captacion,
                       id_propiedad, id_rol_cliente, id_contrato_anterior)
                select organizacion_id, id_oportunidad, null,
                       fecha_cierre, estado_contrato, fecha_inicio_contrato, fecha_fin_contrato,
                       renta_contractual, moneda, id_rol_agente_cierre, id_captacion,
                       id_propiedad, id_rol_cliente, id_contrato_alquiler
                  from contrato_alquiler where id_contrato_alquiler = ?
                """, id));
    }

    /**
     * <b>Ninguna funcion PL/pgSQL puede seguir comparando contra la palabra.</b>
     *
     * <p>V40 convirtio tres columnas de estado al codigo unitario y actualizo
     * CHECK, indices parciales y valores por defecto —todo lo que delatan
     * {@code pg_constraint} y {@code pg_indexes}—, pero se dejo el CUERPO de un
     * trigger: {@code exigir_administrador_operativo()} seguia buscando
     * {@code fa.estado = 'ACTIVO'} y, al no encontrarlo nunca, abortaba todo
     * enrolamiento de segundo factor con un 409. El reactor no lo vio porque
     * una funcion PL/pgSQL es texto hasta que se ejecuta: ni el javac ni
     * Hibernate la miran.
     *
     * <p>Esta prueba es la red que faltaba, y sirve para cualquier conversion
     * futura de vocabulario.
     */
    @Test
    @DisplayName("ninguna funcion PL/pgSQL compara estados contra la palabra completa")
    void lasFuncionesNoConservanElVocabularioViejo() {
        List<String> sospechosas = jdbc.queryForList("""
                select p.proname
                  from pg_proc p
                  join pg_namespace n on n.oid = p.pronamespace
                 where n.nspname = 'public'
                   and p.prosrc ~ '(estado|estado_administrativo)\\s*=\\s*''(ACTIVO|PENDIENTE|REVOCADO|VIGENTE|CONSUMIDO|AGOTADO|CERRADA|CADUCADA|AGOTADA)'''
                 order by p.proname
                """, String.class);

        assertEquals(List.of(), sospechosas,
                "estas funciones comparan un estado contra la palabra completa; tras V40 esa "
                        + "condicion no la cumple ninguna fila");
    }

    @Test
    @DisplayName("la tarea de revision sabe que contrato la origino")
    void laRevisionSeAtaASuContrato() {
        // Sin esta columna, resolver la revision al revisar seria buscar
        // "alguna tarea abierta del inmueble" y podria cerrar la de otro
        // contrato del mismo local.
        Integer columna = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema = 'public' and table_name = 'tarea'
                   and column_name = 'id_contrato_origen'
                """, Integer.class);
        assertEquals(1, columna);

        List<String> huerfanas = jdbc.queryForList("""
                select t.id_tarea::text
                  from tarea t
                 where t.tipo = 'REVISION_INMUEBLE'
                   and t.id_contrato_origen is null
                   and t.estado in ('P', 'E')
                """, String.class);
        assertEquals(List.of(), huerfanas,
                "una revision abierta sin contrato origen no se puede resolver sin adivinar");
    }
}
