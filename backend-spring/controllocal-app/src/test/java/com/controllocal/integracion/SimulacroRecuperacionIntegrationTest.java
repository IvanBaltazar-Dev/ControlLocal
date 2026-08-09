package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.seguridad.ConcesionRecuperacion;
import com.controllocal.service.RecuperacionEmergenciaService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Simulacro de recuperacion de emergencia</b> (V38) con identidades de
 * prueba.
 *
 * <h2>Los secretos se generan aqui y mueren aqui</h2>
 * Nada versionado (D-S0-53). Se generan al arrancar el contexto, se inyectan
 * como configuracion de la corrida y desaparecen con ella. <b>Un fixture no se
 * convierte en custodio por participar en una prueba</b>: la designacion real
 * es requisito de <i>activacion</i>, no de implementacion, y esta clase existe
 * para demostrar exactamente eso.
 *
 * <p>Lo que se comprueba es lo que el §18.16 del diseño pide del simulacro: que
 * una sola aprobacion no autoriza nada, que las tres acciones valen una vez
 * cada una, que la concesion <b>se cierra sola</b> al volver el gobierno, y que
 * <b>no creo cuentas, no fijo contrasenas y no dejo roles nuevos</b>.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "controllocal.recuperacion.habilitada=true")
class SimulacroRecuperacionIntegrationTest {

    private static final long ORGANIZACION = 1L;

    private static final String ID_A = "custodio-e2e-a";
    private static final String ID_B = "custodio-e2e-b";
    private static final String OPERADOR = "operador-e2e";

    /** 128 bits, como exige el procedimiento. Distintos en cada corrida. */
    private static final String SECRETO_A = secretoNuevo();
    private static final String SECRETO_B = secretoNuevo();

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        propiedades.add("spring.datasource.username", () -> System.getenv().getOrDefault(
                "TEST_DB_USER", "controllocal"));
        propiedades.add("spring.datasource.password", () -> System.getenv().getOrDefault(
                "TEST_DB_PASSWORD", "controllocal"));
        propiedades.add("controllocal.recuperacion.custodio-a.id", () -> ID_A);
        propiedades.add("controllocal.recuperacion.custodio-a.hash",
                () -> PasswordHasher.hash(SECRETO_A.toCharArray()));
        propiedades.add("controllocal.recuperacion.custodio-b.id", () -> ID_B);
        propiedades.add("controllocal.recuperacion.custodio-b.hash",
                () -> PasswordHasher.hash(SECRETO_B.toCharArray()));
    }

    @Autowired JdbcTemplate jdbc;

    /**
     * <b>El simulacro tiene que poder repetirse contra la misma base.</b>
     *
     * <p>Sin esto dejaba su concesion en la tabla y la SEGUNDA corrida moria en
     * la emision con "Ya hay una concesion en curso": el indice parcial
     * {@code uq_concesion_viva_por_organizacion} solo admite una viva por
     * tenant, que es justo lo que este test viene a proteger. El reactor pasaba
     * una vez y fallaba a la siguiente, y eso no sirve como gate de cierre
     * (punto 6): el verde tiene que ser reproducible, no depender de si alguien
     * corrio antes.
     *
     * <p>Se limpia ANTES, no despues: si una corrida anterior murio a mitad
     * -que es exactamente cuando esto duele- el {@code @AfterEach} de aquella
     * no llego a ejecutarse.
     */
    @BeforeEach
    void retirarRastroDeCorridasAnteriores() {
        jdbc.update("delete from accion_recuperacion");
        jdbc.update("delete from aprobacion_recuperacion");
        jdbc.update("delete from concesion_recuperacion");
    }
    @Autowired RecuperacionEmergenciaService recuperacion;

    @Test
    void elSimulacroCompleto() {
        long objetivo = unAdministrador();
        long personasAntes = contar("SELECT count(*) FROM persona");
        long credencialesAntes = contar("SELECT count(*) FROM credencial_usuario");
        long rolesAntes = contar("SELECT count(*) FROM persona_rol");
        String hashAntes = jdbc.queryForObject(
                "SELECT contrasena_hash FROM credencial_usuario cu "
                        + " JOIN persona_rol r ON r.id_persona_rol = cu.id_persona_rol "
                        + " WHERE r.id_persona = ?", String.class, objetivo);

        // --- emision: PENDIENTE, y no autoriza nada -----------------------
        long id = recuperacion.emitir(new RecuperacionEmergenciaService.Emision(
                ORGANIZACION, objetivo, OPERADOR, "simulacro: sin administrador operativo"));
        assertEquals(ConcesionRecuperacion.PENDIENTE, recuperacion.consultar(id).estado());

        // --- una sola aprobacion NO habilita nada -------------------------
        assertTrue(recuperacion.aprobar(id, ID_A, SECRETO_A.toCharArray()).isEmpty(),
                "con una aprobacion la concesion sigue PENDIENTE");
        assertEquals(ConcesionRecuperacion.PENDIENTE, recuperacion.consultar(id).estado());

        // --- el mismo custodio no cubre las dos partes --------------------
        assertThrows(ReglaNegocioException.class,
                () -> recuperacion.aprobar(id, ID_A, SECRETO_A.toCharArray()),
                "el UNIQUE por (concesion, custodio) es lo que impide una sola mano");

        // --- un secreto equivocado no aprueba -----------------------------
        assertThrows(ReglaNegocioException.class,
                () -> recuperacion.aprobar(id, ID_B, "no-es-el-secreto".toCharArray()));

        // --- la segunda aprobacion activa y entrega el secreto UNA vez ----
        Optional<String> secreto = recuperacion.aprobar(id, ID_B, SECRETO_B.toCharArray());
        assertTrue(secreto.isPresent(), "la segunda aprobacion entrega el secreto");
        assertEquals(ConcesionRecuperacion.VIGENTE, recuperacion.consultar(id).estado());

        // --- las acciones: una vez cada una -------------------------------
        var primera = recuperacion.aplicar(secreto.get(), "REVOCAR_MFA");
        assertEquals("REVOCAR_MFA", primera.tipo());
        assertThrows(ReglaNegocioException.class,
                () -> recuperacion.aplicar(secreto.get(), "REVOCAR_MFA"),
                "sin el UNIQUE por tipo, max_acciones=3 dejaria repetir la misma");

        // --- AGOTADA tiene productor desde 7.3.3 --------------------------
        // `A` estaba en el vocabulario de V38 y nadie la escribia: al gastar la
        // ultima accion la concesion seguia VIGENTE aunque ya no admitiera
        // ninguna mas. Aqui se consumen las tres y se comprueba que el estado
        // acaba diciendo la verdad.
        recuperacion.aplicar(secreto.get(), "REPONER_MEMBRESIA");
        var ultima = recuperacion.aplicar(secreto.get(), "REACTIVAR_CUENTA");
        assertEquals(0, ultima.accionesRestantes(), "las tres acciones quedaron consumidas");
        assertEquals(ConcesionRecuperacion.AGOTADA, recuperacion.consultar(id).estado(),
                "sin capacidad, la concesion tiene que dejar de figurar VIGENTE");
        // Y deja de ser "viva": uq_concesion_viva_por_organizacion solo cuenta
        // P y V, asi que una agotada no bloquea la emision de otra.
        assertEquals(0L, contar("""
                SELECT count(*) FROM concesion_recuperacion
                 WHERE organizacion_id = 1 AND estado IN ('P', 'V')
                """));

        // --- lo que NO cambio ---------------------------------------------
        assertEquals(personasAntes, contar("SELECT count(*) FROM persona"),
                "la concesion no crea personas");
        assertEquals(credencialesAntes, contar("SELECT count(*) FROM credencial_usuario"),
                "la concesion no crea cuentas");
        assertEquals(rolesAntes, contar("SELECT count(*) FROM persona_rol"),
                "la concesion no deja roles nuevos");
        assertEquals(hashAntes, jdbc.queryForObject(
                        "SELECT contrasena_hash FROM credencial_usuario cu "
                                + " JOIN persona_rol r ON r.id_persona_rol = cu.id_persona_rol "
                                + " WHERE r.id_persona = ?", String.class, objetivo),
                "la concesion NUNCA fija la contrasena de nadie");

        // --- ninguna huella de secretos en la auditoria -------------------
        long sucios = contar("""
                SELECT count(*) FROM evento_seguridad
                 WHERE tipo LIKE 'RECUPERACION_EMERGENCIA%'
                   AND coalesce(detalle_json, '') || coalesce(motivo, '')
                       ~* '(secreto|hash|pbkdf2|contrasena)'""");
        assertEquals(0, sucios, "ni el secreto de la concesion ni los de custodio salen al registro");
    }

    @Test
    void unaAccionDesconocidaNoExiste() {
        assertThrows(ReglaNegocioException.class,
                () -> recuperacion.aplicar("da-igual", "LEER_CLIENTES"),
                "solo hay tres acciones, y ninguna lee datos del negocio");
    }

    @Test
    void elOperadorNoPuedeSerCustodio() {
        // «Quien ejecuta no custodia» (D-S0-52). La guarda da el mensaje; el
        // CHECK de la tabla lo garantiza aunque alguien la esquive.
        assertThrows(ReglaNegocioException.class,
                () -> recuperacion.emitir(new RecuperacionEmergenciaService.Emision(
                        ORGANIZACION, unAdministrador(), ID_A, "intento invalido")));
    }

    @Test
    void laBaseRechazaLasTresIdentidadesIguales() {
        // El CHECK, no la aplicacion: es lo que sigue protegiendo si alguien
        // escribe por SQL.
        assertThrows(Exception.class, () -> jdbc.update("""
                INSERT INTO concesion_recuperacion
                    (organizacion_id, id_persona_objetivo, operador, custodio_a, custodio_b,
                     hash_secreto, motivo)
                VALUES (1, ?, 'mismo', 'mismo', 'otro', 'x', 'saltandose la guarda')
                """, unAdministrador()));
    }

    private long unAdministrador() {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT r.id_persona
                  FROM usuario_organizacion uo
                  JOIN persona_rol r ON r.id_persona_rol = uo.id_usuario
                 WHERE uo.organizacion_id = ? AND uo.estado = 'A' AND uo.rol = 'TENANT_ADMIN'
                 LIMIT 1""", ORGANIZACION);
        assertFalse(filas.isEmpty(), "el seed debe tener un TENANT_ADMIN");
        return ((Number) filas.get(0).get("id_persona")).longValue();
    }

    private long contar(String sql) {
        Long valor = jdbc.queryForObject(sql, Long.class);
        assertNotNull(valor);
        return valor;
    }

    private static String secretoNuevo() {
        byte[] material = new byte[16];
        new SecureRandom().nextBytes(material);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(material);
    }
}
