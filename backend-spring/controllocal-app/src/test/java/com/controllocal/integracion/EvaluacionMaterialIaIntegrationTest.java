package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.*;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.plataforma.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.materialia.*;
import com.controllocal.service.materialia.EvaluacionMaterialIa.*;
import com.controllocal.service.materialia.openai.*;
import com.controllocal.service.materialia.MaterialIaService.Accion;
import com.controllocal.service.materialia.FuentesMaterialIa.DestinoTratamiento;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.archivo.*;
import com.controllocal.domain.presupuesto.UnidadesDeConsumo.*;
import com.controllocal.persistence.presupuesto.EvaluacionesIaB0Repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.nio.file.Path;
import java.net.http.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.*;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** PG/C1/B0/custodia/journal y adaptadores/parser Main. HTTP, dinero, datos y acreditador TEST: cero proveedor/gasto. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,EvaluacionIaB0IntegrationTest.AcreditacionTest.class},webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class EvaluacionMaterialIaIntegrationTest {
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
 @Autowired ApplicationContext app;@Autowired PlatformTransactionManager gestor;@TempDir Path directorio;
 EvaluacionIaB0IntegrationTest d;EvaluacionMaterialIa consumidor;UUID decisionPlan;AtomicInteger gen,qc;
 String huellaPreparacion;AtomicReference<String> entradaQc=new AtomicReference<>();
 Runnable despuesGen=()->{},despuesQc=()->{},alLeerCredencialQc=()->{};boolean qcSinCredencial,observarHttpSintetico;
 @BeforeEach void preparar(TestInfo info){d=new EvaluacionIaB0IntegrationTest();app.getAutowireCapableBeanFactory().autowireBean(d);d.directorio=directorio;d.preparar(info);gen=new AtomicInteger();qc=new AtomicInteger();}
 @AfterEach void limpiar(){d.limpiar();}
 Tarifa tarifa(String clase){return new Tarifa(clase,"token",new BigDecimal("0.01"),new BigDecimal("1000"));}
 void acreditar(UUID ref,String accion,Object dato){d.acreditador.decisiones.put(ref,new AcreditacionesTitularPlataforma.Decision(ref,d.cuenta,null,accion,
  com.controllocal.service.soporte.Idempotencia.huellaExacta(d.json.objetoCanonico(dato)),"FUENTE SINTETICA TEST SIN AUTORIZACION REAL",Instant.now().minusSeconds(2).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusNanos(123456789)));}
 void nativo(boolean fotoInvalida,boolean retirarFuente)throws Exception{
  var fuente=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(64,64,BufferedImage.TYPE_INT_RGB),"png",fuente);byte[] originalNativo=fuente.toByteArray();
  d.plan=UUID.randomUUID();d.caso=UUID.randomUUID();d.archivo=UUID.randomUUID();d.version=UUID.randomUUID().toString();decisionPlan=UUID.randomUUID();
  var eg=new ConfiguracionEdicionOpenAi(d.plan.toString(),d.version,"EDICION_TEST",
   new ConfiguracionEdicionOpenAi.Parametros("medium","png",null,"opaque",1024,1024,1048576),
   new ConfiguracionEdicionOpenAi.Envoltura("ENV_TEST","a".repeat(64),16384,80000,20),
   new ConfiguracionEdicionOpenAi.ContratoUsage("USAGE_TEST","TEST NO CALIBRACION","TARIFA_TEST","texto","imagen","imagen_salida",List.of(tarifa("texto"),tarifa("imagen"),tarifa("imagen_salida"))),Duration.ofSeconds(1),Duration.ofSeconds(2));
  List<Tarifa> tarifas=List.of("entrada","cache","escritura30","escritura12","salida","razonamiento").stream().map(this::tarifa).toList();
  String rubrica="RUBRICA SINTETICA TEST SIN VALIDEZ OPERATIVA";
  var eq=new ConfiguracionQcOpenAi(List.of(new ConfiguracionQcOpenAi.Ruta(Accion.MEJORAR_FOTO,d.plan.toString(),d.version,"QC_TEST","RUBRICA_TEST",
   com.controllocal.service.soporte.Idempotencia.huellaExacta(rubrica),"EVIDENCIA_TEST",rubrica,
   new ConfiguracionRespuestasOpenAi.Parametros("low","auto",50),new ConfiguracionRespuestasOpenAi.Envoltura("ENV_QC_TEST","b".repeat(64),4000,4000,20000,100000,20000,10000,30),
   new ConfiguracionRespuestasOpenAi.ContratoUsage("USAGE_TEST","EVIDENCIA_TEST","TARIFA_TEST","entrada","cache","escritura30","escritura12","salida","razonamiento",tarifas),
   new ConfiguracionQcOpenAi.Cache("implicit","30m","TEST NO AHORRO REAL"),0)),Duration.ofSeconds(1),Duration.ofSeconds(2));
  var cobertura=new Cobertura("EVALUACION_TEST",List.of("FOTOGRAFIA_SINTETICA_TEST"),"PROVEEDOR TEST","CONDICIONES TEST","TEXTO TEST",
   com.controllocal.service.soporte.Idempotencia.huellaExacta("TEXTO TEST"),"DATOS PROPIOS TEST",decisionPlan,
   List.of(new DestinoTratamiento(ProveedorEdicionOpenAi.IDENTIFICADOR,d.plan.toString(),d.version),new DestinoTratamiento(ControlQcOpenAi.IDENTIFICADOR,d.plan.toString(),d.version)),true);
  var fotos=app.getBean(EvaluacionTecnicaFoto.class);var cp=new CasoPreparado(d.caso,"AJUSTE",Accion.MEJORAR_FOTO,EvaluacionIaB0IntegrationTest.hash(originalNativo),originalNativo.length,"image/png",
   EvaluacionTecnicaFoto.VERSION,fotos.evaluar(originalNativo).huella(),Map.of("decimal_preciso",new BigDecimal("0.1000000000000000001"),"cientifico",new BigDecimal("1E+2")),"MEJORAR SINTETICO TEST",eg,eq,
   com.controllocal.service.soporte.Idempotencia.huellaExacta(d.json.objetoCanonico(eg)),com.controllocal.service.soporte.Idempotencia.huellaExacta(d.json.objetoCanonico(eq)),cobertura);
  huellaPreparacion=com.controllocal.service.soporte.Idempotencia.huellaExacta(d.json.objetoCanonico(cp));
  var etapas=List.of(new EvaluacionesIaB0.EtapaPermitida("GENERACION",ProveedorEdicionOpenAi.IDENTIFICADOR,ProveedorEdicionOpenAi.MODELO,"TARIFA_TEST",
   eg.usage().tarifas().stream().map(t->new EvaluacionesIaB0.LimiteUnidad(t.clase(),t.unidad(),new BigDecimal("1000"))).toList()),
   new EvaluacionesIaB0.EtapaPermitida(ControlesMaterialIa.ETAPA_FINANCIERA,ControlQcOpenAi.IDENTIFICADOR,"gpt-6.1-sol","TARIFA_TEST",
   tarifas.stream().map(t->new EvaluacionesIaB0.LimiteUnidad(t.clase(),t.unidad(),new BigDecimal("1000"))).toList()));
  var plan=new Plan(d.plan,d.version,"a".repeat(64),"EVALUACION_TEST",d.json.objetoCanonico(new EvaluacionesIaB0.Configuracion(etapas)),
   d.json.objetoCanonico(new Manifiesto("C6-EVALUACION-1",List.of(cp))),"RUBRICA TEST","POLITICA TEST SIN TTL PRODUCTIVO","OPENAI TEST",d.namespace,new BigDecimal("0.25"),2,60000,1);
  acreditar(decisionPlan,"REGISTRAR_PLAN_EVALUACION",plan);d.evaluaciones.registrarPlan(plan,decisionPlan,d.admin);
  var caso=new Caso(d.caso,d.plan,d.version,"CASO_TEST",EvaluacionIaB0IntegrationTest.hash(originalNativo),"SINTETICO TEST","DATOS PROPIOS TEST",d.archivo,originalNativo.length,"image/png");
  UUID dc=UUID.randomUUID();acreditar(dc,"REGISTRAR_CASO_EVALUACION",caso);d.evaluaciones.registrarCaso(caso,dc,d.admin);
  var cuentaOp=d.crearCuenta();long idOp=app.getBean(ContextosService.class).exigirIdentidad(cuentaOp.referenciaSesion(),cuentaOp.revisionSesion()).identidad().idCuenta();
  d.grantOp=d.conceder(Atribucion.OPERACION_IA_PLATFORM,Set.of(com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.ADMITIR,
   com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.INICIAR,com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.CONSULTAR_ESTADO),Set.of(Campo.ESTADO),d.caso,null,idOp);
  d.operador=EvaluacionIaB0IntegrationTest.conGrant(cuentaOp,d.grantOp);
  d.grantFin=d.conceder(Atribucion.FINANCIERO_PLATFORM,Set.of(com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.FINANCIAR,com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.LEER_LEDGER),Set.of(Campo.LEDGER),null,d.fondo,d.cuenta);
  d.financiero=EvaluacionIaB0IntegrationTest.conGrant(d.admin,d.grantFin);d.fin=UUID.randomUUID();
  d.evaluaciones.financiar(d.fin,d.plan,d.version,d.fondo,new BigDecimal("0.5"),Instant.now().minusSeconds(1),Instant.now().plusSeconds(1200),"FINANCIACION TEST NO FONDOS REALES",d.financiero);
  d.archivos.guardarOriginal(d.caso,d.archivo,"image/png",originalNativo,d.operador);
  var imagen=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(1024,1024,BufferedImage.TYPE_INT_RGB),"png",imagen);byte[] salida=imagen.toByteArray();
  ObjectMapper mapper=app.getBean(ObjectMapper.class);String req="req_TEST_"+UUID.randomUUID(),resp="resp_TEST_"+UUID.randomUUID();
  NativosEvaluacionMaterialIa factory=(g,q)->new NativosEvaluacionMaterialIa.Sesion(
   new ProveedorEdicionOpenAi(g,()->"CLAVE_TEST_NO_REAL",(p,max,total,contextoIntento,observador)->{
    if(observarHttpSintetico)observador.accept(observacionSintetica(contextoIntento));
    gen.incrementAndGet();if(retirarFuente)d.acreditador.decisiones.remove(decisionPlan);despuesGen.run();
    return http(mapper.writeValueAsBytes(Map.of("data",List.of(Map.of("b64_json",Base64.getEncoder().encodeToString(fotoInvalida?new byte[]{1,2,3}:salida))),
     "size","1024x1024","output_format","png","usage",Map.of("input_tokens",30,"output_tokens",40,"total_tokens",70,"input_tokens_details",Map.of("text_tokens",10,"image_tokens",20)))),req);
   }),new ControlQcOpenAi(q,()->{alLeerCredencialQc.run();return qcSinCredencial?null:"CLAVE_TEST_NO_REAL";},(p,max,total,contextoIntento,observador)->{
    if(observarHttpSintetico)observador.accept(observacionSintetica(contextoIntento));
    qc.incrementAndGet();var solicitud=mapper.readTree(cuerpo(p));entradaQc.set(solicitud.path("input").get(0).path("content").get(0).path("text").asText());despuesQc.run();return http(mapper.writeValueAsBytes(Map.of("id",resp,"model","gpt-6.1-sol","status","completed","service_tier","default",
     "usage",Map.of("input_tokens",10,"output_tokens",5,"total_tokens",15,"input_tokens_details",Map.of("cached_tokens",3,"cache_write_tokens",2,
      "cache_write_tokens_details",Map.of("ttl_30m_tokens",1,"ttl_12h_tokens",1)),"output_tokens_details",Map.of("reasoning_tokens",2)),
     "output",List.of(Map.of("type","message","role","assistant","status","completed","content",List.of(Map.of("type","output_text","text","{}")))))),null);
   },fotos),()->{});
  var beans=new StaticListableBeanFactory();beans.addBean("nativos",factory);
  consumidor=new EvaluacionMaterialIa(d.presupuesto,d.evaluaciones,d.archivos,d.uso,d.json,mapper,fotos,beans.getBeanProvider(NativosEvaluacionMaterialIa.class),gestor,d.planes);
 }
 static ObservacionHttpIa observacionSintetica(TransporteOpenAi.ContextoIntento c){return new ObservacionHttpIa(ObservacionHttpIa.ESQUEMA,
  c.claveIntento(),c.proveedor(),c.modelo(),c.versionConfiguracion(),Instant.now(),123456789L,
  ObservacionHttpIa.Resultado.RESPUESTA_COMPLETA,200,true);}
 @Test void consumidorConservaDosObservacionesGenQcYSusMedicionesSinDuplicarAlReanudar()throws Exception{
  nativo(false,false);observarHttpSintetico=true;UUID comando=UUID.randomUUID();
  assertEquals("LIQUIDADA",consumidor.ejecutar(d.caso,comando,d.fin,d.operador).estado());
  assertEquals("LIQUIDADA",consumidor.recuperar(d.caso,comando,d.fin,d.operador).estado());
  assertEquals(1,gen.get());assertEquals(1,qc.get());
  assertEquals(2,d.jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE id_caso_evaluacion=? AND id_operacion=? AND hecho='OBSERVACION_HTTP_IA'",Integer.class,d.caso,comando));
  assertEquals(2,d.jdbc.queryForObject("SELECT count(DISTINCT contenido->>'claveIntento') FROM b0_antecedente WHERE id_caso_evaluacion=? AND hecho='OBSERVACION_HTTP_IA'",Integer.class,d.caso));
  assertEquals(2,d.jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_caso_evaluacion=? AND id_operacion=?",Integer.class,d.caso,comando));
 }
 static byte[] cuerpo(java.net.http.HttpRequest peticion)throws java.io.IOException{
  var bytes=new ByteArrayOutputStream();var fin=new CompletableFuture<byte[]>();
  peticion.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<java.nio.ByteBuffer>(){
   public void onSubscribe(Flow.Subscription s){s.request(Long.MAX_VALUE);}
   public void onNext(java.nio.ByteBuffer b){byte[] c=new byte[b.remaining()];b.get(c);bytes.writeBytes(c);}
   public void onError(Throwable t){fin.completeExceptionally(t);}
   public void onComplete(){fin.complete(bytes.toByteArray());}
  });
  try{return fin.get(10,TimeUnit.SECONDS);}catch(Exception e){throw new java.io.IOException("No se capturó el cuerpo TEST",e);}
 }
 SolicitudEvaluacion solicitudNativa(UUID comando){return new SolicitudEvaluacion(comando,d.caso,d.version,"a".repeat(64),d.fin,huellaPreparacion);}
 List<Object> retenciones(){return List.of(d.jdbc.queryForObject("SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,d.fondo),
  d.jdbc.queryForObject("SELECT reservados FROM b0_uso_capacidad_fisica WHERE namespace_hash=?",Long.class,d.namespace));}
 void retirarGrant(){
  long cuenta=app.getBean(ContextosService.class).exigirIdentidad(d.operador.referenciaSesion(),d.operador.revisionSesion()).identidad().idCuenta();
  String motivo="RETIRADA TEST DURANTE PROVEEDOR SIN GASTO REAL";var dato=Map.of("concesion",d.grantOp,"motivo",motivo);
  UUID decision=d.decision("REVOCAR_ATRIBUCION",dato,cuenta);
  d.tx.executeWithoutResult(t->d.atribuciones.revocar(d.grantOp,decision,motivo,d.admin));
 }
 void cortarDespuesDeRecibir(){
  var archivos=spy(d.archivos);
  doAnswer(i->{i.callRealMethod();throw new ConflictoException("SABOTAJE TEST después del commit de recepción");}).when(archivos).recibir(any(),any(),any(),any());
  org.springframework.test.util.ReflectionTestUtils.setField(consumidor,"archivos",archivos);
 }
 static TransporteOpenAi.Respuesta http(byte[] body,String request){return new TransporteOpenAi.Respuesta(200,HttpHeaders.of(request==null?Map.of("Content-Type",List.of("application/json")):Map.of("Content-Type",List.of("application/json"),"x-request-id",List.of(request)),(a,b)->true),body);}
 @ParameterizedTest @ValueSource(booleans={false,true})
 void claimGenSinRespuestaConservaIncertidumbreYRetencionesSinPost(boolean ejecutar)throws Exception{
  nativo(false,false);UUID comando=UUID.randomUUID();var solicitud=solicitudNativa(comando);d.admitir(solicitud);d.iniciar(solicitud);var antes=retenciones();
  var r=ejecutar?consumidor.ejecutar(d.caso,comando,d.fin,d.operador):consumidor.recuperar(d.caso,comando,d.fin,d.operador);
  assertEquals("INCIERTA",r.estado());assertEquals(antes,retenciones());assertEquals(0,gen.get());assertEquals(0,qc.get());
  assertEquals("INCIERTA",consumidor.recuperar(d.caso,comando,d.fin,d.operador).estado());assertEquals(antes,retenciones());
  assertEquals(0,d.jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_caso_evaluacion=?",Integer.class,d.caso));
 }
 @ParameterizedTest @ValueSource(booleans={false,true})
 void claimQcSinRespuestaQuedaInciertoSinRepetirNiCancelarPendiente(boolean ejecutar)throws Exception{
  nativo(false,false);UUID comando=UUID.randomUUID();cortarDespuesDeRecibir();
  assertThrows(ConflictoException.class,()->consumidor.ejecutar(d.caso,comando,d.fin,d.operador));
  var ref=new ReferenciaEvaluacion(d.caso,comando);var solicitud=solicitudNativa(comando);String etapa=ControlesMaterialIa.ETAPA_FINANCIERA;
  d.tx.executeWithoutResult(t->{d.presupuesto.programarEtapa(ref,etapa,ControlQcOpenAi.IDENTIFICADOR,"a".repeat(64));assertTrue(d.presupuesto.iniciarEtapaEvaluacion(solicitud,etapa,d.operador));});
  var antes=retenciones();var r=ejecutar?consumidor.ejecutar(d.caso,comando,d.fin,d.operador):consumidor.recuperar(d.caso,comando,d.fin,d.operador);
  assertEquals("INCIERTA",r.estado());assertEquals("INCIERTA",d.tx.execute(t->d.presupuesto.estadoEtapaEvaluacion(ref,etapa)).orElseThrow());
  assertEquals(antes,retenciones());assertEquals(1,gen.get());assertEquals(0,qc.get());
  assertEquals("INCIERTA",consumidor.recuperar(d.caso,comando,d.fin,d.operador).estado());assertEquals(antes,retenciones());
  assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_caso_evaluacion=?",Integer.class,d.caso));
 }
 @Test void mapaDelManifiestoConservaDecimalYNotacionCientificaEnHashYQc()throws Exception{
  nativo(false,false);UUID comando=UUID.randomUUID();consumidor.ejecutar(d.caso,comando,d.fin,d.operador);
  assertEquals(huellaPreparacion,d.jdbc.queryForObject("SELECT contenido->'solicitud'->>'huellaContenido' FROM b0_antecedente WHERE id_caso_evaluacion=? AND hecho='RESERVA_ADMITIDA'",String.class,d.caso));
  var datos=(Map<?,?>)d.json.comoObjeto(entradaQc.get(),Map.class).get("datos_permitidos");
  assertEquals(new BigDecimal("0.1000000000000000001"),datos.get("decimal_preciso"));assertEquals(new BigDecimal("1E+2"),datos.get("cientifico"));
  consumidor.recuperar(d.caso,comando,d.fin,d.operador);assertEquals(1,gen.get());assertEquals(1,qc.get());
 }
 @Test void grantRetiradoDuranteQcDeniegaEntregaTrasCommitSinReabrirEtapa()throws Exception{
  nativo(false,false);despuesQc=this::retirarGrant;UUID comando=UUID.randomUUID();
  assertThrows(AccesoNoAutorizadoException.class,()->consumidor.ejecutar(d.caso,comando,d.fin,d.operador));
  var ref=new ReferenciaEvaluacion(d.caso,comando);assertEquals("LIQUIDADA",d.dinero.evaluacion(d.caso,comando).orElseThrow().estado());
  assertEquals("MEDIDA",d.tx.execute(t->d.presupuesto.estadoEtapaEvaluacion(ref,ControlesMaterialIa.ETAPA_FINANCIERA)).orElseThrow());
  assertEquals(1,gen.get());assertEquals(1,qc.get());assertEquals(2,d.jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_caso_evaluacion=?",Integer.class,d.caso));
 }
 @Test void grantRetiradoDuranteGenSinFotoNoRevierteCostosNiLiberacion()throws Exception{
  nativo(true,false);despuesGen=this::retirarGrant;UUID comando=UUID.randomUUID();
  assertThrows(AccesoNoAutorizadoException.class,()->consumidor.ejecutar(d.caso,comando,d.fin,d.operador));
  assertEquals("LIQUIDADA",d.dinero.evaluacion(d.caso,comando).orElseThrow().estado());assertEquals(1,gen.get());assertEquals(0,qc.get());
  assertEquals("CANCELADA",d.jdbc.queryForObject("SELECT estado FROM b0_reserva_almacenamiento WHERE id_caso_evaluacion=? AND operacion_evaluacion_b0 IS NOT NULL",String.class,d.caso));
  assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_caso_evaluacion=?",Integer.class,d.caso));
 }
 @Test void noInvocacionQcConGrantRetiradoDeniegaSoloEntregaTrasResolver()throws Exception{
  nativo(false,false);qcSinCredencial=true;alLeerCredencialQc=this::retirarGrant;UUID comando=UUID.randomUUID();
  assertThrows(AccesoNoAutorizadoException.class,()->consumidor.ejecutar(d.caso,comando,d.fin,d.operador));
  var ref=new ReferenciaEvaluacion(d.caso,comando);assertEquals("LIQUIDADA",d.dinero.evaluacion(d.caso,comando).orElseThrow().estado());
  assertEquals("NO_INVOCADA",d.tx.execute(t->d.presupuesto.estadoEtapaEvaluacion(ref,ControlesMaterialIa.ETAPA_FINANCIERA)).orElseThrow());
  assertEquals(1,gen.get());assertEquals(0,qc.get());assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_caso_evaluacion=?",Integer.class,d.caso));
 }
 @Test void titularDistintoDosPostControlNullYReplaySinReprocesar()throws Exception{
  nativo(false,false);UUID comando=UUID.randomUUID();assertEquals("LIQUIDADA",consumidor.ejecutar(d.caso,comando,d.fin,d.operador).estado());
  long autor=d.dinero.evaluacion(d.caso,comando).orElseThrow().cuenta();
  var otra=d.crearCuenta();long id=app.getBean(ContextosService.class).exigirIdentidad(otra.referenciaSesion(),otra.revisionSesion()).identidad().idCuenta();
  var grant=d.conceder(Atribucion.OPERACION_IA_PLATFORM,Set.of(com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.INICIAR,
   com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.CONSULTAR_ESTADO),Set.of(Campo.ESTADO),d.caso,null,id);
  assertEquals("LIQUIDADA",consumidor.ejecutar(d.caso,comando,d.fin,EvaluacionIaB0IntegrationTest.conGrant(otra,grant)).estado());
  assertEquals(autor,d.dinero.evaluacion(d.caso,comando).orElseThrow().cuenta());assertEquals(1,gen.get());assertEquals(1,qc.get());
  assertEquals(2,d.jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_caso_evaluacion=?",Integer.class,d.caso));
  assertEquals(2,d.jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE id_caso_evaluacion=? AND hecho='RESPUESTA_EVALUACION'",Integer.class,d.caso));
  assertEquals(2,d.jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_caso_evaluacion=? AND estado_costo='CALCULADO'",Integer.class,d.caso));
  assertNull(d.dinero.evaluacion(d.caso,comando).orElseThrow().organizacion());assertNull(d.dinero.evaluacion(d.caso,comando).orElseThrow().perfil());
 }
 @Test void fuenteRetiradaDespuesDelGenConservaUsageYSuspendeQc()throws Exception{
  nativo(false,true);UUID comando=UUID.randomUUID();assertThrows(AccesoNoAutorizadoException.class,()->consumidor.ejecutar(d.caso,comando,d.fin,d.operador));
  assertEquals(1,gen.get());assertEquals(0,qc.get());assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_caso_evaluacion=?",Integer.class,d.caso));
  assertTrue(d.presupuesto.leerRespuestaEvaluacion(new ReferenciaEvaluacion(d.caso,comando),null).isPresent());
 }
 @Test void fotoInvalidaFacturableNoQcNiNoProcesadaYLiberaUnaVez()throws Exception{
  nativo(true,false);var presupuesto=spy(d.presupuesto);
  doAnswer(invocacion->{
   var r=(RespuestaEvaluacion)invocacion.callRealMethod();
   if(r.etapa()==null)presupuesto.programarEtapa(invocacion.getArgument(0),ControlesMaterialIa.ETAPA_FINANCIERA,ControlQcOpenAi.IDENTIFICADOR,"a".repeat(64));
   return r;
  }).when(presupuesto).conservarRespuestaEvaluacion(any(),isNull(),anyString(),anyString(),any(),any());
  org.springframework.test.util.ReflectionTestUtils.setField(consumidor,"b0",presupuesto);
  UUID comando=UUID.randomUUID();assertEquals("LIQUIDADA",consumidor.ejecutar(d.caso,comando,d.fin,d.operador).estado());
  assertEquals("NO_INVOCADA",d.presupuesto.estadoEtapaEvaluacion(new ReferenciaEvaluacion(d.caso,comando),ControlesMaterialIa.ETAPA_FINANCIERA).orElseThrow());
  assertEquals("LIQUIDADA",consumidor.recuperar(d.caso,comando,d.fin,d.operador).estado());assertEquals(1,gen.get());assertEquals(0,qc.get());
  assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE id_caso_evaluacion=? AND hecho='SALIDA_SIN_BINARIO'",Integer.class,d.caso));
  assertEquals("CANCELADA",d.jdbc.queryForObject("SELECT estado FROM b0_reserva_almacenamiento WHERE id_caso_evaluacion=? AND operacion_evaluacion_b0 IS NOT NULL",String.class,d.caso));
 }
 @Test void genValidoPerdidoNoSeConvierteEnSalidaSinBinarioNiRegenera()throws Exception{
  nativo(false,false);var archivos=spy(d.archivos);doThrow(new ConflictoException("SABOTAJE TEST antes de writer")).when(archivos).recibir(any(),any(),any(),any());
  // Reutiliza la fábrica ya instalada del consumidor real; sólo sabotea el writer antes de iniciar139.
  org.springframework.test.util.ReflectionTestUtils.setField(consumidor,"archivos",archivos);UUID comando=UUID.randomUUID();
  assertThrows(ConflictoException.class,()->consumidor.ejecutar(d.caso,comando,d.fin,d.operador));
  assertThrows(ConflictoException.class,()->consumidor.recuperar(d.caso,comando,d.fin,d.operador));
  assertEquals(1,gen.get());assertEquals(0,qc.get());assertEquals(0,d.jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE id_caso_evaluacion=? AND hecho='SALIDA_SIN_BINARIO'",Integer.class,d.caso));
  assertEquals("RESERVADA",d.jdbc.queryForObject("SELECT estado FROM b0_reserva_almacenamiento WHERE id_caso_evaluacion=? AND operacion_evaluacion_b0 IS NOT NULL",String.class,d.caso));
 }
 @Test void recuperacionSinOperacionNoAdmiteNiInvoca()throws Exception{
  nativo(false,false);assertThrows(NoEncontradoException.class,()->consumidor.recuperar(d.caso,UUID.randomUUID(),d.fin,d.operador));assertEquals(0,gen.get());assertEquals(0,qc.get());
 }
 @Test void cualquierIntento139SinCustodiaImpideLiberarSalidaAusente()throws Exception{
  nativo(true,false);var archivos=spy(d.archivos);
  doAnswer(invocacion->{
   ReferenciaEvaluacion ref=invocacion.getArgument(0);var r=d.presupuesto.leerRespuestaEvaluacion(ref,null).orElseThrow();
   var descriptor=r.descriptor();var seguimiento=app.getBean(SeguimientoSalidaBinaria.class);
   d.tx.executeWithoutResult(t->seguimiento.preparar(new PropietarioBinario.CasoEvaluacion(ref.caso()),descriptor.operacionIo(),"SABOTAJE TECNICO TEST",d.namespace,"png","image/png",EvaluacionIaB0IntegrationTest.hash(d.png),d.png.length));
   return invocacion.callRealMethod();
  }).when(archivos).liberarSalidaSinBinario(any());
  org.springframework.test.util.ReflectionTestUtils.setField(consumidor,"archivos",archivos);UUID comando=UUID.randomUUID();
  assertThrows(ConflictoException.class,()->consumidor.ejecutar(d.caso,comando,d.fin,d.operador));
  assertEquals("LIQUIDADA",d.dinero.evaluacion(d.caso,comando).orElseThrow().estado());
  assertEquals("RESERVADA",d.jdbc.queryForObject("SELECT estado FROM b0_reserva_almacenamiento WHERE id_caso_evaluacion=? AND operacion_evaluacion_b0 IS NOT NULL",String.class,d.caso));
  assertEquals(1,gen.get());assertEquals(0,qc.get());
 }
 @Test void recuperarConGastoOffNoReclamaNiCierraQcPendiente()throws Exception{
  nativo(false,false);var archivos=spy(d.archivos);
  doAnswer(invocacion->{invocacion.callRealMethod();throw new ConflictoException("SABOTAJE TEST después de recepción comprometida");}).when(archivos).recibir(any(),any(),any(),any());
  org.springframework.test.util.ReflectionTestUtils.setField(consumidor,"archivos",archivos);UUID comando=UUID.randomUUID();
  assertThrows(ConflictoException.class,()->consumidor.ejecutar(d.caso,comando,d.fin,d.operador));
  var ref=new ReferenciaEvaluacion(d.caso,comando);d.tx.executeWithoutResult(t->d.presupuesto.programarEtapa(ref,ControlesMaterialIa.ETAPA_FINANCIERA,ControlQcOpenAi.IDENTIFICADOR,"a".repeat(64)));
  org.springframework.test.util.ReflectionTestUtils.setField(consumidor,"b0",d.servicio(false));
  assertEquals("EN_PROCESO",consumidor.recuperar(d.caso,comando,d.fin,d.operador).estado());
  assertEquals("PENDIENTE",d.presupuesto.estadoEtapaEvaluacion(ref,ControlesMaterialIa.ETAPA_FINANCIERA).orElseThrow());assertEquals(1,gen.get());assertEquals(0,qc.get());
 }
 @Test void journalConservaNotacionCientificaYReplayOriginal(){
  var s=d.solicitud();d.admitir(s);d.iniciar(s);var ref=d.ref(s);
  var m=new ResultadoFacturable(List.of(new MedicionFacturable(UUID.randomUUID(),"PRV_TEST","MODELO_TEST","TARIFA_TEST",List.of(new Medicion("u","u",new BigDecimal("1E+2"),null)),
   List.of(new Tarifa("u","u",new BigDecimal("1E-4"),new BigDecimal("1E+2"))),null,"REFERENCIA_CIENTIFICA_TEST_"+UUID.randomUUID())));
  String original=d.json.objetoCanonico(m);var r=d.tx.execute(t->d.presupuesto.conservarRespuestaEvaluacion(ref,null,PresupuestoIaService.claveDeIntento(ref),"{}",m,null));
  var leida=d.presupuesto.leerRespuestaEvaluacion(ref,null).orElseThrow();assertEquals(original,leida.medicionOriginal());assertEquals(original,d.json.objetoCanonico(d.presupuesto.medicionRespuestaEvaluacion(leida)));
  assertEquals(r,d.tx.execute(t->d.presupuesto.conservarRespuestaEvaluacion(ref,null,PresupuestoIaService.claveDeIntento(ref),"{}",m,null)));
 }
 @Test void appendConcurrenteConservaUnHechoYUnaMedicion()throws Exception{
  var s=d.solicitud();d.admitir(s);d.iniciar(s);var ref=d.ref(s);var m=d.medicion("0.1");var barrera=new CyclicBarrier(2);var pids=ConcurrentHashMap.<Long>newKeySet();
  var pool=Executors.newFixedThreadPool(2);
  try{Callable<RespuestaEvaluacion> tarea=()->d.tx.execute(t->{
    pids.add(d.jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));
    try{barrera.await(10,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException("Barrera TEST no alcanzada",e);}
    return d.presupuesto.conservarRespuestaEvaluacion(ref,null,PresupuestoIaService.claveDeIntento(ref),"{}",m,null);
   });
   var a=pool.submit(tarea);var b=pool.submit(tarea);assertEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));assertEquals(2,pids.size());
  }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
  assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE id_caso_evaluacion=? AND hecho='RESPUESTA_EVALUACION'",Integer.class,d.caso));
  assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_caso_evaluacion=?",Integer.class,d.caso));
 }
}
