package com.controllocal.persistence.repositorio;

import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.persistence.query.ResumenClientes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Clientes interesados del tenant. Ojo con el alcance (contrato F3 §2): el
 * cliente es CATALOGO COMPARTIDO — admin y agente ven todos los de su
 * organizacion; solo el broker queda acotado, y su conjunto se deriva de las
 * oportunidades de su equipo, por lo que llega como lista de ids.
 */
public interface DetalleClienteRepository extends JpaRepository<DetalleCliente, Long> {

    String FICHA = """
            select c from DetalleCliente c
              join fetch c.rol r
              join fetch r.persona
            """;

    @Query(FICHA + " where c.organizacionId = :idOrganizacion and c.id = :id")
    Optional<DetalleCliente> buscarFicha(@Param("idOrganizacion") long idOrganizacion, @Param("id") long id);

    @Query(value = FICHA + " where c.organizacionId = :idOrganizacion order by c.id desc",
            countQuery = "select count(c) from DetalleCliente c where c.organizacionId = :idOrganizacion")
    Page<DetalleCliente> pagina(@Param("idOrganizacion") long idOrganizacion, Pageable pageable);

    /** Pagina del BROKER: solo los clientes que su equipo trabaja. */
    @Query(value = FICHA + """
             where c.organizacionId = :idOrganizacion and c.id in :ids
             order by c.id desc
            """,
            countQuery = """
            select count(c) from DetalleCliente c
             where c.organizacionId = :idOrganizacion and c.id in :ids
            """)
    Page<DetalleCliente> paginaPorIds(@Param("idOrganizacion") long idOrganizacion,
                                      @Param("ids") Collection<Long> ids,
                                      Pageable pageable);

    long countByOrganizacionId(long idOrganizacion);

    // ------------------------------------------------------------------
    // Bandeja de clientes: filtros aditivos + KPI, resueltos en SQL.
    //
    // El texto busca en nombre/razon social, documento y rubro, que viven en
    // DOS tablas (persona y detalle_cliente). Escrito como un OR seria el
    // predicado que cruza tablas que ningun indice puede servir, asi que va por
    // CONJUNTO DE CANDIDATOS: una rama por tabla, unidas con UNION
    // (contrato-listados-paginados.md §5). Los demas filtros y el alcance del
    // BROKER viajan en las DOS ramas, para que el conjunto quede cerrado antes
    // de unirse y el conteo no pueda discrepar de la pagina.
    // ------------------------------------------------------------------

    /** Filtros comunes a las dos ramas. Se repiten porque cada rama es autonoma. */
    String FILTROS_BANDEJA = """
              and (cast(:tipoPersona as varchar) is null or per.tipo_persona = cast(:tipoPersona as varchar))
              and (cast(:estado as varchar) is null or per.estado = cast(:estado as varchar))
              and (cast(:rubro as varchar) is null or dc.rubro_comercial = cast(:rubro as varchar))
              and (:sinScope = true or dc.id_persona_rol in (:ids))
            """;

    String RAMAS_BANDEJA = """
            select dc.id_persona_rol as id
              from detalle_cliente dc
              join persona_rol pr on pr.id_persona_rol = dc.id_persona_rol
              join persona per on per.id_persona = pr.id_persona
             where dc.organizacion_id = :idOrganizacion
            """ + FILTROS_BANDEJA + """
               and (cast(:texto as varchar) is null
                    or lower(per.nombres_o_razon_social) like lower(concat('%', cast(:texto as varchar), '%'))
                    or lower(per.numero_documento) like lower(concat('%', cast(:texto as varchar), '%')))
            union
            select dc.id_persona_rol as id
              from detalle_cliente dc
              join persona_rol pr on pr.id_persona_rol = dc.id_persona_rol
              join persona per on per.id_persona = pr.id_persona
             where dc.organizacion_id = :idOrganizacion
            """ + FILTROS_BANDEJA + """
               and cast(:texto as varchar) is not null
               and lower(dc.rubro_comercial) like lower(concat('%', cast(:texto as varchar), '%'))
            """;

    /**
     * Ids de la pagina, ordenados y recortados en la base. El orden es
     * {@code id desc}, el del cable para clientes (no ascendente como locales).
     */
    @Query(value = "select c.id from (" + RAMAS_BANDEJA
            + ") c order by c.id desc limit :limite offset :desplazamiento", nativeQuery = true)
    List<Long> idsBandeja(@Param("idOrganizacion") long idOrganizacion,
                          @Param("texto") String texto,
                          @Param("tipoPersona") String tipoPersona,
                          @Param("estado") String estado,
                          @Param("rubro") String rubro,
                          @Param("sinScope") boolean sinScope,
                          @Param("ids") Collection<Long> ids,
                          @Param("limite") int limite,
                          @Param("desplazamiento") int desplazamiento);

    /** Total del MISMO conjunto que pagina {@link #idsBandeja}. */
    @Query(value = "select count(*) from (" + RAMAS_BANDEJA + ") c", nativeQuery = true)
    long contarBandeja(@Param("idOrganizacion") long idOrganizacion,
                       @Param("texto") String texto,
                       @Param("tipoPersona") String tipoPersona,
                       @Param("estado") String estado,
                       @Param("rubro") String rubro,
                       @Param("sinScope") boolean sinScope,
                       @Param("ids") Collection<Long> ids);

    /**
     * KPI de la bandeja en UNA consulta sobre el mismo conjunto: total, activos
     * y los dos consentimientos. Se llama con {@code estado = null} porque el
     * resumen cuenta los cubos, no filtra por uno.
     *
     * <p>Ojo con los dos consentimientos: <b>no viven en la misma tabla</b>. El
     * de contacto es del rol ({@code detalle_cliente}) y el de uso de dato es de
     * la persona, porque vale para todos sus roles.</p>
     */
    @Query(value = """
            select count(*) as total,
                   count(*) filter (where per.estado = 'A') as activos,
                   count(*) filter (where dc.consentimiento_contacto) as contactoAutorizado,
                   count(*) filter (where per.consentimiento_uso_dato) as usoDatoAutorizado
              from (""" + RAMAS_BANDEJA + """
                   ) c
              join detalle_cliente dc on dc.id_persona_rol = c.id
              join persona_rol pr on pr.id_persona_rol = dc.id_persona_rol
              join persona per on per.id_persona = pr.id_persona
            """, nativeQuery = true)
    ResumenClientes resumenBandeja(@Param("idOrganizacion") long idOrganizacion,
                                   @Param("texto") String texto,
                                   @Param("tipoPersona") String tipoPersona,
                                   @Param("estado") String estado,
                                   @Param("rubro") String rubro,
                                   @Param("sinScope") boolean sinScope,
                                   @Param("ids") Collection<Long> ids);

    /**
     * Rubros presentes en el alcance, para que el selector sea data-driven sin
     * una llamada extra ni descargar la cartera (el Blazor los derivaba de las
     * filas ya cargadas, que es justo lo que deja de existir).
     */
    @Query(value = """
            select distinct dc.rubro_comercial
              from detalle_cliente dc
             where dc.organizacion_id = :idOrganizacion
               and dc.rubro_comercial is not null and btrim(dc.rubro_comercial) <> ''
               and (:sinScope = true or dc.id_persona_rol in (:ids))
             order by 1
            """, nativeQuery = true)
    List<String> rubrosDisponibles(@Param("idOrganizacion") long idOrganizacion,
                                   @Param("sinScope") boolean sinScope,
                                   @Param("ids") Collection<Long> ids);

    /** La ficha completa de los ids de la pagina, ya resuelta. */
    @Query(FICHA + " where c.organizacionId = :idOrganizacion and c.id in :ids order by c.id desc")
    List<DetalleCliente> fichasPorIds(@Param("idOrganizacion") long idOrganizacion,
                                      @Param("ids") Collection<Long> ids);
}
