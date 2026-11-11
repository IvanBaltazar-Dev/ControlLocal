package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.materialia.RegistroMaterialIaRepository;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.documentos.*;
import com.controllocal.service.documentos.PiezasComercialesService.*;
import com.controllocal.service.materialia.*;
import com.controllocal.service.materialia.AvisosTratamientoMaterialIa.*;
import com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa.AmbitoTratamiento;
import com.controllocal.service.soporte.*;
import com.controllocal.web.almacen.*;
import com.controllocal.web.seguridad.*;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.controllocal.service.PropiedadUniversalService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T4: dos cuentas/SID y transacciones físicas de actor; inspector PG independiente.
 * El pool admite además las transacciones cortas del journal139, sin simular su durabilidad.
 * Core/C1/E44/B0/SQL/journal reales; proveedor/QC, almacén y compositor TEST sin red.
 * No acredita fidelidad de IA real, render Chrome ni transcurso real de siete días.
 */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=4")
class PiezasDerivadasConcurrenciaC6IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PiezasComercialesService piezas;
    @Autowired ResultadosMaterialIa resultados;
    @MockitoSpyBean PlatformTransactionManager gestor;
    @MockitoSpyBean RegistroMaterialIaRepository jobs;
    @MockitoSpyBean ComandoIdempotenteRepository comandosLocks;
    @MockitoBean AlmacenDocumentos almacen;
    @MockitoBean CompositorPieza compositor;
    @MockitoBean AvisosTratamientoMaterialIa avisos;
    private final Map<String,AlmacenDocumentos.ArchivoDescargado> objetos=new ConcurrentHashMap<>();
    private final AtomicLong pidA=new AtomicLong(),pidB=new AtomicLong(),txA=new AtomicLong(),txB=new AtomicLong();
    private final List<Long> locksA=Collections.synchronizedList(new ArrayList<>()),locksB=Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean pausarCopia=new AtomicBoolean();
    private final AtomicBoolean verificarPrimerIo=new AtomicBoolean();
    private final AtomicInteger verificacionesIo=new AtomicInteger();
    private final Set<UUID> copiasCalientes=new HashSet<>();
    private final CountDownLatch primeraCopia=new CountDownLatch(1),continuarCopia=new CountDownLatch(1);
    private final CountDownLatch metodoATerminado=new CountDownLatch(1),confirmarA=new CountDownLatch(1);
    private final String hiloA="C6-T4-A-"+UUID.randomUUID(),hiloB="C6-T4-B-"+UUID.randomUUID();
    private final String referenciaAviso="AVISO_TEST_T4_"+UUID.randomUUID();
    private FormalizacionesDePrueba b;
    private DerivadasMaterialIaDePrueba ia;
    private OrigenesDePrueba.Equipo equipo;
    private ContextoSesion sidA,sidB;
    private long propiedad,encargoA,encargoB,primeraLlave;
    private byte[] pdf;
    private final List<UUID> originales=new ArrayList<>();

    @BeforeEach void preparar(TestInfo info)throws Exception{
        EvidenciaC4DePrueba.iniciar(info);
        when(almacen.proveedor()).thenReturn("TEST");
        when(almacen.identidadTecnica()).thenReturn(AlmacenDocumentos.huellaIdentidad("TEST","piezas-t4-"+UUID.randomUUID()));
        when(almacen.abrir(anyString())).thenAnswer(i->{
            if(Thread.currentThread().getName().equals(hiloA)&&verificarPrimerIo.compareAndSet(true,false)){
                for(UUID original:originales){long llave=llaveCopia(original);
                    String modo=copiasCalientes.contains(original)?"ShareLock":"ExclusiveLock";
                    assertTrue(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE pid=pg_backend_pid() AND locktype='advisory' AND granted AND mode=? AND classid::bigint=? AND objid::bigint=? AND objsubid=1)",
                            Boolean.class,modo,(llave>>>32)&0xffffffffL,llave&0xffffffffL),"TODOS los COPY deben estar tomados antes del primer I/O.");}
                verificacionesIo.incrementAndGet();
            }
            return Optional.ofNullable(objetos.get((String)i.getArgument(0)));
        });
        doAnswer(i->{String k=i.getArgument(0);byte[] datos=i.getArgument(1);
            objetos.put(k,new AlmacenDocumentos.ArchivoDescargado(datos.clone(),k.substring(k.lastIndexOf('/')+1),i.getArgument(2)));return null;
        }).when(almacen).guardarEnClave(anyString(),any(),anyString());
        doAnswer(i->{objetos.remove((String)i.getArgument(0));return null;}).when(almacen).eliminar(anyString());
        try(var documento=new PDDocument();var salida=new ByteArrayOutputStream()){
            documento.addPage(new PDPage());documento.save(salida);pdf=salida.toByteArray();
        }
        when(compositor.configurado()).thenReturn(true);
        when(compositor.componer(anyMap(),anyMap(),anyString())).thenReturn(new CompositorPieza.Binario(pdf,"application/pdf","pdf"));
        b=new FormalizacionesDePrueba(contexto);ia=new DerivadasMaterialIaDePrueba(contexto,jdbc,gestor);
        when(avisos.vigente(any(),any())).thenAnswer(i->{AmbitoTratamiento a=i.getArgument(0);
            return Optional.of(new Contenido(referenciaAviso,1,"Procesamiento sintético TEST sin red",
                    Set.of(a.solicitud().accion()),a.categorias(),"PROCESADOR SINTÉTICO TEST","TEST-1","Condiciones TEST",
                    "FUENTE_SERVIDOR_TEST",Idempotencia.huellaExacta("Condiciones TEST"),Set.of(ia.destino())));
        });
        when(avisos.legitimacionActual(any(),any(),any())).thenAnswer(i->Optional.of(legitimacion(i.getArgument(0),i.getArgument(1))));
        when(avisos.legitimacionUsoArtefacto(any(),any(),any())).thenAnswer(i->Optional.of(legitimacion(i.getArgument(0),i.getArgument(1))));
        var origenes=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class));
        equipo=origenes.empresa();sidA=b.sesiones.de(equipo.agente());sidB=b.sesiones.de(equipo.colega());
        assertNotEquals(equipo.agente().idPersona(),equipo.colega().idPersona());
        assertNotEquals(sidA.referenciaSesion(),sidB.referenciaSesion());
        principal(equipo.agente(),sidA);ia.prepararAlmacenamiento(equipo.agente());
        registrarDosEncargos();
        // Instrumentación TEST: delega al gestor real y observa la conexión de cada nueva TX B.
        doAnswer(i->{TransactionStatus estado=(TransactionStatus)i.callRealMethod();
            if(Thread.currentThread().getName().equals(hiloB)&&estado.isNewTransaction()){
                pidB.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));
                txB.set(jdbc.queryForObject("SELECT txid_current()",Long.class));
                jdbc.execute("SET LOCAL lock_timeout='15s'");
            }
            return estado;
        }).when(gestor).getTransaction(any());
        doAnswer(i->{i.callRealMethod();long llave=i.getArgument(0);
            if(Thread.currentThread().getName().equals(hiloA))locksA.add(llave);
            if(Thread.currentThread().getName().equals(hiloB))locksB.add(llave);
            return null;
        }).when(jobs).bloquearCompartido(anyLong());
        var repositorioReal=mockingDetails(comandosLocks).getMockCreationSettings().getDefaultAnswer();
        // Spring Data es un proxy: conservar su delegación real, no invocar un método abstracto.
        doAnswer(i->{repositorioReal.answer(i);long llave=i.getArgument(0);
            if(Thread.currentThread().getName().equals(hiloA)&&!originales.isEmpty()
                    &&llave==llaveCopia(originales.getFirst())&&pausarCopia.compareAndSet(true,false)){
                primeraCopia.countDown();esperar(continuarCopia,"No se liberó el primer fence de copia.");
            }return null;
        }).when(comandosLocks).bloquearIdentidad(anyLong());
    }
    @AfterEach void terminar(){continuarCopia.countDown();confirmarA.countDown();SecurityContextHolder.clearContext();EvidenciaC4DePrueba.limpiar();}
    private static Legitimacion legitimacion(AmbitoTratamiento a,Contenido aviso){
        return new Legitimacion("LEGITIMACION_USO_TEST","SERVIDOR_TEST",Idempotencia.huellaExacta("USO_TEST"),
                a.solicitud().recurso(),Set.of(a.solicitud().accion()),a.categorias(),aviso.proveedorLegal(),aviso.condicionesVersion());
    }
    private void registrarDosEncargos(){
        String documento="79000001";
        long titular=contexto.getBean(PropietarioService.class).registrar(new PropietarioService.DatosPropietario(
                "N","D",documento,"Propietario TEST T4","9"+documento,"t4@example.invalid",true,null),equipo.agente()).id();
        propiedad=contexto.getBean(PropiedadUniversalService.class).registrar(new ComandoRegistro(clave(),Procedencia.deLaPantalla(),null,
                "LOCAL_COMERCIAL","COMERCIAL","Inmueble TEST T4",
                new Ubicacion("Dirección ficticia TEST "+UUID.randomUUID(),"Miraflores",null,null,null,null,null,null,null),
                List.of(new Titular(titular,null,true)),List.of(),List.of(
                new OperacionSolicitada("VENTA",new BigDecimal("100000"),"USD",null,null,null,null,null,null,null),
                new OperacionSolicitada("ALQUILER",new BigDecimal("2500"),"PEN",null,null,null,null,null,null,null)),null),equipo.agente()).idPropiedad();
        encargoA=encargo("VENTA");encargoB=encargo("ALQUILER");
        var broker=equipo.broker();var sid=b.sesiones.de(broker);long version=b.encargos.consultar(encargoB,equipo.agente()).version();
        principal(broker,sid);
        contexto.getBean(CaptacionService.class).reasignar(encargoB,new CaptacionService.DatosReasignacion(
                equipo.colega().idRolOperativo(),equipo.agente().idRolOperativo(),version,"Dos responsables TEST de Encargos distintos"),
                broker,sid,clave(),Procedencia.deLaPantalla());
        principal(equipo.agente(),sidA);
    }
    private long encargo(String operacion){return jdbc.queryForObject("SELECT id_captacion FROM captacion WHERE organizacion_id=? AND id_propiedad=? AND motivo_operacion=?",
            Long.class,equipo.agente().idOrganizacion(),propiedad,OperacionInmobiliaria.desde(operacion).codigo());}
    private void generar(int cantidad)throws Exception{
        for(int n=0;n<cantidad;n++){
            var imagen=new BufferedImage(80,60,BufferedImage.TYPE_INT_RGB);var salida=new ByteArrayOutputStream();
            assertTrue(ImageIO.write(imagen,"png",salida));
            var foto=contexto.getBean(GaleriaDeFotos.class).agregar(propiedad,"original-test-"+n+".png",
                    Base64.getEncoder().encodeToString(salida.toByteArray()),FotoPropiedadService.TOPE_UNIVERSAL,equipo.agente());
            UUID original=UUID.fromString(foto.clave());originales.add(original);
            var caso=ia.generar(propiedad,original,equipo.agente(),sidA);var r=caso.resultado();
            assertNotNull(r.archivo());var seleccion=resultados.seleccionActual(original,equipo.agente(),sidA);
            resultados.seleccionar(original,r.operacion(),r.archivo(),seleccion.revision(),clave(),equipo.agente(),sidA);
        }
        originales.sort(Comparator.naturalOrder());primeraLlave=llaveO(originales.getFirst());
    }
    private Estado prepararPieza(Actor actor,ContextoSesion sid,long encargo,List<UUID> orden){
        principal(actor,sid);
        long fisica=jdbc.queryForObject("SELECT version_fisica FROM propiedad WHERE organizacion_id=? AND id_propiedad=?",Long.class,actor.idOrganizacion(),propiedad);
        var entrada=new Preparacion(encargo,b.encargos.consultar(encargo,actor).version(),fisica,"WEB_PROPIA",
                orden.stream().map(o->new Foto(o,null,"Fotografía TEST")).toList(),
                piezas.consultarFotosPermitidas(encargo,null,actor).versionObservada(),Formato.PDF_A4,"marfil",null,null);
        return piezas.preparar(entrada,actor,sid,clave(),Procedencia.deLaPantalla());
    }
    private void principal(Actor actor,ContextoSesion sid){
        EvidenciaC4DePrueba.actor(actor);
        var acceso=new TransactionTemplate(gestor).execute(t->contexto.getBean(ContextosService.class)
                .exigirContextoConLecturaProtegida(sid.referenciaSesion(),sid.revisionContexto(),actor));
        var request=new SesionDeRequest(new TokenService("").de(acceso),acceso);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(request,null,List.of()));
    }
    private Archivo preview(Estado pieza,Actor actor,ContextoSesion sid){return piezas.previsualizar(pieza.pieza(),pieza.versionContenido(),actor,sid,clave(),Procedencia.deLaPantalla());}
    private Future<Archivo> iniciarA(ExecutorService pool,Estado pieza){return pool.submit(()->{
        Thread.currentThread().setName(hiloA);principal(equipo.agente(),sidA);
        try{return new TransactionTemplate(gestor).execute(t->{
            pidA.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));txA.set(jdbc.queryForObject("SELECT txid_current()",Long.class));
            jdbc.execute("SET LOCAL lock_timeout='15s'");var archivo=preview(pieza,equipo.agente(),sidA);
            metodoATerminado.countDown();esperar(confirmarA,"No se autorizó el commit del padre TEST A.");return archivo;
        });}finally{SecurityContextHolder.clearContext();}
    });}
    private long llaveO(UUID original){return Long.parseUnsignedLong(Idempotencia.huellaExacta("BROX-MATERIAL-IA-ORIGINAL\u0000"+
            equipo.agente().idOrganizacion()+"\u0000"+original).substring(0,16),16);}
    private long llaveCopia(UUID original){return Long.parseUnsignedLong(Idempotencia.huellaExacta("COPIA-FOTO\u0000"+
            equipo.agente().idOrganizacion()+"\u0000"+original).substring(0,16),16);}
    private long llaveJ(UUID job){return Long.parseUnsignedLong(Idempotencia.huellaExacta("BROX-MATERIAL-IA-LOCK\u0000"+
            equipo.agente().idOrganizacion()+"\u0000"+job).substring(0,16),16);}
    private void esperarLock(JdbcTemplate inspector,long llave)throws Exception{
        long fin=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean observado=false;
        while(System.nanoTime()<fin){
            if(pidB.get()!=0&&Boolean.TRUE.equals(inspector.queryForObject("""
                SELECT ?=ANY(pg_blocking_pids(?)) AND EXISTS(
                  SELECT 1 FROM pg_locks WHERE pid=? AND locktype='advisory' AND NOT granted
                    AND mode='ExclusiveLock' AND classid::bigint=? AND objid::bigint=? AND objsubid=1)
                """,Boolean.class,(int)pidA.get(),(int)pidB.get(),(int)pidB.get(),(llave>>>32)&0xffffffffL,llave&0xffffffffL))){observado=true;break;}
            Thread.sleep(20);
        }
        assertTrue(observado,"La conexión B debe esperar el fence exacto de A, no otra Cuenta ni el pool.");
        assertNotEquals(pidA.get(),pidB.get());assertNotEquals(txA.get(),txB.get());
    }
    private static void esperar(CountDownLatch latch,String mensaje){try{assertTrue(latch.await(20,TimeUnit.SECONDS),mensaje);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    private static String clave(){return "C6-T4-"+UUID.randomUUID();}

    @Test void cambiarSeleccionEsperaHastaElCommitDeLaPiezaNoSoloHastaElUltimoChequeo()throws Exception{
        generar(1);UUID original=originales.getFirst();var estado=prepararPieza(equipo.agente(),sidA,encargoA,originales);
        principal(equipo.colega(),sidB);var vigente=resultados.seleccionActual(original,equipo.colega(),sidB);
        var hikari=(HikariDataSource)dataSource;var pool=Executors.newFixedThreadPool(2);
        try(var conexion=DriverManager.getConnection(hikari.getJdbcUrl(),hikari.getUsername(),hikari.getPassword())){
            conexion.setReadOnly(true);var inspector=new JdbcTemplate(new SingleConnectionDataSource(conexion,true));
            var a=iniciarA(pool,estado);esperar(metodoATerminado,"A no llegó al intervalo posterior al último chequeo.");
            var cambio=pool.submit(()->{Thread.currentThread().setName(hiloB);principal(equipo.colega(),sidB);
                try{return resultados.seleccionar(original,null,null,vigente.revision(),clave(),equipo.colega(),sidB);}
                finally{SecurityContextHolder.clearContext();}});
            esperarLock(inspector,primeraLlave);assertFalse(cambio.isDone());
            confirmarA.countDown();var archivo=a.get(20,TimeUnit.SECONDS);var nueva=cambio.get(20,TimeUnit.SECONDS);
            assertNull(nueva.archivo());assertEquals(vigente.revision()+1,nueva.revision());
            principal(equipo.agente(),sidA);assertArrayEquals(pdf,piezas.descargar(archivo.referencia(),equipo.agente()).contenido());
            assertEquals(archivo.referencia(),piezas.consultarEstado(estado.pieza(),equipo.agente()).archivoPrivado());
        }finally{confirmarA.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(25,TimeUnit.SECONDS));}
    }

    @Test void dosLectoresConFotosInversasCompartenOLuegoJAntesDeCopiasYB0()throws Exception{
        generar(2);var aEstado=prepararPieza(equipo.agente(),sidA,encargoA,originales);
        var inverso=new ArrayList<>(originales);Collections.reverse(inverso);
        var bEstado=prepararPieza(equipo.colega(),sidB,encargoB,inverso);
        principal(equipo.agente(),sidA);
        var todos=new ArrayList<>(originales.stream().map(this::llaveO).toList());
        todos.addAll(resultados.consultarSelecciones(originales,equipo.agente()).stream()
                .map(ResultadosMaterialIa.Seleccion::operacion).sorted().map(this::llaveJ).toList());
        var componeB=new CountDownLatch(1);var continuarB=new CountDownLatch(1);
        when(compositor.componer(anyMap(),anyMap(),anyString())).thenAnswer(i->{
            if(Thread.currentThread().getName().equals(hiloB)){componeB.countDown();esperar(continuarB,"No se liberó el compositor B.");}
            return new CompositorPieza.Binario(pdf,"application/pdf","pdf");
        });
        var hikari=(HikariDataSource)dataSource;var pool=Executors.newFixedThreadPool(2);
        try(var conexion=DriverManager.getConnection(hikari.getJdbcUrl(),hikari.getUsername(),hikari.getPassword())){
            conexion.setReadOnly(true);var inspector=new JdbcTemplate(new SingleConnectionDataSource(conexion,true));
            var a=iniciarA(pool,aEstado);esperar(metodoATerminado,"A no llegó al intervalo anterior al commit.");
            var otro=pool.submit(()->{Thread.currentThread().setName(hiloB);principal(equipo.colega(),sidB);
                try{return preview(bEstado,equipo.colega(),sidB);}finally{SecurityContextHolder.clearContext();}});
            esperar(componeB,"B debe componer con O/J compartidos mientras A sigue antes del commit.");
            assertNotEquals(pidA.get(),pidB.get());assertNotEquals(txA.get(),txB.get());
            var esperados=originales.stream().map(this::llaveO).toList();
            assertEquals(esperados,List.copyOf(locksA).subList(0,2));
            assertEquals(esperados,List.copyOf(locksB).subList(0,2),"El orden del cliente no altera O antes de J.");
            assertEquals(todos,List.copyOf(locksA));assertEquals(todos,List.copyOf(locksB));
            for(long llave:todos)assertEquals(2,inspector.queryForObject("SELECT count(*) FROM pg_locks WHERE pid IN (?,?) AND locktype='advisory' AND granted AND mode='ShareLock' AND classid::bigint=? AND objid::bigint=? AND objsubid=1",
                    Integer.class,(int)pidA.get(),(int)pidB.get(),(llave>>>32)&0xffffffffL,llave&0xffffffffL));
            // La cuota sí se escribe: A confirma antes de permitir a B almacenar, sin inventar paralelismo financiero.
            confirmarA.countDown();assertNotNull(a.get(20,TimeUnit.SECONDS).referencia());
            continuarB.countDown();assertNotNull(otro.get(20,TimeUnit.SECONDS).referencia());
        }finally{continuarB.countDown();confirmarA.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(25,TimeUnit.SECONDS));}
    }

    @Test void previsualizarMismoActorYSidAlcanzaCompositorMientrasElOtroPadreNoConfirma()throws Exception{
        generar(1);
        var aEstado=prepararPieza(equipo.agente(),sidA,encargoA,originales);
        var bEstado=prepararPieza(equipo.agente(),sidA,encargoA,originales);
        assertFalse(aEstado.pieza().equals(bEstado.pieza()),"Las preparaciones TEST deben ser distintas.");
        assertTrue(aEstado.archivoPrivado()==null&&bEstado.archivoPrivado()==null,"Ambas piezas deben requerir composición nueva.");
        var componeB=new CountDownLatch(1);var continuarB=new CountDownLatch(1);
        when(compositor.componer(anyMap(),anyMap(),anyString())).thenAnswer(i->{
            if(Thread.currentThread().getName().equals(hiloB)){componeB.countDown();esperar(continuarB,"No se liberó el compositor B.");}
            return new CompositorPieza.Binario(pdf,"application/pdf","pdf");
        });
        var hikari=(HikariDataSource)dataSource;var pool=Executors.newFixedThreadPool(2);
        try(var conexion=DriverManager.getConnection(hikari.getJdbcUrl(),hikari.getUsername(),hikari.getPassword())){
            conexion.setReadOnly(true);var inspector=new JdbcTemplate(new SingleConnectionDataSource(conexion,true));
            var a=iniciarA(pool,aEstado);esperar(metodoATerminado,"A no llegó al intervalo anterior al commit.");
            var otro=pool.submit(()->{Thread.currentThread().setName(hiloB);principal(equipo.agente(),sidA);
                try{return preview(bEstado,equipo.agente(),sidA);}finally{SecurityContextHolder.clearContext();}});
            esperar(componeB,"El mismo actor/SID debe llegar a componer mientras A conserva su transacción.");
            assertFalse(a.isDone(),"A debe conservar su padre sin commit al entrar B en compositor.");
            assertTrue(pidA.get()>0&&pidB.get()>0&&txA.get()>0&&txB.get()>0);
            assertNotEquals(pidA.get(),pidB.get());assertNotEquals(txA.get(),txB.get());
            assertEquals(2,inspector.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE pid IN (?,?) AND xact_start IS NOT NULL",
                    Integer.class,(int)pidA.get(),(int)pidB.get()),"Ambos padres físicos deben seguir abiertos.");
            assertEquals(0,inspector.queryForObject("SELECT cardinality(pg_blocking_pids(?))",
                    Integer.class,(int)pidB.get()),"B no debe esperar un lock PG dentro de la barrera del compositor.");
            // La admisión de cuota sigue siendo exclusiva: confirmar A antes de permitir almacenar B.
            confirmarA.countDown();var archivoA=a.get(20,TimeUnit.SECONDS);
            continuarB.countDown();var archivoB=otro.get(20,TimeUnit.SECONDS);
            assertFalse(archivoA.referencia().equals(archivoB.referencia()),"Cada pieza debe conservar su propio archivo.");
            principal(equipo.agente(),sidA);
            assertArrayEquals(pdf,piezas.descargar(archivoA.referencia(),equipo.agente()).contenido());
            assertArrayEquals(pdf,piezas.descargar(archivoB.referencia(),equipo.agente()).contenido());
        }finally{continuarB.countDown();confirmarA.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(25,TimeUnit.SECONDS));}
    }

    @Test void unaLecturaCompartidaNoSatisfaceLaGuardaExclusivaDeUnaMutacion()throws Exception{
        generar(1);principal(equipo.agente(),sidA);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(gestor).execute(t->{
            var snapshot=resultados.bloquearSeleccionesParaLectura(originales,equipo.agente());
            resultados.exigirMismasSeleccionesParaLectura(snapshot,equipo.agente());
            resultados.exigirMismasSelecciones(snapshot,equipo.agente());return null;
        }));
        assertDoesNotThrow(()->new TransactionTemplate(gestor).execute(t->{
            var snapshot=resultados.bloquearSelecciones(originales,equipo.agente());
            resultados.exigirMismasSelecciones(snapshot,equipo.agente());return null;
        }));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void prepararFotosInversasTomaTodasLasCopiasOrdenadasAntesDeB0(boolean mezclaCalienteYFria)throws Exception{
        generar(2);
        for(UUID original:originales){var actual=resultados.seleccionActual(original,equipo.agente(),sidA);
            resultados.seleccionar(original,null,null,actual.revision(),clave(),equipo.agente(),sidA);}
        if(mezclaCalienteYFria){prepararPieza(equipo.agente(),sidA,encargoA,List.of(originales.getLast()));copiasCalientes.add(originales.getLast());}
        var inverso=new ArrayList<>(originales);Collections.reverse(inverso);
        var hikari=(HikariDataSource)dataSource;var pool=Executors.newFixedThreadPool(2);pausarCopia.set(true);verificarPrimerIo.set(true);
        try(var conexion=DriverManager.getConnection(hikari.getJdbcUrl(),hikari.getUsername(),hikari.getPassword())){
            conexion.setReadOnly(true);var inspector=new JdbcTemplate(new SingleConnectionDataSource(conexion,true));
            var a=pool.submit(()->{Thread.currentThread().setName(hiloA);principal(equipo.agente(),sidA);
                try{return new TransactionTemplate(gestor).execute(t->{
                    pidA.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));txA.set(jdbc.queryForObject("SELECT txid_current()",Long.class));
                    jdbc.execute("SET LOCAL lock_timeout='15s'");return prepararPieza(equipo.agente(),sidA,encargoA,originales);
                });}finally{SecurityContextHolder.clearContext();}});
            esperar(primeraCopia,"A no adquirió el primer fence real de copia.");
            var otro=pool.submit(()->{Thread.currentThread().setName(hiloB);principal(equipo.colega(),sidB);
                try{return prepararPieza(equipo.colega(),sidB,encargoB,inverso);}finally{SecurityContextHolder.clearContext();}});
            esperarLock(inspector,llaveCopia(originales.getFirst()));long segunda=llaveCopia(originales.get(1));
            assertFalse(inspector.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE pid=? AND locktype='advisory' AND granted AND classid::bigint=? AND objid::bigint=? AND objsubid=1)",
                    Boolean.class,(int)pidB.get(),(segunda>>>32)&0xffffffffL,segunda&0xffffffffL),"B no toma la copia opuesta antes de esperar la primera.");
            continuarCopia.countDown();var piezaA=a.get(20,TimeUnit.SECONDS);var piezaB=otro.get(20,TimeUnit.SECONDS);
            assertNotNull(piezaA.pieza());assertNotNull(piezaB.pieza());assertNotEquals(piezaA.pieza(),piezaB.pieza());
            assertEquals(1,verificacionesIo.get(),"El primer I/O debe haber comprobado todos los fences reales.");
            exigirOrdenDeFotos(piezaA,originales);exigirOrdenDeFotos(piezaB,inverso);
        }finally{continuarCopia.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(25,TimeUnit.SECONDS));}
    }

    @Test void cacheComparteLecturaDelMismoActorYSidYRetieneEscritorHastaAmbosCommits()throws Exception{
        generar(2);
        for(UUID original:originales){var actual=resultados.seleccionActual(original,equipo.agente(),sidA);
            resultados.seleccionar(original,null,null,actual.revision(),clave(),equipo.agente(),sidA);}
        prepararPieza(equipo.agente(),sidA,encargoA,originales);
        var inverso=new ArrayList<>(originales);Collections.reverse(inverso);
        var preparadaB=new CountDownLatch(1);var confirmarB=new CountDownLatch(1);var pidEscritor=new AtomicLong();
        var hikari=(HikariDataSource)dataSource;var pool=Executors.newFixedThreadPool(3);
        try(var conexion=DriverManager.getConnection(hikari.getJdbcUrl(),hikari.getUsername(),hikari.getPassword())){
            conexion.setReadOnly(true);var inspector=new JdbcTemplate(new SingleConnectionDataSource(conexion,true));
            var a=pool.submit(()->{Thread.currentThread().setName(hiloA);principal(equipo.agente(),sidA);
                try{return new TransactionTemplate(gestor).execute(t->{
                    pidA.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));txA.set(jdbc.queryForObject("SELECT txid_current()",Long.class));
                    jdbc.execute("SET LOCAL lock_timeout='15s'");var salida=prepararPieza(equipo.agente(),sidA,encargoA,originales);
                    metodoATerminado.countDown();esperar(confirmarA,"No se autorizó commit A.");return salida;
                });}finally{SecurityContextHolder.clearContext();}});
            esperar(metodoATerminado,"A no terminó su lectura de las copias.");
            var otro=pool.submit(()->{Thread.currentThread().setName(hiloB);principal(equipo.agente(),sidA);
                try{return new TransactionTemplate(gestor).execute(t->{
                    var salida=prepararPieza(equipo.agente(),sidA,encargoA,inverso);
                    preparadaB.countDown();esperar(confirmarB,"No se autorizó commit B.");return salida;
                });}finally{SecurityContextHolder.clearContext();}});
            esperar(preparadaB,"B debe preparar el mismo cache mientras A conserva su transacción abierta.");
            assertNotEquals(pidA.get(),pidB.get());assertNotEquals(txA.get(),txB.get());
            for(UUID original:originales){long llave=llaveCopia(original);
                assertEquals(2,inspector.queryForObject("SELECT count(*) FROM pg_locks WHERE pid IN (?,?) AND locktype='advisory' AND granted AND mode='ShareLock' AND classid::bigint=? AND objid::bigint=? AND objsubid=1",
                        Integer.class,(int)pidA.get(),(int)pidB.get(),(llave>>>32)&0xffffffffL,llave&0xffffffffL));}
            long llave=llaveCopia(originales.getFirst());
            var escritor=pool.submit(()->new TransactionTemplate(gestor).execute(t->{
                pidEscritor.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));
                jdbc.execute("SET LOCAL lock_timeout='15s'");comandosLocks.bloquearIdentidad(llave);return true;
            }));
            esperarEscritorCopia(inspector,pidEscritor,llave,pidA.get());assertFalse(escritor.isDone());
            confirmarA.countDown();var piezaA=a.get(20,TimeUnit.SECONDS);
            esperarEscritorCopia(inspector,pidEscritor,llave,pidB.get());assertFalse(escritor.isDone());
            confirmarB.countDown();var piezaB=otro.get(20,TimeUnit.SECONDS);assertTrue(escritor.get(20,TimeUnit.SECONDS));
            assertNotEquals(piezaA.pieza(),piezaB.pieza());
            exigirOrdenDeFotos(piezaA,originales);exigirOrdenDeFotos(piezaB,inverso);
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM copia_comercial_foto WHERE organizacion_id=? AND id_propiedad=?",
                    Integer.class,equipo.agente().idOrganizacion(),propiedad));
        }finally{confirmarA.countDown();confirmarB.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(25,TimeUnit.SECONDS));}
    }
    private void esperarEscritorCopia(JdbcTemplate inspector,AtomicLong escritor,long llave,long lector)throws Exception{
        long fin=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<fin){
            if(escritor.get()!=0&&Boolean.TRUE.equals(inspector.queryForObject("""
                SELECT ?=ANY(pg_blocking_pids(?)) AND EXISTS(
                  SELECT 1 FROM pg_locks WHERE pid=? AND locktype='advisory' AND NOT granted
                    AND mode='ExclusiveLock' AND classid::bigint=? AND objid::bigint=? AND objsubid=1)
                """,Boolean.class,(int)lector,(int)escritor.get(),(int)escritor.get(),(llave>>>32)&0xffffffffL,llave&0xffffffffL)))return;
            Thread.sleep(20);
        }
        fail("El escritor debe esperar el fence COPY exacto hasta el commit del lector pendiente.");
    }
    private void exigirOrdenDeFotos(Estado pieza,List<UUID> orden)throws Exception{
        String contenido=jdbc.queryForObject("SELECT solicitud FROM pieza_comercial WHERE organizacion_id=? AND referencia=?",
                String.class,equipo.agente().idOrganizacion(),pieza.pieza());
        var documento=contexto.getBean(com.fasterxml.jackson.databind.ObjectMapper.class).readTree(contenido);
        var observados=new ArrayList<UUID>();documento.path("copias").forEach(f->observados.add(UUID.fromString(f.path("original").asText())));
        assertEquals(orden,observados,"El orden humano de las fotografías no se sustituye por el orden de candados.");
    }

    @Test void rollbackDeLoteFrioRetiraCopiasNuevasSinAfectarOriginalesYPermiteReintentar()throws Exception{
        generar(2);
        for(UUID original:originales){var actual=resultados.seleccionActual(original,equipo.agente(),sidA);
            resultados.seleccionar(original,null,null,actual.revision(),clave(),equipo.agente(),sidA);}
        var anteriores=new HashMap<String,String>();objetos.forEach((k,v)->anteriores.put(k,EvaluacionTecnicaFoto.huella(v.contenido())));
        var revertida=new TransactionTemplate(gestor).execute(t->{
            var salida=prepararPieza(equipo.agente(),sidA,encargoA,originales);
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM copia_comercial_foto WHERE organizacion_id=? AND id_propiedad=?",
                    Integer.class,equipo.agente().idOrganizacion(),propiedad));
            t.setRollbackOnly();return salida;
        });
        assertNotNull(revertida);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM copia_comercial_foto WHERE organizacion_id=? AND id_propiedad=?",
                Integer.class,equipo.agente().idOrganizacion(),propiedad));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM pieza_comercial WHERE organizacion_id=? AND id_propiedad=?",
                Integer.class,equipo.agente().idOrganizacion(),propiedad));
        var posteriores=new HashMap<String,String>();objetos.forEach((k,v)->posteriores.put(k,EvaluacionTecnicaFoto.huella(v.contenido())));
        assertEquals(anteriores,posteriores,"Compensar el lote nuevo no retira originales ni evidencia previa.");
        assertNotNull(prepararPieza(equipo.agente(),sidA,encargoA,originales).pieza());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM copia_comercial_foto WHERE organizacion_id=? AND id_propiedad=?",
                Integer.class,equipo.agente().idOrganizacion(),propiedad));
    }

}
