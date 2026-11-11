package com.controllocal.persistence.repositorio;
import com.controllocal.domain.comercial.RevisionEncargo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface RevisionEncargoRepository extends JpaRepository<RevisionEncargo,Long> {
    Optional<RevisionEncargo> findByOrganizacionIdAndId(long org,long id);
    List<RevisionEncargo> findAllByOrganizacionIdAndIdEncargoOrderByIdAsc(long org,long encargo);
    @Query(value="""
        select r.* from revision_encargo r
         where r.organizacion_id=:org and r.id_captacion=:encargo
           and (r.id_revision_encargo=:propuesta
                or r.snapshot::jsonb->'adenda'->>'propuesta'=cast(:propuesta as text))
         order by r.id_revision_encargo desc limit 1
        """,nativeQuery=true)
    Optional<RevisionEncargo> ultimaDeAdenda(@Param("org")long org,@Param("encargo")long encargo,@Param("propuesta")long propuesta);
}
