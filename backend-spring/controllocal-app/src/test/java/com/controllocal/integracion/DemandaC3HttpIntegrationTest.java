package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.web.seguridad.SesionDeRequest;
import com.controllocal.web.seguridad.TokenService;
import com.fasterxml.jackson.databind.*;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Transporte real, CAS y revisión humana. Principal TEST posterior a identidad; no acredita login/filtros. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters=false)
class DemandaC3HttpIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    private OrigenesDePrueba banco;
    private OrigenesDePrueba.Equipo equipo;
    private ContextoSesion sesion;
    @BeforeEach void montar() {
        banco=new OrigenesDePrueba(jdbc,gestor,captura,contextos);equipo=banco.empresa();
        sesion=banco.sesion(equipo.agente());como(equipo.agente(),sesion);
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void altaYEdicionExigenClaveYVersionObservadaSinAceptarFinComoEstadoGenerico() throws Exception {
        var body=pedido();
        http.perform(post("/requerimientos").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        String k=clave();var alta=alta(body,k);long id=alta.path("id").longValue(),observada=alta.path("version").longValue();
        assertEquals(0,observada);assertEquals("A",alta.path("estado").textValue());assertFalse(alta.has("idCliente"));
        assertTrue(alta.path("idPersonaInteresada").isIntegralNumber());
        http.perform(put("/requerimientos/{id}",id).header("Idempotency-Key",clave()).contentType(MediaType.APPLICATION_JSON).content("{\"observaciones\":\"Cambio sin CAS TEST\"}"))
                .andExpect(status().isBadRequest());
        http.perform(put("/requerimientos/{id}",id).header("Idempotency-Key",clave()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("versionEsperada",observada,"estado","R")))).andExpect(status().isBadRequest());
        http.perform(put("/requerimientos/{id}",id).header("Idempotency-Key",clave()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("versionEsperada",observada,"observaciones","Edición TEST"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(observada+1));
        http.perform(put("/requerimientos/{id}",id).header("Idempotency-Key",clave()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("versionEsperada",observada,"observaciones","Edición obsoleta TEST")))).andExpect(status().isConflict());
        assertEquals(alta,alta(body,k));
        http.perform(get("/requerimientos/{id}",id).param("version",Long.toString(observada))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(observada));
        http.perform(get("/requerimientos/{id}",id)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(observada+1));
        assertEquals(2,numero("select count(*) from requerimiento_version where organizacion_id=? and id_requerimiento=?",org(),id));
    }
    @Test void finSoloPreparaHastaRevisionExactaYConfirmacionConUnEfectoDurable() throws Exception {
        var alta=alta(pedido(),clave());long id=alta.path("id").longValue(),observada=alta.path("version").longValue();
        var p=leer(http.perform(post("/requerimientos/{id}/fin/propuestas",id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("versionEsperada",observada,"tipo","RETIRADA","fechaDelHecho",OrigenesDePrueba.hoy().toString(),"motivo","Solicitud expresa HTTP TEST"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacidad").value("retirar_finalizar_demanda")).andReturn().getResponse().getContentAsByteArray());
        String ref=p.path("referencia").textValue();assertNull(jdbc.queryForObject("select id_borrador from operacion_herramienta where referencia=?",Long.class,ref));
        http.perform(get("/requerimientos/{id}",id)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("A")).andExpect(jsonPath("$.version").value(observada));
        var revision=leer(http.perform(get("/actuaciones/{ref}/revision",ref)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        var confirmado=new LinkedHashMap<String,Object>();confirmado.put("version",revision.path("propuesta").path("version").longValue());
        confirmado.put("huella",revision.path("propuesta").path("huella").textValue());confirmado.put("pruebaRevision",revision.path("pruebaRevision").textValue());confirmado.put("eventoHumano",clave());
        var falso=new LinkedHashMap<>(confirmado);falso.put("huella","0".repeat(64));
        http.perform(post("/actuaciones/{ref}/confirmacion",ref).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(falso))).andExpect(status().isConflict());
        var resultado=leer(http.perform(post("/actuaciones/{ref}/confirmacion",ref).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(confirmado)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EXITO")).andReturn().getResponse().getContentAsByteArray());
        var repetido=leer(http.perform(post("/actuaciones/{ref}/confirmacion",ref).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(confirmado)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(resultado.path("resultado"),repetido.path("resultado"));
        http.perform(get("/requerimientos/{id}",id)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("R")).andExpect(jsonPath("$.version").value(observada+1));
        assertEquals(1,numero("select count(*) from comando_idempotente where organizacion_id=? and idempotency_key=?",org(),"actuacion:"+ref));
        assertEquals(1,numero("select count(*) from evento_dominio where organizacion_id=? and tipo='RETIRAR_O_FINALIZAR_DEMANDA'",org()));
    }
    @Test void conocerElIdNoDaFichaNiCriteriosAlColegaODatoDeOtroTenant() throws Exception {
        var alta=alta(pedido(),clave());long id=alta.path("id").longValue();
        como(equipo.colega(),banco.sesion(equipo.colega()));
        http.perform(get("/requerimientos/{id}",id)).andExpect(status().isForbidden());
        var otro=banco.empresa();como(otro.agente(),banco.sesion(otro.agente()));
        http.perform(get("/requerimientos/{id}",id)).andExpect(status().isNotFound());
        assertEquals(1,numero("select count(*) from requerimiento_cliente where organizacion_id=?",org()));
    }
    private void como(Actor actor,ContextoSesion s) {
        var acceso=contextos.exigirContexto(s.referenciaSesion(),s.revisionContexto(),actor);
        var principal=new SesionDeRequest(new TokenService("").de(acceso),acceso);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,AuthorityUtils.createAuthorityList("ROLE_"+actor.rolEfectivo())));
    }
    private JsonNode alta(Map<String,Object> body,String clave) throws Exception {
        return leer(http.perform(post("/requerimientos").header("Idempotency-Key",clave).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
    }
    private static Map<String,Object> pedido(){return Map.of("operacion","ALQUILER","tiposInmueble",List.of("DEPARTAMENTO"),"observaciones","Pedido HTTP TEST",
            "interesado",Map.of("tipo","CONTACTO_NUEVO","contacto",Map.of("telefono","999000002")));}
    private JsonNode leer(byte[] b) throws Exception{return json.readTree(b);}
    private long numero(String sql,Object... argumentos){return jdbc.queryForObject(sql,Long.class,argumentos);}
    private long org(){return equipo.agente().idOrganizacion();}
    private static String clave(){return "demanda-http-test-"+UUID.randomUUID();}
}
