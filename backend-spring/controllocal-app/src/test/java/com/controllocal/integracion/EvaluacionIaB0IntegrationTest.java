package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.persistence.presupuesto.EvaluacionesIaB0Repository.*;
import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository;
import com.controllocal.service.*;
import com.controllocal.service.archivo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.*;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.plataforma.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.soporte.*;
import com.controllocal.domain.presupuesto.UnidadesDeConsumo.*;
import com.controllocal.web.almacen.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** PG/C1/carril2/disco reales. Acreditador y fondos SINTÉTICOS TEST; cero proveedor o gasto real. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,EvaluacionIaB0IntegrationTest.AcreditacionTest.class},
 webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties="spring.datasource.hikari.maximum-pool-size=8")
class EvaluacionIaB0IntegrationTest {
 @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
 @TestConfiguration static class AcreditacionTest {
  @Bean Acreditador acreditador351(){return new Acreditador();}
 }
 static final class Acreditador implements AcreditacionesTitularPlataforma {
  final Map<UUID,Decision> decisiones=new ConcurrentHashMap<>();
  public Optional<Decision> acreditar(UUID id){return Optional.ofNullable(decisiones.get(id));}
 }
 @Autowired ApplicationContext app;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager gestor;
 @Autowired PresupuestoIaRepository dinero;@Autowired EvaluacionesIaB0Repository planes;
 @Autowired AtribucionesPlataforma atribuciones;@Autowired EvaluacionesIaB0 evaluaciones;
 @Autowired UsoAlmacenamientoB0 uso;@Autowired UsoAlmacenamientoB0Repository espacio;
 @Autowired Documentos json;@Autowired Acreditador acreditador;
 @TempDir Path directorio;
 TransactionTemplate tx;PresupuestoIaService presupuesto;SesionPlataforma admin,operador,financiero,qa;
 long cuenta;UUID plan,caso,archivo,fondo,fin,grantOp,grantFin,grantQa;
 String version,namespace;AlmacenDisco disco;ArchivosEvaluacionIaB0 archivos;
 final byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jx1sAAAAASUVORK5CYII=");
 Instant ahora;CountDownLatch barreraFondo;
 @BeforeEach void preparar(TestInfo info){
  EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);ahora=Instant.now();
  plan=UUID.randomUUID();caso=UUID.randomUUID();archivo=UUID.randomUUID();fondo=UUID.randomUUID();fin=UUID.randomUUID();version=UUID.randomUUID().toString();
  disco=new AlmacenDisco(directorio.toString());namespace=disco.identidadTecnica();admin=crearCuenta();
  cuenta=app.getBean(ContextosService.class).exigirIdentidad(admin.referenciaSesion(),admin.revisionSesion()).identidad().idCuenta();
  tx.executeWithoutResult(t->{
   jdbc.update("INSERT INTO b0_fondo_tecnico(id_fondo,moneda,financiado,desde,hasta,evidencia_financiacion,habilitado) VALUES(?,'USD',2,?,?,'TEST NO FONDOS REALES',true)",
    fondo,utc(ahora.minusSeconds(10)),utc(ahora.plusSeconds(3600)));
   jdbc.update("""
    INSERT INTO b0_politica_capacidad_fisica(namespace_hash,version,capacidad_bytes,ocupacion_conciliada_bytes,
     conciliada_en,evidencia_calibracion,evidencia_aprobacion,evidencia_financiacion,evidencia_conciliacion,desde,hasta,habilitada)
    VALUES(?,?,100000,0,?,'TEST','TEST','TEST NO GASTO','TEST',?,?,true)
    """,namespace,version,utc(ahora.minusSeconds(20)),utc(ahora.minusSeconds(10)),utc(ahora.plusSeconds(3600)));
   jdbc.update("INSERT INTO b0_capacidad_fisica_actual(namespace_hash,version) VALUES(?,?)",namespace,version);
  });
  String config=json.objetoCanonico(new EvaluacionesIaB0.Configuracion(List.of(
   new EvaluacionesIaB0.EtapaPermitida("GENERACION","PRV_TEST","MODELO_TEST","TARIFA_TEST",List.of(new EvaluacionesIaB0.LimiteUnidad("u","u",n("10")))),
   new EvaluacionesIaB0.EtapaPermitida("QC","PRV_TEST","MODELO_TEST","TARIFA_TEST",List.of(new EvaluacionesIaB0.LimiteUnidad("u","u",n("10")))))));
  var p=new Plan(plan,version,"a".repeat(64),"EVALUACION_TEST",config,"MANIFIESTO TEST","RUBRICA TEST","POLITICA TEST SIN TTL PRODUCTIVO",
   "PRV_TEST",namespace,n("0.25"),2,1000,2);
  tx.executeWithoutResult(t->evaluaciones.registrarPlan(p,decision("REGISTRAR_PLAN_EVALUACION",p,null),admin));
  var c=new Caso(caso,plan,version,"CASO_TEST",hash(png),"SINTETICO TEST","DATOS PROPIOS TEST",archivo,png.length,"image/png");
  tx.executeWithoutResult(t->evaluaciones.registrarCaso(c,decision("REGISTRAR_CASO_EVALUACION",c,null),admin));
  grantFin=conceder(Atribucion.FINANCIERO_PLATFORM,Set.of(Accion.FINANCIAR,Accion.REVOCAR_FINANCIACION,Accion.LEER_LEDGER),Set.of(Campo.LEDGER),null,fondo,cuenta);
  grantOp=conceder(Atribucion.OPERACION_IA_PLATFORM,Set.of(Accion.ADMITIR,Accion.INICIAR,Accion.CONSULTAR_ESTADO),Set.of(Campo.ESTADO),caso,null,cuenta);
  grantQa=conceder(Atribucion.BROX_PLATFORM,Set.of(Accion.LEER_MUESTRA),Set.of(Campo.MUESTRA),caso,null,cuenta);
  financiero=conGrant(admin,grantFin);operador=conGrant(admin,grantOp);qa=conGrant(admin,grantQa);
  tx.executeWithoutResult(t->evaluaciones.financiar(fin,plan,version,fondo,n("0.5"),ahora.minusSeconds(1),ahora.plusSeconds(3000),"FINANCIACION TEST SIN PAGO",financiero));
  presupuesto=servicio(true);
  var salida=new SalidaBinariaPersistente(disco,app.getBean(SeguimientoSalidaBinaria.class));
  var confirmacion=new ConfirmacionAlmacenamientoB0(uso,app.getBean(IntentoSalidaBinariaRepository.class),app.getBean(EntityManager.class));
  var cuota=new CuotaArchivosB0(uso,confirmacion,disco);
  archivos=new ArchivosEvaluacionIaB0(evaluaciones,uso,salida,cuota,disco,gestor,presupuesto,app.getBean(SeguimientoSalidaBinaria.class));
 }
 @AfterEach void limpiar(){EvidenciaC4DePrueba.limpiar();}
 PresupuestoIaService servicio(boolean habilitado){
  var s=new PresupuestoIaService(dinero,app.getBean(ContextoPresupuestoB0.class),List.of(),json,app.getBean(DerechosDePlanB0.class),habilitado);
  ReflectionTestUtils.setField(s,"evaluaciones",evaluaciones);ReflectionTestUtils.setField(s,"almacenamiento",uso);return s;
 }
 SesionPlataforma crearCuenta(){return tx.execute(t->{
  String x=UUID.randomUUID().toString(),sid=Base64.getUrlEncoder().withoutPadding().encodeToString((x+x).substring(0,32).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
  long id=jdbc.queryForObject("INSERT INTO cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) VALUES(?,?,clock_timestamp(),'TEST NO LOGIN') RETURNING id_cuenta_acceso",Long.class,"eval-"+x,"eval-"+x+"@example.invalid");
  jdbc.update("""
   INSERT INTO sesion_acceso(referencia_hash,semilla_contextos,id_cuenta_acceso,revision,creada_en,emitida_en,vence_en)
   VALUES(?,?,?,1,?,?,?)
   """,Idempotencia.huellaExacta(sid),"d".repeat(64),id,utc(ahora.minusSeconds(2)),utc(ahora.minusSeconds(1)),utc(ahora.plusSeconds(1800)));
  return new SesionPlataforma(sid,1,null);
 });}
 UUID decision(String accion,Object contenido,Long destinatario){
  UUID id=UUID.randomUUID();acreditador.decisiones.put(id,new AcreditacionesTitularPlataforma.Decision(id,cuenta,destinatario,accion,
   Idempotencia.huellaExacta(json.objetoCanonico(contenido)),"ACREDITADOR TEST NO AUTORIZACION REAL",Instant.now()));return id;
 }
 UUID conceder(Atribucion a,Set<Accion> acciones,Set<Campo> campos,UUID casoScope,UUID fondoScope,long destinatario){
  UUID id=UUID.randomUUID();var d=new Datos(id,destinatario,a,plan,version,casoScope,fondoScope,"EVALUACION_TEST",acciones,campos,ahora.minusSeconds(1),ahora.plusSeconds(3500),"TEST NO GRANT REAL");
  tx.executeWithoutResult(t->atribuciones.conceder(d,decision("CONCEDER_ATRIBUCION",d,destinatario),admin));return id;
 }
 static SesionPlataforma conGrant(SesionPlataforma s,UUID g){return new SesionPlataforma(s.referenciaSesion(),s.revisionSesion(),g);}
 SolicitudEvaluacion solicitud(){return new SolicitudEvaluacion(UUID.randomUUID(),caso,version,"a".repeat(64),fin,"b".repeat(64));}
 ReferenciaEvaluacion ref(SolicitudEvaluacion s){return new ReferenciaEvaluacion(s.caso(),s.operacion());}
 Admision admitir(SolicitudEvaluacion s){return tx.execute(t->presupuesto.admitirEvaluacion(s,operador));}
 void iniciar(SolicitudEvaluacion s){assertTrue(Boolean.TRUE.equals(tx.execute(t->presupuesto.iniciarEvaluacion(s,operador))));}
 void respuestaGenerada(SolicitudEvaluacion s,UUID salida,ResultadoFacturable m){
  UUID io=UUID.nameUUIDFromBytes(("BROX-EVALUACION-ARCHIVO\u0000"+caso+"\u0000"+s.operacion()+"\u0000"+salida).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var d=new DescriptorSalidaEvaluacion(salida,io,hash(png),png.length,"image/png");
  String contenido=json.objetoCanonico(new com.controllocal.service.materialia.EvaluacionMaterialIa.Generacion(null,"image/png","PROCESO_TEST","VERSION_TEST"));
  tx.execute(t->presupuesto.conservarRespuestaEvaluacion(ref(s),null,PresupuestoIaService.claveDeIntento(ref(s)),contenido,m,d));
 }
 ResultadoFacturable medicion(String costo){
  return new ResultadoFacturable(List.of(new MedicionFacturable(UUID.randomUUID(),"PRV_TEST","MODELO_TEST","TARIFA_TEST",
   List.of(new Medicion("u","u",n("1"),null)),List.of(new Tarifa("u","u",n(costo),BigDecimal.ONE)),null,"FACTURACION-TEST:"+UUID.randomUUID())));
 }
 @Test void lecturaInternaOpNoConcedeLecturaQaNiUsaContextoComercial(){
  archivos.guardarOriginal(caso,archivo,"image/png",png,operador);
  assertArrayEquals(png,archivos.leerOriginalParaProcesar(caso,operador));
  assertThrows(AccesoNoAutorizadoException.class,()->archivos.leerOriginalParaProcesar(caso,qa));
  assertThrows(AccesoNoAutorizadoException.class,()->archivos.leerMuestra(caso,archivo,operador));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_operacion_ia WHERE id_caso_evaluacion=?",Integer.class,caso));
 }
 @Test void recuperaElResultadoDelMismoClaimSinNuevaEscrituraNiLecturaQa(){
  var s=solicitud();admitir(s);iniciar(s);
  UUID salida=UUID.randomUUID();respuestaGenerada(s,salida,medicion("0.1"));archivos.recibir(ref(s),salida,"image/png",png);
  int intentos=jdbc.queryForObject("SELECT count(*) FROM intento_salida_binaria WHERE id_caso_evaluacion=?",Integer.class,caso);
  assertArrayEquals(png,archivos.recuperarResultadoParaProcesar(ref(s),salida,"image/png",hash(png),operador).orElseThrow());
  assertArrayEquals(png,archivos.recuperarResultadoParaProcesar(ref(s),salida,"image/png",hash(png),operador).orElseThrow());
  assertThrows(AccesoNoAutorizadoException.class,()->archivos.recuperarResultadoParaProcesar(ref(s),salida,"image/png",hash(png),qa));
  assertEquals(intentos,jdbc.queryForObject("SELECT count(*) FROM intento_salida_binaria WHERE id_caso_evaluacion=?",Integer.class,caso));
 }
 @Test void recuperacionAusenteNoGeneraPutNiInfiere(){
  var s=solicitud();admitir(s);iniciar(s);
  UUID salida=UUID.randomUUID();respuestaGenerada(s,salida,medicion("0.1"));
  assertTrue(archivos.recuperarResultadoParaProcesar(ref(s),salida,"image/png",hash(png),operador).isEmpty());
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM intento_salida_binaria WHERE id_caso_evaluacion=?",Integer.class,caso));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_caso_evaluacion=?",Integer.class,caso));
 }
 @Test void cuentaSinPertenenciaAdmiteSinPerfilComercial(){
  var s=solicitud();assertEquals("RESERVADA",admitir(s).estado());
  var o=dinero.evaluacion(caso,s.operacion()).orElseThrow();assertNull(o.organizacion());assertNull(o.perfil());assertNull(o.maximoCreditos());
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_imputacion WHERE id_operacion=?",Integer.class,s.operacion()));
 }
 @Test void mismoComandoNoDuplicaReserva(){
  var s=solicitud();admitir(s);admitir(s);
  assertEquals(n("0.250000000000"),jdbc.queryForObject("SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,fondo));
 }
 @Test void gastoOffImpideAdmitirAunqueTodoLoDemasSeaValido(){
  var s=solicitud();assertThrows(CapacidadDeshabilitadaException.class,()->tx.execute(t->servicio(false).admitirEvaluacion(s,operador)));
  assertTrue(dinero.evaluacion(caso,s.operacion()).isEmpty());
 }
 @Test void identidadSolaYConcesionAjenaNoAbrenElPlan(){
  var s=solicitud();assertThrows(AccesoNoAutorizadoException.class,()->tx.execute(t->presupuesto.admitirEvaluacion(s,admin)));
  var otra=crearCuenta();assertThrows(AccesoNoAutorizadoException.class,()->tx.execute(t->presupuesto.admitirEvaluacion(s,conGrant(otra,grantOp))));
 }
 @Test void financieroNoAdquiereMuestrasYOperadorNoAdquiereLedger(){
  assertThrows(AccesoNoAutorizadoException.class,()->evaluaciones.exigirLecturaMuestra(caso,financiero));
  assertThrows(AccesoNoAutorizadoException.class,()->evaluaciones.leerFinanciacion(fin,operador));
  assertDoesNotThrow(()->evaluaciones.exigirLecturaMuestra(caso,qa));
 }
 @Test void versionOContenidoDiferenteNoReutilizaAdmision(){
  var s=solicitud();admitir(s);
  assertThrows(ConflictoException.class,()->tx.execute(t->presupuesto.admitirEvaluacion(new SolicitudEvaluacion(s.operacion(),caso,version,"a".repeat(64),fin,"c".repeat(64)),operador)));
  assertThrows(ConflictoException.class,()->tx.execute(t->presupuesto.admitirEvaluacion(new SolicitudEvaluacion(UUID.randomUUID(),caso,"OTRA","a".repeat(64),fin,"b".repeat(64)),operador)));
 }
 @Test void dosTransaccionesReclamanUnaSolaEjecucion() throws Exception {
  var s=solicitud();admitir(s);var ejecutor=Executors.newFixedThreadPool(2);
  try{var inicio=new CountDownLatch(1);Callable<Boolean> tarea=()->{inicio.await();return tx.execute(t->presupuesto.iniciarEvaluacion(s,operador));};
   var a=ejecutor.submit(tarea);var b=ejecutor.submit(tarea);inicio.countDown();
   assertEquals(1,(a.get(10,TimeUnit.SECONDS)?1:0)+(b.get(10,TimeUnit.SECONDS)?1:0));
  }finally{ejecutor.shutdownNow();}
 }
 @Test void techoCompartidoDelPlanNoSeSobrepasa(){
  admitir(solicitud());admitir(solicitud());assertThrows(ReglaNegocioException.class,()->admitir(solicitud()));
  assertEquals(n("0.500000000000"),jdbc.queryForObject("SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,fondo));
 }
 @Test void costoNativoNoSeRedondeaPorFragmentoNiCreaCreditos(){
  var s=solicitud();admitir(s);iniciar(s);var m=medicion("0.1234567890123456789");
  tx.executeWithoutResult(t->presupuesto.registrarMediciones(ref(s),m));
  assertEquals(n("0.1234567890123456789"),jdbc.queryForObject("SELECT costo_usd FROM b0_medicion_tecnica WHERE id_operacion=?",BigDecimal.class,s.operacion()));
  tx.executeWithoutResult(t->presupuesto.conciliar(ref(s),m));
  assertNull(jdbc.queryForObject("SELECT creditos_liquidados FROM b0_operacion_ia WHERE id_caso_evaluacion=? AND id_operacion=?",BigDecimal.class,caso,s.operacion()));
  assertEquals(n("0.123456789012"),jdbc.queryForObject("SELECT liquidado FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,fondo));
 }
 @Test void hechoProveedorNoSeDuplicaConOtroComando(){
  var a=solicitud();var b=solicitud();admitir(a);admitir(b);iniciar(a);iniciar(b);var m=medicion("0.1");
  tx.executeWithoutResult(t->presupuesto.conciliar(ref(a),m));
  assertThrows(ConflictoException.class,()->tx.execute(t->presupuesto.conciliar(ref(b),m)));
 }
 @Test void revocarCortaNuevosClaimsPeroConservaElHechoYaCausado(){
  var a=solicitud();var b=solicitud();admitir(a);admitir(b);iniciar(a);
  UUID d=decision("REVOCAR_ATRIBUCION",Map.of("concesion",grantOp,"motivo","TEST revocación"),cuenta);
  tx.executeWithoutResult(t->atribuciones.revocar(grantOp,d,"TEST revocación",admin));
  assertThrows(AccesoNoAutorizadoException.class,()->tx.execute(t->presupuesto.iniciarEvaluacion(b,operador)));
  assertEquals("LIQUIDADA",tx.execute(t->presupuesto.conciliar(ref(a),medicion("0.1"))).estado());
 }
 @Test void timeoutMantieneProvisionYSinSegundoClaim(){
  var s=solicitud();admitir(s);iniciar(s);tx.executeWithoutResult(t->presupuesto.marcarIncierta(ref(s)));
  assertFalse(Boolean.TRUE.equals(tx.execute(t->presupuesto.iniciarEvaluacion(s,operador))));
  assertEquals(n("0.250000000000"),jdbc.queryForObject("SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,fondo));
 }
 @Test void etapaPendienteImpideCierreYNuncaSeReclamaDosVeces(){
  var s=solicitud();admitir(s);iniciar(s);
  tx.executeWithoutResult(t->presupuesto.programarEtapa(ref(s),"QC","PRV_TEST","e".repeat(64)));
  assertTrue(Boolean.TRUE.equals(tx.execute(t->presupuesto.iniciarEtapaEvaluacion(s,"QC",operador))));
  assertFalse(Boolean.TRUE.equals(tx.execute(t->presupuesto.iniciarEtapaEvaluacion(s,"QC",operador))));
  assertThrows(ConflictoException.class,()->tx.execute(t->presupuesto.conciliar(ref(s),medicion("0.1"))));
 }
 @Test void originalDeCorpusNoNecesitaOperacionIaYSeLeeSoloConQa(){
  assertEquals(archivo,archivos.guardarOriginal(caso,archivo,"image/png",png,operador));
  assertArrayEquals(png,archivos.leerMuestra(caso,archivo,qa));
  assertThrows(AccesoNoAutorizadoException.class,()->archivos.leerMuestra(caso,archivo,operador));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_operacion_ia WHERE id_caso_evaluacion=?",Integer.class,caso));
 }
 @Test void recuperacionDeOriginalNoDuplicaBytesNiReserva(){
  archivos.guardarOriginal(caso,archivo,"image/png",png,operador);archivos.guardarOriginal(caso,archivo,"image/png",png,operador);
  assertEquals((long)png.length,jdbc.queryForObject("SELECT confirmados FROM b0_uso_capacidad_fisica WHERE namespace_hash=?",Long.class,namespace));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM intento_salida_binaria WHERE id_caso_evaluacion=?",Integer.class,caso));
 }
 @Test void limiteDeTransporteEsElDeLaPrevisionPersistida(){
  var s=solicitud();admitir(s);var limite=uso.limiteRecepcion(UsoAlmacenamientoB0.referenciaEvaluacion(caso,s.operacion()));
  assertEquals(1000,limite.maximoBytesContenido());assertEquals(namespace,limite.namespaceHash());assertEquals(version,limite.versionFuente());
 }
 @Test void fksDeOperacionYCasoNoPermitenSuplantarOrigen(){
  var s=solicitud();admitir(s);var o=dinero.evaluacion(caso,s.operacion()).orElseThrow();
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(t->jdbc.update("""
   INSERT INTO b0_medicion_tecnica(organizacion_id,id_operacion,id_operacion_b0,id_caso_evaluacion,id_medicion,proveedor,modelo,version_tarifa,
    unidades_originales,tarifas_originales,costo_usd,estado_costo,referencia_fuente,huella,huella_intento,registrada_en)
   VALUES(NULL,?,?,?,?,'TEST','TEST','TEST','[]','[]',0,'CALCULADO','TEST',?,?,clock_timestamp())
   """,s.operacion(),o.tecnica(),UUID.randomUUID(),UUID.randomUUID(),"a".repeat(64),"f".repeat(64))));
 }

 @Test void cancelacionComprobadaLiberaFondoYPrevisionSinMedicionCero(){
  var s=solicitud();admitir(s);iniciar(s);var r=ref(s);String clave=PresupuestoIaService.claveDeIntento(r);
  var prueba=new ComprobacionNoProcesamiento(clave,"PRV_TEST",clave,"TEST PROVEEDOR NO INICIADO","e".repeat(64),Instant.now());
  tx.executeWithoutResult(t->presupuesto.cancelarNoProcesada(r,prueba));
  tx.executeWithoutResult(t->presupuesto.cancelarNoProcesada(r,prueba));
  assertEquals(n("0.000000000000"),jdbc.queryForObject("SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,fondo));
  assertEquals(0L,jdbc.queryForObject("SELECT reservados FROM b0_uso_capacidad_fisica WHERE namespace_hash=?",Long.class,namespace));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_operacion=?",Integer.class,s.operacion()));
 }
 @Test void revocarFinanciacionNoBorraElConsumoPrevio(){
  var s=solicitud();admitir(s);iniciar(s);
  tx.executeWithoutResult(t->evaluaciones.revocarFinanciacion(fin,"TEST detener lote",financiero));
  assertThrows(CapacidadDeshabilitadaException.class,()->admitir(solicitud()));
  tx.executeWithoutResult(t->presupuesto.conciliar(ref(s),medicion("0.1")));
  assertEquals(n("0.100000000000"),jdbc.queryForObject("SELECT liquidado FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,fondo));
 }
 @Test void liquidacionIdempotenteNoDuplicaElCargo(){
  var s=solicitud();admitir(s);iniciar(s);var m=medicion("0.1");
  tx.executeWithoutResult(t->presupuesto.conciliar(ref(s),m));tx.executeWithoutResult(t->presupuesto.conciliar(ref(s),m));
  assertEquals(n("0.100000000000"),jdbc.queryForObject("SELECT liquidado FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,fondo));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE id_operacion=?",Integer.class,s.operacion()));
 }
 @Test void recepcionPrivadaYRecuperacionUsanElMismoObjetoSinNuevoConsumo(){
  var s=solicitud();admitir(s);iniciar(s);UUID salida=UUID.randomUUID();
  respuestaGenerada(s,salida,medicion("0.1"));
  archivos.recibir(ref(s),salida,"image/png",png);archivos.recibir(ref(s),salida,"image/png",png);
  assertArrayEquals(png,archivos.leerMuestra(caso,salida,qa));
  assertEquals((long)png.length,jdbc.queryForObject("SELECT confirmados FROM b0_uso_capacidad_fisica WHERE namespace_hash=?",Long.class,namespace));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_archivo_evaluacion WHERE operacion_b0 IS NOT NULL AND caso=?",Integer.class,caso));
 }
 @Test void unArchivoNoSePuedeAtribuirAOtroCaso(){
  archivos.guardarOriginal(caso,archivo,"image/png",png,operador);
  var rechazo=assertThrows(org.springframework.dao.DataAccessException.class,()->tx.execute(t->jdbc.update(
   "UPDATE intento_salida_binaria SET id_caso_evaluacion=? WHERE id_caso_evaluacion=?",UUID.randomUUID(),caso)));
  var causa=assertInstanceOf(java.sql.SQLException.class,rechazo.getMostSpecificCause());
  assertEquals("P0001",causa.getSQLState(),"El trigger de dueño inmutable rechaza antes de la FK.");
  assertTrue(causa.getMessage().contains("El intento conserva dueño"));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM intento_salida_binaria WHERE id_caso_evaluacion=?",Integer.class,caso));
  assertArrayEquals(png,archivos.leerMuestra(caso,archivo,qa));
 }
 @Test void mismoUuidEnDosOrganizacionesYEvaluacionConservaTresOperaciones(){
  var s=solicitud();admitir(s);
  var a=EspacioIndividualB0DePrueba.crear(jdbc,tx);var b=EspacioIndividualB0DePrueba.crear(jdbc,tx);
  admitirComercial(a,s.operacion());admitirComercial(b,s.operacion());
  var oa=dinero.operacion(a.idOrganizacion(),s.operacion()).orElseThrow();
  var ob=dinero.operacion(b.idOrganizacion(),s.operacion()).orElseThrow();
  var oe=dinero.evaluacion(caso,s.operacion()).orElseThrow();
  assertEquals(3,Set.of(oa.tecnica(),ob.tecnica(),oe.tecnica()).size());
  assertEquals(n("0.750000000000"),jdbc.queryForObject("SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,fondo));
 }
 void admitirComercial(Actor a,UUID comando){
  String perfil="IDENTIDAD_TEST_"+UUID.randomUUID().toString().substring(0,8);UUID ciclo=UUID.randomUUID(),fuente=UUID.randomUUID();
  tx.executeWithoutResult(t->{
   jdbc.update("""
    INSERT INTO b0_perfil_operativo(perfil,version,id_fondo,normalizador,tarifas_referencia,maximo_creditos,maximo_tecnico_usd,
     limite_intentos,evidencia_calibracion,evidencia_aprobacion,habilitado)
    VALUES(?,?,?,1,'[{"clase":"u","unidad":"u","importeUsd":1,"porCantidad":1}]',1,0.25,2,'TEST NO CALIBRACION','TEST NO APROBACION REAL',true)
    """,perfil,version,fondo);
   jdbc.update("INSERT INTO b0_perfil_actual(perfil,version) VALUES(?,?)",perfil,version);
   jdbc.update("""
    INSERT INTO b0_ciclo(organizacion_id,id_ciclo,plan,desde,hasta,dia_aniversario,importe_financiado_usd,
     orden_verificada,evidencia_pago,registrado_en) VALUES(?,?,'ONE',?,?,1,15,?,'TEST NO PAGO REAL',?)
    """,a.idOrganizacion(),ciclo,utc(ahora.minusSeconds(1)),utc(ahora.plusSeconds(3000)),UUID.randomUUID(),utc(ahora));
   jdbc.update("""
    INSERT INTO b0_fuente_capacidad(organizacion_id,id_fuente,id_ciclo,origen,id_persona_destinataria,plaza,
     origen_verificado,inicial,desde,vence,registrado_en) VALUES(?,?,?,'MENSUAL_AGENTE',?,1,'TEST NO COMPRA REAL',3000,?,?,?)
    """,a.idOrganizacion(),fuente,ciclo,a.idPersona(),utc(ahora.minusSeconds(1)),utc(ahora.plusSeconds(3000)),utc(ahora));
   jdbc.update("INSERT INTO b0_plaza(organizacion_id,id_ciclo,tipo,numero,id_persona,id_fuente) VALUES(?,?,'AGENTE',1,?,?)",a.idOrganizacion(),ciclo,a.idPersona(),fuente);
  });
  var autorizador=new AutorizadorRecursoIa(){
   public boolean atiende(String p,String tipo){return perfil.equals(p)&&"RECURSO_TEST".equals(tipo);}
   public void reautorizar(Solicitud x,Actor actor){if(!actor.equals(a))throw new AccesoNoAutorizadoException();}
  };
  var servicio=new PresupuestoIaService(dinero,app.getBean(ContextoPresupuestoB0.class),List.of(autorizador),json,app.getBean(DerechosDePlanB0.class),true);
  var sid=com.controllocal.service.actuacion.ActuacionesService.ContextoSesion.de(
   SesionesDePrueba.abrir(jdbc,app.getBean(ContextosService.class),a).acceso());
  var solicitud=new Solicitud(comando,perfil,version,new Recurso("RECURSO_TEST",UUID.randomUUID().toString(),"TEST","a".repeat(64)),"b".repeat(64));
  if(barreraFondo!=null){barreraFondo.countDown();try{if(!barreraFondo.await(10,TimeUnit.SECONDS))throw new AssertionError("TEST barrera fondo");}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new AssertionError(ex);}}
  tx.executeWithoutResult(t->servicio.admitir(solicitud,a,sid));
 }

 @Test void comercialYEvaluacionCompitenPorElMismoFondoSinSobregiro() throws Exception {
  tx.executeWithoutResult(t->jdbc.update("UPDATE b0_fondo_tecnico SET financiado=0.25 WHERE id_fondo=?",fondo));
  var actor=EspacioIndividualB0DePrueba.crear(jdbc,tx);var s=solicitud();barreraFondo=new CountDownLatch(2);
  var ejecutor=Executors.newFixedThreadPool(2);
  try{
   Future<Boolean> comercial=ejecutor.submit(()->{try{admitirComercial(actor,UUID.randomUUID());return true;}catch(CapacidadDeshabilitadaException ex){return false;}});
   Future<Boolean> evaluacion=ejecutor.submit(()->{barreraFondo.countDown();if(!barreraFondo.await(10,TimeUnit.SECONDS))throw new AssertionError("TEST barrera fondo");
    try{admitir(s);return true;}catch(CapacidadDeshabilitadaException ex){return false;}});
   assertEquals(1,(comercial.get(15,TimeUnit.SECONDS)?1:0)+(evaluacion.get(15,TimeUnit.SECONDS)?1:0));
   assertEquals(n("0.250000000000"),jdbc.queryForObject("SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,fondo));
  }finally{ejecutor.shutdownNow();}
 }

 @Test void antecedenteSinOperacionNoAdmiteIdentidadDeOtraOperacion(){
  var local=EspacioIndividualB0DePrueba.crear(jdbc,tx);var ajeno=EspacioIndividualB0DePrueba.crear(jdbc,tx);
  UUID comando=UUID.randomUUID();admitirComercial(ajeno,comando);
  var operacionAjena=dinero.operacion(ajeno.idOrganizacion(),comando).orElseThrow();
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(t->jdbc.update("""
   INSERT INTO b0_antecedente(organizacion_id,id_operacion,id_operacion_b0,hecho,contenido,registrado_en)
   VALUES(?,NULL,?,'TEST_ANTECEDENTE_AJENO','{}',clock_timestamp())
   """,local.idOrganizacion(),operacionAjena.tecnica())));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(t->jdbc.update("""
   INSERT INTO b0_antecedente(organizacion_id,id_operacion,id_operacion_b0,hecho,contenido,registrado_en)
   VALUES(?,?,NULL,'TEST_ANTECEDENTE_INCOMPLETO','{}',clock_timestamp())
   """,ajeno.idOrganizacion(),comando)));
  tx.executeWithoutResult(t->dinero.antecedente(local.idOrganizacion(),null,"TEST_SIN_OPERACION","{}",Instant.now()));
  assertEquals(1,jdbc.queryForObject("""
   SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND hecho='TEST_SIN_OPERACION'
    AND id_operacion IS NULL AND id_operacion_b0 IS NULL AND id_caso_evaluacion IS NULL
   """,Integer.class,local.idOrganizacion()));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE hecho='TEST_ANTECEDENTE_AJENO' AND organizacion_id=?",Integer.class,local.idOrganizacion()));
  var s=solicitud();admitir(s);
  assertEquals(1,jdbc.queryForObject("""
   SELECT count(*) FROM b0_antecedente a JOIN b0_operacion_ia o ON o.id_operacion_b0=a.id_operacion_b0
   WHERE a.id_caso_evaluacion=? AND a.id_operacion=? AND a.organizacion_id IS NULL
    AND a.hecho='RESERVA_ADMITIDA' AND o.id_caso_evaluacion=a.id_caso_evaluacion AND o.id_operacion=a.id_operacion
   """,Integer.class,caso,s.operacion()));
 }
 static BigDecimal n(String s){return new BigDecimal(s);}
 static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
 static String hash(byte[] b){return com.controllocal.service.documentos.EvaluacionTecnicaFoto.huella(b);}
}
