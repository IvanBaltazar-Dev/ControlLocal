package com.controllocal.persistence.repositorio;

import com.controllocal.domain.network.VersionParticipacionNetwork;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface VersionParticipacionNetworkRepository extends JpaRepository<VersionParticipacionNetwork,Long> {
    Optional<VersionParticipacionNetwork> findByOrganizacionIdAndId(long organizacion,long id);
    interface MetadataVersion {
        Long getId();UUID getOfertaRef();UUID getReferencia();long getNumero();Long getParticipacion();Long getOrganizacion();Long getEncargo();
        Long getResponsable();Long getBroker();boolean getIndividual();String getPoliticaVersion();String getFinalidad();
        String getCampos();String getHuellaDatos();String getHuellaAutoridad();Instant getDesde();Instant getHasta();
    }
    interface EstadoVersion extends MetadataVersion {String getEstadoParticipacion();long getVersionParticipacion();String getCausasParticipacion();}
    interface Salida extends EstadoVersion {String getDatos();}
    String METADATA="v.id as id,p.referencia as ofertaRef,v.referencia as referencia,v.numero as numero,v.participacion as participacion,v.organizacionId as organizacion,p.encargo as encargo,v.responsable as responsable,v.broker as broker,v.individual as individual,v.politicaVersion as politicaVersion,v.finalidad as finalidad,v.campos as campos,v.huellaDatos as huellaDatos,v.huellaAutoridad as huellaAutoridad,v.desde as desde,v.hasta as hasta";
    @Query("select "+METADATA+" from VersionParticipacionNetwork v join ParticipacionNetwork p on p.id=v.participacion and p.organizacionId=v.organizacionId where v.organizacionId=:org and v.id=:id")
    Optional<MetadataVersion> metadata(@Param("org") long org,@Param("id") long id);
    @Query("select "+METADATA+",p.estado as estadoParticipacion,p.version as versionParticipacion,p.causas as causasParticipacion from VersionParticipacionNetwork v join ParticipacionNetwork p on p.id=v.participacion and p.organizacionId=v.organizacionId where p.versionActual=v.id and p.estado in ('A','S') order by v.id")
    List<EstadoVersion> candidatasActuales();
    @Query("select "+METADATA+",p.estado as estadoParticipacion,p.version as versionParticipacion,p.causas as causasParticipacion from VersionParticipacionNetwork v join ParticipacionNetwork p on p.id=v.participacion and p.organizacionId=v.organizacionId where p.organizacionId=:org and p.encargo=:encargo and p.versionActual=v.id and p.estado in ('A','S') order by p.id")
    List<EstadoVersion> candidatasDelEncargo(@Param("org") long org,@Param("encargo") long encargo);
    /** Dependencias físicas de la raíz o unidades de la composición vigente; sólo recupera metadata. */
    @Query(value="""
            select v.id_version_network as id,p.referencia as ofertaRef,v.referencia as referencia,
                   v.numero as numero,v.id_participacion_network as participacion,v.organizacion_id as organizacion,
                   p.id_captacion as encargo,v.id_responsable as responsable,v.id_broker as broker,
                   v.individual as individual,v.politica_version as politicaVersion,v.finalidad as finalidad,
                   v.campos as campos,v.huella_datos as huellaDatos,v.huella_autoridad as huellaAutoridad,
                   v.desde as desde,v.hasta as hasta,p.estado as estadoParticipacion,
                   p.version as versionParticipacion,p.causas as causasParticipacion
            from participacion_network p
            join version_participacion_network v on v.organizacion_id=p.organizacion_id and v.id_version_network=p.id_version_actual
            join captacion c on c.organizacion_id=p.organizacion_id and c.id_captacion=p.id_captacion
            where p.organizacion_id=:org and p.estado in ('A','S')
              and (c.id_propiedad=:propiedad or exists (
                  select 1 from composicion_encargo ce
                  cross join lateral jsonb_array_elements(cast(ce.unidades as jsonb)) as unidad(valor)
                  where ce.organizacion_id=c.organizacion_id and ce.id_captacion=c.id_captacion
                    and ce.id_composicion_encargo=c.id_composicion_actual
                    and cast(unidad.valor->>'idPropiedad' as bigint)=:propiedad))
            order by p.id_participacion_network
            """,nativeQuery=true)
    List<EstadoVersion> candidatasDePropiedad(@Param("org") long org,@Param("propiedad") long propiedad);
    @Query("select "+METADATA+",p.estado as estadoParticipacion,p.version as versionParticipacion,p.causas as causasParticipacion from VersionParticipacionNetwork v join ParticipacionNetwork p on p.id=v.participacion and p.organizacionId=v.organizacionId where p.referencia in :refs and p.versionActual=v.id and p.estado in ('A','S') order by v.id")
    List<EstadoVersion> candidatasPorReferencia(@Param("refs") Collection<UUID> ofertas);
    /** Sólo las versiones ya autorizadas por metadata, aún actuales; no sustituye V1 por V2 entre consultas. */
    @Query("select "+METADATA+",v.datos as datos,p.estado as estadoParticipacion,p.version as versionParticipacion,p.causas as causasParticipacion from VersionParticipacionNetwork v join ParticipacionNetwork p on p.id=v.participacion and p.organizacionId=v.organizacionId where v.referencia in :refs and p.versionActual=v.id and p.estado in ('A','S') order by v.id")
    List<Salida> salidasActuales(@Param("refs") Collection<UUID> versiones);
    /** Revisión escalar final: una baja/revocación no se oculta por entidades gestionadas calientes. */
    @Query("select v.referencia from VersionParticipacionNetwork v join ParticipacionNetwork p on p.id=v.participacion and p.organizacionId=v.organizacionId where v.referencia in :refs and p.versionActual=v.id and p.estado='A'")
    Set<UUID> referenciasActivas(@Param("refs") Collection<UUID> versiones);
}
