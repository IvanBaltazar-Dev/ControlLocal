package com.controllocal.persistence.materialia;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.controllocal.persistence.archivo.DiarioBinarioTecnico;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Diario técnico por operación; no contiene saldo, cuota, cargo ni otra aritmética B0. */
@Repository
public class RegistroMaterialIaRepository {
    public record Custodia(long organizacion,UUID job,UUID archivo,UUID operacionIo,int numero,String estado,long version){}
    public Optional<Custodia> custodia(long org,UUID job){return custodia(jdbc,org,job);}
    private Optional<Custodia> custodia(JdbcTemplate acceso,long org,UUID job){return acceso.query("SELECT organizacion_id,id_operacion,archivo_ref,operacion_io,numero_io,estado,version FROM custodia_resultado_material_ia WHERE organizacion_id=? AND id_operacion=?",
            (r,n)->new Custodia(r.getLong(1),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class),r.getInt(5),r.getString(6),r.getLong(7)),org,job).stream().findFirst();}
    /** No adquiere locks de filas del padre: referencias e intención139 ya están comprometidas. */
    public Custodia custodiar(long org,UUID job,String clave,UUID io,int numero){
        return custodiaNueva.execute(tx->{
        var o=obtener(jdbcCustodia,org,job).orElseThrow();if(!o.claveExterna().equals(clave)||o.medicion()==null)throw new IllegalStateException("La custodia exige el resultado causado del claim.");
        UUID archivo=UUID.nameUUIDFromBytes(("BROX-CUSTODIA-IA\u0000"+org+"\u0000"+job).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbcCustodia.update("INSERT INTO custodia_resultado_material_ia(organizacion_id,id_operacion,archivo_ref,operacion_io,numero_io,estado) VALUES(?,?,?,?,?,'PREPARADA') ON CONFLICT DO NOTHING",org,job,archivo,io,numero);
        var c=custodia(jdbcCustodia,org,job).orElseThrow();
        if(!c.operacionIo().equals(io)||c.numero()!=numero||"RETIRADA".equals(c.estado()))throw new IllegalStateException("La custodia no admite otra identidad ni escritura.");return c;
        });
    }
    public void verificarCustodia(Custodia c){
        if("VERIFICADA".equals(c.estado()))return;
        exigir(jdbc.update("UPDATE custodia_resultado_material_ia SET estado='VERIFICADA',version=version+1 WHERE organizacion_id=? AND id_operacion=? AND version=? AND estado='PREPARADA'",c.organizacion(),c.job(),c.version()));
    }
    public void retirarCustodia(long org,UUID job){jdbc.update("UPDATE custodia_resultado_material_ia SET estado='RETIRADA',version=version+1 WHERE organizacion_id=? AND id_operacion=? AND estado IN('PREPARADA','VERIFICADA')",org,job);}
    public Optional<String> ultimoHecho(long org,UUID job,String estado){return jdbc.query("SELECT evidencia FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado=? ORDER BY id_evento DESC LIMIT 1",(r,n)->r.getString(1),org,job,estado).stream().findFirst();}
    public Instant ahora(){return jdbc.queryForObject("SELECT clock_timestamp()",java.time.OffsetDateTime.class).toInstant();}
    public record Operacion(long organizacion,UUID id,long persona,long rol,String rolEfectivo,String sesion,
            String accion,String peticion,String solicitud,String snapshot,String huellaSnapshot,
            String perfil,String versionPerfil,String claveExterna,String estado,String medicion,
            String salida,String control,String resultado,String motivo,long version) { }
    private static final String COLUMNAS="organizacion_id,id_operacion,id_persona,id_rol,rol_efectivo,sesion_origen,accion,"+
            "huella_peticion,solicitud,snapshot,huella_snapshot,perfil,version_perfil,clave_externa,estado,medicion,salida,control_automatico,resultado,motivo,version";
    private final JdbcTemplate jdbc;
    private final JdbcTemplate jdbcCustodia;
    private final TransactionTemplate custodiaNueva;
    public RegistroMaterialIaRepository(JdbcTemplate jdbc,DiarioBinarioTecnico diario){
        this.jdbc=jdbc;this.jdbcCustodia=diario.jdbc();custodiaNueva=new TransactionTemplate(diario.gestor());
        custodiaNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void bloquear(long clave){jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(?)",Integer.class,clave);}
    /** Lectura protegida: el mismo escritor exclusivo sigue excluido hasta commit. */
    public void bloquearCompartido(long clave){jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock_shared(?)",Integer.class,clave);}
    public Optional<Operacion> obtener(long org,UUID id){
        return obtener(jdbc,org,id);
    }
    private Optional<Operacion> obtener(JdbcTemplate acceso,long org,UUID id){
        return acceso.query("SELECT "+COLUMNAS+" FROM material_ia_operacion WHERE organizacion_id=? AND id_operacion=?",this::fila,org,id).stream().findFirst();
    }
    public void insertar(Operacion o){
        jdbc.update("""
                INSERT INTO material_ia_operacion(organizacion_id,id_operacion,id_persona,id_rol,rol_efectivo,sesion_origen,
                 accion,huella_peticion,solicitud,snapshot,huella_snapshot,perfil,version_perfil,clave_externa,estado)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'INTENCION')
                """,o.organizacion(),o.id(),o.persona(),o.rol(),o.rolEfectivo(),o.sesion(),o.accion(),o.peticion(),o.solicitud(),
                o.snapshot(),o.huellaSnapshot(),o.perfil(),o.versionPerfil(),o.claveExterna());
        evidencia(o,"INTENCION","{}");
    }
    public void fase(Operacion o,String estado,String motivo){
        exigir(jdbc.update("UPDATE material_ia_operacion SET estado=?,motivo=?,version=version+1,actualizada_en=clock_timestamp() WHERE organizacion_id=? AND id_operacion=? AND version=?",
                estado,motivo,o.organizacion(),o.id(),o.version()));
        evidencia(o,estado,"{}");
    }
    public void medir(Operacion o,String medicion,String salida){
        exigir(jdbc.update("""
                UPDATE material_ia_operacion SET estado='MEDIDA',medicion=?,salida=?,motivo=NULL,version=version+1,
                 actualizada_en=clock_timestamp() WHERE organizacion_id=? AND id_operacion=? AND version=?
                """,medicion,salida,o.organizacion(),o.id(),o.version()));
        evidencia(o,"MEDIDA","{}");
    }
    public void controlar(Operacion o,String control,String estado){
        exigir(jdbc.update("""
                UPDATE material_ia_operacion SET control_automatico=?,estado=?,motivo=NULL,version=version+1,
                 actualizada_en=clock_timestamp() WHERE organizacion_id=? AND id_operacion=? AND version=?
                """,control,estado,o.organizacion(),o.id(),o.version()));
        evidencia(o,estado,"{}");
    }
    public void resultado(Operacion o,String resultado){
        exigir(jdbc.update("""
                UPDATE material_ia_operacion SET resultado=?,estado='DISPONIBLE',motivo=NULL,version=version+1,
                 actualizada_en=clock_timestamp() WHERE organizacion_id=? AND id_operacion=? AND version=?
                """,resultado,o.organizacion(),o.id(),o.version()));
        evidencia(o,"DISPONIBLE","{}");
    }
    /** Anexa evidencia privada en el mismo diario; el ID identifica el hecho real, no una cadena inventada. */
    public long evidencia(Operacion o,String estado,String evidencia){
        return jdbc.queryForObject("INSERT INTO material_ia_evento(organizacion_id,id_operacion,estado,evidencia) VALUES (?,?,?,?) RETURNING id_evento",
                Long.class,o.organizacion(),o.id(),estado,evidencia);
    }
    private static void exigir(int n){if(n!=1)throw new IllegalStateException("La operación material IA cambió durante su fase técnica.");}
    private Operacion fila(ResultSet r,int n)throws SQLException{
        return new Operacion(r.getLong(1),r.getObject(2,UUID.class),r.getLong(3),r.getLong(4),r.getString(5),r.getString(6),
                r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11),r.getString(12),r.getString(13),
                r.getString(14),r.getString(15),r.getString(16),r.getString(17),r.getString(18),r.getString(19),r.getString(20),r.getLong(21));
    }
}
