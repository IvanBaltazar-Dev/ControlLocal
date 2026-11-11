package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.UnidadRelacionada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDate;

/** Acceso tenant-scoped a los vinculos fisicos entre propiedades. */
public interface UnidadRelacionadaRepository extends JpaRepository<UnidadRelacionada, Long> {

    @Query("""
            select u from UnidadRelacionada u
            where u.organizacionId = :organizacionId
              and (u.idPropiedadPrincipal = :idPropiedad
                   or u.idPropiedadRelacionada = :idPropiedad)
            order by u.vigenteDesde asc, u.id asc
            """)
    List<UnidadRelacionada> deLaPropiedad(@Param("organizacionId") long organizacionId,
                                          @Param("idPropiedad") long idPropiedad);

    @Query("""
            select u from UnidadRelacionada u
            where u.organizacionId=:org and u.tipoRelacion='O'
              and (u.idPropiedadPrincipal=:propiedad or u.idPropiedadRelacionada=:propiedad)
            order by u.registradoEn asc,u.id asc
            """)
    List<UnidadRelacionada> origenesDeLaPropiedad(@Param("org") long organizacion,
                                                @Param("propiedad") long propiedad);

    /** La función SQL comparte el mismo candado con la defensa de integridad del grafo. */
    @Query(value="select bloquear_grafo_origen(:org)",nativeQuery=true)
    Integer bloquearGrafoOrigen(@Param("org") long organizacion);

    @Query(value="""
            select exists(select 1 from unidad_relacionada u
             where u.organizacion_id=:org and u.tipo_relacion='O'
               and u.id_propiedad_principal=:matriz and u.id_propiedad_relacionada=:unidad
               and daterange(u.vigente_desde,u.vigente_hasta,'[]')
                   && daterange(cast(:desde as date),cast(:hasta as date),'[]'))
            """,nativeQuery=true)
    boolean existeDuplicadoOrigen(@Param("org") long organizacion,@Param("matriz") long matriz,
                                   @Param("unidad") long unidad,@Param("desde") LocalDate desde,
                                   @Param("hasta") LocalDate hasta);

    /** Todos los O conservados: cerrar una vigencia no habilita un ciclo posterior. */
    @Query(value="""
            with recursive camino(id) as (
              select u.id_propiedad_relacionada from unidad_relacionada u
               where u.organizacion_id=:org and u.tipo_relacion='O' and u.id_propiedad_principal=:desde
              union
              select u.id_propiedad_relacionada from unidad_relacionada u
               join camino c on c.id=u.id_propiedad_principal
               where u.organizacion_id=:org and u.tipo_relacion='O'
            ) select exists(select 1 from camino where id=:hasta)
            """,nativeQuery=true)
    boolean existeCaminoOrigen(@Param("org") long organizacion,@Param("desde") long desde,
                                @Param("hasta") long hasta);
}
