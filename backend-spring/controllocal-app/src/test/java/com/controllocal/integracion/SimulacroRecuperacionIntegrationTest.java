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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recuperación de emergencia con dos custodios de prueba y cuenta propia actual; sin credenciales de Persona. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "controllocal.recuperacion.habilitada=true")
class SimulacroRecuperacionIntegrationTest {

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
    @Autowired org.springframework.transaction.PlatformTransactionManager gestorTx;
    private long cuenta;

    /** Id del tenant aislado, resuelto en cada prueba. */
    private long organizacion;

    /** La persona sobre la que obra la concesion: el administrador sin factor. */
    private long objetivo;

    /** Empresa y cuenta nuevas por caso, gobierno declarado sin factor y sin datos ajenos. */
    @BeforeEach
    void prepararTenantSinGobierno() {
        new org.springframework.transaction.support.TransactionTemplate(gestorTx).executeWithoutResult(tx->{
            String sufijo=java.util.UUID.randomUUID().toString();
            organizacion=jdbc.queryForObject("insert into organizacion(codigo,nombre,estado,modalidad,mfa_gobierno_exigido) values(?,'Simulacro privado','A','EMPRESARIAL',false) returning id_organizacion",Long.class,"SIM-REC-"+sufijo.substring(0,20));
            objetivo=jdbc.queryForObject("insert into persona(organizacion_id,tipo_persona,tipo_documento,numero_documento,nombres_o_razon_social,estado) values(?,'N','D','90000001','Administrador del simulacro','A') returning id_persona",Long.class,organizacion);
            var hoy=java.time.LocalDate.now(java.time.ZoneId.of("America/Lima"));
            jdbc.update("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,'BROKER',?),(?,?,'ADMIN',?)",organizacion,objetivo,hoy,organizacion,objetivo,hoy);
            // Identidad ficticia confirmada y no autenticable: el simulacro no prueba login.
            cuenta=jdbc.queryForObject("insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) values(?,?,clock_timestamp(),'sin-inicio-de-sesion') returning id_cuenta_acceso",Long.class,"simulacro-"+sufijo,"simulacro-"+sufijo+"@example.invalid");
            jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,'BROKER',true,'A')",organizacion,cuenta,objetivo);
        });
        assertEquals(0L,contar("select count(*) from factor_autenticacion where id_cuenta_acceso="+cuenta+" and estado='A'"),"El simulacro requiere ausencia de segundo factor operativo.");
    }
    @Test
    void elSimulacroCompleto() {
        long personasAntes = contar("SELECT count(*) FROM persona");
        long cuentasAntes = contar("SELECT count(*) FROM cuenta_acceso");
        long rolesAntes = contar("SELECT count(*) FROM persona_rol");
        String hashAntes = jdbc.queryForObject(
                "SELECT c.contrasena_hash FROM cuenta_acceso c "
                        + " JOIN usuario_organizacion u ON u.id_usuario=c.id_cuenta_acceso "
                        + " WHERE u.id_persona = ?", String.class, objetivo);

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
        assertEquals(cuentasAntes, contar("SELECT count(*) FROM cuenta_acceso"),
                "la concesion no crea cuentas");
        assertEquals(rolesAntes, contar("SELECT count(*) FROM persona_rol"),
                "la concesion no deja roles nuevos");
        assertEquals(hashAntes, jdbc.queryForObject(
                        "SELECT c.contrasena_hash FROM cuenta_acceso c "
                                + " JOIN usuario_organizacion u ON u.id_usuario=c.id_cuenta_acceso "
                                + " WHERE u.id_persona = ?", String.class, objetivo),
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
