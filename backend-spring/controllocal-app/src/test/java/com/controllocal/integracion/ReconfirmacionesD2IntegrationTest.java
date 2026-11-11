package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.demanda.InteresadosDeDemanda.*;
import com.controllocal.service.disponibilidad.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.util.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.controllocal.service.disponibilidad.DisponibilidadContextualService.*;

/** D2 sobre PG real propio: disponibilidad tardía, espera, aislamiento, CAS y TX concurrentes. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
 properties="controllocal.disponibilidad.observador.habilitado=false")
class ReconfirmacionesD2IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager gestor;@Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;@Autowired RequerimientoService demandas;
    @Autowired DisponibilidadContextualService disponibilidad;@Autowired ReconfirmacionesDisponibilidad solicitudes;
    @Autowired ContextosDeActividad actividad;
    @Autowired NotificacionesBrokerC7 avisosBroker;
    private OrigenesDePrueba origenes;private OrigenesDePrueba.Equipo equipo;private Actor agente;
    private ContextoSesion sesion;private RequerimientoService.FichaRequerimiento demanda;
    private Clock relojActividad,relojConfirmacion;private Instant t0;
    @BeforeEach void preparar(){
        origenes=new OrigenesDePrueba(jdbc,gestor,captura,contextos);equipo=origenes.empresa();agente=equipo.agente();sesion=origenes.sesion(agente);
        var contacto=new Referencia(TipoReferencia.CONTACTO_NUEVO,null,null,null,new DatosContacto(null,null,null,null,"999000001",null));
        demanda=demandas.crear(new RequerimientoService.DatosRequerimiento(null,null,null,null,null,null,"ALQUILER",List.of("DEPARTAMENTO"),null,null,null,null,null,null,null,"D2 TEST",null,null,contacto,null,null,null,null,Set.of()),agente,sesion,clave(),Procedencia.deLaPantalla());
        var a=AopTestUtils.getUltimateTargetObject(actividad);var c=AopTestUtils.getUltimateTargetObject(disponibilidad);
        relojActividad=(Clock)ReflectionTestUtils.getField(a,"reloj");relojConfirmacion=(Clock)ReflectionTestUtils.getField(c,"reloj");
        t0=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);ahora(t0);
    }
    @AfterEach void restaurar(){if(relojActividad!=null)ReflectionTestUtils.setField((Object)AopTestUtils.getUltimateTargetObject(actividad),"reloj",relojActividad);if(relojConfirmacion!=null)ReflectionTestUtils.setField((Object)AopTestUtils.getUltimateTargetObject(disponibilidad),"reloj",relojConfirmacion);}
    @Test void treintaYTtreintaYSieteNoDependenDeLaDisponibilidadReal(){
        confirmar(true,t0,0,agente,sesion);ahora(t0.plus(Duration.ofDays(30)).minusNanos(1000));assertTrue(listar().isEmpty());
        ahora(t0.plus(Duration.ofDays(30)));var s=listar().getFirst();assertEquals(t0.plus(Duration.ofDays(30)),s.disponibleEn());
        assertEquals(t0.plus(Duration.ofDays(36)),s.recordarEn());assertEquals(t0.plus(Duration.ofDays(37)),s.atencionHasta());assertEquals(s.atencionHasta(),s.terminaFrescuraEn());
    }
    @Test void recuperacionEnDiaCuarentaNoImputaDiezDiasDeRetraso(){
        confirmar(true,t0,0,agente,sesion);ahora(t0.plus(Duration.ofDays(40)));var s=listar().getFirst();
        assertEquals(t0.plus(Duration.ofDays(37)),s.terminaFrescuraEn());assertEquals(t0.plus(Duration.ofDays(46)),s.recordarEn());assertEquals(t0.plus(Duration.ofDays(47)),s.atencionHasta());assertFalse(s.orden().plazoVencido());
        assertEquals(s,listar().getFirst());assertEquals(1,cantidad());
    }
    @Test void recordatorioYEscalacionSonUnaVezYLaConfirmacionLosRetira(){
        confirmar(true,t0,0,agente,sesion);ahora(t0.plus(Duration.ofDays(40)));var s=listar().getFirst();
        ahora(s.recordarEn());listar();listar();assertEquals(1,eventos("SOLICITUD_DISPONIBILIDAD_RECORDATORIO"));assertEquals(0,eventos("SOLICITUD_DISPONIBILIDAD_ESCALADA"));
        ahora(s.atencionHasta());listar();listar();assertEquals(1,eventos("SOLICITUD_DISPONIBILIDAD_ESCALADA"));
        confirmar(true,s.atencionHasta(),1,agente,sesion);assertTrue(listar().isEmpty());assertEquals("RESUELTA",estado(s.id()));
    }
    @Test void posponerSinFechaNoRenuevaLosRelojesNiInventaVencimiento(){
        confirmar(true,t0,0,agente,sesion);ahora(t0.plus(Duration.ofDays(40)));var s=listar().getFirst();
        var d=new ReconfirmacionesDisponibilidad.Espera(s.id(),s.version(),ReconfirmacionesDisponibilidad.Respuesta.POSPONE,null,"Esperar información de la parte");String k=clave();
        var respuesta=solicitudes.registrarEspera(d,agente,sesion,k,Procedencia.deLaPantalla());
        assertEquals(respuesta,solicitudes.registrarEspera(d,agente,sesion,k,Procedencia.deLaPantalla()));
        ahora(s.atencionHasta().plus(Duration.ofDays(20)));var actual=listar().getFirst();assertEquals(s.disponibleEn(),actual.disponibleEn());assertEquals(s.atencionHasta(),actual.atencionHasta());assertNull(actual.orden().fecha());assertFalse(actual.orden().plazoVencido());assertEquals(0,eventos("SOLICITUD_DISPONIBILIDAD_RECORDATORIO"));
    }
    @Test void desconocerDetieneInsistenciaSinCrearConfirmacionNegativa(){
        confirmar(true,t0,0,agente,sesion);ahora(t0.plus(Duration.ofDays(40)));var s=listar().getFirst();
        solicitudes.registrarEspera(new ReconfirmacionesDisponibilidad.Espera(s.id(),s.version(),ReconfirmacionesDisponibilidad.Respuesta.DESCONOCE,null,"El propietario aún no responde"),agente,sesion,clave(),Procedencia.deLaPantalla());
        ahora(s.atencionHasta());var actual=listar().getFirst();assertEquals("DESCONOCE",actual.estado());assertNull(actual.orden().fecha());assertEquals(0,eventos("SOLICITUD_DISPONIBILIDAD_ESCALADA"));assertTrue(disponibilidad.consultar(TipoRecurso.DEMANDA,demanda.id(),agente).vigente().disponible());
    }
    @Test void negativaNoAbreOtroCiclo(){confirmar(false,t0,0,agente,sesion);ahora(t0.plus(Duration.ofDays(60)));assertTrue(listar().isEmpty());assertEquals(0,cantidad());}
    @Test void ausenciaDeConfirmacionNoCreaSolicitudSinNecesidadExpresa(){
        ahora(t0.plus(Duration.ofDays(40)));assertTrue(listar().isEmpty());
        var s=solicitudes.solicitarInicial(TipoRecurso.DEMANDA,demanda.id(),demanda.version(),agente,sesion,clave(),Procedencia.deLaPantalla());assertNull(s.baseFrescura());assertNull(s.confirmacionBase());assertNull(s.terminaFrescuraEn());assertEquals(1,listar().size());
    }
    @Test void brokerColegaAdminYOtraOrganizacionNoRespondenNiLeenLaSolicitud(){
        confirmar(true,t0,0,agente,sesion);ahora(t0.plus(Duration.ofDays(40)));var s=listar().getFirst();
        var d=new ReconfirmacionesDisponibilidad.Espera(s.id(),s.version(),ReconfirmacionesDisponibilidad.Respuesta.DESCONOCE,null,"TEST");
        for(var a:List.of(equipo.broker(),equipo.colega(),equipo.admin(),origenes.empresa().agente())){
            var sid=a.esAgente()?origenes.sesion(a):sesion;
            RuntimeException error=assertThrows(RuntimeException.class,()->solicitudes.registrarEspera(d,a,sid,clave(),Procedencia.deLaPantalla()));
            assertTrue(error instanceof AccesoNoAutorizadoException||error instanceof NoEncontradoException,error.toString());
        }
        assertEquals(s,listar().getFirst());
    }
    @Test void noSeFuerzaReasignacionDeDemandaParaRecrearLaSolicitud(){
        confirmar(true,t0,0,agente,sesion);ahora(t0.plus(Duration.ofDays(40)));var s=listar().getFirst();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("update requerimiento_cliente set id_rol_responsable=?,version=version+1 where organizacion_id=? and id_requerimiento=?",equipo.colega().idRolOperativo(),agente.idOrganizacion(),demanda.id()));
        assertEquals(s,listar().getFirst());assertTrue(solicitudes.listar(PoliticaDeDespacho.Consulta.todas(),equipo.colega()).isEmpty());
    }
    @Test void dosTransaccionesNoDuplicanSolicitudNiEventos()throws Exception{
        confirmar(true,t0,0,agente,sesion);ahora(t0.plus(Duration.ofDays(40)));
        ExecutorService hilos=Executors.newFixedThreadPool(2);var inicio=new CountDownLatch(1);
        try{var a=hilos.submit(()->{inicio.await();return listar();});var b=hilos.submit(()->{inicio.await();return listar();});inicio.countDown();assertEquals(a.get(30,TimeUnit.SECONDS).getFirst().id(),b.get(30,TimeUnit.SECONDS).getFirst().id());}
        finally{hilos.shutdownNow();}assertEquals(1,cantidad());assertEquals(1,eventos("SOLICITUD_DISPONIBILIDAD_DISPONIBLE"));
    }
    @Test void rollbackNoAnunciaSolicitudQueNoExiste(){
        confirmar(true,t0,0,agente,sesion);ahora(t0.plus(Duration.ofDays(40)));var tx=new TransactionTemplate(gestor);
        assertThrows(IllegalStateException.class,()->tx.execute(s->{listar();throw new IllegalStateException("rollback TEST");}));assertEquals(0,cantidad());assertEquals(0,eventos("SOLICITUD_DISPONIBILIDAD_DISPONIBLE"));assertEquals(1,listar().size());
    }
    @Test void confirmacionConcurrenteAntesDeEntregarImpideRecordatorioAntiguo()throws Exception{
        confirmar(true,t0,0,agente,sesion);ahora(t0.plus(Duration.ofDays(40)));var s=listar().getFirst();ahora(s.recordarEn());
        var retenida=new CountDownLatch(1);var liberar=new CountDownLatch(1);ExecutorService hilos=Executors.newFixedThreadPool(2);
        try{
            var confirma=hilos.submit(()->new TransactionTemplate(gestor).execute(tx->{confirmar(true,s.recordarEn(),1,agente,sesion);retenida.countDown();try{assertTrue(liberar.await(20,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return true;}));
            assertTrue(retenida.await(20,TimeUnit.SECONDS));var empieza=new CountDownLatch(1);
            var recordatorio=hilos.submit(()->{empieza.countDown();return listar();});assertTrue(empieza.await(20,TimeUnit.SECONDS));liberar.countDown();
            assertTrue(confirma.get(30,TimeUnit.SECONDS));assertTrue(recordatorio.get(30,TimeUnit.SECONDS).isEmpty());
            assertEquals(0,eventos("SOLICITUD_DISPONIBILIDAD_RECORDATORIO"));assertEquals("RESUELTA",estado(s.id()));
        }finally{liberar.countDown();hilos.shutdownNow();}
    }
    @Test void avisoAlBrokerSeRetiraSiLaConfirmacionLlegaAntesDeNuevaEntrega(){
        confirmar(true,t0,0,agente,sesion);ahora(t0.plus(Duration.ofDays(40)));var s=listar().getFirst();ahora(s.atencionHasta());listar();
        var avisos=avisosBroker.listar(1,100,false,equipo.broker()).items();var aviso=avisos.stream().filter(a->a.codigo().equals("NB-14")).findFirst().orElseThrow();
        assertEquals(demanda.id(),aviso.referencia());assertEquals("DEMANDA",aviso.recurso());assertEquals(1,eventos("SOLICITUD_DISPONIBILIDAD_ESCALADA"));
        confirmar(true,s.atencionHasta(),1,agente,sesion);
        // No se necesita otra lectura del agente ni otro barrido para impedir la entrega obsoleta.
        assertTrue(avisosBroker.listar(1,100,false,equipo.broker()).items().stream().noneMatch(a->a.codigo().equals("NB-14")));
        assertEquals("D",jdbc.queryForObject("select estado from alerta where organizacion_id=? and nb_evento=?",String.class,agente.idOrganizacion(),aviso.evento()));
    }
    private List<ReconfirmacionesDisponibilidad.Vista> listar(){return solicitudes.listar(PoliticaDeDespacho.Consulta.todas(),agente);}
    private void ahora(Instant i){Clock c=Clock.fixed(i,ZoneOffset.UTC);ReflectionTestUtils.setField((Object)AopTestUtils.getUltimateTargetObject(actividad),"reloj",c);ReflectionTestUtils.setField((Object)AopTestUtils.getUltimateTargetObject(disponibilidad),"reloj",c);}
    private void confirmar(boolean disponible,Instant fecha,long version,Actor a,ContextoSesion sid){disponibilidad.confirmar(new DatosConfirmacion(TipoRecurso.DEMANDA,demanda.id(),demanda.version(),version,disponible,fecha,null,"Declaración TEST","ref-sintetica"),a,sid,clave(),Procedencia.deLaPantalla());}
    private int cantidad(){return jdbc.queryForObject("select count(*) from solicitud_reconfirmacion where organizacion_id=?",Integer.class,agente.idOrganizacion());}
    private int eventos(String tipo){return jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and tipo=?",Integer.class,agente.idOrganizacion(),tipo);}
    private String estado(UUID id){return jdbc.queryForObject("select estado from solicitud_reconfirmacion where id=?",String.class,id);}
    private static String clave(){return "d2-"+UUID.randomUUID();}
}
