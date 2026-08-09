package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.DocumentoSolicitud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Documentos de una solicitud. El acceso se decide sobre la SOLICITUD (el
 * llamador ya la cargo con scope), asi que aqui basta el tenant.
 */
public interface DocumentoSolicitudRepository extends JpaRepository<DocumentoSolicitud, Long> {

    @Query("""
            select d from DocumentoSolicitud d
              join fetch d.tipoDocumento
            where d.organizacionId = :idOrganizacion and d.solicitud.id = :idSolicitud
            order by d.id
            """)
    List<DocumentoSolicitud> porSolicitud(@Param("idOrganizacion") long idOrganizacion,
                                          @Param("idSolicitud") long idSolicitud);

    /**
     * Una sola lectura para toda una pagina de solicitudes: es lo que alimenta
     * el contador "X/Y" del checklist sin caer en N+1 (MEJ-05).
     */
    @Query("""
            select d from DocumentoSolicitud d
              join fetch d.tipoDocumento
            where d.organizacionId = :idOrganizacion and d.solicitud.id in :idsSolicitud
            """)
    List<DocumentoSolicitud> porSolicitudes(@Param("idOrganizacion") long idOrganizacion,
                                            @Param("idsSolicitud") Collection<Long> idsSolicitud);

    @Query("""
            select d from DocumentoSolicitud d
              join fetch d.tipoDocumento
              join fetch d.solicitud
            where d.organizacionId = :idOrganizacion and d.id = :id
            """)
    Optional<DocumentoSolicitud> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                             @Param("id") long id);

    /** Los que el broker deja conformes en bloque: cargados y aun sin revisar. */
    @Query("""
            select d from DocumentoSolicitud d
            where d.organizacionId = :idOrganizacion and d.solicitud.id = :idSolicitud
              and d.estado = 'R' and (d.resultadoRevision is null or d.resultadoRevision = 'P')
            """)
    List<DocumentoSolicitud> pendientesDeRevision(@Param("idOrganizacion") long idOrganizacion,
                                                  @Param("idSolicitud") long idSolicitud);
}
