package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.FotoPropiedadService;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ActuacionesService.*;
import com.controllocal.service.captura.GuionRegistroPropiedad;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.web.seguridad.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Casos reales UC-K-06/07: captura/definición Core, revisión vinculada,
 * transacciones PostgreSQL, pérdida de respuesta y un único efecto.
 * Los recorridos HTTP KAIROS usan intérprete/puerto/servicios reales y un
 * principal de fixture ya resuelto; sin filtros, no certifican el login.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
class ActuacionesIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p) { BaseDeDatosDePruebas.registrar(p); }
    @Autowired ActuacionesService actuaciones;
    @Autowired MotorDeCaptura captura;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.controllocal.service.ContextosService contextos;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean PlatformTransactionManager transacciones;
    @MockitoSpyBean PropiedadUniversalService propiedades;
    @MockitoSpyBean FotoPropiedadService fotos;
    private Actor actor;
    private ContextoSesion sesion;
    private final Procedencia origen = Procedencia.deLaPantalla();
    record Preparada(long borrador, Propuesta propuesta, Revision revision) { }

    @BeforeEach void prepararActor() {
        reset(transacciones, propiedades, fotos);
        var f = jdbc.queryForMap("""
            select r.organizacion_id,r.id_persona,r.id_persona_rol from detalle_agente a
            join persona_rol r using(id_persona_rol) order by r.id_persona_rol limit 1
            """);
        actor = new Actor(((Number) f.get("organizacion_id")).longValue(), ((Number) f.get("id_persona")).longValue(),
                ((Number) f.get("id_persona_rol")).longValue(), Actor.AGENTE);
        var principal=com.controllocal.integracion.soporte.SesionesDePrueba.abrir(jdbc,contextos,actor);
        sesion = ContextoDeActuacion.de(principal);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, AuthorityUtils.createAuthorityList("ROLE_AGENTE")));
    }
    @AfterEach void limpiar() { reset(transacciones, propiedades, fotos); SecurityContextHolder.clearContext(); }

    private MotorDeCaptura.EstadoCaptura borrador() {
        var datos = new LinkedHashMap<String, String>();
        datos.put(GuionRegistroPropiedad.TIPO_PROPIEDAD, "DEPARTAMENTO");
        datos.put(GuionRegistroPropiedad.DIRECCION, "Av. Revisión " + UUID.randomUUID());
        datos.put(GuionRegistroPropiedad.DISTRITO, "Miraflores");
        datos.put("metraje_total", "96");
        datos.put("dormitorios", "3");
        datos.put("piso", "7");
        datos.put("interiorUnidad", "701");
        var estado = captura.avanzar(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD, null, null, respuestas(datos), null), origen, actor);
        assertTrue(estado.listoParaEjecutar(), () -> "Fixture incompleto según Core: " + estado.faltante());
        return estado;
    }
    private Propuesta proponer(long id) { return actuaciones.prepararCaptura(actor, id, sesion, origen); }
    private Preparada preparada() {
        var capturaInicial = borrador();
        long id = capturaInicial.idBorrador();
        var propuesta = proponer(id);
        return new Preparada(id, propuesta, actuaciones.revisar(actor, propuesta.referencia(), sesion));
    }
    private Confirmacion confirmacion(Preparada preparada, String evento) {
        var revision = preparada.revision();
        return new Confirmacion(revision.propuesta().version(), revision.propuesta().huella(), revision.pruebaRevision(), evento);
    }
    private Resultado confirmar(Preparada preparada, String evento) {
        return actuaciones.confirmar(actor, preparada.propuesta().referencia(), sesion, confirmacion(preparada, evento), origen);
    }
    private Map<String, Long> efectos() {
        var conteos = new LinkedHashMap<String, Long>();
        for (String tabla : List.of("propiedad", "captacion", "comando_idempotente")) {
            conteos.put(tabla, jdbc.queryForObject("select count(*) from " + tabla, Long.class));
        }
        return conteos;
    }
    private void unAltaMas(Map<String, Long> antes) {
        var despues = efectos();
        assertEquals(antes.get("propiedad") + 1, despues.get("propiedad"));
        assertEquals(antes.get("captacion"), despues.get("captacion"));
        assertEquals(antes.get("comando_idempotente") + 1, despues.get("comando_idempotente"));
    }

    @Test void turnoKairosRealRegistraSoloBorradorConActorYTrazaCore() throws Exception {
        var antes = efectos();
        long borradoresAntes = jdbc.queryForObject("select count(*) from borrador_captura", Long.class);
        String conversacion = "p07-" + UUID.randomUUID();
        var entrada = Map.of("canal", "API", "conversacionId", conversacion, "turnoId", "turno_uno",
                "mensajeId", "mensaje_uno", "texto", "registra un departamento en Surco");
        String cuerpo = mvc.perform(post("/kairos/turnos").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(entrada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversacionId").value(conversacion))
                .andExpect(jsonPath("$.desenlace").value("PREGUNTA"))
                .andExpect(jsonPath("$.accion").value("REGISTRAR_PROPIEDAD"))
                .andExpect(jsonPath("$.resultado.captura.conocido.tipoPropiedad").value("DEPARTAMENTO"))
                .andExpect(jsonPath("$.resultado.ejecucion").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(cuerpo).path("resultado").path("captura").path("idBorrador").asLong();
        assertTrue(id > 0);
        var fila = jdbc.queryForMap("select organizacion_id,id_persona_rol,canal,agente,conversacion_id from borrador_captura where id_borrador=?", id);
        assertEquals(actor.idOrganizacion(), ((Number) fila.get("organizacion_id")).longValue());
        assertEquals(actor.idRolOperativo(), ((Number) fila.get("id_persona_rol")).longValue());
        assertEquals("API", fila.get("canal"));
        assertEquals("KAIROS", fila.get("agente"));
        assertEquals(conversacion, fila.get("conversacion_id"));
        assertEquals(borradoresAntes + 1, jdbc.queryForObject("select count(*) from borrador_captura", Long.class));
        var despues = efectos();
        assertEquals(antes.get("propiedad"), despues.get("propiedad"));
        assertEquals(antes.get("captacion"), despues.get("captacion"));
        assertEquals(antes.get("comando_idempotente") + 1, despues.get("comando_idempotente"));
    }

    @Test void confirmarPorKairosRealConBooleanoExtraSoloPreparaLaRevisionHumana() throws Exception {
        var capturaInicial = borrador();
        long id = capturaInicial.idBorrador();
        var antes = efectos();
        var entrada = new LinkedHashMap<String, Object>();
        entrada.put("canal", "API");
        entrada.put("conversacionId", "p07-" + UUID.randomUUID());
        entrada.put("turnoId", "turno_confirmar");
        entrada.put("mensajeId", "mensaje_confirmar");
        entrada.put("texto", "confirmar");
        entrada.put("idBorrador", id);
        entrada.put("versionBorrador", capturaInicial.version());
        entrada.put("confirmado", true); // Campo ajeno al contrato: nunca prueba humana.
        String cuerpo = mvc.perform(post("/kairos/turnos").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(entrada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desenlace").value("PROPUESTA"))
                .andExpect(jsonPath("$.confirmaUnaPersona").value(true))
                .andExpect(jsonPath("$.resultado.actuacion.estado").value("PREPARADA"))
                .andExpect(jsonPath("$.resultado.actuacion.propuesta.capacidad").value("confirmar_captura"))
                .andExpect(jsonPath("$.resultado.actuacion.propuesta.contenido.idBorrador").value(id))
                .andExpect(jsonPath("$.resultado.actuacion.propuesta.pruebaRevision").doesNotExist())
                .andExpect(jsonPath("$.resultado.ejecucion").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String referencia = json.readTree(cuerpo).path("resultado").path("actuacion").path("referencia").asText();
        assertFalse(referencia.isBlank());
        var operacion = jdbc.queryForMap("select organizacion_id,id_persona,id_borrador,estado,prueba_revision,evento_humano from operacion_herramienta where referencia=?", referencia);
        assertEquals(actor.idOrganizacion(), ((Number) operacion.get("organizacion_id")).longValue());
        assertEquals(actor.idPersona(), ((Number) operacion.get("id_persona")).longValue());
        assertEquals(id, ((Number) operacion.get("id_borrador")).longValue());
        assertEquals("P", operacion.get("estado"));
        assertNull(operacion.get("prueba_revision"));
        assertNull(operacion.get("evento_humano"));
        assertEquals(0L, jdbc.queryForObject("select count(*) from operacion_herramienta where id_borrador=? and estado='X'", Long.class, id));
        assertEquals(antes, efectos());
    }

    @Test void prepararYRevisarNoRegistranLaPropiedadNiEncargo() {
        var capturaInicial = borrador();
        long id = capturaInicial.idBorrador();
        var antes = efectos();
        var propuesta = proponer(id);
        assertEquals("confirmar_captura", propuesta.capacidad());
        assertEquals(id, ((Number) propuesta.contenido().get("idBorrador")).longValue());
        assertEquals("PREPARADA", actuaciones.consultar(actor, propuesta.referencia(), sesion).estado());
        var revision = actuaciones.revisar(actor, propuesta.referencia(), sesion);
        assertNotNull(revision.pruebaRevision());
        assertTrue(revision.propuesta().version() > propuesta.version());
        assertEquals(antes, efectos());
    }

    @Test void confirmarYRecuperarRespuestaPerdidaDevuelvenElMismoResultadoOriginal() {
        var preparada = preparada();
        var antes = efectos();
        String evento = UUID.randomUUID().toString();
        var resultado = confirmar(preparada, evento);
        assertEquals("EXITO", resultado.estado());
        long propiedad = ((Number) resultado.resultado().get("idPropiedad")).longValue();
        unAltaMas(antes);
        // La respuesta ya se confirmó; el cliente la pierde y sólo consulta.
        var recuperado = actuaciones.consultar(actor, preparada.propuesta().referencia(), sesion);
        assertEquals(resultado.resultado(), recuperado.resultado());
        var otraSesion = sesionDe(actor);
        assertNotEquals(sesion.referenciaSesion(),otraSesion.referenciaSesion());
        assertEquals(resultado.resultado(), actuaciones.consultar(actor, preparada.propuesta().referencia(), otraSesion).resultado());
        assertEquals(propiedad, ((Number) confirmar(preparada, evento).resultado().get("idPropiedad")).longValue());
        unAltaMas(antes);
    }

    @Test void exitoDurableDelegaEnLaGuardaVigenteDeLaPropiedadAntesDeEntregarDatos() {
        var preparada = preparada();
        assertEquals("EXITO", confirmar(preparada, "evento").estado());
        var antes = efectos();
        clearInvocations(propiedades, fotos);
        String clave = "actuacion:" + preparada.propuesta().referencia();
        PropiedadUniversalService puerta = AopTestUtils.getUltimateTargetObject(propiedades);
        var denegacion = new NoEncontradoException("Propiedad");
        // Sonda de frontera: el caso canónico reautoriza la lectura vigente;
        // aquí se controla su denegación sin reemplazarla por otra ficha.
        // La preparación del Spy evita el proxy @Transactional: su advice
        // llama al TM (también Spy) antes de terminar el stubbing y produce
        // UnfinishedStubbingException. La llamada real conserva ese proxy.
        doThrow(denegacion).when(puerta).consultarResultadoRegistro(clave, actor);
        assertThrows(NoEncontradoException.class,
                () -> actuaciones.consultar(actor, preparada.propuesta().referencia(), sesion));
        verify(puerta, times(1)).consultarResultadoRegistro(clave, actor);
        verify(puerta, never()).consultar(anyLong(), any());
        verifyNoInteractions(fotos);
        assertEquals(antes, efectos());
    }

    @Test void consultarExitoUsaUnaConsultaCanonicaDeResultadoSinFichaFotosOHechosNuevos() {
        var preparada = preparada();
        assertEquals("EXITO", confirmar(preparada, "evento").estado());
        var antes = efectos();
        var eventos = jdbc.queryForObject("select count(*) from evento_dominio", Long.class);
        var historial = jdbc.queryForObject("select count(*) from historial_estado", Long.class);
        clearInvocations(propiedades, fotos);
        var resultado = actuaciones.consultar(actor, preparada.propuesta().referencia(), sesion);
        assertEquals("EXITO", resultado.estado());
        assertNotNull(resultado.resultado().get("idPropiedad"));
        verify(propiedades, times(1)).consultarResultadoRegistro("actuacion:" + preparada.propuesta().referencia(), actor);
        verify(propiedades, never()).consultar(anyLong(), any());
        verifyNoInteractions(fotos);
        assertEquals(antes, efectos());
        assertEquals(eventos, jdbc.queryForObject("select count(*) from evento_dominio", Long.class));
        assertEquals(historial, jdbc.queryForObject("select count(*) from historial_estado", Long.class));
    }

    @Test void unBooleanoDeClienteNoSustituyeElEventoYLaPruebaHumanos() throws Exception {
        var preparada = preparada();
        var antes = efectos();
        mvc.perform(post("/actuaciones/" + preparada.propuesta().referencia() + "/confirmacion")
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmado\":true}"))
                .andExpect(status().isBadRequest());
        assertEquals(antes, efectos());
        assertEquals("PREPARADA", actuaciones.consultar(actor, preparada.propuesta().referencia(), sesion).estado());
    }

    @Test void otraPersonaNoRevisaNiConsultaLaActuacionAjena() {
        var preparada = preparada();
        var otra = otroAgenteDelTenant();var propia=sesionDe(otra);
        // Un Actor ajeno tampoco puede presentarse con la sesión del incorporador.
        assertThrows(AccesoNoAutorizadoException.class,
                () -> actuaciones.consultar(otra, preparada.propuesta().referencia(), sesion));
        assertThrows(AccesoNoAutorizadoException.class,
                () -> actuaciones.revisar(otra, preparada.propuesta().referencia(), propia));
        assertThrows(AccesoNoAutorizadoException.class,
                () -> actuaciones.consultar(otra, preparada.propuesta().referencia(), propia));
    }

    @Test void otroTenantNoLocalizaLaActuacion() {
        var preparada = preparada();
        var otra = agenteDeOtroTenant();var propia=sesionDe(otra);
        assertThrows(AccesoNoAutorizadoException.class, () -> actuaciones.consultar(otra, preparada.propuesta().referencia(), sesion));
        assertThrows(NoEncontradoException.class, () -> actuaciones.revisar(otra, preparada.propuesta().referencia(), propia));
        assertThrows(NoEncontradoException.class, () -> actuaciones.consultar(otra, preparada.propuesta().referencia(), propia));
    }

    @ParameterizedTest @ValueSource(strings = {"RECHAZADO", "EN_VERIFICACION"})
    void rechazoOIncertidumbreDurablesNoRevelanNiReejecutanLaActuacionAjena(String estado) {
        var preparada = preparada();
        var antes = efectos();
        PropiedadUniversalService puerta = AopTestUtils.getUltimateTargetObject(propiedades);
        RuntimeException respuesta = "RECHAZADO".equals(estado)
                ? new ReglaNegocioException("Fixture: rechazo determinista de Core")
                : new IllegalStateException("Fixture: ejecución sin resultado verificable");
        // La intención y aprobación se confirman realmente antes del efecto.
        // El caso canónico rechaza o falla dentro de su transacción de negocio.
        doThrow(respuesta).when(puerta).registrar(any(PropiedadUniversalService.ComandoRegistro.class), any(Actor.class));
        assertEquals(estado, confirmar(preparada, "evento_original").estado());
        String referencia = preparada.propuesta().referencia();
        String estadoDurable = "RECHAZADO".equals(estado) ? "R" : "U";
        assertEquals(estadoDurable, jdbc.queryForObject(
                "select estado from operacion_herramienta where referencia=?", String.class, referencia));
        String aprobacion = jdbc.queryForObject(
                "select aprobacion from operacion_herramienta where referencia=?", String.class, referencia);
        assertNotNull(aprobacion);

        var otraPersona = otroAgenteDelTenant();var sesionOtraPersona=sesionDe(otraPersona);
        var otroTenant = agenteDeOtroTenant();var sesionOtroTenant=sesionDe(otroTenant);
        assertThrows(AccesoNoAutorizadoException.class,
                () -> actuaciones.consultar(otraPersona, referencia, sesionOtraPersona));
        assertThrows(AccesoNoAutorizadoException.class, () -> actuaciones.confirmar(otraPersona,
                referencia, sesionOtraPersona, confirmacion(preparada, "evento_original"), origen));
        assertThrows(NoEncontradoException.class,
                () -> actuaciones.consultar(otroTenant, referencia, sesionOtroTenant));
        assertThrows(NoEncontradoException.class, () -> actuaciones.confirmar(otroTenant,
                referencia, sesionOtroTenant, confirmacion(preparada, "evento_original"), origen));

        assertEquals(estado, actuaciones.consultar(actor, referencia, sesion).estado());
        assertEquals(estado, confirmar(preparada, "evento_original").estado());
        verify(puerta, times(1)).registrar(any(PropiedadUniversalService.ComandoRegistro.class), any(Actor.class));
        assertEquals(estadoDurable, jdbc.queryForObject(
                "select estado from operacion_herramienta where referencia=?", String.class, referencia));
        assertEquals(aprobacion, jdbc.queryForObject(
                "select aprobacion from operacion_herramienta where referencia=?", String.class, referencia));
        assertEquals(antes, efectos());
    }

    @Test void sesionRenovadaRecuperaResultadoSinReusarAprobacionYPuedeConfirmarUnaNueva() {
        var terminada = preparada();
        var resultadoOriginal = confirmar(terminada, "evento_terminado");
        assertEquals("EXITO", resultadoOriginal.estado());
        var pendienteAnterior = preparada();
        var antes = efectos();
        var anterior = SesionActual.sesion().token();
        var renovada = contextos.renovar(anterior.referenciaSesion(),anterior.revisionContexto());
        var principalRenovado = new SesionDeRequest(new TokenService("").de(renovada),renovada);
        var sesionRenovada = ContextoDeActuacion.de(principalRenovado);
        assertNotEquals(sesion.identidad(), sesionRenovada.identidad());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principalRenovado, null, AuthorityUtils.createAuthorityList("ROLE_AGENTE")));

        assertEquals(resultadoOriginal.resultado(), actuaciones.consultar(actor,
                terminada.propuesta().referencia(), sesionRenovada).resultado());
        assertThrows(AccesoNoAutorizadoException.class, () -> actuaciones.revisar(actor,
                pendienteAnterior.propuesta().referencia(), sesionRenovada));
        assertThrows(AccesoNoAutorizadoException.class, () -> actuaciones.confirmar(actor,
                pendienteAnterior.propuesta().referencia(), sesionRenovada,
                confirmacion(pendienteAnterior, "evento_anterior"), origen));
        assertEquals(antes, efectos());

        // Renovar invalida la prueba anterior; no impide revisar de nuevo la
        // misma captura pendiente bajo la sesión actual y su propia propuesta.
        var nueva = actuaciones.prepararCaptura(actor, pendienteAnterior.borrador(), sesionRenovada, origen);
        assertNotEquals(pendienteAnterior.propuesta().referencia(), nueva.referencia());
        var preparadaRenovada = new Preparada(pendienteAnterior.borrador(), nueva,
                actuaciones.revisar(actor, nueva.referencia(), sesionRenovada));
        assertEquals("EXITO", actuaciones.confirmar(actor, nueva.referencia(), sesionRenovada,
                confirmacion(preparadaRenovada, "evento_renovado"), origen).estado());
        assertEquals("EXITO", actuaciones.consultar(actor, nueva.referencia(), sesionRenovada).estado());
        unAltaMas(antes);
    }

    @Test void cambiarSesionInvalidaLaRevisionAunqueLaPersonaSeaLaMisma() {
        var preparada = preparada();
        var antes = efectos();
        var otraSesion = sesionDe(actor);
        assertNotEquals(sesion.referenciaSesion(),otraSesion.referenciaSesion());
        assertThrows(AccesoNoAutorizadoException.class,
                () -> actuaciones.revisar(actor, preparada.propuesta().referencia(), otraSesion));
        assertThrows(AccesoNoAutorizadoException.class, () -> actuaciones.confirmar(actor,
                preparada.propuesta().referencia(), otraSesion, confirmacion(preparada, "evento"), origen));
        var identidadAlterada=new ContextoSesion("b".repeat(64),sesion.expiraEnEpochSeconds(),sesion.referenciaSesion(),sesion.revisionContexto());
        assertThrows(AccesoNoAutorizadoException.class,
                () -> actuaciones.revisar(actor,preparada.propuesta().referencia(),identidadAlterada));
        assertEquals(antes, efectos());
    }

    @Test void otraAtribucionNoConfirmaLaActuacionDelAgente() {
        var preparada = preparada();
        var fila=jdbc.queryForMap("""
            select r.organizacion_id,r.id_persona,r.id_persona_rol from supervision_agente s
            join persona_rol r on r.id_persona_rol=s.id_rol_broker
            where s.organizacion_id=? and s.id_rol_agente=? and s.fecha_fin is null
            """,actor.idOrganizacion(),actor.idRolOperativo());
        var broker=new Actor(((Number)fila.get("organizacion_id")).longValue(),((Number)fila.get("id_persona")).longValue(),
                ((Number)fila.get("id_persona_rol")).longValue(),Actor.BROKER);
        var sesionBroker=sesionDe(broker);
        assertThrows(AccesoNoAutorizadoException.class, () -> actuaciones.confirmar(broker,
                preparada.propuesta().referencia(), sesionBroker, confirmacion(preparada, "evento"), origen));
    }

    @Test void revisionVencidaExigeUnaNuevaSinEjecutar() {
        var preparada = preparada();
        var antes = efectos();
        jdbc.update("update operacion_herramienta set creada_en=?,caduca_en=? where referencia=?",
                Instant.now().minusSeconds(120).atOffset(ZoneOffset.UTC), Instant.now().minusSeconds(1).atOffset(ZoneOffset.UTC),
                preparada.propuesta().referencia());
        assertThrows(ConflictoException.class, () -> confirmar(preparada, "evento"));
        assertEquals(antes, efectos());
    }

    @Test void cambioSoloDeMayusculasEnSnapshotNoConservaLaPruebaDeIntegridad() {
        var preparada = preparada();
        String referencia = preparada.propuesta().referencia();
        String contenido = jdbc.queryForObject("select contenido from operacion_herramienta where referencia=?",
                String.class, referencia);
        String alterado = contenido.replace("REGISTRAR_PROPIEDAD", "registrar_propiedad");
        assertNotEquals(contenido, alterado);
        jdbc.update("update operacion_herramienta set contenido=? where referencia=?", alterado, referencia);
        var antes = efectos();
        assertThrows(IllegalStateException.class, () -> actuaciones.consultar(actor, referencia, sesion));
        assertEquals(antes, efectos());
    }

    @Test void modificarElDatoDespuesDeRevisarNoConfirmaLaInstantaneaAnterior() {
        var preparada = preparada();
        var antes = efectos();
        int versionObservada = ((Number) preparada.propuesta().contenido().get("versionCaptura")).intValue();
        captura.avanzar(new MotorDeCaptura.Avance(null, preparada.borrador(), versionObservada, respuestas(Map.of(GuionRegistroPropiedad.DIRECCION,
                "Av. Dato corregido " + UUID.randomUUID())), null), origen, actor);
        assertThrows(ConflictoException.class, () -> confirmar(preparada, "evento"));
        assertEquals(antes, efectos());
    }

    @Test void cambiarLaDefinicionCoreTambienInvalidaLoRevisado() {
        var preparada = preparada();
        var antes = efectos();
        var definicion = jdbc.queryForMap("select id_catalogo_atributo,rotulo from catalogo_atributo where clave='metraje_total' and organizacion_id is null");
        Object id = definicion.get("id_catalogo_atributo");
        try {
            jdbc.update("update catalogo_atributo set rotulo=? where id_catalogo_atributo=?", "Área revisada por definición", id);
            assertThrows(ConflictoException.class, () -> confirmar(preparada, "evento"));
            assertEquals(antes, efectos());
        } finally {
            jdbc.update("update catalogo_atributo set rotulo=? where id_catalogo_atributo=?", definicion.get("rotulo"), id);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"version", "huella", "prueba", "sinPrueba", "sinHuella"})
    void pruebaVersionOHuellaAjenasNoSeAceptan(String cambio) {
        var preparada = preparada();
        var correcta = confirmacion(preparada, "evento");
        var falsa = new Confirmacion("version".equals(cambio) ? correcta.version() + 1 : correcta.version(),
                "huella".equals(cambio) ? "0".repeat(64) : "sinHuella".equals(cambio) ? null : correcta.huella(),
                "prueba".equals(cambio) ? UUID.randomUUID().toString() : "sinPrueba".equals(cambio) ? null : correcta.pruebaRevision(),
                correcta.eventoHumano());
        var antes = efectos();
        assertThrows(ConflictoException.class, () -> actuaciones.confirmar(actor, preparada.propuesta().referencia(), sesion, falsa, origen));
        assertEquals(antes, efectos());
    }

    @ParameterizedTest @ValueSource(strings = {"", "evento con espacios", "confirmado=true"})
    void noHayConfirmacionSinEventoHumanoReconocido(String evento) {
        var preparada = preparada();
        var antes = efectos();
        assertThrows(ReglaNegocioException.class, () -> confirmar(preparada, evento));
        assertEquals(antes, efectos());
    }

    @Test void volverARevisarRetiraLaPruebaAnterior() {
        var preparada = preparada();
        var nueva = actuaciones.revisar(actor, preparada.propuesta().referencia(), sesion);
        assertNotEquals(preparada.revision().pruebaRevision(), nueva.pruebaRevision());
        var antes = efectos();
        assertThrows(ConflictoException.class, () -> confirmar(preparada, "evento"));
        assertEquals(antes, efectos());
        assertEquals("EXITO", confirmar(new Preparada(preparada.borrador(), preparada.propuesta(), nueva), "evento").estado());
    }

    @Test void unEventoDistintoNoReutilizaLaConfirmacionYaEjecutada() {
        var preparada = preparada();
        assertEquals("EXITO", confirmar(preparada, "evento_original").estado());
        var antes = efectos();
        assertThrows(ConflictoException.class, () -> confirmar(preparada, "otro_evento"));
        assertEquals(antes, efectos());
    }

    @Test void caidaEntreIntencionYEfectoPermaneceInciertaSinReemitir() {
        var preparada = preparada();
        var antes = efectos();
        var caida = new AtomicBoolean();
        doAnswer(invocacion -> {
            TransactionStatus tx = invocacion.getArgument(0);
            boolean intencion = tx.isNewTransaction() && "I".equals(jdbc.queryForObject(
                    "select estado from operacion_herramienta where referencia=?", String.class, preparada.propuesta().referencia()));
            Object respuesta = invocacion.callRealMethod();
            if (intencion && caida.compareAndSet(false, true)) {
                throw new TransactionSystemException("Fixture: caída después de persistir intención, antes del efecto");
            }
            return respuesta;
        }).when(transacciones).commit(any());
        try {
            assertThrows(TransactionSystemException.class, () -> confirmar(preparada, "evento"));
            assertTrue(caida.get());
        } finally { reset(transacciones); }
        assertEquals("EN_VERIFICACION", actuaciones.consultar(actor, preparada.propuesta().referencia(), sesion).estado());
        assertEquals("EN_VERIFICACION", confirmar(preparada, "evento").estado());
        assertEquals(antes, efectos());
        assertEquals("U", jdbc.queryForObject("select estado from operacion_herramienta where referencia=?", String.class, preparada.propuesta().referencia()));
    }

    @Test void consultaIntercaladaYCambioDeDatoNoPierdenElRechazoDefinitivo() throws Exception {
        var preparada = preparada();
        int versionBorradorObservada=((Number)preparada.propuesta().contenido().get("versionCaptura")).intValue();
        var antes = efectos();
        var intercalada = new AtomicBoolean();
        try (var lector = Executors.newSingleThreadExecutor()) {
            doAnswer(invocacion -> {
                TransactionStatus tx = invocacion.getArgument(0);
                boolean intencion = tx.isNewTransaction() && "I".equals(jdbc.queryForObject(
                        "select estado from operacion_herramienta where referencia=?", String.class, preparada.propuesta().referencia()));
                Object respuesta = invocacion.callRealMethod();
                if (intencion && intercalada.compareAndSet(false, true)) {
                    // La primera transacción ya confirmó y liberó su lock.
                    // Otra petición consulta I->U y corrige la captura antes
                    // de permitir que continúe la transacción de ejecución.
                    lector.submit(() -> {
                        assertEquals("EN_VERIFICACION", actuaciones.consultar(actor, preparada.propuesta().referencia(), sesion).estado());
                        captura.avanzar(new MotorDeCaptura.Avance(null, preparada.borrador(), versionBorradorObservada, respuestas(Map.of(GuionRegistroPropiedad.DIRECCION,
                                "Av. Cambio entre fases " + UUID.randomUUID())), null), origen, actor);
                    }).get(20, TimeUnit.SECONDS);
                }
                return respuesta;
            }).when(transacciones).commit(any());
            var resultado = confirmar(preparada, "evento");
            assertTrue(intercalada.get(), "Se controló el intervalo entre commits, no sólo el resultado final");
            assertEquals("RECHAZADO", resultado.estado());
            assertEquals("CONTENIDO_CAMBIADO", resultado.motivo());
            assertEquals("R", jdbc.queryForObject("select estado from operacion_herramienta where referencia=?", String.class, preparada.propuesta().referencia()));
            assertEquals(antes, efectos());
        } finally { reset(transacciones); }
    }

    @Test void aprobacionConservaVersionHuellaEventoYProcedenciaHumana() throws Exception {
        var capturaInicial = borrador();
        long id = capturaInicial.idBorrador();
        var asistida = new Procedencia("API", "KAIROS", null, null, "conversacion", "turno", "mensaje",
                null, "preparar_actuacion_captura");
        var propuesta = actuaciones.prepararCaptura(actor, id, sesion, asistida);
        var preparada = new Preparada(id, propuesta, actuaciones.revisar(actor, propuesta.referencia(), sesion));
        assertEquals("EXITO", confirmar(preparada, "evento_humano").estado());
        var registro = jdbc.queryForMap("select version_aprobada,huella,aprobacion,procedencia from operacion_herramienta where referencia=?",
                propuesta.referencia());
        assertEquals(preparada.revision().propuesta().version(), ((Number) registro.get("version_aprobada")).longValue());
        var aprobacion = json.readTree((String) registro.get("aprobacion"));
        assertEquals("evento_humano", aprobacion.path("eventoHumano").asText());
        assertEquals(preparada.revision().propuesta().version(), aprobacion.path("version").asLong());
        assertEquals(registro.get("huella"), aprobacion.path("huella").asText());
        assertEquals("SPA", aprobacion.path("procedencia").path("canal").asText());
        assertFalse(aprobacion.path("procedencia").hasNonNull("agente"));
        assertEquals("KAIROS", json.readTree((String) registro.get("procedencia")).path("agente").asText());
    }

    @Test void confirmarConcurrentementeLaMismaActuacionSoloProduceUnAlta() throws Exception {
        var preparada = preparada();
        var antes = efectos();
        var listas = new CountDownLatch(2);
        var iniciar = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            Callable<String> accion = () -> {
                listas.countDown();
                assertTrue(iniciar.await(10, TimeUnit.SECONDS));
                return confirmar(preparada, "mismo_evento").estado();
            };
            var primera = ejecutor.submit(accion);
            var segunda = ejecutor.submit(accion);
            assertTrue(listas.await(10, TimeUnit.SECONDS));
            iniciar.countDown();
            for (String resultado : List.of(primera.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS))) {
                assertTrue(Set.of("EXITO", "EN_VERIFICACION").contains(resultado), resultado);
            }
        }
        assertEquals("EXITO", actuaciones.consultar(actor, preparada.propuesta().referencia(), sesion).estado());
        unAltaMas(antes);
    }

    @Test void dosPropuestasNoDuplicanLaMismaCaptura() {
        var primera = preparada();
        var segundaPropuesta = proponer(primera.borrador());
        var segunda = new Preparada(primera.borrador(), segundaPropuesta,
                actuaciones.revisar(actor, segundaPropuesta.referencia(), sesion));
        var antes = efectos();
        assertEquals("EXITO", confirmar(primera, "evento_uno").estado());
        assertThrows(ReglaNegocioException.class, () -> confirmar(segunda, "evento_dos"));
        unAltaMas(antes);
    }

    @Test void commitRealConRespuestaPerdidaSeReconciliaSinOtraEscritura() {
        var preparada = preparada();
        var antes = efectos();
        var perdido = new AtomicBoolean();
        doAnswer(invocacion -> {
            TransactionStatus tx = invocacion.getArgument(0);
            boolean confirmando = tx.isNewTransaction() && "X".equals(jdbc.queryForObject(
                    "select estado from operacion_herramienta where referencia=?", String.class, preparada.propuesta().referencia()));
            Object respuesta = invocacion.callRealMethod();
            if (confirmando && perdido.compareAndSet(false, true)) {
                throw new TransactionSystemException("Fixture: commit confirmado y acuse perdido");
            }
            return respuesta;
        }).when(transacciones).commit(any());
        try {
            var resultado = confirmar(preparada, "evento");
            assertTrue(perdido.get(), "La inyección ocurre después de un commit real, no antes de guardar");
            assertEquals("EXITO", resultado.estado());
            unAltaMas(antes);
            assertEquals(resultado.resultado(), actuaciones.consultar(actor, preparada.propuesta().referencia(), sesion).resultado());
        } finally { reset(transacciones); }
    }

    @Test void documentoPersistidoAlteradoNoPuedePresentarseConHuellaDelOriginal() {
        var preparada = preparada();
        var antes = efectos();
        jdbc.update("update operacion_herramienta set contenido=? where referencia=?",
                "{\"efecto\":\"CONTENIDO_ALTERADO\"}", preparada.propuesta().referencia());
        assertThrows(IllegalStateException.class, () -> actuaciones.revisar(actor, preparada.propuesta().referencia(), sesion));
        assertEquals(antes, efectos());
    }
    private ContextoSesion sesionDe(Actor titular) {
        return ContextoDeActuacion.de(com.controllocal.integracion.soporte.SesionesDePrueba.abrir(jdbc,contextos,titular));
    }
    private Actor otroAgenteDelTenant() {
        var fila=jdbc.queryForMap("""
            select r.organizacion_id,r.id_persona,r.id_persona_rol from persona_rol r
            join usuario_organizacion u on u.organizacion_id=r.organizacion_id and u.id_persona=r.id_persona
            join persona p on p.id_persona=r.id_persona and p.organizacion_id=r.organizacion_id and p.estado='A'
            join cuenta_acceso c on c.id_cuenta_acceso=u.id_usuario and c.estado='A' and c.correo_confirmado_en is not null
            where r.organizacion_id=? and r.tipo_rol='AGENTE' and r.id_persona_rol<>?
              and r.vigencia_hasta is null and u.estado='A' and u.rol='AGENTE'
              and r.vigencia_desde<=(clock_timestamp() at time zone 'America/Lima')::date
              and not c.debe_cambiar_contrasena and not c.debe_enrolar_mfa
            order by r.id_persona_rol limit 1
            """,actor.idOrganizacion(),actor.idRolOperativo());
        return new Actor(((Number)fila.get("organizacion_id")).longValue(),((Number)fila.get("id_persona")).longValue(),
                ((Number)fila.get("id_persona_rol")).longValue(),Actor.AGENTE);
    }
    private Actor agenteDeOtroTenant() {
        String ref=UUID.randomUUID().toString().substring(0,16);
        long org=jdbc.queryForObject("insert into organizacion(codigo,nombre,estado,modalidad) values(?,?,'A','INDIVIDUAL') returning id_organizacion",Long.class,
                "ACT-"+ref,"Espacio independiente ajeno de actuaciones");
        long persona=jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social) values(?,'Profesional independiente ajeno de actuaciones') returning id_persona",Long.class,org);
        long rol=jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,'AGENTE',(clock_timestamp() at time zone 'America/Lima')::date) returning id_persona_rol",Long.class,org,persona);
        jdbc.update("insert into detalle_agente(organizacion_id,id_persona_rol,codigo_agente,fecha_ingreso,estado_operativo) values(?,?,?,(clock_timestamp() at time zone 'America/Lima')::date,'D')",org,rol,"ACT-"+ref);
        return new Actor(org,persona,rol,Actor.AGENTE);
    }

    private static java.util.Map<String,MotorDeCaptura.Respuesta> respuestas(java.util.Map<String,String> datos) {
        var resultado=new java.util.LinkedHashMap<String,MotorDeCaptura.Respuesta>();
        datos.forEach((clave,valor)->resultado.put(clave,MotorDeCaptura.Respuesta.conocida(valor)));
        return resultado;
    }
}
