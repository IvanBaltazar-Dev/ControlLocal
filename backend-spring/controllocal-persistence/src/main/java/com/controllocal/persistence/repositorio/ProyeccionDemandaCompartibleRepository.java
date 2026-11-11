package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/** Columnas y JSON positivos para L1/subset concedido. El servicio acredita la finalidad antes de llamar. */
@Repository
public class ProyeccionDemandaCompartibleRepository {
    public record Base(long id,long version,long responsable,String operacion,String estado,
                       LocalDate desde,LocalDate hasta,List<String> tipos,List<String> zonas,String finalidad,String profesional) { }
    private final NamedParameterJdbcTemplate jdbc;
    public ProyeccionDemandaCompartibleRepository(JdbcTemplate jdbc){this.jdbc=new NamedParameterJdbcTemplate(jdbc);}
    private static final String CONTEXTO="""
        from requerimiento_cliente r
        join persona_rol pr on pr.organizacion_id=r.organizacion_id and pr.id_persona_rol=r.id_rol_responsable
          and pr.tipo_rol='AGENTE' and pr.vigencia_hasta is null and pr.vigencia_desde<=:hoy
        join persona pe on pe.organizacion_id=pr.organizacion_id and pe.id_persona=pr.id_persona and pe.estado='A'
        join usuario_organizacion u on u.organizacion_id=pr.organizacion_id and u.id_persona=pr.id_persona and u.rol='AGENTE' and u.estado='A'
        join cuenta_acceso ca on ca.id_cuenta_acceso=u.id_usuario and ca.estado='A' and ca.correo_confirmado_en is not null
        join organizacion o on o.id_organizacion=r.organizacion_id and o.estado='A'
        where r.organizacion_id=:org
        """;
    private static String seleccion(boolean tipos,boolean zonas,boolean finalidad,boolean identidad) {
        return "select r.id_requerimiento,r.version,r.id_rol_responsable,r.operacion,r.estado,r.vigente_desde,r.vigente_hasta,"
                +(tipos?"array(select t.tipo_inmueble from requerimiento_tipo_inmueble t where t.id_requerimiento=r.id_requerimiento order by t.tipo_inmueble)":"null::text[]")+" as tipos,"
                +(zonas?"array(select d.id_distrito::text from requerimiento_distrito d where d.id_requerimiento=r.id_requerimiento order by d.id_distrito)":"null::text[]")+" as zonas,"
                +(finalidad?"r.finalidad":"null::text")+" as finalidad,"
                +(identidad?"coalesce(nullif(btrim(ca.nombre_profesional_declarado),''),pe.nombres_o_razon_social)":"null::text")+" as profesional ";
    }
    public Optional<Base> metadatosConcedidos(long org,long id,Set<String> campos,LocalDate hoy) {
        var filas=jdbc.query(seleccion(campos.contains("tiposAdmitidos"),campos.contains("zona"),campos.contains("finalidad"),false)+CONTEXTO+" and r.id_requerimiento=:id",
                Map.of("org",org,"id",id,"hoy",hoy),BASE);
        return filas.stream().findFirst();
    }
    public List<Base> candidatasL1(long org,LocalDate hoy) {
        return jdbc.query(seleccion(true,true,false,true)+CONTEXTO+" and o.modalidad='EMPRESARIAL' and r.estado='A' and (r.vigente_desde is null or r.vigente_desde<=:hoy) and (r.vigente_hasta is null or r.vigente_hasta>=:hoy) order by r.id_requerimiento",
                Map.of("org",org,"hoy",hoy),BASE);
    }
    public List<Base> actualesL1(long org,Collection<Long> ids,LocalDate hoy) {
        if(ids.isEmpty())return List.of();
        return jdbc.query(seleccion(true,true,false,true)+CONTEXTO+" and o.modalidad='EMPRESARIAL' and r.id_requerimiento in (:ids) and r.estado='A' and (r.vigente_desde is null or r.vigente_desde<=:hoy) and (r.vigente_hasta is null or r.vigente_hasta>=:hoy) order by r.id_requerimiento",
                Map.of("org",org,"ids",ids,"hoy",hoy),BASE);
    }
    /** Todo el criterio o ninguno: el WHERE se aplica antes de producir el JSON sin metadatos privados. */
    public List<String> criterios(long org,long id,long version,Set<String> selectores) {
        if(selectores.isEmpty())return List.of();
        return jdbc.queryForList("""
            select jsonb_build_object('referencia',j->'referencia','clave',j->'clave','exigencia',j->'exigencia','prioridad',j->'prioridad',
              'alternativas',(select jsonb_agg(jsonb_build_object(
                'claveDato',a->'claveDato','tipo',a->'tipo','operador',a->'operador','valor',a->'valor','hasta',a->'hasta','negada',a->'negada',
                'contexto',jsonb_build_object('concepto',a#>'{contexto,concepto}','unidad',a#>'{contexto,unidad}',
                  'moneda',null,'periodo',null,'operacion',a#>'{contexto,operacion}','configuracion',null,
                  'jurisdiccion',a#>'{contexto,jurisdiccion}','referenciaCatalogo',null,'versionFuente',a#>'{contexto,versionFuente}'),
                'metadatos',jsonb_build_object('estado',a#>'{metadatos,estado}','causa',a#>'{metadatos,causa}',
                  'vigenteDesde',a#>'{metadatos,vigenteDesde}','vigenteHasta',a#>'{metadatos,vigenteHasta}')) order by n)
                from jsonb_array_elements(j->'alternativas') with ordinality q(a,n)))::text
            from requerimiento_criterio c cross join lateral (select c.criterio_json::jsonb as j) contenido
            where c.organizacion_id=:org and c.id_requerimiento=:id and c.version_demanda=:version
              and c.criterio_json is not null and jsonb_typeof(j->'alternativas')='array' and jsonb_array_length(j->'alternativas')>0
              and not exists(select 1 from jsonb_array_elements(j->'alternativas') a where
                a->>'claveDato' is null or a->>'claveDato' not in (:selectores) or a->>'tipo'='IMPORTE'
                or a#>>'{contexto,moneda}' is not null or a#>>'{valor,moneda}' is not null or a#>>'{hasta,moneda}' is not null
                or a#>>'{contexto,configuracion}' is not null or a#>>'{contexto,periodo}' is not null)
            order by c.clave
            """,Map.of("org",org,"id",id,"version",version,"selectores",selectores),String.class);
    }
    private static final RowMapper<Base> BASE=(r,n)->new Base(r.getLong("id_requerimiento"),r.getLong("version"),r.getLong("id_rol_responsable"),
            r.getString("operacion"),r.getString("estado"),r.getObject("vigente_desde",LocalDate.class),r.getObject("vigente_hasta",LocalDate.class),
            lista(r,"tipos"),lista(r,"zonas"),r.getString("finalidad"),r.getString("profesional"));
    private static List<String> lista(ResultSet r,String columna)throws SQLException {
        var a=r.getArray(columna);if(a==null)return null;
        try{return List.copyOf(Arrays.asList((String[])a.getArray()));}finally{a.free();}
    }
}
