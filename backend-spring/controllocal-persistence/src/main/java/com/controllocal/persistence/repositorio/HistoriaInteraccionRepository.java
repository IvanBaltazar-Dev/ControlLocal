package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

/** Historia privada de una comunicación. La metadata permite autorizar antes de cargar contenido. */
@Repository
public class HistoriaInteraccionRepository {
    public record Metadata(long version,String accesoJson) { }
    public record Version(long version,Instant registradoEn,String operacion,String motivo,String snapshotJson,String procedenciaJson) { }
    public record Clave(long id,long version) { }
    public record Acceso(long id,long version,String accesoJson) { }
    private final JdbcTemplate jdbc;
    public HistoriaInteraccionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Acceso> accesos(long org,Collection<Clave> claves){
        if(claves.isEmpty())return List.of();var lista=List.copyOf(new java.util.LinkedHashSet<>(claves));
        String ids=lista.stream().map(c->Long.toString(c.id())).collect(java.util.stream.Collectors.joining(",","{","}"));
        String versiones=lista.stream().map(c->Long.toString(c.version())).collect(java.util.stream.Collectors.joining(",","{","}"));
        return jdbc.query("""
            select h.id_interaccion,h.version,(h.snapshot_json::jsonb->'personasOriginalesPorCampo')::text
            from interaccion_version h join unnest(?::bigint[],?::bigint[]) k(id,version)
              on h.id_interaccion=k.id and h.version=k.version where h.organizacion_id=?
            """,(r,n)->new Acceso(r.getLong(1),r.getLong(2),r.getString(3)),ids,versiones,org);
    }
    public void anexar(long org,long id,long version,long actor,Instant registrado,String operacion,String motivo,String snapshot,String procedencia){
        jdbc.update("""
            insert into interaccion_version(organizacion_id,id_interaccion,version,id_rol_actor,registrado_en,
                operacion,motivo,snapshot_json,procedencia_json) values(?,?,?,?,?,?,?,?,?)
            """,org,id,version,actor,java.sql.Timestamp.from(registrado),operacion,motivo,snapshot,procedencia);
    }
    public List<Metadata> metadatos(long org,long id){
        return jdbc.query("""
            select version,(snapshot_json::jsonb->'personasOriginalesPorCampo')::text as acceso
            from interaccion_version where organizacion_id=? and id_interaccion=? order by version
            """,(r,n)->new Metadata(r.getLong(1),r.getString(2)),org,id);
    }
    public Optional<String> acceso(long org,long id,long version){
        return jdbc.query("""
            select (snapshot_json::jsonb->'personasOriginalesPorCampo')::text from interaccion_version
            where organizacion_id=? and id_interaccion=? and version=?
            """,(r,n)->r.getString(1),org,id,version).stream().findFirst();
    }
    public Optional<Version> version(long org,long id,long version){
        return jdbc.query("""
            select version,registrado_en,operacion,motivo,snapshot_json,procedencia_json
            from interaccion_version where organizacion_id=? and id_interaccion=? and version=?
            """,(r,n)->new Version(r.getLong(1),r.getTimestamp(2).toInstant(),r.getString(3),r.getString(4),r.getString(5),r.getString(6)),org,id,version).stream().findFirst();
    }
}
