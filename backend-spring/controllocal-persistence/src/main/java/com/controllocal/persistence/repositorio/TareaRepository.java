package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.Tarea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Tareas de la bandeja del agente.
 *
 * <p>Aqui NO hay paginacion en SQL y no es un descuido: la bandeja se
 * <b>reconcilia</b> en cada lectura y luego se ordena y corta <b>en memoria</b>
 * por dos campos que no estan en la tabla —{@code prioridad} pesada y
 * {@code diasSinAccion}, que se derivan de la entidad de origen—. Paginar en
 * SQL daria un orden distinto al del cable. El conjunto esta acotado por
 * agente, asi que es pequeno por construccion.
 */
public interface TareaRepository extends JpaRepository<Tarea, Long> {

    /**
     * TODAS las del agente, en cualquier estado: el reconcile necesita ver las
     * canceladas para no volver a crearlas (§5.2, trampa 1).
     */
    @Query("""
            select t from Tarea t
            where t.organizacionId = :idOrganizacion and t.agente.id = :idRolAgente
            order by t.fechaProgramada
            """)
    List<Tarea> porAgente(@Param("idOrganizacion") long idOrganizacion,
                          @Param("idRolAgente") long idRolAgente);

    /**
     * Abiertas de una entidad concreta. Es lo que consume el <b>efecto 7</b> de
     * la cascada de F4: al cerrar el alquiler se dan por hechas las tareas de
     * la oportunidad, la solicitud, la captacion y el local.
     */
    @Query("""
            select t from Tarea t
            where t.organizacionId = :idOrganizacion
              and t.entidadTipo = :entidadTipo
              and t.entidadId = :entidadId
              and t.estado in ('P', 'E')
            """)
    List<Tarea> abiertasDeEntidad(@Param("idOrganizacion") long idOrganizacion,
                                  @Param("entidadTipo") String entidadTipo,
                                  @Param("entidadId") long entidadId);

    /**
     * Las tareas abiertas que produjo un contrato concreto. Es lo que permite
     * cerrar EXACTAMENTE la revision de ese contrato al resolverla, en vez de
     * buscar "alguna tarea abierta del inmueble" y arriesgarse a cerrar la de
     * un contrato anterior del mismo local.
     */
    @Query("""
            select t from Tarea t
            where t.organizacionId = :idOrganizacion
              and t.idContratoOrigen = :idContrato
              and t.estado in ('P', 'E')
            """)
    List<Tarea> abiertasDeContratoOrigen(@Param("idOrganizacion") long idOrganizacion,
                                         @Param("idContrato") long idContrato);

    /** Para cancelar: acotada al tenant; que sea del agente lo comprueba el service. */
    @Query("""
            select t from Tarea t
              join fetch t.agente
            where t.organizacionId = :idOrganizacion and t.id = :id
            """)
    Optional<Tarea> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                @Param("id") long id);
}
