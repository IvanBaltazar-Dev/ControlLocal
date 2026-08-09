package com.controllocal.persistence.repositorio;

import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.ConteoPorEstado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PersonaRolRepository extends JpaRepository<PersonaRol, Long> {

    @Query("""
            select r from PersonaRol r
            where r.persona.id = :idPersona
              and r.tipoRol = :tipoRol
              and r.vigenciaHasta is null
            """)
    Optional<PersonaRol> buscarVigente(@Param("idPersona") long idPersona, @Param("tipoRol") TipoRol tipoRol);

    /**
     * Nombre del titular de un rol operativo, acotado al tenant. Lo usa la
     * constancia de autorizacion (D-27) para poner un <b>nombre</b> donde
     * {@code autorizacion_tratamiento_evento.registrada_por} guarda un id.
     * <p>
     * Devuelve la proyeccion y no la entidad a proposito: {@code persona} es
     * LAZY, y traerla entera para leer un campo obliga a cuidar la sesion en
     * cada llamador.
     */
    @Query("""
            select r.persona.nombresORazonSocial from PersonaRol r
            where r.id = :idRol
              and r.organizacionId = :idOrganizacion
            """)
    Optional<String> nombreDelTitular(@Param("idRol") long idRol,
                                      @Param("idOrganizacion") long idOrganizacion);

    /**
     * Propietarios del tenant. El PROPIETARIO es el unico de los cinco roles sin
     * tabla de detalle: todo lo que el cable expone —documento, nombre, contacto,
     * estado— vive en {@code persona}, y {@code cantidadLocales} se deriva. Por eso
     * su "repositorio" son estas consultas sobre {@code persona_rol} y no una
     * entidad propia.
     *
     * <p>Orden {@code id desc} = ultimo creado primero, igual que el
     * {@code ORDER BY pr.id_propietario DESC} de la v1.
     */
    @Query("""
            select r from PersonaRol r
              join fetch r.persona
            where r.organizacionId = :idOrganizacion
              and r.tipoRol = com.controllocal.domain.persona.enums.TipoRol.PROPIETARIO
              and r.vigenciaHasta is null
            order by r.id desc
            """)
    Page<PersonaRol> paginaPropietarios(@Param("idOrganizacion") long idOrganizacion, Pageable pageable);

    @Query("""
            select count(r) from PersonaRol r
            where r.organizacionId = :idOrganizacion
              and r.tipoRol = com.controllocal.domain.persona.enums.TipoRol.PROPIETARIO
              and r.vigenciaHasta is null
            """)
    long contarPropietarios(@Param("idOrganizacion") long idOrganizacion);

    /** La pagina del BROKER: sus ids ya vienen acotados, aqui solo se hidratan. */
    @Query("""
            select r from PersonaRol r
              join fetch r.persona
            where r.organizacionId = :idOrganizacion
              and r.tipoRol = com.controllocal.domain.persona.enums.TipoRol.PROPIETARIO
              and r.vigenciaHasta is null
              and r.id in :ids
            order by r.id desc
            """)
    List<PersonaRol> propietariosPorIds(@Param("idOrganizacion") long idOrganizacion,
                                        @Param("ids") Collection<Long> ids);

    @Query("""
            select r from PersonaRol r
              join fetch r.persona
            where r.organizacionId = :idOrganizacion
              and r.tipoRol = com.controllocal.domain.persona.enums.TipoRol.PROPIETARIO
              and r.id = :id
            """)
    Optional<PersonaRol> buscarPropietario(@Param("idOrganizacion") long idOrganizacion,
                                           @Param("id") long id);

    // ------------------------------------------------------------------
    // Busqueda del catalogo de propietarios: filtros ADITIVOS en la BASE
    // ------------------------------------------------------------------
    //
    // `:ids` es el conjunto que el BROKER alcanza (sus propiedades via captacion
    // o prospeccion); para ADMIN y AGENTE llega `:sinScope = true` y no se mira.
    // Nunca se pasa una lista vacia: el service manda el centinela, misma
    // convencion que `Alcances.paramRoles()`.
    //
    // Esto ademas baja a SQL la paginacion del BROKER, que antes se resolvia
    // cortando en memoria la lista completa de ids.
    String PROPIETARIOS_FILTRABLES = """
            from PersonaRol r
              join r.persona p
            where r.organizacionId = :idOrganizacion
              and r.tipoRol = com.controllocal.domain.persona.enums.TipoRol.PROPIETARIO
              and r.vigenciaHasta is null
              and (:sinScope = true or r.id in :ids)
              and (:texto is null
                   or lower(p.nombresORazonSocial) like lower(concat('%', cast(:texto as string), '%'))
                   or lower(p.numeroDocumento) like lower(concat('%', cast(:texto as string), '%'))
                   or lower(p.correo) like lower(concat('%', cast(:texto as string), '%')))
              and (:estado is null or p.estado = :estado)
            """;

    @Query(value = "select r " + PROPIETARIOS_FILTRABLES + " order by r.id desc",
            countQuery = "select count(r) " + PROPIETARIOS_FILTRABLES)
    Page<PersonaRol> buscarPropietarios(@Param("idOrganizacion") long idOrganizacion,
                                        @Param("sinScope") boolean sinScope,
                                        @Param("ids") Collection<Long> ids,
                                        @Param("texto") String texto,
                                        @Param("estado") String estado,
                                        Pageable pageable);

    @Query("select p.estado as estado, count(r) as total " + PROPIETARIOS_FILTRABLES
            + " group by p.estado")
    List<ConteoPorEstado> contarPropietariosPorEstado(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("sinScope") boolean sinScope,
            @Param("ids") Collection<Long> ids,
            @Param("texto") String texto,
            @Param("estado") String estado);
}
