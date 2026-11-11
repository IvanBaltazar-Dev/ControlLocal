package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.MetaComercialService.*;
import com.controllocal.service.actividad.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.captura.MotorDeCaptura;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.controllocal.service.soporte.IndicadorMetaComercial.*;
import static com.controllocal.domain.comercial.TipoHitoContractual.ESCRITURA;

/** C7-METAS-01: transacciones/autoridad/actuaciones/fuentes reales, sólo identidades sintéticas. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties={"controllocal.programas.habilitado=false","spring.datasource.hikari.maximum-pool-size=6"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters=false)
class MetasVersionadasC7IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext app;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager gestor;
    @Autowired MetaComercialService metas;@Autowired ActuacionesService actuaciones;@Autowired RendimientoComercialService rendimiento;
    @Autowired org.springframework.test.web.servlet.MockMvc http;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.controllocal.persistence.repositorio.IdentidadColaboracionRepository identidades;
    OrigenesDePrueba.Equipo e;SesionesComercialesDePrueba sesiones;ActuacionesHumanasC4DePrueba humanas;TransactionTemplate tx;
    String mes;List<LocalDate> fechas;
    static String key(){return UUID.randomUUID().toString();}static Procedencia proc(){return Procedencia.deLaPantalla();}
    @BeforeEach void iniciar(TestInfo info){EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);
        e=new OrigenesDePrueba(jdbc,gestor,app.getBean(MotorDeCaptura.class),app.getBean(ContextosService.class)).empresa();
        sesiones=new SesionesComercialesDePrueba(jdbc,app.getBean(ContextosService.class),app.getBean(CuentaService.class),app.getBean(FactorAutenticacionRepository.class),app.getBean(CifradoSecretos.class));
        humanas=new ActuacionesHumanasC4DePrueba(actuaciones,app.getBean(Documentos.class));mes=YearMonth.now(ZoneId.of("America/Lima")).toString();var ym=YearMonth.parse(mes);fechas=List.of(ym.atDay(2),ym.atDay(12),ym.atDay(24));}
    @AfterEach void terminar(){org.springframework.security.core.context.SecurityContextHolder.clearContext();EvidenciaC4DePrueba.limpiar();}
    Asignacion asignar(Actor a,String codigo,String op,long version,int valor){return new Asignacion(a.idRolOperativo(),codigo,op,version,valor,fechas,"Objetivo expresamente acordado TEST");}
    Establecer establecer(Asignacion...xs){return new Establecer(mes,List.of(xs));}
    MetaDeAgente nueva(Asignacion x,Actor autoridad){var sid=sesiones.de(autoridad);var p=actuaciones.prepararEstablecimientoMetas(autoridad,establecer(x),sid,proc());return humanas.confirmar(p,autoridad,sid,Resultado.class).datos().metas().getFirst();}
    MetaDeAgente actual(Actor a,String codigo,String op){return metas.del(mes,a).stream().filter(m->m.idRolAgente()==a.idRolOperativo()&&m.kpi().equals(codigo)&&Objects.equals(m.operacion(),op)).findFirst().orElseThrow();}
    Propuesta propuesta(int valor){return new Propuesta(OPORTUNIDAD_APERTURA.name(),"VENTA",0L,null,null,valor,fechas,"Propuesta propia explícita TEST");}
    PropuestaMeta proponer(int valor){return metas.proponer(mes,propuesta(valor),e.agente(),sesiones.de(e.agente()),key(),proc()).metas().getFirst().propuesta();}
    int contar(String tabla){return jdbc.queryForObject("select count(*) from "+tabla+" where organizacion_id=?",Integer.class,e.agente().idOrganizacion());}

    @Test void proponerNoEstableceYBrokerConfirmaUnaSolaVez(){
        var p=proponer(8);assertNull(actual(e.agente(),OPORTUNIDAD_APERTURA.name(),"VENTA").valor());assertEquals(0,contar("meta_comercial"));
        assertThrows(AccesoNoAutorizadoException.class,()->actuaciones.prepararDecisionMeta(e.agente(),new Decision(p.idRevision(),p.version(),TipoDecision.APROBAR,null,"Decisión no competente TEST"),sesiones.de(e.agente()),proc()));
        var sid=sesiones.de(e.broker());var preparada=actuaciones.prepararDecisionMeta(e.broker(),new Decision(p.idRevision(),p.version(),TipoDecision.APROBAR,9,"Aprobación exacta del objetivo TEST"),sid,proc());assertEquals(0,contar("meta_comercial"));
        var hecha=humanas.confirmar(preparada,e.broker(),sid,Resultado.class);assertEquals(9,hecha.datos().metas().getFirst().valor());assertEquals(1,contar("meta_comercial"));
        assertEquals(hecha.resultado().resultado(),actuaciones.confirmar(e.broker(),preparada.referencia(),sid,hecha.confirmacion(),proc()).resultado());assertEquals(1,contar("meta_revision"));
    }
    @Test void versionesYOperacionesSeparadasSinCambiarLaHistoria(){
        var v=nueva(asignar(e.agente(),ENCARGO_PRIMERA_HABILITACION.name(),"VENTA",0,4),e.broker());
        nueva(asignar(e.agente(),ENCARGO_PRIMERA_HABILITACION.name(),"ALQUILER",0,6),e.broker());
        var dos=nueva(asignar(e.agente(),ENCARGO_PRIMERA_HABILITACION.name(),"VENTA",v.version(),7),e.broker());assertEquals(2,dos.version());assertEquals(2,dos.historial().size());assertEquals(4,dos.historial().getFirst().valorPropuesto());assertEquals(6,actual(e.agente(),ENCARGO_PRIMERA_HABILITACION.name(),"ALQUILER").valor());
        assertThrows(ConflictoException.class,()->actuaciones.prepararEstablecimientoMetas(e.broker(),establecer(asignar(e.agente(),ENCARGO_PRIMERA_HABILITACION.name(),"VENTA",1,5)),sesiones.de(e.broker()),proc()));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update meta_revision set valor_propuesto=99 where id_revision=?",dos.historial().getFirst().id()));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("delete from meta_comercial where id_meta=?",dos.idMeta()));
    }
    @Test void rechazarYObservarConservanPropuestaSinCrearObjetivoCero(){
        for(var decision:List.of(TipoDecision.RECHAZAR,TipoDecision.OBSERVAR)){
            var p=proponer(5);var sid=sesiones.de(e.broker());var preparada=actuaciones.prepararDecisionMeta(e.broker(),new Decision(p.idRevision(),p.version(),decision,null,"Resultado razonado del broker TEST"),sid,proc());
            var resultado=humanas.confirmar(preparada,e.broker(),sid,Resultado.class).datos().metas().getFirst();assertNull(resultado.valor());assertNull(resultado.idMeta());assertEquals(0,contar("meta_comercial"));
            var historica=metas.consultarPropuesta(p.idRevision(),e.agente());assertEquals(decision==TipoDecision.RECHAZAR?"R":"O",historica.constancia().estado());assertNotNull(historica.constancia().fechaDecision());
        }
    }
    @Test void independienteEstableceSuPropiaMetaSinBrokerFicticio(){
        Actor solo=EspacioIndividualB0DePrueba.crear(jdbc,tx);var m=nueva(asignar(solo,CIERRE_CONTRACTUAL_FAVORABLE.name(),"VENTA",0,2),solo);assertEquals(2,m.valor());assertEquals(solo.idRolOperativo(),m.historial().getFirst().decisor());
        assertThrows(AccesoNoAutorizadoException.class,()->metas.proponer(mes,propuesta(5),solo,sesiones.de(solo),key(),proc()));
    }
    @Test void unAgenteEmpresarialNoEstableceNiGobiernoConsultaCartera(){
        assertThrows(AccesoNoAutorizadoException.class,()->actuaciones.prepararEstablecimientoMetas(e.agente(),establecer(asignar(e.agente(),OPORTUNIDAD_APERTURA.name(),"VENTA",0,3)),sesiones.de(e.agente()),proc()));
        assertThrows(AccesoNoAutorizadoException.class,()->metas.del(mes,e.admin()));assertThrows(AccesoNoAutorizadoException.class,()->metas.propuestasPendientes(e.admin()));assertThrows(AccesoNoAutorizadoException.class,()->rendimiento.del(mes,e.admin()));
        assertThrows(AccesoNoAutorizadoException.class,()->rendimiento.pulso(mes,e.agente()));assertEquals(4,metas.definicion(e.admin()).categorias().size());
    }
    @Test void aislamientoYRevocacionImpediranLeerDecidirORecuperar(){
        var p=proponer(5);var otra=new OrigenesDePrueba(jdbc,gestor,app.getBean(MotorDeCaptura.class),app.getBean(ContextosService.class)).empresa();
        assertThrows(NoEncontradoException.class,()->metas.consultarPropuesta(p.idRevision(),otra.broker()));
        assertThrows(NoEncontradoException.class,()->actuaciones.prepararDecisionMeta(otra.broker(),new Decision(p.idRevision(),p.version(),TipoDecision.APROBAR,null,"Decisión ajena no autorizada TEST"),sesiones.de(otra.broker()),proc()));
        var sid=sesiones.de(e.broker());var d=establecer(asignar(e.agente(),OPORTUNIDAD_APERTURA.name(),"VENTA",0,5));var k=key();metas.fijar(d,e.broker(),sid,k,proc());assertTrue(metas.resultado("establecer_meta",k,e.broker()).isPresent());
        var nuevoBroker=new OrigenesDePrueba(jdbc,gestor,app.getBean(MotorDeCaptura.class),app.getBean(ContextosService.class)).brokerAdicional(e);
        tx.executeWithoutResult(t->{
            jdbc.update("update supervision_agente set fecha_fin=current_date where organizacion_id=? and id_rol_agente=? and fecha_fin is null",e.agente().idOrganizacion(),e.agente().idRolOperativo());
            jdbc.update("insert into supervision_agente(organizacion_id,id_rol_broker,id_rol_agente,fecha_asignacion,motivo) values(?,?,?,current_date,'Cambio de supervisor TEST')",e.agente().idOrganizacion(),nuevoBroker.idRolOperativo(),e.agente().idRolOperativo());
            jdbc.execute("set constraints all immediate");
        });
        assertThrows(NoEncontradoException.class,()->metas.consultarPropuesta(p.idRevision(),e.broker()));assertThrows(AccesoNoAutorizadoException.class,()->metas.resultado("establecer_meta",k,e.broker()));
    }
    @Test void rollbackIncluyeObjetivoRevisionYRecibo(){
        var d=establecer(asignar(e.agente(),OPORTUNIDAD_APERTURA.name(),"VENTA",0,5));var sid=sesiones.de(e.broker());var k=key();int recibos=contar("comando_idempotente");
        tx.executeWithoutResult(t->{metas.fijar(d,e.broker(),sid,k,proc());t.setRollbackOnly();});assertEquals(0,contar("meta_comercial"));assertEquals(0,contar("meta_revision"));assertEquals(recibos,contar("comando_idempotente"));
    }
    @Test void replayDevuelveLaVersionOriginalSinRestaurarUnObjetivoViejo(){
        var sid=sesiones.de(e.broker());var k=key();var uno=establecer(asignar(e.agente(),OPORTUNIDAD_APERTURA.name(),"VENTA",0,5));var original=metas.fijar(uno,e.broker(),sid,k,proc());
        metas.fijar(establecer(asignar(e.agente(),OPORTUNIDAD_APERTURA.name(),"VENTA",1,8)),e.broker(),sid,key(),proc());
        assertEquals(original,metas.fijar(uno,e.broker(),sid,k,proc()));assertEquals(8,actual(e.agente(),OPORTUNIDAD_APERTURA.name(),"VENTA").valor());assertEquals(2,contar("meta_revision"));
        assertThrows(ReglaNegocioException.class,()->metas.fijar(establecer(asignar(e.agente(),OPORTUNIDAD_APERTURA.name(),"VENTA",2,9)),e.broker(),sid,k,proc()));
    }
    @Test void dosConfirmacionesConcurrentesNoCreanDosMetas(){
        var sid=sesiones.de(e.broker());var p1=actuaciones.prepararEstablecimientoMetas(e.broker(),establecer(asignar(e.agente(),OPORTUNIDAD_APERTURA.name(),"VENTA",0,5)),sid,proc());var p2=actuaciones.prepararEstablecimientoMetas(e.broker(),establecer(asignar(e.agente(),OPORTUNIDAD_APERTURA.name(),"VENTA",0,8)),sid,proc());
        var c1=humanas.revisar(p1,e.broker(),sid);var c2=humanas.revisar(p2,e.broker(),sid);var barrera=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try{List<Future<Boolean>> resultados=new ArrayList<>();for(int i=0;i<2;i++){var p=i==0?p1:p2;var c=i==0?c1:c2;resultados.add(pool.submit(()->{barrera.await(20,TimeUnit.SECONDS);try{return "EXITO".equals(actuaciones.confirmar(e.broker(),p.referencia(),sid,c,proc()).estado());}catch(ConflictoException|ReglaNegocioException ex){return false;}}));}
            int exitos=0;for(var f:resultados)if(f.get(60,TimeUnit.SECONDS))exitos++;assertEquals(1,exitos);assertEquals(1,contar("meta_comercial"));assertEquals(1,contar("meta_revision"));
        }catch(Exception ex){throw new AssertionError(ex);}finally{pool.shutdownNow();}
    }
    @Test void sustituirUnaPropuestaNoFingeUnRechazo(){
        var p=proponer(5);var d=new Propuesta(OPORTUNIDAD_APERTURA.name(),"VENTA",0L,p.idRevision(),p.version(),7,fechas,"Sustitución expresa de propuesta TEST");
        var nueva=metas.proponer(mes,d,e.agente(),sesiones.de(e.agente()),key(),proc()).metas().getFirst().propuesta();assertEquals(1,metas.propuestasPendientes(e.broker()).size());
        var anterior=metas.consultarPropuesta(p.idRevision(),e.agente());assertEquals("E",anterior.constancia().estado());assertEquals(nueva.idRevision(),anterior.constancia().reemplazadaPor());assertNull(anterior.constancia().decisor());assertEquals(2,contar("meta_revision"));assertEquals(0,contar("meta_comercial"));
        assertThrows(ConflictoException.class,()->metas.validarDecision(new Decision(p.idRevision(),p.version(),TipoDecision.APROBAR,null,"Una propuesta sustituida no se decide TEST"),e.broker()));
    }
    @Test void unaMetaCambiadaImpideAprobarElValorViejoPeroNoObservarLaPropuesta(){
        var p=proponer(5);nueva(asignar(e.agente(),OPORTUNIDAD_APERTURA.name(),"VENTA",0,9),e.broker());
        assertThrows(ConflictoException.class,()->metas.validarDecision(new Decision(p.idRevision(),p.version(),TipoDecision.APROBAR,null,"La base ya cambió, debe revisarse TEST"),e.broker()));
        var sid=sesiones.de(e.broker());var acto=actuaciones.prepararDecisionMeta(e.broker(),new Decision(p.idRevision(),p.version(),TipoDecision.OBSERVAR,null,"Observar sin alterar el objetivo vigente TEST"),sid,proc());
        humanas.confirmar(acto,e.broker(),sid,Resultado.class);assertEquals(9,actual(e.agente(),OPORTUNIDAD_APERTURA.name(),"VENTA").valor());assertEquals("O",metas.consultarPropuesta(p.idRevision(),e.agente()).constancia().estado());
    }
    @Test void calendarioYVersionSonExpresosYLosDatosHistoricosNoSeConvierten(){
        var a=e.agente();var sid=sesiones.de(e.broker());
        assertThrows(ReglaNegocioException.class,()->metas.validarEstablecimiento(establecer(new Asignacion(a.idRolOperativo(),OPORTUNIDAD_APERTURA.name(),"VENTA",null,5,fechas,"Sin versión no se acepta TEST")),e.broker()));
        assertThrows(ReglaNegocioException.class,()->metas.validarEstablecimiento(establecer(new Asignacion(a.idRolOperativo(),OPORTUNIDAD_APERTURA.name(),"VENTA",0L,5,List.of(fechas.getFirst(),fechas.getFirst()),"Fecha duplicada no pondera TEST")),e.broker()));
        jdbc.update("insert into meta_comercial(organizacion_id,id_rol_agente,kpi,anio,mes,valor,id_rol_autor) values(?,?,'C',?,?,12,?)",a.idOrganizacion(),a.idRolOperativo(),YearMonth.parse(mes).getYear(),YearMonth.parse(mes).getMonthValue(),e.broker().idRolOperativo());
        var legacy=actual(a,"C",null);assertTrue(legacy.historica());assertEquals(12,legacy.valor());assertNull(legacy.version());assertEquals("HISTORICA_CPSF",legacy.definicion());
        assertThrows(ReglaNegocioException.class,()->metas.fijar(establecer(asignar(a,"C","GENERAL",0,9)),e.broker(),sid,key(),proc()));assertEquals(12,actual(a,"C",null).valor());
        var vacias=metas.del(mes,a).stream().filter(m->!m.historica()).toList();assertEquals(7,vacias.size());assertTrue(vacias.stream().allMatch(m->m.valor()==null));
    }
    @Test void sinMetaNiCalendarioNoSeInventanObjetivosNiUnPulsoCompetitivo(){
        var panel=rendimiento.del(mes,e.agente());assertEquals(7,panel.kpis().size());assertNull(panel.pulso());assertTrue(panel.kpis().stream().allMatch(k->k.avance().metaPeriodo()==null));
        var meta=new Asignacion(e.agente().idRolOperativo(),OPORTUNIDAD_APERTURA.name(),"VENTA",0L,3,null,"Objetivo sin calendario aún declarado TEST");nueva(meta,e.broker());
        var k=rendimiento.del(mes,e.agente()).kpis().stream().filter(x->x.codigo().equals(OPORTUNIDAD_APERTURA.name())&&x.operacion().equals("VENTA")).findFirst().orElseThrow();assertEquals(3L,k.avance().metaPeriodo());assertNull(k.avance().necesarioPorFechaRestante());assertEquals("SIN_CALENDARIO_DECLARADO",k.avance().estadoCalculo());
    }
    @Test void sqlNoPermiteReescribirHistoriaNiEnlazarUnaSustitucionDeOtraOperacion(){
        var venta=proponer(5);var otra=new Propuesta(OPORTUNIDAD_APERTURA.name(),"ALQUILER",0L,null,null,7,fechas,"Propuesta separada de alquiler TEST");
        var alquiler=metas.proponer(mes,otra,e.agente(),sesiones.de(e.agente()),key(),proc()).metas().getFirst().propuesta();
        for(long destino:List.of(venta.idRevision(),alquiler.idRevision())){
            assertThrows(org.springframework.dao.DataAccessException.class,()->tx.executeWithoutResult(t->{
                jdbc.update("update meta_revision set reemplazada_por=?,version=version+1 where organizacion_id=? and id_revision=?",destino,e.agente().idOrganizacion(),venta.idRevision());
                jdbc.execute("set constraints all immediate");
            }));
            var intacta=metas.consultarPropuesta(venta.idRevision(),e.agente());assertNull(intacta.constancia().reemplazadaPor());assertEquals(venta.version(),intacta.version());
        }
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update meta_revision set valor_propuesto=99 where organizacion_id=? and id_revision=?",e.agente().idOrganizacion(),venta.idRevision()));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("delete from meta_revision where organizacion_id=? and id_revision=?",e.agente().idOrganizacion(),venta.idRevision()));
        assertEquals(2,contar("meta_revision"));assertEquals(0,contar("meta_comercial"));
    }
    @Test void sieteCategoriasYPendienteReutilizanUnaSolaLecturaDeIdentidad(){
        proponer(5);org.mockito.Mockito.clearInvocations(identidades);
        var filas=metas.del(mes,e.agente());assertEquals(7,filas.size());assertEquals(1,filas.stream().filter(f->f.propuesta()!=null).count());
        org.mockito.Mockito.verify(identidades,org.mockito.Mockito.times(1)).deResponsable(org.mockito.ArgumentMatchers.eq(e.agente().idOrganizacion()),org.mockito.ArgumentMatchers.eq(e.agente().idRolOperativo()),org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoMoreInteractions(identidades);
    }
    @Test void aperturaDelPeriodoNoEsElStockActual(){
        var banco=new FormalizacionesDePrueba(app);var c=banco.nuevo("VENTA",ComisionC5IntegrationTest.fija(ESCRITURA,ESCRITURA,List.of()));var anterior=YearMonth.now(ZoneId.of("America/Lima")).minusMonths(1);
        var lectura=app.getBean(HechosDeMetas.class).leer(anterior.atDay(1),anterior.atEndOfMonth(),Instant.now(),c.agente());
        assertTrue(lectura.hechos().isEmpty());assertTrue(lectura.faltantes().isEmpty());
    }
    @Test void aperturasYHabilitacionesSonEventosYElCierreRequiereC5(){
        var banco=new FormalizacionesDePrueba(app);var c=banco.nuevo("VENTA",ComisionC5IntegrationTest.fija(ESCRITURA,ESCRITURA,List.of()));var fuente=app.getBean(HechosDeMetas.class);var hoy=LocalDate.now(ZoneId.of("America/Lima"));
        var antes=fuente.leer(hoy.withDayOfMonth(1),hoy,Instant.now(),c.agente());assertEquals(1,antes.hechos().stream().filter(h->h.indicador()==ENCARGO_PRIMERA_HABILITACION).count());assertEquals(1,antes.hechos().stream().filter(h->h.indicador()==OPORTUNIDAD_APERTURA).count());assertEquals(0,antes.hechos().stream().filter(h->h.indicador()==CIERRE_CONTRACTUAL_FAVORABLE).count());
        banco.confirmar(banco.listo(c,ESCRITURA,hoy,banco.venta(c,new BigDecimal("100000"),"PEN",null),false));
        var despues=fuente.leer(hoy.withDayOfMonth(1),hoy,Instant.now(),c.agente());assertEquals(1,despues.hechos().stream().filter(h->h.indicador()==CIERRE_CONTRACTUAL_FAVORABLE).count());assertEquals(1,despues.hechos().stream().filter(h->h.indicador()==OPORTUNIDAD_APERTURA).count());assertTrue(despues.hechos().stream().allMatch(h->h.operacion().equals("VENTA")));
    }
    @Test void superficieHttpNoConservaElPutSinRevisionYNoAceptaCamposOcultos()throws Exception{
        sesiones.de(e.broker());var principal=SesionesDePrueba.abrir(jdbc,app.getBean(ContextosService.class),e.broker());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_BROKER")));
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/indicadores/metas").contentType("application/json").content("{}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isMethodNotAllowed());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/indicadores/metas/definicion")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.version").value(VERSION));
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/indicadores/metas/establecimiento/propuestas").contentType("application/json").content("{\"mes\":\""+mes+"\",\"asignaciones\":[],\"objetivoAutomatico\":true}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());assertEquals(0,contar("meta_comercial"));
    }
    @Test void contactoSalienteSeCuentaUnaVezConDosVinculosYLogisticaNoSuma(){
        var banco=new FormalizacionesDePrueba(app);var c=banco.negociacion.mesaEnEquipo(e,"VENTA",new BigDecimal("100000"),"PEN",true);var a=c.agente();var sid=c.sesion();
        var persona=new InteraccionService.ParticipanteDeclarado(null,"GESTOR_COMERCIAL","Gestora TEST sin titularidad","900000098","GESTORA-TEST-"+key());
        var vinculos=List.of(new InteraccionService.VinculoActividad(InteraccionService.TipoContexto.ENCARGO,c.encargo(),null,null),new InteraccionService.VinculoActividad(InteraccionService.TipoContexto.OPORTUNIDAD,c.oportunidad(),null,null));var dia=LocalDate.now(ZoneId.of("America/Lima"));var momento=new InteraccionService.MomentoHecho(dia,null,null);
        for(boolean comercial:List.of(true,false)){
            var gestion=new InteraccionService.GestionContacto(Set.of(comercial?InteraccionService.FinalidadContacto.PROSPECCION_COMERCIAL:InteraccionService.FinalidadContacto.COORDINACION_ACCESO),Set.of(comercial?InteraccionService.FuncionContacto.GESTION_COMERCIAL:InteraccionService.FuncionContacto.COORDINACION_ACCESO),"Función declarada en esta conversación TEST",false,null);
            var d=new InteraccionService.DatosInteraccion(vinculos,persona,persona,CatalogoInteracciones.TipoActividad.LLAMADA,CatalogoInteracciones.CanalComunicacion.TELEFONO,null,List.of(new InteraccionService.EventoDeclarado(key(),CatalogoInteracciones.EventoComunicacion.LLAMADA_SALIENTE,momento)),CatalogoInteracciones.ResultadoInteraccion.INTERCAMBIO_EFECTIVO,momento,"Conversación real sintética TEST",List.of(),null,null,"Declaración TEST",null,gestion,null);
            tx.execute(t->app.getBean(InteraccionService.class).registrar(d,a,sid,proc()));
        }
        var hs=app.getBean(HechosDeMetas.class).leer(dia.withDayOfMonth(1),dia,Instant.now(),a).hechos().stream().filter(h->h.indicador()==PROSPECCION_EFECTIVA_SALIENTE).toList();assertEquals(1,hs.size());assertEquals("GENERAL",hs.getFirst().operacion());assertEquals(a.idRolOperativo(),hs.getFirst().responsable());
    }
}
