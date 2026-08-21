package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.Alerta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Alertas de la campana, con el tenant por delante y el scope del actor en el
 * WHERE (V6 / RC-001).
 *
 * <p>Como la alerta cuelga SIEMPRE de un agente (§1 del contrato), el alcance
 * es el mismo {@code Alcance} que usa el resto del sistema: el AGENTE pasa su
 * rol, el BROKER los de su equipo y el ADMIN va sin filtro. No hace falta una
 * consulta distinta por rol como en la v1.
 *
 * <p>Cierra la deuda de la v1: alli {@code listar} traia la lista completa y
 * cortaba con {@code subList}; aqui la paginacion baja al LIMIT/OFFSET
 * (MEJ-05 / RC-003, igual que en {@code /evaluaciones}) con la misma respuesta.
 */
public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    /** La campana SOLO muestra activas: las tres ramas de rol de la v1 filtran por ACTIVA. */
    String DESDE = """
            from Alerta a
              join a.agente ag
            where a.organizacionId = :idOrganizacion
              and a.estado = 'A'
              and (:sinScope = true or ag.id in :roles)
            """;

    /**
     * Una alerta VISIBLE para el actor. Se llama asi y no "conAcceso" a
     * proposito: el cable responde <b>404</b> cuando la alerta existe pero no
     * es visible, no 403 (D-F6-3), asi que quien la use debe traducir el vacio
     * a "no encontrada".
     */
    @Query("""
            select a from Alerta a
              join fetch a.agente ag
              join fetch ag.rol agRol
              join fetch agRol.persona
            where a.organizacionId = :idOrganizacion
              and a.id = :id
              and a.estado = 'A'
              and (:sinScope = true or ag.id in :roles)
            """)
    Optional<Alerta> buscarVisible(@Param("idOrganizacion") long idOrganizacion,
                                   @Param("id") long id,
                                   @Param("sinScope") boolean sinScope,
                                   @Param("roles") Collection<Long> roles);

    /**
     * "¿Ya hay una activa de este tipo para esta entidad?" — es lo que evita
     * que el barrido de recontacto duplique la alerta en cada pasada.
     */
    @Query("""
            select count(a) > 0 from Alerta a
            where a.organizacionId = :idOrganizacion
              and a.entidadTipo = :entidadTipo
              and a.entidadId = :entidadId
              and a.tipo = :tipo
              and a.estado = 'A'
            """)
    boolean existeActivaDe(@Param("idOrganizacion") long idOrganizacion,
                           @Param("entidadTipo") String entidadTipo,
                           @Param("entidadId") long entidadId,
                           @Param("tipo") String tipo);

    /**
     * <b>Avisos de recontacto que ya no aplican.</b> La otra mitad de la
     * reconciliacion: {@link #existeActivaDe} impide crear dos, y esto cierra
     * los que sobrevivieron a su motivo.
     *
     * <p>La condicion es <b>la misma</b> que la de
     * {@code ProspeccionRepository.recontactables} —fecha de recontacto vencida
     * y prospeccion en proceso—, negada. Se escribe aqui como {@code not
     * exists} y no restando conjuntos en memoria por una razon concreta: el
     * barrido que crea esta acotado a 500, y una diferencia de conjuntos
     * cerraria en falso todos los avisos cuya prospeccion quedo fuera de esa
     * pagina.
     *
     * <p>Sin filtro de alcance a proposito: el barrido es del tenant entero,
     * igual que el de creacion. Cerrar un aviso que ya no aplica no depende de
     * quien abra la campana.
     */
    @Query("""
            select a from Alerta a
            where a.organizacionId = :idOrganizacion
              and a.estado = 'A'
              and a.tipo = :tipo
              and a.entidadTipo = :entidadTipo
              and not exists (
                select 1 from Prospeccion p
                where p.id = a.entidadId
                  and p.organizacionId = :idOrganizacion
                  and p.fechaRecontacto is not null
                  and p.fechaRecontacto <= :limite
                  and p.estado not in ('T', 'D')
              )
            """)
    List<Alerta> recontactosQueYaNoAplican(@Param("idOrganizacion") long idOrganizacion,
                                           @Param("entidadTipo") String entidadTipo,
                                           @Param("tipo") String tipo,
                                           @Param("limite") LocalDate limite);

    /**
     * La pagina de la campana, con la persona resuelta en el mismo select: la
     * respuesta lleva {@code agenteNombre} y sin el fetch join cada fila
     * dispararia su lazy.
     */
    @Query(value = """
            select a from Alerta a
              join fetch a.agente ag
              join fetch ag.rol agRol
              join fetch agRol.persona
            where a.organizacionId = :idOrganizacion
              and a.estado = 'A'
              and (:sinScope = true or ag.id in :roles)
            order by a.fechaGeneracion desc, a.id desc
            """,
            countQuery = "select count(a) " + DESDE)
    Page<Alerta> buscarConAgente(@Param("idOrganizacion") long idOrganizacion,
                                 @Param("sinScope") boolean sinScope,
                                 @Param("roles") Collection<Long> roles,
                                 Pageable pageable);
}
