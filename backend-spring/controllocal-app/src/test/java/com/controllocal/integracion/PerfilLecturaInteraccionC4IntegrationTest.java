package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.repositorio.InteraccionComercialRepository;
import com.controllocal.persistence.repositorio.LecturasInteraccionRepository;
import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actuacion.ComandosAutomaticosService;
import com.controllocal.service.actuacion.ComandosAutomaticosService.CorrelacionAutomatico;
import com.controllocal.service.actividad.ContextosDeInteraccion;
import com.controllocal.service.actividad.CatalogoInteracciones.ResultadoInteraccion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.Answers;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Diagnóstico opt-in Core/JPA/SQL, no HTTP/p95/SLA. Una comunicación AUTO real es
 * la plantilla autorizada; los 100k clones son sólo volumen TEST, no hechos reales.
 * Las consultas participan en la TX TEST writable que contiene el bulk y revierte:
 * no reproduce transacciones HTTP independientes ni su aislamiento entre requests.
 */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class PerfilLecturaInteraccionC4IntegrationTest {
    private static final int VOLUMEN=100_000,PASO=4_999,TAMANO=20;
    private static final String TEMP="perfil_lectura_interaccion_c4";
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired ClienteService clientes;
    @Autowired ComandosAutomaticosService automaticos;
    @Autowired InteraccionService interacciones;
    @Autowired EntityManager entidades;
    @Autowired ObjectMapper json;
    @MockitoSpyBean InteraccionComercialRepository repositorio;
    @MockitoSpyBean ContextosDeInteraccion permisos;
    @MockitoSpyBean LecturasInteraccionRepository lecturas;
    private final ThreadLocal<Medicion> activa=new ThreadLocal<>();
    private final Map<Etapa,Answer<?>> originales=new EnumMap<>(Etapa.class);

    private enum Etapa { CABECERAS,VERIFICAR_LOTE,BUSCAR,ACTUALES }
    private static final class Marco {
        final Etapa etapa;final long inicio=System.nanoTime();long hijos;
        Marco(Etapa etapa){this.etapa=etapa;}
    }
    private static final class Medicion {
        final EnumMap<Etapa,Long> nanos=new EnumMap<>(Etapa.class);
        final EnumMap<Etapa,Integer> llamadas=new EnumMap<>(Etapa.class);
        final Deque<Marco> pila=new ArrayDeque<>();
        Object ejecutar(Etapa e,InvocationOnMock invocacion,Answer<?> original)throws Throwable {
            var marco=new Marco(e);pila.push(marco);
            try{return llamarOriginal(invocacion,original);}
            finally{
                long duracion=System.nanoTime()-marco.inicio;pila.pop();
                nanos.merge(e,duracion-marco.hijos,Long::sum);llamadas.merge(e,1,Integer::sum);
                if(!pila.isEmpty())pila.peek().hijos+=duracion;
            }
        }
    }
    private static Object llamarOriginal(InvocationOnMock invocacion,Answer<?> original)throws Throwable{
        if(original==null)throw new IllegalStateException("Falta el delegado real del spy TEST.");
        // Spring puede envolver repositorios JDK con delegatesTo: una query abstracta
        // no tiene implementación Java a la que callRealMethod pueda saltar directamente.
        // Se conserva exactamente el Answer original del spy (query/proxy Spring real).
        return original==Answers.CALLS_REAL_METHODS?invocacion.callRealMethod():original.answer(invocacion);
    }
    private Object medir(Etapa e,InvocationOnMock invocacion)throws Throwable {
        var m=activa.get();var original=originales.get(e);
        return m==null?llamarOriginal(invocacion,original):m.ejecutar(e,invocacion,original);
    }
    private void instrumentar(){
        var consultasReales=mockingDetails(repositorio).getMockCreationSettings().getDefaultAnswer();
        originales.put(Etapa.CABECERAS,mockingDetails(lecturas).getMockCreationSettings().getDefaultAnswer());originales.put(Etapa.ACTUALES,consultasReales);
        originales.put(Etapa.VERIFICAR_LOTE,mockingDetails(permisos).getMockCreationSettings().getDefaultAnswer());
        originales.put(Etapa.BUSCAR,mockingDetails(lecturas).getMockCreationSettings().getDefaultAnswer());
        doAnswer(i->medir(Etapa.CABECERAS,i)).when(lecturas).cabeceras(anyLong(),anyCollection());
        doAnswer(i->medir(Etapa.VERIFICAR_LOTE,i)).when(permisos).verificarLote(anyList(),any(Actor.class),anyBoolean());
        doAnswer(i->medir(Etapa.BUSCAR,i)).when(lecturas).buscar(anyLong(),anyCollection(),nullable(String.class),
                nullable(String.class),nullable(String.class),nullable(String.class),anyInt(),anyInt());
        doAnswer(i->medir(Etapa.ACTUALES,i)).when(repositorio).actuales(anyLong(),anyCollection());
    }

    @Test
    @EnabledIfEnvironmentVariable(named="CONTROLLOCAL_PERFIL_INTERACCION_C4",matches="1")
    void perfilCoreConCienMilClonesAutorizaPaginaExactaYMideEtapasDisjuntas(){
        var origenes=new OrigenesDePrueba(jdbc,gestor,captura,contextos);var equipo=origenes.empresa();
        Actor actor=equipo.agente(),sinPermiso=equipo.colega();
        var sesion=origenes.sesion(actor);var sesionSinPermiso=origenes.sesion(sinPermiso);
        String documento=Long.toString(10_000_000L+Math.floorMod(System.nanoTime(),80_000_000L));
        long cliente=clientes.registrar(new ClienteService.DatosCliente("N","D",documento,
                "Cliente TEST perfil lectura","900000001",null,"Comercio",true,true,null),actor).id();
        var base=ComunicacionesDePrueba.llamadaSalienteSinRespuesta(cliente,"Plantilla real AUTO de perfil TEST");
        var participante=new ParticipanteDeclarado(base.destinatario().contacto(),"CLIENTE","Cliente TEST perfil lectura",null,null);
        var declaracion=new Declaracion("Declaracion de la plantilla TEST",null,null,null,null,base.momento(),null,"Fixture TEST explicito",null);
        var datos=new DatosInteraccion(base.vinculos(),participante,participante,base.tipo(),base.canal(),base.medioReal(),
                base.eventos(),ResultadoInteraccion.INTERCAMBIO_EFECTIVO,base.momento(),base.resumen(),List.of(declaracion),
                null,null,"Diagnostico TEST; no actividad productiva",null);
        var creada=automaticos.registrarInteraccion(datos,actor,sesion,new CorrelacionAutomatico(
                "perfil-c4-"+UUID.randomUUID(),"plantilla-"+UUID.randomUUID(),null),Procedencia.deLaPantalla()).resultado();
        assertEquals(0,creada.version());
        long org=actor.idOrganizacion(),plantilla=creada.id();
        assertEquals(1,contar("interaccion_comercial",org));
        var antes=conteos(org);String original=huellaPlantilla(org,plantilla);
        instrumentar();var tx=new TransactionTemplate(gestor);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        Throwable principal=null;
        try{
            tx.executeWithoutResult(estado->{
                try{
                    cargarClones(org,plantilla);analizar();
                    assertEquals(VOLUMEN+1L,contar("interaccion_comercial",org));
                    assertEquals(VOLUMEN/PASO,jdbc.queryForObject("select count(*) from "+TEMP+" where n%?=0",Integer.class,PASO));
                    consultar("SIN_TEXTO_P1",null,1,actor,sesion,plantilla,VOLUMEN+1L,false);
                    for(var q:List.of("HITOOBS","HITODEST","HITOINTER","HITODECL"))
                        consultar("SELECTIVA_"+q,q,1,actor,sesion,plantilla,VOLUMEN/PASO,false);
                    consultar("SIN_TEXTO_P2",null,2,actor,sesion,plantilla,VOLUMEN+1L,false);
                    consultar("SIN_PERMISO_ACTUAL",null,1,sinPermiso,sesionSinPermiso,plantilla,0,true);
                }finally{activa.remove();estado.setRollbackOnly();}
            });
        }catch(RuntimeException|Error error){principal=error;throw error;}
        finally{
            activa.remove();
            try{
                assertEquals(antes,conteos(org),"El rollback retira todos los clones, vínculos e historia TEST.");
                assertTrue(original.equals(huellaPlantilla(org,plantilla)),"La plantilla AUTO original debe conservarse byte a byte.");
                analizar();
                emitir(Map.of("fase","LIMPIEZA","organizacion",org,"plantilla",plantilla,
                        "versionPlantilla",creada.version(),"clonesPersistidos",0,"plantillaIntacta",true));
            }catch(RuntimeException|Error limpieza){if(principal!=null)principal.addSuppressed(limpieza);else throw limpieza;}
            finally{reset(repositorio,permisos,lecturas);}
        }
    }

    private void consultar(String escenario,String q,int pagina,Actor actor,ContextoSesion sesion,
                           long plantilla,long totalEsperado,boolean denegado){
        // Comprobación de cuenta/contexto real fuera del reloj; el lector revalida su propia autoridad.
        assertTrue(sesion.equals(ContextoSesion.de(contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor))),
                "La cuenta/contexto actual debe coincidir con el observado, sin imprimir su prueba o SID.");
        entidades.clear();var m=new Medicion();Pagina<FichaInteraccion> salida=null;
        activa.set(m);long inicio=System.nanoTime();
        try{salida=interacciones.listar(new FiltrosInteraccion(null,null,null,null,null,q,pagina,TAMANO),actor);}
        finally{
            long total=System.nanoTime()-inicio;activa.remove();
            try{
                assertTrue(m.pila.isEmpty());long medido=m.nanos.values().stream().mapToLong(Long::longValue).sum();
                assertTrue(total>=medido,"Las etapas exclusivas no pueden superar el total.");
                var ms=new LinkedHashMap<String,Object>();var llamadas=new LinkedHashMap<String,Object>();
                for(var e:Etapa.values()){ms.put(e.name(),m.nanos.getOrDefault(e,0L)/1_000_000d);llamadas.put(e.name(),m.llamadas.getOrDefault(e,0));}
                ms.put("OTRO_CORE_Y_TX",(total-medido)/1_000_000d);
                var linea=new LinkedHashMap<String,Object>();linea.put("fase","MEDICION");linea.put("diagnostico",true);
                linea.put("certificaP95",false);linea.put("escenario",escenario);linea.put("pagina",pagina);
                linea.put("organizacion",actor.idOrganizacion());linea.put("personaActor",actor.idPersona());
                linea.put("rolActor",actor.idRolOperativo());linea.put("atribucion",actor.rolEfectivo());
                linea.put("plantilla",plantilla);linea.put("volumenClones",VOLUMEN);linea.put("msTotal",total/1_000_000d);
                linea.put("msExclusivos",ms);linea.put("llamadas",llamadas);linea.put("retornoCore",salida!=null);
                if(salida!=null){linea.put("total",salida.total());linea.put("items",salida.items().size());
                    linea.put("versionesPagina",salida.items().stream().map(FichaInteraccion::version).distinct().toList());}
                emitir(linea);
            }finally{clearInvocations(repositorio,permisos,lecturas);}
        }
        assertNotNull(salida);assertEquals(totalEsperado,salida.total());
        var esperadas=denegado?List.<Long>of():paginaEsperada(plantilla,q!=null,pagina);
        assertEquals(esperadas,salida.items().stream().map(FichaInteraccion::id).toList(),"Orden/página exactos sobre IDs técnicos conocidos.");
        assertTrue(salida.items().stream().allMatch(i->i.version()==0&&i.idAgente()==actor.idRolOperativo()));
        assertEquals(2,m.llamadas.getOrDefault(Etapa.CABECERAS,0));
        assertEquals(2,m.llamadas.getOrDefault(Etapa.VERIFICAR_LOTE,0));
        assertEquals(1,m.llamadas.getOrDefault(Etapa.BUSCAR,0));
        assertEquals(denegado?0:1,m.llamadas.getOrDefault(Etapa.ACTUALES,0));
    }
    private List<Long> paginaEsperada(long plantilla,boolean selectiva,int pagina){
        String fuente=selectiva?"select id from "+TEMP+" where n%"+PASO+"=0":
                "select id from "+TEMP+" union all select "+plantilla;
        return jdbc.query("select id from ("+fuente+") ids order by id desc limit ? offset ?",
                (r,n)->r.getLong(1),TAMANO,(long)(pagina-1)*TAMANO);
    }
    private void cargarClones(long org,long plantilla){
        jdbc.execute("create temporary table "+TEMP+"(id bigint primary key,n bigint,contenido jsonb) on commit drop");
        assertEquals(1,jdbc.queryForObject("select count(*) from interaccion_comercial where organizacion_id=? and id_interaccion=? "
                +"and jsonb_typeof(contexto_declarado_json::jsonb#>'{datos,destinatario}')='object' "
                +"and jsonb_typeof(contexto_declarado_json::jsonb#>'{datos,interlocutor}')='object' "
                +"and jsonb_array_length(contexto_declarado_json::jsonb#>'{datos,declaraciones}')=1",Integer.class,org,plantilla));
        jdbc.update("""
            insert into perfil_lectura_interaccion_c4
            select nextval(pg_get_serial_sequence('interaccion_comercial','id_interaccion')),g,
              jsonb_set(jsonb_set(jsonb_set(jsonb_set(i.contexto_declarado_json::jsonb,
              '{datos,resumen}',to_jsonb('Carga TEST '||g||case when g%4999=0 then ' HITOOBS' else '' end)),
              '{datos,destinatario,nombre}',to_jsonb('Destinatario TEST'||case when g%4999=0 then ' HITODEST' else '' end)),
              '{datos,interlocutor,nombre}',to_jsonb('Interlocutor TEST'||case when g%4999=0 then ' HITOINTER' else '' end)),
              '{datos,declaraciones,0,texto}',to_jsonb('Declaracion TEST'||case when g%4999=0 then ' HITODECL' else '' end))
            from generate_series(1,100000) g cross join interaccion_comercial i
            where i.organizacion_id=? and i.id_interaccion=?
            """,org,plantilla);
        assertEquals(VOLUMEN,jdbc.update("""
            insert into interaccion_comercial(id_interaccion,organizacion_id,id_rol_agente,version,fecha_hecho,registrado_en,id_rol_autor,
              autorizacion_json,contexto_declarado_json,version_diccionario,procedencia_json)
            select n.id,i.organizacion_id,i.id_rol_agente,i.version,i.fecha_hecho,i.registrado_en,i.id_rol_autor,
              i.autorizacion_json,n.contenido::text,i.version_diccionario,i.procedencia_json
            from perfil_lectura_interaccion_c4 n cross join interaccion_comercial i
            where i.organizacion_id=? and i.id_interaccion=?
            """,org,plantilla));
        jdbc.update("""
            insert into interaccion_vinculo(organizacion_id,id_interaccion,tipo,id_propiedad,id_persona,id_prospeccion,id_captacion,
              id_oportunidad,id_rol_cliente,version_referencia,creado_en)
            select v.organizacion_id,n.id,v.tipo,v.id_propiedad,v.id_persona,v.id_prospeccion,v.id_captacion,
              v.id_oportunidad,v.id_rol_cliente,v.version_referencia,v.creado_en
            from perfil_lectura_interaccion_c4 n cross join interaccion_vinculo v
            where v.organizacion_id=? and v.id_interaccion=? and v.retirado_en is null
            """,org,plantilla);
        jdbc.update("""
            insert into interaccion_version(organizacion_id,id_interaccion,version,id_rol_actor,registrado_en,operacion,motivo,snapshot_json,procedencia_json)
            select h.organizacion_id,n.id,h.version,h.id_rol_actor,h.registrado_en,h.operacion,'BULK_DIAGNOSTICO_TEST_C4',
              jsonb_set(h.snapshot_json::jsonb,'{ficha}',(h.snapshot_json::jsonb->'ficha')||jsonb_build_object('id',n.id,'datos',n.contenido->'datos'))::text,h.procedencia_json
            from perfil_lectura_interaccion_c4 n cross join interaccion_version h
            where h.organizacion_id=? and h.id_interaccion=?
            """,org,plantilla);
    }
    private long contar(String tabla,long org){
        if(!Set.of("interaccion_comercial","interaccion_vinculo","interaccion_version").contains(tabla))throw new IllegalArgumentException();
        return jdbc.queryForObject("select count(*) from "+tabla+" where organizacion_id=?",Long.class,org);
    }
    private List<Long> conteos(long org){return List.of(contar("interaccion_comercial",org),contar("interaccion_vinculo",org),contar("interaccion_version",org));}
    private String huellaPlantilla(long org,long id){return jdbc.queryForObject("select md5(row_to_json(i)::text) from interaccion_comercial i where organizacion_id=? and id_interaccion=?",String.class,org,id);}
    private void analizar(){for(var t:List.of("interaccion_comercial","interaccion_vinculo","interaccion_version"))jdbc.execute("analyze "+t);}
    private void emitir(Map<String,?> metadatos){
        try{System.out.println("PERFIL_INTERACCION_C4 "+json.writeValueAsString(metadatos));}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("No se pudo emitir metadata numérica del diagnóstico TEST.",e);}
    }
}
