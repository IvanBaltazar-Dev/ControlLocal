package com.controllocal.persistence.presupuesto;

import java.time.*;
import java.util.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AtribucionesPlataformaRepository {
    private final JdbcTemplate jdbc;
    public AtribucionesPlataformaRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Concesion(UUID id,long otorgante,long cuenta,String atribucion,UUID plan,String version,
        UUID caso,UUID fondo,String finalidad,String acciones,String campos,Instant desde,Instant hasta,String evidencia,String huella,
        Instant revocada,String recursoCompra,String recursoMercado) {
        public Concesion(UUID id,long otorgante,long cuenta,String atribucion,UUID plan,String version,UUID caso,UUID fondo,
                String finalidad,String acciones,String campos,Instant desde,Instant hasta,String evidencia,String huella,Instant revocada){
            this(id,otorgante,cuenta,atribucion,plan,version,caso,fondo,finalidad,acciones,campos,desde,hasta,evidencia,huella,revocada,null,null);
        }
        public Concesion(UUID id,long otorgante,long cuenta,String atribucion,UUID plan,String version,UUID caso,UUID fondo,String finalidad,String acciones,String campos,Instant desde,Instant hasta,String evidencia,String huella,Instant revocada,String compra){this(id,otorgante,cuenta,atribucion,plan,version,caso,fondo,finalidad,acciones,campos,desde,hasta,evidencia,huella,revocada,compra,null);}
    }
    private static final String SELECCION="""
        SELECT id,otorgante,cuenta,atribucion,plan,version_plan,finalidad,acciones::text,campos::text,
               desde,hasta,evidencia,huella,revocada_en,caso,fondo,recurso_compra,recurso_mercado
        FROM b0_atribucion_plataforma
        """;
    public Optional<Concesion> bloquear(UUID id) {
        return jdbc.query(SELECCION+" WHERE id=? FOR SHARE",AtribucionesPlataformaRepository::fila,id).stream().findFirst();
    }
    /** Filtra por cuenta y vigencia antes de entregar opciones; ejecutar siempre revalida bajo lock. */
    public List<Concesion> comprasVigentes(long cuenta,Instant ahora){
        return jdbc.query(SELECCION+"""
            WHERE cuenta=? AND atribucion='FINANCIERO_PLATFORM' AND recurso_compra='COMPRAS_MANUALES_WEB_MVP'
              AND revocada_en IS NULL AND desde<=? AND ?<hasta
            ORDER BY hasta,id
            """,AtribucionesPlataformaRepository::fila,cuenta,utc(ahora),utc(ahora));
    }
    public List<Concesion> mercadoVigentes(long cuenta,Instant ahora){
        return jdbc.query(SELECCION+" WHERE cuenta=? AND recurso_mercado='MERCADO_MANUAL_I0' AND revocada_en IS NULL AND desde<=? AND ?<hasta ORDER BY hasta,id",AtribucionesPlataformaRepository::fila,cuenta,utc(ahora),utc(ahora));
    }
    private static Concesion fila(ResultSet r,int n)throws SQLException{
        return new Concesion(r.getObject(1,UUID.class),r.getLong(2),r.getLong(3),r.getString(4),
            r.getObject(5,UUID.class),r.getString(6),r.getObject(15,UUID.class),r.getObject(16,UUID.class),r.getString(7),r.getString(8),r.getString(9),
            r.getTimestamp(10).toInstant(),r.getTimestamp(11).toInstant(),r.getString(12),r.getString(13),
            r.getTimestamp(14)==null?null:r.getTimestamp(14).toInstant(),r.getString(17),r.getString(18));
    }
    public Optional<Concesion> bloquearParaRevocar(UUID id){
        jdbc.query("SELECT id FROM b0_atribucion_plataforma WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),id);return bloquear(id);
    }
    public void insertar(Concesion c,UUID decision,Instant ahora) {
        jdbc.update("""
            INSERT INTO b0_atribucion_plataforma(id,otorgante,cuenta,atribucion,plan,version_plan,
              finalidad,acciones,campos,desde,hasta,evidencia,huella,decision,registrada_en,caso,fondo,recurso_compra,recurso_mercado)
            VALUES(?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?,?,?,?,?,?,?)
            """,c.id(),c.otorgante(),c.cuenta(),c.atribucion(),c.plan(),c.version(),c.finalidad(),
            c.acciones(),c.campos(),utc(c.desde()),utc(c.hasta()),c.evidencia(),c.huella(),decision,utc(ahora),c.caso(),c.fondo(),c.recursoCompra(),c.recursoMercado());
    }
    public record FuenteDecision(long otorgante,String actuacion,String huella,String evidencia,Instant acreditada){}
    public Optional<FuenteDecision> fuenteDecision(UUID id){
        return jdbc.query("SELECT otorgante,actuacion,huella,evidencia,acreditada_en FROM b0_decision_plataforma WHERE id=?",
                (r,n)->new FuenteDecision(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getObject(5,OffsetDateTime.class).toInstant()),id).stream().findFirst();
    }
    public record DecisionRegistrada(long cuenta,String accion,String huella) {}
    public Optional<DecisionRegistrada> decision(UUID id) {
        return jdbc.query("SELECT otorgante,actuacion,huella FROM b0_decision_plataforma WHERE id=?",
            (r,n)->new DecisionRegistrada(r.getLong(1),r.getString(2),r.getString(3)),id).stream().findFirst();
    }
    public void decision(UUID id,long cuenta,String accion,String hash,String evidencia,Instant acreditada,Instant ahora) {
        jdbc.update("""
            INSERT INTO b0_decision_plataforma(id,otorgante,actuacion,huella,evidencia,acreditada_en,registrada_en)
            VALUES(?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
            """,id,cuenta,accion,hash,evidencia,utc(acreditada),utc(ahora));
    }
    public boolean revocar(UUID id,long actor,UUID decision,String motivo,Instant ahora) {
        return jdbc.update("""
            UPDATE b0_atribucion_plataforma SET revocada_en=?,revocada_por=?,decision_revocacion=?,motivo_revocacion=?
            WHERE id=? AND revocada_en IS NULL
            """,utc(ahora),actor,decision,motivo,id)==1;
    }
    public static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
}
