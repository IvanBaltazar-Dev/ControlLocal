package com.controllocal.persistence.repositorio;
import com.controllocal.domain.comercial.ComisionEvaluacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ComisionEvaluacionRepository extends JpaRepository<ComisionEvaluacion,Long> {
    Optional<ComisionEvaluacion> findByOrganizacionIdAndComisionAndId(long org,long comision,long id);
    Optional<ComisionEvaluacion> findByOrganizacionIdAndComisionAndHuella(long org,long comision,String huella);
    List<ComisionEvaluacion> findByOrganizacionIdAndComisionOrderByIdAsc(long org,long comision);
    List<ComisionEvaluacion> findByOrganizacionIdAndIdIn(long org,Collection<Long> ids);
    /** Cada revisión consumida conserva pertenencia estructural al mismo resultado. */
    @Modifying
    @Query(value="""
        insert into comision_evaluacion_hito(organizacion_id,id_comision_evaluacion,id_contexto_economico,id_hito,id_revision_hito)
        values(:org,:evaluacion,:contexto,:hito,:revision)
        on conflict(organizacion_id,id_comision_evaluacion,id_hito,id_revision_hito) do nothing
        """,nativeQuery=true)
    int enlazarHito(@Param("org")long org,@Param("evaluacion")long evaluacion,@Param("contexto")long contexto,
                   @Param("hito")long hito,@Param("revision")long revision);
}
