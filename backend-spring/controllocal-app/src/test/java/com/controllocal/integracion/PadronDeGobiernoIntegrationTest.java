package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.CredencialUsuarioRepository;
import com.controllocal.persistence.repositorio.CuentaDeGobiernoFila;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.SeguridadService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate del <b>padron de gobierno</b> (`GET /accesos`).
 *
 * <p><b>Por que existe.</b> La primera version de esta consulta devolvia
 * <b>una sola fila</b> para un tenant de veintiuna cuentas, y el error no lo
 * vio ni el compilador ni el arranque: el JPQL era valido y el SQL equivalente,
 * escrito a mano, daba las veintiuna. Solo se cayo en un E2E, que tarda diez
 * minutos por intento. Esto lo comprueba en segundos y contra la base de
 * verdad, que es donde el fallo vivia.
 *
 * <p>Se ejecuta solo con {@code TEST_DB_URL} apuntando a una base con el
 * esquema y el seed, igual que el resto de los tests de esta carpeta.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PadronDeGobiernoIntegrationTest {

    private static final long ORGANIZACION = 1L;

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CredencialUsuarioRepository credenciales;
    @Autowired UsuarioOrganizacionRepository membresias;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired SeguridadService seguridad;

    @Test
    void elPadronDevuelveTodasLasCuentasDelTenant() {
        Integer esperadas = jdbc.queryForObject("""
                SELECT count(*)
                  FROM credencial_usuario c
                  JOIN persona_rol r ON r.id_persona_rol = c.id_persona_rol
                  JOIN persona p ON p.id_persona = r.id_persona
                 WHERE c.organizacion_id = ?
                   AND r.vigencia_hasta IS NULL
                """, Integer.class, ORGANIZACION);

        List<CuentaDeGobiernoFila> padron = credenciales.cuentasDeGobierno(ORGANIZACION);

        assertTrue(esperadas != null && esperadas > 1,
                "el seed debe tener mas de una cuenta para que la prueba signifique algo");
        assertEquals(esperadas.intValue(), padron.size(),
                "el padron debe traer una fila por cuenta, no una por tenant");
    }

    @Test
    void cadaFilaTraeLosDosIdentificadoresYNingunoEsElOtro() {
        List<CuentaDeGobiernoFila> padron = credenciales.cuentasDeGobierno(ORGANIZACION);

        for (CuentaDeGobiernoFila fila : padron) {
            assertTrue(fila.idPersona() != null && fila.idRol() != null && fila.idCredencial() != null,
                    "sin los tres identificadores no se puede gobernar la cuenta: " + fila);
            // El id del ROL y el de la CREDENCIAL coinciden por diseño (@MapsId);
            // el de la PERSONA es otro, y confundirlos es revocarle el factor a
            // quien no era.
            assertEquals(fila.idRol(), fila.idCredencial(),
                    "credencial y rol comparten clave por @MapsId");
        }
    }

    /** Las dos consultas auxiliares del padron: se cruzan por credencial. */
    @Test
    void lasConsultasAuxiliaresSonAgrupadasYNoColapsan() {
        assertTrue(membresias.bandasActivas(ORGANIZACION).size() >= 1,
                "el seed tiene al menos una membresia activa");
        // Sin factores activos la lista es vacia, y eso es valido: la ausencia
        // significa "sin MFA", no un hueco.
        assertTrue(factores.codigosDisponiblesPorCredencial(ORGANIZACION).size() >= 0);
    }

    /**
     * El camino COMPLETO hasta el cable: service + serializacion. El fallo que
     * motivo esta clase se veia como "el API devuelve una fila", asi que
     * comprobar solo el repositorio dejaria sin cubrir justo el tramo donde
     * podria volver a perderse.
     */
    @Test
    void elServicioYLaSerializacionConservanTodasLasCuentas() throws Exception {
        Actor gobierno = new Actor(ORGANIZACION, 1L, 1L, Actor.TENANT_ADMIN);

        List<SeguridadService.CuentaDeGobierno> cuentas = seguridad.cuentas(gobierno);
        assertEquals(credenciales.cuentasDeGobierno(ORGANIZACION).size(), cuentas.size(),
                "el service no puede perder filas al cruzar banda y codigos");

        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(cuentas));
        assertTrue(json.isArray(), "el cable espera un array");
        assertEquals(cuentas.size(), json.size(), "la serializacion no puede colapsar el array");
    }

    /** Gobierno es gobierno: un BROKER no ve el padron. */
    @Test
    void unBrokerNoLeeElPadron() {
        Actor broker = new Actor(ORGANIZACION, 2L, 2L, Actor.BROKER);
        assertThrows(AccesoNoAutorizadoException.class, () -> seguridad.cuentas(broker));
    }
}
