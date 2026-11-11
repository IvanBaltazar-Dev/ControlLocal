package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/** Diario técnico privado. Ningún método concede acceso comercial al archivo. */
@Repository
public class IntentoSalidaBinariaRepository {
    private static final String COLUMNAS="id_intento,organizacion_id,operacion,numero,fuente,clave_proveedor,extension,content_type,huella,bytes,estado,escritura_incierta,ultima_verificacion_en,resultado_tecnico,almacen_huella,dueno_binario,id_caso_evaluacion";
    private final JdbcTemplate jdbc;
    public IntentoSalidaBinariaRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Intento(long id,Long organizacion,UUID operacion,int numero,String fuente,String clave,
                          String extension,String contentType,String huella,long bytes,String estado,
                          boolean escrituraIncierta,Instant ultimaVerificacion,String resultadoTecnico,String almacenHuella,UUID dueno,UUID caso) { }
    public void bloquear(long identidad){jdbc.queryForObject("select 1 from pg_advisory_xact_lock(?)",Integer.class,identidad);}
    public boolean poseeBloqueo(long identidad){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE pid=pg_backend_pid() AND locktype='advisory' AND granted AND mode='ExclusiveLock' AND classid::bigint=? AND objid::bigint=? AND objsubid=1)",Boolean.class,(identidad>>>32)&0xffffffffL,identidad&0xffffffffL));}
    public boolean intentarBloqueo(long identidad){return Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_xact_lock(?)",Boolean.class,identidad));}
    public Optional<Intento> ultimo(UUID org,UUID operacion){
        return jdbc.query("select "+COLUMNAS+" from intento_salida_binaria where dueno_binario=? and operacion=? order by numero desc limit 1",this::fila,org,operacion).stream().findFirst();
    }
    public List<Intento> deOperacion(UUID org,UUID operacion){return jdbc.query("select "+COLUMNAS+" from intento_salida_binaria where dueno_binario=? and operacion=? order by numero",this::fila,org,operacion);}
    public Intento insertar(UUID org,UUID operacion,int numero,String fuente,String clave,String extension,
                            String tipo,String huella,long bytes,String almacenHuella){
        return jdbc.queryForObject("""
                insert into intento_salida_binaria(organizacion_id,id_caso_evaluacion,dueno_binario,operacion,numero,fuente,clave_proveedor,
                   extension,content_type,huella,bytes,estado,escritura_incierta,almacen_huella)
                select organizacion_id,caso,id,?,?,?,?,?,?,?,?,'PREPARADO',true,? from b0_propietario_binario where id=? returning
                """+COLUMNAS,this::fila,operacion,numero,fuente,clave,extension,tipo,huella,bytes,almacenHuella,org);
    }
    public void estado(Intento i,String estado,boolean escrituraIncierta,String resultado,boolean verificado){
        int filas=jdbc.update("""
            update intento_salida_binaria set estado=?,escritura_incierta=?,resultado_tecnico=?,
              actualizado_en=clock_timestamp(),ultima_verificacion_en=case when ? then clock_timestamp() else ultima_verificacion_en end
            where dueno_binario=? and operacion=? and numero=? and id_intento=? and almacen_huella=?
            """,estado,escrituraIncierta,resultado,verificado,i.dueno(),i.operacion(),i.numero(),i.id(),i.almacenHuella());
        if(filas!=1)throw new IllegalStateException("No se actualizó el intento binario esperado.");
    }
    public boolean vinculado(UUID org,String clave){
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from copia_comercial_foto where organizacion_id=(SELECT organizacion_id FROM b0_propietario_binario WHERE id=?) and clave_proveedor=?)
                or exists(select 1 from pieza_comercial where organizacion_id=(SELECT organizacion_id FROM b0_propietario_binario WHERE id=?) and (clave_privada=? or clave_difusion=?))
                or exists(select 1 from archivo_encargo where organizacion_id=(SELECT organizacion_id FROM b0_propietario_binario WHERE id=?) and clave_proveedor=?)
                or exists(select 1 from carga_archivo_contextual where organizacion_id=(SELECT organizacion_id FROM b0_propietario_binario WHERE id=?) and clave_proveedor=?)
                or exists(select 1 from foto_propiedad where organizacion_id=(SELECT organizacion_id FROM b0_propietario_binario WHERE id=?) and clave=?)
                or exists(select 1 from documento_solicitud where organizacion_id=(SELECT organizacion_id FROM b0_propietario_binario WHERE id=?) and ruta_archivo=?)
                or exists(select 1 from persona where organizacion_id=(SELECT organizacion_id FROM b0_propietario_binario WHERE id=?) and foto_clave=?)
                or exists(select 1 from derivada_material_ia d join intento_salida_binaria i
                    on i.organizacion_id=d.organizacion_id and i.operacion=d.operacion_io and i.numero=d.numero_io
                    where d.organizacion_id=(SELECT organizacion_id FROM b0_propietario_binario WHERE id=?) and i.clave_proveedor=?)
                or exists(select 1 from custodia_resultado_material_ia c join intento_salida_binaria i
                    on i.organizacion_id=c.organizacion_id and i.operacion=c.operacion_io and i.numero=c.numero_io
                    where c.organizacion_id=(SELECT organizacion_id FROM b0_propietario_binario WHERE id=?) and i.clave_proveedor=? and c.estado='VERIFICADA')
                or exists(select 1 from b0_archivo_evaluacion a join intento_salida_binaria i on i.id_intento=a.id_intento where a.dueno_binario=? and i.clave_proveedor=? and a.estado='VERIFICADA')
            """,Boolean.class,org,clave,org,clave,clave,org,clave,org,clave,org,clave,org,clave,org,clave,org,clave,org,clave,org,clave));
    }
    /** Custodia exacta: no equivale a QC ni verificación de bytes. */
    public boolean custodiado(UUID dueno,UUID operacion,int numero){
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM custodia_resultado_material_ia c JOIN intento_salida_binaria i
              ON i.organizacion_id=c.organizacion_id AND i.operacion=c.operacion_io AND i.numero=c.numero_io
              WHERE i.dueno_binario=? AND i.operacion=? AND i.numero=? AND c.estado IN('PREPARADA','VERIFICADA'))
            OR EXISTS(SELECT 1 FROM b0_archivo_evaluacion a JOIN intento_salida_binaria i ON i.id_intento=a.id_intento
              WHERE a.dueno_binario=? AND i.operacion=? AND i.numero=? AND a.estado IN('PREPARADA','VERIFICADA'))
            """,Boolean.class,dueno,operacion,numero,dueno,operacion,numero));
    }
    public UUID dueno(Long organizacion,UUID caso){
        var id=jdbc.query("SELECT b0_resolver_dueno(?,?)",(rs,n)->rs.getObject(1,UUID.class),organizacion,caso).getFirst();
        if(id==null)throw new IllegalArgumentException("No existe el dueño real del binario.");return id;
    }
    public List<Intento> pendientesDespues(String almacenHuella,long desde,int limite){
        return jdbc.query("select "+COLUMNAS+" from intento_salida_binaria where almacen_huella=? and id_intento>? and (estado not in ('VINCULADO','RETIRADO_VERIFICADO') or escritura_incierta) order by id_intento limit ?",this::fila,almacenHuella,desde,limite);
    }
    private Intento fila(ResultSet r,int numero)throws SQLException{
        var verificacion=r.getTimestamp("ultima_verificacion_en");
        return new Intento(r.getLong("id_intento"),r.getObject("organizacion_id",Long.class),r.getObject("operacion",UUID.class),
                r.getInt("numero"),r.getString("fuente"),r.getString("clave_proveedor"),r.getString("extension"),
                r.getString("content_type"),r.getString("huella"),r.getLong("bytes"),r.getString("estado"),
                r.getBoolean("escritura_incierta"),verificacion==null?null:verificacion.toInstant(),r.getString("resultado_tecnico"),r.getString("almacen_huella"),r.getObject("dueno_binario",UUID.class),r.getObject("id_caso_evaluacion",UUID.class));
    }
}
