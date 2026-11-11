package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.ContextosService;
import com.controllocal.service.ContrasenaService;
import com.controllocal.service.CuentaService;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.soporte.CifradoSecretos;
import com.controllocal.service.soporte.NotificadorIdentidad;
import com.controllocal.service.soporte.PasswordHasher;
import com.controllocal.service.soporte.Totp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static com.controllocal.service.soporte.NotificadorIdentidad.Proposito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** C1 correo propio aprobado: PostgreSQL real; sólo se reemplaza el transporte externo. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=4")
class CuentaPersonalIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p) {BaseDeDatosDePruebas.registrar(p);}
    @Autowired CuentaService cuentas;
    @Autowired ContrasenaService contrasenas;
    @Autowired ContextosService contextos;
    @Autowired CuentaAccesoRepository repositorio;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestorTx;
    @MockitoBean NotificadorIdentidad canal;
    private final List<Entrega> entregas=new CopyOnWriteArrayList<>();
    private static final char[] PASSWORD="una frase privada de prueba".toCharArray();
    private static final char[] NUEVA="otra frase distinta de prueba".toCharArray();
    private static final char[] TERCERA="tercera frase diferente de prueba".toCharArray();

    private record Entrega(NotificadorIdentidad.Proposito proposito,NotificadorIdentidad.Destino destino,
                           NotificadorIdentidad.TokenEmitido enlace) { }
    private record Titular(long id,String correo,String usuario) { }

    @BeforeEach void transporteSeguroDePrueba() {
        entregas.clear();
        when(canal.entregaAlTitular()).thenReturn(true);
        doAnswer(i->{entregas.add(new Entrega(i.getArgument(0),i.getArgument(1),i.getArgument(2)));return null;})
                .when(canal).enviar(any(),any(),any());
    }

    @Test void altaPruebaCorreoAntesDeCrearCuentaYNoAbreSesion() {
        String correo=correo(),usuario=usuario();cuentas.solicitarAlta(correo);
        assertTrue(repositorio.buscarPorCorreo(correo).isEmpty());
        assertThrows(CredencialesInvalidasException.class,()->contextos.iniciarAcceso(correo,PASSWORD,false));
        var envio=entrega(ALTA_CORREO,correo);
        assertNull(envio.destino().idCuenta());
        var fila=jdbc.queryForMap("select hash_token,creado_en,expira_en,id_cuenta_acceso from token_acceso where hash_token=?",
                Idempotencia.huellaExacta(envio.enlace().token()));
        assertNull(fila.get("id_cuenta_acceso"));
        assertFalse(envio.enlace().token().equals(fila.get("hash_token")));
        assertEquals(1800L,jdbc.queryForObject("select extract(epoch from expira_en-creado_en)::bigint from token_acceso where hash_token=?",
                Long.class,Idempotencia.huellaExacta(envio.enlace().token())));
        cuentas.confirmarAlta(envio.enlace().token(),usuario,null,PASSWORD);
        var cuenta=repositorio.buscarPorCorreo(correo).orElseThrow();
        assertNotNull(cuenta.getCorreoConfirmadoEn());assertTrue(PasswordHasher.verificar(PASSWORD,cuenta.getContrasenaHash()));
        assertEquals(0,contador("sesion_acceso",cuenta.getId()));
        assertEquals(0,jdbc.queryForObject("select count(*) from usuario_organizacion where id_usuario=?",Integer.class,cuenta.getId()));
        assertThrows(CredencialesInvalidasException.class,()->contextos.iniciarAcceso(usuario,PASSWORD,false));
        var sesion=contextos.iniciarAcceso(correo,PASSWORD,false).sesion();
        assertEquals(cuenta.getId(),sesion.identidad().idCuenta());assertNull(sesion.activo());
        assertThrows(ReglaNegocioException.class,()->cuentas.confirmarAlta(envio.enlace().token(),usuario,null,PASSWORD));
    }

    @Test void reenviarAltaRetiraElEnlaceAnteriorSinCrearCuenta() {
        String correo=correo();cuentas.solicitarAlta(correo);String anterior=entrega(ALTA_CORREO,correo).enlace().token();
        cuentas.solicitarAlta(correo);String actual=entrega(ALTA_CORREO,correo).enlace().token();
        assertThrows(ReglaNegocioException.class,()->cuentas.confirmarAlta(anterior,usuario(),null,PASSWORD));
        cuentas.confirmarAlta(actual,usuario(),null,PASSWORD);
        int envios=entregas.size();cuentas.solicitarAlta(correo);assertEquals(envios,entregas.size());
    }

    @Test void cambioDeCorreoConservaAnteriorHastaConfirmarYRevocaSesionYRecuperacion() {
        var titular=alta();var sesion=abrir(titular);
        contrasenas.solicitarRecuperacion(titular.correo(),null,null);
        String recovery=entrega(RECUPERACION,titular.correo()).enlace().token();String nuevo=correo();
        cuentas.solicitarCambioCorreo(sesion.referencia(),sesion.revision(),nuevo,PASSWORD,null);
        assertEquals(titular.correo(),repositorio.findById(titular.id()).orElseThrow().getCorreoPropio());
        assertEquals(titular.id(),cuentas.consultarPropia(sesion.referencia(),sesion.revision()).idCuenta());
        cuentas.confirmarCambioCorreo(entrega(CAMBIO_CORREO,nuevo).enlace().token());
        assertEquals(nuevo,repositorio.findById(titular.id()).orElseThrow().getCorreoPropio());
        assertThrows(CredencialesInvalidasException.class,()->contextos.comprobarSesion(sesion.referencia(),sesion.revision(),null,null));
        assertThrows(ReglaNegocioException.class,()->contrasenas.canjear(recovery,NUEVA,null,null));
        assertThrows(CredencialesInvalidasException.class,()->contextos.iniciarAcceso(titular.correo(),PASSWORD,false));
        assertTrue(PasswordHasher.verificar(PASSWORD,repositorio.findById(titular.id()).orElseThrow().getContrasenaHash()));
    }

    @Test void contrasenaPropiaNoDependeDePertenenciaYRevocaTodosLosDispositivos() {
        var titular=alta();var a=abrir(titular);var b=abrir(titular);
        assertTrue(contextos.contextos(a.referencia(),a.revision()).isEmpty());
        cuentas.cambiarContrasena(a.referencia(),a.revision(),PASSWORD,NUEVA);
        assertTrue(PasswordHasher.verificar(NUEVA,repositorio.findById(titular.id()).orElseThrow().getContrasenaHash()));
        assertThrows(CredencialesInvalidasException.class,()->cuentas.consultarPropia(a.referencia(),a.revision()));
        assertThrows(CredencialesInvalidasException.class,()->cuentas.consultarPropia(b.referencia(),b.revision()));
        assertEquals(1,contador("credencial_password",titular.id()));
    }

    @Test void cambioDeCorreoConMfaActivoRequiereYConsumeElFactorPropio() {
        var titular=alta();var inicial=abrir(titular);
        cuentas.iniciarMfa(inicial.referencia(),inicial.revision());
        var pendiente=factores.pendientesDe(titular.id()).getFirst();
        byte[] secreto=cifrado.descifrar(pendiente.getSecretoCifrado(),pendiente.getNonce(),pendiente.getVersionClave());
        String codigo=Totp.generar(secreto,Totp.pasoDe(Instant.now()));
        var respaldos=cuentas.confirmarMfa(inicial.referencia(),inicial.revision(),codigo);
        var desafio=contextos.iniciarAcceso(titular.correo(),PASSWORD,true).desafio();
        var sesion=contextos.completarSegundoFactor(desafio.token(),respaldos.get(0),null,null);
        String nuevo=correo();int previos=entregas.size();
        assertThrows(ReglaNegocioException.class,()->cuentas.solicitarCambioCorreo(sesion.referencia(),sesion.revision(),nuevo,PASSWORD,null));
        assertEquals(previos,entregas.size());
        cuentas.solicitarCambioCorreo(sesion.referencia(),sesion.revision(),nuevo,PASSWORD,respaldos.get(1));
        assertNotNull(entrega(CAMBIO_CORREO,nuevo));
        assertThrows(ReglaNegocioException.class,()->cuentas.solicitarCambioCorreo(sesion.referencia(),sesion.revision(),correo(),PASSWORD,respaldos.get(1)));
        assertEquals(titular.correo(),repositorio.findById(titular.id()).orElseThrow().getCorreoPropio());
    }

    @Test void guardasDeCuentaReleenPendientesEnCoreSinDependerDelFiltroHttp() {
        var titular=alta();var sesion=abrir(titular);
        jdbc.update("update cuenta_acceso set debe_cambiar_contrasena=true where id_cuenta_acceso=?",titular.id());
        assertTrue(cuentas.consultarPropia(sesion.referencia(),sesion.revision()).debeCambiarContrasena());
        assertThrows(AccesoNoAutorizadoException.class,()->cuentas.iniciarMfa(sesion.referencia(),sesion.revision()));
        assertThrows(AccesoNoAutorizadoException.class,()->cuentas.estadoMfa(sesion.referencia(),sesion.revision()));
        jdbc.update("update cuenta_acceso set debe_cambiar_contrasena=false,debe_enrolar_mfa=true where id_cuenta_acceso=?",titular.id());
        assertTrue(cuentas.estadoMfa(sesion.referencia(),sesion.revision()).debeEnrolar());
        assertThrows(AccesoNoAutorizadoException.class,()->cuentas.cambiarContrasena(sesion.referencia(),sesion.revision(),PASSWORD,NUEVA));
        assertThrows(AccesoNoAutorizadoException.class,()->cuentas.regenerarCodigosMfa(sesion.referencia(),sesion.revision(),PASSWORD,"invalido"));
        assertThrows(AccesoNoAutorizadoException.class,()->cuentas.revocarMfaPropio(sesion.referencia(),sesion.revision(),PASSWORD,"invalido",null,null));
        assertThrows(AccesoNoAutorizadoException.class,()->cuentas.solicitarCambioCorreo(sesion.referencia(),sesion.revision(),correo(),PASSWORD,null));
        cuentas.iniciarMfa(sesion.referencia(),sesion.revision());
        var pendiente=factores.pendientesDe(titular.id()).getFirst();
        byte[] secreto=cifrado.descifrar(pendiente.getSecretoCifrado(),pendiente.getNonce(),pendiente.getVersionClave());
        assertFalse(cuentas.confirmarMfa(sesion.referencia(),sesion.revision(),Totp.generar(secreto,Totp.pasoDe(Instant.now()))).isEmpty());
    }

    @Test void cadaEnlaceMantieneSuFinalidadYCaducidadReal() {
        var titular=alta();contrasenas.solicitarRecuperacion(titular.correo(),null,null);
        String token=entrega(RECUPERACION,titular.correo()).enlace().token();
        assertThrows(ReglaNegocioException.class,()->cuentas.confirmarCambioCorreo(token));
        assertThrows(ReglaNegocioException.class,()->cuentas.confirmarAlta(token,usuario(),null,NUEVA));
        assertEquals("V",estadoToken(token));
        envejecerEnlace(token);
        assertThrows(ReglaNegocioException.class,()->contrasenas.canjear(token,NUEVA,null,null));
        assertTrue(PasswordHasher.verificar(PASSWORD,repositorio.findById(titular.id()).orElseThrow().getContrasenaHash()));
    }

    @Test void falloDePoliticaPosteriorAlCasHaceRollbackDeEnlaceEHistorial() {
        var titular=alta();contrasenas.solicitarRecuperacion(titular.correo(),null,null);
        String token=entrega(RECUPERACION,titular.correo()).enlace().token();
        assertThrows(ReglaNegocioException.class,()->contrasenas.canjear(token,"corta".toCharArray(),null,null));
        assertEquals("V",estadoToken(token));assertEquals(0,contador("credencial_password",titular.id()));
        contrasenas.canjear(token,NUEVA,null,null);assertEquals("C",estadoToken(token));
        assertTrue(PasswordHasher.verificar(NUEVA,repositorio.findById(titular.id()).orElseThrow().getContrasenaHash()));
    }

    @Test void rollbackExternoNoConservaCuentaNiConsumoNiAuditoriaDeExito() {
        String correo=correo();cuentas.solicitarAlta(correo);String token=entrega(ALTA_CORREO,correo).enlace().token();String usuario=usuario();
        long[] creada={0};
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(gestorTx).executeWithoutResult(tx->{
            cuentas.confirmarAlta(token,usuario,null,PASSWORD);creada[0]=repositorio.buscarPorCorreo(correo).orElseThrow().getId();
            throw new IllegalStateException("rollback deliberado");
        }));
        assertTrue(repositorio.buscarPorCorreo(correo).isEmpty());assertEquals("V",estadoToken(token));
        assertTrue(creada[0]>0);
        assertEquals(0,jdbc.queryForObject("select count(*) from evento_seguridad where id_cuenta_acceso=? and tipo='CUENTA_ALTA_CONFIRMADA'",Integer.class,creada[0]));
        cuentas.confirmarAlta(token,usuario,null,PASSWORD);assertTrue(repositorio.buscarPorCorreo(correo).isPresent());
    }

    @Test void dosConfirmacionesConcurrentesDeAltaCreanUnaSolaCuenta() throws Exception {
        String correo=correo();cuentas.solicitarAlta(correo);String token=entrega(ALTA_CORREO,correo).enlace().token();
        String usuario=usuario();assertEquals(1,concurrentes(()->cuentas.confirmarAlta(token,usuario,null,PASSWORD),
                ()->cuentas.confirmarAlta(token,usuario,null,PASSWORD)));
        assertEquals(1,jdbc.queryForObject("select count(*) from cuenta_acceso where correo_propio=?",Integer.class,correo));
        assertEquals("C",estadoToken(token));
    }

    @Test void dosCanjesConcurrentesNoCambianDosVecesLaContrasena() throws Exception {
        var titular=alta();contrasenas.solicitarRecuperacion(titular.correo(),null,null);
        String token=entrega(RECUPERACION,titular.correo()).enlace().token();
        assertEquals(1,concurrentes(()->contrasenas.canjear(token,NUEVA,null,null),()->contrasenas.canjear(token,TERCERA,null,null)));
        assertEquals("C",estadoToken(token));assertEquals(1,contador("credencial_password",titular.id()));
        String hash=repositorio.findById(titular.id()).orElseThrow().getContrasenaHash();
        assertTrue(PasswordHasher.verificar(NUEVA,hash)^PasswordHasher.verificar(TERCERA,hash));
    }

    @Test void casRechazaEnlaceQueCaducoMientrasEsperabaLaCuenta() throws Exception {
        var titular=alta();contrasenas.solicitarRecuperacion(titular.correo(),null,null);
        String token=entrega(RECUPERACION,titular.correo()).enlace().token();String aplicacion="cuenta-cas-"+UUID.randomUUID();
        CountDownLatch retenida=new CountDownLatch(1),liberar=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> bloqueo=pool.submit(()->new TransactionTemplate(gestorTx).executeWithoutResult(tx->{
                jdbc.queryForObject("select id_cuenta_acceso from cuenta_acceso where id_cuenta_acceso=? for update",Long.class,titular.id());
                retenida.countDown();esperar(liberar);
            }));
            assertTrue(retenida.await(10,TimeUnit.SECONDS));
            Future<?> canje=pool.submit(()->new TransactionTemplate(gestorTx).executeWithoutResult(tx->{
                jdbc.queryForObject("select set_config('application_name',?,true)",String.class,aplicacion);
                contrasenas.canjear(token,NUEVA,null,null);
            }));
            esperarBloqueo(aplicacion);
            envejecerEnlace(token);
            liberar.countDown();bloqueo.get(10,TimeUnit.SECONDS);
            var error=assertThrows(ExecutionException.class,()->canje.get(10,TimeUnit.SECONDS));
            assertInstanceOf(ReglaNegocioException.class,error.getCause());assertEquals("V",estadoToken(token));
            assertEquals(0,contador("credencial_password",titular.id()));
        } finally {liberar.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }

    private Titular alta() {
        String correo=correo(),usuario=usuario();cuentas.solicitarAlta(correo);
        cuentas.confirmarAlta(entrega(ALTA_CORREO,correo).enlace().token(),usuario,null,PASSWORD);
        return new Titular(repositorio.buscarPorCorreo(correo).orElseThrow().getId(),correo,usuario);
    }
    private ContextosService.SesionAccesoDTO abrir(Titular t) {return contextos.iniciarAcceso(t.correo(),PASSWORD,false).sesion();}
    private Entrega entrega(NotificadorIdentidad.Proposito p,String correo) {
        return entregas.stream().filter(e->e.proposito()==p&&correo.equals(e.destino().correoPropio())).reduce((a,b)->b).orElseThrow();
    }
    private int contador(String tabla,long id) {
        // Sólo dos nombres constantes internos; ningún identificador procede del cliente.
        if(!List.of("sesion_acceso","credencial_password").contains(tabla))throw new IllegalArgumentException();
        return jdbc.queryForObject("select count(*) from "+tabla+" where id_cuenta_acceso=?",Integer.class,id);
    }
    private String estadoToken(String secreto) {return jdbc.queryForObject("select estado from token_acceso where hash_token=?",String.class,Idempotencia.huellaExacta(secreto));}
    private void envejecerEnlace(String secreto) {
        // Fixture de un enlace de treinta minutos ya vencido: conserva creado_en < expira_en.
        jdbc.update("""
            with reloj as (select clock_timestamp() as ahora)
            update token_acceso set creado_en=reloj.ahora-interval '31 minutes',
              expira_en=reloj.ahora-interval '1 minute'
            from reloj where hash_token=?
            """,Idempotencia.huellaExacta(secreto));
    }
    private static String correo() {return "cuenta-"+UUID.randomUUID()+"@example.test";}
    private static String usuario() {return "usuario-"+UUID.randomUUID();}
    private static void esperar(CountDownLatch señal) {
        try {if(!señal.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout de bloqueo");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
    private static int concurrentes(Runnable a,Runnable b) throws Exception {
        CyclicBarrier salida=new CyclicBarrier(2);
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> primero=()->intentar(salida,a),segundo=()->intentar(salida,b);
            var fa=pool.submit(primero);var fb=pool.submit(segundo);
            return (fa.get(15,TimeUnit.SECONDS)?1:0)+(fb.get(15,TimeUnit.SECONDS)?1:0);
        }
    }
    private static boolean intentar(CyclicBarrier salida,Runnable accion) throws Exception {
        salida.await(10,TimeUnit.SECONDS);
        try{accion.run();return true;}catch(ReglaNegocioException esperado){return false;}
    }
    private void esperarBloqueo(String aplicacion) throws InterruptedException {
        Instant limite=Instant.now().plus(Duration.ofSeconds(10));
        while(Instant.now().isBefore(limite)) {
            if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where application_name=? and wait_event_type='Lock')",Boolean.class,aplicacion)))return;
            Thread.sleep(20);
        }
        fail("El canje no llego al candado de cuenta; no se afirma la prueba de espera.");
    }
}
