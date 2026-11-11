package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.comercial.TipoHitoContractual;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.encargo.PactosDeEncargo.*;
import com.controllocal.service.formalizacion.FormalizacionService.*;
import com.controllocal.service.formalizacion.HitosConfirmados.ReferenciaContextoEconomico;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fuentes reales C2/C4/C5 y consulta D5; no fabrica fechas de vencimiento ni dinero. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class RadarFuentesC7IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired PendientesComercialesRadar radar;
    private FormalizacionesDePrueba b;
    @BeforeEach void iniciar(TestInfo info){EvidenciaC4DePrueba.iniciar(info);b=new FormalizacionesDePrueba(contexto);}
    @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}

    @Test void presentacionEsRevisionSinFechaNoAtrasoNiTareaDelAgente(){
        var c=caso();var e=b.presentar(c,false);
        var a=motivo(c.broker(),"EXPEDIENTE_PRESENTADO").getFirst();
        assertEquals(e.id(),a.referencia());assertEquals(PoliticaDeDespacho.Banda.R3,a.orden().banda());
        assertFalse(a.orden().plazoVencido());assertNull(a.fechaActuacion());assertEquals(a,motivo(c.broker(),"EXPEDIENTE_PRESENTADO").getFirst());
        assertTrue(motivo(c.agente(),"EXPEDIENTE_PRESENTADO").isEmpty());
        var q=new PoliticaDeDespacho.Consulta(PoliticaDeDespacho.Vista.PROCESO,"ENCARGO",c.encargo());
        assertTrue(radar.de(q,c.broker()).contains(a));
        assertTrue(radar.de(new PoliticaDeDespacho.Consulta(PoliticaDeDespacho.Vista.VENCIDAS,null,null),c.broker()).isEmpty());
    }
    @Test void observarTrasladaLaActuacionAlAgenteYLaNuevaPresentacionRetiraLaAnterior(){
        var c=caso();var e=b.presentar(c,false);var actual=b.formalizacion.consultar(e.id(),c.broker());
        var d=new DatosRevision(e.id(),actual.version(),"OBSERVAR","Corregir antecedente TEST",actual.documentos(),true,true,null,null);
        var p=b.actuaciones.prepararEvaluacionExpediente(c.broker(),d,b.sesiones.de(c.broker()),Procedencia.deLaPantalla());
        b.humanas.confirmar(p,c.broker(),b.sesiones.de(c.broker()),Expediente.class);
        assertTrue(motivo(c.broker(),"EXPEDIENTE_PRESENTADO").isEmpty());
        assertEquals(1,motivo(c.agente(),"OBSERVACIONES_EXPEDIENTE").size());
        actual=b.formalizacion.consultar(e.id(),c.agente());
        var reenviar=new DatosPresentacion(c.oportunidad(),b.oportunidades.obtener(c.oportunidad(),c.agente()).version(),
            c.encargo(),b.encargos.consultar(c.encargo(),c.agente()).version(),null,null,c.pacto(),List.of(c.documento()),e.id(),actual.version(),null);
        var propuesta=b.actuaciones.prepararPresentacionExpediente(c.agente(),reenviar,b.sesiones.de(c.agente()),Procedencia.deLaPantalla());
        b.humanas.confirmar(propuesta,c.agente(),b.sesiones.de(c.agente()),Expediente.class);
        assertTrue(motivo(c.agente(),"OBSERVACIONES_EXPEDIENTE").isEmpty());
        assertEquals(1,motivo(c.broker(),"EXPEDIENTE_PRESENTADO").size());
    }
    @Test void propuestaSuperadaNoSiguePidiendoRespuestaNiCambiaPrecioAutorizado(){
        var c=b.negociacion.mesa("VENTA",new BigDecimal("100000"),"PEN",true);
        var f=b.negociacion.presentar(c,new BigDecimal("95000"));assertEquals(f.id(),motivo(c.agente(),"OFERTA_PENDIENTE").getFirst().referencia());
        var contra=b.negociacion.contraofertar(c,f,new BigDecimal("98000"),"P");
        var actos=motivo(c.agente(),"OFERTA_PENDIENTE");assertEquals(1,actos.size());assertEquals(contra.id(),actos.getFirst().referencia());
        assertEquals(0,new BigDecimal("100000").compareTo(b.encargos.consultar(c.encargo(),c.agente()).condiciones().precioPedido()));
    }
    @Test void hitoInformadoNoEsCobroYConfirmarloRetiraSoloSuRevision(){
        var c=caso();var r=b.listo(c,TipoHitoContractual.ESCRITURA,FormalizacionesDePrueba.hoy(),b.venta(c,new BigDecimal("100000"),"PEN",null),false);
        assertEquals(1,motivo(c.broker(),"HITO_INFORMADO").size());assertTrue(motivo(c.broker(),"SALDO_EXIGIBLE_PENDIENTE").isEmpty());
        b.confirmar(r);assertTrue(motivo(c.broker(),"HITO_INFORMADO").isEmpty());
        var saldo=motivo(c.broker(),"SALDO_EXIGIBLE_PENDIENTE").getFirst();assertTrue(saldo.accionable());
        var f=b.comision(c,r.expediente());assertTrue(f.cobro().ingresosRecibidos().isEmpty());
        b.cobrar(c,b.prepararCobro(c,r.expediente(),new BigDecimal("500"),FormalizacionesDePrueba.hoy(),FormalizacionesDePrueba.clave()));
        assertEquals(saldo.id(),motivo(c.broker(),"SALDO_EXIGIBLE_PENDIENTE").getFirst().id());
        f=b.comision(c,r.expediente());
        var p=b.actuaciones.prepararConclusionCobro(c.broker(),new ComisionService.DatosConclusion(f.id(),f.version(),"Gestión concluida con importe recibido TEST"),b.sesiones.de(c.broker()),Procedencia.deLaPantalla());
        b.humanas.confirmar(p,c.broker(),b.sesiones.de(c.broker()),ComisionService.ResultadoGestion.class);
        assertTrue(motivo(c.broker(),"SALDO_EXIGIBLE_PENDIENTE").isEmpty());
        assertEquals(new BigDecimal("500.00"),b.comision(c,r.expediente()).cobro().ingresosRecibidos().getFirst().importe());
    }
    @Test void gobiernoYOrganizacionAjenaNoRecibenHechosNiConteosDelExpediente(){
        var c=caso();b.presentar(c,false);var otro=caso();
        assertTrue(radar.de(PoliticaDeDespacho.Consulta.todas(),c.comercial().equipo().admin()).isEmpty());
        assertTrue(motivo(otro.broker(),"EXPEDIENTE_PRESENTADO").isEmpty());
        assertTrue(radar.de(new PoliticaDeDespacho.Consulta(PoliticaDeDespacho.Vista.PROCESO,"ENCARGO",c.encargo()),otro.broker()).isEmpty());
        assertThrows(com.controllocal.service.excepcion.AccesoNoAutorizadoException.class,()->b.comisiones.gestionParaRadar(c.pacto(),new ReferenciaContextoEconomico(1),c.agente()));
    }
    @Test void consultasConcurrentesConservanIdentidadFuentesYOrdenSinCrearTareas()throws Exception{
        var c=caso();b.presentar(c,false);var hilos=Executors.newFixedThreadPool(2);var inicio=new CountDownLatch(1);
        try{var x=hilos.submit(()->{inicio.await();return motivo(c.broker(),"EXPEDIENTE_PRESENTADO");});var y=hilos.submit(()->{inicio.await();return motivo(c.broker(),"EXPEDIENTE_PRESENTADO");});inicio.countDown();assertEquals(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS));}
        finally{hilos.shutdownNow();}
        assertEquals(0,b.jdbc.queryForObject("select count(*) from tarea where organizacion_id=?",Integer.class,c.broker().idOrganizacion()));
    }
    private List<PendientesComercialesRadar.Actuacion> motivo(Actor actor,String motivo){return radar.de(PoliticaDeDespacho.Consulta.todas(),actor).stream().filter(a->a.motivo().equals(motivo)).toList();}
    private FormalizacionesDePrueba.Caso caso(){
        var ejecucion=new EjecucionPacto(new CondicionDevengo(TipoHitoContractual.ESCRITURA),new CondicionExigibilidad(TipoHitoContractual.ESCRITURA,null,null,null,null));
        return b.nuevo("VENTA",new EncargoService.Condiciones(new BigDecimal("100000"),"PEN",null,"F","N",new BigDecimal("15000"),"PEN","N",null,"ESCRITURA","PROPIETARIO_TEST","AGENCIA_TEST","ESCRITURA","Pacto estructurado TEST",List.of(),null,ejecucion));
    }
}
