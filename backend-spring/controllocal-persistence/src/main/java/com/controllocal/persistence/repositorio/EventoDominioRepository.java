package com.controllocal.persistence.repositorio;

import com.controllocal.domain.auditoria.EventoDominio;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * El outbox (D-E4-1, V52).
 *
 * <p>Este repositorio ESCRIBE poco y LEE en dos modos muy distintos: el
 * consumidor pide lo pendiente en orden de llegada, y la ficha pide lo que le
 * paso a una entidad. Los dos tienen su indice en V52 — el de pendientes es
 * parcial, asi que no crece con la tabla.
 */
public interface EventoDominioRepository extends JpaRepository<EventoDominio, Long> {

    /**
     * Lo que falta por proyectar, en el orden en que ocurrio. El {@code Limit}
     * es del llamante a proposito: un consumidor que se traiga la tabla entera
     * en el primer arranque es un problema esperando.
     */
    @Query("""
            select e from EventoDominio e
            where e.proyectadoEn is null
            order by e.id asc
            """)
    List<EventoDominio> pendientesDeProyectar(Limit limite);

    /** Lo que le ha pasado a una entidad, para su ficha o su auditoria. */
    @Query("""
            select e from EventoDominio e
            where e.entidadTipo = :entidadTipo and e.entidadId = :entidadId
            order by e.ocurridoEn asc, e.id asc
            """)
    List<EventoDominio> historiaDe(@Param("entidadTipo") String entidadTipo,
                                   @Param("entidadId") long entidadId);

    /**
     * Lo que entro por un canal en una organizacion. Es media respuesta a
     * "quien decidio esto"; la otra mitad la da {@link #porAgente}.
     */
    @Query("""
            select e from EventoDominio e
            where e.organizacionId = :idOrganizacion
              and e.canal = :canal
              and e.ocurridoEn >= :desde
            order by e.ocurridoEn desc
            """)
    List<EventoDominio> porCanal(@Param("idOrganizacion") long idOrganizacion,
                                 @Param("canal") String canal,
                                 @Param("desde") OffsetDateTime desde);

    /**
     * Lo que hizo un agente automatico, sea cual sea el canal por el que entro.
     *
     * <p>Separada de {@link #porCanal} porque son dos preguntas distintas: un
     * mismo agente puede entrar por WhatsApp hoy y por otro canal manana, y
     * "que ha hecho el asistente" no debe depender de por donde hablaba.
     */
    @Query("""
            select e from EventoDominio e
            where e.organizacionId = :idOrganizacion
              and e.agente = :agente
              and e.ocurridoEn >= :desde
            order by e.ocurridoEn desc
            """)
    List<EventoDominio> porAgente(@Param("idOrganizacion") long idOrganizacion,
                                  @Param("agente") String agente,
                                  @Param("desde") OffsetDateTime desde);

    /**
     * Marca en bloque lo ya proyectado. Va como {@code @Modifying} y no
     * entidad a entidad porque el consumidor confirma un lote entero: cargar
     * mil eventos para tocarles una columna es trabajo que no hace falta.
     */
    @Modifying
    @Query("""
            update EventoDominio e set e.proyectadoEn = :momento
            where e.id in :ids and e.proyectadoEn is null
            """)
    int marcarProyectados(@Param("ids") Collection<Long> ids,
                          @Param("momento") OffsetDateTime momento);

    long countByProyectadoEnIsNull();
}
