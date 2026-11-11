package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.integracion.soporte.EvidenciaC4DePrueba;
import com.controllocal.integracion.soporte.OrigenesDePrueba;
import com.controllocal.persistence.repositorio.LecturasInteraccionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.InteraccionService;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.RequerimientoService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actuacion.ComandosAutomaticosService;
import com.controllocal.service.actuacion.ComandosAutomaticosService.CorrelacionAutomatico;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.demanda.InteresadosDeDemanda;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.soporte.Procedencia;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

/**
 * C4-02/DAT-04: dos transacciones PostgreSQL READ_COMMITTED y autoridad real.
 * El único spy conserva la búsqueda SQL y sólo demora su retorno al lector.
 * El fixture postidentidad no acredita login/JWT ni rendimiento HTTP.
 */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class LecturaInteraccionConcurrenteIntegrationTest {
    @DynamicPropertySource static void base(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired EncargoService encargos;
    @Autowired RequerimientoService demandas;
    @Autowired InteraccionService interacciones;
    @Autowired ComandosAutomaticosService automaticos;
    @Autowired PropiedadUniversalService propiedades;
    @MockitoSpyBean LecturasInteraccionRepository lecturas;
    private OrigenesDePrueba origenes;
    private Actor actor;
    private ContextoSesion sid;

    @BeforeEach void preparar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);
        origenes=new OrigenesDePrueba(jdbc,gestor,captura,contextos);
        actor=origenes.empresa().agente();sid=origenes.sesion(actor);
        EvidenciaC4DePrueba.actor(actor);
    }
    @AfterEach void cerrarEvidencia(){EvidenciaC4DePrueba.limpiar();}

    @Test void cambiarElInteresadoTrasElSqlImpideEntregarPaginaYConteoDeJsonCompartido() throws Exception {
        long propiedad=origenes.propiedad(actor,"Lectura concurrente TEST");
        long encargo=Objects.requireNonNull(new TransactionTemplate(gestor).execute(t->encargos.abrir(
                new EncargoService.DatosApertura(propiedad,actor.idRolOperativo(),"LC-"+clave().substring(0,12),
                        OperacionInmobiliaria.VENTA,null,null,null,"Preparación privada TEST",null,
                        Procedencia.deLaPantalla(),List.of()),actor))).id();
        var demanda=demandas.crear(datosDemanda(contacto("Contacto original TEST","900000071"),true),
                actor,sid,clave(),Procedencia.deLaPantalla());
        assertNotNull(demanda.idPersonaInteresada());
        EvidenciaC4DePrueba.recurso(actor,"PROPIEDAD",propiedad,null);
        EvidenciaC4DePrueba.recurso(actor,"ENCARGO",encargo,null);
        EvidenciaC4DePrueba.recurso(actor,"DEMANDA",demanda.id(),demanda.version());

        String q="seleccion"+clave().replace("-","");
        var visible=registrar(comunicacion(propiedad,encargo,demanda,"Resumen TEST "+q,-2));
        var fueraDePagina=registrar(comunicacion(propiedad,encargo,demanda,"Otro hecho TEST sin el texto seleccionado",-1));
        assertNotEquals(visible.id(),fueraDePagina.id());
        assertEquals(1L,jdbc.queryForObject("""
                select count(distinct convert_to(autorizacion_json,'UTF8')) from interaccion_comercial
                 where organizacion_id=? and id_interaccion in (?,?)
                """,Long.class,actor.idOrganizacion(),visible.id(),fueraDePagina.id()));
        assertEquals(0L,jdbc.queryForObject("""
                select count(*) from interaccion_comercial where organizacion_id=?
                   and id_interaccion in (?,?) and autorizacion_json is null
                """,Long.class,actor.idOrganizacion(),visible.id(),fueraDePagina.id()));
        var filtro=filtro(propiedad,q);
        var inicial=interacciones.listar(filtro,actor);
        assertEquals(1,inicial.total());
        assertEquals(List.of(visible.id()),inicial.items().stream().map(FichaInteraccion::id).toList());
        var historiaAntes=historia(visible.id(),fueraDePagina.id());
        assertEquals(2,historiaAntes.filas());

        var sqlTerminado=new CountDownLatch(1);
        var liberarLector=new CountDownLatch(1);
        var detenida=new AtomicBoolean();
        var commitEscritor=new AtomicBoolean();
        var entregada=new AtomicBoolean();
        var hiloLector=new AtomicReference<Thread>();
        var transaccionLectora=new AtomicReference<MarcaTransaccion>();
        var transaccionEscritora=new AtomicReference<MarcaTransaccion>();
        var paginaSql=new AtomicReference<LecturasInteraccionRepository.PaginaIds>();
        var original=mockingDetails(lecturas).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocacion->{
            // Conservar la implementación/delegación original del spy Spring real.
            Object salida=original==Answers.CALLS_REAL_METHODS?invocacion.callRealMethod():original.answer(invocacion);
            if(Thread.currentThread()==hiloLector.get()&&detenida.compareAndSet(false,true)){
                paginaSql.set((LecturasInteraccionRepository.PaginaIds)salida);
                sqlTerminado.countDown();
                assertTrue(liberarLector.await(30,TimeUnit.SECONDS),"La barrera posterior al SQL debe liberarse");
                assertTrue(commitEscritor.get(),"El escritor debe confirmar antes de reanudar la entrega");
            }
            return salida;
        }).when(lecturas).buscar(anyLong(),anyCollection(),nullable(String.class),nullable(String.class),
                nullable(String.class),nullable(String.class),anyInt(),anyInt());

        var hilos=Executors.newFixedThreadPool(2);
        Future<?> lector=null,escritor=null;
        try{
            lector=hilos.submit(()->EvidenciaC4DePrueba.enHilo(()->transaccion(true).execute(t->{
                hiloLector.set(Thread.currentThread());
                transaccionLectora.set(marcaTransaccion());
                var pagina=interacciones.listar(filtro,actor);
                entregada.set(true);return pagina;
            })));
            assertTrue(sqlTerminado.await(20,TimeUnit.SECONDS),"El lector debe completar SQL real antes de iniciar el cambio");
            assertNotNull(paginaSql.get());assertEquals(1,paginaSql.get().total());
            assertEquals(List.of(visible.id()),paginaSql.get().ids());
            assertFalse(commitEscritor.get());assertFalse(entregada.get());

            escritor=hilos.submit(()->EvidenciaC4DePrueba.enHilo(()->transaccion(false).execute(t->{
                transaccionEscritora.set(marcaTransaccion());
                var cambiada=demandas.actualizar(demanda.id(),demanda.version(),
                        datosDemanda(contacto("Contacto posterior TEST","900000072"),false),actor,sid,
                        clave(),Procedencia.deLaPantalla());
                assertNotNull(cambiada.idPersonaInteresada());
                assertNotEquals(demanda.idPersonaInteresada(),cambiada.idPersonaInteresada());
                assertTrue(cambiada.version()>demanda.version());
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                    @Override public void afterCommit(){commitEscritor.set(true);}
                });
                return cambiada;
            })));
            var cambiada=(RequerimientoService.FichaRequerimiento)escritor.get(20,TimeUnit.SECONDS);
            assertTrue(commitEscritor.get(),"Debe existir commit real, no sólo retorno del efector");
            var actual=demandas.buscar(demanda.id(),actor);
            assertEquals(cambiada.idPersonaInteresada(),actual.idPersonaInteresada());
            assertEquals(cambiada.version(),actual.version());
            assertNotEquals(transaccionLectora.get().pid(),transaccionEscritora.get().pid());
            assertNotEquals(transaccionLectora.get().txid(),transaccionEscritora.get().txid());
            liberarLector.countDown();

            Future<?> lecturaPendiente=lector;
            var fallo=assertThrows(ExecutionException.class,()->lecturaPendiente.get(20,TimeUnit.SECONDS));
            assertInstanceOf(ConflictoException.class,fallo.getCause());
            assertFalse(entregada.get(),"La página y su conteo no deben salir de Core tras perder el permiso");
            assertEquals(propiedad,propiedades.consultar(propiedad,actor).id());
            var nueva=interacciones.listar(filtro,actor);
            assertEquals(0,nueva.total());assertTrue(nueva.items().isEmpty());
            var sinTexto=interacciones.listar(filtro(propiedad,null),actor);
            assertEquals(0,sinTexto.total());assertTrue(sinTexto.items().isEmpty());
            var historiaDespues=historia(visible.id(),fueraDePagina.id());
            assertEquals(historiaAntes.filas(),historiaDespues.filas());
            assertTrue(historiaAntes.huella().equals(historiaDespues.huella()),"No se reescribe ni borra la historia");
            assertEquals(visible.version(),versionActual(visible.id()));
            assertEquals(fueraDePagina.version(),versionActual(fueraDePagina.id()));
            EvidenciaC4DePrueba.recurso(actor,"DEMANDA",actual.id(),actual.version());
            // Sólo tras esperar ambos futuros: no se confunde retorno con commit.
            EvidenciaC4DePrueba.transaccion("LECTOR_READ_COMMITTED",transaccionLectora.get().pid(),transaccionLectora.get().txid());
            EvidenciaC4DePrueba.transaccion("ESCRITOR_CONFIRMADO",transaccionEscritora.get().pid(),transaccionEscritora.get().txid());
            EvidenciaC4DePrueba.estado("LECTURA_CONCURRENTE",actor,"CONFLICTO");
        }finally{
            liberarLector.countDown();
            if(lector!=null&&!lector.isDone())lector.cancel(true);
            if(escritor!=null&&!escritor.isDone())escritor.cancel(true);
            hilos.shutdownNow();
            assertTrue(hilos.awaitTermination(40,TimeUnit.SECONDS),"Los dos hilos deben terminar incluso al fallar el caso");
        }
    }

    private TransactionTemplate transaccion(boolean lectura){
        var t=new TransactionTemplate(gestor);
        t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);t.setReadOnly(lectura);t.setTimeout(35);
        return t;
    }
    private record MarcaTransaccion(long pid,long txid){}
    private MarcaTransaccion marcaTransaccion(){
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        return Objects.requireNonNull(jdbc.queryForObject("""
                select pg_backend_pid(),txid_current(),current_setting('transaction_isolation')
                """,(r,n)->{
            assertEquals("read committed",r.getString(3));
            return new MarcaTransaccion(r.getLong(1),r.getLong(2));
        }));
    }
    private FichaInteraccion registrar(DatosInteraccion datos){
        var ficha=automaticos.registrarInteraccion(datos,actor,sid,
                new CorrelacionAutomatico("conversacion-test-"+clave(),"turno-test-"+clave(),null),
                Procedencia.deLaPantalla()).resultado();
        EvidenciaC4DePrueba.resultado("REGISTRAR_INTERACCION",actor,ficha);return ficha;
    }
    private static DatosInteraccion comunicacion(long propiedad,long encargo,RequerimientoService.FichaRequerimiento demanda,
                                                 String resumen,int dias){
        var origen=new ReferenciaContactoContextual(TipoContacto.DEMANDA_ORIGEN,demanda.id(),demanda.version(),null);
        var local=new ReferenciaContactoContextual(TipoContacto.DESTINATARIO,null,null,null);
        var links=List.of(new VinculoActividad(TipoContexto.PROPIEDAD,propiedad,null,null),
                new VinculoActividad(TipoContexto.ENCARGO,encargo,null,null),
                new VinculoActividad(TipoContexto.CONTACTO,demanda.idPersonaInteresada(),null,local));
        var hecho=new MomentoHecho(OrigenesDePrueba.hoy().plusDays(dias),LocalTime.of(9,0),ZoneOffset.ofHours(-5));
        return new DatosInteraccion(links,new ParticipanteDeclarado(origen,"PERSONA",null,null,null),null,
                TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,null,
                List.of(new EventoDeclarado("llamada-test",EventoComunicacion.LLAMADA_SALIENTE,hecho)),
                ResultadoInteraccion.SIN_RESPUESTA,null,resumen,List.of(),null,null,"Declaración de hechos TEST",null);
    }
    private static InteresadosDeDemanda.Referencia contacto(String nombre,String telefono){
        return new InteresadosDeDemanda.Referencia(InteresadosDeDemanda.TipoReferencia.CONTACTO_NUEVO,null,null,null,
                new InteresadosDeDemanda.DatosContacto("N",null,null,nombre,telefono,null));
    }
    private static RequerimientoService.DatosRequerimiento datosDemanda(InteresadosDeDemanda.Referencia ref,boolean alta){
        return new RequerimientoService.DatosRequerimiento(null,null,null,null,null,null,alta?"VENTA":null,
                alta?List.of("TERRENO"):null,null,null,null,null,null,null,null,null,null,alta?List.of():null,
                ref,null,null,null,null,Set.of());
    }
    private static FiltrosInteraccion filtro(long propiedad,String q){
        return new FiltrosInteraccion(TipoContexto.PROPIEDAD,propiedad,null,null,null,q,1,20);
    }
    private record EstadoHistoria(long filas,String huella){}
    private EstadoHistoria historia(long a,long b){
        return Objects.requireNonNull(jdbc.queryForObject("""
                select count(*),md5(coalesce(string_agg(to_jsonb(h)::text,'|' order by h.id_interaccion,h.version),''))
                  from interaccion_version h where organizacion_id=? and id_interaccion in (?,?)
                """,(r,n)->new EstadoHistoria(r.getLong(1),r.getString(2)),actor.idOrganizacion(),a,b));
    }
    private long versionActual(long id){
        return Objects.requireNonNull(jdbc.queryForObject("select version from interaccion_comercial where organizacion_id=? and id_interaccion=?",
                Long.class,actor.idOrganizacion(),id));
    }
    private static String clave(){return UUID.randomUUID().toString();}
}
