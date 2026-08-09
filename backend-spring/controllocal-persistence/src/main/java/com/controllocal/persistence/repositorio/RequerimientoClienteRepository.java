package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.RequerimientoCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Perfiles de busqueda. Las consultas traen los distritos en el mismo select
 * porque tanto la respuesta del cable como el matching de cartera los
 * necesitan siempre (evita el N+1 de la coleccion LAZY).
 */
public interface RequerimientoClienteRepository extends JpaRepository<RequerimientoCliente, Long> {

    @Query("""
            select distinct r from RequerimientoCliente r
              left join fetch r.distritos
            where r.organizacionId = :idOrganizacion and r.id = :id
            """)
    Optional<RequerimientoCliente> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                               @Param("id") long id);

    @Query("""
            select distinct r from RequerimientoCliente r
              left join fetch r.distritos
            where r.organizacionId = :idOrganizacion and r.cliente.id = :idRolCliente
            order by r.id desc
            """)
    List<RequerimientoCliente> listarPorCliente(@Param("idOrganizacion") long idOrganizacion,
                                                @Param("idRolCliente") long idRolCliente);

    /**
     * Base del matching en el sentido propiedad→clientes: solo los ACTIVO
     * cuentan como demanda vigente, y el cliente viene con su persona porque
     * la coincidencia muestra su nombre.
     */
    @Query("""
            select distinct r from RequerimientoCliente r
              join fetch r.cliente cli
              join fetch cli.rol cliRol
              join fetch cliRol.persona
              left join fetch r.distritos
            where r.organizacionId = :idOrganizacion and r.estado = 'A'
            """)
    List<RequerimientoCliente> listarActivos(@Param("idOrganizacion") long idOrganizacion);

    @Query("""
            select distinct r from RequerimientoCliente r
              left join fetch r.distritos
            where r.organizacionId = :idOrganizacion
              and r.estado = 'A' and r.cliente.id = :idRolCliente
            """)
    List<RequerimientoCliente> listarActivosPorCliente(@Param("idOrganizacion") long idOrganizacion,
                                                       @Param("idRolCliente") long idRolCliente);
}
