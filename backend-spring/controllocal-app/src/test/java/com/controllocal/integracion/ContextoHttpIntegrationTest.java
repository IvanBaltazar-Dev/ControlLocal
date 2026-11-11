package com.controllocal.integracion;

import com.controllocal.service.Actor;
import com.controllocal.service.ClienteService;
import com.controllocal.service.PropietarioService;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.soporte.PasswordHasher;
import com.controllocal.service.CuentaService;
import com.controllocal.service.soporte.CifradoSecretos;
import com.controllocal.service.soporte.Totp;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.web.seguridad.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Cadena HTTP real, JWT/filtro/Core/PostgreSQL; sin principal prefabricado ni filtros omitidos. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ContextoHttpIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p) {BaseDeDatosDePruebas.registrar(p);}
    private static final String CLAVE="Prueba C1 HTTP! Solo fixture 2026";
    private static final String HASH=PasswordHasher.hash(CLAVE.toCharArray());
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired CuentaService cuentas;
    @Autowired ClienteService clientes;
    @Autowired PropietarioService propietarios;
    @Autowired TokenService tokens;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    private long org,cuenta,persona,rol;
    private String correo;
    private LocalDate fechaLocal;

    @BeforeEach void fixture() {
        fechaLocal=LocalDate.now(ZoneId.of("America/Lima"));
        new TransactionTemplate(transacciones).executeWithoutResult(tx->{
            String id=UUID.randomUUID().toString();correo="ctx-http-"+id+"@example.test";
            org=jdbc.queryForObject("insert into organizacion(codigo,nombre,estado,modalidad) values(?,?,'A','EMPRESARIAL') returning id_organizacion",Long.class,"HTTP-"+id.substring(0,24),"Organizacion ficticia HTTP");
            long pBroker=persona("Broker HTTP"),rBroker=rol(pBroker,"BROKER");rol(pBroker,"ADMIN");
            jdbc.update("insert into detalle_broker(organizacion_id,id_persona_rol,codigo_broker,fecha_designacion) values(?,?,?,?)",org,rBroker,"HB-"+id.substring(0,14),fechaLocal);
            long cBroker=cuenta("broker-http-"+id+"@example.test");
            jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,'BROKER',true,'A')",org,cBroker,pBroker);
            persona=persona("Agente HTTP");rol=rol(persona,"AGENTE");cuenta=cuenta(correo);
            jdbc.update("insert into detalle_agente(organizacion_id,id_persona_rol,codigo_agente,fecha_ingreso,estado_operativo) values(?,?,?,?,'D')",org,rol,"HA-"+id.substring(0,14),fechaLocal);
            jdbc.update("insert into supervision_agente(organizacion_id,id_rol_broker,id_rol_agente,fecha_asignacion,motivo) values(?,?,?,?,'Supervision explicita fixture HTTP')",org,rBroker,rol,fechaLocal);
            jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,'AGENTE',false,'A')",org,cuenta,persona);
        });
    }
    private long persona(String nombre) {return jdbc.queryForObject("insert into persona(organizacion_id,tipo_persona,tipo_documento,numero_documento,nombres_o_razon_social) values(?,'N','P',?,?) returning id_persona",Long.class,org,UUID.randomUUID().toString().substring(0,25),nombre);}
    private long rol(long idPersona,String tipo) {return jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,?,?) returning id_persona_rol",Long.class,org,idPersona,tipo,fechaLocal);}
    private long cuenta(String email) {return jdbc.queryForObject("insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) values(?,?,clock_timestamp(),?) returning id_cuenta_acceso",Long.class,"http-"+UUID.randomUUID(),email,HASH);}
    private JsonNode entrar() throws Exception {
        var r=mvc.perform(post("/auth/mfa/desafio").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("correo",correo,"contrasena",CLAVE))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control",containsString("no-store")))
                .andReturn().getResponse();
        return json.readTree(r.getContentAsByteArray());
    }
    private String elegir(JsonNode login) throws Exception {
        assertEquals(1,login.path("contextos").size());
        return json.readTree(mvc.perform(post("/sesion/contexto").header("Authorization","Bearer "+login.path("token").asText())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("referencia",login.path("contextos").get(0).path("referencia").asText()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("AGENTE"))
                .andExpect(jsonPath("$.idOrganizacion").value(org)).andExpect(jsonPath("$.idUsuario").value(persona))
                .andExpect(jsonPath("$.idDominio").value(rol)).andReturn().getResponse().getContentAsByteArray()).path("token").asText();
    }
    @Test void recuperacionVaciaOInvalidaConservaAcuseUniformeSinCrearUnEnlace() throws Exception {
        long antes=jdbc.queryForObject("select count(*) from token_acceso where tipo='RECUPERACION'",Long.class);
        for(String cuerpo:new String[]{"","{}","{\"correo\":\"sin formato de correo\"}"}) {
            mvc.perform(post("/auth/recuperacion").with(r->{r.setRemoteAddr("198.51.100.90");return r;})
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isAccepted()).andExpect(content().string(""))
                    .andExpect(header().string("Cache-Control",containsString("no-store")));
        }
        assertEquals(antes,jdbc.queryForObject("select count(*) from token_acceso where tipo='RECUPERACION'",Long.class));
    }
    @Test void identidadNoOperaYLaSeleccionInvalidaElTokenPrevio() throws Exception {
        var login=entrar();String previo=login.path("token").asText();
        assertTrue(login.path("contextoRequerido").asBoolean());assertEquals("IDENTIDAD",login.path("rol").asText());
        assertEquals(0,login.path("idUsuario").asLong());assertEquals(0,login.path("idDominio").asLong());assertEquals(0,login.path("idOrganizacion").asLong());
        mvc.perform(get("/propiedades").header("Authorization","Bearer "+previo)).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CONTEXTO_REQUERIDO"));
        var vigente=elegir(login);
        mvc.perform(get("/sesion").header("Authorization","Bearer "+previo)).andExpect(status().isUnauthorized());
        mvc.perform(get("/propiedades").header("Authorization","Bearer "+vigente)).andExpect(status().isOk());
    }
    @Test void referenciaInventadaNoEligeOrganizacionORol() throws Exception {
        var login=entrar();
        mvc.perform(post("/sesion/contexto").header("Authorization","Bearer "+login.path("token").asText())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("referencia","f".repeat(64)))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/sesion").header("Authorization","Bearer "+login.path("token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contextoRequerido").value(true));
    }
    @Test void bajaYReactivacionNoResucitanJwtNiPermitenLeerUnaClaveDeProveedor() throws Exception {
        String token=elegir(entrar());
        mvc.perform(get("/documentos/contenido").param("clave","documentos/ajeno.pdf").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        mvc.perform(get("/documentos/contenido").param("clave",UUID.randomUUID().toString()).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        jdbc.update("update usuario_organizacion set estado='I' where id_usuario=?",cuenta);
        mvc.perform(get("/sesion").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        jdbc.update("update usuario_organizacion set estado='A' where id_usuario=?",cuenta);
        mvc.perform(get("/sesion").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/sesion").header("Authorization","Bearer "+elegir(entrar()))).andExpect(status().isOk());
    }
    @Test void salirDesdeIdentidadRevocaLosDosDispositivos() throws Exception {
        String a=entrar().path("token").asText(),b=entrar().path("token").asText();
        mvc.perform(post("/auth/logout").header("Authorization","Bearer "+a)).andExpect(status().isNoContent());
        mvc.perform(get("/sesion").header("Authorization","Bearer "+a)).andExpect(status().isUnauthorized());
        mvc.perform(get("/sesion").header("Authorization","Bearer "+b)).andExpect(status().isUnauthorized());
    }
    private java.util.List<String> enrolarMfa(JsonNode inicial) {
        var segura=tokens.validar(inicial.path("token").asText()).orElseThrow();
        cuentas.iniciarMfa(segura.referenciaSesion(),segura.revisionContexto());
        var f=factores.pendientesDe(cuenta).getFirst();
        byte[] secreto=cifrado.descifrar(f.getSecretoCifrado(),f.getNonce(),f.getVersionClave());
        return cuentas.confirmarMfa(segura.referenciaSesion(),segura.revisionContexto(),Totp.generar(secreto,Totp.pasoDe(Instant.now())));
    }
    @Test void segundoFactorRealPrecedeASesionIdentidadYSeleccionDeContexto() throws Exception {
        var respaldos=enrolarMfa(entrar());
        var reto=json.readTree(mvc.perform(post("/auth/mfa/desafio").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("correo",correo,"contrasena",CLAVE))))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control",containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray());
        assertFalse(reto.has("token"));
        var login=json.readTree(mvc.perform(post("/auth/mfa/verificar").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("desafio",reto.path("desafio").asText(),"codigo",respaldos.getFirst()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("IDENTIDAD"))
                .andExpect(jsonPath("$.idUsuario").value(0)).andReturn().getResponse().getContentAsByteArray());
        String token=elegir(login);mvc.perform(get("/sesion").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(post("/auth/mfa/verificar").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("desafio",reto.path("desafio").asText(),"codigo",respaldos.get(1)))))
                .andExpect(status().isBadRequest());
    }

    @Test void gobiernoConMfaResueltaLeeSuPadronSinAbrirCarteraComercial() throws Exception {
        Actor agente=new Actor(org,persona,rol,Actor.AGENTE);
        String sufijo=UUID.randomUUID().toString().substring(0,20);
        long idCliente=clientes.registrar(new ClienteService.DatosCliente("N","P",sufijo,
                "Cliente privado HTTP",null,null,null,false,true,"A"),agente).id();
        long idPropietario=propietarios.registrar(new PropietarioService.DatosPropietario("N","P",
                "p-"+sufijo,"Propietario privado HTTP",null,null,true,"A"),agente).id();
        new TransactionTemplate(transacciones).executeWithoutResult(tx->{
            rol(persona,"ADMIN");
            jdbc.update("update usuario_organizacion set administrar=true where id_usuario=? and organizacion_id=?",cuenta,org);
        });
        var respaldos=enrolarMfa(entrar());
        var reto=json.readTree(mvc.perform(post("/auth/mfa/desafio").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("correo",correo,"contrasena",CLAVE))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsByteArray());
        var login=json.readTree(mvc.perform(post("/auth/mfa/verificar").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("desafio",reto.path("desafio").asText(),"codigo",respaldos.getFirst()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        String referencia=null;
        for(JsonNode contexto:login.path("contextos"))
            if(Actor.TENANT_ADMIN.equals(contexto.path("rol").asText())) referencia=contexto.path("referencia").asText();
        assertNotNull(referencia,"La atribucion de gobierno debe ser ofrecida por Core.");
        String token=json.readTree(mvc.perform(post("/sesion/contexto").header("Authorization","Bearer "+login.path("token").asText())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("referencia",referencia))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value(Actor.TENANT_ADMIN))
                .andReturn().getResponse().getContentAsByteArray()).path("token").asText();
        mvc.perform(get("/sesion").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.debeEnrolarMfa").value(false))
                .andExpect(jsonPath("$.debeCambiarContrasena").value(false));
        mvc.perform(get("/accesos").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        for(String capacidad:new String[]{"buscar_persona","buscar_propietarios","que_resolver_hoy"})
            mvc.perform(get("/capacidades").param("idCore",capacidad).header("Authorization","Bearer "+token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.capacidades[0].estado").value("PERMISO_INSUFICIENTE"));
        for(String ruta:new String[]{"/clientes","/propietarios","/oportunidades","/visitas","/contratos","/evaluaciones",
                "/propiedades","/prospecciones","/captaciones",
                "/clientes/"+idCliente,"/propietarios/"+idPropietario})
            mvc.perform(get(ruta).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(get("/clientes/"+Long.MAX_VALUE).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        mvc.perform(get("/propietarios/"+Long.MAX_VALUE).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
    }
}
