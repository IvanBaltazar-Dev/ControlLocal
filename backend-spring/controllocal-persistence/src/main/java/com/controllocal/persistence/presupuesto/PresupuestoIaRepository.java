package com.controllocal.persistence.presupuesto;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Almacenamiento financiero privado. El servicio es dueño de permisos y aritmética. */
@Repository
public class PresupuestoIaRepository {
    private final JdbcTemplate jdbc;
    public PresupuestoIaRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Organizacion(String modalidad,boolean activa){}
    public record Perfil(String id,String version,UUID fondo,BigDecimal normalizador,String tarifas,
                          BigDecimal maximoCreditos,BigDecimal maximoTecnico,int intentos,boolean habilitado){}
    public record Fondo(UUID id,BigDecimal financiado,BigDecimal retenido,BigDecimal liquidado,Instant desde,Instant hasta,boolean habilitado){}
    public record Ciclo(UUID id,String plan,Instant desde,Instant hasta,boolean revocado,BigDecimal financiado,int aniversario,int brokersExtra,long version){}
    public record Fuente(UUID id,String origen,Long persona,BigDecimal inicial,BigDecimal retenido,BigDecimal liquidado,Instant desde,Instant vence){
        public BigDecimal disponible(){return inicial.subtract(retenido).subtract(liquidado);}
    }
    public record Operacion(Long organizacion,UUID id,Long persona,Long rol,String perfil,String versionPerfil,
            String tipo,String recurso,String finalidad,String origen,String peticion,String estado,
            BigDecimal maximoCreditos,BigDecimal maximoTecnico,UUID fondo,Instant admitida,String resultado,
            UUID tecnica,UUID caso,Long cuenta,UUID concesion,UUID autorizacion){
        public Operacion(long org,UUID id,long persona,long rol,String perfil,String versionPerfil,
            String tipo,String recurso,String finalidad,String origen,String peticion,String estado,
            BigDecimal maximoCreditos,BigDecimal maximoTecnico,UUID fondo,Instant admitida,String resultado){
            this(org,id,persona,rol,perfil,versionPerfil,tipo,recurso,finalidad,origen,peticion,estado,
                 maximoCreditos,maximoTecnico,fondo,admitida,resultado,UUID.randomUUID(),null,null,null,null);
        }
        public boolean evaluacion(){return caso!=null;}
        public UUID referenciaRepositorio(){return evaluacion()?tecnica:id;}
    }
    public record Imputacion(UUID fuente,BigDecimal retenido,BigDecimal liquidado,int orden){}
    public record Plaza(String tipo,int numero,Long persona,UUID fuente){}
    public Organizacion bloquearOrganizacion(long org){
        return jdbc.query("SELECT modalidad,estado='A' FROM organizacion WHERE id_organizacion=? FOR NO KEY UPDATE",
                (r,n)->new Organizacion(r.getString(1),r.getBoolean(2)),org).stream().findFirst().orElse(null);
    }
    public Optional<Organizacion> organizacionActual(long org){return jdbc.query("SELECT modalidad,estado='A' FROM organizacion WHERE id_organizacion=?",(r,n)->new Organizacion(r.getString(1),r.getBoolean(2)),org).stream().findFirst();}
    public Optional<Perfil> perfil(String id,String version){
        return jdbc.query("""
            SELECT perfil,version,id_fondo,normalizador,tarifas_referencia::text,maximo_creditos,maximo_tecnico_usd,
                   limite_intentos,habilitado FROM b0_perfil_operativo WHERE perfil=? AND version=?
            """,(r,n)->new Perfil(r.getString(1),r.getString(2),r.getObject(3,UUID.class),r.getBigDecimal(4),r.getString(5),
                        r.getBigDecimal(6),r.getBigDecimal(7),r.getInt(8),r.getBoolean(9)),id,version).stream().findFirst();
    }
    public boolean esPerfilActual(String perfil,String version){
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM b0_perfil_actual WHERE perfil=? AND version=?)",Boolean.class,perfil,version));
    }
    public Optional<Fondo> bloquearFondo(UUID id){
        return jdbc.query("SELECT id_fondo,financiado,retenido,liquidado,desde,hasta,habilitado FROM b0_fondo_tecnico WHERE id_fondo=? FOR UPDATE",
                (r,n)->new Fondo(r.getObject(1,UUID.class),r.getBigDecimal(2),r.getBigDecimal(3),r.getBigDecimal(4),
                        instante(r,5),instante(r,6),r.getBoolean(7)),id).stream().findFirst();
    }
    public Optional<Ciclo> cicloVigente(long org,Instant ahora){
        var ciclos=jdbc.query("SELECT id_ciclo,plan,desde,hasta,revocado,importe_financiado_usd,dia_aniversario,brokers_extra,version FROM b0_ciclo WHERE organizacion_id=? AND desde<=? AND hasta>? AND (cerrado_en IS NULL OR cerrado_en>?) ORDER BY desde DESC",
                (r,n)->new Ciclo(r.getObject(1,UUID.class),r.getString(2),instante(r,3),instante(r,4),r.getBoolean(5),r.getBigDecimal(6),r.getInt(7),r.getInt(8),r.getLong(9)),org,utc(ahora),utc(ahora),utc(ahora));
        if(ciclos.size()>1)throw new IllegalStateException("Hay ciclos financiados superpuestos.");return ciclos.stream().findFirst();
    }
    public Optional<Ciclo> ciclo(long org,UUID id){
        return jdbc.query("SELECT id_ciclo,plan,desde,hasta,revocado,importe_financiado_usd,dia_aniversario,brokers_extra,version FROM b0_ciclo WHERE organizacion_id=? AND id_ciclo=?",
                (r,n)->new Ciclo(r.getObject(1,UUID.class),r.getString(2),instante(r,3),instante(r,4),r.getBoolean(5),r.getBigDecimal(6),r.getInt(7),r.getInt(8),r.getLong(9)),org,id).stream().findFirst();
    }
    public List<Fuente> fuentesElegibles(long org,long persona,boolean broker,UUID ciclo,Instant ahora,boolean bloquear){
        return jdbc.query("""
            SELECT id_fuente,origen,id_persona_destinataria,inicial,retenido,liquidado,desde,vence FROM b0_fuente_capacidad
            WHERE organizacion_id=? AND desde<=? AND vence>? AND inicial>retenido+liquidado
              AND (origen='BOOST' OR (id_ciclo=? AND ((? AND origen='MENSUAL_BROKER') OR
                   (NOT ? AND origen IN('MENSUAL_AGENTE','RESERVA_ASIGNADA') AND id_persona_destinataria=?))))
            ORDER BY CASE WHEN origen='BOOST' THEN 1 ELSE 0 END,vence,id_fuente
            """+(bloquear?" FOR UPDATE":""),(r,n)->new Fuente(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,Long.class),
                        r.getBigDecimal(4),r.getBigDecimal(5),r.getBigDecimal(6),instante(r,7),instante(r,8)),
                org,utc(ahora),utc(ahora),ciclo,broker,broker,persona);
    }
    public BigDecimal cuotaLegitima(long org,long persona,boolean broker,UUID ciclo){
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(inicial),0) FROM b0_fuente_capacidad WHERE organizacion_id=? AND id_ciclo=?
              AND ((? AND origen='MENSUAL_BROKER') OR (NOT ? AND origen IN('MENSUAL_AGENTE','RESERVA_ASIGNADA') AND id_persona_destinataria=?))
            """,BigDecimal.class,org,ciclo,broker,broker,persona);
    }
    public Optional<Operacion> operacion(Long org,UUID id){
        return jdbc.query("""
            SELECT organizacion_id,id_operacion,id_persona,id_rol,perfil,version_perfil,recurso_tipo,recurso_id,finalidad,
                   huella_origen,huella_peticion,estado,maximo_creditos,maximo_tecnico_usd,id_fondo,admitida_en,huella_resultado,id_operacion_b0,id_caso_evaluacion,cuenta_evaluacion,concesion_evaluacion,autorizacion_evaluacion
            FROM b0_operacion_ia WHERE id_operacion_b0=b0_resolver_operacion(?,?)
            """,(r,n)->new Operacion(r.getObject(1,Long.class),r.getObject(2,UUID.class),r.getObject(3,Long.class),r.getObject(4,Long.class),r.getString(5),r.getString(6),
                        r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11),r.getString(12),r.getBigDecimal(13),
                        r.getBigDecimal(14),r.getObject(15,UUID.class),instante(r,16),r.getString(17),r.getObject(18,UUID.class),r.getObject(19,UUID.class),r.getObject(20,Long.class),r.getObject(21,UUID.class),r.getObject(22,UUID.class)),org,id).stream().findFirst();
    }
    /** Resolver evaluación exige caso y comando; nunca UUID de comando global. */
    public Optional<Operacion> evaluacion(UUID caso,UUID comando){
        var ids=jdbc.query("SELECT id_operacion_b0 FROM b0_operacion_ia WHERE id_caso_evaluacion=? AND id_operacion=?",
                (r,n)->r.getObject(1,UUID.class),caso,comando);
        return ids.stream().findFirst().flatMap(id->operacion(null,id));
    }
    public void insertar(Operacion o){
        jdbc.update("""
            INSERT INTO b0_operacion_ia(organizacion_id,id_operacion,id_persona,id_rol,perfil,version_perfil,recurso_tipo,recurso_id,finalidad,
              huella_origen,huella_peticion,estado,maximo_creditos,maximo_tecnico_usd,id_fondo,admitida_en,id_operacion_b0,id_caso_evaluacion,cuenta_evaluacion,concesion_evaluacion,autorizacion_evaluacion)
              VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,o.organizacion(),o.id(),o.persona(),o.rol(),o.perfil(),o.versionPerfil(),o.tipo(),o.recurso(),o.finalidad(),o.origen(),o.peticion(),
                o.estado(),o.maximoCreditos(),o.maximoTecnico(),o.fondo(),utc(o.admitida()),o.tecnica(),o.caso(),o.cuenta(),o.concesion(),o.autorizacion());
    }
    public void retenerFondo(UUID id,BigDecimal monto){
        exigir(jdbc.update("UPDATE b0_fondo_tecnico SET retenido=retenido+?,version=version+1 WHERE id_fondo=? AND financiado-retenido-liquidado>=?",monto,id,monto));
    }
    public void retenerFuente(long org,UUID id,BigDecimal monto){
        exigir(jdbc.update("UPDATE b0_fuente_capacidad SET retenido=retenido+? WHERE organizacion_id=? AND id_fuente=? AND inicial-retenido-liquidado>=?",monto,org,id,monto));
    }
    public void imputar(Long org,UUID operacion,UUID fuente,BigDecimal monto,int orden){
        jdbc.update("INSERT INTO b0_imputacion(organizacion_id,id_operacion,id_fuente,retenido,orden) VALUES (?,?,?,?,?)",org,operacion,fuente,monto,orden);
    }
    public boolean iniciar(Long org,UUID operacion,Instant ahora){
        return jdbc.update("UPDATE b0_operacion_ia SET estado='EN_PROCESO',iniciada_en=?,version=version+1 WHERE id_operacion_b0=b0_resolver_operacion(?,?) AND estado='RESERVADA'",utc(ahora),org,operacion)==1;
    }
    public Optional<Instant> inicio(Long org,UUID operacion){
        return jdbc.query("SELECT iniciada_en FROM b0_operacion_ia WHERE id_operacion_b0=b0_resolver_operacion(?,?) AND iniciada_en IS NOT NULL",
                (r,n)->instante(r,1),org,operacion).stream().findFirst();
    }
    public void incierta(Long org,UUID operacion){
        jdbc.update("UPDATE b0_operacion_ia SET estado='INCIERTA',version=version+1 WHERE id_operacion_b0=b0_resolver_operacion(?,?) AND estado IN('EN_PROCESO','INCIERTA')",org,operacion);
    }
    public void incidenciaConResultado(Long org,UUID operacion,String huella){
        exigir(jdbc.update("UPDATE b0_operacion_ia SET estado='INCIERTA',huella_resultado=?,version=version+1 WHERE id_operacion_b0=b0_resolver_operacion(?,?) AND estado IN('EN_PROCESO','INCIERTA')",huella,org,operacion));
    }
    public List<Imputacion> imputaciones(Long org,UUID operacion){
        return jdbc.query("SELECT id_fuente,retenido,liquidado,orden FROM b0_imputacion WHERE organizacion_id=? AND id_operacion=? ORDER BY orden",
                (r,n)->new Imputacion(r.getObject(1,UUID.class),r.getBigDecimal(2),r.getBigDecimal(3),r.getInt(4)),org,operacion);
    }
    public List<Plaza> plazas(long org,UUID ciclo,String tipo){
        return jdbc.query("SELECT tipo,numero,id_persona,id_fuente FROM b0_plaza WHERE organizacion_id=? AND id_ciclo=? AND tipo=? ORDER BY numero FOR UPDATE",
                (r,n)->new Plaza(r.getString(1),r.getInt(2),r.getObject(3,Long.class),r.getObject(4,UUID.class)),org,ciclo,tipo);
    }
    public boolean conservaPertenencia(long org,long persona,String tipo,java.time.LocalDate hoy){
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM usuario_organizacion u JOIN persona_rol r
              ON r.organizacion_id=u.organizacion_id AND r.id_persona=u.id_persona
              WHERE u.organizacion_id=? AND u.id_persona=? AND u.estado='A' AND u.rol=?
                AND r.tipo_rol=? AND r.vigencia_desde<=? AND (r.vigencia_hasta IS NULL OR r.vigencia_hasta>=?))
            """,Boolean.class,org,persona,tipo,tipo,hoy,hoy));
    }
    public void asignarPlaza(long org,UUID ciclo,Plaza p,long persona){
        exigir(jdbc.update("UPDATE b0_plaza SET id_persona=? WHERE organizacion_id=? AND id_ciclo=? AND tipo=? AND numero=? AND id_persona IS NOT DISTINCT FROM ?",
                persona,org,ciclo,p.tipo(),p.numero(),p.persona()));
        if("AGENTE".equals(p.tipo()))exigir(jdbc.update("UPDATE b0_fuente_capacidad SET id_persona_destinataria=? WHERE organizacion_id=? AND id_fuente=? AND origen='MENSUAL_AGENTE'",persona,org,p.fuente()));
    }
    public Plaza crearPlazaBroker(long org,UUID ciclo,int numero){
        UUID fuente=jdbc.queryForObject("SELECT id_fuente FROM b0_fuente_capacidad WHERE organizacion_id=? AND id_ciclo=? AND origen='MENSUAL_BROKER'",UUID.class,org,ciclo);
        jdbc.update("INSERT INTO b0_plaza(organizacion_id,id_ciclo,tipo,numero,id_fuente) VALUES (?,?,'BROKER',?,?)",org,ciclo,numero,fuente);
        return new Plaza("BROKER",numero,null,fuente);
    }
    public void liquidarFuente(Long org,UUID operacion,Imputacion i,BigDecimal consumido){
        exigir(jdbc.update("UPDATE b0_fuente_capacidad SET retenido=retenido-?,liquidado=liquidado+? WHERE organizacion_id=? AND id_fuente=? AND retenido>=?",
                i.retenido(),consumido,org,i.fuente(),i.retenido()));
        exigir(jdbc.update("UPDATE b0_imputacion SET liquidado=? WHERE organizacion_id=? AND id_operacion=? AND id_fuente=? AND liquidado IS NULL",
                consumido,org,operacion,i.fuente()));
    }
    public void liquidarFondo(UUID id,BigDecimal retenido,BigDecimal consumido){
        exigir(jdbc.update("UPDATE b0_fondo_tecnico SET retenido=retenido-?,liquidado=liquidado+?,version=version+1 WHERE id_fondo=? AND retenido>=?",retenido,consumido,id,retenido));
    }
    public void cerrar(Long org,UUID id,String estado,BigDecimal creditos,BigDecimal costo,String huella,Instant ahora){
        exigir(jdbc.update("""
            UPDATE b0_operacion_ia SET estado=?,creditos_liquidados=?,costo_tecnico_usd=?,huella_resultado=?,liquidada_en=?,version=version+1
            WHERE id_operacion_b0=b0_resolver_operacion(?,?) AND estado NOT IN('LIQUIDADA','CANCELADA')
            """,estado,creditos,costo,huella,utc(ahora),org,id));
    }
    public Optional<String> huellaMedicion(Long org,UUID operacion,UUID medicion){
        return jdbc.query("SELECT huella FROM b0_medicion_tecnica WHERE id_operacion_b0=b0_resolver_operacion(?,?) AND id_medicion=?",(r,n)->r.getString(1),org,operacion,medicion).stream().findFirst();
    }
    public Set<UUID> idsMediciones(Long org,UUID operacion){
        return new HashSet<>(jdbc.query("SELECT id_medicion FROM b0_medicion_tecnica WHERE id_operacion_b0=b0_resolver_operacion(?,?)",(r,n)->r.getObject(1,UUID.class),org,operacion));
    }
    /** Fence del mismo padre que toma el trigger de etapa, sin reordenar org/O/J. */
    public void bloquearOperacion(Long org,UUID operacion){jdbc.query("SELECT id_operacion FROM b0_operacion_ia WHERE id_operacion_b0=b0_resolver_operacion(?,?) FOR NO KEY UPDATE",(r,n)->r.getObject(1,UUID.class),org,operacion);}
    public Set<UUID> idsMedicionesEtapas(Long org,UUID operacion){return Set.copyOf(jdbc.query("SELECT DISTINCT m.id::uuid FROM b0_etapa_operacion_ia e CROSS JOIN LATERAL jsonb_array_elements_text(e.mediciones) m(id) WHERE e.id_operacion_b0=b0_resolver_operacion(?,?) AND e.estado='MEDIDA'",(r,n)->r.getObject(1,UUID.class),org,operacion));}
    public record Etapa(String etapa,String clave,String proveedor,String peticion,String estado,String resultado,Instant reclamada,String prueba,Instant programada){}
    public Optional<Etapa> etapa(Long org,UUID operacion,String clave){
        return jdbc.query("SELECT etapa,clave,proveedor,huella_peticion,estado,resultado_hash,reclamada_en,prueba::text,programada_en FROM b0_etapa_operacion_ia WHERE id_operacion_b0=b0_resolver_operacion(?,?) AND clave=?",
                (r,n)->new Etapa(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getTimestamp(7)==null?null:r.getTimestamp(7).toInstant(),r.getString(8),r.getTimestamp(9).toInstant()),org,operacion,clave).stream().findFirst();
    }
    public boolean etapasPendientes(Long org,UUID operacion){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM b0_etapa_operacion_ia WHERE id_operacion_b0=b0_resolver_operacion(?,?) AND estado NOT IN('MEDIDA','NO_INVOCADA'))",Boolean.class,org,operacion));}
    public void programarEtapa(Long org,UUID operacion,String etapa,String clave,String proveedor,String peticion){
        jdbc.update("INSERT INTO b0_etapa_operacion_ia(organizacion_id,id_operacion,id_operacion_b0,id_caso_evaluacion,etapa,clave,proveedor,huella_peticion,estado) SELECT organizacion_id,id_operacion,id_operacion_b0,id_caso_evaluacion,?,?,?,?,'PENDIENTE' FROM b0_operacion_ia WHERE id_operacion_b0=b0_resolver_operacion(?,?) ON CONFLICT DO NOTHING",etapa,clave,proveedor,peticion,org,operacion);
    }
    public boolean reclamarEtapa(Long org,UUID operacion,String clave){return jdbc.update("UPDATE b0_etapa_operacion_ia SET estado='RECLAMADA',reclamada_en=clock_timestamp() WHERE id_operacion_b0=b0_resolver_operacion(?,?) AND clave=? AND estado='PENDIENTE'",org,operacion,clave)==1;}
    public void resolverEtapa(Long org,UUID operacion,String clave,String estado,String hash,String ids,String prueba){
        exigir(jdbc.update("UPDATE b0_etapa_operacion_ia SET estado=?,resultado_hash=?,mediciones=CAST(? AS jsonb),prueba=CAST(? AS jsonb),resuelta_en=CASE WHEN ? IN('MEDIDA','NO_INVOCADA') THEN clock_timestamp() END WHERE id_operacion_b0=b0_resolver_operacion(?,?) AND clave=? AND (estado IN('RECLAMADA','INCIERTA') OR (estado='PENDIENTE' AND ?='NO_INVOCADA'))",estado,hash,ids,prueba,estado,org,operacion,clave,estado));
    }
    public Instant ahoraEtapas(){return jdbc.queryForObject("SELECT clock_timestamp()",java.time.OffsetDateTime.class).toInstant();}
    public boolean medicion(Long org,UUID operacion,UUID id,String proveedor,String modelo,String tarifa,String unidades,String tarifas,
            BigDecimal costo,String estado,String referencia,String huella,String identidadIntento,Instant ahora,String etapa){
        return jdbc.update("""
            INSERT INTO b0_medicion_tecnica(organizacion_id,id_operacion,id_operacion_b0,id_caso_evaluacion,id_medicion,proveedor,modelo,version_tarifa,unidades_originales,tarifas_originales,
                costo_usd,estado_costo,referencia_fuente,huella,huella_intento,registrada_en,etapa_clave) SELECT organizacion_id,id_operacion,id_operacion_b0,id_caso_evaluacion,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?,?,?,? FROM b0_operacion_ia WHERE id_operacion_b0=b0_resolver_operacion(?,?) ON CONFLICT DO NOTHING
            """,id,proveedor,modelo,tarifa,unidades,tarifas,costo,estado,referencia,huella,identidadIntento,utc(ahora),etapa,org,operacion)==1;
    }
    public List<String> respuestaEvaluacion(UUID operacion,String clave){
        return jdbc.query("SELECT contenido::text FROM b0_antecedente WHERE id_operacion_b0=? AND organizacion_id IS NULL AND hecho='RESPUESTA_EVALUACION' AND contenido->>'claveIntento'=? ORDER BY id_antecedente LIMIT 2",
                (r,n)->r.getString(1),operacion,clave);
    }
    public List<String> respuestasEvaluacion(UUID operacion){
        return jdbc.query("SELECT contenido::text FROM b0_antecedente WHERE id_operacion_b0=? AND organizacion_id IS NULL AND hecho='RESPUESTA_EVALUACION' ORDER BY id_antecedente",
                (r,n)->r.getString(1),operacion);
    }
    public Optional<String> observacionHttp(Long org,UUID operacion,String clave){
        return jdbc.query("SELECT contenido::text FROM b0_antecedente WHERE id_operacion_b0=b0_resolver_operacion(?,?) AND hecho='OBSERVACION_HTTP_IA' AND contenido->>'claveIntento'=?",
                (r,n)->r.getString(1),org,operacion,clave).stream().findFirst();
    }
    public void antecedente(Long org,UUID operacion,String hecho,String contenido,Instant ahora){
        if(operacion==null){
            if(org==null)throw new IllegalArgumentException("El antecedente requiere dueño.");
            jdbc.update("INSERT INTO b0_antecedente(organizacion_id,hecho,contenido,registrado_en) VALUES(?, ?,CAST(? AS jsonb),?)",org,hecho,contenido,utc(ahora));return;
        }
        jdbc.update("""
            INSERT INTO b0_antecedente(organizacion_id,id_operacion,id_operacion_b0,id_caso_evaluacion,hecho,contenido,registrado_en)
            SELECT organizacion_id,id_operacion,id_operacion_b0,id_caso_evaluacion,?,CAST(? AS jsonb),?
            FROM b0_operacion_ia WHERE id_operacion_b0=b0_resolver_operacion(?,?)
            """,hecho,contenido,utc(ahora),org,operacion);
    }
    private static Instant instante(java.sql.ResultSet r,int i)throws java.sql.SQLException{return r.getObject(i,OffsetDateTime.class).toInstant();}

    public record ProyeccionCosto(UUID operacion,BigDecimal efectivo,BigDecimal imputado,BigDecimal pendiente,String huella,long version){}
    public record LineaCosto(UUID operacion,String estado,BigDecimal maximo,BigDecimal cierre,UUID medicion,
            BigDecimal original,BigDecimal confirmado,UUID confirmacion){}
    private static final String LINEAS_COSTO="""
        SELECT o.id_operacion_b0,o.estado,o.maximo_tecnico_usd,o.costo_tecnico_usd,m.id_medicion,m.costo_usd,
          (h.contenido->>'costoTotalUsd')::numeric confirmado,(h.contenido->>'confirmacion')::uuid confirmacion
        FROM b0_operacion_ia o LEFT JOIN b0_medicion_tecnica m USING(id_operacion_b0)
        LEFT JOIN LATERAL(SELECT a.contenido FROM b0_antecedente a
          WHERE a.id_operacion_b0=o.id_operacion_b0 AND a.hecho='CONFIRMACION_COSTO_IA'
            AND a.contenido->>'medicion'=m.id_medicion::text ORDER BY a.id_antecedente DESC LIMIT 1) h ON true
        """;
    private static LineaCosto lineaCosto(ResultSet r,int n)throws SQLException{
        return new LineaCosto(r.getObject(1,UUID.class),r.getString(2),r.getBigDecimal(3),r.getBigDecimal(4),
            r.getObject(5,UUID.class),r.getBigDecimal(6),r.getBigDecimal(7),r.getObject(8,UUID.class));
    }
    /** Una consulta para reconstruir las proyecciones antiguas que faltan, nunca N+1. Bajo lock de fondo. */
    public List<LineaCosto> costosSinProyeccion(UUID fondo){return jdbc.query(LINEAS_COSTO+" WHERE o.id_fondo=? AND NOT EXISTS(SELECT 1 FROM b0_costo_operacion_ia p WHERE p.id_operacion_b0=o.id_operacion_b0) ORDER BY o.id_operacion_b0,m.id_medicion",PresupuestoIaRepository::lineaCosto,fondo);}
    public List<LineaCosto> costosOperacion(UUID operacion){return jdbc.query(LINEAS_COSTO+" WHERE o.id_operacion_b0=? ORDER BY m.id_medicion",PresupuestoIaRepository::lineaCosto,operacion);}
    public Optional<ProyeccionCosto> proyeccionCosto(UUID operacion){return jdbc.query("SELECT id_operacion_b0,costo_efectivo_usd,costo_imputado_usd,pendiente_positivo_usd,conjunto_hash,version FROM b0_costo_operacion_ia WHERE id_operacion_b0=?",
        (r,n)->new ProyeccionCosto(r.getObject(1,UUID.class),r.getBigDecimal(2),r.getBigDecimal(3),r.getBigDecimal(4),r.getString(5),r.getLong(6)),operacion).stream().findFirst();}
    public void crearProyeccionesCosto(List<ProyeccionCosto> filas){
        jdbc.batchUpdate("INSERT INTO b0_costo_operacion_ia(id_operacion_b0,costo_efectivo_usd,costo_imputado_usd,pendiente_positivo_usd,conjunto_hash,version) VALUES(?,?,?,?,?,?)",
            filas.stream().map(p->new Object[]{p.operacion(),p.efectivo(),p.imputado(),p.pendiente(),p.huella(),p.version()}).toList());
    }
    public void actualizarProyeccionCosto(ProyeccionCosto anterior,ProyeccionCosto nueva){
        exigir(jdbc.update("UPDATE b0_costo_operacion_ia SET costo_efectivo_usd=?,costo_imputado_usd=?,pendiente_positivo_usd=?,conjunto_hash=?,version=? WHERE id_operacion_b0=? AND version=?",
            nueva.efectivo(),nueva.imputado(),nueva.pendiente(),nueva.huella(),nueva.version(),nueva.operacion(),anterior.version()));
    }
    public BigDecimal pendienteCostoFondo(UUID fondo){return jdbc.queryForObject("SELECT COALESCE(sum(p.pendiente_positivo_usd),0) FROM b0_costo_operacion_ia p JOIN b0_operacion_ia o USING(id_operacion_b0) WHERE o.id_fondo=?",BigDecimal.class,fondo);}
    /** Sólo diferencia técnica; no retención ni fuente comercial. Caller conserva hecho y toma fondo. */
    public void aplicarDiferenciaCosto(UUID fondo,BigDecimal diferencia){
        exigir(jdbc.update("UPDATE b0_fondo_tecnico SET liquidado=liquidado+?,version=version+1 WHERE id_fondo=? AND liquidado+?>=0 AND financiado-retenido-liquidado>=?",diferencia,fondo,diferencia,diferencia));
    }
    public record MedicionOriginalCosto(UUID id,String proveedor,String huella){}
    public Optional<MedicionOriginalCosto> medicionOriginalCosto(UUID operacion,UUID medicion){return jdbc.query("SELECT id_medicion,proveedor,huella FROM b0_medicion_tecnica WHERE id_operacion_b0=? AND id_medicion=?",
        (r,n)->new MedicionOriginalCosto(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),operacion,medicion).stream().findFirst();}
    public Optional<String> fuenteCosto(String natural){return jdbc.query("SELECT contenido::text FROM b0_antecedente WHERE hecho='FUENTE_COSTO_IA' AND contenido->>'fuenteNaturalHash'=?",(r,n)->r.getString(1),natural).stream().findFirst();}
    public Optional<String> confirmacionCosto(UUID id){return jdbc.query("SELECT contenido::text FROM b0_antecedente WHERE hecho='CONFIRMACION_COSTO_IA' AND contenido->>'confirmacion'=?",(r,n)->r.getString(1),id.toString()).stream().findFirst();}
    public Optional<UUID> ultimaConfirmacionCosto(UUID operacion,UUID medicion){return jdbc.query("SELECT (contenido->>'confirmacion')::uuid FROM b0_antecedente WHERE id_operacion_b0=? AND hecho='CONFIRMACION_COSTO_IA' AND contenido->>'medicion'=? ORDER BY id_antecedente DESC LIMIT 1",(r,n)->r.getObject(1,UUID.class),operacion,medicion.toString()).stream().findFirst();}

    private static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
    private static void exigir(int n){if(n!=1)throw new org.springframework.dao.OptimisticLockingFailureException("La reserva cambió durante la operación.");}
}
