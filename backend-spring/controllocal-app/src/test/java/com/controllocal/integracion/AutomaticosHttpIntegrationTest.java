package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.asistencia.brox.ClienteBrox;
import com.controllocal.asistencia.brox.ErrorBrox;
import com.controllocal.asistencia.brox.SesionBrox;
import com.controllocal.asistencia.brox.Traza;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.integracion.soporte.OrigenesDePrueba;
import com.controllocal.integracion.soporte.ComunicacionesDePrueba;
import com.controllocal.integracion.soporte.SesionesComercialesDePrueba;
import com.controllocal.integracion.soporte.EvidenciaC4DePrueba;
import com.controllocal.service.Actor;
import com.controllocal.service.PropietarioService;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ComandosAutomaticosService;
import com.controllocal.service.actuacion.ComandosAutomaticosService.CorrelacionAutomatico;
import com.controllocal.service.capacidades.RegistroDeCapacidades;
import com.controllocal.service.captura.GuionRegistroPropiedad;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.seguridad.SesionDeRequest;
import com.controllocal.web.seguridad.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
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
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-K-07/RF-K-029/030 por la entrada HTTP real y PostgreSQL. Intérprete,
 * orquestador, puerto interno y casos canónicos son reales. El principal ya
 * resuelto es un fixture; sin filtros no se certifica el login por esta suite.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
class AutomaticosHttpIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p) { BaseDeDatosDePruebas.registrar(p); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.controllocal.service.ContextosService contextos;
    @Autowired ClienteBrox puerto;
    @Autowired MotorDeCaptura captura;
    @Autowired PropietarioService propietarios;
    @Autowired com.controllocal.service.ClienteService clientes;
    @Autowired com.controllocal.service.CuentaService cuentas;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired com.controllocal.service.soporte.CifradoSecretos cifrado;
    @Autowired com.controllocal.persistence.repositorio.FactorAutenticacionRepository factores;
    @MockitoSpyBean ComandosAutomaticosService automaticos;
    @MockitoSpyBean ActuacionesService actuaciones;
    @MockitoSpyBean RegistroDeCapacidades registro;
    private Actor actor;
    private SesionBrox sesion;
    private long cliente;
    private OrigenesDePrueba origenes;
    private OrigenesDePrueba.Equipo equipo;
    private SesionesComercialesDePrueba sesiones;

    @BeforeEach void preparar(TestInfo info) {
        EvidenciaC4DePrueba.iniciar(info);
        reset(automaticos, actuaciones, registro);
        origenes=new OrigenesDePrueba(jdbc,transacciones,captura,contextos);
        equipo=origenes.empresa();actor=equipo.agente();
        sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
        establecerSesion(actor);
        String documento=Long.toString(10_000_000L+Math.floorMod(System.nanoTime(),80_000_000L));
        cliente=clientes.registrar(new com.controllocal.service.ClienteService.DatosCliente("N","D",documento,
                "Cliente TEST HTTP AUTO","900000001",null,"Comercio",true,true,null),actor).id();
        assertTrue(cliente>0);
        EvidenciaC4DePrueba.actor(actor);
        EvidenciaC4DePrueba.recurso(actor,"CLIENTE",cliente,null);
    }

    @AfterEach void limpiar() {
        reset(automaticos, actuaciones, registro);
        SecurityContextHolder.clearContext();
        EvidenciaC4DePrueba.limpiar();
    }

    private void establecerSesion(Actor identidad) {
        var vigente=sesiones.de(identidad);
        var acceso=contextos.exigirContexto(vigente.referenciaSesion(),vigente.revisionContexto(),identidad);
        var principal=new SesionDeRequest(new TokenService("").de(acceso),acceso);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, AuthorityUtils.createAuthorityList("ROLE_" + identidad.rolEfectivo())));
        var contexto = ContextoDeActuacion.de(principal);
        sesion = new SesionBrox(identidad.idOrganizacion(), identidad.idPersona(), identidad.idRolOperativo(),
                identidad.rolEfectivo(), contexto.identidad(), contexto.expiraEnEpochSeconds());
    }

    private Map<String, Object> turno(String texto, Long idBorrador, Integer versionBorrador) {
        var entrada = new LinkedHashMap<String, Object>();
        entrada.put("canal", "API");
        entrada.put("conversacionId", "http-auto-" + UUID.randomUUID());
        entrada.put("turnoId", "turno-" + UUID.randomUUID());
        entrada.put("mensajeId", "mensaje-" + UUID.randomUUID());
        entrada.put("texto", texto);
        if (idBorrador != null) {
            assertNotNull(versionBorrador, "El fixture debe enviar la versión observada, sin releerla al emitir.");
            entrada.put("idBorrador", idBorrador);
            entrada.put("versionBorrador", versionBorrador);
        } else assertNull(versionBorrador);
        return entrada;
    }

    private String textoInteraccion() throws Exception {
        return "registrar interaccion "+json.writeValueAsString(ComunicacionesDePrueba
                .llamadaSalienteSinRespuesta(cliente,"Llamada HTTP TEST declarada explícitamente"));
    }

    private JsonNode enviar(Map<String, Object> entrada) throws Exception {
        var respuesta = mvc.perform(post("/kairos/turnos").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(entrada)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var salida=json.readTree(respuesta);
        EvidenciaC4DePrueba.estado("HTTP_TURNO",com.controllocal.web.seguridad.SesionActual.actor(),salida.path("desenlace").asText());
        return salida;
    }

    private JsonNode consultar(String referencia) throws Exception {
        var entrada = turno("", null, null);
        entrada.put("referenciaActuacion", referencia);
        return enviar(entrada);
    }

    private static String referencia(JsonNode respuesta, String capacidad) {
        var operacion = respuesta.path("resultado").path("automatico");
        assertEquals(capacidad, operacion.path("capacidad").asText());
        assertEquals(ComandosAutomaticosService.VERSION, operacion.path("version").asInt());
        String ref = operacion.path("referencia").asText();
        assertTrue(ref.matches("auto:" + capacidad + ":[a-f0-9]{64}"), ref);
        return ref;
    }

    private static void sinDato(JsonNode valor) {
        assertTrue(valor.isMissingNode() || valor.isNull(), valor.toString());
    }

    private Map<String, Long> efectos() {
        var conteos = new LinkedHashMap<String, Long>();
        for (String tabla : List.of("persona", "persona_rol", "interaccion_comercial", "interaccion_vinculo", "interaccion_version", "seguimiento_prospeccion", "contacto_autoria_contextual", "borrador_captura",
                "propiedad", "captacion", "comando_idempotente", "evento_dominio", "operacion_herramienta")) {
            conteos.put(tabla, jdbc.queryForObject("select count(*) from " + tabla, Long.class));
        }
        return conteos;
    }

    private MotorDeCaptura.EstadoCaptura borradorListo() {
        var datos = new LinkedHashMap<String, String>();
        datos.put(GuionRegistroPropiedad.TIPO_PROPIEDAD, "DEPARTAMENTO");
        datos.put(GuionRegistroPropiedad.DIRECCION, "Av. Propuesta HTTP " + UUID.randomUUID());
        datos.put(GuionRegistroPropiedad.DISTRITO, "Miraflores");
        datos.put("metraje_total", "96");
        datos.put("dormitorios", "3");
        datos.put("piso", "7");
        datos.put("interiorUnidad", "701");
        var estado = captura.avanzar(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD, null, null, respuestas(datos), null), Procedencia.deLaPantalla(), actor);
        assertTrue(estado.listoParaEjecutar(), () -> "Fixture incompleto según Core: " + estado.faltante());
        return estado;
    }

    @Test void propietarioConDtoExplicitoConservaSnapshotYSeConsultaPorHttpTrasEditarDatosActuales() throws Exception {
        String documento = "HTTP-" + UUID.randomUUID().toString().substring(0, 20);
        var datos = new LinkedHashMap<String, String>();
        datos.put("tipoPersona", "N");
        datos.put("tipoDocumento", "P");
        datos.put("numeroDocumento", documento);
        datos.put("nombre", "Propietario original HTTP");
        datos.put("telefono", "900000001");
        datos.put("consentimientoUsoDato", "true");
        var traza = new Traza("API", "KAIROS", "determinista", "0.1.0", "dto-" + UUID.randomUUID(),
                "turno-propietario", "mensaje-propietario", "Formulario autorizado de propietario", "registrar_propietario");
        // El consentimiento es explícito en la superficie estructurada actual.
        // El perfil lingüístico no lo extrae ni lo supone a partir de una frase.
        var original = puerto.registrarPropietario(sesion, datos, traza);
        propietarios.actualizar(original.resultado().id(), new PropietarioService.DatosPropietario("N", "P",
                documento, "Nombre posterior HTTP", "900000009", null, true, null), actor);
        var trasEdicion = efectos();

        assertEquals(original, puerto.registrarPropietario(sesion, datos, traza));
        var consulta = consultar(original.operacion().referencia());
        assertEquals("RESPONDIDO", consulta.path("desenlace").asText());
        var resultado = consulta.path("resultado").path("actuacion");
        assertEquals("EXITO", resultado.path("estado").asText());
        assertEquals(json.readTree(json.writeValueAsString(original.resultado())),
                resultado.path("resultado").path("persona"));
        assertEquals(original.resultado().id().longValue(), resultado.path("resultado").path("persona").path("id").asLong());
        assertEquals("Propietario original HTTP", resultado.path("resultado").path("persona").path("nombre").asText());
        assertEquals("900000001", resultado.path("resultado").path("persona").path("telefono").asText());
        assertEquals("Nombre posterior HTTP", propietarios.obtener(original.resultado().id(), actor).nombre());

        var otroContenido = new LinkedHashMap<>(datos);
        otroContenido.put("nombre", "Otra solicitud con la misma identidad");
        var conflicto = assertThrows(ErrorBrox.class, () -> puerto.registrarPropietario(sesion, otroContenido, traza));
        assertEquals(ErrorBrox.Tipo.SOLICITUD_RECHAZADA, conflicto.tipo());
        assertEquals("CONFLICTO", conflicto.codigo());
        assertEquals(trasEdicion, efectos());
    }

    @Test void fraseSinConsentimientoNoInventaLaAutorizacionNiPersisteUnAltaParcial() throws Exception {
        var antes = efectos();
        String documento = Integer.toString(10000000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(90000000));
        var respuesta = enviar(turno("registra al propietario Prueba HTTP con DNI " + documento
                + " y telefono 987654321", null, null));
        assertEquals("RECHAZADO", respuesta.path("desenlace").asText());
        assertEquals("SOLICITUD_INVALIDA", respuesta.path("motivo").asText());
        sinDato(respuesta.path("resultado").path("automatico"));
        sinDato(respuesta.path("resultado").path("persona"));
        assertEquals(antes, efectos());
    }

    @Test void repetirInteraccionPorHttpDevuelveLaMismaReferenciaYUnSoloHecho() throws Exception {
        var entrada = turno(textoInteraccion(), null, null);
        var antes = efectos();
        var primera = enviar(entrada);
        assertEquals("EJECUTADO", primera.path("desenlace").asText());
        String ref = referencia(primera, "registrar_interaccion");
        var original=primera.path("resultado").path("comercial").path("datos");
        long id = original.path("id").asLong();
        assertTrue(id > 0);
        assertTrue(original.path("version").canConvertToLong());
        assertEquals(com.controllocal.service.actividad.CatalogoInteracciones.VERSION,original.path("versionDiccionario").asText());
        assertEquals("SIN_RESPUESTA",original.path("datos").path("resultado").asText());
        EvidenciaC4DePrueba.recurso(actor,"INTERACCION",id,original.path("version").longValue());
        var trasCommit = efectos();
        assertEquals(antes.get("interaccion_comercial") + 1, trasCommit.get("interaccion_comercial"));
        assertEquals(antes.get("interaccion_version") + 1, trasCommit.get("interaccion_version"));
        assertEquals(antes.get("comando_idempotente") + 1, trasCommit.get("comando_idempotente"));

        var repetida = enviar(entrada);
        assertEquals("EJECUTADO", repetida.path("desenlace").asText());
        assertEquals(ref, referencia(repetida, "registrar_interaccion"));
        assertEquals(original, repetida.path("resultado").path("comercial").path("datos"));
        var consulta = consultar(ref);
        assertEquals("EXITO", consulta.path("resultado").path("actuacion").path("estado").asText());
        assertEquals(original,consulta.path("resultado").path("comercial").path("datos"));
        assertEquals(id,consulta.path("resultado").path("comercial").path("datos").path("id").asLong());
        sinDato(primera.path("resultado").path("interaccion"));
        assertEquals(trasCommit, efectos());
    }

    @Test void falloAntesDelCommitRevierteHechoVinculosHistoriaComandoYEventoSinInventarExito() throws Exception {
        var antes=efectos();var seAlcanzoElResultado=new AtomicBoolean();
        ComandosAutomaticosService puerta=AopTestUtils.getUltimateTargetObject(automaticos);
        // El target devuelve dentro de la transacción AOP aún abierta: aquí el fallo impide el commit.
        doAnswer(invocacion->{
            invocacion.callRealMethod();seAlcanzoElResultado.set(true);
            throw new IllegalStateException("Fixture: fallo antes del commit del AUTO");
        }).when(puerta).registrarInteraccion(any(com.controllocal.service.InteraccionService.DatosInteraccion.class),
                eq(actor),any(ActuacionesService.ContextoSesion.class),any(CorrelacionAutomatico.class),any(Procedencia.class));
        var respuesta=enviar(turno(textoInteraccion(),null,null));
        assertTrue(seAlcanzoElResultado.get());
        assertEquals("RESULTADO_INCIERTO",respuesta.path("desenlace").asText());
        String ref=referencia(respuesta,"registrar_interaccion");
        sinDato(respuesta.path("resultado").path("comercial"));
        assertEquals(antes,efectos(),"El error antes del commit revierte el agregado y su rastro transaccional.");
        var consulta=consultar(ref);
        assertEquals("RESULTADO_INCIERTO",consulta.path("desenlace").asText());
        var actuacion=consulta.path("resultado").path("actuacion");
        assertEquals("EN_VERIFICACION",actuacion.path("estado").asText());
        sinDato(consulta.path("resultado").path("comercial"));
        assertEquals(antes,efectos());
        verify(puerta,times(1)).registrarInteraccion(any(),eq(actor),any(),any(),any());
    }

    @Test void sesionRevocadaNoEntregaElResultadoYUnaNuevaSesionValidaPermiteConsultarYActuar() throws Exception {
        var original=enviar(turno(textoInteraccion(),null,null));
        assertEquals("EJECUTADO",original.path("desenlace").asText());
        String ref=referencia(original,"registrar_interaccion");
        var cuerpo=original.path("resultado").path("comercial").path("datos");
        var actual=ContextoDeActuacion.de(com.controllocal.web.seguridad.SesionActual.sesion());
        contextos.cerrar(actual.referenciaSesion(),actual.revisionContexto());
        var trasRevocar=efectos();
        var denegada=consultar(ref);
        assertEquals("SIN_PERMISO",denegada.path("desenlace").asText());
        sinDato(denegada.path("resultado").path("actuacion"));
        sinDato(denegada.path("resultado").path("comercial"));
        assertEquals(trasRevocar,efectos());

        // La sesión cerrada no se reactiva ni se altera por SQL. Se prueba identidad de TEST y selección actuales.
        sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
        establecerSesion(actor);
        var trasNuevaSesion=efectos();
        var recuperada=consultar(ref);
        assertEquals("EXITO",recuperada.path("resultado").path("actuacion").path("estado").asText());
        assertEquals(cuerpo,recuperada.path("resultado").path("comercial").path("datos"));
        assertEquals(trasNuevaSesion,efectos());
        var siguiente=enviar(turno(textoInteraccion(),null,null));
        assertEquals("EJECUTADO",siguiente.path("desenlace").asText());
        assertNotEquals(cuerpo.path("id").asLong(),siguiente.path("resultado").path("comercial").path("datos").path("id").asLong());
        assertEquals(trasNuevaSesion.get("interaccion_comercial")+1,efectos().get("interaccion_comercial"));
        verify(automaticos,times(2)).registrarInteraccion(any(),eq(actor),any(),any(),any());
    }

    @Test void aperturaYRespuestaRepetidasPorHttpNoDuplicanNiPisanDatosPosteriores() throws Exception {
        var primeraEntrada = turno("registra un departamento en Surco", null, null);
        var antes = efectos();
        var apertura = enviar(primeraEntrada);
        String refApertura = referencia(apertura, "abrir_sesion_captura");
        long id = apertura.path("resultado").path("captura").path("idBorrador").asLong();
        assertTrue(id > 0);
        assertTrue(apertura.path("resultado").path("captura").path("version").canConvertToInt());
        var respuestaEntrada = turno("registra un departamento en Miraflores", id, apertura.path("resultado").path("captura").path("version").intValue());
        var respuesta = enviar(respuestaEntrada);
        assertTrue(respuesta.path("resultado").path("captura").path("version").canConvertToInt());
        String refRespuesta = referencia(respuesta, "responder_captura");
        enviar(turno("registra un departamento en San Isidro", id, respuesta.path("resultado").path("captura").path("version").intValue()));
        var trasAvance = efectos();
        assertEquals(antes.get("borrador_captura") + 1, trasAvance.get("borrador_captura"));
        assertEquals("San Isidro", captura.consultar(id, actor).conocido().get("distrito"));

        var aperturaRepetida = enviar(primeraEntrada);
        var respuestaRepetida = enviar(respuestaEntrada);
        assertEquals(refApertura, referencia(aperturaRepetida, "abrir_sesion_captura"));
        assertEquals(refRespuesta, referencia(respuestaRepetida, "responder_captura"));
        assertEquals(apertura.path("resultado").path("captura"), aperturaRepetida.path("resultado").path("captura"));
        assertEquals(respuesta.path("resultado").path("captura"), respuestaRepetida.path("resultado").path("captura"));
        assertEquals("San Isidro", captura.consultar(id, actor).conocido().get("distrito"));

        var otraEntrada = new LinkedHashMap<>(primeraEntrada);
        otraEntrada.put("texto", "registra un departamento en Barranco");
        var conflicto = enviar(otraEntrada);
        assertEquals("RECHAZADO", conflicto.path("desenlace").asText());
        assertEquals("CONFLICTO", conflicto.path("motivo").asText());
        assertEquals(trasAvance, efectos());
    }

    @Test void repetirCapturaListaConservaPropuestaOriginalAunqueElBorradorCambieDespues() throws Exception {
        var capturaInicial = borradorListo();
        long id = capturaInicial.idBorrador();
        var entrada = turno("registra un departamento en Miraflores", id, capturaInicial.version());
        var antes = efectos();
        var original = enviar(entrada);
        assertEquals("PROPUESTA", original.path("desenlace").asText());
        String refAuto = referencia(original, "responder_captura");
        var propuestaOriginal = original.path("resultado").path("actuacion");
        assertEquals("PREPARADA", propuestaOriginal.path("estado").asText());
        assertFalse(propuestaOriginal.path("referencia").asText().isBlank());
        var trasPropuesta = efectos();
        assertEquals(antes.get("comando_idempotente") + 1, trasPropuesta.get("comando_idempotente"));
        assertEquals(antes.get("operacion_herramienta") + 1, trasPropuesta.get("operacion_herramienta"));
        assertEquals(antes.get("propiedad"), trasPropuesta.get("propiedad"));

        var repetida = enviar(entrada);
        assertEquals("PROPUESTA", repetida.path("desenlace").asText());
        assertEquals(refAuto, referencia(repetida, "responder_captura"));
        assertEquals(propuestaOriginal, repetida.path("resultado").path("actuacion"));
        assertEquals(trasPropuesta, efectos());

        String direccionPosterior = "Av. Corrección posterior " + UUID.randomUUID();
        captura.avanzar(new MotorDeCaptura.Avance(null, id, original.path("resultado").path("captura").path("version").intValue(), respuestas(Map.of(GuionRegistroPropiedad.DIRECCION, direccionPosterior)), null), Procedencia.deLaPantalla(), actor);
        var trasEdicion = efectos();
        var antigua = enviar(entrada);
        assertEquals("PROPUESTA", antigua.path("desenlace").asText());
        assertEquals(refAuto, referencia(antigua, "responder_captura"));
        assertEquals(original.path("resultado").path("captura"), antigua.path("resultado").path("captura"));
        assertEquals(propuestaOriginal, antigua.path("resultado").path("actuacion"));
        assertEquals(direccionPosterior, captura.consultar(id, actor).conocido().get(GuionRegistroPropiedad.DIRECCION));
        assertEquals(trasEdicion, efectos());
    }

    @Test void falloAlPrepararDespuesDelAvanceConfirmadoConservaCapturaYReferenciaAuto() throws Exception {
        var capturaInicial = borradorListo();
        long id = capturaInicial.idBorrador();
        var antes = efectos();
        ActuacionesService puerta = AopTestUtils.getUltimateTargetObject(actuaciones);
        doThrow(new IllegalStateException("Fixture: preparación posterior no disponible")).when(puerta)
                .prepararCaptura(eq(actor), eq(id), any(ActuacionesService.ContextoSesion.class),
                        any(Procedencia.class), any(CorrelacionAutomatico.class), anyMap());

        var respuesta = enviar(turno("registra un departamento en Miraflores", id, capturaInicial.version()));
        assertEquals("FALLO_TECNICO", respuesta.path("desenlace").asText());
        assertEquals("FALLO_TECNICO", respuesta.path("motivo").asText());
        String ref = referencia(respuesta, "responder_captura");
        var capturaConfirmada = respuesta.path("resultado").path("captura");
        assertEquals(id, capturaConfirmada.path("idBorrador").asLong());
        assertEquals("Miraflores", capturaConfirmada.path("conocido").path("distrito").asText());
        assertTrue(capturaConfirmada.path("listoParaEjecutar").asBoolean());
        var trasAvance = efectos();
        assertEquals(antes.get("comando_idempotente") + 1, trasAvance.get("comando_idempotente"));
        assertEquals(antes.get("operacion_herramienta"), trasAvance.get("operacion_herramienta"));
        assertEquals(antes.get("propiedad"), trasAvance.get("propiedad"));

        var consulta = consultar(ref).path("resultado").path("actuacion");
        assertEquals("EXITO", consulta.path("estado").asText());
        assertEquals(id, consulta.path("resultado").path("captura").path("idBorrador").asLong());
        verify(automaticos, times(1)).avanzarCaptura(argThat(a -> a != null && java.util.Objects.equals(a.idBorrador(),id)), eq(actor),
                any(CorrelacionAutomatico.class), any(Procedencia.class));
        assertEquals(trasAvance, efectos());
    }

    @Test void repetirTurnoTrasEjecutarSuActuacionNoPresentaUnaNuevaPropuestaPreparada() throws Exception {
        var capturaInicial = borradorListo();
        long id = capturaInicial.idBorrador();
        var entrada = turno("registra un departamento en Miraflores", id, capturaInicial.version());
        var original = enviar(entrada);
        assertEquals("PROPUESTA", original.path("desenlace").asText());
        String refAuto = referencia(original, "responder_captura");
        String refActuacion = original.path("resultado").path("actuacion").path("referencia").asText();
        var contexto = ContextoDeActuacion.de(com.controllocal.web.seguridad.SesionActual.sesion());
        var revision = actuaciones.revisar(actor, refActuacion, contexto);
        var confirmacion = new ActuacionesService.Confirmacion(revision.propuesta().version(),
                revision.propuesta().huella(), revision.pruebaRevision(), "evento_http_confirmado");
        assertEquals("EXITO", actuaciones.confirmar(actor, refActuacion, contexto, confirmacion,
                Procedencia.deLaPantalla()).estado());
        var trasEjecucion = efectos();

        var repetida = enviar(entrada);
        assertEquals("RECHAZADO", repetida.path("desenlace").asText());
        assertEquals("CONFLICTO", repetida.path("motivo").asText());
        assertEquals(refAuto, referencia(repetida, "responder_captura"));
        assertEquals(original.path("resultado").path("captura"), repetida.path("resultado").path("captura"));
        sinDato(repetida.path("resultado").path("actuacion"));
        var consulta = consultar(refActuacion).path("resultado").path("actuacion");
        assertEquals("EXITO", consulta.path("estado").asText());
        assertEquals(refActuacion, consulta.path("referencia").asText());
        assertEquals(trasEjecucion, efectos());
    }

    @Test void cambioConfirmadoEntreAvanceYPrimeraPreparacionNoMezclaInstantaneas() throws Exception {
        var capturaInicial = borradorListo();
        long id = capturaInicial.idBorrador();
        String direccionPosterior = "Av. Otra transacción " + UUID.randomUUID();
        var antes = efectos();
        var intercalada = new AtomicBoolean();
        var txIndependiente = new TransactionTemplate(transacciones);
        txIndependiente.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        ComandosAutomaticosService puerta = AopTestUtils.getUltimateTargetObject(automaticos);
        doAnswer(invocacion -> {
            Object original = txIndependiente.execute(s -> {
                try { return invocacion.callRealMethod(); }
                catch (RuntimeException error) { throw error; }
                catch (Throwable error) { throw new AssertionError(error); }
            });
            // El avance y la corrección son commits distintos anteriores a
            // la primera preparación. El snapshot AUTO original ya es durable.
            int versionConfirmada=((ComandosAutomaticosService.ResultadoAutomatico<MotorDeCaptura.EstadoCaptura>)original).resultado().version();
            txIndependiente.executeWithoutResult(s -> captura.avanzar(new MotorDeCaptura.Avance(null, id, versionConfirmada, respuestas(Map.of(GuionRegistroPropiedad.DIRECCION, direccionPosterior)), null), Procedencia.deLaPantalla(), actor));
            intercalada.set(true);
            return original;
        }).when(puerta).avanzarCaptura(argThat(a -> a != null && java.util.Objects.equals(a.idBorrador(),id)), eq(actor),
                any(CorrelacionAutomatico.class), any(Procedencia.class));

        var respuesta = enviar(turno("registra un departamento en Miraflores", id, capturaInicial.version()));
        assertTrue(intercalada.get());
        assertEquals("RECHAZADO", respuesta.path("desenlace").asText());
        assertEquals("CONFLICTO", respuesta.path("motivo").asText());
        String ref = referencia(respuesta, "responder_captura");
        sinDato(respuesta.path("resultado").path("actuacion"));
        assertNotEquals(direccionPosterior, respuesta.path("resultado").path("captura")
                .path("conocido").path(GuionRegistroPropiedad.DIRECCION).asText());
        assertEquals(direccionPosterior, captura.consultar(id, actor).conocido().get(GuionRegistroPropiedad.DIRECCION));
        var trasCarrera = efectos();
        assertEquals(antes.get("comando_idempotente") + 1, trasCarrera.get("comando_idempotente"));
        assertEquals(antes.get("operacion_herramienta"), trasCarrera.get("operacion_herramienta"));
        assertEquals(antes.get("propiedad"), trasCarrera.get("propiedad"));
        assertEquals("EXITO", consultar(ref).path("resultado").path("actuacion").path("estado").asText());
        verify(puerta, times(1)).avanzarCaptura(argThat(a -> a != null && java.util.Objects.equals(a.idBorrador(),id)), eq(actor),
                any(CorrelacionAutomatico.class), any(Procedencia.class));
        assertEquals(trasCarrera, efectos());
    }

    @Test void commitConfirmadoYAcusePerdidoRetornanReferenciaYLaConsultaHttpNoReemite() throws Exception {
        var antes = efectos();
        var perdida = new AtomicBoolean();
        var txIndependiente = new TransactionTemplate(transacciones);
        txIndependiente.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        ComandosAutomaticosService puerta = AopTestUtils.getUltimateTargetObject(automaticos);
        // El Spy envuelve el target de @Transactional. Lanzar justo tras
        // callRealMethod aún precedería al commit exterior y probaría rollback.
        // Se confirma una transacción real independiente antes de perder el
        // acuse: la exterior queda vacía y su rollback no deshace el hecho.
        doAnswer(invocacion -> {
            txIndependiente.execute(s -> {
                try { return invocacion.callRealMethod(); }
                catch (RuntimeException error) { throw error; }
                catch (Throwable error) { throw new AssertionError(error); }
            });
            perdida.set(true);
            throw new IllegalStateException("Fixture: commit confirmado y acuse perdido");
        }).when(puerta).avanzarCaptura(argThat(a -> a != null && a.idBorrador()==null), eq(actor),
                any(CorrelacionAutomatico.class), any(Procedencia.class));

        var respuesta = enviar(turno("registra un departamento en Santiago de Surco", null, null));
        assertTrue(perdida.get());
        assertEquals("RESULTADO_INCIERTO", respuesta.path("desenlace").asText());
        assertEquals("RESULTADO_INCIERTO", respuesta.path("motivo").asText());
        String ref = referencia(respuesta, "abrir_sesion_captura");
        var trasCommit = efectos();
        assertEquals(antes.get("borrador_captura") + 1, trasCommit.get("borrador_captura"));
        assertEquals(antes.get("comando_idempotente") + 1, trasCommit.get("comando_idempotente"));
        assertEquals(antes.get("propiedad"), trasCommit.get("propiedad"));

        var consulta = consultar(ref).path("resultado").path("actuacion");
        assertEquals("EXITO", consulta.path("estado").asText());
        assertEquals("Santiago de Surco", consulta.path("resultado").path("captura").path("conocido").path("distrito").asText());
        verify(puerta, times(1)).avanzarCaptura(argThat(a -> a != null && a.idBorrador()==null), eq(actor),
                any(CorrelacionAutomatico.class), any(Procedencia.class));
        assertEquals(trasCommit, efectos());
    }

    @ParameterizedTest @ValueSource(strings = {"ACTOR", "TENANT"})
    void unaReferenciaHttpAjenaNoEntregaElResultadoPrivadoNiProduceEfectos(String cambio) throws Exception {
        var primera = enviar(turno("registra un departamento en Surco", null, null));
        String ref = referencia(primera, "abrir_sesion_captura");
        Actor ajeno="ACTOR".equals(cambio)?equipo.colega():origenes.empresa().agente();
        establecerSesion(ajeno);
        // Cuenta, persona, rol y sesión ajenos forman el fixture; la consulta comienza después.
        var antes = efectos();

        var consulta = consultar(ref);
        assertEquals("RESULTADO_INCIERTO", consulta.path("desenlace").asText());
        var resultado = consulta.path("resultado").path("actuacion");
        assertEquals("EN_VERIFICACION", resultado.path("estado").asText());
        assertEquals("RESULTADO_NO_LOCALIZADO", resultado.path("motivo").asText());
        sinDato(resultado.path("resultado"));
        sinDato(resultado.path("propuesta"));
        assertEquals(antes, efectos());
    }

    @Test void capacidadDeshabilitadaNoSeConvierteEnIncertidumbreNiEmiteLaInteraccion() throws Exception {
        doReturn(false).when(registro).habilitadas(List.of("registrar_interaccion"));
        var antes = efectos();
        var respuesta = enviar(turno(textoInteraccion(), null, null));
        assertEquals("CAPACIDAD_DESHABILITADA", respuesta.path("desenlace").asText());
        sinDato(respuesta.path("resultado").path("automatico"));
        verify(automaticos, never()).registrarInteraccion(any(), any(), any(), any(), any());
        assertEquals(antes, efectos());
    }

    @Test void gobernarNoDaPermisoDeInteraccionNiDevuelveIncertidumbre() throws Exception {
        var gobierno=equipo.admin();
        establecerSesion(gobierno);

        var antes = efectos();
        var respuesta = enviar(turno(textoInteraccion(), null, null));
        assertEquals("SIN_PERMISO", respuesta.path("desenlace").asText());
        sinDato(respuesta.path("resultado").path("automatico"));
        verify(automaticos, never()).registrarInteraccion(any(), any(), any(), any(), any());
        assertEquals(antes, efectos());
    }

    private static java.util.Map<String,MotorDeCaptura.Respuesta> respuestas(java.util.Map<String,String> datos) {
        var resultado=new java.util.LinkedHashMap<String,MotorDeCaptura.Respuesta>();
        datos.forEach((clave,valor)->resultado.put(clave,MotorDeCaptura.Respuesta.conocida(valor)));
        return resultado;
    }
}
