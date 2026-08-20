package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.Prospeccion;
import com.controllocal.persistence.query.CandidatoTarea;
import com.controllocal.persistence.query.ExpedienteDeLaProspeccion;
import com.controllocal.persistence.query.IndicadorProspeccion;
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
 * Consultas de prospeccion con el SCOPE del actor resuelto en el WHERE
 * (RC-001/RC-003): {@code sinScope=true} solo para el ADMIN; en el resto,
 * {@code rolesAgente} son los roles del propio agente o de los agentes
 * supervisados por el broker. Filtros y orden replican el cable v1.
 *
 * <p>{@code idOrganizacion} filtra ANTES que el rol (V6): el ADMIN es global
 * dentro de su corredora, no entre corredoras.
 */
public interface ProspeccionRepository extends JpaRepository<Prospeccion, Long> {

    /**
     * Dos filtros del cable v1 que hay que respetar aqui y son fáciles de
     * pasar por alto porque no son columnas:
     *
     * <ul>
     *   <li><b>{@code estado = 'GESTION'} no es un estado</b>, es el cubo de
     *       las ACTIVAS: {@code P, C, R, E, S}. La v1 lo resuelve en
     *       {@code ProspeccionesRest.coincideEstado}. Comparandolo como
     *       columna no coincide con nada y la lista sale <b>vacia en
     *       silencio</b>, que es justo lo que hacia esta consulta antes.</li>
     *   <li><b>{@code agentesDelBroker}</b> viene de
     *       {@code idBrokerSupervisor}: el ADMIN filtra por el equipo de un
     *       broker. Es distinto del scope del actor —que ya esta en
     *       {@code rolesAgente}— y se aplica ADEMAS, nunca en su lugar.</li>
     * </ul>
     */
    String DESDE = """
            from Prospeccion p
              join p.propiedad prop
              join p.agente ag
              left join p.captacion c
            where p.organizacionId = :idOrganizacion
              and (:sinScope = true or ag.id in :rolesAgente)
              and (:estado is null
                   or (:estado = 'GESTION' and p.estado in ('P', 'C', 'R', 'E', 'S'))
                   or (:estado <> 'GESTION' and p.estado = :estado))
              and (:distrito is null or lower(prop.distrito) like lower(concat('%', cast(:distrito as string), '%')))
              and (:idCaptacion is null or c.id = :idCaptacion)
              and (:idLocal is null or prop.id = :idLocal)
              and (:idAgente is null or ag.id = :idAgente)
              and (:filtrarPorBroker = false or ag.id in :agentesDelBroker)
              and (:q is null
                   or lower(p.codigoProspeccion) like lower(concat('%', cast(:q as string), '%'))
                   or lower(prop.codigo) like lower(concat('%', cast(:q as string), '%'))
                   or lower(prop.direccion) like lower(concat('%', cast(:q as string), '%'))
                   or lower(prop.distrito) like lower(concat('%', cast(:q as string), '%')))
            """;

    /** Orden por defecto del cable: el creado mas reciente primero. */
    @Query(value = "select p " + DESDE + " order by p.id desc",
            countQuery = "select count(p) " + DESDE)
    Page<Prospeccion> buscar(@Param("idOrganizacion") long idOrganizacion,
                             @Param("sinScope") boolean sinScope,
                             @Param("rolesAgente") Collection<Long> rolesAgente,
                             @Param("estado") String estado,
                             @Param("distrito") String distrito,
                             @Param("idCaptacion") Long idCaptacion,
                             @Param("idLocal") Long idLocal,
                             @Param("idAgente") Long idAgente,
                             @Param("filtrarPorBroker") boolean filtrarPorBroker,
                             @Param("agentesDelBroker") Collection<Long> agentesDelBroker,
                             @Param("q") String q,
                             Pageable pageable);

    /**
     * orden=ultimo_contacto del cable v1: la fecha de interaccion mas
     * reciente (contacto/reunion/propuesta/recontacto) primero; sin ninguna
     * fecha, al final (el centinela 1900 replica el nullsLast v1).
     */
    @Query(value = "select p " + DESDE + """
            order by greatest(
                coalesce(p.fechaContacto,  {d '1900-01-01'}),
                coalesce(p.fechaReunion,   {d '1900-01-01'}),
                coalesce(p.fechaPropuesta, {d '1900-01-01'}),
                coalesce(p.fechaRecontacto,{d '1900-01-01'})) desc, p.id desc
            """,
            countQuery = "select count(p) " + DESDE)
    Page<Prospeccion> buscarPorUltimoContacto(@Param("idOrganizacion") long idOrganizacion,
                                              @Param("sinScope") boolean sinScope,
                                              @Param("rolesAgente") Collection<Long> rolesAgente,
                                              @Param("estado") String estado,
                                              @Param("distrito") String distrito,
                                              @Param("idCaptacion") Long idCaptacion,
                                              @Param("idLocal") Long idLocal,
                                              @Param("idAgente") Long idAgente,
                                              @Param("filtrarPorBroker") boolean filtrarPorBroker,
                             @Param("agentesDelBroker") Collection<Long> agentesDelBroker,
                                              @Param("q") String q,
                                              Pageable pageable);

    String DESDE_RECONTACTO = """
            from Prospeccion p
              join p.agente ag
            where p.organizacionId = :idOrganizacion
              and (:sinScope = true or ag.id in :rolesAgente)
              and p.fechaRecontacto is not null
              and p.fechaRecontacto <= :limite
              and p.estado not in ('T', 'D')
            """;

    /** Recontacto vencido: la ultima accion tiene ya N dias o mas (dia 8 = alerta). */
    @Query(value = "select p " + DESDE_RECONTACTO + " order by p.fechaRecontacto asc, p.id desc",
            countQuery = "select count(p) " + DESDE_RECONTACTO)
    Page<Prospeccion> recontactables(@Param("idOrganizacion") long idOrganizacion,
                                     @Param("sinScope") boolean sinScope,
                                     @Param("rolesAgente") Collection<Long> rolesAgente,
                                     @Param("limite") LocalDate limite,
                                     Pageable pageable);

    /** Regla de pertenencia (RF-004, v2): el agente prospecto ese local. */
    boolean existsByOrganizacionIdAndPropiedadIdAndAgenteId(Long idOrganizacion, Long idPropiedad, Long idRolAgente);

    /** Correlativo PRO-#### por organizacion: cada corredora numera desde 0001 (V6.3). */
    long countByOrganizacionId(long idOrganizacion);

    /** Ficha completa: trae todo lo que la respuesta congelada necesita en un solo select. */
    @Query("""
            select p from Prospeccion p
              join fetch p.propiedad prop
              left join fetch prop.detalleLocal
              left join fetch prop.rolPropietario rp
              left join fetch rp.persona
              join fetch p.agente ag
              join fetch ag.rol agr
              join fetch agr.persona
              left join fetch p.captacion
            where p.organizacionId = :idOrganizacion and p.id = :id
            """)
    Optional<Prospeccion> buscarFicha(@Param("idOrganizacion") long idOrganizacion, @Param("id") long id);

    /**
     * Disparador 1 de la bandeja (F7): recontacto vencido del agente. Mismo
     * criterio que {@link #recontactables} —en proceso y con la fecha de
     * recontacto ya pasada— pero acotado a UN agente y devolviendo solo lo que
     * la tarea necesita. {@code fechaPlazo} es la fecha de recontacto: es ella,
     * y no la de creacion de la tarea, la que da los "dias sin accion".
     */
    @Query("""
            select p.id as entidadId, p.codigoProspeccion as entidadCodigo,
                   p.fechaRecontacto as fechaPlazo, cast(null as string) as marca
            from Prospeccion p
            where p.organizacionId = :idOrganizacion
              and p.agente.id = :idRolAgente
              and p.estado not in ('T', 'D')
              and p.fechaRecontacto is not null
              and p.fechaRecontacto <= :limite
            """)
    List<CandidatoTarea> paraRecontactar(@Param("idOrganizacion") long idOrganizacion,
                                         @Param("idRolAgente") long idRolAgente,
                                         @Param("limite") LocalDate limite);

    /** Base transversal E3: toda la historia del propietario dentro del tenant. */
    @Query("""
            select p from Prospeccion p
            where p.organizacionId = :idOrganizacion
              and p.propiedad.rolPropietario.id = :idPropietario
            order by p.id
            """)
    List<Prospeccion> listarFichaPorPropietario(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idPropietario") long idPropietario);

    /** Base de prospecciones del indicador operativo E4 (recontactos y conversion). */
    @Query("""
            select p.id as id, p.agente.id as idAgente, p.estado as estado,
                   p.fechaRegistro as fechaRegistro, p.fechaRecontacto as fechaRecontacto
            from Prospeccion p
            where p.organizacionId = :idOrganizacion
              and (:sinScope = true or p.agente.id in :rolesAgente)
            """)
    List<IndicadorProspeccion> indicadores(@Param("idOrganizacion") long idOrganizacion,
                                           @Param("sinScope") boolean sinScope,
                                           @Param("rolesAgente") Collection<Long> rolesAgente);

    /** Fuente de prospecciones del seguimiento comercial (alcance por agente). */
    @Query("""
            select p from Prospeccion p
              join fetch p.propiedad prop
              left join fetch prop.rolPropietario propRol
              left join fetch propRol.persona
              join fetch p.agente ag
              join fetch ag.rol agRol
              join fetch agRol.persona
              left join fetch p.captacion
            where p.organizacionId = :idOrganizacion
              and (:sinScope = true or ag.id in :rolesAgente)
            """)
    List<Prospeccion> listarSeguimiento(@Param("idOrganizacion") long idOrganizacion,
                                        @Param("sinScope") boolean sinScope,
                                        @Param("rolesAgente") Collection<Long> rolesAgente);

    /**
     * El expediente de un lote de prospecciones, para la capa de resolucion del
     * Inicio (D-E2-1 §10.3).
     *
     * <p><b>Por lote y no por asunto.</b> Una pagina del Radar son cinco
     * asuntos; pedirlo dentro del bucle serian cinco consultas, que es el N+1
     * que RC-003 quito del listado y que vuelve cada vez que alguien anade una
     * capa de interpretacion.
     *
     * <p>El propietario y la direccion se leen a traves de la propiedad que la
     * prospeccion persigue. Los {@code left join} no son defensivos por gusto:
     * una prospeccion puede estar apuntando a un inmueble cuyo propietario
     * todavia no tiene ficha completa, y perder la fila entera por eso dejaria
     * el expediente vacio justo en el caso que mas falta hace resolver.
     */
    @Query(value = """
            select p.id_prospeccion  as idProspeccion,
                   p.estado          as estado,
                   p.fecha_registro::date as fechaRegistro,
                   p.fecha_contacto  as fechaContacto,
                   p.fecha_reunion   as fechaReunion,
                   p.fecha_propuesta as fechaPropuesta,
                   p.fecha_recontacto as fechaRecontacto,
                   per.nombres_o_razon_social as propietario,
                   pr.direccion      as direccion,
                   pr.distrito       as distrito
              from prospeccion p
              left join propiedad pr   on pr.id_propiedad   = p.id_propiedad
              left join persona_rol rp on rp.id_persona_rol = pr.id_rol_propietario
              left join persona per    on per.id_persona    = rp.id_persona
             where p.organizacion_id = :idOrganizacion
               and p.id_prospeccion in (:ids)
            """, nativeQuery = true)
    List<ExpedienteDeLaProspeccion> expedientesDe(@Param("idOrganizacion") long idOrganizacion,
                                                  @Param("ids") Collection<Long> ids);
}
