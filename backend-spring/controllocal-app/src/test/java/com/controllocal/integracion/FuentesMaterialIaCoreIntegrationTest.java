package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.documentos.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.*;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.materialia.FuentesMaterialIa.EstadoEvidencia;
import com.controllocal.web.almacen.*;
import com.controllocal.web.seguridad.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Fuente/E44/C1/B0/SQL/TX reales; almacén en memoria. Sin proveedor, gasto o aceptación IA. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class FuentesMaterialIaCoreIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired FuentesMaterialIaCore fuentes;
    @Autowired MaterialIaService material;
    @Autowired GaleriaDeFotos galerias;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @MockitoBean AlmacenDocumentos almacen;
    @MockitoSpyBean ArchivosFotoContextuales originales;
    private final Map<String,AlmacenDocumentos.ArchivoDescargado> objetos=new ConcurrentHashMap<>();
    private FormalizacionesDePrueba banco;
    private ActividadNegociacionDePrueba.Caso caso;
    private byte[] imagen;

    @BeforeEach void preparar(TestInfo info)throws Exception{
        EvidenciaC4DePrueba.iniciar(info);banco=new FormalizacionesDePrueba(contexto);
        when(almacen.proveedor()).thenReturn("TEST");
        when(almacen.identidadTecnica()).thenReturn(AlmacenDocumentos.huellaIdentidad("TEST","fuentes-"+UUID.randomUUID()));
        when(almacen.abrir(anyString())).thenAnswer(i->Optional.ofNullable(objetos.get((String)i.getArgument(0))));
        doAnswer(i->{objetos.put(i.getArgument(0),new AlmacenDocumentos.ArchivoDescargado(i.getArgument(1),"original TEST",i.getArgument(2)));return null;})
                .when(almacen).guardarEnClave(anyString(),any(byte[].class),anyString());
        doAnswer(i->{objetos.remove((String)i.getArgument(0));return null;}).when(almacen).eliminar(anyString());
        caso=banco.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",true,
                List.of(new PropiedadUniversalService.ValorAtributo("modalidad_precio","MENSUAL_TOTAL")));
        principal(caso.agente(),caso.sesion());
        var foto=new BufferedImage(160,120,BufferedImage.TYPE_INT_RGB);
        for(int x=0;x<160;x++)for(int y=0;y<120;y++)foto.setRGB(x,y,0xff8899aa);
        var bytes=new ByteArrayOutputStream();assertTrue(ImageIO.write(foto,"png",bytes));imagen=bytes.toByteArray();
    }
    @AfterEach void cerrar(){SecurityContextHolder.clearContext();EvidenciaC4DePrueba.limpiar();}

    @Test void elEncargoEntregaPrecioConConceptoMonedaYBaseSinIdentidadesNiPieza(){
        long piezas=cuenta("pieza_comercial"),jobs=cuenta("material_ia_operacion");
        var s=new Solicitud(UUID.randomUUID(),Accion.REDACTAR_FICHA,new Recurso("ENCARGO",Long.toString(caso.encargo())),null,null,"Redactar ficha comercial TEST.");
        var f=fuentes.preparar(s,caso.agente(),caso.sesion());
        assertEquals(EstadoEvidencia.PENDIENTE,f.evidencia().estado());
        var encargo=mapa(f.datosPermitidos().get("encargo"));var precio=mapa(encargo.get("precio"));
        assertFalse(encargo.containsKey("id"));assertFalse(encargo.containsKey("version"));
        assertEquals("PEN",mapa(precio.get("moneda")).get("valor"));
        Object importe=mapa(precio.get("importe")).get("valor");assertInstanceOf(Number.class,importe);
        assertEquals(0,new BigDecimal("2500").compareTo(new BigDecimal(importe.toString())));
        assertEquals("MENSUAL",precio.get("periodo"));
        assertEquals("MENSUAL_TOTAL",mapa(mapa(encargo.get("condiciones")).get("modalidad_precio")).get("valor"));
        assertFalse(f.datosPermitidos().containsKey("marcaYContacto"));
        assertFalse(f.datosPermitidos().containsKey("pieza"));
        assertEquals(piezas,cuenta("pieza_comercial"));assertEquals(jobs,cuenta("material_ia_operacion"));
        var peticion=new Solicitud(s.operacion(),s.accion(),s.recurso(),f.versionOrigen(),null,s.instruccion());
        assertThrows(CapacidadDeshabilitadaException.class,()->material.solicitar(peticion,caso.agente(),caso.sesion()));
        assertEquals(jobs,cuenta("material_ia_operacion"));
    }

    @Test void laGaleriaCambiadaEnOtraTransaccionDuranteIoInvalidaLaPreparacion()throws Exception{
        UUID original=foto("primera TEST.png");var unaVez=new AtomicBoolean();
        doAnswer(i->{byte[] bytes=(byte[])i.callRealMethod();
            if(unaVez.compareAndSet(false,true)){
                var pool=Executors.newSingleThreadExecutor();
                try{assertNotNull(pool.submit(()->{principal(caso.agente(),caso.sesion());
                    try{return foto("segunda TEST.png");}finally{SecurityContextHolder.clearContext();}
                }).get(20,TimeUnit.SECONDS));}
                finally{pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
            }return bytes;
        }).when(originales).original(eq(original),eq(caso.agente()));
        var s=new Solicitud(UUID.randomUUID(),Accion.ANALIZAR_FOTO,new Recurso("PROPIEDAD",Long.toString(caso.propiedad())),null,original,"Analizar esta foto TEST.");
        assertThrows(ConflictoException.class,()->fuentes.preparar(s,caso.agente(),caso.sesion()));
        assertTrue(unaVez.get());assertEquals(0,cuenta("material_ia_operacion"));
    }

    @Test void conocerUnOriginalDeOtraOrganizacionNoPermitePrepararlo(){
        UUID original=foto("propia TEST.png");
        var ajena=banco.negociacion.mesa("VENTA",new BigDecimal("150000"),"USD",true);
        principal(ajena.agente(),ajena.sesion());
        var s=new Solicitud(UUID.randomUUID(),Accion.ANALIZAR_FOTO,new Recurso("PROPIEDAD",Long.toString(caso.propiedad())),null,original,"Analizar foto ajena TEST.");
        assertThrows(AccesoNoAutorizadoException.class,()->fuentes.preparar(s,ajena.agente(),ajena.sesion()));
        assertEquals(0,jdbc.queryForObject("select count(*) from material_ia_operacion where organizacion_id=?",Long.class,ajena.agente().idOrganizacion()));
    }

    private UUID foto(String nombre){return UUID.fromString(galerias.agregar(caso.propiedad(),nombre,Base64.getEncoder().encodeToString(imagen),
            FotoPropiedadService.TOPE_UNIVERSAL,caso.agente()).clave());}
    private void principal(Actor actor,ContextoSesion sesion){
        var acceso=new TransactionTemplate(gestor).execute(t->contexto.getBean(ContextosService.class)
                .exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor));
        var principal=new SesionDeRequest(new TokenService("").de(acceso),acceso);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of()));
    }
    private long cuenta(String tabla){
        if(!Set.of("pieza_comercial","material_ia_operacion").contains(tabla))throw new IllegalArgumentException();
        return jdbc.queryForObject("select count(*) from "+tabla+" where organizacion_id=?",Long.class,caso.agente().idOrganizacion());
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> mapa(Object valor){return (Map<String,Object>)valor;}
}
