package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.presupuesto.ConfirmacionesCostoIaB0.*;
import com.controllocal.service.presupuesto.ConfirmacionesCostoIaB0.Solicitud;
import com.controllocal.service.soporte.Idempotencia;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** PG/R25/B0 reales, proveedor/financiación/grants estrictamenteTEST. Ninguna llamada externa. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,EvaluacionIaB0IntegrationTest.AcreditacionTest.class},webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"spring.datasource.hikari.maximum-pool-size=5","spring.datasource.hikari.minimum-idle=0"})
class ConfirmacionesCostoIaB0IntegrationTest {
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
 @Autowired ApplicationContext app;@Autowired ConfirmacionesCostoIaB0 costos;@Autowired MetricasIaB0 metricas;@TempDir Path directorio;
 EvaluacionIaB0IntegrationTest d;TestInfo infoActual;SesionPlataforma escritor;UUID grant;SolicitudEvaluacion s;ResultadoFacturable original;
 static BigDecimal n(String x){return new BigDecimal(x);}
 @BeforeEach void preparar(TestInfo info){
  infoActual=info;d=new EvaluacionIaB0IntegrationTest();app.getAutowireCapableBeanFactory().autowireBean(d);d.directorio=directorio;d.preparar(info);
  grant=d.conceder(Atribucion.FINANCIERO_PLATFORM,Set.of(Accion.AJUSTAR_COSTO),Set.of(Campo.LEDGER),d.caso,d.fondo,d.cuenta);
  escritor=EvaluacionIaB0IntegrationTest.conGrant(d.admin,grant);s=d.solicitud();d.admitir(s);d.iniciar(s);original=d.medicion("0.1");
 }
 @AfterEach void limpiar(){d.limpiar();}
 void cerrar(){d.tx.execute(t->d.presupuesto.conciliar(d.ref(s),original));}
 UUID tecnica(){return d.dinero.evaluacion(s.caso(),s.operacion()).orElseThrow().tecnica();}
 Solicitud solicitud(UUID id,UUID anterior,UUID medicion,String importe,String revision){
  String huella=d.jdbc.queryForObject("SELECT huella FROM b0_medicion_tecnica WHERE id_operacion_b0=? AND id_medicion=?",String.class,tecnica(),medicion);
  return new Solicitud(id,medicion,huella,anterior,"CUENTA_PROVEEDOR_TEST","EMISOR_CANONICO_TEST","DOCUMENTO_TEST_"+medicion,"LINEA_TEST",revision,n(importe),"2026-11-04","EVIDENCIA TEST NO FACTURA REAL","e".repeat(64),"CORRECCION TEST SIN TRANSFERENCIA");
 }
 Solicitud primera(String importe){return solicitud(UUID.randomUUID(),null,original.mediciones().getFirst().id(),importe,"REV1_TEST");}
 Recibo confirmar(Solicitud q){return costos.confirmar(s.caso(),s.operacion(),q,escritor);}
 long hechos(String tipo){return d.jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE id_operacion_b0=? AND hecho=?",Long.class,tecnica(),tipo);}
 BigDecimal fondo(){return d.jdbc.queryForObject("SELECT liquidado FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,d.fondo);}
 BigDecimal efectivo(){return d.dinero.proyeccionCosto(tecnica()).orElseThrow().efectivo();}
 MetricasIaB0.Financiera leer(){return metricas.financiera(d.financiero,s.caso(),s.operacion(),d.ahora.minusSeconds(5),Instant.now().plusSeconds(5));}
 @Test void confirmaYAplicaAutomaticamenteSinReescribirOriginalNiConcederLectura(){
  cerrar();var op=d.dinero.evaluacion(s.caso(),s.operacion()).orElseThrow();String hash=op.resultado();var q=primera("0.2");
  assertTrue(confirmar(q).aplicada());assertEquals(0,n("0.2").compareTo(fondo()));assertEquals(0,n("0.2").compareTo(efectivo()));
  assertEquals("LIQUIDADA",d.dinero.evaluacion(s.caso(),s.operacion()).orElseThrow().estado());
  assertEquals(hash,d.dinero.evaluacion(s.caso(),s.operacion()).orElseThrow().resultado());
  assertEquals("CALCULADO",d.jdbc.queryForObject("SELECT estado_costo FROM b0_medicion_tecnica WHERE id_operacion_b0=?",String.class,tecnica()));
  assertEquals(0,n("0.1").compareTo(d.jdbc.queryForObject("SELECT costo_tecnico_usd FROM b0_operacion_ia WHERE id_operacion_b0=?",BigDecimal.class,tecnica())));
  assertEquals(1,hechos("CONFIRMACION_COSTO_IA"));assertEquals(1,hechos("APLICACION_COSTO_IA"));
  assertThrows(AccesoNoAutorizadoException.class,()->metricas.financiera(escritor,s.caso(),s.operacion(),d.ahora.minusSeconds(5),Instant.now().plusSeconds(5)));
  var lectura=leer();assertEquals(2,lectura.valoracionTecnica().historia().size());assertEquals(0,n("0.2").compareTo(lectura.valoracionTecnica().efectivoUsd()));
  assertNull(lectura.costoPorTareaCorrecta());assertTrue(original.mediciones().stream().allMatch(m->m.costoConfirmadoUsd()==null));
 }
 @Test void replayNoDuplicaNiPermiteMismoComandoConOtroImporte(){
  cerrar();var q=primera("0.2");var a=confirmar(q);var saldo=fondo();var b=confirmar(q);
  // El hecho y su aplicación son únicos; el motivo informa la condición actual,
  // no reproduce la acción que el primer intento ya dejó conservada.
  assertEquals(a.confirmacion(),b.confirmacion());assertEquals(a.revision(),b.revision());
  assertTrue(a.aplicada());assertTrue(b.aplicada());
  assertEquals("APLICACION_CONSERVADA",a.motivo());assertEquals("SIN_DIFERENCIA_PENDIENTE",b.motivo());
  assertEquals(0,saldo.compareTo(fondo()));
  var distinta=solicitud(q.confirmacion(),null,q.medicion(),"0.21",q.revisionFuente());
  assertThrows(ConflictoException.class,()->confirmar(distinta));assertEquals(1,hechos("CONFIRMACION_COSTO_IA"));assertEquals(1,hechos("APLICACION_COSTO_IA"));
 }
 @Test void noHayWritePorFinanciarLeerOpsNiUuidAjeno(){
  cerrar();var q=primera("0.2");
  for(var sesion:List.of(d.financiero,d.operador,d.qa))assertThrows(AccesoNoAutorizadoException.class,()->costos.confirmar(s.caso(),s.operacion(),q,sesion));
  assertThrows(AccesoNoAutorizadoException.class,()->costos.confirmar(UUID.randomUUID(),s.operacion(),q,escritor));assertEquals(0,hechos("CONFIRMACION_COSTO_IA"));
 }
 @Test void ajusteAbiertoNoLiberaYUnaMedicionPosteriorActualizaConjuntoCompleto(){
  d.tx.executeWithoutResult(t->d.presupuesto.registrarMediciones(d.ref(s),original));var q=primera("0.08");
  assertFalse(confirmar(q).aplicada());assertEquals(0,fondo().signum());
  assertEquals(0,n("0.25").compareTo(d.jdbc.queryForObject("SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,d.fondo)));
  var segunda=d.medicion("0.1");var lista=new ArrayList<>(original.mediciones());lista.addAll(segunda.mediciones());
  var conjunto=new ResultadoFacturable(lista);d.tx.executeWithoutResult(t->d.presupuesto.registrarMediciones(d.ref(s),conjunto));
  assertEquals(0,n("0.18").compareTo(efectivo()));d.tx.execute(t->d.presupuesto.conciliar(d.ref(s),conjunto));
  assertEquals(0,n("0.18").compareTo(fondo()));assertEquals(2,d.jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_operacion_b0=?",Integer.class,tecnica()));
  assertEquals(1,hechos("CONFIRMACION_COSTO_IA"));
 }
 @Test void faltaProvisionConservaHechoYLuegoRectificacionALaBajaNoDuplica(){
  cerrar();d.tx.executeWithoutResult(t->d.jdbc.update("UPDATE b0_fondo_tecnico SET financiado=liquidado+retenido+0.05 WHERE id_fondo=?",d.fondo));
  var q=primera("0.2");assertEquals("PROVISION_INSUFICIENTE",confirmar(q).motivo());assertEquals(1,hechos("CONFIRMACION_COSTO_IA"));
  assertEquals(0,n("0.1").compareTo(fondo()));assertEquals(0,n("0.1").compareTo(d.dinero.proyeccionCosto(tecnica()).orElseThrow().pendiente()));
  var baja=solicitud(UUID.randomUUID(),q.confirmacion(),q.medicion(),"0.08","REV2_TEST");assertTrue(confirmar(baja).aplicada());
  assertEquals(0,n("0.08").compareTo(fondo()));assertEquals(2,hechos("CONFIRMACION_COSTO_IA"));assertEquals(1,hechos("APLICACION_COSTO_IA"));
  assertThrows(ConflictoException.class,()->confirmar(solicitud(UUID.randomUUID(),q.confirmacion(),q.medicion(),"0.09","REV3_TEST")));
 }
 @Test void excesoDeMaximoPlanYFondoNoAutorizaClaimReservadoAntesDelAjuste(){
  cerrar();var reservada=d.solicitud();d.admitir(reservada);
  var q=primera("0.4");assertEquals("LIMITE_TECNICO_ORIGINAL",confirmar(q).motivo());
  assertEquals(0,n("0.1").compareTo(fondo()));assertEquals(0,n("0.4").compareTo(efectivo()));
  assertThrows(ReglaNegocioException.class,()->d.tx.execute(t->d.presupuesto.iniciarEvaluacion(reservada,d.operador)));
  assertEquals("RESERVADA",d.dinero.evaluacion(reservada.caso(),reservada.operacion()).orElseThrow().estado());
  assertThrows(ReglaNegocioException.class,()->d.admitir(d.solicitud()));
 }
 @Test void ceroRealNoSeInfiereDeLaAusenciaYPrecisionNoCuantizaFragmentos(){
  var a=original.mediciones().getFirst();var b=d.medicion("0.0000000000006").mediciones().getFirst();
  original=new ResultadoFacturable(List.of(new MedicionFacturable(a.id(),a.proveedor(),a.modelo(),a.versionTarifa(),a.unidades(),a.tarifasReales(),n("0.0000000000006"),a.referenciaFuente()),b));
  cerrar();assertEquals(0,n("0.000000000001").compareTo(fondo()));
  var q=primera("0");assertTrue(confirmar(q).aplicada());assertEquals(0,n("0.000000000001").compareTo(fondo()));
  assertEquals(0,n("0").compareTo(leer().valoracionTecnica().historia().getFirst().costoTotalUsd()));
  assertEquals(0,n("0.0000000000006").compareTo(d.jdbc.queryForObject("SELECT costo_usd FROM b0_medicion_tecnica WHERE id_operacion_b0=? AND id_medicion=?",BigDecimal.class,tecnica(),a.id())));
 }
 @Test void fuenteNoSeReapropiaConOtroComandoYMedicion(){
  var b=d.medicion("0.1").mediciones().getFirst();var lista=new ArrayList<>(original.mediciones());lista.add(b);original=new ResultadoFacturable(lista);cerrar();
  var q=primera("0.1");confirmar(q);
  var otra=new Solicitud(UUID.randomUUID(),b.id(),d.jdbc.queryForObject("SELECT huella FROM b0_medicion_tecnica WHERE id_operacion_b0=? AND id_medicion=?",String.class,tecnica(),b.id()),null,
    q.cuentaProveedor(),q.emisorFacturacion(),q.documento(),q.linea(),q.revisionFuente(),n("0.1"),q.fechaFuente(),q.evidencia(),q.huellaEvidencia(),q.causa());
  assertThrows(ConflictoException.class,()->confirmar(otra));assertEquals(1,hechos("CONFIRMACION_COSTO_IA"));
 }
 @Test void ajusteDentroDeMaximoOperacionPuedeSuperarTechoTotalDelPlan(){
  cerrar();var ops=new ArrayList<SolicitudEvaluacion>();var medidas=new ArrayList<ResultadoFacturable>();ops.add(s);medidas.add(original);
  for(int i=0;i<2;i++){var x=d.solicitud();var m=d.medicion("0.1");d.admitir(x);d.iniciar(x);d.tx.execute(t->d.presupuesto.conciliar(d.ref(x),m));ops.add(x);medidas.add(m);}
  assertTrue(confirmar(primera("0.24")).aplicada());assertEquals(0,n("0.44").compareTo(fondo()));
  s=ops.get(1);original=medidas.get(1);var segundo=primera("0.24");assertEquals("LIMITE_TECNICO_ORIGINAL",confirmar(segundo).motivo());
  assertEquals(0,n("0.14").compareTo(d.dinero.proyeccionCosto(tecnica()).orElseThrow().pendiente()));
  assertEquals(0,n("0.58").compareTo(d.planes.comprometido(d.fin)));assertEquals(0,n("0.44").compareTo(fondo()));
  assertThrows(ReglaNegocioException.class,()->d.admitir(d.solicitud()));
 }
 @Test void negativoConservadoSinAplicarNoLiberaFondoNiCompromisoDelPlan(){
  cerrar();var q=primera("0.08");var commits=new AtomicInteger();
  var gestor=new PlatformTransactionManager(){
   public TransactionStatus getTransaction(TransactionDefinition def){if(commits.get()==1)throw new IllegalStateException("PAUSA_NEGATIVO_TEST");return d.gestor.getTransaction(def);}
   public void commit(TransactionStatus st){d.gestor.commit(st);commits.incrementAndGet();}public void rollback(TransactionStatus st){d.gestor.rollback(st);}
  };
  assertThrows(IllegalStateException.class,()->conGestor(gestor).confirmar(s.caso(),s.operacion(),q,escritor));
  assertEquals(0,n("0.08").compareTo(efectivo()));assertEquals(0,n("0.1").compareTo(fondo()));assertEquals(0,n("0.1").compareTo(d.planes.comprometido(d.fin)));
  assertTrue(costos.aplicar(s.caso(),s.operacion(),new Recuperacion(q.confirmacion()),escritor).aplicada());
  assertEquals(0,n("0.08").compareTo(fondo()));assertEquals(0,n("0.08").compareTo(d.planes.comprometido(d.fin)));
 }
 @Test void revisionNuevaYAliasAdaptadorNoCambianDuenoNaturalEntreDosOperaciones(){
  cerrar();var primera=primera("0.1");confirmar(primera);
  var otra=d.solicitud();d.admitir(otra);d.iniciar(otra);var base=d.medicion("0.1").mediciones().getFirst();
  var alias=new MedicionFacturable(base.id(),"OTRO_ADAPTADOR_TEST",base.modelo(),base.versionTarifa(),base.unidades(),base.tarifasReales(),null,base.referenciaFuente());
  // Fuera del plan se conserva como incidencia técnica, nunca se inventa una ejecución autorizada.
  assertEquals("INCIERTA",d.tx.execute(t->d.presupuesto.registrarMediciones(d.ref(otra),new ResultadoFacturable(List.of(alias)))).estado());
  var opOtra=d.dinero.evaluacion(otra.caso(),otra.operacion()).orElseThrow();
  String hash=d.jdbc.queryForObject("SELECT huella FROM b0_medicion_tecnica WHERE id_operacion_b0=? AND id_medicion=?",String.class,opOtra.tecnica(),alias.id());
  var intento=new Solicitud(UUID.randomUUID(),alias.id(),hash,null,primera.cuentaProveedor(),primera.emisorFacturacion(),
    primera.documento(),primera.linea(),"REV2_TEST",n("0.1"),primera.fechaFuente(),primera.evidencia(),primera.huellaEvidencia(),primera.causa());
  var fallo=assertThrows(ConflictoException.class,()->costos.confirmar(otra.caso(),otra.operacion(),intento,escritor));assertTrue(fallo.getMessage().contains("dueño"));
  assertEquals(0,d.jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE id_operacion_b0=? AND hecho='CONFIRMACION_COSTO_IA'",Integer.class,opOtra.tecnica()));
  var valida=solicitud(UUID.randomUUID(),primera.confirmacion(),primera.medicion(),"0.12","REV2_TEST");assertTrue(confirmar(valida).aplicada());
  assertEquals(1,hechos("FUENTE_COSTO_IA"));assertEquals(2,hechos("CONFIRMACION_COSTO_IA"));
 }
 @Test void confirmacionCompletaResuelveExcesoTecnicoAbiertoSinReescribirResultado(){
  original=d.medicion("0.3");assertEquals("INCIERTA",d.tx.execute(t->d.presupuesto.conciliar(d.ref(s),original)).estado());
  assertEquals(1,hechos("INCIDENCIA_LIMITE"));assertEquals("INCIERTA",d.tx.execute(t->d.presupuesto.conciliar(d.ref(s),original)).estado());
  assertEquals(1,hechos("INCIDENCIA_LIMITE"));var q=primera("0.2");assertFalse(confirmar(q).aplicada());
  assertEquals("LIQUIDADA",d.tx.execute(t->d.presupuesto.conciliar(d.ref(s),original)).estado());
  assertEquals(0,n("0.2").compareTo(fondo()));assertEquals(1,hechos("INCIDENCIA_LIMITE"));
 }
 @Test void fuenteNaturalConcurrenteEntreDosPadresFondosYRevisionesTieneUnSoloDueno()throws Exception{
  cerrar();var b=new EvaluacionIaB0IntegrationTest();app.getAutowireCapableBeanFactory().autowireBean(b);
  b.directorio=java.nio.file.Files.createDirectory(directorio.resolve("segundo-fondo-test"));
  // Reutiliza el fixture canónico sin modificarlo; la segunda preparación tiene identidades/fondo/plan propios.
  b.preparar(infoActual);
  try{
   var otro=b.solicitud();b.admitir(otro);b.iniciar(otro);var m=b.medicion("0.1");b.tx.execute(t->b.presupuesto.conciliar(b.ref(otro),m));
   UUID otroGrant=b.conceder(Atribucion.FINANCIERO_PLATFORM,Set.of(Accion.AJUSTAR_COSTO),Set.of(Campo.LEDGER),b.caso,b.fondo,b.cuenta);
   var otraSesion=EvaluacionIaB0IntegrationTest.conGrant(b.admin,otroGrant);var otroPadre=b.dinero.evaluacion(otro.caso(),otro.operacion()).orElseThrow();
   var med=m.mediciones().getFirst();String hash=b.jdbc.queryForObject("SELECT huella FROM b0_medicion_tecnica WHERE id_operacion_b0=? AND id_medicion=?",String.class,otroPadre.tecnica(),med.id());
   String cuentaCanonica="CUENTA_CANONICA_COMPARTIDA_TEST:"+UUID.randomUUID();
   var inicial=primera("0.15");var a=new Solicitud(inicial.confirmacion(),inicial.medicion(),inicial.huellaMedicion(),null,
     cuentaCanonica,"EMISOR_CANONICO_TEST","DOCUMENTO_CARRERA_TEST","LINEA_TEST","REV_A_TEST",n("0.15"),inicial.fechaFuente(),inicial.evidencia(),inicial.huellaEvidencia(),inicial.causa());
   var q=new Solicitud(UUID.randomUUID(),med.id(),hash,null,a.cuentaProveedor(),a.emisorFacturacion(),a.documento(),a.linea(),"REV_B_TEST",n("0.16"),a.fechaFuente(),a.evidencia(),a.huellaEvidencia(),a.causa());
   var leidas=new CountDownLatch(2);var pids=ConcurrentHashMap.<Long>newKeySet();
   var real=new PresupuestoIaRepository(d.jdbc){
    @Override public Optional<String> fuenteCosto(String natural){var resultado=super.fuenteCosto(natural);
     assertTrue(resultado.isEmpty(),"Ambos padres deben llegar a la misma ausencia antes de insertar");pids.add(d.jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));leidas.countDown();
     try{if(!leidas.await(10,TimeUnit.SECONDS))throw new AssertionError("Barrera de fuenteTEST");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return resultado;}
   };
   var uno=new ConfirmacionesCostoIaB0(d.presupuesto,real,d.planes,d.atribuciones,d.json,d.gestor);
   var dos=new ConfirmacionesCostoIaB0(b.presupuesto,real,b.planes,b.atribuciones,b.json,b.gestor);
   assertNotEquals(d.fondo,b.fondo);assertNotEquals(tecnica(),otroPadre.tecnica());
   try(var pool=Executors.newFixedThreadPool(2)){
    List<Future<Recibo>> futures=List.of(pool.submit(()->uno.confirmar(s.caso(),s.operacion(),a,escritor)),pool.submit(()->dos.confirmar(otro.caso(),otro.operacion(),q,otraSesion)));
    int ganadores=0,rechazos=0;for(var f:futures){try{assertTrue(f.get(20,TimeUnit.SECONDS).aplicada());ganadores++;}catch(ExecutionException e){
     assertInstanceOf(org.springframework.dao.DataIntegrityViolationException.class,e.getCause());boolean indice=false;
     for(Throwable c=e.getCause();c!=null;c=c.getCause())if(c.getMessage()!=null&&c.getMessage().contains("uq_b0_fuente_costo_dueno"))indice=true;
     assertTrue(indice,"La derrota debe ser la unicidad global de fuente, no un fallo genérico");rechazos++;}}
    assertEquals(1,ganadores);assertEquals(1,rechazos);
   }
   assertEquals(2,pids.size());assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE hecho='FUENTE_COSTO_IA' AND contenido->>'documento'='DOCUMENTO_CARRERA_TEST' AND contenido->>'cuentaProveedor'=?",Integer.class,cuentaCanonica));
   assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE hecho='CONFIRMACION_COSTO_IA' AND contenido->'solicitud'->>'documento'='DOCUMENTO_CARRERA_TEST' AND contenido->'solicitud'->>'cuentaProveedor'=?",Integer.class,cuentaCanonica));
  }finally{b.limpiar();}
 }
 ConfirmacionesCostoIaB0 conGestor(PlatformTransactionManager gestor){return new ConfirmacionesCostoIaB0(d.presupuesto,d.dinero,d.planes,d.atribuciones,d.json,gestor);}
 @Test void cuatroTransaccionesConMismaCabezaTienenUnSoloGanadorCas()throws Exception{
  cerrar();var inicial=primera("0.1");confirmar(inicial);var listos=new CountDownLatch(4);var liberar=new CountDownLatch(1);var pids=ConcurrentHashMap.<Long>newKeySet();var inicios=new AtomicInteger();
  var gestor=new PlatformTransactionManager(){
   public TransactionStatus getTransaction(TransactionDefinition def){var st=d.gestor.getTransaction(def);if(inicios.incrementAndGet()<=4){
    pids.add(d.jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));listos.countDown();try{if(!liberar.await(10,TimeUnit.SECONDS))throw new AssertionError("BarreraTEST");}catch(InterruptedException e){Thread.currentThread().interrupt();d.gestor.rollback(st);throw new AssertionError(e);}catch(RuntimeException|Error e){d.gestor.rollback(st);throw e;}}return st;}
   public void commit(TransactionStatus st){d.gestor.commit(st);}public void rollback(TransactionStatus st){d.gestor.rollback(st);}
  };
  var servicio=conGestor(gestor);try(var pool=Executors.newFixedThreadPool(4)){
   var tareas=new ArrayList<Future<Boolean>>();for(int i=0;i<4;i++){int j=i;tareas.add(pool.submit(()->{try{servicio.confirmar(s.caso(),s.operacion(),solicitud(UUID.randomUUID(),inicial.confirmacion(),inicial.medicion(),"0.1"+(j+1),"CAS_TEST_"+j),escritor);return true;}catch(ConflictoException e){return false;}}));}
   try{assertTrue(listos.await(10,TimeUnit.SECONDS));assertEquals(4,pids.size());}finally{liberar.countDown();}
   int ganadores=0;for(var f:tareas)if(f.get(20,TimeUnit.SECONDS))ganadores++;assertEquals(1,ganadores);
  }finally{liberar.countDown();}
  assertEquals(2,hechos("CONFIRMACION_COSTO_IA"));assertEquals(1,hechos("APLICACION_COSTO_IA"));assertEquals(0,efectivo().compareTo(fondo()));
 }
 @Test void falloDeSegundaFaseNoBorraHechoYRecuperacionNoRegistraOtro()throws Exception{
  cerrar();var q=primera("0.2");var commits=new AtomicInteger();
  var gestor=new PlatformTransactionManager(){
   public TransactionStatus getTransaction(TransactionDefinition def){if(commits.get()==1)throw new IllegalStateException("FALLO_APLICACION_TEST");return d.gestor.getTransaction(def);}
   public void commit(TransactionStatus st){d.gestor.commit(st);commits.incrementAndGet();}public void rollback(TransactionStatus st){d.gestor.rollback(st);}
  };
  assertThrows(IllegalStateException.class,()->conGestor(gestor).confirmar(s.caso(),s.operacion(),q,escritor));
  assertEquals(1,hechos("CONFIRMACION_COSTO_IA"));assertEquals(0,hechos("APLICACION_COSTO_IA"));assertEquals(0,n("0.1").compareTo(fondo()));
  assertTrue(costos.aplicar(s.caso(),s.operacion(),new Recuperacion(q.confirmacion()),escritor).aplicada());assertEquals(1,hechos("CONFIRMACION_COSTO_IA"));assertEquals(1,hechos("APLICACION_COSTO_IA"));
 }
 @Test void revocacionDespuesDeEsperaYPrimerCommitDeniegaAplicacionSinBorrarHecho()throws Exception{
  cerrar();var q=primera("0.2");var retenido=new CountDownLatch(1);var soltar=new CountDownLatch(1);var entrada=new CountDownLatch(1);var pid=new AtomicLong();var commits=new AtomicInteger();
  var gestor=new PlatformTransactionManager(){
   public TransactionStatus getTransaction(TransactionDefinition def){var st=d.gestor.getTransaction(def);pid.set(d.jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));entrada.countDown();return st;}
   public void commit(TransactionStatus st){d.gestor.commit(st);if(commits.incrementAndGet()==1){var cuerpo=Map.of("concesion",grant,"motivo","REVOCACION_TEST");d.atribuciones.revocar(grant,d.decision("REVOCAR_ATRIBUCION",cuerpo,d.cuenta),"REVOCACION_TEST",d.admin);}}
   public void rollback(TransactionStatus st){d.gestor.rollback(st);}
  };
  try(var pool=Executors.newFixedThreadPool(2)){
   var padre=pool.submit(()->d.tx.executeWithoutResult(t->{d.jdbc.queryForObject("SELECT id_fondo FROM b0_fondo_tecnico WHERE id_fondo=? FOR UPDATE",UUID.class,d.fondo);retenido.countDown();try{if(!soltar.await(15,TimeUnit.SECONDS))throw new AssertionError("BarreraTEST");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}));
   assertTrue(retenido.await(10,TimeUnit.SECONDS));var hija=pool.submit(()->conGestor(gestor).confirmar(s.caso(),s.operacion(),q,escritor));
   try{assertTrue(entrada.await(10,TimeUnit.SECONDS));long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean bloqueo=false;
    while(System.nanoTime()<limite){if(Boolean.TRUE.equals(d.jdbc.queryForObject("SELECT cardinality(pg_blocking_pids(?))>0",Boolean.class,(int)pid.get()))){bloqueo=true;break;}Thread.sleep(10);}assertTrue(bloqueo);
   }finally{soltar.countDown();}
   padre.get(10,TimeUnit.SECONDS);var e=assertThrows(ExecutionException.class,()->hija.get(15,TimeUnit.SECONDS));assertInstanceOf(AccesoNoAutorizadoException.class,e.getCause());
  }finally{soltar.countDown();}
  assertEquals(1,hechos("CONFIRMACION_COSTO_IA"));assertEquals(0,hechos("APLICACION_COSTO_IA"));assertEquals(0,n("0.1").compareTo(fondo()));
 }
}
