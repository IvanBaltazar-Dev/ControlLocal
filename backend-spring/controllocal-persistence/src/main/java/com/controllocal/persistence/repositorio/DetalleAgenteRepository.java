package com.controllocal.persistence.repositorio;

import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.query.ConteoPorEstado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** La PK es el id del rol AGENTE (persona_rol). */
public interface DetalleAgenteRepository extends JpaRepository<DetalleAgente, Long> {

    String FICHA = """
            select a from DetalleAgente a
              join fetch a.rol r
              join fetch r.persona
            """;

    @Query(value = FICHA + """
            where a.organizacionId = :idOrganizacion
            order by a.id desc
            """,
            countQuery = """
                    select count(a) from DetalleAgente a
                    where a.organizacionId = :idOrganizacion
                    """)
    Page<DetalleAgente> paginaTodos(@Param("idOrganizacion") long idOrganizacion,
                                    Pageable pageable);

    @Query(value = FICHA + """
            where a.organizacionId = :idOrganizacion
              and exists (
                  select 1 from SupervisionAgente s
                  where s.organizacionId = :idOrganizacion
                    and s.idRolBroker = :idRolBroker
                    and s.idRolAgente = a.id
                    and s.fechaFin is null)
            order by a.id desc
            """,
            countQuery = """
                    select count(a) from DetalleAgente a
                    where a.organizacionId = :idOrganizacion
                      and exists (
                          select 1 from SupervisionAgente s
                          where s.organizacionId = :idOrganizacion
                            and s.idRolBroker = :idRolBroker
                            and s.idRolAgente = a.id
                            and s.fechaFin is null)
                    """)
    Page<DetalleAgente> paginaPorBroker(@Param("idOrganizacion") long idOrganizacion,
                                        @Param("idRolBroker") long idRolBroker,
                                        Pageable pageable);

    // ------------------------------------------------------------------
    // Busqueda del catalogo: filtros ADITIVOS, resueltos en la BASE
    // ------------------------------------------------------------------
    //
    // El estado administrativo NO vive en el agente: vive en su credencial, que
    // cuelga del rol USUARIO_INTERNO de la misma persona. Por eso el join va
    // por PERSONA y no por rol, y es `left join`: un agente sin credencial
    // seguiria saliendo en el listado (hoy no puede ocurrir, porque el alta los
    // crea juntos, pero un left join no cuesta nada y un inner escondería la
    // fila en vez de mostrarla incompleta).
    String DESDE_FILTRABLE = """
            from DetalleAgente a
              join a.rol r
              join r.persona p
              left join CredencialUsuario c
                     on c.rol.persona = p and c.rol.vigenciaHasta is null
            """;

    String CONDICION_FILTRABLE = """
            where a.organizacionId = :idOrganizacion
              and (:sinScope = true
                   or exists (select 1 from SupervisionAgente s
                              where s.organizacionId = :idOrganizacion
                                and s.idRolBroker = :idRolBroker
                                and s.idRolAgente = a.id
                                and s.fechaFin is null))
              and (:texto is null
                   or lower(p.nombresORazonSocial) like lower(concat('%', cast(:texto as string), '%'))
                   or lower(p.numeroDocumento) like lower(concat('%', cast(:texto as string), '%'))
                   or lower(a.codigoAgente) like lower(concat('%', cast(:texto as string), '%'))
                   or lower(a.zonaAsignada) like lower(concat('%', cast(:texto as string), '%')))
              and (:estado is null or c.estadoAdministrativo = :estado)
              and (:estadoOperativo is null or a.estadoOperativo = :estadoOperativo)
              and (:zona is null or a.zonaAsignada = :zona)
            """;

    /**
     * Listado con filtros aditivos. Con los cuatro en {@code null} y
     * {@code sinScope} según el rol devuelve exactamente lo mismo que los
     * antiguos {@code paginaTodos}/{@code paginaPorBroker}: el cable congelado
     * de {@code GET /agentes} no cambia al omitirlos.
     */
    @Query(value = "select a " + DESDE_FILTRABLE + CONDICION_FILTRABLE + " order by a.id desc",
            countQuery = "select count(a) " + DESDE_FILTRABLE + CONDICION_FILTRABLE)
    Page<DetalleAgente> buscar(@Param("idOrganizacion") long idOrganizacion,
                               @Param("sinScope") boolean sinScope,
                               @Param("idRolBroker") long idRolBroker,
                               @Param("texto") String texto,
                               @Param("estado") String estado,
                               @Param("estadoOperativo") String estadoOperativo,
                               @Param("zona") String zona,
                               Pageable pageable);

    /**
     * Cubos del resumen sobre el MISMO conjunto que pagina la lista. El cubo de
     * estado administrativo se cuenta aparte del operativo porque son dos
     * máquinas distintas: un agente activo puede estar de vacaciones.
     */
    @Query("select count(a) " + DESDE_FILTRABLE + CONDICION_FILTRABLE)
    long contarFiltrados(@Param("idOrganizacion") long idOrganizacion,
                         @Param("sinScope") boolean sinScope,
                         @Param("idRolBroker") long idRolBroker,
                         @Param("texto") String texto,
                         @Param("estado") String estado,
                         @Param("estadoOperativo") String estadoOperativo,
                         @Param("zona") String zona);

    @Query("select coalesce(c.estadoAdministrativo, 'A') as estado, count(a) as total "
            + DESDE_FILTRABLE + CONDICION_FILTRABLE
            + " group by coalesce(c.estadoAdministrativo, 'A')")
    List<ConteoPorEstado> contarPorEstadoAdministrativo(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("sinScope") boolean sinScope,
            @Param("idRolBroker") long idRolBroker,
            @Param("texto") String texto,
            @Param("estado") String estado,
            @Param("estadoOperativo") String estadoOperativo,
            @Param("zona") String zona);

    @Query("select a.estadoOperativo as estado, count(a) as total "
            + DESDE_FILTRABLE + CONDICION_FILTRABLE + " group by a.estadoOperativo")
    List<ConteoPorEstado> contarPorEstadoOperativo(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("sinScope") boolean sinScope,
            @Param("idRolBroker") long idRolBroker,
            @Param("texto") String texto,
            @Param("estado") String estado,
            @Param("estadoOperativo") String estadoOperativo,
            @Param("zona") String zona);

    /**
     * Zonas del alcance para que el selector sea data-driven. Recorre el
     * alcance completo a propósito —ofrece las opciones disponibles— así que
     * NO acepta el filtro de zona, que es justo el que acota.
     */
    @Query("""
            select distinct a.zonaAsignada from DetalleAgente a
            where a.organizacionId = :idOrganizacion
              and (:sinScope = true
                   or exists (select 1 from SupervisionAgente s
                              where s.organizacionId = :idOrganizacion
                                and s.idRolBroker = :idRolBroker
                                and s.idRolAgente = a.id
                                and s.fechaFin is null))
              and a.zonaAsignada is not null and a.zonaAsignada <> ''
            order by a.zonaAsignada
            """)
    List<String> zonasDisponibles(@Param("idOrganizacion") long idOrganizacion,
                                  @Param("sinScope") boolean sinScope,
                                  @Param("idRolBroker") long idRolBroker);

    @Query(FICHA + """
            where a.organizacionId = :idOrganizacion
              and a.id = :id
            """)
    Optional<DetalleAgente> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                        @Param("id") long id);

    @Query(FICHA + """
            where a.organizacionId = :idOrganizacion
              and a.id in :ids
            order by a.id desc
            """)
    List<DetalleAgente> buscarFichas(@Param("idOrganizacion") long idOrganizacion,
                                     @Param("ids") Collection<Long> ids);

    @Query(FICHA + """
            where a.organizacionId = :idOrganizacion
            order by a.id desc
            """)
    List<DetalleAgente> listarFichas(@Param("idOrganizacion") long idOrganizacion);

    long countByOrganizacionId(long idOrganizacion);
}
