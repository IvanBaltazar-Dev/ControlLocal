package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.CaptacionService;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.documentos.*;
import com.controllocal.service.documentos.PiezasComercialesService.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.web.almacen.AlmacenDocumentos;
import com.controllocal.web.seguridad.SesionDeRequest;
import com.controllocal.web.seguridad.TokenService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.controllocal.integracion.soporte.FormalizacionesDePrueba.*;

/** Core/SQL/B0/journal reales. Compositor y almacén TEST: no certifica Chrome, R2 ni IA. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class PiezasComercialesC6IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired PiezasComercialesService piezas;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @MockitoBean AlmacenDocumentos almacen;
    @MockitoBean CompositorPieza compositor;
    private final Map<String,AlmacenDocumentos.ArchivoDescargado> objetos=new ConcurrentHashMap<>();
    private final AtomicInteger composiciones=new AtomicInteger();
    private FormalizacionesDePrueba b;
    private ActividadNegociacionDePrueba.Caso c;
    private byte[] pdf;
    @BeforeEach void iniciar(TestInfo info)throws Exception{
        EvidenciaC4DePrueba.iniciar(info);b=new FormalizacionesDePrueba(contexto);
        when(almacen.proveedor()).thenReturn("TEST");
        when(almacen.identidadTecnica()).thenReturn(AlmacenDocumentos.huellaIdentidad("TEST","piezas-"+UUID.randomUUID()));
        when(almacen.abrir(anyString())).thenAnswer(i->Optional.ofNullable(objetos.get((String)i.getArgument(0))));
        doAnswer(i->{String k=i.getArgument(0);byte[] datos=i.getArgument(1);
            objetos.put(k,new AlmacenDocumentos.ArchivoDescargado(datos.clone(),k.substring(k.lastIndexOf('/')+1),i.getArgument(2)));return null;
        }).when(almacen).guardarEnClave(anyString(),any(),anyString());
        doAnswer(i->{objetos.remove((String)i.getArgument(0));return null;}).when(almacen).eliminar(anyString());
        try(var documento=new PDDocument();var salida=new ByteArrayOutputStream()){
            documento.addPage(new PDPage());documento.save(salida);pdf=salida.toByteArray();
        }
        when(compositor.configurado()).thenReturn(true);
        when(compositor.componer(anyMap(),anyMap(),anyString())).thenAnswer(i->{composiciones.incrementAndGet();return binario();});
        c=b.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",true);
        principal(c.agente(),c.sesion());
    }
    @AfterEach void terminar(){SecurityContextHolder.clearContext();EvidenciaC4DePrueba.limpiar();}
    private void principal(Actor actor,ContextoSesion sesion){
        var acceso=new TransactionTemplate(gestor).execute(t->contexto.getBean(ContextosService.class)
                .exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor));
        var solicitud=new SesionDeRequest(new TokenService("").de(acceso),acceso);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(solicitud,null,List.of()));
    }
    private CompositorPieza.Binario binario(){return new CompositorPieza.Binario(pdf,"application/pdf","pdf");}
    private Preparacion entrada(){return entrada(null);}
    private Preparacion entrada(Contacto contacto){return entrada(contacto,null);}
    private Preparacion entrada(Contacto contacto,Long propiedadFicha){
        long propiedad=propiedadFicha==null?c.propiedad():propiedadFicha;
        long fisica=jdbc.queryForObject("SELECT version_fisica FROM propiedad WHERE organizacion_id=? AND id_propiedad=?",Long.class,c.agente().idOrganizacion(),propiedad);
        return new Preparacion(c.encargo(),b.encargos.consultar(c.encargo(),c.agente()).version(),fisica,"WEB_PROPIA",List.of(),
                piezas.consultarFotosPermitidas(c.encargo(),propiedadFicha,c.agente()).versionObservada(),Formato.PDF_A4,"marfil",contacto,propiedadFicha);
    }
    private Estado preparar(){return piezas.preparar(entrada(),c.agente(),c.sesion(),clave(),procedencia());}
    private Archivo preview(Estado e,String clave){return piezas.previsualizar(e.pieza(),e.versionContenido(),c.agente(),c.sesion(),clave,procedencia());}
    private long bytes(){return jdbc.queryForObject("SELECT confirmados FROM b0_uso_almacenamiento WHERE organizacion_id=?",Long.class,c.agente().idOrganizacion());}
    private int filas(){return jdbc.queryForObject("SELECT count(*) FROM pieza_comercial WHERE organizacion_id=?",Integer.class,c.agente().idOrganizacion());}
    private record UnidadPrivada(ActividadNegociacionDePrueba.Caso unidad,Estado pieza,Archivo archivo,String clavePreview) { }
    private void asignar(long encargo,Actor anterior,Actor nuevo){
        long version=b.encargos.consultar(encargo,anterior).version();var broker=c.equipo().broker();var sesion=b.sesiones.de(broker);
        principal(broker,sesion);
        try{contexto.getBean(CaptacionService.class).reasignar(encargo,new CaptacionService.DatosReasignacion(
                nuevo.idRolOperativo(),anterior.idRolOperativo(),version,"Reasignación TEST de la lectura física de unidad"),broker,sesion,clave(),procedencia());}
        finally{principal(c.agente(),c.sesion());}
    }
    private UnidadPrivada unidadPrivada(){
        c=b.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",false);principal(c.agente(),c.sesion());
        var equipo=c.equipo();var equipoColega=new OrigenesDePrueba.Equipo(equipo.colega(),equipo.agente(),equipo.broker(),equipo.admin());
        var unidad=b.negociacion.mesaEnEquipo(equipoColega,"ALQUILER",new BigDecimal("2500"),"PEN",false);
        asignar(unidad.encargo(),unidad.agente(),c.agente());
        var lista=List.of(new EncargoService.Unidad(c.propiedad(),new BigDecimal("2500"),"PEN","Principal TEST"),
                new EncargoService.Unidad(unidad.propiedad(),new BigDecimal("2500"),"PEN","Unidad TEST"));
        b.encargos.declararComposicion(c.encargo(),new EncargoService.DatosComposicion(lista,new BigDecimal("5000"),"PEN",
                "Objeto conjunto TEST explícito",b.encargos.consultar(c.encargo(),c.agente()).version()),c.agente(),c.sesion(),clave(),procedencia());
        var e=piezas.preparar(entrada(null,unidad.propiedad()),c.agente(),c.sesion(),clave(),procedencia());
        String comando=clave();var archivo=preview(e,comando);return new UnidadPrivada(unidad,e,archivo,comando);
    }

    @Test void preparacionYPreviewConservanProyeccionArchivoCuotaYReplay(){
        var entrada=entrada();String comando=clave();var e=piezas.preparar(entrada,c.agente(),c.sesion(),comando,procedencia());
        assertEquals(e.pieza(),piezas.preparar(entrada,c.agente(),c.sesion(),comando,procedencia()).pieza());assertEquals(1,filas());
        long antes=bytes();String salida=clave();var archivo=preview(e,salida);
        assertArrayEquals(pdf,piezas.descargar(archivo.referencia(),c.agente()).contenido());
        assertEquals(archivo,preview(e,salida));assertEquals(1,composiciones.get());assertEquals(antes,bytes());
        // Directriz7.2: preview no multiplica cuota comercial; sí conserva bytes y categoría técnicos.
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_reserva_almacenamiento WHERE organizacion_id=? AND categoria='PREVIEW' AND bytes=? AND estado='CONFIRMADA'",Integer.class,c.agente().idOrganizacion(),pdf.length));
        assertNotNull(piezas.consultarEstado(e.pieza(),c.agente()).archivoPrivado());
        assertNull(piezas.consultarEstado(e.pieza(),c.agente()).archivoDifusion());
    }
    @Test void referenciaAjenaNoAbreElArchivoNiExponeLaPieza(){
        var e=preparar();var archivo=preview(e,clave());var ajeno=b.negociacion.mesa("VENTA",new BigDecimal("100000"),"USD",false);
        principal(ajeno.agente(),ajeno.sesion());
        clearInvocations(almacen);
        assertThrows(NoEncontradoException.class,()->piezas.descargar(archivo.referencia(),ajeno.agente()));
        assertThrows(NoEncontradoException.class,()->piezas.consultarEstado(e.pieza(),ajeno.agente()));
        verify(almacen,never()).abrir(anyString());
    }
    @Test void gobiernoYSupervisionNoConcedenEdicionDePiezas(){
        var e=preparar();
        for(Actor actor:List.of(c.equipo().broker(),c.equipo().admin())){
            var sesion=b.sesiones.de(actor);principal(actor,sesion);
            assertThrows(AccesoNoAutorizadoException.class,()->piezas.previsualizar(e.pieza(),e.versionContenido(),actor,sesion,clave(),procedencia()));
        }
        assertEquals(0,composiciones.get());
    }
    @Test void compositorDeshabilitadoPreservaLaLecturaDelArchivoYaGuardado(){
        var e=preparar();when(compositor.configurado()).thenReturn(false);
        assertThrows(CapacidadDeshabilitadaException.class,()->preview(e,clave()));
        assertNull(piezas.consultarEstado(e.pieza(),c.agente()).archivoPrivado());
        when(compositor.configurado()).thenReturn(true);var a=preview(e,clave());
        when(compositor.configurado()).thenReturn(false);
        assertArrayEquals(pdf,piezas.descargar(a.referencia(),c.agente()).contenido());assertEquals(a,preview(e,clave()));
        assertEquals(1,composiciones.get());
    }
    @Test void falloTecnicoNoInventaArchivoDisponibleNiConsumo(){
        var e=preparar();long antes=bytes();
        when(compositor.componer(anyMap(),anyMap(),anyString())).thenThrow(new IllegalStateException("TEST compositor"));
        assertEquals("TEST compositor",assertThrows(IllegalStateException.class,()->preview(e,clave())).getMessage());
        assertNull(piezas.consultarEstado(e.pieza(),c.agente()).archivoPrivado());assertEquals(antes,bytes());
    }
    @Test void rollbackRetiraLaSalidaSinBorrarLaPreparacionCorrecta(){
        var e=preparar();long antes=bytes();var tx=new TransactionTemplate(gestor);
        tx.executeWithoutResult(t->{preview(e,clave());t.setRollbackOnly();});
        assertEquals(1,filas());assertNull(piezas.consultarEstado(e.pieza(),c.agente()).archivoPrivado());assertEquals(antes,bytes());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM intento_salida_binaria WHERE organizacion_id=? AND estado='RETIRADO_VERIFICADO'",Integer.class,c.agente().idOrganizacion()));
    }
    @Test void dosPreparacionesConcurrentesConMismaClaveNoDuplicanFamilia()throws Exception{
        var datos=entrada();String comando=clave();var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Estado> trabajo=()->EvidenciaC4DePrueba.enHilo(()->{
            assertTrue(inicio.await(15,TimeUnit.SECONDS));principal(c.agente(),c.sesion());
            try{return piezas.preparar(datos,c.agente(),c.sesion(),comando,procedencia());}
            finally{SecurityContextHolder.clearContext();}
        });
        try{var a=pool.submit(trabajo);var z=pool.submit(trabajo);inicio.countDown();
            assertEquals(a.get(45,TimeUnit.SECONDS).pieza(),z.get(45,TimeUnit.SECONDS).pieza());assertEquals(1,filas());
        }finally{inicio.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }
    @Test void cambioDeFirmaEnOtraTransaccionDuranteComposicionBloqueaDifusion()throws Exception{
        var e=piezas.preparar(entrada(new Contacto("CORREO","canal-comercial@example.invalid")),c.agente(),c.sesion(),clave(),procedencia());
        var p=b.actuaciones.prepararEntregaPieza(c.agente(),new Entrega(e.pieza(),e.versionContenido(),e.huellaOrigen()),c.sesion(),procedencia());
        var confirmacion=b.humanas.revisar(p,c.agente(),c.sesion());long antes=bytes();
        when(compositor.componer(anyMap(),anyMap(),anyString())).thenAnswer(i->{
            Map<String,Object> proyeccion=i.getArgument(0);var firma=(Map<?,?>)proyeccion.get("marcaYContacto");
            assertEquals("VIGENTE",firma.get("estado"));assertNotNull(firma.get("nombre"));
            assertNotEquals("Nombre TEST cambiado durante composicion",firma.get("nombre"));
            var pool=Executors.newSingleThreadExecutor();
            try{Integer cambiadas=pool.submit(()->new TransactionTemplate(gestor).execute(t->jdbc.update(
                    "UPDATE persona SET nombres_o_razon_social=? WHERE organizacion_id=? AND id_persona=?",
                    "Nombre TEST cambiado durante composicion",c.agente().idOrganizacion(),c.agente().idPersona()))).get(20,TimeUnit.SECONDS);
                assertEquals(1,cambiadas.intValue());
            }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
            return binario();
        });
        var resultado=b.actuaciones.confirmar(c.agente(),p.referencia(),c.sesion(),confirmacion,procedencia());
        assertEquals("RECHAZADO",resultado.estado());assertNull(resultado.resultado());
        assertNull(piezas.consultarEstado(e.pieza(),c.agente()).archivoDifusion());assertEquals(antes,bytes());
        assertTrue(piezas.consultarEstado(e.pieza(),c.agente()).desactualizada());
    }
    @Test void perderLecturaDeUnidadSinFotosImpideArchivoCacheYReplayAunqueConserveEncargoPrincipal(){
        var u=unidadPrivada();assertTrue(piezas.consultarFotosPermitidas(c.encargo(),u.unidad().propiedad(),c.agente()).fotos().isEmpty());
        long antes=bytes();asignar(u.unidad().encargo(),c.agente(),u.unidad().agente());
        assertNotNull(b.encargos.consultar(c.encargo(),c.agente()));
        assertThrows(AccesoNoAutorizadoException.class,()->piezas.descargar(u.archivo().referencia(),c.agente()));
        assertThrows(AccesoNoAutorizadoException.class,()->piezas.consultarEstado(u.pieza().pieza(),c.agente()));
        assertThrows(AccesoNoAutorizadoException.class,()->preview(u.pieza(),u.clavePreview()));
        assertEquals(antes,bytes());assertEquals(1,composiciones.get());
    }
    @Test void retirarUnidadDeComposicionPreservaRevisionPrivadaLegitimaSinPermitirSeleccionNueva(){
        var u=unidadPrivada();
        b.encargos.declararComposicion(c.encargo(),new EncargoService.DatosComposicion(
                List.of(new EncargoService.Unidad(c.propiedad(),new BigDecimal("2500"),"PEN","Principal TEST")),new BigDecimal("2500"),"PEN",
                "Retiro TEST de unidad del objeto comercial",b.encargos.consultar(c.encargo(),c.agente()).version()),c.agente(),c.sesion(),clave(),procedencia());
        var e=piezas.consultarEstado(u.pieza().pieza(),c.agente());assertTrue(e.desactualizada());assertTrue(e.causas().contains("ORIGEN_MODIFICADO"));
        assertArrayEquals(pdf,piezas.descargar(u.archivo().referencia(),c.agente()).contenido());
        assertEquals(u.archivo(),preview(u.pieza(),u.clavePreview()));assertEquals(1,composiciones.get());
        assertThrows(AccesoNoAutorizadoException.class,()->piezas.preparar(entrada(null,u.unidad().propiedad()),c.agente(),c.sesion(),clave(),procedencia()));
    }
}
