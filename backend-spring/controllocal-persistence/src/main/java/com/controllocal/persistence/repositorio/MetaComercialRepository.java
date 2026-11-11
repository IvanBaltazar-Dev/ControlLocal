package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Único acceso a las tablas existentes de metas/revisiones. No interpreta C/P/S/F como categorías nuevas. */
@Repository
public class MetaComercialRepository {
    public record Meta(long id,long agente,String kpi,String operacion,String definicion,int anio,int mes,int valor,
                       long version,String calendario) { }
    public record Cabecera(long id,long agente,String definicion,long version,String estado,Long reemplazadaPor) { }
    public record Revision(long id,long agente,String kpi,String operacion,String definicion,int anio,int mes,long version,
                           String origen,String estado,Integer anterior,int propuesto,String calendario,String motivo,long autor,
                           Instant fecha,Long decisor,Instant decidida,String motivoDecision,Integer aprobado,
                           Long versionMeta,Long versionResultado,Long meta,Long reemplazadaPor) { }
    private final JdbcTemplate jdbc;
    public MetaComercialRepository(JdbcTemplate j){jdbc=j;}
    private static final RowMapper<Meta> META=(r,n)->new Meta(r.getLong("id_meta"),r.getLong("id_rol_agente"),r.getString("kpi"),r.getString("operacion"),r.getString("definicion"),r.getInt("anio"),r.getInt("mes"),r.getInt("valor"),r.getLong("version"),r.getString("calendario"));
    private static Instant instante(ResultSet r,String k)throws SQLException{var t=r.getTimestamp(k);return t==null?null:t.toInstant();}
    private static final RowMapper<Revision> REV=(r,n)->new Revision(r.getLong("id_revision"),r.getLong("id_rol_agente"),r.getString("kpi"),r.getString("operacion"),r.getString("definicion"),r.getInt("anio"),r.getInt("mes"),r.getLong("version"),r.getString("origen"),r.getString("estado"),r.getObject("valor_anterior",Integer.class),r.getInt("valor_propuesto"),r.getString("calendario"),r.getString("motivo"),r.getLong("id_rol_autor"),instante(r,"fecha_creacion"),r.getObject("id_rol_decisor",Long.class),instante(r,"fecha_decision"),r.getString("motivo_decision"),r.getObject("valor_aprobado",Integer.class),r.getObject("version_meta",Long.class),r.getObject("version_resultado",Long.class),r.getObject("id_meta",Long.class),r.getObject("reemplazada_por",Long.class));
    private static String roles(Collection<Long> rs){return "{"+rs.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))+"}";}
    public List<Meta> del(long org,int anio,int mes,Collection<Long> agentes){
        if(agentes.isEmpty())return List.of();
        return jdbc.query("select * from meta_comercial where organizacion_id=? and anio=? and mes=? and id_rol_agente=any(?::bigint[]) order by id_rol_agente,kpi,operacion nulls first",META,org,anio,mes,roles(agentes));
    }
    public List<Revision> historia(long org,int anio,int mes,Collection<Long> agentes){
        if(agentes.isEmpty())return List.of();
        return jdbc.query("select * from meta_revision where organizacion_id=? and anio=? and mes=? and id_rol_agente=any(?::bigint[]) order by id_revision",REV,org,anio,mes,roles(agentes));
    }
    public List<Revision> pendientes(long org,Collection<Long> agentes){
        if(agentes.isEmpty())return List.of();
        return jdbc.query("select * from meta_revision where organizacion_id=? and estado='E' and reemplazada_por is null and id_rol_agente=any(?::bigint[]) order by id_revision",REV,org,roles(agentes));
    }
    public Optional<Meta> actual(long org,long agente,String kpi,String operacion,int anio,int mes,boolean bloquear){
        return jdbc.query("select * from meta_comercial where organizacion_id=? and id_rol_agente=? and kpi=? and operacion is not distinct from ? and anio=? and mes=?"+(bloquear?" for update":""),META,org,agente,kpi,operacion,anio,mes).stream().findFirst();
    }
    public Optional<Cabecera> cabecera(long org,long revision,boolean bloquear){
        return jdbc.query("select id_revision,id_rol_agente,definicion,version,estado,reemplazada_por from meta_revision where organizacion_id=? and id_revision=?"+(bloquear?" for update":""),(r,n)->new Cabecera(r.getLong(1),r.getLong(2),r.getString(3),r.getLong(4),r.getString(5),r.getObject(6,Long.class)),org,revision).stream().findFirst();
    }
    public Optional<Revision> revision(long org,long id){return jdbc.query("select * from meta_revision where organizacion_id=? and id_revision=?",REV,org,id).stream().findFirst();}
    public long insertar(long org,long agente,String kpi,String operacion,String definicion,int anio,int mes,int valor,String calendario,long autor,Instant fecha){
        return Objects.requireNonNull(jdbc.queryForObject("insert into meta_comercial(organizacion_id,id_rol_agente,kpi,operacion,definicion,anio,mes,valor,version,calendario,id_rol_autor,fecha_creacion) values(?,?,?,?,?,?,?,?,1,?::jsonb,?,?) returning id_meta",Long.class,org,agente,kpi,operacion,definicion,anio,mes,valor,calendario,autor,Timestamp.from(fecha)));
    }
    public int actualizar(long org,long id,long version,int valor,String calendario,long autor,Instant fecha){
        return jdbc.update("update meta_comercial set version=version+1,valor=?,calendario=?::jsonb,id_rol_autor=?,fecha_actualizacion=? where organizacion_id=? and id_meta=? and version=?",valor,calendario,autor,Timestamp.from(fecha),org,id,version);
    }
    public long siguienteRevision(){return Objects.requireNonNull(jdbc.queryForObject("select nextval(pg_get_serial_sequence('meta_revision','id_revision'))",Long.class));}
    public void insertarRevision(long id,long org,long agente,String kpi,String operacion,String definicion,int anio,int mes,String origen,String estado,Integer anterior,int propuesto,String calendario,String motivo,long autor,Instant fecha,long versionMeta,Long versionResultado,Long meta,Integer aprobado){
        boolean aplicada="A".equals(estado);
        jdbc.update("insert into meta_revision(id_revision,organizacion_id,id_rol_agente,kpi,operacion,definicion,anio,mes,version,origen,estado,valor_anterior,valor_propuesto,calendario,motivo,id_rol_autor,fecha_creacion,version_meta,version_resultado,id_meta,valor_aprobado,id_rol_decisor,fecha_decision,motivo_decision) values(?,?,?,?,?,?,?,?,1,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?,?)",id,org,agente,kpi,operacion,definicion,anio,mes,origen,estado,anterior,propuesto,calendario,motivo,autor,Timestamp.from(fecha),versionMeta,versionResultado,meta,aprobado,aplicada?autor:null,aplicada?Timestamp.from(fecha):null,aplicada?motivo:null);
    }
    public int sustituir(long org,long id,long version,long nueva){
        return jdbc.update("update meta_revision set version=version+1,reemplazada_por=? where organizacion_id=? and id_revision=? and version=? and estado='E' and reemplazada_por is null",nueva,org,id,version);
    }
    public int decidir(long org,long id,long version,String estado,Integer aprobado,long decisor,String motivo,Instant fecha,Long versionResultado,Long meta){
        return jdbc.update("update meta_revision set version=version+1,estado=?,valor_aprobado=?,id_rol_decisor=?,motivo_decision=?,fecha_decision=?,version_resultado=?,id_meta=? where organizacion_id=? and id_revision=? and version=? and estado='E' and reemplazada_por is null",estado,aprobado,decisor,motivo,Timestamp.from(fecha),versionResultado,meta,org,id,version);
    }
}
