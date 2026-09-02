package com.controllocal.persistence.repositorio;

import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.query.ConteoPorEstado;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // ------------------------------------------------------------------
    // D-P0-7: quien puede RECIBIR una propiedad, resuelto en la BASE
    // ------------------------------------------------------------------
    //
    // Las cinco condiciones de la elegibilidad viven en UN solo sitio
    // -{@link #CONDICION_CANDIDATO}- y las usan las DOS preguntas: la lista de
    // candidatos que el Core ofrece (D-P0-12) y la revalidacion del POST. Si
    // fueran dos escrituras del mismo predicado, la lista acabaria ofreciendo a
    // alguien que el POST rechaza -o al reves-, que es exactamente el defecto
    // que D-P0-12 vino a cerrar.
    //
    // NINGUNA de las cinco es un estado nuevo: cada una es la autoridad que ya
    // existe para ese hecho, y esta anotada al lado de su condicion.

    /**
     * El FROM sin {@code fetch}, para las preguntas que solo cuentan.
     *
     * <p>Se separa del de abajo <b>unicamente</b> por el {@code fetch}: la regla
     * —{@link #CONDICION_CANDIDATO}— es la misma cadena en las dos consultas, asi
     * que no hay dos versiones del predicado que puedan divergir.
     */
    String DESDE_CANDIDATO = """
            from DetalleAgente a
              join a.rol r
              join r.persona p
            """;

    /**
     * El mismo FROM con las dos referencias {@code ToOne} traidas de una vez.
     *
     * <p>La pagina de candidatos publica el <b>nombre</b> de cada uno, y con los
     * joins perezosos eso serian dos consultas por fila: el N+1 que RC-003
     * retiro, reapareciendo con un sujeto nuevo. Son referencias a-uno, asi que
     * el {@code fetch} convive con la paginacion (el {@code countQuery} usa el
     * FROM de arriba, sin fetch, como ya hace {@code paginaTodos}).
     */
    String DESDE_CANDIDATO_CON_FICHA = """
            from DetalleAgente a
              join fetch a.rol r
              join fetch r.persona p
            """;

    /**
     * <b>Las cinco condiciones de D-P0-7</b>, cada una con la autoridad que ya
     * decide ese hecho hoy:
     *
     * <pre>
     *   mismo tenant          organizacion_id del detalle Y del rol
     *   rol AGENTE vigente    persona_rol.tipo_rol='AGENTE' y vigencia_hasta null
     *   cuenta habilitada     credencial_usuario.estado_administrativo='A' de la
     *                         MISMA persona en la org, colgando de su
     *                         persona_rol USUARIO_INTERNO vigente
     *   membresia vigente     usuario_organizacion.estado='A' por ese mismo rol
     *                         USUARIO_INTERNO (la fuente que ya lee
     *                         UsuarioOrganizacionRepository.bandaActivaDePersona)
     *   disponible            detalle_agente.estado_operativo='D'
     * </pre>
     *
     * <p>Y la sexta, que <b>no</b> es de la persona sino del ACTOR:
     * {@code sinSupervision} lo pone el TENANT_ADMIN, que gobierna su
     * organizacion entera; un BROKER tiene que supervisar hoy al destino
     * ({@code supervision_agente.fecha_fin is null}), que es el mismo hecho que
     * {@code Alcances.alcanza} ya pregunta en el POST.
     *
     * <p><b>Supervision vigente y agente operativo son invariantes DISTINTAS</b>
     * y por eso son dos condiciones y no una: un agente puede estar supervisado
     * y de baja, y puede estar disponible y en el equipo de otro broker.
     */
    String CONDICION_CANDIDATO = """
            where a.organizacionId = :idOrganizacion
              and r.organizacionId = :idOrganizacion
              and r.tipoRol = com.controllocal.domain.persona.enums.TipoRol.AGENTE
              and r.vigenciaHasta is null
              and a.estadoOperativo = 'D'
              and exists (select 1 from CredencialUsuario c
                            join c.rol rc
                           where rc.persona = p
                             and rc.organizacionId = :idOrganizacion
                             and rc.tipoRol
                                 = com.controllocal.domain.persona.enums.TipoRol.USUARIO_INTERNO
                             and rc.vigenciaHasta is null
                             and c.estadoAdministrativo = 'A')
              and exists (select 1 from UsuarioOrganizacion uo, PersonaRol ru
                           where uo.organizacionId = :idOrganizacion
                             and uo.idUsuario = ru.id
                             and uo.estado = 'A'
                             and ru.persona = p
                             and ru.organizacionId = :idOrganizacion
                             and ru.tipoRol
                                 = com.controllocal.domain.persona.enums.TipoRol.USUARIO_INTERNO
                             and ru.vigenciaHasta is null)
              and (:sinSupervision = true
                   or exists (select 1 from SupervisionAgente s
                               where s.organizacionId = :idOrganizacion
                                 and s.idRolBroker = :idRolBroker
                                 and s.idRolAgente = a.id
                                 and s.fechaFin is null))
              and a.id <> :excluir
              and (:texto is null
                   or lower(p.nombresORazonSocial) like lower(concat('%', cast(:texto as string), '%'))
                   or lower(a.codigoAgente) like lower(concat('%', cast(:texto as string), '%')))
            """;

    /**
     * Los agentes que <b>ya</b> podrian recibir una propiedad de este actor,
     * por nombre de persona ascendente.
     *
     * <p>{@code excluir} saca al responsable actual: ofrecerlo seria ofrecer un
     * traspaso que el POST rechaza con "ese agente ya responde por esta
     * propiedad". Sin responsable se pasa {@code -1}, que no es ningun id.
     */
    @Query(value = "select a " + DESDE_CANDIDATO_CON_FICHA + CONDICION_CANDIDATO
            + " order by p.nombresORazonSocial asc, a.id asc",
            countQuery = "select count(a) " + DESDE_CANDIDATO + CONDICION_CANDIDATO)
    Page<DetalleAgente> candidatosAResponsable(@Param("idOrganizacion") long idOrganizacion,
                                               @Param("sinSupervision") boolean sinSupervision,
                                               @Param("idRolBroker") long idRolBroker,
                                               @Param("excluir") long excluir,
                                               @Param("texto") String texto,
                                               Pageable pageable);

    /**
     * La <b>misma</b> pregunta sobre UN agente concreto, para revalidar el POST.
     *
     * <p>Comparte la cadena {@link #CONDICION_CANDIDATO} con la lista de arriba
     * a proposito: la lista no puede ofrecer a nadie que esta consulta rechace,
     * porque las dos preguntan literalmente lo mismo.
     */
    @Query("select count(a) > 0 " + DESDE_CANDIDATO + CONDICION_CANDIDATO
            + " and a.id = :idRolAgente")
    boolean esCandidatoAResponsable(@Param("idOrganizacion") long idOrganizacion,
                                    @Param("sinSupervision") boolean sinSupervision,
                                    @Param("idRolBroker") long idRolBroker,
                                    @Param("excluir") long excluir,
                                    @Param("texto") String texto,
                                    @Param("idRolAgente") long idRolAgente);

    @Query(FICHA + """
            where a.organizacionId = :idOrganizacion
              and a.id = :id
            """)
    Optional<DetalleAgente> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                        @Param("id") long id);

    // ------------------------------------------------------------------
    // D-P0-13: el punto UNICO de serializacion del gobierno de un agente
    // ------------------------------------------------------------------

    /**
     * <b>Toma la fila del agente para que su elegibilidad no cambie a mitad de
     * un traspaso</b> (D-P0-13, 2026-09-01).
     *
     * <h2>Que defecto cierra</h2>
     * La elegibilidad de D-P0-7 se <b>comprueba</b> y despues se <b>escribe</b>.
     * Entre las dos cosas cabe otra transaccion que desactive al destino —le
     * suspenda la cuenta, lo ponga de licencia o le cierre la supervision— y la
     * propiedad, o el encargo, acaba en manos de alguien que <b>ya no puede
     * recibirlo</b>. No es un fallo del predicado: el predicado dice la verdad
     * en el instante en que se pregunta, y el problema es que el instante pasa.
     *
     * <h2>Por que ESTA fila y no otra</h2>
     * Las cinco condiciones viven en cuatro tablas ({@code persona_rol},
     * {@code credencial_usuario}, {@code usuario_organizacion},
     * {@code detalle_agente}) mas {@code supervision_agente}. Bloquearlas todas
     * seria cinco candados y un orden que respetar; bloquear <b>una</b>
     * convenida basta si <b>todo</b> el que cambia la elegibilidad pasa por
     * ella. Se elige {@code detalle_agente} porque es la fila que <b>siempre</b>
     * existe para un agente y la que ya identifica el sujeto de la decision.
     *
     * <h2>Quien tiene que tomarlo, medido el 2026-09-01</h2>
     * <pre>
     *   LEE la elegibilidad y escribe   -&gt; ElegibilidadDeResponsable.exigirElegible
     *   CAMBIA credencial/disponibilidad-&gt; AgenteServiceImpl.actualizar
     *   CAMBIA la supervision           -&gt; AsignacionServiceImpl.reasignar
     * </pre>
     * Y nadie mas: {@code usuario_organizacion.estado} y
     * {@code persona_rol.vigencia_hasta} <b>no tienen escritor de servicio</b>
     * en esta fecha. <b>Si alguien anade uno, tiene que tomar este bloqueo
     * antes de escribir</b>, o la ventana vuelve a abrirse por la puerta nueva.
     *
     * <p><b>Lo que NO hace.</b> No sustituye a ninguna guarda: el que bloquea
     * sigue preguntando la elegibilidad entera despues. Y no ordena la historia
     * —si el traspaso entra primero y la baja despues, el resultado legitimo es
     * un agente desactivado que lleva propiedades (D-P0-8), no una reasignacion
     * automatica.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DetalleAgente a where a.organizacionId = :idOrganizacion and a.id = :id")
    Optional<DetalleAgente> bloquearParaGobierno(@Param("idOrganizacion") long idOrganizacion,
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
