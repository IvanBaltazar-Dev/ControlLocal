package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.ContextosService;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.web.seguridad.TokenService;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP/SID/grant/SQL reales con corpus/fondos SINTETICOS; sin proveedor o gasto. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,EvaluacionIaB0IntegrationTest.AcreditacionTest.class},
 webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties="spring.datasource.hikari.maximum-pool-size=8")
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class MetricasIaAccesoHttpIntegrationTest {
 @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
 @Autowired ApplicationContext app;@Autowired MockMvc http;@Autowired TokenService tokens;
 @TempDir Path directorio;EvaluacionIaB0IntegrationTest d;
 @BeforeEach void preparar(TestInfo info){d=new EvaluacionIaB0IntegrationTest();app.getAutowireCapableBeanFactory().autowireBean(d);d.directorio=directorio;d.preparar(info);
  // El helper B0 directo conserva nanos. Esta sesion HTTP TEST usa iat/exp enteros como el login real.
  d.tx.executeWithoutResult(t->d.jdbc.update("UPDATE sesion_acceso SET emitida_en=date_trunc('second',emitida_en),vence_en=date_trunc('second',vence_en) WHERE referencia_hash=?",
   com.controllocal.service.soporte.Idempotencia.huellaExacta(d.admin.referenciaSesion())));
 }
 @AfterEach void limpiar(){d.limpiar();}
 String bearer(){return "Bearer "+tokens.firmar(tokens.de(app.getBean(ContextosService.class).exigirIdentidad(d.admin.referenciaSesion(),d.admin.revisionSesion())));}
 @Test void cuentaSinContextoPuedeLeerSuLedgerSoloConConcesionFinancieraYNoCache()throws Exception{
  var s=d.solicitud();d.admitir(s);d.iniciar(s);d.tx.execute(t->d.presupuesto.conciliar(d.ref(s),d.medicion("0.1234567890123456789")));
  var r=http.perform(get("/plataforma/metricas-ia/financieras/"+d.caso+"/"+s.operacion())
   .header("Authorization",bearer()).header("X-Brox-Concesion",d.grantFin.toString())
   .param("desde",d.ahora.minusSeconds(60).toString()).param("hasta",Instant.now().plusSeconds(1).toString()))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
  var n=app.getBean(com.fasterxml.jackson.databind.ObjectMapper.class).copy()
   .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(r.getResponse().getContentAsByteArray());
  assertEquals(s.operacion().toString(),n.path("comando").asText());assertEquals(1,n.path("mediciones").size());
  assertEquals(0,n.path("mediciones").get(0).path("costoUsd").decimalValue().compareTo(new java.math.BigDecimal("0.1234567890123456789")));
  assertFalse(n.hasNonNull("costoPorTareaCorrecta"),"Una respuesta200 no es una tarea correcta acreditada.");
  assertTrue(n.path("coberturaCalidad").asText().contains("SIN_REFERENCIA_HUMANA"));
  assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_consulta_metricas_ia WHERE concesion=? AND operacion=?",Integer.class,d.grantFin,s.operacion()));
 }
 @Test void identidadOGrantDeOperacionNoConcedenLecturaFinanciera()throws Exception{
  var s=d.solicitud();d.admitir(s);
  http.perform(get("/plataforma/metricas-ia/financieras/"+d.caso+"/"+s.operacion())
    .header("Authorization",bearer()).header("X-Brox-Concesion",d.grantOp.toString())
    .param("desde",d.ahora.minusSeconds(60).toString()).param("hasta",Instant.now().plusSeconds(1).toString())).andExpect(status().isForbidden());
  assertEquals(0,d.jdbc.queryForObject("SELECT count(*) FROM b0_consulta_metricas_ia WHERE concesion=?",Integer.class,d.grantOp));
 }
 @Test void revocarLaConcesionCortaLecturaHttpSinCambiarIdentidad()throws Exception{
  var s=d.solicitud();d.admitir(s);String token=bearer();
  var dato=Map.of("concesion",d.grantFin,"motivo","TEST REVOCACION HTTP");
  d.atribuciones.revocar(d.grantFin,d.decision("REVOCAR_ATRIBUCION",dato,d.cuenta),"TEST REVOCACION HTTP",d.admin);
  http.perform(get("/plataforma/metricas-ia/financieras/"+d.caso+"/"+s.operacion())
    .header("Authorization",token).header("X-Brox-Concesion",d.grantFin.toString())
    .param("desde",d.ahora.minusSeconds(60).toString()).param("hasta",Instant.now().plusSeconds(1).toString())).andExpect(status().isForbidden());
  assertNotNull(app.getBean(ContextosService.class).exigirIdentidad(d.admin.referenciaSesion(),d.admin.revisionSesion()));
 }
 @Test void confirmacionYRecuperacionHttpUsanGrantExpresoSinConcederLectura()throws Exception{
  var s=d.solicitud();d.admitir(s);d.iniciar(s);var original=d.medicion("0.1");
  d.tx.execute(t->d.presupuesto.conciliar(d.ref(s),original));
  UUID padre=d.dinero.evaluacion(s.caso(),s.operacion()).orElseThrow().tecnica();
  var id=original.mediciones().getFirst().id();var confirmacion=UUID.randomUUID();
  String huella=d.jdbc.queryForObject("SELECT huella FROM b0_medicion_tecnica WHERE id_operacion_b0=? AND id_medicion=?",String.class,padre,id);
  var q=new com.controllocal.service.presupuesto.ConfirmacionesCostoIaB0.Solicitud(confirmacion,id,huella,null,
   "CUENTA_CANONICA_HTTP_TEST","EMISOR_HTTP_TEST","DOC_HTTP_TEST_"+confirmacion,"LINEA_TEST","REV1_TEST",new java.math.BigDecimal("0.2"),
   "2026-11-04","EVIDENCIA SINTETICA NO FACTURA","e".repeat(64),"Corrección TEST sin transferencia");
  var grant=d.conceder(Atribucion.FINANCIERO_PLATFORM,Set.of(Accion.AJUSTAR_COSTO),Set.of(Campo.LEDGER),d.caso,d.fondo,d.cuenta);
  var mapper=app.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);String ruta="/plataforma/metricas-ia/financieras/"+d.caso+"/"+s.operacion();
  for(var sinEscritura:List.of(d.grantFin,d.grantOp))
   http.perform(post(ruta+"/confirmaciones-costo").header("Authorization",bearer()).header("X-Brox-Concesion",sinEscritura.toString())
     .contentType("application/json").content(mapper.writeValueAsBytes(q))).andExpect(status().isForbidden());
  assertEquals(0,d.jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE id_operacion_b0=? AND hecho='CONFIRMACION_COSTO_IA'",Integer.class,padre));
  for(int i=0;i<2;i++){
   var r=http.perform(post(ruta+"/confirmaciones-costo").header("Authorization",bearer()).header("X-Brox-Concesion",grant.toString())
     .contentType("application/json").content(mapper.writeValueAsBytes(q))).andExpect(status().isOk())
     .andExpect(header().string("Cache-Control","no-store")).andReturn();
   var n=mapper.readTree(r.getResponse().getContentAsByteArray());assertEquals(confirmacion.toString(),n.path("confirmacion").asText());
   assertTrue(n.path("aplicada").asBoolean());assertEquals(4,n.size());assertFalse(n.has("historia"));assertFalse(n.has("costoTotalUsd"));
  }
  http.perform(post(ruta+"/aplicacion-costo").header("Authorization",bearer()).header("X-Brox-Concesion",grant.toString())
    .contentType("application/json").content(mapper.writeValueAsBytes(Map.of("confirmacion",confirmacion)))).andExpect(status().isOk());
  http.perform(get(ruta).header("Authorization",bearer()).header("X-Brox-Concesion",grant.toString())
    .param("desde",d.ahora.minusSeconds(60).toString()).param("hasta",Instant.now().plusSeconds(1).toString())).andExpect(status().isForbidden());
  assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE id_operacion_b0=? AND hecho='CONFIRMACION_COSTO_IA'",Integer.class,padre));
  assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE id_operacion_b0=? AND hecho='APLICACION_COSTO_IA'",Integer.class,padre));
  assertEquals(0,new java.math.BigDecimal("0.2").compareTo(d.jdbc.queryForObject("SELECT liquidado FROM b0_fondo_tecnico WHERE id_fondo=?",java.math.BigDecimal.class,d.fondo)));
 }
}
