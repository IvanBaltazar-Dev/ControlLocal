package com.controllocal.persistence.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

/** RF078/079: primero cabeceras/referencias; el cuerpo sólo después de autorizar sus fuentes en Core. */
@Repository
public class ReporteAvanceRepository {
    public record Cabecera(long id,UUID referencia,Long version,long encargo,long autor,LocalDate desde,LocalDate hasta,
                           Instant corte,String finalidad,String fuentes,UUID archivoPrevisto,Instant registrada) { }
    public record Comunicacion(long id,UUID referencia,long version,long reporte,String fuenteContacto,Instant registrada,Instant actualizada) { }
    public record VersionComunicacion(long version,String fuenteContacto,Instant registrada,String motivo) { }
    public record Antecedente(long id,LocalDate fecha,LocalDate desde,LocalDate hasta,int consultas,int visitas,String objeciones,
                              String recomendaciones,String canalHistorico,Instant registrada) { }
    public record HechoActividad(String tipo,long id,long version,long oportunidad,LocalDate fecha,String estado,
                                 Instant registrada,Long ofertaAnterior,LocalDate fechaProgramada,LocalTime horaProgramada) { }
    public record Oportunidad(long id,long version) { }
    private static final String H="id_reporte_propietario,referencia,version,id_captacion,id_rol_agente,periodo_inicio,periodo_fin,corte,finalidad_reporte,coalesce(fuentes_version,poblacion_interacciones),archivo_previsto_ref,fecha_creacion";
    private static final String C="id_comunicacion_reporte,referencia,version,id_reporte_propietario,fuente_contacto_actual,registrado_en,actualizado_en";
    private final JdbcTemplate jdbc;
    public ReporteAvanceRepository(JdbcTemplate j){jdbc=j;}
    private static Timestamp ts(Instant i){return i==null?null:Timestamp.from(i);}
    private static java.sql.Date fecha(LocalDate d){return d==null?null:java.sql.Date.valueOf(d);}
    private static Instant instante(ResultSet r,int i)throws SQLException{var t=r.getTimestamp(i);return t==null?null:t.toInstant();}
    private static Cabecera cabecera(ResultSet r,int n)throws SQLException{return new Cabecera(r.getLong(1),r.getObject(2,UUID.class),r.getObject(3,Long.class),r.getLong(4),r.getLong(5),r.getObject(6,LocalDate.class),r.getObject(7,LocalDate.class),instante(r,8),r.getString(9),r.getString(10),r.getObject(11,UUID.class),instante(r,12));}
    private static Comunicacion comunicacion(ResultSet r,int n)throws SQLException{return new Comunicacion(r.getLong(1),r.getObject(2,UUID.class),r.getLong(3),r.getLong(4),r.getString(5),instante(r,6),instante(r,7));}
    public void bloquearFamilia(long org,UUID ref){jdbc.query("select pg_advisory_xact_lock(hashtextextended('BROX:REPORTE:'||?::text||':'||?::text,0))",(r,n)->1,org,ref);}
    public Optional<Cabecera> version(long org,UUID ref,long version){return jdbc.query("select "+H+" from reporte_propietario where organizacion_id=? and referencia=? and version=?",ReporteAvanceRepository::cabecera,org,ref,version).stream().findFirst();}
    public Optional<Cabecera> ultima(long org,UUID ref){return jdbc.query("select "+H+" from reporte_propietario where organizacion_id=? and referencia=? order by version desc limit 1",ReporteAvanceRepository::cabecera,org,ref).stream().findFirst();}
    public Optional<Cabecera> porId(long org,long id){return jdbc.query("select "+H+" from reporte_propietario where organizacion_id=? and id_reporte_propietario=?",ReporteAvanceRepository::cabecera,org,id).stream().findFirst();}
    public Optional<Cabecera> porArchivo(long org,UUID ref){return jdbc.query("select "+H+" from reporte_propietario where organizacion_id=? and archivo_previsto_ref=?",ReporteAvanceRepository::cabecera,org,ref).stream().findFirst();}
    public List<Cabecera> listar(long org,long encargo,int pagina,int tamano){return jdbc.query("select "+H+" from reporte_propietario where organizacion_id=? and id_captacion=? order by fecha_creacion desc,id_reporte_propietario desc limit ? offset ?",ReporteAvanceRepository::cabecera,org,encargo,tamano,Math.multiplyExact((long)pagina-1,tamano));}
    public Optional<String> contenido(long org,long id){return jdbc.query("select contenido_version from reporte_propietario where organizacion_id=? and id_reporte_propietario=? and referencia is not null",(r,n)->r.getString(1),org,id).stream().filter(Objects::nonNull).findFirst();}
    public Optional<Antecedente> antecedente(long org,long id){return jdbc.query("select id_reporte_propietario,fecha_reporte,periodo_inicio,periodo_fin,consultas_reportadas,visitas_reportadas,objeciones_frecuentes,ajustes_recomendados,canal_envio,fecha_creacion from reporte_propietario where organizacion_id=? and id_reporte_propietario=? and referencia is null",(r,n)->new Antecedente(r.getLong(1),r.getObject(2,LocalDate.class),r.getObject(3,LocalDate.class),r.getObject(4,LocalDate.class),r.getInt(5),r.getInt(6),r.getString(7),r.getString(8),r.getString(9),instante(r,10)),org,id).stream().findFirst();}
    public Cabecera crear(long org,UUID ref,long version,long encargo,long autor,LocalDate desde,LocalDate hasta,Instant corte,
                           String finalidad,String contenido,String fuentes,UUID archivo,int consultas,int visitas,Instant ahora){
        return jdbc.query("""
            insert into reporte_propietario(organizacion_id,id_captacion,id_rol_agente,fecha_reporte,periodo_inicio,periodo_fin,
              consultas_reportadas,visitas_reportadas,canal_envio,fecha_creacion,referencia,version,corte,finalidad_reporte,contenido_version,fuentes_version,archivo_previsto_ref)
            values(?,?,?,?,?,?,?,?,null,?,?,?,?,?,?,?,?) returning
            """+H,ReporteAvanceRepository::cabecera,org,encargo,autor,fecha(ahora.atZone(ZoneId.of("America/Lima")).toLocalDate()),fecha(desde),fecha(hasta),consultas,visitas,ts(ahora),ref,version,ts(corte),finalidad,contenido,fuentes,archivo).getFirst();
    }
    public Optional<Comunicacion> comunicacion(long org,UUID ref,boolean lock){return jdbc.query("select "+C+" from comunicacion_reporte where organizacion_id=? and referencia=?"+(lock?" for update":""),ReporteAvanceRepository::comunicacion,org,ref).stream().findFirst();}
    public List<Comunicacion> comunicaciones(long org,long encargo,int pagina,int tamano){return jdbc.query("select "+Arrays.stream(C.split(",")).map(s->"c."+s).collect(java.util.stream.Collectors.joining(","))+" from comunicacion_reporte c join reporte_propietario r on r.organizacion_id=c.organizacion_id and r.id_reporte_propietario=c.id_reporte_propietario where c.organizacion_id=? and r.id_captacion=? order by c.registrado_en desc,c.id_comunicacion_reporte desc limit ? offset ?",ReporteAvanceRepository::comunicacion,org,encargo,tamano,Math.multiplyExact((long)pagina-1,tamano));}
    public List<VersionComunicacion> historia(long org,long id){return jdbc.query("select version,fuente_contacto,registrado_en,motivo from comunicacion_reporte_version where organizacion_id=? and id_comunicacion_reporte=? order by version",(r,n)->new VersionComunicacion(r.getLong(1),r.getString(2),instante(r,3),r.getString(4)),org,id);}
    public Optional<String> contenidoComunicacion(long org,long id,long version){return jdbc.query("select contenido from comunicacion_reporte_version where organizacion_id=? and id_comunicacion_reporte=? and version=?",(r,n)->r.getString(1),org,id,version).stream().findFirst();}
    public Comunicacion crearComunicacion(long org,UUID ref,long reporte,String contenido,String fuente,long actor,Instant ahora){
        var c=jdbc.query("insert into comunicacion_reporte(organizacion_id,referencia,id_reporte_propietario,version,contenido_actual,fuente_contacto_actual,registrado_en,actualizado_en) values(?,?,?,0,?,?,?,?) returning "+C,ReporteAvanceRepository::comunicacion,org,ref,reporte,contenido,fuente,ts(ahora),ts(ahora)).getFirst();
        versionar(c,contenido,fuente,actor,ahora,null,org);return c;
    }
    public Optional<Comunicacion> rectificar(Comunicacion c,long org,String contenido,String fuente,long actor,Instant ahora,String motivo){
        var filas=jdbc.query("update comunicacion_reporte set version=version+1,contenido_actual=?,fuente_contacto_actual=?,actualizado_en=? where organizacion_id=? and id_comunicacion_reporte=? and version=? returning "+C,ReporteAvanceRepository::comunicacion,contenido,fuente,ts(ahora),org,c.id(),c.version());
        if(filas.isEmpty())return Optional.empty();var nueva=filas.getFirst();versionar(nueva,contenido,fuente,actor,ahora,motivo,org);return Optional.of(nueva);
    }
    private void versionar(Comunicacion c,String contenido,String fuente,long actor,Instant ahora,String motivo,long org){jdbc.update("insert into comunicacion_reporte_version(organizacion_id,id_comunicacion_reporte,version,contenido,fuente_contacto,id_rol_autor,registrado_en,motivo) values(?,?,?,?,?,?,?,?)",org,c.id(),c.version(),contenido,fuente,actor,ts(ahora),motivo);}
    /** Sólo la comunicación realizada actualiza este reloj; preparar o descargar no lo hace. */
    public List<Object[]> ultimasComunicaciones(long org,Collection<Long> encargos){
        if(encargos.isEmpty())return List.of();String ids=encargos.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",","{","}"));
        return jdbc.query("select r.id_captacion,max((c.contenido_actual::jsonb->>'fechaReal')::date) from comunicacion_reporte c join reporte_propietario r on r.organizacion_id=c.organizacion_id and r.id_reporte_propietario=c.id_reporte_propietario where c.organizacion_id=? and r.id_captacion=any(?::bigint[]) group by r.id_captacion",(r,n)->new Object[]{r.getLong(1),r.getObject(2,LocalDate.class)},org,ids);
    }
    /** Los IDs proceden de oportunidades autorizadas por su puerta canónica, nunca del request. */
    public List<HechoActividad> actividad(long org,Collection<Long> oportunidades){
        if(oportunidades.isEmpty())return List.of();String ids=oportunidades.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",","{","}"));
        return jdbc.query("""
            select 'VISITA',id_visita,version,id_oportunidad,fecha_hecho,estado,registrado_en,null::bigint,fecha_visita,hora_visita
              from visita where organizacion_id=? and id_oportunidad=any(?::bigint[])
            union all
            select 'OFERTA',id_oferta,version,id_oportunidad,fecha,estado,fecha_creacion,id_oferta_anterior,null::date,null::time
              from oferta where organizacion_id=? and id_oportunidad=any(?::bigint[]) order by 1,2
            """,(r,n)->new HechoActividad(r.getString(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getObject(5,LocalDate.class),r.getString(6),instante(r,7),r.getObject(8,Long.class),r.getObject(9,LocalDate.class),r.getObject(10,LocalTime.class)),org,ids,org,ids);
    }
    public List<Oportunidad> oportunidades(long org,long encargo,Collection<Long> roles,boolean broker){
        if(roles.isEmpty())return List.of();String ids=roles.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",","{","}"));
        return jdbc.query("select o.id_oportunidad,o.version from oportunidad_comercial o join captacion c on c.organizacion_id=o.organizacion_id and c.id_captacion=o.id_captacion where o.organizacion_id=? and o.id_captacion=? and "+(broker?"c.id_rol_agente":"o.id_rol_agente")+"=any(?::bigint[]) order by o.id_oportunidad",(r,n)->new Oportunidad(r.getLong(1),r.getLong(2)),org,encargo,ids);
    }
}
