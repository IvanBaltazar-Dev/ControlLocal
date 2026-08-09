package com.controllocal.persistence.repositorio;

import com.controllocal.domain.persona.DetalleBroker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** La PK es el id del rol BROKER (persona_rol). */
public interface DetalleBrokerRepository extends JpaRepository<DetalleBroker, Long> {

    String FICHA = """
            select b from DetalleBroker b
              join fetch b.rol r
              join fetch r.persona
            """;

    @Query(value = FICHA + """
            where b.organizacionId = :idOrganizacion
            order by b.id desc
            """,
            countQuery = """
                    select count(b) from DetalleBroker b
                    where b.organizacionId = :idOrganizacion
                    """)
    Page<DetalleBroker> pagina(@Param("idOrganizacion") long idOrganizacion, Pageable pageable);

    @Query(FICHA + """
            where b.organizacionId = :idOrganizacion
              and b.id = :id
            """)
    Optional<DetalleBroker> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                        @Param("id") long id);

    @Query(FICHA + """
            where b.organizacionId = :idOrganizacion
            order by b.id desc
            """)
    List<DetalleBroker> listarFichas(@Param("idOrganizacion") long idOrganizacion);

    long countByOrganizacionId(long idOrganizacion);

    boolean existsByOrganizacionIdAndEsAdministradorTrue(long idOrganizacion);
}
