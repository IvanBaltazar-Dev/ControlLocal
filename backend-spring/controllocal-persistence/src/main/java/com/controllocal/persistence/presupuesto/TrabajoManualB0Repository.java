package com.controllocal.persistence.presupuesto;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Extensión manual de la misma operación B0 y sus imputaciones; no otro saldo o ledger. */
@Repository
public class TrabajoManualB0Repository {
    public record Regla(UUID id,String version,String unidad,BigDecimal creditosPorRonda,BigDecimal cota,
        BigDecimal reservadas,BigDecimal realizadas,Instant desde,Instant hasta,UUID decision,long responsable,String evidencia,String huella,boolean habilitada,String financiacion) { }
    public record Manual(UUID regla,String version,BigDecimal rondasPrevistas,BigDecimal rondasAcreditadas) { }
    private final JdbcTemplate jdbc;
    public TrabajoManualB0Repository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Optional<Regla> regla(UUID id,String version,boolean bloqueo){
        return jdbc.query("select id,version,unidad,creditos_por_ronda,cota_rondas,reservadas,realizadas,desde,hasta,decision,responsable,evidencia,huella,habilitada,financiacion from b0_regla_trabajo_manual where id=? and version=?"+(bloqueo?" for update":""),
            (r,n)->new Regla(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getBigDecimal(4),r.getBigDecimal(5),r.getBigDecimal(6),r.getBigDecimal(7),r.getTimestamp(8).toInstant(),r.getTimestamp(9).toInstant(),r.getObject(10,UUID.class),r.getLong(11),r.getString(12),r.getString(13),r.getBoolean(14),r.getString(15)),id,version).stream().findFirst();
    }
    public void insertarRegla(Regla r){
        jdbc.update("insert into b0_regla_trabajo_manual(id,version,unidad,creditos_por_ronda,cota_rondas,desde,hasta,decision,responsable,evidencia,huella,habilitada,financiacion) values(?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict do nothing",
            r.id(),r.version(),r.unidad(),r.creditosPorRonda(),r.cota(),utc(r.desde()),utc(r.hasta()),r.decision(),r.responsable(),r.evidencia(),r.huella(),r.habilitada(),r.financiacion());
    }
    public Optional<Manual> manual(long org,UUID id,boolean bloqueo){
        return jdbc.query("select regla_manual,version_regla_manual,rondas_previstas,rondas_acreditadas from b0_operacion_ia where organizacion_id=? and id_operacion=? and modalidad='MANUAL_I0'"+(bloqueo?" for update":""),
            (r,n)->new Manual(r.getObject(1,UUID.class),r.getString(2),r.getBigDecimal(3),r.getBigDecimal(4)),org,id).stream().findFirst();
    }
    public void insertar(PresupuestoIaRepository.Operacion o,UUID regla,String version,BigDecimal unidades){
        jdbc.update("""
            insert into b0_operacion_ia(organizacion_id,id_operacion,id_persona,id_rol,recurso_tipo,recurso_id,finalidad,
              huella_origen,huella_peticion,estado,maximo_creditos,admitida_en,id_operacion_b0,modalidad,regla_manual,version_regla_manual,rondas_previstas,aceptada_en)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,'MANUAL_I0',?,?,?,?)
            """,o.organizacion(),o.id(),o.persona(),o.rol(),o.tipo(),o.recurso(),o.finalidad(),o.origen(),o.peticion(),o.estado(),o.maximoCreditos(),utc(o.admitida()),o.tecnica(),regla,version,unidades,utc(o.admitida()));
    }
    public void cerrar(long org,UUID id,String estado,BigDecimal realizadas,BigDecimal creditos,String hash,Instant ahora){
        int n=jdbc.update("""
            update b0_operacion_ia set estado=?,rondas_acreditadas=?,creditos_liquidados=?,huella_resultado=?,liquidada_en=?,version=version+1
             where organizacion_id=? and id_operacion=? and modalidad='MANUAL_I0' and estado not in('LIQUIDADA','CANCELADA')
            """,estado,realizadas,creditos,hash,utc(ahora),org,id);
        if(n!=1)throw new IllegalStateException("El cierre manual no conservó su fence.");
    }
    private static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
}
