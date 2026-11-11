package com.controllocal.persistence.materialia;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;

/** Sólo persistencia privada append-only E44; no acredita legitimación por existencia de una fila. */
@Repository
public class RegistroTratamientoMaterialIaRepository {
    public record Aviso(UUID id,String referencia,long version,String huella,String contenido) { }
    public record Presentacion(UUID id,long cuenta,long organizacion,String alcance,String sesion,long versionCobertura,
                               UUID aviso,String ambito,String huellaAmbito) { }
    public record DecisionFila(UUID evento,long cuenta,long organizacion,String alcance,long version,
            UUID presentacion,String decision,UUID aviso,String datos,String huella,Instant registradaEn) { }
    private final JdbcTemplate jdbc;
    public RegistroTratamientoMaterialIaRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void bloquear(long clave){jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(?)",Integer.class,clave);}
    public Aviso registrarAviso(String referencia,long version,String huella,String contenido){
        jdbc.update("INSERT INTO material_ia_aviso_e44(id_aviso,referencia_servidor,version_aviso,huella_aviso,contenido) VALUES(?,?,?,?,?) ON CONFLICT(referencia_servidor,version_aviso) DO NOTHING",
                UUID.randomUUID(),referencia,version,huella,contenido);
        var a=jdbc.queryForObject("SELECT id_aviso,referencia_servidor,version_aviso,huella_aviso,contenido FROM material_ia_aviso_e44 WHERE referencia_servidor=? AND version_aviso=?",
                (r,n)->new Aviso(r.getObject(1,UUID.class),r.getString(2),r.getLong(3),r.getString(4),r.getString(5)),referencia,version);
        if(a==null||!a.huella().equals(huella)||!a.contenido().equals(contenido))throw new IllegalStateException("Una versión de aviso no puede cambiar su contenido.");
        return a;
    }
    public Optional<Aviso> aviso(UUID id){
        return jdbc.query("SELECT id_aviso,referencia_servidor,version_aviso,huella_aviso,contenido FROM material_ia_aviso_e44 WHERE id_aviso=?",
                (r,n)->new Aviso(r.getObject(1,UUID.class),r.getString(2),r.getLong(3),r.getString(4),r.getString(5)),id).stream().findFirst();
    }
    public void presentar(Presentacion p){
        jdbc.update("INSERT INTO material_ia_presentacion_e44(id_presentacion,id_cuenta,organizacion_id,ambito_huella,sesion_huella,version_cobertura,id_aviso,ambito,huella_ambito) VALUES(?,?,?,?,?,?,?,?,?)",
                p.id(),p.cuenta(),p.organizacion(),p.alcance(),p.sesion(),p.versionCobertura(),p.aviso(),p.ambito(),p.huellaAmbito());
    }
    public Optional<Presentacion> presentacion(UUID id,long cuenta,long org){
        return jdbc.query("SELECT id_presentacion,id_cuenta,organizacion_id,ambito_huella,sesion_huella,version_cobertura,id_aviso,ambito,huella_ambito FROM material_ia_presentacion_e44 WHERE id_presentacion=? AND id_cuenta=? AND organizacion_id=?",
                (r,n)->new Presentacion(r.getObject(1,UUID.class),r.getLong(2),r.getLong(3),r.getString(4),r.getString(5),r.getLong(6),r.getObject(7,UUID.class),r.getString(8),r.getString(9)),id,cuenta,org).stream().findFirst();
    }
    private static final String COLUMNAS="id_evento,id_cuenta,organizacion_id,ambito_huella,version,id_presentacion,decision,id_aviso,datos,huella_datos,registrada_en";
    public Optional<DecisionFila> ultima(long cuenta,long org,String alcance){
        return jdbc.query("SELECT "+COLUMNAS+" FROM material_ia_decision_e44 WHERE id_cuenta=? AND organizacion_id=? AND ambito_huella=? ORDER BY version DESC LIMIT 1",this::decision,cuenta,org,alcance).stream().findFirst();
    }
    public Optional<DecisionFila> evento(long cuenta,UUID evento){
        return jdbc.query("SELECT "+COLUMNAS+" FROM material_ia_decision_e44 WHERE id_cuenta=? AND id_evento=?",this::decision,cuenta,evento).stream().findFirst();
    }
    /** Evidencia interna del artefacto organizacional. Una referencia ambigua nunca elige la primera cuenta. */
    public List<DecisionFila> eventosDeOrganizacion(long org,UUID evento){
        return jdbc.query("SELECT "+COLUMNAS+" FROM material_ia_decision_e44 WHERE organizacion_id=? AND id_evento=?",this::decision,org,evento);
    }
    public DecisionFila decidir(DecisionFila d){
        jdbc.update("INSERT INTO material_ia_decision_e44(id_evento,id_cuenta,organizacion_id,ambito_huella,version,id_presentacion,decision,id_aviso,datos,huella_datos) VALUES(?,?,?,?,?,?,?,?,?,?)",
                d.evento(),d.cuenta(),d.organizacion(),d.alcance(),d.version(),d.presentacion(),d.decision(),d.aviso(),d.datos(),d.huella());
        return evento(d.cuenta(),d.evento()).orElseThrow();
    }
    private DecisionFila decision(java.sql.ResultSet r,int n)throws java.sql.SQLException {
        return new DecisionFila(r.getObject(1,UUID.class),r.getLong(2),r.getLong(3),r.getString(4),r.getLong(5),r.getObject(6,UUID.class),r.getString(7),r.getObject(8,UUID.class),r.getString(9),r.getString(10),r.getTimestamp(11).toInstant());
    }
}
