package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.LinkedHashMap;

/** Versiones inmutables de un anuncio; la consulta siempre incluye organización y recurso. */
@Repository
public class HistoriaPublicacionRepository {
    private final JdbcTemplate jdbc;
    public HistoriaPublicacionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Version(long numero,String actuacion,Instant registradaEn,String contenido,String motivo) { }
    /** Sólo metadatos de referencias de Encargos que el caso de uso ya autorizó; una consulta por lote. */
    public Map<Long,Long> versionesComposiciones(long organizacion,Collection<Long> referencias){
        if(referencias.isEmpty())return Map.of();var versiones=new LinkedHashMap<Long,Long>();
        new NamedParameterJdbcTemplate(jdbc).query("""
            SELECT id_composicion_encargo,version_contenido FROM composicion_encargo
            WHERE organizacion_id=:org AND id_composicion_encargo IN (:ids)
            """,Map.of("org",organizacion,"ids",referencias),rs->{versiones.put(rs.getLong(1),rs.getLong(2));});
        return versiones;
    }
    public Long versionComposicion(long organizacion,Long referencia){
        if(referencia==null)return null;Long version=versionesComposiciones(organizacion,List.of(referencia)).get(referencia);
        if(version==null)throw new IllegalStateException("Falta la versión de composición vinculada al Encargo.");return version;
    }
    public void guardar(long organizacion,long anuncio,long version,long actor,String accion,
                        Instant fecha,String contenido,String origen,String evaluacion,String motivo){
        jdbc.update("""
            INSERT INTO version_publicacion(organizacion_id,id_publicacion,version,actor_rol,actuacion,
              registrada_en,contenido,procedencia,evaluacion,motivo)
            VALUES (?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb),?)
            """,organizacion,anuncio,version,actor,accion,Timestamp.from(fecha),contenido,origen,evaluacion,motivo);
    }
    public List<Version> consultar(long organizacion,long encargo,long anuncio){
        return jdbc.query("""
            SELECT v.version,v.actuacion,v.registrada_en,v.contenido::text,v.motivo
            FROM version_publicacion v JOIN publicacion p ON p.organizacion_id=v.organizacion_id
              AND p.id_publicacion=v.id_publicacion
            WHERE v.organizacion_id=? AND p.id_captacion=? AND v.id_publicacion=? ORDER BY v.version
            """,(rs,n)->new Version(rs.getLong(1),rs.getString(2),rs.getTimestamp(3).toInstant(),rs.getString(4),rs.getString(5)),
                organizacion,encargo,anuncio);
    }
    /** Huella de valores realmente leídos, sin devolverlos al cliente ni inferir versiones por fecha. */
    public String fuentes(long organizacion,long propiedad,long encargo){
        return jdbc.queryForObject("""
            SELECT jsonb_build_object(
              'fisico',to_jsonb(p), 'encargo',to_jsonb(c),
              'economia',(SELECT to_jsonb(e) FROM condicion_economica_captacion e
                 WHERE e.organizacion_id=c.organizacion_id AND e.id_condicion_economica=c.id_condicion_economica),
              'composicion',(SELECT to_jsonb(x) FROM composicion_encargo x
                 WHERE x.organizacion_id=c.organizacion_id AND x.id_captacion=c.id_captacion AND x.id_composicion_encargo=c.id_composicion_actual),
              'atributosFisicos',COALESCE((SELECT jsonb_agg(to_jsonb(a) ORDER BY a.clave)
                FROM atributo_propiedad a WHERE a.organizacion_id=? AND a.id_propiedad=p.id_propiedad),'[]'::jsonb),
              'atributosEncargo',COALESCE((SELECT jsonb_agg(to_jsonb(a) ORDER BY a.clave)
                FROM atributo_encargo a WHERE a.organizacion_id=? AND a.id_captacion=c.id_captacion),'[]'::jsonb),
              'opcionesFisicas',COALESCE((SELECT jsonb_agg(to_jsonb(o) ORDER BY o.id_atributo_propiedad,o.valor)
                FROM atributo_propiedad_opcion o JOIN atributo_propiedad a USING(id_atributo_propiedad)
                WHERE a.organizacion_id=p.organizacion_id AND a.id_propiedad=p.id_propiedad),'[]'::jsonb),
              'opcionesEncargo',COALESCE((SELECT jsonb_agg(to_jsonb(o) ORDER BY o.id_atributo_encargo,o.valor)
                FROM atributo_encargo_opcion o JOIN atributo_encargo a USING(id_atributo_encargo)
                WHERE a.organizacion_id=c.organizacion_id AND a.id_captacion=c.id_captacion),'[]'::jsonb)
            )::text
            FROM propiedad p JOIN captacion c ON c.organizacion_id=p.organizacion_id
               AND c.id_propiedad=p.id_propiedad
            WHERE p.organizacion_id=? AND p.id_propiedad=? AND c.id_captacion=?
            """,String.class,organizacion,organizacion,organizacion,propiedad,encargo);
    }
}
