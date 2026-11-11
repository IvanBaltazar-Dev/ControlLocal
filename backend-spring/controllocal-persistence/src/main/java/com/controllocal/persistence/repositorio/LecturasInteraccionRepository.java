package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** Busca/pagina únicamente el conjunto ya autorizado por Core; no contiene reglas de permiso. */
@Repository
public class LecturasInteraccionRepository {
    public record PaginaIds(List<Long> ids,long total) { }
    public record Medicion(long id,long version,long agente,LocalDate fecha,Instant registrado,String tipo,String resultado,String eventos) { }
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate consultas;
    public LecturasInteraccionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;this.consultas=new NamedParameterJdbcTemplate(jdbc);}
    private static final String DATOS="i.contexto_declarado_json::jsonb->'datos'";
    private static final String CABECERAS="""
        SELECT i.id_rol_agente AS agente,
               i.autorizacion_json COLLATE "C" AS autorizacion,
               i.version_diccionario COLLATE "C" AS diccionario,
               i.id_oportunidad AS oportunidad,
               i.id_prospeccion AS prospeccion,
               i.id_captacion AS encargo,
               i.id_rol_cliente AS cliente,
               string_agg(pg_catalog.int8send(i.id_interaccion) || pg_catalog.int8send(i.version), ''::bytea) AS referencias,
               count(*) AS cantidad
          FROM public.interaccion_comercial i
         WHERE i.organizacion_id = cast(:org AS bigint)
           AND i.id_rol_agente = ANY(cast(:roles AS bigint[]))
         GROUP BY i.id_rol_agente,
                  i.autorizacion_json COLLATE "C",
                  i.version_diccionario COLLATE "C",
                  i.id_oportunidad,
                  i.id_prospeccion,
                  i.id_captacion,
                  i.id_rol_cliente
        """;

    /** Compresión de transporte, sin decisiones de permiso ni pérdida de identidad. */
    public List<InteraccionComercialRepository.Cabecera> cabeceras(long org,Collection<Long> roles){
        if(roles.isEmpty())return List.of();
        var p=new MapSqlParameterSource().addValue("org",org).addValue("roles",new SqlArrayValue("bigint",roles.toArray()));
        return consultas.query(CABECERAS,p,r->{
            var salida=new ArrayList<InteraccionComercialRepository.Cabecera>();
            while(r.next()){
                long agente=Objects.requireNonNull(r.getObject(1,Long.class));
                String autorizacion=r.getString(2),diccionario=r.getString(3);
                Long oportunidad=r.getObject(4,Long.class),prospeccion=r.getObject(5,Long.class),
                        encargo=r.getObject(6,Long.class),cliente=r.getObject(7,Long.class);
                byte[] bytes=Objects.requireNonNull(r.getBytes(8),"Faltan las referencias de cabecera.");
                long cantidad=Objects.requireNonNull(r.getObject(9,Long.class),"Falta el conteo de cabeceras.");
                final long esperados;
                try{esperados=Math.multiplyExact(cantidad,16L);}
                catch(ArithmeticException e){throw new IllegalStateException("Longitud de referencias de cabecera incompatible.",e);}
                // string_agg omite NULL: comprobar COUNT evita perder un par silenciosamente.
                if(bytes.length!=esperados)throw new IllegalStateException("Referencias de cabecera incompletas.");
                var pares=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
                while(pares.hasRemaining()){
                    long id=pares.getLong(),version=pares.getLong();
                    salida.add(new InteraccionComercialRepository.CabeceraActual(id,version,agente,autorizacion,diccionario,
                            oportunidad,prospeccion,encargo,cliente));
                }
            }
            salida.sort(Comparator.comparingLong(InteraccionComercialRepository.Cabecera::getId).reversed());
            return List.copyOf(salida);
        });
    }

    private static final String BUSQUEDA="""
        WITH candidatas AS NOT MATERIALIZED (
            SELECT i.id_interaccion,
                   j.datos
              FROM public.interaccion_comercial i
              CROSS JOIN LATERAL (
                  SELECT CASE
                      WHEN cast(:texto AS text) IS NULL
                       AND cast(:tipo AS text) IS NULL
                       AND cast(:resultado AS text) IS NULL
                       AND cast(:canal AS text) IS NULL
                      THEN NULL::jsonb
                      ELSE i.busqueda_proyectada -> 'datos'
                  END AS datos
                  OFFSET 0
              ) j
             WHERE i.organizacion_id = cast(:org AS bigint)
               AND i.id_interaccion = ANY(cast(:ids AS bigint[]))
               AND CASE
                       WHEN cast(:texto AS text) IS NULL THEN true
                       ELSE i.busqueda_proyectada ->> 'texto' LIKE ('%' || cast(:texto AS text) || '%')
                   END
        ), filtradas AS MATERIALIZED (
            SELECT i.id_interaccion
              FROM candidatas i
             WHERE (cast(:tipo AS text) IS NULL OR i.datos ->> 'tipo' = cast(:tipo AS text))
               AND (cast(:resultado AS text) IS NULL OR i.datos ->> 'resultado' = cast(:resultado AS text))
               AND (cast(:canal AS text) IS NULL OR i.datos ->> 'canal' = cast(:canal AS text))
               AND (
                   cast(:texto AS text) IS NULL
                   OR lower(coalesce(i.datos ->> 'observaciones', '')) LIKE cast(:texto AS text)
                   OR lower(coalesce(i.datos ->> 'resumen', '')) LIKE cast(:texto AS text)
                   OR lower(coalesce(i.datos -> 'destinatario' ->> 'nombre', '')) LIKE cast(:texto AS text)
                   OR lower(coalesce(i.datos -> 'interlocutor' ->> 'nombre', '')) LIKE cast(:texto AS text)
                   OR EXISTS (
                       SELECT 1
                         FROM jsonb_array_elements(
                             coalesce(nullif(i.datos -> 'declaraciones', 'null'::jsonb), '[]'::jsonb)
                         ) AS d(valor)
                        WHERE lower(coalesce(d.valor ->> 'texto', '')) LIKE cast(:texto AS text)
                   )
               )
        ), conteo AS (
            SELECT count(*) AS total FROM filtradas
        ), pagina AS (
            SELECT id_interaccion FROM filtradas
             ORDER BY id_interaccion DESC
             LIMIT cast(:limite AS integer) OFFSET cast(:offset AS bigint)
        )
        SELECT p.id_interaccion, c.total
          FROM conteo c LEFT JOIN pagina p ON true
         ORDER BY p.id_interaccion DESC NULLS LAST
        """;

    public PaginaIds buscar(long org,Collection<Long> permitidas,String tipo,String resultado,String canal,String q,int pagina,int tamano){
        if(permitidas.isEmpty())return new PaginaIds(List.of(),0);
        String texto=q==null||q.isBlank()?null:"%"+q.toLowerCase(Locale.ROOT)+"%";
        var p=new MapSqlParameterSource().addValue("org",org).addValue("ids",array(permitidas))
                .addValue("tipo",tipo).addValue("resultado",resultado).addValue("canal",canal)
                .addValue("texto",texto).addValue("limite",tamano).addValue("offset",(long)(pagina-1)*tamano);
        // El prefiltro indexado solo obtiene candidatos. El recheck mantiene las
        // cinco ramas originales; COUNT y pagina comparten una sola consulta.
        return consultas.query(BUSQUEDA,p,r->{
            var ids=new ArrayList<Long>();long total=0;boolean leido=false;
            while(r.next()){
                long id=r.getLong(1);if(!r.wasNull())ids.add(id);
                total=r.getLong(2);leido=true;
            }
            if(!leido)throw new IllegalStateException("El lector no devolvio su conteo.");
            return new PaginaIds(List.copyOf(ids),total);
        });
    }
    public List<Medicion> mediciones(long org,Collection<Long> permitidas){
        if(permitidas.isEmpty())return List.of();
        return jdbc.query("select i.id_interaccion,i.version,i.id_rol_agente,i.fecha_hecho,i.registrado_en,"+DATOS+"->>'tipo',"+DATOS+"->>'resultado',jsonb_build_object('eventos',coalesce("+DATOS+"->'eventos','[]'::jsonb),'contactoProspeccion',i.contexto_declarado_json::jsonb->'contactoProspeccion','atencionConsultas',i.contexto_declarado_json::jsonb->'atencionConsultas','proximaActuacion',case when jsonb_typeof("+DATOS+"->'proxima')='object' then jsonb_build_object('origen',"+DATOS+"->'proxima'->'origen','fecha',"+DATOS+"->'proxima'->'fecha') end)::text from interaccion_comercial i where i.organizacion_id=? and i.id_interaccion=any(?::bigint[]) order by i.id_interaccion desc",
                (r,n)->new Medicion(r.getLong(1),r.getLong(2),r.getLong(3),r.getObject(4,LocalDate.class),r.getTimestamp(5)==null?null:r.getTimestamp(5).toInstant(),r.getString(6),r.getString(7),r.getString(8)),org,array(permitidas));
    }
    private static SqlArrayValue array(Collection<Long> ids){
        // VARCHAR::bigint[] impide a PostgreSQL optimizar la pertenencia de conjuntos
        // grandes. JDBC crea/libera el ARRAY nativo sin cambiar los IDs autorizados.
        for(var id:ids)if(id==null||id<=0)throw new IllegalArgumentException("Identificador de interacción inválido.");
        return new SqlArrayValue("bigint",ids.toArray());
    }
}
