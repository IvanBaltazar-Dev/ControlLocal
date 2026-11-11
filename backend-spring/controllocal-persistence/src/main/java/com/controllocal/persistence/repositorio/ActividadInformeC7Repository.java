package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Metadata mínima RF072: ámbito y período entran al SQL. No devuelve clientes, notas ni expedientes. */
@Repository
public class ActividadInformeC7Repository {
    public record Fuente(String tipo,long id,long version) { }
    public record Trayectoria(long id,long version,long encargo,String operacion,String situacion,long responsableActual,
                              Long responsableApertura,LocalDate apertura,LocalDate primeraVisita,LocalDate primeraOferta,String motivoRegistrado) { }
    public record FuenteActividad(String tipo,long id,long version,long oportunidad) { }
    public record Habilitacion(long id,long version,String operacion,LocalDate fecha,Instant registrada,Long responsableHistorico,Long revision) { }
    private final JdbcTemplate jdbc;
    /** Metadatos de cartera actual; la operación de una prospección no se infiere de su inmueble. */
    public record CasoInforme(String tipo,long id,long version,long responsable,String operacion,String situacion,LocalDate registrada,Long encargo) { }
    public record ActoInforme(String tipo,long id,long version,long oportunidad,long responsable,String estado,LocalDate fecha,Instant registrado,boolean contraoferta,LocalDate programada,LocalTime hora,String proximaAccion) { }
    public List<CasoInforme> casos(long org,Collection<Long> responsables,boolean porEncargo,Instant corte){
        if(responsables.isEmpty())return List.of();String ids=roles(responsables);
        return jdbc.query("""
          select 'ENCARGO',c.id_captacion,c.version,c.id_rol_agente,c.motivo_operacion,c.estado,(c.fecha_creacion at time zone 'America/Lima')::date,c.id_captacion
            from captacion c where c.organizacion_id=? and c.id_rol_agente=any(?::bigint[]) and c.fecha_creacion<=?
          union all
          select 'OPORTUNIDAD',o.id_oportunidad,o.version,o.id_rol_agente,c.motivo_operacion,o.estado,(o.fecha_registro at time zone 'America/Lima')::date,c.id_captacion
            from oportunidad_comercial o join captacion c on c.organizacion_id=o.organizacion_id and c.id_captacion=o.id_captacion
            where o.organizacion_id=? and %s and o.fecha_registro<=?
          union all
          select 'PROSPECCION',p.id_prospeccion,p.version,p.id_rol_agente,null,p.estado,(p.fecha_registro at time zone 'America/Lima')::date,p.id_captacion
            from prospeccion p where p.organizacion_id=? and p.id_rol_agente=any(?::bigint[]) and p.fecha_creacion<=?
          order by 1,2
          """.formatted(ambito(porEncargo)),(r,n)->new CasoInforme(r.getString(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getString(5),r.getString(6),r.getObject(7,LocalDate.class),r.getObject(8,Long.class)),org,ids,Timestamp.from(corte),org,ids,Timestamp.from(corte),org,ids,Timestamp.from(corte));
    }
    public List<ActoInforme> actos(long org,Collection<Long> responsables,boolean porEncargo,Instant corte){
        if(responsables.isEmpty())return List.of();String ids=roles(responsables);
        return jdbc.query("""
          select 'VISITA',v.id_visita,v.version,v.id_oportunidad,v.id_rol_agente,v.estado,v.fecha_hecho,v.registrado_en,false,v.fecha_visita,v.hora_visita,v.proxima_accion
            from visita v join oportunidad_comercial o on o.organizacion_id=v.organizacion_id and o.id_oportunidad=v.id_oportunidad
            join captacion c on c.organizacion_id=o.organizacion_id and c.id_captacion=o.id_captacion where v.organizacion_id=? and %s and v.registrado_en<=?
          union all
          select 'OFERTA',f.id_oferta,f.version,f.id_oportunidad,f.id_rol_registra,f.estado,f.fecha,f.fecha_creacion,(f.id_oferta_anterior is not null),null::date,null::time,null::varchar
            from oferta f join oportunidad_comercial o on o.organizacion_id=f.organizacion_id and o.id_oportunidad=f.id_oportunidad
            join captacion c on c.organizacion_id=o.organizacion_id and c.id_captacion=o.id_captacion where f.organizacion_id=? and %s and f.fecha_creacion<=?
          order by 1,2
          """.formatted(ambito(porEncargo),ambito(porEncargo)),(r,n)->new ActoInforme(r.getString(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getLong(5),r.getString(6),r.getObject(7,LocalDate.class),r.getTimestamp(8)==null?null:r.getTimestamp(8).toInstant(),r.getBoolean(9),r.getObject(10,LocalDate.class),r.getObject(11,LocalTime.class),r.getString(12)),org,ids,Timestamp.from(corte),org,ids,Timestamp.from(corte));
    }
    public ActividadInformeC7Repository(JdbcTemplate j){jdbc=j;}
    private static String roles(Collection<Long> responsables){return "{"+responsables.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))+"}";}
    private static String ambito(boolean porEncargo){return (porEncargo?"c.id_rol_agente":"o.id_rol_agente")+"=any(?::bigint[])";}
    /** Sólo fuentes escalares del Encargo autorizado. La ausencia histórica no se completa con el responsable actual. */
    public List<Habilitacion> habilitaciones(long org,Collection<Long> responsables,Instant corte){
        if(responsables.isEmpty())return List.of();
        return jdbc.query("""
            select c.id_captacion,c.version,c.motivo_operacion,h.fecha_habilitacion,h.registrada_en,
              case when firma.snapshot::jsonb->>'responsableFirmado' ~ '^[0-9]+$'
                then (firma.snapshot::jsonb->>'responsableFirmado')::bigint end,h.id_revision_encargo
            from captacion c left join encargo_primera_habilitacion h
              on h.organizacion_id=c.organizacion_id and h.id_captacion=c.id_captacion and h.registrada_en<=?
            left join revision_encargo r on r.organizacion_id=h.organizacion_id and r.id_revision_encargo=h.id_revision_encargo
            left join revision_encargo firma on firma.organizacion_id=r.organizacion_id
              and firma.id_captacion=r.id_captacion and firma.id_peticion=r.id_peticion
              and firma.actuacion in('PRESENTAR','REENVIAR') and r.actuacion='APROBAR'
              and firma.id_revision_encargo=case when r.snapshot::jsonb->>'revisionPresentacionFirma' ~ '^[0-9]+$'
                then (r.snapshot::jsonb->>'revisionPresentacionFirma')::bigint end
            where c.organizacion_id=? and c.id_rol_agente=any(?::bigint[]) and c.fecha_creacion<=?
              and (h.id_captacion is not null or(c.estado='A' and c.fecha_inicio_encargo<=(cast(? as timestamptz) at time zone 'America/Lima')::date))
            order by c.id_captacion
            """,(r,n)->new Habilitacion(r.getLong(1),r.getLong(2),r.getString(3),r.getObject(4,LocalDate.class),
                r.getTimestamp(5)==null?null:r.getTimestamp(5).toInstant(),r.getObject(6,Long.class),r.getObject(7,Long.class)),Timestamp.from(corte),org,roles(responsables),Timestamp.from(corte),Timestamp.from(corte));
    }
    public List<Fuente> fuentesPropias(long org,Collection<Long> responsables,boolean porEncargo){
        if(responsables.isEmpty())return List.of();String ids=roles(responsables);
        return jdbc.query("select 'ENCARGO',id_captacion,version from captacion where organizacion_id=? and id_rol_agente=any(?::bigint[]) union all select 'PROSPECCION',id_prospeccion,version from prospeccion where organizacion_id=? and id_rol_agente=any(?::bigint[]) union all select 'OPORTUNIDAD',o.id_oportunidad,o.version from oportunidad_comercial o join captacion c on c.organizacion_id=o.organizacion_id and c.id_captacion=o.id_captacion where o.organizacion_id=? and "+ambito(porEncargo)+" order by 1,2",(r,n)->new Fuente(r.getString(1),r.getLong(2),r.getLong(3)),org,ids,org,ids,org,ids);
    }
    /** Sólo metadata. La capa de servicio aplica además la puerta canónica de cada oportunidad. */
    public List<Trayectoria> trayectorias(long org,Collection<Long> responsables,boolean porEncargo,Instant corte){
        if(responsables.isEmpty())return List.of();String ids=roles(responsables);
        return jdbc.query("""
            select o.id_oportunidad,o.version,o.id_captacion,c.motivo_operacion,o.estado,o.id_rol_agente,
              (select (h.ficha->>'idAgente')::bigint from oportunidad_historia h
                where h.organizacion_id=o.organizacion_id and h.id_oportunidad=o.id_oportunidad
                  and h.operacion='ABRIR_OPORTUNIDAD' and h.registrado_en<=? order by h.version limit 1),
              (o.fecha_registro at time zone 'America/Lima')::date,
              (select min(v.fecha_hecho) from visita v where v.organizacion_id=o.organizacion_id and v.id_oportunidad=o.id_oportunidad
                and v.estado='R' and v.registrado_en<=? and v.fecha_hecho<=(? at time zone 'America/Lima')::date),
              (select min(f.fecha) from oferta f where f.organizacion_id=o.organizacion_id and f.id_oportunidad=o.id_oportunidad
                and f.fecha_creacion<=? and f.fecha<=(? at time zone 'America/Lima')::date),o.motivo_cierre
            from oportunidad_comercial o join captacion c on c.organizacion_id=o.organizacion_id and c.id_captacion=o.id_captacion
            where o.organizacion_id=? and o.fecha_registro<=? and %s
            order by o.id_oportunidad
            """.formatted(ambito(porEncargo)),(r,n)->new Trayectoria(r.getLong(1),r.getLong(2),r.getLong(3),r.getString(4),r.getString(5),r.getLong(6),
                r.getObject(7,Long.class),r.getObject(8,LocalDate.class),r.getObject(9,LocalDate.class),r.getObject(10,LocalDate.class),r.getString(11)),
            Timestamp.from(corte),Timestamp.from(corte),Timestamp.from(corte),Timestamp.from(corte),Timestamp.from(corte),org,Timestamp.from(corte),ids);
    }
    public Optional<FuenteActividad> fuenteActividad(long org,String tipo,long id){
        String tabla,columna;
        switch(tipo){case "VISITA"->{tabla="visita";columna="id_visita";}case "OFERTA"->{tabla="oferta";columna="id_oferta";}default->throw new IllegalArgumentException("Tipo de fuente no admitido.");}
        return jdbc.query("select "+columna+",version,id_oportunidad from "+tabla+" where organizacion_id=? and "+columna+"=?",
                (r,n)->new FuenteActividad(tipo,r.getLong(1),r.getLong(2),r.getLong(3)),org,id).stream().findFirst();
    }
}
