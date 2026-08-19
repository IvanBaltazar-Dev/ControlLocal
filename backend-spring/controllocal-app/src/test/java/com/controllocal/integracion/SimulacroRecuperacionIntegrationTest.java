package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.seguridad.ConcesionRecuperacion;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
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
 * <p>Lo que se comprueba es lo que el §18.16 del diseno pide del simulacro: que
 * una sola aprobacion no autoriza nada, que las tres acciones valen una vez
 * cada una, que la concesion <b>se cierra sola</b> al volver el gobierno, y que
 * <b>no creo cuentas, no fijo contrasenas y no dejo roles nuevos</b>.
 *
 * <h2>Por que el simulacro tiene su PROPIO tenant</h2>
 * Una recuperacion de emergencia solo tiene sentido en una organizacion que se
 * quedo <b>sin administrador operativo</b>: si el tenant puede gobernarse, la
 * concesion se cierra sola y rechaza la accion — que es justo lo que
 * {@code aplicar} comprueba antes de obrar.
 *
 * <p>Apuntando a la organizacion 1, el resultado dependia del ESTADO de la base
 * de desarrollo compartida: ahi hay un administrador con su segundo factor
 * activo, asi que el simulacro moria con "La organizacion ya tiene un
 * administrador operativo". No era un fallo de codigo: era el gate de cierre
 * contando como error una precondicion que la prueba nunca fijo.
 *
 * <p>Por eso {@link #prepararTenantSinGobierno()} construye un tenant propio con
 * un TENANT_ADMIN <b>sin factor MFA</b>: membresia activa — que es lo que el
 * trigger de V44 exige mientras {@code mfa_gobierno_exigido} sea falso — pero
 * no operativo, que es la definicion misma de la emergencia. La prueba deja de
 * leer el estado de nadie y pasa a construir el suyo.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "controllocal.recuperacion.habilitada=true")
class SimulacroRecuperacionIntegrationTest {

    /** El tenant del simulacro. Se reconoce por su codigo, nunca por su id. */
    private static final String CODIGO_TENANT = "SIMULACRO-RECUPERACION";

    private static final String ID_A = "custodio-e2e-a";
    private static final String ID_B = "custodio-e2e-b";
    private static final String OPERADOR = "operador-e2e";

    /** 128 bits, como exige el procedimiento. Distintos en cada corrida. */
    private static final String SECRETO_A = secretoNuevo();
    private static final String SECRETO_B = secretoNuevo();

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
        propiedades.add("controllocal.recuperacion.custodio-a.id", () -> ID_A);
        propiedades.add("controllocal.recuperacion.custodio-a.hash",
                () -> PasswordHasher.hash(SECRETO_A.toCharArray()));
        propiedades.add("controllocal.recuperacion.custodio-b.id", () -> ID_B);
        propiedades.add("controllocal.recuperacion.custodio-b.hash",
                () -> PasswordHasher.hash(SECRETO_B.toCharArray()));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired RecuperacionEmergenciaService recuperacion;

    /** Id del tenant aislado, resuelto en cada prueba. */
    private long organizacion;

    /** La persona sobre la que obra la concesion: el administrador sin factor. */
    private long objetivo;

    /**
     * <b>El simulacro construye su precondicion en vez de encontrarla.</b>
     *
     * <p>Crea — o reutiliza — un tenant con un TENANT_ADMIN activo y <b>sin
     * segundo factor</b>. Con {@code mfa_gobierno_exigido} en falso, el trigger
     * de V44 se conforma con que exista la membresia, asi que el tenant es
     * legal; y como {@code contarAdministradoresOperativos} exige un factor en
     * estado {@code A}, {@code hayAlgunoOperativo} devuelve falso durante toda
     * la corrida. Esa es la emergencia que el mecanismo viene a resolver.
     *
     * <p>Es idempotente a proposito: la segunda corrida contra la misma base
     * encuentra el tenant hecho y solo retira las concesiones de la anterior.
     * <b>Se limpia ANTES, no despues</b> — si una corrida murio a mitad, que es
     * cuando esto duele, su limpieza final nunca llego a ejecutarse.
     */
    @BeforeEach
    void prepararTenantSinGobierno() {
        organizacion = idDelTenant();
        objetivo = idDelAdministradorSinFactor();

        // Solo lo de ESTE tenant: el simulacro ya no puede estropear el estado
        // de otra organizacion, ni depender de el.
        jdbc.update("delete from accion_recuperacion where organizacion_id = ?", organizacion);
        jdbc.update("delete from aprobacion_recuperacion where organizacion_id = ?", organizacion);
        jdbc.update("delete from concesion_recuperacion where organizacion_id = ?", organizacion);

        assertEquals(0L, contar("""
                        select count(*)
                          from usuario_organizacion uo
                          join credencial_usuario cu on cu.id_persona_rol = uo.id_usuario
                          join factor_autenticacion fa on fa.id_credencial = cu.id_persona_rol
                         where uo.organizacion_id = %d and uo.estado = 'A'
                           and uo.rol = 'TENANT_ADMIN' and fa.estado = 'A'
                        """.formatted(organizacion)),
                "el simulacro exige un tenant sin administrador operativo: es la emergencia misma");
    }

    private long idDelTenant() {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "select id_organizacion from organizacion where codigo = ?", CODIGO_TENANT);
        if (!filas.isEmpty()) {
            return ((Number) filas.get(0).get("id_organizacion")).longValue();
        }
        return jdbc.queryForObject("""
                insert into organizacion (codigo, nombre, estado, mfa_gobierno_exigido)
                values (?, 'Tenant del simulacro de recuperacion', 'A', false)
                returning id_organizacion
                """, Long.class, CODIGO_TENANT);
    }

    /**
     * Persona + rol interno + credencial + membresia TENANT_ADMIN, y ningun
     * {@code factor_autenticacion}. Las tres acciones de la concesion necesitan
     * exactamente esto: {@code REACTIVAR_CUENTA} y {@code REPONER_MEMBRESIA}
     * fallan si no hay credencial o si la membresia no esta activa.
     */
    private long idDelAdministradorSinFactor() {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                select uo.id_persona
                  from usuario_organizacion uo
                 where uo.organizacion_id = ? and uo.estado = 'A' and uo.rol = 'TENANT_ADMIN'
                 limit 1""", organizacion);
        if (!filas.isEmpty()) {
            return ((Number) filas.get(0).get("id_persona")).longValue();
        }

        Long idPersona = jdbc.queryForObject("""
                insert into persona (organizacion_id, tipo_persona, tipo_documento, numero_documento,
                                     nombres_o_razon_social, correo, estado)
                values (?, 'N', 'D', '90000001', 'Administrador del simulacro',
                        'simulacro@controllocal.test', 'A')
                returning id_persona
                """, Long.class, organizacion);

        Long idRol = jdbc.queryForObject("""
                insert into persona_rol (organizacion_id, id_persona, tipo_rol, vigencia_desde)
                values (?, ?, 'USUARIO_INTERNO', current_date)
                returning id_persona_rol
                """, Long.class, organizacion, idPersona);

        // El hash no se usa: nadie inicia sesion con esta cuenta. La columna es
        // NOT NULL, y una cadena que no es un hash valido no autentica a nadie.
        jdbc.update("""
                insert into credencial_usuario (id_persona_rol, tipo_rol, nombre_usuario, contrasena_hash,
                                                estado_administrativo, organizacion_id,
                                                debe_cambiar_contrasena, debe_enrolar_mfa)
                values (?, 'USUARIO_INTERNO', 'simulacro-recuperacion', 'sin-inicio-de-sesion',
                        'A', ?, false, false)
                """, idRol, organizacion);

        jdbc.update("""
                insert into usuario_organizacion (organizacion_id, id_usuario, rol, nombre_visible,
                                                  estado, id_persona)
                values (?, ?, 'TENANT_ADMIN', 'Administrador del simulacro', 'A', ?)
                """, organizacion, idRol, idPersona);

        return idPersona;
    }

    @Test
    void elSimulacroCompleto() {
        long personasAntes = contar("SELECT count(*) FROM persona");
        long credencialesAntes = contar("SELECT count(*) FROM credencial_usuario");
        long rolesAntes = contar("SELECT count(*) FROM persona_rol");
        String hashAntes = jdbc.queryForObject(
                "SELECT contrasena_hash FROM credencial_usuario cu "
                        + " JOIN persona_rol r ON r.id_persona_rol = cu.id_persona_rol "
                        + " WHERE r.id_persona = ?", String.class, objetivo);

        // --- emision: PENDIENTE, y no autoriza nada -----------------------
        long id = recuperacion.emitir(new RecuperacionEmergenciaService.Emision(
                organizacion, objetivo, OPERADOR, "simulacro: sin administrador operativo"));
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
                 WHERE organizacion_id = %d AND estado IN ('P', 'V')
                """.formatted(organizacion)));

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
        // "Quien ejecuta no custodia" (D-S0-52). La guarda da el mensaje; el
        // CHECK de la tabla lo garantiza aunque alguien la esquive.
        assertThrows(ReglaNegocioException.class,
                () -> recuperacion.emitir(new RecuperacionEmergenciaService.Emision(
                        organizacion, objetivo, ID_A, "intento invalido")));
    }

    @Test
    void laBaseRechazaLasTresIdentidadesIguales() {
        // El CHECK, no la aplicacion: es lo que sigue protegiendo si alguien
        // escribe por SQL.
        assertThrows(Exception.class, () -> jdbc.update("""
                INSERT INTO concesion_recuperacion
                    (organizacion_id, id_persona_objetivo, operador, custodio_a, custodio_b,
                     hash_secreto, motivo)
                VALUES (?, ?, 'mismo', 'mismo', 'otro', 'x', 'saltandose la guarda')
                """, organizacion, objetivo));
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
