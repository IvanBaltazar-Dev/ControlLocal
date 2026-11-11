package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.repositorio.InvestigacionesMercadoRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.mercado.*;
import com.controllocal.service.mercado.ContratoInvestigacionManual.*;
import com.controllocal.service.mercado.DatosInvestigacionMercado.*;
import com.controllocal.service.plataforma.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.presupuesto.PresupuestoIaService;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.soporte.*;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Recorrido PG real I0/B0/R25; fuentes y ejecución IA TEST explícitas, sin proveedor ni tarifa productiva. */
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,InvestigacionesMercadoIntegrationTest.Config.class},webEnvironment=SpringBootTest.WebEnvironment.MOCK,
 properties={"spring.datasource.hikari.maximum-pool-size=6","controllocal.investigacion.manual.habilitada=true"})
class InvestigacionesMercadoIntegrationTest {
 @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
 @TestConfiguration static class Config {@Bean Acreditador acreditadorI0Test(){return new Acreditador();}}
 static class Acreditador implements AcreditacionesTitularPlataforma {
  final Map<UUID,Decision> decisiones=new ConcurrentHashMap<>();public Optional<Decision> acreditar(UUID id){return Optional.ofNullable(decisiones.get(id));}
 }
 @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager gestor;@Autowired ContextosService contextos;
 @Autowired MotorDeCaptura captura;@Autowired Documentos json;@Autowired AtribucionesPlataforma atribuciones;
 @Autowired Acreditador acreditador;@Autowired PresupuestoIaService presupuesto;@Autowired InvestigacionesMercado servicio;
 @Autowired InvestigacionesMercadoRepository repo;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;@Autowired com.controllocal.web.seguridad.TokenService tokens;
 Actor agente;ContextoSesion sid;SesionPlataforma financiero,investigador,revisor;Configuracion politica;UUID regla,fuente;Instant ahora;TransactionTemplate tx;
 @BeforeEach void preparar(TestInfo info){
  EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);ahora=Instant.now();regla=UUID.randomUUID();fuente=UUID.randomUUID();
  var origenes=new OrigenesDePrueba(jdbc,gestor,captura,contextos);agente=origenes.empresa().agente();sid=origenes.sesion(agente);
  financiero=conceder(Atribucion.FINANCIERO_PLATFORM,Set.of(Accion.CONFIGURAR_REGLA_MANUAL,Accion.CONCILIAR_TRABAJO,Accion.CONSULTAR_TRABAJO),Set.of(Campo.LEDGER,Campo.METADATOS_TRABAJO));
  investigador=conceder(Atribucion.INVESTIGACION_PLATFORM,Set.of(Accion.CONSULTAR_TRABAJO,Accion.TOMAR_TRABAJO,Accion.ENTREGAR_EVIDENCIA),Set.of(Campo.METADATOS_TRABAJO,Campo.CONTEXTO_MINIMO,Campo.EVIDENCIA_PUBLICA));
  revisor=conceder(Atribucion.REVISION_MERCADO_PLATFORM,Set.of(Accion.CONFIGURAR_MERCADO,Accion.CONSULTAR_TRABAJO,Accion.REVISAR_EVIDENCIA,Accion.PUBLICAR_REFERENCIA),Set.of(Campo.CONFIGURACION,Campo.METADATOS_TRABAJO,Campo.EVIDENCIA_PUBLICA));
  configurarRegla(regla,FinanciacionManual.SOLICITANTE);
  long distrito=jdbc.queryForObject("select id_distrito from distrito where lower(nombre) like '%surco%' order by id_distrito limit 1",Long.class);
  politica=configuracion(regla,distrito);servicio.configurar(politica,decision(revisor,"CONFIGURAR_MERCADO_I0",politica),revisor);
  UUID ciclo=UUID.randomUUID();
  tx.execute(t->{jdbc.update("INSERT INTO b0_ciclo(organizacion_id,id_ciclo,plan,desde,hasta,dia_aniversario,importe_financiado_usd,orden_verificada,evidencia_pago,registrado_en) VALUES (?,?,'TEAM',?,?,1,75,?,'TEST NO PAGO REAL',?)",agente.idOrganizacion(),ciclo,utc(ahora.minusSeconds(60)),utc(ahora.plusSeconds(3600)),UUID.randomUUID(),utc(ahora));
   jdbc.update("INSERT INTO b0_fuente_capacidad(organizacion_id,id_fuente,id_ciclo,origen,id_persona_destinataria,plaza,origen_verificado,inicial,desde,vence,registrado_en) VALUES (?,?,?,'MENSUAL_AGENTE',?,1,?,10,?,?,?)",agente.idOrganizacion(),fuente,ciclo,agente.idPersona(),"TEST-"+fuente,utc(ahora.minusSeconds(60)),utc(ahora.plusSeconds(3600)),utc(ahora));
   jdbc.update("INSERT INTO b0_plaza(organizacion_id,id_ciclo,tipo,numero,id_persona,id_fuente) VALUES (?,?,'AGENTE',1,?,?)",agente.idOrganizacion(),ciclo,agente.idPersona(),fuente);return null;});
 }
 @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}
 void configurarRegla(UUID id,FinanciacionManual tipo){var r=new ReglaManual(id,"TEST_V1",tipo==FinanciacionManual.SOLICITANTE?new BigDecimal("0.5"):null,new BigDecimal("12"),ahora.minusSeconds(1),ahora.plusSeconds(3600),"TEST NO VALORES PRODUCTIVOS",true,tipo);presupuesto.configurarReglaManual(r,decision(financiero,"CONFIGURAR_REGLA_MANUAL_I0",r),financiero);}
 Configuracion configuracion(UUID r,long distrito){return new Configuracion(UUID.randomUUID(),"TEST_I0_V1",new Segmento(OperacionInmobiliaria.VENTA,"DEPARTAMENTO",distrito,"PEN",null,CalculoPrecioMercado.Naturaleza.OFERTA,"AREA_CONSTRUIDA","SIN_COCHERA_NI_DEPOSITO"),
  List.of(new FuentePrevista(Fuente.URBANIA,"TEST_F1","Sin acceso real, fixture","Sólo referencias TEST","Permiso fuente simulado TEST")),Set.of(Etapa.BUSQUEDA,Etapa.REVISION,Etapa.ENTREGA),"Entrega o límite TEST",Duration.ofHours(1),2,Duration.ofHours(1),Duration.ofMinutes(30),r,"TEST_V1",ahora.minusSeconds(1),ahora.plusSeconds(3600),true,false,
  new CalculoPrecioMercado.Normalizacion(new BigDecimal("50"),new BigDecimal("150"),"Evidencia TEST NO CALIBRADA","TEST_N1"),"TEST SIN HABILITACION REAL");}
 SesionPlataforma conceder(Atribucion tipo,Set<Accion> acciones,Set<Campo> campos){
  var s=cuenta();long id=cuentaId(s);var d=new Datos(UUID.randomUUID(),id,tipo,null,null,null,null,AtribucionesPlataforma.FINALIDAD_MERCADO,acciones,campos,ahora.minusSeconds(1),ahora.plusSeconds(3600),"TEST NO PERMISO REAL",null,RecursoMercado.MERCADO_MANUAL_I0);
  atribuciones.conceder(d,decision(s,"CONCEDER_ATRIBUCION",d),s);return new SesionPlataforma(s.referenciaSesion(),1,d.id());
 }
 long cuentaId(SesionPlataforma s){return contextos.exigirIdentidad(s.referenciaSesion(),1).identidad().idCuenta();}
 UUID decision(SesionPlataforma s,String accion,Object datos){long c=cuentaId(s);UUID id=UUID.randomUUID();acreditador.decisiones.put(id,new AcreditacionesTitularPlataforma.Decision(id,c,c,accion,Idempotencia.huellaExacta(json.objetoCanonico(datos)),"TEST SINTETICO",Instant.now()));return id;}
 SesionPlataforma cuenta(){return tx.execute(t->{String x=UUID.randomUUID().toString(),ref=Base64.getUrlEncoder().withoutPadding().encodeToString((x+x).substring(0,32).getBytes(java.nio.charset.StandardCharsets.US_ASCII));long id=jdbc.queryForObject("INSERT INTO cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) VALUES(?,?,clock_timestamp(),'TEST NO LOGIN') RETURNING id_cuenta_acceso",Long.class,"i0-"+x,"i0-"+x+"@example.invalid");var n=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);jdbc.update("INSERT INTO sesion_acceso(referencia_hash,semilla_contextos,id_cuenta_acceso,revision,creada_en,emitida_en,vence_en) VALUES(?,?,?,1,?,?,?)",Idempotencia.huellaExacta(ref),"c".repeat(64),id,utc(n.minusSeconds(2)),utc(n.minusSeconds(1)),utc(n.plusSeconds(1800)));return new SesionPlataforma(ref,1,null);});}
 Preparar datos(){return new Preparar(UUID.randomUUID(),politica.id(),politica.version(),null,"Carencia TEST de referencias vigentes",2,null);}
 Aceptacion aceptar(){var p=servicio.preparar(datos(),agente,sid);return new Aceptacion(p.datos(),p.maximo(),p.huella());}
 long solicitar(Aceptacion a){servicio.solicitar(a,agente,sid);return repo.trabajo(agente.idOrganizacion(),a.datos().solicitud(),false).orElseThrow().id();}
 Comando cmd(long id){return new Comando(UUID.randomUUID(),repo.trabajoPlataforma(id,false).orElseThrow().version());}
 Ronda ronda(long id,Cierre cierre,boolean comparable){
  var n=Instant.now();var plan=json.comoObjeto(repo.trabajoPlataforma(id,false).orElseThrow().plan(),Plan.class);
  var etapas=plan.etapas().stream().collect(java.util.stream.Collectors.toMap(e->e,e->"Evidencia etapa TEST"));List<Referencia> refs=List.of();
  if(comparable){var seg=politica.segmento();String hash=Idempotencia.huellaExacta(json.objetoCanonico(seg));var ctx=new CalculoPrecioMercado.Contexto(seg.operacion(),seg.tipo(),hash,seg.moneda(),seg.periodo(),seg.naturaleza(),seg.conceptoArea());
   var ausente=new CalculoPrecioMercado.Componente(null,true,"Ausencia confirmada TEST");var c=new CalculoPrecioMercado.Comparable("INMUEBLE_TEST", "REF_TEST",ctx,new CalculoPrecioMercado.Dinero(new BigDecimal("200000"),"PEN",null),new CalculoPrecioMercado.Area(new BigDecimal("100"),"AREA_CONSTRUIDA","TEST"),ausente,ausente,seg.composicion(),List.of(),null,null);
   refs=List.of(new Referencia("REF_TEST","INMUEBLE_TEST","Verificación identidad TEST",Fuente.URBANIA,"ORIGEN_TEST","TEST_F1",new FechaFuente(n.atZone(ZoneId.of("America/Lima")).toLocalDate(),null),n,true,c,null,null,ConceptoImporte.PRECIO_TOTAL,"Importe total TEST"));}
  return new Ronda(UUID.randomUUID(),1,cierre,comparable?Resultado.PARCIAL:Resultado.SIN_EVIDENCIA,n,n,"Ejecución IA SIMULADA, no API real",null,List.of(new Consulta(UUID.randomUUID(),Fuente.URBANIA,"TEST_F1","Consulta sintética","Evidencia TEST",n,true,false)),refs,"Informe TEST","Limitaciones TEST, no calidad empírica",null,etapas);
 }
 void entregarRevisar(long id,Cierre cierre,boolean comparable){servicio.tomar(id,cmd(id),investigador);servicio.entregar(id,new Entrega(cmd(id),List.of(ronda(id,cierre,comparable))),investigador);servicio.revisar(id,new RevisionEntrega(cmd(id),DecisionRevision.APROBAR,"Revisión TEST"),revisor);}
 void monto(String valor,String columna){assertEquals(0,new BigDecimal(valor).compareTo(jdbc.queryForObject("select "+columna+" from b0_fuente_capacidad where id_fuente=?",BigDecimal.class,fuente)));}
 @Test void sinHallazgosPuedeAcreditarUnaRondaSinPublicarNiInventarCosto(){var a=aceptar();long id=solicitar(a);entregarRevisar(id,Cierre.COMPLETA,false);assertEquals("REVISADA",repo.trabajoPlataforma(id,false).orElseThrow().estado());var c=cmd(id);servicio.liquidar(id,c,financiero);servicio.liquidar(id,c,financiero);monto("0.5","liquidado");monto("0","retenido");assertEquals(0,jdbc.queryForObject("select count(*) from i0_referencia_aprobada where trabajo=?",Integer.class,id));assertEquals(1,jdbc.queryForObject("select count(*) from b0_operacion_ia where id_operacion=? and costo_tecnico_usd is null and maximo_tecnico_usd is null",Integer.class,a.datos().solicitud()));}
 @Test void incertidumbreNoLiberaNiCobraHastaAclararMismaOperacion(){long id=solicitar(aceptar());entregarRevisar(id,Cierre.INCIERTA,false);assertThrows(ConflictoException.class,()->servicio.liquidar(id,cmd(id),financiero));monto("1","retenido");monto("0","liquidado");}
 @Test void retiroNoIniciadoLiberaMismasFuentesSinComenzar(){var a=aceptar();long id=solicitar(a);var d=new Retiro(cmd(id),"No continuar TEST");servicio.retirarNoIniciada(a.datos().solicitud(),d,agente,sid);servicio.retirarNoIniciada(a.datos().solicitud(),d,agente,sid);monto("0","retenido");monto("0","liquidado");assertEquals("CANCELADA",jdbc.queryForObject("select estado from b0_operacion_ia where id_operacion=?",String.class,a.datos().solicitud()));}
 @Test void otroTenantNoObtieneTrabajoYRolDeInvestigacionNoLiquida(){var a=aceptar();long id=solicitar(a);var o=new OrigenesDePrueba(jdbc,gestor,captura,contextos);Actor otro=o.empresa().agente();assertThrows(NoEncontradoException.class,()->servicio.consultar(a.datos().solicitud(),otro,o.sesion(otro)));assertThrows(AccesoNoAutorizadoException.class,()->servicio.liquidar(id,cmd(id),investigador));monto("1","retenido");}
 @Test void publicarYConsultarNoCobraYNoExponeExpedientePrivado(){long id=solicitar(aceptar());entregarRevisar(id,Cierre.COMPLETA,true);var p=new Publicacion(cmd(id),UUID.randomUUID(),List.of("REF_TEST"),Instant.now().minusSeconds(1),Instant.now().plusSeconds(600),List.of("Limitaciones publicas TEST"));servicio.publicar(id,p,revisor);servicio.publicar(id,p,revisor);var r=servicio.referencia(politica.id(),politica.version(),agente,sid).orElseThrow();assertEquals("REFERENCIA_UNICA",r.estado());assertEquals(0,new BigDecimal("2000").compareTo(r.promedioPorM2()));String proyeccion=json.objetoCanonico(r);assertFalse(proyeccion.contains("Carencia TEST"));assertFalse(proyeccion.contains("maximoAceptado"));assertFalse(proyeccion.contains("creditosPorRonda"));monto("0","liquidado");monto("1","retenido");}
 @Test void solicitudDuplicadaConcurrenteConservaUnaReservaYUnTrabajo() throws Exception {var a=aceptar();var salida=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);try{var x=pool.submit(()->{salida.await();return servicio.solicitar(a,agente,sid);});var y=pool.submit(()->{salida.await();return servicio.solicitar(a,agente,sid);});salida.countDown();assertEquals(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS));assertEquals(1,jdbc.queryForObject("select count(*) from i0_trabajo_mercado where organizacion_id=? and id_solicitud=?",Integer.class,agente.idOrganizacion(),a.datos().solicitud()));monto("1","retenido");}finally{pool.shutdownNow();}}
 @Test void rollbackNoDejaTrabajoReservaNiHistoria(){var a=aceptar();assertThrows(IllegalStateException.class,()->tx.execute(t->{solicitar(a);throw new IllegalStateException("TEST ROLLBACK");}));assertTrue(repo.trabajo(agente.idOrganizacion(),a.datos().solicitud(),false).isEmpty());monto("0","retenido");}
 @Test void revocarOperadorImpideLecturaYPermiteRecuperacionPorOtroAutorizado(){long id=solicitar(aceptar());servicio.tomar(id,cmd(id),investigador);var motivo="Revocacion TEST";atribuciones.revocar(investigador.concesion(),decision(investigador,"REVOCAR_ATRIBUCION",Map.of("concesion",investigador.concesion(),"motivo",motivo)),motivo,investigador);assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultarOperador(id,investigador));var otro=conceder(Atribucion.INVESTIGACION_PLATFORM,Set.of(Accion.TOMAR_TRABAJO,Accion.CONSULTAR_TRABAJO),Set.of(Campo.METADATOS_TRABAJO,Campo.CONTEXTO_MINIMO,Campo.EVIDENCIA_PUBLICA));var plazo=servicio.consultarRevision(id,revisor).cabecera().plazoComunicado();servicio.tomar(id,cmd(id),otro);assertEquals(plazo,servicio.consultarOperador(id,otro).cabecera().plazoComunicado());monto("1","retenido");}
 @Test void mantenimientoUsaCuentaRealYCotaCorporativaSinCargarUnaBolsa(){UUID r=UUID.randomUUID();configurarRegla(r,FinanciacionManual.MANTENIMIENTO_BROX);var c=configuracion(r,politica.segmento().distrito());servicio.configurar(c,decision(revisor,"CONFIGURAR_MERCADO_I0",c),revisor);var d=new Preparar(UUID.randomUUID(),c.id(),c.version(),null,"Mantenimiento TEST",1,null);long id=servicio.mantenimiento(d,revisor).trabajo();assertEquals(id,servicio.mantenimiento(d,revisor).trabajo());assertNull(repo.trabajoPlataforma(id,false).orElseThrow().organizacion());assertEquals(0,jdbc.queryForObject("select count(*) from b0_operacion_ia where id_operacion=?",Integer.class,d.solicitud()));monto("0","retenido");}

 @Test void dosOperadoresConcurrentesNoSeApropianDelMismoTrabajo() throws Exception {
  long id=solicitar(aceptar());var otro=conceder(Atribucion.INVESTIGACION_PLATFORM,Set.of(Accion.TOMAR_TRABAJO),Set.of(Campo.METADATOS_TRABAJO,Campo.CONTEXTO_MINIMO));var salir=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);var comando=cmd(id);
  try{List<Future<Boolean>> fs=new ArrayList<>();for(var actor:List.of(investigador,otro))fs.add(pool.submit(()->{salir.await();try{servicio.tomar(id,comando,actor);return true;}catch(ConflictoException e){return false;}}));salir.countDown();int ganan=0;for(var f:fs)if(f.get(30,TimeUnit.SECONDS))ganan++;assertEquals(1,ganan);assertEquals(2,jdbc.queryForObject("select count(*) from i0_evento_mercado where trabajo=?",Integer.class,id));monto("1","retenido");}finally{pool.shutdownNow();}
 }
 @Test void observarUnaPublicacionLaRetiraYCorregirNoGeneraOtraLiquidacion(){
  long id=solicitar(aceptar());entregarRevisar(id,Cierre.COMPLETA,true);servicio.liquidar(id,cmd(id),financiero);
  UUID publicada=UUID.randomUUID();servicio.publicar(id,new Publicacion(cmd(id),publicada,List.of("REF_TEST"),Instant.now().minusSeconds(1),Instant.now().plusSeconds(600),List.of("Limite publico TEST")),revisor);
  var original=repo.trabajoPlataforma(id,false).orElseThrow();var ronda=servicio.consultarRevision(id,revisor).evidencia().getFirst();
  servicio.revisar(id,new RevisionEntrega(cmd(id),DecisionRevision.OBSERVAR,"Corregir evidencia TEST"),revisor);
  assertTrue(servicio.referencia(politica.id(),politica.version(),agente,sid).map(r->!r.version().equals(publicada)).orElse(true));assertEquals(1,jdbc.queryForObject("select count(*) from i0_referencia_aprobada where id=? and retirada_en is not null",Integer.class,publicada));servicio.tomar(id,cmd(id),investigador);
  var corregida=new Ronda(ronda.identidad(),ronda.numero(),ronda.cierre(),ronda.resultado(),ronda.iniciadaEn(),ronda.terminadaEn(),ronda.ejecucionAsistidaIa(),ronda.tiempos(),ronda.consultas(),ronda.referencias(),"Correccion TEST",ronda.limitaciones(),null,ronda.etapasAcreditadas());
  servicio.entregar(id,new Entrega(cmd(id),List.of(corregida)),investigador);servicio.revisar(id,new RevisionEntrega(cmd(id),DecisionRevision.APROBAR,"Correccion comprobada TEST"),revisor);
  assertThrows(ConflictoException.class,()->servicio.liquidar(id,cmd(id),financiero));var actual=repo.trabajoPlataforma(id,false).orElseThrow();assertEquals(original.revisionLiquidada(),actual.revisionLiquidada());monto("0.5","liquidado");monto("0","retenido");
 }
 @Test void revisorNoRecibeElPlanPrivadoNiElMaximoDelSolicitante(){long id=solicitar(aceptar());String proyeccion=json.objetoCanonico(servicio.consultarRevision(id,revisor));assertFalse(proyeccion.contains("Carencia TEST"));assertFalse(proyeccion.contains("maximoAceptado"));assertFalse(proyeccion.contains("idPersona"));}

 @Test void esquemaJDBCClasificaPropietariosYConservaLasClavesDeAmbito(){
  assertEquals(Set.of("i0_politica_mercado","i0_trabajo_mercado","i0_evento_mercado","i0_referencia_aprobada"),new HashSet<>(jdbc.queryForList("select tablename from pg_tables where schemaname='public' and tablename like 'i0_%'",String.class)));
  assertEquals(25,jdbc.queryForObject("select count(*) from pg_constraint x join pg_class c on c.oid=x.conrelid join pg_namespace n on n.oid=c.relnamespace where x.contype='f' and n.nspname='public' and c.relname like 'i0_%'",Integer.class));
  var claves=jdbc.queryForList("select pg_get_constraintdef(x.oid) from pg_constraint x join pg_class c on c.oid=x.conrelid where x.contype='f' and c.relname='i0_trabajo_mercado'",String.class);
  for(String destino:List.of("persona(organizacion_id, id_persona)","persona_rol(organizacion_id, id_persona_rol)","propiedad(organizacion_id, id_propiedad)","b0_operacion_ia(organizacion_id, id_operacion)"))assertTrue(claves.stream().anyMatch(v->v.contains("FOREIGN KEY (organizacion_id,")&&v.contains("REFERENCES "+destino)),destino);
 }
 @Test void sinEventoTransaccionalNoSePuedeAvanzarLaVersion(){
  long id=solicitar(aceptar());long version=repo.trabajoPlataforma(id,false).orElseThrow().version();
  var e=assertThrows(RuntimeException.class,()->tx.execute(t->{jdbc.update("update i0_trabajo_mercado set version=version+1 where id=?",id);return null;}));
  String mensajes="";for(Throwable c=e;c!=null;c=c.getCause())mensajes+=c.getMessage();assertTrue(mensajes.contains("historia/salida"));assertEquals(version,repo.trabajoPlataforma(id,false).orElseThrow().version());monto("1","retenido");
 }
 @Test void liquidacionConcurrenteDelMismoComandoNoDuplicaCargoNiHistoria() throws Exception {
  long id=solicitar(aceptar());entregarRevisar(id,Cierre.COMPLETA,false);var c=cmd(id);var salida=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{var x=pool.submit(()->{salida.await();return servicio.liquidar(id,c,financiero);});var y=pool.submit(()->{salida.await();return servicio.liquidar(id,c,financiero);});salida.countDown();assertEquals(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS));monto("0.5","liquidado");monto("0","retenido");assertEquals(1,jdbc.queryForObject("select count(*) from i0_evento_mercado where trabajo=? and tipo='LIQUIDADA'",Integer.class,id));}finally{pool.shutdownNow();}
 }

 @Test void consultaFinancieraYColasNoConcedenDatosAlOperador(){
  long id=solicitar(aceptar());var antes=servicio.consultarFinanciero(id,financiero);assertNull(antes.rondasAcreditadas());assertNull(antes.liquidadaEn());assertEquals(0,new BigDecimal("0.5").compareTo(antes.tarifaAceptada()));
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultarFinanciero(id,investigador));
  assertTrue(servicio.cola(id-1,100,investigador).stream().anyMatch(h->h.trabajo()==id));assertTrue(servicio.colaRevision(id-1,100,revisor).stream().noneMatch(h->h.trabajo()==id));
  entregarRevisar(id,Cierre.COMPLETA,false);assertTrue(servicio.colaFinanciera(id-1,100,financiero).stream().anyMatch(h->h.trabajo()==id));
  var opciones=atribuciones.atribucionesMercado(investigador.referenciaSesion(),1);assertEquals(1,opciones.size());assertEquals(investigador.concesion(),opciones.getFirst().referencia());
 }
 @Test void resultadoPrivadoNoPublicaNiDevuelveConsultasOConsumo(){
  var a=aceptar();long id=solicitar(a);entregarRevisar(id,Cierre.COMPLETA,true);var resultado=servicio.resultado(a.datos().solicitud(),agente,sid);
  assertEquals("REFERENCIA_PRELIMINAR",resultado.resultado());assertEquals(0,new BigDecimal("2000").compareTo(resultado.referencia().promedioPorM2()));assertNull(resultado.trabajo().referenciaPublicada());
  var texto=json.objetoCanonico(resultado);for(var secreto:List.of("ejecucionAsistidaIa","consultas","creditosPorRonda","tarifaAceptada","cuentaRevisora","Evidencia etapa"))assertFalse(texto.contains(secreto),secreto);
 }
 @Test void retirarPoliticaRevalidaFuenteSinImpedirConciliacionHistorica(){
  long id=solicitar(aceptar());entregarRevisar(id,Cierre.COMPLETA,true);String motivo="Retiro de permiso fuente TEST";
  UUID decision=decision(revisor,"RETIRAR_POLITICA_MERCADO_I0",Map.of("politica",politica.id(),"version",politica.version(),"motivo",motivo));var retiro=new RetiroPolitica(decision,motivo);
  servicio.retirarPolitica(politica.id(),politica.version(),retiro,revisor);servicio.retirarPolitica(politica.id(),politica.version(),retiro,revisor);
  assertThrows(CapacidadDeshabilitadaException.class,()->servicio.referencia(politica.id(),politica.version(),agente,sid));assertThrows(CapacidadDeshabilitadaException.class,()->servicio.resultado(repo.trabajoPlataforma(id,false).orElseThrow().solicitud(),agente,sid));
  servicio.liquidar(id,cmd(id),financiero);monto("0.5","liquidado");monto("0","retenido");
 }
 @Test void puertaHttpUsaIdentidadYConcesionActualSinActorFicticio() throws Exception {
  long id=solicitar(aceptar());var acceso=tx.execute(t->contextos.exigirIdentidad(revisor.referenciaSesion(),1));String token=tokens.firmar(tokens.de(acceso));
  var r=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/plataforma/mercado/trabajos/"+id+"/revision").header("Authorization","Bearer "+token).header("X-Brox-Concesion",revisor.concesion())).andReturn().getResponse();
  assertEquals(200,r.getStatus(),r.getContentAsString());assertEquals("no-store",r.getHeader("Cache-Control"));assertFalse(r.getContentAsString().contains("Carencia TEST"));assertFalse(r.getContentAsString().contains("maximoAceptado"));
  var ajena=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/plataforma/mercado/trabajos/"+id+"/finanzas").header("Authorization","Bearer "+token).header("X-Brox-Concesion",financiero.concesion())).andReturn().getResponse();assertEquals(403,ajena.getStatus());
  var sinCabecera=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/plataforma/mercado/trabajos/"+id+"/revision").header("Authorization","Bearer "+token)).andReturn().getResponse();assertEquals(400,sinCabecera.getStatus());
 }

 @Test void puertaHttpPrivadaUsaContextoSeleccionadoYNuncaDevuelveUnResultadoAjeno() throws Exception {
  var a=aceptar();long id=solicitar(a);var acceso=tx.execute(t->contextos.exigirContexto(sid.referenciaSesion(),sid.revisionContexto(),agente));String token=tokens.firmar(tokens.de(acceso));
  var r=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/mercado/investigaciones/"+a.datos().solicitud()+"/resultado").header("Authorization","Bearer "+token)).andReturn().getResponse();
  assertEquals(200,r.getStatus(),r.getContentAsString());assertTrue(r.getContentAsString().contains("PENDIENTE_DE_RESULTADO_REVISADO"));assertFalse(r.getContentAsString().contains("tarifaAceptada"));
  var o=new OrigenesDePrueba(jdbc,gestor,captura,contextos);var otro=o.empresa().agente();var sidOtro=o.sesion(otro);var accesoOtro=tx.execute(t->contextos.exigirContexto(sidOtro.referenciaSesion(),sidOtro.revisionContexto(),otro));String tokenOtro=tokens.firmar(tokens.de(accesoOtro));
  for(UUID consulta:List.of(a.datos().solicitud(),UUID.randomUUID())){var ajena=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/mercado/investigaciones/"+consulta+"/resultado").header("Authorization","Bearer "+tokenOtro)).andReturn().getResponse();assertEquals(404,ajena.getStatus());}
 }

 @Test void referenciasVencidasSoloSeOfrecenComoHistoricasConPermisoActual(){
  var sinHistoria=politica;var datos=json.comoMapa(json.objetoCanonico(politica));datos.put("id",UUID.randomUUID().toString());datos.put("permiteReferenciaHistorica",true);politica=json.comoObjeto(json.objetoCanonico(datos),Configuracion.class);
  servicio.configurar(politica,decision(revisor,"CONFIGURAR_MERCADO_I0",politica),revisor);var a=aceptar();long id=solicitar(a);entregarRevisar(id,Cierre.COMPLETA,true);UUID version=UUID.randomUUID();
  servicio.publicar(id,new Publicacion(cmd(id),version,List.of("REF_TEST"),Instant.now().minusSeconds(1),Instant.now().plusSeconds(600),List.of("Antecedente TEST permitido")),revisor);
  var original=org.springframework.test.util.ReflectionTestUtils.getField(servicio,"repo");var relojConsulta=org.mockito.Mockito.spy(org.springframework.test.util.AopTestUtils.<InvestigacionesMercadoRepository>getUltimateTargetObject(repo));Instant corte=Instant.now().plusSeconds(2100);
  // Solo reloj de consulta: JDBC, permisos, sesion, revision y fuente siguen reales. No se simula IA o autenticacion aceptada.
  org.mockito.Mockito.doReturn(corte).when(relojConsulta).ahora();org.springframework.test.util.ReflectionTestUtils.setField(servicio,"repo",relojConsulta);
  try{
   var r=servicio.referencia(politica.id(),politica.version(),agente,sid).orElseThrow();assertEquals(version,r.version());assertEquals("REFERENCIA_CADUCADA",r.estado());assertTrue(r.vigenteHasta().isBefore(corte));
   assertTrue(servicio.referencia(sinHistoria.id(),sinHistoria.version(),agente,sid).isEmpty());
   var privado=servicio.resultado(a.datos().solicitud(),agente,sid);assertEquals("REFERENCIA_CADUCADA",privado.resultado());assertNotNull(privado.referencia().vigenteHasta());
  }finally{org.springframework.test.util.ReflectionTestUtils.setField(servicio,"repo",original);}
 }

 void retirarFuente(){String motivo="Permiso fuente retirado TEST";servicio.retirarPolitica(politica.id(),politica.version(),new RetiroPolitica(decision(revisor,"RETIRAR_POLITICA_MERCADO_I0",Map.of("politica",politica.id(),"version",politica.version(),"motivo",motivo)),motivo),revisor);}
 @Test void finanzasSoloLiberaReservaNoIniciadaCuandoPerdioAutorizacion(){
  var a=aceptar();long id=solicitar(a);var d=new Retiro(cmd(id),"Autoridad retirada TEST");assertThrows(ConflictoException.class,()->servicio.liberarNoIniciada(id,d,financiero));retirarFuente();
  servicio.liberarNoIniciada(id,d,financiero);servicio.liberarNoIniciada(id,d,financiero);monto("0","retenido");monto("0","liquidado");assertEquals("CANCELADA",jdbc.queryForObject("select estado from b0_operacion_ia where id_operacion=?",String.class,a.datos().solicitud()));
  assertEquals(1,jdbc.queryForObject("select count(*) from i0_evento_mercado where trabajo=? and tipo='RESERVA_NO_INICIADA_LIBERADA'",Integer.class,id));
 }
 @Test void hechosAnterioresAlRetiroSeConservanYConcilianSinNuevaDivulgacion(){
  long id=solicitar(aceptar());servicio.tomar(id,cmd(id),investigador);var r=ronda(id,Cierre.COMPLETA,true);retirarFuente();assertFalse(servicio.consultarOperador(id,investigador).puedeInvestigar());
  servicio.entregar(id,new Entrega(cmd(id),List.of(r)),investigador);servicio.revisar(id,new RevisionEntrega(cmd(id),DecisionRevision.APROBAR,"Hechos obtenidos antes del retiro TEST"),revisor);servicio.liquidar(id,cmd(id),financiero);monto("0.5","liquidado");monto("0","retenido");
  assertThrows(CapacidadDeshabilitadaException.class,()->servicio.publicar(id,new Publicacion(cmd(id),UUID.randomUUID(),List.of("REF_TEST"),Instant.now().minusSeconds(1),Instant.now().plusSeconds(600),List.of("TEST")),revisor));
 }
 @Test void retirarFuenteNoAutorizaAcreditarTrabajoNuevoNiLiberarUnaEjecucionIncierta(){
  long id=solicitar(aceptar());servicio.tomar(id,cmd(id),investigador);retirarFuente();assertThrows(ReglaNegocioException.class,()->servicio.entregar(id,new Entrega(cmd(id),List.of(ronda(id,Cierre.COMPLETA,false))),investigador));
  assertThrows(ConflictoException.class,()->servicio.liberarNoIniciada(id,new Retiro(cmd(id),"No se presume ejecucion cero"),financiero));
  servicio.entregar(id,new Entrega(cmd(id),List.of(ronda(id,Cierre.INCIERTA,false))),investigador);servicio.revisar(id,new RevisionEntrega(cmd(id),DecisionRevision.APROBAR,"Se conserva incertidumbre TEST"),revisor);assertThrows(ConflictoException.class,()->servicio.liquidar(id,cmd(id),financiero));monto("1","retenido");monto("0","liquidado");
 }
 @Test void mantenimientoNoIniciadoLiberaCotaSinTarifaOCreditosFicticios(){
  UUID reglaCorporativa=UUID.randomUUID();configurarRegla(reglaCorporativa,FinanciacionManual.MANTENIMIENTO_BROX);politica=configuracion(reglaCorporativa,politica.segmento().distrito());servicio.configurar(politica,decision(revisor,"CONFIGURAR_MERCADO_I0",politica),revisor);
  long id=servicio.mantenimiento(new Preparar(UUID.randomUUID(),politica.id(),politica.version(),null,"Mantenimiento TEST",1,null),revisor).trabajo();retirarFuente();var d=new Retiro(cmd(id),"Capacidad no iniciada TEST");servicio.liberarNoIniciada(id,d,financiero);servicio.liberarNoIniciada(id,d,financiero);
  assertEquals(0,new BigDecimal("0").compareTo(jdbc.queryForObject("select reservadas from b0_regla_trabajo_manual where id=?",BigDecimal.class,reglaCorporativa)));assertEquals(0,new BigDecimal("0").compareTo(jdbc.queryForObject("select realizadas from b0_regla_trabajo_manual where id=?",BigDecimal.class,reglaCorporativa)));assertNull(servicio.consultarFinanciero(id,financiero).tarifaAceptada());monto("0","retenido");monto("0","liquidado");
 }
 static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
}
