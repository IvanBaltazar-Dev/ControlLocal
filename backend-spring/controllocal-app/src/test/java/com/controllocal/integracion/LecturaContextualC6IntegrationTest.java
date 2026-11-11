package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** PG/T4 de las guardas reales. No render, IA, HTTP, cobro ni métrica de rendimiento. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class LecturaContextualC6IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext app;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired ContextosService contextos;
    @Autowired ContextosDeActividad actividad;
    @Autowired AccesoEncargo encargos;
    @Autowired ContextoPresupuestoB0 presupuesto;
    @Autowired CuentaAccesoRepository cuentas;
    private ActividadNegociacionDePrueba.Caso caso;
    private long cuenta;
    private String contextoOfrecido;

    @BeforeEach void montar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);
        // Productor existente: expediente de preparación, sin firmar/activar ni escribir archivos.
        caso=new FormalizacionesDePrueba(app).negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",false);
        cuenta=jdbc.queryForObject("select id_usuario from usuario_organizacion where organizacion_id=? and id_persona=? and estado='A'",
                Long.class,caso.agente().idOrganizacion(),caso.agente().idPersona());
        contextoOfrecido=contextos.contextos(caso.sesion().referenciaSesion(),caso.sesion().revisionContexto()).stream()
                .filter(c->c.idRolOperativo()==caso.agente().idRolOperativo()).findFirst().orElseThrow().referencia();
    }
    @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}

    private void leer(){leer(caso.agente(),caso.sesion());}
    private void leer(Actor actor,ContextoSesion sid){
        actividad.iniciarConLecturaProtegida(actor,sid);
        var inicio=encargos.iniciarConLecturaProtegida(actor,sid,caso.encargo());
        var encargo=encargos.leerParaActuar(caso.encargo(),actor,inicio);
        encargos.exigirResponsable(encargo,actor);
        // Cadena que antes volvía a solicitar Cuenta/SID WRITE al admitir un archivo.
        presupuesto.iniciarArchivo(actor,sid);
    }
    private TransactionTemplate tx(){return new TransactionTemplate(gestor);}
    private int identificar(){
        jdbc.execute("SET LOCAL lock_timeout='15s'");
        return jdbc.queryForObject("select pg_backend_pid()",Integer.class);
    }
    private static void esperar(CountDownLatch latch){
        try{assertTrue(latch.await(15,TimeUnit.SECONDS),"La barrera de las transacciones reales no se alcanzó.");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
    private void bloqueadoPor(int bloqueado,int... retenedores)throws InterruptedException{
        long fin=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<fin){
            var actuales=jdbc.queryForList("select unnest(pg_blocking_pids(?))",Integer.class,bloqueado);
            for(int retenedor:retenedores)if(actuales.contains(retenedor))return;
            Thread.sleep(10);
        }
        fail("No se observó en PostgreSQL el bloqueo por la transacción retenida.");
    }
    private void rechazoTrasCambio(){assertThrows(AccesoNoAutorizadoException.class,()->tx().executeWithoutResult(t->leer()));}

    enum Cambio { CUENTA,CREDENCIAL,MEMBRESIA,ROL,RENOVACION,CONTEXTO }
    private ContextosService.SesionAccesoDTO cambiar(Cambio cambio){
        if(cambio==Cambio.RENOVACION)return contextos.renovar(caso.sesion().referenciaSesion(),caso.sesion().revisionContexto());
        if(cambio==Cambio.CONTEXTO)return contextos.seleccionar(caso.sesion().referenciaSesion(),caso.sesion().revisionContexto(),contextoOfrecido);
        // Escritura de prueba explícita: conserva la toma Cuenta WRITE anterior a la mutación.
        // No pretende verificar la UX de contraseña ni sustituir los casos de cuenta/membresía C1.
        cuentas.bloquearCambioAcceso(cuenta).orElseThrow();
        switch(cambio){
            case CUENTA->jdbc.update("update cuenta_acceso set estado='I' where id_cuenta_acceso=?",cuenta);
            case CREDENCIAL->jdbc.update("update cuenta_acceso set contrasena_hash='TEST-sin-credencial-autenticable',sesiones_invalidas_desde=clock_timestamp() where id_cuenta_acceso=?",cuenta);
            case MEMBRESIA->jdbc.update("update usuario_organizacion set estado='I' where id_usuario=? and organizacion_id=?",cuenta,caso.agente().idOrganizacion());
            case ROL->{
                jdbc.update("update persona_rol set vigencia_hasta=? where id_persona_rol=?",
                        LocalDate.now(ZoneId.of("America/Lima")),caso.agente().idRolOperativo());
                // Fixture de retiro conjunto: una pertenencia AGENTE activa no puede quedar sin
                // su atribución vigente supervisada (V100). Se conserva el UPDATE/fence de ROL.
                // No acredita baja humana ni atribuye la denegación exclusivamente al rol.
                jdbc.update("update usuario_organizacion set estado='I' where id_usuario=? and organizacion_id=?",
                        cuenta,caso.agente().idOrganizacion());
            }
            default->throw new IllegalStateException("Escritor no identificado.");
        }
        return null;
    }

    @FunctionalInterface private interface Ensayo{void ejecutar(Lectores l)throws Exception;}
    private record Lectores(ExecutorService pool,int pidA,int pidB,Future<?> a,Future<?> b,
                            CountDownLatch liberarA,CountDownLatch liberarB){
        void primera()throws Exception{liberarA.countDown();a.get(15,TimeUnit.SECONDS);}
        void segunda()throws Exception{liberarB.countDown();b.get(15,TimeUnit.SECONDS);}
    }
    private Future<?> lector(ExecutorService pool,AtomicInteger pid,CountDownLatch dentro,CountDownLatch liberar){
        return pool.submit(()->tx().executeWithoutResult(t->{
            pid.set(identificar());leer();dentro.countDown();esperar(liberar);
            // La relectura dentro de la TX conserva la misma guarda, no un Actor memoizado.
            leer();
        }));
    }
    private void dosLectores(Ensayo ensayo)throws Exception{
        var pool=Executors.newFixedThreadPool(3);
        var dentro=new CountDownLatch(2);var liberarA=new CountDownLatch(1);var liberarB=new CountDownLatch(1);
        var pidA=new AtomicInteger();var pidB=new AtomicInteger();
        try{
            var a=lector(pool,pidA,dentro,liberarA);var b=lector(pool,pidB,dentro,liberarB);
            esperar(dentro);assertNotEquals(pidA.get(),pidB.get(),"Se necesitan dos conexiones/TX físicas, no una sesión reutilizada.");
            ensayo.ejecutar(new Lectores(pool,pidA.get(),pidB.get(),a,b,liberarA,liberarB));
        }finally{
            liberarA.countDown();liberarB.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest @EnumSource(Cambio.class)
    void dosLectoresMismaCuentaSidYEncargoCoexistenYElCambioEsperaAmbos(Cambio cambio)throws Exception{
        dosLectores(l->{
            var pidEscritor=new AtomicInteger();var inicia=new CountDownLatch(1);
            var escritor=l.pool().submit(()->tx().execute(t->{pidEscritor.set(identificar());inicia.countDown();return cambiar(cambio);}));
            esperar(inicia);bloqueadoPor(pidEscritor.get(),l.pidA(),l.pidB());
            l.primera();bloqueadoPor(pidEscritor.get(),l.pidB());assertFalse(escritor.isDone());
            l.segunda();var nueva=escritor.get(15,TimeUnit.SECONDS);rechazoTrasCambio();
            if(nueva!=null){
                assertEquals(caso.sesion().revisionContexto()+1,nueva.revision());
                tx().executeWithoutResult(t->leer(caso.agente(),ContextoSesion.de(nueva)));
            }
        });
    }

    @ParameterizedTest @EnumSource(Cambio.class)
    void siElCambioGanaPrimeroLaLecturaEsperaYRechazaElContextoAnterior(Cambio cambio)throws Exception{
        var pool=Executors.newFixedThreadPool(2);var dentro=new CountDownLatch(1);var liberar=new CountDownLatch(1);
        var empiezaLectura=new CountDownLatch(1);var pidEscritor=new AtomicInteger();var pidLector=new AtomicInteger();
        try{
            var escritor=pool.submit(()->tx().execute(t->{pidEscritor.set(identificar());var nueva=cambiar(cambio);dentro.countDown();esperar(liberar);return nueva;}));
            esperar(dentro);
            var lector=pool.submit(()->tx().executeWithoutResult(t->{pidLector.set(identificar());empiezaLectura.countDown();leer();}));
            esperar(empiezaLectura);bloqueadoPor(pidLector.get(),pidEscritor.get());assertFalse(lector.isDone());
            liberar.countDown();escritor.get(15,TimeUnit.SECONDS);
            var fallo=assertThrows(ExecutionException.class,()->lector.get(15,TimeUnit.SECONDS));
            assertInstanceOf(AccesoNoAutorizadoException.class,fallo.getCause());rechazoTrasCambio();
        }finally{liberar.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }

    @Test void lecturaRealExcluyeUnUpdateNoClaveDeCuentaAunqueNoPidaForUpdateAntes()throws Exception{
        dosLectores(l->{
            var pid=new AtomicInteger();var inicia=new CountDownLatch(1);
            var cambio=l.pool().submit(()->tx().executeWithoutResult(t->{
                pid.set(identificar());inicia.countDown();
                // Verifica la compatibilidad efectiva del READ generado por Hibernate, no su anotación.
                jdbc.update("update cuenta_acceso set estado='I' where id_cuenta_acceso=?",cuenta);
            }));
            esperar(inicia);bloqueadoPor(pid.get(),l.pidA(),l.pidB());l.primera();bloqueadoPor(pid.get(),l.pidB());
            l.segunda();cambio.get(15,TimeUnit.SECONDS);rechazoTrasCambio();
        });
    }

    @Test void lecturaDeEncargoImpideCambiarLaAutoridadHastaTerminarAmbasTransacciones()throws Exception{
        dosLectores(l->{
            var pid=new AtomicInteger();var inicia=new CountDownLatch(1);
            var cambio=l.pool().submit(()->tx().executeWithoutResult(t->{
                pid.set(identificar());inicia.countDown();
                // Mutación SQL TEST del recurso: sin Cuenta WRITE que oculte el lock de Encargo.
                jdbc.update("update captacion set id_rol_agente=?,version=version+1 where id_captacion=? and organizacion_id=?",
                        caso.equipo().colega().idRolOperativo(),caso.encargo(),caso.agente().idOrganizacion());
            }));
            esperar(inicia);bloqueadoPor(pid.get(),l.pidA(),l.pidB());l.primera();bloqueadoPor(pid.get(),l.pidB());
            l.segunda();cambio.get(15,TimeUnit.SECONDS);rechazoTrasCambio();
        });
    }

    @Test void entradaDeConsumoConservaLaExclusionAnteriorSinCambiarImportes()throws Exception{
        dosLectores(l->{
            var pid=new AtomicInteger();var inicia=new CountDownLatch(1);
            var consumo=l.pool().submit(()->tx().executeWithoutResult(t->{
                pid.set(identificar());inicia.countDown();presupuesto.iniciarConsumo(caso.agente(),caso.sesion());
            }));
            esperar(inicia);bloqueadoPor(pid.get(),l.pidA(),l.pidB());l.primera();bloqueadoPor(pid.get(),l.pidB());
            l.segunda();consumo.get(15,TimeUnit.SECONDS);
            tx().executeWithoutResult(t->leer());
        });
    }

    @Test void lecturaProtegidaNoAceptaUnActorDeOtraOrganizacion(){
        var actor=caso.agente();var ajeno=new Actor(actor.idOrganizacion()+1,actor.idPersona(),actor.idRolOperativo(),actor.rolEfectivo());
        assertThrows(AccesoNoAutorizadoException.class,()->tx().executeWithoutResult(t->leer(ajeno,caso.sesion())));
    }
}
