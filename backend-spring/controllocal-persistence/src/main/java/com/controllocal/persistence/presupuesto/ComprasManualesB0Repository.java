package com.controllocal.persistence.presupuesto;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Persistencia de CAT-E38. No acredita fondos ni concede permisos. */
@Repository
public class ComprasManualesB0Repository {
    private final JdbcTemplate jdbc;
    public ComprasManualesB0Repository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Compra(UUID id,long organizacion,long creadaPor,String solicitudHuella,String terminos,Instant creadaEn,
            Long consentidaPor,Instant consentidaEn,String huellaConsentimiento,String estado,long version,
            String pagoInformado,String fondos,String incidencia){}
    public record Evento(String tipo,Instant registradaEn,String estado){}
    public Optional<Compra> consultar(UUID id){return leer(id,false);}
    public Optional<Compra> bloquear(UUID id){return leer(id,true);}
    public boolean verificacionRegistrada(UUID id){
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM b0_compra_manual c
             JOIN b0_pago_manual_identidad p ON p.id_compra=c.id_compra
             JOIN b0_compra_manual_evento e ON e.id_compra=c.id_compra AND e.tipo='FONDOS_VERIFICADOS'
             WHERE c.id_compra=? AND c.consentida_en IS NOT NULL AND c.fondos IS NOT NULL
              AND p.origen=c.fondos#>>'{verificacion,origen}' AND p.referencia=c.fondos#>>'{verificacion,referencia}'
              AND to_jsonb(e.cuenta)=c.fondos->'cuenta' AND e.concesion::text=c.fondos->>'concesion'
              AND e.contenido=c.fondos->'verificacion'
              AND EXISTS(SELECT 1 FROM b0_compra_manual_evento s WHERE s.id_compra=c.id_compra AND s.tipo='CONSENTIDA'
                AND s.cuenta=c.consentida_por AND s.contenido->>'huellaConsentimiento'=c.huella_consentimiento))
            """,Boolean.class,id));
    }
    private Optional<Compra> leer(UUID id,boolean lock){
        return jdbc.query("SELECT * FROM b0_compra_manual WHERE id_compra=?"+(lock?" FOR UPDATE":""),this::fila,id).stream().findFirst();
    }
    public boolean crear(UUID id,long org,long cuenta,String solicitud,String terminos,Instant ahora){
        return jdbc.update("INSERT INTO b0_compra_manual(id_compra,organizacion_id,creada_por,solicitud_huella,terminos,creada_en) VALUES (?,?,?,?,CAST(? AS jsonb),?) ON CONFLICT DO NOTHING",
                id,org,cuenta,solicitud,terminos,utc(ahora))==1;
    }
    public List<Compra> listar(Long org,int limite,int desplazamiento){
        return jdbc.query("SELECT * FROM b0_compra_manual"+(org==null?"":" WHERE organizacion_id=?")+" ORDER BY creada_en DESC,id_compra LIMIT ? OFFSET ?",
                this::fila,org==null?new Object[]{limite,desplazamiento}:new Object[]{org,limite,desplazamiento});
    }
    public void consentir(Compra c,long cuenta,String huella,Instant ahora){
        exigir(jdbc.update("UPDATE b0_compra_manual SET consentida_por=?,consentida_en=?,huella_consentimiento=?,version=version+1 WHERE id_compra=? AND version=? AND consentida_en IS NULL",
                cuenta,utc(ahora),huella,c.id(),c.version()));
    }
    public void informar(Compra c,String pago){
        exigir(jdbc.update("UPDATE b0_compra_manual SET pago_informado=CAST(? AS jsonb),estado='EN_VERIFICACION',incidencia=NULL,version=version+1 WHERE id_compra=? AND version=?",
                pago,c.id(),c.version()));
    }
    public boolean reservarPago(UUID id,String origen,String referencia){
        return jdbc.update("INSERT INTO b0_pago_manual_identidad(origen,referencia,id_compra) VALUES (?,?,?) ON CONFLICT DO NOTHING",origen,referencia,id)==1;
    }
    public boolean mismoPago(UUID id,String origen,String referencia){
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM b0_pago_manual_identidad WHERE origen=? AND referencia=? AND id_compra=?)",Boolean.class,origen,referencia,id));
    }
    public void verificar(Compra c,String fondos){
        exigir(jdbc.update("UPDATE b0_compra_manual SET fondos=CAST(? AS jsonb),estado='EN_VERIFICACION',incidencia=NULL,version=version+1 WHERE id_compra=? AND version=? AND fondos IS NULL",
                fondos,c.id(),c.version()));
    }
    public void estado(Compra c,String estado,String incidencia){
        exigir(jdbc.update("UPDATE b0_compra_manual SET estado=?,incidencia=?,version=version+1 WHERE id_compra=? AND version=?",estado,incidencia,c.id(),c.version()));
    }
    public Optional<String> comando(UUID id,UUID comando){
        return jdbc.query("SELECT huella FROM b0_compra_manual_evento WHERE id_compra=? AND id_comando=?",(r,n)->r.getString(1),id,comando).stream().findFirst();
    }
    public void evento(Compra c,UUID comando,long cuenta,UUID concesion,String tipo,String huella,String contenido,Instant ahora){
        jdbc.update("INSERT INTO b0_compra_manual_evento(id_compra,id_comando,cuenta,concesion,tipo,huella,contenido,version_compra,estado,registrada_en) VALUES (?,?,?,?,?,?,CAST(? AS jsonb),?,?,?)",
                c.id(),comando,cuenta,concesion,tipo,huella,contenido,c.version(),c.estado(),utc(ahora));
    }
    public List<Evento> historial(UUID id){
        return jdbc.query("SELECT tipo,registrada_en,estado FROM b0_compra_manual_evento WHERE id_compra=? ORDER BY version_compra,registrada_en,id_comando",
                (r,n)->new Evento(r.getString(1),instante(r,"registrada_en"),r.getString(3)),id);
    }
    /** Sólo IDs de la página ya autorizada por Core; no carga contenido financiero del evento. */
    public Map<UUID,List<Evento>> historiales(List<UUID> ids){
        if(ids.isEmpty())return Map.of();
        var compras=List.copyOf(ids);String parametros=String.join(",",Collections.nCopies(compras.size(),"?"));
        return jdbc.query("SELECT id_compra,tipo,registrada_en,estado FROM b0_compra_manual_evento WHERE id_compra IN ("+parametros+") ORDER BY id_compra,version_compra,registrada_en,id_comando",
                r->{
                    Map<UUID,List<Evento>> resultado=new LinkedHashMap<>();
                    while(r.next())resultado.computeIfAbsent(r.getObject("id_compra",UUID.class),id->new ArrayList<>())
                            .add(new Evento(r.getString("tipo"),instante(r,"registrada_en"),r.getString("estado")));
                    resultado.replaceAll((id,eventos)->List.copyOf(eventos));return Map.copyOf(resultado);
                },compras.toArray());
    }
    private Compra fila(ResultSet r,int n)throws SQLException{
        return new Compra(r.getObject("id_compra",UUID.class),r.getLong("organizacion_id"),r.getLong("creada_por"),r.getString("solicitud_huella"),r.getString("terminos"),instante(r,"creada_en"),
                r.getObject("consentida_por",Long.class),instante(r,"consentida_en"),r.getString("huella_consentimiento"),r.getString("estado"),r.getLong("version"),r.getString("pago_informado"),r.getString("fondos"),r.getString("incidencia"));
    }
    private static Instant instante(ResultSet r,String nombre)throws SQLException{var d=r.getObject(nombre,OffsetDateTime.class);return d==null?null:d.toInstant();}
    private static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
    private static void exigir(int n){if(n!=1)throw new org.springframework.dao.OptimisticLockingFailureException("Cambió la compra; revisa su versión actual.");}
}
