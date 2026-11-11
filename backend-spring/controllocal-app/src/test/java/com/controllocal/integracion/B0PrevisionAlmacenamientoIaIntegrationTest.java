package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.planes.CategoriaAlmacenamiento;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.*;
import com.controllocal.service.planes.UsoAlmacenamientoB0.*;
import com.controllocal.service.planes.ClasificadorCargaAlmacenamientoB0.*;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Path;
import java.security.MessageDigest;
import com.controllocal.web.almacen.AlmacenDisco;
import com.controllocal.web.almacen.AlmacenDocumentos;
import static org.junit.jupiter.api.Assertions.*;

/** Ledger/C1/SQL reales; clasificador, políticas y comprobaciones de objeto son TEST sintéticos.
 * No ejecuta inferencia, proveedor, QC, calibración ni financiación productivos. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class B0PrevisionAlmacenamientoIaIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager gestor;
    @Autowired UsoAlmacenamientoB0Repository repo;@Autowired PresupuestoIaRepository dinero;@Autowired Documentos json;
    TransactionTemplate tx;UsoAlmacenamientoB0 servicio;Actor actor;ContextoSesion sid;Instant ahora;
    @TempDir Path directorio;AlmacenDocumentos almacen;
    String namespace,perfil;final String version="B0-PREVISION-TEST",origen="c".repeat(64),hash="d".repeat(64);
    final Map<UUID,Actor> jobs=new ConcurrentHashMap<>();final Map<UUID,AusenciaComprobada> retiros=new ConcurrentHashMap<>();
    final Map<UUID,NoAlmacenadoComprobado> noAlmacenados=new ConcurrentHashMap<>();
    final ClasificadorCargaAlmacenamientoB0 clasificador=new ClasificadorCargaAlmacenamientoB0(){
        public boolean atiende(Recurso r){return r!=null&&"PREVISION_B0_TEST".equals(r.tipo());}
        public Clasificacion clasificar(Solicitud s,Actor a){return clasificarJob(UUID.fromString(s.recurso().referencia()),a);}
        public Clasificacion clasificarPrevision(PrevisionIa p,Actor a){return clasificarJob(p.job(),a);}
        private Clasificacion clasificarJob(UUID job,Actor a){
            if(!a.equals(jobs.get(job)))throw new AccesoNoAutorizadoException();
            return new Clasificacion(CategoriaAlmacenamiento.DERIVADA_IA,Necesidad.ORDINARIA,origen);
        }
        public void exigirRetiro(Recurso r,AusenciaComprobada p){if(!p.equals(retiros.get(p.retiro()))||!r.referencia().equals(p.job().toString()))throw new ConflictoException("TEST versión protegida o sin declaración.");}
        public void exigirNoAlmacenado(Recurso r,NoAlmacenadoComprobado p){if(!p.equals(noAlmacenados.get(p.job()))||!r.referencia().equals(p.job().toString()))throw new ConflictoException("TEST sin prueba durable NO_WRITER.");}
    };
    @BeforeEach void preparar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);ahora=Instant.now().truncatedTo(ChronoUnit.MICROS);
        actor=EspacioIndividualB0DePrueba.crear(jdbc,tx);sid=sesion(actor);
        almacen=new AlmacenDisco(directorio.toString());namespace=almacen.identidadTecnica();perfil="PREV_TEST_"+UUID.randomUUID();
        servicio=new UsoAlmacenamientoB0(contexto.getBean(ContextoPresupuestoB0.class),dinero,repo,json,List.of(clasificador),Clock.fixed(ahora,ZoneOffset.UTC));
        configurar(100,1_000);
    }
    @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}
    ContextoSesion sesion(Actor a){return new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class)).sesion(a);}
    void configurar(long techo,long capacidad){
        UUID fondo=UUID.randomUUID();var desde=utc(ahora.minusSeconds(60));var hasta=utc(ahora.plus(Duration.ofDays(1)));
        jdbc.update("INSERT INTO b0_fondo_tecnico(id_fondo,moneda,financiado,desde,hasta,evidencia_financiacion,habilitado) VALUES(?,'USD',0,?,?,'TEST SIN FONDOS REALES',false)",fondo,desde,hasta);
        jdbc.update("""
            INSERT INTO b0_perfil_operativo(perfil,version,id_fondo,normalizador,tarifas_referencia,maximo_creditos,maximo_tecnico_usd,
             limite_intentos,evidencia_calibracion,evidencia_aprobacion,habilitado)
             VALUES(?,?,?,1,'[]'::jsonb,1,1,1,'TEST NO CALIBRACION','TEST NO APROBACION PRODUCTIVA',true)
            """,perfil,version,fondo);
        jdbc.update("INSERT INTO b0_perfil_actual(perfil,version) VALUES(?,?)",perfil,version);
        jdbc.update("INSERT INTO b0_limite_salida_ia(perfil,version_perfil,maximo_bytes,evidencia_calibracion,evidencia_aprobacion,desde,hasta,habilitado) VALUES(?,?,?,'TEST NO CALIBRACION','TEST NO APROBACION PRODUCTIVA',?,?,true)",perfil,version,techo,desde,hasta);
        jdbc.update("""
            INSERT INTO b0_politica_capacidad_fisica(namespace_hash,version,capacidad_bytes,ocupacion_conciliada_bytes,conciliada_en,
             evidencia_calibracion,evidencia_aprobacion,evidencia_financiacion,evidencia_conciliacion,desde,hasta,habilitada)
             VALUES(?,?,?,0,?,'TEST NO CALIBRACION','TEST NO APROBACION PRODUCTIVA','TEST SIN FINANCIACION REAL','TEST OCUPACION SINTETICA',?,?,true)
            """,namespace,version,capacidad,desde,desde,hasta);
        jdbc.update("INSERT INTO b0_capacidad_fisica_actual(namespace_hash,version) VALUES(?,?)",namespace,version);
    }
    PrevisionIa job(Actor a){
        UUID id=UUID.randomUUID();jobs.put(id,a);String clave=Idempotencia.huellaExacta("JOB_TEST_"+id);
        jdbc.update("""
            INSERT INTO material_ia_operacion(organizacion_id,id_operacion,id_persona,id_rol,rol_efectivo,sesion_origen,accion,
             huella_peticion,solicitud,snapshot,huella_snapshot,perfil,version_perfil,clave_externa,estado)
             VALUES(?,?,?,?,'AGENTE',?,'MEJORAR_FOTO',?,'{}','{}',?,?,?,?,'INTENCION')
            """,a.idOrganizacion(),id,a.idPersona(),a.idRolOperativo(),"a".repeat(64),clave,Idempotencia.huellaExacta("{}"),perfil,version,clave);
        return new PrevisionIa(id,new Recurso("PREVISION_B0_TEST",id.toString(),"TEST_LEDGER",origen),perfil,version,namespace);
    }
    PrevisionAdmitida reservar(PrevisionIa p,Actor a,ContextoSesion s){return tx.execute(t->servicio.previsionar(p,a,s));}
    RefInterna reservarYReclamar(PrevisionIa p){var r=reservar(p,actor,sid).referencia();tx.executeWithoutResult(t->servicio.exigirAntesDeClaim(r,actor,sid));return r;}
    String identidad(PrevisionIa p){return identidad(p,1);}
    String identidad(PrevisionIa p,int numero){return namespace+":tenant/"+jobs.get(p.job()).idOrganizacion()+"/salidas/"+p.job()+"/"+numero+".png";}
    long intento(PrevisionIa p,int numero,String contenido,long bytes,String estado,boolean incierta){
        return jdbc.queryForObject("""
            INSERT INTO intento_salida_binaria(dueno_binario,organizacion_id,operacion,numero,fuente,clave_proveedor,extension,content_type,
             huella,bytes,estado,escritura_incierta,almacen_huella)
             VALUES(b0_resolver_dueno(?,NULL),?,?,?,'TEST PREVISION',?,'png','image/png',?,?,?,?,?) RETURNING id_intento
            """,Long.class,jobs.get(p.job()).idOrganizacion(),jobs.get(p.job()).idOrganizacion(),p.job(),numero,identidad(p,numero).substring(namespace.length()+1),contenido,bytes,estado,incierta,namespace);
    }
    void conservar(PrevisionIa p,RefInterna r,long bytes){
        tx.executeWithoutResult(t->{servicio.admitirPrevisionParaAlmacenamiento(r,new Solicitud(r.reserva(),p.recurso(),bytes,hash,identidad(p),namespace),actor,sid);
            servicio.confirmar(r,new ComprobacionObjeto(identidad(p),bytes,hash,"TEST METADATA SIN ALMACEN REAL"));});
    }
    long uso(String columna){return jdbc.queryForObject("SELECT COALESCE((SELECT "+columna+" FROM b0_uso_almacenamiento WHERE organizacion_id=?),0)",Long.class,actor.idOrganizacion());}
    long fisico(String columna){return jdbc.queryForObject("SELECT COALESCE((SELECT "+columna+" FROM b0_uso_capacidad_fisica WHERE namespace_hash=?),0)",Long.class,namespace);}
    @Test void techoSinHashSeResuelveAlRealSinDuplicarCuota(){
        var p=job(actor);RefInterna r=reservarYReclamar(p);
        assertNull(jdbc.queryForObject("SELECT bytes FROM b0_reserva_almacenamiento WHERE organizacion_id=? AND id_reserva=?",Long.class,actor.idOrganizacion(),r.reserva()));
        assertNull(jdbc.queryForObject("SELECT huella_contenido FROM b0_reserva_almacenamiento WHERE organizacion_id=? AND id_reserva=?",String.class,actor.idOrganizacion(),r.reserva()));
        assertEquals(100,uso("reservados"));assertEquals(100,fisico("reservados"));conservar(p,r,60);
        assertEquals(60,uso("confirmados"));assertEquals(0,uso("reservados"));assertEquals(60,fisico("confirmados"));assertEquals(0,fisico("reservados"));
        tx.executeWithoutResult(t->servicio.confirmar(r,new ComprobacionObjeto(identidad(p),60,hash,"TEST REPLAY")));
        assertEquals(60,uso("confirmados"));assertEquals(60,fisico("confirmados"));
    }
    @Test void rollbackDeAdmisionNoDejaBytesRetenidos(){
        var p=job(actor);tx.executeWithoutResult(t->{servicio.previsionar(p,actor,sid);t.setRollbackOnly();});
        assertEquals(0,uso("reservados"));assertEquals(0,fisico("reservados"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_reserva_almacenamiento WHERE organizacion_id=? AND job_ia=?",Integer.class,actor.idOrganizacion(),p.job()));
    }
    @Test void configurarPerfilSinPresupuestoFisicoNoAutorizaPrevision(){
        var p=job(actor);jdbc.update("DELETE FROM b0_capacidad_fisica_actual WHERE namespace_hash=?",namespace);
        assertThrows(CapacidadDeshabilitadaException.class,()->reservar(p,actor,sid));assertEquals(0,uso("reservados"));
    }
    @Test void sinPerfilActualNoFabricaUnTecho(){
        var p=job(actor);jdbc.update("DELETE FROM b0_perfil_actual WHERE perfil=?",perfil);
        assertThrows(CapacidadDeshabilitadaException.class,()->reservar(p,actor,sid));assertEquals(0,uso("reservados"));
    }
    @Test void resultadoMayorNoAmpliaNiLiberaLaPrevision(){
        var p=job(actor);var r=reservarYReclamar(p);
        assertThrows(ConflictoException.class,()->tx.execute(t->servicio.admitirPrevisionParaAlmacenamiento(r,new Solicitud(r.reserva(),p.recurso(),101,hash,identidad(p),namespace),actor,sid)));
        assertEquals(100,uso("reservados"));assertEquals(100,fisico("reservados"));assertEquals(0,uso("confirmados"));
    }
    @Test void perdidaDeRespuestaNoPermiteLiberarConUnaCadena(){
        var p=job(actor);var r=reservarYReclamar(p);
        assertThrows(ConflictoException.class,()->tx.execute(t->servicio.cancelarNoAlmacenado(r,"TEST TIMEOUT NO ES PRUEBA")));
        assertEquals(100,uso("reservados"));assertEquals(100,fisico("reservados"));
    }
    @Test void qcSinEscritorLiberaSoloConSuPruebaYReplayNoDuplica(){
        var p=job(actor);var r=reservarYReclamar(p);
        Instant observada=tx.execute(t->repo.ahoraDeAlmacenamiento());
        assertFalse(observada.isBefore(repo.detalle(repo.dueno(actor.idOrganizacion(),null),r.reserva()).orElseThrow().reclamada()));
        var prueba=new NoAlmacenadoComprobado(p.job(),"MATERIAL-IA-NO-ALMACENADO:TEST",hash,observada);
        assertThrows(ConflictoException.class,()->tx.execute(t->servicio.cancelarPrevisionNoAlmacenada(r,prueba)));assertEquals(100,uso("reservados"));
        noAlmacenados.put(p.job(),prueba);tx.executeWithoutResult(t->servicio.cancelarPrevisionNoAlmacenada(r,prueba));
        tx.executeWithoutResult(t->servicio.cancelarPrevisionNoAlmacenada(r,prueba));assertEquals(0,uso("reservados"));assertEquals(0,fisico("reservados"));
    }
    @Test void retiradaRollbackYReplayConservanMetadataYNoDuplicanLiberacion(){
        var p=job(actor);var r=reservarYReclamar(p);conservar(p,r,60);UUID retiro=UUID.randomUUID();
        var ausencia=new AusenciaComprobada(retiro,p.job(),UUID.randomUUID(),1,identidad(p),60,hash,"MATERIAL-IA-RETIRO:"+retiro);retiros.put(retiro,ausencia);
        tx.executeWithoutResult(t->{servicio.liberarRetiradaVerificada(r,ausencia);t.setRollbackOnly();});assertEquals(60,uso("confirmados"));
        tx.executeWithoutResult(t->servicio.liberarRetiradaVerificada(r,ausencia));tx.executeWithoutResult(t->servicio.liberarRetiradaVerificada(r,ausencia));
        assertEquals(0,uso("confirmados"));assertEquals(0,fisico("confirmados"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_objeto_almacenamiento WHERE organizacion_id=? AND huella_identidad=?",Integer.class,actor.idOrganizacion(),Idempotencia.huellaExacta(identidad(p))));
        assertThrows(ConflictoException.class,()->tx.execute(t->servicio.confirmar(r,new ComprobacionObjeto(identidad(p),60,hash,"TEST NO RESUCITAR"))));
    }
    @Test void versionProtegidaNoSeLiberaSoloPorConocerObjeto(){
        var p=job(actor);var r=reservarYReclamar(p);conservar(p,r,60);UUID retiro=UUID.randomUUID();
        var ausencia=new AusenciaComprobada(retiro,p.job(),UUID.randomUUID(),1,identidad(p),60,hash,"MATERIAL-IA-RETIRO:"+retiro);
        assertThrows(ConflictoException.class,()->tx.execute(t->servicio.liberarRetiradaVerificada(r,ausencia)));assertEquals(60,uso("confirmados"));assertEquals(60,fisico("confirmados"));
    }
    @Test void otraOrganizacionNoReclamaLaReferenciaAjena(){
        var p=job(actor);var r=reservar(p,actor,sid).referencia();Actor otra=EspacioIndividualB0DePrueba.crear(jdbc,tx);var otraSid=sesion(otra);
        assertThrows(AccesoNoAutorizadoException.class,()->tx.executeWithoutResult(t->servicio.exigirAntesDeClaim(r,otra,otraSid)));
        assertEquals(100,uso("reservados"));
    }
    @Test void identidadFuenteYTechoNoSePuedenReescribirEnSql(){
        var p=job(actor);var r=reservarYReclamar(p);
        assertThrows(org.springframework.dao.DataAccessException.class,()->tx.executeWithoutResult(t->jdbc.update("UPDATE b0_reserva_almacenamiento SET techo_bytes=techo_bytes+1,bytes_retenidos=bytes_retenidos+1,bytes_fisicos_retenidos=bytes_fisicos_retenidos+1 WHERE organizacion_id=? AND id_reserva=?",actor.idOrganizacion(),r.reserva())));
        assertEquals(100,uso("reservados"));
    }
    @Test void putSeguidoDeRollbackConservaPrevisionYBloqueaNuevaAdmisionHastaRecuperar()throws Exception{
        var p=job(actor);var r=reservarYReclamar(p);byte[] bytes=new byte[60];
        String real=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        intento(p,1,real,60,"PREPARADO",true);String clave=identidad(p).substring(namespace.length()+1);
        tx.executeWithoutResult(t->{
            servicio.admitirPrevisionParaAlmacenamiento(r,new Solicitud(r.reserva(),p.recurso(),60,real,identidad(p),namespace),actor,sid);
            almacen.guardarEnClave(clave,bytes,"image/png");
            servicio.confirmar(r,new ComprobacionObjeto(identidad(p),60,real,"TEST PUT DISCO OBSERVADO"));t.setRollbackOnly();
        });
        assertTrue(almacen.abrir(clave).isPresent());assertEquals(100,uso("reservados"));assertEquals(0,fisico("confirmados"));
        var otra=job(actor);var error=assertThrows(IncertidumbreFisicaException.class,()->reservar(otra,actor,sid));
        assertEquals("INCERTIDUMBRE_TECNICA",error.causa());assertEquals(100,fisico("reservados"));
        // La identidad propia observada queda cubierta por su previsión y no necesita otro PUT.
        tx.executeWithoutResult(t->{
            assertArrayEquals(bytes,almacen.abrir(clave).orElseThrow().contenido());
            servicio.admitirPrevisionParaAlmacenamiento(r,new Solicitud(r.reserva(),p.recurso(),60,real,identidad(p),namespace),actor,sid);
            servicio.confirmar(r,new ComprobacionObjeto(identidad(p),60,real,"TEST RECUPERACION DISCO"));
        });
        // El mismo journal todavía PREPARADO ya tiene objeto físico contabilizado; no es una segunda deuda.
        reservar(otra,actor,sid);assertEquals(60,fisico("confirmados"));assertEquals(100,fisico("reservados"));
        tx.executeWithoutResult(t->servicio.confirmar(r,new ComprobacionObjeto(identidad(p),60,real,"TEST REPLAY")));
        assertEquals(60,fisico("confirmados"));assertTrue(almacen.abrir(clave).isPresent());
    }
    @Test void coberturaFisicaExcluyeSoloIdentidadNamespaceContenidoExactos(){
        var p=job(actor);long i=intento(p,1,hash,60,"INCIERTO",true);String objeto=Idempotencia.huellaExacta(identidad(p));
        tx.executeWithoutResult(t->{repo.bloquearCapacidadFisica(namespace,ahora);
            assertFalse(repo.existenIntentosFisicosSinCobertura(namespace,repo.dueno(actor.idOrganizacion(),null),objeto,60,hash,UsoAlmacenamientoB0Repository.AmbitoIntentos.NAMESPACE));
            assertTrue(repo.existenIntentosFisicosSinCobertura(namespace,repo.dueno(actor.idOrganizacion(),null),objeto,61,hash,UsoAlmacenamientoB0Repository.AmbitoIntentos.NAMESPACE));
            assertTrue(repo.existenIntentosFisicosSinCobertura(namespace,repo.dueno(actor.idOrganizacion(),null),objeto,60,"e".repeat(64),UsoAlmacenamientoB0Repository.AmbitoIntentos.NAMESPACE));
        });
        jdbc.update("UPDATE intento_salida_binaria SET estado='RETIRADO_VERIFICADO',escritura_incierta=false WHERE id_intento=?",i);
        assertFalse(repo.existenIntentosFisicosSinCobertura(namespace,null,null,0,null,UsoAlmacenamientoB0Repository.AmbitoIntentos.NAMESPACE));
        jdbc.update("UPDATE intento_salida_binaria SET escritura_incierta=true WHERE id_intento=?",i);
        assertTrue(repo.existenIntentosFisicosSinCobertura(namespace,null,null,0,null,UsoAlmacenamientoB0Repository.AmbitoIntentos.NAMESPACE));
    }
    @Test void nuevoIntentoDeMismaOperacionEsperaAusenciaSinBloquearResolucionPorHuerfanoAjeno(){
        var p=job(actor);var r=reservarYReclamar(p);long viejo=intento(p,1,hash,60,"RETIRADO_VERIFICADO",true);
        intento(p,2,hash,60,"PREPARADO",true);String actual=identidad(p,2);
        assertThrows(IncertidumbreFisicaException.class,()->tx.executeWithoutResult(t->servicio.admitirPrevisionParaAlmacenamiento(r,new Solicitud(r.reserva(),p.recurso(),60,hash,actual,namespace),actor,sid)));
        assertEquals(100,fisico("reservados"));
        jdbc.update("UPDATE intento_salida_binaria SET escritura_incierta=false WHERE id_intento=?",viejo);
        var ajena=job(actor);long pendiente=intento(ajena,1,hash,10,"INCIERTO",true);
        tx.executeWithoutResult(t->{servicio.admitirPrevisionParaAlmacenamiento(r,new Solicitud(r.reserva(),p.recurso(),60,hash,actual,namespace),actor,sid);
            servicio.confirmar(r,new ComprobacionObjeto(actual,60,hash,"TEST RESOLUCION YA CUBIERTA"));});
        var siguiente=job(actor);assertThrows(IncertidumbreFisicaException.class,()->reservar(siguiente,actor,sid));
        jdbc.update("UPDATE intento_salida_binaria SET estado='RETIRADO_VERIFICADO',escritura_incierta=false WHERE id_intento=?",pendiente);
        reservar(siguiente,actor,sid);assertEquals(60,fisico("confirmados"));assertEquals(100,fisico("reservados"));
    }
    @Test void dosOrganizacionesCompitenPorPoolFisicoSinSobreReservarlo()throws Exception{
        // Política TEST propia más estrecha; no se modifica una política inmutable.
        String nueva="TEST_CONCURRENCIA";
        jdbc.update("""
            INSERT INTO b0_politica_capacidad_fisica(namespace_hash,version,capacidad_bytes,ocupacion_conciliada_bytes,conciliada_en,
             evidencia_calibracion,evidencia_aprobacion,evidencia_financiacion,evidencia_conciliacion,desde,hasta,habilitada)
             VALUES(?,?,150,0,?,'TEST','TEST','TEST SIN FINANCIACION REAL','TEST OCUPACION',?,?,true)
            """,namespace,nueva,utc(ahora.minusSeconds(60)),utc(ahora.minusSeconds(60)),utc(ahora.plusSeconds(3600)));
        jdbc.update("UPDATE b0_capacidad_fisica_actual SET version=? WHERE namespace_hash=?",nueva,namespace);
        Actor otra=EspacioIndividualB0DePrueba.crear(jdbc,tx);var otraSid=sesion(otra);var p1=job(actor);var p2=job(otra);
        var retenida=new CountDownLatch(1);var liberar=new CountDownLatch(1);var segundaInicio=new CountDownLatch(1);
        var pid1=new AtomicInteger();var pid2=new AtomicInteger();var pool=Executors.newFixedThreadPool(2);
        try{
            Future<?> primera=pool.submit(()->tx.executeWithoutResult(t->{pid1.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));servicio.previsionar(p1,actor,sid);retenida.countDown();esperar(liberar);}));
            assertTrue(retenida.await(10,TimeUnit.SECONDS));
            Future<?> segunda=pool.submit(()->tx.executeWithoutResult(t->{pid2.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));segundaInicio.countDown();servicio.previsionar(p2,otra,otraSid);}));
            assertTrue(segundaInicio.await(10,TimeUnit.SECONDS));boolean bloqueada=false;long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<limite){bloqueada=Boolean.TRUE.equals(jdbc.queryForObject("SELECT ?=ANY(pg_blocking_pids(?))",Boolean.class,pid1.get(),pid2.get()));if(bloqueada)break;Thread.yield();}
            assertTrue(bloqueada,"La segunda organización debe esperar el pool físico de la primera, no simular concurrencia por Future pendiente.");
            liberar.countDown();primera.get(10,TimeUnit.SECONDS);var fallo=assertThrows(ExecutionException.class,()->segunda.get(10,TimeUnit.SECONDS));assertInstanceOf(ReglaNegocioException.class,fallo.getCause());
            assertEquals(100,fisico("reservados"));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_reserva_almacenamiento WHERE namespace_hash=?",Integer.class,namespace));
        }finally{liberar.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }
    static void esperar(CountDownLatch l){try{if(!l.await(15,TimeUnit.SECONDS))throw new IllegalStateException("TEST barrera no liberada");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
}
