package com.controllocal.integracion;
import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.actividad.*;
import com.controllocal.service.actividad.InformesC7.*;
import com.controllocal.service.actividad.FiltrosDeInforme.*;
import com.controllocal.service.planes.DerechosDeInformesC7.Composicion;
import com.controllocal.service.excepcion.*;
import com.controllocal.domain.planes.PlanBrox;
import com.controllocal.persistence.presupuesto.CiclosPagadosB0Repository;
import com.controllocal.persistence.repositorio.ActividadInformeC7Repository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.controllocal.domain.comercial.TipoHitoContractual.ESCRITURA;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"controllocal.programas.habilitado=false","spring.datasource.hikari.maximum-pool-size=6"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters=false)
class InformesFiltradosC7IntegrationTest {
 @DynamicPropertySource static void base(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
 @Autowired ApplicationContext app;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager gestor;@Autowired InformesC7 informes;
 @MockitoSpyBean ActividadInformeC7Repository repositorio;
 @MockitoSpyBean InteraccionService interacciones;
 OrigenesDePrueba.Equipo e;FormalizacionesDePrueba banco;TransactionTemplate tx;LocalDate hoy;
 @BeforeEach void inicio(TestInfo i){EvidenciaC4DePrueba.iniciar(i);tx=new TransactionTemplate(gestor);e=new OrigenesDePrueba(jdbc,gestor,app.getBean(com.controllocal.service.captura.MotorDeCaptura.class),app.getBean(ContextosService.class)).empresa();banco=new FormalizacionesDePrueba(app);hoy=LocalDate.now(ZoneId.of("America/Lima"));}
 @AfterEach void fin(){reset(repositorio);org.springframework.security.core.context.SecurityContextHolder.clearContext();EvidenciaC4DePrueba.limpiar();}
 void plan(Actor a,PlanBrox p){if(p==PlanBrox.FREE)return;var t=Instant.now();tx.executeWithoutResult(s->new CiclosPagadosB0Repository(jdbc).nuevoCiclo(a.idOrganizacion(),UUID.randomUUID(),p.name(),t.minusSeconds(60),t.plusSeconds(86400),hoy.getDayOfMonth(),p.precioUsd(),UUID.randomUUID(),"TEST SINTETICO NO PAGO REAL",0,t));}
 Consulta q(Composicion c,Filtros f){return new Consulta(c,hoy.withDayOfMonth(1),hoy,null,null,f,null);}
 long propiedad(Actor a,String op){var ops=op==null?List.<PropiedadUniversalService.OperacionSolicitada>of():List.of(new PropiedadUniversalService.OperacionSolicitada(op,new BigDecimal("100000"),"PEN",null,null,null,null,null,null,null,List.of()));return app.getBean(PropiedadUniversalService.class).registrar(new PropiedadUniversalService.ComandoRegistro(UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla(),null,"DEPARTAMENTO","VIVIENDA","Departamento TEST53",new PropiedadUniversalService.Ubicacion("Dirección ficticia TEST53 "+UUID.randomUUID(),"Surco",null,null,null,"TEST301",null,null,null),List.of(),List.of(),ops,null),a).idPropiedad();}
 @ParameterizedTest @EnumSource(PlanBrox.class) void cadaPlanEntregaSuComposicionRealSinComprarAutoridad(PlanBrox p){var a=p.empresarial()?e.broker():EspacioIndividualB0DePrueba.crear(jdbc,tx);plan(a,p);var c=switch(p){case FREE->Composicion.OPERACION_ACTUAL;case ONE->Composicion.RESUMEN_MENSUAL;case ONE_PRO->Composicion.EVOLUCION_MEJORA;case TEAM->Composicion.RESUMEN_OFICINA;case GROWTH->Composicion.SEGUIMIENTO_RESPONSABLES;case SCALE->Composicion.RITMO_METAS;case PRIME->Composicion.DIAGNOSTICO_RESPONSABLE;};var r=informes.preparar(q(c,null),a);assertEquals(p.name(),r.plan());assertTrue(r.bloques().containsKey("alcances"));assertTrue(r.bloques().containsKey("resultadosPeriodo"));assertEquals(p.empresarial()?"EQUIPO":"PROPIO",r.ambito());if(p==PlanBrox.TEAM)assertFalse(r.bloques().containsKey("responsables"));if(p==PlanBrox.PRIME)assertTrue(r.bloques().containsKey("diagnosticos"));}
 @Test void filtrosSeleccionanOperacionYEstadoConCoberturaYFechaSinInferir(){plan(e.broker(),PlanBrox.GROWTH);propiedad(e.agente(),"VENTA");propiedad(e.colega(),"ALQUILER");var f=new Filtros("VENTA",e.agente().idRolOperativo(),null,new Situacion("ENCARGO","P"),null);var r=informes.preparar(q(Composicion.SEGUIMIENTO_RESPONSABLES,f),e.broker());var actual=(OperacionActual)r.bloques().get("operacionActual");assertEquals(1,actual.situaciones().stream().filter(x->x.recurso().equals("ENCARGO")).mapToLong(GrupoSituacion::casos).sum());var ac=(Map<?,?>)r.bloques().get("alcances");var medida=(AlcanceBloque)ac.get("mediciones");assertNull(medida.operacion());assertNull(medida.situacion());assertTrue(medida.cobertura().contains("NO se aplican"));assertEquals(hoy,r.hasta());}
 @Test void responsableAjenoNoSeConvierteEnResultadoVacioNiPermiso(){plan(e.broker(),PlanBrox.PRIME);var otra=new OrigenesDePrueba(jdbc,gestor,app.getBean(com.controllocal.service.captura.MotorDeCaptura.class),app.getBean(ContextosService.class)).empresa();clearInvocations(repositorio);assertThrows(NoEncontradoException.class,()->informes.preparar(q(Composicion.DIAGNOSTICO_RESPONSABLE,new Filtros(null,otra.agente().idRolOperativo(),null,null,null)),e.broker()));verify(repositorio,never()).casos(anyLong(),anyCollection(),anyBoolean(),any());assertThrows(AccesoNoAutorizadoException.class,()->informes.preparar(q(Composicion.DIAGNOSTICO_RESPONSABLE,null),e.admin()));}
 @Test void perderElPlanDurantePreparacionImpideEntregar(){plan(e.broker(),PlanBrox.GROWTH);doAnswer(inv->{var r=inv.callRealMethod();var nueva=new TransactionTemplate(gestor);nueva.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);nueva.executeWithoutResult(t->jdbc.update("update b0_ciclo set revocado=true where organizacion_id=?",e.broker().idOrganizacion()));return r;}).when(repositorio).actos(eq(e.broker().idOrganizacion()),anyCollection(),eq(true),any());assertThrows(CapacidadDeshabilitadaException.class,()->informes.preparar(q(Composicion.SEGUIMIENTO_RESPONSABLES,null),e.broker()));}
 @Test void altaConcurrenteNoEntregaUnaMezclaDeCarteras()throws Exception{plan(e.broker(),PlanBrox.GROWTH);var leida=new CountDownLatch(1);var continuar=new CountDownLatch(1);var primera=new java.util.concurrent.atomic.AtomicBoolean(true);doAnswer(inv->{var r=inv.callRealMethod();if(primera.getAndSet(false)){leida.countDown();assertTrue(continuar.await(30,TimeUnit.SECONDS));}return r;}).when(repositorio).casos(eq(e.broker().idOrganizacion()),anyCollection(),eq(true),any());var pool=Executors.newSingleThreadExecutor();try{var f=pool.submit(()->informes.preparar(q(Composicion.SEGUIMIENTO_RESPONSABLES,null),e.broker()));assertTrue(leida.await(30,TimeUnit.SECONDS));propiedad(e.agente(),"VENTA");continuar.countDown();var primeraSalida=f.get(60,TimeUnit.SECONDS);assertTrue(((OperacionActual)primeraSalida.bloques().get("operacionActual")).situaciones().isEmpty());var segunda=informes.preparar(q(Composicion.SEGUIMIENTO_RESPONSABLES,null),e.broker());assertEquals(1,((OperacionActual)segunda.bloques().get("operacionActual")).situaciones().stream().filter(x->x.recurso().equals("ENCARGO")).mapToLong(GrupoSituacion::casos).sum());}finally{continuar.countDown();pool.shutdownNow();}}
 @Test void filtrarFavorablesNoConvierteUnoDeCuatroEnCienPorCiento(){plan(e.broker(),PlanBrox.PRIME);var c=banco.nuevoEnEquipo(e,"VENTA",ComisionC5IntegrationTest.fija(ESCRITURA,ESCRITURA,List.of()));banco.confirmar(banco.listo(c,ESCRITURA,hoy,banco.venta(c,new BigDecimal("100000"),"PEN",null),false));for(int i=0;i<3;i++){var d=banco.negociacion.mesaEnEquipo(e,"VENTA",new BigDecimal("100000"),"PEN",true);var o=banco.oportunidades.obtener(d.oportunidad(),d.agente());tx.execute(t->banco.oportunidades.noContinuidad(new OportunidadService.NoContinuidad(o.id(),o.version(),"S","Desistimiento TEST53",hoy),d.agente(),banco.sesiones.de(d.agente()),UUID.randomUUID().toString(),FormalizacionesDePrueba.procedencia()));}var r=informes.preparar(q(Composicion.DIAGNOSTICO_RESPONSABLE,new Filtros("VENTA",null,null,new Situacion("OPORTUNIDAD","F"),null)),e.broker());var m=(MedicionesC7.Lectura)r.bloques().get("mediciones");assertEquals(1,m.cierre().get("VENTA").exitos());assertEquals(4,m.cierre().get("VENTA").evaluables());assertEquals(0,new BigDecimal("25").compareTo(m.cierre().get("VENTA").porcentaje()));var eventos=(List<ResultadosPeriodo>)r.bloques().get("resultadosPeriodo");assertEquals(1,eventos.getFirst().cierresContractualesConfirmados());assertEquals(0,eventos.getFirst().noContinuidadRegistrada());var cs=(List<CohortesDeInforme.Cohorte>)r.bloques().get("cohortes");assertEquals(4,cs.stream().mapToInt(CohortesDeInforme.Cohorte::casos).sum());var fichas=(List<FichaResponsable>)r.bloques().get("diagnosticos");assertTrue(fichas.stream().anyMatch(x->x.responsable()==e.agente().idRolOperativo()&&x.cohortes().stream().mapToInt(CohortesDeInforme.Cohorte::casos).sum()==4));}
 @Test void comunicacionConDosVinculosCuentaUnaVezYNoExportaDetallePrivado(){plan(e.broker(),PlanBrox.GROWTH);var c=banco.negociacion.mesaEnEquipo(e,"VENTA",new BigDecimal("100000"),"PEN",true);var persona=new InteraccionService.ParticipanteDeclarado(null,"GESTOR_COMERCIAL","Gestora TEST53","900000053","GESTORA-TEST53");var momento=new InteraccionService.MomentoHecho(hoy,null,null);var d=new InteraccionService.DatosInteraccion(List.of(new InteraccionService.VinculoActividad(InteraccionService.TipoContexto.ENCARGO,c.encargo(),null,null),new InteraccionService.VinculoActividad(InteraccionService.TipoContexto.OPORTUNIDAD,c.oportunidad(),null,null)),persona,persona,CatalogoInteracciones.TipoActividad.LLAMADA,CatalogoInteracciones.CanalComunicacion.TELEFONO,null,List.of(new InteraccionService.EventoDeclarado("llamada",CatalogoInteracciones.EventoComunicacion.LLAMADA_SALIENTE,momento)),CatalogoInteracciones.ResultadoInteraccion.INTERCAMBIO_EFECTIVO,momento,"DETALLE PRIVADO TEST53",List.of(),new InteraccionService.ProximaActuacion(InteraccionService.OrigenActuacion.ACUERDO_EXTERNO,hoy.plusDays(3),"NO-COPIAR-PRIVADO-TEST53"),null,"Declaración TEST53",null);tx.execute(t->app.getBean(InteraccionService.class).registrar(d,c.agente(),c.sesion(),FormalizacionesDePrueba.procedencia()));clearInvocations(interacciones);var r=informes.preparar(q(Composicion.SEGUIMIENTO_RESPONSABLES,new Filtros("VENTA",null,CatalogoInteracciones.TipoActividad.LLAMADA,new Situacion("OPORTUNIDAD","A"),null)),e.broker());assertEquals(1,((Map<?,?>)r.bloques().get("periodo")).get("comunicacionesUnicas"));var filas=(List<SeguimientoResponsable>)r.bloques().get("responsables");assertTrue(filas.stream().filter(f->f.responsable()==e.agente().idRolOperativo()).allMatch(f->f.oportunidadesSinProximaActuacion().isEmpty()));var json=app.getBean(com.controllocal.service.soporte.Documentos.class).objetoCanonico(r);assertFalse(json.contains("NO-COPIAR"));assertFalse(json.contains("900000053"));assertFalse(json.contains("DETALLE PRIVADO"));verify(interacciones,times(2)).indicadores(e.broker());}
 @Test void metasEmparejanOperacionConObjetivoYGeneralPermaneceUnico(){plan(e.broker(),PlanBrox.SCALE);banco.negociacion.mesaEnEquipo(e,"VENTA",new BigDecimal("100000"),"PEN",true);var meta=new MetaComercialService.Asignacion(e.agente().idRolOperativo(),"OPORTUNIDAD_APERTURA","VENTA",0L,5,List.of(hoy),"Objetivo explícito sintético TEST53");var sid=banco.sesiones.de(e.broker());var act=banco.actuaciones.prepararEstablecimientoMetas(e.broker(),new MetaComercialService.Establecer(YearMonth.from(hoy).toString(),List.of(meta)),sid,FormalizacionesDePrueba.procedencia());banco.humanas.confirmar(act,e.broker(),sid,MetaComercialService.Resultado.class);var r=informes.preparar(q(Composicion.RITMO_METAS,new Filtros("VENTA",e.agente().idRolOperativo(),null,null,null)),e.broker());var m=(MetasPreparadas)r.bloques().get("ritmoYMetas");var k=m.indicadores().stream().filter(x->x.codigo().equals("OPORTUNIDAD_APERTURA")).findFirst().orElseThrow();assertEquals("VENTA",k.operacion());assertEquals(1L,k.avance().actual());assertEquals(5L,k.avance().metaPeriodo());assertFalse(m.indicadores().stream().anyMatch(x->x.operacion().equals("ALQUILER")));assertEquals(1,m.indicadores().stream().filter(x->x.operacion().equals("GENERAL")).count());}
 @Test void comparacionConservaSusFechasYNoImponeSituacionDeCartera(){plan(e.broker(),PlanBrox.PRIME);var q=new Consulta(Composicion.DIAGNOSTICO_RESPONSABLE,hoy.minusDays(6),hoy,hoy.minusDays(13),hoy.minusDays(7),new Filtros("VENTA",null,null,new Situacion("ENCARGO","P"),null),"ALQUILER");var r=informes.preparar(q,e.broker());var alcance=(Map<String,AlcanceBloque>)r.bloques().get("alcances");assertEquals(hoy.minusDays(13),alcance.get("comparacion").desde());assertNull(alcance.get("cohortes").situacion());assertEquals("ALQUILER",alcance.get("cohortes").operacion());}
 @Test void cambioConcurrenteDeUnaFuenteExistenteImpideEntregarVersionMezclada()throws Exception{
  plan(e.broker(),PlanBrox.GROWTH);long p=propiedad(e.agente(),"VENTA");
  long id=jdbc.queryForObject("select id_captacion from captacion where organizacion_id=? and id_propiedad=?",Long.class,e.agente().idOrganizacion(),p);
  var servicio=app.getBean(com.controllocal.service.encargo.EncargoService.class);var antes=servicio.consultar(id,e.agente());var sid=banco.sesiones.de(e.agente());
  var leida=new CountDownLatch(1);var continuar=new CountDownLatch(1);var primera=new java.util.concurrent.atomic.AtomicBoolean(true);
  doAnswer(inv->{var r=inv.callRealMethod();if(primera.getAndSet(false)){leida.countDown();assertTrue(continuar.await(30,TimeUnit.SECONDS));}return r;})
    .when(repositorio).casos(eq(e.broker().idOrganizacion()),anyCollection(),eq(true),any());
  var pool=Executors.newSingleThreadExecutor();try{
   var lectura=pool.submit(()->informes.preparar(q(Composicion.SEGUIMIENTO_RESPONSABLES,null),e.broker()));assertTrue(leida.await(30,TimeUnit.SECONDS));
   tx.execute(t->servicio.editar(id,new com.controllocal.service.encargo.EncargoService.Edicion(antes.version(),null,null,null,"Observación concurrente TEST53",null,"Precisar información sintética TEST53",null,null),e.agente(),sid,UUID.randomUUID().toString(),FormalizacionesDePrueba.procedencia()));
   continuar.countDown();var error=assertThrows(ExecutionException.class,()->lectura.get(60,TimeUnit.SECONDS));assertInstanceOf(ConflictoException.class,error.getCause());
  }finally{continuar.countDown();pool.shutdownNow();}
 }
 @Test void agenteNoRecibeCarteraDelColegaNiDeOtraOrganizacionAlFiltrar(){
  plan(e.broker(),PlanBrox.GROWTH);propiedad(e.agente(),"VENTA");propiedad(e.colega(),"VENTA");
  var otra=new OrigenesDePrueba(jdbc,gestor,app.getBean(com.controllocal.service.captura.MotorDeCaptura.class),app.getBean(ContextosService.class)).empresa();propiedad(otra.agente(),"VENTA");
  var r=informes.preparar(q(Composicion.SEGUIMIENTO_RESPONSABLES,new Filtros("VENTA",null,null,new Situacion("ENCARGO","P"),null)),e.agente());
  assertEquals(List.of(e.agente().idRolOperativo()),r.responsablesAutorizados());
  assertEquals(1,((OperacionActual)r.bloques().get("operacionActual")).situaciones().stream().filter(x->x.recurso().equals("ENCARGO")).mapToLong(GrupoSituacion::casos).sum());
  assertTrue(r.fuentes().stream().filter(x->x.tipo().equals("ENCARGO")).allMatch(x->jdbc.queryForObject("select id_rol_agente from captacion where id_captacion=? and organizacion_id=?",Long.class,x.id(),e.agente().idOrganizacion())==e.agente().idRolOperativo()));
  var broker=informes.preparar(q(Composicion.SEGUIMIENTO_RESPONSABLES,null),e.broker());
  assertEquals(2,((OperacionActual)broker.bloques().get("operacionActual")).situaciones().stream().filter(x->x.recurso().equals("ENCARGO")).mapToLong(GrupoSituacion::casos).sum());
  assertThrows(NoEncontradoException.class,()->informes.preparar(q(Composicion.SEGUIMIENTO_RESPONSABLES,new Filtros(null,e.colega().idRolOperativo(),null,null,null)),e.agente()));
 }
 @Test void economiaConservaCobrosRealesMonedasYHechoAunqueLaCarteraFiltreFavorables(){
  plan(e.broker(),PlanBrox.GROWTH);
  for(String moneda:List.of("PEN","USD")){
   var base=ComisionC5IntegrationTest.fija(ESCRITURA,ESCRITURA,List.of());
   var condiciones=new com.controllocal.service.encargo.EncargoService.Condiciones(base.precioPedido(),moneda,base.periodoPrecio(),base.tipoComision(),base.baseCalculo(),base.valorComision(),moneda,base.tratamientoIgv(),base.motivoSinComision(),base.hitoDevengo(),base.parteObligada(),base.beneficiario(),base.exigibilidad(),base.procedencia(),base.tramos(),base.composicionMensual(),base.ejecucion());
   var c=banco.nuevoEnEquipo(e,"VENTA",condiciones);var r=banco.listo(c,ESCRITURA,hoy,banco.venta(c,new BigDecimal("100000"),moneda,null),false);banco.confirmar(r);
   banco.cobrar(c,banco.prepararCobro(c,r.expediente(),new BigDecimal("100"),hoy,"RECIBO TEST53 "+moneda));
  }
  var filtro=new Filtros("VENTA",null,null,new Situacion("OPORTUNIDAD","A"),HechoEconomico.COBRO_EN_PERIODO);
  var r=informes.preparar(q(Composicion.SEGUIMIENTO_RESPONSABLES,filtro),e.broker());var dinero=(Map<?,?>)r.bloques().get("economia");
  assertFalse(dinero.containsKey("calculadoActual"));assertFalse(dinero.containsKey("devengadoActual"));assertFalse(dinero.containsKey("exigibilidadActual"));
  var cobros=(List<ComisionService.ImportePorOperacion>)dinero.get("cobradoEnPeriodo");assertEquals(2,cobros.size());
  assertEquals(Set.of("PEN","USD"),cobros.stream().map(ComisionService.ImportePorOperacion::moneda).collect(java.util.stream.Collectors.toSet()));
  assertTrue(cobros.stream().allMatch(x->x.operacion().equals("VENTA")&&x.importe().compareTo(new BigDecimal("100"))==0));
  var alc=(Map<String,AlcanceBloque>)r.bloques().get("alcances");assertNull(alc.get("economia").situacion());assertEquals("VENTA",alc.get("economia").operacion());
 }
}
