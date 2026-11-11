package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.PasswordHasher;
import com.controllocal.service.soporte.CifradoSecretos;
import com.controllocal.service.soporte.Totp;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PostgreSQL real: decisión de la organización, aceptación y retirada atómicas e identidad separada. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class InvitacionesMembresiaIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p) {BaseDeDatosDePruebas.registrar(p);}
    private static final char[] PASSWORD="Invitacion fixture personal 2026!".toCharArray();
    private static final String HASH=PasswordHasher.hash(PASSWORD);
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired InvitacionesMembresiaService invitaciones;
    @Autowired ContextosService contextos;
    @Autowired MembresiasService membresias;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired CuentaService cuentasPropias;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    @MockitoSpyBean ComandosIdempotentes comandos;
    @AfterEach void retirarSabotajeDePrueba() {reset(comandos);}

    @Test void aceptarNoModificaCredencialesNiSeleccionaUnContextoYRepetirNoDuplica() {
        var org=empresa();var cuenta=cuenta();var sesion=sesion(cuenta);
        var invitacion=emitir(org,cuenta,Instant.now().plusSeconds(600));
        var resultado=invitaciones.aceptar(invitacion.referencia(),sesion.referencia(),sesion.revision());
        var repetido=invitaciones.aceptar(invitacion.referencia(),sesion.referencia(),sesion.revision());
        assertEquals(resultado,repetido);assertEquals(1,activos(cuenta));
        assertNull(contextos.comprobarSesion(sesion.referencia(),sesion.revision(),null,null).activo());
        assertEquals(cuenta.correo(),jdbc.queryForObject("select correo_propio from cuenta_acceso where id_cuenta_acceso=?",String.class,cuenta.id()));
        assertEquals(HASH,jdbc.queryForObject("select contrasena_hash from cuenta_acceso where id_cuenta_acceso=?",String.class,cuenta.id()));
        assertNull(jdbc.queryForObject("select numero_documento from persona where id_persona=?",String.class,resultado.idPersona()));
        assertNull(jdbc.queryForObject("select nombres_o_razon_social from persona where id_persona=?",String.class,resultado.idPersona()));
        assertNotNull(jdbc.queryForObject("select aceptada_en from invitacion_membresia where referencia=?::uuid",Object.class,invitacion.referencia()));
        assertEquals(1,jdbc.queryForObject("select count(*) from evento_seguridad where tipo='ACCESO_TENANT_CONCEDIDO' and id_cuenta_acceso=? and organizacion_id=?",Integer.class,cuenta.id(),org.org()));
    }

    @Test void dosInvitacionesDeDosOrganizacionesNoCreanDosPertenenciasActivas() throws Exception {
        var a=empresa();var b=empresa();var cuenta=cuenta();var s1=sesion(cuenta);var s2=sesion(cuenta);
        var i1=emitir(a,cuenta,Instant.now().plusSeconds(600));var i2=emitir(b,cuenta,Instant.now().plusSeconds(600));
        var resultado=carrera(()->invitaciones.aceptar(i1.referencia(),s1.referencia(),s1.revision()),
                ()->invitaciones.aceptar(i2.referencia(),s2.referencia(),s2.revision()));
        assertEquals(1,resultado.stream().filter(x->x instanceof InvitacionesMembresiaService.PertenenciaAceptada).count());
        assertEquals(1,activos(cuenta));
        assertEquals(1,jdbc.queryForObject("select count(*) from invitacion_membresia where id_cuenta_aceptante=?",Integer.class,cuenta.id()));
    }

    @Test void retiradaYPrimeraAceptacionSeSerializanSinDobleEstado() throws Exception {
        var org=empresa();var cuenta=cuenta();var sesion=sesion(cuenta);
        var i=emitir(org,cuenta,Instant.now().plusSeconds(600));
        var resultado=carrera(()->invitaciones.aceptar(i.referencia(),sesion.referencia(),sesion.revision()),
                ()->invitaciones.retirar(i.referencia(),"Retiro expreso por gobierno.",org.admin(),org.contexto()));
        var estado=jdbc.queryForMap("select aceptada_en,retirada_en from invitacion_membresia where referencia=?::uuid",i.referencia());
        boolean aceptada=estado.get("aceptada_en")!=null,retirada=estado.get("retirada_en")!=null;
        assertNotEquals(aceptada,retirada);
        assertEquals(aceptada?1:0,activos(cuenta));
        assertEquals(1,resultado.stream().filter(x->x instanceof InvitacionesMembresiaService.PertenenciaAceptada
                || x instanceof InvitacionesMembresiaService.InvitacionRetirada).count());
    }

    @Test void perderGobiernoDelEmisorNoRetiraLaInvitacionDeLaOrganizacion() {
        var org=empresa();var cuenta=cuenta();var sesion=sesion(cuenta);
        var i=emitir(org,cuenta,Instant.now().plusSeconds(600));
        Actor reemplazo=new TransactionTemplate(gestor).execute(tx->miembroBrokerAdministrador(org.org()));
        contextoGobierno(reemplazo);
        new TransactionTemplate(gestor).executeWithoutResult(tx->{
            jdbc.update("update usuario_organizacion set administrar=false where organizacion_id=? and id_persona=?",org.org(),org.admin().idPersona());
        });
        assertNotNull(invitaciones.aceptar(i.referencia(),sesion.referencia(),sesion.revision()));
        assertNull(jdbc.queryForObject("select retirada_en from invitacion_membresia where referencia=?::uuid",Object.class,i.referencia()));
    }

    @Test void agenteDadoDeBajaPuedeAceptarBrokerSinCerrarRolesNiMoverSuCartera() {
        var org=empresa();var cuenta=cuenta();var s1=sesion(cuenta);
        long supervisor=jdbc.queryForObject("select id_persona_rol from persona_rol where organizacion_id=? and id_persona=? and tipo_rol='BROKER' and vigencia_hasta is null",Long.class,org.org(),org.admin().idPersona());
        var primera=invitaciones.emitir(new InvitacionesMembresiaService.DatosInvitacion(cuenta.correo(),null,
                "AGENTE",false,supervisor,null,null,null,Instant.now().plusSeconds(600),"Pertenencia inicial de ensayo."),org.admin(),org.contexto(),UUID.randomUUID().toString());
        var agente=invitaciones.aceptar(primera.referencia(),s1.referencia(),s1.revision());
        String codigo="IM-"+UUID.randomUUID().toString().substring(0,12);
        long propiedad=jdbc.queryForObject("""
            insert into propiedad(codigo,direccion,distrito,metraje,tipo_inmueble,uso,estado_registro,
              disponibilidad_comercial,organizacion_id)
            values(?,?,'ENSAYO-C1',100,'L','C','A','D',?) returning id_propiedad
            """,Long.class,codigo,"Direccion de ensayo "+codigo,org.org());
        propiedades.asignarResponsable(propiedad,agente.idRolOperativo(),
                "Asignacion expresa de la propiedad de ensayo antes de la baja",
                PropiedadUniversalService.ResponsableObservado.faltante(),org.admin());
        jdbc.update("insert into prospeccion(codigo_prospeccion,estado,id_propiedad,id_rol_agente,organizacion_id) values(?,'P',?,?,?)",
                "P-"+codigo,propiedad,agente.idRolOperativo(),org.org());
        long version=jdbc.queryForObject("select version from usuario_organizacion where id_usuario_organizacion=?",Long.class,agente.idMembresia());
        var baja=membresias.darBaja(agente.idMembresia(),version,"Baja formal para nueva atribucion aceptada.",org.admin(),org.contexto());
        assertNotNull(baja.bajaRealEn());
        var segunda=invitaciones.emitir(new InvitacionesMembresiaService.DatosInvitacion(cuenta.correo(),
                new InvitacionesMembresiaService.PersonaLocal(agente.idPersona(),null,null,null,null,null,null),
                "BROKER",false,null,null,null,null,Instant.now().plusSeconds(600),"Nueva atribucion de ensayo."),org.admin(),org.contexto(),UUID.randomUUID().toString());
        var nuevaSesion=sesion(cuenta);
        var broker=invitaciones.aceptar(segunda.referencia(),nuevaSesion.referencia(),nuevaSesion.revision());
        assertEquals(agente.idPersona(),broker.idPersona());assertEquals(agente.idMembresia(),broker.idMembresia());
        assertEquals(agente.idRolOperativo(),jdbc.queryForObject("select id_rol_responsable from propiedad where id_propiedad=?",Long.class,propiedad));
        assertEquals(agente.idRolOperativo(),jdbc.queryForObject("select id_rol_agente from prospeccion where id_propiedad=?",Long.class,propiedad));
        assertNull(jdbc.queryForObject("select vigencia_hasta from persona_rol where id_persona_rol=?",Object.class,agente.idRolOperativo()));
        assertEquals(java.util.List.of("BROKER"),contextos.contextos(nuevaSesion.referencia(),nuevaSesion.revision()).stream().map(ContextosService.Contexto::rol).toList());
    }

    @Test void elTiempoEsperandoUnCandadoNoReviveUnaInvitacionVencida() throws Exception {
        var org=empresa();var cuenta=cuenta();var sesion=sesion(cuenta);
        Instant limite=Instant.now().plusSeconds(3);var i=emitir(org,cuenta,limite);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch candado=new CountDownLatch(1),liberar=new CountDownLatch(1);
        try {
            Future<?> bloqueador=executor.submit(()->new TransactionTemplate(gestor).executeWithoutResult(tx->{
                jdbc.queryForObject("select id_invitacion_membresia from invitacion_membresia where referencia=?::uuid for update",Long.class,i.referencia());
                candado.countDown();esperar(liberar);
            }));
            assertTrue(candado.await(5,TimeUnit.SECONDS));
            Future<Object> aceptacion=executor.submit(()->intentar(()->invitaciones.aceptar(i.referencia(),sesion.referencia(),sesion.revision())));
            long inicio=System.nanoTime();boolean esperando=false;
            while(System.nanoTime()-inicio<TimeUnit.SECONDS.toNanos(5)) {
                Integer n=jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%invitacion_membresia%'",Integer.class);
                if(n!=null&&n>0){esperando=true;break;}Thread.sleep(20);
            }
            assertTrue(esperando,"la aceptación debe alcanzar realmente el candado PostgreSQL");
            while(Instant.now().isBefore(limite.plusMillis(100)))Thread.sleep(20);
            liberar.countDown();bloqueador.get(5,TimeUnit.SECONDS);
            assertInstanceOf(NoEncontradoException.class,aceptacion.get(5,TimeUnit.SECONDS));
            assertEquals(0,activos(cuenta));
        } finally {liberar.countDown();executor.shutdownNow();}
    }

    private InvitacionesMembresiaService.InvitacionEmitida emitir(Empresa e,Titular c,Instant limite) {
        return invitaciones.emitir(datos(c,limite),e.admin(),e.contexto(),UUID.randomUUID().toString());
    }
    private static InvitacionesMembresiaService.DatosInvitacion datos(Titular c,Instant limite) {
        return new InvitacionesMembresiaService.DatosInvitacion(c.correo(),null,
                "BROKER",false,null,null,null,null,limite,"Incorporacion de fixture autorizada.");
    }

    @Test void dosEmisionesConLaMismaClaveCreanUnSoloHechoYElMismoResultado() throws Exception {
        var org=empresa();var cuenta=cuenta();var datos=datos(cuenta,Instant.now().plusSeconds(600));String clave=UUID.randomUUID().toString();
        var resultados=carrera(()->invitaciones.emitir(datos,org.admin(),org.contexto(),clave),
                ()->invitaciones.emitir(datos,org.admin(),org.contexto(),clave));
        assertInstanceOf(InvitacionesMembresiaService.InvitacionEmitida.class,resultados.getFirst());
        assertEquals(resultados.getFirst(),resultados.getLast());assertEquals(1,invitacionesEn(org.org()));
        assertEquals(1,comandosEn(org.org(),clave));
    }

    @Test void repetirEmisionRetiradaDevuelveSnapshotOriginalSinReactivarla() {
        var org=empresa();var cuenta=cuenta();var datos=datos(cuenta,Instant.now().plusSeconds(600));String clave=UUID.randomUUID().toString();
        var primera=invitaciones.emitir(datos,org.admin(),org.contexto(),clave);
        var retirada=invitaciones.retirar(primera.referencia(),"Retirada expresa de ensayo.",org.admin(),org.contexto());
        var repetida=invitaciones.emitir(datos,org.admin(),org.contexto(),clave);
        assertEquals(primera,repetida);assertEquals(1,invitacionesEn(org.org()));
        assertEquals(retirada.retiradaEn(),jdbc.queryForObject("select retirada_en from invitacion_membresia where referencia=?::uuid",java.time.OffsetDateTime.class,primera.referencia()).toInstant());
        assertNull(jdbc.queryForObject("select aceptada_en from invitacion_membresia where referencia=?::uuid",Object.class,primera.referencia()));
    }

    @Test void reutilizarClaveConOtroContenidoNoConfirmaOtraDecision() {
        var org=empresa();var cuenta=cuenta();Instant limite=Instant.now().plusSeconds(600);String clave=UUID.randomUUID().toString();
        invitaciones.emitir(datos(cuenta,limite),org.admin(),org.contexto(),clave);
        assertThrows(ConflictoException.class,()->invitaciones.emitir(datos(cuenta,limite.plusSeconds(1)),org.admin(),org.contexto(),clave));
        assertEquals(1,invitacionesEn(org.org()));assertEquals(1,comandosEn(org.org(),clave));
    }

    @Test void unaClaveNoEntregaElResultadoDeOtroActorNiDeOtroTenant() {
        var a=empresa();var b=empresa();var cuenta=cuenta();var datos=datos(cuenta,Instant.now().plusSeconds(600));String clave=UUID.randomUUID().toString();
        var primera=invitaciones.emitir(datos,a.admin(),a.contexto(),clave);
        var otra=invitaciones.emitir(datos,b.admin(),b.contexto(),clave);
        assertNotEquals(primera.referencia(),otra.referencia());
        Actor segundoGobierno=new TransactionTemplate(gestor).execute(tx->miembroBrokerAdministrador(a.org()));
        var contexto=contextoGobierno(segundoGobierno);
        var fallo=assertThrows(ConflictoException.class,()->invitaciones.emitir(datos,segundoGobierno,contexto,clave));
        assertFalse(fallo.getMessage().contains(primera.referencia()));
        assertEquals(1,invitacionesEn(a.org()));assertEquals(1,invitacionesEn(b.org()));
    }

    @Test void falloTrasRegistrarLedgerRevierteInvitacionAuditoriaYComandoJuntos() {
        var org=empresa();var cuenta=cuenta();var datos=datos(cuenta,Instant.now().plusSeconds(600));String clave=UUID.randomUUID().toString();
        doAnswer(i->{i.callRealMethod();throw new IllegalStateException("Fallo de prueba posterior al ledger.");})
                .when(comandos).registrar(eq(org.admin()),eq(clave),anyString(),anyString(),anyString(),anyLong(),any(),anyString());
        assertThrows(IllegalStateException.class,()->invitaciones.emitir(datos,org.admin(),org.contexto(),clave));
        assertEquals(0,invitacionesEn(org.org()));assertEquals(0,comandosEn(org.org(),clave));
        assertEquals(0,jdbc.queryForObject("select count(*) from evento_seguridad where organizacion_id=? and tipo='INVITACION_EMITIDA'",Integer.class,org.org()));
        reset(comandos);assertNotNull(invitaciones.emitir(datos,org.admin(),org.contexto(),clave));
        assertEquals(1,invitacionesEn(org.org()));assertEquals(1,comandosEn(org.org(),clave));
    }

    private long invitacionesEn(long org) {return jdbc.queryForObject("select count(*) from invitacion_membresia where organizacion_id=?",Long.class,org);}
    private long comandosEn(long org,String clave) {return jdbc.queryForObject("select count(*) from comando_idempotente where organizacion_id=? and idempotency_key=?",Long.class,org,clave);}
    private ContextosService.SesionAccesoDTO sesion(Titular t) {
        return contextos.iniciarAcceso(t.correo(),PASSWORD,false).sesion();
    }
    private long activos(Titular t) {
        return jdbc.queryForObject("select count(*) from usuario_organizacion where id_usuario=? and estado='A'",Long.class,t.id());
    }
    private Empresa empresa() {
        Empresa creada=new TransactionTemplate(gestor).execute(tx->{
            String marca=UUID.randomUUID().toString().replace("-","");
            long org=jdbc.queryForObject("insert into organizacion(codigo,nombre,modalidad,estado) values(?,?,'EMPRESARIAL','I') returning id_organizacion",Long.class,
                    "IM-"+marca.substring(0,20),"Empresa de ensayo "+marca.substring(0,8));
            Actor admin=miembroBrokerAdministrador(org);
            jdbc.update("update organizacion set estado='A' where id_organizacion=?",org);
            return new Empresa(org,admin,null);
        });
        return new Empresa(creada.org(),creada.admin(),contextoGobierno(creada.admin()));
    }
    private Actor miembroBrokerAdministrador(long org) {
        Titular c=cuenta();
        long persona=jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social) values(?,?) returning id_persona",Long.class,org,"Miembro de ensayo");
        long broker=jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,'BROKER',(clock_timestamp() at time zone 'America/Lima')::date) returning id_persona_rol",Long.class,org,persona);
        long admin=jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,'ADMIN',(clock_timestamp() at time zone 'America/Lima')::date) returning id_persona_rol",Long.class,org,persona);
        jdbc.update("insert into detalle_broker(organizacion_id,id_persona_rol,codigo_broker,fecha_designacion) values(?,?,?,(clock_timestamp() at time zone 'America/Lima')::date)",org,broker,"BRK-"+broker);
        jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,'BROKER',true,'A')",org,c.id(),persona);
        return new Actor(org,persona,admin,Actor.TENANT_ADMIN);
    }
    private Titular cuenta() {
        String marca=UUID.randomUUID().toString().replace("-","");String correo="invit-"+marca+"@example.test";
        long id=jdbc.queryForObject("insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) values(?,?,clock_timestamp(),?) returning id_cuenta_acceso",Long.class,"invit-"+marca,correo,HASH);
        return new Titular(id,correo);
    }
    private ContextoSesion contextoGobierno(Actor actor) {
        long cuenta=jdbc.queryForObject("select id_usuario from usuario_organizacion where organizacion_id=? and id_persona=?",Long.class,actor.idOrganizacion(),actor.idPersona());
        String correo=jdbc.queryForObject("select correo_propio from cuenta_acceso where id_cuenta_acceso=?",String.class,cuenta);
        var inicial=contextos.iniciarAcceso(correo,PASSWORD,false).sesion();
        cuentasPropias.iniciarMfa(inicial.referencia(),inicial.revision());
        var pendiente=factores.pendientesDe(cuenta).getFirst();
        byte[] secreto=cifrado.descifrar(pendiente.getSecretoCifrado(),pendiente.getNonce(),pendiente.getVersionClave());
        var respaldos=cuentasPropias.confirmarMfa(inicial.referencia(),inicial.revision(),Totp.generar(secreto,Totp.pasoDe(Instant.now())));
        var reto=contextos.iniciarAcceso(correo,PASSWORD,true).desafio();
        var segura=contextos.completarSegundoFactor(reto.token(),respaldos.getFirst(),null,null);
        var opcion=contextos.contextos(segura.referencia(),segura.revision()).stream().filter(c->
                c.idOrganizacion()==actor.idOrganizacion() && c.idPersona()==actor.idPersona() && Actor.TENANT_ADMIN.equals(c.rol())).findFirst().orElseThrow();
        return ContextoSesion.de(contextos.seleccionar(segura.referencia(),segura.revision(),opcion.referencia()));
    }
    private static java.util.List<Object> carrera(Supplier<Object> a,Supplier<Object> b) throws Exception {
        ExecutorService e=Executors.newFixedThreadPool(2);CountDownLatch salida=new CountDownLatch(1);
        try {
            Future<Object> x=e.submit(()->{esperar(salida);return intentar(a);});
            Future<Object> y=e.submit(()->{esperar(salida);return intentar(b);});salida.countDown();
            return java.util.List.of(x.get(10,TimeUnit.SECONDS),y.get(10,TimeUnit.SECONDS));
        } finally {e.shutdownNow();}
    }
    private static Object intentar(Supplier<Object> r) {
        try{return r.get();}catch(NoEncontradoException|ReglaNegocioException|ConflictoException fallo){return fallo;}
    }
    private static void esperar(CountDownLatch l) {
        try{if(!l.await(10,TimeUnit.SECONDS))throw new AssertionError("No se libero el candado de la prueba.");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
    }
    private record Titular(long id,String correo) { }
    private record Empresa(long org,Actor admin,ContextoSesion contexto) { }
}
