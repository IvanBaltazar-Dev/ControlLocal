package com.controllocal.persistence.repositorio;

import com.controllocal.domain.seguridad.EventoSeguridad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Auditoria de accesos y privilegios. <b>APPEND-ONLY</b>: este repositorio
 * expone escritura de alta y lectura, y ninguna operacion de borrado o
 * actualizacion. Que {@code JpaRepository} las herede no las hace legitimas —
 * en produccion el privilegio se retira tambien en la base.
 */
public interface EventoSeguridadRepository extends JpaRepository<EventoSeguridad, Long> {

    /** Timeline de una cuenta, del mas reciente al mas antiguo. */
    List<EventoSeguridad> findByOrganizacionIdAndIdPersonaOrderByIdDesc(long organizacionId, long idPersona);

    /** Barrido por tipo: "todos los bloqueos de ayer". */
    @Query("""
            select e from EventoSeguridad e
             where e.organizacionId = :idOrganizacion
               and e.tipo = :tipo
             order by e.id desc
            """)
    List<EventoSeguridad> porTipo(@Param("idOrganizacion") long idOrganizacion,
                                  @Param("tipo") String tipo);

    /**
     * Aviso de gobierno paginado, con los <b>nombres</b> del actor y del
     * afectado ya resueltos.
     *
     * <p>Los dos {@code left join} son sobre entidades <b>sin relacion
     * declarada</b>: {@code evento_seguridad} guarda ids sueltos a proposito
     * —un login fallido contra un usuario inexistente tiene que poder
     * registrarse igual, y una FK lo impediria—. Se resuelven aqui, en una
     * consulta, en vez de una lectura por fila desde el service.
     *
     * <p>{@code left}, no {@code inner}: si la persona ya no existe el hecho
     * sigue importando, y un {@code inner join} lo haria desaparecer del
     * tablero justo en el caso mas sospechoso.
     */
    @Query("""
            select e,
                   pa.nombresORazonSocial,
                   po.nombresORazonSocial
              from EventoSeguridad e
              left join com.controllocal.domain.persona.Persona pa
                     on pa.id = e.idPersona
              left join com.controllocal.domain.persona.Persona po
                     on po.id = e.idObjetivo
             where e.organizacionId = :idOrganizacion
               and e.tipo in :tipos
             order by e.id desc
            """)
    Page<Object[]> avisosDeGobierno(@Param("idOrganizacion") long idOrganizacion,
                                    @Param("tipos") Collection<String> tipos,
                                    Pageable pageable);
}
