package com.controllocal.persistence.materialia;

import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository;
import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository.Intento;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Hechos144: selección por original y conservación, sin bytes/QC/costos duplicados. */
@Repository
public class DerivadasMaterialIaRepository {
    public record Vinculo(long organizacion,UUID job,long propiedad,long foto,UUID original,UUID archivo,UUID operacionIo,int numeroIo) { }
    public record Foto(long propiedad,long foto,UUID original) { }
    public record Original(UUID original,long revision,long revisionMaterial,UUID job,UUID archivo) { }
    public record Slot(long organizacion,UUID job,UUID original,long revision,String estado,boolean guardada,
            Long eventoGeneracion,Instant generadaEn,Instant temporalDesde,Instant venceEn,String motivo,boolean protegida,String namespaceHash) { }
    public record Candidato(long organizacion,UUID job,long revision) { }
    public record MiembroCausa(UUID job,long revision,long eventoRetencion,Instant temporalDesde,Instant venceEn) { }
    public record Evento(long id,long organizacion,UUID job,String causa,String datos,String huella,Instant registradaEn) { }
    public record Generacion(long evento,Instant observadaEn) { }
    public record Gestion(String peticion,String respuesta,long persona,long rol) { }
    public record Retiro(UUID id,long organizacion,UUID job,UUID archivo,long revision,String causa,String estado,
            String identidadObjeto,long bytes,String huella,String referenciaComprobacion,Instant comprobadaEn) { }
    public record Pieza(long organizacion,long version,long propiedad,String solicitud,UUID archivoDifusion) { }
    private final JdbcTemplate jdbc;private final IntentoSalidaBinariaRepository intentos;
    public DerivadasMaterialIaRepository(JdbcTemplate jdbc,IntentoSalidaBinariaRepository intentos){this.jdbc=jdbc;this.intentos=intentos;}
    public Instant ahora(){return jdbc.queryForObject("SELECT clock_timestamp()",(r,n)->r.getTimestamp(1).toInstant());}
    public boolean poseeLock(long lock){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE pid=pg_backend_pid() AND locktype='advisory' AND granted AND mode='ExclusiveLock' AND classid::bigint=? AND objid::bigint=? AND objsubid=1)",Boolean.class,(lock>>>32)&0xffffffffL,lock&0xffffffffL));}
    /** Sólo consultas; nunca sustituye poseeLock en una admisión o mutación. */
    public boolean poseeLockParaLectura(long lock){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE pid=pg_backend_pid() AND locktype='advisory' AND granted AND mode IN ('ShareLock','ExclusiveLock') AND classid::bigint=? AND objid::bigint=? AND objsubid=1)",Boolean.class,(lock>>>32)&0xffffffffL,lock&0xffffffffL));}
    public Optional<Vinculo> obtener(long org,UUID job){return jdbc.query("""
        SELECT organizacion_id,id_operacion,id_propiedad,id_foto,original_ref,archivo_ref,operacion_io,numero_io
        FROM derivada_material_ia WHERE organizacion_id=? AND id_operacion=?
        """,(r,n)->new Vinculo(r.getLong(1),r.getObject(2,UUID.class),r.getLong(3),r.getLong(4),r.getObject(5,UUID.class),
                r.getObject(6,UUID.class),r.getObject(7,UUID.class),r.getInt(8)),org,job).stream().findFirst();}
    public Optional<Long> original(long org,long propiedad,UUID original){return foto(org,original).filter(f->f.propiedad()==propiedad).map(Foto::foto);}
    /** Metadata para gestión/retirada; NO es un vínculo comercial ni una autorización de lectura de bytes. */
    public Optional<Vinculo> obtenerGestion(long org,UUID job){
        var finalizada=obtener(org,job);if(finalizada.isPresent())return finalizada;
        return jdbc.query("""
            SELECT c.organizacion_id,c.id_operacion,f.id_propiedad,f.id_foto,f.archivo_ref,c.archivo_ref,c.operacion_io,c.numero_io
            FROM custodia_resultado_material_ia c JOIN material_ia_operacion j USING(organizacion_id,id_operacion)
            JOIN foto_propiedad f ON f.organizacion_id=j.organizacion_id AND f.archivo_ref=(j.solicitud::jsonb->>'fotoOriginal')::uuid
            WHERE c.organizacion_id=? AND c.id_operacion=?
            """,(r,n)->new Vinculo(r.getLong(1),r.getObject(2,UUID.class),r.getLong(3),r.getLong(4),r.getObject(5,UUID.class),r.getObject(6,UUID.class),r.getObject(7,UUID.class),r.getInt(8)),org,job).stream().findFirst();
    }
    public Optional<Long> fotoHistorica(long org,long propiedad,UUID original){return jdbc.query("SELECT id_foto FROM foto_propiedad WHERE organizacion_id=? AND id_propiedad=? AND archivo_ref=?",(r,n)->r.getLong(1),org,propiedad,original).stream().findFirst();}
    public Optional<Foto> foto(long org,UUID original){return jdbc.query("SELECT id_propiedad,id_foto,archivo_ref FROM foto_propiedad WHERE organizacion_id=? AND archivo_ref=? AND estado='A'",
            (r,n)->new Foto(r.getLong(1),r.getLong(2),r.getObject(3,UUID.class)),org,original).stream().findFirst();}
    public Optional<Intento> intento(long org,UUID operacion,int numero){return intentos.deOperacion(intentos.dueno(org,null),operacion).stream().filter(i->i.numero()==numero).findFirst();}
    public boolean hayIntento(long org,UUID operacion){return !intentos.deOperacion(intentos.dueno(org,null),operacion).isEmpty();}
    public Optional<Instant> fechaOriginal(long org,long propiedad,UUID original){return jdbc.query("SELECT fecha_registro FROM foto_propiedad WHERE organizacion_id=? AND id_propiedad=? AND archivo_ref=?",
            (r,n)->instante(r,1),org,propiedad,original).stream().filter(Objects::nonNull).findFirst();}
    public void insertar(Vinculo v){jdbc.update("""
        INSERT INTO derivada_material_ia(organizacion_id,id_operacion,id_propiedad,id_foto,original_ref,archivo_ref,operacion_io,numero_io)
        VALUES (?,?,?,?,?,?,?,?)
        """,v.organizacion(),v.job(),v.propiedad(),v.foto(),v.original(),v.archivo(),v.operacionIo(),v.numeroIo());}
    public void asegurarOriginal(long org,Foto f){jdbc.update("""
        INSERT INTO seleccion_material_ia(organizacion_id,original_ref,id_propiedad,id_foto) VALUES (?,?,?,?) ON CONFLICT DO NOTHING
        """,org,f.original(),f.propiedad(),f.foto());}
    public Optional<Original> seleccion(long org,UUID original){return jdbc.query("SELECT original_ref,revision,revision_material,id_operacion,archivo_ref FROM seleccion_material_ia WHERE organizacion_id=? AND original_ref=?",
            (r,n)->new Original(r.getObject(1,UUID.class),r.getLong(2),r.getLong(3),r.getObject(4,UUID.class),r.getObject(5,UUID.class)),org,original).stream().findFirst();}
    public boolean seleccionada(long org,UUID job){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM seleccion_material_ia WHERE organizacion_id=? AND id_operacion=?)",Boolean.class,org,job));}
    public int cambiarSeleccion(long org,UUID original,long esperada,UUID job,UUID archivo){return jdbc.update("UPDATE seleccion_material_ia SET id_operacion=?,archivo_ref=?,revision=revision+1,revision_material=revision_material+1,actualizada_en=clock_timestamp() WHERE organizacion_id=? AND original_ref=? AND revision=?",
            job,archivo,org,original,esperada);}
    public void eventoSeleccion(long org,UUID original,long revision,UUID job,UUID archivo,UUID anterior,UUID archivoAnterior,long persona,long rol,String banda,String sid,long contexto,Instant cuando){jdbc.update("""
        INSERT INTO seleccion_material_ia_evento(organizacion_id,original_ref,revision,id_operacion,archivo_ref,id_operacion_anterior,archivo_anterior_ref,id_persona,id_rol,rol_efectivo,sesion_contexto,revision_contexto,registrada_en)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,org,original,revision,job,archivo,anterior,archivoAnterior,persona,rol,banda,sid,contexto,fecha(cuando));}
    public void tocarOriginal(long org,UUID original){jdbc.update("UPDATE seleccion_material_ia SET revision_material=revision_material+1,actualizada_en=clock_timestamp() WHERE organizacion_id=? AND original_ref=?",org,original);}
    private static final String SLOTS="""
        SELECT s.organizacion_id,s.id_operacion,s.original_ref,s.revision,s.estado,s.guardada,s.evento_generacion,s.generada_en,
         s.temporal_desde,s.vence_en,s.ultimo_motivo,EXISTS(SELECT 1 FROM proteccion_derivada_material_ia p WHERE p.organizacion_id=s.organizacion_id AND p.id_operacion=s.id_operacion),s.namespace_hash
        FROM slot_derivada_material_ia s
        """;
    public Optional<Slot> slot(long org,UUID job){return jdbc.query(SLOTS+" WHERE s.organizacion_id=? AND s.id_operacion=?",this::slot,org,job).stream().findFirst();}
    public List<Slot> slots(long org,UUID original){return jdbc.query(SLOTS+" WHERE s.organizacion_id=? AND s.original_ref=? ORDER BY s.id_operacion",this::slot,org,original);}
    public List<UUID> originalesConSlots(long org){return jdbc.query("SELECT DISTINCT original_ref FROM slot_derivada_material_ia WHERE organizacion_id=? ORDER BY original_ref",(r,n)->r.getObject(1,UUID.class),org);}
    public void reservarSlot(long org,UUID job,UUID original,String namespaceHash){jdbc.update("INSERT INTO slot_derivada_material_ia(organizacion_id,id_operacion,original_ref,estado,ultimo_motivo,namespace_hash) VALUES (?,?,?,'RESERVADA','ADMISION',?) ON CONFLICT DO NOTHING",org,job,original,namespaceHash);tocarOriginal(org,original);}
    public List<Candidato> candidatos(String namespace,Long orgDesde,UUID jobDesde,int limite){return jdbc.query("""
        SELECT s.organizacion_id,s.id_operacion,s.revision FROM slot_derivada_material_ia s
        JOIN material_ia_operacion j ON j.organizacion_id=s.organizacion_id AND j.id_operacion=s.id_operacion
        WHERE s.namespace_hash=? AND (?::bigint IS NULL OR (s.organizacion_id,s.id_operacion)>(?::bigint,?::uuid))
        AND (s.estado='RETIRADA_PENDIENTE' OR
         (s.estado IN('GENERADA','DISPONIBLE') AND NOT s.guardada AND s.vence_en<=clock_timestamp()
          AND EXISTS(SELECT 1 FROM custodia_resultado_material_ia c WHERE c.organizacion_id=s.organizacion_id AND c.id_operacion=s.id_operacion)
          AND NOT EXISTS(SELECT 1 FROM seleccion_material_ia o WHERE o.organizacion_id=s.organizacion_id AND o.id_operacion=s.id_operacion)
          AND NOT EXISTS(SELECT 1 FROM proteccion_derivada_material_ia p WHERE p.organizacion_id=s.organizacion_id AND p.id_operacion=s.id_operacion)) OR
         (s.estado NOT IN('NO_ALMACENADA','RETIRADA') AND j.estado IN('OBSERVADA','RECHAZADA','CANCELADA')
          AND NOT EXISTS(SELECT 1 FROM custodia_resultado_material_ia d WHERE d.organizacion_id=s.organizacion_id AND d.id_operacion=s.id_operacion)))
        ORDER BY s.organizacion_id,s.id_operacion LIMIT ?
        """,(r,n)->new Candidato(r.getLong(1),r.getObject(2,UUID.class),r.getLong(3)),namespace,orgDesde,orgDesde,jobDesde,limite);}
    public int actualizarSlot(Slot s,String estado,boolean guardada,Long evento,Instant generada,Instant desde,Instant vence,String motivo){
        int n=jdbc.update("UPDATE slot_derivada_material_ia SET estado=?,guardada=?,evento_generacion=?,generada_en=?,procedencia_fecha=?,temporal_desde=?,vence_en=?,ultimo_motivo=?,revision_inicio=CASE WHEN ?='CAMBIO_SELECCION' THEN (SELECT revision FROM seleccion_material_ia WHERE organizacion_id=? AND original_ref=?) ELSE revision_inicio END,revision=revision+1,actualizada_en=clock_timestamp() WHERE organizacion_id=? AND id_operacion=? AND revision=?",
                estado,guardada,evento,fecha(generada),generada==null?null:"OBSERVADA_CORE",fecha(desde),fecha(vence),motivo,motivo,s.organizacion(),s.original(),s.organizacion(),s.job(),s.revision());
        if(n==1)tocarOriginal(s.organizacion(),s.original());return n;
    }
    public Optional<Generacion> generacion(long org,UUID job){return jdbc.query("SELECT id_evento,registrada_en FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='MEDIDA' ORDER BY id_evento LIMIT 1",
            (r,n)->new Generacion(r.getLong(1),instante(r,2)),org,job).stream().findFirst();}
    public Evento evento(long org,UUID job,long revision,String causa,String datos,String huella){return jdbc.queryForObject("INSERT INTO retencion_material_ia_evento(organizacion_id,id_operacion,revision,causa,datos,huella) VALUES (?,?,?,?,?,?) RETURNING id_evento,registrada_en",
            (r,n)->new Evento(r.getLong(1),org,job,causa,datos,huella,instante(r,2)),org,job,revision,causa,datos,huella);}
    public Optional<Evento> evento(long org,long id){return jdbc.query("SELECT id_evento,id_operacion,causa,datos,huella,registrada_en FROM retencion_material_ia_evento WHERE organizacion_id=? AND id_evento=?",
            (r,n)->new Evento(r.getLong(1),org,r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getString(5),instante(r,6)),org,id).stream().findFirst();}
    public Optional<Evento> ultimoEvento(long org,UUID job,String causa){return jdbc.query("SELECT id_evento,datos,huella,registrada_en,revision FROM retencion_material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND causa=? ORDER BY id_evento DESC LIMIT 1",
            (r,n)->new Evento(r.getLong(1),org,job,causa,r.getString(2),r.getString(3),instante(r,4)),org,job,causa).stream().findFirst();}
    public Optional<Long> eventoVersion(Slot s){return jdbc.query("SELECT id_evento FROM retencion_material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND revision=? AND causa=? ORDER BY id_evento LIMIT 1",(r,n)->r.getLong(1),s.organizacion(),s.job(),s.revision(),s.motivo()).stream().findFirst();}
    public void causa(long org,UUID original,String huella,List<MiembroCausa> miembros){
        jdbc.update("INSERT INTO causa_caducidad_material_ia(organizacion_id,original_ref,huella) VALUES (?,?,?) ON CONFLICT DO NOTHING",org,original,huella);
        for(var m:miembros)jdbc.update("INSERT INTO miembro_causa_caducidad_material_ia(organizacion_id,original_ref,huella,id_operacion,revision,evento_retencion,temporal_desde,vence_en) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",org,original,huella,m.job(),m.revision(),m.eventoRetencion(),fecha(m.temporalDesde()),fecha(m.venceEn()));
    }
    public List<MiembroCausa> causa(long org,UUID original,String huella){return jdbc.query("SELECT id_operacion,revision,evento_retencion,temporal_desde,vence_en FROM miembro_causa_caducidad_material_ia WHERE organizacion_id=? AND original_ref=? AND huella=? ORDER BY id_operacion",(r,n)->new MiembroCausa(r.getObject(1,UUID.class),r.getLong(2),r.getLong(3),instante(r,4),instante(r,5)),org,original,huella);}
    public Optional<Gestion> gestion(long org,String clave){return jdbc.query("SELECT huella_peticion,respuesta,id_persona,id_rol FROM gestion_material_ia WHERE organizacion_id=? AND clave_huella=?",
            (r,n)->new Gestion(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4)),org,clave).stream().findFirst();}
    public void gestionar(long org,String clave,String peticion,String respuesta,long persona,long rol){jdbc.update("INSERT INTO gestion_material_ia(organizacion_id,clave_huella,huella_peticion,respuesta,id_persona,id_rol) VALUES (?,?,?,?,?,?)",org,clave,peticion,respuesta,persona,rol);}
    public Optional<Pieza> pieza(long org,UUID pieza){return jdbc.query("SELECT organizacion_id,version_contenido,id_propiedad,solicitud,archivo_difusion_ref FROM pieza_comercial WHERE organizacion_id=? AND referencia=?",
            (r,n)->new Pieza(r.getLong(1),r.getLong(2),r.getLong(3),r.getString(4),r.getObject(5,UUID.class)),org,pieza).stream().findFirst();}
    public void proteger(long org,UUID job,UUID archivo,UUID pieza,long version,long revision){jdbc.update("INSERT INTO proteccion_derivada_material_ia(organizacion_id,id_operacion,archivo_ref,pieza_ref,version_pieza,revision_seleccion) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",org,job,archivo,pieza,version,revision);}
    public Optional<Retiro> retiro(long org,UUID job){return jdbc.query("SELECT id_retiro,id_operacion,archivo_ref,revision,causa,estado,identidad_objeto,bytes,huella,referencia_comprobacion,comprobada_en FROM retirada_derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",
            (r,n)->new Retiro(r.getObject(1,UUID.class),org,r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getLong(4),r.getString(5),r.getString(6),r.getString(7),r.getLong(8),r.getString(9),r.getString(10),instante(r,11)),org,job).stream().findFirst();}
    public void iniciarRetiro(Retiro r){jdbc.update("INSERT INTO retirada_derivada_material_ia(id_retiro,organizacion_id,id_operacion,archivo_ref,revision,causa,estado,identidad_objeto,bytes,huella) VALUES (?,?,?,?,?,?,'INTENCION',?,?,?)",
            r.id(),r.organizacion(),r.job(),r.archivo(),r.revision(),r.causa(),r.identidadObjeto(),r.bytes(),r.huella());}
    public int ausencia(Retiro r,String prueba,Instant fecha){return jdbc.update("UPDATE retirada_derivada_material_ia SET estado='AUSENCIA_COMPROBADA',referencia_comprobacion=?,comprobada_en=? WHERE organizacion_id=? AND id_retiro=? AND estado='INTENCION'",prueba,fecha(fecha),r.organizacion(),r.id());}
    public int liberar(Retiro r){return jdbc.update("UPDATE retirada_derivada_material_ia SET estado='LIBERADA' WHERE organizacion_id=? AND id_retiro=? AND estado='AUSENCIA_COMPROBADA'",r.organizacion(),r.id());}
    private Slot slot(ResultSet r,int n)throws SQLException{
        Long evento=r.getObject(7,Long.class);return new Slot(r.getLong(1),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getLong(4),r.getString(5),r.getBoolean(6),evento,instante(r,8),instante(r,9),instante(r,10),r.getString(11),r.getBoolean(12),r.getString(13));
    }
    private static Instant instante(ResultSet r,int i)throws SQLException{var t=r.getTimestamp(i);return t==null?null:t.toInstant();}
    private static OffsetDateTime fecha(Instant i){return i==null?null:i.atOffset(ZoneOffset.UTC);}
}
