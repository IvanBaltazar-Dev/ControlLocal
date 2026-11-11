package com.controllocal.persistence.presupuesto;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

/** Ledger de bytes comerciales exactos; no guarda bytes ni decide acceso/retención. */
@Repository
public class UsoAlmacenamientoB0Repository {
    private final JdbcTemplate jdbc;
    public UsoAlmacenamientoB0Repository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    /** Autoridad temporal local de claim/prueba de almacenamiento; coincide con los eventos durables DB. */
    public Instant ahoraDeAlmacenamiento(){
        return jdbc.queryForObject("SELECT clock_timestamp()",(r,n)->r.getObject(1,OffsetDateTime.class).toInstant());
    }
    public record Uso(long confirmados,long reservados){}
    public record Politica(String version,Long margen){}
    public record UltimoPeriodo(String plan,Instant fin){}
    public record Objeto(String identidad,String contenido,long bytes,String categoria){}
    public record Reserva(UUID id,Long persona,Long rol,String peticion,String tipo,String recurso,String finalidad,String origen,
            String categoria,String necesidad,Long bytes,String contenido,Long retenidos,String politica,String contextoLimite,String estado,String objeto,UUID operacionB0){
        public Reserva(UUID id,long persona,long rol,String peticion,String tipo,String recurso,String finalidad,String origen,
            String categoria,String necesidad,Long bytes,String contenido,long retenidos,String politica,String contextoLimite,String estado,String objeto){
            this(id,persona,rol,peticion,tipo,recurso,finalidad,origen,categoria,necesidad,bytes,contenido,retenidos,politica,contextoLimite,estado,objeto,null);
        }
        public Reserva(UUID id,long persona,long rol,String peticion,String tipo,String recurso,String finalidad,String origen,
                String categoria,String necesidad,long bytes,String contenido,long retenidos,String politica,String contextoLimite,String estado,String objeto){
            this(id,persona,rol,peticion,tipo,recurso,finalidad,origen,categoria,necesidad,Long.valueOf(bytes),contenido,retenidos,politica,contextoLimite,estado,objeto);
        }
    }
    public record Detalle(String modalidad,UUID job,String perfil,String versionPerfil,String namespace,Long techo,
            Instant reclamada,String versionFisica,long retenidosFisicos,String liberacion){
        public boolean prevision(){return "PREVISION_IA".equals(modalidad);}
    }
    public record LimiteSalida(long bytes){}
    public record CapacidadFisica(String namespace,String version,long limite,long confirmados,long reservados,boolean vigente){}
    public record Presencia(String namespace,String versionFisica,boolean comercial,boolean fisico,String estado,long revision,UUID retiro,String huellaRetiro){}
    public enum AmbitoIntentos {NAMESPACE,OPERACION_PROPIA}
    /** MVCC bajo P; no toma S ni bloquea el diario hijo. Una TX revertida no convierte un PUT incierto en espacio libre. */
    public boolean existenIntentosFisicosSinCobertura(String namespace,UUID orgPropia,String objetoPropio,long bytesPropios,String contenidoPropio,AmbitoIntentos ambito){
        Objects.requireNonNull(ambito);
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(
             SELECT 1 FROM intento_salida_binaria i
             CROSS JOIN LATERAL (SELECT encode(sha256(convert_to(i.almacen_huella||':'||i.clave_proveedor,'UTF8')),'hex') AS objeto) h
             WHERE i.almacen_huella=? AND i.estado<>'VINCULADO'
              AND (i.estado<>'RETIRADO_VERIFICADO' OR i.escritura_incierta)
              AND (? OR EXISTS(SELECT 1 FROM intento_salida_binaria actual
               WHERE actual.dueno_binario=? AND actual.dueno_binario=i.dueno_binario AND actual.operacion=i.operacion
                AND actual.almacen_huella=i.almacen_huella AND actual.bytes=? AND actual.huella=?
                AND encode(sha256(convert_to(actual.almacen_huella||':'||actual.clave_proveedor,'UTF8')),'hex')=?))
              AND NOT COALESCE(i.dueno_binario=? AND h.objeto=? AND i.bytes=? AND i.huella=?,false)
              AND NOT EXISTS(
               SELECT 1 FROM b0_reserva_almacenamiento r
               LEFT JOIN b0_presencia_objeto_almacenamiento p
                ON p.dueno_binario=r.dueno_binario AND p.huella_identidad=r.huella_objeto
               WHERE r.dueno_binario=i.dueno_binario AND r.namespace_hash=i.almacen_huella
                AND r.version_fisica IS NOT NULL AND r.huella_objeto=h.objeto
                AND r.bytes=i.bytes AND r.huella_contenido=i.huella
                AND ((r.estado IN('RESERVADA','ALMACENANDO','INCIERTA') AND r.bytes_fisicos_retenidos>=i.bytes)
                 OR (r.estado='CONFIRMADA' AND p.estado='PRESENTE' AND p.fisico))))
            """,Boolean.class,namespace,ambito==AmbitoIntentos.NAMESPACE,orgPropia,bytesPropios,contenidoPropio,objetoPropio,
                    orgPropia,objetoPropio,bytesPropios,contenidoPropio));
    }
    public Uso bloquearUso(long org){
        jdbc.update("INSERT INTO b0_uso_almacenamiento(organizacion_id) VALUES (?) ON CONFLICT DO NOTHING",org);
        return jdbc.queryForObject("SELECT confirmados,reservados FROM b0_uso_almacenamiento WHERE organizacion_id=? FOR UPDATE",(r,n)->new Uso(r.getLong(1),r.getLong(2)),org);
    }
    public List<Politica> politicas(String plan,Instant ahora){
        return jdbc.query("SELECT p.version,p.margen_esencial_bytes FROM b0_politica_almacenamiento p JOIN b0_politica_almacenamiento_actual a ON a.plan=p.plan AND a.version=p.version WHERE p.plan=? AND p.habilitada AND p.desde<=? AND p.hasta>?",
                (r,n)->new Politica(r.getString(1),r.getObject(2,Long.class)),plan,utc(ahora),utc(ahora));
    }
    public Optional<UltimoPeriodo> ultimoPeriodo(long org,Instant ahora){
        return jdbc.query("SELECT plan,LEAST(hasta,COALESCE(cerrado_en,hasta)) FROM b0_ciclo WHERE organizacion_id=? AND desde<=? ORDER BY desde DESC LIMIT 1",
                (r,n)->new UltimoPeriodo(r.getString(1),r.getObject(2,OffsetDateTime.class).toInstant()),org,utc(ahora)).stream().findFirst();
    }
    public Optional<Objeto> objeto(UUID dueno,String identidad){
        return jdbc.query("SELECT huella_identidad,huella_contenido,bytes,categoria FROM b0_objeto_almacenamiento WHERE dueno_binario=? AND huella_identidad=?",
            (r,n)->new Objeto(r.getString(1),r.getString(2),r.getLong(3),r.getString(4)),dueno,identidad).stream().findFirst();
    }
    public Optional<Reserva> reserva(UUID dueno,UUID id){
        return jdbc.query("""
            SELECT id_reserva,id_persona,id_rol,huella_peticion,recurso_tipo,recurso_id,finalidad,huella_origen,
             categoria,necesidad,bytes,huella_contenido,bytes_retenidos,version_politica,contexto_limite::text,
             estado,huella_objeto,operacion_evaluacion_b0
            FROM b0_reserva_almacenamiento WHERE dueno_binario=? AND id_reserva=?
            """,(r,n)->new Reserva(r.getObject(1,UUID.class),r.getObject(2,Long.class),r.getObject(3,Long.class),
                r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),
                r.getObject(11,Long.class),r.getString(12),r.getObject(13,Long.class),r.getString(14),r.getString(15),
                r.getString(16),r.getString(17),r.getObject(18,UUID.class)),dueno,id).stream().findFirst();
    }
    public void crearReserva(UUID dueno,Reserva r,Instant ahora){
        jdbc.update("""
            INSERT INTO b0_reserva_almacenamiento(dueno_binario,organizacion_id,id_caso_evaluacion,id_reserva,id_persona,id_rol,
             huella_peticion,recurso_tipo,recurso_id,finalidad,huella_origen,categoria,necesidad,bytes,huella_contenido,
             bytes_retenidos,version_politica,contexto_limite,estado,huella_objeto,creada_en,operacion_evaluacion_b0,namespace_hash,version_fisica,bytes_fisicos_retenidos)
            SELECT id,organizacion_id,caso,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?,?,
             CASE WHEN caso IS NOT NULL THEN CAST(? AS jsonb)->>'namespace' END,
             CASE WHEN caso IS NOT NULL THEN CAST(? AS jsonb)->>'versionFisica' END,
             CASE WHEN caso IS NOT NULL THEN ? ELSE 0 END
            FROM b0_propietario_binario WHERE id=?
            """,r.id(),r.persona(),r.rol(),r.peticion(),r.tipo(),r.recurso(),r.finalidad(),r.origen(),r.categoria(),
            r.necesidad(),r.bytes(),r.contenido(),r.retenidos(),r.politica(),r.contextoLimite(),r.estado(),r.objeto(),
            utc(ahora),r.operacionB0(),r.contextoLimite(),r.contextoLimite(),r.bytes(),dueno);
    }
    public void retener(long org,long bytes){
        exigir(jdbc.update("UPDATE b0_uso_almacenamiento SET reservados=reservados+?,version=version+1 WHERE organizacion_id=?",bytes,org));
    }
    public boolean iniciar(UUID org,UUID id){
        return jdbc.update("UPDATE b0_reserva_almacenamiento SET estado='ALMACENANDO' WHERE dueno_binario=? AND id_reserva=? AND estado='RESERVADA'",org,id)==1;
    }
    public void incierta(UUID org,UUID id){
        jdbc.update("UPDATE b0_reserva_almacenamiento SET estado='INCIERTA' WHERE dueno_binario=? AND id_reserva=? AND estado='ALMACENANDO'",org,id);
    }
    public void objetoConfirmado(UUID dueno,Objeto objeto,String evidencia,Instant ahora){
        jdbc.update("""
            INSERT INTO b0_objeto_almacenamiento(dueno_binario,organizacion_id,id_caso_evaluacion,huella_identidad,
             huella_contenido,bytes,categoria,referencia_comprobacion,confirmado_en)
            SELECT id,organizacion_id,caso,?,?,?,?,?,? FROM b0_propietario_binario WHERE id=?
            """,objeto.identidad(),objeto.contenido(),objeto.bytes(),objeto.categoria(),evidencia,utc(ahora),dueno);
    }
    public void contabilizar(long org,long liberar,long confirmar){
        exigir(jdbc.update("UPDATE b0_uso_almacenamiento SET reservados=reservados-?,confirmados=confirmados+?,version=version+1 WHERE organizacion_id=? AND reservados>=?",liberar,confirmar,org,liberar));
    }
    public void cerrar(UUID org,UUID id,String estado,String objeto,Instant ahora){
        exigir(jdbc.update("UPDATE b0_reserva_almacenamiento SET estado=?,huella_objeto=?,terminada_en=? WHERE dueno_binario=? AND id_reserva=? AND estado NOT IN('CONFIRMADA','CANCELADA')",
                estado,objeto,utc(ahora),org,id));
    }
    public Optional<Detalle> detalle(UUID org,UUID reserva){
        return jdbc.query("""
            SELECT modalidad,job_ia,perfil_ia,version_perfil_ia,namespace_hash,techo_bytes,reclamada_en,
             version_fisica,bytes_fisicos_retenidos,huella_liberacion FROM b0_reserva_almacenamiento
             WHERE dueno_binario=? AND id_reserva=?
            """,(r,n)->new Detalle(r.getString(1),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getString(5),r.getObject(6,Long.class),
                    r.getObject(7,OffsetDateTime.class)==null?null:r.getObject(7,OffsetDateTime.class).toInstant(),r.getString(8),r.getLong(9),r.getString(10)),org,reserva).stream().findFirst();
    }
    public Optional<LimiteSalida> limiteSalida(String perfil,String version,Instant ahora){
        return jdbc.query("""
            SELECT l.maximo_bytes FROM b0_limite_salida_ia l JOIN b0_perfil_operativo p
             ON p.perfil=l.perfil AND p.version=l.version_perfil
             JOIN b0_perfil_actual a ON a.perfil=p.perfil AND a.version=p.version
             WHERE l.perfil=? AND l.version_perfil=? AND l.habilitado AND p.habilitado AND l.desde<=? AND l.hasta>?
            """,(r,n)->new LimiteSalida(r.getLong(1)),perfil,version,utc(ahora),utc(ahora)).stream().findFirst();
    }
    /** La organización ya está retenida; este es el único orden hacia el pool físico. */
    public Optional<CapacidadFisica> bloquearCapacidadFisica(String namespace,Instant ahora){
        if(namespace==null)return Optional.empty();
        jdbc.update("""
            INSERT INTO b0_uso_capacidad_fisica(namespace_hash,version_politica,confirmados,reservados)
             SELECT p.namespace_hash,p.version,p.ocupacion_conciliada_bytes,0 FROM b0_politica_capacidad_fisica p
             JOIN b0_capacidad_fisica_actual a ON a.namespace_hash=p.namespace_hash AND a.version=p.version
             WHERE p.namespace_hash=? AND p.habilitada AND p.desde<=? AND p.hasta>? ON CONFLICT DO NOTHING
            """,namespace,utc(ahora),utc(ahora));
        var actual=jdbc.query("""
            SELECT u.namespace_hash,u.version_politica,p.capacidad_bytes,u.confirmados,u.reservados
             FROM b0_uso_capacidad_fisica u JOIN b0_politica_capacidad_fisica p
              ON p.namespace_hash=u.namespace_hash AND p.version=u.version_politica
             JOIN b0_capacidad_fisica_actual a ON a.namespace_hash=p.namespace_hash AND a.version=p.version
             WHERE u.namespace_hash=? AND p.habilitada AND p.desde<=? AND p.hasta>? FOR UPDATE OF u
            """,(r,n)->new CapacidadFisica(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4),r.getLong(5),true),namespace,utc(ahora),utc(ahora)).stream().findFirst();
        if(actual.isPresent())return actual;
        // Una política vencida/cambiada no permite que otra carga consuma espacio ya comprometido.
        return jdbc.query("""
            SELECT u.namespace_hash,u.version_politica,p.capacidad_bytes,u.confirmados,u.reservados
             FROM b0_uso_capacidad_fisica u JOIN b0_politica_capacidad_fisica p
              ON p.namespace_hash=u.namespace_hash AND p.version=u.version_politica
             WHERE u.namespace_hash=? FOR UPDATE OF u
            """,(r,n)->new CapacidadFisica(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4),r.getLong(5),false),namespace).stream().findFirst();
    }
    /** Recuperación/confirmación conserva la política reservada aunque haya vencido su admisión. */
    public CapacidadFisica bloquearFisicaReservada(String namespace,String version){
        return jdbc.queryForObject("""
            SELECT u.namespace_hash,u.version_politica,p.capacidad_bytes,u.confirmados,u.reservados
             FROM b0_uso_capacidad_fisica u JOIN b0_politica_capacidad_fisica p
             ON p.namespace_hash=u.namespace_hash AND p.version=?
             WHERE u.namespace_hash=? FOR UPDATE OF u
            """,(r,n)->new CapacidadFisica(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4),r.getLong(5),false),version,namespace);
    }
    public void asociarFisica(UUID org,UUID id,String namespace,String version,long retenidos){
        exigir(jdbc.update("UPDATE b0_reserva_almacenamiento SET namespace_hash=?,version_fisica=?,bytes_fisicos_retenidos=? WHERE dueno_binario=? AND id_reserva=? AND modalidad='EXACTA'",namespace,version,retenidos,org,id));
    }
    public void crearPrevision(UUID dueno,Reserva r,UUID job,String perfil,String version,String namespace,long techo,String fisica,Instant ahora){
        jdbc.update("""
            INSERT INTO b0_reserva_almacenamiento(dueno_binario,organizacion_id,id_caso_evaluacion,id_reserva,id_persona,id_rol,
             huella_peticion,recurso_tipo,recurso_id,finalidad,huella_origen,categoria,necesidad,bytes,huella_contenido,
             bytes_retenidos,version_politica,contexto_limite,estado,creada_en,modalidad,job_ia,perfil_ia,version_perfil_ia,
             namespace_hash,techo_bytes,version_fisica,bytes_fisicos_retenidos,operacion_evaluacion_b0)
            SELECT id,organizacion_id,caso,?,?,?,?,?,?,?,?,?,?,NULL,NULL,?,?,CAST(? AS jsonb),'RESERVADA',?,'PREVISION_IA',?,?,?,?,?,?,?,?
            FROM b0_propietario_binario WHERE id=?
            """,r.id(),r.persona(),r.rol(),r.peticion(),r.tipo(),r.recurso(),r.finalidad(),r.origen(),r.categoria(),r.necesidad(),
                r.retenidos(),r.politica(),r.contextoLimite(),utc(ahora),job,perfil,version,namespace,techo,fisica,techo,r.operacionB0(),dueno);
    }
    public void reclamarPrevision(UUID org,UUID id,Instant ahora){
        exigir(jdbc.update("UPDATE b0_reserva_almacenamiento SET reclamada_en=COALESCE(reclamada_en,?) WHERE dueno_binario=? AND id_reserva=? AND modalidad='PREVISION_IA' AND estado='RESERVADA'",utc(ahora),org,id));
    }
    public void fijarContenido(UUID org,UUID id,long bytes,String huella,String objeto,Instant ahora){
        exigir(jdbc.update("""
            UPDATE b0_reserva_almacenamiento SET bytes=?,huella_contenido=?,huella_objeto=?,contenido_fijado_en=?
             WHERE dueno_binario=? AND id_reserva=? AND modalidad='PREVISION_IA' AND bytes IS NULL
              AND estado IN('RESERVADA','ALMACENANDO','INCIERTA') AND reclamada_en IS NOT NULL
            """,bytes,huella,objeto,utc(ahora),org,id));
    }
    public void retenerFisica(String namespace,long bytes){
        exigir(jdbc.update("UPDATE b0_uso_capacidad_fisica SET reservados=reservados+?,version=version+1 WHERE namespace_hash=?",bytes,namespace));
    }
    public void contabilizarFisica(String namespace,long liberar,long confirmar){
        exigir(jdbc.update("UPDATE b0_uso_capacidad_fisica SET reservados=reservados-?,confirmados=confirmados+?,version=version+1 WHERE namespace_hash=? AND reservados>=?",liberar,confirmar,namespace,liberar));
    }
    public Optional<Presencia> presencia(UUID dueno,String objeto){
        return jdbc.query("SELECT namespace_hash,version_fisica,comercial,fisico,estado,revision,id_retiro,huella_retiro FROM b0_presencia_objeto_almacenamiento WHERE dueno_binario=? AND huella_identidad=?",
            (r,n)->new Presencia(r.getString(1),r.getString(2),r.getBoolean(3),r.getBoolean(4),r.getString(5),r.getLong(6),r.getObject(7,UUID.class),r.getString(8)),dueno,objeto).stream().findFirst();
    }
    public void presencia(UUID dueno,String objeto,String namespace,String politica,boolean comercial,boolean fisico){
        jdbc.update("""
            INSERT INTO b0_presencia_objeto_almacenamiento(dueno_binario,organizacion_id,id_caso_evaluacion,huella_identidad,
             namespace_hash,version_fisica,comercial,fisico,estado)
            SELECT id,organizacion_id,caso,?,?,?,?,?,'PRESENTE' FROM b0_propietario_binario WHERE id=?
            """,objeto,namespace,politica,comercial,fisico,dueno);
    }
    public void retirar(UUID org,String objeto,UUID retiro,String huella,Instant ahora){
        exigir(jdbc.update("UPDATE b0_presencia_objeto_almacenamiento SET estado='RETIRADO_VERIFICADO',revision=revision+1,id_retiro=?,huella_retiro=?,retirado_en=? WHERE dueno_binario=? AND huella_identidad=? AND estado='PRESENTE'",retiro,huella,utc(ahora),org,objeto));
    }
    public void promoverComercial(UUID org,String objeto){
        exigir(jdbc.update("UPDATE b0_presencia_objeto_almacenamiento SET comercial=true,revision=revision+1 WHERE dueno_binario=? AND huella_identidad=? AND estado='PRESENTE' AND NOT comercial",org,objeto));
    }
    public void liberarConfirmados(long org,long bytes){
        exigir(jdbc.update("UPDATE b0_uso_almacenamiento SET confirmados=confirmados-?,version=version+1 WHERE organizacion_id=? AND confirmados>=?",bytes,org,bytes));
    }
    public void liberarFisicosConfirmados(String namespace,long bytes){
        exigir(jdbc.update("UPDATE b0_uso_capacidad_fisica SET confirmados=confirmados-?,version=version+1 WHERE namespace_hash=? AND confirmados>=?",bytes,namespace,bytes));
    }
    public void cancelarPrevision(UUID org,UUID id,String huella,Instant ahora){
        exigir(jdbc.update("UPDATE b0_reserva_almacenamiento SET estado='CANCELADA',terminada_en=?,huella_liberacion=? WHERE dueno_binario=? AND id_reserva=? AND modalidad='PREVISION_IA' AND estado NOT IN('CONFIRMADA','CANCELADA')",utc(ahora),huella,org,id));
    }
    public UUID dueno(Long org,UUID caso){
        UUID id=jdbc.queryForObject("SELECT b0_resolver_dueno(?,?)",UUID.class,org,caso);
        if(id==null)throw new IllegalArgumentException("No existe el dueño real.");return id;
    }
    public void bloquearReserva(UUID dueno,UUID reserva){
        jdbc.query("SELECT id_reserva FROM b0_reserva_almacenamiento WHERE dueno_binario=? AND id_reserva=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),dueno,reserva);
    }
    public void fijarExacta(UUID dueno,UUID reserva,String identidad){
        exigir(jdbc.update("UPDATE b0_reserva_almacenamiento SET huella_objeto=? WHERE dueno_binario=? AND id_reserva=? AND modalidad='EXACTA' AND huella_objeto IS NULL",
            identidad,dueno,reserva));
    }
    private static OffsetDateTime utc(Instant instante){return instante.atOffset(ZoneOffset.UTC);}
    private static void exigir(int n){if(n!=1)throw new org.springframework.dao.OptimisticLockingFailureException("Cambió el registro de almacenamiento.");}
}
