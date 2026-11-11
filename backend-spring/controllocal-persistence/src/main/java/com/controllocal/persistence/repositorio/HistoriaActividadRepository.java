package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** Snapshots de los mismos agregados C4. Sólo anexar/leer; nombres SQL de una lista técnica cerrada. */
@Repository
public class HistoriaActividadRepository {
    public enum Recurso {
        OPORTUNIDAD("oportunidad_version"),INTERACCION("interaccion_version"),VISITA("visita_version");
        private final String tabla;
        Recurso(String tabla){this.tabla=tabla;}
    }
    public record Version(long version,Instant registradoEn,String operacion,String motivo,String snapshot) { }
    private final JdbcTemplate jdbc;
    public HistoriaActividadRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void anexar(Recurso recurso,long org,long id,long version,long actor,Instant registrado,
            String operacion,String motivo,String snapshot,String procedencia){
        jdbc.update("insert into "+recurso.tabla+"(organizacion_id,id_recurso,version,id_rol_actor,registrado_en,operacion,motivo,snapshot_json,procedencia_json) values(?,?,?,?,?,?,?,?,?)",
                org,id,version,actor,Timestamp.from(registrado),operacion,motivo,snapshot,procedencia);
    }
    public List<Version> historia(Recurso recurso,long org,long id){
        return jdbc.query("select version,registrado_en,operacion,motivo,snapshot_json from "+recurso.tabla
                +" where organizacion_id=? and id_recurso=? order by version",
                (r,n)->new Version(r.getLong(1),r.getTimestamp(2).toInstant(),r.getString(3),r.getString(4),r.getString(5)),org,id);
    }
    public Optional<String> alCorte(Recurso recurso,long org,long id,Instant corte){
        return jdbc.query("select snapshot_json from "+recurso.tabla
                +" where organizacion_id=? and id_recurso=? and registrado_en<=? order by registrado_en desc,version desc limit 1",
                (r,n)->r.getString(1),org,id,Timestamp.from(corte)).stream().findFirst();
    }
    public List<String> porOportunidad(Recurso recurso,long org,long oportunidad,Instant corte){
        if(recurso==Recurso.OPORTUNIDAD)throw new IllegalArgumentException("Consulta el agregado por su referencia.");
        return jdbc.query("select snapshot_json from (select distinct on(id_recurso) id_recurso,snapshot_json from "
                +recurso.tabla+" where organizacion_id=? and (snapshot_json::jsonb->>'idOportunidad')::bigint=?"
                +" and registrado_en<=? order by id_recurso,registrado_en desc,version desc) s order by id_recurso",
                (r,n)->r.getString(1),org,oportunidad,Timestamp.from(corte));
    }
}
