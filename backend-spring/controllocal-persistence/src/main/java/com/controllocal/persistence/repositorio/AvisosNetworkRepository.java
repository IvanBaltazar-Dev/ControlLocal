package com.controllocal.persistence.repositorio;

import com.controllocal.domain.auditoria.EventoDominio;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

/** Metadata del outbox en alcance comercial actual; no selecciona proyecciones V1. */
public interface AvisosNetworkRepository extends Repository<EventoDominio,Long> {
    interface Fila {
        Long getIdEvento();String getTipo();Instant getOcurridoEn();Long getIdEncargo();String getCodigoEncargo();
        String getParticipacion();String getSolicitud();String getAccion();String getEstadoSolicitud();
        String getEstadoParticipacion();Long getVersionProyeccion();String getMotivo();String getCausas();
    }
    String BASE="""
            from evento_dominio e
            join participacion_network p on p.organizacion_id=e.organizacion_id and p.id_participacion_network=e.entidad_id
            join captacion c on c.organizacion_id=p.organizacion_id and c.id_captacion=p.id_captacion
            left join solicitud_participacion_network s on s.organizacion_id=p.organizacion_id
                 and s.id_participacion_network=p.id_participacion_network
                 and cast(s.referencia as text)=cast(e.carga_util as jsonb)->>'solicitud'
            left join decision_participacion_network d on d.organizacion_id=p.organizacion_id
                 and d.id_participacion_network=p.id_participacion_network
                 and cast(d.id_decision_network as text)=cast(e.carga_util as jsonb)->>'decision'
            """;
    String ALCANCE="""
            where e.organizacion_id=:org and e.entidad_tipo='PARTICIPACION_NETWORK'
              and c.id_rol_agente in (:roles)
              and (
                (e.tipo='NETWORK_SOLICITADA' and (
                   (cast(e.carga_util as jsonb)->>'estadoSolicitud'='PENDIENTE'
                    and cast(e.carga_util as jsonb)->>'broker'=cast(:actorRol as text))
                   or (cast(e.carga_util as jsonb)->>'estadoSolicitud'='APROBADA'
                       and cast(e.carga_util as jsonb)->>'responsable'=cast(:actorRol as text))))
                or (e.tipo in ('NETWORK_DECIDIDA','NETWORK_DESACTIVADA','NETWORK_SUSPENDIDA','NETWORK_FRESCURA_REVALIDADA')
                    and (cast(e.carga_util as jsonb)->>'broker'=cast(:actorRol as text)
                         or cast(e.carga_util as jsonb)->>'responsable'=cast(:actorRol as text)))
              )
            """;
    String COLUMNAS="""
            select e.id_evento as "idEvento",e.tipo as tipo,e.ocurrido_en as "ocurridoEn",
                   c.id_captacion as "idEncargo",c.codigo_captacion as "codigoEncargo",
                   cast(p.referencia as text) as participacion,cast(e.carga_util as jsonb)->>'solicitud' as solicitud,
                   cast(e.carga_util as jsonb)->>'accion' as accion,
                   cast(e.carga_util as jsonb)->>'estadoSolicitud' as "estadoSolicitud",
                   cast(e.carga_util as jsonb)->>'estadoParticipacion' as "estadoParticipacion",
                   cast(coalesce(cast(e.carga_util as jsonb)->>'versionProyeccion',cast(e.carga_util as jsonb)->>'version') as bigint) as "versionProyeccion",
                   coalesce(d.motivo,s.motivo) as motivo,
                   cast(cast(e.carga_util as jsonb)->'causas' as text) as causas
            """;
    @Query(value=COLUMNAS+BASE+ALCANCE+" order by e.ocurrido_en desc,e.id_evento desc limit :limite offset :desplazamiento",nativeQuery=true)
    List<Fila> pagina(@Param("org") long org,@Param("actorRol") long actorRol,@Param("roles") Collection<Long> roles,
                      @Param("limite") int limite,@Param("desplazamiento") long desplazamiento);
    @Query(value=COLUMNAS+BASE+ALCANCE+" and e.id_evento in (:ids) order by e.ocurrido_en desc,e.id_evento desc",nativeQuery=true)
    List<Fila> revalidar(@Param("org") long org,@Param("actorRol") long actorRol,@Param("roles") Collection<Long> roles,@Param("ids") Collection<Long> ids);
    @Query(value="select count(*) "+BASE+ALCANCE,nativeQuery=true)
    long contar(@Param("org") long org,@Param("actorRol") long actorRol,@Param("roles") Collection<Long> roles);
}
