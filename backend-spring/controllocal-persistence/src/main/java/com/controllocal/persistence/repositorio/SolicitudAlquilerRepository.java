package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.persistence.query.AgenteConSolicitudes;
import com.controllocal.persistence.query.CandidatoTarea;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.query.IndicadorSolicitud;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Solicitudes con el scope del actor en el WHERE (RC-001/RC-003) y el tenant
 * por delante (V6).
 *
 * <p>Aqui el alcance del BROKER es <b>por agente supervisado</b> —no por
 * captacion como en oportunidades y contratos—, asi que las dos ramas del rol
 * comparten la misma condicion y solo cambia el conjunto de roles: el AGENTE
 * pasa el suyo y el BROKER los de su equipo. Es el mismo
 * {@code alcanceAgentes()} de {@code SolicitudesRest}.
 */
public interface SolicitudAlquilerRepository extends JpaRepository<SolicitudAlquiler, Long> {

    /**
     * Alcance y filtros del listado. Dos apuntes que se pagan caro si se
     * pierden:
     *
     * <ul>
     *   <li>el <b>agente no se une</b>: {@code s.agente.id} es la FK de la
     *       propia solicitud y Hibernate la resuelve sin visitar
     *       {@code detalle_agente}. El {@code join s.agente ag} que habia aqui
     *       era un join muerto que el conteo pagaba en cada peticion — la misma
     *       leccion que dejo el gate de F3;</li>
     *   <li>la <b>propiedad si se une</b>, y no por gusto: el filtro por
     *       distrito vive ahi y tiene que estar cerrado antes de contar, o el
     *       total y la pagina podrian discrepar.</li>
     * </ul>
     *
     * <p>{@code estado = 'PENDIENTES'} <b>no es un estado</b>: es el cubo de la
     * bandeja del broker —{@code E} en revision y {@code O} observada—, igual
     * que {@code GESTION} en prospecciones. Se resuelve aqui para que la cola
     * salga en UNA consulta paginada en la base.
     */
    String DESDE = """
            from SolicitudAlquiler s
              join s.oportunidad op
              join op.captacion cap
              join cap.propiedad prop
            where s.organizacionId = :idOrganizacion
              and (:sinScope = true or s.agente.id in :roles)
              and (:idOportunidad is null or s.oportunidad.id = :idOportunidad)
              and (:idCaptacion is null or op.captacion.id = :idCaptacion)
              and (:idAgente is null or s.agente.id = :idAgente)
              and (:estado is null
                   or (:estado = 'PENDIENTES' and s.estado in ('E', 'O'))
                   or (:estado <> 'PENDIENTES' and s.estado = :estado))
              and (:distrito is null
                   or lower(prop.distrito) like lower(concat('%', cast(:distrito as string), '%')))
            """;

    /**
     * Listado SIN texto libre. El texto no entra aqui a proposito: cruza cinco
     * tablas y tiene su propio camino por conjunto de candidatos
     * ({@link #idsPorTexto}), como exige la §5 del contrato de listados. Si no
     * esta en la firma, nadie puede volver a colarlo en un {@code OR}.
     */
    @Query(value = "select s " + DESDE + " order by s.id desc",
            countQuery = "select count(s) " + DESDE)
    Page<SolicitudAlquiler> buscar(@Param("idOrganizacion") long idOrganizacion,
                                   @Param("sinScope") boolean sinScope,
                                   @Param("roles") Collection<Long> roles,
                                   @Param("idOportunidad") Long idOportunidad,
                                   @Param("idCaptacion") Long idCaptacion,
                                   @Param("idAgente") Long idAgente,
                                   @Param("estado") String estado,
                                   @Param("distrito") String distrito,
                                   Pageable pageable);

    /**
     * KPI de la bandeja por estado, con un solo {@code group by} sobre el MISMO
     * conjunto que pagina {@link #buscar} y con el {@code estado} en nulo: son
     * los cubos que cuenta, no un filtro.
     *
     * <p>Extension aditiva del v2. El Blazor descargaba TODAS las solicitudes
     * del alcance y agrupaba en memoria; con paginacion real eso solo contaria
     * la pagina visible.
     */
    /**
     * Reparto por estado de las solicitudes de UN agente, para su ficha. No
     * lleva el filtro de alcance del listado: quién puede ver la ficha lo
     * decide el service antes de llamar aquí.
     */
    @Query("""
            select s.estado as estado, count(s) as total
            from SolicitudAlquiler s
            where s.organizacionId = :idOrganizacion
              and s.agente.id = :idRolAgente
            group by s.estado
            """)
    List<ConteoPorEstado> contarPorEstadoDeAgente(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idRolAgente") long idRolAgente);

    @Query("select s.estado as estado, count(s) as total " + DESDE + " group by s.estado")
    List<ConteoPorEstado> contarPorEstado(@Param("idOrganizacion") long idOrganizacion,
                                          @Param("sinScope") boolean sinScope,
                                          @Param("roles") Collection<Long> roles,
                                          @Param("idOportunidad") Long idOportunidad,
                                          @Param("idCaptacion") Long idCaptacion,
                                          @Param("idAgente") Long idAgente,
                                          @Param("estado") String estado,
                                          @Param("distrito") String distrito);

    /** Distritos presentes en el alcance, para que el selector sea data-driven. */
    @Query("select distinct prop.distrito " + DESDE + " and prop.distrito is not null"
            + " order by prop.distrito")
    List<String> distritosDisponibles(@Param("idOrganizacion") long idOrganizacion,
                                      @Param("sinScope") boolean sinScope,
                                      @Param("roles") Collection<Long> roles,
                                      @Param("idOportunidad") Long idOportunidad,
                                      @Param("idCaptacion") Long idCaptacion,
                                      @Param("idAgente") Long idAgente,
                                      @Param("estado") String estado,
                                      @Param("distrito") String distrito);

    /**
     * Agentes con al menos una solicitud en el alcance. Aqui SI hace falta unir
     * el agente y su persona: se pide el nombre, no solo el id.
     */
    @Query("select distinct s.agente.id as id, agPer.nombresORazonSocial as nombre "
            + " from SolicitudAlquiler s"
            + "   join s.oportunidad op"
            + "   join op.captacion cap"
            + "   join cap.propiedad prop"
            + "   join s.agente ag join ag.rol agRol join agRol.persona agPer"
            + " where s.organizacionId = :idOrganizacion"
            + "   and (:sinScope = true or s.agente.id in :roles)"
            + "   and (:idOportunidad is null or s.oportunidad.id = :idOportunidad)"
            + "   and (:idCaptacion is null or op.captacion.id = :idCaptacion)"
            + "   and (:estado is null"
            + "        or (:estado = 'PENDIENTES' and s.estado in ('E', 'O'))"
            + "        or (:estado <> 'PENDIENTES' and s.estado = :estado))"
            + "   and (:distrito is null"
            + "        or lower(prop.distrito) like lower(concat('%', cast(:distrito as string), '%')))"
            + " order by agPer.nombresORazonSocial")
    List<AgenteConSolicitudes> agentesDisponibles(@Param("idOrganizacion") long idOrganizacion,
                                                  @Param("sinScope") boolean sinScope,
                                                  @Param("roles") Collection<Long> roles,
                                                  @Param("idOportunidad") Long idOportunidad,
                                                  @Param("idCaptacion") Long idCaptacion,
                                                  @Param("estado") String estado,
                                                  @Param("distrito") String distrito);

    // ------------------------------------------------------------------
    // Busqueda por CONJUNTO DE CANDIDATOS (§5 del contrato de listados).
    //
    // El texto de esta bandeja mira CINCO tablas: la solicitud (su codigo), la
    // oportunidad (el suyo), la propiedad (direccion y distrito), la persona
    // del cliente y la del agente. Escrito como un unico OR, ese predicado
    // cruza tablas, PostgreSQL no combina los trigramas de cada una y cae a
    // Seq Scan con el LIKE como Join Filter — y el conteo del PageResponse
    // paga ese barrido en CADA peticion.
    //
    // Cada rama pregunta a UNA sola tabla; las demas entran por igualdad sobre
    // su FK. UNION, no UNION ALL: una solicitud puede casar por varias.
    // Tenant, alcance y filtros activos viajan en LAS CINCO ramas.
    // ------------------------------------------------------------------

    String PATRON = " like lower(concat('%', cast(:texto as varchar), '%'))";

    /** Alcance + filtros activos, identicos en todas las ramas. */
    String COMUN = """
             where s.organizacion_id = :idOrganizacion
               and (:sinScope = true or s.id_rol_agente = any(cast(:roles as bigint[])))
               and (cast(:idOportunidad as bigint) is null
                    or s.id_oportunidad = cast(:idOportunidad as bigint))
               and (cast(:idCaptacion as bigint) is null
                    or op.id_captacion = cast(:idCaptacion as bigint))
               and (cast(:idAgente as bigint) is null
                    or s.id_rol_agente = cast(:idAgente as bigint))
               and (cast(:estado as varchar) is null
                    or (cast(:estado as varchar) = 'PENDIENTES' and s.estado in ('E', 'O'))
                    or (cast(:estado as varchar) <> 'PENDIENTES'
                        and s.estado = cast(:estado as varchar)))
               and (cast(:distrito as varchar) is null
                    or lower(prop.distrito)
                       like lower(concat('%', cast(:distrito as varchar), '%')))
            """;

    /**
     * Las cinco ramas arrastran {@code oportunidad}, {@code captacion} y
     * {@code propiedad} aunque solo dos busquen ahi: los filtros por captacion
     * y por distrito viven en ellas y tienen que estar cerrados ANTES del
     * union.
     */
    String DESDE_TEXTO = """
              from solicitud_alquiler s
              join oportunidad_comercial op on op.id_oportunidad = s.id_oportunidad
              join captacion cap on cap.id_captacion = op.id_captacion
              join propiedad prop on prop.id_propiedad = cap.id_propiedad
            """;

    String RAMAS_TEXTO = "select s.id_solicitud as id" + DESDE_TEXTO + COMUN + """
               and lower(s.codigo_solicitud)""" + PATRON + """

            union
            select s.id_solicitud as id""" + DESDE_TEXTO + COMUN + """
               and lower(op.codigo_oportunidad)""" + PATRON + """

            union
            select s.id_solicitud as id""" + DESDE_TEXTO + COMUN + """
               and (lower(prop.direccion)""" + PATRON + """
                    or lower(prop.distrito)""" + PATRON + """
                   )
            union
            select s.id_solicitud as id""" + DESDE_TEXTO + """
              join persona_rol prCli on prCli.id_persona_rol = op.id_rol_cliente
              join persona perCli on perCli.id_persona = prCli.id_persona
            """ + COMUN + """
               and lower(perCli.nombres_o_razon_social)""" + PATRON + """

            union
            select s.id_solicitud as id""" + DESDE_TEXTO + """
              join persona_rol prAg on prAg.id_persona_rol = s.id_rol_agente
              join persona perAg on perAg.id_persona = prAg.id_persona
            """ + COMUN + """
               and lower(perAg.nombres_o_razon_social)""" + PATRON + """

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
                           @Param("roles") String roles,
                           @Param("idOportunidad") Long idOportunidad,
                           @Param("idCaptacion") Long idCaptacion,
                           @Param("idAgente") Long idAgente,
                           @Param("estado") String estado,
                           @Param("distrito") String distrito,
                           @Param("texto") String texto,
                           @Param("limite") int limite,
                           @Param("desplazamiento") int desplazamiento);

    /** Total del MISMO conjunto que pagina {@link #idsPorTexto}. */
    @Query(value = "select count(*) from (" + RAMAS_TEXTO + ") c", nativeQuery = true)
    long contarPorTexto(@Param("idOrganizacion") long idOrganizacion,
                        @Param("sinScope") boolean sinScope,
                        @Param("roles") String roles,
                        @Param("idOportunidad") Long idOportunidad,
                        @Param("idCaptacion") Long idCaptacion,
                        @Param("idAgente") Long idAgente,
                        @Param("estado") String estado,
                        @Param("distrito") String distrito,
                        @Param("texto") String texto);

    /** KPI sobre el MISMO conjunto de candidatos, con el estado en nulo. */
    @Query(value = "select s.estado as estado, count(*) as total"
            + " from solicitud_alquiler s join (" + RAMAS_TEXTO + ") c on c.id = s.id_solicitud"
            + " group by s.estado",
            nativeQuery = true)
    List<ConteoPorEstado> contarPorEstadoConTexto(@Param("idOrganizacion") long idOrganizacion,
                                                  @Param("sinScope") boolean sinScope,
                                                  @Param("roles") String roles,
                                                  @Param("idOportunidad") Long idOportunidad,
                                                  @Param("idCaptacion") Long idCaptacion,
                                                  @Param("idAgente") Long idAgente,
                                                  @Param("estado") String estado,
                                                  @Param("distrito") String distrito,
                                                  @Param("texto") String texto);

    /** Ficha completa: todo lo que la respuesta congelada necesita en un solo select. */
    String FICHA = """
            select s from SolicitudAlquiler s
              join fetch s.oportunidad op
              join fetch op.cliente cli
              join fetch cli.rol cliRol
              join fetch cliRol.persona
              join fetch op.captacion cap
              join fetch cap.propiedad
              join fetch s.agente ag
              join fetch ag.rol agRol
              join fetch agRol.persona
            """;

    @Query(FICHA + " where s.organizacionId = :idOrganizacion and s.id = :id")
    Optional<SolicitudAlquiler> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                            @Param("id") long id);

    @Query(FICHA + " where s.organizacionId = :idOrganizacion and upper(s.codigoSolicitud) = upper(:codigo)")
    Optional<SolicitudAlquiler> buscarFichaPorCodigo(@Param("idOrganizacion") long idOrganizacion,
                                                     @Param("codigo") String codigo);

    /** Proyeccion completa de la pagina ya resuelta por texto: acceso por clave. */
    @Query(FICHA + " where s.organizacionId = :idOrganizacion and s.id in :ids order by s.id desc")
    List<SolicitudAlquiler> buscarFichaPorIds(@Param("idOrganizacion") long idOrganizacion,
                                              @Param("ids") Collection<Long> ids);

    /** Invariante del cable: una sola solicitud por oportunidad. */
    @Query("""
            select count(s) > 0 from SolicitudAlquiler s
            where s.organizacionId = :idOrganizacion and s.oportunidad.id = :idOportunidad
            """)
    boolean existeDeOportunidad(@Param("idOrganizacion") long idOrganizacion,
                                @Param("idOportunidad") long idOportunidad);

    /** Correlativo por organizacion (D-F4-4): el codigo es unico DENTRO del tenant. */
    @Query("""
            select count(s) > 0 from SolicitudAlquiler s
            where s.organizacionId = :idOrganizacion and upper(s.codigoSolicitud) = upper(:codigo)
            """)
    boolean existeCodigo(@Param("idOrganizacion") long idOrganizacion,
                         @Param("codigo") String codigo);

    /**
     * Disparadores 2 y 4 de la bandeja (F7): solicitudes del agente en un
     * estado que exige accion —{@code A} aprobada sin cerrar (seguimiento) y
     * {@code O} observada (subsanar documentos)—.
     *
     * <p>{@code fechaPlazo} es la <b>vigencia de la oferta</b>, que es lo que
     * de verdad vence; los "dias sin accion" salen de
     * {@code fechaActualizacionEstado}, que viaja como {@code marca} porque es
     * un timestamp y el read-DTO expone fechas.
     */
    @Query("""
            select s.id as entidadId, s.codigoSolicitud as entidadCodigo,
                   s.fechaVigenciaOferta as fechaPlazo,
                   cast(s.fechaActualizacionEstado as string) as marca
            from SolicitudAlquiler s
            where s.organizacionId = :idOrganizacion
              and s.agente.id = :idRolAgente
              and s.estado = :estado
            """)
    List<CandidatoTarea> porEstadoDelAgente(@Param("idOrganizacion") long idOrganizacion,
                                            @Param("idRolAgente") long idRolAgente,
                                            @Param("estado") String estado);

    @Query(FICHA + """
             where s.organizacionId = :idOrganizacion
               and cli.id = :idCliente
             order by s.id
            """)
    List<SolicitudAlquiler> listarFichaPorCliente(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idCliente") long idCliente);

    @Query(FICHA + """
             where s.organizacionId = :idOrganizacion
               and cap.propiedad.rolPropietario.id = :idPropietario
             order by s.id
            """)
    List<SolicitudAlquiler> listarFichaPorPropietario(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idPropietario") long idPropietario);

    /** Los expedientes de UNOS ENCARGOS concretos: actividad de la ficha universal. */
    @Query(FICHA + """
             where s.organizacionId = :idOrganizacion
               and cap.id in :idsEncargos
             order by s.fechaRegistro desc, s.id desc
            """)
    List<SolicitudAlquiler> listarFichaPorEncargos(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idsEncargos") Collection<Long> idsEncargos);

    /** Base de solicitudes de los indicadores E4: alcance SOLO por agente. */
    @Query("""
            select s.id as id, s.agente.id as idAgente, s.estado as estado,
                   s.fechaRegistro as fechaRegistro, op.captacion.id as idCaptacion,
                   op.id as idOportunidad, op.cliente.id as idCliente
            from SolicitudAlquiler s join s.oportunidad op
            where s.organizacionId = :idOrganizacion
              and (:sinScope = true or s.agente.id in :roles)
            """)
    List<IndicadorSolicitud> indicadores(@Param("idOrganizacion") long idOrganizacion,
                                         @Param("sinScope") boolean sinScope,
                                         @Param("roles") Collection<Long> roles);

    /**
     * Fuente del seguimiento comercial: union de agente propio y agente de la
     * captacion. {@code porCaptacion} solo es {@code true} para el BROKER — el
     * AGENTE ve lo suyo y nada mas. Superconjunto: el filtro autoritativo se
     * repite arriba, fila por fila.
     */
    @Query(FICHA + """
             where s.organizacionId = :idOrganizacion
               and (:sinScope = true or ag.id in :roles
                    or (:porCaptacion = true and cap.agente.id in :roles))
            """)
    List<SolicitudAlquiler> listarSeguimiento(@Param("idOrganizacion") long idOrganizacion,
                                              @Param("sinScope") boolean sinScope,
                                              @Param("porCaptacion") boolean porCaptacion,
                                              @Param("roles") Collection<Long> roles);

    /**
     * <b>Solicitudes esperando la firma del broker</b> (D-E2-5, E2.5).
     *
     * <p>Las REGISTRADAS y las EN_REVISION de su alcance: las que no avanzan
     * hasta que el broker evalua. Firmar una evaluacion es «la mas sensible de
     * las 18» segun la matriz operacion-rol, y hoy no aparece en ninguna parte
     * del Inicio.
     *
     * <p><b>No incluye OBSERVADA</b>, y esa exclusion es la decision: una
     * solicitud observada espera al AGENTE, que tiene que subsanar. Es la misma
     * solicitud y el dueno cambia con el estado, asi que meterlas juntas pondria
     * en el foco del broker algo que no puede resolver -- justo lo que E2.2
     * quito con `dependeDeMi`.
     *
     * <p>Devuelve {@code CandidatoTarea}, la misma forma que los disparadores
     * del agente, para que el foco del broker pase por la MISMA politica de
     * despacho y la MISMA capa de interpretacion. Una forma propia habria
     * significado un segundo motor.
     */
    @Query("""
            select s.id as entidadId,
                   s.codigoSolicitud as entidadCodigo,
                   s.fechaRegistro as fechaPlazo,
                   s.estado as marca
              from SolicitudAlquiler s
             where s.organizacionId = :idOrganizacion
               and s.estado in ('G', 'E')
               and (:sinScope = true or s.agente.id in :roles)
             order by s.fechaRegistro asc, s.id asc
            """)
    List<CandidatoTarea> porEvaluarDelBroker(@Param("idOrganizacion") long idOrganizacion,
                                             @Param("sinScope") boolean sinScope,
                                             @Param("roles") List<Long> roles);

    /**
     * <b>Cuantos documentos esperan la conformidad del broker, por solicitud</b>
     * (D-E2-5, cierre de E2.5).
     *
     * <p>Una consulta con {@code group by} para TODA la pagina, no una por
     * solicitud. Devuelve las dos cifras -- pendientes y total -- porque el
     * Inicio no dice "faltan 3": dice "2 de 5 conformados", y eso contesta
     * «cuanto me falta» sin abrir nada.
     *
     * <p>Es el primer contador REAL del `avance` de E2.4, que hasta ahora viajaba
     * en null por no tener ningun requisito contable de verdad.
     */
    @Query("""
            select s.id as idSolicitud,
                   count(d) as total,
                   sum(case when d.resultadoRevision = 'P' then 1 else 0 end) as pendientes
              from DocumentoSolicitud d
              join d.solicitud s
             where s.organizacionId = :idOrganizacion
               and s.id in :idsSolicitud
             group by s.id
            """)
    List<Object[]> documentosPorConformar(@Param("idOrganizacion") long idOrganizacion,
                                          @Param("idsSolicitud") Collection<Long> idsSolicitud);
}
