package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;

/** Snapshots append-only del mismo agregado; no permite actualizar ni borrar. */
@Repository
public class HistoriaRequerimientoRepository {
    public record ContactoOriginal(Long id,Long version,Long persona) { }
    private final JdbcTemplate jdbc;
    public HistoriaRequerimientoRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public void anexar(long org,long id,long version,long actor,Instant registrado,
                      String snapshot,String procedencia) {
        jdbc.update("""
            insert into requerimiento_version(organizacion_id,id_requerimiento,version,id_rol_actor,
                registrado_en,snapshot_json,procedencia_json) values(?,?,?,?,?,?,?)
            """,org,id,version,actor,java.sql.Timestamp.from(registrado),snapshot,procedencia);
    }
    public Optional<String> version(long org,long id,long version) {
        return jdbc.query("""
            select snapshot_json from requerimiento_version
            where organizacion_id=? and id_requerimiento=? and version=?
            """,(r,n)->r.getString(1),org,id,version).stream().findFirst();
    }
    /** Sólo metadatos originales, nunca criterios, nombres ni teléfonos del snapshot. */
    public Optional<ContactoOriginal> contactoOriginal(long org,long id,long version){
        return jdbc.query("""
            select (snapshot_json::jsonb->>'id')::bigint,(snapshot_json::jsonb->>'version')::bigint,
                   (snapshot_json::jsonb->>'idPersonaInteresada')::bigint
            from requerimiento_version where organizacion_id=? and id_requerimiento=? and version=?
            """,(r,n)->new ContactoOriginal(r.getObject(1,Long.class),r.getObject(2,Long.class),r.getObject(3,Long.class)),org,id,version).stream().findFirst();
    }
    public Optional<String> alCorte(long org,long id,Instant corte) {
        return jdbc.query("""
            select snapshot_json from requerimiento_version
            where organizacion_id=? and id_requerimiento=? and registrado_en<=?
            order by registrado_en desc,version desc limit 1
            """,(r,n)->r.getString(1),org,id,java.sql.Timestamp.from(corte)).stream().findFirst();
    }
}
