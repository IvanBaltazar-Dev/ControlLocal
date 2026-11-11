package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.actividad.ConversionDeProspecciones;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Procedencia;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.controllocal.integracion.soporte.FormalizacionesDePrueba.*;
import static com.controllocal.service.actividad.IndicadoresIndividuales.*;
import static org.junit.jupiter.api.Assertions.*;

/** Catálogo20.9: productores Core y SQL reales; no confunde preparación con habilitación. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class OrigenConversionC7IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired ConversionDeProspecciones fuentes;
    @Autowired ProspeccionService prospecciones;
    @Autowired PlatformTransactionManager gestor;
    FormalizacionesDePrueba b;
    @BeforeEach void iniciar(TestInfo info){EvidenciaC4DePrueba.iniciar(info);b=new FormalizacionesDePrueba(contexto);}
    @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}
    ActividadNegociacionDePrueba.Caso caso(){return b.negociacion.mesa("VENTA",new BigDecimal("100000"),"PEN",false);}
    ProspeccionService.FichaProspeccion prospectar(ActividadNegociacionDePrueba.Caso c,Actor a){
        return prospecciones.registrar(new ProspeccionService.DatosProspeccion(c.propiedad(),"Gestión independiente TEST",null),a);
    }
    EncargosDePrueba banco(){return new EncargosDePrueba(b.jdbc,b.encargos,gestor,
            contexto.getBean(com.controllocal.service.archivo.AccesoArchivoService.class),
            contexto.getBean(com.controllocal.web.almacen.AlmacenDocumentos.class),b.actuaciones,
            contexto.getBean(com.controllocal.service.soporte.Documentos.class));}
    EncargosDePrueba.Caso condiciones(ActividadNegociacionDePrueba.Caso c,OperacionInmobiliaria op,LocalDate inicio,boolean exclusiva){
        return new EncargosDePrueba.Caso(c.propiedad(),op,inicio,inicio.plusMonths(6),exclusiva,
                op==OperacionInmobiliaria.VENTA?EncargosDePrueba.venta(new BigDecimal("100000"),"PEN",new BigDecimal("0.03"),"PROPIETARIO_TEST","AGENCIA_TEST"):
                EncargosDePrueba.alquiler(new BigDecimal("2500"),"PEN",BigDecimal.ONE,"PROPIETARIO_TEST","AGENCIA_TEST"),
                new EncargosDePrueba.Actuante(c.agente(),c.sesion()),
                new EncargosDePrueba.Actuante(c.equipo().broker(),b.sesiones.de(c.equipo().broker())));
    }
    void habilitar(ActividadNegociacionDePrueba.Caso c,long id,OperacionInmobiliaria op){banco().habilitarExistente(id,condiciones(c,op,hoy(),true));}
    Ventana ventana(){return new Ventana(hoy().minusDays(6),hoy(),Instant.now());}
    long prueba(long c){return b.jdbc.queryForObject("select count(*) from encargo_primera_habilitacion where id_captacion=?",Long.class,c);}
    boolean vincular(long encargo,long p,Actor a){return new TransactionTemplate(gestor).execute(t->b.encargos.vincularOrigenProspeccion(encargo,p,a));}
    long abrir(ActividadNegociacionDePrueba.Caso c,OperacionInmobiliaria op,EncargoService.OrigenProspeccion origen){
        return new TransactionTemplate(gestor).execute(t->b.encargos.abrir(new EncargoService.DatosApertura(c.propiedad(),c.agente().idRolOperativo(),
                "ORIGEN-"+UUID.randomUUID().toString().substring(0,12),op,hoy(),hoy().plusMonths(6),true,"Documento privado TEST",null,procedencia(),List.of(),origen),c.agente()).id());
    }
    @Test void prepararNoConvierteHabilitarUnaVezSiYElReintentoNoDuplica(){
        var c=caso();var p=prospectar(c,c.agente());prospecciones.marcarCaptado(p.id(),c.encargo(),null,c.agente());
        var antes=conversion(fuentes.leer(c.agente(),ventana()).hechos(),ventana());
        assertEquals(0,antes.tasa().exitos());assertEquals(1,antes.enPreparacion());assertNull(antes.tasa().porcentaje());assertEquals(0,prueba(c.encargo()));
        habilitar(c,c.encargo(),OperacionInmobiliaria.VENTA);
        var despues=conversion(fuentes.leer(c.agente(),ventana()).hechos(),ventana());
        assertEquals(1,despues.tasa().exitos());assertEquals(1,despues.tasa().evaluables());assertEquals(0,new BigDecimal("100").compareTo(despues.tasa().porcentaje()));
        var h=b.jdbc.queryForMap("select * from encargo_primera_habilitacion where id_captacion=?",c.encargo());
        assertEquals(p.id(),h.get("id_prospeccion_origen"));assertEquals(c.agente().idRolOperativo(),h.get("id_rol_responsable_prospeccion"));
        assertEquals(hoy(),((java.sql.Date)h.get("fecha_habilitacion")).toLocalDate());assertEquals(1,prueba(c.encargo()));
        long historia=b.jdbc.queryForObject("select count(*) from historial_estado where entidad_tipo='PROSPECCION' and id_entidad=?",Long.class,p.id());
        prospecciones.marcarCaptado(p.id(),c.encargo(),null,c.agente());
        assertEquals(1,prueba(c.encargo()));assertEquals(historia,b.jdbc.queryForObject("select count(*) from historial_estado where entidad_tipo='PROSPECCION' and id_entidad=?",Long.class,p.id()));
        assertEquals(despues.tasa().exitos(),conversion(fuentes.leer(c.agente(),ventana()).hechos(),ventana()).tasa().exitos());
    }
    @Test void otraProspeccionEsAntecedenteNoSeCierraNiPierdeSuSeguimiento(){
        var c=caso();var origen=prospectar(c,c.agente());var otra=prospectar(c,c.agente());
        prospecciones.marcarCaptado(origen.id(),c.encargo(),null,c.agente());
        var estadoAntes=prospecciones.obtener(otra.id(),c.agente()).estado();
        var seguimientoAntes=prospecciones.seguimientos(otra.id(),c.agente());assertFalse(seguimientoAntes.isEmpty());
        prospecciones.marcarCaptado(otra.id(),c.encargo(),null,c.agente());
        assertEquals(estadoAntes,prospecciones.obtener(otra.id(),c.agente()).estado());assertNull(prospecciones.obtener(otra.id(),c.agente()).idCaptacion());
        assertEquals(seguimientoAntes,prospecciones.seguimientos(otra.id(),c.agente()));
        assertEquals(origen.id(),b.encargos.consultar(c.encargo(),c.agente()).origen().prospeccion());
        assertEquals(1,b.jdbc.queryForObject("select count(*) from encargo_antecedente_prospeccion where id_captacion=? and id_prospeccion=?",Integer.class,c.encargo(),otra.id()));
        habilitar(c,c.encargo(),OperacionInmobiliaria.VENTA);
        var tasa=conversion(fuentes.leer(c.agente(),ventana()).hechos(),ventana());assertEquals(1,tasa.tasa().exitos());assertEquals(1,tasa.tasa().pendientes());
    }
    @Test void anaYLuisPuedenProspectarSinReservaYConservanGestionesSeparadas(){
        var c=caso();var ana=prospectar(c,c.equipo().colega());var luis=prospectar(c,c.agente());
        var seguimiento=prospecciones.seguimientos(ana.id(),c.equipo().colega());assertFalse(seguimiento.isEmpty());
        prospecciones.marcarCaptado(luis.id(),c.encargo(),null,c.agente());habilitar(c,c.encargo(),OperacionInmobiliaria.VENTA);
        assertEquals(ana.estado(),prospecciones.obtener(ana.id(),c.equipo().colega()).estado());
        assertEquals(seguimiento,prospecciones.seguimientos(ana.id(),c.equipo().colega()));
        assertEquals(0,conversion(fuentes.leer(c.equipo().colega(),ventana()).hechos(),ventana()).tasa().exitos());
        assertEquals(1,conversion(fuentes.leer(c.agente(),ventana()).hechos(),ventana()).tasa().exitos());
        assertThrows(NoEncontradoException.class,()->vincular(c.encargo(),ana.id(),c.agente()));
    }
    @Test void ventaYAlquilerDeLaMismaProspeccionSoloConviertenUnaVez(){
        var c=caso();var p=prospectar(c,c.agente());prospecciones.marcarCaptado(p.id(),c.encargo(),null,c.agente());habilitar(c,c.encargo(),OperacionInmobiliaria.VENTA);
        long alquiler=abrir(c,OperacionInmobiliaria.ALQUILER,new EncargoService.OrigenProspeccion(EncargoService.TipoOrigenProspeccion.PROSPECCION,p.id()));
        habilitar(c,alquiler,OperacionInmobiliaria.ALQUILER);
        var hechos=fuentes.leer(c.agente(),ventana()).hechos();assertEquals(1,hechos.size());assertEquals(1,hechos.getFirst().habilitaciones().size());
        assertEquals(1,conversion(hechos,ventana()).tasa().exitos());assertEquals(2,b.jdbc.queryForObject("select count(*) from encargo_primera_habilitacion where organizacion_id=?",Integer.class,c.agente().idOrganizacion()));
    }
    @Test void origenDirectoEsExplicitoYElHistoricoDesconocidoNoSeInfiere(){
        var c=caso();assertEquals(EncargoService.TipoOrigenProspeccion.DESCONOCIDO,b.encargos.consultar(c.encargo(),c.agente()).origen().tipo());
        long alquiler=abrir(c,OperacionInmobiliaria.ALQUILER,new EncargoService.OrigenProspeccion(EncargoService.TipoOrigenProspeccion.DIRECTO,null));
        assertEquals(EncargoService.TipoOrigenProspeccion.DIRECTO,b.encargos.consultar(alquiler,c.agente()).origen().tipo());
        var p=prospectar(c,c.agente());assertFalse(vincular(alquiler,p.id(),c.agente()));
        assertEquals(EncargoService.TipoOrigenProspeccion.DIRECTO,b.encargos.consultar(alquiler,c.agente()).origen().tipo());
        assertTrue(fuentes.leer(c.agente(),ventana()).hechos().getFirst().habilitaciones().isEmpty());
    }
    @Test void historiaDeHabilitacionYAntecedentesNoSeSobrescribe(){
        var c=caso();var p=prospectar(c,c.agente());var otra=prospectar(c,c.agente());vincular(c.encargo(),p.id(),c.agente());vincular(c.encargo(),otra.id(),c.agente());habilitar(c,c.encargo(),OperacionInmobiliaria.VENTA);
        assertThrows(DataAccessException.class,()->b.jdbc.update("delete from encargo_primera_habilitacion where id_captacion=?",c.encargo()));
        assertThrows(DataAccessException.class,()->b.jdbc.update("update encargo_primera_habilitacion set fecha_habilitacion=current_date-1 where id_captacion=?",c.encargo()));
        assertThrows(DataAccessException.class,()->b.jdbc.update("delete from encargo_antecedente_prospeccion where id_captacion=?",c.encargo()));
        assertEquals(1,prueba(c.encargo()));
    }
    @Test void aprobacionConInicioFuturoNoRegistraUnaHabilitacionYaOcurrida(){
        var c=caso();var p=prospectar(c,c.agente());prospecciones.marcarCaptado(p.id(),c.encargo(),null,c.agente());
        var presentada=banco().presentarExistente(c.encargo(),condiciones(c,OperacionInmobiliaria.VENTA,hoy().plusDays(1),true));
        var decisor=c.equipo().broker();var antes=b.encargos.consultar(c.encargo(),decisor);var firma=presentada.peticion().firma();
        var d=new EncargoService.Decision("APROBAR",presentada.peticion().version(),antes.version(),"Instrumento TEST con inicio futuro",
                new EncargoService.RevisionManuscrita(firma.revisionPresentacion(),firma.huellaManifestacion(),true));
        var propuesta=b.actuaciones.prepararDecisionEncargo(decisor,presentada.peticion().id(),d,b.sesiones.de(decisor),procedencia());
        b.humanas.confirmar(propuesta,decisor,b.sesiones.de(decisor),EncargoService.Peticion.class);
        assertEquals("APROBADO",b.encargos.consultar(c.encargo(),c.agente()).estado());
        assertFalse(b.encargos.consultarVigencia(c.encargo(),hoy(),c.agente()).habilitado());assertEquals(0,prueba(c.encargo()));
        var m=conversion(fuentes.leer(c.agente(),ventana()).hechos(),ventana());assertEquals(0,m.tasa().exitos());assertEquals(0,m.enPreparacion());assertEquals(1,m.otrasPendientes());
    }
    @Test void dosOrigenesConcurrentesConservanUnoYElOtroComoAntecedente()throws Exception{
        var c=caso();var p=prospectar(c,c.agente());var q=prospectar(c,c.agente());
        var inicio=new CountDownLatch(1);var listos=new CountDownLatch(2);var pool=Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->EvidenciaC4DePrueba.enHilo(()->{listos.countDown();assertTrue(inicio.await(10,TimeUnit.SECONDS));return vincular(c.encargo(),p.id(),c.agente());}));
            var z=pool.submit(()->EvidenciaC4DePrueba.enHilo(()->{listos.countDown();assertTrue(inicio.await(10,TimeUnit.SECONDS));return vincular(c.encargo(),q.id(),c.agente());}));
            assertTrue(listos.await(10,TimeUnit.SECONDS));inicio.countDown();Boolean ra=resultado(a),rz=resultado(z);
            assertEquals(1,Arrays.asList(ra,rz).stream().filter(Boolean.TRUE::equals).count());
            // CAS conserva el conflicto concurrente; el reintento explícito vincula el antecedente, nunca otro origen.
            if(ra==null)assertFalse(vincular(c.encargo(),p.id(),c.agente()));
            if(rz==null)assertFalse(vincular(c.encargo(),q.id(),c.agente()));
            assertEquals(1,b.jdbc.queryForObject("select count(*) from encargo_antecedente_prospeccion where id_captacion=?",Integer.class,c.encargo()));
            assertTrue(Set.of(p.id(),q.id()).contains(b.encargos.consultar(c.encargo(),c.agente()).origen().prospeccion()));
        }finally{inicio.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }
    Boolean resultado(Future<Boolean> futuro)throws Exception{
        try{return futuro.get(60,TimeUnit.SECONDS);}catch(ExecutionException ex){assertInstanceOf(ConflictoException.class,ex.getCause());return null;}
    }
    @Test void dosAperturasConcurrentesMismaOficinaYOperacionNoCreanDuplicadoVivo()throws Exception{
        var c=caso();var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Boolean> accion=()->EvidenciaC4DePrueba.enHilo(()->{assertTrue(inicio.await(10,TimeUnit.SECONDS));try{abrir(c,OperacionInmobiliaria.ALQUILER,null);return true;}catch(ReglaNegocioException ex){assertTrue(ex.getMessage().contains("Ya existe un Encargo"));return false;}});
        try{var a=pool.submit(accion);var z=pool.submit(accion);inicio.countDown();var r=List.of(a.get(60,TimeUnit.SECONDS),z.get(60,TimeUnit.SECONDS));assertEquals(1,r.stream().filter(Boolean::booleanValue).count());
            assertEquals(1,b.jdbc.queryForObject("select count(*) from captacion where organizacion_id=? and id_propiedad=? and motivo_operacion='A' and estado in ('P','A')",Integer.class,c.agente().idOrganizacion(),c.propiedad()));
        }finally{inicio.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }
    @Test void orgAjenaYAdminNoLeenNiVinculanOrigen(){
        var c=caso();var ajena=caso();var p=prospectar(c,c.agente());
        assertThrows(NoEncontradoException.class,()->vincular(ajena.encargo(),p.id(),ajena.agente()));
        assertThrows(AccesoNoAutorizadoException.class,()->fuentes.leer(c.equipo().admin(),ventana()));
        assertThrows(NoEncontradoException.class,()->b.encargos.consultar(c.encargo(),ajena.agente()));
        assertEquals(EncargoService.TipoOrigenProspeccion.DESCONOCIDO,b.encargos.consultar(ajena.encargo(),ajena.agente()).origen().tipo());
    }
}
