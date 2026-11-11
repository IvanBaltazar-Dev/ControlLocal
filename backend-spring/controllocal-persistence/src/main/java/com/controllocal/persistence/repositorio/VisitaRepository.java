package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.Visita;
import com.controllocal.persistence.query.CandidatoTarea;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.query.IndicadorVisita;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Visitas con el scope del actor en el WHERE. Igual que en oportunidades, el
 * BROKER alcanza **por la captacion** de la oportunidad visitada, no por el
 * agente de la visita (contrato F3 §5).
 */
public interface VisitaRepository extends JpaRepository<Visita, Long> {

    interface VersionActividad {
        long getVersion(); java.time.Instant getRegistradoEn(); String getOperacion(); String getMotivo(); String getFicha();
    }
    @org.springframework.data.jpa.repository.Modifying
    @Query(value="INSERT INTO visita_historia(organizacion_id,id_visita,id_oportunidad,version,registrado_en,operacion,motivo,ficha) VALUES (:org,:id,:op,:version,:fecha,:operacion,:motivo,CAST(:ficha AS jsonb))",nativeQuery=true)
    void historizar(@Param("org") long org,@Param("id") long id,@Param("op") long op,@Param("version") long version,
        @Param("fecha") java.time.Instant fecha,@Param("operacion") String operacion,@Param("motivo") String motivo,@Param("ficha") String ficha);
    @Query(value="SELECT ficha::text FROM visita_historia WHERE organizacion_id=:org AND id_visita=:id AND version=:version",nativeQuery=true)
    Optional<String> fichaVersion(@Param("org") long org,@Param("id") long id,@Param("version") long version);
    @Query(value="SELECT version,registrado_en AS \"registradoEn\",operacion,motivo,ficha::text AS ficha FROM visita_historia WHERE organizacion_id=:org AND id_visita=:id ORDER BY version",nativeQuery=true)
    List<VersionActividad> historia(@Param("org") long org,@Param("id") long id);
    @Query(value="SELECT ficha::text FROM (SELECT DISTINCT ON(id_visita) id_visita,version,ficha FROM visita_historia WHERE organizacion_id=:org AND id_oportunidad=:op AND registrado_en<=:corte ORDER BY id_visita,version DESC) h ORDER BY id_visita",nativeQuery=true)
    List<String> fichasAlCorte(@Param("org") long org,@Param("op") long op,@Param("corte") java.time.Instant corte);
    @Query("select v.oportunidad.id from Visita v where v.organizacionId=:org and v.id=:id")
    Optional<Long> oportunidadDe(@Param("org") long org,@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Visita v where v.organizacionId=:org and v.id=:id")
    Optional<Visita> bloquear(@Param("org") long org,@Param("id") long id);
    @Query("select v from Visita v where v.organizacionId=:org and v.oportunidad.id=:op order by v.fechaVisita,v.horaVisita,v.id")
    List<Visita> deOportunidad(@Param("org") long org,@Param("op") long op);
    @Query(value="select v "+DESDE+" and v.fechaVisita between :desde and :hasta order by v.fechaVisita,v.horaVisita,v.id",
            countQuery="select count(v) "+DESDE+" and v.fechaVisita between :desde and :hasta")
    Page<Visita> agenda(@Param("idOrganizacion") long org,@Param("sinScope") boolean global,
            @Param("porAgente") boolean agente,@Param("roles") Collection<Long> roles,
            @Param("idOportunidad") Long oportunidad,@Param("estado") String estado,@Param("distrito") String distrito,
            @Param("desde") LocalDate desde,@Param("hasta") LocalDate hasta,Pageable pagina);

    String DESDE = """
            from Visita v
              join v.oportunidad o
              join o.captacion cap
              join cap.propiedad prop
              join cap.agente capAg
              join v.agente ag
            where v.organizacionId = :idOrganizacion
              and (:sinScope = true
                   or (:porAgente = true  and ag.id in :roles)
                   or (:porAgente = false and capAg.id in :roles))
              and (:idOportunidad is null or o.id = :idOportunidad)
              and (:estado is null or v.estado = :estado)
              and (:distrito is null or lower(prop.distrito) like lower(concat('%', cast(:distrito as string), '%')))
            """;

    /**
     * Listado SIN texto libre. El texto tiene su propio camino por conjunto de
     * candidatos ({@link #idsPorTexto}): cruza dos tablas y la §5 del contrato
     * de listados prohibe resolverlo con un OR.
     */
    @Query(value = "select v " + DESDE + " order by v.fechaVisita desc, v.id desc",
            countQuery = "select count(v) " + DESDE)
    Page<Visita> buscar(@Param("idOrganizacion") long idOrganizacion,
                        @Param("sinScope") boolean sinScope,
                        @Param("porAgente") boolean porAgente,
                        @Param("roles") Collection<Long> roles,
                        @Param("idOportunidad") Long idOportunidad,
                        @Param("estado") String estado,
                        @Param("distrito") String distrito,
                        Pageable pageable);

    /**
     * KPI de la bandeja por estado, con un solo {@code group by} sobre el MISMO
     * conjunto que pagina {@link #buscar} y con el {@code estado} en nulo: son
     * los cubos que cuenta, no un filtro.
     *
     * <p>Extension aditiva del v2, por la misma razon que en oportunidades: los
     * cinco contadores de la bandeja no se pueden derivar de una pagina.
     */
    @Query("select v.estado as estado, count(v) as total " + DESDE + " group by v.estado")
    List<ConteoPorEstado> contarPorEstado(@Param("idOrganizacion") long idOrganizacion,
                                          @Param("sinScope") boolean sinScope,
                                          @Param("porAgente") boolean porAgente,
                                          @Param("roles") Collection<Long> roles,
                                          @Param("idOportunidad") Long idOportunidad,
                                          @Param("estado") String estado,
                                          @Param("distrito") String distrito);

    // ------------------------------------------------------------------
    // Busqueda por CONJUNTO DE CANDIDATOS (§5 del contrato de listados).
    //
    // El texto de esta bandeja mira DOS tablas: la oportunidad (su codigo) y
    // la propiedad (direccion y distrito). Un OR entre ellas no lo sirve
    // ningun indice; una rama por tabla si.
    // ------------------------------------------------------------------

    String PATRON = " like lower(concat('%', cast(:texto as varchar), '%'))";

    /** Alcance + filtros activos, identicos en las dos ramas. */
    String COMUN = """
             where v.organizacion_id = :idOrganizacion
               and (:sinScope = true
                    or (:porAgente = true  and v.id_rol_agente = any(cast(:roles as bigint[])))
                    or (:porAgente = false and cap.id_rol_agente = any(cast(:roles as bigint[]))))
               and (cast(:idOportunidad as bigint) is null
                    or v.id_oportunidad = cast(:idOportunidad as bigint))
               and (cast(:estado as varchar) is null or v.estado = cast(:estado as varchar))
               and (cast(:distrito as varchar) is null
                    or lower(prop.distrito) like lower(concat('%', cast(:distrito as varchar), '%')))
            """;

    /**
     * Las dos ramas arrastran {@code propiedad} aunque solo una busque en ella:
     * el filtro de distrito vive ahi y tiene que estar cerrado ANTES del union,
     * o el conteo y la pagina podrian discrepar.
     */
    String RAMAS_TEXTO = """
            select v.id_visita as id
              from visita v
              join oportunidad_comercial o on o.id_oportunidad = v.id_oportunidad
              join captacion cap on cap.id_captacion = o.id_captacion
              join propiedad prop on prop.id_propiedad = cap.id_propiedad
            """ + COMUN + """
               and lower(o.codigo_oportunidad)""" + PATRON + """

            union
            select v.id_visita as id
              from visita v
              join oportunidad_comercial o on o.id_oportunidad = v.id_oportunidad
              join captacion cap on cap.id_captacion = o.id_captacion
              join propiedad prop on prop.id_propiedad = cap.id_propiedad
            """ + COMUN + """
               and (lower(prop.direccion)""" + PATRON + """
                    or lower(prop.distrito)""" + PATRON + """
                   )
            """;

    /** Ids de la pagina, ordenados y recortados EN LA BASE. */
    @Query(value = "select c.id from (" + RAMAS_TEXTO + ") c"
            + " join visita v2 on v2.id_visita = c.id"
            + " order by v2.fecha_visita desc, c.id desc limit :limite offset :desplazamiento",
            nativeQuery = true)
    List<Long> idsPorTexto(@Param("idOrganizacion") long idOrganizacion,
                           @Param("sinScope") boolean sinScope,
                           @Param("porAgente") boolean porAgente,
                           @Param("roles") String roles,
                           @Param("idOportunidad") Long idOportunidad,
                           @Param("estado") String estado,
                           @Param("distrito") String distrito,
                           @Param("texto") String texto,
                           @Param("limite") int limite,
                           @Param("desplazamiento") int desplazamiento);

    /** Total del MISMO conjunto que pagina {@link #idsPorTexto}. */
    @Query(value = "select count(*) from (" + RAMAS_TEXTO + ") c", nativeQuery = true)
    long contarPorTexto(@Param("idOrganizacion") long idOrganizacion,
                        @Param("sinScope") boolean sinScope,
                        @Param("porAgente") boolean porAgente,
                        @Param("roles") String roles,
                        @Param("idOportunidad") Long idOportunidad,
                        @Param("estado") String estado,
                        @Param("distrito") String distrito,
                        @Param("texto") String texto);

    /** KPI sobre el MISMO conjunto de candidatos, con el estado en nulo. */
    @Query(value = "select v.estado as estado, count(*) as total"
            + " from visita v join (" + RAMAS_TEXTO + ") c on c.id = v.id_visita"
            + " group by v.estado",
            nativeQuery = true)
    List<ConteoPorEstado> contarPorEstadoConTexto(@Param("idOrganizacion") long idOrganizacion,
                                                  @Param("sinScope") boolean sinScope,
                                                  @Param("porAgente") boolean porAgente,
                                                  @Param("roles") String roles,
                                                  @Param("idOportunidad") Long idOportunidad,
                                                  @Param("estado") String estado,
                                                  @Param("distrito") String distrito,
                                                  @Param("texto") String texto);

    /** Proyeccion completa de la pagina ya resuelta: acceso por clave. */
    @Query("""
            select v from Visita v
              join fetch v.oportunidad o
              left join fetch o.personaInteresada
              left join fetch o.cliente cli
              left join fetch cli.rol cliRol
              left join fetch cliRol.persona
              join fetch o.captacion cap
              join fetch cap.propiedad
              join fetch v.agente ag
              join fetch ag.rol agRol
              join fetch agRol.persona
            where v.organizacionId = :idOrganizacion and v.id in :ids
            order by v.fechaVisita desc, v.id desc
            """)
    List<Visita> buscarFichaPorIds(@Param("idOrganizacion") long idOrganizacion,
                                   @Param("ids") Collection<Long> ids);

    /**
     * Las visitas de UNOS ENCARGOS concretos: actividad de la ficha universal.
     *
     * <p>La visita es la que mas lejos esta de la propiedad --cuelga de la
     * oportunidad, que cuelga del encargo-- y por eso la que peor se resolvia
     * desde el cliente: {@code GET /visitas} solo filtra por
     * {@code idOportunidad}, o sea <b>una llamada por oportunidad</b>. Aqui es
     * una, y ademas devuelve de que encargo viene cada una.
     *
     * <p>Que una visita sea de alguien que quiere comprar o de alguien que
     * quiere alquilar la misma propiedad es justo lo que una lista plana
     * pierde.
     */
    @Query("""
            select v from Visita v
              join fetch v.oportunidad o
              left join fetch o.personaInteresada
              left join fetch o.cliente cli
              left join fetch cli.rol cliRol
              left join fetch cliRol.persona
              join fetch v.agente ag
              join fetch ag.rol agRol
              join fetch agRol.persona
            where v.organizacionId = :idOrganizacion
              and o.captacion.id in :idsEncargos
            order by v.fechaVisita desc, v.id desc
            """)
    List<Visita> listarFichaPorEncargos(@Param("idOrganizacion") long idOrganizacion,
                                        @Param("idsEncargos") Collection<Long> idsEncargos);

    /**
     * Distritos presentes en el alcance, para que el selector sea data-driven
     * sin descargar la agenda. No se acota por {@code distrito}: es justo el
     * filtro cuyas opciones devuelve.
     */
    @Query("""
            select distinct prop.distrito
            from Visita v
              join v.oportunidad o
              join o.captacion cap
              join cap.propiedad prop
              join cap.agente capAg
              join v.agente ag
            where v.organizacionId = :idOrganizacion
              and (:sinScope = true
                   or (:porAgente = true  and ag.id in :roles)
                   or (:porAgente = false and capAg.id in :roles))
              and prop.distrito is not null
            order by prop.distrito
            """)
    List<String> distritosDisponibles(@Param("idOrganizacion") long idOrganizacion,
                                      @Param("sinScope") boolean sinScope,
                                      @Param("porAgente") boolean porAgente,
                                      @Param("roles") Collection<Long> roles);

    String SCOPE_AGENDA = """
            from Visita v
              join v.oportunidad o
              join o.captacion cap
              join cap.agente capAg
              join v.agente ag
            where v.organizacionId = :idOrganizacion
              and (:sinScope = true
                   or (:porAgente = true  and ag.id in :roles)
                   or (:porAgente = false and capAg.id in :roles))
            """;

    /** Agenda: las que siguen vivas desde hoy, la mas cercana primero. */
    @Query("select v " + SCOPE_AGENDA + """
              and v.estado in ('P', 'G')
              and v.fechaVisita >= :desde
            order by v.fechaVisita asc, v.horaVisita asc, v.id asc
            """)
    List<Visita> listarProximas(@Param("idOrganizacion") long idOrganizacion,
                                @Param("sinScope") boolean sinScope,
                                @Param("porAgente") boolean porAgente,
                                @Param("roles") Collection<Long> roles,
                                @Param("desde") LocalDate desde,
                                Pageable pageable);

    /** Calendario del mes (sin paginar, como el cable v1). */
    @Query("select v " + SCOPE_AGENDA + """
              and v.fechaVisita >= :desde and v.fechaVisita <= :hasta
            order by v.fechaVisita asc, v.horaVisita asc, v.id asc
            """)
    List<Visita> listarMes(@Param("idOrganizacion") long idOrganizacion,
                           @Param("sinScope") boolean sinScope,
                           @Param("porAgente") boolean porAgente,
                           @Param("roles") Collection<Long> roles,
                           @Param("desde") LocalDate desde,
                           @Param("hasta") LocalDate hasta);

    @Query("""
            select v from Visita v
              join fetch v.oportunidad o
              left join fetch o.personaInteresada
              left join fetch o.cliente cli
              left join fetch cli.rol cliRol
              left join fetch cliRol.persona
              join fetch o.captacion cap
              join fetch cap.propiedad
              join fetch v.agente ag
              join fetch ag.rol agRol
              join fetch agRol.persona
            where v.organizacionId = :idOrganizacion and v.id = :id
            """)
    Optional<Visita> buscarFicha(@Param("idOrganizacion") long idOrganizacion, @Param("id") long id);

    /**
     * Disparador 5 de la bandeja (F7): visitas del agente que exigen accion —la
     * que se cayo ({@code N} no realizada) y la que esta programada o
     * reprogramada con fecha ya dentro del horizonte—. REALIZADA y CANCELADA
     * son terminales: no disparan y por eso se auto-resuelven.
     *
     * <p>El estado viaja como {@code marca} porque de el dependen la prioridad
     * y el mensaje: no realizada y vencida son ALTA, proxima es MEDIA.
     */
    @Query("""
            select v.id as entidadId, op.codigoOportunidad as entidadCodigo,
                   v.fechaVisita as fechaPlazo, v.horaVisita as horaPlazo, v.estado as marca
            from Visita v
              join v.oportunidad op
            where v.organizacionId = :idOrganizacion
              and v.agente.id = :idRolAgente
              and (v.estado = 'N'
                   or (v.estado in ('P', 'G') and v.fechaVisita is not null))
            """)
    List<CandidatoTarea> queExigenAccion(@Param("idOrganizacion") long idOrganizacion,
                                         @Param("idRolAgente") long idRolAgente);

    /** Visitas REALIZADAS de las oportunidades de una captacion en rango inclusivo. */
    @Query("""
            select count(v) from Visita v
              join v.oportunidad o
            where v.organizacionId = :idOrganizacion
              and o.captacion.id = :idCaptacion
              and v.estado = 'R'
              and v.fechaVisita >= :desde
              and v.fechaVisita <= :hasta
            """)
    long contarRealizadasParaReporte(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idCaptacion") long idCaptacion,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    @Query("""
            select v from Visita v
            where v.organizacionId = :idOrganizacion
              and v.oportunidad.cliente.id = :idCliente
            order by v.id
            """)
    List<Visita> listarFichaPorCliente(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idCliente") long idCliente);

    /**
     * Base de visitas de los indicadores E4: alcance SOLO por agente de la
     * visita (aqui la captacion no alcanza a nadie, al reves que en el listado
     * de /visitas).
     */
    @Query("""
            select v.id as id, v.agente.id as idAgente, v.estado as estado,
                   v.fechaVisita as fechaVisita, op.id as idOportunidad
            from Visita v join v.oportunidad op
            where v.organizacionId = :idOrganizacion
              and (:sinScope = true or v.agente.id in :roles)
            """)
    List<IndicadorVisita> indicadores(@Param("idOrganizacion") long idOrganizacion,
                                      @Param("sinScope") boolean sinScope,
                                      @Param("roles") Collection<Long> roles);
}
