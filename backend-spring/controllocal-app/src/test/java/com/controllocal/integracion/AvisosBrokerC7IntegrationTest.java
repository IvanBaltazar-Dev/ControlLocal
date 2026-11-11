package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.soporte.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.domain.comercial.TipoHitoContractual;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** NB con productores C2/C4/C5, SID y PostgreSQL reales propios. Sin transportes externos. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class AvisosBrokerC7IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;@Autowired NotificacionesBrokerC7 avisos;
    @Autowired AsignacionService asignaciones;@Autowired PlatformTransactionManager gestor;
    private FormalizacionesDePrueba b;
    @BeforeEach void iniciar(TestInfo info){EvidenciaC4DePrueba.iniciar(info);b=new FormalizacionesDePrueba(contexto);}
    @AfterEach void finalizar(){EvidenciaC4DePrueba.limpiar();}
    @Test void ofertaRealNotificaUnaVezYAtenderNoRegistraRespuestaComercial(){
        var c=mesa();var f=b.negociacion.presentar(c,new BigDecimal("95000"));var a=activos(c.equipo().broker(),"NB-04").getFirst();
        assertEquals(c.encargo(),a.referencia());assertEquals(f.id(),a.idHecho());assertEquals(f.version(),a.version());assertEquals("ENCARGO",a.recurso());
        assertEquals(a,activos(c.equipo().broker(),"NB-04").getFirst());assertEquals(1,entregas(c,"NB-04"));
        assertTrue(avisos.atender(a.evento(),c.equipo().broker(),b.sesiones.de(c.equipo().broker())));
        assertFalse(avisos.atender(a.evento(),c.equipo().broker(),b.sesiones.de(c.equipo().broker())));assertTrue(activos(c.equipo().broker(),"NB-04").isEmpty());
        assertEquals("P",b.negociacion.ficha(c,f.id()).estado());
    }
    @Test void contraofertaConservaSuIdentidadRetiraLaActuacionSuperadaYNoCambiaPrecioDelEncargo(){
        var c=mesa();var f=b.negociacion.presentar(c,new BigDecimal("95000"));var anterior=activos(c.equipo().broker(),"NB-04").getFirst();
        var contra=b.negociacion.contraofertar(c,f,new BigDecimal("98000"),"P");var actual=activos(c.equipo().broker(),"NB-05").getFirst();
        assertNotEquals(anterior.evento(),actual.evento());assertEquals(contra.id(),actual.idHecho());assertTrue(activos(c.equipo().broker(),"NB-04").isEmpty());
        assertEquals(0,new BigDecimal("100000").compareTo(b.encargos.consultar(c.encargo(),c.agente()).condiciones().precioPedido()));
        assertEquals(1,entregas(c,"NB-04"));assertEquals(1,entregas(c,"NB-05"));
        assertEquals("D",b.jdbc.queryForObject("select estado from alerta where organizacion_id=? and nb_evento=?",String.class,c.agente().idOrganizacion(),anterior.evento()));
    }
    @Test void brokerQueYaActuoConservaAntecedenteAtendidoSinOtroRecordatorio(){
        var c=mesa();var historial=avisos.listar(1,100,true,c.equipo().broker()).items();
        var precio=historial.stream().filter(x->x.codigo().equals("NB-03")).findFirst().orElseThrow();
        assertEquals("T",precio.estado());assertTrue(precio.atendidoPorActuacionPropia());assertTrue(activos(c.equipo().broker(),"NB-03").isEmpty());
    }
    @Test void agenteGobiernoYBrokerAjenoNoObtienenAvisosNiConteosPrivados(){
        var c=mesa();b.negociacion.presentar(c,new BigDecimal("95000"));var propio=activos(c.equipo().broker(),"NB-04").getFirst();
        assertThrows(AccesoNoAutorizadoException.class,()->avisos.listar(1,20,false,c.agente()));
        assertThrows(AccesoNoAutorizadoException.class,()->avisos.listar(1,20,false,c.equipo().admin()));
        var otro=mesa();assertEquals(0,avisos.listar(1,20,false,otro.equipo().broker()).total());
        var a=assertThrows(NoEncontradoException.class,()->avisos.atender(propio.evento(),otro.equipo().broker(),b.sesiones.de(otro.equipo().broker())));
        var n=assertThrows(NoEncontradoException.class,()->avisos.atender(Long.MAX_VALUE,otro.equipo().broker(),b.sesiones.de(otro.equipo().broker())));assertEquals(n.getMessage(),a.getMessage());
    }
    @Test void cambioDeSupervisorRevalidaDestinoYConservaHechoYFecha(){
        var c=mesa();b.negociacion.presentar(c,new BigDecimal("95000"));var anterior=c.equipo().broker();var a=activos(anterior,"NB-04").getFirst();
        var origenes=new OrigenesDePrueba(b.jdbc,gestor,contexto.getBean(com.controllocal.service.captura.MotorDeCaptura.class),contexto.getBean(ContextosService.class));
        var nuevo=origenes.brokerAdicional(c.equipo());b.sesiones.de(nuevo);
        asignaciones.reasignar(new AsignacionService.DatosReasignacion(c.agente().idRolOperativo(),nuevo.idRolOperativo(),"Distribución de supervisión TEST NB"),c.equipo().admin());
        assertTrue(activos(anterior,"NB-04").isEmpty());assertThrows(NoEncontradoException.class,()->avisos.atender(a.evento(),anterior,b.sesiones.de(anterior)));
        var actual=activos(nuevo,"NB-04").getFirst();assertEquals(a.evento(),actual.evento());assertEquals(a.registradoEn(),actual.registradoEn());assertEquals(a.version(),actual.version());
        assertEquals(2,entregas(c,"NB-04"));
    }
    @Test void dosConsultasConcurrentesNoDuplicanEntregas()throws Exception{
        var c=mesa();b.negociacion.presentar(c,new BigDecimal("95000"));var hilos=Executors.newFixedThreadPool(2);var inicio=new CountDownLatch(1);
        try{var a=hilos.submit(()->{inicio.await();return activos(c.equipo().broker(),"NB-04");});var d=hilos.submit(()->{inicio.await();return activos(c.equipo().broker(),"NB-04");});inicio.countDown();assertEquals(a.get(30,TimeUnit.SECONDS),d.get(30,TimeUnit.SECONDS));}
        finally{hilos.shutdownNow();}assertEquals(1,entregas(c,"NB-04"));
    }
    @Test void rollbackDeLaOfertaNoEntregaUnHechoInexistente(){
        var c=mesa();var datos=b.negociacion.registro(c,new BigDecimal("95000"),"I",null,null);var sid=b.sesiones.de(c.agente());
        var p=b.actuaciones.prepararRegistroOferta(c.agente(),datos,sid,Procedencia.deLaPantalla());
        var confirmacion=b.humanas.revisar(p,c.agente(),sid);
        String nombre="nb_rollback_"+UUID.randomUUID().toString().replace("-","");
        // Fallo DENTRO de la TX canónica del efecto; no cambia guardas ni fuerza rollback de una TX ya comprometida.
        b.jdbc.execute("create function "+nombre+"() returns trigger language plpgsql as $$ begin if NEW.organizacion_id="+c.agente().idOrganizacion()+" and NEW.tipo='NEGOCIACION_REGISTRADA' then raise exception 'TEST_ROLLBACK_NB';end if;return NEW;end $$");
        b.jdbc.execute("create trigger "+nombre+" before insert on evento_dominio for each row execute function "+nombre+"()");
        try{var resultado=b.actuaciones.confirmar(c.agente(),p.referencia(),sid,confirmacion,Procedencia.deLaPantalla());assertEquals("EN_VERIFICACION",resultado.estado());assertNull(resultado.resultado());}
        finally{b.jdbc.execute("drop trigger "+nombre+" on evento_dominio");b.jdbc.execute("drop function "+nombre+"()");}
        assertEquals("EN_VERIFICACION",b.actuaciones.confirmar(c.agente(),p.referencia(),sid,confirmacion,Procedencia.deLaPantalla()).estado());
        assertTrue(activos(c.equipo().broker(),"NB-04").isEmpty());assertEquals(0,entregas(c,"NB-04"));assertEquals(0,b.jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and tipo='NEGOCIACION_REGISTRADA'",Integer.class,c.agente().idOrganizacion()));
        assertEquals(0,b.jdbc.queryForObject("select count(*) from oferta where organizacion_id=?",Integer.class,c.agente().idOrganizacion()));
    }
    @Test void formalizacionYDineroMantienenAvisosYSignificadosSeparados(){
        var ejecucion=new com.controllocal.service.encargo.PactosDeEncargo.EjecucionPacto(
            new com.controllocal.service.encargo.PactosDeEncargo.CondicionDevengo(TipoHitoContractual.ESCRITURA),
            new com.controllocal.service.encargo.PactosDeEncargo.CondicionExigibilidad(TipoHitoContractual.ESCRITURA,null,null,null,null));
        var c=b.nuevo("VENTA",new EncargoService.Condiciones(new BigDecimal("100000"),"PEN",null,"F","N",new BigDecimal("15000"),"PEN","N",null,
            "ESCRITURA","PROPIETARIO_TEST","AGENCIA_TEST","ESCRITURA","Pacto estructurado explícito TEST",List.of(),null,ejecucion));
        var acuerdo=b.venta(c,new BigDecimal("100000"),"PEN",null);var r=b.listo(c,TipoHitoContractual.ESCRITURA,FormalizacionesDePrueba.hoy(),acuerdo,false);
        assertTrue(activos(c.broker(),"NB-07").stream().anyMatch(a->a.entidadHecho().equals("HITO_CONTRACTUAL")));
        b.confirmar(r);assertTrue(activos(c.broker(),"NB-07").isEmpty());
        assertTrue(avisos.listar(1,100,true,c.broker()).items().stream().anyMatch(a->a.codigo().equals("NB-08")&&a.atendidoPorActuacionPropia()));
        assertTrue(avisos.listar(1,100,true,c.broker()).items().stream().noneMatch(a->a.codigo().equals("NB-09")));
        b.cobrar(c,b.prepararCobro(c,r.expediente(),new BigDecimal("500"),FormalizacionesDePrueba.hoy(),FormalizacionesDePrueba.clave()));
        assertEquals(1,avisos.listar(1,100,true,c.broker()).items().stream().filter(a->a.codigo().equals("NB-09")).count());
    }
    private ActividadNegociacionDePrueba.Caso mesa(){return b.negociacion.mesa("VENTA",new BigDecimal("100000"),"PEN",true);}
    private List<NotificacionesBrokerC7.Aviso> activos(Actor a,String codigo){return avisos.listar(1,100,false,a).items().stream().filter(x->x.codigo().equals(codigo)).toList();}
    private int entregas(ActividadNegociacionDePrueba.Caso c,String codigo){return b.jdbc.queryForObject("select count(*) from alerta where organizacion_id=? and nb_codigo=?",Integer.class,c.agente().idOrganizacion(),codigo);}
}
