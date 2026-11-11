package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Persistencia de RF046–049: no contiene permisos ni reglas comerciales. */
@Repository
public class MesaNegociacionRepository {
    private final JdbcTemplate jdbc;
    public MesaNegociacionRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    /** El consumidor ya bloqueó Oportunidad; éste es el siguiente bloqueo canónico. */
    public long bloquear(long organizacion,long oportunidad) {
        jdbc.update("INSERT INTO mesa_negociacion(organizacion_id,id_oportunidad) VALUES (?,?) ON CONFLICT DO NOTHING",organizacion,oportunidad);
        return jdbc.queryForObject("SELECT version FROM mesa_negociacion WHERE organizacion_id=? AND id_oportunidad=? FOR UPDATE",Long.class,organizacion,oportunidad);
    }
    public long version(long organizacion,long oportunidad) {
        var encontrados=jdbc.query("SELECT version FROM mesa_negociacion WHERE organizacion_id=? AND id_oportunidad=?",(r,n)->r.getLong(1),organizacion,oportunidad);
        return encontrados.isEmpty()?0:encontrados.getFirst();
    }
    public void avanzar(long organizacion,long oportunidad,long observada) {
        int filas=jdbc.update("UPDATE mesa_negociacion SET version=version+1 WHERE organizacion_id=? AND id_oportunidad=? AND version=?",organizacion,oportunidad,observada);
        if(filas!=1) throw new org.springframework.dao.OptimisticLockingFailureException("Cambió la versión observada de la mesa");
    }
    public void propuesta(long organizacion,long oferta,String contenido,Instant fecha) {
        jdbc.update("INSERT INTO oferta_propuesta(organizacion_id,id_oferta,contenido,registrada_en) VALUES (?,?,CAST(? AS jsonb),?)",organizacion,oferta,contenido,utc(fecha));
    }
    public Optional<String> propuesta(long organizacion,long oferta) {
        return jdbc.query("SELECT contenido::text FROM oferta_propuesta WHERE organizacion_id=? AND id_oferta=?",(r,n)->r.getString(1),organizacion,oferta).stream().findFirst();
    }
    public void historia(long organizacion,long oferta,long versionOferta,long oportunidad,long versionMesa,String ficha,Instant fecha) {
        jdbc.update("INSERT INTO oferta_historia(organizacion_id,id_oferta,version_oferta,id_oportunidad,version_mesa,ficha,registrada_en) VALUES (?,?,?,?,?,CAST(? AS jsonb),?)",organizacion,oferta,versionOferta,oportunidad,versionMesa,ficha,utc(fecha));
    }
    public List<String> historia(long organizacion,long oportunidad,Instant corte) {
        return jdbc.query("""
            SELECT ficha::text FROM (
              SELECT DISTINCT ON(id_oferta) id_oferta,version_oferta,ficha
              FROM oferta_historia WHERE organizacion_id=? AND id_oportunidad=? AND registrada_en<=?
              ORDER BY id_oferta,version_oferta DESC
            ) h ORDER BY id_oferta
            """,(r,n)->r.getString(1),organizacion,oportunidad,utc(corte));
    }
    public long acuerdo(long organizacion,long oferta,long versionOferta,String contenido,Instant fecha) {
        return jdbc.queryForObject("INSERT INTO acuerdo_aceptado(organizacion_id,id_oferta,version_oferta,contenido,registrada_en) VALUES (?,?,?,CAST(? AS jsonb),?) RETURNING id_acuerdo",Long.class,organizacion,oferta,versionOferta,contenido,utc(fecha));
    }
    public record AcuerdoGuardado(long id,long version,long oferta,long versionOferta,String contenido,Instant registradoEn) { }
    public Optional<AcuerdoGuardado> acuerdoExacto(long org,long id,long version) {
        return jdbc.query("""
            SELECT id_acuerdo,version,id_oferta,version_oferta,contenido::text,registrada_en
            FROM acuerdo_aceptado WHERE organizacion_id=? AND id_acuerdo=? AND version=?
            """,(r,n)->new AcuerdoGuardado(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getString(5),
                r.getObject(6,OffsetDateTime.class).toInstant()),org,id,version).stream().findFirst();
    }
    public Optional<AcuerdoGuardado> acuerdoDeLaMesa(long org,long oportunidad,Instant corte){
        var filas=jdbc.query("""
            SELECT a.id_acuerdo,a.version,a.id_oferta,a.version_oferta,a.contenido::text,a.registrada_en
            FROM acuerdo_aceptado a JOIN oferta o ON o.organizacion_id=a.organizacion_id AND o.id_oferta=a.id_oferta
            WHERE a.organizacion_id=? AND o.id_oportunidad=? AND a.registrada_en<=? ORDER BY a.id_acuerdo
            """,(r,n)->new AcuerdoGuardado(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getString(5),
                r.getObject(6,OffsetDateTime.class).toInstant()),org,oportunidad,utc(corte));
        if(filas.size()>1)throw new IllegalStateException("La mesa conserva acuerdos incompatibles.");
        return filas.stream().findFirst();
    }
    private static OffsetDateTime utc(Instant instante) { return instante.atOffset(ZoneOffset.UTC); }
}
