package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.CifradoSecretos;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.ConfigurableTransactionManager;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static com.controllocal.service.captura.SuficienciaInmueble.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** C2: sesiones reales, evidencia exacta, efectos durables, rollback y carreras de dos transacciones. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters=false)
class EncargoC2IntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.archivo.AccesoArchivoService archivosFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.web.almacen.AlmacenDocumentos almacenFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.actuacion.ActuacionesService actuacionesFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.soporte.Documentos documentosFirmaTest;

    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired EncargoService ciclo;
    @Autowired com.controllocal.service.encargo.AccesoArchivoEncargo accesoArchivos;
    @Autowired com.controllocal.service.archivo.AccesoArchivoService archivosAutorizados;
    @Autowired com.controllocal.service.encargo.DocumentosEncargo binarios;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired ProspeccionService prospecciones;
    @Autowired ContextosService contextos;
    @Autowired CuentaService cuentas;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MockMvc http;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @BeforeEach void evidencia(TestInfo caso){EvidenciaC4DePrueba.iniciar(caso);}
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();EvidenciaC4DePrueba.limpiar();}
    private SesionesComercialesDePrueba sesiones;
    private synchronized ContextoSesion sesion(Actor a){if(sesiones==null)sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);return sesiones.de(a);}
    private EncargosDePrueba banco(){return new EncargosDePrueba(jdbc,ciclo,gestor, archivosFirmaTest, almacenFirmaTest, actuacionesFirmaTest, documentosFirmaTest);}

    @Test void preparacionPresentacionYDecisionSonHechosDistintosConVersionesYUnSoloPrecio() {
        var e=escenario(false);assertEquals("EN_PREPARACION",ciclo.consultar(e.encargo(),e.agente()).estado());assertEquals(0,hitos(e.encargo()));
        var p=banco().presentarExistente(e.encargo(),caso(e));
        // AC065: consulta real de la versión autorizada; preparar no sustituye probar su lectura.
        long versionAntesDeConsultar=ciclo.consultar(e.encargo(),e.agente()).version();
        int revisionesAntesDeConsultar=filas("revision_encargo",e.encargo());
        var documentoConsultado=ciclo.consultarDocumento(p.documento().id(),e.agente());
        assertEquals(p.documento().id(),documentoConsultado.id());assertEquals(p.documento().referencia(),documentoConsultado.referencia());
        assertEquals(p.documento().huella(),documentoConsultado.huella());assertEquals(p.documento().version(),documentoConsultado.version());
        assertEquals(p.documento().versionEncargo(),documentoConsultado.versionEncargo());
        assertEquals(versionAntesDeConsultar,ciclo.consultar(e.encargo(),e.agente()).version());assertEquals(revisionesAntesDeConsultar,filas("revision_encargo",e.encargo()));
        assertEquals("PENDIENTE",p.peticion().estado());assertFalse(p.ficha().vigencia().habilitado());assertEquals(0,hitos(e.encargo()));
        assertThrows(AccesoNoAutorizadoException.class,()->actuacionesFirmaTest.prepararDecisionEncargo(e.admin(),p.peticion().id(),decision(p,"APROBAR"),sesion(e.admin()),origen()));
        assertThrows(AccesoNoAutorizadoException.class,()->actuacionesFirmaTest.prepararDecisionEncargo(e.agente(),p.peticion().id(),decision(p,"APROBAR"),sesion(e.agente()),origen()));
        var confirmada=decidir(p.peticion().id(),decision(p,"APROBAR"),e.broker());var primera=confirmada.datos();
        assertEquals("APROBADA",primera.estado());assertEquals(confirmada.resultado(),actuacionesFirmaTest.confirmar(e.broker(),
                confirmada.revision().propuesta().referencia(),sesion(e.broker()),confirmada.confirmacion(),origen()));
        assertEquals(1,hitos(e.encargo()));assertTrue(ciclo.consultar(e.encargo(),e.agente()).vigencia().habilitado());
        assertEquals(2,filas("revision_encargo",e.encargo()));
    }
    @Test void independienteConfirmaSuPropiaDecisionSinCrearBroker() {
        var e=escenario(true);var r=banco().habilitarExistente(e.encargo(),caso(e));
        assertTrue(r.ficha().vigencia().habilitado());assertEquals(0,jdbc.queryForObject("select count(*) from detalle_broker where organizacion_id=?",Integer.class,e.org()));assertEquals(1,hitos(e.encargo()));
    }
    /** MockMvc con principal posterior a identidad real del fixture; no certifica login ni filtros JWT. */
    @Test void rutaDeDecisionSoloPreparaExigePruebaYNoEntregaElReciboConSesionRevocada() throws Exception {
        var e=escenario(false);var p=banco().presentarExistente(e.encargo(),caso(e));var contexto=sesion(e.broker());
        como(e.broker(),contexto);
        var propuesta=json.readTree(http.perform(post("/encargos/peticiones/{id}/decision",p.peticion().id())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(decision(p,"APROBAR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacidad").value("decidir_captacion"))
                .andReturn().getResponse().getContentAsByteArray());
        String referencia=propuesta.path("referencia").textValue();assertNotNull(referencia);
        assertEquals("PENDIENTE",ciclo.consultarPeticion(p.peticion().id(),e.broker()).estado());
        assertEquals(0,hitos(e.encargo()));assertEquals(1,filas("revision_encargo",e.encargo()));
        var revision=json.readTree(http.perform(get("/actuaciones/{ref}/revision",referencia))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        var sinPrueba=Map.of("version",revision.path("propuesta").path("version").longValue(),
                "huella",revision.path("propuesta").path("huella").textValue(),"eventoHumano","TEST_C2_SIN_PRUEBA");
        http.perform(post("/actuaciones/{ref}/confirmacion",referencia).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(sinPrueba))).andExpect(status().isConflict());
        assertEquals("PENDIENTE",ciclo.consultarPeticion(p.peticion().id(),e.broker()).estado());assertEquals(0,hitos(e.encargo()));
        var confirmacion=new com.controllocal.service.actuacion.ActuacionesService.Confirmacion(
                revision.path("propuesta").path("version").longValue(),revision.path("propuesta").path("huella").textValue(),
                revision.path("pruebaRevision").textValue(),"TEST_C2_FIRMA_"+UUID.randomUUID());
        var original=json.readTree(http.perform(post("/actuaciones/{ref}/confirmacion",referencia).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(confirmacion))).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EXITO"))
                .andExpect(jsonPath("$.resultado.estado").value("APROBADA")).andReturn().getResponse().getContentAsByteArray());
        assertEquals(p.peticion().firma().huellaManifestacion(),original.path("resultado").path("firma").path("huellaManifestacion").textValue());
        var repetida=json.readTree(http.perform(post("/actuaciones/{ref}/confirmacion",referencia).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(confirmacion))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(original.path("resultado"),repetida.path("resultado"));assertEquals(1,hitos(e.encargo()));assertEquals(2,filas("revision_encargo",e.encargo()));
        contextos.cerrar(contexto.referenciaSesion(),contexto.revisionContexto());
        http.perform(post("/actuaciones/{ref}/confirmacion",referencia).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(confirmacion))).andExpect(status().isForbidden());
        assertEquals(1,hitos(e.encargo()));assertEquals(2,filas("revision_encargo",e.encargo()));
    }
    @Test void observadaSeReenviaMismaPeticionSinBorrarRevisionAnterior() {
        var e=escenario(false);var p=banco().presentarExistente(e.encargo(),caso(e));
        var observada=decidir(p.peticion().id(),decision(p,"OBSERVAR"),e.broker()).datos();
        assertEquals("OBSERVADA",observada.estado());assertNotNull(observada.observaciones());
        var partes=partes(e,p);var actual=ciclo.consultar(e.encargo(),e.agente());
        var reenviada=ciclo.corregirYReenviar(observada.id(),observada.version(),new EncargoService.Presentacion(p.documento().id(),actual.version(),partes,p.peticion().firma().firmas(),"Sustento aclarado",null),e.agente(),sesion(e.agente()),clave(),origen());
        assertEquals(p.peticion().id(),reenviada.id());assertEquals("PENDIENTE",reenviada.estado());assertEquals(1,filas("peticion_revision_encargo",e.encargo()));assertEquals(3,filas("revision_encargo",e.encargo()));
        decidir(reenviada.id(),new EncargoService.Decision("APROBAR",reenviada.version(),ciclo.consultar(e.encargo(),e.broker()).version(),"Revisión de la misma petición",
                new EncargoService.RevisionManuscrita(reenviada.firma().revisionPresentacion(),reenviada.firma().huellaManifestacion(),true)),e.broker());
        assertEquals(4,filas("revision_encargo",e.encargo()));assertEquals(1,hitos(e.encargo()));
    }
    @Test void finFuturoNoSeAplicaYRescisionPasadaConservaFechasReales() {
        var e=escenario(false);banco().habilitarExistente(e.encargo(),caso(e));var actual=ciclo.consultar(e.encargo(),e.agente());
        var carga=ciclo.cargarArchivo(new EncargoService.CargaArchivo(e.encargo(),null,"FIN_ENCARGO","acuerdo.txt","text/plain","Acuerdo explícito TEST de rescisión".getBytes(java.nio.charset.StandardCharsets.UTF_8),actual.version()),e.agente(),sesion(e.agente()),clave(),origen());
        var evidencia=ciclo.adjuntarOReutilizar(new EncargoService.VinculoArchivo(carga.referencia(),e.encargo(),null,"FIN_ENCARGO",actual.version()),e.agente(),sesion(e.agente()),clave(),origen());
        var futuro=new EncargoService.DatosFin("RESCISION",hoy().plusDays(1),"Acuerdo aún futuro",actual.version(),List.of(evidencia.referencia()));
        assertThrows(ReglaNegocioException.class,()->ciclo.solicitarFin(e.encargo(),futuro,null,null,e.agente(),sesion(e.agente()),clave(),origen()));
        assertThrows(ReglaNegocioException.class,()->ciclo.finalizar(e.encargo(),futuro,e.broker(),sesion(e.broker()),clave(),origen()));
        assertEquals(0,filas("solicitud_finalizacion_encargo",e.encargo()));assertTrue(ciclo.consultar(e.encargo(),e.agente()).vigencia().habilitado());
        var fin=ciclo.finalizar(e.encargo(),new EncargoService.DatosFin("RESCISION",hoy().minusDays(1),"Rescisión acreditada de ayer",actual.version(),List.of(evidencia.referencia())),e.broker(),sesion(e.broker()),clave(),origen());
        assertEquals("RESCINDIDO",fin.estado());assertFalse(fin.vigencia().habilitado());assertEquals(hoy().minusDays(1),jdbc.queryForObject("select fecha_cierre from captacion where id_captacion=?",LocalDate.class,e.encargo()));
        assertEquals(hoy(),jdbc.queryForObject("select (registrada_en at time zone 'America/Lima')::date from revision_encargo where id_captacion=? and actuacion='FINALIZAR'",LocalDate.class,e.encargo()));
    }
    @Test void unaCorreccionFisicaOrdinariaRevalidaSinAdendaNiPrecioNuevo() {
        var e=escenario(false);banco().habilitarExistente(e.encargo(),caso(e));
        propiedades.editar(e.propiedad(),edicionAscensores(e,2),e.agente());
        assertTrue(ciclo.consultar(e.encargo(),e.agente()).vigencia().habilitado());assertEquals(1,hitos(e.encargo()));
    }
    @Test void dosAprobacionesConcurrentesDelMismoComandoTienenUnSoloEfecto() throws Exception {
        var e=escenario(false);var p=banco().presentarExistente(e.encargo(),caso(e));var ctx=sesion(e.broker());
        var propuesta=actuacionesFirmaTest.prepararDecisionEncargo(e.broker(),p.peticion().id(),decision(p,"APROBAR"),ctx,origen());
        var prueba=humanas().revisar(propuesta,e.broker(),ctx);
        var resultados=carrera(()->actuacionesFirmaTest.confirmar(e.broker(),propuesta.referencia(),ctx,prueba,origen()),
                ()->actuacionesFirmaTest.confirmar(e.broker(),propuesta.referencia(),ctx,prueba,origen()));
        assertTrue(resultados.stream().noneMatch(Throwable.class::isInstance),()->resultados.toString());
        var original=actuacionesFirmaTest.consultar(e.broker(),propuesta.referencia(),ctx);assertEquals("EXITO",original.estado());
        for(var recibido:resultados){
            var r=assertInstanceOf(com.controllocal.service.actuacion.ActuacionesService.Resultado.class,recibido);
            assertTrue(Set.of("EXITO","EN_VERIFICACION").contains(r.estado()));
            if("EXITO".equals(r.estado()))assertEquals(original.resultado(),r.resultado());
        }
        assertEquals(original,actuacionesFirmaTest.confirmar(e.broker(),propuesta.referencia(),ctx,prueba,origen()));
        var decision=documentosFirmaTest.comoObjeto(documentosFirmaTest.objetoCanonico(original.resultado()),EncargoService.Peticion.class);
        assertEquals("APROBADA",decision.estado());assertEquals(1,hitos(e.encargo()));assertEquals(2,filas("revision_encargo",e.encargo()));
        assertEquals(1,jdbc.queryForObject("select count(*) from comando_idempotente where organizacion_id=? and idempotency_key=?",Integer.class,e.org(),"actuacion:"+propuesta.referencia()));
        assertEquals(1,jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and tipo='PETICION_ENCARGO_DECIDIDA'",Integer.class,e.org()));
    }
    @Test void aprobacionYEdicionFisicaConcurrentesConservanObjetoYEvitanDeadlock() throws Exception {
        var e=escenario(false);var p=banco().presentarExistente(e.encargo(),caso(e));var ctx=sesion(e.broker());
        var edicion=edicionAscensores(e,2);
        var propuesta=actuacionesFirmaTest.prepararDecisionEncargo(e.broker(),p.peticion().id(),decision(p,"APROBAR"),ctx,origen());
        var prueba=humanas().revisar(propuesta,e.broker(),ctx);
        var resultados=carrera(()->actuacionesFirmaTest.confirmar(e.broker(),propuesta.referencia(),ctx,prueba,origen()),()->propiedades.editar(e.propiedad(),edicion,e.agente()));
        assertTrue(resultados.stream().noneMatch(Throwable.class::isInstance),()->resultados.toString());
        assertTrue(ciclo.consultar(e.encargo(),e.agente()).vigencia().habilitado());assertEquals(1,hitos(e.encargo()));
    }
    @Test void falloAlAnexarPresentacionReviertePeticionYAceptacionAplicada() {
        var e=escenario(false);String sufijo=UUID.randomUUID().toString().replace("-","");String funcion="fallar_pet_c2_"+sufijo;String trigger="trg_"+sufijo;
        jdbc.execute("create function "+funcion+"() returns trigger language plpgsql as $$ begin if new.organizacion_id="+e.org()+" and new.actuacion='PRESENTAR' then raise exception 'fallo inyectado presentacion C2'; end if; return new; end $$");
        jdbc.execute("create trigger "+trigger+" before insert on revision_encargo for each row execute function "+funcion+"()");
        try {
            var fallo=assertThrows(RuntimeException.class,()->banco().presentarExistente(e.encargo(),caso(e)));assertTrue(causas(fallo).contains("fallo inyectado presentacion C2"));
            assertEquals(0,filas("peticion_revision_encargo",e.encargo()));assertEquals(0,filas("revision_encargo",e.encargo()));assertEquals(0,hitos(e.encargo()));
            assertFalse(jdbc.queryForObject("select terminos_aceptados from captacion where id_captacion=?",Boolean.class,e.encargo()));
            assertNull(jdbc.queryForObject("select id_peticion_actual from captacion where id_captacion=?",Long.class,e.encargo()));
            assertEquals("EN_PREPARACION",ciclo.consultar(e.encargo(),e.agente()).estado());
        } finally {jdbc.execute("drop trigger "+trigger+" on revision_encargo");jdbc.execute("drop function "+funcion+"()");}
    }
    @Test void otroTenantNoConsultaDocumentoPeticionNiOriginalPrivado() {
        var e=escenario(false);var ajeno=escenario(false);var p=banco().presentarExistente(e.encargo(),caso(e));
        assertThrows(NoEncontradoException.class,()->ciclo.consultar(e.encargo(),ajeno.agente()));assertThrows(NoEncontradoException.class,()->ciclo.consultarDocumento(p.documento().id(),ajeno.agente()));
        assertThrows(NoEncontradoException.class,()->ciclo.consultarPeticion(p.peticion().id(),ajeno.broker()));assertThrows(NoEncontradoException.class,()->accesoArchivos.autorizar(p.documento().referencia(),ajeno.agente()));
    }
    @Test void cargaTecnicaYReusoCreanVinculosDistintosSinRecopiaNiAceptacion() {
        var e=escenario(false);new PlanesFinanciadosDePrueba(jdbc,gestor).asegurarTeam(e.org());
        long version=ciclo.consultar(e.encargo(),e.agente()).version();var carga=cargar(e,version);
        assertEquals(0,archivos(e.encargo()));assertFalse(ciclo.consultar(e.encargo(),e.agente()).vigencia().habilitado());
        assertThrows(AccesoNoAutorizadoException.class,()->accesoArchivos.autorizarCarga(carga.referencia(),e.broker()));
        assertEquals(carga.huella(),accesoArchivos.autorizarCarga(carga.referencia(),e.agente()).huella());
        var primero=ciclo.adjuntarOReutilizar(new EncargoService.VinculoArchivo(carga.referencia(),e.encargo(),null,"TITULARIDAD_ENCARGO",version),e.agente(),sesion(e.agente()),clave(),origen());
        long otra=new TransactionTemplate(gestor).execute(t->ciclo.abrir(new EncargoService.DatosApertura(e.propiedad(),e.agente().idRolOperativo(),null,OperacionInmobiliaria.VENTA,null,null,null,null,null,origen(),List.of()),e.agente())).id();
        long segundaVersion=ciclo.consultar(otra,e.agente()).version();String idempotencia=clave();
        var d=new EncargoService.VinculoArchivo(primero.referencia(),otra,null,"TITULARIDAD_ENCARGO",segundaVersion);
        var segundo=ciclo.adjuntarOReutilizar(d,e.agente(),sesion(e.agente()),idempotencia,origen());
        assertEquals(segundo,ciclo.adjuntarOReutilizar(d,e.agente(),sesion(e.agente()),idempotencia,origen()));
        assertNotEquals(primero.referencia(),segundo.referencia());assertEquals(primero.huella(),segundo.huella());
        assertEquals(accesoArchivos.autorizar(primero.referencia(),e.agente()).claveProveedor(),accesoArchivos.autorizar(segundo.referencia(),e.agente()).claveProveedor());
        assertEquals(1,archivos(e.encargo()));assertEquals(1,archivos(otra));assertEquals(1,jdbc.queryForObject("select count(*) from carga_archivo_contextual where organizacion_id=?",Integer.class,e.org()));
        assertFalse(jdbc.queryForObject("select terminos_aceptados from captacion where id_captacion=?",Boolean.class,otra));assertEquals(0,filas("peticion_revision_encargo",otra));
    }
    @Test void reusoExigeOrigenAutorizadoYVersionDeDestinoSinAlterarOriginal() {
        var e=escenario(false);var otro=escenario(false);long version=ciclo.consultar(e.encargo(),e.agente()).version();
        var planes=new PlanesFinanciadosDePrueba(jdbc,gestor);planes.asegurarTeam(e.org());planes.asegurarTeam(otro.org());
        var carga=cargar(e,version);var ajena=cargar(otro,ciclo.consultar(otro.encargo(),otro.agente()).version());
        assertThrows(NoEncontradoException.class,()->ciclo.adjuntarOReutilizar(new EncargoService.VinculoArchivo(ajena.referencia(),e.encargo(),null,"TITULARIDAD_ENCARGO",version),e.agente(),sesion(e.agente()),clave(),origen()));
        ciclo.editar(e.encargo(),new EncargoService.Edicion(version,hoy(),hoy().plusMonths(6),false,null,null,"Vigencia declarada TEST",null,null),e.agente(),sesion(e.agente()),clave(),origen());
        assertThrows(ConflictoException.class,()->ciclo.adjuntarOReutilizar(new EncargoService.VinculoArchivo(carga.referencia(),e.encargo(),null,"TITULARIDAD_ENCARGO",version),e.agente(),sesion(e.agente()),clave(),origen()));
        assertEquals(0,archivos(e.encargo()));assertEquals(carga.huella(),accesoArchivos.autorizarCarga(carga.referencia(),e.agente()).huella());
    }
    @Test void laBaseImpideOrigenDeOtroTenantAunSaltandoCore() {
        var destino=escenario(false);var origen=escenario(false);new PlanesFinanciadosDePrueba(jdbc,gestor).asegurarTeam(origen.org());
        var carga=cargar(origen,ciclo.consultar(origen.encargo(),origen.agente()).version());
        String claveProveedor=jdbc.queryForObject("select clave_proveedor from carga_archivo_contextual where archivo_ref=?",String.class,carga.referencia());
        var error=assertThrows(RuntimeException.class,()->new TransactionTemplate(gestor).execute(t->{
            jdbc.update("""
               insert into archivo_encargo(organizacion_id,archivo_ref,id_captacion,id_rol_autor,finalidad,clave_proveedor,nombre,content_type,huella,registrado_en,
                   origen_referencia,origen_tipo,origen_huella_vinculo,version_destino)
               values(?,?,?,?,'TITULARIDAD_ENCARGO',?,'insercion-hostil.txt','text/plain',?,clock_timestamp(),?,'CARGA_TEMPORAL',?,0)
               """,destino.org(),UUID.randomUUID(),destino.encargo(),destino.agente().idRolOperativo(),claveProveedor,carga.huella(),carga.referencia(),"a".repeat(64));return null;
        }));
        assertTrue(causas(error).contains("origen documental no pertenece"),()->causas(error));assertEquals(0,archivos(destino.encargo()));
        assertEquals(carga.huella(),accesoArchivos.autorizarCarga(carga.referencia(),origen.agente()).huella());
    }

    @Test void condicionesDinamicasSeEditanRetiranYReproducenEnElEncargoSinMutarLoFisico(){
        var e=escenario(false);var actual=ciclo.consultar(e.encargo(),e.agente());
        long versionFisica=propiedades.consultar(e.propiedad(),e.agente()).versionFisica();
        long material=jdbc.queryForObject("select version_contenido from captacion where id_captacion=?",Long.class,e.encargo());
        var adicional=new PropiedadUniversalService.ValorAtributo("precio_estacionamiento_adicional","350.00","PEN",null,"DECLARADO",null,hoy(),"Pacto TEST de cochera adicional",null);
        var entrada=new EncargoService.Edicion(actual.version(),null,null,null,null,null,"Condiciones aportadas por el mandante TEST",List.of(valor("garantia_meses","2",null),adicional),List.of());String clave=clave();
        var editado=ciclo.editar(e.encargo(),entrada,e.agente(),sesion(e.agente()),clave,origen());
        assertEquals(editado,ciclo.editar(e.encargo(),entrada,e.agente(),sesion(e.agente()),clave,origen()));
        assertEquals("PEN 350",editado.condicionesDinamicas().stream().filter(a->a.clave().equals("precio_estacionamiento_adicional")).findFirst().orElseThrow().valor());
        assertEquals(material+1,jdbc.queryForObject("select version_contenido from captacion where id_captacion=?",Long.class,e.encargo()));
        assertEquals(versionFisica,propiedades.consultar(e.propiedad(),e.agente()).versionFisica());
        assertEquals(0,jdbc.queryForObject("select count(*) from atributo_propiedad where id_propiedad=? and clave in ('precio_estacionamiento_adicional','garantia_meses')",Integer.class,e.propiedad()));
        assertEquals(2,jdbc.queryForObject("select count(*) from atributo_encargo where id_captacion=?",Integer.class,e.encargo()));
        assertThrows(ReglaNegocioException.class,()->propiedades.editar(e.propiedad(),new PropiedadUniversalService.ComandoEdicion(clave(),origen(),null,null,null,null,null,null,List.of(new PropiedadUniversalService.CondicionesDeEncargo(e.encargo(),List.of(valor("garantia_meses","3",null)),null))).conVersion(versionFisica),e.agente()));
        var retirado=ciclo.editar(e.encargo(),new EncargoService.Edicion(editado.version(),null,null,null,null,null,"Retirar garantía del pacto TEST",List.of(),List.of("garantia_meses")),e.agente(),sesion(e.agente()),clave(),origen());
        assertFalse(retirado.condicionesDinamicas().stream().anyMatch(a->a.clave().equals("garantia_meses")));
        assertEquals(3,jdbc.queryForObject("select count(*) from rastro_valor_gobernado where organizacion_id=? and sujeto='ENCARGO' and id_agregado=?",Integer.class,e.org(),e.encargo()));
        assertEquals(0,hitos(e.encargo()));
    }
    @Test void lasCondicionesIncluidasEnLaRevisionAceptadaNoSeSobrescribenPorEdicionOrdinaria() throws Exception {
        var e=escenario(false);var actual=ciclo.consultar(e.encargo(),e.agente());
        var adicional=new PropiedadUniversalService.ValorAtributo("precio_estacionamiento_adicional","350.00","PEN",null,"DECLARADO",null,hoy(),"Precio TEST por cochera adicional",null);
        ciclo.editar(e.encargo(),new EncargoService.Edicion(actual.version(),null,null,null,null,null,"Garantía TEST antes de aceptación",List.of(valor("garantia_meses","2",null),adicional),List.of()),e.agente(),sesion(e.agente()),clave(),origen());
        var habilitado=banco().habilitarExistente(e.encargo(),caso(e));assertTrue(habilitado.ficha().vigencia().habilitado());
        assertTrue(jdbc.queryForObject("select contenido from documento_encargo where id_documento_encargo=?",String.class,habilitado.documento().id()).contains("garantia_meses"));
        var autorizada=archivosAutorizados.resolver(habilitado.documento().referencia().toString(),e.agente());
        var original=binarios.abrir(autorizada.claveProveedor()).orElseThrow();
        archivosAutorizados.verificarContenido(autorizada,original.contenido());archivosAutorizados.revalidar(autorizada,e.agente());
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(original.contenido())){
            String texto=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertTrue(texto.contains("Uso ofrecido"));assertTrue(texto.contains("m²"));assertTrue(texto.contains("(meses)"));
            assertTrue(texto.contains("Precio por cochera adicional"));assertTrue(texto.contains("PEN 350"));assertTrue(texto.contains("2500 PEN / mes"));
            assertFalse(texto.contains("precio_estacionamiento_adicional"));assertFalse(texto.contains("privado/"));
            var salida=java.nio.file.Path.of("target","encargo-c2-pdf-qa");java.nio.file.Files.createDirectories(salida);
            java.nio.file.Files.write(salida.resolve("encargo-core-real.pdf"),original.contenido());
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0,110),"png",salida.resolve("pagina-1.png").toFile());
            if(pdf.getNumberOfPages()>1)javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(pdf.getNumberOfPages()-1,110),"png",salida.resolve("pagina-final.png").toFile());
        }
        assertThrows(ReglaNegocioException.class,()->ciclo.editar(e.encargo(),new EncargoService.Edicion(habilitado.ficha().version(),null,null,null,null,null,"Intento sobre término aceptado",List.of(valor("garantia_meses","4",null)),List.of()),e.agente(),sesion(e.agente()),clave(),origen()));
        assertEquals(0,new BigDecimal("2").compareTo(jdbc.queryForObject("select valor_numero from atributo_encargo where id_captacion=? and clave='garantia_meses'",BigDecimal.class,e.encargo())));assertEquals(1,hitos(e.encargo()));
    }
    @Test void dosCambiosDinamicosConLaMismaVersionNoSePisanEntreTransacciones() throws Exception {
        var e=escenario(false);var actual=ciclo.consultar(e.encargo(),e.agente());var ctx=sesion(e.agente());
        var primero=new EncargoService.Edicion(actual.version(),null,null,null,null,null,"Garantía dos TEST",List.of(valor("garantia_meses","2",null)),List.of());
        var segundo=new EncargoService.Edicion(actual.version(),null,null,null,null,null,"Garantía tres TEST",List.of(valor("garantia_meses","3",null)),List.of());
        var resultados=carrera(()->ciclo.editar(e.encargo(),primero,e.agente(),ctx,clave(),origen()),()->ciclo.editar(e.encargo(),segundo,e.agente(),ctx,clave(),origen()));
        assertEquals(1,resultados.stream().filter(EncargoService.Ficha.class::isInstance).count(),()->resultados.toString());assertEquals(1,resultados.stream().filter(ConflictoException.class::isInstance).count(),()->resultados.toString());
        assertEquals(1,jdbc.queryForObject("select count(*) from rastro_valor_gobernado where organizacion_id=? and sujeto='ENCARGO' and id_agregado=?",Integer.class,e.org(),e.encargo()));
    }
    @Test void solicitudDeFinSeConsultaObservaYReenviaConMismaIdentidadHastaAplicarElFin(){
        var e=escenario(false);banco().habilitarExistente(e.encargo(),caso(e));var datos=finAcreditado(e);
        var pendiente=ciclo.solicitarFin(e.encargo(),datos,null,null,e.agente(),sesion(e.agente()),clave(),origen());
        assertEquals(pendiente.id(),ciclo.consultarSolicitudFin(pendiente.id(),e.broker()).id());assertEquals("PENDIENTE",pendiente.estado());
        var observada=ciclo.decidirSolicitudFin(pendiente.id(),new EncargoService.Decision("OBSERVAR",pendiente.version(),ciclo.consultar(e.encargo(),e.broker()).version(),"Aclarar el acuerdo TEST",null),e.broker(),sesion(e.broker()),clave(),origen());
        assertEquals("OBSERVADA",observada.estado());assertEquals("APROBADO",ciclo.consultar(e.encargo(),e.agente()).estado());
        var reenviada=ciclo.solicitarFin(e.encargo(),datos,observada.id(),observada.version(),e.agente(),sesion(e.agente()),clave(),origen());
        assertEquals(pendiente.id(),reenviada.id());assertEquals("PENDIENTE",reenviada.estado());assertEquals(1,filas("solicitud_finalizacion_encargo",e.encargo()));
        var decision=new EncargoService.Decision("APROBAR",reenviada.version(),ciclo.consultar(e.encargo(),e.broker()).version(),"Acuerdo TEST comprobado",null);String clave=clave();
        var aprobada=ciclo.decidirSolicitudFin(reenviada.id(),decision,e.broker(),sesion(e.broker()),clave,origen());
        assertEquals(aprobada,ciclo.decidirSolicitudFin(reenviada.id(),decision,e.broker(),sesion(e.broker()),clave,origen()));
        assertEquals("APROBADA",ciclo.consultarSolicitudFin(aprobada.id(),e.agente()).estado());assertEquals("FINALIZADO",ciclo.consultar(e.encargo(),e.agente()).estado());
        assertEquals(1,jdbc.queryForObject("select count(*) from revision_encargo where id_captacion=? and actuacion='FINALIZAR'",Integer.class,e.encargo()));
    }
    @Test void cancelarSolicitudConservaElEncargoYNoPermiteDecidirEsaSolicitudDespues(){
        var e=escenario(false);banco().habilitarExistente(e.encargo(),caso(e));var pendiente=ciclo.solicitarFin(e.encargo(),finAcreditado(e),null,null,e.agente(),sesion(e.agente()),clave(),origen());
        var cancelada=ciclo.cancelarSolicitudFin(pendiente.id(),pendiente.version(),"El mandante TEST retira su solicitud",e.agente(),sesion(e.agente()),clave(),origen());assertEquals("CANCELADA",cancelada.estado());
        var actual=ciclo.consultar(e.encargo(),e.broker());assertEquals("APROBADO",actual.estado());assertTrue(actual.vigencia().habilitado());
        assertThrows(ReglaNegocioException.class,()->ciclo.decidirSolicitudFin(cancelada.id(),new EncargoService.Decision("APROBAR",cancelada.version(),actual.version(),"Intento posterior TEST",null),e.broker(),sesion(e.broker()),clave(),origen()));
        assertEquals("CANCELADA",ciclo.consultarSolicitudFin(cancelada.id(),e.agente()).estado());assertEquals(0,jdbc.queryForObject("select count(*) from revision_encargo where id_captacion=? and actuacion='FINALIZAR'",Integer.class,e.encargo()));
    }
    private EncargoService.DatosFin finAcreditado(Escenario e){
        var actual=ciclo.consultar(e.encargo(),e.agente());
        var carga=ciclo.cargarArchivo(new EncargoService.CargaArchivo(e.encargo(),null,"FIN_ENCARGO","acuerdo-fin-test.txt","text/plain","Acuerdo expreso de finalización del mandante TEST".getBytes(java.nio.charset.StandardCharsets.UTF_8),actual.version()),e.agente(),sesion(e.agente()),clave(),origen());
        var prueba=ciclo.adjuntarOReutilizar(new EncargoService.VinculoArchivo(carga.referencia(),e.encargo(),null,"FIN_ENCARGO",actual.version()),e.agente(),sesion(e.agente()),clave(),origen());
        return new EncargoService.DatosFin("FINALIZACION",hoy(),"Finalización acreditada TEST",ciclo.consultar(e.encargo(),e.agente()).version(),List.of(prueba.referencia()));
    }
    @Test void composicionHistoricaYReplayReautorizanSusUnidadesYLaPropuestaNoHeredaUnaBasePrivada() throws Exception {
        var e=escenario(false);
        new PlanesFinanciadosDePrueba(jdbc,gestor).asegurarTeam(e.org());
        long unidad=propiedades.registrar(new PropiedadUniversalService.ComandoRegistro(clave(),origen(),null,"DEPARTAMENTO","VIVIENDA","Unidad adicional TEST sin contexto comercial",
                new PropiedadUniversalService.Ubicacion("Av. Unidad TEST "+UUID.randomUUID(),"Surco",null,null,null,"402",null,null,null),List.of(),List.of(),List.of(),null),e.agente()).idPropiedad();
        var actual=ciclo.consultar(e.encargo(),e.agente());
        ciclo.editar(e.encargo(),new EncargoService.Edicion(actual.version(),hoy(),hoy().plusMonths(6),false,null,
                EncargosDePrueba.alquiler(new BigDecimal("7000"),"PEN",BigDecimal.ONE,"Mandante TEST","Agencia TEST"),"Términos explícitos del paquete TEST",List.of(),List.of()),e.agente(),sesion(e.agente()),clave(),origen());
        var unidades=List.of(new EncargoService.Unidad(e.propiedad(),new BigDecimal("2500")," pen ","Desglose TEST unidad principal"),
                new EncargoService.Unidad(unidad,new BigDecimal("4500")," pen ","Desglose TEST unidad adicional"));
        var primeraEntrada=new EncargoService.DatosComposicion(unidades,new BigDecimal("7000")," pen ","Paquete TEST completo",ciclo.consultar(e.encargo(),e.agente()).version());String primeraClave=clave();
        var primera=ciclo.declararComposicion(e.encargo(),primeraEntrada,e.agente(),sesion(e.agente()),primeraClave,origen());
        assertEquals("PEN",primera.moneda());assertTrue(primera.unidades().stream().allMatch(u->"PEN".equals(u.moneda())));
        Actor otro=new TransactionTemplate(gestor).execute(t->{
            long persona=persona(e.org(),"Segundo agente TEST"),rol=rol(e.org(),persona,"AGENTE");
            jdbc.update("insert into detalle_agente(organizacion_id,id_persona_rol,codigo_agente,fecha_ingreso,estado_operativo) values(?,?,?,?,'D')",e.org(),rol,"AGT-"+rol,hoy());pertenencia(e.org(),persona,"AGENTE",false);
            jdbc.update("insert into supervision_agente(organizacion_id,id_rol_broker,id_rol_agente,fecha_asignacion,motivo) values(?,?,?,?,?)",e.org(),e.broker().idRolOperativo(),rol,hoy(),"Supervisión explícita TEST");return new Actor(e.org(),persona,rol,Actor.AGENTE);
        });
        prospecciones.registrar(new ProspeccionService.DatosProspeccion(unidad,"Prospección explícita del segundo agente TEST", null),otro);
        assertThrows(NoEncontradoException.class,()->propiedades.consultar(unidad,e.agente()));assertNotNull(propiedades.consultar(e.propiedad(),e.agente()));
        var propuesta=ciclo.prepararDocumento(new EncargoService.Preparacion(e.encargo(),ciclo.consultar(e.encargo(),e.agente()).version(),e.propiedad(),null,List.of(),OperacionInmobiliaria.ALQUILER,
                List.of(new EncargoService.Unidad(e.propiedad(),null,null,null)),null,null,null,null,null,List.of()),e.agente(),sesion(e.agente()),clave(),origen());
        var documento=new com.fasterxml.jackson.databind.ObjectMapper().readTree(jdbc.queryForObject("select contenido from documento_encargo where id_documento_encargo=?",String.class,propuesta.id()));
        var base=documento.path("metadataOrigen");assertEquals("PERMISO_INSUFICIENTE",base.path("permisoBase").asText());assertEquals(primera.id(),base.path("composicion").asLong());
        assertFalse(base.has("unidades"));assertFalse(base.has("totalDeclarado"));assertFalse(base.has("condiciones"));
        assertTrue(documento.path("totalComposicion").isMissingNode()||documento.path("totalComposicion").isNull());assertTrue(documento.path("condiciones").path("precioPedido").isMissingNode()||documento.path("condiciones").path("precioPedido").isNull());
        assertEquals(1,documento.path("unidades").size());assertEquals(e.propiedad(),documento.path("unidades").get(0).path("idPropiedad").asLong());
        var lectura=archivosAutorizados.resolver(propuesta.referencia().toString(),e.agente());var pdfPrivado=binarios.abrir(lectura.claveProveedor()).orElseThrow();
        archivosAutorizados.verificarContenido(lectura,pdfPrivado.contenido());archivosAutorizados.revalidar(lectura,e.agente());
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(pdfPrivado.contenido())){String texto=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertFalse(texto.contains("Av. Unidad TEST"));assertFalse(texto.matches("(?s).*\\b(?:7000|4500)(?:\\.0+)?\\s+PEN\\b.*"));}
        var nuevaEntrada=new EncargoService.DatosComposicion(List.of(new EncargoService.Unidad(e.propiedad(),new BigDecimal("2500"),"PEN","Desglose TEST nuevo paquete")),new BigDecimal("2500"),"PEN","Paquete TEST reducido",ciclo.consultar(e.encargo(),e.agente()).version());String nuevaClave=clave();
        var nueva=ciclo.declararComposicion(e.encargo(),nuevaEntrada,e.agente(),sesion(e.agente()),nuevaClave,origen());
        var consultada=ciclo.consultarComposicion(e.encargo(),null,e.agente());
        assertEquals(nueva.id(),consultada.id());assertEquals(nueva.version(),consultada.version());assertEquals(nueva.unidades(),consultada.unidades());assertEquals("PEN",consultada.moneda());
        assertEquals(0,nueva.totalDeclarado().compareTo(consultada.totalDeclarado()));
        assertEquals(consultada,ciclo.consultarComposicion(e.encargo(),nueva.version(),e.agente()));
        assertThrows(AccesoNoAutorizadoException.class,()->ciclo.consultarComposicion(e.encargo(),primera.version(),e.agente()));
        long version=ciclo.consultar(e.encargo(),e.agente()).version();long eventos=jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and entidad_tipo='CAPTACION' and entidad_id=?",Long.class,e.org(),e.encargo());
        assertThrows(AccesoNoAutorizadoException.class,()->ciclo.declararComposicion(e.encargo(),primeraEntrada,e.agente(),sesion(e.agente()),primeraClave,origen()));
        assertEquals(nueva,ciclo.declararComposicion(e.encargo(),nuevaEntrada,e.agente(),sesion(e.agente()),nuevaClave,origen()));
        assertEquals(version,ciclo.consultar(e.encargo(),e.agente()).version());assertEquals(eventos,jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and entidad_tipo='CAPTACION' and entidad_id=?",Long.class,e.org(),e.encargo()));
        assertEquals(2,jdbc.queryForObject("select count(*) from composicion_encargo where id_captacion=?",Integer.class,e.encargo()));
        RuntimeException duplicada=assertThrows(RuntimeException.class,()->jdbc.update("""
            insert into composicion_encargo(organizacion_id,id_captacion,version_contenido,unidades,total_declarado,moneda,procedencia,registrada_en,id_rol_actor)
            select organizacion_id,id_captacion,version_contenido,unidades,total_declarado,moneda,procedencia,registrada_en,id_rol_actor
              from composicion_encargo where id_composicion_encargo=?
            """,nueva.id()));
        String sqlState=null;for(Throwable causa=duplicada;causa!=null;causa=causa.getCause())if(causa instanceof java.sql.SQLException sql)sqlState=sql.getSQLState();
        assertEquals("23505",sqlState);assertTrue(causas(duplicada).contains("uq_composicion_encargo_version_material"));
        assertEquals(2,jdbc.queryForObject("select count(*) from composicion_encargo where id_captacion=?",Integer.class,e.encargo()));
    }
    @Test void dosAperturasConcurrentesDeLaMismaOperacionCreanUnSoloBorrador() throws Exception {
        var e=escenario(false);var ctx=sesion(e.agente());
        var primera=new EncargoService.DatosApertura(e.propiedad(),e.agente().idRolOperativo(),"T4-A-"+UUID.randomUUID().toString().substring(0,8),OperacionInmobiliaria.VENTA,null,null,null,"Borrador TEST concurrente A",null,origen(),List.of());
        var segunda=new EncargoService.DatosApertura(e.propiedad(),e.agente().idRolOperativo(),"T4-B-"+UUID.randomUUID().toString().substring(0,8),OperacionInmobiliaria.VENTA,null,null,null,"Borrador TEST concurrente B",null,origen(),List.of());
        var resultados=carrera(()->abrirEnTransaccion(primera,e.agente(),ctx),()->abrirEnTransaccion(segunda,e.agente(),ctx));
        assertEquals(1,resultados.stream().filter(EncargoService.Apertura.class::isInstance).count(),()->resultados.toString());
        assertEquals(1,resultados.stream().filter(ReglaNegocioException.class::isInstance).count(),()->resultados.toString());
        var abierta=resultados.stream().filter(EncargoService.Apertura.class::isInstance).map(EncargoService.Apertura.class::cast).findFirst().orElseThrow();
        assertEquals("EN_PREPARACION",abierta.estado());
        assertEquals(1,jdbc.queryForObject("select count(*) from captacion where organizacion_id=? and id_propiedad=? and motivo_operacion='V' and estado in ('P','A')",Integer.class,e.org(),e.propiedad()));
        assertEquals(1,jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and entidad_tipo='CAPTACION' and entidad_id=? and tipo='ENCARGO_ABIERTO'",Integer.class,e.org(),abierta.id()));
        assertEquals(0,hitos(abierta.id()));assertEquals(0,filas("revision_encargo",abierta.id()));
        assertNull(jdbc.queryForObject("select disponibilidad_comercial from propiedad where id_propiedad=?",String.class,e.propiedad()));
    }
    private EncargoService.Apertura abrirEnTransaccion(EncargoService.DatosApertura datos,Actor actor,ContextoSesion ctx){
        return new TransactionTemplate(gestor).execute(t->{
            assertEquals(ctx,ContextoSesion.de(contextos.exigirContexto(ctx.referenciaSesion(),ctx.revisionContexto(),actor)));
            return ciclo.abrir(datos,actor);
        });
    }
    private record Equipo(long org,Actor agente,Actor broker,Actor admin,long propietario) { }
    private record Escenario(long org,Actor agente,Actor broker,Actor admin,long propiedad,long encargo) { }
    private Escenario escenario(boolean independiente) {
        Equipo e=new TransactionTemplate(gestor).execute(t->{
            String ref=UUID.randomUUID().toString().replace("-","");long org=jdbc.queryForObject("insert into organizacion(codigo,nombre,modalidad,estado) values(?,?,?,'I') returning id_organizacion",Long.class,"C2-"+ref.substring(0,17),"Agencia TEST C2",independiente?"INDIVIDUAL":"EMPRESARIAL");
            Actor broker=null,admin=null;
            if(!independiente) {
                long persona=persona(org,"Broker TEST"),rol=rol(org,persona,"BROKER"),gob=rol(org,persona,"ADMIN");
                jdbc.update("insert into detalle_broker(organizacion_id,id_persona_rol,codigo_broker,fecha_designacion) values(?,?,?,?)",org,rol,"BRK-"+rol,hoy());
                pertenencia(org,persona,"BROKER",true);broker=new Actor(org,persona,rol,Actor.BROKER);admin=new Actor(org,persona,gob,Actor.TENANT_ADMIN);
            }
            long persona=persona(org,"Agente TEST"),agente=rol(org,persona,"AGENTE");
            jdbc.update("insert into detalle_agente(organizacion_id,id_persona_rol,codigo_agente,fecha_ingreso,estado_operativo) values(?,?,?,?,'D')",org,agente,"AGT-"+agente,hoy());pertenencia(org,persona,"AGENTE",false);
            if(broker!=null)jdbc.update("insert into supervision_agente(organizacion_id,id_rol_broker,id_rol_agente,fecha_asignacion,motivo) values(?,?,?,?,?)",org,broker.idRolOperativo(),agente,hoy(),"Supervisión TEST explícita C2");
            long titular=persona(org,"María Titular TEST");long propietario=rol(org,titular,"PROPIETARIO");
            jdbc.update("update organizacion set estado='A' where id_organizacion=?",org);
            Actor a=new Actor(org,persona,agente,Actor.AGENTE);return new Equipo(org,a,independiente?a:broker,admin,propietario);
        });
        // El enrolamiento personal exigido por ADMIN revoca los SID previos de esa cuenta.
        // Broker y gobierno son atribuciones de la misma persona del fixture; se abre después el SID comercial.
        sesion(e.agente());if(e.admin()!=null)sesion(e.admin());sesion(e.broker());
        var registrado=propiedades.registrar(new PropiedadUniversalService.ComandoRegistro(clave(),origen(),null,"DEPARTAMENTO","VIVIENDA","Inmueble de prueba C2",
                new PropiedadUniversalService.Ubicacion("Av. Perú "+UUID.randomUUID().toString().substring(0,8),"Surco",null,null,null,"302",null,null,null),
                List.of(new PropiedadUniversalService.Titular(e.propietario(),new BigDecimal("100"),true)),List.of(),List.of(),null),e.agente());
        long id=registrado.idPropiedad();var abierto=new TransactionTemplate(gestor).execute(t->ciclo.abrir(new EncargoService.DatosApertura(id,e.agente().idRolOperativo(),null,OperacionInmobiliaria.ALQUILER,null,null,null,null,null,origen(),List.of()),e.agente()));
        var contexto=new Contexto("D","V","Departamento302","ALQUILER",Map.of(UNIDAD_DE_EDIFICIO,Aplicabilidad.APLICA,MANTENIMIENTO,Aplicabilidad.NO_APLICA),Set.of());
        var atributos=new ArrayList<>(List.of(valor("metraje_total","90",AREA_TOTAL),valor("dormitorios","2",null),valor("banos","2",null),valor("piso","3",null),valor("ascensores","1",null)));
        atributos.add(new PropiedadUniversalService.ValorAtributo("contexto."+MANTENIMIENTO,null,null,null,"DECLARADO",null,hoy(),"Declaración TEST de mantenimiento no aplicable",new PropiedadUniversalService.MetadatosDeCaptura(EstadoDato.NO_APLICABLE,null,null,null,false)));
        propiedades.editar(id,new PropiedadUniversalService.ComandoEdicion(clave(),origen(),null,null,null,atributos,null,null,null,contexto).conVersion(propiedades.consultar(id,e.agente()).versionFisica()),e.agente());
        return new Escenario(e.org(),e.agente(),e.broker(),e.admin(),id,abierto.id());
    }
    private long persona(long org,String nombre){return jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social,tipo_persona,tipo_documento,numero_documento) values(?,?,'N','D',?) returning id_persona",Long.class,org,nombre,String.format("%08d",java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000)));}
    private long rol(long org,long persona,String tipo){return jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,?,?) returning id_persona_rol",Long.class,org,persona,tipo,hoy());}
    private void pertenencia(long org,long persona,String rol,boolean administrar){String ref="c2-"+UUID.randomUUID();long cuenta=jdbc.queryForObject("insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) values(?,?,clock_timestamp(),?) returning id_cuenta_acceso",Long.class,ref,ref+"@example.test","fixture-identidad-previa-no-login");jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,?,?,'A')",org,cuenta,persona,rol,administrar);}
    private EncargosDePrueba.Caso caso(Escenario e){return new EncargosDePrueba.Caso(e.propiedad(),OperacionInmobiliaria.ALQUILER,hoy(),hoy().plusMonths(6),false,EncargosDePrueba.alquiler(new BigDecimal("2500"),"PEN",BigDecimal.ONE,"Propietario","Agencia"),new EncargosDePrueba.Actuante(e.agente(),sesion(e.agente())),new EncargosDePrueba.Actuante(e.broker(),sesion(e.broker())));}
    private List<EncargoService.Parte> partes(Escenario e,EncargosDePrueba.Resultado r){UUID evidencia=jdbc.queryForObject("select archivo_ref from archivo_encargo where id_captacion=? and finalidad='TITULARIDAD_ENCARGO' order by id_archivo_encargo limit 1",UUID.class,e.encargo());return jdbc.query("select t.id_titularidad,r.id_persona,t.cuota,p.nombres_o_razon_social from titularidad_propiedad t join persona_rol r on r.id_persona_rol=t.id_rol_propietario join persona p on p.id_persona=r.id_persona where t.id_propiedad=? and t.vigente_hasta is null",(x,n)->new EncargoService.Parte(x.getLong(1),x.getLong(2),x.getBigDecimal(3),evidencia,null,null,null,null,null,x.getString(4),null),e.propiedad());}
    private static EncargoService.Decision decision(EncargosDePrueba.Resultado r,String accion){return new EncargoService.Decision(accion,r.peticion().version(),r.ficha().version(),"Decisión TEST explícita",
            "APROBAR".equals(accion)?new EncargoService.RevisionManuscrita(r.peticion().firma().revisionPresentacion(),r.peticion().firma().huellaManifestacion(),true):null);}
    private ActuacionesHumanasC4DePrueba humanas(){return new ActuacionesHumanasC4DePrueba(actuacionesFirmaTest,documentosFirmaTest);}
    private void como(Actor actor,ContextoSesion sesion){
        var acceso=contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor);
        var principal=new com.controllocal.web.seguridad.SesionDeRequest(new com.controllocal.web.seguridad.TokenService("").de(acceso),acceso);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,
                AuthorityUtils.createAuthorityList("ROLE_"+actor.rolEfectivo())));
    }
    private ActuacionesHumanasC4DePrueba.Confirmada<EncargoService.Peticion> decidir(long peticion,EncargoService.Decision datos,Actor actor){
        var contexto=sesion(actor);var propuesta=actuacionesFirmaTest.prepararDecisionEncargo(actor,peticion,datos,contexto,origen());
        return humanas().confirmar(propuesta,actor,contexto,EncargoService.Peticion.class);
    }
    private PropiedadUniversalService.ComandoEdicion edicionAscensores(Escenario e,int n){return new PropiedadUniversalService.ComandoEdicion(clave(),origen(),null,null,null,List.of(valor("ascensores",Integer.toString(n),null)),null,null).conVersion(propiedades.consultar(e.propiedad(),e.agente()).versionFisica());}
    private static PropiedadUniversalService.ValorAtributo valor(String clave,String valor,String concepto){return new PropiedadUniversalService.ValorAtributo(clave,valor,null,null,"DECLARADO",null,hoy(),"Fuente TEST explícita C2",new PropiedadUniversalService.MetadatosDeCaptura(EstadoDato.CONOCIDO,concepto,null,null,false));}
    private int filas(String tabla,long id){if(!Set.of("revision_encargo","peticion_revision_encargo","solicitud_finalizacion_encargo").contains(tabla))throw new IllegalArgumentException();return jdbc.queryForObject("select count(*) from "+tabla+" where id_captacion=?",Integer.class,id);}
    private int hitos(long id){return jdbc.queryForObject("select count(*) from precio_propiedad where id_captacion=? and hito='U'",Integer.class,id);}
    private int archivos(long id){return jdbc.queryForObject("select count(*) from archivo_encargo where id_captacion=?",Integer.class,id);}
    private EncargoService.CargaTemporal cargar(Escenario e,long version){return ciclo.cargarArchivo(new EncargoService.CargaArchivo(e.encargo(),null,"TITULARIDAD_ENCARGO","original.txt","text/plain","Original explícito TEST RF052".getBytes(java.nio.charset.StandardCharsets.UTF_8),version),e.agente(),sesion(e.agente()),clave(),origen());}
    private record TxCompetidora(String hilo,long pid,long txid) { }
    /** Observa las TX del propio servicio: no envuelve el protocolo durable en una TX TEST externa. */
    private List<Object> carrera(Supplier<Object> primero,Supplier<Object> segundo)throws Exception{
        String etiqueta="C2-T4-"+UUID.randomUUID();var numero=new AtomicInteger();
        var transacciones=new ConcurrentLinkedQueue<TxCompetidora>();
        var inicio=new CountDownLatch(1);var ambas=new CountDownLatch(2);
        var hilos=Executors.newFixedThreadPool(2,r->new Thread(r,etiqueta+"-"+numero.incrementAndGet()));
        var configurable=(ConfigurableTransactionManager)gestor;
        var anteriores=new ArrayList<>(configurable.getTransactionExecutionListeners());
        var durante=new ArrayList<>(anteriores);durante.add(new TransactionExecutionListener(){
            @Override public void afterBegin(TransactionExecution tx,Throwable error){
                if(error==null&&Thread.currentThread().getName().startsWith(etiqueta)){
                    var fila=jdbc.queryForMap("select pg_backend_pid() pid,txid_current() txid");
                    transacciones.add(new TxCompetidora(Thread.currentThread().getName(),((Number)fila.get("pid")).longValue(),((Number)fila.get("txid")).longValue()));
                    ambas.countDown();
                    try{assertTrue(ambas.await(10,TimeUnit.SECONDS),"Ambas TX reales deben comenzar antes de competir");}
                    catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
                }
            }
        });configurable.setTransactionExecutionListeners(durante);
        try{
            var a=hilos.submit(()->EvidenciaC4DePrueba.enHilo(()->{esperar(inicio);return intentar(primero);}));
            var b=hilos.submit(()->EvidenciaC4DePrueba.enHilo(()->{esperar(inicio);return intentar(segundo);}));
            inicio.countDown();var resultados=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            assertEquals(2,transacciones.stream().map(TxCompetidora::hilo).distinct().count());
            assertTrue(transacciones.stream().map(TxCompetidora::pid).distinct().count()>=2,"Conexiones PostgreSQL independientes");
            assertTrue(transacciones.stream().map(TxCompetidora::txid).distinct().count()>=2,"Transacciones PostgreSQL independientes");
            transacciones.forEach(t->EvidenciaC4DePrueba.transaccion(t.hilo().endsWith("-1")?"COMPETIDOR_1":"COMPETIDOR_2",t.pid(),t.txid()));
            return resultados;
        }finally{
            inicio.countDown();hilos.shutdownNow();
            try{assertTrue(hilos.awaitTermination(10,TimeUnit.SECONDS));}
            finally{configurable.setTransactionExecutionListeners(anteriores);}
        }
    }
    private static Object intentar(Supplier<Object> accion){try{return accion.get();}catch(RuntimeException e){return e;}}
    private static void esperar(CountDownLatch inicio){try{if(!inicio.await(5,TimeUnit.SECONDS))throw new AssertionError("La carrera no se inició.");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    private static String causas(Throwable e){StringBuilder s=new StringBuilder();for(Throwable t=e;t!=null;t=t.getCause())s.append(t.getMessage()).append('\n');return s.toString();}
    private static String clave(){return "C2-"+UUID.randomUUID();}private static Procedencia origen(){return Procedencia.deCabecera("API");}private static LocalDate hoy(){return LocalDate.now(ZoneId.of("America/Lima"));}
}
