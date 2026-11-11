package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.actividad.DesenlacesDeOportunidad;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static com.controllocal.domain.comercial.TipoHitoContractual.*;
import static com.controllocal.integracion.soporte.FormalizacionesDePrueba.*;
import static com.controllocal.service.actividad.IndicadoresIndividuales.*;
import static org.junit.jupiter.api.Assertions.*;

/** Nuevos recorridos C7 sobre C4/C5 reales; sin fabricar cierres, fechas o permisos por SQL. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class DesenlacesC7IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired DesenlacesDeOportunidad fuentes;
    @Autowired IndicadorService indicadores;
    @Autowired PlatformTransactionManager gestor;
    FormalizacionesDePrueba b;
    @BeforeEach void iniciar(TestInfo info){EvidenciaC4DePrueba.iniciar(info);b=new FormalizacionesDePrueba(contexto);}
    @AfterEach void finalizar(){EvidenciaC4DePrueba.limpiar();}
    Ventana ventana(){return new Ventana(hoy().minusDays(6),hoy(),Instant.now());}
    FormalizacionesDePrueba.Caso venta(){return b.nuevo("VENTA",ComisionC5IntegrationTest.fija(ESCRITURA,ESCRITURA,List.of()));}
    @Test void escrituraConfirmadaEsUnDesenlaceConFuenteYAtribucionPropias(){
        var c=venta();var previo=cierres(fuentes.leer(c.agente(),Instant.now()),ventana()).get("VENTA");
        assertEquals(1,previo.pendientes());assertEquals(0,previo.evaluables());assertNull(previo.porcentaje());
        var r=b.listo(c,ESCRITURA,hoy(),b.venta(c,new BigDecimal("100000"),"PEN",null),false);var h=b.confirmar(r);
        var hechos=fuentes.leer(c.agente(),Instant.now());assertEquals(1,hechos.size());var hecho=hechos.getFirst();
        assertTrue(hecho.favorableConfirmadoC5());assertEquals(h.revisionConfirmada(),hecho.revisionHito());
        assertEquals(c.agente().idRolOperativo(),hecho.fuente().responsableHistorico());assertEquals(hoy(),hecho.fechaResolucion());assertNotNull(hecho.historial());
        var audit=b.jdbc.queryForMap("select id_rol_responsable,id_revision_hito,fecha_efectiva from historial_estado where organizacion_id=? and id_historial=?",c.agente().idOrganizacion(),hecho.historial());
        assertEquals(c.agente().idRolOperativo(),audit.get("id_rol_responsable"));assertEquals(h.revisionConfirmada(),audit.get("id_revision_hito"));
        var resultado=indicadores.resumen("7d",c.agente()).efectividadDeCierre();assertEquals(1,resultado.get("VENTA").exitos());
        assertEquals(1,resultado.get("VENTA").evaluables());assertEquals(0,new BigDecimal("100").compareTo(resultado.get("VENTA").porcentaje()));assertNull(resultado.get("ALQUILER").porcentaje());
        assertEquals(0,b.jdbc.queryForObject("select count(*) from comision_movimiento where organizacion_id=?",Integer.class,c.agente().idOrganizacion()));
    }
    @Test void alquilerConfirmadoNoSeMezclaConVenta(){
        var condiciones=new com.controllocal.service.encargo.EncargoService.Condiciones(new BigDecimal("2500"),"PEN","MENSUAL","F","N",new BigDecimal("1000"),"PEN","N",null,
                CONTRATO_ALQUILER.name(),"PROPIETARIO_TEST","AGENCIA_TEST",CONTRATO_ALQUILER.name(),"Pacto TEST",List.of(),null,
                ComisionC5IntegrationTest.ejecucion(CONTRATO_ALQUILER,new com.controllocal.service.encargo.PactosDeEncargo.CondicionExigibilidad(CONTRATO_ALQUILER,null,null,null,null)));
        var c=b.nuevo("ALQUILER",condiciones);var r=b.listo(c,CONTRATO_ALQUILER,hoy(),b.alquiler(c,new BigDecimal("2500"),"PEN"),false);b.confirmar(r);
        var tasas=cierres(fuentes.leer(c.agente(),Instant.now()),ventana());assertEquals(1,tasas.get("ALQUILER").exitos());assertEquals(1,tasas.get("ALQUILER").evaluables());assertEquals(0,tasas.get("VENTA").evaluables());assertNull(tasas.get("VENTA").porcentaje());
    }
    @Test void rectificacionC5ConservaUnDesenlaceYLaFuenteConocidaEnCadaCorte(){
        var c=venta();var r=b.listo(c,ESCRITURA,hoy(),b.venta(c,new BigDecimal("100000"),"PEN",null),false);var firmado=b.confirmar(r);
        var corteAnterior=Instant.now();var anterior=fuentes.leer(c.agente(),corteAnterior).getFirst();
        var acuerdo=b.venta(c,new BigDecimal("105000"),"PEN",null);var revisado=b.aprobar(c,r.expediente(),acuerdo);
        var exp=b.formalizacion.consultar(revisado.id(),c.broker());
        var datos=new com.controllocal.service.formalizacion.FormalizacionService.DatosRectificacionHito(
                new com.controllocal.service.formalizacion.HitosConfirmados.ReferenciaHito(firmado.id(),firmado.revisionConfirmada()),
                firmado.version(),exp.version(),hoy(),"Rectificación acreditada TEST",acuerdo.documentos(),acuerdo);
        var propuesta=b.actuaciones.prepararRectificacionHito(c.broker(),datos,b.sesiones.de(c.broker()),procedencia());
        var rectificada=b.humanas.confirmar(propuesta,c.broker(),b.sesiones.de(c.broker()),com.controllocal.service.formalizacion.FormalizacionService.Hito.class).datos();
        var actuales=fuentes.leer(c.agente(),Instant.now());assertEquals(1,actuales.size());var actual=actuales.getFirst();
        assertEquals(rectificada.revisionConfirmada(),actual.revisionHito());assertNotEquals(anterior.revisionHito(),actual.revisionHito());
        assertEquals(anterior.historial(),actual.historial());assertEquals(anterior.fuente().responsableHistorico(),actual.fuente().responsableHistorico());
        assertEquals(firmado.revisionConfirmada(),fuentes.leer(c.agente(),corteAnterior).getFirst().revisionHito());
        assertEquals(1,b.jdbc.queryForObject("select count(*) from contrato_compraventa where organizacion_id=?",Integer.class,c.agente().idOrganizacion()));
        assertEquals(1,b.jdbc.queryForObject("select count(*) from historial_estado where organizacion_id=? and entidad_tipo='OPORTUNIDAD' and id_entidad=? and estado_nuevo='F'",Integer.class,c.agente().idOrganizacion(),c.oportunidad()));
        assertEquals(0,b.jdbc.queryForObject("select count(*) from comision_movimiento where organizacion_id=?",Integer.class,c.agente().idOrganizacion()));
    }
    @Test void noContinuidadConservaSuFechaEfectivaSinConvertirseEnCierreFavorable(){
        var c=venta();var o=b.oportunidades.obtener(c.oportunidad(),c.agente());
        new TransactionTemplate(gestor).execute(t->b.oportunidades.noContinuidad(new OportunidadService.NoContinuidad(o.id(),o.version(),"S","Desistimiento TEST",hoy().minusDays(1)),
                c.agente(),b.sesiones.de(c.agente()),clave(),procedencia()));
        var hechos=fuentes.leer(c.agente(),Instant.now());assertEquals(1,hechos.size());assertTrue(hechos.getFirst().resuelta());assertFalse(hechos.getFirst().favorableConfirmadoC5());
        assertEquals(hoy().minusDays(1),hechos.getFirst().fechaResolucion());assertNull(hechos.getFirst().revisionHito());
        var t=cierres(hechos,ventana()).get("VENTA");assertEquals(1,t.evaluables());assertEquals(0,t.porcentaje().compareTo(BigDecimal.ZERO));
    }
    @Test void aislamientoYAdministracionNoOtorganLecturaComercial(){
        var c=venta();var otro=venta();var r=b.listo(c,ESCRITURA,hoy(),b.venta(c,new BigDecimal("100000"),"PEN",null),false);b.confirmar(r);
        var ajeno=fuentes.leer(otro.agente(),Instant.now());assertEquals(1,ajeno.size());assertEquals(otro.oportunidad(),ajeno.getFirst().fuente().id());assertFalse(ajeno.getFirst().resuelta());
        assertThrows(AccesoNoAutorizadoException.class,()->fuentes.leer(c.comercial().equipo().admin(),Instant.now()));
        assertEquals(1,fuentes.leer(c.broker(),Instant.now()).size());assertEquals(1,fuentes.leer(otro.broker(),Instant.now()).size());
    }
}
