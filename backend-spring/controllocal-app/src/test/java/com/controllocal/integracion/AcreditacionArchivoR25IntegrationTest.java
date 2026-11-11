package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.app.plataforma.AcreditacionesTitularArchivo;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.presupuesto.AtribucionesPlataformaRepository;
import com.controllocal.service.ContextosService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.plataforma.AcreditacionesTitularPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.soporte.Idempotencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.*;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fuente de archivo real -> Core/C1/PG reales. Decisiones y cuentas sólo TEST; ningún permiso real. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,EvaluacionIaB0IntegrationTest.AcreditacionTest.class},
 webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class AcreditacionArchivoR25IntegrationTest {
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
 @Autowired ApplicationContext app;
 @TempDir Path directorio;
 EvaluacionIaB0IntegrationTest d;
 ObjectMapper json;
 Path archivo;
 AcreditacionesTitularArchivo fuente;
 AtribucionesPlataforma core;
 Datos datos;
 UUID decision;
 long destinatario;
 SesionPlataforma cuentaDestino,sesionDestino;

 @BeforeEach void preparar(TestInfo info)throws Exception{
  // El acreditador del helper prepara sólo datos previos no sometidos al caso de archivo.
  d=new EvaluacionIaB0IntegrationTest();app.getAutowireCapableBeanFactory().autowireBean(d);
  d.directorio=directorio;d.preparar(info);json=app.getBean(ObjectMapper.class);
  assertTrue(app.getBeansOfType(AcreditacionesTitularArchivo.class).isEmpty(),"La aplicación conserva la fuente OFF por defecto.");
  archivo=directorio.resolve("DECISIONES_R25_TEST_NO_OPERATIVAS.json");fuente=new AcreditacionesTitularArchivo(archivo.toString());
  var beans=new StaticListableBeanFactory();beans.addBean("fuenteArchivoRealTest",fuente);
  core=new AtribucionesPlataforma(app.getBean(ContextosService.class),app.getBean(AtribucionesPlataformaRepository.class),
   beans.getBeanProvider(AcreditacionesTitularPlataforma.class),d.json);
  cuentaDestino=d.crearCuenta();destinatario=app.getBean(ContextosService.class).exigirIdentidad(cuentaDestino.referenciaSesion(),cuentaDestino.revisionSesion()).identidad().idCuenta();
  UUID id=UUID.randomUUID();decision=UUID.randomUUID();
  datos=new Datos(id,destinatario,Atribucion.OPERACION_IA_PLATFORM,d.plan,d.version,d.caso,null,"EVALUACION_TEST",
   Set.of(Accion.CONSULTAR_ESTADO),Set.of(Campo.ESTADO),Instant.now().minusSeconds(1),Instant.now().plusSeconds(600),"CONCESION TEST NO AUTORIZACION REAL");
  sesionDestino=EvaluacionIaB0IntegrationTest.conGrant(cuentaDestino,id);
 }
 @AfterEach void limpiar(){if(d!=null)d.limpiar();}

 ObjectNode declaracion(UUID ref,String accion,Object contenido){
  var n=json.createObjectNode();n.put("referencia",ref.toString());n.put("otorgante",d.cuenta);n.put("destinatario",destinatario);
  n.put("actuacion",accion);n.put("huellaContenido",Idempotencia.huellaExacta(d.json.objetoCanonico(contenido)));
  n.put("evidencia","FUENTE ARCHIVO TEST SIN DECISION REAL");n.put("acreditadaEn",Instant.now().minusSeconds(2).toString());return n;
 }
 void escribir(ObjectNode... decisiones)throws Exception{
  var n=json.createObjectNode();n.put("version",1);var a=n.putArray("decisiones");for(var x:decisiones)a.add(x);
  Files.writeString(archivo,json.writeValueAsString(n));
 }
 void conceder(SesionPlataforma s){d.tx.executeWithoutResult(t->core.conceder(datos,decision,s));}
 void resolver(){d.tx.executeWithoutResult(t->{var a=core.resolver(sesionDestino,Atribucion.OPERACION_IA_PLATFORM,Accion.CONSULTAR_ESTADO,Campo.ESTADO);
  assertEquals(destinatario,a.cuenta());assertEquals(datos.id(),a.concesion());assertEquals(d.plan,a.plan());});}

 @Test void archivoNoConcedeSinComandoNiSidCompetenteYRetiroNoSustituyeRevocacion()throws Exception{
  escribir(declaracion(decision,"CONCEDER_ATRIBUCION",datos));
  assertThrows(AccesoNoAutorizadoException.class,this::resolver,"Configurar una decisión no crea una concesión.");
  assertThrows(AccesoNoAutorizadoException.class,()->conceder(cuentaDestino),"El destinatario no suplanta al otorgante acreditado.");
  assertEquals(0,d.jdbc.queryForObject("SELECT count(*) FROM b0_atribucion_plataforma WHERE id=?",Integer.class,datos.id()));
  conceder(d.admin);resolver();
  core.verificarDecisionRegistrada(decision,"CONCEDER_ATRIBUCION",d.cuenta,destinatario);
  assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_atribucion_plataforma WHERE id=? AND decision=?",Integer.class,datos.id(),decision));
  escribir();
  assertThrows(AccesoNoAutorizadoException.class,()->core.verificarDecisionRegistrada(decision,"CONCEDER_ATRIBUCION",d.cuenta,destinatario));
  resolver(); // La autoridad vigente es la concesión Core; el archivo no la revoca implícitamente.
  Files.writeString(archivo,"NO ES JSON TEST");
  assertThrows(IllegalStateException.class,()->core.verificarDecisionRegistrada(decision,"CONCEDER_ATRIBUCION",d.cuenta,destinatario));
  resolver();
  UUID revocacion=UUID.randomUUID();String motivo="REVOCACION EXPRESA TEST";
  escribir(declaracion(revocacion,"REVOCAR_ATRIBUCION",Map.of("concesion",datos.id(),"motivo",motivo)));
  d.tx.executeWithoutResult(t->core.revocar(datos.id(),revocacion,motivo,d.admin));
  assertThrows(AccesoNoAutorizadoException.class,this::resolver);
  assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_decision_plataforma WHERE id=?",Integer.class,decision));
  assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_atribucion_plataforma WHERE id=? AND decision_revocacion=? AND revocada_en IS NOT NULL",Integer.class,datos.id(),revocacion));
 }

 @ParameterizedTest @ValueSource(strings={"otorgante","destinatario","actuacion","huellaContenido","acreditadaEn","evidencia"})
 void archivoLegibleNoSupleLaTuplaAcreditadaPorCore(String campo)throws Exception{
  var n=declaracion(decision,"CONCEDER_ATRIBUCION",datos);
  switch(campo){
   case "otorgante" -> n.put(campo,d.cuenta+1000000);
   case "destinatario" -> n.put(campo,destinatario+1000000);
   case "actuacion" -> n.put(campo,"OTRA_ACTUACION_TEST");
   case "huellaContenido" -> n.put(campo,"b".repeat(64));
   case "acreditadaEn" -> n.put(campo,"2099-01-01T00:00:00Z");
   case "evidencia" -> n.put(campo," ");
   default -> throw new AssertionError(campo);
  }
  escribir(n);assertTrue(fuente.acreditar(decision).isPresent(),"El caso pasa el parser y prueba la autoridad Core.");
  assertThrows(AccesoNoAutorizadoException.class,()->conceder(d.admin));
  assertEquals(0,d.jdbc.queryForObject("SELECT count(*) FROM b0_decision_plataforma WHERE id=?",Integer.class,decision));
  assertEquals(0,d.jdbc.queryForObject("SELECT count(*) FROM b0_atribucion_plataforma WHERE id=?",Integer.class,datos.id()));
 }
}
