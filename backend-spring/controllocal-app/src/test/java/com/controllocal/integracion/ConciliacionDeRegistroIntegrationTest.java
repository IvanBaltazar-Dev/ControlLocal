package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.*;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.persistence.repositorio.BorradorCapturaRepository;
import com.controllocal.domain.captura.BorradorCaptura;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import com.controllocal.web.seguridad.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static com.controllocal.service.PropiedadUniversalService.*;
import static com.controllocal.service.captura.MotorDeCaptura.EstadoDelResultado.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"spring.jpa.properties.hibernate.generate_statistics=true",
                "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureMockMvc(addFilters = false)
class ConciliacionDeRegistroIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p) { BaseDeDatosDePruebas.registrar(p); }
    @Autowired MotorDeCaptura motor;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.controllocal.service.ContextosService contextos;
    @Autowired Documentos documentos;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transacciones;
    @MockitoSpyBean BorradorCapturaRepository borradores;
    @Autowired EntityManager entidades;
    @Autowired EntityManagerFactory fabrica;
    private Actor agente;
    record Registro(long borrador,int version,String clave,ResultadoRegistro resultado) { }
    @BeforeEach void prepararActor() {
        var f=jdbc.queryForMap("""
            select r.organizacion_id,r.id_persona,r.id_persona_rol from detalle_agente a
            join persona_rol r using(id_persona_rol) order by r.id_persona_rol limit 1
            """);
        agente=new Actor(((Number)f.get("organizacion_id")).longValue(),((Number)f.get("id_persona")).longValue(),
                ((Number)f.get("id_persona_rol")).longValue(),Actor.AGENTE);
        sesion(agente);
    }
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }
    private void sesion(Actor a) {
        var principal=com.controllocal.integracion.soporte.SesionesDePrueba.abrir(jdbc,contextos,a);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,
                AuthorityUtils.createAuthorityList("ROLE_"+a.rolEfectivo())));
    }
    private long borrador() {
        return motor.avanzar(new MotorDeCaptura.Avance("REGISTRAR_PROPIEDAD",null,null,respuestas(Map.of(
                "tipoPropiedad","CASA","uso","VIVIENDA","distrito","Miraflores","direccion","Av. Conciliación "+UUID.randomUUID(),
                "metraje_total","120","area_terreno","150","metraje_construido","90","dormitorios","3","banos","2")),null),null,agente).idBorrador();
    }
    private ComandoRegistro comando(long borrador,String clave) {
        return new ComandoRegistro(clave,null,null,"CASA","VIVIENDA","Conciliación C0",
                new Ubicacion(motor.consultar(borrador,agente).conocido().get("direccion").toString(),"Miraflores",null,null,null,null,null,null,null),
                List.of(),List.of(new ValorAtributo("metraje_total","120"),new ValorAtributo("area_terreno","150"),
                        new ValorAtributo("metraje_construido","90"),new ValorAtributo("dormitorios","3"),
                        new ValorAtributo("banos","2")),List.of(),borrador);
    }
    private ResultadoRegistro registrar(long borrador,String clave) {
        return propiedades.registrar(comando(borrador,clave),agente);
    }
    private Registro registro() {
        long b=borrador();String clave=UUID.randomUUID().toString();int version=motor.consultar(b,agente).version();return new Registro(b,version,clave,registrar(b,clave));
    }
    @Test void devuelveElResultadoConfirmadoSinRepetirElAlta() {
        var r=registro();var antes=conteos();
        var stats=fabrica.unwrap(SessionFactory.class).getStatistics();long sqlAntes=stats.getPrepareStatementCount();
        var estado=motor.consultarResultado(r.borrador(),r.clave(),agente);
        long consultas=stats.getPrepareStatementCount()-sqlAntes;
        assertTrue(consultas<=3,"Consulta indexada sin fotos, ficha completa ni lecturas por atributo");
        System.out.println("C0 conciliación SQL: "+consultas);
        assertEquals(EXITO,estado.estado());assertEquals(r.resultado().idPropiedad(),estado.resultado().idPropiedad());
        assertEquals(r.resultado().codigo(),estado.resultado().codigoPropiedad());
        assertEquals(r.resultado().idsEncargos(),estado.resultado().idsEncargos());
        assertEquals(antes,conteos());
    }
    @Test void noRecomponeElResultadoConHechosPosteriores() {
        var r=registro();
        jdbc.update("update propiedad set codigo=? where id_propiedad=?","CAM-"+r.resultado().idPropiedad(),r.resultado().idPropiedad());
        assertEquals(r.resultado().codigo(),motor.consultarResultado(r.borrador(),r.clave(),agente).resultado().codigoPropiedad());
        assertEquals(r.resultado().codigo(),motor.ejecutar(r.borrador(),r.version(),r.clave(),null,agente).codigoPropiedad());
    }
    @Test void claveNoLocalizadaNoAcreditaFracasoNiCreaOtroComando() {
        var r=registro();var antes=conteos();
        var estado=motor.consultarResultado(r.borrador(),UUID.randomUUID().toString(),agente);
        assertEquals(NO_LOCALIZABLE,estado.estado());assertNull(estado.resultado());assertEquals(antes,conteos());
    }
    @Test void otroAgenteNoConsultaLaActuacionAjena() {
        var r=registro();var otro=new Actor(agente.idOrganizacion(),agente.idPersona()+1000,agente.idRolOperativo()+1000,Actor.AGENTE);
        assertThrows(NoEncontradoException.class,()->motor.consultarResultado(r.borrador(),r.clave(),otro));
    }
    @Test void otroTenantNoConoceLaCapturaNiElResultado() {
        var r=registro();var otro=new Actor(agente.idOrganizacion()+1000,agente.idPersona(),agente.idRolOperativo(),Actor.AGENTE);
        assertThrows(NoEncontradoException.class,()->motor.consultarResultado(r.borrador(),r.clave(),otro));
    }
    @Test void gobiernoNoAdquiereLecturaComercialDelResultado() {
        var r=registro();var admin=new Actor(agente.idOrganizacion(),agente.idPersona(),agente.idRolOperativo(),Actor.TENANT_ADMIN);
        assertThrows(AccesoNoAutorizadoException.class,()->motor.consultarResultado(r.borrador(),r.clave(),admin));
    }
    @Test void unaClaveValidaNoSeAsociaAOtraCaptura() {
        var r=registro();long otro=borrador();
        assertThrows(ConflictoException.class,()->motor.consultarResultado(otro,r.clave(),agente));
    }
    @Test void reintentoConOtraIdentidadNoDevuelveUnExitoDeLaAnterior() {
        var r=registro();
        assertThrows(ConflictoException.class,()->motor.ejecutar(r.borrador(),r.version(),UUID.randomUUID().toString(),null,agente));
    }
    @Test void reutilizarUnaClaveAjenaNoCierraOtraCapturaConSuResultado() {
        var r=registro();long b=borrador();var antes=conteos();
        var otro=new Actor(agente.idOrganizacion(),agente.idPersona()+1000,agente.idRolOperativo()+1000,Actor.AGENTE);
        assertThrows(ConflictoException.class,()->propiedades.registrar(comando(b,r.clave()),otro));
        assertEquals("E",motor.consultar(b,agente).estado());assertEquals(antes,conteos());
    }
    @Test void laRutaPublicaExigeClaveYDevuelveElContratoTipado() throws Exception {
        var r=registro();
        mvc.perform(get("/captura/{id}/resultado",r.borrador())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
        mvc.perform(get("/captura/{id}/resultado",r.borrador()).header("Idempotency-Key",r.clave()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EXITO"))
                .andExpect(jsonPath("$.resultado.idPropiedad").value(r.resultado().idPropiedad()));
    }
    @ParameterizedTest @ValueSource(strings={"SIN_CODIGO","SIN_ENCARGOS","ID_FRACCIONARIO","ENCARGO_FRACCIONARIO"})
    void resultadoPersistidoIncompatibleEsFalloTecnico(String caso) {
        var r=registro();Map<String,Object> json=new LinkedHashMap<>();
        json.put("idPropiedad",r.resultado().idPropiedad());json.put("codigo",r.resultado().codigo());json.put("idsEncargos",List.of());
        switch(caso) {
            case "SIN_CODIGO"->json.remove("codigo");case "SIN_ENCARGOS"->json.remove("idsEncargos");
            case "ID_FRACCIONARIO"->json.put("idPropiedad",new BigDecimal(r.resultado().idPropiedad()+".5"));
            case "ENCARGO_FRACCIONARIO"->json.put("idsEncargos",List.of(new BigDecimal("1.5")));
        }
        jdbc.update("update comando_idempotente set resultado=? where organizacion_id=? and idempotency_key=?",
                documentos.objeto(json),agente.idOrganizacion(),r.clave());
        assertThrows(IllegalStateException.class,()->motor.consultarResultado(r.borrador(),r.clave(),agente));
    }
    @Test void transaccionSinCommitSigueInciertaYDespuesConfirmaLaMismaClave() throws Exception {
        long b=borrador();String clave=UUID.randomUUID().toString();
        var escrito=new CountDownLatch(1);var liberar=new CountDownLatch(1);
        var pool=Executors.newSingleThreadExecutor();
        try {
            var trabajo=pool.submit(()->new TransactionTemplate(transacciones).execute(s->{
                var r=registrar(b,clave);escrito.countDown();
                try { if(!liberar.await(30,TimeUnit.SECONDS))throw new IllegalStateException("Sin liberación de fixture"); }
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                return r;
            }));
            assertTrue(escrito.await(30,TimeUnit.SECONDS));
            assertEquals(NO_LOCALIZABLE,motor.consultarResultado(b,clave,agente).estado());
            liberar.countDown();var r=trabajo.get(60,TimeUnit.SECONDS);
            assertEquals(r.idPropiedad(),motor.consultarResultado(b,clave,agente).resultado().idPropiedad());
        } finally { liberar.countDown();pool.shutdownNow(); }
    }
    @Test void commitEntreLecturasNoMezclaEstadoYResultado() throws Exception {
        long b=borrador();String clave=UUID.randomUUID().toString();
        var escrito=new CountDownLatch(1);var liberar=new CountDownLatch(1);
        var pool=Executors.newSingleThreadExecutor();
        try {
            var trabajo=pool.submit(()->new TransactionTemplate(transacciones).execute(s->{
                var r=registrar(b,clave);escrito.countDown();
                try { if(!liberar.await(30,TimeUnit.SECONDS))throw new IllegalStateException("Sin liberación de fixture"); }
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                return r;
            }));
            assertTrue(escrito.await(30,TimeUnit.SECONDS));
            doAnswer(invocacion->{
                // Consulta JPA real del mismo tenant, dentro de la transacción del lector.
                Optional<BorradorCaptura> leido;
                try (var filas=entidades.createQuery("select b from BorradorCaptura b where b.organizacionId=:org and b.id=:id",BorradorCaptura.class)
                        .setParameter("org",agente.idOrganizacion()).setParameter("id",b).getResultStream()) {
                    leido=filas.findFirst();
                }
                liberar.countDown();trabajo.get(60,TimeUnit.SECONDS); // Commit real entre ambos SELECT.
                return leido;
            }).when(borradores).findByOrganizacionIdAndId(agente.idOrganizacion(),b);
            assertEquals(NO_LOCALIZABLE,motor.consultarResultado(b,clave,agente).estado());
            reset(borradores);
            assertEquals(EXITO,motor.consultarResultado(b,clave,agente).estado());
        } finally { liberar.countDown();pool.shutdownNow();reset(borradores); }
    }
    @Test void rollbackNoDejaUnExitoQueLaConsultaPuedaInventar() {
        long b=borrador();String clave=UUID.randomUUID().toString();var antes=conteos();
        new TransactionTemplate(transacciones).executeWithoutResult(s->{registrar(b,clave);s.setRollbackOnly();});
        assertEquals(NO_LOCALIZABLE,motor.consultarResultado(b,clave,agente).estado());
        assertEquals(antes,conteos());
    }
    private Map<String,Long> conteos() {
        Map<String,Long> r=new LinkedHashMap<>();
        for(String t:List.of("propiedad","borrador_captura","comando_idempotente","evento_dominio","historial_estado"))
            r.put(t,jdbc.queryForObject("select count(*) from "+t,Long.class));
        return r;
    }

    private static Map<String,MotorDeCaptura.Respuesta> respuestas(Map<String,String> datos){
        var salida=new LinkedHashMap<String,MotorDeCaptura.Respuesta>();
        datos.forEach((k,v)->salida.put(k,MotorDeCaptura.Respuesta.conocida(v)));return salida;
    }
}
