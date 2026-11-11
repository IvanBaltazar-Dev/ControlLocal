package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.integracion.soporte.OrigenesDePrueba;
import com.controllocal.integracion.soporte.ComunicacionesDePrueba;
import com.controllocal.integracion.soporte.ActuacionesHumanasC4DePrueba;
import com.controllocal.integracion.soporte.EvidenciaC4DePrueba;
import com.controllocal.service.ContextosService;
import com.controllocal.service.ClienteService;
import com.controllocal.service.VisitaService;
import com.controllocal.service.ProspeccionService;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.InteraccionService;
import com.controllocal.service.InteraccionService.DatosInteraccion;
import com.controllocal.service.PropietarioService;
import com.controllocal.service.PropietarioService.DatosPropietario;
import com.controllocal.service.actuacion.ComandosAutomaticosService;
import com.controllocal.service.actuacion.ComandosAutomaticosService.CorrelacionAutomatico;
import com.controllocal.service.actuacion.ComandosAutomaticosService.ResultadoAutomatico;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.controllocal.service.actuacion.ComandosAutomaticosService.*;
import static org.junit.jupiter.api.Assertions.*;

/** UC-K-07 sobre commits PostgreSQL: cuatro AUTO, misma identidad y resultado original autorizado. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        // Dos transacciones ocupadas y una conexión observadora del bloqueo real.
        properties = "spring.datasource.hikari.maximum-pool-size=3")
class ComandosAutomaticosIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p) { BaseDeDatosDePruebas.registrar(p); }
    @Autowired ComandosAutomaticosService servicio;
    @Autowired MotorDeCaptura captura;
    @Autowired PropietarioService propietarios;
    @Autowired InteraccionService interacciones;
    @Autowired ComandoIdempotenteRepository registros;
    @Autowired EventoDominioRepository eventos;
    @Autowired ComandosIdempotentes comandos;
    @Autowired Documentos documentos;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired ClienteService clientes;
    @Autowired ContextosService contextos;
    @Autowired VisitaService visitas;
    @Autowired ProspeccionService prospecciones;
    @Autowired ContextosDeActividad contextosActividad;
    @Autowired ActuacionesService actuaciones;
    private OrigenesDePrueba origenes;
    private OrigenesDePrueba.Equipo equipo;
    private ContextoSesion sesion;
    private Actor actor;
    private long cliente;

    enum Auto { PROPIETARIO, INTERACCION, ABRIR, RESPONDER }
    record Operacion(String capacidad, CorrelacionAutomatico correlacion, Supplier<ResultadoAutomatico<?>> emitir,
                     Function<String, Optional<ResultadoAutomatico<?>>> consultar) { }
    static class RespuestaPerdida extends RuntimeException { }
    static class FalloDespuesDelHecho extends RuntimeException { }

    @BeforeEach void preparar(TestInfo info) {
        EvidenciaC4DePrueba.iniciar(info);
        origenes=new OrigenesDePrueba(jdbc,transacciones,captura,contextos);
        equipo=origenes.empresa();actor=equipo.agente();sesion=origenes.sesion(actor);
        String documento=Long.toString(10_000_000L+Math.floorMod(System.nanoTime(),80_000_000L));
        cliente=clientes.registrar(new ClienteService.DatosCliente("N","D",documento,
                "Cliente TEST AUTO","900000001",null,"Comercio",true,true,null),actor).id();
        EvidenciaC4DePrueba.actor(actor);
        EvidenciaC4DePrueba.recurso(actor,"CLIENTE",cliente,null);
    }
    @AfterEach void cerrarEvidencia(){EvidenciaC4DePrueba.limpiar();}
    private CorrelacionAutomatico correlacion() {
        return new CorrelacionAutomatico("auto-" + UUID.randomUUID(), "turno-uno", "mensaje-" + UUID.randomUUID());
    }
    private Procedencia origen(CorrelacionAutomatico c) {
        return Procedencia.deAgente("API", "KAIROS", "fixture-determinista", "1",
                c.conversacionId(), c.turnoId(), c.mensajeId(), "Fixture de solicitud expresa del usuario");
    }
    private DatosPropietario propietario() {
        return new DatosPropietario("N", "P", "CI-" + UUID.randomUUID().toString().substring(0, 20), "Propietario de prueba C0",
                "900000001", null, true, null);
    }
    private DatosInteraccion interaccion(String observaciones) {
        return ComunicacionesDePrueba.llamadaSalienteSinRespuesta(cliente,observaciones);
    }
    private Operacion operacion(Auto tipo) {
        EvidenciaC4DePrueba.accion("AUTO_"+tipo.name(),actor,null,null,null);
        var c = correlacion(); var p = origen(c);
        var operacion = switch (tipo) {
            case PROPIETARIO -> {
                var d = propietario();
                yield new Operacion(REGISTRAR_PROPIETARIO, c, () -> servicio.registrarPropietario(d, actor, c, p),
                        k -> servicio.consultarPropietario(actor, k).map(r -> (ResultadoAutomatico<?>) r));
            }
            case INTERACCION -> {
                var d = interaccion("Solicitud de prueba");
                yield new Operacion(REGISTRAR_INTERACCION, c, () -> servicio.registrarInteraccion(d, actor, sesion, c, p),
                        k -> servicio.consultarInteraccion(REGISTRAR_INTERACCION, actor, k).map(r -> (ResultadoAutomatico<?>) r));
            }
            case ABRIR -> new Operacion(ABRIR_CAPTURA, c,
                    () -> servicio.avanzarCaptura(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD, null, null, respuestas(Map.of("distrito", "Surco")), null), actor, c, p),
                    k -> servicio.consultarCaptura(actor, ABRIR_CAPTURA, k).map(r -> (ResultadoAutomatico<?>) r));
            case RESPONDER -> {
                var capturaInicial = captura.avanzar(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD, null, null, respuestas(Map.of("distrito", "Surco")), null), p, actor);
                EvidenciaC4DePrueba.resultado("PREPARAR_RESPUESTA_CAPTURA",actor,capturaInicial);
                long id=capturaInicial.idBorrador();
                yield new Operacion(RESPONDER_CAPTURA, c,
                        () -> servicio.avanzarCaptura(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD, id, capturaInicial.version(), respuestas(Map.of("distrito", "Miraflores")), null), actor, c, p),
                        k -> servicio.consultarCaptura(actor, RESPONDER_CAPTURA, k).map(r -> (ResultadoAutomatico<?>) r));
            }
        };
        return new Operacion(operacion.capacidad(),operacion.correlacion(),()->{
            EvidenciaC4DePrueba.accion(operacion.capacidad(),actor,null,null,null);
            var r=operacion.emitir().get();
            EvidenciaC4DePrueba.resultado(operacion.capacidad(),actor,r.resultado());
            return r;
        },clave->{
            var r=operacion.consultar().apply(clave);
            EvidenciaC4DePrueba.estado("CONSULTAR_"+operacion.capacidad(),actor,r.isPresent()?"PRESENTE":"AUSENTE");
            r.ifPresent(v->EvidenciaC4DePrueba.resultado(operacion.capacidad(),actor,v.resultado()));
            return r;
        });
    }
    private Map<String, Long> efectos() {
        var r = new LinkedHashMap<String, Long>();
        for (String tabla : List.of("persona", "persona_rol", "interaccion_comercial","interaccion_vinculo","interaccion_version","borrador_captura",
                "comando_idempotente", "evento_dominio")) {
            r.put(tabla, jdbc.queryForObject("select count(*) from " + tabla, Long.class));
        }
        return r;
    }

    @ParameterizedTest @EnumSource(Auto.class)
    void respuestaPerdidaDespuesDelCommitSeConciliaSinReemitir(Auto tipo) {
        var op = operacion(tipo);
        var antes = efectos();
        String clave = servicio.clavePara(op.capacidad(), actor, op.correlacion());
        assertThrows(RespuestaPerdida.class, () -> { op.emitir().get(); throw new RespuestaPerdida(); });
        var trasCommit = efectos();
        assertEquals(antes.get("comando_idempotente") + 1, trasCommit.get("comando_idempotente"));
        assertEquals(antes.get("evento_dominio") + 1, trasCommit.get("evento_dominio"));
        var original = op.consultar().apply(clave).orElseThrow();
        assertEquals(original, op.emitir().get());
        assertEquals(trasCommit, efectos());
    }

    @ParameterizedTest @EnumSource(Auto.class)
    void falloDeTransaccionRevierteHechoResultadoYProcedencia(Auto tipo) {
        var op = operacion(tipo);
        var antes = efectos();
        assertThrows(FalloDespuesDelHecho.class, () -> new TransactionTemplate(transacciones).execute(s -> {
            op.emitir().get(); throw new FalloDespuesDelHecho();
        }));
        assertEquals(antes, efectos());
        assertTrue(op.consultar().apply(servicio.clavePara(op.capacidad(), actor, op.correlacion())).isEmpty());
        assertNotNull(op.emitir().get().resultado());
    }

    @ParameterizedTest @EnumSource(Auto.class)
    void dosTransaccionesRealesEsperanLaMismaIdentidadYSoloProducenUnEfecto(Auto tipo) throws Exception {
        var op = operacion(tipo);
        var antes = efectos();
        var listo = new CountDownLatch(1);
        var permitirCommit = new CountDownLatch(1);
        var segundoPidListo = new CountDownLatch(1);
        var segundoPid = new AtomicLong();var primeroPid=new AtomicLong();
        var primerTxid=new AtomicLong();var segundoTxid=new AtomicLong();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var primero = pool.submit(() -> EvidenciaC4DePrueba.enHilo(()->new TransactionTemplate(transacciones).execute(s -> {
                primeroPid.set(jdbc.queryForObject("select pg_backend_pid()",Long.class));
                primerTxid.set(jdbc.queryForObject("select txid_current()",Long.class));
                var r = op.emitir().get(); listo.countDown();
                esperar(permitirCommit); return r;
            })));
            assertTrue(listo.await(15, TimeUnit.SECONDS));
            var segundo = pool.submit(() -> EvidenciaC4DePrueba.enHilo(()->new TransactionTemplate(transacciones).execute(s -> {
                segundoPid.set(jdbc.queryForObject("select pg_backend_pid()", Long.class));
                segundoTxid.set(jdbc.queryForObject("select txid_current()",Long.class));
                segundoPidListo.countDown(); return op.emitir().get();
            })));
            assertTrue(segundoPidListo.await(10, TimeUnit.SECONDS));
            boolean bloqueoReal = false;
            for (int i = 0; i < 100 && !bloqueoReal; i++) {
                bloqueoReal = Boolean.TRUE.equals(jdbc.queryForObject("""
                    select coalesce(bool_or(wait_event_type='Lock' and ?=any(pg_blocking_pids(pid))), false)
                    from pg_stat_activity where pid=?
                    """, Boolean.class, primeroPid.get(),segundoPid.get()));
                if (!bloqueoReal) Thread.sleep(20);
            }
            assertNotEquals(primeroPid.get(),segundoPid.get());assertNotEquals(primerTxid.get(),segundoTxid.get());
            assertTrue(bloqueoReal, "La segunda TX debe esperar a la primera hasta su commit: Cuenta precede al advisory en C4.");
            permitirCommit.countDown();
            assertEquals(primero.get(15, TimeUnit.SECONDS), segundo.get(15, TimeUnit.SECONDS));
            assertEquals(antes.get("comando_idempotente") + 1, efectos().get("comando_idempotente"));
            assertEquals(antes.get("evento_dominio") + 1, efectos().get("evento_dominio"));
        } finally {
            permitirCommit.countDown(); pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS);
            if(primeroPid.get()>0&&primerTxid.get()>0)EvidenciaC4DePrueba.transaccion("PRIMERA",primeroPid.get(),primerTxid.get());
            if(segundoPid.get()>0&&segundoTxid.get()>0)EvidenciaC4DePrueba.transaccion("SEGUNDA",segundoPid.get(),segundoTxid.get());
        }
    }

    @ParameterizedTest @EnumSource(Auto.class)
    void aislamientoAmbientalIncompatibleNoEjecutaNiDejaUnResultadoFalso(Auto tipo) {
        var op = operacion(tipo); var antes = efectos();
        var tx = new TransactionTemplate(transacciones);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThrows(IllegalStateException.class, () -> tx.execute(s -> op.emitir().get()));
        assertEquals(antes, efectos());
        assertTrue(op.consultar().apply(servicio.clavePara(op.capacidad(), actor, op.correlacion())).isEmpty());
    }
    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("La prueba no liberó el commit.");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    @Test void consultaDesdeOtraInstanciaLeeLaBaseYConservaElOriginalTrasEditarLaPersona() {
        var d = propietario(); var c = correlacion();
        var original = servicio.registrarPropietario(d, actor, c, origen(c));
        propietarios.actualizar(original.resultado().id(), new DatosPropietario(d.tipoPersona(), d.tipoDocumento(),
                d.numeroDocumento(), "Nombre posterior", "900000009", null, true, null), actor);
        var otraInstancia = new ComandosAutomaticosService(propietarios, interacciones, visitas,
                prospecciones,contextosActividad,captura,comandos,registros,eventos,documentos,json,
                new com.controllocal.service.actuacion.IdentidadComandoAutomatico(documentos,json));
        assertEquals(original, otraInstancia.consultarPropietario(actor, original.clave()).orElseThrow());
        assertEquals(original, servicio.recuperarPropietario(d, actor, c).orElseThrow());
        assertEquals("Nombre posterior", propietarios.obtener(original.resultado().id(), actor).nombre());
    }

    @Test void mismaCorrelacionConOtroCuerpoIncluidasMayusculasNoEsOtroRegistro() {
        var d = propietario(); var c = correlacion();
        servicio.registrarPropietario(d, actor, c, origen(c)); var antes = efectos();
        var cambiado = new DatosPropietario(d.tipoPersona(), d.tipoDocumento(), d.numeroDocumento(),
                d.nombre().toUpperCase(java.util.Locale.ROOT), d.telefono(), d.correo(), true, d.estado());
        assertThrows(ConflictoException.class, () -> servicio.registrarPropietario(cambiado, actor, c, origen(c)));
        assertThrows(ConflictoException.class, () -> servicio.recuperarPropietario(cambiado, actor, c));
        assertEquals(antes, efectos());
    }

    @Test void mismoMensajeDeOtraCapacidadActorOTenantNoRevelaElResultado() {
        var otroEquipo=origenes.empresa();
        EvidenciaC4DePrueba.actor(otroEquipo.agente());
        EvidenciaC4DePrueba.actor(equipo.colega());
        EvidenciaC4DePrueba.actor(equipo.admin());
        var c=correlacion();var original=servicio.registrarPropietario(propietario(),actor,c,origen(c));
        EvidenciaC4DePrueba.resultado(REGISTRAR_PROPIETARIO,actor,original.resultado());
        var antes=efectos();
        assertTrue(servicio.consultarInteraccion(REGISTRAR_INTERACCION,actor,original.clave()).isEmpty());
        assertTrue(servicio.consultarPropietario(otroEquipo.agente(),original.clave()).isEmpty());
        assertTrue(servicio.consultarPropietario(equipo.colega(),original.clave()).isEmpty());
        // Sabotaje de vinculación: Persona de un miembro real con rol operativo de otro.
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultarPropietario(
                new Actor(actor.idOrganizacion(),equipo.colega().idPersona(),actor.idRolOperativo(),Actor.AGENTE),original.clave()));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultarPropietario(equipo.admin(),original.clave()));
        assertEquals(antes,efectos());
    }

    @Test void unaInteraccionEditadaNoReemplazaElResultadoInicial() {
        var d = interaccion("Observación original"); var c = correlacion();
        var original = servicio.registrarInteraccion(d, actor, sesion, c, origen(c));
        var rectificacion=new InteraccionService.Rectificacion(original.resultado().id(),original.resultado().version(),
                interaccion("Observación posterior"),"Corrección expresa TEST, sin otra llamada");
        var propuesta=actuaciones.prepararRectificacionInteraccion(actor,rectificacion,sesion,Procedencia.deLaPantalla());
        new ActuacionesHumanasC4DePrueba(actuaciones,documentos).confirmar(propuesta,actor,sesion,InteraccionService.FichaInteraccion.class);
        assertEquals(original, servicio.consultarInteraccion(REGISTRAR_INTERACCION, actor, original.clave()).orElseThrow());
        assertEquals(original, servicio.registrarInteraccion(d, actor, sesion, c, origen(c)));
        assertEquals("Observación posterior", interacciones.obtener(original.resultado().id(), actor).datos().resumen());
    }

    @Test void aperturaYRespuestaAntiguasNoPisanUnAvancePosterior() {
        var c1 = correlacion(); var c2 = correlacion(); var c3 = correlacion();
        var apertura = servicio.avanzarCaptura(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD, null, null, respuestas(Map.of("distrito", "Surco")), null), actor, c1, origen(c1));
        long id = apertura.resultado().idBorrador();
        var respuesta = servicio.avanzarCaptura(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD, id, apertura.resultado().version(), respuestas(Map.of("distrito", "Miraflores")), null), actor, c2, origen(c2));
        servicio.avanzarCaptura(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD, id, respuesta.resultado().version(), respuestas(Map.of("distrito", "San Isidro")), null), actor, c3, origen(c3));
        var antes = efectos();
        assertEquals(apertura, servicio.avanzarCaptura(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD, null, null, respuestas(Map.of("distrito", "Surco")), null), actor, c1, origen(c1)));
        assertEquals(respuesta, servicio.avanzarCaptura(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD, id, apertura.resultado().version(), respuestas(Map.of("distrito", "Miraflores")), null), actor, c2, origen(c2)));
        assertEquals("Surco", servicio.consultarCaptura(actor, ABRIR_CAPTURA, apertura.clave()).orElseThrow().resultado().conocido().get("distrito"));
        assertEquals("Miraflores", servicio.consultarCaptura(actor, RESPONDER_CAPTURA, respuesta.clave()).orElseThrow().resultado().conocido().get("distrito"));
        assertEquals("San Isidro", captura.consultar(id, actor).conocido().get("distrito"));
        assertEquals(antes, efectos());
    }

    @Test void otraCorrelacionConVersionObsoletaNoEmiteRespuestaNiOcultaElConflicto() {
        var c1 = correlacion(); var c2 = correlacion(); var c3 = correlacion();
        var apertura = servicio.avanzarCaptura(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD,
                null, null, respuestas(Map.of("distrito", "Surco")), null), actor, c1, origen(c1));
        long id = apertura.resultado().idBorrador();
        int versionObservada = apertura.resultado().version();
        var avance = servicio.avanzarCaptura(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD,
                id, versionObservada, respuestas(Map.of("distrito", "Miraflores")), null), actor, c2, origen(c2));
        assertTrue(avance.resultado().version() > versionObservada);
        var antes = efectos();
        assertThrows(ConflictoException.class, () -> servicio.avanzarCaptura(new MotorDeCaptura.Avance(
                MotorDeCaptura.REGISTRAR_PROPIEDAD, id, versionObservada,
                respuestas(Map.of("distrito", "San Isidro")), null), actor, c3, origen(c3)));
        assertTrue(servicio.consultarCaptura(actor, RESPONDER_CAPTURA,
                servicio.clavePara(RESPONDER_CAPTURA, actor, c3)).isEmpty());
        assertEquals("Miraflores", captura.consultar(id, actor).conocido().get("distrito"));
        assertEquals(antes, efectos());
    }

    @Test void procedenciaCompletaYActorSeConfirmanEnElMismoCommit() {
        var c = new CorrelacionAutomatico("c".repeat(80) + UUID.randomUUID(), "t".repeat(80), "m".repeat(160));
        var original = servicio.registrarPropietario(propietario(), actor, c, origen(c));
        var fila = jdbc.queryForMap("""
            select c.organizacion_id,c.id_persona_rol,c.agente,c.canal,c.mensaje_id,c.resultado,
                   e.conversacion_id,e.turno_id,e.herramienta,e.carga_util
            from comando_idempotente c join evento_dominio e
              on e.organizacion_id=c.organizacion_id and e.carga_util::jsonb->>'claveComando'=c.idempotency_key
            where c.organizacion_id=? and c.idempotency_key=?
            """, actor.idOrganizacion(), original.clave());
        assertEquals(actor.idOrganizacion(), ((Number) fila.get("organizacion_id")).longValue());
        assertEquals(actor.idRolOperativo(), ((Number) fila.get("id_persona_rol")).longValue());
        assertEquals("KAIROS", fila.get("agente")); assertEquals("API", fila.get("canal"));
        assertEquals(REGISTRAR_PROPIETARIO, fila.get("herramienta"));
        assertEquals("c".repeat(64), fila.get("conversacion_id"));
        assertEquals("t".repeat(64), fila.get("turno_id"));
        Map<?, ?> correlacion = (Map<?, ?>) documentos.comoMapa(fila.get("resultado").toString()).get("correlacion");
        assertEquals(c.conversacionId(), correlacion.get("conversacionId"));
        assertEquals(c.turnoId(), correlacion.get("turnoId"));
        assertEquals(c.mensajeId(), correlacion.get("mensajeId"));
        assertEquals(documentos.comoMapa(fila.get("resultado").toString()).get("huellaResultado"),
                documentos.comoMapa(fila.get("carga_util").toString()).get("huellaResultado"));
        assertEquals(original, servicio.consultarPropietario(actor, original.clave()).orElseThrow());
    }

    @Test void resultadoCorrompidoSeDetectaAntesDeLaEntregaYNoSeReejecuta() {
        var c = correlacion(); var d = propietario();
        var original = servicio.registrarPropietario(d, actor, c, origen(c));
        String guardado = jdbc.queryForObject("select resultado from comando_idempotente where organizacion_id=? and idempotency_key=?",
                String.class, actor.idOrganizacion(), original.clave());
        var documento = documentos.comoMapa(guardado);
        var resultado = json.convertValue(documento.get("resultado"), new TypeReference<LinkedHashMap<String, Object>>() { });
        resultado.put("nombre", "Texto corrompido de fixture"); documento.put("resultado", resultado);
        jdbc.update("update comando_idempotente set resultado=? where organizacion_id=? and idempotency_key=?",
                documentos.objetoCanonico(documento), actor.idOrganizacion(), original.clave());
        try {
            var antes = efectos();
            assertThrows(IllegalStateException.class, () -> servicio.consultarPropietario(actor, original.clave()));
            assertThrows(IllegalStateException.class, () -> servicio.registrarPropietario(d, actor, c, origen(c)));
            assertEquals(antes, efectos());
            assertEquals(d.nombre(), propietarios.obtener(original.resultado().id(), actor).nombre());
        } finally {
            // Sabotaje acotado: se conserva la fila original exacta, no se deja metadata corrupta.
            jdbc.update("update comando_idempotente set resultado=? where organizacion_id=? and idempotency_key=?",
                    guardado,actor.idOrganizacion(),original.clave());
        }
    }

    @Test void conservaLasGuardasDeConsentimientoYNoRegistraUnExitoParcial() {
        var d = propietario(); var c = correlacion(); var antes = efectos();
        var sinConsentimiento = new DatosPropietario(d.tipoPersona(), d.tipoDocumento(), d.numeroDocumento(),
                d.nombre(), d.telefono(), d.correo(), false, null);
        assertThrows(ReglaNegocioException.class, () -> servicio.registrarPropietario(sinConsentimiento, actor, c, origen(c)));
        assertEquals(antes, efectos());
        assertTrue(servicio.consultarPropietario(actor, servicio.clavePara(REGISTRAR_PROPIETARIO, actor, c)).isEmpty());
    }

    @Test void consultaAusenteNoProduceEfectoNiSeConcluyeRollback() {
        var antes = efectos();
        assertTrue(servicio.consultarPropietario(actor, UUID.randomUUID().toString()).isEmpty());
        assertEquals(antes, efectos());
    }

    private static java.util.Map<String,MotorDeCaptura.Respuesta> respuestas(java.util.Map<String,String> datos) {
        var resultado=new java.util.LinkedHashMap<String,MotorDeCaptura.Respuesta>();
        datos.forEach((clave,valor)->resultado.put(clave,MotorDeCaptura.Respuesta.conocida(valor)));
        return resultado;
    }
}
