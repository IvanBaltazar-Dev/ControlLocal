package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.ReportePropietarioService.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.controllocal.web.almacen.AlmacenDocumentos;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** RF078/079: Core/PG/I-O reales con identidades y datos sintéticos. Sin proveedor ni envío externo. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"controllocal.programas.habilitado=false","spring.datasource.hikari.maximum-pool-size=6"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters=false)
class ReportesVersionadosC7IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext app;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager gestor;
    @Autowired ReportePropietarioService reportes;@Autowired EncargoService encargos;
    @Autowired org.springframework.test.web.servlet.MockMvc http;
    ContextosService.SesionAccesoDTO sesionActual;
    Actor a;ActuacionesService.ContextoSesion sid;long encargo;TransactionTemplate tx;
    static Procedencia proc(){return Procedencia.deLaPantalla();}static String clave(){return UUID.randomUUID().toString();}
    @BeforeEach void iniciar(TestInfo info){EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);a=EspacioIndividualB0DePrueba.crear(jdbc,tx);
        sesionActual=SesionesDePrueba.abrir(jdbc,app.getBean(ContextosService.class),a).acceso();sid=ActuacionesService.ContextoSesion.de(sesionActual);
        var propiedad=app.getBean(PropiedadUniversalService.class).registrar(new PropiedadUniversalService.ComandoRegistro(clave(),proc(),null,"DEPARTAMENTO","VIVIENDA","Departamento TEST reportes",
            new PropiedadUniversalService.Ubicacion("Dirección ficticia TEST "+UUID.randomUUID(),"Surco",null,null,null,"301",null,null,null),List.of(),List.of(),List.of(new PropiedadUniversalService.OperacionSolicitada("VENTA",new BigDecimal("100000"),"PEN",null,null,null,null,null,null,null,List.of())),null),a).idPropiedad();
        encargo=jdbc.queryForObject("select id_captacion from captacion where organizacion_id=? and id_propiedad=?",Long.class,a.idOrganizacion(),propiedad);
    }
    @AfterEach void terminar(){org.springframework.security.core.context.SecurityContextHolder.clearContext();EvidenciaC4DePrueba.limpiar();}
    Destinatario nuevaGestora(){return new Destinatario(new ReferenciaContactoContextual(TipoContacto.CONTACTO_NUEVO,null,null,new com.controllocal.service.demanda.InteresadosDeDemanda.DatosContacto("N",null,null,"Gestora TEST sin cuota","900000000",null)),"Recibe y trata propuestas","Ella declara llevar la gestión por encargo de su hermano");}
    Destinatario existente(FichaReporte f){return new Destinatario(new ReferenciaContactoContextual(TipoContacto.PERSONA,f.destinatarioPrevisto().persona(),null,null),f.destinatarioPrevisto().funcionDeclarada(),f.destinatarioPrevisto().procedenciaDeclaracion());}
    DatosReporte datos(Referencia anterior,Destinatario contacto){var hoy=LocalDate.now(ZoneId.of("America/Lima"));return new DatosReporte(anterior,encargos.consultar(encargo,a).version(),hoy.minusDays(6),hoy,contacto,"Informar avance registrado",List.of("Revisar la presentación comercial con la gestora"),ReportePropietarioService.PLANTILLA);}
    FichaReporte preparar(){return reportes.preparar(encargo,datos(null,nuevaGestora()),a,sid,clave(),proc());}
    DatosComunicacion comunicacion(FichaReporte f){return new DatosComunicacion(f.reporte(),existente(f),LocalDate.now(ZoneId.of("America/Lima")),CanalComunicacion.WHATSAPP,null,"El agente declara que comunicó esta versión a la gestora",List.of());}
    int contar(String tabla){return jdbc.queryForObject("select count(*) from "+tabla+" where organizacion_id=?",Integer.class,a.idOrganizacion());}
    @Test void recuperaRecibosDePreparacionYComunicacionSinRegenerarNiFingirEnvio(){
        String kp=clave();var f=reportes.preparar(encargo,datos(null,nuevaGestora()),a,sid,kp,proc());
        assertEquals(f,reportes.resultadoPreparacion(kp,a).orElseThrow());
        String kc=clave();var c=reportes.registrarComunicacion(comunicacion(f),a,sid,kc,proc());
        assertEquals(c,reportes.resultadoComunicacion(kc,a).orElseThrow());assertEquals(1,contar("reporte_propietario"));assertEquals(1,contar("comunicacion_reporte"));
        assertTrue(reportes.resultadoComunicacion(kp,a).isEmpty());
        assertTrue(reportes.resultadoPreparacion(kc,a).isEmpty());
        var otra=EspacioIndividualB0DePrueba.crear(jdbc,tx);
        assertTrue(reportes.resultadoPreparacion(kp,otra).isEmpty());
        assertTrue(reportes.resultadoComunicacion(kc,otra).isEmpty());
    }
    @Test void preparaVersionPrivadaSinTitularidadNiEnvioYReintentaUnaVez(){var d=datos(null,nuevaGestora());String k=clave();var f=reportes.preparar(encargo,d,a,sid,k,proc());assertEquals(f,reportes.preparar(encargo,d,a,sid,k,proc()));
        assertEquals(0L,f.reporte().version());assertNull(f.archivo());assertEquals(1,contar("reporte_propietario"));assertEquals(0,contar("comunicacion_reporte"));
        assertEquals(0,jdbc.queryForObject("select count(*) from titularidad_propiedad where organizacion_id=?",Integer.class,a.idOrganizacion()));
        assertEquals("Recibe y trata propuestas",f.destinatarioPrevisto().funcionDeclarada());assertEquals(f,reportes.consultar(f.reporte(),a));}
    @Test void nuevaVersionConservaLaAnteriorYRechazaBaseSuperada(){var f=preparar();var d=datos(f.reporte(),existente(f));var nueva=reportes.preparar(encargo,d,a,sid,clave(),proc());assertEquals(1L,nueva.reporte().version());assertEquals(f.reporte().referencia(),nueva.reporte().referencia());assertEquals(f,reportes.consultar(f.reporte(),a));assertThrows(ConflictoException.class,()->reportes.preparar(encargo,d,a,sid,clave(),proc()));assertEquals(2,contar("reporte_propietario"));}
    @Test void descargarNoComunicaNiRepiteBinarioYSeCompruebaElPDF()throws Exception{var f=preparar();var archivo=reportes.obtenerArchivo(f.reporte(),a,sid);assertEquals(archivo,reportes.obtenerArchivo(f.reporte(),a,sid));assertEquals("application/pdf",archivo.contentType());assertEquals(1,contar("archivo_encargo"));assertEquals(0,contar("comunicacion_reporte"));
        var acceso=app.getBean(com.controllocal.service.archivo.AccesoArchivoService.class);var autorizado=acceso.resolver(archivo.referencia().toString(),a);var bin=app.getBean(AlmacenDocumentos.class).abrir(autorizado.claveProveedor()).orElseThrow();acceso.verificarContenido(autorizado,bin.contenido());acceso.revalidar(autorizado,a);
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(bin.contenido())){var texto=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);assertTrue(texto.contains("Reporte de avance"));assertTrue(texto.contains("Gestora TEST"));assertTrue(texto.contains("no acreditan envío ni recepción"));assertFalse(texto.contains("900000000"));assertFalse(texto.contains("aceptación de las partes"));}
        Path raiz=Path.of("").toAbsolutePath();while(raiz!=null&&!Files.isRegularFile(raiz.resolve("AGENTS.md")))raiz=raiz.getParent();assertNotNull(raiz);Path dir=raiz.resolve("work/c7-k34-20261104/reportes-pdf");Files.createDirectories(dir);Path destino=dir.resolve(archivo.referencia()+".pdf");Files.write(destino,bin.contenido(),StandardOpenOption.CREATE_NEW);System.out.println("PDF_C7_TEST="+destino);}
    @Test void comunicacionRealVersionadaRepetibleYCorreccionHumanaConservaAntecedente(){var f=preparar();var d=comunicacion(f);String k=clave();var c=reportes.registrarComunicacion(d,a,sid,k,proc());assertEquals(c,reportes.registrarComunicacion(d,a,sid,k,proc()));assertEquals(1,contar("comunicacion_reporte"));assertEquals("COMUNICACION_DECLARADA_SIN_ACUSE",c.naturaleza());
        var rect=new ReportePropietarioService.Rectificacion(c.referencia(),c.version(),existente(f),d.fechaReal(),CanalComunicacion.CORREO_ELECTRONICO,null,"Se precisa el canal real del mismo hecho",List.of(),"Corregir canal transcrito");
        var actuaciones=app.getBean(ActuacionesService.class);var propuesta=actuaciones.prepararRectificacionComunicacionReporte(a,rect,sid,proc());var humana=new ActuacionesHumanasC4DePrueba(actuaciones,app.getBean(Documentos.class));
        var resultado=humana.confirmar(propuesta,a,sid,Comunicacion.class);var nueva=resultado.datos();assertEquals(1,nueva.version());assertEquals(c.reporte(),nueva.reporte());assertEquals(c,reportes.consultarComunicacion(c.referencia(),0,a));
        var historia=reportes.comunicaciones(encargo,1,20,a).getFirst();assertEquals(List.of(c),historia.antecedentes());assertEquals(nueva,historia.vigente());assertThrows(ConflictoException.class,()->reportes.validarRectificacion(rect,a));assertEquals(1,contar("comunicacion_reporte"));assertEquals(2,contar("comunicacion_reporte_version"));}
    @Test void fechaFuturaNoSeConvierteEnEnvioReal(){var f=preparar();var d=comunicacion(f);assertThrows(ReglaNegocioException.class,()->reportes.registrarComunicacion(new DatosComunicacion(d.reporte(),d.destinatarioReal(),d.fechaReal().plusDays(1),d.canal(),null,d.constancia(),List.of()),a,sid,clave(),proc()));assertEquals(0,contar("comunicacion_reporte"));}
    @Test void rollbackConservaAtomicaLaVersionContactoYRecibo(){int personas=contar("persona");tx.executeWithoutResult(t->{preparar();t.setRollbackOnly();});assertEquals(0,contar("reporte_propietario"));assertEquals(personas,contar("persona"));assertEquals(0,contar("comunicacion_reporte"));}
    @Test void dosPreparacionesConcurrentesDeLaMismaBaseNoMezclanVersiones()throws Exception{var f=preparar();var d=datos(f.reporte(),existente(f));var resultados=concurrentes(()->{try{reportes.preparar(encargo,d,a,sid,clave(),proc());return "APLICADO";}catch(ConflictoException e){return "CONFLICTO";}});assertEquals(1,Collections.frequency(resultados,"APLICADO"));assertEquals(1,Collections.frequency(resultados,"CONFLICTO"));assertEquals(2,contar("reporte_propietario"));assertEquals(f,reportes.consultar(f.reporte(),a));}
    @Test void dosReintentosConcurrentesComunicanUnaSolaVez()throws Exception{var f=preparar();var d=comunicacion(f);String k=clave();var resultados=concurrentes(()->reportes.registrarComunicacion(d,a,sid,k,proc()).referencia().toString());assertEquals(1,new HashSet<>(resultados).size());assertEquals(1,contar("comunicacion_reporte"));assertEquals(1,contar("comunicacion_reporte_version"));}
    @Test void otraOrganizacionNiAdminObtienenVersionComunicacionOArchivo(){var f=preparar();var c=reportes.registrarComunicacion(comunicacion(f),a,sid,clave(),proc());var b=EspacioIndividualB0DePrueba.crear(jdbc,tx);
        assertThrows(NoEncontradoException.class,()->reportes.consultar(f.reporte(),b));assertThrows(NoEncontradoException.class,()->reportes.consultarComunicacion(c.referencia(),0,b));
        assertThrows(AccesoNoAutorizadoException.class,()->reportes.consultar(f.reporte(),new Actor(a.idOrganizacion(),a.idPersona(),a.idRolOperativo(),"TENANT_ADMIN")));}
    @Test void versionYComunicacionNoSeSobrescribenPorSQL(){var f=preparar();var c=reportes.registrarComunicacion(comunicacion(f),a,sid,clave(),proc());
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update reporte_propietario set finalidad_reporte='otra' where organizacion_id=?",a.idOrganizacion()));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("delete from comunicacion_reporte_version where organizacion_id=?",a.idOrganizacion()));assertEquals(f,reportes.consultar(f.reporte(),a));assertEquals(c,reportes.consultarComunicacion(c.referencia(),0,a));}
    @Test void unaComunicacionConDosVinculosNoSeDuplicaYReunionNoSeConvierteEnVisita(){
        var b=new FormalizacionesDePrueba(app);var c=b.negociacion.mesa("VENTA",new BigDecimal("100000"),"PEN",false);a=c.agente();sid=c.sesion();encargo=c.encargo();
        var f=preparar();var destinataria=new ParticipanteDeclarado(existente(f).contacto(),null,null,null,null);var momento=new MomentoHecho(LocalDate.now(ZoneId.of("America/Lima")),null,null);
        var vinculos=List.of(new VinculoActividad(TipoContexto.ENCARGO,encargo,null,null),new VinculoActividad(TipoContexto.OPORTUNIDAD,c.oportunidad(),null,null));
        var mensaje=new DatosInteraccion(vinculos,destinataria,destinataria,TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,null,List.of(new EventoDeclarado("consulta",EventoComunicacion.MENSAJE_RECIBIDO,momento)),ResultadoInteraccion.SIN_INTERCAMBIO,momento,"NOTA_INTERNA_NO_DIFUNDIR",List.of(),null,null,"Declaración TEST",null,null,new AtencionConsultas(new ConsultaRecibida("consulta",momento),List.of()));
        tx.execute(t->app.getBean(InteraccionService.class).registrar(mensaje,a,sid,proc()));
        var reunion=new DatosInteraccion(vinculos,destinataria,destinataria,TipoActividad.REUNION,CanalComunicacion.PRESENCIAL,null,List.of(new EventoDeclarado("reunion",EventoComunicacion.REUNION_REALIZADA,momento)),ResultadoInteraccion.INTERCAMBIO_EFECTIVO,momento,"Otra nota interna",List.of(),null,null,"Declaración TEST",null);
        tx.execute(t->app.getBean(InteraccionService.class).registrar(reunion,a,sid,proc()));
        var actual=reportes.preview(encargo,momento.fecha().minusDays(6),momento.fecha(),a);assertEquals(2,actual.hechos().comunicacionesRegistradas());assertEquals(1,actual.hechos().consultasComercialesRegistradas());assertEquals(0,actual.hechos().visitasRealizadas());
        assertFalse(app.getBean(Documentos.class).objetoCanonico(actual).contains("NOTA_INTERNA"));
        assertEquals(0,f.avance().hechos().comunicacionesRegistradas());assertEquals(f,reportes.consultar(f.reporte(),a));
        assertDoesNotThrow(()->reportes.consultar(f.reporte(),c.equipo().broker()));assertThrows(AccesoNoAutorizadoException.class,()->reportes.consultar(f.reporte(),c.equipo().colega()));
    }
    @Test void antecedenteYPreparacionNoReinicianElRelojDeComunicacion(){
        String fuentes=app.getBean(Documentos.class).objeto(Map.of("versionEsquema",1,"idEncargo",encargo,"interacciones",List.of()));
        long id=jdbc.queryForObject("insert into reporte_propietario(organizacion_id,id_captacion,id_rol_agente,fecha_reporte,consultas_reportadas,visitas_reportadas,canal_envio,poblacion_interacciones) values(?,?,?,current_date,0,0,'W',?) returning id_reporte_propietario",Long.class,a.idOrganizacion(),encargo,a.idRolOperativo(),fuentes);
        var antecedente=reportes.consultarAntecedente(id,a);assertEquals(id,antecedente.id());var repo=app.getBean(com.controllocal.persistence.repositorio.ReporteAvanceRepository.class);
        assertTrue(repo.ultimasComunicaciones(a.idOrganizacion(),List.of(encargo)).isEmpty());
        var f=preparar();assertTrue(repo.ultimasComunicaciones(a.idOrganizacion(),List.of(encargo)).isEmpty());
        assertTrue(reportes.listar(encargo,1,20,a).stream().anyMatch(h->h.antecedenteSinVersion()!=null&&h.antecedenteSinVersion()==id&&h.reporte()==null));
        var c=reportes.registrarComunicacion(comunicacion(f),a,sid,clave(),proc());assertEquals(c.fechaReal(),repo.ultimasComunicaciones(a.idOrganizacion(),List.of(encargo)).getFirst()[1]);
    }
    @Test void transporteUsaVersionExactaRetiraRutaViejaYRechazaContadoresDelCliente()throws Exception{
        var f=preparar();autenticar(sesionActual,"AGENTE");String raiz="/reportes-propietario/"+f.reporte().referencia()+"/versiones/0";
        var r=http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(raiz)).andReturn();assertEquals(200,r.getResponse().getStatus());
        String cuerpo=r.getResponse().getContentAsString();assertFalse(cuerpo.contains("fuentes_version"));assertFalse(cuerpo.contains("claveProveedor"));assertFalse(cuerpo.contains("900000000"));
        assertEquals(404,http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/captaciones/"+encargo+"/reportes-propietario/preview")).andReturn().getResponse().getStatus());
        assertEquals(400,http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/captaciones/"+encargo+"/reportes-propietario").header("Idempotency-Key",clave()).contentType("application/json").content("{\"consultasReportadas\":900,\"canalEnvio\":\"W\"}")).andReturn().getResponse().getStatus());assertEquals(1,contar("reporte_propietario"));
        autenticar(sesionActual,"TENANT_ADMIN");assertEquals(403,http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(raiz)).andReturn().getResponse().getStatus());
        var b=EspacioIndividualB0DePrueba.crear(jdbc,tx);autenticar(SesionesDePrueba.abrir(jdbc,app.getBean(ContextosService.class),b).acceso(),"AGENTE");assertEquals(404,http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(raiz)).andReturn().getResponse().getStatus());
    }
    private void autenticar(ContextosService.SesionAccesoDTO sesion,String rol){var principal=new com.controllocal.web.seguridad.SesionDeRequest(new com.controllocal.web.seguridad.TokenService("").de(sesion),sesion);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_"+rol)));}
    private List<String> concurrentes(Callable<String> tarea)throws Exception{var ex=Executors.newFixedThreadPool(2);var inicio=new CountDownLatch(1);try{Callable<String> go=()->{inicio.await();return tarea.call();};var x=ex.submit(go);var y=ex.submit(go);inicio.countDown();return List.of(x.get(45,TimeUnit.SECONDS),y.get(45,TimeUnit.SECONDS));}finally{ex.shutdownNow();}}
}
