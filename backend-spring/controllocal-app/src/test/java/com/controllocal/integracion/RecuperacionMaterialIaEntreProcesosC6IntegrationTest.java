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
import com.controllocal.service.materialia.*;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.materialia.MaterialIaService.Solicitud;
import com.controllocal.service.materialia.MaterialIaService.Recurso;
import com.controllocal.service.materialia.FuentesMaterialIa.*;
import com.controllocal.service.materialia.ControlesMaterialIa.*;
import com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa.AmbitoTratamiento;
import com.controllocal.service.planes.DerechosDePlanB0;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.soporte.*;
import com.controllocal.web.almacen.*;
import com.controllocal.web.seguridad.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * T4: dos JVM, PG y disco reales. A termina abruptamente después de recepción
 * comprometida y antes de QC. B no puede generar y no recibe bytes/Salida de A.
 * Procesador, QC, financiación y acreditación legal son SINTÉTICOS TEST sin red.
 * No acredita proveedor, calibración, fidelidad, R2 o resistencia a perder el disco.
 */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
class RecuperacionMaterialIaEntreProcesosC6IntegrationTest {
    private static final String PROVEEDOR="DERIVADA_TEST_SIN_RED";
    private static final ObjectMapper JSON=new ObjectMapper();

    @Test void otraJvmRecuperaElMismoObjetoSinSalidaNiGeneracionDelProcesoAnterior()throws Exception{
        // Valida la frontera ANTES de lanzar cualquier JVM que escriba.
        BaseDeDatosDePruebas.registrar((nombre,valor)->valor.get());
        Path base=Path.of(System.getProperty("jdk.net.unixdomain.tmpdir",System.getProperty("java.io.tmpdir")))
                .toAbsolutePath().normalize().resolve("brox-c6-procesos");
        Files.createDirectories(base);Path ensayo=Files.createTempDirectory(base,"recepcion-").toRealPath();
        Process a=null,b=null;
        try{
            a=lanzar("A",ensayo);esperarArchivo(a,ensayo.resolve("recepcion.json"),Duration.ofMinutes(4));
            var recibida=JSON.readTree(Files.readString(ensayo.resolve("recepcion.json")));
            assertEquals(a.pid(),recibida.path("pid").asLong());
            assertEquals("RECEPCION_COMPROMETIDA_ANTES_QC",recibida.path("frontera").asText());
            assertEquals(1,cuenta(ensayo,"GENERACION"));assertEquals(0,cuenta(ensayo,"QC"));
            assertTrue(a.isAlive(),"A debe seguir detenido en el puerto TEST previo a QC.");
            // Sólo este hijo conocido. No shutdown de Spring ni finally que pueda reparar A.
            a.destroyForcibly();assertTrue(a.waitFor(20,TimeUnit.SECONDS));assertFalse(a.isAlive());
            b=lanzar("B",ensayo);assertTrue(b.waitFor(240,TimeUnit.SECONDS),"B no terminó; ver su log privado de ensayo.");
            assertEquals(0,b.exitValue(),"La recuperación B debe superar sus aserciones reales.");
            var finalizada=JSON.readTree(Files.readString(ensayo.resolve("recuperacion.json")));
            assertEquals(b.pid(),finalizada.path("pid").asLong());
            assertEquals("DISPONIBLE",finalizada.path("estado").asText());
            assertEquals(recibida.path("huella").asText(),finalizada.path("huella").asText());
            assertEquals(recibida.path("archivo").asText(),finalizada.path("archivo").asText());
            assertEquals(recibida.path("namespace").asText(),finalizada.path("namespace").asText());
            assertEquals(recibida.path("cuotaConfirmada").asLong(),finalizada.path("cuotaConfirmada").asLong());
            assertEquals("LIQUIDADA",finalizada.path("financiera").asText());
            assertEquals(1,cuenta(ensayo,"GENERACION"));assertEquals(1,cuenta(ensayo,"QC"));
            assertTrue(finalizada.path("originalIntacto").asBoolean());
            assertTrue(finalizada.path("sesionNueva").asBoolean());
        }finally{terminar(a);terminar(b);}
    }

    private static Process lanzar(String modo,Path ensayo)throws IOException{
        String java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        if(!Files.isRegularFile(Path.of(java)))java=Path.of(System.getProperty("java.home"),"bin","java").toString();
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var comando=new ArrayList<>(List.of(java,"-Djava.awt.headless=true"));
        String temporalNio=System.getProperty("jdk.net.unixdomain.tmpdir");
        if(temporalNio!=null&&!temporalNio.isBlank())comando.add("-Djdk.net.unixdomain.tmpdir="+temporalNio);
        Collections.addAll(comando,"-cp",cp,Trabajador.class.getName(),modo,ensayo.toString());
        return new ProcessBuilder(comando)
                .redirectErrorStream(true).redirectOutput(ensayo.resolve("jvm-"+modo+".log").toFile()).start();
    }
    private static void esperarArchivo(Process p,Path archivo,Duration plazo)throws Exception{
        long limite=System.nanoTime()+plazo.toNanos();
        while(System.nanoTime()<limite){
            if(Files.isRegularFile(archivo))return;
            assertTrue(p.isAlive(),"A terminó antes de recepción; ver su log privado de ensayo.");
            TimeUnit.MILLISECONDS.sleep(25);
        }
        fail("No se acreditó recepción comprometida antes del plazo técnico de ensayo.");
    }
    private static void terminar(Process p)throws InterruptedException{
        if(p!=null&&p.isAlive()){p.destroyForcibly();assertTrue(p.waitFor(20,TimeUnit.SECONDS));}
    }
    private static long cuenta(Path ensayo,String etapa)throws IOException{
        Path p=ensayo.resolve("inferencias-test.txt");
        if(!Files.exists(p))return 0;
        return Files.readAllLines(p).stream().filter(etapa::equals).count();
    }
    private static void marcar(Path ensayo,String etapa){
        try(var f=FileChannel.open(ensayo.resolve("inferencias-test.txt"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND);
            var lock=f.lock()){
            ByteBuffer b=java.nio.charset.StandardCharsets.UTF_8.encode(etapa+"\n");while(b.hasRemaining())f.write(b);f.force(true);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static void escribir(Path ensayo,String nombre,Map<String,?> datos)throws Exception{
        Path temporal=ensayo.resolve(nombre+".tmp"),destino=ensayo.resolve(nombre);
        assertTrue(temporal.normalize().startsWith(ensayo));assertTrue(destino.normalize().startsWith(ensayo));
        Files.writeString(temporal,JSON.writeValueAsString(datos));Files.move(temporal,destino,StandardCopyOption.ATOMIC_MOVE);
    }

    /** Única entrada ejecutable de hijos TEST; no se empaqueta como fuente Main. */
    public static final class Trabajador {
        public static void main(String[] argumentos)throws Exception{
            if(argumentos.length!=2||!Set.of("A","B").contains(argumentos[0]))throw new IllegalArgumentException("Ensayo TEST inválido.");
            Path ensayo=Path.of(argumentos[1]).toRealPath();boolean primera="A".equals(argumentos[0]);
            Path raiz=ensayo.resolve("almacen");Files.createDirectories(raiz);
            Map<String,Object> propiedades=new HashMap<>();
            BaseDeDatosDePruebas.registrar((nombre,valor)->propiedades.put(nombre,valor.get()));
            propiedades.putAll(Map.of("server.address","127.0.0.1","server.port","0",
                    "spring.datasource.hikari.maximum-pool-size","4","controllocal.almacen.proveedor","DISCO",
                    "controllocal.almacen.directorio",raiz.toString(),"controllocal.ia.gasto-externo-habilitado","false",
                    "controllocal.material-ia.conservacion.habilitada","false"));
            var app=new SpringApplication(ControlLocalApplication.class,ConfiguracionProcesoTest.class);
            app.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("ensayo-c6-procesos",propiedades)));
            try(var contexto=app.run()){
                var disco=contexto.getBean(AlmacenDocumentos.class);assertInstanceOf(AlmacenDisco.class,disco);
                assertEquals(1,contexto.getBeansOfType(AlmacenDocumentos.class).size());
                assertFalse(contexto.getBean(PresupuestoIaService.class).gastoExternoHabilitado());
                assertEquals(1,contexto.getBeansOfType(FuentesMaterialIa.class).size());
                assertInstanceOf(FuentesMaterialIaCore.class,contexto.getBean(FuentesMaterialIa.class));
                if(primera)generarYDetener(contexto,ensayo,disco);else recuperar(contexto,ensayo,disco);
            }
        }
    }

    private static void generarYDetener(ConfigurableApplicationContext ctx,Path ensayo,AlmacenDocumentos disco)throws Exception{
        var jdbc=ctx.getBean(JdbcTemplate.class);var gestor=ctx.getBean(PlatformTransactionManager.class);
        var origenes=origenes(ctx);var equipo=origenes.empresa();
        var configuracion=new DerivadasMaterialIaDePrueba(ctx,jdbc,gestor);
        configuracion.prepararAlmacenamiento(equipo.agente());var destino=configuracion.destino();
        assertEquals(PROVEEDOR,destino.proveedor());
        var actor=equipo.agente();var sid=origenes.sesion(actor);principal(ctx,actor,sid);
        long propiedad=origenes.propiedad(actor,"Recuperación de foto en prospección");
        ctx.getBean(ProspeccionService.class).registrar(new ProspeccionService.DatosProspeccion(propiedad,"Prospección explícita TEST de recuperación",null),actor);
        byte[] original=png(80,60,0x112233);
        var foto=ctx.getBean(GaleriaDeFotos.class).agregar(propiedad,"original-procesos-test.png",
                Base64.getEncoder().encodeToString(original),FotoPropiedadService.TOPE_UNIVERSAL,actor);
        UUID refOriginal=UUID.fromString(foto.clave());String huellaOriginal=sha(original);
        ctx.getBean(AvisosProcesoTest.class).permitir(actor,propiedad,refOriginal,destino);
        ResultadosMaterialIa real=ctx.getBean(ResultadosMaterialIa.class);
        // Interceptor exclusivamente TEST: la llamada real retorna con recepción/cuota comprometidas.
        ResultadosMaterialIa frontera=(ResultadosMaterialIa)Proxy.newProxyInstance(ResultadosMaterialIa.class.getClassLoader(),
                new Class<?>[]{ResultadosMaterialIa.class},(proxy,metodo,args)->{
                    Object respuesta;
                    try{respuesta=metodo.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
                    if("recibirResultado".equals(metodo.getName())){
                        fueraSql();long org=(long)args[0];UUID job=(UUID)args[1];String clave=(String)args[2];
                        var custodia=ctx.getBean(RegistroMaterialIaRepository.class).custodia(org,job).orElseThrow();
                        assertEquals("VERIFICADA",custodia.estado());
                        assertEquals(0,jdbc.queryForObject("select count(*) from material_ia_evento where organizacion_id=? and id_operacion=? and estado='QC_CLAIM'",Integer.class,org,job));
                        var recibida=real.recuperarRecibido(org,job,clave);
                        Map<String,Object> marca=new LinkedHashMap<>();marca.put("pid",ProcessHandle.current().pid());
                        marca.put("frontera","RECEPCION_COMPROMETIDA_ANTES_QC");marca.put("organizacion",org);marca.put("job",job.toString());
                        marca.put("archivo",custodia.archivo().toString());marca.put("huella",sha(recibida.foto()));
                        marca.put("namespace",disco.identidadTecnica());marca.put("original",refOriginal.toString());marca.put("huellaOriginal",huellaOriginal);
                        marca.put("cuotaConfirmada",cuota(jdbc,org));
                        escribir(ensayo,"recepcion.json",marca);new CountDownLatch(1).await();
                    }
                    return respuesta;
                });
        var coordinador=coordinador(ctx,ensayo,destino,frontera,true);
        var inicial=new Solicitud(UUID.randomUUID(),Accion.MEJORAR_FOTO,new Recurso("PROPIEDAD",Long.toString(propiedad)),null,refOriginal,null);
        var aviso=ctx.getBean(FuentesMaterialIaCore.class).presentarTratamiento(inicial,actor,sid);
        ctx.getBean(EvidenciasTratamientoMaterialIa.class).capturar(new EvidenciasTratamientoMaterialIa.Captura(UUID.randomUUID(),aviso.referencia(),
                aviso.versionCobertura(),EvidenciasTratamientoMaterialIa.Decision.ACEPTAR),actor,sid,Procedencia.deLaPantalla());
        var preparada=coordinador.preparar(inicial,actor,sid);
        coordinador.solicitar(new Solicitud(inicial.operacion(),inicial.accion(),inicial.recurso(),preparada.versionOrigen(),refOriginal,null),actor,sid);
        fail("El puerto TEST debe detener A antes de completar QC.");
    }

    private static void recuperar(ConfigurableApplicationContext ctx,Path ensayo,AlmacenDocumentos disco)throws Exception{
        var marca=JSON.readTree(Files.readString(ensayo.resolve("recepcion.json")));
        long org=marca.path("organizacion").asLong();UUID job=UUID.fromString(marca.path("job").asText());
        var jdbc=ctx.getBean(JdbcTemplate.class);var registro=ctx.getBean(RegistroMaterialIaRepository.class);
        var almacenada=registro.obtener(org,job).orElseThrow();var documentos=ctx.getBean(Documentos.class);
        var solicitud=documentos.comoObjeto(almacenada.solicitud(),Solicitud.class);
        assertEquals(job,solicitud.operacion());assertEquals(marca.path("namespace").asText(),disco.identidadTecnica());
        Actor actor=new Actor(org,almacenada.persona(),almacenada.rol(),almacenada.rolEfectivo());
        var destino=new DestinoTratamiento(PROVEEDOR,almacenada.perfil(),almacenada.versionPerfil());
        ((ConfigurableEnvironment)ctx.getEnvironment()).getPropertySources().addFirst(new MapPropertySource("perfil-persistido-test",
                Map.of("controllocal.ia.material.mejorar_foto.perfil",destino.perfil(),"controllocal.ia.material.mejorar_foto.version",destino.versionPerfil())));
        var sid=origenes(ctx).sesion(actor);assertNotEquals(almacenada.sesion(),sid.referenciaSesion());principal(ctx,actor,sid);
        ctx.getBean(AvisosProcesoTest.class).permitir(actor,Long.parseLong(solicitud.recurso().referencia()),solicitud.fotoOriginal(),destino);
        var real=ctx.getBean(ResultadosMaterialIa.class);long antes=cuota(jdbc,org);
        var resultado=coordinador(ctx,ensayo,destino,real,false).recuperarResultadoDurable(org,job,actor,sid);
        assertEquals("DISPONIBLE",resultado.estado());assertNotNull(resultado.archivo());assertEquals(antes,cuota(jdbc,org));
        assertEquals(UUID.fromString(marca.path("archivo").asText()),resultado.archivo());
        var archivo=real.descargar(job,resultado.archivo(),actor,sid);assertEquals(marca.path("huella").asText(),sha(archivo.bytes()));
        var lectura=ctx.getBean(AccesoArchivoService.class).resolver(solicitud.fotoOriginal().toString(),actor);
        var original=disco.abrir(lectura.claveProveedor()).orElseThrow();
        assertEquals(marca.path("huellaOriginal").asText(),sha(original.contenido()));
        assertEquals("LIQUIDADA",ctx.getBean(PresupuestoIaRepository.class).operacion(org,job).orElseThrow().estado());
        Map<String,Object> fin=new LinkedHashMap<>();fin.put("pid",ProcessHandle.current().pid());fin.put("estado",resultado.estado());
        fin.put("archivo",resultado.archivo().toString());fin.put("huella",sha(archivo.bytes()));fin.put("namespace",disco.identidadTecnica());
        fin.put("cuotaConfirmada",cuota(jdbc,org));fin.put("financiera","LIQUIDADA");fin.put("originalIntacto",true);fin.put("sesionNueva",true);
        escribir(ensayo,"recuperacion.json",fin);
    }

    private static MaterialIaServiceImpl coordinador(ConfigurableApplicationContext ctx,Path ensayo,DestinoTratamiento destino,ResultadosMaterialIa resultados,boolean primera){
        var repo=ctx.getBean(PresupuestoIaRepository.class);var documentos=ctx.getBean(Documentos.class);var autoridad=ctx.getBean(AutoridadMaterialIa.class);
        var presupuesto=new PresupuestoIaService(repo,ctx.getBean(ContextoPresupuestoB0.class),List.of(autoridad),documentos,new DerechosDePlanB0(repo,documentos),true);
        // true sólo en este objeto TEST, cuyo procesador es local. El bean Main permanece OFF.
        ProveedorMaterialIa proveedor=new ProveedorMaterialIa(){
            public String identificador(){return destino.proveedor();}
            public boolean atiende(Accion a,String p,String v){return a==Accion.MEJORAR_FOTO&&destino.perfil().equals(p)&&destino.versionPerfil().equals(v);}
            public Salida ejecutar(Peticion peticion,java.util.function.Consumer<com.controllocal.service.presupuesto.ObservacionHttpIa> observador){
                fueraSql();marcar(ensayo,"GENERACION");
                if(!primera)throw new AssertionError("B no tiene una ruta de generación permisiva.");
                assertNotNull(peticion.fotoAutorizada());
                var medida=new ResultadoFacturable(List.of(new MedicionFacturable(UUID.randomUUID(),identificador(),"SINTETICO_NO_MODELO_REAL","TEST-1",
                        List.of(new Medicion("unidades_test","u",BigDecimal.ONE,null)),List.of(new Tarifa("unidades_test","u",new BigDecimal("0.1"),BigDecimal.ONE)),null,"TEST-PROCESOS:"+peticion.claveExterna())));
                try{return new Salida(null,"image/png",png(32,32,0xff4422),"PROCESO_SINTETICO_NO_FIDELIDAD_REAL","TEST-1",medida);}
                catch(IOException e){throw new UncheckedIOException(e);}
            }
        };
        ControlesMaterialIa qc=new ControlesMaterialIa(){
            public boolean atiende(Accion a,String p,String v){return proveedor.atiende(a,p,v);}
            public Control evaluar(Solicitud s,Snapshot f,ProveedorMaterialIa.Salida salida){
                fueraSql();marcar(ensayo,"QC");assertFalse(primera,"A no debe llegar a QC antes del punto de caída.");
                return new Control(Decision.APROBADA,"QC_SINTETICO_PROCESOS",Instant.now(),Arrays.stream(Aspecto.values())
                        .map(a->new Senal(a,EstadoSenal.EVALUADA,false,"TEST SIN CONTROL REAL DE FIDELIDAD","TEST-QC:"+s.operacion()+":"+a)).toList());
            }
        };
        return new MaterialIaServiceImpl(ctx.getBean(RegistroMaterialIaRepository.class),presupuesto,ctx.getBean(ContextoPresupuestoB0.class),autoridad,
                ctx.getBean(PerfilesMaterialIa.class),documentos,List.of(proveedor),List.of(qc),List.of(resultados),ctx.getBean(PlatformTransactionManager.class));
    }
    private static OrigenesDePrueba origenes(ConfigurableApplicationContext ctx){return new OrigenesDePrueba(ctx.getBean(JdbcTemplate.class),ctx.getBean(PlatformTransactionManager.class),ctx.getBean(MotorDeCaptura.class),ctx.getBean(ContextosService.class));}
    private static long cuota(JdbcTemplate jdbc,long org){return jdbc.queryForObject("select confirmados from b0_uso_almacenamiento where organizacion_id=?",Long.class,org);}
    private static void principal(ConfigurableApplicationContext ctx,Actor actor,ContextoSesion sid){
        EvidenciaC4DePrueba.actor(actor);var actual=new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class))
                .execute(t->ctx.getBean(ContextosService.class).exigirContexto(sid.referenciaSesion(),sid.revisionContexto(),actor));
        var request=new SesionDeRequest(new TokenService("").de(actual),actual);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(request,null,List.of()));
    }
    private static void fueraSql(){assertFalse(TransactionSynchronizationManager.isActualTransactionActive());}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static byte[] png(int ancho,int alto,int color)throws IOException{
        var imagen=new BufferedImage(ancho,alto,BufferedImage.TYPE_INT_RGB);
        for(int x=0;x<ancho;x++)for(int y=0;y<alto;y++)imagen.setRGB(x,y,color);
        try(var out=new ByteArrayOutputStream()){assertTrue(ImageIO.write(imagen,"png",out));return out.toByteArray();}
    }

    @TestConfiguration static class ConfiguracionProcesoTest {@Bean AvisosProcesoTest avisosProcesoTest(){return new AvisosProcesoTest();}}
    static final class AvisosProcesoTest implements AvisosTratamientoMaterialIa {
        private Actor actor;private long propiedad;private UUID original;private DestinoTratamiento destino;
        void permitir(Actor a,long p,UUID o,DestinoTratamiento d){actor=a;propiedad=p;original=o;destino=d;}
        private boolean permite(AmbitoTratamiento a,Actor solicitante){return actor!=null&&actor.equals(solicitante)&&a.solicitud().accion()==Accion.MEJORAR_FOTO&&a.solicitud().recurso().equals(new Recurso("PROPIEDAD",Long.toString(propiedad)))&&original.equals(a.solicitud().fotoOriginal());}
        public Optional<Contenido> vigente(AmbitoTratamiento a,Actor solicitante){return permite(a,solicitante)?Optional.of(new Contenido("AVISO_PROCESOS_TEST_"+original,1,
                "TEST SINTETICO SIN BASE LEGAL PRODUCTIVA",Set.of(Accion.MEJORAR_FOTO),a.categorias(),"PROCESADOR SINTETICO TEST","TEST-1",
                "Condiciones TEST sin red","FUENTE_SERVIDOR_TEST",Idempotencia.huellaExacta("Condiciones TEST sin red"),Set.of(destino))):Optional.empty();}
        public Optional<Legitimacion> legitimacionActual(AmbitoTratamiento a,Contenido aviso,Actor solicitante){return permite(a,solicitante)?Optional.of(legitimacion(a,aviso)):Optional.empty();}
        public Optional<Legitimacion> legitimacionUsoArtefacto(AmbitoTratamiento a,Contenido aviso,Actor solicitante){return permite(a,solicitante)?Optional.of(legitimacion(a,aviso)):Optional.empty();}
        private Legitimacion legitimacion(AmbitoTratamiento a,Contenido aviso){return new Legitimacion("BASE_USO_TEST","FUENTE_TEST_NO_LEGAL",Idempotencia.huellaExacta("LEGITIMACION TEST NO PRODUCTIVA"),a.solicitud().recurso(),Set.of(Accion.MEJORAR_FOTO),a.categorias(),aviso.proveedorLegal(),aviso.condicionesVersion());}
    }
}
