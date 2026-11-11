package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.comercial.TipoHitoContractual;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.repositorio.HistoriaPublicacionRepository;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.service.Actor;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.PublicacionService.*;
import com.controllocal.service.actuacion.ActuacionesService.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Procedencia;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.controllocal.service.PublicacionService.*;
import static com.controllocal.integracion.soporte.FormalizacionesDePrueba.*;

/** C6/AC079–086, contra SQL y protocolo humano reales. No certifica un clic ni un canal externo. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class PublicacionesC6IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired PublicacionService publicaciones;
    @MockitoSpyBean HistoriaPublicacionRepository historia;
    @MockitoSpyBean ComandoIdempotenteRepository recibos;
    FormalizacionesDePrueba b;
    @BeforeEach void iniciar(TestInfo info){EvidenciaC4DePrueba.iniciar(info);b=new FormalizacionesDePrueba(contexto);}
    @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}
    ActividadNegociacionDePrueba.Caso caso(){return b.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",true);}
    DatosContenido contenido(){return new DatosContenido("WEB_PROPIA",null,new BigDecimal("2500"),"PEN","Local TEST autorizado",null);}
    DatosBorrador datos(ActividadNegociacionDePrueba.Caso c,DatosContenido d){return new DatosBorrador(c.encargo(),b.encargos.consultar(c.encargo(),c.agente()).version(),null,null,d);}
    FichaPublicacion crear(ActividadNegociacionDePrueba.Caso c,DatosContenido d){return publicaciones.crearBorradorAnuncio(datos(c,d),c.agente(),c.sesion(),clave(),procedencia());}
    Propuesta preparar(String cap,ActividadNegociacionDePrueba.Caso c,FichaPublicacion f,DatosContenido d,EstadoExterno externo){
        return b.actuaciones.prepararPublicacion(cap,c.agente(),new DatosCambio(c.encargo(),b.encargos.consultar(c.encargo(),c.agente()).version(),
                f.id(),f.version(),d,externo,null,(PAUSAR.equals(cap)||FINALIZAR.equals(cap))?"Motivo expreso del responsable TEST":null),c.sesion(),procedencia());
    }
    FichaPublicacion confirmar(Propuesta p,ActividadNegociacionDePrueba.Caso c){return b.humanas.confirmar(p,c.agente(),c.sesion(),FichaPublicacion.class).datos();}
    FichaPublicacion publicar(ActividadNegociacionDePrueba.Caso c){return confirmar(preparar(PUBLICAR,c,crear(c,contenido()),null,null),c);}
    int cuenta(String tabla,ActividadNegociacionDePrueba.Caso c,long anuncio){return b.jdbc.queryForObject("select count(*) from "+tabla+" where organizacion_id=? and id_publicacion=?",Integer.class,c.agente().idOrganizacion(),anuncio);}
    int precios(ActividadNegociacionDePrueba.Caso c){return b.jdbc.queryForObject("select count(*) from precio_propiedad where organizacion_id=? and id_captacion=? and hito='P'",Integer.class,c.agente().idOrganizacion(),c.encargo());}
    record Intento(FichaPublicacion ficha,RuntimeException fallo){boolean exito(){return ficha!=null;}}
    Intento intentar(Callable<FichaPublicacion> accion){try{return new Intento(accion.call(),null);}catch(RuntimeException ex){return new Intento(null,ex);}catch(Exception ex){throw new AssertionError(ex);}}
    List<Intento> competir(Callable<FichaPublicacion> a,Callable<FichaPublicacion> z)throws Exception{
        var listas=new CountDownLatch(2);var salir=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var uno=pool.submit(()->EvidenciaC4DePrueba.enHilo(()->{listas.countDown();assertTrue(salir.await(20,TimeUnit.SECONDS));return intentar(a);}));
            var dos=pool.submit(()->EvidenciaC4DePrueba.enHilo(()->{listas.countDown();assertTrue(salir.await(20,TimeUnit.SECONDS));return intentar(z);}));
            assertTrue(listas.await(20,TimeUnit.SECONDS));salir.countDown();return List.of(uno.get(60,TimeUnit.SECONDS),dos.get(60,TimeUnit.SECONDS));
        }finally{salir.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }

    @Test void ac080BorradorVacioSinEncargoHabilitadoNoPublicaNiConsume(){
        var c=b.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",false);
        var f=crear(c,null);assertEquals("B",f.estado());assertNull(f.importePublicado());assertNull(f.fechaPublicacion());assertNull(f.moneda());
        assertEquals(1,cuenta("version_publicacion",c,f.id()));assertEquals(0,precios(c));
        assertFalse(publicaciones.evaluarPublicabilidad(c.encargo(),null,c.agente()).cumple());
    }
    @Test void ac081EdicionPrivadaNoPublicaNiRequierePub(){
        var c=caso();var f=crear(c,null);var d=new DatosBorrador(c.encargo(),f.versionEncargo(),f.id(),f.version(),contenido());
        var editada=publicaciones.editarBorradorAnuncio(d,c.agente(),c.sesion(),clave(),procedencia());
        assertEquals("B",editada.estado());assertEquals(2,editada.version());assertNull(editada.fechaPublicacion());assertEquals(0,precios(c));
    }
    @Test void ac079EvaluacionExplicaYNoEsUnaConcesion(){
        var c=caso();var e=publicaciones.evaluarPublicabilidad(c.encargo(),"WEB_PROPIA",c.agente());
        assertTrue(e.cumple(),()->e.motivos().toString());assertEquals(c.encargo(),e.encargo());assertEquals(c.propiedad(),e.propiedad());
        assertNotNull(e.huellaContexto());assertFalse(e.versionesOrigen().isEmpty());assertTrue(publicaciones.listarDeEncargo(c.encargo(),c.agente()).isEmpty());
        assertEquals(0,precios(c));
    }
    @Test void ac082PrepararYRevisarNoPublicaConfirmarSi(){
        var c=caso();var f=crear(c,contenido());var p=preparar(PUBLICAR,c,f,null,null);
        var revision=b.humanas.revisar(p,c.agente(),c.sesion());assertEquals("B",publicaciones.obtener(c.encargo(),f.id(),c.agente()).estado());assertEquals(0,precios(c));
        var resultado=b.actuaciones.confirmar(c.agente(),p.referencia(),c.sesion(),revision,procedencia());assertEquals("EXITO",resultado.estado());
        var publicada=publicaciones.obtener(c.encargo(),f.id(),c.agente());assertEquals("P",publicada.estado());assertEquals(2,publicada.version());
        assertNotNull(publicada.fechaPublicacion());assertNull(publicada.estadoExterno());assertEquals(1,precios(c));assertEquals(2,cuenta("version_publicacion",c,f.id()));
        var version=publicaciones.consultarHistoria(c.encargo(),f.id(),c.agente()).getLast().contenido();
        long precio=((Number)version.get("precioRegistradoEnEstaVersion")).longValue();
        assertEquals(c.encargo(),b.jdbc.queryForObject("select id_captacion from precio_propiedad where organizacion_id=? and id_precio=? and hito='P'",Long.class,c.agente().idOrganizacion(),precio));
        assertEquals(precio,b.jdbc.queryForObject("select (carga_util::jsonb->>'precioRegistradoEnEstaVersion')::bigint from evento_dominio where organizacion_id=? and entidad_tipo='PUBLICACION' and entidad_id=? and tipo='PUBLICACION_VERSIONADA' and carga_util::jsonb->>'actuacion'=?",Long.class,c.agente().idOrganizacion(),f.id(),PUBLICAR));
    }
    @Test void ac083TextoNuevoNoDuplicaPrecioNiModificaPacto(){
        var c=caso();var f=publicar(c);var antes=b.encargos.consultar(c.encargo(),c.agente()).condiciones();
        var nuevo=new DatosContenido("WEB_PROPIA",null,new BigDecimal("2500"),"PEN","Título comercial rectificado",null);
        var editada=confirmar(preparar(ACTUALIZAR_VISIBLE,c,f,nuevo,null),c);
        assertEquals(3,editada.version());assertEquals("Título comercial rectificado",editada.tituloAnuncio());assertEquals(1,precios(c));
        assertEquals(antes,b.encargos.consultar(c.encargo(),c.agente()).condiciones());
        assertEquals(3,publicaciones.consultarHistoria(c.encargo(),f.id(),c.agente()).size());
    }
    @Test void nuevoPrecioOMonedaDejaHechoPropioSinSobrescribir(){
        var c=caso();var f=publicar(c);
        var dos=confirmar(preparar(ACTUALIZAR_VISIBLE,c,f,new DatosContenido("WEB_PROPIA",null,new BigDecimal("2600"),"PEN","Local TEST",null),null),c);
        var tres=confirmar(preparar(ACTUALIZAR_VISIBLE,c,dos,new DatosContenido("WEB_PROPIA",null,new BigDecimal("2600"),"USD","Local TEST",null),null),c);
        assertEquals(3,precios(c));assertEquals("USD",tres.moneda());assertEquals("PEN",f.moneda());
        assertEquals(4,publicaciones.consultarHistoria(c.encargo(),f.id(),c.agente()).size());
    }
    @Test void composicionConPrecioTotalConservaSuObjetoSinAsignarUnPrecioIndividual(){
        var c=b.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",false);
        var antes=b.encargos.consultar(c.encargo(),c.agente());
        var comp=b.encargos.declararComposicion(c.encargo(),new com.controllocal.service.encargo.EncargoService.DatosComposicion(
                List.of(new com.controllocal.service.encargo.EncargoService.Unidad(c.propiedad(),null,null,"Sin desglose individual TEST")),
                new BigDecimal("2500"),"PEN","Total de composición declarado TEST",antes.version()),c.agente(),c.sesion(),clave(),procedencia());
        var banco=new EncargosDePrueba(b.jdbc,b.encargos,contexto.getBean(org.springframework.transaction.PlatformTransactionManager.class),
                contexto.getBean(com.controllocal.service.archivo.AccesoArchivoService.class),contexto.getBean(com.controllocal.web.almacen.AlmacenDocumentos.class),
                b.actuaciones,contexto.getBean(com.controllocal.service.soporte.Documentos.class));
        banco.habilitarExistente(c.encargo(),new EncargosDePrueba.Caso(c.propiedad(),com.controllocal.domain.inmueble.OperacionInmobiliaria.ALQUILER,
                hoy(),hoy().plusMonths(6),false,EncargosDePrueba.alquiler(new BigDecimal("2500"),"PEN",BigDecimal.ONE,"PROPIETARIO_TEST","AGENCIA_TEST"),
                new EncargosDePrueba.Actuante(c.agente(),c.sesion()),new EncargosDePrueba.Actuante(c.equipo().broker(),b.sesiones.de(c.equipo().broker()))));
        var p=publicar(c);assertEquals(comp.id(),p.composicion());assertEquals(comp.version(),p.versionComposicion());assertEquals(0,precios(c));
        var e=publicaciones.evaluarPublicabilidad(c.encargo(),"WEB_PROPIA",c.agente());assertEquals(comp.id(),e.composicion());assertEquals(comp.version(),e.versionesOrigen().get("composicion"));
        var historia=publicaciones.consultarHistoria(c.encargo(),p.id(),c.agente()).getLast().contenido();
        assertEquals(comp.id(),((Number)historia.get("composicion")).longValue());assertEquals(comp.version(),((Number)historia.get("versionComposicion")).longValue());
        assertEquals(0,new BigDecimal("2500").compareTo(p.importePublicado()));
    }
    @Test void ac084PausaConservaExposicionYNoCierraEncargo(){
        var c=caso();var f=publicar(c);var pausada=confirmar(preparar(PAUSAR,c,f,null,null),c);
        assertEquals("S",pausada.estado());assertEquals(f.fechaPublicacion(),pausada.fechaPublicacion());assertNull(pausada.estadoExterno());
        assertEquals("APROBADO",b.encargos.consultar(c.encargo(),c.agente()).estado());assertEquals(1,precios(c));
    }
    @Test void ac085ReanudaElMismoAnuncioSinHitoDuplicado(){
        var c=caso();var f=publicar(c);var s=confirmar(preparar(PAUSAR,c,f,null,null),c);var r=confirmar(preparar(REANUDAR,c,s,null,null),c);
        assertEquals(f.id(),r.id());assertEquals("P",r.estado());assertEquals(f.fechaPublicacion(),r.fechaPublicacion());assertEquals(1,precios(c));assertEquals(4,cuenta("version_publicacion",c,f.id()));
    }
    @Test void ac086CierreConservaHistoriaNoPruebaRetiroExterno(){
        var c=caso();var f=publicar(c);var cerrada=confirmar(preparar(FINALIZAR,c,f,null,null),c);
        assertEquals("C",cerrada.estado());assertNotNull(cerrada.fechaBaja());assertNull(cerrada.estadoExterno());assertEquals(3,cuenta("version_publicacion",c,f.id()));
        assertThrows(ConflictoException.class,()->preparar(REANUDAR,c,cerrada,null,null));
        assertEquals("APROBADO",b.encargos.consultar(c.encargo(),c.agente()).estado());
    }
    @Test void aislamientoDeOrganizacionYResponsableTambienEnHistoria(){
        var c=caso();var ajena=caso();var f=publicar(c);
        assertThrows(NoEncontradoException.class,()->publicaciones.obtener(c.encargo(),f.id(),ajena.agente()));
        assertThrows(NoEncontradoException.class,()->publicaciones.consultarHistoria(c.encargo(),f.id(),ajena.agente()));
        assertThrows(AccesoNoAutorizadoException.class,()->publicaciones.obtener(c.encargo(),f.id(),c.equipo().colega()));
        assertThrows(AccesoNoAutorizadoException.class,()->publicaciones.consultarHistoria(c.encargo(),f.id(),c.equipo().colega()));
        assertEquals(f.id(),publicaciones.obtener(c.encargo(),f.id(),c.equipo().broker()).id());
    }
    @Test void tenantAdminNoHeredaOperacionComercial(){
        var c=caso();var f=publicar(c);
        assertThrows(AccesoNoAutorizadoException.class,()->publicaciones.obtener(c.encargo(),f.id(),c.equipo().admin()));
        assertThrows(AccesoNoAutorizadoException.class,()->publicaciones.crearBorradorAnuncio(datos(c,null),c.equipo().admin(),b.sesiones.de(c.equipo().admin()),clave(),procedencia()));
        assertThrows(AccesoNoAutorizadoException.class,()->b.actuaciones.prepararPublicacion(PAUSAR,c.equipo().broker(),
                new DatosCambio(c.encargo(),f.versionEncargo(),f.id(),f.version(),null,null,null,"Motivo TEST"),b.sesiones.de(c.equipo().broker()),procedencia()));
    }
    @Test void vinculoDeEncargoNoPuedeUsarseParaOtroAnuncio(){
        var c=caso();var otro=b.negociacion.mesaEnEquipo(c.equipo(),"ALQUILER",new BigDecimal("2500"),"PEN",true);var f=publicar(c);
        assertThrows(NoEncontradoException.class,()->publicaciones.obtener(otro.encargo(),f.id(),c.agente()));
        assertThrows(NoEncontradoException.class,()->publicaciones.validarActuacion(PAUSAR,new DatosCambio(otro.encargo(),
                b.encargos.consultar(otro.encargo(),c.agente()).version(),f.id(),f.version(),null,null,null,"Motivo TEST"),c.agente()));
    }
    @Test void hechoExternoConservaFuenteYFechaDistintas(){
        var c=caso();var f=crear(c,contenido());var fecha=Instant.now().minusSeconds(3600);
        var p=confirmar(preparar(PUBLICAR,c,f,null,new EstadoExterno("Estado comunicado por el agente",fecha,"Comprobación manual TEST, no consulta de proveedor")),c);
        assertEquals(fecha.truncatedTo(java.time.temporal.ChronoUnit.MICROS),p.estadoExterno().fechaEfectiva().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertEquals("Comprobación manual TEST, no consulta de proveedor",p.estadoExterno().procedencia());
        assertNotEquals(p.fechaPublicacion().toInstant(java.time.ZoneOffset.ofHours(-5)),p.estadoExterno().fechaEfectiva());
    }
    @Test void t4DosBorradoresConMismaClaveSonUnoSolo()throws Exception{
        var c=caso();var d=datos(c,null);String k=clave();
        var resultados=competir(()->publicaciones.crearBorradorAnuncio(d,c.agente(),c.sesion(),k,procedencia()),
                ()->publicaciones.crearBorradorAnuncio(d,c.agente(),c.sesion(),k,procedencia()));
        assertTrue(resultados.stream().allMatch(Intento::exito),resultados.toString());assertEquals(resultados.get(0).ficha(),resultados.get(1).ficha());
        assertEquals(1,publicaciones.listarDeEncargo(c.encargo(),c.agente()).size());assertEquals(1,cuenta("version_publicacion",c,resultados.get(0).ficha().id()));assertEquals(0,precios(c));
    }
    @Test void t4DosEdicionesSobreMismaVersionNoSePisan()throws Exception{
        var c=caso();var f=crear(c,null);var uno=new DatosBorrador(c.encargo(),f.versionEncargo(),f.id(),f.version(),contenido());
        var dos=new DatosBorrador(c.encargo(),f.versionEncargo(),f.id(),f.version(),new DatosContenido("URBANIA",null,null,null,"Otro borrador TEST",null));
        var r=competir(()->publicaciones.editarBorradorAnuncio(uno,c.agente(),c.sesion(),clave(),procedencia()),()->publicaciones.editarBorradorAnuncio(dos,c.agente(),c.sesion(),clave(),procedencia()));
        assertEquals(1,r.stream().filter(Intento::exito).count());var fallo=r.stream().filter(x->!x.exito()).findFirst().orElseThrow().fallo();assertInstanceOf(ConflictoException.class,fallo);
        assertEquals(2,cuenta("version_publicacion",c,f.id()));assertEquals(2,publicaciones.obtener(c.encargo(),f.id(),c.agente()).version());
    }
    @Test void t4ConfirmacionRepetidaTieneUnaVersionYUnHito()throws Exception{
        var c=caso();var f=crear(c,contenido());var p=preparar(PUBLICAR,c,f,null,null);var prueba=b.humanas.revisar(p,c.agente(),c.sesion());
        var dos=competir(()->resultado(b.actuaciones.confirmar(c.agente(),p.referencia(),c.sesion(),prueba,procedencia())),
                ()->resultado(b.actuaciones.confirmar(c.agente(),p.referencia(),c.sesion(),prueba,procedencia())));
        assertTrue(dos.stream().allMatch(Intento::exito),dos.toString());assertEquals(dos.get(0).ficha(),dos.get(1).ficha());assertEquals(1,precios(c));assertEquals(2,cuenta("version_publicacion",c,f.id()));
    }
    private FichaPublicacion resultado(Resultado r){assertEquals("EXITO",r.estado(),r.motivo());return contexto.getBean(com.controllocal.service.soporte.Documentos.class).comoObjeto(
            contexto.getBean(com.controllocal.service.soporte.Documentos.class).objetoCanonico(r.resultado()),FichaPublicacion.class);}
    @Test void versionViejaRechazaUnaEdicionSinPerderHistoria(){
        var c=caso();var f=crear(c,null);var d=new DatosBorrador(c.encargo(),f.versionEncargo(),f.id(),f.version(),contenido());
        publicaciones.editarBorradorAnuncio(d,c.agente(),c.sesion(),clave(),procedencia());
        assertThrows(ConflictoException.class,()->publicaciones.editarBorradorAnuncio(d,c.agente(),c.sesion(),clave(),procedencia()));assertEquals(2,cuenta("version_publicacion",c,f.id()));
    }
    @Test void cambiarUnDatoFisicoDespuesDeRevisarExigeOtraPropuesta(){
        var c=caso();var f=crear(c,contenido());var p=preparar(PUBLICAR,c,f,null,null);var prueba=b.humanas.revisar(p,c.agente(),c.sesion());
        var fisicos=contexto.getBean(com.controllocal.service.PropiedadUniversalService.class);var antes=fisicos.consultar(c.propiedad(),c.agente());
        fisicos.editar(c.propiedad(),new com.controllocal.service.PropiedadUniversalService.ComandoEdicion(clave(),procedencia(),
                "Descripción comercial modificada después de revisión TEST",null,null,null,null,null,null,null).conVersion(antes.versionFisica()),c.agente());
        assertNotEquals(antes.versionFisica(),fisicos.consultar(c.propiedad(),c.agente()).versionFisica(),"El sabotaje debe cambiar la versión física revisada.");
        // La revalidación previa al inicio rechaza la revisión obsoleta mediante
        // conflicto; no alcanza la TX de ejecución que devuelve RECHAZADO.
        assertThrows(ConflictoException.class,()->b.actuaciones.confirmar(c.agente(),p.referencia(),c.sesion(),prueba,procedencia()));
        assertEquals("B",publicaciones.obtener(c.encargo(),f.id(),c.agente()).estado());assertEquals(0,precios(c));assertEquals(1,cuenta("version_publicacion",c,f.id()));
    }
    @Test void sqlNoPermiteEditarHistoriaNiInventarPrecioPublicado(){
        var c=caso();var f=crear(c,null);
        assertThrows(DataAccessException.class,()->b.jdbc.update("update version_publicacion set actuacion='FALSO' where organizacion_id=? and id_publicacion=?",c.agente().idOrganizacion(),f.id()));
        assertThrows(DataAccessException.class,()->b.jdbc.update("delete from version_publicacion where organizacion_id=? and id_publicacion=?",c.agente().idOrganizacion(),f.id()));
        assertThrows(DataAccessException.class,()->b.jdbc.update("update publicacion set estado='P' where organizacion_id=? and id_publicacion=?",c.agente().idOrganizacion(),f.id()));
        assertEquals("B",publicaciones.obtener(c.encargo(),f.id(),c.agente()).estado());assertEquals(1,cuenta("version_publicacion",c,f.id()));
    }
    @Test void t4FalloDespuesDeVersionPrecioYOutboxRevierteElEfectoCompleto(){
        var c=caso();var f=crear(c,contenido());var p=preparar(PUBLICAR,c,f,null,null);var prueba=b.humanas.revisar(p,c.agente(),c.sesion());
        var alcanzado=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(inv->{alcanzado.set(true);throw new IllegalStateException("Fallo de registro final inyectado TEST C6");})
                .when(recibos).save(argThat((ComandoIdempotente cmd)->cmd!=null&&PUBLICAR.equals(cmd.getTipoComando())));
        var resultado=b.actuaciones.confirmar(c.agente(),p.referencia(),c.sesion(),prueba,procedencia());
        assertTrue(alcanzado.get(),"El sabotaje debe alcanzar el recibo después de todos los efectos.");assertNotEquals("EXITO",resultado.estado());
        assertEquals("B",publicaciones.obtener(c.encargo(),f.id(),c.agente()).estado());assertEquals(1,cuenta("version_publicacion",c,f.id()));assertEquals(0,precios(c));
        assertEquals(0,b.jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and entidad_tipo='PUBLICACION' and entidad_id=? and tipo='PUBLICACION_VERSIONADA' and carga_util::jsonb->>'actuacion'=?",Integer.class,c.agente().idOrganizacion(),f.id(),PUBLICAR));
    }
    @Test void revocarLaPertenenciaDetieneLecturaYReplayDeUnResultadoAnterior(){
        var c=caso();var d=datos(c,contenido());String clave=clave();var f=publicaciones.crearBorradorAnuncio(d,c.agente(),c.sesion(),clave,procedencia());
        b.jdbc.update("update usuario_organizacion set estado='I',baja_real_en=clock_timestamp(),version=version+1 where organizacion_id=? and id_persona=?",c.agente().idOrganizacion(),c.agente().idPersona());
        assertThrows(AccesoNoAutorizadoException.class,()->publicaciones.obtener(c.encargo(),f.id(),c.agente()));
        assertThrows(AccesoNoAutorizadoException.class,()->publicaciones.crearBorradorAnuncio(d,c.agente(),c.sesion(),clave,procedencia()));
        assertEquals(1,cuenta("version_publicacion",c,f.id()));assertEquals(0,precios(c));
    }
    @Test void cierreContractualConservaVersionYActorDelCierreDeDifusion(){
        var c=b.nuevo("VENTA",ComisionC5IntegrationTest.fija(TipoHitoContractual.ESCRITURA,TipoHitoContractual.ESCRITURA,List.of()));
        var comercial=c.comercial();var f=publicar(comercial);
        var h=b.listo(c,TipoHitoContractual.ESCRITURA,hoy(),b.venta(c,new BigDecimal("100000"),"PEN",null),false);b.confirmar(h);
        var actual=publicaciones.obtener(c.encargo(),f.id(),c.agente());assertEquals("C",actual.estado());assertEquals(3,actual.version());
        assertEquals(c.broker().idRolOperativo(),b.jdbc.queryForObject("select actor_rol from version_publicacion where organizacion_id=? and id_publicacion=? and version=3",Long.class,c.agente().idOrganizacion(),f.id()));
        assertEquals("CIERRE_POR_HITO_CONFIRMADO",b.jdbc.queryForObject("select actuacion from version_publicacion where organizacion_id=? and id_publicacion=? and version=3",String.class,c.agente().idOrganizacion(),f.id()));
    }
    @Test void t4PublicarContraCierreContractualNoDejaExposicionTrasElHito()throws Exception{
        var c=b.nuevo("VENTA",ComisionC5IntegrationTest.fija(TipoHitoContractual.ESCRITURA,TipoHitoContractual.ESCRITURA,List.of()));
        var comercial=c.comercial();var f=crear(comercial,contenido());var anuncio=preparar(PUBLICAR,comercial,f,null,null);
        var pruebaAnuncio=b.humanas.revisar(anuncio,c.agente(),comercial.sesion());
        var listo=b.listo(c,TipoHitoContractual.ESCRITURA,hoy(),b.venta(c,new BigDecimal("100000"),"PEN",null),false);
        var hito=b.prepararConfirmacion(listo);var sidBroker=b.sesiones.de(c.broker());var pruebaHito=b.humanas.revisar(hito,c.broker(),sidBroker);
        var listas=new CountDownLatch(2);var salir=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var publicacion=pool.submit(()->EvidenciaC4DePrueba.enHilo(()->{listas.countDown();assertTrue(salir.await(20,TimeUnit.SECONDS));return b.actuaciones.confirmar(c.agente(),anuncio.referencia(),comercial.sesion(),pruebaAnuncio,procedencia());}));
            var cierre=pool.submit(()->EvidenciaC4DePrueba.enHilo(()->{listas.countDown();assertTrue(salir.await(20,TimeUnit.SECONDS));return b.actuaciones.confirmar(c.broker(),hito.referencia(),sidBroker,pruebaHito,procedencia());}));
            assertTrue(listas.await(20,TimeUnit.SECONDS));salir.countDown();var resultadoCierre=cierre.get(60,TimeUnit.SECONDS);var resultadoAnuncio=publicacion.get(60,TimeUnit.SECONDS);
            assertEquals("EXITO",resultadoCierre.estado(),resultadoCierre.motivo());assertTrue(Set.of("EXITO","RECHAZADO").contains(resultadoAnuncio.estado()),resultadoAnuncio.toString());
            var actual=publicaciones.obtener(c.encargo(),f.id(),c.agente());assertEquals("C",actual.estado());
            assertEquals("FINALIZADO",b.encargos.consultar(c.encargo(),c.broker()).estado());
            assertEquals("EXITO".equals(resultadoAnuncio.estado())?1:0,precios(comercial));
            var versiones=publicaciones.consultarHistoria(c.encargo(),f.id(),c.agente());assertEquals("CIERRE_POR_HITO_CONFIRMADO",versiones.getLast().actuacion());
        }finally{salir.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }
}
