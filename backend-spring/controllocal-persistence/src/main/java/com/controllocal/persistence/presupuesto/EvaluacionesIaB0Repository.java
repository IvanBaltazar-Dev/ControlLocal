package com.controllocal.persistence.presupuesto;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Metadatos autorizados de evaluación; dinero y consumo permanecen en B0. */
@Repository
public class EvaluacionesIaB0Repository {
    private final JdbcTemplate jdbc;
    private final JdbcTemplate jdbcCustodia;
    private final org.springframework.transaction.support.TransactionTemplate custodiaNueva;
    public EvaluacionesIaB0Repository(JdbcTemplate jdbc,com.controllocal.persistence.archivo.DiarioBinarioTecnico diario){
        this.jdbc=jdbc;this.jdbcCustodia=diario.jdbc();
        this.custodiaNueva=new org.springframework.transaction.support.TransactionTemplate(diario.gestor());
        this.custodiaNueva.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public record Plan(UUID id,String version,String huella,String finalidad,String configuracion,String manifiesto,
        String rubrica,String conservacion,String destino,String namespace,BigDecimal maximoOperacion,
        int llamadas,long salidaBytes,int concurrencia) {}
    public record Caso(UUID id,UUID plan,String version,String codigo,String huella,String procedencia,
        String legitimacion,UUID archivo,long bytes,String mime) {}
    public record Financiacion(UUID id,UUID plan,String version,UUID fondo,BigDecimal maximo,Instant desde,
        Instant hasta,long cuenta,UUID concesion,String evidencia,Instant revocada) {}
    public Instant ahoraTecnica(){return jdbc.queryForObject("SELECT clock_timestamp()",OffsetDateTime.class).toInstant();}
    public record Acreditacion(UUID plan,String version,UUID decisionPlan,long cuentaPlan,UUID decisionCaso,long cuentaCaso){}
    public Optional<Acreditacion> acreditacion(UUID caso){
        return jdbc.query("SELECT p.id,p.version,p.decision,p.cuenta,c.decision,c.cuenta FROM b0_evaluacion_caso c JOIN b0_evaluacion_plan p ON p.id=c.plan AND p.version=c.version_plan WHERE c.id=?",
                (r,n)->new Acreditacion(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,UUID.class),r.getLong(4),r.getObject(5,UUID.class),r.getLong(6)),caso).stream().findFirst();
    }
    public Optional<String> hecho(UUID caso,UUID comando,String hecho){
        var filas=jdbc.query("SELECT a.contenido::text FROM b0_antecedente a JOIN b0_operacion_ia o ON o.id_operacion_b0=a.id_operacion_b0 WHERE o.id_caso_evaluacion=? AND o.id_operacion=? AND a.hecho=? ORDER BY a.id_antecedente LIMIT 2",(r,n)->r.getString(1),caso,comando,hecho);
        if(filas.size()>1)throw new IllegalStateException("El hecho de evaluación está duplicado.");return filas.stream().findFirst();
    }
    public void hecho(UUID caso,UUID comando,String hecho,String contenido){
        int n=jdbc.update("INSERT INTO b0_antecedente(organizacion_id,id_operacion,id_operacion_b0,id_caso_evaluacion,hecho,contenido,registrado_en) SELECT NULL,id_operacion,id_operacion_b0,id_caso_evaluacion,?,CAST(? AS jsonb),clock_timestamp() FROM b0_operacion_ia WHERE id_caso_evaluacion=? AND id_operacion=?",hecho,contenido,caso,comando);
        if(n!=1)throw new IllegalStateException("Falta la operación real de evaluación.");
    }
    public Optional<Plan> plan(UUID id,String version){return plan(jdbc,id,version);}
    private Optional<Plan> plan(JdbcTemplate acceso,UUID id,String version){
        return acceso.query("""
            SELECT id,version,huella,finalidad,configuracion::text,manifiesto,rubrica,conservacion,destino,
                   namespace_hash,maximo_operacion_usd,limite_llamadas,maximo_salida_bytes,concurrencia
            FROM b0_evaluacion_plan WHERE id=? AND version=?
            """,(r,n)->new Plan(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getString(5),
                r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getBigDecimal(11),
                r.getInt(12),r.getLong(13),r.getInt(14)),id,version).stream().findFirst();
    }
    public Optional<Caso> caso(UUID id){return caso(jdbc,id);}
    private Optional<Caso> caso(JdbcTemplate acceso,UUID id){
        return acceso.query("""
            SELECT id,plan,version_plan,codigo,huella,procedencia,legitimacion,archivo_original,bytes_original,mime_original
            FROM b0_evaluacion_caso WHERE id=?
            """,(r,n)->new Caso(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),
                r.getString(5),r.getString(6),r.getString(7),r.getObject(8,UUID.class),r.getLong(9),r.getString(10)),id).stream().findFirst();
    }
    public Optional<Financiacion> financiacion(UUID id,boolean bloquear){
        return jdbc.query("""
            SELECT id,plan,version_plan,fondo,maximo_usd,desde,hasta,cuenta,concesion,evidencia,revocada_en
            FROM b0_evaluacion_financiacion WHERE id=?
            """+(bloquear?" FOR UPDATE":""),(r,n)->new Financiacion(r.getObject(1,UUID.class),r.getObject(2,UUID.class),
                r.getString(3),r.getObject(4,UUID.class),r.getBigDecimal(5),r.getTimestamp(6).toInstant(),
                r.getTimestamp(7).toInstant(),r.getLong(8),r.getObject(9,UUID.class),r.getString(10),
                r.getTimestamp(11)==null?null:r.getTimestamp(11).toInstant()),id).stream().findFirst();
    }
    public BigDecimal comprometido(UUID financiacion) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(CASE WHEN o.estado IN('LIQUIDADA','CANCELADA')
                THEN GREATEST(COALESCE(p.costo_efectivo_usd,o.costo_tecnico_usd),COALESCE(p.costo_imputado_usd,o.costo_tecnico_usd))
                ELSE GREATEST(o.maximo_tecnico_usd,COALESCE(p.costo_efectivo_usd,0)) END),0)
            FROM b0_operacion_ia o LEFT JOIN b0_costo_operacion_ia p USING(id_operacion_b0) WHERE autorizacion_evaluacion=?
            """,BigDecimal.class,financiacion);
    }
    public int enCurso(UUID plan,String version){
        return jdbc.queryForObject("""
            SELECT count(*) FROM b0_operacion_ia o JOIN b0_evaluacion_caso c ON c.id=o.id_caso_evaluacion
            WHERE c.plan=? AND c.version_plan=? AND o.estado IN('RESERVADA','EN_PROCESO','INCIERTA')
            """,Integer.class,plan,version);
    }
    public void insertar(Plan p,UUID decision,long cuenta){
        jdbc.update("""
            INSERT INTO b0_evaluacion_plan(id,version,huella,finalidad,configuracion,manifiesto,rubrica,conservacion,
             destino,namespace_hash,maximo_operacion_usd,limite_llamadas,maximo_salida_bytes,concurrencia,decision,cuenta)
            VALUES(?,?,?,?,CAST(? AS jsonb),?,?,?,?,?,?,?,?,?,?,?)
            """,p.id(),p.version(),p.huella(),p.finalidad(),p.configuracion(),p.manifiesto(),p.rubrica(),p.conservacion(),
            p.destino(),p.namespace(),p.maximoOperacion(),p.llamadas(),p.salidaBytes(),p.concurrencia(),decision,cuenta);
    }
    public void insertar(Caso c,UUID decision,long cuenta){
        jdbc.update("""
            INSERT INTO b0_evaluacion_caso(id,plan,version_plan,codigo,huella,procedencia,legitimacion,
              archivo_original,bytes_original,mime_original,decision,cuenta) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            """,c.id(),c.plan(),c.version(),c.codigo(),c.huella(),c.procedencia(),c.legitimacion(),
            c.archivo(),c.bytes(),c.mime(),decision,cuenta);
    }
    public void insertar(Financiacion f){
        jdbc.update("""
            INSERT INTO b0_evaluacion_financiacion(id,plan,version_plan,fondo,maximo_usd,desde,hasta,cuenta,concesion,evidencia)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            """,f.id(),f.plan(),f.version(),f.fondo(),f.maximo(),AtribucionesPlataformaRepository.utc(f.desde()),
            AtribucionesPlataformaRepository.utc(f.hasta()),f.cuenta(),f.concesion(),f.evidencia());
    }

    public record Archivo(UUID archivo,UUID caso,UUID operacion,String tipo,String estado,long intento,
        UUID operacionIo,int numero,String namespace,String clave,String huella,long bytes,String mime) {}
    public Optional<Archivo> archivo(UUID caso,UUID archivo){return archivo(jdbc,caso,archivo);}
    private Optional<Archivo> archivo(JdbcTemplate acceso,UUID caso,UUID archivo){
        return acceso.query("""
            SELECT a.archivo,a.caso,a.operacion_b0,a.tipo,a.estado,i.id_intento,i.operacion,i.numero,
             i.almacen_huella,i.clave_proveedor,i.huella,i.bytes,i.content_type
            FROM b0_archivo_evaluacion a JOIN intento_salida_binaria i ON i.id_intento=a.id_intento
            WHERE a.caso=? AND a.archivo=?
            """,(x,n)->new Archivo(x.getObject(1,UUID.class),x.getObject(2,UUID.class),x.getObject(3,UUID.class),
                x.getString(4),x.getString(5),x.getLong(6),x.getObject(7,UUID.class),x.getInt(8),x.getString(9),
                x.getString(10),x.getString(11),x.getLong(12),x.getString(13)),caso,archivo).stream().findFirst();
    }
    /** Misma infraestructura139/148; ninguna consulta Main dentro del hijo técnico. */
    public void comprometer(UUID caso,UUID archivo,UUID operacionB0,UUID io,int numero,String hash,long bytes){
        custodiaNueva.executeWithoutResult(tx->{
            var c=caso(jdbcCustodia,caso).orElseThrow(()->new IllegalStateException("El caso debe estar comprometido antes de la custodia."));
            var p=plan(jdbcCustodia,c.plan(),c.version()).orElseThrow(()->new IllegalStateException("El plan debe estar comprometido."));
            if(operacionB0!=null)exigirClaim(jdbcCustodia,caso,operacionB0);
            // No consulta concesión, cuenta, previsión ni otros cambios aún no confirmados del padre.
            String fuente="EVALUACION-IA:"+caso+":"+archivo+":"+p.huella()+":"+hash;
            boolean intento=Boolean.TRUE.equals(jdbcCustodia.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM intento_salida_binaria i
                 JOIN b0_propietario_binario d ON d.id=i.dueno_binario AND d.caso=i.id_caso_evaluacion
                 WHERE i.id_caso_evaluacion=? AND i.operacion=? AND i.numero=? AND i.huella=? AND i.bytes=?
                  AND i.almacen_huella=? AND i.fuente=?)
                """,Boolean.class,caso,io,numero,hash,bytes,p.namespace(),fuente));
            if(!intento)throw new IllegalStateException("La custodia exige su intento139 comprometido.");
            if(operacionB0==null&&(!c.archivo().equals(archivo)||!c.huella().equals(hash)||c.bytes()!=bytes))
                throw new IllegalStateException("El original conserva su manifiesto exacto.");
            int n=jdbcCustodia.update("""
                INSERT INTO b0_archivo_evaluacion(archivo,caso,dueno_binario,id_intento,tipo,operacion_b0,estado)
                SELECT ?,?,i.dueno_binario,i.id_intento,?,?,'PREPARADA' FROM intento_salida_binaria i
                WHERE i.id_caso_evaluacion=? AND i.operacion=? AND i.numero=? AND i.huella=? AND i.bytes=?
                 AND i.almacen_huella=? AND i.fuente=?
                ON CONFLICT(archivo) DO NOTHING
                """,archivo,caso,operacionB0==null?"ORIGINAL_CORPUS":"RESULTADO",operacionB0,caso,io,numero,hash,bytes,p.namespace(),fuente);
            var a=archivo(jdbcCustodia,caso,archivo).orElseThrow(()->new IllegalStateException("No se acreditó la custodia del caso."));
            if(!Objects.equals(a.operacion(),operacionB0)||!a.operacionIo().equals(io)||a.numero()!=numero
                ||!a.huella().equals(hash)||a.bytes()!=bytes||!a.namespace().equals(p.namespace()))
                throw new IllegalStateException("La custodia conserva otro contenido.");
        });
    }
    public void verificar(UUID caso,UUID archivo){
        jdbc.update("UPDATE b0_archivo_evaluacion SET estado='VERIFICADA',verificada_en=clock_timestamp() WHERE caso=? AND archivo=? AND estado='PREPARADA'",caso,archivo);
    }
    public void exigirClaim(UUID caso,UUID operacionB0){exigirClaim(jdbc,caso,operacionB0);}
    private void exigirClaim(JdbcTemplate acceso,UUID caso,UUID operacionB0){
        if(!Boolean.TRUE.equals(acceso.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM b0_operacion_ia WHERE id_operacion_b0=? AND id_caso_evaluacion=?
             AND iniciada_en IS NOT NULL AND estado IN('EN_PROCESO','INCIERTA','LIQUIDADA')
             AND EXISTS(SELECT 1 FROM b0_medicion_tecnica m WHERE m.id_operacion_b0=b0_operacion_ia.id_operacion_b0))
            """,Boolean.class,operacionB0,caso)))throw new IllegalArgumentException("No se acreditó el claim original.");
    }

    public Optional<UUID> identidadOperacion(UUID caso,UUID comando){
        return jdbc.query("SELECT id_operacion_b0 FROM b0_operacion_ia WHERE id_caso_evaluacion=? AND id_operacion=?",
            (x,n)->x.getObject(1,UUID.class),caso,comando).stream().findFirst();
    }

    public boolean tieneArchivoOperacion(UUID operacion){
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM b0_archivo_evaluacion WHERE operacion_b0=?)",Boolean.class,operacion));
    }

    public void revocarFinanciacion(UUID id,long cuenta,UUID concesion,String motivo){
        jdbc.update("UPDATE b0_evaluacion_financiacion SET revocada_en=clock_timestamp(),revocada_por=?,concesion_revocacion=?,motivo_revocacion=? WHERE id=? AND revocada_en IS NULL",cuenta,concesion,motivo,id);
    }

    public Optional<UUID> autorizacionDePlan(UUID plan,String version){
        return jdbc.query("SELECT id FROM b0_evaluacion_financiacion WHERE plan=? AND version_plan=?",
            (x,n)->x.getObject(1,UUID.class),plan,version).stream().findFirst();
    }
}
