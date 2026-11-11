package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

/** Persistencia del único seguimiento R27; las comunicaciones originales no se reescriben. */
@Repository
public class SeguimientoProspeccionRepository {
    public record Contexto(long id,long responsable,long propiedad,Long encargo,String estado,boolean retirado,boolean encargoIniciado) { }
    public record Hecho(long id,long version,Instant registrado,String proyeccionJson) { }
    public record DestinoVerificado(long prospeccion,long propiedad,long persona) { }
    public record RestriccionActual(long prospeccion,long persona,boolean restringido,boolean alcancePorPrecisar) { }
    public record ClaveVersion(long seguimiento,long version) { }
    public record Constancia(long seguimiento,long version,String fuentesJson) { }
    public record Fila(long id,long prospeccion,String destinatario,long responsable,long version,
                       Long inicial,Long ultimo,Long versionUltimo,LocalDate fechaUltimo,int recontactos,
                       LocalDate proxima,String origen,LocalDate pospuesta,String restriccionJson,String contextoJson,
                       Instant registrado,Instant actualizado,Long antecedente) { }
    private final JdbcTemplate jdbc;
    private static final String RE07="""
        exists(select 1 from captacion c where c.organizacion_id=p.organizacion_id
          and (c.id_prospeccion_origen=p.id_prospeccion or c.id_captacion=p.id_captacion))
        """;
    public SeguimientoProspeccionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void conservarVersion(long org,long seguimiento,long version,String fuentes,Instant registrado){
        jdbc.update("insert into seguimiento_prospeccion_version(organizacion_id,id_seguimiento,version,fuentes_json,registrado_en) values(?,?,?,?,?)",org,seguimiento,version,fuentes,java.sql.Timestamp.from(registrado));
    }
    public List<Constancia> constancias(long org,Collection<ClaveVersion> versiones){
        if(versiones.isEmpty())return List.of();var lista=List.copyOf(new LinkedHashSet<>(versiones));
        return jdbc.query("""
            select s.id_seguimiento,s.version,s.fuentes_json from seguimiento_prospeccion_version s
            join unnest(?::bigint[],?::bigint[]) k(id,version) on s.id_seguimiento=k.id and s.version=k.version
            where s.organizacion_id=?
            """,(r,n)->new Constancia(r.getLong(1),r.getLong(2),r.getString(3)),array(lista.stream().map(ClaveVersion::seguimiento).toList()),array(lista.stream().map(ClaveVersion::version).toList()),org);
    }
    public Optional<Fila> porDestino(long org,long prospeccion,String clave){return jdbc.query("select "+CAMPOS+" from seguimiento_prospeccion s where s.organizacion_id=? and s.id_prospeccion=? and s.destinatario_clave=? and s.retirado_en is null",SeguimientoProspeccionRepository::fila,org,prospeccion,clave).stream().findFirst();}
    public Optional<Contexto> contexto(long org,long id,boolean bloquear){
        return jdbc.query("select p.id_prospeccion,p.id_rol_agente,p.id_propiedad,p.id_captacion,p.estado,p.r27_retirado_en is not null,"+RE07+" from prospeccion p where p.organizacion_id=? and p.id_prospeccion=?"+(bloquear?" for update of p":""),
                (r,n)->new Contexto(r.getLong(1),r.getLong(2),r.getLong(3),r.getObject(4,Long.class),r.getString(5),r.getBoolean(6),r.getBoolean(7)),org,id).stream().findFirst();
    }
    public void bloquearParaProyeccion(long org,long responsable){
        jdbc.query("select p.id_prospeccion from prospeccion p where p.organizacion_id=? and p.id_rol_agente=? and exists(select 1 from seguimiento_prospeccion s where s.organizacion_id=p.organizacion_id and s.id_prospeccion=p.id_prospeccion and s.retirado_en is null) order by p.id_prospeccion for share of p",(r,n)->r.getLong(1),org,responsable);
    }
    public Optional<Long> propietario(long org,long propiedad){
        return jdbc.query("select id_rol_propietario from propiedad where organizacion_id=? and id_propiedad=? and id_rol_propietario is not null",(r,n)->r.getLong(1),org,propiedad).stream().findFirst();
    }
    /** Sólo recibe contextos y personas previamente autorizados; no devuelve el hecho privado. */
    public List<RestriccionActual> restriccionesContextuales(long org,Collection<DestinoVerificado> destinos,String finalidad){
        if(destinos.isEmpty())return List.of();
        for(var d:destinos)if(d==null||d.prospeccion()<=0||d.propiedad()<=0||d.persona()<=0)throw new IllegalArgumentException("Destino contextual inválido.");
        var lista=List.copyOf(new LinkedHashSet<>(destinos));
        String prospecciones=array(lista.stream().map(DestinoVerificado::prospeccion).toList());
        String propiedades=array(lista.stream().map(DestinoVerificado::propiedad).toList());
        String personas=array(lista.stream().map(DestinoVerificado::persona).toList());
        return jdbc.query("""
            with destinos as (select * from unnest(?::bigint[],?::bigint[],?::bigint[]) d(prospeccion,propiedad,persona)),
            restricciones as (
              select d.prospeccion,d.persona,
                upper(nullif(btrim(i.contexto_declarado_json::jsonb#>>'{datos,restriccion,finalidad}'),'')) finalidad,
                (select count(*) from interaccion_vinculo x where x.organizacion_id=i.organizacion_id
                  and x.id_interaccion=i.id_interaccion and x.retirado_en is null
                  and x.tipo in ('PROPIEDAD','PROSPECCION','ENCARGO','OPORTUNIDAD')) ambitos
              from destinos d join interaccion_comercial i on i.organizacion_id=?
                and (i.autorizacion_json::jsonb#>>'{contactos,restriccion,persona}')::bigint=d.persona
              where jsonb_typeof(i.contexto_declarado_json::jsonb#>'{datos,restriccion}')='object'
                and exists(select 1 from interaccion_vinculo v where v.organizacion_id=i.organizacion_id
                and v.id_interaccion=i.id_interaccion and v.retirado_en is null and
                ((v.tipo='PROPIEDAD' and v.id_propiedad=d.propiedad) or (v.tipo='PROSPECCION' and v.id_prospeccion=d.prospeccion)))
            )
            select prospeccion,persona,bool_or(finalidad=? and ambitos=1),bool_or(finalidad is null or ambitos<>1)
            from restricciones where finalidad is null or finalidad=? group by prospeccion,persona
            """,(r,n)->new RestriccionActual(r.getLong(1),r.getLong(2),r.getBoolean(3),r.getBoolean(4)),
                prospecciones,propiedades,personas,org,finalidad,finalidad);
    }
    private static String array(Collection<Long> valores){return valores.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",","{","}"));}
    public List<Hecho> hechos(long org,long prospeccion){
        return jdbc.query("""
            select i.id_interaccion,i.version,i.registrado_en,
                   jsonb_build_object('hechos',coalesce(nullif(i.contexto_declarado_json::jsonb->'seguimiento','null'::jsonb),'[]'::jsonb),
                     'literal',case when i.contexto_declarado_json::jsonb#>'{datos,gestionContacto,continuarConInterlocutor}'='true'::jsonb
                       and nullif(i.contexto_declarado_json::jsonb#>'{datos,interlocutor,contacto}','null'::jsonb) is null
                       then jsonb_build_object('declarado',jsonb_build_object('tipo','GESTOR_COMERCIAL',
                         'nombre',i.contexto_declarado_json::jsonb#>>'{datos,interlocutor,nombre}',
                         'telefono',i.contexto_declarado_json::jsonb#>>'{datos,interlocutor,telefono}',
                         'fuente',coalesce(nullif(i.contexto_declarado_json::jsonb#>>'{datos,gestionContacto,fuenteFunciones}',''),i.contexto_declarado_json::jsonb#>>'{datos,fuente}')),
                         'referencia',i.contexto_declarado_json::jsonb#>>'{datos,interlocutor,referenciaDeclarada}')
                       else null end)::text
            from interaccion_comercial i where i.organizacion_id=? and exists(
                select 1 from interaccion_vinculo v where v.organizacion_id=i.organizacion_id and v.id_interaccion=i.id_interaccion
                    and v.tipo='PROSPECCION' and v.id_prospeccion=? and v.retirado_en is null)
            order by i.registrado_en,i.id_interaccion
            """,(r,n)->new Hecho(r.getLong(1),r.getLong(2),r.getTimestamp(3)==null?null:r.getTimestamp(3).toInstant(),r.getString(4)),org,prospeccion);
    }
    private static final String CAMPOS="s.id_seguimiento,s.id_prospeccion,s.destinatario_clave,s.id_rol_responsable,s.version,s.id_interaccion_inicial,s.id_ultimo_intento,s.version_ultimo_intento,s.fecha_ultimo_intento,s.recontactos_sin_respuesta,s.proxima_fecha,s.origen_fecha,s.pospuesta_hasta,s.restriccion_json,s.contexto_json,s.registrado_en,s.actualizado_en,s.id_antecedente";
    public List<Fila> activas(long org,long prospeccion){return jdbc.query("select "+CAMPOS+" from seguimiento_prospeccion s where s.organizacion_id=? and s.id_prospeccion=? and s.retirado_en is null order by s.id_seguimiento",SeguimientoProspeccionRepository::fila,org,prospeccion);}
    public Optional<Long> ultimoEstado(long org,long prospeccion,String destinatario){return jdbc.query("select id_seguimiento from seguimiento_prospeccion where organizacion_id=? and id_prospeccion=? and destinatario_clave=? and finalidad='RECONTACTAR_PROPIETARIO' order by id_seguimiento desc limit 1",(r,n)->r.getLong(1),org,prospeccion,destinatario).stream().findFirst();}
    public List<Fila> enAlcance(long org,Collection<Long> roles){
        if(roles.isEmpty())return List.of();String array=roles.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",","{","}"));
        return jdbc.query("select "+CAMPOS+" from seguimiento_prospeccion s join prospeccion p on p.organizacion_id=s.organizacion_id and p.id_prospeccion=s.id_prospeccion where s.organizacion_id=? and p.id_rol_agente=any(?::bigint[]) and p.estado in ('P','C','R','E','S') and p.id_captacion is null and p.r27_retirado_en is null and not "+RE07+" and s.retirado_en is null order by s.id_seguimiento",SeguimientoProspeccionRepository::fila,org,array);
    }
    public void marcarRetiro(long org,long prospeccion,Instant ahora){jdbc.update("update prospeccion set r27_retirado_en=coalesce(r27_retirado_en,?) where organizacion_id=? and id_prospeccion=?",java.sql.Timestamp.from(ahora),org,prospeccion);}
    public List<Long> retirarPorEncargo(long org,long encargo,long responsable,Instant ahora){
        return jdbc.query("""
            update prospeccion p set r27_retirado_en=coalesce(p.r27_retirado_en,?)
             where p.organizacion_id=? and p.id_rol_agente=? and exists(
               select 1 from captacion c where c.organizacion_id=p.organizacion_id and c.id_captacion=?
                 and (c.id_prospeccion_origen=p.id_prospeccion or c.id_captacion=p.id_captacion))
             returning p.id_prospeccion
            """,(r,n)->r.getLong(1),java.sql.Timestamp.from(ahora),org,responsable,encargo);
    }
    public Fila insertar(long org,long prospeccion,String destinatario,long responsable,Long inicial,Long ultimo,Long versionUltimo,
                         LocalDate fechaUltimo,int recontactos,LocalDate proxima,String origen,LocalDate pospuesta,String restriccion,String contexto,Instant ahora,Long antecedente,String finalidad){
        if(finalidad==null||finalidad.isBlank())throw new IllegalArgumentException("La finalidad del seguimiento es obligatoria.");
        Long id=jdbc.queryForObject("""
            insert into seguimiento_prospeccion(organizacion_id,id_prospeccion,destinatario_clave,id_rol_responsable,
                id_interaccion_inicial,id_ultimo_intento,version_ultimo_intento,fecha_ultimo_intento,recontactos_sin_respuesta,
                proxima_fecha,origen_fecha,pospuesta_hasta,restriccion_json,contexto_json,politica_version,registrado_en,actualizado_en,id_antecedente,finalidad)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, ?,?,?,?) returning id_seguimiento
            """,Long.class,org,prospeccion,destinatario,responsable,inicial,ultimo,versionUltimo,fechaUltimo,recontactos,proxima,origen,pospuesta,restriccion,contexto,"CAT-R27-20261030-1",java.sql.Timestamp.from(ahora),java.sql.Timestamp.from(ahora),antecedente,finalidad);
        return porId(org,Objects.requireNonNull(id)).orElseThrow();
    }
    public int actualizar(long org,Fila anterior,long responsable,Long inicial,Long ultimo,Long versionUltimo,LocalDate fechaUltimo,int recontactos,
                          LocalDate proxima,String origen,LocalDate pospuesta,String restriccion,String contexto,Instant ahora){
        return jdbc.update("""
            update seguimiento_prospeccion set id_rol_responsable=?,id_interaccion_inicial=?,id_ultimo_intento=?,version_ultimo_intento=?,
                fecha_ultimo_intento=?,recontactos_sin_respuesta=?,proxima_fecha=?,origen_fecha=?,pospuesta_hasta=?,restriccion_json=?,contexto_json=?,
                actualizado_en=?,version=version+1 where organizacion_id=? and id_seguimiento=? and version=? and retirado_en is null
            """,responsable,inicial,ultimo,versionUltimo,fechaUltimo,recontactos,proxima,origen,pospuesta,restriccion,contexto,java.sql.Timestamp.from(ahora),org,anterior.id(),anterior.version());
    }
    public Optional<Fila> porId(long org,long id){return jdbc.query("select "+CAMPOS+" from seguimiento_prospeccion s where s.organizacion_id=? and s.id_seguimiento=? and s.retirado_en is null",SeguimientoProspeccionRepository::fila,org,id).stream().findFirst();}
    public Optional<Fila> porIdHistorico(long org,long id){return jdbc.query("select "+CAMPOS+" from seguimiento_prospeccion s where s.organizacion_id=? and s.id_seguimiento=?",SeguimientoProspeccionRepository::fila,org,id).stream().findFirst();}
    public void retirar(long org,long id,Instant ahora,String fuentes){jdbc.update("""
        with retirado as (update seguimiento_prospeccion set retirado_en=?,actualizado_en=?,version=version+1
          where organizacion_id=? and id_seguimiento=? and retirado_en is null returning organizacion_id,id_seguimiento,version)
        insert into seguimiento_prospeccion_version(organizacion_id,id_seguimiento,version,fuentes_json,registrado_en)
        select r.organizacion_id,r.id_seguimiento,r.version,coalesce(cast(? as text),h.fuentes_json),? from retirado r
          left join seguimiento_prospeccion_version h on h.organizacion_id=r.organizacion_id
            and h.id_seguimiento=r.id_seguimiento and h.version=r.version-1
        where cast(? as text) is not null or h.fuentes_json is not null
        """,java.sql.Timestamp.from(ahora),java.sql.Timestamp.from(ahora),org,id,fuentes,java.sql.Timestamp.from(ahora),fuentes);}
    private static Fila fila(java.sql.ResultSet r,int n)throws java.sql.SQLException{
        return new Fila(r.getLong(1),r.getLong(2),r.getString(3),r.getLong(4),r.getLong(5),r.getObject(6,Long.class),r.getObject(7,Long.class),r.getObject(8,Long.class),
                r.getObject(9,LocalDate.class),r.getInt(10),r.getObject(11,LocalDate.class),r.getString(12),r.getObject(13,LocalDate.class),r.getString(14),r.getString(15),r.getTimestamp(16).toInstant(),r.getTimestamp(17).toInstant(),r.getObject(18,Long.class));
    }
}
