package com.controllocal.persistence.presupuesto;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Lectura R25 del ledger existente. Cada resultado procede de una sola sentencia/snapshot SQL. */
@Repository
public class MetricasIaB0Repository {
    public record Ambito(UUID plan,String version,String finalidad,UUID caso,UUID fondo,Instant desde,Instant hasta) {}
    public record TecnicaFila(Instant consultadaEn,long operaciones,long conMedicion,long mediciones,
            long conObservacion,long etapas,String estados,String unidades,String estadosEtapa,String observaciones) {}
    public record FinancieraFila(Instant consultadaEn,UUID caso,UUID comando,String estado,Instant admitidaEn,
            Instant iniciadaEn,BigDecimal maximoTecnicoUsd,BigDecimal costoTecnicoUsd,
            String mediciones,String etapas,String observaciones,BigDecimal costoEfectivo,BigDecimal costoImputado,BigDecimal pendientePositivo,Long revisionCosto,String historiaCosto) {}
    private final JdbcTemplate jdbc;
    public MetricasIaB0Repository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    // El caso/fondo opcionales sólo reducen el ámbito acreditado; nunca se resuelve por UUID global.
    private static final String COHORTE="""
        WITH parametros AS (
          SELECT CAST(? AS uuid) plan,CAST(? AS text) version,CAST(? AS text) finalidad,
                 CAST(? AS uuid) caso,CAST(? AS uuid) fondo,CAST(? AS timestamptz) desde,
                 CAST(? AS timestamptz) hasta,CAST(? AS uuid) comando
        ), cohorte AS (
          SELECT o.id_operacion_b0,o.id_operacion,o.id_caso_evaluacion,o.estado,o.admitida_en,o.iniciada_en FROM b0_operacion_ia o
          JOIN b0_evaluacion_caso c ON c.id=o.id_caso_evaluacion
          JOIN b0_evaluacion_plan p ON p.id=c.plan AND p.version=c.version_plan
          CROSS JOIN parametros q
          WHERE o.organizacion_id IS NULL AND c.plan=q.plan AND c.version_plan=q.version
            AND p.finalidad=q.finalidad AND o.finalidad=p.finalidad
            AND (q.caso IS NULL OR c.id=q.caso) AND (q.fondo IS NULL OR o.id_fondo=q.fondo)
            AND (q.comando IS NULL OR o.id_operacion=q.comando)
            AND o.admitida_en>=q.desde AND o.admitida_en<q.hasta
        ), etapas AS (
          SELECT e.* FROM b0_etapa_operacion_ia e JOIN cohorte o USING(id_operacion_b0)
        ), observaciones AS (
          SELECT a.id_operacion_b0,a.registrado_en,
            jsonb_build_object('esquema',a.contenido->'esquema','claveIntento',a.contenido->'claveIntento',
              'proveedor',a.contenido->'proveedor','modelo',a.contenido->'modelo',
              'versionConfiguracion',a.contenido->'versionConfiguracion','iniciadaEn',a.contenido->'iniciadaEn',
              'duracionNanos',a.contenido->'duracionNanos','resultado',a.contenido->'resultado',
              'estadoHttp',a.contenido->'estadoHttp','envioIntentado',a.contenido->'envioIntentado') contenido
          FROM b0_antecedente a JOIN cohorte o USING(id_operacion_b0)
          WHERE a.hecho='OBSERVACION_HTTP_IA'
        )
        """;
    private static final String JSON_OBSERVACIONES="""
        COALESCE((SELECT jsonb_agg(jsonb_build_object('operacion',h.id_operacion_b0,
          'contenido',h.contenido::text,'registradaEn',h.registrado_en) ORDER BY h.registrado_en,h.id_operacion_b0)
          FROM observaciones h),'[]'::jsonb)::text
        """;
    static final String TECNICA=COHORTE+"""
        , medidas AS (
          SELECT m.id_operacion_b0,m.id_medicion,m.proveedor,m.modelo,m.version_tarifa,m.unidades_originales
          FROM b0_medicion_tecnica m JOIN cohorte o USING(id_operacion_b0)
        ), estados AS (SELECT estado,count(*) cantidad FROM cohorte GROUP BY estado),
        estados_etapa AS (SELECT etapa,estado,count(*) cantidad FROM etapas GROUP BY etapa,estado),
        unidades AS (
          SELECT m.proveedor,m.modelo,m.version_tarifa,u->>'clase' clase,u->>'unidad' unidad,
            u->>'subconjuntoDe' padre,NULL::numeric cantidad,count(*) mediciones
          FROM medidas m CROSS JOIN LATERAL jsonb_array_elements(m.unidades_originales) u
          GROUP BY m.proveedor,m.modelo,m.version_tarifa,u->>'clase',u->>'unidad',u->>'subconjuntoDe'
        )
        SELECT statement_timestamp() consultada_en,(SELECT count(*) FROM cohorte) operaciones,
          (SELECT count(DISTINCT id_operacion_b0) FROM medidas) con_medicion,
          (SELECT count(*) FROM medidas) mediciones,
          (SELECT count(DISTINCT id_operacion_b0) FROM observaciones) con_observacion,
          (SELECT count(*) FROM etapas) etapas,
          COALESCE((SELECT jsonb_agg(jsonb_build_object('estado',estado,'operaciones',cantidad) ORDER BY estado)
            FROM estados),'[]'::jsonb)::text estados,
          COALESCE((SELECT jsonb_agg(jsonb_build_object('proveedor',proveedor,'modelo',modelo,
            'versionMedicion',version_tarifa,'clase',clase,'unidad',unidad,'subconjuntoDe',padre,
            'cantidadOriginal',cantidad,'mediciones',mediciones)
            ORDER BY proveedor,modelo,version_tarifa,clase,unidad,padre) FROM unidades),'[]'::jsonb)::text unidades,
          COALESCE((SELECT jsonb_agg(jsonb_build_object('etapa',etapa,'estado',estado,'etapas',cantidad)
            ORDER BY etapa,estado) FROM estados_etapa),'[]'::jsonb)::text estados_etapa,
        """+JSON_OBSERVACIONES+" AS observaciones";
    static final String FINANCIERA=COHORTE+"""
        SELECT statement_timestamp() consultada_en,o.id_caso_evaluacion,o.id_operacion,o.estado,
          o.admitida_en,o.iniciada_en,o.maximo_tecnico_usd,o.costo_tecnico_usd,
          pc.costo_efectivo_usd,pc.costo_imputado_usd,pc.pendiente_positivo_usd,pc.version revision_costo,
          COALESCE((SELECT jsonb_agg(jsonb_build_object(
            'hecho',a.hecho,'confirmacion',a.contenido->'confirmacion','medicion',a.contenido->'medicion','anterior',a.contenido->'anterior',
            'costoTotalUsd',a.contenido->'costoTotalUsd','diferenciaUsd',a.contenido->'diferenciaUsd',
            'documento',a.contenido->'solicitud'->'documento','linea',a.contenido->'solicitud'->'linea',
            'revisionFuente',a.contenido->'solicitud'->'revisionFuente','fechaFuente',a.contenido->'solicitud'->'fechaFuente',
            'evidencia',a.contenido->'solicitud'->'evidencia','causa',a.contenido->'solicitud'->'causa',
            'revision',a.contenido->'revision','cuenta',a.contenido->'cuenta','concesion',a.contenido->'concesion',
            'registradaEn',a.registrado_en) ORDER BY a.id_antecedente) FROM b0_antecedente a
            WHERE a.id_operacion_b0=o.id_operacion_b0 AND a.hecho IN('CONFIRMACION_COSTO_IA','APLICACION_COSTO_IA')),'[]'::jsonb)::text historia_costo,
          COALESCE((SELECT jsonb_agg(jsonb_build_object('id',m.id_medicion,'proveedor',m.proveedor,
            'modelo',m.modelo,'versionTarifa',m.version_tarifa,'unidadesOriginales',m.unidades_originales::text,
            'tarifasOriginales',m.tarifas_originales::text,'costoUsd',m.costo_usd,'estadoCosto',m.estado_costo,
            'referenciaFuente',m.referencia_fuente,'registradaEn',m.registrada_en,'claveEtapa',m.etapa_clave)
            ORDER BY m.registrada_en,m.id_medicion) FROM b0_medicion_tecnica m
            WHERE m.id_operacion_b0=o.id_operacion_b0),'[]'::jsonb)::text mediciones,
          COALESCE((SELECT jsonb_agg(jsonb_build_object('etapa',e.etapa,'estado',e.estado,
            'reclamadaEn',e.reclamada_en,'resueltaEn',e.resuelta_en) ORDER BY e.etapa)
            FROM etapas e WHERE e.id_operacion_b0=o.id_operacion_b0),'[]'::jsonb)::text etapas,
        """+JSON_OBSERVACIONES+" AS observaciones FROM cohorte c JOIN b0_operacion_ia o ON o.id_operacion_b0=c.id_operacion_b0 LEFT JOIN b0_costo_operacion_ia pc ON pc.id_operacion_b0=o.id_operacion_b0";

    public TecnicaFila tecnica(Ambito a){
        var filas=jdbc.query(TECNICA,(r,n)->new TecnicaFila(instante(r,"consultada_en"),r.getLong("operaciones"),
                r.getLong("con_medicion"),r.getLong("mediciones"),r.getLong("con_observacion"),r.getLong("etapas"),
                r.getString("estados"),r.getString("unidades"),r.getString("estados_etapa"),r.getString("observaciones")),parametros(a,null));
        if(filas.size()!=1)throw new IllegalStateException("La lectura de métricas no conserva un único universo.");
        return filas.getFirst();
    }
    public Optional<FinancieraFila> financiera(Ambito a,UUID comando){
        if(a.caso()==null||a.fondo()==null||comando==null)throw new IllegalArgumentException("La lectura financiera exige caso, fondo y comando.");
        var filas=jdbc.query(FINANCIERA,(r,n)->new FinancieraFila(instante(r,"consultada_en"),r.getObject("id_caso_evaluacion",UUID.class),
                r.getObject("id_operacion",UUID.class),r.getString("estado"),instante(r,"admitida_en"),instante(r,"iniciada_en"),
                r.getBigDecimal("maximo_tecnico_usd"),r.getBigDecimal("costo_tecnico_usd"),r.getString("mediciones"),
                r.getString("etapas"),r.getString("observaciones"),r.getBigDecimal("costo_efectivo_usd"),r.getBigDecimal("costo_imputado_usd"),
                r.getBigDecimal("pendiente_positivo_usd"),r.getObject("revision_costo",Long.class),r.getString("historia_costo")),parametros(a,comando));
        if(filas.size()>1)throw new IllegalStateException("La operación financiera no es unívoca.");
        return filas.stream().findFirst();
    }
    /** Sólo referencias de la consulta autorizada; no guarda el resultado ni el contenido nativo. */
    public void auditar(long cuenta,UUID concesion,Ambito a,String clase,UUID comando){
        int n=jdbc.update("""
            INSERT INTO b0_consulta_metricas_ia(concesion,cuenta,plan,version_plan,caso,fondo,clase,
              desde,hasta,operacion,registrada_en) VALUES(?,?,?,?,?,?,?,?,?,?,clock_timestamp())
            """,concesion,cuenta,a.plan(),a.version(),a.caso(),a.fondo(),clase,utc(a.desde()),utc(a.hasta()),comando);
        if(n!=1)throw new IllegalStateException("La consulta de métricas no conservó su auditoría.");
    }
    private static Object[] parametros(Ambito a,UUID comando){
        return new Object[]{a.plan(),a.version(),a.finalidad(),a.caso(),a.fondo(),utc(a.desde()),utc(a.hasta()),comando};
    }
    private static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
    private static Instant instante(ResultSet r,String columna)throws SQLException{
        var fecha=r.getObject(columna,OffsetDateTime.class);return fecha==null?null:fecha.toInstant();
    }
}
