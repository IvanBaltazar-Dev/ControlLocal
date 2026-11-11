package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.domain.planes.CategoriaAlmacenamiento;
import com.controllocal.service.*;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.UsoAlmacenamientoB0;
import com.controllocal.web.almacen.*;
import com.controllocal.web.seguridad.SesionDeRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Core/B0/journal y SID C1 reales en PG; sólo el almacén binario es TEST en memoria, sin red. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class CuotaGaleriaC6IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager gestor;@Autowired GaleriaDeFotos galeria;
    @Autowired FotoPropiedadService fotos;@Autowired ContextosService contextos;@Autowired MotorDeCaptura captura;
    @Autowired UsoAlmacenamientoB0 uso;
    @MockitoBean AlmacenDocumentos almacen;
    @MockitoSpyBean EvaluacionTecnicaFoto evaluador;
    final Map<String,byte[]> objetos=new ConcurrentHashMap<>();final AtomicInteger puts=new AtomicInteger();
    TransactionTemplate tx;Actor actor;SesionDeRequest sid;long propiedad;byte[] bytes;String base64,namespace;
    @BeforeEach void preparar(TestInfo info)throws Exception{
        EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);actor=EspacioIndividualB0DePrueba.crear(jdbc,tx);
        sid=SesionesDePrueba.abrir(jdbc,contextos,actor);principal(sid);
        propiedad=new OrigenesDePrueba(jdbc,gestor,captura,contextos).propiedad(actor,"Original C6");
        namespace=AlmacenDocumentos.huellaIdentidad("TEST","memoria-"+UUID.randomUUID());
        when(almacen.identidadTecnica()).thenReturn(namespace);when(almacen.proveedor()).thenReturn("TEST");
        when(almacen.abrir(anyString())).thenAnswer(i->{String k=i.getArgument(0);byte[] b=objetos.get(k);return b==null?Optional.empty():Optional.of(new AlmacenDocumentos.ArchivoDescargado(b.clone(),"foto.png","image/png"));});
        doAnswer(i->{objetos.put(i.getArgument(0),((byte[])i.getArgument(1)).clone());puts.incrementAndGet();return null;}).when(almacen).guardarEnClave(anyString(),any(),anyString());
        doAnswer(i->{objetos.remove((String)i.getArgument(0));return null;}).when(almacen).eliminar(anyString());
        doCallRealMethod().when(evaluador).evaluar(any());
        var salida=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new BufferedImage(2,3,BufferedImage.TYPE_INT_RGB),"png",salida);bytes=salida.toByteArray();base64=Base64.getEncoder().encodeToString(bytes);
    }
    @AfterEach void terminar(){SecurityContextHolder.clearContext();EvidenciaC4DePrueba.limpiar();}
    @Test void originalQuedaVinculadoYContadoConUtilidadLocalReal(){
        var foto=subir();assertNotNull(foto.clave());assertEquals("EVALUADA",foto.utilidad().estado());
        assertEquals(2,foto.utilidad().evaluacion().anchoOriginal());assertEquals(3,foto.utilidad().evaluacion().altoOriginal());
        assertEquals(1,puts.get());assertEquals(bytes.length,confirmados());assertEquals(0,reservados());
        assertEquals(1,cuenta("foto_propiedad"));assertEquals(1,cuenta("b0_objeto_almacenamiento"));
        assertEquals("VINCULADO",jdbc.queryForObject("SELECT estado FROM intento_salida_binaria WHERE organizacion_id=?",String.class,actor.idOrganizacion()));
    }
    @Test void cuotaAgotadaRechazaAntesDelPutYConservaLoExistente(){
        tx.executeWithoutResult(t->uso.observarExistenteComprobado(actor.idOrganizacion(),new UsoAlmacenamientoB0.ComprobacionObjeto("CUOTA_BASE_SINTETICA_TEST",2_000_000_000L,"f".repeat(64),"FIXTURE NO BINARIO DE 2GB"),CategoriaAlmacenamiento.DOCUMENTO));
        assertThrows(ReglaNegocioException.class,this::subir);assertEquals(0,puts.get());assertEquals(0,cuenta("foto_propiedad"));assertEquals(2_000_000_000L,confirmados());assertEquals(0,reservados());
    }
    @Test void propiedadDeOtraOrganizacionNoEscribeNiCuenta(){
        var ajeno=EspacioIndividualB0DePrueba.crear(jdbc,tx);long otra=new OrigenesDePrueba(jdbc,gestor,captura,contextos).propiedad(ajeno,"Ajena C6");
        assertThrows(ReglaNegocioException.class,()->galeria.agregar(otra,"foto.png",base64,12,actor));assertEquals(0,puts.get());assertEquals(0,cuenta("foto_propiedad"));
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM b0_reserva_almacenamiento WHERE organizacion_id=?",Long.class,actor.idOrganizacion()));
    }
    @Test void elTopeDeGaleriaSeCompruebaAntesDeOtroPut(){
        for(int n=0;n<12;n++)subir();assertEquals(12,puts.get());
        assertThrows(ReglaNegocioException.class,this::subir);assertEquals(12,puts.get());assertEquals(12,cuenta("foto_propiedad"));assertEquals(12L*bytes.length,confirmados());
    }
    @Test void dosSubidasConcurrentesCompitenPorLaUltimaPlazaSinDuplicarLimite()throws Exception{
        for(int n=0;n<11;n++)subir();puts.set(0);
        var listas=new CountDownLatch(2);var salida=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Boolean> tarea=()->{principal(sid);listas.countDown();try{assertTrue(salida.await(20,TimeUnit.SECONDS));subir();return true;}catch(ReglaNegocioException limite){return false;}finally{SecurityContextHolder.clearContext();}};
        try{var a=pool.submit(tarea);var b=pool.submit(tarea);assertTrue(listas.await(20,TimeUnit.SECONDS));salida.countDown();
            assertNotEquals(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS));assertEquals(1,puts.get());assertEquals(12,cuenta("foto_propiedad"));assertEquals(12L*bytes.length,confirmados());assertEquals(0,reservados());
        }finally{salida.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }
    @Test void falloDeLaUtilidadConservaOriginalYCuotaConfirmados(){
        doThrow(new ReglaNegocioException("TEST evaluación indisponible")).when(evaluador).evaluar(any());
        var foto=subir();assertEquals("NO_DISPONIBLE",foto.utilidad().estado());assertNotNull(foto.id());assertEquals(1,cuenta("foto_propiedad"));assertEquals(bytes.length,confirmados());assertEquals(0,reservados());
    }
    @Test void ultimaVerificacionFallidaRevierteMetadataYCuotaConDiarioRecuperable(){
        AtomicInteger lecturas=new AtomicInteger();
        when(almacen.abrir(anyString())).thenAnswer(i->{String k=i.getArgument(0);byte[] b=objetos.get(k);if(lecturas.incrementAndGet()>=3)return Optional.empty();return b==null?Optional.empty():Optional.of(new AlmacenDocumentos.ArchivoDescargado(b.clone(),"foto.png","image/png"));});
        assertThrows(AlmacenException.class,this::subir);assertEquals(1,puts.get());assertEquals(0,cuenta("foto_propiedad"));
        assertEquals(0,cuenta("b0_objeto_almacenamiento"));assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM b0_reserva_almacenamiento WHERE organizacion_id=?",Long.class,actor.idOrganizacion()));
        assertEquals("RETIRADO_VERIFICADO",jdbc.queryForObject("SELECT estado FROM intento_salida_binaria WHERE organizacion_id=?",String.class,actor.idOrganizacion()));
    }
    private FotoPropiedadService.FotoDePropiedad subir(){return galeria.agregar(propiedad,"foto.png",base64,12,actor);}
    private long confirmados(){return jdbc.queryForObject("SELECT confirmados FROM b0_uso_almacenamiento WHERE organizacion_id=?",Long.class,actor.idOrganizacion());}
    private long reservados(){return jdbc.queryForObject("SELECT reservados FROM b0_uso_almacenamiento WHERE organizacion_id=?",Long.class,actor.idOrganizacion());}
    private int cuenta(String tabla){return jdbc.queryForObject("SELECT count(*) FROM "+tabla+" WHERE organizacion_id=?",Integer.class,actor.idOrganizacion());}
    private static void principal(SesionDeRequest s){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(s,null,List.of()));}
}
