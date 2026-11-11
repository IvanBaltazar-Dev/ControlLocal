package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.Captacion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Sólo columnas V1 y valores cuyo selector ya fue admitido; ninguna ficha/atributo privado se recupera. */
@Repository
public class ProyeccionOfertaL1Repository {
    public record Base(long id,long version,long versionContenido,long propiedad,long versionFisica,long responsable,
                       String operacion,Long composicion,String tipo,Long distrito,String jurisdiccion,String profesional) { }
    public record Fisico(String clave,String tipoDato,String unidad,Object valor,boolean tieneMetadato,
                         String estado,String concepto,String referenciaCatalogo,boolean documentado,String jurisdiccion) { }
    public record Precio(BigDecimal importe,String moneda,String periodo,String unidad) { }
    public record Booleano(String clave,Boolean valor) { }
    private final NamedParameterJdbcTemplate jdbc;
    public ProyeccionOfertaL1Repository(JdbcTemplate jdbc){this.jdbc=new NamedParameterJdbcTemplate(jdbc);}
    private static final String BASE="""
        select c.id_captacion,c.version,c.version_contenido,c.id_propiedad,p.version_fisica,c.id_rol_agente,c.motivo_operacion,c.id_composicion_actual,
          case when c.id_composicion_actual is null then p.tipo_inmueble end as tipo,
          case when c.id_composicion_actual is null then p.id_distrito end as distrito,
          case when c.id_composicion_actual is null then p.contexto_captura::jsonb->>'jurisdiccion' end as jurisdiccion,
          coalesce(nullif(btrim(ca.nombre_profesional_declarado),''),pe.nombres_o_razon_social) as profesional
        from captacion c join propiedad p on p.organizacion_id=c.organizacion_id and p.id_propiedad=c.id_propiedad
        join persona_rol pr on pr.organizacion_id=c.organizacion_id and pr.id_persona_rol=c.id_rol_agente
          and pr.tipo_rol='AGENTE' and pr.vigencia_hasta is null and pr.vigencia_desde<=:hoy
        join persona pe on pe.organizacion_id=pr.organizacion_id and pe.id_persona=pr.id_persona and pe.estado='A'
        join usuario_organizacion u on u.organizacion_id=pr.organizacion_id and u.id_persona=pr.id_persona and u.rol='AGENTE' and u.estado='A'
        join cuenta_acceso ca on ca.id_cuenta_acceso=u.id_usuario and ca.estado='A' and ca.correo_confirmado_en is not null
        join organizacion o on o.id_organizacion=c.organizacion_id and o.estado='A' and o.modalidad='EMPRESARIAL'
        where c.organizacion_id=:org and c.estado in (:vivos)
        """;
    public List<Base> candidatas(long org,LocalDate hoy){return jdbc.query(BASE+" order by c.id_captacion",parametros(org,hoy),FILA);}
    public List<Base> actuales(long org,Collection<Long> ids,LocalDate hoy) {
        if(ids.isEmpty())return List.of();var p=parametros(org,hoy);p.put("ids",ids);
        return jdbc.query(BASE+" and c.id_captacion in (:ids) order by c.id_captacion",p,FILA);
    }
    public List<Fisico> fisicos(long org,long propiedad,Set<String> claves) {
        if(claves.isEmpty())return List.of();
        return jdbc.query("""
            select d.clave,d.tipo_dato,d.unidad,
              case when d.destino='ESTRUCTURAL' and d.campo_estructural='METRAJE' then p.metraje when d.destino='ATRIBUTO' then a.valor_numero end as numero,
              case when d.destino='ESTRUCTURAL' and d.campo_estructural='PISO' then p.piso when d.destino='ATRIBUTO' then a.valor_texto end as texto,
              case when d.destino='ATRIBUTO' then a.valor_booleano end as booleano,
              jsonb_typeof(p.metadatos_atributos::jsonb->d.clave)='object' as tiene_metadato,
              p.metadatos_atributos::jsonb->d.clave->>'estado' as estado,
              p.metadatos_atributos::jsonb->d.clave->>'concepto' as concepto,
              case when d.clave='zonificacion' then p.metadatos_atributos::jsonb->d.clave->>'referenciaCatalogo' end as referencia_catalogo,
              case when d.clave='zonificacion' then nullif(btrim(p.metadatos_atributos::jsonb->d.clave->>'evidenciaRef'),'') is not null else false end as documentado,
              case when d.clave='zonificacion' then p.contexto_captura::jsonb->>'jurisdiccion' end as jurisdiccion
            from propiedad p join catalogo_atributo d on d.sujeto='PROPIEDAD' and d.activo and (d.organizacion_id is null or d.organizacion_id=p.organizacion_id)
            left join atributo_propiedad a on a.organizacion_id=p.organizacion_id and a.id_propiedad=p.id_propiedad and a.clave=d.clave and d.destino='ATRIBUTO'
            where p.organizacion_id=:org and p.id_propiedad=:propiedad and d.clave in (:claves)
              and exists(select 1 from catalogo_atributo_tipo t where t.id_catalogo_atributo=d.id_catalogo_atributo and t.tipo_propiedad=p.tipo_inmueble)
            order by d.clave
            """,Map.of("org",org,"propiedad",propiedad,"claves",claves),(r,n)->{
                Object valor=r.getBigDecimal("numero");if(valor==null)valor=r.getString("texto");if(valor==null)valor=r.getObject("booleano",Boolean.class);
                return new Fisico(r.getString("clave"),r.getString("tipo_dato"),r.getString("unidad"),valor,r.getBoolean("tiene_metadato"),
                        r.getString("estado"),r.getString("concepto"),r.getString("referencia_catalogo"),r.getBoolean("documentado"),r.getString("jurisdiccion"));
            });
    }
    /** Sólo se llama después de G canónico habilitado y para esa misma versión/configuración. */
    public Optional<Precio> precio(long org,long encargo,long version) {
        return jdbc.query("""
            select e.importe_referencia,e.moneda_referencia,e.periodo_precio,
              case when c.motivo_operacion='A' and modalidad.valor_texto='POR_M2_AL_MES' then 'm2' end as unidad_precio
            from captacion c
            join condicion_economica_captacion e on e.organizacion_id=c.organizacion_id and e.id_condicion_economica=c.id_condicion_economica
            left join atributo_encargo modalidad on modalidad.organizacion_id=c.organizacion_id
              and modalidad.id_captacion=c.id_captacion and modalidad.clave='modalidad_precio'
            where c.organizacion_id=:org and c.id_captacion=:id and c.version=:version and c.estado=:estado
            """,Map.of("org",org,"id",encargo,"version",version,"estado",Captacion.APROBADO),
                (r,n)->new Precio(r.getBigDecimal(1),r.getString(2),r.getString(3),r.getString(4))).stream().findFirst();
    }
    public List<Booleano> booleanos(long org,long encargo) {
        return jdbc.query("""
            select clave,valor_booleano from atributo_encargo where organizacion_id=:org and id_captacion=:id
              and clave in ('se_ofrece_amoblado','se_entrega_implementado') order by clave
            """,Map.of("org",org,"id",encargo),(r,n)->new Booleano(r.getString(1),r.getObject(2,Boolean.class)));
    }
    private static Map<String,Object> parametros(long org,LocalDate hoy){var p=new HashMap<String,Object>();p.put("org",org);p.put("hoy",hoy);p.put("vivos",List.of(Captacion.EN_PREPARACION,Captacion.APROBADO));return p;}
    private static final RowMapper<Base> FILA=(r,n)->new Base(r.getLong("id_captacion"),r.getLong("version"),r.getLong("version_contenido"),r.getLong("id_propiedad"),r.getLong("version_fisica"),
            r.getLong("id_rol_agente"),r.getString("motivo_operacion"),r.getObject("id_composicion_actual",Long.class),r.getString("tipo"),r.getObject("distrito",Long.class),r.getString("jurisdiccion"),r.getString("profesional"));
}
