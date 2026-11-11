package com.controllocal.persistence.presupuesto;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Productor de derechos desde una compra acreditada; no verifica fondos por sí mismo. */
@Repository
public class CiclosPagadosB0Repository {
    private final JdbcTemplate jdbc;
    public CiclosPagadosB0Repository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Linea(BigDecimal financiado,Instant desde,Instant hasta){}
    public Optional<String> acreditacion(long org,UUID pedido){
        return jdbc.query("SELECT huella FROM b0_acreditacion_pago WHERE organizacion_id=? AND id_pedido=?",(r,n)->r.getString(1),org,pedido).stream().findFirst();
    }
    public boolean acreditar(long org,UUID pedido,String proveedor,String transaccion,String huella,String contenido,Instant ahora){
        return jdbc.update("INSERT INTO b0_acreditacion_pago(organizacion_id,id_pedido,proveedor,transaccion,huella,contenido,registrada_en) VALUES (?,?,?,?,?,CAST(? AS jsonb),?) ON CONFLICT DO NOTHING",
                org,pedido,proveedor,transaccion,huella,contenido,utc(ahora))==1;
    }
    public boolean tieneFuturo(long org,Instant corte){
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM b0_ciclo WHERE organizacion_id=? AND desde>? AND cerrado_en IS NULL AND NOT revocado)",Boolean.class,org,utc(corte)));
    }
    public List<Linea> lineas(long org,UUID ciclo){
        return jdbc.query("SELECT financiado_usd,desde,hasta FROM b0_linea_financiada WHERE organizacion_id=? AND id_ciclo=? ORDER BY id_linea",
                (r,n)->new Linea(r.getBigDecimal(1),r.getObject(2,OffsetDateTime.class).toInstant(),r.getObject(3,OffsetDateTime.class).toInstant()),org,ciclo);
    }
    public void nuevoCiclo(long org,UUID ciclo,String plan,Instant desde,Instant hasta,int aniversario,BigDecimal financiado,UUID pedido,
            String evidencia,int extras,Instant ahora){
        jdbc.update("""
            INSERT INTO b0_ciclo(organizacion_id,id_ciclo,plan,desde,hasta,dia_aniversario,importe_financiado_usd,orden_verificada,evidencia_pago,registrado_en,brokers_extra)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """,org,ciclo,plan,utc(desde),utc(hasta),aniversario,financiado,pedido,evidencia,utc(ahora),extras);
    }
    public void cerrarCiclo(long org,UUID ciclo,long version,Instant cuando){
        exigir(jdbc.update("UPDATE b0_ciclo SET cerrado_en=?,version=version+1 WHERE organizacion_id=? AND id_ciclo=? AND version=? AND cerrado_en IS NULL",utc(cuando),org,ciclo,version));
    }
    public void linea(long org,UUID ciclo,UUID pedido,String concepto,BigDecimal monto,Instant desde,Instant hasta){
        jdbc.update("INSERT INTO b0_linea_financiada(organizacion_id,id_linea,id_ciclo,id_pedido,concepto,financiado_usd,desde,hasta) VALUES (?,?,?,?,?,?,?,?)",org,UUID.randomUUID(),ciclo,pedido,concepto,monto,utc(desde),utc(hasta));
    }
    public UUID fuente(long org,UUID ciclo,String tipo,Integer plaza,BigDecimal cantidad,String origen,Instant desde,Instant vence,Instant ahora){
        UUID fuente=UUID.randomUUID();
        jdbc.update("INSERT INTO b0_fuente_capacidad(organizacion_id,id_fuente,id_ciclo,origen,plaza,inicial,origen_verificado,desde,vence,registrado_en) VALUES (?,?,?,?,?,?,?,?,?,?)",
                org,fuente,ciclo,tipo,plaza,cantidad,origen,utc(desde),utc(vence),utc(ahora));return fuente;
    }
    public void plaza(long org,UUID ciclo,String tipo,int numero,UUID fuente){
        jdbc.update("INSERT INTO b0_plaza(organizacion_id,id_ciclo,tipo,numero,id_fuente) VALUES (?,?,?,?,?)",org,ciclo,tipo,numero,fuente);
    }
    public Optional<UUID> fuenteBroker(long org,UUID ciclo){
        return jdbc.query("SELECT id_fuente FROM b0_fuente_capacidad WHERE organizacion_id=? AND id_ciclo=? AND origen='MENSUAL_BROKER'",(r,n)->r.getObject(1,UUID.class),org,ciclo).stream().findFirst();
    }
    public void brokerAdicional(long org,UUID ciclo,long version,int extras){
        exigir(jdbc.update("UPDATE b0_ciclo SET brokers_extra=?,version=version+1 WHERE organizacion_id=? AND id_ciclo=? AND version=?",extras,org,ciclo,version));
    }
    private static void exigir(int n){if(n!=1)throw new org.springframework.dao.OptimisticLockingFailureException("Cambió el ciclo financiado.");}
    private static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
}
