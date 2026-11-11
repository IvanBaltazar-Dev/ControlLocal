package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.archivo.DiarioBinarioTecnico;
import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository;
import com.controllocal.service.archivo.PropietarioBinario;
import com.controllocal.service.archivo.SeguimientoSalidaBinaria;
import com.controllocal.service.planes.ConfirmacionAlmacenamientoB0;
import com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma;
import com.controllocal.service.presupuesto.EvaluacionesIaB0;
import com.controllocal.service.presupuesto.PresupuestoIaService;
import com.controllocal.service.planes.UsoAlmacenamientoB0;
import com.controllocal.web.almacen.*;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pool principal1 y carril139 reales. Sin proveedor ni nueva infraestructura o financiación real. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,EvaluacionIaB0IntegrationTest.AcreditacionTest.class},
 webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={
  "spring.datasource.hikari.maximum-pool-size=2","spring.datasource.hikari.minimum-idle=1",
  "spring.datasource.hikari.connection-timeout=2000"})
class CustodiaEvaluacionIaCarrilTecnicoIntegrationTest {
 @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
 @Autowired ApplicationContext app;
 @Autowired JdbcTemplate principal;
 @Autowired PlatformTransactionManager gestorPrincipal;
 @Autowired DiarioBinarioTecnico diario;
 @TempDir Path directorio;
 EvaluacionIaB0IntegrationTest datos;
 @BeforeEach void preparar(TestInfo info) throws InterruptedException {
  // Flyway valida/migra con sus conexiones de arranque. El riesgo probado comienza después:
  // un único Main físico para fixture/actuación, sin deshabilitar Flyway ni crear otro pool.
  var pool=(HikariDataSource)app.getBean(DataSource.class);
  pool.setMaximumPoolSize(1);pool.getHikariPoolMXBean().softEvictConnections();
  long limite=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
  while(pool.getHikariPoolMXBean().getTotalConnections()>1&&System.nanoTime()<limite)Thread.sleep(10);
  assertTrue(pool.getHikariPoolMXBean().getTotalConnections()<=1,"No quedan conexiones Main sobrantes.");
  // Reutiliza únicamente el montaje existente; no hereda ni vuelve a ejecutar sus25tests.
  datos=new EvaluacionIaB0IntegrationTest();
  app.getAutowireCapableBeanFactory().autowireBean(datos);
  datos.directorio=directorio;datos.preparar(info);
  assertInstanceOf(HikariDataSource.class,app.getBean(DataSource.class));
  assertEquals(1,((HikariDataSource)app.getBean(DataSource.class)).getMaximumPoolSize());
  assertEquals(1,app.getBeansOfType(PlatformTransactionManager.class).size(),"El carril2 no publica otro gestor comercial.");
 }
 @AfterEach void limpiar(){
  try{if(datos!=null)datos.limpiar();}
  finally{((HikariDataSource)app.getBean(DataSource.class)).setMaximumPoolSize(2);}
 }
 TransactionTemplate lecturaTecnica(){
  var tx=new TransactionTemplate(diario.gestor());tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);return tx;
 }
 @Test void custodiaComprometidaEsVisibleAntesDelPutConElUnicoPadreActivo(){
  var disco=spy(datos.disco);var intentos=app.getBean(IntentoSalidaBinariaRepository.class);
  var confirmacion=new ConfirmacionAlmacenamientoB0(datos.uso,intentos,app.getBean(EntityManager.class));
  var cuota=new CuotaArchivosB0(datos.uso,confirmacion,disco){
   @Override public SalidaBinariaPersistente.Admision admisionOriginalEvaluacion(UUID caso,UUID archivo,
       SesionPlataforma sesion,EvaluacionesIaB0 evaluaciones){
    var original=super.admisionOriginalEvaluacion(caso,archivo,sesion,evaluaciones);
    return i->{
     // Identidades inmutables: NO KEY UPDATE permite las FK KEY SHARE del hijo.
     principal.query("SELECT id FROM b0_evaluacion_caso WHERE id=? FOR NO KEY UPDATE",(r,n)->r.getObject(1,UUID.class),caso);
     principal.query("SELECT id FROM b0_propietario_binario WHERE caso=? FOR NO KEY UPDATE",(r,n)->r.getObject(1,UUID.class),caso);
     original.comprobar(i);
    };
   }
  };
  var comprobaciones=new AtomicInteger();
  doAnswer(invocacion->{
   long pidPrincipal=principal.queryForObject("SELECT pg_backend_pid()",Long.class);
   assertEquals(1,principal.queryForObject("SELECT count(*) FROM b0_reserva_almacenamiento WHERE id_caso_evaluacion=?",Integer.class,datos.caso));
   lecturaTecnica().executeWithoutResult(t->{
    long pidTecnico=diario.jdbc().queryForObject("SELECT pg_backend_pid()",Long.class);assertNotEquals(pidPrincipal,pidTecnico);
    assertEquals(1,diario.jdbc().queryForObject("SELECT count(*) FROM b0_archivo_evaluacion WHERE caso=? AND archivo=? AND estado='PREPARADA'",Integer.class,datos.caso,datos.archivo));
    assertEquals(0,diario.jdbc().queryForObject("SELECT count(*) FROM b0_reserva_almacenamiento WHERE id_caso_evaluacion=?",Integer.class,datos.caso),
     "La reserva del padre aún no está comprometida y la custodia no la necesita.");
   });
   assertTrue(datos.disco.abrir(invocacion.getArgument(0,String.class)).isEmpty());
   comprobaciones.incrementAndGet();return invocacion.callRealMethod();
  }).when(disco).guardarEnClave(anyString(),any(),anyString());
  var salidas=new SalidaBinariaPersistente(disco,app.getBean(SeguimientoSalidaBinaria.class));
  var archivos=new ArchivosEvaluacionIaB0(datos.evaluaciones,datos.uso,salidas,cuota,disco,gestorPrincipal,datos.presupuesto,app.getBean(SeguimientoSalidaBinaria.class));
  assertTimeout(Duration.ofSeconds(10),()->assertEquals(datos.archivo,archivos.guardarOriginal(datos.caso,datos.archivo,"image/png",datos.png,datos.operador)));
  assertEquals(1,comprobaciones.get());assertArrayEquals(datos.png,archivos.leerMuestra(datos.caso,datos.archivo,datos.qa));
 }
 @Test void claimSinCommitDelPadreNoSeUsaComoFkDelHijo(){
  var solicitud=datos.solicitud();UUID io=UUID.randomUUID(),archivo=UUID.randomUUID();
  var dueno=new PropietarioBinario.CasoEvaluacion(datos.caso);var seguimiento=app.getBean(SeguimientoSalidaBinaria.class);
  String hash=EvaluacionIaB0IntegrationTest.hash(datos.png);
  String fuente="EVALUACION-IA:"+datos.caso+":"+archivo+":"+"a".repeat(64)+":"+hash;
  assertTimeout(Duration.ofSeconds(10),()->new TransactionTemplate(gestorPrincipal).executeWithoutResult(t->{
   datos.presupuesto.admitirEvaluacion(solicitud,datos.operador);
   assertTrue(datos.presupuesto.iniciarEvaluacion(solicitud,datos.operador));
   datos.presupuesto.registrarMediciones(datos.ref(solicitud),datos.medicion("0.1"));
   var operacion=datos.dinero.evaluacion(datos.caso,solicitud.operacion()).orElseThrow();
   var intento=seguimiento.preparar(dueno,io,fuente,datos.namespace,"png","image/png",hash,datos.png.length).intento();
   var rechazo=assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,()->datos.evaluaciones.comprometerArchivo(datos.caso,archivo,operacion.tecnica(),io,intento.numero(),hash,datos.png.length),
    "El carril2 debe rechazar el claim no comprometido antes de intentar su FK.");
   var causa=assertInstanceOf(IllegalArgumentException.class,rechazo.getMostSpecificCause());
   assertEquals("No se acreditó el claim original.",causa.getMessage());
   t.setRollbackOnly();
  }));
  assertEquals(0,principal.queryForObject("SELECT count(*) FROM b0_archivo_evaluacion WHERE archivo=?",Integer.class,archivo));
  assertTrue(datos.dinero.evaluacion(datos.caso,solicitud.operacion()).isEmpty());
  seguimiento.completar(dueno,io,datos.namespace,i->datos.disco.abrir(i.clave()).isEmpty());
 }

 @Test void rollbackDelPadreConservaCustodiaTecnicaSinConfirmarBytes(){
  var disco=spy(datos.disco);var salidas=new SalidaBinariaPersistente(disco,app.getBean(SeguimientoSalidaBinaria.class));
  var confirmacion=new ConfirmacionAlmacenamientoB0(datos.uso,app.getBean(IntentoSalidaBinariaRepository.class),app.getBean(EntityManager.class));
  var cuota=new CuotaArchivosB0(datos.uso,confirmacion,disco);
  doAnswer(i->{
   lecturaTecnica().executeWithoutResult(t->assertEquals(1,diario.jdbc().queryForObject(
    "SELECT count(*) FROM b0_archivo_evaluacion WHERE caso=? AND archivo=? AND estado='PREPARADA'",
    Integer.class,datos.caso,datos.archivo)));
   throw new IllegalStateException("SABOTAJE TEST antes del PUT real");
  }).when(disco).guardarEnClave(anyString(),any(),anyString());
  var archivos=new ArchivosEvaluacionIaB0(datos.evaluaciones,datos.uso,salidas,cuota,disco,gestorPrincipal,datos.presupuesto,app.getBean(SeguimientoSalidaBinaria.class));
  assertTimeout(Duration.ofSeconds(10),()->assertThrows(AlmacenException.class,
   ()->archivos.guardarOriginal(datos.caso,datos.archivo,"image/png",datos.png,datos.operador)));
  assertEquals(1,principal.queryForObject("SELECT count(*) FROM b0_archivo_evaluacion WHERE caso=? AND estado='PREPARADA'",Integer.class,datos.caso));
  assertEquals(0,principal.queryForObject("SELECT count(*) FROM b0_reserva_almacenamiento WHERE id_caso_evaluacion=?",Integer.class,datos.caso));
  var a=datos.evaluaciones.archivoInterno(datos.caso,datos.archivo);assertTrue(datos.disco.abrir(a.clave()).isEmpty());
 }

 @Test void resultadoComprometidoYReplayFuncionanConPoolPrincipalUno(){
  var preparado=prepararResultado(datos);
  var disco=spy(datos.disco);var verificaciones=new AtomicInteger();
  doAnswer(i->{
   assertEquals(1,((HikariDataSource)app.getBean(DataSource.class)).getHikariPoolMXBean().getActiveConnections());
   comprobarPrePut(datos,principal,diario,preparado,i.getArgument(0,String.class));
   verificaciones.incrementAndGet();return i.callRealMethod();
  }).when(disco).guardarEnClave(anyString(),any(),anyString());
  var salidas=new SalidaBinariaPersistente(disco,app.getBean(SeguimientoSalidaBinaria.class));
  var confirmacion=new ConfirmacionAlmacenamientoB0(datos.uso,app.getBean(IntentoSalidaBinariaRepository.class),app.getBean(EntityManager.class));
  var cuota=new CuotaArchivosB0(datos.uso,confirmacion,disco);
  var archivos=new ArchivosEvaluacionIaB0(datos.evaluaciones,datos.uso,salidas,cuota,disco,gestorPrincipal,datos.presupuesto,app.getBean(SeguimientoSalidaBinaria.class));
  assertTimeout(Duration.ofSeconds(10),()->assertEquals(preparado.archivo(),
   archivos.recibir(datos.ref(preparado.solicitud()),preparado.archivo(),"image/png",datos.png)));
  comprobarResultadoYReplay(datos,principal,preparado,archivos,disco);
  assertEquals(1,verificaciones.get());
 }

 /** Comparte preparación/aserciones del riesgo, no otra implementación de B0 ni otra suite heredada. */
 record ResultadoPreparado(PresupuestoIaService.SolicitudEvaluacion solicitud,UUID archivo,UUID operacionB0,
     UsoAlmacenamientoB0.RefEvaluacion reserva,java.sql.Timestamp inicio,
     java.math.BigDecimal retenido,java.math.BigDecimal liquidado,java.math.BigDecimal costoNativo){}
 static ResultadoPreparado prepararResultado(EvaluacionIaB0IntegrationTest datos){
  var s=datos.solicitud();datos.admitir(s);datos.iniciar(s);UUID archivo=UUID.randomUUID();var m=datos.medicion("0.1234567890123456789");
  datos.respuestaGenerada(s,archivo,m);
  datos.tx.executeWithoutResult(t->{
   datos.presupuesto.registrarMediciones(datos.ref(s),m);
   datos.presupuesto.conciliar(datos.ref(s),m);
  });
  var b0=datos.dinero.evaluacion(datos.caso,s.operacion()).orElseThrow();
  assertEquals("LIQUIDADA",b0.estado());
  var r=UsoAlmacenamientoB0.referenciaEvaluacion(datos.caso,s.operacion());
  return new ResultadoPreparado(s,archivo,b0.tecnica(),r,
   datos.jdbc.queryForObject("SELECT iniciada_en FROM b0_operacion_ia WHERE id_operacion_b0=?",java.sql.Timestamp.class,b0.tecnica()),
   datos.jdbc.queryForObject("SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",java.math.BigDecimal.class,datos.fondo),
   datos.jdbc.queryForObject("SELECT liquidado FROM b0_fondo_tecnico WHERE id_fondo=?",java.math.BigDecimal.class,datos.fondo),
   datos.jdbc.queryForObject("SELECT costo_usd FROM b0_medicion_tecnica WHERE id_operacion_b0=?",java.math.BigDecimal.class,b0.tecnica()));
 }
 static void comprobarPrePut(EvaluacionIaB0IntegrationTest datos,JdbcTemplate principal,
     DiarioBinarioTecnico diario,ResultadoPreparado r,String clave){
  long padre=principal.queryForObject("SELECT pg_backend_pid()",Long.class);
  assertEquals("ALMACENANDO",principal.queryForObject(
   "SELECT estado FROM b0_reserva_almacenamiento WHERE id_caso_evaluacion=? AND id_reserva=?",
   String.class,datos.caso,r.reserva().reserva()));
  assertEquals(r.operacionB0(),principal.queryForObject(
   "SELECT operacion_evaluacion_b0 FROM b0_reserva_almacenamiento WHERE id_caso_evaluacion=? AND id_reserva=?",
   UUID.class,datos.caso,r.reserva().reserva()));
  var tecnica=new TransactionTemplate(diario.gestor());
  tecnica.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  tecnica.executeWithoutResult(t->{
   assertNotEquals(padre,diario.jdbc().queryForObject("SELECT pg_backend_pid()",Long.class).longValue());
   assertEquals(1,diario.jdbc().queryForObject("""
    SELECT count(*) FROM b0_archivo_evaluacion a JOIN intento_salida_binaria i USING(id_intento)
    WHERE a.caso=? AND a.archivo=? AND a.operacion_b0=? AND a.tipo='RESULTADO'
     AND a.estado='PREPARADA' AND i.clave_proveedor=? AND i.huella=? AND i.bytes=?
    """,Integer.class,datos.caso,r.archivo(),r.operacionB0(),clave,EvaluacionIaB0IntegrationTest.hash(datos.png),datos.png.length));
   assertEquals("RESERVADA",diario.jdbc().queryForObject(
    "SELECT estado FROM b0_reserva_almacenamiento WHERE id_caso_evaluacion=? AND id_reserva=?",
    String.class,datos.caso,r.reserva().reserva()),"La custodia no usa cambios del padre sin commit.");
   assertNull(diario.jdbc().queryForObject(
    "SELECT bytes FROM b0_reserva_almacenamiento WHERE id_caso_evaluacion=? AND id_reserva=?",
    Long.class,datos.caso,r.reserva().reserva()));
   assertEquals(1,diario.jdbc().queryForObject(
    "SELECT count(*) FROM b0_antecedente WHERE id_operacion_b0=? AND hecho='EJECUCION_RECLAMADA'",Integer.class,r.operacionB0()));
   assertEquals(r.costoNativo(),diario.jdbc().queryForObject(
    "SELECT costo_usd FROM b0_medicion_tecnica WHERE id_operacion_b0=?",java.math.BigDecimal.class,r.operacionB0()));
  });
  assertTrue(datos.disco.abrir(clave).isEmpty(),"La prueba observa custodia comprometida ANTES del PUT real.");
 }
 static void comprobarResultadoYReplay(EvaluacionIaB0IntegrationTest datos,JdbcTemplate principal,
     ResultadoPreparado r,ArchivosEvaluacionIaB0 archivos,AlmacenDisco disco){
  assertEquals(r.archivo(),archivos.recibir(datos.ref(r.solicitud()),r.archivo(),"image/png",datos.png));
  assertArrayEquals(datos.png,archivos.leerMuestra(datos.caso,r.archivo(),datos.qa));
  assertEquals(1,principal.queryForObject(
   "SELECT count(*) FROM b0_archivo_evaluacion WHERE caso=? AND archivo=? AND operacion_b0=? AND estado='VERIFICADA'",
   Integer.class,datos.caso,r.archivo(),r.operacionB0()));
  assertEquals(1,principal.queryForObject(
   "SELECT count(*) FROM intento_salida_binaria WHERE id_caso_evaluacion=?",Integer.class,datos.caso));
  assertEquals(1,principal.queryForObject(
   "SELECT count(*) FROM b0_operacion_ia WHERE id_caso_evaluacion=? AND id_operacion=?",
   Integer.class,datos.caso,r.solicitud().operacion()));
  assertEquals(r.operacionB0(),datos.dinero.evaluacion(datos.caso,r.solicitud().operacion()).orElseThrow().tecnica());
  assertEquals(1,principal.queryForObject(
   "SELECT count(*) FROM b0_reserva_almacenamiento WHERE id_caso_evaluacion=? AND id_reserva=? AND operacion_evaluacion_b0=? AND estado='CONFIRMADA'",
   Integer.class,datos.caso,r.reserva().reserva(),r.operacionB0()));
  assertEquals((long)datos.png.length,principal.queryForObject(
   "SELECT confirmados FROM b0_uso_capacidad_fisica WHERE namespace_hash=?",Long.class,datos.namespace));
  assertEquals(r.inicio(),principal.queryForObject(
   "SELECT iniciada_en FROM b0_operacion_ia WHERE id_operacion_b0=?",java.sql.Timestamp.class,r.operacionB0()));
  boolean segundoClaim=datos.tx.execute(t->datos.presupuesto.iniciarEvaluacion(r.solicitud(),datos.operador));
  assertFalse(segundoClaim);
  assertEquals(1,principal.queryForObject(
   "SELECT count(*) FROM b0_antecedente WHERE id_operacion_b0=? AND hecho='EJECUCION_RECLAMADA'",Integer.class,r.operacionB0()));
  assertEquals(1,principal.queryForObject(
   "SELECT count(*) FROM b0_medicion_tecnica WHERE id_operacion_b0=?",Integer.class,r.operacionB0()));
  assertEquals(r.costoNativo(),principal.queryForObject(
   "SELECT costo_usd FROM b0_medicion_tecnica WHERE id_operacion_b0=?",java.math.BigDecimal.class,r.operacionB0()));
  assertEquals(r.retenido(),principal.queryForObject(
   "SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",java.math.BigDecimal.class,datos.fondo));
  assertEquals(r.liquidado(),principal.queryForObject(
   "SELECT liquidado FROM b0_fondo_tecnico WHERE id_fondo=?",java.math.BigDecimal.class,datos.fondo));
  verify(disco,times(1)).guardarEnClave(anyString(),any(),anyString());
 }
}
