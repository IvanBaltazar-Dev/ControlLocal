package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** RF003: origen de contacto nuevo; la fila no sustituye pertenencia/autoridad actuales. */
@Repository
public class AutoriaContactoContextualRepository {
    public record Origen(long persona,long autor,long personaAutor) { }
    private final JdbcTemplate jdbc;
    public AutoriaContactoContextualRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void registrar(long org,long persona,long autor,Instant registrado,String procedencia){
        jdbc.update("""
            insert into contacto_autoria_contextual(organizacion_id,id_persona,id_rol_autor,registrado_en,procedencia_json)
            values(?,?,?,?,?) on conflict(organizacion_id,id_persona,id_rol_autor) do nothing
            """,org,persona,autor,java.sql.Timestamp.from(registrado),procedencia);
    }
    public List<Origen> origenes(long org,long persona){
        return jdbc.query("""
            select a.id_persona,a.id_rol_autor,r.id_persona from contacto_autoria_contextual a
            join persona_rol r on r.organizacion_id=a.organizacion_id and r.id_persona_rol=a.id_rol_autor
            where a.organizacion_id=? and a.id_persona=? and a.retirado_en is null
            """,(r,n)->new Origen(r.getLong(1),r.getLong(2),r.getLong(3)),org,persona);
    }
}
