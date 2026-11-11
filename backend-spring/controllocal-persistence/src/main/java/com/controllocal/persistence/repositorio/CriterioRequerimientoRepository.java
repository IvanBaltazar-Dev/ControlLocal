package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.CriterioRequerimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Lo que cada requerimiento pide sobre las claves gobernadas.
 *
 * <p>Repositorio propio y no coleccion dentro de {@code RequerimientoCliente},
 * por la misma razon que {@code AtributoPropiedad}: el matcher los lee <b>por
 * lotes</b> —los de todos los requerimientos activos de un tenant, en una
 * consulta— y una coleccion mapeada obligaria a recorrer la entidad padre para
 * llegar a ellos, con una consulta por requerimiento.
 */
public interface CriterioRequerimientoRepository extends JpaRepository<CriterioRequerimiento, Long> {

    /**
     * Los criterios de UN requerimiento, con sus valores de conjunto ya
     * resueltos. El {@code left join fetch} evita la consulta por criterio que
     * costaria leer {@code valoresPedidos()}.
     */
    @Query("""
            select distinct c from CriterioRequerimiento c
              left join fetch c.valores
            where c.organizacionId = :idOrganizacion and c.idRequerimiento = :idRequerimiento
              and exists(select r.id from RequerimientoCliente r where r.id=c.idRequerimiento
                and r.organizacionId=c.organizacionId and r.version=c.versionDemanda)
            order by c.clave asc
            """)
    List<CriterioRequerimiento> deRequerimiento(@Param("idOrganizacion") long idOrganizacion,
                                                @Param("idRequerimiento") long idRequerimiento);

    /**
     * Los de VARIOS requerimientos, en una consulta. Es lo que hace que cruzar
     * la cartera contra la demanda entera del tenant siga costando un numero
     * fijo de consultas y no una por requerimiento.
     */
    @Query("""
            select distinct c from CriterioRequerimiento c
              left join fetch c.valores
            where c.organizacionId = :idOrganizacion
              and c.idRequerimiento in :idsRequerimiento
              and exists(select r.id from RequerimientoCliente r where r.id=c.idRequerimiento
                and r.organizacionId=c.organizacionId and r.version=c.versionDemanda)
            order by c.idRequerimiento asc, c.clave asc
            """)
    List<CriterioRequerimiento> deVarios(@Param("idOrganizacion") long idOrganizacion,
                                         @Param("idsRequerimiento") Collection<Long> idsRequerimiento);

    @Query("""
        select c from CriterioRequerimiento c left join fetch c.valores
        where c.organizacionId=:org and c.idRequerimiento=:id and c.versionDemanda=:version
        order by c.clave
        """)
    List<CriterioRequerimiento> deVersion(@Param("org") long org,@Param("id") long id,
        @Param("version") long version);

}
