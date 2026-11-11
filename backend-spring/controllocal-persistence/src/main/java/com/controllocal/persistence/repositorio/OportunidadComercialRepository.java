package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.persistence.query.ConteoPorAgente;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.query.IndicadorOportunidad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Oportunidades con el scope del actor en el WHERE (RC-001/RC-003) y el
 * tenant por delante (V6).
 *
 * <p>El alcance del BROKER es **por captacion** (contrato F3 §4): ve las
 * oportunidades cuya CAPTACION es de un agente de su equipo, aunque la
 * oportunidad se haya reasignado a otro. Por eso {@code porAgente} distingue
 * las dos ramas en vez de filtrar siempre por el agente de la fila.
 */
public interface OportunidadComercialRepository extends JpaRepository<OportunidadComercial, Long> {
    /** Metadata exclusivamente comercial; no hidrata personas, documentos o condiciones privadas. */
    interface FuenteDesenlace {
        Long getId();Long getVersion();Long getResponsable();Long getResponsableEncargo();
        String getEstado();String getOperacion();Long getResponsableHistorico();Long getRevisionHito();Long getHistorial();
        java.time.Instant getRegistrado();java.time.Instant getActualizado();java.time.LocalDate getFechaResolucion();Boolean getConfirmadoC5();
    }
    @Query(value="""
        select o.id_oportunidad as id,o.version as version,o.id_rol_agente as responsable,
          c.id_rol_agente as "responsableEncargo",o.estado as estado,c.motivo_operacion as operacion,
          case when o.estado in ('F','N','X') then he.id_rol_responsable else o.id_rol_agente end as "responsableHistorico",
          rh.id_revision_hito as "revisionHito",he.id_historial as historial,
          coalesce(rh.registrado_en,he.fecha_evento,o.fecha_registro) as registrado,o.fecha_actualizacion as actualizado,
          case when o.estado in ('N','X') then he.fecha_efectiva
            when o.estado='F' and e.id_expediente is not null then rh.fecha_efectiva end as "fechaResolucion",
          (o.estado='F' and e.id_expediente is not null and rh.id_revision_hito is not null) as "confirmadoC5"
        from oportunidad_comercial o join captacion c
          on c.organizacion_id=o.organizacion_id and c.id_captacion=o.id_captacion
        left join lateral(select h.* from historial_estado h where h.organizacion_id=o.organizacion_id
          and h.entidad_tipo='OPORTUNIDAD' and h.id_entidad=o.id_oportunidad
          and h.estado_nuevo=o.estado and h.estado_nuevo in ('F','N','X') and h.fecha_evento<=:corte
          order by h.id_historial desc limit 1) he on true
        left join revision_hito_contractual original on original.organizacion_id=o.organizacion_id
          and original.id_revision_hito=he.id_revision_hito and original.tipo_registro in ('CONFIRMACION','RECTIFICACION')
        left join hito_contractual hito on hito.organizacion_id=original.organizacion_id
          and hito.id_hito=original.id_hito and hito.id_expediente=original.id_expediente
          and ((c.motivo_operacion='V' and hito.tipo='ESCRITURA') or (c.motivo_operacion='A' and hito.tipo='CONTRATO_ALQUILER'))
        left join expediente_operacion e on e.organizacion_id=hito.organizacion_id and e.id_expediente=hito.id_expediente
          and e.id_oportunidad=o.id_oportunidad and e.id_captacion=c.id_captacion
          and ((c.motivo_operacion='V' and e.id_contrato_venta is not null) or (c.motivo_operacion='A' and e.id_contrato_alquiler is not null))
        left join lateral(select r.id_revision_hito,r.registrado_en,r.fecha_efectiva
          from revision_hito_contractual r where r.organizacion_id=e.organizacion_id and r.id_expediente=e.id_expediente
          and r.id_hito=hito.id_hito and r.tipo_registro in ('CONFIRMACION','RECTIFICACION') and r.registrado_en<=:corte
          order by r.id_revision_hito desc limit 1) rh on true
        where o.organizacion_id=:org and o.fecha_registro<=:corte
          and (o.id_rol_agente in :roles or c.id_rol_agente in :roles)
        order by o.id_oportunidad
        """,nativeQuery=true)
    List<FuenteDesenlace> fuentesDesenlace(@Param("org") long org,@Param("roles") Collection<Long> roles,
                                           @Param("corte") java.time.Instant corte);

    interface VersionActividad {
        long getVersion(); java.time.Instant getRegistradoEn(); String getOperacion(); String getMotivo(); String getFicha();
    }
    @org.springframework.data.jpa.repository.Modifying
    @Query(value="INSERT INTO oportunidad_historia(organizacion_id,id_oportunidad,version,registrado_en,operacion,motivo,ficha) VALUES (:org,:id,:version,:fecha,:operacion,:motivo,CAST(:ficha AS jsonb))",nativeQuery=true)
    void historizar(@Param("org") long org,@Param("id") long id,@Param("version") long version,
        @Param("fecha") java.time.Instant fecha,@Param("operacion") String operacion,@Param("motivo") String motivo,@Param("ficha") String ficha);
    @Query(value="SELECT ficha::text FROM oportunidad_historia WHERE organizacion_id=:org AND id_oportunidad=:id AND registrado_en<=:corte ORDER BY version DESC LIMIT 1",nativeQuery=true)
    Optional<String> fichaAlCorte(@Param("org") long org,@Param("id") long id,@Param("corte") java.time.Instant corte);
    @Query(value="SELECT ficha::text FROM oportunidad_historia WHERE organizacion_id=:org AND id_oportunidad=:id AND version=:version",nativeQuery=true)
    Optional<String> fichaVersion(@Param("org") long org,@Param("id") long id,@Param("version") long version);
    @Query(value="SELECT version,registrado_en AS \"registradoEn\",operacion,motivo,ficha::text AS ficha FROM oportunidad_historia WHERE organizacion_id=:org AND id_oportunidad=:id AND registrado_en<=:corte ORDER BY version",nativeQuery=true)
    List<VersionActividad> historia(@Param("org") long org,@Param("id") long id,@Param("corte") java.time.Instant corte);

    interface ContextoOportunidad {
        Long getId(); Long getOrganizacion(); long getVersion(); Long getEncargo();
        Long getResponsable(); Long getResponsableEncargo(); String getEstado();
        String getOperacion();
        Long getPropiedad(); Long getPersona(); Long getCliente();
    }
    @Query("""
        select o.id as id,o.organizacionId as organizacion,o.version as version,
          c.id as encargo,o.agente.id as responsable,c.agente.id as responsableEncargo,
          o.estado as estado,c.motivoOperacion as operacion,c.propiedad.id as propiedad,
          coalesce(pi.id,pc.id) as persona,cli.id as cliente
        from OportunidadComercial o join o.captacion c
        left join o.personaInteresada pi left join o.cliente cli
        left join cli.rol rc left join rc.persona pc
        where o.organizacionId=:org and o.id=:id
        """)
    Optional<ContextoOportunidad> contexto(@Param("org") long org,@Param("id") long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OportunidadComercial o where o.organizacionId=:org and o.id=:id")
    Optional<OportunidadComercial> bloquear(@Param("org") long org,@Param("id") long id);
    @Query(value="select 1 from pg_advisory_xact_lock(hashtextextended('BROX:OP:'||cast(:org as text),0))",nativeQuery=true)
    int bloquearNumeracion(@Param("org") long org);
    @Query(value="select coalesce(max(id_oportunidad),0)+1 from oportunidad_comercial where organizacion_id=:org",nativeQuery=true)
    long siguienteNumero(@Param("org") long org);
    @Query("""
        select count(o)>0 from OportunidadComercial o left join o.personaInteresada pi
          left join o.cliente cli left join cli.rol cr left join cr.persona pc
        where o.organizacionId=:org and o.estado='A' and o.captacion.id=:encargo
          and (pi.id=:persona or pc.id=:persona)
        """)
    boolean existeAbiertaDelInteresado(@Param("org") long org,@Param("encargo") long encargo,@Param("persona") long persona);
    /** Revalida pertenencia/rol actual sin recuperar cartera ni datos de la Persona. */
    @Query("""
        select count(r)>0 from PersonaRol r,UsuarioOrganizacion u,CuentaAcceso c,Organizacion o
        where r.id=:rol and r.organizacionId=:org and r.persona.id=:persona
          and r.persona.estado='A' and r.vigenciaHasta is null and r.vigenciaDesde<=:hoy
          and u.organizacionId=:org and u.idPersona=:persona and u.estado='A' and u.rol=:atribucion
          and ((:atribucion='AGENTE' and r.tipoRol=com.controllocal.domain.persona.enums.TipoRol.AGENTE)
            or (:atribucion='BROKER' and r.tipoRol=com.controllocal.domain.persona.enums.TipoRol.BROKER))
          and c.id=u.idUsuario and c.estado='A' and c.correoConfirmadoEn is not null
          and o.id=:org and o.estado='A'
        """)
    boolean contextoOperativoActual(@Param("org") long org,@Param("persona") long persona,
        @Param("rol") long rol,@Param("atribucion") String atribucion,@Param("hoy") java.time.LocalDate hoy);

    /** Sólo definición estática: gobierno vigente no obtiene cartera ni operación. */
    @Query("""
        select count(r)>0 from PersonaRol r,UsuarioOrganizacion u,CuentaAcceso c,Organizacion o
        where r.id=:rol and r.organizacionId=:org and r.persona.id=:persona
          and r.persona.estado='A' and r.vigenciaHasta is null and r.vigenciaDesde<=:hoy
          and r.tipoRol=com.controllocal.domain.persona.enums.TipoRol.ADMIN
          and u.organizacionId=:org and u.idPersona=:persona and u.estado='A' and u.administrar=true
          and c.id=u.idUsuario and c.estado='A' and c.correoConfirmadoEn is not null
          and o.id=:org and o.estado='A'
        """)
    boolean contextoGobiernoParaMetadata(@Param("org") long org,@Param("persona") long persona,
        @Param("rol") long rol,@Param("hoy") java.time.LocalDate hoy);

    String SCOPE = """
            where o.organizacionId = :idOrganizacion
              and (:sinScope = true
                   or (:porAgente = true  and ag.id in :roles)
                   or (:porAgente = false and capAg.id in :roles))
            """;

    /**
     * Solo las tablas que ALGO necesita: la captacion y los dos agentes, que
     * son el alcance. El cliente, su persona y la propiedad se unian aqui unica
     * y exclusivamente para el {@code LIKE} del texto libre; al mudarse ese
     * predicado a las ramas del {@code UNION} quedaron como joins muertos que
     * el conteo seguia pagando —medido: 521/1043 ms de p95 en un listado SIN
     * buscar nada, sobre 100.000 filas—.
     *
     * <p>Los dos filtros por id se resuelven contra la COLUMNA de la propia
     * oportunidad ({@code o.captacion.id}, {@code o.cliente.id}), que Hibernate
     * traduce a la FK sin visitar la tabla del otro lado.
     */
    String DESDE = """
            from OportunidadComercial o
              join o.captacion cap
              join cap.agente capAg
              join o.agente ag
            """ + SCOPE + """
              and (:idCaptacion is null or o.captacion.id = :idCaptacion)
              and (:idCliente is null or o.cliente.id = :idCliente)
              and (:estado is null or o.estado = :estado)
            """;

    /**
     * Listado SIN texto libre. El texto no se resuelve aqui a proposito: cruza
     * cuatro tablas y tiene su propio camino por conjunto de candidatos
     * ({@link #idsPorTexto}), como exige la §5 del contrato de listados.
     */
    @Query(value = "select o " + DESDE + " order by o.id desc",
            countQuery = "select count(o) " + DESDE)
    Page<OportunidadComercial> buscar(@Param("idOrganizacion") long idOrganizacion,
                                      @Param("sinScope") boolean sinScope,
                                      @Param("porAgente") boolean porAgente,
                                      @Param("roles") Collection<Long> roles,
                                      @Param("idCaptacion") Long idCaptacion,
                                      @Param("idCliente") Long idCliente,
                                      @Param("estado") String estado,
                                      Pageable pageable);

    /**
     * KPI de la bandeja por estado, resuelto con un solo {@code group by} sobre
     * el MISMO conjunto que pagina {@link #buscar} — de ahi que repita su
     * {@code DESDE} con el {@code estado} en nulo: los cubos son lo que cuenta,
     * no lo que filtra.
     *
     * <p>Extension aditiva del v2. Sin esto, la bandeja Angular tendria que
     * contar sobre la pagina descargada, que es justo lo que hacia el Blazor
     * (descargaba TODAS las oportunidades y agrupaba en memoria).
     */
    @Query("select o.estado as estado, count(o) as total " + DESDE + " group by o.estado")
    List<ConteoPorEstado> contarPorEstado(@Param("idOrganizacion") long idOrganizacion,
                                          @Param("sinScope") boolean sinScope,
                                          @Param("porAgente") boolean porAgente,
                                          @Param("roles") Collection<Long> roles,
                                          @Param("idCaptacion") Long idCaptacion,
                                          @Param("idCliente") Long idCliente,
                                          @Param("estado") String estado);

    // ------------------------------------------------------------------
    // Busqueda por CONJUNTO DE CANDIDATOS (§5 del contrato de listados).
    //
    // El texto libre de esta bandeja mira CUATRO tablas: la oportunidad (su
    // codigo), la captacion (el suyo), la propiedad (direccion) y la persona
    // del cliente. Escrito como un unico OR, ese predicado cruza tablas y
    // PostgreSQL no puede combinar los indices trigrama de cada una: cae a
    // Seq Scan con el LIKE como Join Filter, y el conteo del PageResponse
    // paga ese barrido entero en CADA peticion.
    //
    // Aqui cada rama pregunta a UNA sola tabla —las demas entran solo por
    // igualdad sobre su FK, que si esta indexada— y el UNION las junta.
    // UNION, no UNION ALL: una oportunidad puede casar por varias ramas.
    //
    // Tenant, alcance y filtros activos viajan en LAS CUATRO ramas: el
    // conjunto queda cerrado antes de unirse, asi el conteo y la pagina no
    // pueden discrepar.
    // ------------------------------------------------------------------

    String PATRON = " like lower(concat('%', cast(:texto as varchar), '%'))";

    /** Alcance + filtros activos, identicos en todas las ramas. */
    String COMUN = """
             where o.organizacion_id = :idOrganizacion
               and (:sinScope = true
                    or (:porAgente = true  and o.id_rol_agente = any(cast(:roles as bigint[])))
                    or (:porAgente = false and cap.id_rol_agente = any(cast(:roles as bigint[]))))
               and (cast(:idCaptacion as bigint) is null or o.id_captacion = cast(:idCaptacion as bigint))
               and (cast(:idCliente as bigint) is null or o.id_rol_cliente = cast(:idCliente as bigint))
               and (cast(:estado as varchar) is null or o.estado = cast(:estado as varchar))
            """;

    String RAMAS_TEXTO = """
            select o.id_oportunidad as id
              from oportunidad_comercial o
              join captacion cap on cap.id_captacion = o.id_captacion
            """ + COMUN + """
               and lower(o.codigo_oportunidad)""" + PATRON + """

            union
            select o.id_oportunidad as id
              from oportunidad_comercial o
              join captacion cap on cap.id_captacion = o.id_captacion
            """ + COMUN + """
               and lower(cap.codigo_captacion)""" + PATRON + """

            union
            select o.id_oportunidad as id
              from oportunidad_comercial o
              join captacion cap on cap.id_captacion = o.id_captacion
              join propiedad prop on prop.id_propiedad = cap.id_propiedad
            """ + COMUN + """
               and lower(prop.direccion)""" + PATRON + """

            union
            select o.id_oportunidad as id
              from oportunidad_comercial o
              join captacion cap on cap.id_captacion = o.id_captacion
              join persona_rol prCli on prCli.id_persona_rol = o.id_rol_cliente
              join persona perCli on perCli.id_persona = prCli.id_persona
            """ + COMUN + """
               and lower(perCli.nombres_o_razon_social)""" + PATRON + """

            union
            select o.id_oportunidad as id
              from oportunidad_comercial o
              join captacion cap on cap.id_captacion = o.id_captacion
              join persona perInteres on perInteres.id_persona=o.id_persona_interesada
            """ + COMUN + """
               and lower(perInteres.nombres_o_razon_social)""" + PATRON + """

            """;

    /**
     * Ids de la pagina, ordenados y recortados EN LA BASE. Nunca sube a Java el
     * conjunto completo: eso seria el barrido que §5 vino a quitar.
     */
    @Query(value = "select c.id from (" + RAMAS_TEXTO
            + ") c order by c.id desc limit :limite offset :desplazamiento",
            nativeQuery = true)
    List<Long> idsPorTexto(@Param("idOrganizacion") long idOrganizacion,
                           @Param("sinScope") boolean sinScope,
                           @Param("porAgente") boolean porAgente,
                           @Param("roles") String roles,
                           @Param("idCaptacion") Long idCaptacion,
                           @Param("idCliente") Long idCliente,
                           @Param("estado") String estado,
                           @Param("texto") String texto,
                           @Param("limite") int limite,
                           @Param("desplazamiento") int desplazamiento);

    /** Total del MISMO conjunto que pagina {@link #idsPorTexto}. */
    @Query(value = "select count(*) from (" + RAMAS_TEXTO + ") c", nativeQuery = true)
    long contarPorTexto(@Param("idOrganizacion") long idOrganizacion,
                        @Param("sinScope") boolean sinScope,
                        @Param("porAgente") boolean porAgente,
                        @Param("roles") String roles,
                        @Param("idCaptacion") Long idCaptacion,
                        @Param("idCliente") Long idCliente,
                        @Param("estado") String estado,
                        @Param("texto") String texto);

    /** KPI sobre el MISMO conjunto de candidatos, con el estado en nulo. */
    @Query(value = "select o.estado as estado, count(*) as total"
            + " from oportunidad_comercial o join (" + RAMAS_TEXTO + ") c on c.id = o.id_oportunidad"
            + " group by o.estado",
            nativeQuery = true)
    List<ConteoPorEstado> contarPorEstadoConTexto(@Param("idOrganizacion") long idOrganizacion,
                                                  @Param("sinScope") boolean sinScope,
                                                  @Param("porAgente") boolean porAgente,
                                                  @Param("roles") String roles,
                                                  @Param("idCaptacion") Long idCaptacion,
                                                  @Param("idCliente") Long idCliente,
                                                  @Param("estado") String estado,
                                                  @Param("texto") String texto);

    /** Proyeccion completa de la pagina ya resuelta: acceso por clave. */
    @Query(FICHA + " where o.organizacionId = :idOrganizacion and o.id in :ids order by o.id desc")
    List<OportunidadComercial> buscarFichaPorIds(@Param("idOrganizacion") long idOrganizacion,
                                                 @Param("ids") Collection<Long> ids);

    String FICHA = """
            select o from OportunidadComercial o
              left join fetch o.cliente cli
              left join fetch cli.rol cliRol
              left join fetch cliRol.persona
              left join fetch o.personaInteresada
              join fetch o.captacion cap
              join fetch cap.propiedad
              join fetch cap.agente capAg
              join fetch o.agente ag
              join fetch ag.rol agRol
              join fetch agRol.persona
            """;

    @Query(FICHA + " where o.organizacionId = :idOrganizacion and o.id = :id")
    Optional<OportunidadComercial> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                               @Param("id") long id);

    /**
     * Clientes que un equipo trabaja: base del alcance de /clientes para el
     * BROKER y de la "vista personal" del matching de cartera (§7). Las DOS
     * ramas son del cable v1: cuenta el agente responsable de la oportunidad
     * <em>o</em> el agente de su captacion, porque una oportunidad reasignada
     * sigue siendo del equipo que capto el local.
     */
    @Query("""
            select distinct o.cliente.id from OportunidadComercial o
            where o.organizacionId = :idOrganizacion
              and (o.agente.id in :roles or o.captacion.agente.id in :roles)
            """)
    List<Long> idsClienteDelEquipo(@Param("idOrganizacion") long idOrganizacion,
                                   @Param("roles") Collection<Long> roles);

    /**
     * Pares {@code [idCliente, idCaptacion]} que YA tienen oportunidad del
     * equipo. Es lo que impide que el disparador 7 de la bandeja (F7) vuelva a
     * proponer una coincidencia que el agente ya convirtio.
     */
    @Query("""
            select distinct o.cliente.id, o.captacion.id from OportunidadComercial o
            where o.organizacionId = :idOrganizacion
              and (o.agente.id in :roles or o.captacion.agente.id in :roles)
            """)
    List<Object[]> paresClienteCaptacionDelEquipo(@Param("idOrganizacion") long idOrganizacion,
                                                  @Param("roles") Collection<Long> roles);

    /** Invariante v1: una sola oportunidad ABIERTA por cliente y captacion. */
    @Query("""
            select count(o) > 0 from OportunidadComercial o
            where o.organizacionId = :idOrganizacion and o.estado = 'A'
              and o.cliente.id = :idRolCliente and o.captacion.id = :idCaptacion
            """)
    boolean existeAbiertaDe(@Param("idOrganizacion") long idOrganizacion,
                            @Param("idRolCliente") long idRolCliente,
                            @Param("idCaptacion") long idCaptacion);

    long countByOrganizacionId(long idOrganizacion);

    /** Reparto por estado de las oportunidades de UN agente, para su ficha. */
    @Query("""
            select o.estado as estado, count(o) as total
            from OportunidadComercial o
            where o.organizacionId = :idOrganizacion
              and o.agente.id = :idRolAgente
            group by o.estado
            """)
    List<ConteoPorEstado> contarPorEstadoDeAgente(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idRolAgente") long idRolAgente);

    /** Contadores autoritativos del catalogo /agentes: ABIERTA o SOLICITUD_CREADA. */
    @Query("""
            select o.agente.id as idAgente, count(o) as total
            from OportunidadComercial o
            where o.organizacionId = :idOrganizacion
              and o.agente.id in :idsAgente
              and o.estado in ('A', 'S')
            group by o.agente.id
            """)
    List<ConteoPorAgente> contarActivasPorAgentes(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idsAgente") Collection<Long> idsAgente);

    @Query(FICHA + """
             where o.organizacionId = :idOrganizacion
               and cli.id = :idCliente
             order by o.id
            """)
    List<OportunidadComercial> listarFichaPorCliente(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idCliente") long idCliente);

    @Query(FICHA + """
             where o.organizacionId = :idOrganizacion
               and cap.propiedad.rolPropietario.id = :idPropietario
             order by o.id
            """)
    List<OportunidadComercial> listarFichaPorPropietario(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idPropietario") long idPropietario);

    /**
     * Las oportunidades de UNOS ENCARGOS concretos, para la actividad de la
     * ficha universal.
     *
     * <p>Se pide por encargo y no por propiedad para que la <b>procedencia</b>
     * salga de la propia consulta: quien la llama ya sabe de que encargo es
     * cada fila, sin tener que recorrer despues oportunidad -> captacion para
     * averiguarlo. Es lo que permite que el cliente reciba el {@code idEncargo}
     * puesto por el productor en vez de deducirlo (D-A-1).
     */
    @Query(FICHA + """
             where o.organizacionId = :idOrganizacion
               and cap.id in :idsEncargos
             order by o.fechaRegistro desc, o.id desc
            """)
    List<OportunidadComercial> listarFichaPorEncargos(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idsEncargos") Collection<Long> idsEncargos);

    /** Base de oportunidades de los indicadores E4: alcance SOLO por agente. */
    @Query("""
            select o.id as id, o.agente.id as idAgente, o.estado as estado,
                   o.fechaRegistro as fechaRegistro, o.captacion.id as idCaptacion,
                   o.cliente.id as idCliente
            from OportunidadComercial o
            where o.organizacionId = :idOrganizacion
              and (:sinScope = true or o.agente.id in :roles)
            """)
    List<IndicadorOportunidad> indicadores(@Param("idOrganizacion") long idOrganizacion,
                                           @Param("sinScope") boolean sinScope,
                                           @Param("roles") Collection<Long> roles);

    /**
     * Fuente del seguimiento comercial: la UNION de las dos ramas de alcance
     * (agente de la fila <em>o</em> agente de su captacion), no el {@code
     * switch} de {@link #buscar}. Es la unica lectura del sistema que las suma.
     *
     * <p>{@code porCaptacion} solo es {@code true} para el BROKER: el AGENTE ve
     * <b>lo suyo y nada mas</b>, aunque la captacion sea de su propia cartera.
     * Esto es un superconjunto; el filtro autoritativo se repite arriba, fila
     * por fila, igual que en la v1.
     */
    @Query(FICHA + """
             where o.organizacionId = :idOrganizacion
               and (:sinScope = true or ag.id in :roles
                    or (:porCaptacion = true and capAg.id in :roles))
            """)
    List<OportunidadComercial> listarSeguimiento(@Param("idOrganizacion") long idOrganizacion,
                                                 @Param("sinScope") boolean sinScope,
                                                 @Param("porCaptacion") boolean porCaptacion,
                                                 @Param("roles") Collection<Long> roles);
}
