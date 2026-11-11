package com.controllocal.integracion;
import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.DerechosDePlanB0;
import com.controllocal.service.plataforma.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
/** PG real de B0 manual; fuente de acreditación y evidencia I0 sintéticas explícitas. No certifica el recorrido I0. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,TrabajoManualB0IntegrationTest.Config.class},webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties="spring.datasource.hikari.maximum-pool-size=6")
class TrabajoManualB0IntegrationTest {
 @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
 @TestConfiguration static class Config {@Bean Acreditador acreditadorManualTest(){return new Acreditador();}}
 static class Acreditador implements AcreditacionesTitularPlataforma {
  final Map<UUID,Decision> decisiones=new ConcurrentHashMap<>();
  public Optional<Decision> acreditar(UUID id){return Optional.ofNullable(decisiones.get(id));}
 }
 @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager gestor;@Autowired ContextosService contextos;
 @Autowired MotorDeCaptura captura;@Autowired Documentos json;@Autowired AtribucionesPlataforma atribuciones;
 @Autowired Acreditador acreditador;@Autowired PresupuestoIaRepository repo;@Autowired TrabajoManualB0Repository manual;
 @Autowired ContextoPresupuestoB0 presupuestoContexto;
 TransactionTemplate tx;Actor agente;ContextoSesion sid;SesionPlataforma financiero;UUID regla,fuente,ciclo;
 PresupuestoIaService servicio;Instant ahora;final Map<UUID,BigDecimal> evidencia=new ConcurrentHashMap<>();final Set<Actor> autorizados=ConcurrentHashMap.newKeySet();
 final AutorizadorRecursoIa propietario=new AutorizadorRecursoIa(){
  public boolean atiende(String perfil,String tipo){return PresupuestoIaService.PERFIL_MANUAL.equals(perfil)&&"INVESTIGACION_MERCADO".equals(tipo);}
  public void reautorizar(Solicitud s,Actor a){if(!autorizados.contains(a))throw new AccesoNoAutorizadoException();}
  public void exigirEvidenciaManual(long org,UUID op,Recurso r,BigDecimal rondas,String ref){if(org!=agente.idOrganizacion()||!"TEST_ACREDITADO".equals(ref)||!evidencia.containsKey(op)||evidencia.get(op).compareTo(rondas)!=0)throw new ConflictoException("Sin ronda completa acreditada en la fuente TEST.");}
 };
 @BeforeEach void preparar(TestInfo info){
  EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);ahora=Instant.now();regla=UUID.randomUUID();fuente=UUID.randomUUID();ciclo=UUID.randomUUID();
  var origenes=new OrigenesDePrueba(jdbc,gestor,captura,contextos);agente=origenes.empresa().agente();autorizados.add(agente);sid=origenes.sesion(agente);
  servicio=new PresupuestoIaService(repo,presupuestoContexto,List.of(propietario),json,new DerechosDePlanB0(repo,json),false);
  ReflectionTestUtils.setField(servicio,"manual",manual);ReflectionTestUtils.setField(servicio,"atribuciones",atribuciones);ReflectionTestUtils.setField(servicio,"manualHabilitado",true);
  var identidad=cuentaPlataforma();long cuenta=contextos.exigirIdentidad(identidad.referenciaSesion(),1).identidad().idCuenta();
  var d=new Datos(UUID.randomUUID(),cuenta,Atribucion.FINANCIERO_PLATFORM,null,null,null,null,AtribucionesPlataforma.FINALIDAD_MERCADO,
   Set.of(Accion.CONFIGURAR_REGLA_MANUAL,Accion.CONCILIAR_TRABAJO),Set.of(Campo.LEDGER),ahora.minusSeconds(1),ahora.plusSeconds(3600),"TEST NO PERMISO REAL",null,RecursoMercado.MERCADO_MANUAL_I0);
  UUID decision=decision(cuenta,"CONCEDER_ATRIBUCION",json.objetoCanonico(d));atribuciones.conceder(d,decision,identidad);
  financiero=new SesionPlataforma(identidad.referenciaSesion(),1,d.id());
  var reglaDatos=new ReglaManual(regla,"TEST_V1",new BigDecimal("0.5"),new BigDecimal("3"),ahora.minusSeconds(1),ahora.plusSeconds(3600),"TEST SIN TARIFA PRODUCTIVA",true,FinanciacionManual.SOLICITANTE);
  servicio.configurarReglaManual(reglaDatos,decision(cuenta,"CONFIGURAR_REGLA_MANUAL_I0",json.objetoCanonico(reglaDatos)),financiero);
  financiar(agente,ciclo,fuente);
 }
 @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}
 void financiar(Actor a,UUID idCiclo,UUID idFuente){
  tx.execute(t->{
   jdbc.update("INSERT INTO b0_ciclo(organizacion_id,id_ciclo,plan,desde,hasta,dia_aniversario,importe_financiado_usd,orden_verificada,evidencia_pago,registrado_en) VALUES (?,?,'TEAM',?,?,1,75,?,'TEST NO PAGO REAL',?)",a.idOrganizacion(),idCiclo,utc(ahora.minusSeconds(60)),utc(ahora.plusSeconds(3600)),UUID.randomUUID(),utc(ahora));
   jdbc.update("INSERT INTO b0_fuente_capacidad(organizacion_id,id_fuente,id_ciclo,origen,id_persona_destinataria,plaza,origen_verificado,inicial,desde,vence,registrado_en) VALUES (?,?,?,'MENSUAL_AGENTE',?,1,?,10,?,?,?)",a.idOrganizacion(),idFuente,idCiclo,a.idPersona(),"TEST-"+idFuente,utc(ahora.minusSeconds(60)),utc(ahora.plusSeconds(3600)),utc(ahora));
   jdbc.update("INSERT INTO b0_plaza(organizacion_id,id_ciclo,tipo,numero,id_persona,id_fuente) VALUES (?,?,'AGENTE',1,?,?)",a.idOrganizacion(),idCiclo,a.idPersona(),idFuente);return null;
  });
 }
 UUID decision(long cuenta,String accion,String contenido){UUID id=UUID.randomUUID();acreditador.decisiones.put(id,new AcreditacionesTitularPlataforma.Decision(id,cuenta,cuenta,accion,Idempotencia.huellaExacta(contenido),"TEST SINTETICO",Instant.now()));return id;}
    SesionPlataforma cuentaPlataforma(){return tx.execute(t->{
        String x=UUID.randomUUID().toString(),ref=Base64.getUrlEncoder().withoutPadding().encodeToString((x+x).substring(0,32).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        long id=jdbc.queryForObject("INSERT INTO cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) VALUES(?,?,clock_timestamp(),'TEST NO LOGIN') RETURNING id_cuenta_acceso",Long.class,"compra-"+x,"compra-"+x+"@example.invalid");
        var ahora=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);jdbc.update("INSERT INTO sesion_acceso(referencia_hash,semilla_contextos,id_cuenta_acceso,revision,creada_en,emitida_en,vence_en) VALUES(?,?,?,1,?,?,?)",
                Idempotencia.huellaExacta(ref),"c".repeat(64),id,utc(ahora.minusSeconds(2)),utc(ahora.minusSeconds(1)),utc(ahora.plusSeconds(1800)));
        return new SesionPlataforma(ref,1,null);
    });}

 SolicitudManual solicitud(int rondas){UUID id=UUID.randomUUID();return new SolicitudManual(id,regla,"TEST_V1",new Recurso("INVESTIGACION_MERCADO",id.toString(),"INVESTIGACION_PUNTUAL","a".repeat(64)),"b".repeat(64),BigDecimal.valueOf(rondas),new BigDecimal("0.5").multiply(BigDecimal.valueOf(rondas)));}
 Admision admitir(SolicitudManual s){return tx.execute(t->servicio.admitirManual(s,agente,sid));}
 Admision conciliar(SolicitudManual s,int rondas){return tx.execute(t->servicio.conciliarManual(new ConciliacionManual(agente.idOrganizacion(),s.operacion(),BigDecimal.valueOf(rondas),"TEST_ACREDITADO"),financiero));}
 void monto(String valor,String sql,Object...args){assertEquals(0,new BigDecimal(valor).compareTo(jdbc.queryForObject(sql,BigDecimal.class,args)));}
 @Test void reservaYLiquidacionConservanFuentesYRondasSinInventarApi(){
  var s=solicitud(3);admitir(s);admitir(s);assertTrue(Boolean.TRUE.equals(tx.execute(t->servicio.iniciarManual(s,agente))));evidencia.put(s.operacion(),BigDecimal.ONE);
  assertEquals("LIQUIDADA",conciliar(s,1).estado());assertEquals("LIQUIDADA",conciliar(s,1).estado());
  monto("0.5","select liquidado from b0_fuente_capacidad where id_fuente=?",fuente);monto("0","select retenido from b0_fuente_capacidad where id_fuente=?",fuente);
  monto("1","select realizadas from b0_regla_trabajo_manual where id=?",regla);monto("0","select reservadas from b0_regla_trabajo_manual where id=?",regla);
  assertEquals(1,jdbc.queryForObject("select count(*) from b0_operacion_ia where id_operacion=? and modalidad='MANUAL_I0' and costo_tecnico_usd is null and maximo_tecnico_usd is null and id_fondo is null",Integer.class,s.operacion()));
 }
 @Test void rollbackNoDejaReservaNiCotaConsumida(){
  var s=solicitud(2);assertThrows(IllegalStateException.class,()->tx.execute(t->{servicio.admitirManual(s,agente,sid);throw new IllegalStateException("TEST ROLLBACK");}));
  assertTrue(repo.operacion(agente.idOrganizacion(),s.operacion()).isEmpty());monto("0","select reservadas from b0_regla_trabajo_manual where id=?",regla);monto("0","select retenido from b0_fuente_capacidad where id_fuente=?",fuente);
 }
 @Test void sinEvidenciaNoSeLiquidaYLaReservaPermanece(){var s=solicitud(1);admitir(s);assertThrows(ConflictoException.class,()->conciliar(s,1));monto("0.5","select retenido from b0_fuente_capacidad where id_fuente=?",fuente);}
 @Test void revocacionFinancieraImpideLiquidarSinBorrarTrabajo(){var s=solicitud(1);admitir(s);evidencia.put(s.operacion(),BigDecimal.ONE);long cuenta=contextos.exigirIdentidad(financiero.referenciaSesion(),1).identidad().idCuenta();String motivo="TEST";atribuciones.revocar(financiero.concesion(),decision(cuenta,"REVOCAR_ATRIBUCION",json.objetoCanonico(Map.of("concesion",financiero.concesion(),"motivo",motivo))),motivo,financiero);assertThrows(AccesoNoAutorizadoException.class,()->conciliar(s,1));monto("0.5","select retenido from b0_fuente_capacidad where id_fuente=?",fuente);}
 @Test void otraOrganizacionExistenteNoObtieneElTrabajoNiLoLiquida(){
  var otra=new OrigenesDePrueba(jdbc,gestor,captura,contextos).empresa().agente();assertNotEquals(agente.idOrganizacion(),otra.idOrganizacion());
  var s=solicitud(1);admitir(s);assertTrue(manual.manual(otra.idOrganizacion(),s.operacion(),false).isEmpty());
  assertThrows(NoEncontradoException.class,()->tx.execute(t->servicio.conciliarManual(new ConciliacionManual(otra.idOrganizacion(),s.operacion(),BigDecimal.ONE,"TEST_ACREDITADO"),financiero)));
  monto("0.5","select retenido from b0_fuente_capacidad where id_fuente=?",fuente);
 }
 @Test void dosTransaccionesNoSuperanLaCotaNiDuplicanLaReserva() throws Exception {
  var s=solicitud(2);var otra=solicitud(2);var salida=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{List<Future<Boolean>> fs=new ArrayList<>();for(var d:List.of(s,otra))fs.add(pool.submit(()->{salida.await();try{admitir(d);return true;}catch(ReglaNegocioException e){assertTrue(e.getMessage().contains("capacidad operativa"));return false;}}));salida.countDown();int ganadoras=0;for(var f:fs)if(f.get(30,TimeUnit.SECONDS))ganadoras++;assertEquals(1,ganadoras);monto("2","select reservadas from b0_regla_trabajo_manual where id=?",regla);monto("1","select retenido from b0_fuente_capacidad where id_fuente=?",fuente);
  }finally{pool.shutdownNow();}
 }
 @Test void dosTransaccionesConElMismoComandoConservanUnaSolaReserva() throws Exception {
  var s=solicitud(2);var salida=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{var x=pool.submit(()->{salida.await();return admitir(s);});var y=pool.submit(()->{salida.await();return admitir(s);});salida.countDown();assertEquals(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS));
   assertEquals(1,jdbc.queryForObject("select count(*) from b0_operacion_ia where organizacion_id=? and id_operacion=?",Integer.class,agente.idOrganizacion(),s.operacion()));monto("2","select reservadas from b0_regla_trabajo_manual where id=?",regla);monto("1","select retenido from b0_fuente_capacidad where id_fuente=?",fuente);
  }finally{pool.shutdownNow();}
 }
 @Test void dosOrganizacionesCompitenPorLaMismaCotaSinCruzarSusFuentes() throws Exception {
  var origenes=new OrigenesDePrueba(jdbc,gestor,captura,contextos);var otra=origenes.empresa().agente();var otraSid=origenes.sesion(otra);autorizados.add(otra);UUID otraFuente=UUID.randomUUID();financiar(otra,UUID.randomUUID(),otraFuente);
  var una=solicitud(2);var dos=solicitud(2);var salida=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{var x=pool.submit(()->{salida.await();try{tx.execute(t->servicio.admitirManual(una,agente,sid));return true;}catch(ReglaNegocioException e){assertTrue(e.getMessage().contains("capacidad operativa"));return false;}});
   var y=pool.submit(()->{salida.await();try{tx.execute(t->servicio.admitirManual(dos,otra,otraSid));return true;}catch(ReglaNegocioException e){assertTrue(e.getMessage().contains("capacidad operativa"));return false;}});
   salida.countDown();boolean a=x.get(30,TimeUnit.SECONDS),b=y.get(30,TimeUnit.SECONDS);assertNotEquals(a,b);
   monto("2","select reservadas from b0_regla_trabajo_manual where id=?",regla);monto("1","select sum(retenido) from b0_fuente_capacidad where id_fuente in (?,?)",fuente,otraFuente);
   assertEquals(1,jdbc.queryForObject("select count(*) from b0_operacion_ia where regla_manual=?",Integer.class,regla));
   assertTrue(manual.manual(otra.idOrganizacion(),una.operacion(),false).isEmpty());assertTrue(manual.manual(agente.idOrganizacion(),dos.operacion(),false).isEmpty());
  }finally{pool.shutdownNow();}
 }
 static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
}
