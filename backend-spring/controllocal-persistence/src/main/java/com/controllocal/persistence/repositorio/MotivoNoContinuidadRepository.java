package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.MotivoNoContinuidad;
import com.controllocal.persistence.query.MotivoPorCaptacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface MotivoNoContinuidadRepository extends JpaRepository<MotivoNoContinuidad, Long> {

    /** Razones registradas de una oportunidad caida, la mas reciente primero. */
    List<MotivoNoContinuidad> findByOportunidadIdOrderByIdDesc(long idOportunidad);

    /**
     * Breakdown del reporte al propietario. El desempate por codigo solo hace
     * determinista lo que la v1 deja sin especificar; la regla observable es
     * frecuencia descendente.
     *
     * @return filas {@code [codigoRazon, cantidad]}
     */
    @Query("""
            select m.razonPrincipal, count(m) from MotivoNoContinuidad m
              join m.oportunidad o
            where m.organizacionId = :idOrganizacion
              and o.captacion.id = :idCaptacion
              and m.fechaRegistro >= :desde
              and m.fechaRegistro < :hastaExclusiva
            group by m.razonPrincipal
            order by count(m) desc, m.razonPrincipal asc
            """)
    List<Object[]> contarParaReporte(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idCaptacion") long idCaptacion,
            @Param("desde") OffsetDateTime desde,
            @Param("hastaExclusiva") OffsetDateTime hastaExclusiva);

    /**
     * Motivo principal de cada captacion del avance comercial (RF-017). La v1
     * traia TODOS los motivos del sistema y los contaba en un HashMap por
     * captacion; aqui el GROUP BY lo hace la base y el desempate por codigo
     * vuelve determinista lo que alli dependia del orden del mapa.
     */
    @Query("""
            select o.captacion.id as idCaptacion, m.razonPrincipal as razon,
                   count(m) as total
            from MotivoNoContinuidad m
              join m.oportunidad o
            where m.organizacionId = :idOrganizacion
              and (:sinScope = true or o.agente.id in :roles)
              and m.razonPrincipal is not null
            group by o.captacion.id, m.razonPrincipal
            order by o.captacion.id asc, count(m) desc, m.razonPrincipal asc
            """)
    List<MotivoPorCaptacion> principalPorCaptacion(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("sinScope") boolean sinScope,
            @Param("roles") Collection<Long> roles);
}
