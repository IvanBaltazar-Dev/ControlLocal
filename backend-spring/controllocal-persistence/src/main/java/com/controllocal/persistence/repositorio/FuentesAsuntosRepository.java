package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.HechoAsunto;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Cabeceras canónicas sin motivos, personas, contactos, importes ni documentos. No concede permisos. */
@Repository
public class FuentesAsuntosRepository {
    public record Recurso(String tipo,long id) { }
    public record Contexto(Recurso recurso,Long responsable,Long responsableEncargo,Long solicitud,
                           Long encargo,String estado) { }
    public record Fuente(HechoAsunto hecho,Recurso recurso,Instant registradoEn,LocalDate fechaEfectiva,String estado) { }
    public record Decision(long revision,long encargo,long responsable) { }
    public record HitoCierre(long revision,long encargo,long oportunidad,Long solicitud,String tipo,String unidades,long actor) { }
    private final NamedParameterJdbcTemplate jdbc;
    public FuentesAsuntosRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

    /** Sólo identidades de la cobertura aprobada; no carga el expediente ni la Solicitud. */
    public Optional<HitoCierre> hitoCierre(long org,long revision){
        var filas=jdbc.query("""
            select r.id_revision_hito,e.id_captacion,e.id_oportunidad,e.id_solicitud_alquiler,h.tipo,
                   (cast(p.contenido_json as jsonb)#>'{cobertura,objeto,unidades}')::text unidades,r.id_rol_actor
              from revision_hito_contractual r
              join hito_contractual h on h.organizacion_id=r.organizacion_id and h.id_hito=r.id_hito and h.id_expediente=r.id_expediente
              join expediente_operacion e on e.organizacion_id=r.organizacion_id and e.id_expediente=r.id_expediente
              join revision_expediente_operacion p on p.organizacion_id=r.organizacion_id and p.id_expediente=e.id_expediente and p.id_revision_expediente=r.id_revision_expediente
             where r.organizacion_id=:org and r.id_revision_hito=:revision and r.tipo_registro='CONFIRMACION'
               and h.id_revision_confirmada=r.id_revision_hito and h.tipo in('CONTRATO_ALQUILER','ESCRITURA')
               and p.tipo_registro='REVISION' and p.decision='APROBAR'
               and (cast(p.contenido_json as jsonb)#>>'{cobertura,objeto,encargo}')=e.id_captacion::text
            """,Map.of("org",org,"revision",revision),(r,n)->new HitoCierre(r.getLong(1),r.getLong(2),r.getLong(3),numero(r,"id_solicitud_alquiler"),r.getString(5),r.getString(6),r.getLong(7)));
        if(filas.size()>1)throw new IllegalStateException("La revisión de cierre no es única.");
        return filas.stream().findFirst();
    }

    public Map<Recurso,Contexto> contextos(long org,Collection<Recurso> recursos){
        var grupos=new LinkedHashMap<String,Set<Long>>();
        for(var r:recursos)if(r!=null&&r.id()>0)grupos.computeIfAbsent(r.tipo(),k->new LinkedHashSet<>()).add(r.id());
        var resultado=new LinkedHashMap<Recurso,Contexto>();
        for(var grupo:grupos.entrySet()){
            String consulta=switch(grupo.getKey()){
                case "SOLICITUD_ALQUILER" -> "select id_solicitud id,id_rol_agente responsable,id_rol_agente responsable_encargo,id_solicitud solicitud,null::bigint encargo,estado from solicitud_alquiler where organizacion_id=:org and id_solicitud in (:ids)";
                case "CAPTACION","ENCARGO" -> "select id_captacion id,id_rol_agente responsable,id_rol_agente responsable_encargo,null::bigint solicitud,id_captacion encargo,estado from captacion where organizacion_id=:org and id_captacion in (:ids)";
                case "OPORTUNIDAD" -> "select o.id_oportunidad id,o.id_rol_agente responsable,c.id_rol_agente responsable_encargo,null::bigint solicitud,c.id_captacion encargo,o.estado from oportunidad_comercial o join captacion c on c.organizacion_id=o.organizacion_id and c.id_captacion=o.id_captacion where o.organizacion_id=:org and o.id_oportunidad in (:ids)";
                case "CONTRATO_ALQUILER" -> "select k.id_contrato_alquiler id,s.id_rol_agente responsable,c.id_rol_agente responsable_encargo,s.id_solicitud solicitud,c.id_captacion encargo,k.estado_contrato estado from contrato_alquiler k join oportunidad_comercial o on o.organizacion_id=k.organizacion_id and o.id_oportunidad=k.id_oportunidad join captacion c on c.organizacion_id=o.organizacion_id and c.id_captacion=o.id_captacion left join solicitud_alquiler s on s.organizacion_id=k.organizacion_id and s.id_solicitud=k.id_solicitud where k.organizacion_id=:org and k.id_contrato_alquiler in (:ids)";
                default -> null;
            };
            if(consulta==null)continue;
            for(var c:jdbc.query(consulta,Map.of("org",org,"ids",grupo.getValue()),(r,n)->new Contexto(
                    new Recurso(grupo.getKey(),r.getLong("id")),numero(r,"responsable"),numero(r,"responsable_encargo"),
                    numero(r,"solicitud"),numero(r,"encargo"),r.getString("estado"))))resultado.put(c.recurso(),c);
        }
        return Map.copyOf(resultado);
    }

    /** La transición es la revisión material, no el timestamp o la versión técnica de Solicitud. */
    public Map<Long,Fuente> ultimasTransicionesSolicitud(long org,Collection<Long> ids){
        if(ids.isEmpty())return Map.of();
        var filas=jdbc.query("""
            select distinct on(h.id_entidad) h.id_historial,h.id_entidad,h.fecha_evento,h.fecha_efectiva,h.estado_nuevo
              from historial_estado h join solicitud_alquiler s
                on s.organizacion_id=h.organizacion_id and s.id_solicitud=h.id_entidad
             where h.organizacion_id=:org and h.entidad_tipo='SOLICITUD_ALQUILER'
               and h.id_entidad in (:ids) and s.estado=h.estado_nuevo
             order by h.id_entidad,h.id_historial desc
            """,Map.of("org",org,"ids",ids),(r,n)->new Fuente(new HechoAsunto("HISTORIAL_ESTADO",r.getLong("id_historial"),null),
                    new Recurso("SOLICITUD_ALQUILER",r.getLong("id_entidad")),instante(r,"fecha_evento"),dia(r,"fecha_efectiva"),r.getString("estado_nuevo")));
        var resultado=new LinkedHashMap<Long,Fuente>();for(var f:filas)resultado.put(f.recurso().id(),f);return Map.copyOf(resultado);
    }

    /** Selectores de productor internos. La referencia devuelta siempre es una fila canónica real. */
    public Optional<Fuente> resolver(long org,String selector,String referencia){
        return Optional.ofNullable(resolverLote(org,selector,List.of(referencia)).get(referencia));
    }
    public Map<String,Fuente> resolverLote(long org,String selector,Collection<String> referencias){
        if(referencias.isEmpty())return Map.of();
        String consulta=switch(selector){
            case "ESTADO_SOLICITUD" -> """
                select distinct on(h.id_entidad) h.id_entidad::text referencia,h.id_historial id,'HISTORIAL_ESTADO' tipo,null::bigint version,
                       h.entidad_tipo recurso,h.id_entidad recurso_id,h.fecha_evento registro,h.fecha_efectiva fecha,h.estado_nuevo estado
                  from historial_estado h where h.organizacion_id=:org and h.entidad_tipo=:entidad
                   and h.id_entidad in (:ids) order by h.id_entidad,h.id_historial desc
                """;
            case "ENTREGA_DOCUMENTO" -> "select id_documento::text referencia,id_documento id,'DOCUMENTO_SOLICITUD' tipo,null::bigint version,'SOLICITUD_ALQUILER' recurso,id_solicitud recurso_id,fecha_entrega registro,null::date fecha from documento_solicitud where organizacion_id=:org and id_documento in (:ids)";
            case "EVALUACION_SOLICITUD" -> "select id_evaluacion::text referencia,id_evaluacion id,'EVALUACION_SOLICITUD' tipo,null::bigint version,'SOLICITUD_ALQUILER' recurso,id_solicitud recurso_id,fecha_evaluacion registro,null::date fecha from evaluacion_solicitud where organizacion_id=:org and id_evaluacion in (:ids)";
            case "REVISION_ENCARGO" -> "select r.id_revision_encargo::text referencia,r.id_revision_encargo id,'REVISION_ENCARGO' tipo,r.version_encargo version,'CAPTACION' recurso,r.id_captacion recurso_id,r.registrada_en registro,null::date fecha from revision_encargo r join peticion_revision_encargo p on p.organizacion_id=r.organizacion_id and p.id_peticion_revision_encargo=r.id_peticion join captacion c on c.organizacion_id=r.organizacion_id and c.id_captacion=r.id_captacion where r.organizacion_id=:org and r.id_revision_encargo in (:ids) and p.id_ultima_revision=r.id_revision_encargo and c.id_peticion_actual=p.id_peticion_revision_encargo";
            case "COMISION_MOVIMIENTO" -> "select m.id_comision_movimiento::text referencia,m.id_comision_movimiento id,'COMISION_MOVIMIENTO' tipo,null::bigint version,'CONTRATO_ALQUILER' recurso,c.id_contrato_alquiler recurso_id,m.fecha_creacion registro,m.fecha fecha from comision_movimiento m join comision_liquidacion c on c.organizacion_id=m.organizacion_id and c.id_comision_liquidacion=m.id_comision_liquidacion where m.organizacion_id=:org and m.id_comision_movimiento in (:ids)";
            case "COMANDO_REPARTO" -> "select x.idempotency_key referencia,x.id_comando id,'COMANDO_IDEMPOTENTE' tipo,null::bigint version,'CONTRATO_ALQUILER' recurso,c.id_contrato_alquiler recurso_id,x.fecha registro,null::date fecha from comando_idempotente x join comision_liquidacion c on c.organizacion_id=x.organizacion_id and c.id_comision_liquidacion=x.entidad_id where x.organizacion_id=:org and x.idempotency_key in (:refs) and x.tipo_comando='ASIGNAR_REPARTO_COMISION' and x.entidad_tipo='COMISION_LIQUIDACION'";
            case "REVISION_HITO" -> "select r.id_revision_hito::text referencia,r.id_revision_hito id,'REVISION_HITO_CONTRACTUAL' tipo,null::bigint version,'OPORTUNIDAD' recurso,e.id_oportunidad recurso_id,r.registrado_en registro,r.fecha_efectiva fecha from revision_hito_contractual r join expediente_operacion e on e.organizacion_id=r.organizacion_id and e.id_expediente=r.id_expediente where r.organizacion_id=:org and r.id_revision_hito in (:ids) and r.tipo_registro='CONFIRMACION'";
            case "REVISION_DOCUMENTO" -> "select e.id_evento::text referencia,e.id_evento id,'EVENTO_DOMINIO' tipo,null::bigint version,'SOLICITUD_ALQUILER' recurso,d.id_solicitud recurso_id,e.ocurrido_en registro,null::date fecha from evento_dominio e join documento_solicitud d on d.organizacion_id=e.organizacion_id and d.id_documento=e.entidad_id where e.organizacion_id=:org and e.id_evento in (:ids) and e.entidad_tipo='DOCUMENTO_SOLICITUD' and e.tipo='DOCUMENTO_SOLICITUD_REVISADO' and not exists(select 1 from evento_dominio n where n.organizacion_id=e.organizacion_id and n.entidad_tipo=e.entidad_tipo and n.entidad_id=e.entidad_id and n.tipo=e.tipo and n.id_evento>e.id_evento)";
            default -> throw new IllegalArgumentException("Selector de hecho no admitido.");
        };
        var parametros=new HashMap<String,Object>();parametros.put("org",org);parametros.put("refs",referencias);
        if(!"COMANDO_REPARTO".equals(selector))parametros.put("ids",referencias.stream().map(Long::parseLong).toList());
        parametros.put("entidad","ESTADO_SOLICITUD".equals(selector)?"SOLICITUD_ALQUILER":"");
        var resultado=new LinkedHashMap<String,Fuente>();
        jdbc.query(consulta,parametros,r->{String ref=r.getString("referencia");
            var f=new Fuente(new HechoAsunto(r.getString("tipo"),r.getLong("id"),numero(r,"version")),
                    new Recurso(r.getString("recurso"),r.getLong("recurso_id")),instante(r,"registro"),dia(r,"fecha"),
                    "ESTADO_SOLICITUD".equals(selector)?r.getString("estado"):null);
            if(resultado.put(ref,f)!=null)throw new IllegalStateException("La referencia identifica varios hechos.");
        });return Map.copyOf(resultado);
    }

    /** Sólo fuentes; observaciones se recuperan posteriormente para las revisiones ya autorizadas. */
    public List<Decision> decisionesActualesEncargo(long org,Collection<Long> responsables){
        if(responsables.isEmpty())return List.of();
        return jdbc.query("""
            select r.id_revision_encargo,c.id_captacion,c.id_rol_agente
              from captacion c join peticion_revision_encargo p
                on p.organizacion_id=c.organizacion_id and p.id_peticion_revision_encargo=c.id_peticion_actual
              join revision_encargo r on r.organizacion_id=p.organizacion_id and r.id_revision_encargo=p.id_ultima_revision
             where c.organizacion_id=:org and c.id_rol_agente in (:roles)
               and r.id_peticion=p.id_peticion_revision_encargo and r.actuacion in('OBSERVAR','APROBAR')
            """,Map.of("org",org,"roles",responsables),(r,n)->new Decision(r.getLong(1),r.getLong(2),r.getLong(3)));
    }
    public record ResultadoDecision(long revision,String accion,String motivo) { }
    public record RevisionDocumento(long evento,String contenido) { }
    /** Sólo el productor ya autorizado de la revisión; no se usa para poblar una bandeja. */
    public Optional<RevisionDocumento> ultimaRevisionDocumento(long org,long documento){
        return jdbc.query("select id_evento,carga_util from evento_dominio where organizacion_id=:org and entidad_tipo='DOCUMENTO_SOLICITUD' and entidad_id=:doc and tipo='DOCUMENTO_SOLICITUD_REVISADO' order by id_evento desc limit 1",
                Map.of("org",org,"doc",documento),(r,n)->new RevisionDocumento(r.getLong(1),r.getString(2))).stream().findFirst();
    }
    public boolean tieneConstanciaReiteracion(long org,long documento,long actor,long material){
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from evento_dominio where organizacion_id=:org
              and entidad_tipo='DOCUMENTO_SOLICITUD' and entidad_id=:doc and id_persona_rol=:actor
              and tipo='REVISION_DOCUMENTO_REITERADA' and carga_util::jsonb->>'materialRef'=cast(:material as text))
            """,Map.of("org",org,"doc",documento,"actor",actor,"material",material),Boolean.class));
    }
    public List<ResultadoDecision> resultadosDecision(long org,Collection<Long> revisiones){
        if(revisiones.isEmpty())return List.of();
        return jdbc.query("select id_revision_encargo,actuacion,observaciones from revision_encargo where organizacion_id=:org and id_revision_encargo in (:ids)",
                Map.of("org",org,"ids",revisiones),(r,n)->new ResultadoDecision(r.getLong(1),r.getString(2),r.getString(3)));
    }
    private static Long numero(ResultSet r,String campo)throws SQLException{Object v=r.getObject(campo);return v==null?null:((Number)v).longValue();}
    private static Instant instante(ResultSet r,String campo)throws SQLException{var t=r.getTimestamp(campo);return t==null?null:t.toInstant();}
    private static LocalDate dia(ResultSet r,String campo)throws SQLException{var d=r.getDate(campo);return d==null?null:d.toLocalDate();}
}
