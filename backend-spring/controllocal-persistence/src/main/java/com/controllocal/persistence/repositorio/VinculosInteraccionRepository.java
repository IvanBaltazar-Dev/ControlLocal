package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;

/** Índice de vínculos explícitos; no concede autoridad ni crea sus recursos. */
@Repository
public class VinculosInteraccionRepository {
    public record Vinculo(String tipo,long referencia,Long version) { }
    private static final Map<String,String> COLUMNAS=Map.of("PROPIEDAD","id_propiedad","CONTACTO","id_persona",
            "PROSPECCION","id_prospeccion","ENCARGO","id_captacion","OPORTUNIDAD","id_oportunidad","CLIENTE","id_rol_cliente");
    private final JdbcTemplate jdbc;
    public VinculosInteraccionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void sincronizar(long org,long interaccion,List<Vinculo> vinculos,Instant instante){
        var anteriores=jdbc.query("select id_vinculo,tipo,coalesce(id_propiedad,id_persona,id_prospeccion,id_captacion,id_oportunidad,id_rol_cliente) from interaccion_vinculo where organizacion_id=? and id_interaccion=? and retirado_en is null",
                (r,n)->new Object[]{r.getLong(1),r.getString(2),r.getLong(3)},org,interaccion);
        Set<String> actuales=new HashSet<>();
        for(var a:anteriores){String clave=a[1]+":"+a[2];
            if(vinculos.stream().anyMatch(v->clave.equals(v.tipo()+":"+v.referencia())))actuales.add(clave);
            else jdbc.update("update interaccion_vinculo set retirado_en=? where organizacion_id=? and id_vinculo=? and retirado_en is null",
                    java.sql.Timestamp.from(instante),org,a[0]);
        }
        for(var v:vinculos){if(actuales.add(v.tipo()+":"+v.referencia())){
            String columna=COLUMNAS.get(v.tipo());if(columna==null)throw new IllegalArgumentException("Tipo de vínculo no admitido.");
            jdbc.update("insert into interaccion_vinculo(organizacion_id,id_interaccion,tipo,"+columna+",version_referencia,creado_en) values(?,?,?,?,?,?)",
                    org,interaccion,v.tipo(),v.referencia(),v.version(),java.sql.Timestamp.from(instante));
        }}
    }
}
