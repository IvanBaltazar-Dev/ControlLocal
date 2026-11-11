package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Persistencia I0; las consultas privadas llevan organización. La colección de plataforma exige R25 en Core. */
@Repository
public class InvestigacionesMercadoRepository {
    public record Politica(UUID id,String version,String configuracion,UUID regla,String versionRegla,UUID decision,
                           long responsable,String evidencia,String huella,Instant desde,Instant hasta,boolean habilitada,Instant retirada) { }
    public record Trabajo(long id,UUID solicitud,Long organizacion,Long persona,Long rol,Long propiedad,Long solicitante,
                          UUID politica,String versionPolitica,String huellaSolicitud,String huellaRecurso,String contexto,String plan,
                          BigDecimal rondas,BigDecimal maximo,Instant aceptada,String estado,long version,Long operador,UUID concesionOperador,
                          String entrega,String huellaEntrega,UUID revision,Long revisor,UUID concesionRevision,BigDecimal acreditadas,
                          Instant liquidada,BigDecimal rondasLiquidadas,UUID revisionLiquidada,Instant creada) { }
    public record Evento(String huella,String tipo,long version) { }
    public record Cabecera(long id,String estado,long version,Instant creada,Instant plazo) { }
    public enum Cola { EJECUCION,REVISION,FINANZAS }
    public record Publicada(UUID id,long trabajo,long versionTrabajo,UUID politica,String versionPolitica,String segmento,
                            String proyeccion,String huella,long revisor,UUID concesion,Instant desde,Instant hasta,Instant publicada) { }
    private final JdbcTemplate jdbc;
    public InvestigacionesMercadoRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Instant ahora(){return jdbc.queryForObject("select clock_timestamp()",OffsetDateTime.class).toInstant();}
    public void bloquearSolicitud(Long org,UUID id){jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))",r->{},"I0:"+(org==null?"PLATAFORMA":org)+":"+id);}
    public Optional<Politica> politica(UUID id,String version,boolean bloquear){
        return jdbc.query("select * from i0_politica_mercado where id=? and version=?"+(bloquear?" for share":""),(r,n)->new Politica(r.getObject("id",UUID.class),r.getString("version"),r.getString("configuracion"),r.getObject("regla",UUID.class),r.getString("version_regla"),r.getObject("decision",UUID.class),r.getLong("responsable"),r.getString("evidencia"),r.getString("huella"),instante(r,"desde"),instante(r,"hasta"),r.getBoolean("habilitada"),instante(r,"retirada_en")),id,version).stream().findFirst();
    }
    public void guardarPolitica(Politica p){
        jdbc.update("""
            insert into i0_politica_mercado(id,version,configuracion,regla,version_regla,decision,responsable,evidencia,huella,desde,hasta,habilitada)
            values(?,?,?::jsonb,?,?,?,?,?,?,?,?,?) on conflict do nothing
            """,p.id(),p.version(),p.configuracion(),p.regla(),p.versionRegla(),p.decision(),p.responsable(),p.evidencia(),p.huella(),utc(p.desde()),utc(p.hasta()),p.habilitada());
    }
    public Optional<Trabajo> trabajo(Long org,UUID id,boolean bloquear){return jdbc.query("select * from i0_trabajo_mercado where organizacion_id is not distinct from ?::bigint and id_solicitud=?"+(bloquear?" for update":""),InvestigacionesMercadoRepository::trabajo,org,id).stream().findFirst();}
    public Optional<Trabajo> trabajoPlataforma(long id,boolean bloquear){return jdbc.query("select * from i0_trabajo_mercado where id=?"+(bloquear?" for update":""),InvestigacionesMercadoRepository::trabajo,id).stream().findFirst();}
    public Optional<String> rolSolicitante(Trabajo t){return jdbc.query("select tipo_rol from persona_rol where organizacion_id=? and id_persona=? and id_persona_rol=?",(r,n)->r.getString(1),t.organizacion(),t.persona(),t.rol()).stream().findFirst();}
    /** Sólo cabeceras; la entrega de contexto/evidencia se proyecta después de exigir los campos R25. */
    public List<Cabecera> cola(long despues,int cantidad,Cola cola){
        String filtro=switch(cola){case EJECUCION->"estado in('PENDIENTE','ASIGNADA','OBSERVADA','RECHAZADA','INCIDENCIA')";case REVISION->"estado='ENTREGADA'";case FINANZAS->"estado in('REVISADA','PUBLICADA') and liquidada_en is null";};
        return jdbc.query("select id,estado,version,creada_en,(plan->>'plazoComunicado')::timestamptz as plazo from i0_trabajo_mercado where id>? and "+filtro+" order by id limit ?",(r,n)->new Cabecera(r.getLong(1),r.getString(2),r.getLong(3),instante(r,"creada_en"),instante(r,"plazo")),despues,cantidad);
    }
    public Optional<UUID> referenciaDelTrabajo(long trabajo){return jdbc.query("select id from i0_referencia_aprobada where trabajo=? and retirada_en is null order by publicada_en desc,id limit 1",(r,n)->r.getObject(1,UUID.class),trabajo).stream().findFirst();}
    public boolean retirarPolitica(UUID id,String version,UUID decision,String motivo){
        jdbc.query("select id from i0_politica_mercado where id=? and version=? for update",r->{},id,version);
        if(jdbc.update("update i0_politica_mercado set retirada_en=clock_timestamp(),decision_retiro=?,motivo_retiro=? where id=? and version=? and retirada_en is null",decision,motivo,id,version)==1)return true;
        return Boolean.TRUE.equals(jdbc.queryForObject("select decision_retiro=? and motivo_retiro=? from i0_politica_mercado where id=? and version=?",Boolean.class,decision,motivo,id,version));
    }
    public long insertar(UUID solicitud,Long org,Long persona,Long rol,Long propiedad,Long cuenta,UUID politica,String version,
                         String huella,String origen,String contexto,String plan,BigDecimal rondas,BigDecimal maximo,Instant ahora){
        return jdbc.queryForObject("""
            insert into i0_trabajo_mercado(id_solicitud,organizacion_id,id_persona,id_rol,id_propiedad,cuenta_solicitante,politica,version_politica,
              huella_solicitud,huella_recurso,contexto_minimo,plan,rondas_previstas,maximo_aceptado,aceptada_en,creada_en)
            values(?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?) returning id
            """,Long.class,solicitud,org,persona,rol,propiedad,cuenta,politica,version,huella,origen,contexto,plan,rondas,maximo,utc(ahora),utc(ahora));
    }
    public boolean asignar(long id,long version,long cuenta,UUID concesion){return jdbc.update("update i0_trabajo_mercado set operador=?,concesion_operador=?,estado='ASIGNADA',version=version+1 where id=? and version=?",cuenta,concesion,id,version)==1;}
    public boolean entregar(long id,long version,String entrega,String huella){return jdbc.update("update i0_trabajo_mercado set entrega=?::jsonb,huella_entrega=?,estado='ENTREGADA',revision=null,cuenta_revisora=null,concesion_revision=null,rondas_acreditadas=null,version=version+1 where id=? and version=?",entrega,huella,id,version)==1;}
    public boolean revisar(long id,long version,String estado,UUID revision,long cuenta,UUID concesion,String entrega,String huella,BigDecimal rondas){
        return jdbc.update("update i0_trabajo_mercado set estado=?,revision=?,cuenta_revisora=?,concesion_revision=?,entrega=?::jsonb,huella_entrega=?,rondas_acreditadas=?,version=version+1 where id=? and version=?",estado,revision,cuenta,concesion,entrega,huella,rondas,id,version)==1;
    }
    public boolean estado(long id,long version,String estado){return jdbc.update("update i0_trabajo_mercado set estado=?,version=version+1 where id=? and version=?",estado,id,version)==1;}
    public boolean liquidar(long id,long version,Instant ahora){return jdbc.update("update i0_trabajo_mercado set liquidada_en=?,rondas_liquidadas=rondas_acreditadas,revision_liquidada=revision,version=version+1 where id=? and version=? and liquidada_en is null",utc(ahora),id,version)==1;}
    public Optional<Evento> evento(long trabajo,UUID comando){return jdbc.query("select huella,tipo,version_trabajo from i0_evento_mercado where trabajo=? and comando=?",(r,n)->new Evento(r.getString(1),r.getString(2),r.getLong(3)),trabajo,comando).stream().findFirst();}
    public void evento(long trabajo,UUID comando,String huella,String tipo,long version,Long cuenta,Long org,Long rol,UUID concesion,String contenido){
        jdbc.update("insert into i0_evento_mercado(trabajo,comando,huella,tipo,version_trabajo,cuenta,organizacion_id,id_rol,concesion,contenido) values(?,?,?,?,?,?,?,?,?,?::jsonb)",trabajo,comando,huella,tipo,version,cuenta,org,rol,concesion,contenido);
    }
    public void publicar(Publicada p){jdbc.update("""
        insert into i0_referencia_aprobada(id,trabajo,version_trabajo,politica,version_politica,segmento,proyeccion,huella,revisor,concesion,desde,hasta)
        values(?,?,?,?,?,?,?::jsonb,?,?,?,?,?)
        """,p.id(),p.trabajo(),p.versionTrabajo(),p.politica(),p.versionPolitica(),p.segmento(),p.proyeccion(),p.huella(),p.revisor(),p.concesion(),utc(p.desde()),utc(p.hasta()));}
    public void retirarReferencias(long trabajo,long cuenta,UUID concesion,String motivo){jdbc.update("update i0_referencia_aprobada set retirada_en=clock_timestamp(),retirada_por=?,concesion_retiro=?,motivo_retiro=? where trabajo=? and retirada_en is null",cuenta,concesion,motivo,trabajo);}
    public Optional<Publicada> ultima(String segmento,Instant ahora){return referencia(segmento,ahora,false);}
    public Optional<Publicada> historica(String segmento,Instant ahora){return referencia(segmento,ahora,true);}
    private Optional<Publicada> referencia(String segmento,Instant ahora,boolean historica){
        String vigencia=historica?"r.hasta<=? and p.configuracion->>'permiteReferenciaHistorica'='true'":"r.hasta>?";
        return jdbc.query("""
        select r.* from i0_referencia_aprobada r join i0_politica_mercado p on p.id=r.politica and p.version=r.version_politica
         where r.segmento=? and r.retirada_en is null and p.habilitada and p.retirada_en is null and p.desde<=? and p.hasta>?
           and r.desde<=? and
        """+vigencia+" order by r.publicada_en desc,r.id limit 1",(r,n)->new Publicada(r.getObject("id",UUID.class),r.getLong("trabajo"),r.getLong("version_trabajo"),r.getObject("politica",UUID.class),r.getString("version_politica"),r.getString("segmento"),r.getString("proyeccion"),r.getString("huella"),r.getLong("revisor"),r.getObject("concesion",UUID.class),instante(r,"desde"),instante(r,"hasta"),instante(r,"publicada_en")),segmento,utc(ahora),utc(ahora),utc(ahora),utc(ahora)).stream().findFirst();
    }
    private static Trabajo trabajo(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new Trabajo(r.getLong("id"),r.getObject("id_solicitud",UUID.class),r.getObject("organizacion_id",Long.class),r.getObject("id_persona",Long.class),r.getObject("id_rol",Long.class),r.getObject("id_propiedad",Long.class),r.getObject("cuenta_solicitante",Long.class),r.getObject("politica",UUID.class),r.getString("version_politica"),r.getString("huella_solicitud"),r.getString("huella_recurso"),r.getString("contexto_minimo"),r.getString("plan"),r.getBigDecimal("rondas_previstas"),r.getBigDecimal("maximo_aceptado"),instante(r,"aceptada_en"),r.getString("estado"),r.getLong("version"),r.getObject("operador",Long.class),r.getObject("concesion_operador",UUID.class),r.getString("entrega"),r.getString("huella_entrega"),r.getObject("revision",UUID.class),r.getObject("cuenta_revisora",Long.class),r.getObject("concesion_revision",UUID.class),r.getBigDecimal("rondas_acreditadas"),instante(r,"liquidada_en"),r.getBigDecimal("rondas_liquidadas"),r.getObject("revision_liquidada",UUID.class),instante(r,"creada_en"));}
    private static Instant instante(java.sql.ResultSet r,String col)throws java.sql.SQLException {var t=r.getTimestamp(col);return t==null?null:t.toInstant();}
    private static OffsetDateTime utc(Instant i){return i==null?null:i.atOffset(ZoneOffset.UTC);}
}
