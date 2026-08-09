package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.EvaluacionSolicitud;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Evaluaciones del broker. El recurso {@code /evaluaciones} es solo de
 * BROKER/ADMIN: el broker ve las SUYAS (las que el firmo) y el admin todas.
 *
 * <p>Cierra la deuda de la v1: alli {@code listar} y {@code obtener} traian
 * {@code listarTodos()} y cortaban en memoria; aqui la paginacion y el filtro
 * bajan al WHERE (MEJ-05 / RC-003) con la misma respuesta.
 */
public interface EvaluacionSolicitudRepository extends JpaRepository<EvaluacionSolicitud, Long> {

    String DESDE = """
            from EvaluacionSolicitud e
              join e.broker br
            where e.organizacionId = :idOrganizacion
              and (:sinScope = true or br.id = :idRolBroker)
            """;

    @Query(value = "select e " + DESDE + " order by e.id desc",
            countQuery = "select count(e) " + DESDE)
    Page<EvaluacionSolicitud> buscar(@Param("idOrganizacion") long idOrganizacion,
                                     @Param("sinScope") boolean sinScope,
                                     @Param("idRolBroker") long idRolBroker,
                                     Pageable pageable);

    String FICHA = """
            select e from EvaluacionSolicitud e
              join fetch e.broker br
              join fetch br.rol brRol
              join fetch brRol.persona
              join fetch e.solicitud
            """;

    @Query(FICHA + """
             where e.organizacionId = :idOrganizacion and e.id = :id
               and (:sinScope = true or br.id = :idRolBroker)
            """)
    Optional<EvaluacionSolicitud> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                              @Param("id") long id,
                                              @Param("sinScope") boolean sinScope,
                                              @Param("idRolBroker") long idRolBroker);

    /** Historial de la solicitud: lo ve tambien el agente dueno (no lleva scope de broker). */
    @Query(FICHA + """
             where e.organizacionId = :idOrganizacion and e.solicitud.id = :idSolicitud
             order by e.id desc
            """)
    List<EvaluacionSolicitud> porSolicitud(@Param("idOrganizacion") long idOrganizacion,
                                           @Param("idSolicitud") long idSolicitud);

    /**
     * "Solo una evaluacion FINAL por solicitud". La BD ya lo impide con un
     * unico parcial; esto permite responder el mensaje del cable en vez de un
     * 409 de integridad.
     */
    @Query("""
            select count(e) > 0 from EvaluacionSolicitud e
            where e.organizacionId = :idOrganizacion and e.solicitud.id = :idSolicitud
              and e.tipoEvaluacion = 'F'
            """)
    boolean existeFinalDe(@Param("idOrganizacion") long idOrganizacion,
                          @Param("idSolicitud") long idSolicitud);
}
