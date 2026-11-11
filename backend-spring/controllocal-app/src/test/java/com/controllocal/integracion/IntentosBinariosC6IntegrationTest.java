package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.planes.CategoriaAlmacenamiento;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository;
import com.controllocal.service.*;
import com.controllocal.service.archivo.SeguimientoSalidaBinaria;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.*;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.web.almacen.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** PG/T4 real para TX/locks/FK; almacén controlado TEST permite inyectar fallos, no certifica S3. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,IntentosBinariosC6IntegrationTest.AlmacenTest.class},
        webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-init-sql=SET lock_timeout='5s'"})
class IntentosBinariosC6IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @TestConfiguration static class AlmacenTest {@Bean @Primary Memoria almacenIo139(){return new Memoria();}}
    static class Memoria implements AlmacenDocumentos {
        final Map<String,byte[]> objetos=new ConcurrentHashMap<>();volatile boolean fallaRetiro,fallaPut,fallaLectura;
        final String identidad=AlmacenDocumentos.huellaIdentidad("TEST_IO139",UUID.randomUUID().toString()); public String identidadTecnica(){return identidad;} public String proveedor(){return "TEST_IO139";}
        public void guardarEnClave(String clave,byte[] bytes,String tipo){if(fallaPut)throw new AlmacenException("Fallo TEST",null);objetos.put(clave,bytes.clone());}
        public Optional<ArchivoDescargado> abrir(String clave){if(fallaLectura)throw new AlmacenException("Fallo lectura TEST",null);var b=objetos.get(clave);return b==null?Optional.empty():Optional.of(new ArchivoDescargado(b.clone(),"salida-test.pdf","application/pdf"));}
        public void eliminar(String clave){if(!fallaRetiro)objetos.remove(clave);}
        public Set<String> listarClaves(){return Set.copyOf(objetos.keySet());}
    }
    @Autowired ApplicationContext contexto;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired SeguimientoSalidaBinaria seguimiento;
    @Autowired IntentoSalidaBinariaRepository intentos;
    @Autowired SalidaBinariaPersistente salida;
    @Autowired Memoria almacen;
    @Autowired javax.sql.DataSource datosPrincipales;
    TransactionTemplate tx;OrigenesDePrueba.Equipo equipo;UUID op;byte[] bytes;
    // Sólo prueba el diario técnico: no acredita admisión comercial/cuota B0.
    final SalidaBinariaPersistente.Admision admisionTecnicaTest=i->{};
    @BeforeEach void iniciar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);op=UUID.randomUUID();bytes="SALIDA TEST IO139".getBytes(StandardCharsets.UTF_8);
        equipo=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class)).empresa();
        almacen.fallaPut=false;almacen.fallaRetiro=false;almacen.fallaLectura=false;
    }
    @AfterEach void terminar(){almacen.fallaPut=false;almacen.fallaRetiro=false;almacen.fallaLectura=false;EvidenciaC4DePrueba.limpiar();}
    long org(){return equipo.agente().idOrganizacion();}
    String hash(){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new AssertionError(e);}}
    SalidaBinariaPersistente.Guardado guardar(){return salida.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,"FUENTE_IO139_TEST","pdf","application/pdf",bytes,admisionTecnicaTest);}
    void vincular(SalidaBinariaPersistente.Guardado g){
        // Referencia técnica explícita TEST; no se acredita autorización comercial, firma o archivo de cliente.
        jdbc.update("""
            insert into archivo_encargo(organizacion_id,archivo_ref,id_rol_autor,finalidad,clave_proveedor,
              nombre,content_type,huella,registrado_en)
            values (?,?,?,'TITULARIDAD_ENCARGO',?,'salida-TEST.pdf','application/pdf',?,clock_timestamp())
            """,org(),UUID.randomUUID(),equipo.agente().idRolOperativo(),g.clave(),g.huella());
    }
    UsoAlmacenamientoB0 presupuesto(){
        var clasificador=new ClasificadorCargaAlmacenamientoB0(){
            public boolean atiende(UsoAlmacenamientoB0.Recurso r){return "IO139_TEST".equals(r.tipo());}
            public Clasificacion clasificar(UsoAlmacenamientoB0.Solicitud s,Actor a){if(!equipo.agente().equals(a))throw new AccesoNoAutorizadoException();
                return new Clasificacion(CategoriaAlmacenamiento.DOCUMENTO,Necesidad.ORDINARIA,"a".repeat(64));}
        };
        return new UsoAlmacenamientoB0(contexto.getBean(ContextoPresupuestoB0.class),contexto.getBean(PresupuestoIaRepository.class),
                contexto.getBean(UsoAlmacenamientoB0Repository.class),contexto.getBean(Documentos.class),List.of(clasificador));
    }
    UsoAlmacenamientoB0.Solicitud reservarEnPadre(){
        var sesiones=new SesionesComercialesDePrueba(jdbc,contexto.getBean(ContextosService.class),contexto.getBean(CuentaService.class),
                contexto.getBean(com.controllocal.persistence.repositorio.FactorAutenticacionRepository.class),contexto.getBean(com.controllocal.service.soporte.CifradoSecretos.class));
        var sid=sesiones.de(equipo.agente());
        var s=new UsoAlmacenamientoB0.Solicitud(UUID.randomUUID(),new UsoAlmacenamientoB0.Recurso("IO139_TEST",op.toString(),"FINALIDAD_IO139_TEST","a".repeat(64)),bytes.length,hash(),null);
        presupuesto().reservar(s,equipo.agente(),sid);return s;
    }
    @Test void diarioTecnicoSobreviveRollbackDelPadre(){
        tx.execute(s->{seguimiento.preparar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,"FUENTE_IO139_TEST",almacen.identidadTecnica(),"pdf","application/pdf",hash(),bytes.length);s.setRollbackOnly();return null;});
        var i=intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow();assertEquals("PREPARADO",i.estado());assertEquals(hash(),i.huella());assertFalse(almacen.objetos.containsKey(i.clave()));
    }
    @Test void fkRealOrgNoSeBloqueaConReservaB0PadreYConservaElHijoEnRollback(){
        new PlanesFinanciadosDePrueba(jdbc,gestor).asegurarTeam(org());
        var reserva=new AtomicReference<UsoAlmacenamientoB0.Solicitud>();
        tx.execute(s->{jdbc.execute("set local lock_timeout='3s'");reserva.set(reservarEnPadre());
            seguimiento.preparar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,"FUENTE_IO139_TEST",almacen.identidadTecnica(),"pdf","application/pdf",hash(),bytes.length);s.setRollbackOnly();return null;});
        assertTrue(intentos.ultimo(intentos.dueno(org(),null),op).isPresent());
        assertEquals(0,jdbc.queryForObject("select count(*) from b0_reserva_almacenamiento where organizacion_id=? and id_reserva=?",Integer.class,org(),reserva.get().reserva()));
    }
    @Test void callbackCommitConservaElBinarioSoloSiExisteElVinculo(){
        var f=tx.execute(s->{var g=guardar();vincular(g);return g;});assertNotNull(f);assertTrue(almacen.objetos.containsKey(f.clave()));
        assertEquals("VINCULADO",intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow().estado());
        var recuperada=tx.execute(s->salida.recuperar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,"FUENTE_IO139_TEST",admisionTecnicaTest));assertEquals(SalidaBinariaPersistente.EstadoConsulta.DISPONIBLE,recuperada.estado());assertEquals(f,recuperada.guardado());
    }
    @Test void rollbackRetiraYVerificaSinPerderElIntento(){
        var f=tx.execute(s->{var g=guardar();s.setRollbackOnly();return g;});assertNotNull(f);assertFalse(almacen.objetos.containsKey(f.clave()));
        var i=intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow();assertEquals("RETIRADO_VERIFICADO",i.estado());assertFalse(i.escrituraIncierta());assertNotNull(i.ultimaVerificacion());
    }
    @Test void borradoBestEffortFallidoQuedaPendienteYSeRecupera(){
        almacen.fallaRetiro=true;var f=tx.execute(s->{var g=guardar();s.setRollbackOnly();return g;});
        assertEquals("PENDIENTE_RETIRO",intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow().estado());assertTrue(almacen.objetos.containsKey(f.clave()));
        almacen.fallaRetiro=false;seguimiento.recuperarPendientes(almacen.identidadTecnica(),i->{almacen.eliminar(i.clave());return almacen.abrir(i.clave()).isEmpty();});
        assertEquals("RETIRADO_VERIFICADO",intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow().estado());assertFalse(almacen.objetos.containsKey(f.clave()));
    }
    @Test void nuevoIntentoTrasRetiroMantieneOperacionYElAntecedente(){
        var uno=tx.execute(s->{var g=guardar();s.setRollbackOnly();return g;});
        var dos=tx.execute(s->{var g=guardar();vincular(g);return g;});
        assertEquals(uno.operacion(),dos.operacion());assertNotEquals(uno.clave(),dos.clave());assertEquals(2,dos.numeroIntento());assertEquals(2,intentos.deOperacion(intentos.dueno(org(),null),op).size());
        assertFalse(almacen.objetos.containsKey(uno.clave()));assertTrue(almacen.objetos.containsKey(dos.clave()));
    }
    @Test void recuperacionRespetaUnaTxActivaQueTodaviaVaAVincular()throws Exception{
        var escrito=new CountDownLatch(1);var permitir=new CountDownLatch(1);var ref=new AtomicReference<SalidaBinariaPersistente.Guardado>();var pool=Executors.newSingleThreadExecutor();
        try{
            var futuro=pool.submit(()->tx.execute(s->{var g=guardar();ref.set(g);escrito.countDown();try{assertTrue(permitir.await(20,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}vincular(g);return g;}));
            var otraInstancia=new SeguimientoSalidaBinaria(intentos,contexto.getBean(com.controllocal.persistence.archivo.DiarioBinarioTecnico.class));
            assertTrue(escrito.await(20,TimeUnit.SECONDS));assertFalse(otraInstancia.completar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,almacen.identidadTecnica(),i->{throw new AssertionError("No debe tocar bytes de la TX activa.");}));
            assertTrue(almacen.objetos.containsKey(ref.get().clave()));permitir.countDown();var g=futuro.get(60,TimeUnit.SECONDS);
            assertEquals("VINCULADO",intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow().estado());assertTrue(almacen.objetos.containsKey(g.clave()));
        }finally{permitir.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }
    @Test void putInciertoQueApareceTardeSigueSiendoRecuperable(){
        tx.execute(s->{seguimiento.preparar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,"FUENTE_IO139_TEST",almacen.identidadTecnica(),"pdf","application/pdf",hash(),bytes.length);s.setRollbackOnly();return null;});
        var i=intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow();seguimiento.completar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,almacen.identidadTecnica(),x->almacen.abrir(x.clave()).isEmpty());
        assertEquals("RETIRADO_VERIFICADO",intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow().estado());assertTrue(intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow().escrituraIncierta());
        almacen.objetos.put(i.clave(),bytes.clone());seguimiento.recuperarPendientes(almacen.identidadTecnica(),x->{almacen.eliminar(x.clave());return almacen.abrir(x.clave()).isEmpty();});
        assertFalse(almacen.objetos.containsKey(i.clave()));assertEquals("RETIRADO_VERIFICADO",intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow().estado());
    }
    @Test void unaUuidNoConcedeAccesoEntreOrganizaciones(){
        tx.execute(s->{seguimiento.preparar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,"FUENTE_IO139_TEST",almacen.identidadTecnica(),"pdf","application/pdf",hash(),bytes.length);return null;});
        var otro=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class)).empresa();
        assertTrue(intentos.ultimo(intentos.dueno(otro.agente().idOrganizacion(),null),op).isEmpty());
        tx.execute(s->{seguimiento.preparar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(otro.agente().idOrganizacion()),op,"FUENTE_IO139_TEST",almacen.identidadTecnica(),"pdf","application/pdf",hash(),bytes.length);return null;});
        assertNotEquals(intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow().clave(),intentos.ultimo(intentos.dueno(otro.agente().idOrganizacion(),null),op).orElseThrow().clave());
    }
    @Test void padresOcupanTodoElPoolPeroElDiarioTieneSuCarrilIndependiente()throws Exception{
        assertInstanceOf(com.zaxxer.hikari.HikariDataSource.class,datosPrincipales);
        assertEquals(2,((com.zaxxer.hikari.HikariDataSource)datosPrincipales).getMaximumPoolSize());
        var listas=new CountDownLatch(2);var avanzar=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        UUID otra=UUID.randomUUID();
        var segundaInstancia=new SeguimientoSalidaBinaria(intentos,contexto.getBean(com.controllocal.persistence.archivo.DiarioBinarioTecnico.class));
        java.util.function.BiFunction<UUID,SeguimientoSalidaBinaria,UUID> accion=(operacion,servicio)->tx.execute(s->{
            assertEquals(1,jdbc.queryForObject("select 1",Integer.class));listas.countDown();
            try{assertTrue(avanzar.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}
            servicio.preparar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),operacion,"FUENTE_IO139_TEST",almacen.identidadTecnica(),"pdf","application/pdf",hash(),bytes.length);
            s.setRollbackOnly();return operacion;
        });
        try{
            var uno=pool.submit(()->accion.apply(op,seguimiento));var dos=pool.submit(()->accion.apply(otra,segundaInstancia));
            assertTrue(listas.await(10,TimeUnit.SECONDS));avanzar.countDown();assertEquals(op,uno.get(20,TimeUnit.SECONDS));assertEquals(otra,dos.get(20,TimeUnit.SECONDS));
            assertTrue(intentos.ultimo(intentos.dueno(org(),null),op).isPresent());assertTrue(intentos.ultimo(intentos.dueno(org(),null),otra).isPresent());
            assertEquals(1,contexto.getBeansOfType(org.springframework.transaction.PlatformTransactionManager.class).size(),"El carril técnico no sustituye ni publica otro gestor comercial.");
        }finally{avanzar.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }
    @Test void otroNamespaceNoLeeNiRetiraNiAdjudicaElIntento(){
        tx.execute(s->{seguimiento.preparar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,"FUENTE_IO139_TEST",almacen.identidadTecnica(),"pdf","application/pdf",hash(),bytes.length);return null;});
        var i=intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow();almacen.objetos.put(i.clave(),bytes.clone());
        var otro=new Memoria();otro.objetos.put(i.clave(),new byte[]{9});var otraSalida=new SalidaBinariaPersistente(otro,seguimiento);
        otraSalida.recuperarAlArranque();assertEquals("PREPARADO",intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow().estado());
        assertTrue(almacen.objetos.containsKey(i.clave()));assertArrayEquals(new byte[]{9},otro.objetos.get(i.clave()));
        assertThrows(ConflictoException.class,()->tx.execute(s->otraSalida.recuperar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,"FUENTE_IO139_TEST",admisionTecnicaTest)));
        assertThrows(ConflictoException.class,()->tx.execute(s->otraSalida.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,"FUENTE_IO139_TEST","pdf","application/pdf",bytes,admisionTecnicaTest)));
        salida.recuperarAlArranque();assertFalse(almacen.objetos.containsKey(i.clave()));assertArrayEquals(new byte[]{9},otro.objetos.get(i.clave()));
    }
    @Test void discoRealRetiraRollbackYConservaCommitConMarcadorPrivado(@org.junit.jupiter.api.io.TempDir java.nio.file.Path raiz){
        var disco=new AlmacenDisco(raiz.toString());var real=new SalidaBinariaPersistente(disco,seguimiento);
        var uno=tx.execute(s->{var g=real.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,"FUENTE_IO139_TEST","pdf","application/pdf",bytes,admisionTecnicaTest);s.setRollbackOnly();return g;});
        assertTrue(disco.abrir(uno.clave()).isEmpty());assertEquals("RETIRADO_VERIFICADO",intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow().estado());assertTrue(disco.listarClaves().isEmpty());
        var dos=tx.execute(s->{var g=real.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,"FUENTE_IO139_TEST","pdf","application/pdf",bytes,admisionTecnicaTest);vincular(g);return g;});
        assertArrayEquals(bytes,disco.abrir(dos.clave()).orElseThrow().contenido());assertEquals("VINCULADO",intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow().estado());
        assertEquals(disco.identidadTecnica(),new AlmacenDisco(raiz.toString()).identidadTecnica());assertEquals(Set.of(dos.clave()),disco.listarClaves());
    }
    @Test void sqlProtegeIdYNamespaceSinReatribuirHistoria(){
        tx.execute(s->{seguimiento.preparar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,"FUENTE_IO139_TEST",almacen.identidadTecnica(),"pdf","application/pdf",hash(),bytes.length);return null;});
        var i=intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow();
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update intento_salida_binaria set almacen_huella=? where id_intento=?","d".repeat(64),i.id()));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update intento_salida_binaria set id_intento=id_intento+1000000 where id_intento=?",i.id()));
        assertEquals(almacen.identidadTecnica(),intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow().almacenHuella());
    }
    @Test void denegacionAntesDePutConservaDiarioSinCrearBinario(){
        var capturada=new AtomicReference<SalidaBinariaPersistente.Intencion>();
        assertThrows(AccesoNoAutorizadoException.class,()->tx.execute(s->salida.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(org()),op,"FUENTE_IO139_TEST","pdf","application/pdf",bytes,i->{capturada.set(i);assertTrue(intentos.ultimo(intentos.dueno(org(),null),op).isPresent(),"La intención debe estar confirmada antes del hook.");throw new AccesoNoAutorizadoException();})));
        assertNotNull(capturada.get());assertEquals(almacen.identidadTecnica(),capturada.get().namespaceHash());
        var i=intentos.ultimo(intentos.dueno(org(),null),op).orElseThrow();assertFalse(almacen.objetos.containsKey(i.clave()));assertEquals("RETIRADO_VERIFICADO",i.estado());
    }
}
