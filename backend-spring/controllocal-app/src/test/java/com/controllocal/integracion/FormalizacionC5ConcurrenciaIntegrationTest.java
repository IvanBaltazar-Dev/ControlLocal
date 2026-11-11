package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.ComisionService;
import com.controllocal.service.actuacion.ActuacionesService.*;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.formalizacion.EfectosContractualesDeFormalizacion;
import com.controllocal.service.formalizacion.FormalizacionService.Hito;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.controllocal.domain.comercial.TipoHitoContractual.ESCRITURA;
import static com.controllocal.integracion.soporte.FormalizacionesDePrueba.*;

/** T4: transacciones de confirmación reales, sin dormir para fabricar el orden ni desactivar guardas. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class FormalizacionC5ConcurrenciaIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @MockitoSpyBean EfectosContractualesDeFormalizacion efectos;
    @MockitoSpyBean ComisionService comisiones;
    FormalizacionesDePrueba b;
    @BeforeEach void iniciar(TestInfo info){EvidenciaC4DePrueba.iniciar(info);b=new FormalizacionesDePrueba(contexto);}
    @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}
    FormalizacionesDePrueba.HitoRevisado listo(){
        var c=b.nuevo("VENTA",ComisionC5IntegrationTest.fija(ESCRITURA,ESCRITURA,List.of()));
        return b.listo(c,ESCRITURA,hoy(),b.venta(c,new BigDecimal("100000"),"PEN",null),false);
    }
    record Intento(Resultado resultado,RuntimeException fallo){boolean exito(){return resultado!=null&&"EXITO".equals(resultado.estado());}}
    Intento intentar(FormalizacionesDePrueba.Caso c,Propuesta p,Confirmacion d){
        try{return new Intento(b.actuaciones.confirmar(c.broker(),p.referencia(),b.sesiones.de(c.broker()),d,procedencia()),null);}
        catch(RuntimeException fallo){return new Intento(null,fallo);}
    }
    List<Intento> competir(Callable<Intento> a,Callable<Intento> c)throws Exception{
        var listas=new CountDownLatch(2);var salir=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var f1=pool.submit(()->EvidenciaC4DePrueba.enHilo(()->{listas.countDown();assertTrue(salir.await(20,TimeUnit.SECONDS));return a.call();}));
            var f2=pool.submit(()->EvidenciaC4DePrueba.enHilo(()->{listas.countDown();assertTrue(salir.await(20,TimeUnit.SECONDS));return c.call();}));
            assertTrue(listas.await(20,TimeUnit.SECONDS));salir.countDown();return List.of(f1.get(60,TimeUnit.SECONDS),f2.get(60,TimeUnit.SECONDS));
        }finally{salir.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }
    void exigirResultadosConocidos(List<Intento> intentos){
        for(var i:intentos){
            if(i.fallo()!=null)assertTrue(i.fallo() instanceof ConflictoException||i.fallo() instanceof ReglaNegocioException,
                    ()->"Un fallo técnico no acredita la guarda concurrente: "+i.fallo().getClass());
            else assertTrue(Set.of("EXITO","RECHAZADO").contains(i.resultado().estado()),()->"Resultado no conciliado: "+i.resultado().estado());
        }
    }

    @Test void confirmarDosVecesEnTransaccionesConcurrentesConservaUnHechoYUnCierre()throws Exception{
        var r=listo();var p1=b.prepararConfirmacion(r);var p2=b.prepararConfirmacion(r);
        var d1=b.humanas.revisar(p1,r.caso().broker(),b.sesiones.de(r.caso().broker()));
        var d2=b.humanas.revisar(p2,r.caso().broker(),b.sesiones.de(r.caso().broker()));
        var resultados=competir(()->intentar(r.caso(),p1,d1),()->intentar(r.caso(),p2,d2));exigirResultadosConocidos(resultados);
        assertTrue(resultados.stream().anyMatch(Intento::exito));
        assertEquals(1,b.jdbc.queryForObject("select count(*) from revision_hito_contractual where organizacion_id=? and id_hito=? and tipo_registro='CONFIRMACION'",Integer.class,r.caso().agente().idOrganizacion(),r.informado().id()));
        assertEquals(1,b.jdbc.queryForObject("select count(*) from contrato_compraventa where organizacion_id=? and id_captacion=?",Integer.class,r.caso().agente().idOrganizacion(),r.caso().encargo()));
        var f=b.comision(r.caso(),r.expediente());assertNotNull(f.id());assertEquals("DEVENGADO",f.devengo().estado());
        if(!p1.referencia().equals(p2.referencia()))assertEquals(1,resultados.stream().filter(Intento::exito).count());
    }
    @Test void dosCobrosConcurrentesNoSobrepasanElDevengado()throws Exception{
        var r=listo();b.confirmar(r);var p1=b.prepararCobro(r.caso(),r.expediente(),new BigDecimal("9000"),hoy(),clave());
        var p2=b.prepararCobro(r.caso(),r.expediente(),new BigDecimal("9000"),hoy(),clave());assertNotEquals(p1.referencia(),p2.referencia());
        var d1=b.humanas.revisar(p1,r.caso().broker(),b.sesiones.de(r.caso().broker()));var d2=b.humanas.revisar(p2,r.caso().broker(),b.sesiones.de(r.caso().broker()));
        var resultados=competir(()->intentar(r.caso(),p1,d1),()->intentar(r.caso(),p2,d2));exigirResultadosConocidos(resultados);assertEquals(1,resultados.stream().filter(Intento::exito).count());
        var f=b.comision(r.caso(),r.expediente());assertEquals(1,f.cobro().ingresosRecibidos().size());assertEquals("PEN",f.cobro().ingresosRecibidos().getFirst().moneda());
        assertEquals(0,new BigDecimal("9000").compareTo(f.cobro().ingresosRecibidos().getFirst().importe()));
        assertEquals(0,new BigDecimal("6000").compareTo(f.cobro().saldoInformativoSobreCalculo().importe()));
        assertEquals(1,b.jdbc.queryForObject("select count(*) from comision_movimiento where organizacion_id=? and id_comision_liquidacion=?",Integer.class,r.caso().agente().idOrganizacion(),f.id()));
    }
    @Test void agenteNoConfirmaLaPropuestaDeDecisionDelBroker(){
        var r=listo();var p=b.prepararConfirmacion(r);var d=b.humanas.revisar(p,r.caso().broker(),b.sesiones.de(r.caso().broker()));
        assertThrows(AccesoNoAutorizadoException.class,()->b.actuaciones.confirmar(r.caso().agente(),p.referencia(),b.sesiones.de(r.caso().agente()),d,procedencia()));
        assertNull(b.jdbc.queryForObject("select id_revision_confirmada from hito_contractual where organizacion_id=? and id_hito=?",Long.class,r.caso().agente().idOrganizacion(),r.informado().id()));
        assertNotNull(b.confirmar(r).revisionConfirmada());
    }
    @Test void reprogramarYConcluirConcurrentesNoPisanLaVersionDeLaOtraActuacion()throws Exception{
        var r=listo();b.confirmar(r);
        var p1=b.prepararReprogramacion(r.caso(),r.expediente(),hoy().plusDays(5),"Fecha acordada TEST");
        var p2=b.prepararConclusion(r.caso(),r.expediente(),"Concluir gestión TEST");
        var d1=b.humanas.revisar(p1,r.caso().broker(),b.sesiones.de(r.caso().broker()));var d2=b.humanas.revisar(p2,r.caso().broker(),b.sesiones.de(r.caso().broker()));
        var resultados=competir(()->intentar(r.caso(),p1,d1),()->intentar(r.caso(),p2,d2));exigirResultadosConocidos(resultados);
        assertEquals(1,resultados.stream().filter(Intento::exito).count());
        var f=b.comision(r.caso(),r.expediente());assertNotNull(f.gestion());
        if(!f.gestion().concluida())assertEquals(hoy().plusDays(5),f.gestion().proximaRevision());
        assertTrue(f.cobro().ingresosRecibidos().isEmpty());assertEquals(0,b.jdbc.queryForObject("select count(*) from comision_movimiento where organizacion_id=? and id_comision_liquidacion=?",Integer.class,r.caso().agente().idOrganizacion(),f.id()));
    }
    @Test void falloDelCallbackRevierteElHechoYNoCreaDinero(){
        var r=listo();var p=b.prepararConfirmacion(r);var d=b.humanas.revisar(p,r.caso().broker(),b.sesiones.de(r.caso().broker()));var alcanzado=new AtomicBoolean();
        EfectosContractualesDeFormalizacion objetivo=AopTestUtils.getUltimateTargetObject(efectos);
        doAnswer(inv->{alcanzado.set(true);throw new IllegalStateException("Fallo contractual inyectado TEST");})
                .when(objetivo).aplicar(any(),any(),any(),any(),anyString(),any(),any(),any());
        var intento=intentar(r.caso(),p,d);assertFalse(intento.exito());assertTrue(alcanzado.get(),"La prueba debe alcanzar la falla inyectada.");exigirRollback(r);
    }
    @Test void falloEconomicoPosteriorRevierteTambienElCierreContractual(){
        var r=listo();var p=b.prepararConfirmacion(r);var d=b.humanas.revisar(p,r.caso().broker(),b.sesiones.de(r.caso().broker()));var alcanzado=new AtomicBoolean();
        ComisionService objetivo=AopTestUtils.getUltimateTargetObject(comisiones);
        doAnswer(inv->{alcanzado.set(true);throw new IllegalStateException("Fallo de evaluación inyectado TEST");})
                .when(objetivo).evaluar(any(),any(),any(),any(),anyString(),any());
        var intento=intentar(r.caso(),p,d);assertFalse(intento.exito());assertTrue(alcanzado.get(),"La prueba debe alcanzar Money después del callback.");exigirRollback(r);
    }
    void exigirRollback(FormalizacionesDePrueba.HitoRevisado r){
        long org=r.caso().agente().idOrganizacion();
        assertNull(b.jdbc.queryForObject("select id_revision_confirmada from hito_contractual where organizacion_id=? and id_hito=?",Long.class,org,r.informado().id()));
        assertEquals(0,b.jdbc.queryForObject("select count(*) from revision_hito_contractual where organizacion_id=? and id_hito=? and tipo_registro in ('CONFIRMACION','RECTIFICACION')",Integer.class,org,r.informado().id()));
        assertEquals(0,b.jdbc.queryForObject("select count(*) from contrato_compraventa where organizacion_id=? and id_captacion=?",Integer.class,org,r.caso().encargo()));
        assertEquals(0,b.jdbc.queryForObject("select count(*) from comision_liquidacion where organizacion_id=?",Integer.class,org));
        assertEquals(0,b.jdbc.queryForObject("select count(*) from precio_propiedad where organizacion_id=? and id_captacion=? and hito='C'",Integer.class,org,r.caso().encargo()));
        assertEquals(0,b.jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and entidad_tipo='HITO_CONTRACTUAL' and entidad_id=? and tipo='FORMALIZACION_REGISTRADA' and carga_util::jsonb->>'actuacion'='confirmar_hito_contractual'",Integer.class,org,r.informado().id()));
    }
}
