package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.archivo.DiarioBinarioTecnico;
import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository;
import com.controllocal.service.archivo.PropietarioBinario;
import com.controllocal.service.archivo.SeguimientoSalidaBinaria;
import com.controllocal.service.planes.ConfirmacionAlmacenamientoB0;
import com.controllocal.web.almacen.*;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Dos padres Main reales ocupan todo el pool; custodia/diario siguen por el carril2 existente. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,EvaluacionIaB0IntegrationTest.AcreditacionTest.class},
 webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={
  "spring.datasource.hikari.maximum-pool-size=2","spring.datasource.hikari.connection-timeout=2000"})
class CustodiaEvaluacionIaPoolSaturadoIntegrationTest {
 @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
 @Autowired ApplicationContext app;
 @Autowired JdbcTemplate principal;
 @Autowired PlatformTransactionManager gestorPrincipal;
 @Autowired DiarioBinarioTecnico diario;
 @TempDir Path directorio;
 EvaluacionIaB0IntegrationTest datos;
 @BeforeEach void preparar(TestInfo info){
  datos=new EvaluacionIaB0IntegrationTest();
  app.getAutowireCapableBeanFactory().autowireBean(datos);
  datos.directorio=directorio;datos.preparar(info);
  assertInstanceOf(HikariDataSource.class,app.getBean(DataSource.class));
  assertEquals(2,((HikariDataSource)app.getBean(DataSource.class)).getMaximumPoolSize());
  assertEquals(1,app.getBeansOfType(PlatformTransactionManager.class).size());
 }
 @AfterEach void limpiar(){if(datos!=null)datos.limpiar();}
 @Test void resultadoYCustodiaAvanzanMientrasDosTransaccionesSaturanMain() throws Exception {
  var r=CustodiaEvaluacionIaCarrilTecnicoIntegrationTest.prepararResultado(datos);
  var hikari=(HikariDataSource)app.getBean(DataSource.class);
  var bloqueadorListo=new CountDownLatch(1);var liberarBloqueador=new CountDownLatch(1);
  var pidBloqueador=new AtomicLong();var xidBloqueador=new AtomicLong();
  var pidEscritor=new AtomicLong();var verificaciones=new AtomicInteger();
  var disco=spy(datos.disco);
  var salidas=new SalidaBinariaPersistente(disco,app.getBean(SeguimientoSalidaBinaria.class)){
   @Override public Guardado recibir(PropietarioBinario dueno,UUID io,String fuente,String extension,
       String mime,byte[] contenido,Admision admision,Custodia custodia){
    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
    pidEscritor.set(principal.queryForObject("SELECT pg_backend_pid()",Long.class));
    long xid=principal.queryForObject("SELECT txid_current()",Long.class);
    assertNotEquals(pidBloqueador.get(),pidEscritor.get());
    assertNotEquals(xidBloqueador.get(),xid);
    assertEquals(2,hikari.getHikariPoolMXBean().getActiveConnections(),
     "Ambas conexiones Main deben estar ocupadas ANTES de abrir la custodia.");
    return super.recibir(dueno,io,fuente,extension,mime,contenido,admision,custodia);
   }
  };
  doAnswer(i->{
   assertEquals(pidEscritor.get(),principal.queryForObject("SELECT pg_backend_pid()",Long.class).longValue());
   assertEquals(2,hikari.getHikariPoolMXBean().getActiveConnections(),
    "El bloqueador sigue activo y el escritor conserva su propia transacción.");
   CustodiaEvaluacionIaCarrilTecnicoIntegrationTest.comprobarPrePut(datos,principal,diario,r,i.getArgument(0,String.class));
   verificaciones.incrementAndGet();return i.callRealMethod();
  }).when(disco).guardarEnClave(anyString(),any(),anyString());
  var confirmacion=new ConfirmacionAlmacenamientoB0(datos.uso,app.getBean(IntentoSalidaBinariaRepository.class),app.getBean(EntityManager.class));
  var cuota=new CuotaArchivosB0(datos.uso,confirmacion,disco);
  var archivos=new ArchivosEvaluacionIaB0(datos.evaluaciones,datos.uso,salidas,cuota,disco,gestorPrincipal,datos.presupuesto,app.getBean(SeguimientoSalidaBinaria.class));
  var ejecutor=Executors.newFixedThreadPool(2);
  Future<Long> bloqueo=null;Future<UUID> escritura=null;
  try{
   bloqueo=ejecutor.submit(()->new TransactionTemplate(gestorPrincipal).execute(t->{
    pidBloqueador.set(principal.queryForObject("SELECT pg_backend_pid()",Long.class));
    xidBloqueador.set(principal.queryForObject("SELECT txid_current()",Long.class));
    bloqueadorListo.countDown();esperar(liberarBloqueador);return pidBloqueador.get();
   }));
   esperar(bloqueadorListo);
   escritura=ejecutor.submit(()->archivos.recibir(datos.ref(r.solicitud()),r.archivo(),"image/png",datos.png));
   assertEquals(r.archivo(),escritura.get(12,TimeUnit.SECONDS));
   liberarBloqueador.countDown();
   assertEquals(pidBloqueador.get(),bloqueo.get(5,TimeUnit.SECONDS).longValue());
  }finally{
   liberarBloqueador.countDown();
   if(escritura!=null&&!escritura.isDone())escritura.cancel(true);
   if(bloqueo!=null&&!bloqueo.isDone())bloqueo.cancel(true);
   ejecutor.shutdownNow();
   assertTrue(ejecutor.awaitTermination(10,TimeUnit.SECONDS),"Ningún padre debe sobrevivir al caso T4.");
  }
  assertEquals(1,verificaciones.get());
  CustodiaEvaluacionIaCarrilTecnicoIntegrationTest.comprobarResultadoYReplay(datos,principal,r,archivos,disco);
 }
 private static void esperar(CountDownLatch latch){
  try{assertTrue(latch.await(15,TimeUnit.SECONDS),"La barrera T4 no fue alcanzada.");}
  catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError("Barrera T4 interrumpida.",e);}
 }
}
