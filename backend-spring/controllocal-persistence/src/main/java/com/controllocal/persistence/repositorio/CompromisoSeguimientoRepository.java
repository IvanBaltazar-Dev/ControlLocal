package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Constancias del compromiso R27; no mantiene otro calendario ni deduce actuaciones. */
@Repository
public class CompromisoSeguimientoRepository {
    public record AnteriorSinTipo(long seguimiento,long prospeccion,long version,java.time.LocalDate plazo,Instant planificada) { }
    public record Fila(long id,UUID referencia,long seguimiento,long prospeccion,long responsableHistorico,long version,String contenido,Instant registrada,Instant actualizada) { }
    private final JdbcTemplate jdbc;
    public CompromisoSeguimientoRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static final String COLUMNAS="c.id_compromiso,c.referencia,c.id_seguimiento,c.id_prospeccion,c.id_responsable_historico,c.version,c.contenido_json,c.registrado_en,c.actualizado_en";
    private static final String CABECERA=COLUMNAS.replace("c.contenido_json","jsonb_build_object('versionSeguimiento',c.contenido_json::jsonb->'versionSeguimiento')::text");
    public Optional<Fila> cabeceraPorId(long org,long id){return jdbc.query("select "+CABECERA+" from compromiso_seguimiento c where c.organizacion_id=? and c.id_compromiso=?",CompromisoSeguimientoRepository::fila,org,id).stream().findFirst();}
    private static Fila fila(ResultSet r,int n)throws SQLException{return new Fila(r.getLong(1),r.getObject(2,UUID.class),r.getLong(3),r.getLong(4),r.getLong(5),r.getLong(6),r.getString(7),r.getTimestamp(8).toInstant(),r.getTimestamp(9).toInstant());}
    public Optional<Fila> obtener(long org,UUID ref,boolean bloquear){return jdbc.query("select "+COLUMNAS+" from compromiso_seguimiento c where c.organizacion_id=? and c.referencia=?"+(bloquear?" for update":""),CompromisoSeguimientoRepository::fila,org,ref).stream().findFirst();}
    public Optional<Fila> cabecera(long org,UUID ref,boolean bloquear){return jdbc.query("select "+CABECERA+" from compromiso_seguimiento c where c.organizacion_id=? and c.referencia=?"+(bloquear?" for update of c":""),CompromisoSeguimientoRepository::fila,org,ref).stream().findFirst();}
    public List<Fila> deProspeccion(long org,long id){return jdbc.query("select "+CABECERA+" from compromiso_seguimiento c where c.organizacion_id=? and c.id_prospeccion=? order by c.id_compromiso",CompromisoSeguimientoRepository::fila,org,id);}
    public boolean tienePendiente(long org,long seguimiento){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from compromiso_seguimiento c where c.organizacion_id=? and c.id_seguimiento=? and nullif(c.contenido_json::jsonb->'resolucion','null'::jsonb) is null and jsonb_array_length(c.contenido_json::jsonb->'actuaciones')=0)",Boolean.class,org,seguimiento));}
    public String contenidoVersion(long org,long id,long version){return jdbc.query("select contenido_json from compromiso_seguimiento_version where organizacion_id=? and id_compromiso=? and version=?",(r,n)->r.getString(1),org,id,version).stream().findFirst().orElseThrow(()->new IllegalStateException("Falta la constancia de la versión del compromiso."));}
    public Optional<Fila> cabeceraVersion(long org,UUID ref,long version){return jdbc.query("select c.id_compromiso,c.referencia,c.id_seguimiento,c.id_prospeccion,c.id_responsable_historico,h.version,jsonb_build_object('versionSeguimiento',h.contenido_json::jsonb->'versionSeguimiento')::text,c.registrado_en,h.registrado_en from compromiso_seguimiento c join compromiso_seguimiento_version h on h.organizacion_id=c.organizacion_id and h.id_compromiso=c.id_compromiso where c.organizacion_id=? and c.referencia=? and h.version=?",CompromisoSeguimientoRepository::fila,org,ref,version).stream().findFirst();}
    public Fila crear(long org,long seguimiento,long prospeccion,long responsable,String contenido,Instant ahora){return jdbc.query("insert into compromiso_seguimiento(organizacion_id,id_seguimiento,id_prospeccion,referencia,id_responsable_historico,contenido_json,registrado_en,actualizado_en) values(?,?,?,?,?,?,?,?) returning "+COLUMNAS.replace("c.",""),CompromisoSeguimientoRepository::fila,org,seguimiento,prospeccion,UUID.randomUUID(),responsable,contenido,Timestamp.from(ahora),Timestamp.from(ahora)).getFirst();}
    public boolean actualizar(long org,Fila f,String contenido,Instant ahora){return jdbc.update("update compromiso_seguimiento set contenido_json=?,version=version+1,actualizado_en=? where organizacion_id=? and id_compromiso=? and version=?",contenido,Timestamp.from(ahora),org,f.id(),f.version())==1;}
    public void version(long org,Fila f,long actor,Instant ahora){jdbc.update("insert into compromiso_seguimiento_version(organizacion_id,id_compromiso,version,contenido_json,id_rol_actor,registrado_en) values(?,?,?,?,?,?)",org,f.id(),f.version(),f.contenido(),actor,Timestamp.from(ahora));}
    public List<Fila> alCorte(long org,Collection<Long> roles,Collection<Long> responsablesHistoricos,Instant corte){
        if(roles.isEmpty()||responsablesHistoricos!=null&&responsablesHistoricos.isEmpty())return List.of();
        String ids=roles.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String historicos=responsablesHistoricos==null?null:responsablesHistoricos.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",","{","}"));
        return jdbc.query("""
            select c.id_compromiso,c.referencia,c.id_seguimiento,c.id_prospeccion,c.id_responsable_historico,
              h.version,jsonb_build_object('versionSeguimiento',h.contenido_json::jsonb->'versionSeguimiento')::text,c.registrado_en,h.registrado_en
            from compromiso_seguimiento c join prospeccion p on p.organizacion_id=c.organizacion_id and p.id_prospeccion=c.id_prospeccion
            join lateral(select v.version,v.contenido_json,v.registrado_en from compromiso_seguimiento_version v
              where v.organizacion_id=c.organizacion_id and v.id_compromiso=c.id_compromiso and v.registrado_en<=?
              order by v.version desc limit 1) h on true
            where c.organizacion_id=? and p.id_rol_agente=any(?::bigint[]) and (?::bigint[] is null or c.id_responsable_historico=any(?::bigint[]))
            order by c.id_compromiso
            """,CompromisoSeguimientoRepository::fila,Timestamp.from(corte),org,"{"+ids+"}",historicos,historicos);
    }
    /** Un plan expreso anterior no recibe una actividad inferida desde su texto. */
    public List<AnteriorSinTipo> anterioresSinTipo(long org,Collection<Long> roles,Instant corte){
        if(roles.isEmpty())return List.of();String ids=roles.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",","{","}"));
        return jdbc.query("""
            select s.id_seguimiento,s.id_prospeccion,s.version,s.proxima_fecha,
              (s.contexto_json::jsonb->>'planificadoEn')::timestamptz
            from seguimiento_prospeccion s join prospeccion p on p.organizacion_id=s.organizacion_id and p.id_prospeccion=s.id_prospeccion
            where s.organizacion_id=? and p.id_rol_agente=any(?::bigint[]) and s.actualizado_en<=?
              and s.proxima_fecha is not null and s.contexto_json::jsonb->>'origenExplicito' in('INTENCION_PROFESIONAL','ACUERDO_EXTERNO')
              and s.contexto_json::jsonb->>'planificadoEn' is not null
              and not exists(select 1 from compromiso_seguimiento c where c.organizacion_id=s.organizacion_id and c.id_seguimiento=s.id_seguimiento and c.registrado_en<=?)
            order by s.id_seguimiento
            """,(r,n)->new AnteriorSinTipo(r.getLong(1),r.getLong(2),r.getLong(3),r.getDate(4).toLocalDate(),r.getTimestamp(5).toInstant()),org,ids,Timestamp.from(corte),Timestamp.from(corte));
    }
}
