package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.Captacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

/** Metadata de referencias aprobadas: nunca ficha, atributos, titular, documento o contacto. */
public interface ContextoNetworkRepository extends JpaRepository<Captacion,Long> {
    interface ContextoActual {
        long getEncargo();long getOrganizacion();long getVersion();Long getResponsable();Long getBroker();
        String getModalidad();String getEstadoOrganizacion();
    }
    @Query(value="""
        select c.id_captacion as encargo,c.organizacion_id as organizacion,c.version as version,c.id_rol_agente as responsable,
               s.id_rol_broker as broker,o.modalidad as modalidad,o.estado as estadoOrganizacion
          from captacion c join organizacion o on o.id_organizacion=c.organizacion_id
          left join supervision_agente s on s.organizacion_id=c.organizacion_id
               and s.id_rol_agente=c.id_rol_agente and s.fecha_fin is null
         where c.id_captacion in (:ids)
        """,nativeQuery=true)
    List<ContextoActual> deReferenciasAprobadas(@Param("ids") Collection<Long> ids);
}
