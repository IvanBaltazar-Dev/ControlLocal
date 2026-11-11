package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.persistence.repositorio.ContextoCuentaFila;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import com.controllocal.service.*;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import com.controllocal.service.excepcion.ErrorMfaException;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.CifradoSecretos;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.soporte.PasswordHasher;
import com.controllocal.service.soporte.Totp;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.eq;

/** PostgreSQL real: cuenta propia independiente, pertenencias locales, revisiones y candados. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=4")
class ContextosSesionIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p) {BaseDeDatosDePruebas.registrar(p);}
    @Autowired ContextosService contextos;
    @Autowired MembresiasService membresias;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired UsuarioOrganizacionRepository usuariosOrganizacion;
    @Autowired EntityManagerFactory fabrica;
    @PersistenceContext EntityManager em;
    @Autowired CifradoSecretos cifrado;
    @MockitoSpyBean MfaService mfa;
    private static final String PASSWORD="PruebaContexto2026!";
    private static final String HASH=PasswordHasher.hash(PASSWORD.toCharArray());
    private long orgA,orgB,personaA,personaB,cuenta,rolA,rolB;
    private String alias,correo;
    @BeforeEach void fixture() {
        new TransactionTemplate(transacciones).executeWithoutResult(tx->{
            String sufijo=UUID.randomUUID().toString();alias="ctx-"+sufijo;correo=alias+"@example.invalid";
            orgA=org("CA-"+sufijo.substring(0,24));orgB=org("CB-"+sufijo.substring(0,24));
            jdbc.update("update organizacion set modalidad='INDIVIDUAL' where id_organizacion=?",orgB);
            personaA=persona(orgA,sufijo+"A");personaB=persona(orgB,sufijo+"B");
            cuenta=cuenta(alias,correo);rolA=rol(orgA,personaA,"BROKER");rolB=rol(orgB,personaB,"ADMIN");
            rol(orgA,personaA,"ADMIN");
            miembro(orgA,personaA,"BROKER","A");miembro(orgB,personaB,null,"I");
        });
    }
    private long org(String codigo) {return jdbc.queryForObject("insert into organizacion(codigo,nombre,estado,modalidad) values(?,?,'A','EMPRESARIAL') returning id_organizacion",Long.class,codigo,codigo);}
    private long cuenta(String usuario,String email) {return jdbc.queryForObject("insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) values(?,?,clock_timestamp(),?) returning id_cuenta_acceso",Long.class,usuario,email,HASH);}
    private long persona(long org,String ref) {return jdbc.queryForObject("insert into persona(organizacion_id,tipo_persona,tipo_documento,numero_documento,nombres_o_razon_social) values(?,'N','P',?,'Contexto prueba') returning id_persona",Long.class,org,ref.substring(0,28));}
    private long rol(long org,long persona,String tipo) {return jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,?,?) returning id_persona_rol",Long.class,org,persona,tipo,LocalDate.now(ZoneId.of("America/Lima")));}
    private void miembro(long org,long persona,String principal,String estado) {jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,?,true,?)",org,cuenta,persona,principal,estado);}
    private ContextosService.SesionAccesoDTO abrir() {return contextos.iniciarAcceso(correo,PASSWORD.toCharArray(),true).sesion();}
    private ContextosService.SesionAccesoDTO elegir(ContextosService.SesionAccesoDTO s,long org,String rol) {
        var c=contextos.contextos(s.referencia(),s.revision()).stream().filter(x->x.idOrganizacion()==org&&x.rol().equals(rol)).findFirst().orElseThrow();
        return contextos.seleccionar(s.referencia(),s.revision(),c.referencia());
    }
    @Test void cuentaUnicaNoDependeDePersonaYLoginNoSeleccionaContexto() {
        var s=abrir();assertNull(s.activo());assertEquals(cuenta,s.identidad().idCuenta());
        assertEquals(2,contextos.contextos(s.referencia(),s.revision()).size());
        assertEquals(1,jdbc.queryForObject("select count(*) from cuenta_acceso where id_cuenta_acceso=?",Integer.class,cuenta));
        var columnas=jdbc.queryForList("select column_name from information_schema.columns where table_name='cuenta_acceso'",String.class);
        assertTrue(columnas.contains("contrasena_hash"));
        assertFalse(columnas.stream().anyMatch(c->c.contains("persona")||c.contains("organizacion")||c.contains("credencial")));
        assertEquals(0,jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=current_schema() and table_name='credencial_usuario'",Integer.class));
        assertThrows(CredencialesInvalidasException.class,()->contextos.iniciarAcceso(alias,PASSWORD.toCharArray(),true));
        assertEquals(0,s.emitidaEn().getNano());assertEquals(0,s.venceEn().getNano());
        assertFalse(jdbc.queryForList("select referencia_hash from sesion_acceso where id_cuenta_acceso=?",String.class,cuenta).contains(s.referencia()));
    }
    @Test void regresoAAConservaActorPeroNoReviveRevisionNiReferencia() {
        var a=elegir(abrir(),orgA,Actor.BROKER);var b=elegir(a,orgA,Actor.TENANT_ADMIN);var a2=elegir(b,orgA,Actor.BROKER);
        assertEquals(a.activo().idRolOperativo(),a2.activo().idRolOperativo());assertEquals(a.revision()+2,a2.revision());
        assertNotEquals(a.activo().referencia(),a2.activo().referencia());
        assertThrows(CredencialesInvalidasException.class,()->contextos.comprobarSesion(a.referencia(),a.revision(),a.emitidaEn(),a.venceEn()));
    }
    @Test void bajaYNuevaAltaConservanCuentaPeroNoArrastranContextoAnterior() {
        reemplazoEnA();
        var a=elegir(abrir(),orgA,Actor.BROKER);
        jdbc.update("update usuario_organizacion set estado='I' where organizacion_id=? and id_usuario=?",orgA,cuenta);
        assertThrows(CredencialesInvalidasException.class,()->contextos.comprobarSesion(a.referencia(),a.revision(),null,null));
        // Prueba estructural de la base; no sustituye invitación/aceptación del caso de traslado.
        jdbc.update("update usuario_organizacion set estado='A' where organizacion_id=? and id_usuario=?",orgB,cuenta);
        var b=elegir(abrir(),orgB,Actor.TENANT_ADMIN);
        assertEquals(personaB,contextos.comprobarSesion(b.referencia(),b.revision(),null,null).activo().idPersona());
        assertEquals(orgA,jdbc.queryForObject("select organizacion_id from persona where id_persona=?",Long.class,personaA));
        assertEquals(cuenta,b.identidad().idCuenta());
        assertEquals("A",jdbc.queryForObject("select estado from cuenta_acceso where id_cuenta_acceso=?",String.class,cuenta));
        // A conserva y gobierna su persona histórica; esa mutación no gobierna el acceso global en B.
        jdbc.update("update persona set estado='I' where id_persona=?",personaA);
        jdbc.update("update persona_rol set vigencia_hasta=current_date where id_persona_rol=?",rolA);
        assertEquals(personaB,contextos.comprobarSesion(b.referencia(),b.revision(),null,null).activo().idPersona());
        assertEquals(cuenta,abrir().identidad().idCuenta());
    }
    @Test void segundaConsultaNoReutilizaEntidadGestionadaDeAutoridad() throws Exception {
        reemplazoEnA();
        var a=elegir(abrir(),orgA,Actor.BROKER);
        assertThrows(CredencialesInvalidasException.class,()->new TransactionTemplate(transacciones).executeWithoutResult(tx->{
            contextos.comprobarSesion(a.referencia(),a.revision(),null,null);
            try(ExecutorService pool=Executors.newSingleThreadExecutor()) {
                pool.submit(()->jdbc.update("update usuario_organizacion set estado='I' where organizacion_id=? and id_usuario=?",orgA,cuenta)).get(5,TimeUnit.SECONDS);
            } catch(Exception e) {throw new RuntimeException(e);}
            contextos.comprobarSesion(a.referencia(),a.revision(),null,null);
        }));
    }
    @Test void rollbackDeSeleccionNoConservaRevisionNiAuditoriaDeExito() {
        var a=elegir(abrir(),orgA,Actor.BROKER);long antes=auditorias();
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transacciones).executeWithoutResult(tx->{
            elegir(a,orgA,Actor.TENANT_ADMIN);throw new IllegalStateException("rollback deliberado");
        }));
        assertEquals(orgA,contextos.comprobarSesion(a.referencia(),a.revision(),null,null).activo().idOrganizacion());assertEquals(antes,auditorias());
    }
    private long auditorias() {return jdbc.queryForObject("select count(*) from evento_seguridad where id_cuenta_acceso=? and tipo='ACCESO_TENANT_USADO'",Long.class,cuenta);}
    private long reemplazoEnA() {
        return new TransactionTemplate(transacciones).execute(tx->{
            String ref=UUID.randomUUID().toString();long persona=persona(orgA,ref);
            long nueva=cuenta("relevo-"+ref,"relevo-"+ref+"@example.invalid");rol(orgA,persona,"BROKER");rol(orgA,persona,"ADMIN");
            jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,'BROKER',true,'A')",orgA,nueva,persona);
            return nueva;
        });
    }
    @Test void candadoOrdenaEfectoAntesDeCambioYRechazaLaRevisionViejaDespues() throws Exception {
        var a=elegir(abrir(),orgA,Actor.BROKER);var actor=new Actor(orgA,personaA,rolA,Actor.BROKER);
        String destino=contextos.contextos(a.referencia(),a.revision()).stream().filter(c->c.idOrganizacion()==orgA&&c.rol().equals(Actor.TENANT_ADMIN)).findFirst().orElseThrow().referencia();
        CountDownLatch retenida=new CountDownLatch(1),liberar=new CountDownLatch(1),iniciada=new CountDownLatch(1);
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            Future<?> efecto=pool.submit(()->new TransactionTemplate(transacciones).executeWithoutResult(tx->{
                contextos.exigirContexto(a.referencia(),a.revision(),actor);retenida.countDown();
                try {if(!liberar.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");} catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
            }));
            assertTrue(retenida.await(5,TimeUnit.SECONDS));
            Future<ContextosService.SesionAccesoDTO> cambio=pool.submit(()->{iniciada.countDown();return contextos.seleccionar(a.referencia(),a.revision(),destino);});
            assertTrue(iniciada.await(5,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->cambio.get(150,TimeUnit.MILLISECONDS));
            liberar.countDown();efecto.get(5,TimeUnit.SECONDS);assertEquals(Actor.TENANT_ADMIN,cambio.get(5,TimeUnit.SECONDS).activo().rol());
            assertThrows(CredencialesInvalidasException.class,()->contextos.exigirContexto(a.referencia(),a.revision(),actor));
        } finally {liberar.countDown();}
    }
    @Test void snapshotAntiguoSeRechazaAunqueLaSesionParezcaValida() {
        var s=abrir();var tx=new TransactionTemplate(transacciones);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(t->contextos.comprobarSesion(s.referencia(),s.revision(),null,null)));
    }
    @Test void logoutGlobalInvalidaAmbosDispositivos() {
        var a=abrir();var b=abrir();contextos.cerrar(a.referencia(),a.revision());
        assertThrows(CredencialesInvalidasException.class,()->contextos.comprobarSesion(a.referencia(),a.revision(),null,null));
        assertThrows(CredencialesInvalidasException.class,()->contextos.comprobarSesion(b.referencia(),b.revision(),null,null));
    }
    @Test void nombreDeUsuarioGlobalNoSeDuplicaNiPorOtroTenant() {
        assertThrows(DataIntegrityViolationException.class,()->cuenta(alias,"otro-"+correo));
        assertThrows(DataIntegrityViolationException.class,()->cuenta("otro-"+alias,correo.toUpperCase(Locale.ROOT)));
    }
    @Test void cuentaNoTieneDosOrganizacionesActivasAunqueLaSegundaSeaSoloGobierno() {
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "update usuario_organizacion set estado='A' where organizacion_id=? and id_usuario=?",orgB,cuenta));
    }
    @Test void ultimoBrokerEmpresarialRequiereReemplazoAntesDeSuBaja() {
        assertThrows(RuntimeException.class,()->new TransactionTemplate(transacciones).executeWithoutResult(tx->jdbc.update(
                "update usuario_organizacion set estado='I' where organizacion_id=? and id_usuario=?",orgA,cuenta)));
        assertEquals("A",jdbc.queryForObject("select estado from usuario_organizacion where organizacion_id=? and id_usuario=?",String.class,orgA,cuenta));
    }
    @Test void reactivarMismaPertenenciaNoResucitaJWTAnterior() {
        reemplazoEnA();var s=elegir(abrir(),orgA,Actor.BROKER);
        jdbc.update("update usuario_organizacion set estado='I' where organizacion_id=? and id_usuario=?",orgA,cuenta);
        jdbc.update("update usuario_organizacion set estado='A' where organizacion_id=? and id_usuario=?",orgA,cuenta);
        assertThrows(CredencialesInvalidasException.class,()->contextos.comprobarSesion(s.referencia(),s.revision(),null,null));
        assertEquals(orgA,elegir(abrir(),orgA,Actor.BROKER).activo().idOrganizacion());
    }
    @Test void revocacionDistingueCreacionPrecisaAunqueDosJwtTenganElMismoIat() {
        var anterior=abrir();var posterior=abrir();
        // Montaje exacto de la frontera persistida: no depende de caer casualmente en un segundo.
        Instant segundo=Instant.now().minusSeconds(2).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        jdbc.update("update sesion_acceso set emitida_en=?,creada_en=? where referencia_hash=?",
                java.sql.Timestamp.from(segundo),java.sql.Timestamp.from(segundo.plusNanos(100_000)),Idempotencia.huellaExacta(anterior.referencia()));
        jdbc.update("update cuenta_acceso set sesiones_invalidas_desde=? where id_cuenta_acceso=?",
                java.sql.Timestamp.from(segundo.plusNanos(200_000)),cuenta);
        jdbc.update("update sesion_acceso set emitida_en=?,creada_en=? where referencia_hash=?",
                java.sql.Timestamp.from(segundo),java.sql.Timestamp.from(segundo.plusNanos(300_000)),Idempotencia.huellaExacta(posterior.referencia()));
        assertThrows(CredencialesInvalidasException.class,()->contextos.comprobarSesion(anterior.referencia(),anterior.revision(),segundo,anterior.venceEn()));
        var vigente=contextos.comprobarSesion(posterior.referencia(),posterior.revision(),segundo,posterior.venceEn());
        assertEquals(segundo,vigente.emitidaEn());assertNotEquals(anterior.referencia(),vigente.referencia());
        Instant creada=jdbc.queryForObject("select creada_en from sesion_acceso where referencia_hash=?",java.sql.Timestamp.class,Idempotencia.huellaExacta(posterior.referencia())).toInstant();
        contextos.renovar(posterior.referencia(),posterior.revision());
        assertEquals(creada,jdbc.queryForObject("select creada_en from sesion_acceso where referencia_hash=?",java.sql.Timestamp.class,Idempotencia.huellaExacta(posterior.referencia())).toInstant());
        assertNotNull(contextos.iniciarAcceso(correo,PASSWORD.toCharArray(),true).sesion());
    }
    private record FactorPreparado(long id,String totp,List<String> respaldos) { }
    private FactorPreparado prepararFactor() {return prepararFactor(cuenta);}
    private FactorPreparado prepararFactor(long idCuenta) {
        mfa.iniciar(idCuenta);var f=factores.pendientesDe(idCuenta).getFirst();
        byte[] secreto=cifrado.descifrar(f.getSecretoCifrado(),f.getNonce(),f.getVersionClave());
        var respaldo=mfa.confirmar(idCuenta,Totp.generar(secreto,Totp.pasoDe(Instant.now())));
        // Paso no consumido de este montaje; la prueba comprueba el CAS real de dos consumidores.
        jdbc.update("update factor_autenticacion set ultimo_paso=-1 where id_factor=?",f.getId());
        return new FactorPreparado(f.getId(),Totp.generar(secreto,Totp.pasoDe(Instant.now())),respaldo);
    }
    private ContextosService.SesionAccesoDTO gobierno(long idCuenta,FactorPreparado factor) {
        String email=jdbc.queryForObject("select correo_propio from cuenta_acceso where id_cuenta_acceso=?",String.class,idCuenta);
        var reto=contextos.iniciarAcceso(email,PASSWORD.toCharArray(),true).desafio();
        return elegir(contextos.completarSegundoFactor(reto.token(),factor.respaldos().getFirst(),null,null),orgA,Actor.TENANT_ADMIN);
    }
    private ContextosService.SesionAccesoDTO otroGobierno() {
        long id=reemplazoEnA();return gobierno(id,prepararFactor(id));
    }
    private static Actor actor(ContextosService.SesionAccesoDTO s) {
        var c=s.activo();return new Actor(c.idOrganizacion(),c.idPersona(),c.idRolOperativo(),c.rol());
    }
    private long miembroActual() {return jdbc.queryForObject("select id_usuario_organizacion from usuario_organizacion where organizacion_id=? and id_usuario=?",Long.class,orgA,cuenta);}
    @Test void bajaFormalEsLocalVersionadaEIdempotenteSinDuplicarElEvento() {
        var gov=otroGobierno();var anterior=elegir(abrir(),orgA,Actor.BROKER);long miembro=miembroActual();
        long version=jdbc.queryForObject("select version from usuario_organizacion where id_usuario_organizacion=?",Long.class,miembro);
        var baja=membresias.darBaja(miembro,version,"Fin de pertenencia autorizado",actor(gov),ContextoSesion.de(gov));
        var repetida=membresias.darBaja(miembro,version,"Fin de pertenencia autorizado",actor(gov),ContextoSesion.de(gov));
        assertEquals(baja,repetida);assertEquals(version+1,baja.version());assertNotNull(baja.bajaRealEn());
        assertEquals(1,jdbc.queryForObject("select count(*) from evento_seguridad where tipo='MEMBRESIA_BAJA_FORMAL' and id_objetivo=?",Integer.class,personaA));
        assertEquals("A",jdbc.queryForObject("select estado from cuenta_acceso where id_cuenta_acceso=?",String.class,cuenta));
        assertEquals(correo,jdbc.queryForObject("select correo_propio from cuenta_acceso where id_cuenta_acceso=?",String.class,cuenta));
        assertThrows(CredencialesInvalidasException.class,()->contextos.comprobarSesion(anterior.referencia(),anterior.revision(),null,null));
        // Reingreso estructural para probar versión; aceptación real se demuestra en Invitaciones.
        jdbc.update("update usuario_organizacion set estado='A' where id_usuario_organizacion=?",miembro);
        assertThrows(ConflictoException.class,()->membresias.darBaja(miembro,version,"Solicitud anterior",actor(gov),ContextoSesion.de(gov)));
        assertEquals("A",jdbc.queryForObject("select estado from usuario_organizacion where id_usuario_organizacion=?",String.class,miembro));
        assertThrows(CredencialesInvalidasException.class,()->contextos.comprobarSesion(anterior.referencia(),anterior.revision(),null,null));
    }
    @Test void cambiarContextoAntesDeBajaRechazaSesionAnteriorSinRetirarAlObjetivo() {
        var gov=otroGobierno();elegir(gov,orgA,Actor.BROKER);
        assertThrows(AccesoNoAutorizadoException.class,()->membresias.darBaja(miembroActual(),0,"Solicitud anterior",actor(gov),ContextoSesion.de(gov)));
        assertEquals("A",jdbc.queryForObject("select estado from usuario_organizacion where id_usuario_organizacion=?",String.class,miembroActual()));
        assertEquals(0,jdbc.queryForObject("select count(*) from evento_seguridad where tipo='MEMBRESIA_BAJA_FORMAL' and id_objetivo=?",Integer.class,personaA));
    }
    @Test void dosGobiernosCruzadosSeSerializanSinDejarEmpresaSinGobierno() throws Exception {
        var govB=otroGobierno();var govA=gobierno(cuenta,prepararFactor());long miembroA=miembroActual();
        long miembroB=jdbc.queryForObject("select id_usuario_organizacion from usuario_organizacion where organizacion_id=? and id_usuario=?",Long.class,orgA,govB.identidad().idCuenta());
        long versionA=jdbc.queryForObject("select version from usuario_organizacion where id_usuario_organizacion=?",Long.class,miembroA);
        long versionB=jdbc.queryForObject("select version from usuario_organizacion where id_usuario_organizacion=?",Long.class,miembroB);
        CyclicBarrier inicio=new CyclicBarrier(2);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a=pool.submit(()->bajaConcurrente(inicio,miembroB,versionB,govA));
            Future<Boolean> b=pool.submit(()->bajaConcurrente(inicio,miembroA,versionA,govB));
            assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("select count(*) from usuario_organizacion where organizacion_id=? and estado='A'",Integer.class,orgA));
            assertEquals(1,jdbc.queryForObject("select count(*) from evento_seguridad where organizacion_id=? and tipo='MEMBRESIA_BAJA_FORMAL'",Integer.class,orgA));
            assertEquals(2,jdbc.queryForObject("select count(*) from cuenta_acceso c join usuario_organizacion u on u.id_usuario=c.id_cuenta_acceso where u.organizacion_id=? and c.estado='A'",Integer.class,orgA));
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }
    private boolean bajaConcurrente(CyclicBarrier inicio,long miembro,long version,ContextosService.SesionAccesoDTO gov) throws Exception {
        inicio.await(10,TimeUnit.SECONDS);
        try {membresias.darBaja(miembro,version,"Baja formal concurrente de prueba",actor(gov),ContextoSesion.de(gov));return true;}
        catch(AccesoNoAutorizadoException|ReglaNegocioException|ConflictoException e){return false;}
    }
    @Test void loginYRevocacionMfaTomanCuentaAntesDeFactorSinCicloDeCandados() throws Exception {
        var factor=prepararFactor();var desafio=contextos.iniciarAcceso(correo,PASSWORD.toCharArray(),true).desafio();
        String login="mfa-login-"+UUID.randomUUID(),revocar="mfa-revocar-"+UUID.randomUUID();
        CountDownLatch retenida=new CountDownLatch(1),liberar=new CountDownLatch(1);AtomicInteger pidLogin=new AtomicInteger();
        ExecutorService pool=Executors.newFixedThreadPool(3);
        try {
            Future<?> bloqueo=pool.submit(()->new TransactionTemplate(transacciones).executeWithoutResult(tx->{
                jdbc.queryForObject("select id_factor from factor_autenticacion where id_factor=? for update",Long.class,factor.id());
                retenida.countDown();esperar(liberar);
            }));
            assertTrue(retenida.await(10,TimeUnit.SECONDS));
            Future<ContextosService.SesionAccesoDTO> acceso=pool.submit(()->new TransactionTemplate(transacciones).execute(tx->{
                jdbc.queryForObject("select set_config('application_name',?,true)",String.class,login);
                pidLogin.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));
                return contextos.completarSegundoFactor(desafio.token(),factor.totp(),null,null);
            }));
            esperarBloqueo(login);
            Future<?> baja=pool.submit(()->new TransactionTemplate(transacciones).executeWithoutResult(tx->{
                jdbc.queryForObject("select set_config('application_name',?,true)",String.class,revocar);
                mfa.revocarPropio(cuenta,PASSWORD.toCharArray(),factor.totp(),null,null);
            }));
            esperarBloqueo(revocar);
            assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where application_name=? and ?=any(pg_blocking_pids(pid)))",Boolean.class,revocar,pidLogin.get())),"Revocar debe esperar Cuenta retenida por el login, antes de intentar Factor.");
            liberar.countDown();bloqueo.get(10,TimeUnit.SECONDS);assertNotNull(acceso.get(10,TimeUnit.SECONDS));
            var rechazo=assertThrows(ExecutionException.class,()->baja.get(10,TimeUnit.SECONDS));
            assertInstanceOf(ErrorMfaException.class,rechazo.getCause());assertTrue(factores.activoDeCuenta(cuenta));
        } finally {liberar.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }
    @Test void retoQueVenceEsperandoCuentaNoConsumeElCodigoDeRespaldo() throws Exception {
        var factor=prepararFactor();var desafio=contextos.iniciarAcceso(correo,PASSWORD.toCharArray(),true).desafio();
        String canje="mfa-caducidad-"+UUID.randomUUID();CountDownLatch retenida=new CountDownLatch(1),liberar=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> bloqueo=pool.submit(()->new TransactionTemplate(transacciones).executeWithoutResult(tx->{
                jdbc.queryForObject("select id_cuenta_acceso from cuenta_acceso where id_cuenta_acceso=? for update",Long.class,cuenta);
                retenida.countDown();esperar(liberar);
            }));
            assertTrue(retenida.await(10,TimeUnit.SECONDS));
            Future<?> resultado=pool.submit(()->new TransactionTemplate(transacciones).executeWithoutResult(tx->{
                jdbc.queryForObject("select set_config('application_name',?,true)",String.class,canje);
                contextos.completarSegundoFactor(desafio.token(),factor.respaldos().getFirst(),null,null);
            }));
            esperarBloqueo(canje);
            jdbc.update("update token_acceso set creado_en=clock_timestamp()-interval '6 minutes',expira_en=clock_timestamp()-interval '1 minute' where hash_token=?",Idempotencia.huellaExacta(desafio.token()));
            liberar.countDown();bloqueo.get(10,TimeUnit.SECONDS);
            var error=assertThrows(ExecutionException.class,()->resultado.get(10,TimeUnit.SECONDS));assertInstanceOf(ErrorMfaException.class,error.getCause());
            assertEquals(factor.respaldos().size(),jdbc.queryForObject("select count(*) from codigo_respaldo_mfa where id_factor=? and usado_en is null",Integer.class,factor.id()));
            assertEquals("V",jdbc.queryForObject("select estado from token_acceso where hash_token=?",String.class,Idempotencia.huellaExacta(desafio.token())));
        } finally {liberar.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }
    private static void esperar(CountDownLatch latch) {
        try {if(!latch.await(15,TimeUnit.SECONDS))throw new IllegalStateException("No se libero el candado de prueba.");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
    private void esperarBloqueo(String aplicacion) throws InterruptedException {
        long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<limite) {
            if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where application_name=? and wait_event_type='Lock')",Boolean.class,aplicacion)))return;
            Thread.sleep(10);
        }
        fail("No se observo el candado esperado de "+aplicacion);
    }
    @Test void cambioGlobalConcurrenteEsperaHastaConfirmarPruebaYSesionYDespuesLaRevoca() throws Exception {
        CountDownLatch probada=new CountDownLatch(1),continuar=new CountDownLatch(1),escribiendo=new CountDownLatch(1);
        AtomicInteger escritor=new AtomicInteger();String nuevaHash=PasswordHasher.hash("NuevaPrueba2026!".toCharArray());
        doAnswer(inv->{probada.countDown();if(!continuar.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");return false;})
                .when(mfa).exigeSegundoFactor(eq(cuenta));
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            Future<ContextosService.SesionAccesoDTO> acceso=pool.submit(this::abrir);
            assertTrue(probada.await(5,TimeUnit.SECONDS));
            Future<?> cambio=pool.submit(()->new TransactionTemplate(transacciones).executeWithoutResult(tx->{
                escritor.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));escribiendo.countDown();
                // El protocolo Core obtiene Cuenta WRITE antes de fijar su marca temporal.
                // Evaluar clock_timestamp dentro de un UPDATE que espera no demuestra ese orden.
                jdbc.queryForObject("select id_cuenta_acceso from cuenta_acceso where id_cuenta_acceso=? for update",Long.class,cuenta);
                jdbc.update("update cuenta_acceso set contrasena_hash=?,sesiones_invalidas_desde=clock_timestamp() where id_cuenta_acceso=?",nuevaHash,cuenta);
            }));
            try {
                assertTrue(escribiendo.await(5,TimeUnit.SECONDS));
                boolean esperando=false;long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
                while(System.nanoTime()<limite) {
                    esperando=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where pid=? and wait_event_type='Lock')",Boolean.class,escritor.get()));
                    if(esperando) break;Thread.sleep(10);
                }
                assertTrue(esperando,"El cambio global debe esperar el candado de la prueba y apertura Core.");
            } finally {continuar.countDown();}
            var sesion=acceso.get(5,TimeUnit.SECONDS);cambio.get(5,TimeUnit.SECONDS);
            assertThrows(CredencialesInvalidasException.class,()->contextos.comprobarSesion(sesion.referencia(),sesion.revision(),null,null));
        } finally {continuar.countDown();}
    }
    @Test void lecturaUnicaConservaLasDosListasConMenosSentenciasYMiembrosSinRol() {
        var abierta=elegir(abrir(),orgA,Actor.BROKER); // Abrir contexto fuera del contador de la lectura.
        var stats=fabrica.unwrap(SessionFactory.class).getStatistics();boolean anterior=stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            new TransactionTemplate(transacciones).executeWithoutResult(tx->{
                tx.setRollbackOnly();
                String sufijo=UUID.randomUUID().toString();long sinRol=org("SR-"+sufijo.substring(0,24));
                miembro(sinRol,persona(sinRol,sufijo),null,"I");
                long sinPersona=org("SP-"+sufijo.substring(0,24));
                jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,rol,administrar,estado) values(?,?,null,false,'I')",sinPersona,cuenta);
                compararListasYContarSentencias();
                jdbc.update("update organizacion set estado='I' where id_organizacion=?",orgA);
                compararListasYContarSentencias();
                assertThrows(CredencialesInvalidasException.class,()->contextos.comprobarSesion(abierta.referencia(),abierta.revision(),null,null));
            });
        } finally {stats.setStatisticsEnabled(anterior);}
    }
    /** Oráculo privado puntual de las dos lecturas sustituidas; no conserva una API antigua Main. */
    private void compararListasYContarSentencias() {
        var stats=fabrica.unwrap(SessionFactory.class).getStatistics();long antes=stats.getPrepareStatementCount();
        var miembros=em.createQuery("""
            select u.id,u.idUsuario,u.organizacionId,u.idPersona,u.rol,u.administrar,u.estado,o.estado,o.nombre,o.modalidad
            from UsuarioOrganizacion u,Organizacion o where u.idUsuario=:cuenta and o.id=u.organizacionId
            """,Object[].class).setParameter("cuenta",cuenta).getResultList();
        var roles=em.createQuery("""
            select r.id,r.organizacionId,p.id,p.estado,r.tipoRol,r.vigenciaDesde,r.vigenciaHasta
            from PersonaRol r join r.persona p,UsuarioOrganizacion u where u.idUsuario=:cuenta
            and r.organizacionId=u.organizacionId and p.id=u.idPersona and p.organizacionId=u.organizacionId
            """,Object[].class).setParameter("cuenta",cuenta).getResultList();
        assertEquals(2,stats.getPrepareStatementCount()-antes,"Oráculo: exactamente dos sentencias, sin endpoint ni setup.");
        antes=stats.getPrepareStatementCount();var actuales=usuariosOrganizacion.lecturaContextosDeCuenta(cuenta);
        assertEquals(1,stats.getPrepareStatementCount()-antes,"Lectura nueva: una sentencia, sin N+1.");
        var miembrosActuales=actuales.stream().map(f->Arrays.<Object>asList(f.idMembresia(),cuenta,f.idOrganizacion(),f.idPersona(),
                f.rolPrincipal(),f.administrar(),f.estadoMembresia(),f.estadoOrganizacion(),f.nombreOrganizacion(),f.modalidad())).distinct().toList();
        var rolesActuales=actuales.stream().filter(f->f.idRolOperativo()!=null).map(f->Arrays.<Object>asList(f.idRolOperativo(),
                f.idOrganizacion(),f.idPersona(),f.estadoPersona(),f.tipoRol(),f.desde(),f.hasta())).toList();
        Comparator<List<Object>> porId=Comparator.comparing(f->(Long)f.getFirst());
        assertEquals(miembros.stream().map(Arrays::asList).sorted(porId).toList(),miembrosActuales.stream().sorted(porId).toList());
        assertEquals(roles.stream().map(Arrays::asList).sorted(porId).toList(),rolesActuales.stream().sorted(porId).toList());
        assertTrue(actuales.stream().anyMatch(f->f.idRolOperativo()==null&&f.idPersona()!=null));
        assertTrue(actuales.stream().anyMatch(f->f.idPersona()==null));
    }
    @Test void segundaConsultaVeRetiroDeRolComprometidoSinReutilizarEntidadGestionada() {
        reemplazoEnA();var abierta=elegir(abrir(),orgA,Actor.BROKER);
        assertThrows(CredencialesInvalidasException.class,()->new TransactionTemplate(transacciones).executeWithoutResult(tx->{
            var gestionada=em.find(com.controllocal.domain.persona.PersonaRol.class,rolA);
            assertNull(gestionada.getVigenciaHasta());
            contextos.comprobarSesion(abierta.referencia(),abierta.revision(),null,null);
            int lector=jdbc.queryForObject("select pg_backend_pid()",Integer.class);
            try(ExecutorService pool=Executors.newSingleThreadExecutor()) {
                pool.submit(()->new TransactionTemplate(transacciones).executeWithoutResult(otra->{
                    assertNotEquals(lector,jdbc.queryForObject("select pg_backend_pid()",Integer.class));
                    jdbc.update("update persona_rol set vigencia_hasta=current_date where id_persona_rol=?",rolA);
                })).get(5,TimeUnit.SECONDS);
            } catch(Exception error) {throw new RuntimeException(error);}
            assertNull(gestionada.getVigenciaHasta(),"La entidad anterior permanece gestionada; no prueba autoridad actual.");
            var rolActual=usuariosOrganizacion.lecturaContextosDeCuenta(cuenta).stream()
                    .filter(f->Objects.equals(f.idRolOperativo(),rolA)).findFirst().orElseThrow();
            assertNotNull(rolActual.hasta(),"La proyección conserva el retiro comprometido aun con entidad gestionada antigua.");
            contextos.comprobarSesion(abierta.referencia(),abierta.revision(),null,null);
        }));
    }

}
