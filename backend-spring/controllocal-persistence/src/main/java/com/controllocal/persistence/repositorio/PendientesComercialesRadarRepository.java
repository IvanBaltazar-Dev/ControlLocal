package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

/** D5: cabeceras actuales de actuaciones C2/C4/C5, sin clientes, textos privados, documentos o importes. */
@Repository
public class PendientesComercialesRadarRepository {
    public record Fuente(String motivo,String recurso,long referencia,long encargo,Long oportunidad,String operacion,
        String tipoHecho,long hecho,Long version,Instant registrada,LocalDate fechaActuacion) { }
    public record Economia(long comision,long encargo,long contexto,long revisionPacto,long evaluacion,Instant registrada,String operacion,long version) { }
    private final NamedParameterJdbcTemplate jdbc;
    public PendientesComercialesRadarRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Fuente> fuentes(long org,Collection<Long> operadores,Collection<Long> decisores){
        var ops=operadores.isEmpty()?List.of(-1L):operadores;var dec=decisores.isEmpty()?List.of(-1L):decisores;
        return jdbc.query("""
            select 'OFERTA_PENDIENTE' motivo,'OFERTA' recurso,f.id_oferta referencia,c.id_captacion encargo,o.id_oportunidad oportunidad,c.motivo_operacion operacion,
              'OFERTA' tipo_hecho,f.id_oferta hecho,f.version,f.fecha_creacion registrada,null::date fecha_actuacion
              from oferta f join oportunidad_comercial o on o.organizacion_id=f.organizacion_id and o.id_oportunidad=f.id_oportunidad
              join captacion c on c.organizacion_id=o.organizacion_id and c.id_captacion=o.id_captacion
             where f.organizacion_id=:org and o.id_rol_agente in (:ops) and o.estado='A' and f.estado='P'
            union all
            select 'EXPEDIENTE_PRESENTADO','EXPEDIENTE_OPERACION',e.id_expediente,c.id_captacion,e.id_oportunidad,c.motivo_operacion,
              'REVISION_EXPEDIENTE_OPERACION',r.id_revision_expediente,null::bigint,r.registrado_en,null::date
              from expediente_operacion e join captacion c on c.organizacion_id=e.organizacion_id and c.id_captacion=e.id_captacion
              join revision_expediente_operacion r on r.organizacion_id=e.organizacion_id and r.id_revision_expediente=e.id_revision_presentada
             where e.organizacion_id=:org and c.id_rol_agente in (:dec) and r.tipo_registro='PRESENTACION'
               and not exists(select 1 from revision_expediente_operacion v where v.organizacion_id=e.organizacion_id and v.id_expediente=e.id_expediente and v.id_presentacion=r.id_revision_expediente and v.tipo_registro='REVISION')
            union all
            select 'OBSERVACIONES_EXPEDIENTE','EXPEDIENTE_OPERACION',e.id_expediente,c.id_captacion,e.id_oportunidad,c.motivo_operacion,
              'REVISION_EXPEDIENTE_OPERACION',r.id_revision_expediente,null::bigint,r.registrado_en,null::date
              from expediente_operacion e join captacion c on c.organizacion_id=e.organizacion_id and c.id_captacion=e.id_captacion
              join revision_expediente_operacion r on r.organizacion_id=e.organizacion_id and r.id_revision_expediente=e.id_revision_evaluada
             where e.organizacion_id=:org and c.id_rol_agente in (:ops) and r.tipo_registro='REVISION' and r.decision='OBSERVAR' and r.id_presentacion=e.id_revision_presentada
            union all
            select 'HITO_INFORMADO','HITO_CONTRACTUAL',h.id_hito,c.id_captacion,e.id_oportunidad,c.motivo_operacion,
              'REVISION_HITO_CONTRACTUAL',r.id_revision_hito,null::bigint,r.registrado_en,null::date
              from hito_contractual h join expediente_operacion e on e.organizacion_id=h.organizacion_id and e.id_expediente=h.id_expediente
              join captacion c on c.organizacion_id=e.organizacion_id and c.id_captacion=e.id_captacion
              join revision_hito_contractual r on r.organizacion_id=h.organizacion_id and r.id_revision_hito=h.id_revision_informada
             where h.organizacion_id=:org and c.id_rol_agente in (:dec) and h.id_revision_confirmada is null and r.tipo_registro='INFORMACION'
            union all
            select 'ADENDA_PENDIENTE','ENCARGO',c.id_captacion,c.id_captacion,null::bigint,c.motivo_operacion,
              'REVISION_ENCARGO',r.id_revision_encargo,null::bigint,r.registrada_en,null::date
              from revision_encargo r join captacion c on c.organizacion_id=r.organizacion_id and c.id_captacion=r.id_captacion
             where r.organizacion_id=:org and c.id_rol_agente in (:dec) and r.actuacion='PROPONER_ADENDA' and c.estado in('P','A')
               and not exists(select 1 from revision_encargo n where n.organizacion_id=r.organizacion_id and n.id_captacion=r.id_captacion and n.id_revision_encargo>r.id_revision_encargo and n.snapshot::jsonb#>>'{adenda,propuesta}'=r.id_revision_encargo::text)
            union all
            select 'FIN_ENCARGO_SOLICITADO','ENCARGO',c.id_captacion,c.id_captacion,null::bigint,c.motivo_operacion,
              'SOLICITUD_FIN_ENCARGO',s.id_solicitud_finalizacion_encargo,s.version,s.registrada_en,null::date
              from solicitud_finalizacion_encargo s join captacion c on c.organizacion_id=s.organizacion_id and c.id_captacion=s.id_captacion
             where s.organizacion_id=:org and c.id_rol_agente in (:dec) and s.estado='P' and c.estado in('P','A')
            union all
            select 'CONTINUIDAD_ENCARGO','ENCARGO',c.id_captacion,c.id_captacion,null::bigint,c.motivo_operacion,
              'REVISION_ENCARGO',r.id_revision_encargo,null::bigint,r.registrada_en,c.fecha_fin_encargo
              from captacion c join revision_encargo r on r.organizacion_id=c.organizacion_id and r.id_revision_encargo=c.id_revision_aplicable
             where c.organizacion_id=:org and c.id_rol_agente in (:ops) and c.estado='A' and c.fecha_fin_encargo is not null
            order by registrada,hecho
            """,Map.of("org",org,"ops",ops,"dec",dec),(r,n)->new Fuente(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4),(Long)r.getObject(5),r.getString(6),r.getString(7),r.getLong(8),(Long)r.getObject(9),r.getTimestamp(10).toInstant(),r.getObject(11,LocalDate.class)));
    }
    public List<Economia> economia(long org,Collection<Long> decisores){
        if(decisores.isEmpty())return List.of();return jdbc.query("""
            select l.id_comision_liquidacion,c.id_captacion,l.id_contexto_economico,l.id_revision_pacto,e.id_comision_evaluacion,e.registrado_en,c.motivo_operacion,l.version
              from comision_liquidacion l join captacion c on c.organizacion_id=l.organizacion_id and c.id_captacion=l.id_captacion
              join comision_evaluacion e on e.organizacion_id=l.organizacion_id and e.id_comision_evaluacion=l.id_ultima_evaluacion
             where l.organizacion_id=:org and c.id_rol_agente in (:dec) and l.id_contexto_economico is not null
             order by e.registrado_en,l.id_comision_liquidacion
            """,Map.of("org",org,"dec",decisores),(r,n)->new Economia(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getLong(5),r.getTimestamp(6).toInstant(),r.getString(7),r.getLong(8)));
    }
}
