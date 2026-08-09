package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.ReportePropietario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Reportes al propietario. De momento solo tiene la consulta que la bandeja
 * necesita: su recurso REST llega con su modulo (D-F7-1).
 */
public interface ReportePropietarioRepository extends JpaRepository<ReportePropietario, Long> {

    /**
     * Lista pelada del cable, con las dos referencias que necesita el DTO ya
     * cargadas. El tenant forma parte del WHERE aunque id_captacion sea global
     * hoy: esa propiedad no se filtra por accidente.
     */
    @Query("""
            select r from ReportePropietario r
              join fetch r.captacion
              join fetch r.agente
            where r.organizacionId = :idOrganizacion
              and r.captacion.id = :idCaptacion
            order by r.fechaReporte desc
            """)
    List<ReportePropietario> listarPorCaptacion(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idCaptacion") long idCaptacion);

    /**
     * Ultimo reporte por captacion, en UNA consulta para todas las captaciones
     * del agente. Es lo que alimenta el disparador 6 (cadencia de 15 dias) sin
     * volver a la BD captacion por captacion.
     *
     * @return filas {@code [idCaptacion, fechaUltimoReporte]}
     */
    @Query("""
            select r.captacion.id, max(r.fechaReporte) from ReportePropietario r
            where r.organizacionId = :idOrganizacion and r.captacion.id in :idsCaptacion
            group by r.captacion.id
            """)
    List<Object[]> ultimoPorCaptaciones(@Param("idOrganizacion") long idOrganizacion,
                                        @Param("idsCaptacion") Collection<Long> idsCaptacion);
}
