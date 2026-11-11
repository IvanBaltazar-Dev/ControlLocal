package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository;
import com.controllocal.service.archivo.*;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.planes.ConfirmacionAlmacenamientoB0;
import com.controllocal.service.presupuesto.PresupuestoIaService.ReferenciaEvaluacion;
import com.controllocal.web.almacen.*;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PG/IO/carril2 reales; los únicos sabotajes son ventanas precisas TEST. Cero proveedor/gasto. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,EvaluacionIaB0IntegrationTest.AcreditacionTest.class},
 webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class FenceSalidaEvaluacionIntegrationTest {
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
 @Autowired ApplicationContext app;
 @TempDir Path directorio;
 EvaluacionMaterialIaIntegrationTest h;
 EvaluacionIaB0IntegrationTest d;
 SeguimientoSalidaBinaria seguimiento;
 org.springframework.jdbc.core.JdbcTemplate observador;

 @BeforeEach void preparar(TestInfo info){
  h=new EvaluacionMaterialIaIntegrationTest();app.getAutowireCapableBeanFactory().autowireBean(h);
  h.directorio=directorio;h.preparar(info);d=h.d;seguimiento=app.getBean(SeguimientoSalidaBinaria.class);
  observador=app.getBean(com.controllocal.persistence.archivo.DiarioBinarioTecnico.class).jdbc();
 }
 @AfterEach void limpiar(){if(h!=null)h.limpiar();}

 ReferenciaEvaluacion sinBinarioAntesDeLiberar()throws Exception{
  h.nativo(true,false);var archivos=spy(d.archivos);
  doThrow(new ConflictoException("SABOTAJE TEST antes del fence de liberación")).when(archivos).liberarSalidaSinBinario(any());
  ReflectionTestUtils.setField(h.consumidor,"archivos",archivos);UUID comando=UUID.randomUUID();
  assertThrows(ConflictoException.class,()->h.consumidor.ejecutar(d.caso,comando,d.fin,d.operador));
  ReflectionTestUtils.setField(h.consumidor,"archivos",d.archivos);
  assertEquals("LIQUIDADA",d.dinero.evaluacion(d.caso,comando).orElseThrow().estado());
  assertEquals(1,h.gen.get());assertEquals(0,h.qc.get());return new ReferenciaEvaluacion(d.caso,comando);
 }
 ReferenciaEvaluacion genConFotoAntesDelWriter()throws Exception{
  h.nativo(false,false);var archivos=spy(d.archivos);
  doThrow(new ConflictoException("SABOTAJE TEST después del Gen durable y antes139")).when(archivos).recibir(any(),any(),any(),any());
  ReflectionTestUtils.setField(h.consumidor,"archivos",archivos);UUID comando=UUID.randomUUID();
  assertThrows(ConflictoException.class,()->h.consumidor.ejecutar(d.caso,comando,d.fin,d.operador));
  ReflectionTestUtils.setField(h.consumidor,"archivos",d.archivos);
  return new ReferenciaEvaluacion(d.caso,comando);
 }
 PropietarioBinario propietario(ReferenciaEvaluacion r){return new PropietarioBinario.CasoEvaluacion(r.caso());}
 long intentos(UUID io){return observador.queryForObject("SELECT count(*) FROM intento_salida_binaria WHERE id_caso_evaluacion=? AND operacion=?",Long.class,d.caso,io);}
 String reserva(){return observador.queryForObject("SELECT estado FROM b0_reserva_almacenamiento WHERE id_caso_evaluacion=? AND operacion_evaluacion_b0 IS NOT NULL",String.class,d.caso);}

 @ParameterizedTest @EnumSource(SeguimientoSalidaBinaria.Estado.class)
 void ningunEstadoDeIntentoNiOtroNamespaceAcreditaNoIo(SeguimientoSalidaBinaria.Estado estado)throws Exception{
  var ref=sinBinarioAntesDeLiberar();var descriptor=d.presupuesto.leerRespuestaEvaluacion(ref,null).orElseThrow().descriptor();
  var i=d.tx.execute(t->seguimiento.preparar(propietario(ref),descriptor.operacionIo(),"SABOTAJE TEST INTENTO SIN CUSTODIA","c".repeat(64),
   "png","image/png",EvaluacionIaB0IntegrationTest.hash(d.png),d.png.length)).intento();
  // Montaje técnico explícito: cualquier estado histórico sigue siendo un intento real del IO.
  var repo=app.getBean(IntentoSalidaBinariaRepository.class);
  d.tx.executeWithoutResult(t->{var fila=repo.deOperacion(repo.dueno(null,ref.caso()),descriptor.operacionIo()).getFirst();
   repo.estado(fila,estado.name(),false,"SABOTAJE TEST ESTADO HISTORICO",false);});
  assertThrows(ConflictoException.class,()->d.archivos.liberarSalidaSinBinario(ref));
  assertEquals("RESERVADA",reserva());assertEquals("LIQUIDADA",d.dinero.evaluacion(ref.caso(),ref.operacion()).orElseThrow().estado());
  assertEquals(1,intentos(descriptor.operacionIo()));assertEquals(1,i.numero());
 }

 @Test void escritorConIntentoSinCustodiaExcluyeLiberacionYLaRetencionPermanece()throws Exception{
  var ref=sinBinarioAntesDeLiberar();var descriptor=d.presupuesto.leerRespuestaEvaluacion(ref,null).orElseThrow().descriptor();
  var principal=(com.zaxxer.hikari.HikariDataSource)app.getBean(javax.sql.DataSource.class);
  assertEquals(2,principal.getMaximumPoolSize(),"Dos padres ocupan todo Main; el observador usa el carril técnico existente.");
  var listo=new CountDownLatch(1);var soltar=new CountDownLatch(1);var entraLiberador=new CountDownLatch(1);
  var pidWriter=new AtomicLong();var pidLiberador=new AtomicLong();
  var control=new SeguimientoSalidaBinaria(app.getBean(IntentoSalidaBinariaRepository.class),app.getBean(com.controllocal.persistence.archivo.DiarioBinarioTecnico.class)){
   @Override public void bloquear(PropietarioBinario p,UUID io){
    pidLiberador.set(d.jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));entraLiberador.countDown();seguimiento.bloquear(p,io);
   }
  };
  var archivos=archivos(control,new SalidaBinariaPersistente(d.disco,seguimiento));
  var pool=Executors.newFixedThreadPool(2);Future<?> writer=null,liberador=null;
  try{
   writer=pool.submit(()->d.tx.executeWithoutResult(t->{
    pidWriter.set(d.jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));
    seguimiento.preparar(propietario(ref),descriptor.operacionIo(),"SABOTAJE TEST VENTANA ANTES CUSTODIA",d.namespace,"png","image/png",EvaluacionIaB0IntegrationTest.hash(d.png),d.png.length);
    listo.countDown();esperar(soltar);
   }));
   esperar(listo);
   liberador=pool.submit(()->assertThrows(ConflictoException.class,()->archivos.liberarSalidaSinBinario(ref)));
   esperar(entraLiberador);
   long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
   boolean esperando=false;
   while(!esperando&&System.nanoTime()<limite){
    esperando=Boolean.TRUE.equals(observador.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE pid=? AND locktype='advisory' AND NOT granted)",Boolean.class,pidLiberador.get()));
    if(!esperando)Thread.sleep(20);
   }
   assertTrue(esperando,"La liberación debe esperar al escritor en el advisory IO real.");
   assertTrue(Boolean.TRUE.equals(observador.queryForObject("SELECT ?=ANY(pg_blocking_pids(?::integer))",Boolean.class,pidWriter.get(),pidLiberador.get())));
   assertNotEquals(pidWriter.get(),pidLiberador.get());assertFalse(liberador.isDone());
   long pidObservador=observador.queryForObject("SELECT pg_backend_pid()",Long.class);
   assertNotEquals(pidWriter.get(),pidObservador);assertNotEquals(pidLiberador.get(),pidObservador);
   assertEquals(2,principal.getHikariPoolMXBean().getActiveConnections());
   System.out.println("C6_T4_FENCE "+Map.of("writer",pidWriter.get(),"liberador",pidLiberador.get(),"observadorTecnico",pidObservador,"esperaIoComprobada",true));
   assertEquals("RESERVADA",reserva());assertEquals(1,intentos(descriptor.operacionIo()));
   soltar.countDown();writer.get(10,TimeUnit.SECONDS);liberador.get(10,TimeUnit.SECONDS);
  }finally{soltar.countDown();if(writer!=null&&!writer.isDone())writer.cancel(true);if(liberador!=null&&!liberador.isDone())liberador.cancel(true);
   pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
  assertEquals("RESERVADA",reserva());assertEquals(1,intentos(descriptor.operacionIo()));assertEquals(1,h.gen.get());assertEquals(0,h.qc.get());
 }

 @Test void callbackAfterCompletionRealNoEsperaAlNuevoPropietarioDelIo()throws Exception{
  var ref=genConFotoAntesDelWriter();var descriptor=d.presupuesto.leerRespuestaEvaluacion(ref,null).orElseThrow().descriptor();
  var entraCallback=new CountDownLatch(1);var otroTieneIo=new CountDownLatch(1);var soltarOtro=new CountDownLatch(1);
  var pidWriter=new AtomicLong();var pidOtro=new AtomicLong();var callback=new AtomicInteger();
  var resultadoCallback=new AtomicReference<Boolean>();var errorCallback=new AtomicReference<Throwable>();
  // Instancia real con la misma infraestructura; no se espía un proxy Spring.
  var control=new SeguimientoSalidaBinaria(app.getBean(IntentoSalidaBinariaRepository.class),app.getBean(com.controllocal.persistence.archivo.DiarioBinarioTecnico.class)){
   @Override public boolean completar(PropietarioBinario p,UUID io,String ns,Retiro retiro){
    callback.incrementAndGet();entraCallback.countDown();
    try{esperar(otroTieneIo);boolean r=seguimiento.completar(p,io,ns,retiro);resultadoCallback.set(r);return r;}
    catch(RuntimeException|Error e){errorCallback.set(e);throw e;}
    finally{soltarOtro.countDown();}
   }
  };
  var salida=new SalidaBinariaPersistente(d.disco,control){
   @Override public Guardado recibir(PropietarioBinario p,UUID io,String fuente,String ext,String mime,byte[] b,Admision admision,Custodia custodia){
    pidWriter.set(d.jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));return super.recibir(p,io,fuente,ext,mime,b,admision,custodia);
   }
  };
  var archivos=archivos(seguimiento,salida);
  var pool=Executors.newFixedThreadPool(2);Future<?> otro=null;Future<UUID> writer=null;
  try{
   otro=pool.submit(()->{esperar(entraCallback);d.tx.executeWithoutResult(t->{
    pidOtro.set(d.jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));seguimiento.bloquear(propietario(ref),descriptor.operacionIo());
    otroTieneIo.countDown();esperar(soltarOtro);
   });});
   byte[] foto=imagenGenerada();
   assertEquals(descriptor.huella(),EvaluacionIaB0IntegrationTest.hash(foto));assertEquals(descriptor.bytes(),foto.length);
   writer=pool.submit(()->archivos.recibir(ref,descriptor.archivo(),descriptor.mime(),foto));
   assertEquals(descriptor.archivo(),writer.get(15,TimeUnit.SECONDS));otro.get(10,TimeUnit.SECONDS);
  }finally{soltarOtro.countDown();otroTieneIo.countDown();entraCallback.countDown();
   if(writer!=null&&!writer.isDone())writer.cancel(true);if(otro!=null&&!otro.isDone())otro.cancel(true);
   pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
  assertEquals(1,callback.get());assertNull(errorCallback.get(),"El callback real no debe fallar ni ocultar una aserción.");
  assertEquals(Boolean.FALSE,resultadoCallback.get(),"tryIO debe devolver false mientras el otro padre conserva IO.");
  assertNotEquals(pidWriter.get(),pidOtro.get());assertNotEquals(0,pidOtro.get());
  assertTrue(seguimiento.completar(propietario(ref),descriptor.operacionIo(),d.namespace,i->{fail("La custodia verificada no se elimina.");return false;}));
  assertEquals("VINCULADO",d.jdbc.queryForObject("SELECT estado FROM intento_salida_binaria WHERE id_caso_evaluacion=? AND operacion=?",String.class,d.caso,descriptor.operacionIo()));
  assertEquals(1,h.gen.get());assertEquals(0,h.qc.get());
 }
 ArchivosEvaluacionIaB0 archivos(SeguimientoSalidaBinaria control,SalidaBinariaPersistente salida){
  var confirmacion=new ConfirmacionAlmacenamientoB0(d.uso,app.getBean(IntentoSalidaBinariaRepository.class),app.getBean(EntityManager.class));
  return new ArchivosEvaluacionIaB0(d.evaluaciones,d.uso,salida,new CuotaArchivosB0(d.uso,confirmacion,d.disco),d.disco,d.gestor,d.presupuesto,control);
 }
 static byte[] imagenGenerada()throws Exception{
  var b=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1024,1024,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",b);return b.toByteArray();
 }
 static void esperar(CountDownLatch l){try{assertTrue(l.await(15,TimeUnit.SECONDS),"No se alcanzó la barrera T4.");}
  catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
}
