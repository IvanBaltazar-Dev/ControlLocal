package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.integracion.soporte.SesionesDePrueba;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.seguridad.InicioSeguroService;
import com.controllocal.service.seguridad.InicioSeguroService.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL real: persistencia, constraints, rollback y consumo concurrente. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class InicioSeguroIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p) { BaseDeDatosDePruebas.registrar(p); }
    @Autowired InicioSeguroService inicio;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired ContextosService contextos;
    private Actor actor;
    private SesionVerificada sesion;

    @BeforeEach void preparar() {
        var f = jdbc.queryForMap("""
            select r.organizacion_id,r.id_persona,r.id_persona_rol from detalle_agente a
            join persona_rol r using(id_persona_rol) order by r.id_persona_rol limit 1
            """);
        actor = new Actor(((Number) f.get("organizacion_id")).longValue(), ((Number) f.get("id_persona")).longValue(),
                ((Number) f.get("id_persona_rol")).longValue(), Actor.AGENTE);
        sesion = sesionReal();
    }

    private SesionVerificada sesionReal() {
        var p=SesionesDePrueba.abrir(jdbc,contextos,actor);
        var c=ContextoDeActuacion.de(p);
        return new SesionVerificada(actor,c.identidad(),p.token().emitidoEn(),p.token().expiraEn(),
                p.token().referenciaSesion(),p.token().revisionContexto());
    }

    private Vinculo nuevo() {
        var vinculo = inicio.iniciar(Proposito.ACCESO, Instant.now().plusSeconds(1800));
        // Fixture temporal explícito: simula creación anterior al login sin
        // dormir ni modificar el reloj/contrato de producción.
        jdbc.update("update vinculo_interaccion_segura set creado_en=? where hash_flujo=?",
                Instant.now().minusSeconds(120).atOffset(ZoneOffset.UTC), huella(vinculo.flujoRef()));
        return vinculo;
    }
    private RetornoPreparado completar(Vinculo vinculo) {
        return inicio.completar(vinculo.flujoRef(), Proposito.ACCESO, sesion);
    }
    private Map<String, Object> fila(Vinculo vinculo) {
        return jdbc.queryForMap("select * from vinculo_interaccion_segura where hash_flujo=?", huella(vinculo.flujoRef()));
    }

    @Test void correlacionPreviaNoCreaIdentidadNiGuardaReferenciasEnClaro() {
        var vinculo = nuevo();
        var fila = fila(vinculo);
        assertNull(fila.get("organizacion_id"));
        assertNull(fila.get("id_persona"));
        assertNull(fila.get("contexto_sesion"));
        assertNull(fila.get("hash_retorno"));
        assertNotEquals(vinculo.flujoRef(), fila.get("hash_flujo"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update vinculo_interaccion_segura set organizacion_id=? where hash_flujo=?",
                actor.idOrganizacion(), huella(vinculo.flujoRef())));
        assertNull(fila(vinculo).get("organizacion_id"));
    }

    @Test void resultadoDeAccesoYConsumoSeConservanEntreTransacciones() {
        var vinculo = nuevo();
        var retorno = completar(vinculo);
        var preparada = fila(vinculo);
        assertEquals(actor.idOrganizacion(), ((Number) preparada.get("organizacion_id")).longValue());
        assertEquals(actor.idPersona(), ((Number) preparada.get("id_persona")).longValue());
        assertNotEquals(retorno.retornoRef(), preparada.get("hash_retorno"));
        assertNotNull(preparada.get("completado_en"));
        assertNull(preparada.get("usado_en"));
        assertEquals("CONFIRMADO", inicio.consumir(vinculo.flujoRef(), Proposito.ACCESO, retorno.retornoRef(), sesion).estado());
        assertNotNull(fila(vinculo).get("usado_en"));
        assertThrows(ConflictoException.class, () -> inicio.consumir(vinculo.flujoRef(), Proposito.ACCESO, retorno.retornoRef(), sesion));
    }

    @Test void jwtEmitidoEnElSegundoDeCreacionCompletaYConsumeSinEsperar() {
        var vinculo = inicio.iniciar(Proposito.ACCESO, Instant.now().plusSeconds(1800));
        // Creación y emisión reales en la misma precisión de segundo; sin sleeps.
        var rapida = sesionReal();
        jdbc.update("update vinculo_interaccion_segura set creado_en=? where hash_flujo=?",
                rapida.emitidaEn().plusMillis(100).atOffset(ZoneOffset.UTC),huella(vinculo.flujoRef()));
        var retorno = inicio.completar(vinculo.flujoRef(), Proposito.ACCESO, rapida);
        assertEquals("CONFIRMADO", inicio.consumir(vinculo.flujoRef(), Proposito.ACCESO, retorno.retornoRef(), rapida).estado());
    }

    @Test void dosTransaccionesCompitenYUnSoloRetornoSeAcepta() throws Exception {
        var vinculo = nuevo();
        var retorno = completar(vinculo);
        var preparados = new CountDownLatch(2);
        var salir = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> consumir = () -> {
                preparados.countDown();
                assertTrue(salir.await(10, TimeUnit.SECONDS));
                try {
                    inicio.consumir(vinculo.flujoRef(), Proposito.ACCESO, retorno.retornoRef(), sesion);
                    return true;
                } catch (ConflictoException rechazado) {
                    return false;
                }
            };
            var primera = ejecutor.submit(consumir);
            var segunda = ejecutor.submit(consumir);
            assertTrue(preparados.await(10, TimeUnit.SECONDS));
            salir.countDown();
            assertEquals(1, List.of(primera.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS))
                    .stream().filter(Boolean::booleanValue).count());
        }
        assertNotNull(fila(vinculo).get("usado_en"));
    }

    @Test void rollbackDelConsumoNoMarcaUsoQueNoSeConfirmo() {
        var vinculo = nuevo();
        var retorno = completar(vinculo);
        new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
            inicio.consumir(vinculo.flujoRef(), Proposito.ACCESO, retorno.retornoRef(), sesion);
            tx.setRollbackOnly();
        });
        assertNull(fila(vinculo).get("usado_en"));
        assertEquals("CONFIRMADO", inicio.consumir(vinculo.flujoRef(), Proposito.ACCESO, retorno.retornoRef(), sesion).estado());
    }

    @Test void otroTenantNoObtieneNiConsumeElResultadoAutenticado() {
        var vinculo = nuevo();
        var retorno = completar(vinculo);
        var ajena = new SesionVerificada(new Actor(actor.idOrganizacion() + 10000,
                actor.idPersona(), actor.idRolOperativo(), Actor.AGENTE), sesion.contexto(), sesion.emitidaEn(), sesion.expiraEn(),sesion.referenciaSesion(),sesion.revisionContexto());
        assertThrows(AccesoNoAutorizadoException.class, () -> inicio.consumir(vinculo.flujoRef(), Proposito.ACCESO, retorno.retornoRef(), ajena));
        assertNull(fila(vinculo).get("usado_en"));
        assertEquals("CONFIRMADO", inicio.consumir(vinculo.flujoRef(), Proposito.ACCESO, retorno.retornoRef(), sesion).estado());
    }

    @Test void unLoginNuevoNoReutilizaLaAprobacionDeLaSesionAnterior() {
        var vinculo = nuevo();
        var retorno = completar(vinculo);
        var otraSesion = sesionReal();
        assertThrows(ConflictoException.class, () -> inicio.consumir(vinculo.flujoRef(), Proposito.ACCESO, retorno.retornoRef(), otraSesion));
        assertNull(fila(vinculo).get("usado_en"));
    }

    @Test void sesionRevocadaDespuesDeCompletarNoEntregaRetorno() {
        var vinculo = nuevo();
        var retorno = completar(vinculo);
        jdbc.update("update sesion_acceso set revocada=true where referencia_hash=?",huella(sesion.referenciaSesion()));
        assertThrows(AccesoNoAutorizadoException.class, () -> inicio.consumir(vinculo.flujoRef(), Proposito.ACCESO, retorno.retornoRef(), sesion));
        assertNull(fila(vinculo).get("usado_en"));
    }

    @Test void vinculoVencidoConSesionAunValidaNoSeConsume() {
        var vinculo = nuevo();
        var retorno = completar(vinculo);
        jdbc.update("update vinculo_interaccion_segura set vence_en=? where hash_flujo=?",
                Instant.now().minusSeconds(1).atOffset(ZoneOffset.UTC), huella(vinculo.flujoRef()));
        assertThrows(ConflictoException.class, () -> inicio.consumir(vinculo.flujoRef(), Proposito.ACCESO, retorno.retornoRef(), sesion));
        assertNull(fila(vinculo).get("usado_en"));
    }

    @Test void noExisteCambioParcialDeTenantDespuesDeLigarElRol() {
        var vinculo = nuevo();
        completar(vinculo);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update vinculo_interaccion_segura set organizacion_id=? where hash_flujo=?",
                actor.idOrganizacion() + 10000, huella(vinculo.flujoRef())));
        assertEquals(actor.idOrganizacion(), ((Number) fila(vinculo).get("organizacion_id")).longValue());
    }

    private static String huella(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
