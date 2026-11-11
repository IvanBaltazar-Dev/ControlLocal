package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.*;
import com.controllocal.service.GobiernoCatalogoCapturaService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.CifradoSecretos;
import com.controllocal.service.soporte.PasswordHasher;
import com.controllocal.service.soporte.Totp;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** C2: perfil privado con fuente ficticia explícita, CAS, conservación y candados PostgreSQL reales. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class GobiernoCatalogoCapturaIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired GobiernoCatalogoCapturaService catalogo;
    @Autowired ContextosService contextos;
    @Autowired CuentaService cuentas;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @MockitoSpyBean EventoDominioRepository eventos;
    private static final char[] PASSWORD="Catalogo de prueba personal 2026!".toCharArray();
    private static final String HASH=PasswordHasher.hash(PASSWORD);
    private Gobierno gobierno;
    @BeforeEach void preparar(){gobierno=gobierno();}
    @AfterEach void limpiar(){reset(eventos);}

    @Test void perfilCerradoNoCambiaClaveTipoNiBaseComunYNoAdmiteCodigosInventados(){
        long base=zonificacion();String tipo=jdbc.queryForObject("select tipo_dato from catalogo_atributo where id_catalogo_atributo=?",String.class,base);
        var p=crearPerfil("v1-test","Rotulo ficticio");
        var r=catalogo.validarOpcion(gobierno.org(),"zonificacion","COD-ENSAYO",p.referenciaCatalogo(),"JURISDICCION-ENSAYO");
        assertTrue(r.valida());assertEquals("Rotulo ficticio",r.rotulo());
        assertFalse(catalogo.validarOpcion(gobierno.org(),"zonificacion","OTRO",null,"JURISDICCION-ENSAYO").valida());
        assertEquals("JURISDICCION_FALTANTE",catalogo.opcionesVigentes(gobierno.org(),"zonificacion",null).motivo());
        assertEquals(tipo,jdbc.queryForObject("select tipo_dato from catalogo_atributo where id_catalogo_atributo=?",String.class,base));
        assertEquals(0,jdbc.queryForObject("select count(*) from catalogo_atributo where organizacion_id=? and clave='zonificacion'",Integer.class,gobierno.org()));
    }
    @Test void versionConservaSnapshotCodigoYRotuloAnteriorSinAceptarReferenciaObsoleta(){
        var inicial=crearPerfil("v1-test","Rotulo ficticio");
        var actual=catalogo.versionarPerfil(inicial.id(),inicial.version(),datosPerfil("v2-test","Rotulo corregido ficticio"),gobierno.actor(),gobierno.sesion());
        assertTrue(actual.version()>inicial.version());assertEquals(inicial,catalogo.consultarPerfil(inicial.id(),inicial.version(),gobierno.actor(),gobierno.sesion()));
        assertEquals("Rotulo corregido ficticio",catalogo.validarOpcion(gobierno.org(),"zonificacion","COD-ENSAYO",actual.referenciaCatalogo(),"JURISDICCION-ENSAYO").rotulo());
        assertEquals("REFERENCIA_NO_VIGENTE",catalogo.validarOpcion(gobierno.org(),"zonificacion","COD-ENSAYO",inicial.referenciaCatalogo(),"JURISDICCION-ENSAYO").motivo());
        assertThrows(ConflictoException.class,()->catalogo.versionarPerfil(inicial.id(),inicial.version(),datosPerfil("v3-test","Otro rotulo ficticio"),gobierno.actor(),gobierno.sesion()));
        assertEquals(1,revisiones(inicial.id()));
    }
    @Test void retiroConservaOpcionesYVersionAnteriorYNoEliminaLaObligacionDeDefinicion(){
        var inicial=crearPerfil("v1-test","Rotulo ficticio");
        var retiro=catalogo.retirarPerfil(inicial.id(),inicial.version(),gobierno.actor(),gobierno.sesion());
        assertFalse(retiro.activo());assertNotNull(retiro.retiradaEn());
        assertEquals(inicial,catalogo.consultarPerfil(inicial.id(),inicial.version(),gobierno.actor(),gobierno.sesion()));
        assertEquals(1,jdbc.queryForObject("select count(*) from perfil_catalogo_opcion where id_perfil=?",Integer.class,inicial.id()));
        assertEquals("PERFIL_NO_DISPONIBLE",catalogo.opcionesVigentes(gobierno.org(),"zonificacion","JURISDICCION-ENSAYO").motivo());
        assertTrue(catalogo.consultarDefinicion(zonificacion(),null,gobierno.actor(),gobierno.sesion()).activa());
    }
    @Test void snapshotAppendOnlySeProtegeTambienContraSqlDirecto(){
        var inicial=crearPerfil("v1-test","Rotulo ficticio");
        catalogo.versionarPerfil(inicial.id(),inicial.version(),datosPerfil("v2-test","Rotulo corregido ficticio"),gobierno.actor(),gobierno.sesion());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update revision_catalogo_gobernado set snapshot_json='{}' where organizacion_id=? and id_recurso=?",gobierno.org(),inicial.id()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("delete from revision_catalogo_gobernado where organizacion_id=? and id_recurso=?",gobierno.org(),inicial.id()));
        assertEquals(inicial,catalogo.consultarPerfil(inicial.id(),inicial.version(),gobierno.actor(),gobierno.sesion()));
    }
    @Test void perfilAjenoNoSeLeeNiValidaAunqueSeConozcaSuReferencia(){
        var inicial=crearPerfil("v1-test","Rotulo ficticio");var otro=gobierno();
        assertThrows(NoEncontradoException.class,()->catalogo.consultarPerfil(inicial.id(),null,otro.actor(),otro.sesion()));
        assertFalse(catalogo.validarOpcion(otro.org(),"zonificacion","COD-ENSAYO",inicial.referenciaCatalogo(),"JURISDICCION-ENSAYO").valida());
        assertThrows(AccesoNoAutorizadoException.class,()->catalogo.retirarDefinicion(zonificacion(),0,null,gobierno.actor(),gobierno.sesion()));
    }
    @Test void extensionOpcionalSeVersionaYRetiraSinSustitutaYConservaSuLectura(){
        String clave="test_c2_"+UUID.randomUUID().toString().replace("-","");
        var original=catalogo.crearDefinicion(datosDefinicion(clave,"Etiqueta ficticia"),gobierno.actor(),gobierno.sesion());
        var version=catalogo.versionarDefinicion(original.id(),original.version(),datosDefinicion(clave,"Etiqueta corregida ficticia"),gobierno.actor(),gobierno.sesion());
        var retirada=catalogo.retirarDefinicion(original.id(),version.version(),null,gobierno.actor(),gobierno.sesion());
        assertFalse(retirada.activa());assertEquals(original,catalogo.consultarDefinicion(original.id(),original.version(),gobierno.actor(),gobierno.sesion()));
        assertEquals(version,catalogo.consultarDefinicion(original.id(),version.version(),gobierno.actor(),gobierno.sesion()));
        assertTrue(catalogo.consultar(gobierno.actor(),gobierno.sesion()).definiciones().stream().anyMatch(d->d.id()==original.id()&&!d.activa()));
    }
    @Test void falloDeEventoReviertePerfilSnapshotYMutacionJuntos(){
        var inicial=crearPerfil("v1-test","Rotulo ficticio");
        doThrow(new IllegalStateException("Fallo de prueba al persistir el evento.")).when(eventos).save(any());
        assertThrows(IllegalStateException.class,()->catalogo.versionarPerfil(inicial.id(),inicial.version(),datosPerfil("v2-test","Rotulo corregido ficticio"),gobierno.actor(),gobierno.sesion()));
        reset(eventos);
        assertEquals(inicial,catalogo.consultarPerfil(inicial.id(),null,gobierno.actor(),gobierno.sesion()));assertEquals(0,revisiones(inicial.id()));
        assertEquals(0,jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and entidad_tipo='PERFIL_CATALOGO_OPCIONES' and entidad_id=? and tipo='PERFIL_CATALOGO_VERSIONADO'",Integer.class,gobierno.org(),inicial.id()));
    }
    @Test void dosVersionesConcurrentesConMismoCasProducenUnSoloCambio() throws Exception {
        var inicial=crearPerfil("v1-test","Rotulo ficticio");ExecutorService hilos=Executors.newFixedThreadPool(2);CountDownLatch salida=new CountDownLatch(1);
        try {
            var a=hilos.submit(()->{esperar(salida);return intentarVersion(inicial,"v2a-test");});
            var b=hilos.submit(()->{esperar(salida);return intentarVersion(inicial,"v2b-test");});salida.countDown();
            var resultados=List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertEquals(1,resultados.stream().filter(Perfil.class::isInstance).count());assertEquals(1,resultados.stream().filter(ConflictoException.class::isInstance).count());
            assertEquals(1,revisiones(inicial.id()));
        }finally{hilos.shutdownNow();}
    }
    @Test void validacionAntesDelEfectoMantienePerfilBloqueadoHastaCommit() throws Exception {
        var inicial=crearPerfil("v1-test","Rotulo ficticio");ExecutorService hilos=Executors.newFixedThreadPool(2);
        CountDownLatch leido=new CountDownLatch(1),liberar=new CountDownLatch(1);String nombre="catalogo-t4-"+UUID.randomUUID();
        try {
            var lectura=hilos.submit(()->new TransactionTemplate(gestor).execute(tx->{
                var r=catalogo.validarOpcion(gobierno.org(),"zonificacion","COD-ENSAYO",inicial.referenciaCatalogo(),"JURISDICCION-ENSAYO");
                assertTrue(r.valida());leido.countDown();esperar(liberar);return r;
            }));esperar(leido);
            var escritura=hilos.submit(()->new TransactionTemplate(gestor).execute(tx->{jdbc.queryForObject("select set_config('application_name',?,true)",String.class,nombre);
                return catalogo.versionarPerfil(inicial.id(),inicial.version(),datosPerfil("v2-test","Rotulo corregido ficticio"),gobierno.actor(),gobierno.sesion());}));
            long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean bloqueada=false;
            while(System.nanoTime()<limite&&!bloqueada){bloqueada=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where application_name=? and wait_event_type='Lock')",Boolean.class,nombre));if(!bloqueada)Thread.sleep(20);}
            assertTrue(bloqueada,"La escritura debe esperar el perfil validado por la transaccion del efecto.");assertFalse(escritura.isDone());
            liberar.countDown();lectura.get(10,TimeUnit.SECONDS);assertTrue(escritura.get(10,TimeUnit.SECONDS).version()>inicial.version());
            assertFalse(catalogo.validarOpcion(gobierno.org(),"zonificacion","COD-ENSAYO",inicial.referenciaCatalogo(),"JURISDICCION-ENSAYO").valida());
        }finally{liberar.countDown();hilos.shutdownNow();}
    }
    private Object intentarVersion(Perfil p,String version){try{return catalogo.versionarPerfil(p.id(),p.version(),datosPerfil(version,"Rotulo ficticio"),gobierno.actor(),gobierno.sesion());}catch(ConflictoException e){return e;}}
    private long revisiones(long perfil){return jdbc.queryForObject("select count(*) from revision_catalogo_gobernado where organizacion_id=? and clase_recurso='PERFIL_OPCIONES' and id_recurso=?",Long.class,gobierno.org(),perfil);}
    private long zonificacion(){return jdbc.queryForObject("select id_catalogo_atributo from catalogo_atributo where clave='zonificacion' and sujeto='PROPIEDAD' and organizacion_id is null",Long.class);}
    private DatosPerfil datosPerfil(String version,String rotulo){return new DatosPerfil(zonificacion(),"JURISDICCION-ENSAYO",version,"Fuente ficticia exclusivamente de prueba",List.of(new OpcionDefinida("COD-ENSAYO",rotulo,1,true)));}
    private Perfil crearPerfil(String version,String rotulo){return catalogo.crearPerfil(datosPerfil(version,rotulo),gobierno.actor(),gobierno.sesion());}
    private DatosDefinicion datosDefinicion(String clave,String rotulo){return new DatosDefinicion(clave,"PROPIEDAD","TEXTO",null,rotulo,null,null,100,null,null,null,null,"v1-test","Fuente ficticia exclusivamente de prueba",List.of(new Aplicacion("T",null,"OPC")),List.of());}
    private Gobierno gobierno(){
        long[] ids=new TransactionTemplate(gestor).execute(tx->{
            String marca=UUID.randomUUID().toString().replace("-","");String correo="catalogo-"+marca+"@example.invalid";
            long org=jdbc.queryForObject("insert into organizacion(codigo,nombre,modalidad,estado) values(?,?,'EMPRESARIAL','I') returning id_organizacion",Long.class,"CAT-"+marca.substring(0,20),"Catalogo ficticio de ensayo");
            long cuenta=jdbc.queryForObject("insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) values(?,?,clock_timestamp(),?) returning id_cuenta_acceso",Long.class,"catalogo-"+marca,correo,HASH);
            long persona=jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social) values(?,'Miembro ficticio de ensayo') returning id_persona",Long.class,org);
            long broker=rol(org,persona,"BROKER"),admin=rol(org,persona,"ADMIN");
            jdbc.update("insert into detalle_broker(organizacion_id,id_persona_rol,codigo_broker,fecha_designacion) values(?,?,?,(clock_timestamp() at time zone 'America/Lima')::date)",org,broker,"BRK-CAT-"+broker);
            jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,'BROKER',true,'A')",org,cuenta,persona);
            jdbc.update("update organizacion set estado='A' where id_organizacion=?",org);return new long[]{org,cuenta,persona,admin};
        });
        String correo=jdbc.queryForObject("select correo_propio from cuenta_acceso where id_cuenta_acceso=?",String.class,ids[1]);
        var inicial=contextos.iniciarAcceso(correo,PASSWORD,false).sesion();cuentas.iniciarMfa(inicial.referencia(),inicial.revision());
        var factor=factores.pendientesDe(ids[1]).getFirst();byte[] secreto=cifrado.descifrar(factor.getSecretoCifrado(),factor.getNonce(),factor.getVersionClave());
        var respaldos=cuentas.confirmarMfa(inicial.referencia(),inicial.revision(),Totp.generar(secreto,Totp.pasoDe(Instant.now())));
        var desafio=contextos.iniciarAcceso(correo,PASSWORD,true).desafio();var segura=contextos.completarSegundoFactor(desafio.token(),respaldos.getFirst(),null,null);
        var ctx=contextos.contextos(segura.referencia(),segura.revision()).stream().filter(c->c.idOrganizacion()==ids[0]&&Actor.TENANT_ADMIN.equals(c.rol())).findFirst().orElseThrow();
        var elegida=contextos.seleccionar(segura.referencia(),segura.revision(),ctx.referencia());return new Gobierno(ids[0],new Actor(ids[0],ids[2],ids[3],Actor.TENANT_ADMIN),ContextoSesion.de(elegida));
    }
    private long rol(long org,long persona,String rol){return jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,?,(clock_timestamp() at time zone 'America/Lima')::date) returning id_persona_rol",Long.class,org,persona,rol);}
    private static void esperar(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("No se libero la fase de prueba.");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    private record Gobierno(long org,Actor actor,ContextoSesion sesion) { }
}
