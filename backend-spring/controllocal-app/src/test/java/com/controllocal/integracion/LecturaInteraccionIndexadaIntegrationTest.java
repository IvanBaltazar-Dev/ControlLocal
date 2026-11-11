package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.repositorio.LecturasInteraccionRepository;
import com.controllocal.persistence.repositorio.LecturasInteraccionRepository.PaginaIds;
import com.controllocal.persistence.repositorio.InteraccionComercialRepository;
import com.controllocal.persistence.repositorio.InteraccionComercialRepository.Cabecera;
import com.controllocal.persistence.repositorio.InteraccionComercialRepository.CabeceraActual;
import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actuacion.ComandosAutomaticosService;
import com.controllocal.service.actuacion.ComandosAutomaticosService.CorrelacionAutomatico;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Procedencia;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Equivalencia funcional del lector JDBC/PG y su proyeccion privada V129.
 * Comunicaciones nuevas mediante AUTO/Core real; legacy es un fixture SQL explicito.
 * Sin mocks de autoridad, volumen, mediciones de latencia ni afirmaciones SLA.
 */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class LecturaInteraccionIndexadaIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired ClienteService clientes;
    @Autowired ComandosAutomaticosService automaticos;
    @Autowired ActuacionesService actuaciones;
    @Autowired Documentos documentos;
    @Autowired InteraccionService interacciones;
    @Autowired LecturasInteraccionRepository lecturas;
    @Autowired EntityManager entidades;
    private OrigenesDePrueba origenes;
    private OrigenesDePrueba.Equipo equipo;
    private Actor actor;
    private ContextoSesion sid;
    private long cliente;

    @BeforeEach void preparar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);
        origenes=new OrigenesDePrueba(jdbc,gestor,captura,contextos);
        equipo=origenes.empresa();actor=equipo.agente();sid=origenes.sesion(actor);
        cliente=cliente(actor);EvidenciaC4DePrueba.actor(actor);
    }
    @AfterEach void cerrarEvidencia(){EvidenciaC4DePrueba.limpiar();}

    @Test void buscaLosCincoCamposExactosIncluidasObservacionesLegadas(){
        var resumen=nueva("IDXRESUMEN",null,null,List.of(),"Fuente TEST");
        var destino=nueva("Conversacion declarada", "IDXDESTINO",null,List.of(),"Fuente TEST");
        var interlocutor=nueva("Conversacion declarada",null,"IDXINTERLOCUTOR",List.of(),"Fuente TEST");
        var declaracion=nueva("Conversacion declarada",null,null,List.of(declaracion("IDXDECLARACION","Fuente TEST")),"Fuente TEST");
        long legado=legado("IDXLEGADO");
        var esperadas=new LinkedHashMap<String,Long>();
        esperadas.put("IDXRESUMEN",resumen.id());esperadas.put("IDXDESTINO",destino.id());
        esperadas.put("IDXINTERLOCUTOR",interlocutor.id());esperadas.put("IDXDECLARACION",declaracion.id());
        esperadas.put("IDXLEGADO",legado);
        var ids=List.copyOf(esperadas.values());
        esperadas.forEach((texto,id)->{
            assertPagina(buscar(ids,texto),List.of(id),1);
            var pagina=interacciones.listar(filtro(texto),actor);
            assertEquals(1,pagina.total());assertEquals(List.of(id),pagina.items().stream().map(FichaInteraccion::id).toList());
        });
        assertNull(jdbc.queryForObject("select version_diccionario from interaccion_comercial where organizacion_id=? and id_interaccion=?",String.class,actor.idOrganizacion(),legado));
        assertNull(jdbc.queryForObject("select contexto_declarado_json from interaccion_comercial where organizacion_id=? and id_interaccion=?",String.class,actor.idOrganizacion(),legado));
        var historica=interacciones.obtener(legado,actor);
        assertEquals("L",historica.canalHistorico());assertNull(historica.datos().tipo());assertNull(historica.datos().canal());

        // La rectificacion confirmada cambia la proyeccion actual, sin borrar su version original.
        assertTrue(esCandidata(resumen.id(),"IDXRESUMEN"));
        assertFalse(esCandidata(resumen.id(),"IDXRECTIFICADO"));
        var d=resumen.datos();
        var correccion=new DatosInteraccion(d.vinculos(),d.destinatario(),d.interlocutor(),d.tipo(),d.canal(),d.medioReal(),
                d.eventos(),d.resultado(),d.momento(),"IDXRECTIFICADO",d.declaraciones(),d.proxima(),d.interaccionAnterior(),d.fuente(),d.restriccion());
        var propuesta=actuaciones.prepararRectificacionInteraccion(actor,
                new Rectificacion(resumen.id(),resumen.version(),correccion,"Correccion expresa del resumen TEST"),sid,Procedencia.deLaPantalla());
        var rectificada=new ActuacionesHumanasC4DePrueba(actuaciones,documentos)
                .confirmar(propuesta,actor,sid,FichaInteraccion.class).datos();
        assertEquals(resumen.id(),rectificada.id());assertEquals(resumen.version()+1,rectificada.version());
        assertFalse(esCandidata(resumen.id(),"IDXRESUMEN"));
        assertTrue(esCandidata(resumen.id(),"IDXRECTIFICADO"));
        assertPagina(buscar(ids,"IDXRESUMEN"),List.of(),0);
        assertPagina(buscar(ids,"IDXRECTIFICADO"),List.of(resumen.id()),1);
        assertEquals(0,interacciones.listar(filtro("IDXRESUMEN"),actor).total());
        var paginaRectificada=interacciones.listar(filtro("IDXRECTIFICADO"),actor);
        assertEquals(1,paginaRectificada.total());assertEquals(List.of(rectificada),paginaRectificada.items());
        assertEquals(resumen,interacciones.obtenerVersion(resumen.id(),resumen.version(),actor));
        assertEquals(rectificada,interacciones.obtenerVersion(rectificada.id(),rectificada.version(),actor));
        assertEquals(List.of(resumen.version(),rectificada.version()),
                interacciones.historia(resumen.id(),actor).stream().map(Antecedente::version).sorted().toList());
    }

    @Test void buscaComillasYSaltoDesescapadosYNoPierdePorcentajeFinalEscapado(){
        var comillas=nueva("Nota \"CITAJDBC\" en Línea\nSURCOJDBC",null,null,List.of(),"Fuente TEST");
        var porcentaje=nueva("FINJDBC%",null,null,List.of(),"Fuente TEST");
        var ids=List.of(comillas.id(),porcentaje.id());
        String json=contenido(comillas.id());
        assertTrue(json.contains("\\\"CITAJDBC\\\""),"El original JSON contiene comillas escapadas, no el texto literal del indice.");
        assertPagina(buscar(ids,"\"CITAJDBC\""),List.of(comillas.id()),1);
        assertPagina(buscar(ids,"Línea\nSURCOJDBC"),List.of(comillas.id()),1);
        // El patron Core termina en porcentaje escapado: %finjdbc\%.
        // El prefilter debe ampliar candidatos; el match definitivo sigue siendo el de este campo.
        assertPagina(buscar(ids,"FINJDBC\\"),List.of(porcentaje.id()),1);
    }

    @Test void elPrefiltroPuedeCruzarCamposODeclaracionesPeroElMatchExactoLosExcluye(){
        var campos=nueva("ALFA","BETA",null,List.of(),"Fuente TEST");
        var declaraciones=nueva("Conversacion declarada",null,null,
                List.of(declaracion("GAMMA","Fuente TEST"),declaracion("DELTA","Fuente TEST")),"Fuente TEST");
        String entreCampos="ALFA"+(char)31+"BETA",entreDeclaraciones="GAMMA"+(char)31+"DELTA";
        assertTrue(esCandidata(campos.id(),entreCampos),"La negativa debe alcanzar el prefilter materializado.");
        assertTrue(esCandidata(declaraciones.id(),entreDeclaraciones),"La negativa debe alcanzar la concatenacion de declaraciones.");
        var ids=List.of(campos.id(),declaraciones.id());
        assertPagina(buscar(ids,entreCampos),List.of(),0);
        assertPagina(buscar(ids,entreDeclaraciones),List.of(),0);
    }

    @Test void deduplicaConservaTotalFueraDeRangoYRespetaOrgYSubconjuntoPermitido(){
        var uno=nueva("IDXREPETIDO","IDXREPETIDO","IDXREPETIDO",
                List.of(declaracion("IDXREPETIDO","Fuente TEST"),declaracion("IDXREPETIDO","Fuente TEST")),"Fuente TEST");
        var dos=nueva("IDXREPETIDO",null,null,List.of(),"Fuente TEST");
        var excluida=nueva("IDXREPETIDO",null,null,List.of(),"Fuente TEST");
        var otro=origenes.empresa();var otraSesion=origenes.sesion(otro.agente());long otroCliente=cliente(otro.agente());
        var ajena=registrar(ComunicacionesDePrueba.llamadaSalienteSinRespuesta(otroCliente,"IDXREPETIDO"),otro.agente(),otraSesion);
        var autorizadas=List.of(uno.id(),uno.id(),dos.id(),ajena.id());
        var esperadas=List.of(uno.id(),dos.id()).stream().sorted(Comparator.reverseOrder()).toList();
        assertPagina(buscar(autorizadas,"IDXREPETIDO"),esperadas,2);
        assertPagina(lecturas.buscar(actor.idOrganizacion(),autorizadas,null,null,null,"IDXREPETIDO",8,1),List.of(),2);
        assertPagina(buscar(List.of(uno.id(),ajena.id()),"IDXREPETIDO"),List.of(uno.id()),1);
        assertPagina(buscar(List.of(ajena.id()),"IDXREPETIDO"),List.of(),0);
        assertPagina(buscar(List.of(),"IDXREPETIDO"),List.of(),0);
        assertFalse(esperadas.contains(excluida.id()));
    }

    @Test void conservaFiltrosExactosYDatosNulosSinInferirDelLegado(){
        var llamada=registrar(base("Filtro TEST"));
        var b=base("Filtro TEST");
        var mensaje=registrar(new DatosInteraccion(b.vinculos(),b.destinatario(),null,
                TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,null,
                List.of(new EventoDeclarado("envio-TEST",EventoComunicacion.MENSAJE_ENVIADO,b.momento())),
                ResultadoInteraccion.SIN_INTERCAMBIO,b.momento(),b.resumen(),List.of(),null,null,b.fuente(),null));
        var parcial=registrar(new DatosInteraccion(b.vinculos(),null,null,null,null,null,
                List.of(),null,null,null,null,null,null,"Hecho real declarado TEST con dimensiones desconocidas",null));
        long legado=legado("Filtro TEST");var ids=List.of(llamada.id(),mensaje.id(),parcial.id(),legado);
        assertEquals(4,buscar(ids,null).total());assertEquals(4,buscar(ids," ").total());
        assertPagina(lecturas.buscar(actor.idOrganizacion(),ids,"LLAMADA","SIN_RESPUESTA","TELEFONO",null,1,20),List.of(llamada.id()),1);
        assertPagina(lecturas.buscar(actor.idOrganizacion(),ids,"MENSAJERIA","SIN_INTERCAMBIO","WHATSAPP",null,1,20),List.of(mensaje.id()),1);
        assertPagina(lecturas.buscar(actor.idOrganizacion(),ids,"LLAMADA",null,"WHATSAPP",null,1,20),List.of(),0);
        assertNull(parcial.datos().tipo());assertNull(parcial.datos().canal());assertNull(parcial.datos().resultado());
        assertTrue(parcial.datos().declaraciones().isEmpty());
        assertPagina(buscar(List.of(parcial.id()),"_"),List.of(),0);
    }

    @Test void fuentesYObservacionPosteriorConservadasNoSeVuelvenCamposBuscables(){
        var b=base("Contenido original TEST");
        var original=registrar(new DatosInteraccion(b.vinculos(),b.destinatario(),null,
                TipoActividad.MENSAJERIA,CanalComunicacion.CORREO_ELECTRONICO,null,
                List.of(new EventoDeclarado("correo-TEST",EventoComunicacion.MENSAJE_ENVIADO,b.momento())),
                ResultadoInteraccion.SIN_INTERCAMBIO,b.momento(),b.resumen(),
                List.of(declaracion("Declaracion original TEST","IDX_FUENTE_DECLARACION")),null,null,"IDX_FUENTE_HECHO",null));
        var momentoPosterior=new MomentoHecho(b.momento().fecha(),LocalTime.of(10,0),ZoneOffset.ofHours(-5));
        var observacion=new ObservacionPosterior(original.version(),momentoPosterior,"IDX_DETALLE_POSTERIOR","IDX_FUENTE_POSTERIOR",ResultadoInteraccion.COMUNICACION_FALLIDA);
        var actual=automaticos.anotarObservacionInteraccion(original.id(),observacion,actor,sid,correlacion(),Procedencia.deLaPantalla()).resultado();
        EvidenciaC4DePrueba.resultado("ANOTAR_OBSERVACION",actor,actual);
        assertEquals(1,actual.hechosPosteriores().size());assertEquals(original.datos(),actual.datos());
        String json=contenido(original.id());
        for(String noBuscable:List.of("IDX_FUENTE_DECLARACION","IDX_FUENTE_HECHO","IDX_DETALLE_POSTERIOR","IDX_FUENTE_POSTERIOR")){
            assertTrue(json.contains(noBuscable),"El dato excluido sigue conservado en su original.");
            assertPagina(buscar(List.of(original.id()),noBuscable),List.of(),0);
            assertEquals(0,interacciones.listar(filtro(noBuscable),actor).total());
        }
    }

    @Test void elIndiceNoConcedeAlColegaUnaInteraccionFueraDeSuAlcanceActual(){
        var propia=nueva("IDX_ALCANCE_AUTOR",null,null,List.of(),"Fuente TEST");
        assertPagina(buscar(List.of(propia.id()),"IDX_ALCANCE_AUTOR"),List.of(propia.id()),1);
        assertEquals(1,interacciones.listar(filtro("IDX_ALCANCE_AUTOR"),actor).total());
        origenes.sesion(equipo.colega());
        var denegada=interacciones.listar(filtro("IDX_ALCANCE_AUTOR"),equipo.colega());
        assertEquals(0,denegada.total());assertTrue(denegada.items().isEmpty());
    }

    @Test void cabecerasCompactasConservanCadaParYLosNueveCamposFrenteAlJpqlPlano(){
        var primera=nueva("Primera del grupo TEST",null,null,List.of(),"Fuente TEST");
        long legadoIntercalado=legado("Antecedente legado intercalado TEST");
        var segunda=nueva("Segunda del grupo TEST",null,null,List.of(),"Fuente TEST");
        long otroCliente=cliente(actor);
        var tercera=nueva("Tercera del grupo TEST",null,null,List.of(),"Fuente TEST");
        var colegaSid=origenes.sesion(equipo.colega());
        var colega=registrar(ComunicacionesDePrueba.llamadaSalienteSinRespuesta(cliente,"Autor distinto TEST"),equipo.colega(),colegaSid);
        var variante=nueva("Mismo objeto de autorizacion TEST",null,null,List.of(),"Fuente TEST");
        var nota=new ObservacionPosterior(primera.version(),
                new MomentoHecho(OrigenesDePrueba.hoy().minusDays(1),LocalTime.of(10,0),ZoneOffset.ofHours(-5)),
                "Conocimiento posterior TEST para version distinta","Fuente TEST",null);
        var primeraActual=automaticos.anotarObservacionInteraccion(primera.id(),nota,actor,sid,correlacion(),Procedencia.deLaPantalla()).resultado();
        EvidenciaC4DePrueba.resultado("ANOTAR_OBSERVACION",actor,primeraActual);
        assertNotEquals(primera.version(),primeraActual.version());

        String autorizacion=autorizacion(primera.id());
        assertEquals(autorizacion,autorizacion(segunda.id()));assertEquals(autorizacion,autorizacion(tercera.id()));
        assertEquals(autorizacion,autorizacion(variante.id()));
        var otraEmpresa=origenes.empresa();var otroActor=otraEmpresa.agente();
        var ajena=registrar(ComunicacionesDePrueba.llamadaSalienteSinRespuesta(cliente(otroActor),"Otro tenant TEST"),otroActor,origenes.sesion(otroActor));

        var tx=new TransactionTemplate(gestor);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.executeWithoutResult(estado->{
            try{
                // Fixture lexico temporal: mismo objeto/actor/refs; no cambia historia ni permisos.
                // Se comprueba que COLLATE C no fusione textos distintos por castear a JSONB.
                assertTrue(autorizacion.contains("\"agente\""));
                String distinta=autorizacion.replace("\"agente\"","\""+'\\'+"u0061gente\"");
                assertNotEquals(autorizacion,distinta);
                assertEquals(Boolean.TRUE,jdbc.queryForObject("select cast(? as jsonb)=cast(? as jsonb)",Boolean.class,autorizacion,distinta));
                assertEquals(1,jdbc.update("update interaccion_comercial set autorizacion_json=? where organizacion_id=? and id_interaccion=?",distinta,actor.idOrganizacion(),variante.id()));
                long otroLegado=Objects.requireNonNull(jdbc.queryForObject("""
                    insert into interaccion_comercial(organizacion_id,contexto,id_rol_cliente,id_rol_agente,
                        canal_contacto,observaciones,fecha_hora)
                    values(?,'CLIENTE',?,?,'L',?,?) returning id_interaccion
                    """,Long.class,actor.idOrganizacion(),otroCliente,actor.idRolOperativo(),"Otra referencia legada TEST",
                        OffsetDateTime.of(OrigenesDePrueba.hoy().minusDays(1),LocalTime.of(9,0),ZoneOffset.ofHours(-5))));

                var propias=compararCabeceras(actor.idOrganizacion(),List.of(actor.idRolOperativo()));
                var ids=List.of(primera.id(),legadoIntercalado,segunda.id(),tercera.id(),variante.id(),otroLegado)
                        .stream().sorted(Comparator.reverseOrder()).toList();
                assertEquals(ids,propias.stream().map(CabeceraActual::id).toList());
                var porId=new HashMap<Long,CabeceraActual>();propias.forEach(c->porId.put(c.id(),c));
                assertEquals(primeraActual.version(),porId.get(primera.id()).version());
                assertEquals(segunda.version(),porId.get(segunda.id()).version());
                assertEquals(tercera.version(),porId.get(tercera.id()).version());
                assertEquals(distinta,porId.get(variante.id()).autorizacion());
                assertEquals(autorizacion,porId.get(primera.id()).autorizacion());
                var legado=porId.get(legadoIntercalado);
                assertNull(legado.autorizacion());assertNull(legado.diccionario());
                assertNull(legado.oportunidad());assertNull(legado.prospeccion());assertNull(legado.encargo());
                assertEquals(cliente,legado.cliente().longValue());
                assertEquals(otroCliente,porId.get(otroLegado).cliente().longValue());
                for(long id:List.of(primera.id(),segunda.id(),tercera.id(),variante.id())){
                    var actual=porId.get(id);assertNull(actual.oportunidad());assertNull(actual.prospeccion());
                    assertNull(actual.encargo());assertNull(actual.cliente());
                }
                var compartidas=compararCabeceras(actor.idOrganizacion(),List.of(actor.idRolOperativo(),equipo.colega().idRolOperativo()));
                assertEquals(7,compartidas.size());assertTrue(compartidas.stream().anyMatch(c->c.id()==colega.id()));
                assertEquals(propias,compararCabeceras(actor.idOrganizacion(),List.of(actor.idRolOperativo(),actor.idRolOperativo(),otroActor.idRolOperativo())));
                assertTrue(compararCabeceras(actor.idOrganizacion(),List.of(otroActor.idRolOperativo())).isEmpty());
                assertTrue(lecturas.cabeceras(actor.idOrganizacion(),List.of()).isEmpty());
                assertTrue(compararCabeceras(otroActor.idOrganizacion(),List.of(actor.idRolOperativo())).isEmpty());
                assertEquals(List.of(ajena.id()),compararCabeceras(otroActor.idOrganizacion(),List.of(otroActor.idRolOperativo())).stream().map(CabeceraActual::id).toList());
            }finally{estado.setRollbackOnly();}
        });
        assertEquals(autorizacion,autorizacion(variante.id()),"El fixture lexico revierte y conserva el original.");
    }

    private List<CabeceraActual> compararCabeceras(long org,List<Long> roles){
        // Referencia de prueba: no es un fallback ni otra lectura de producto.
        var plana=entidades.createQuery(InteraccionComercialRepository.CABECERA
                        +"where i.organizacionId=:org and i.agente.id in :roles order by i.id desc",CabeceraActual.class)
                .setParameter("org",org).setParameter("roles",roles).getResultList();
        var compacta=lecturas.cabeceras(org,roles).stream().map(LecturaInteraccionIndexadaIntegrationTest::copiarCabecera).toList();
        assertEquals(plana,compacta,"Cada ID/version y los siete metadatos deben coincidir, en orden global.");
        return compacta;
    }
    private static CabeceraActual copiarCabecera(Cabecera c){return new CabeceraActual(c.getId(),c.getVersion(),c.getAgente(),c.getAutorizacion(),c.getDiccionario(),c.getOportunidad(),c.getProspeccion(),c.getEncargo(),c.getCliente());}
    private String autorizacion(long id){return jdbc.queryForObject("select autorizacion_json from interaccion_comercial where organizacion_id=? and id_interaccion=?",String.class,actor.idOrganizacion(),id);}

    private FichaInteraccion nueva(String resumen,String nombreDestino,String nombreInterlocutor,List<Declaracion> declaraciones,String fuente){
        var b=base(resumen);var contacto=b.destinatario().contacto();
        var destino=nombreDestino==null?b.destinatario():new ParticipanteDeclarado(contacto,"CLIENTE",nombreDestino,null,null);
        var interlocutor=nombreInterlocutor==null?null:new ParticipanteDeclarado(contacto,"CLIENTE",nombreInterlocutor,null,null);
        return registrar(new DatosInteraccion(b.vinculos(),destino,interlocutor,b.tipo(),b.canal(),b.medioReal(),
                b.eventos(),ResultadoInteraccion.INTERCAMBIO_EFECTIVO,b.momento(),resumen,declaraciones,null,null,fuente,null));
    }
    private DatosInteraccion base(String resumen){return ComunicacionesDePrueba.llamadaSalienteSinRespuesta(cliente,resumen);}
    private FichaInteraccion registrar(DatosInteraccion datos){return registrar(datos,actor,sid);}
    private FichaInteraccion registrar(DatosInteraccion datos,Actor a,ContextoSesion sesion){
        var ficha=automaticos.registrarInteraccion(datos,a,sesion,correlacion(),Procedencia.deLaPantalla()).resultado();
        EvidenciaC4DePrueba.resultado("REGISTRAR_INTERACCION",a,ficha);return ficha;
    }
    private long cliente(Actor a){
        String documento=Long.toString(10_000_000L+Math.floorMod(System.nanoTime(),80_000_000L));
        long id=clientes.registrar(new ClienteService.DatosCliente("N","D",documento,"Cliente TEST indice",
                "900000001",null,"Comercio",true,true,null),a).id();
        EvidenciaC4DePrueba.recurso(a,"CLIENTE",id,null);return id;
    }
    private long legado(String observaciones){
        // Registro historico TEST expresamente legado, no traduccion de un resultado viejo a C4.
        long id=Objects.requireNonNull(jdbc.queryForObject("""
            insert into interaccion_comercial(organizacion_id,contexto,id_rol_cliente,id_rol_agente,
                canal_contacto,observaciones,fecha_hora)
            values(?,'CLIENTE',?,?,'L',?,?) returning id_interaccion
            """,Long.class,actor.idOrganizacion(),cliente,actor.idRolOperativo(),observaciones,
                OffsetDateTime.of(OrigenesDePrueba.hoy().minusDays(1),LocalTime.of(9,0),ZoneOffset.ofHours(-5))));
        EvidenciaC4DePrueba.recurso(actor,"INTERACCION",id,0L);return id;
    }
    private PaginaIds buscar(Collection<Long> ids,String q){return lecturas.buscar(actor.idOrganizacion(),ids,null,null,null,q,1,20);}
    private boolean esCandidata(long id,String q){
        String patron="%%"+q.toLowerCase(Locale.ROOT)+"%%";
        return Boolean.TRUE.equals(jdbc.queryForObject("select (busqueda_proyectada ->> 'texto') like ? from interaccion_comercial where organizacion_id=? and id_interaccion=?",
                Boolean.class,patron,actor.idOrganizacion(),id));
    }
    private String contenido(long id){return jdbc.queryForObject("select contexto_declarado_json from interaccion_comercial where organizacion_id=? and id_interaccion=?",String.class,actor.idOrganizacion(),id);}
    private static Declaracion declaracion(String texto,String fuente){return new Declaracion(texto,null,null,null,null,null,null,fuente,null);}
    private static FiltrosInteraccion filtro(String texto){return new FiltrosInteraccion(null,null,null,null,null,texto,1,20);}
    private static void assertPagina(PaginaIds pagina,List<Long> ids,long total){assertEquals(total,pagina.total());assertEquals(ids,pagina.ids());}
    private static CorrelacionAutomatico correlacion(){return new CorrelacionAutomatico("indice-c4-"+UUID.randomUUID(),"turno-TEST",UUID.randomUUID().toString());}
}
