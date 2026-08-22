package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.query.ExpedienteDeLaPropiedad;
import com.controllocal.persistence.query.CandidatoTarea;
import com.controllocal.persistence.query.ConteoPorAgente;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.query.IndicadorCaptacion;
import com.controllocal.persistence.query.PropiedadDeEquipo;
import com.controllocal.persistence.query.ResumenPropiedadesEquipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Consultas de captacion con el scope del actor en el WHERE (RC-001/RC-003).
 * {@code estado} filtra las bandejas del cable v1: pendientes ('P') y
 * reasignables ('A'); {@code q} busca por codigo, local o agente.
 *
 * <p>{@code idOrganizacion} es el primer filtro de TODAS las consultas (V6):
 * el scope por rol se aplica dentro del tenant, nunca al reves.
 */
public interface CaptacionRepository extends JpaRepository<Captacion, Long> {

    String DESDE = """
            from Captacion cap
              join cap.propiedad prop
              join cap.agente ag
              join ag.rol agr
              join agr.persona agp
            where cap.organizacionId = :idOrganizacion
              and (:sinScope = true or ag.id in :rolesAgente)
              and (:estado is null or cap.estado = :estado)
              and (:idAgente is null or ag.id = :idAgente)
              and (:q is null
                   or lower(cap.codigoCaptacion) like lower(concat('%', cast(:q as string), '%'))
                   or lower(prop.codigo) like lower(concat('%', cast(:q as string), '%'))
                   or lower(prop.direccion) like lower(concat('%', cast(:q as string), '%'))
                   or lower(prop.distrito) like lower(concat('%', cast(:q as string), '%'))
                   or lower(agp.nombresORazonSocial) like lower(concat('%', cast(:q as string), '%')))
            """;

    @Query(value = "select cap " + DESDE + " order by cap.id desc",
            countQuery = "select count(cap) " + DESDE)
    Page<Captacion> buscar(@Param("idOrganizacion") long idOrganizacion,
                           @Param("sinScope") boolean sinScope,
                           @Param("rolesAgente") Collection<Long> rolesAgente,
                           @Param("estado") String estado,
                           @Param("idAgente") Long idAgente,
                           @Param("q") String q,
                           Pageable pageable);

    String DESDE_PENDIENTES = """
            from Captacion cap
              join cap.propiedad prop
              join cap.agente ag
              join ag.rol agr
              join agr.persona agp
            where cap.organizacionId = :idOrganizacion
              and (:sinScope = true or ag.id in :rolesAgente)
              and cap.estado in ('P', 'O')
              and (:estado is null or cap.estado = :estado)
              and (:idAgente is null or ag.id = :idAgente)
              and (:q is null
                   or lower(cap.codigoCaptacion) like lower(concat('%', cast(:q as string), '%'))
                   or lower(prop.codigo) like lower(concat('%', cast(:q as string), '%'))
                   or lower(prop.direccion) like lower(concat('%', cast(:q as string), '%'))
                   or lower(prop.distrito) like lower(concat('%', cast(:q as string), '%'))
                   or lower(agp.nombresORazonSocial) like lower(concat('%', cast(:q as string), '%')))
            """;

    /** Bandeja de revision del cable v1: pendientes (P) u observadas (O) en el alcance. */
    @Query(value = "select cap " + DESDE_PENDIENTES + " order by cap.id desc",
            countQuery = "select count(cap) " + DESDE_PENDIENTES)
    Page<Captacion> pendientes(@Param("idOrganizacion") long idOrganizacion,
                               @Param("sinScope") boolean sinScope,
                               @Param("rolesAgente") Collection<Long> rolesAgente,
                               @Param("estado") String estado,
                               @Param("idAgente") Long idAgente,
                               @Param("q") String q,
                               Pageable pageable);

    // =====================================================================
    // Cartera del equipo: UNA fila por inmueble (extension aditiva).
    //
    // La pantalla del broker mira su cartera "por inmueble", no por captacion:
    // un local puede acumular varias (cerradas, rechazadas, vencidas) y solo
    // una ACTIVA a la vez. Deduplicar en el cliente exigiria descargar TODAS
    // las captaciones del equipo —que es justo lo que hacia el Blazor— porque
    // sobre una pagina no se puede deduplicar.
    //
    // DISTINCT ON es de PostgreSQL y no tiene equivalente en JPQL, asi que
    // estas dos consultas son nativas. La CTE se queda con la captacion mas
    // reciente de cada propiedad y el filtro/orden/paginado se aplican encima.
    // =====================================================================

    String CARTERA_EQUIPO_CTE = """
            with mas_reciente as (
                select distinct on (cap.id_propiedad)
                       cap.id_propiedad              as idPropiedad,
                       cap.id_captacion              as idCaptacion,
                       cap.codigo_captacion          as codigoCaptacion,
                       cap.estado                    as estado,
                       prop.codigo                   as codigoLocal,
                       prop.direccion                as direccion,
                       prop.distrito                 as distrito,
                       atr.valor_texto               as rubro,
                       prop.metraje                  as areaM2,
                       cap.id_rol_agente             as idAgente,
                       agp.nombres_o_razon_social    as agenteNombre
                  from captacion cap
                  join propiedad prop on prop.id_propiedad = cap.id_propiedad
                  left join atributo_propiedad atr on atr.id_propiedad = prop.id_propiedad
                                                  and atr.clave = 'rubro_permitido'
                  join detalle_agente ag on ag.id_persona_rol = cap.id_rol_agente
                  join persona_rol agr on agr.id_persona_rol = ag.id_persona_rol
                  join persona agp on agp.id_persona = agr.id_persona
                 where cap.organizacion_id = :idOrganizacion
                   and (:sinScope = true or cap.id_rol_agente in (:rolesAgente))
                 -- Mas reciente = la que vence mas tarde; sin fecha va al final
                 -- y el id desempata, para que el orden sea estable.
                 order by cap.id_propiedad, cap.fecha_fin_encargo desc nulls last,
                          cap.id_captacion desc
            )
            select """;

    // El SELECT va en medio, asi que la consulta se arma concatenando
    // constantes: un `@Query` solo admite expresiones constantes en
    // compilacion, y `String.formatted(...)` es una llamada a metodo.
    String CARTERA_EQUIPO_FILTRO = """
             from mas_reciente
             where (cast(:distrito as text) is null or distrito = cast(:distrito as text))
               and (cast(:texto as text) is null
                    or lower(direccion)       like lower('%' || cast(:texto as text) || '%')
                    or lower(distrito)        like lower('%' || cast(:texto as text) || '%')
                    or lower(codigoLocal)     like lower('%' || cast(:texto as text) || '%')
                    or lower(codigoCaptacion) like lower('%' || cast(:texto as text) || '%')
                    or lower(agenteNombre)    like lower('%' || cast(:texto as text) || '%'))
            """;

    @Query(value = CARTERA_EQUIPO_CTE + " * " + CARTERA_EQUIPO_FILTRO
            + " order by direccion, idPropiedad",
            countQuery = CARTERA_EQUIPO_CTE + " count(*) " + CARTERA_EQUIPO_FILTRO,
            nativeQuery = true)
    Page<PropiedadDeEquipo> carteraDelEquipo(@Param("idOrganizacion") long idOrganizacion,
                                             @Param("sinScope") boolean sinScope,
                                             @Param("rolesAgente") Collection<Long> rolesAgente,
                                             @Param("texto") String texto,
                                             @Param("distrito") String distrito,
                                             Pageable pageable);

    /**
     * Los cuatro KPI sobre el MISMO conjunto deduplicado, en una consulta.
     * Se cuentan inmuebles distintos, no captaciones: eso no se puede deducir
     * de una pagina descargada.
     */
    @Query(value = CARTERA_EQUIPO_CTE + """
                    count(*)                                                    as propiedades,
                    count(*) filter (where estado = 'A')                         as conCaptacionActiva,
                    count(distinct idAgente)                                     as agentesConCartera,
                    count(distinct distrito) filter (where distrito is not null) as distritos
            """ + CARTERA_EQUIPO_FILTRO,
            nativeQuery = true)
    ResumenPropiedadesEquipo resumenCarteraDelEquipo(@Param("idOrganizacion") long idOrganizacion,
                                                     @Param("sinScope") boolean sinScope,
                                                     @Param("rolesAgente") Collection<Long> rolesAgente,
                                                     @Param("texto") String texto,
                                                     @Param("distrito") String distrito);

    /** Distritos presentes en la cartera del equipo, para el filtro data-driven. */
    @Query(value = CARTERA_EQUIPO_CTE + " distinct distrito " + CARTERA_EQUIPO_FILTRO
            + " and distrito is not null order by distrito", nativeQuery = true)
    List<String> distritosDelEquipo(@Param("idOrganizacion") long idOrganizacion,
                                    @Param("sinScope") boolean sinScope,
                                    @Param("rolesAgente") Collection<Long> rolesAgente,
                                    @Param("texto") String texto,
                                    @Param("distrito") String distrito);

    String FICHA = """
            select cap from Captacion cap
              join fetch cap.propiedad prop
              left join fetch prop.rolPropietario rp
              left join fetch rp.persona
              join fetch cap.agente ag
              join fetch ag.rol agr
              join fetch agr.persona
              left join fetch cap.brokerRevisor
            """;

    @Query(FICHA + " where cap.organizacionId = :idOrganizacion and cap.id = :id")
    Optional<Captacion> buscarFicha(@Param("idOrganizacion") long idOrganizacion, @Param("id") long id);

    /** El codigo solo es unico DENTRO de la organizacion (V6.3), asi que el tenant es parte de la clave de busqueda. */
    @Query(FICHA + " where cap.organizacionId = :idOrganizacion and cap.codigoCaptacion = :codigo")
    Optional<Captacion> buscarFichaPorCodigo(@Param("idOrganizacion") long idOrganizacion,
                                             @Param("codigo") String codigo);

    /** Variante sin distinguir mayusculas: /captaciones/{idOrCodigo}/coincidencias acepta "cap-0001". */
    @Query(FICHA + " where cap.organizacionId = :idOrganizacion"
            + " and lower(cap.codigoCaptacion) = lower(:codigo)")
    Optional<Captacion> buscarFichaPorCodigoIgnorandoMayusculas(
            @Param("idOrganizacion") long idOrganizacion, @Param("codigo") String codigo);

    /** Invariante v1 (uq_captacion_activa_por_local): una sola ACTIVA por local. */
    boolean existsByOrganizacionIdAndPropiedadIdAndEstado(Long idOrganizacion, Long idPropiedad, String estado);

    /** Regla "mis locales"/edicion del agente: captacion suya sobre el local, no cerrada. */
    boolean existsByOrganizacionIdAndPropiedadIdAndAgenteIdAndEstadoNot(Long idOrganizacion, Long idPropiedad,
                                                                        Long idRolAgente, String estado);

    /** Correlativo CAP-#### por organizacion: cada corredora numera desde 0001 (V6.3). */
    long countByOrganizacionId(long idOrganizacion);

    /**
     * Los encargos VIVOS de una propiedad (D-E4-1).
     *
     * <p>"Vivo" es {@code P}, {@code O} o {@code A} — la misma definicion que
     * usa el indice {@code uq_captacion_viva_por_operacion} de V50, y por eso
     * esta consulta puede devolver como mucho DOS filas: una de venta y otra de
     * alquiler. Dos de la misma operacion las impide la base.
     *
     * <p>Es la consulta que responde "¿de que operacion es este importe?"
     * cuando el productor no lo dice. Si devuelve una, la respuesta es esa; si
     * devuelve dos, la pregunta sigue abierta y hay que declararla; si devuelve
     * cero, no hay encargo del que deducirla.
     */
    @Query("""
            select c from Captacion c
            where c.organizacionId = :idOrganizacion
              and c.propiedad.id = :idPropiedad
              and c.estado in ('P', 'O', 'A')
            order by c.motivoOperacion asc
            """)
    List<Captacion> encargosVivosDe(@Param("idOrganizacion") long idOrganizacion,
                                    @Param("idPropiedad") long idPropiedad);

    /**
     * <b>Los encargos vivos de UNA PAGINA de propiedades, de una vez.</b>
     *
     * <p>Es la segunda mitad del listado universal: la primera resuelve que
     * propiedades entran, y esta les cuelga sus encargos. Dos consultas por
     * pagina en vez de una por fila -- y con dos operaciones vivas por
     * propiedad, el N+1 seria del doble.
     *
     * <p>La lista de ids nunca pasa del tamano de pagina, asi que el {@code in}
     * es un acceso por clave y no un barrido.
     */
    @Query("""
            select c from Captacion c
              left join fetch c.condicionEconomica
            where c.organizacionId = :idOrganizacion
              and c.propiedad.id in :idsPropiedades
              and c.estado in ('P', 'O', 'A')
            order by c.propiedad.id, c.motivoOperacion
            """)
    List<Captacion> encargosVivosDe(@Param("idOrganizacion") long idOrganizacion,
                                    @Param("idsPropiedades") Collection<Long> idsPropiedades);

    /**
     * <b>TODOS los encargos de una propiedad, vivos y cerrados.</b>
     *
     * <p>Es la consulta de la FICHA, y se separa de {@link #encargosVivosDe} a
     * proposito. Un listado ensena lo que esta vivo, porque su pregunta es «que
     * hay en cartera». Una ficha tiene otra: «que ha pasado con esta
     * propiedad», y ahi un encargo cerrado no es ruido -- <b>es el unico sitio
     * donde vive su historico economico</b>.
     *
     * <p>Una propiedad con alquiler 2024 cerrado, alquiler 2025 cerrado y
     * alquiler 2026 vigente tiene tres encargos y tres series de precios. Con
     * el filtro de vivos, la ficha ensenaria uno y las otras dos series
     * desaparecerian de la vista sin decir que existen. V50 prohibe dos
     * <b>vivos</b> de la misma operacion; nunca prohibio que hubiera varios.
     */
    @Query("""
            select c from Captacion c
              left join fetch c.condicionEconomica
              left join fetch c.agente ag
              left join fetch ag.rol agRol
              left join fetch agRol.persona
            where c.organizacionId = :idOrganizacion
              and c.propiedad.id = :idPropiedad
            order by c.fechaCaptacion desc, c.id desc
            """)
    List<Captacion> encargosDe(@Param("idOrganizacion") long idOrganizacion,
                               @Param("idPropiedad") long idPropiedad);

    /** El encargo vivo de UNA operacion concreta. Como mucho hay uno (V50). */
    @Query("""
            select c from Captacion c
            where c.organizacionId = :idOrganizacion
              and c.propiedad.id = :idPropiedad
              and c.motivoOperacion = :operacion
              and c.estado in ('P', 'O', 'A')
            """)
    Optional<Captacion> encargoVivoDe(@Param("idOrganizacion") long idOrganizacion,
                                      @Param("idPropiedad") long idPropiedad,
                                      @Param("operacion") String operacion);

    /**
     * Oferta candidata del matching de cartera (§7): las captaciones ACTIVAS
     * del alcance, con la propiedad y su detalle ya cargados porque el scoring
     * los lee criterio a criterio. No se pagina: el cruce se hace completo y
     * la paginacion cae sobre las coincidencias resultantes.
     */
    @Query("""
            select distinct cap from Captacion cap
              join fetch cap.propiedad prop
              join fetch cap.agente ag
            where cap.organizacionId = :idOrganizacion
              and (:sinScope = true or ag.id in :rolesAgente)
              and cap.estado = 'A'
            order by cap.id desc
            """)
    List<Captacion> activasEnAlcance(@Param("idOrganizacion") long idOrganizacion,
                                     @Param("sinScope") boolean sinScope,
                                     @Param("rolesAgente") Collection<Long> rolesAgente);

    /** mis-locales del cable v1: locales con captacion del agente en estado != 'C'. */
    @Query(value = """
            select distinct prop from Captacion cap join cap.propiedad prop
            where cap.organizacionId = :idOrganizacion
              and cap.agente.id = :idRolAgente and cap.estado <> 'C'
            order by prop.id desc
            """,
            countQuery = """
            select count(distinct prop) from Captacion cap join cap.propiedad prop
            where cap.organizacionId = :idOrganizacion
              and cap.agente.id = :idRolAgente and cap.estado <> 'C'
            """)
    Page<Propiedad> localesDelAgente(@Param("idOrganizacion") long idOrganizacion,
                                     @Param("idRolAgente") long idRolAgente, Pageable pageable);

    /**
     * Disparador 6 de la bandeja (F7): captaciones ACTIVAS del agente. La
     * cadencia del reporte al propietario se decide fuera, cruzando esto con
     * el ultimo reporte de cada una; {@code fechaPlazo} es la fecha de
     * captacion, que es el punto de partida del reloj cuando no hay ningun
     * reporte todavia.
     */
    @Query("""
            select cap.id as entidadId, cap.codigoCaptacion as entidadCodigo,
                   cap.fechaCaptacion as fechaPlazo, cast(null as string) as marca
            from Captacion cap
            where cap.organizacionId = :idOrganizacion
              and cap.agente.id = :idRolAgente
              and cap.estado = 'A'
            """)
    List<CandidatoTarea> activasDelAgente(@Param("idOrganizacion") long idOrganizacion,
                                          @Param("idRolAgente") long idRolAgente);

    /**
     * Disparador 7 (matching de cartera): captaciones ACTIVAS del agente cuyo
     * local sigue DISPONIBLE, con la propiedad ya resuelta —{@code evaluar()}
     * la necesita entera—.
     */
    @Query("""
            select cap from Captacion cap
              join fetch cap.propiedad prop
            where cap.organizacionId = :idOrganizacion
              and cap.agente.id = :idRolAgente
              and cap.estado = 'A'
              and prop.estadoRegistro = 'A'
              and prop.disponibilidadComercial = 'D'
            """)
    List<Captacion> activasConLocalDisponible(@Param("idOrganizacion") long idOrganizacion,
                                              @Param("idRolAgente") long idRolAgente);

    /**
     * Reparto por estado de las captaciones de UN agente, para su ficha.
     *
     * <p>A diferencia de {@code contarEnCarteraPorAgentes}, que solo cuenta las
     * de la cartera viva ({@code P,O,A}), aquí entran <b>todas</b>: la ficha
     * muestra la trayectoria del agente, y una captación cerrada o rechazada
     * también es trabajo suyo.
     */
    @Query("""
            select cap.estado as estado, count(cap) as total
            from Captacion cap
            where cap.organizacionId = :idOrganizacion
              and cap.agente.id = :idRolAgente
            group by cap.estado
            """)
    List<ConteoPorEstado> contarPorEstadoDeAgente(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idRolAgente") long idRolAgente);

    /** Contadores autoritativos del catalogo /agentes: PENDIENTE, OBSERVADA o ACTIVA. */
    @Query("""
            select cap.agente.id as idAgente, count(cap) as total
            from Captacion cap
            where cap.organizacionId = :idOrganizacion
              and cap.agente.id in :idsAgente
              and cap.estado in ('P', 'O', 'A')
            group by cap.agente.id
            """)
    List<ConteoPorAgente> contarEnCarteraPorAgentes(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idsAgente") Collection<Long> idsAgente);

    /** Base transversal E3: captaciones de los locales del propietario. */
    @Query(FICHA + """
             where cap.organizacionId = :idOrganizacion
               and prop.rolPropietario.id = :idPropietario
             order by cap.id
            """)
    List<Captacion> listarFichaPorPropietario(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idPropietario") long idPropietario);

    /**
     * Base de captaciones de los indicadores E4: TODAS las del alcance, sin
     * ventana —el donut y los pills del menu no dependen del periodo— y sin el
     * grafo de la entidad (D-E4-2). El alcance de indicadores es solo por
     * agente responsable; la captacion no amplia el de nadie.
     */
    @Query("""
            select cap.id as id, cap.agente.id as idAgente, cap.estado as estado,
                   cap.fechaCaptacion as fechaCaptacion, prop.id as idPropiedad,
                   cap.codigoCaptacion as codigo, prop.direccion as direccion,
                   prop.distrito as distrito
            from Captacion cap join cap.propiedad prop
            where cap.organizacionId = :idOrganizacion
              and (:sinScope = true or cap.agente.id in :rolesAgente)
            """)
    List<IndicadorCaptacion> indicadores(@Param("idOrganizacion") long idOrganizacion,
                                         @Param("sinScope") boolean sinScope,
                                         @Param("rolesAgente") Collection<Long> rolesAgente);

    /**
     * Fuente de captaciones del seguimiento comercial: TODO el tenant, sin
     * scope de rol. Es deliberado y del cable — la v1 arma con esta lista el
     * mapa {@code id_local → propietario} que enriquece las filas de
     * oportunidad, solicitud y cierre, que cargan el local sin propietario. El
     * filtro por rol se aplica despues, fila por fila.
     */
    @Query(FICHA + " where cap.organizacionId = :idOrganizacion")
    List<Captacion> listarSeguimiento(@Param("idOrganizacion") long idOrganizacion);

    /**
     * <b>Los cuatro renglones del expediente comercial, por lote</b> (E2.4).
     *
     * <p>Los datos existian repartidos en cinco sitios y lo que faltaba era la
     * vista que los junta. Esta es esa vista: una consulta para toda la pagina de
     * asuntos, no cuatro por asunto.
     *
     * <p>Las visitas se cuentan con subconsulta y no con {@code join} + 
     * {@code group by} a proposito: un join multiplicaria la fila de la captacion
     * por cada visita y el resto de los renglones habria que agruparlos tambien,
     * que es como una consulta de expediente acaba devolviendo rentas sumadas.
     *
     * <p>{@code rentaDesde} sale del ultimo hito del historico economico (E0), que
     * es lo que permite decir «sin cambios desde junio» en vez de repetir el
     * numero que ya esta arriba.
     */
    @Query("""
            select p.id as idPropiedad,
                   c.fechaInicioEncargo as inicioVigencia,
                   c.fechaFinEncargo as finVigencia,
                   c.fechaCaptacion as fechaCaptacion,
                   p.precioReferencial as renta,
                   p.monedaReferencial as moneda,
                   (select max(pr.fecha) from PrecioPropiedad pr
                     where pr.idPropiedad = p.id) as rentaDesde,
                   (select count(v) from Visita v join v.oportunidad o
                     where o.captacion = c and v.estado = 'R') as visitasRealizadas,
                   (select count(v2) from Visita v2 join v2.oportunidad o2
                     where o2.captacion = c) as visitasTotales,
                   per.nombresORazonSocial as propietario,
                   p.direccion as direccion,
                   p.distrito as distrito,
                   p.metraje as metraje
              from Captacion c
              join c.propiedad p
              left join p.rolPropietario rp
              left join rp.persona per
             where c.organizacionId = :idOrganizacion
               and p.id in :idsPropiedad
             order by p.id, c.id desc
            """)
    List<ExpedienteDeLaPropiedad> expedientesDe(@Param("idOrganizacion") long idOrganizacion,
                                                @Param("idsPropiedad") Collection<Long> idsPropiedad);

    /**
     * <b>De que propiedad habla cada asunto</b> (E2.4).
     *
     * <p>Un asunto de la bandeja cuelga de seis entidades distintas y todas
     * acaban -- por caminos distintos -- en la misma propiedad. Sin esto habria
     * que preguntarlo seis veces, una por tipo, o resolverlo asunto a asunto.
     *
     * <p>Es SQL nativo con UNION y no JPQL porque cada rama parte de una tabla
     * distinta: JPQL no sabe unir seis consultas sin entidad comun, y forzarlo
     * con seis metodos separados devolveria el N+1 por la puerta del repositorio.
     *
     * <p>PROSPECCION no esta: una prospeccion es anterior a la propiedad -- se
     * prospecta a un propietario, no a un local -- y darle un expediente de
     * inmueble seria inventarle uno.
     *
     * @return filas {@code (entidad_tipo, entidad_id, id_propiedad)}
     */
    @Query(nativeQuery = true, value = """
            select 'CAPTACION' as entidad_tipo, c.id_captacion as entidad_id,
                   c.id_propiedad as id_propiedad
              from captacion c
             where c.organizacion_id = :idOrganizacion
            union all
            select 'INMUEBLE', p.id_propiedad, p.id_propiedad
              from propiedad p
             where p.organizacion_id = :idOrganizacion
            union all
            select 'VISITA', v.id_visita, c.id_propiedad
              from visita v
              join oportunidad_comercial o on o.id_oportunidad = v.id_oportunidad
              join captacion c on c.id_captacion = o.id_captacion
             where v.organizacion_id = :idOrganizacion
            union all
            select 'SOLICITUD_ALQUILER', s.id_solicitud, c.id_propiedad
              from solicitud_alquiler s
              join oportunidad_comercial o on o.id_oportunidad = s.id_oportunidad
              join captacion c on c.id_captacion = o.id_captacion
             where s.organizacion_id = :idOrganizacion
            union all
            select 'CONTRATO_ALQUILER', k.id_contrato_alquiler, c.id_propiedad
              from contrato_alquiler k
              join oportunidad_comercial o on o.id_oportunidad = k.id_oportunidad
              join captacion c on c.id_captacion = o.id_captacion
             where k.organizacion_id = :idOrganizacion
            """)
    List<Object[]> propiedadPorAsunto(@Param("idOrganizacion") long idOrganizacion);

    /**
     * <b>Captaciones esperando la decision del broker</b> (D-E2-5, E2.5).
     *
     * <p>Las pendientes (P) y las observadas (O) de su alcance: aprobar u
     * observar una captacion es suyo y de nadie mas, y hasta que lo haga el
     * local no se puede ofrecer.
     *
     * <p>{@code fechaPlazo} es la fecha de captacion, que es desde cuando lleva
     * esperando: la politica de despacho la pesa como antiguedad accionable.
     */
    @Query("""
            select c.id as entidadId,
                   c.codigoCaptacion as entidadCodigo,
                   c.fechaCaptacion as fechaPlazo,
                   c.estado as marca
              from Captacion c
             where c.organizacionId = :idOrganizacion
               and c.estado in ('P', 'O')
               and (:sinScope = true or c.agente.id in :roles)
             order by c.fechaCaptacion asc, c.id asc
            """)
    List<CandidatoTarea> porRevisarDelBroker(@Param("idOrganizacion") long idOrganizacion,
                                             @Param("sinScope") boolean sinScope,
                                             @Param("roles") List<Long> roles);

    /**
     * <b>Comisiones asignadas que nadie ha cobrado</b> (D-E2-5, E2.5).
     *
     * <p>Registrar el cobro es BROKER en la matriz operacion-rol. En E2.2 este
     * mismo hecho aparecia en la bandeja del AGENTE con {@code dependeDeMi =
     * false}, porque el agente no puede cobrarlo: un asunto sin dueno, que al
     * agente no le servia y al broker no le llegaba. Aqui encuentra el suyo.
     *
     * <p>Se apoya en la captacion para el alcance -- el broker supervisa por
     * captacion -- y no en el agente de la solicitud, que es otro camino y otro
     * conjunto.
     */
    @Query("""
            select k.id as entidadId,
                   cast(k.id as string) as entidadCodigo,
                   k.fechaCierre as fechaPlazo,
                   k.estadoContrato as marca
              from ContratoAlquiler k
              join k.oportunidad o
              join o.captacion c
             where k.organizacionId = :idOrganizacion
               and (:sinScope = true or c.agente.id in :roles)
               and exists (select 1 from ComisionLiquidacion cl
                            where cl.contrato = k and cl.estado = 'P')
             order by k.fechaCierre asc, k.id asc
            """)
    List<CandidatoTarea> comisionesSinCobrarDelBroker(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("sinScope") boolean sinScope,
            @Param("roles") List<Long> roles);
}
