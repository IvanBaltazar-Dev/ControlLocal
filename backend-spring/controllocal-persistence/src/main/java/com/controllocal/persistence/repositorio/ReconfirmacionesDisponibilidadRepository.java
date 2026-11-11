package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Sólo solicitudes y referencias; no copia el hecho de confirmación ni documentos privados. */
@Repository
public class ReconfirmacionesDisponibilidadRepository {
    public record Ambito(long organizacion,String tipo,long recurso,String operacion,String aspecto,String contexto) {
        public String claveLock(){return organizacion+"/"+tipo+"/"+recurso+"/"+operacion+"/"+aspecto+"/"+contexto;}
    }
    public record Solicitud(UUID id,Ambito ambito,long confirmacion,Instant base,Instant disponible,
        Instant recordatorio,Instant vence,long responsable,String estado,LocalDate revisarEn,String motivo,
        long version,Instant recordatorioEmitido,Instant escalacionEmitida) { }
    public record Destinatario(long organizacion,long persona,long rol) { }
    public record Recurso(String tipo,long id) { }
    private final NamedParameterJdbcTemplate jdbc;
    public ReconfirmacionesDisponibilidadRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Recurso> recursos(long org,long rol){
        return jdbc.query("""
            select distinct 'ENCARGO' tipo,c.id_captacion id from captacion c
             where c.organizacion_id=:org and c.id_rol_agente=:rol
               and (exists(select 1 from confirmacion_disponibilidad_contextual f where f.organizacion_id=c.organizacion_id and f.tipo_recurso='ENCARGO' and f.id_recurso=c.id_captacion)
                 or exists(select 1 from solicitud_reconfirmacion s where s.organizacion_id=c.organizacion_id and s.tipo_recurso='ENCARGO' and s.recurso_id=c.id_captacion))
            union
            select 'DEMANDA',d.id_requerimiento from requerimiento_cliente d
             where d.organizacion_id=:org and d.id_rol_responsable=:rol
               and (exists(select 1 from confirmacion_disponibilidad_contextual f where f.organizacion_id=d.organizacion_id and f.tipo_recurso='DEMANDA' and f.id_recurso=d.id_requerimiento)
                 or exists(select 1 from solicitud_reconfirmacion s where s.organizacion_id=d.organizacion_id and s.tipo_recurso='DEMANDA' and s.recurso_id=d.id_requerimiento))
            order by tipo,id
            """,Map.of("org",org,"rol",rol),(r,n)->new Recurso(r.getString(1),r.getLong(2)));
    }
    /** Pagina destinatarios reales; el servicio vuelve a comprobar su atribución actual. */
    public List<Destinatario> destinatarios(long despues,int tamano){
        return jdbc.query("""
            select a.organizacion_id,p.id_persona,a.id_persona_rol from detalle_agente a
              join persona_rol p on p.organizacion_id=a.organizacion_id and p.id_persona_rol=a.id_persona_rol
             where a.id_persona_rol>:despues and (
              exists(select 1 from captacion c join confirmacion_disponibilidad_contextual f on f.organizacion_id=c.organizacion_id and f.tipo_recurso='ENCARGO' and f.id_recurso=c.id_captacion where c.organizacion_id=a.organizacion_id and c.id_rol_agente=a.id_persona_rol)
              or exists(select 1 from requerimiento_cliente d join confirmacion_disponibilidad_contextual f on f.organizacion_id=d.organizacion_id and f.tipo_recurso='DEMANDA' and f.id_recurso=d.id_requerimiento where d.organizacion_id=a.organizacion_id and d.id_rol_responsable=a.id_persona_rol)
              or exists(select 1 from solicitud_reconfirmacion s where s.organizacion_id=a.organizacion_id and s.rol_responsable=a.id_persona_rol))
             order by a.id_persona_rol limit :tamano
            """,Map.of("despues",despues,"tamano",tamano),(r,n)->new Destinatario(r.getLong(1),r.getLong(2),r.getLong(3)));
    }
    public void bloquearRecurso(long org,String tipo,long id){
        String tabla=switch(tipo){case "ENCARGO"->"captacion";case "DEMANDA"->"requerimiento_cliente";default->throw new IllegalArgumentException("Recurso D2");};
        String pk="ENCARGO".equals(tipo)?"id_captacion":"id_requerimiento";
        jdbc.query("select "+pk+" from "+tabla+" where organizacion_id=:org and "+pk+"=:id for update",Map.of("org",org,"id",id),(r,n)->r.getLong(1));
    }
    public List<Solicitud> delRecurso(long org,String tipo,long id){
        return jdbc.query("select * from solicitud_reconfirmacion where organizacion_id=:org and tipo_recurso=:tipo and recurso_id=:id order by disponible_en,id for update",
            Map.of("org",org,"tipo",tipo,"id",id),ReconfirmacionesDisponibilidadRepository::fila);
    }
    public Optional<Solicitud> cabecera(long org,UUID id){
        return jdbc.query("select * from solicitud_reconfirmacion where organizacion_id=:org and id=:id",Map.of("org",org,"id",id),ReconfirmacionesDisponibilidadRepository::fila).stream().findFirst();
    }
    public Solicitud insertar(Solicitud s){
        var p=parametros(s);jdbc.update("""
            insert into solicitud_reconfirmacion(id,organizacion_id,tipo_recurso,recurso_id,operacion,aspecto,contexto,
             confirmacion_base,base_frescura,disponible_en,recordatorio_en,atencion_hasta,rol_responsable,estado)
            values(:id,:org,:tipo,:recurso,:operacion,:aspecto,:contexto,:baseId,:base,:disponible,:recordatorio,:vence,:rol,'PENDIENTE')
            on conflict(organizacion_id,tipo_recurso,recurso_id,operacion,aspecto,contexto,confirmacion_base) do nothing
            """,p);
        return delRecurso(s.ambito().organizacion(),s.ambito().tipo(),s.ambito().recurso()).stream()
            .filter(x->x.ambito().equals(s.ambito())&&x.confirmacion()==s.confirmacion()).findFirst().orElseThrow();
    }
    public void cambiar(Solicitud s,String estado,LocalDate revisar,String motivo,long rol,Instant ahora,Long actor,String contenido){
        var p=parametros(s);p.put("estado",estado);p.put("revisar",revisar);p.put("motivo",motivo);p.put("nuevoRol",rol);
        int n=jdbc.update("""
            update solicitud_reconfirmacion set estado=:estado,revisar_en=:revisar,motivo=:motivo,
             rol_responsable=:nuevoRol,version=version+1 where organizacion_id=:org and id=:id and version=:version
            """,p);if(n!=1)throw new IllegalStateException("La solicitud cambió bajo el lock de su ámbito.");
        historia(s.id(),s.version()+1,estado,actor,ahora,contenido);
    }
    public boolean marcar(Solicitud s,boolean escalacion,Instant ahora){
        var p=parametros(s);p.put("ahora",utc(ahora));String columna=escalacion?"escalacion_emitida_en":"recordatorio_emitido_en";
        return jdbc.update("update solicitud_reconfirmacion set "+columna+"=:ahora where organizacion_id=:org and id=:id and estado='PENDIENTE' and "+columna+" is null",p)==1;
    }
    public void historia(UUID id,long version,String hecho,Long actor,Instant ahora,String contenido){
        var p=new HashMap<String,Object>();p.put("id",id);p.put("version",version);p.put("hecho",hecho);p.put("actor",actor);p.put("ahora",utc(ahora));p.put("contenido",contenido);
        jdbc.update("insert into actuacion_reconfirmacion(solicitud,version,hecho,rol_actor,registrado_en,contenido) values(:id,:version,:hecho,:actor,:ahora,cast(:contenido as jsonb)) on conflict do nothing",p);
    }
    private static Map<String,Object> parametros(Solicitud s){
        var a=s.ambito();var p=new HashMap<String,Object>();p.put("id",s.id());p.put("org",a.organizacion());p.put("tipo",a.tipo());p.put("recurso",a.recurso());p.put("operacion",a.operacion());p.put("aspecto",a.aspecto());p.put("contexto",a.contexto());p.put("baseId",s.confirmacion());p.put("base",utc(s.base()));p.put("disponible",utc(s.disponible()));p.put("recordatorio",utc(s.recordatorio()));p.put("vence",utc(s.vence()));p.put("rol",s.responsable());p.put("version",s.version());return p;
    }
    private static Solicitud fila(ResultSet r,int n)throws SQLException{
        return new Solicitud(r.getObject("id",UUID.class),new Ambito(r.getLong("organizacion_id"),r.getString("tipo_recurso"),r.getLong("recurso_id"),r.getString("operacion"),r.getString("aspecto"),r.getString("contexto")),r.getLong("confirmacion_base"),instant(r,"base_frescura"),instant(r,"disponible_en"),instant(r,"recordatorio_en"),instant(r,"atencion_hasta"),r.getLong("rol_responsable"),r.getString("estado"),r.getObject("revisar_en",LocalDate.class),r.getString("motivo"),r.getLong("version"),instant(r,"recordatorio_emitido_en"),instant(r,"escalacion_emitida_en"));
    }
    private static Instant instant(ResultSet r,String c)throws SQLException{return r.getTimestamp(c)==null?null:r.getTimestamp(c).toInstant();}
    private static OffsetDateTime utc(Instant i){return i==null?null:i.atOffset(ZoneOffset.UTC);}
}
