package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.presupuesto.UnidadesDeConsumo.*;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.materialia.RegistroMaterialIaRepository;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.archivo.AccesoArchivoService;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.documentos.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.*;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.materialia.MaterialIaService.Solicitud;
import com.controllocal.service.materialia.FuentesMaterialIa.*;
import com.controllocal.service.materialia.ControlesMaterialIa.*;
import com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa.AmbitoTratamiento;
import com.controllocal.service.planes.DerechosDePlanB0;
import com.controllocal.service.planes.UsoAlmacenamientoB0;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.soporte.*;
import com.controllocal.web.almacen.*;
import com.controllocal.web.seguridad.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Prueba focal PG+C1/E44/B0/journal/Disco; la ejecución se acredita en el corte.
 * Proveedor, QC y acreditación legal SINTÉTICOS TEST, sin red ni gasto externo.
 * El compositor TEST sólo demuestra vínculo/protección DIF, no render/fidelidad.
 * La Salida de recuperación vive en este banco: NO prueba pérdida de memoria/JVM.
 */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties={"spring.datasource.hikari.maximum-pool-size=4",
                "controllocal.almacen.proveedor=DISCO","controllocal.ia.gasto-externo-habilitado=false",
                "controllocal.material-ia.conservacion.habilitada=false"})
@Import(MaterialIaDiscoC6IntegrationTest.ConfiguracionTest.class)
@Execution(ExecutionMode.SAME_THREAD)
class MaterialIaDiscoC6IntegrationTest {
    private static final Path BASE=baseEnsayo();
    private static final Path ENSAYO=BASE.resolve("material-ia-disco-"+UUID.randomUUID()).normalize();
    private static final Path RAIZ=ENSAYO.resolve("almacen");
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){
        BaseDeDatosDePruebas.registrar(p);
        if(!ENSAYO.startsWith(BASE)||ENSAYO.equals(BASE))throw new IllegalStateException("Ensayo fuera de work/tmp.");
        try{Files.createDirectories(BASE);Files.createDirectory(ENSAYO);Files.createDirectory(RAIZ);}
        catch(IOException e){throw new UncheckedIOException(e);}
        p.add("controllocal.almacen.directorio",()->RAIZ.toString());
    }
    @Autowired ApplicationContext contexto;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired AlmacenDocumentos almacen;
    @Autowired ResultadosMaterialIa resultados;
    @Autowired RegistroMaterialIaRepository jobs;
    @Autowired AvisosDiscoTest avisos;
    @Autowired PiezasComercialesService piezas;
    @MockitoBean CompositorPieza compositor;
    private final AtomicInteger llamadas=new AtomicInteger(),controles=new AtomicInteger();
    private Runnable trasRespuesta=()->{};
    private FormalizacionesDePrueba b;
    private ActividadNegociacionDePrueba.Caso c;
    private DerivadasMaterialIaDePrueba configuracion;
    private DestinoTratamiento destino;
    private MaterialIaServiceImpl coordinador;
    private ProveedorMaterialIa proveedorTest;
    private ControlesMaterialIa qcTest;
    private PresupuestoIaService presupuestoTest;
    private UUID original;
    private String claveOriginal,namespace;
    private byte[] bytesOriginal,salidaTest;

    @BeforeEach void preparar(TestInfo info)throws Exception{
        EvidenciaC4DePrueba.iniciar(info);
        assertInstanceOf(AlmacenDisco.class,almacen);assertFalse(mockingDetails(almacen).isMock());
        assertEquals("DISCO",almacen.proveedor());namespace=almacen.identidadTecnica();
        assertFalse(contexto.getBean(PresupuestoIaService.class).gastoExternoHabilitado());
        assertEquals(1,contexto.getBeansOfType(FuentesMaterialIa.class).size());
        b=new FormalizacionesDePrueba(contexto);
        var origenes=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class));
        var equipo=origenes.empresa();
        // Reutiliza perfil, fondo y política145 TEST existentes; no modifica el helper.
        // En el primer caso el namespace se financia ANTES de que haya binarios.
        configuracion=new DerivadasMaterialIaDePrueba(contexto,jdbc,gestor);
        configuracion.prepararAlmacenamiento(equipo.agente());destino=configuracion.destino();
        c=b.negociacion.mesaEnEquipo(equipo,"ALQUILER",new BigDecimal("2500"),"PEN",true);
        principal(c.agente(),c.sesion());
        bytesOriginal=png(80,60,0x112233);salidaTest=png(32,32,0xff4422);
        var foto=contexto.getBean(GaleriaDeFotos.class).agregar(c.propiedad(),"original-sintetico-disco.png",
                Base64.getEncoder().encodeToString(bytesOriginal),FotoPropiedadService.TOPE_UNIVERSAL,c.agente());
        original=UUID.fromString(foto.clave());
        claveOriginal=contexto.getBean(AccesoArchivoService.class).resolver(original.toString(),c.agente()).claveProveedor();
        assertArrayEquals(bytesOriginal,Files.readAllBytes(privado(claveOriginal)));
        avisos.permitir(c.agente(),c.propiedad(),destino);
        assertTrue(salidaTest.length<=jdbc.queryForObject("SELECT maximo_bytes FROM b0_limite_salida_ia WHERE perfil=? AND version_perfil=?",Long.class,destino.perfil(),destino.versionPerfil()));
        var financiero=contexto.getBean(PresupuestoIaRepository.class);var documentos=contexto.getBean(Documentos.class);
        presupuestoTest=new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),
                List.of(contexto.getBean(AutoridadMaterialIa.class)),documentos,new DerechosDePlanB0(financiero,documentos),true);
        // true sólo en este objeto de banco sin red; el bean Main continúa OFF.
        proveedorTest=new ProveedorMaterialIa(){
            public String identificador(){return destino.proveedor();}
            public boolean atiende(Accion a,String p,String v){return a==Accion.MEJORAR_FOTO&&destino.perfil().equals(p)&&destino.versionPerfil().equals(v);}
            public Salida ejecutar(Peticion p,java.util.function.Consumer<com.controllocal.service.presupuesto.ObservacionHttpIa> observador){
                fueraSql();llamadas.incrementAndGet();assertNotNull(p.fotoAutorizada());
                var medida=new ResultadoFacturable(List.of(new MedicionFacturable(UUID.randomUUID(),identificador(),
                        "SINTETICO_NO_MODELO_REAL","TEST-1",List.of(new Medicion("unidades_test","u",BigDecimal.ONE,null)),
                        List.of(new Tarifa("unidades_test","u",new BigDecimal("0.1"),BigDecimal.ONE)),null,"TEST-SIN-RED:"+p.claveExterna())));
                var salida=new Salida(null,"image/png",salidaTest,"PROCESO_SINTETICO_NO_FIDELIDAD_REAL","TEST-1",medida);
                trasRespuesta.run();return salida;
            }
        };
        qcTest=new ControlesMaterialIa(){
            public boolean atiende(Accion a,String p,String v){return proveedorTest.atiende(a,p,v);}
            public Control evaluar(Solicitud s,Snapshot f,ProveedorMaterialIa.Salida r){
                fueraSql();controles.incrementAndGet();
                return new Control(Decision.APROBADA,"QC_SINTETICO_NO_ACEPTACION_IA",Instant.now(),Arrays.stream(Aspecto.values())
                        .map(a->new Senal(a,EstadoSenal.EVALUADA,false,"TEST: NO CALIDAD NI FIDELIDAD REAL","TEST-DISCO:"+s.operacion()+":"+a)).toList());
            }
        };
        coordinador=coordinador(resultados);
        byte[] pdf;
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){doc.addPage(new PDPage());doc.save(out);pdf=out.toByteArray();}
        when(compositor.configurado()).thenReturn(true);
        when(compositor.componer(anyMap(),anyMap(),anyString())).thenReturn(new CompositorPieza.Binario(pdf,"application/pdf","pdf"));
    }
    @AfterEach void terminar(){SecurityContextHolder.clearContext();EvidenciaC4DePrueba.limpiar();}

    @Test void disponibleEsBinarioPrivadoAntesDeConservarYReplayNoProcesaOtraVez()throws Exception{
        long antes=uso();var g=generar();var v=resultados.version(g.solicitud().operacion(),c.agente());
        assertTrue(v.disponible());assertFalse(v.guardada());assertFalse(v.protegida());
        var inicial=resultados.seleccionActual(original,c.agente(),c.sesion());
        assertEquals(0,inicial.revision());assertNull(inicial.operacion());assertNull(inicial.archivo());
        assertEquals(original,v.original());assertNotNull(v.generadoEn());assertEquals(v.generadoEn(),v.temporalDesde());
        assertEquals(v.temporalDesde().plus(Duration.ofDays(7)),v.venceEn());
        comprobarArchivo(g);assertEquals(antes+salidaTest.length,uso());
        var claves=Set.copyOf(almacen.listarClaves());
        assertEquals(g.resultado(),coordinador.solicitar(g.solicitud(),c.agente(),c.sesion()));
        assertEquals(v,resultados.version(g.solicitud().operacion(),c.agente()),"Releer no cambia generación ni vencimiento.");
        assertEquals(claves,almacen.listarClaves());assertEquals(1,llamadas.get());assertEquals(1,controles.get());
        assertEquals("LIQUIDADA",financiera(g.solicitud()));assertEquals(1,medidas(g.solicitud()));originalIntacto();
    }

    @Test void seleccionGuardarYCambioAOriginalConservanDosTemporalesSinMutacionParcial()throws Exception{
        var g1=generar();var generada1=resultados.version(g1.solicitud().operacion(),c.agente()).generadoEn();
        var s0=resultados.seleccionActual(original,c.agente(),c.sesion());
        resultados.seleccionar(original,g1.solicitud().operacion(),g1.resultado().archivo(),s0.revision(),clave(),c.agente(),c.sesion());
        var g2=generar();var v2=resultados.version(g2.solicitud().operacion(),c.agente());long cuota=uso();String guardar=clave();
        var guardada=resultados.guardarAlternativa(v2.operacion(),v2.archivo(),v2.revision(),guardar,c.agente(),c.sesion());
        assertTrue(guardada.guardada());assertEquals(guardada,resultados.guardarAlternativa(v2.operacion(),v2.archivo(),v2.revision(),guardar,c.agente(),c.sesion()));
        assertEquals(cuota,uso());var g3=generar();
        var seleccion=resultados.seleccionActual(original,c.agente(),c.sesion());String volver=clave();
        var vuelta=resultados.seleccionar(original,null,null,seleccion.revision(),volver,c.agente(),c.sesion());
        assertNull(vuelta.operacion());assertEquals(vuelta,resultados.seleccionar(original,null,null,seleccion.revision(),volver,c.agente(),c.sesion()));
        var temporal1=resultados.version(g1.solicitud().operacion(),c.agente());assertFalse(temporal1.guardada());
        assertEquals(generada1,temporal1.generadoEn());assertTrue(temporal1.temporalDesde().isAfter(generada1));
        assertEquals(temporal1.temporalDesde().plus(Duration.ofDays(7)),temporal1.venceEn());
        var claves=Set.copyOf(almacen.listarClaves());long antesDenegacion=uso();var cuarta=prepararSolicitud();
        assertThrows(ConflictoException.class,()->coordinador.solicitar(cuarta,c.agente(),c.sesion()));
        assertEquals(3,llamadas.get());assertEquals(claves,almacen.listarClaves());assertEquals(antesDenegacion,uso());
        assertTrue(contexto.getBean(PresupuestoIaRepository.class).operacion(c.agente().idOrganizacion(),cuarta.operacion()).isEmpty());
        comprobarArchivo(g1);comprobarArchivo(g2);comprobarArchivo(g3);originalIntacto();
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void retiroExpresoCompruebaAusenciaRealYLiberaUnaSolaVez(boolean guardar)throws Exception{
        var g=generar();var v=resultados.version(g.solicitud().operacion(),c.agente());
        if(guardar)v=resultados.guardarAlternativa(v.operacion(),v.archivo(),v.revision(),clave(),c.agente(),c.sesion());
        long antes=uso(),revision=v.revision();String llave=clave();
        var retirada=guardar?resultados.retirarAlternativa(v.operacion(),v.archivo(),revision,llave,c.agente(),c.sesion()):
                resultados.descartarTemporal(v.operacion(),v.archivo(),revision,llave,c.agente(),c.sesion());
        assertEquals("RETIRADA_VERIFICADA",retirada.estadoGestion());assertFalse(retirada.disponible());
        assertTrue(Files.notExists(privado(g.clave())));assertTrue(almacen.abrir(g.clave()).isEmpty());assertEquals(antes-salidaTest.length,uso());
        var repetida=guardar?resultados.retirarAlternativa(v.operacion(),v.archivo(),revision,llave,c.agente(),c.sesion()):
                resultados.descartarTemporal(v.operacion(),v.archivo(),revision,llave,c.agente(),c.sesion());
        assertEquals(retirada,repetida);assertEquals(antes-salidaTest.length,uso());
        assertThrows(NoEncontradoException.class,()->resultados.descargar(g.solicitud().operacion(),g.resultado().archivo(),c.agente(),c.sesion()));
        assertEquals(g.resultado().archivo(),resultados.exigirArchivo(g.solicitud().operacion(),g.resultado().archivo(),c.agente()).archivo());
        assertEquals(1,llamadas.get());assertEquals("LIQUIDADA",financiera(g.solicitud()));originalIntacto();
    }

    @Test void rollbackPosteriorAlDeleteRealConservaCuotaHastaRecuperarLaMismaAusencia()throws Exception{
        var g=generar();var v=resultados.version(g.solicitud().operacion(),c.agente());long antes=uso();String llave=clave();
        // Sabotaje Core DESPUÉS del DELETE físico; no se mockea Almacen ni su respuesta de ausencia.
        CuotaArchivosB0 target=AopTestUtils.getUltimateTargetObject(contexto.getBean(CuotaArchivosB0.class));
        var cuotaFallida=spy(target);doThrow(new IllegalStateException("SABOTAJE_TEST_POST_DELETE"))
                .when(cuotaFallida).liberarRetiradaVerificada(any(),any());
        var fallido=adaptador(almacen,cuotaFallida);
        var error=assertThrows(IllegalStateException.class,()->fallido.descartarTemporal(v.operacion(),v.archivo(),v.revision(),llave,c.agente(),c.sesion()));
        assertEquals("SABOTAJE_TEST_POST_DELETE",error.getMessage());assertTrue(Files.notExists(privado(g.clave())));
        assertEquals(antes,uso());var pendiente=resultados.version(v.operacion(),c.agente());
        assertEquals("RETIRADA_PENDIENTE",pendiente.estadoGestion());
        assertEquals("INTENCION",jdbc.queryForObject("SELECT estado FROM retirada_derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",String.class,c.agente().idOrganizacion(),v.operacion()));
        assertThrows(NoEncontradoException.class,()->resultados.guardarAlternativa(pendiente.operacion(),pendiente.archivo(),pendiente.revision(),clave(),c.agente(),c.sesion()));
        // Callback Core existente, no cron nuevo ni SID histórico fabricado.
        resultados.conciliarCaducidad(c.agente().idOrganizacion(),v.operacion());
        assertEquals("RETIRADA_VERIFICADA",resultados.version(v.operacion(),c.agente()).estadoGestion());assertEquals(antes-salidaTest.length,uso());
        resultados.conciliarCaducidad(c.agente().idOrganizacion(),v.operacion());assertEquals(antes-salidaTest.length,uso());
        assertEquals(1,llamadas.get());assertEquals("LIQUIDADA",financiera(g.solicitud()));originalIntacto();
    }

    @Test void otroTenantYRevocacionDeUsoNoEntreganNiEliminanBinario()throws Exception{
        var g=generar();long antes=uso();
        var otro=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class)).empresa();
        var sidAjena=b.sesiones.de(otro.agente());principal(otro.agente(),sidAjena);
        assertThrows(NoEncontradoException.class,()->resultados.descargar(g.solicitud().operacion(),g.resultado().archivo(),otro.agente(),sidAjena));
        principal(c.agente(),c.sesion());avisos.usoRevocado=true;
        try{assertThrows(CapacidadDeshabilitadaException.class,()->resultados.descargar(g.solicitud().operacion(),g.resultado().archivo(),c.agente(),c.sesion()));}
        finally{avisos.usoRevocado=false;}
        assertEquals(antes,uso());assertEquals(1,llamadas.get());comprobarArchivo(g);originalIntacto();
    }

    @Test void previewNoProtegePeroDifusionConfirmadaConArchivoRealProtegeVersionEmitida()throws Exception{
        var g=generar();var s=resultados.seleccionActual(original,c.agente(),c.sesion());
        resultados.seleccionar(original,g.solicitud().operacion(),g.resultado().archivo(),s.revision(),clave(),c.agente(),c.sesion());
        long fisica=jdbc.queryForObject("SELECT version_fisica FROM propiedad WHERE organizacion_id=? AND id_propiedad=?",Long.class,c.agente().idOrganizacion(),c.propiedad());
        var entrada=new PiezasComercialesService.Preparacion(c.encargo(),b.encargos.consultar(c.encargo(),c.agente()).version(),fisica,"WEB_PROPIA",
                List.of(new PiezasComercialesService.Foto(original,null,"Fotografía TEST")),piezas.consultarFotosPermitidas(c.encargo(),null,c.agente()).versionObservada(),
                PiezasComercialesService.Formato.PDF_A4,"marfil",new PiezasComercialesService.Contacto("CORREO","canal-test@example.invalid"),null);
        var pieza=piezas.preparar(entrada,c.agente(),c.sesion(),clave(),Procedencia.deLaPantalla());
        piezas.previsualizar(pieza.pieza(),pieza.versionContenido(),c.agente(),c.sesion(),clave(),Procedencia.deLaPantalla());
        assertFalse(resultados.version(g.solicitud().operacion(),c.agente()).protegida());
        var propuesta=b.actuaciones.prepararEntregaPieza(c.agente(),new PiezasComercialesService.Entrega(pieza.pieza(),pieza.versionContenido(),pieza.huellaOrigen()),c.sesion(),Procedencia.deLaPantalla());
        var confirmacion=b.humanas.revisar(propuesta,c.agente(),c.sesion());
        var confirmada=b.actuaciones.confirmar(c.agente(),propuesta.referencia(),c.sesion(),confirmacion,Procedencia.deLaPantalla());
        assertNotNull(confirmada.resultado(),confirmada.estado());
        var emitida=piezas.consultarEstado(pieza.pieza(),c.agente());assertNotNull(emitida.archivoDifusion());
        var documento=piezas.descargar(emitida.archivoDifusion(),c.agente());String huella=sha(documento.contenido());
        assertTrue(resultados.version(g.solicitud().operacion(),c.agente()).protegida());
        var vigente=resultados.seleccionActual(original,c.agente(),c.sesion());resultados.seleccionar(original,null,null,vigente.revision(),clave(),c.agente(),c.sesion());
        var version=resultados.version(g.solicitud().operacion(),c.agente());
        var guardada=resultados.guardarAlternativa(version.operacion(),version.archivo(),version.revision(),clave(),c.agente(),c.sesion());
        assertTrue(guardada.guardada());assertTrue(guardada.protegida());
        assertThrows(ConflictoException.class,()->resultados.retirarAlternativa(guardada.operacion(),guardada.archivo(),guardada.revision(),clave(),c.agente(),c.sesion()));
        assertEquals(pieza.versionContenido(),piezas.consultarEstado(pieza.pieza(),c.agente()).versionContenido());
        assertEquals(huella,sha(piezas.descargar(emitida.archivoDifusion(),c.agente()).contenido()));
        assertEquals(1,llamadas.get());comprobarArchivo(g);originalIntacto();
    }

    @Test void averiaDelMarcadorAntesDeCustodiaNoInventaRecuperacionDesdeRamTrasReinstanciarBackend()throws Exception{
        var solicitud=prepararSolicitud();Path marcador=privado(".brox-almacen-id");byte[] identidad=Files.readAllBytes(marcador);
        assertEquals(36,identidad.length);
        trasRespuesta=()->{try{Files.write(marcador,new byte[0],StandardOpenOption.TRUNCATE_EXISTING);}catch(IOException e){throw new UncheckedIOException(e);}};
        try{
            assertThrows(AlmacenException.class,()->coordinador.solicitar(solicitud,c.agente(),c.sesion()));
            assertEquals(0,Files.size(marcador));assertEquals(1,llamadas.get());
            assertEquals("LIQUIDADA",financiera(solicitud));assertEquals("FALLO_RESULTADO",jobs.obtener(c.agente().idOrganizacion(),solicitud.operacion()).orElseThrow().estado());
        }finally{Files.write(marcador,identidad,StandardOpenOption.TRUNCATE_EXISTING);trasRespuesta=()->{};}
        long despuesFactura=uso();
        var reiniciado=new AlmacenDisco(RAIZ.toString());assertEquals(namespace,reiniciado.identidadTecnica());
        assertFalse(mockingDetails(reiniciado).isMock());assertArrayEquals(bytesOriginal,reiniciado.abrir(claveOriginal).orElseThrow().contenido());
        var adaptadorNuevo=adaptador(reiniciado,contexto.getBean(CuotaArchivosB0.class));var nuevo=coordinador(adaptadorNuevo);
        var op=jobs.obtener(c.agente().idOrganizacion(),solicitud.operacion()).orElseThrow();
        // Nunca se comprometió la custodia: reiniciar backend no recupera un resultado perdido.
        assertThrows(NoEncontradoException.class,()->nuevo.recuperarResultadoDurable(op.organizacion(),op.id(),c.agente(),c.sesion()));
        assertThrows(NoEncontradoException.class,()->nuevo.solicitar(solicitud,c.agente(),c.sesion()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM custodia_resultado_material_ia WHERE organizacion_id=? AND id_operacion=?",Integer.class,op.organizacion(),op.id()));
        assertEquals(1,llamadas.get());assertEquals(0,controles.get());assertEquals(1,medidas(solicitud));assertEquals("LIQUIDADA",financiera(solicitud));
        assertEquals(despuesFactura,uso());originalIntacto();
    }

    @ParameterizedTest @ValueSource(strings={"RECUPERAR","RETIRAR"})
    void recepcionComprometidaEnDiscoPrecedeQcYReinstanciarBackendNoRequiereSalidaEnRam(String accion)throws Exception{
        var s=prepararSolicitud();var receptor=spy(adaptador(almacen,contexto.getBean(CuotaArchivosB0.class)));
        doAnswer(i->{i.callRealMethod();var custodia=jobs.custodia(c.agente().idOrganizacion(),s.operacion()).orElseThrow();
            assertEquals("VERIFICADA",custodia.estado());assertEquals(0,controles.get());
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='QC_CLAIM'",Integer.class,c.agente().idOrganizacion(),s.operacion()));
            throw new IllegalStateException("TEST interrupción después de recibir comprometido, antes de QC");
        }).when(receptor).recibirResultado(anyLong(),any(),anyString(),any());
        assertThrows(IllegalStateException.class,()->coordinador(receptor).solicitar(s,c.agente(),c.sesion()));
        var custodia=jobs.custodia(c.agente().idOrganizacion(),s.operacion()).orElseThrow();
        String privada=jdbc.queryForObject("SELECT clave_proveedor FROM intento_salida_binaria WHERE organizacion_id=? AND operacion=? AND numero=?",String.class,custodia.organizacion(),custodia.operacionIo(),custodia.numero());
        assertArrayEquals(salidaTest,Files.readAllBytes(privado(privada)));assertEquals(1,llamadas.get());assertEquals(0,controles.get());assertEquals("LIQUIDADA",financiera(s));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",Integer.class,custodia.organizacion(),s.operacion()));
        long recibida=uso();var backendNuevo=new AlmacenDisco(RAIZ.toString());var archivos=adaptador(backendNuevo,contexto.getBean(CuotaArchivosB0.class));
        var proveedorProhibido=new ProveedorMaterialIa(){
            public String identificador(){return proveedorTest.identificador();}
            public boolean atiende(Accion a,String p,String v){return proveedorTest.atiende(a,p,v);}
            public Salida ejecutar(Peticion p,java.util.function.Consumer<com.controllocal.service.presupuesto.ObservacionHttpIa> observador){throw new AssertionError("Recovery/retirada no puede generar otra imagen");}
        };
        var nuevo=new MaterialIaServiceImpl(jobs,presupuestoTest,contexto.getBean(ContextoPresupuestoB0.class),contexto.getBean(AutoridadMaterialIa.class),contexto.getBean(PerfilesMaterialIa.class),contexto.getBean(Documentos.class),List.of(proveedorProhibido),List.of(qcTest),List.of(archivos),gestor);
        // Reinstancia backend/coordinador, NO prueba pérdida de JVM: el harness independiente cubre eso.
        if(accion.equals("RECUPERAR")){
            var r=nuevo.recuperarResultadoDurable(custodia.organizacion(),s.operacion(),c.agente(),c.sesion());
            assertEquals("DISPONIBLE",r.estado());assertEquals(custodia.archivo(),r.archivo());assertEquals(1,controles.get());assertEquals(recibida,uso());
            assertArrayEquals(salidaTest,archivos.descargar(s.operacion(),r.archivo(),c.agente(),c.sesion()).bytes());
            assertEquals(r,nuevo.solicitar(s,c.agente(),c.sesion()));assertEquals(1,medidas(s));
        }else{
            var v=archivos.version(s.operacion(),c.agente());assertFalse(v.disponible());assertFalse(v.guardada());
            String clave="retirar-recibido-"+UUID.randomUUID();var retirada=archivos.descartarTemporal(s.operacion(),v.archivo(),v.revision(),clave,c.agente(),c.sesion());
            assertEquals("RETIRADA_VERIFICADA",retirada.estadoGestion());assertFalse(Files.exists(privado(privada)));assertEquals(recibida-salidaTest.length,uso());
            assertEquals(retirada,archivos.descartarTemporal(s.operacion(),v.archivo(),v.revision(),clave,c.agente(),c.sesion()));
            assertThrows(NoEncontradoException.class,()->nuevo.recuperarResultadoDurable(custodia.organizacion(),s.operacion(),c.agente(),c.sesion()));assertEquals(0,controles.get());
        }
        assertEquals(1,llamadas.get());assertEquals("LIQUIDADA",financiera(s));originalIntacto();
    }

    private MaterialIaServiceImpl coordinador(ResultadosMaterialIa archivos){return new MaterialIaServiceImpl(jobs,presupuestoTest,
            contexto.getBean(ContextoPresupuestoB0.class),contexto.getBean(AutoridadMaterialIa.class),contexto.getBean(PerfilesMaterialIa.class),
            contexto.getBean(Documentos.class),List.of(proveedorTest),List.of(qcTest),List.of(archivos),gestor);}
    private ResultadosMaterialIaAlmacen adaptador(AlmacenDocumentos disco,CuotaArchivosB0 cuota){return new ResultadosMaterialIaAlmacen(
            contexto.getBean(DerivadasMaterialIaService.class),contexto.getBean(FuentesMaterialIa.class),contexto.getBean(SalidaBinariaPersistente.class),
            cuota,disco,contexto.getBean(UsoAlmacenamientoB0.class),presupuestoTest);}
    private record Generada(Solicitud solicitud,Resultado resultado,String clave){}
    private Solicitud prepararSolicitud(){
        fueraSql();var inicial=new Solicitud(UUID.randomUUID(),Accion.MEJORAR_FOTO,new MaterialIaService.Recurso("PROPIEDAD",Long.toString(c.propiedad())),null,original,null);
        var aviso=contexto.getBean(FuentesMaterialIaCore.class).presentarTratamiento(inicial,c.agente(),c.sesion());
        contexto.getBean(EvidenciasTratamientoMaterialIa.class).capturar(new EvidenciasTratamientoMaterialIa.Captura(UUID.randomUUID(),aviso.referencia(),
                aviso.versionCobertura(),EvidenciasTratamientoMaterialIa.Decision.ACEPTAR),c.agente(),c.sesion(),Procedencia.deLaPantalla());
        var preparada=coordinador.preparar(inicial,c.agente(),c.sesion());
        return new Solicitud(inicial.operacion(),inicial.accion(),inicial.recurso(),preparada.versionOrigen(),original,null);
    }
    private Generada generar(){var s=prepararSolicitud();var r=coordinador.solicitar(s,c.agente(),c.sesion());
        assertEquals("DISPONIBLE",r.estado());assertNotNull(r.archivo());
        return new Generada(s,r,jdbc.queryForObject("SELECT i.clave_proveedor FROM derivada_material_ia d JOIN intento_salida_binaria i ON i.organizacion_id=d.organizacion_id AND i.operacion=d.operacion_io AND i.numero=d.numero_io WHERE d.organizacion_id=? AND d.id_operacion=?",String.class,c.agente().idOrganizacion(),s.operacion()));}
    private void comprobarArchivo(Generada g)throws Exception{
        assertNotEquals(original,g.resultado().archivo());assertNotEquals(claveOriginal,g.clave());
        assertTrue(Files.isRegularFile(privado(g.clave())));assertArrayEquals(salidaTest,Files.readAllBytes(privado(g.clave())));
        assertArrayEquals(salidaTest,resultados.descargar(g.solicitud().operacion(),g.resultado().archivo(),c.agente(),c.sesion()).bytes());
        var archivo=resultados.exigirArchivo(g.solicitud().operacion(),g.resultado().archivo(),c.agente());
        assertEquals(g.solicitud().operacion(),archivo.operacion());assertEquals(g.resultado().archivo(),archivo.archivo());
        assertEquals(original,archivo.original());assertEquals(sha(salidaTest),archivo.huella());assertEquals("image/png",archivo.contentType());
        assertEquals("PROCESO_SINTETICO_NO_FIDELIDAD_REAL",archivo.proceso());assertEquals("TEST-1",archivo.versionProceso());
    }
    private void originalIntacto()throws Exception{
        assertEquals(namespace,almacen.identidadTecnica());
        assertTrue(Files.isRegularFile(privado(claveOriginal)));assertEquals(sha(bytesOriginal),sha(Files.readAllBytes(privado(claveOriginal))));
        assertArrayEquals(bytesOriginal,almacen.abrir(claveOriginal).orElseThrow().contenido());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM foto_propiedad WHERE organizacion_id=? AND archivo_ref=? AND estado='A'",Integer.class,c.agente().idOrganizacion(),original));
    }
    private long uso(){return jdbc.queryForObject("SELECT confirmados FROM b0_uso_almacenamiento WHERE organizacion_id=?",Long.class,c.agente().idOrganizacion());}
    private String financiera(Solicitud s){return contexto.getBean(PresupuestoIaRepository.class).operacion(c.agente().idOrganizacion(),s.operacion()).orElseThrow().estado();}
    private int medidas(Solicitud s){return jdbc.queryForObject("SELECT count(*) FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='MEDIDA'",Integer.class,c.agente().idOrganizacion(),s.operacion());}
    private static Path baseEnsayo(){
        // El runner acota esta propiedad a work/c6-tmp; otros entornos usan su temporal real.
        return Path.of(System.getProperty("jdk.net.unixdomain.tmpdir",System.getProperty("java.io.tmpdir")))
                .toAbsolutePath().normalize().resolve("brox-c6-disco");
    }
    private static Path privado(String clave){Path p=RAIZ.resolve(clave).normalize();if(!p.startsWith(RAIZ)||p.equals(RAIZ))throw new IllegalStateException("Ruta fuera del ensayo TEST.");return p;}
    private static String clave(){return "disco-test-"+UUID.randomUUID();}
    private static void fueraSql(){assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"El banco no suspende una TX padre.");}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static byte[] png(int ancho,int alto,int color)throws IOException{var imagen=new BufferedImage(ancho,alto,BufferedImage.TYPE_INT_RGB);
        for(int x=0;x<ancho;x++)for(int y=0;y<alto;y++)imagen.setRGB(x,y,color);
        try(var out=new ByteArrayOutputStream()){assertTrue(ImageIO.write(imagen,"png",out));return out.toByteArray();}}
    private void principal(Actor actor,ContextoSesion sid){EvidenciaC4DePrueba.actor(actor);var actual=new TransactionTemplate(gestor)
            .execute(t->contexto.getBean(ContextosService.class).exigirContexto(sid.referenciaSesion(),sid.revisionContexto(),actor));
        var request=new SesionDeRequest(new TokenService("").de(actual),actual);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(request,null,List.of()));}

    @TestConfiguration static class ConfiguracionTest {@Bean AvisosDiscoTest avisoDiscoTest(){return new AvisosDiscoTest();}}
    /** Fuente legal SINTÉTICA server TEST; E44 captura/presenta/valida por sus métodos reales. */
    static final class AvisosDiscoTest implements AvisosTratamientoMaterialIa {
        private Actor autorizado;private long propiedad;private DestinoTratamiento destino;private String referencia;
        boolean usoRevocado;
        void permitir(Actor actor,long propiedad,DestinoTratamiento destino){this.autorizado=actor;this.propiedad=propiedad;this.destino=destino;referencia="AVISO_DISCO_TEST_"+UUID.randomUUID();usoRevocado=false;}
        private boolean permite(AmbitoTratamiento a,Actor actor){return autorizado!=null&&autorizado.equals(actor)&&
                a.solicitud().accion()==Accion.MEJORAR_FOTO&&a.solicitud().recurso().equals(new MaterialIaService.Recurso("PROPIEDAD",Long.toString(propiedad)));}
        public Optional<Contenido> vigente(AmbitoTratamiento a,Actor actor){return permite(a,actor)?Optional.of(new Contenido(referencia,1,
                "TEST SINTETICO SIN BASE LEGAL PRODUCTIVA",Set.of(Accion.MEJORAR_FOTO),a.categorias(),"PROCESADOR SINTETICO TEST",
                "TEST-1","Condiciones TEST sin red","FUENTE_SERVIDOR_TEST",Idempotencia.huellaExacta("Condiciones TEST sin red"),Set.of(destino))):Optional.empty();}
        public Optional<Legitimacion> legitimacionActual(AmbitoTratamiento a,Contenido aviso,Actor actor){return permite(a,actor)?Optional.of(legitimacion(a,aviso)):Optional.empty();}
        public Optional<Legitimacion> legitimacionUsoArtefacto(AmbitoTratamiento a,Contenido historico,Actor actor){return !usoRevocado&&permite(a,actor)?Optional.of(legitimacion(a,historico)):Optional.empty();}
        private Legitimacion legitimacion(AmbitoTratamiento a,Contenido aviso){return new Legitimacion("BASE_USO_TEST","FUENTE_TEST_NO_LEGAL",
                Idempotencia.huellaExacta("LEGITIMACION TEST NO PRODUCTIVA"),a.solicitud().recurso(),Set.of(Accion.MEJORAR_FOTO),a.categorias(),aviso.proveedorLegal(),aviso.condicionesVersion());}
    }
}
