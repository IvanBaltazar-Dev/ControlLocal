package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.*;
import static org.junit.jupiter.api.Assertions.*;

/** PG real propio, observaciones/fondos/cuentas SINTETICOS. Sin red o gasto IA. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,EvaluacionIaB0IntegrationTest.AcreditacionTest.class},
 webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties="spring.datasource.hikari.maximum-pool-size=8")
class ObservacionMetricasIaB0IntegrationTest {
 @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
 @Autowired ApplicationContext app;
 @TempDir Path directorio;
 EvaluacionIaB0IntegrationTest d;
 @BeforeEach void preparar(TestInfo info){d=new EvaluacionIaB0IntegrationTest();app.getAutowireCapableBeanFactory().autowireBean(d);d.directorio=directorio;d.preparar(info);}
 @AfterEach void limpiar(){d.limpiar();}
 ObservacionHttpIa observacion(ReferenciaEvaluacion ref,String etapa){return new ObservacionHttpIa(ObservacionHttpIa.ESQUEMA,
  etapa==null?PresupuestoIaService.claveDeIntento(ref):PresupuestoIaService.claveDeEtapa(ref,etapa),"PRV_TEST","MODELO_TEST",d.version,
  Instant.now(),123456789L,ObservacionHttpIa.Resultado.TIMEOUT,null,true);}
 void guardar(ReferenciaEvaluacion ref,String etapa,ObservacionHttpIa o){d.tx.executeWithoutResult(t->d.presupuesto.registrarObservacionHttp(ref,etapa,o));}
 long filas(ReferenciaEvaluacion ref){return d.jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE id_caso_evaluacion=? AND id_operacion=? AND hecho='OBSERVACION_HTTP_IA'",Long.class,ref.caso(),ref.operacion());}
 @Test void sinClaimNoInventarIntento(){var s=d.solicitud();d.admitir(s);var ref=d.ref(s);
  assertThrows(ConflictoException.class,()->guardar(ref,null,observacion(ref,null)));assertEquals(0,filas(ref));}
 @Test void timeoutNoSeConvierteEnCeroNiLiquidaElPresupuesto(){var s=d.solicitud();d.admitir(s);d.iniciar(s);var ref=d.ref(s);var o=observacion(ref,null);
  guardar(ref,null,o);guardar(ref,null,o);assertEquals(1,filas(ref));
  assertEquals("EN_PROCESO",d.dinero.evaluacion(ref.caso(),ref.operacion()).orElseThrow().estado());
  assertEquals(0,d.jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_caso_evaluacion=?",Integer.class,d.caso));
  String contenido=d.jdbc.queryForObject("SELECT contenido::text FROM b0_antecedente WHERE id_caso_evaluacion=? AND hecho='OBSERVACION_HTTP_IA'",String.class,d.caso);
  assertEquals(o,d.json.comoObjeto(contenido,ObservacionHttpIa.class));
  assertEquals(EvaluacionIaB0IntegrationTest.n("0.250000000000"),d.jdbc.queryForObject("SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",java.math.BigDecimal.class,d.fondo));}
 @Test void dosTransaccionesConservanUnaObservacionSinNuevaInferencia()throws Exception{
  var s=d.solicitud();d.admitir(s);d.iniciar(s);var ref=d.ref(s);var o=observacion(ref,null);var barrera=new CountDownLatch(2);
  var pool=Executors.newFixedThreadPool(2);try{List<Future<?>> resultados=new ArrayList<>();for(int i=0;i<2;i++)resultados.add(pool.submit(()->{barrera.countDown();try{if(!barrera.await(10,TimeUnit.SECONDS))throw new AssertionError("Barrera TEST");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}guardar(ref,null,o);}));
   for(var f:resultados)f.get(15,TimeUnit.SECONDS);assertEquals(1,filas(ref));
  }finally{pool.shutdownNow();}}
 @Test void noSobrescribirLaObservacionNiReutilizarlaEnOtraOperacion(){var s=d.solicitud();d.admitir(s);d.iniciar(s);var ref=d.ref(s);var o=observacion(ref,null);guardar(ref,null,o);
  var otra=new ObservacionHttpIa(o.esquema(),o.claveIntento(),o.proveedor(),o.modelo(),o.versionConfiguracion(),o.iniciadaEn(),o.duracionNanos()+1,o.resultado(),o.estadoHttp(),o.envioIntentado());
  assertThrows(ConflictoException.class,()->guardar(ref,null,otra));var s2=d.solicitud();d.admitir(s2);d.iniciar(s2);
  assertThrows(ConflictoException.class,()->guardar(d.ref(s2),null,o));assertEquals(1,filas(ref));assertEquals(0,filas(d.ref(s2)));}
 @Test void revocacionNoBorraHechoDeUnIntentoYaComprometido(){var s=d.solicitud();d.admitir(s);d.iniciar(s);var ref=d.ref(s);
  var dato=Map.of("concesion",d.grantOp,"motivo","TEST REVOCACION");
  d.atribuciones.revocar(d.grantOp,d.decision("REVOCAR_ATRIBUCION",dato,d.cuenta),"TEST REVOCACION",d.admin);
  guardar(ref,null,observacion(ref,null));assertEquals(1,filas(ref));
  assertThrows(AccesoNoAutorizadoException.class,()->d.tx.execute(t->d.presupuesto.consultarEstadoEvaluacion(s,d.operador)));}
 @Test void modeloYConfiguracionDebenSerLosDelPlan(){var s=d.solicitud();d.admitir(s);d.iniciar(s);var ref=d.ref(s);var o=observacion(ref,null);
  var ajena=new ObservacionHttpIa(o.esquema(),o.claveIntento(),o.proveedor(),"MODELO_AJENO_TEST",o.versionConfiguracion(),o.iniciadaEn(),o.duracionNanos(),o.resultado(),o.estadoHttp(),true);
  assertThrows(ConflictoException.class,()->guardar(ref,null,ajena));
  var versionAjena=new ObservacionHttpIa(o.esquema(),o.claveIntento(),o.proveedor(),o.modelo(),"VERSION_AJENA_TEST",o.iniciadaEn(),o.duracionNanos(),o.resultado(),o.estadoHttp(),true);
  assertThrows(ConflictoException.class,()->guardar(ref,null,versionAjena));assertEquals(0,filas(ref));}
 @Test void observacionConservaOriginalAnteActualizacionOEliminacionSql(){var s=d.solicitud();d.admitir(s);d.iniciar(s);var ref=d.ref(s);guardar(ref,null,observacion(ref,null));
  assertThrows(org.springframework.dao.DataAccessException.class,()->d.tx.execute(t->d.jdbc.update("UPDATE b0_antecedente SET contenido='{}' WHERE id_caso_evaluacion=? AND hecho='OBSERVACION_HTTP_IA'",d.caso)));
  assertThrows(org.springframework.dao.DataAccessException.class,()->d.tx.execute(t->d.jdbc.update("DELETE FROM b0_antecedente WHERE id_caso_evaluacion=? AND hecho='OBSERVACION_HTTP_IA'",d.caso)));assertEquals(1,filas(ref));}
}
