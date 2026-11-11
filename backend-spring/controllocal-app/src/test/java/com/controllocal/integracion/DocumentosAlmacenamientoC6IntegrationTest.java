package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.encargo.DocumentosEncargo;
import com.controllocal.service.planes.ClasificadorDocumentosEncargoC6;
import com.controllocal.service.planes.UsoAlmacenamientoB0;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.web.almacen.*;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PDF real y Core/B0/journal/SQL reales. Almacén TEST sin red; no prueba R2, PSP ni IA. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class DocumentosAlmacenamientoC6IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired EncargoService encargos;
    @Autowired ContextosService contextos;
    @Autowired MotorDeCaptura captura;
    @Autowired ClasificadorDocumentosEncargoC6 clasificador;
    @Autowired UsoAlmacenamientoB0 uso;
    @MockitoBean AlmacenDocumentos almacen;
    private final Map<String,byte[]> objetos=new ConcurrentHashMap<>();
    private final AtomicInteger puts=new AtomicInteger();
    private Actor actor;
    private ContextoSesion sid;
    private TransactionTemplate tx;
    @BeforeEach void iniciar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);
        actor=EspacioIndividualB0DePrueba.crear(jdbc,tx);sid=ContextoSesion.de(SesionesDePrueba.abrir(jdbc,contextos,actor).acceso());
        when(almacen.proveedor()).thenReturn("TEST");when(almacen.identidadTecnica()).thenReturn(AlmacenDocumentos.huellaIdentidad("TEST","documentos-"+UUID.randomUUID()));
        when(almacen.abrir(anyString())).thenAnswer(i->{byte[] b=objetos.get((String)i.getArgument(0));return b==null?Optional.empty():Optional.of(new AlmacenDocumentos.ArchivoDescargado(b.clone(),"encargo.pdf","application/pdf"));});
        doAnswer(i->{objetos.put(i.getArgument(0),((byte[])i.getArgument(1)).clone());puts.incrementAndGet();return null;}).when(almacen).guardarEnClave(anyString(),any(),anyString());
        doAnswer(i->{objetos.remove((String)i.getArgument(0));return null;}).when(almacen).eliminar(anyString());
    }
    @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}
    private EncargoService.Preparacion parcial(){return new EncargoService.Preparacion(null,null,null,null,List.of(),null,List.of(),null,null,null,null,"ENCARGO_MVP_V1",List.of());}
    private EncargoService.Documento preparar(String clave){return encargos.prepararDocumento(parcial(),actor,sid,clave,Procedencia.deLaPantalla());}
    private int cuenta(String tabla){return jdbc.queryForObject("SELECT count(*) FROM "+tabla+" WHERE organizacion_id=?",Integer.class,actor.idOrganizacion());}
    private long contador(String columna){return jdbc.queryForObject("SELECT coalesce((SELECT "+columna+" FROM b0_uso_almacenamiento WHERE organizacion_id=?),0)",Long.class,actor.idOrganizacion());}

    @Test void preparacionParcialEntregaPdfRealYCuentaUnaVezSinEncargoFicticio()throws Exception{
        String clave="DOC-C6-"+UUID.randomUUID();var d=preparar(clave);assertNull(d.encargo());assertFalse(d.faltantes().isEmpty());
        assertEquals(1,puts.get());assertEquals(1,cuenta("documento_encargo"));assertEquals(0,cuenta("captacion"));assertEquals(0,cuenta("propiedad"));
        var datos=objetos.values().iterator().next();try(var pdf=Loader.loadPDF(datos)){assertTrue(pdf.getNumberOfPages()>0);}
        assertEquals(datos.length,contador("confirmados"));assertEquals(0,contador("reservados"));
        assertEquals(d,preparar(clave));assertEquals(1,puts.get());assertEquals(1,cuenta("b0_objeto_almacenamiento"));
        assertEquals("VINCULADO",jdbc.queryForObject("SELECT estado FROM intento_salida_binaria WHERE organizacion_id=?",String.class,actor.idOrganizacion()));
    }
    @Test void rollbackNoConservaDocumentoNiCuotaYVerificaElRetiroDelBinario(){
        tx.executeWithoutResult(t->{preparar("DOC-ROLLBACK-"+UUID.randomUUID());t.setRollbackOnly();});
        assertEquals(1,puts.get());assertEquals(0,cuenta("documento_encargo"));assertEquals(0,cuenta("archivo_encargo"));
        assertTrue(objetos.isEmpty());assertEquals(0,contador("confirmados"));assertEquals(0,contador("reservados"));
        assertEquals("RETIRADO_VERIFICADO",jdbc.queryForObject("SELECT estado FROM intento_salida_binaria WHERE organizacion_id=?",String.class,actor.idOrganizacion()));
    }
    @Test void empresaSinFuenteDePlanNoObtieneQuotaFreeNiAlmacenaElDocumento(){
        var origenes=new OrigenesDePrueba(jdbc,gestor,captura,contextos);actor=origenes.empresa().agente();sid=origenes.sesion(actor);
        assertThrows(CapacidadDeshabilitadaException.class,()->preparar("SIN-PLAN-"+UUID.randomUUID()));
        assertEquals(0,puts.get());assertEquals(0,cuenta("documento_encargo"));assertEquals(0,contador("confirmados"));
    }
    @Test void unaReferenciaDeDocumentoNoConcedeLecturaEntreOrganizaciones(){
        var d=preparar("DOC-PRIVADO-"+UUID.randomUUID());var ajeno=EspacioIndividualB0DePrueba.crear(jdbc,tx);
        assertThrows(NoEncontradoException.class,()->encargos.consultarDocumento(d.id(),ajeno));assertEquals(1,puts.get());
    }
    @Test void cerrarLaSesionImpidePrepararYAlmacenarOtroDocumento(){
        contextos.cerrar(sid.referenciaSesion(),sid.revisionContexto());
        assertThrows(AccesoNoAutorizadoException.class,()->preparar("DOC-SID-CERRADO-"+UUID.randomUUID()));
        assertEquals(0,puts.get());assertEquals(0,cuenta("documento_encargo"));
        assertEquals(0,contador("confirmados"));assertEquals(0,contador("reservados"));
    }
    @Test void contextoDocumentalObsoletoNoReservaOtraCarga(){
        var d=preparar("DOC-FUENTE-"+UUID.randomUUID());
        var origen=new DocumentosEncargo.Origen(UUID.randomUUID(),actor,sid,"DOCUMENTO_ENCARGO_C6",d.referencia().toString(),"PDF_ENCARGO",d.huella());
        var recurso=tx.execute(t->clasificador.preparar(origen));long confirmados=contador("confirmados");
        // Sabotaje TEST de revisión en otra transacción; no altera el material histórico.
        assertEquals(1,jdbc.update("UPDATE documento_encargo SET version=version+1 WHERE organizacion_id=? AND id_documento_encargo=?",actor.idOrganizacion(),d.id()));
        var solicitud=new UsoAlmacenamientoB0.Solicitud(UUID.randomUUID(),recurso,50,"b".repeat(64),null);
        assertThrows(ConflictoException.class,()->uso.reservar(solicitud,actor,sid));
        assertEquals(1,puts.get());assertEquals(confirmados,contador("confirmados"));assertEquals(0,contador("reservados"));
    }
}
