package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.soporte.*;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.web.seguridad.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Propuestas/revisión/efecto reales. Principal TEST posterior a identidad; no certifica login/filtros. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters=false)
class OrigenFisicoHttpIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired UnidadRelacionadaService origen;
    @MockitoSpyBean ActuacionesService actuaciones;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired CuentaService cuentas;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    private OrigenesDePrueba banco;
    private OrigenesDePrueba.Equipo equipo;
    private long matriz,unidad;
    private ActuacionesService.ContextoSesion sesion;
    private UnidadRelacionadaService.DatosOrigen entrada;
    @BeforeEach void montar() {
        reset(actuaciones);
        doAnswer(invocacion->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "La descripción no retiene una transacción padre mientras consulta sus lectores.");
            return invocacion.callRealMethod();
        }).when(actuaciones).capacidadSobre(anyString(),any(Actor.class));
        banco=new OrigenesDePrueba(jdbc,gestor,captura,contextos);equipo=banco.empresa();
        sesion=banco.sesion(equipo.agente());como(equipo.agente(),sesion);
        matriz=banco.propiedad(equipo.agente(),"Matriz HTTP");unidad=banco.propiedad(equipo.agente(),"Unidad HTTP");
        entrada=banco.datos(matriz,unidad,equipo.agente(),origen);
    }
    @AfterEach void limpiar(){reset(actuaciones);SecurityContextHolder.clearContext();}
    private void como(Actor actor,ActuacionesService.ContextoSesion s) {
        var acceso=contextos.exigirContexto(s.referenciaSesion(),s.revisionContexto(),actor);
        var principal=new SesionDeRequest(new TokenService("").de(acceso),acceso);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,
                AuthorityUtils.createAuthorityList("ROLE_"+actor.rolEfectivo())));
    }
    private JsonNode preparar() throws Exception {
        return json.readTree(http.perform(post("/origen-fisico/propuestas").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(entrada))).andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidad").value("registrar_origen_fisico_unidad")).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode revisar(JsonNode propuesta) throws Exception {
        return json.readTree(http.perform(get("/actuaciones/{ref}/revision",propuesta.path("referencia").asText()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private Map<String,Object> confirmacion(JsonNode revision) {
        return Map.of("version",revision.path("propuesta").path("version").longValue(),
                "huella",revision.path("propuesta").path("huella").textValue(),
                "pruebaRevision",revision.path("pruebaRevision").textValue(),"eventoHumano","revision-fisica-test");
    }
    private long vinculos(){return jdbc.queryForObject("select count(*) from unidad_relacionada where organizacion_id=? and tipo_relacion='O'",Long.class,equipo.agente().idOrganizacion());}

    @Test void propuestaNoRegistraYLaRevisionExactaTieneUnSoloEfectoDurable() throws Exception {
        var p=preparar();String ref=p.path("referencia").textValue();assertEquals(0,vinculos());
        assertNull(jdbc.queryForObject("select id_borrador from operacion_herramienta where referencia=?",Long.class,ref));
        var revision=revisar(p);var aprobacion=confirmacion(revision);
        var falsa=new LinkedHashMap<>(aprobacion);falsa.put("huella","0".repeat(64));
        http.perform(post("/actuaciones/{ref}/confirmacion",ref).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(falsa)))
                .andExpect(status().isConflict());assertEquals(0,vinculos());
        var resultado=json.readTree(http.perform(post("/actuaciones/{ref}/confirmacion",ref).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(aprobacion))).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EXITO"))
                .andReturn().getResponse().getContentAsByteArray());
        var repetido=json.readTree(http.perform(post("/actuaciones/{ref}/confirmacion",ref).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(aprobacion))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(resultado.path("resultado"),repetido.path("resultado"));assertEquals(1,vinculos());
        var actual=origen.capacidadSobreOrigen(matriz,unidad,equipo.agente());
        assertEquals(entrada.versionMatrizEsperada()+1,actual.versionMatriz());assertEquals(entrada.versionUnidadEsperada()+1,actual.versionUnidad());
        assertEquals(1,jdbc.queryForObject("select count(*) from comando_idempotente where organizacion_id=? and idempotency_key=?",Integer.class,
                equipo.agente().idOrganizacion(),"actuacion:"+ref));
        http.perform(get("/origen-fisico/{id}",matriz)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void ambasVersionesSonExplicitasYUnaCorreccionInvalidaLaPropuesta() throws Exception {
        ObjectNode cuerpo=json.valueToTree(entrada);cuerpo.remove("versionMatrizEsperada");
        http.perform(post("/origen-fisico/propuestas").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cuerpo))).andExpect(status().isBadRequest());
        cuerpo=json.valueToTree(entrada);cuerpo.putNull("versionUnidadEsperada");
        http.perform(post("/origen-fisico/propuestas").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cuerpo))).andExpect(status().isBadRequest());
        var p=preparar();var r=revisar(p);
        propiedades.editar(matriz,new PropiedadUniversalService.ComandoEdicion("RF014-EDIT-"+UUID.randomUUID(),Procedencia.deLaPantalla(),
                "Matriz corregida después de revisar",null,null,List.of(),null,null).conVersion(entrada.versionMatrizEsperada()),equipo.agente());
        http.perform(post("/actuaciones/{ref}/confirmacion",p.path("referencia").textValue()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(confirmacion(r)))).andExpect(status().isConflict());assertEquals(0,vinculos());
    }
    @Test void lecturaVaciaYCorteConservanPermisoMientrasLaReferenciaAjenaNoRevelaDatos() throws Exception {
        http.perform(get("/origen-fisico/{id}",matriz).param("corte",Instant.now().toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        var p=preparar();var ajeno=banco.empresa();como(ajeno.agente(),banco.sesion(ajeno.agente()));
        http.perform(get("/origen-fisico/{id}",matriz)).andExpect(status().isNotFound());
        http.perform(post("/actuaciones/{ref}/confirmacion",p.path("referencia").textValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"huella\":\"falsa\",\"pruebaRevision\":\"falsa\",\"eventoHumano\":\"test\"}"))
                .andExpect(status().isNotFound());assertEquals(0,vinculos());
    }
    @Test void elDescriptorExigeAmbosExtremosYNoConfundeElEfectoDeLaActuacion() throws Exception {
        http.perform(get("/capacidades").param("idCore","registrar_origen_fisico_unidad").param("recurso","PROPIEDAD").param("referencia",Long.toString(matriz)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacidades[0].estado").value("CONTEXTO_REQUERIDO"));
        http.perform(get("/capacidades").param("idCore","registrar_origen_fisico_unidad").param("recurso","PROPIEDAD")
                .param("referencia",Long.toString(matriz)).param("referenciaRelacionada",Long.toString(unidad)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacidades[0].estado").value("HABILITADA"))
                .andExpect(jsonPath("$.capacidades[0].contexto.referenciaRelacionada").value(Long.toString(unidad)));
        var p=preparar();String ref=p.path("referencia").textValue();
        http.perform(get("/capacidades").param("idCore","confirmar_captura").param("recurso","ACTUACION").param("referencia",ref))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacidades[0].estado").value("DESHABILITADA"));
        http.perform(get("/capacidades").param("idCore","registrar_origen_fisico_unidad").param("recurso","ACTUACION").param("referencia",ref))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacidades[0].estado").value("HABILITADA"));
        assertEquals(0,vinculos());
    }
    @Test void gobiernoYBrokerNoPreparanElVinculoYBrokerSoloLeeSuAmbito() throws Exception {
        var sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
        como(equipo.admin(),sesiones.de(equipo.admin()));
        http.perform(post("/origen-fisico/propuestas").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(entrada))).andExpect(status().isForbidden());
        http.perform(get("/origen-fisico/{id}",matriz)).andExpect(status().isForbidden());
        como(equipo.broker(),sesiones.de(equipo.broker()));
        http.perform(post("/origen-fisico/propuestas").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(entrada))).andExpect(status().isForbidden());
        http.perform(get("/origen-fisico/{id}",matriz)).andExpect(status().isOk()).andExpect(content().json("[]"));assertEquals(0,vinculos());
    }
    @Test void otraSesionDeLaMismaCuentaNoHeredaLaRevisionHumana() throws Exception {
        var p=preparar();var revision=revisar(p);
        como(equipo.agente(),banco.sesion(equipo.agente()));
        http.perform(post("/actuaciones/{ref}/confirmacion",p.path("referencia").textValue()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(confirmacion(revision)))).andExpect(status().isForbidden());
        assertEquals(0,vinculos());
    }
    @Test void perderPermisoFisicoEnSoloLaUnidadImpideAplicarLaPropuesta() throws Exception {
        var p=preparar();var revision=revisar(p);
        var observado=propiedades.consultar(unidad,equipo.agente()).responsabilidad().idResponsable();
        var sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
        como(equipo.broker(),sesiones.de(equipo.broker()));
        propiedades.asignarResponsable(unidad,equipo.colega().idRolOperativo(),"Cambio físico TEST posterior a revisión",
                PropiedadUniversalService.ResponsableObservado.de(observado),equipo.broker());
        como(equipo.agente(),sesion);
        assertTrue(propiedades.capacidadSobre(matriz,null,equipo.agente()).puedeEditar());
        assertFalse(propiedades.capacidadSobre(unidad,null,equipo.agente()).puedeEditar());
        String ref=p.path("referencia").textValue();
        http.perform(get("/actuaciones/{ref}",ref)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PREPARADA"));
        http.perform(get("/capacidades").param("idCore","consultar_actuacion").param("recurso","ACTUACION").param("referencia",ref))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacidades[0].estado").value("HABILITADA"));
        http.perform(get("/capacidades").param("idCore","registrar_origen_fisico_unidad").param("recurso","ACTUACION").param("referencia",ref))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacidades[0].estado").value("PERMISO_INSUFICIENTE"));
        http.perform(post("/actuaciones/{ref}/confirmacion",p.path("referencia").textValue()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(confirmacion(revision)))).andExpect(status().isForbidden());
        assertEquals(0,vinculos());
    }
}
