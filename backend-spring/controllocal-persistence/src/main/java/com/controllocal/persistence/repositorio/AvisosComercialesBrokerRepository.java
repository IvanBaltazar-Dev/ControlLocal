package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

/** Proyección mínima del outbox; filtra organización, recurso, responsabilidad y fuente antes del contenido. */
@Repository
public class AvisosComercialesBrokerRepository {
    public record Fuente(long evento,String tipo,String entidad,long id,Long actor,Instant registrado,
        String marca,String solicitud,String estadoFuente,Long responsable,boolean causaPendiente) { }
    public record Entrega(long id,String estado,Instant atendido) { }
    private final NamedParameterJdbcTemplate jdbc;
    public AvisosComercialesBrokerRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Fuente> fuentes(long org,Collection<Long> roles){
        if(roles.isEmpty())return List.of();
        return jdbc.query("""
            with hechos as (
              select e.*,e.carga_util::jsonb->'avisoBroker' marca,
                e.carga_util::jsonb#>>'{avisoBroker,recurso}' raiz,
                case when e.carga_util::jsonb#>>'{avisoBroker,referencia}' ~ '^[1-9][0-9]*$'
                  then (e.carga_util::jsonb#>>'{avisoBroker,referencia}')::bigint end raiz_id,
                e.carga_util::jsonb->>'solicitud' solicitud
               from evento_dominio e where e.organizacion_id=:org and jsonb_exists(e.carga_util::jsonb,'avisoBroker'))
            select e.id_evento,e.tipo,e.entidad_tipo,e.entidad_id,e.id_persona_rol,e.ocurrido_en,
               e.marca::text,e.solicitud,coalesce(c.estado,d.estado,s.estado) estado_fuente,
               coalesce(c.id_rol_agente,d.id_rol_responsable,
                 case when s.organizacion_encargo_id=:org then s.id_responsable_encargo
                   else coalesce(s.id_responsable_demanda,s.id_responsable_invitado) end) responsable,
               case
                when e.tipo in ('PETICION_ENCARGO_PRESENTADA','PETICION_ENCARGO_REENVIADA') then exists(
                  select 1 from peticion_revision_encargo p where p.organizacion_id=:org and p.id_peticion_revision_encargo=c.id_peticion_actual and p.id_peticion_revision_encargo::text=e.carga_util::jsonb->>'peticion' and p.estado='P')
                when e.tipo='FIN_ENCARGO_SOLICITADO' then exists(select 1 from solicitud_finalizacion_encargo f where f.organizacion_id=:org and f.id_captacion=c.id_captacion and f.id_solicitud_finalizacion_encargo::text=e.carga_util::jsonb->>'solicitud' and f.estado='P')
                when e.tipo='ADENDA_PROPUESTA' then coalesce((select r.actuacion='PROPONER_ADENDA' from revision_encargo r where r.organizacion_id=:org and r.id_captacion=c.id_captacion and (r.id_revision_encargo::text=e.carga_util::jsonb->>'propuesta' or r.snapshot::jsonb#>>'{adenda,propuesta}'=e.carga_util::jsonb->>'propuesta') order by r.id_revision_encargo desc limit 1),false)
                when e.tipo='NEGOCIACION_REGISTRADA' then exists(select 1 from oferta o where o.organizacion_id=:org and o.id_oferta=e.entidad_id and o.estado='P' and o.version::text=e.marca->>'version')
                when e.tipo='FORMALIZACION_REGISTRADA' and e.entidad_tipo='HITO_CONTRACTUAL' then exists(select 1 from hito_contractual h where h.organizacion_id=:org and h.id_hito=e.entidad_id and h.id_revision_informada is not null and h.id_revision_confirmada is null)
                when e.tipo='FORMALIZACION_REGISTRADA' and e.entidad_tipo='EXPEDIENTE_OPERACION' then exists(select 1 from expediente_operacion x where x.organizacion_id=:org and x.id_expediente=e.entidad_id and x.id_revision_presentada is not null and not exists(select 1 from revision_expediente_operacion r where r.organizacion_id=x.organizacion_id and r.id_expediente=x.id_expediente and r.id_presentacion=x.id_revision_presentada and r.tipo_registro='REVISION'))
                else true end causa_pendiente
             from hechos e
             left join captacion c on e.raiz='ENCARGO' and c.organizacion_id=:org and c.id_captacion=e.raiz_id
             left join requerimiento_cliente d on e.raiz='DEMANDA' and d.organizacion_id=:org and d.id_requerimiento=e.raiz_id
             left join solicitud_colaboracion s on e.raiz='COLABORACION' and s.id_solicitud_colaboracion=e.raiz_id
               and /*VISIBLE_COLAB*/
            where (
             (c.id_rol_agente in (:roles) and (
               (e.entidad_tipo='CAPTACION' and e.entidad_id=c.id_captacion)
               or (e.entidad_tipo='OFERTA' and exists(select 1 from oferta f where f.organizacion_id=:org and f.id_oferta=e.entidad_id and f.id_captacion=c.id_captacion))
               or (e.entidad_tipo='EXPEDIENTE_OPERACION' and exists(select 1 from expediente_operacion x where x.organizacion_id=:org and x.id_expediente=e.entidad_id and x.id_captacion=c.id_captacion))
               or (e.entidad_tipo='HITO_CONTRACTUAL' and exists(select 1 from hito_contractual h join expediente_operacion x on x.organizacion_id=h.organizacion_id and x.id_expediente=h.id_expediente where h.organizacion_id=:org and h.id_hito=e.entidad_id and x.id_captacion=c.id_captacion))
               or (e.entidad_tipo='COMISION_LIQUIDACION' and exists(select 1 from comision_liquidacion x where x.organizacion_id=:org and x.id_comision_liquidacion=e.entidad_id and x.id_captacion=c.id_captacion))
               or (e.entidad_tipo='RECONFIRMACION' and exists(select 1 from solicitud_reconfirmacion x where x.organizacion_id=:org and x.id::text=e.solicitud and x.tipo_recurso='ENCARGO' and x.recurso_id=c.id_captacion))))
             or (d.id_rol_responsable in (:roles) and e.entidad_tipo='RECONFIRMACION' and exists(select 1 from solicitud_reconfirmacion x where x.organizacion_id=:org and x.id::text=e.solicitud and x.tipo_recurso='DEMANDA' and x.recurso_id=d.id_requerimiento))
             or (e.entidad_tipo='SOLICITUD_COLABORACION' and e.entidad_id=s.id_solicitud_colaboracion and
                 (case when s.organizacion_encargo_id=:org then s.id_responsable_encargo else coalesce(s.id_responsable_demanda,s.id_responsable_invitado) end) in (:roles))
            ) order by e.ocurrido_en desc,e.id_evento desc
            """.replace("/*VISIBLE_COLAB*/",SolicitudColaboracionRepository.VISIBLE_SQL),Map.of("org",org,"roles",roles),(r,n)->new Fuente(r.getLong(1),r.getString(2),r.getString(3),r.getLong(4),(Long)r.getObject(5),r.getTimestamp(6).toInstant(),r.getString(7),r.getString(8),r.getString(9),(Long)r.getObject(10),r.getBoolean(11)));
    }
    public Entrega registrar(long org,long broker,Fuente f,String codigo,String recurso,long idRecurso,boolean propia){
        String tipo=switch(recurso){case "ENCARGO"->"CAPTACION";case "DEMANDA"->"REQUERIMIENTO";case "COLABORACION"->"SOLICITUD_COLABORACION";default->throw new IllegalArgumentException("Recurso de entrega NB no acreditado");};
        var p=parametros(org,broker,f.evento(),codigo);p.put("tipoRecurso",tipo);p.put("recurso",idRecurso);p.put("mensaje",codigo+": hecho comercial registrado");
        p.put("estado",propia?"T":"A");p.put("generada",utc(f.registrado()));p.put("resuelta",propia?utc(f.registrado()):null);
        jdbc.update("""
            insert into alerta(organizacion_id,tipo,severidad,entidad_tipo,entidad_id,id_rol_broker,nb_evento,nb_codigo,mensaje,estado,fecha_generacion,fecha_resolucion)
            values(:org,'AVISO_BROKER','INFO',:tipoRecurso,:recurso,:broker,:evento,:codigo,:mensaje,:estado,:generada,:resuelta)
            on conflict(organizacion_id,nb_evento,nb_codigo,id_rol_broker) where nb_evento is not null do nothing
            """,p);
        return entrega(org,broker,f.evento(),codigo).orElseThrow();
    }
    public Optional<Entrega> entrega(long org,long broker,long evento,String codigo){
        return jdbc.query("select id_alerta,estado,fecha_resolucion from alerta where organizacion_id=:org and id_rol_broker=:broker and nb_evento=:evento and nb_codigo=:codigo",
            parametros(org,broker,evento,codigo),(r,n)->new Entrega(r.getLong(1),r.getString(2),r.getTimestamp(3)==null?null:r.getTimestamp(3).toInstant())).stream().findFirst();
    }
    public boolean atender(long org,long broker,long evento,String codigo,Instant ahora){
        var p=parametros(org,broker,evento,codigo);p.put("ahora",utc(ahora));return jdbc.update("update alerta set estado='T',fecha_resolucion=:ahora where organizacion_id=:org and id_rol_broker=:broker and nb_evento=:evento and nb_codigo=:codigo and estado='A'",p)==1;
    }
    public void retirar(long org,long broker,long evento,String codigo,Instant ahora){
        var p=parametros(org,broker,evento,codigo);p.put("ahora",utc(ahora));jdbc.update("update alerta set estado='D',fecha_resolucion=:ahora where organizacion_id=:org and id_rol_broker=:broker and nb_evento=:evento and nb_codigo=:codigo and estado='A'",p);
    }
    private static Map<String,Object> parametros(long org,long broker,long evento,String codigo){var p=new HashMap<String,Object>();p.put("org",org);p.put("broker",broker);p.put("evento",evento);p.put("codigo",codigo);return p;}
    private static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
}
