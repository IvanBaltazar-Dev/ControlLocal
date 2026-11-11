package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.Prospeccion;
import com.controllocal.persistence.query.IndicadorProspeccion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    interface FuenteConversion {
        Long getId();Long getResponsable();String getEstado();java.time.Instant getRegistrada();java.time.Instant getActualizada();
        Long getHistorial();java.time.LocalDate getTermino();java.time.Instant getTerminoRegistrado();Long getResponsableTermino();
        Long getEncargo();Long getVersionEncargo();Long getResponsableEncargo();String getTipoOrigen();Long getOrigen();
        String getEstadoEncargo();java.time.LocalDate getDesdeEncargo();
        java.time.Instant getOrigenRegistrado();Long getRevisionHabilitacion();java.time.LocalDate getHabilitada();
        java.time.Instant getHabilitacionRegistrada();Long getProspeccionHabilitada();Long getResponsableHabilitacion();Long getResponsableAnterior();
    }
    @Query(value="""
        select p.id_prospeccion as id,p.id_rol_agente as responsable,p.estado as estado,
          p.fecha_registro as registrada,p.fecha_actualizacion as actualizada,
          fin.id_historial as historial,fin.fecha_efectiva as termino,fin.fecha_evento as "terminoRegistrado",fin.id_rol_responsable as "responsableTermino",
          c.id_captacion as encargo,c.version as "versionEncargo",c.id_rol_agente as "responsableEncargo",
          c.estado as "estadoEncargo",c.fecha_inicio_encargo as "desdeEncargo",
          c.tipo_origen_prospeccion as "tipoOrigen",c.id_prospeccion_origen as origen,c.origen_registrado_en as "origenRegistrado",
          h.id_revision_encargo as "revisionHabilitacion",h.fecha_habilitacion as habilitada,
          h.registrada_en as "habilitacionRegistrada",h.id_prospeccion_origen as "prospeccionHabilitada",
          h.id_rol_responsable_prospeccion as "responsableHabilitacion",anterior.id_rol_responsable as "responsableAnterior"
        from prospeccion p
        left join captacion c on c.organizacion_id=p.organizacion_id and c.fecha_creacion<=:corte
          and (c.id_prospeccion_origen=p.id_prospeccion or c.id_captacion=p.id_captacion)
        left join encargo_primera_habilitacion h on h.organizacion_id=c.organizacion_id and h.id_captacion=c.id_captacion and h.registrada_en<=:corte
        left join lateral(select he.id_historial,he.fecha_efectiva,he.fecha_evento,he.id_rol_responsable
          from historial_estado he where he.organizacion_id=p.organizacion_id and he.entidad_tipo='PROSPECCION'
          and he.id_entidad=p.id_prospeccion and he.estado_nuevo='D' and he.fecha_evento<=:corte order by he.id_historial desc limit 1) fin on true
        left join lateral(select he.id_rol_responsable from historial_estado he
          where he.organizacion_id=p.organizacion_id and he.entidad_tipo='PROSPECCION' and he.id_entidad=p.id_prospeccion
          and he.fecha_evento<=h.registrada_en order by he.id_historial desc limit 1) anterior on true
        where p.organizacion_id=:org and p.id_rol_agente in :roles and p.fecha_registro<=:corte
        order by p.id_prospeccion,c.id_captacion
        """,nativeQuery=true)
    List<FuenteConversion> fuentesConversion(@Param("org")long org,@Param("roles")Collection<Long> roles,@Param("corte")java.time.Instant corte);
    interface ContextoParaActividad {
        Long getResponsable();Long getPropiedad();Long getEncargo();String getEstado();
    }
    @Query(value="""
        select id_rol_agente as responsable,id_propiedad as propiedad,id_captacion as encargo,estado
        from prospeccion where organizacion_id=:org and id_prospeccion=:id
        """,nativeQuery=true)
    Optional<ContextoParaActividad> contextoParaActividad(@Param("org") long org,@Param("id") long id);

    /** AC149: coordenadas autorizadas antes de hidratar la propiedad o los contactos. */
    interface OrigenParaMatching {
        Long getIdPropiedad();
        Long getIdResponsable();
        String getEstado();
    }
    @Query("""
            select p.propiedad.id as idPropiedad, p.agente.id as idResponsable, p.estado as estado
              from Prospeccion p
             where p.organizacionId=:org and p.id=:id and p.agente.id in :roles
            """)
    Optional<OrigenParaMatching> propiedadParaMatching(@Param("org") long org, @Param("id") long id,
                                                       @Param("roles") Collection<Long> roles);

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

    /** Regla de pertenencia (RF-004, v2): el agente prospecto ese local. */
    boolean existsByOrganizacionIdAndPropiedadIdAndAgenteId(Long idOrganizacion, Long idPropiedad, Long idRolAgente);

    /** Correlativo PRO-#### por organizacion: cada corredora numera desde 0001 (V6.3). */
    long countByOrganizacionId(long idOrganizacion);

    /** Ficha completa: trae todo lo que la respuesta congelada necesita en un solo select. */
    @Query("""
            select p from Prospeccion p
              join fetch p.propiedad prop
              left join fetch prop.rolPropietario rp
              left join fetch rp.persona
              join fetch p.agente ag
              join fetch ag.rol agr
              join fetch agr.persona
              left join fetch p.captacion
            where p.organizacionId = :idOrganizacion and p.id = :id
            """)
    Optional<Prospeccion> buscarFicha(@Param("idOrganizacion") long idOrganizacion, @Param("id") long id);

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


    interface ContextoGobierno {long getVersion();long getResponsable();}
    @Query("select p.version as version,p.agente.id as responsable from Prospeccion p where p.organizacionId=:org and p.id=:id and (:global=true or p.agente.id in :roles)")
    Optional<ContextoGobierno> contextoGobierno(@Param("org")long org,@Param("id")long id,@Param("global")boolean global,@Param("roles")Collection<Long> roles);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prospeccion p where p.organizacionId=:org and p.id=:id and (:global=true or p.agente.id in :roles)")
    Optional<Prospeccion> bloquearGobierno(@Param("org")long org,@Param("id")long id,@Param("global")boolean global,@Param("roles")Collection<Long> roles);
}
