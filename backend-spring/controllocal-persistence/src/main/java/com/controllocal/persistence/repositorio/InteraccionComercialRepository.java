package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.InteraccionComercial;
import com.controllocal.persistence.query.IndicadorInteraccion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Bitacora polimorfica. A diferencia de oportunidades y visitas, aqui el
 * alcance del BROKER es **por agente supervisado** (el responsable de la
 * interaccion), no por captacion: son dos reglas distintas del cable real y
 * no deben unificarse (contrato F3 §6).
 *
 * <p>La v1 filtraba y ordenaba en memoria; aqui todo baja al WHERE, incluida
 * la busqueda de texto sobre codigo de prospeccion/captacion, nombre de
 * cliente/agente y observaciones.
 */
public interface InteraccionComercialRepository extends JpaRepository<InteraccionComercial, Long> {

    /**
     * Igual que en oportunidades: las personas del agente y del cliente, la
     * prospeccion y la captacion se unian solo para el {@code LIKE} del texto.
     * Con el texto fuera, el listado no necesita ninguna de ellas y los cuatro
     * filtros por id se resuelven contra la FK de la propia interaccion.
     */
    String DESDE = """
            from InteraccionComercial i
              join i.agente ag
            where i.organizacionId = :idOrganizacion
              and (:sinScope = true or ag.id in :roles)
              and (:contexto is null or i.contexto = :contexto)
              and (:idOportunidad is null or i.oportunidad.id = :idOportunidad)
              and (:idProspeccion is null or i.prospeccion.id = :idProspeccion)
              and (:idCaptacion is null or i.captacion.id = :idCaptacion)
              and (:idCliente is null or i.cliente.id = :idCliente)
              and (:soloPropietario is null
                   or (:soloPropietario = true  and i.contexto in ('PROSPECCION', 'CAPTACION'))
                   or (:soloPropietario = false and i.contexto not in ('PROSPECCION', 'CAPTACION')))
              and (:resultado is null or i.resultado = :resultado)
              and (:canal is null or i.canalContacto = :canal)
            """;

    /**
     * Orden del cable: lo mas reciente primero (fecha_hora desc, id desc).
     *
     * <p>SIN texto libre: el texto de esta bandeja mira cinco campos en cuatro
     * tablas y tiene su propio camino por conjunto de candidatos
     * ({@link #idsPorTexto}), como exige la §5 del contrato de listados.
     */
    @Query(value = "select i " + DESDE + " order by i.fechaHora desc, i.id desc",
            countQuery = "select count(i) " + DESDE)
    Page<InteraccionComercial> buscar(@Param("idOrganizacion") long idOrganizacion,
                                      @Param("sinScope") boolean sinScope,
                                      @Param("roles") Collection<Long> roles,
                                      @Param("contexto") String contexto,
                                      @Param("idOportunidad") Long idOportunidad,
                                      @Param("idProspeccion") Long idProspeccion,
                                      @Param("idCaptacion") Long idCaptacion,
                                      @Param("idCliente") Long idCliente,
                                      @Param("soloPropietario") Boolean soloPropietario,
                                      @Param("resultado") String resultado,
                                      @Param("canal") String canal,
                                      Pageable pageable);

    // ------------------------------------------------------------------
    // Busqueda por CONJUNTO DE CANDIDATOS (§5 del contrato de listados).
    //
    // Cinco campos buscables en CUATRO tablas: prospeccion (codigo), captacion
    // (codigo), persona del cliente, persona del agente y las observaciones de
    // la propia interaccion. Un OR entre todas ellas degenera en Seq Scan y el
    // conteo lo paga en cada peticion; una rama por tabla deja que cada indice
    // trigrama entre.
    // ------------------------------------------------------------------

    String PATRON = " like lower(concat('%', cast(:texto as varchar), '%'))";

    /** Alcance (por AGENTE RESPONSABLE) + filtros activos, en todas las ramas. */
    String COMUN = """
             where i.organizacion_id = :idOrganizacion
               and (:sinScope = true or i.id_rol_agente = any(cast(:roles as bigint[])))
               and (cast(:contexto as varchar) is null or i.contexto = cast(:contexto as varchar))
               and (cast(:idOportunidad as bigint) is null
                    or i.id_oportunidad = cast(:idOportunidad as bigint))
               and (cast(:idProspeccion as bigint) is null
                    or i.id_prospeccion = cast(:idProspeccion as bigint))
               and (cast(:idCaptacion as bigint) is null
                    or i.id_captacion = cast(:idCaptacion as bigint))
               and (cast(:idCliente as bigint) is null
                    or i.id_rol_cliente = cast(:idCliente as bigint))
               and (cast(:soloPropietario as boolean) is null
                    or (cast(:soloPropietario as boolean) = true
                        and i.contexto in ('PROSPECCION', 'CAPTACION'))
                    or (cast(:soloPropietario as boolean) = false
                        and i.contexto not in ('PROSPECCION', 'CAPTACION')))
               and (cast(:resultado as varchar) is null or i.resultado = cast(:resultado as varchar))
               and (cast(:canal as varchar) is null or i.canal_contacto = cast(:canal as varchar))
            """;

    String RAMAS_TEXTO = """
            select i.id_interaccion as id
              from interaccion_comercial i
            """ + COMUN + """
               and lower(i.observaciones)""" + PATRON + """

            union
            select i.id_interaccion as id
              from interaccion_comercial i
              join prospeccion pro on pro.id_prospeccion = i.id_prospeccion
            """ + COMUN + """
               and lower(pro.codigo_prospeccion)""" + PATRON + """

            union
            select i.id_interaccion as id
              from interaccion_comercial i
              join captacion cap on cap.id_captacion = i.id_captacion
            """ + COMUN + """
               and lower(cap.codigo_captacion)""" + PATRON + """

            union
            select i.id_interaccion as id
              from interaccion_comercial i
              join persona_rol prCli on prCli.id_persona_rol = i.id_rol_cliente
              join persona perCli on perCli.id_persona = prCli.id_persona
            """ + COMUN + """
               and lower(perCli.nombres_o_razon_social)""" + PATRON + """

            union
            select i.id_interaccion as id
              from interaccion_comercial i
              join persona_rol prAg on prAg.id_persona_rol = i.id_rol_agente
              join persona perAg on perAg.id_persona = prAg.id_persona
            """ + COMUN + """
               and lower(perAg.nombres_o_razon_social)""" + PATRON + """

            """;

    /** Ids de la pagina, ordenados y recortados EN LA BASE. */
    @Query(value = "select c.id from (" + RAMAS_TEXTO + ") c"
            + " join interaccion_comercial i2 on i2.id_interaccion = c.id"
            + " order by i2.fecha_hora desc, c.id desc limit :limite offset :desplazamiento",
            nativeQuery = true)
    List<Long> idsPorTexto(@Param("idOrganizacion") long idOrganizacion,
                           @Param("sinScope") boolean sinScope,
                           @Param("roles") String roles,
                           @Param("contexto") String contexto,
                           @Param("idOportunidad") Long idOportunidad,
                           @Param("idProspeccion") Long idProspeccion,
                           @Param("idCaptacion") Long idCaptacion,
                           @Param("idCliente") Long idCliente,
                           @Param("soloPropietario") Boolean soloPropietario,
                           @Param("resultado") String resultado,
                           @Param("canal") String canal,
                           @Param("texto") String texto,
                           @Param("limite") int limite,
                           @Param("desplazamiento") int desplazamiento);

    /** Total del MISMO conjunto que pagina {@link #idsPorTexto}. */
    @Query(value = "select count(*) from (" + RAMAS_TEXTO + ") c", nativeQuery = true)
    long contarPorTexto(@Param("idOrganizacion") long idOrganizacion,
                        @Param("sinScope") boolean sinScope,
                        @Param("roles") String roles,
                        @Param("contexto") String contexto,
                        @Param("idOportunidad") Long idOportunidad,
                        @Param("idProspeccion") Long idProspeccion,
                        @Param("idCaptacion") Long idCaptacion,
                        @Param("idCliente") Long idCliente,
                        @Param("soloPropietario") Boolean soloPropietario,
                        @Param("resultado") String resultado,
                        @Param("canal") String canal,
                        @Param("texto") String texto);

    /** Proyeccion completa de la pagina ya resuelta: acceso por clave. */
    @Query("""
            select i from InteraccionComercial i
              join fetch i.agente ag
              join fetch ag.rol agRol
              join fetch agRol.persona
              left join fetch i.oportunidad
              left join fetch i.prospeccion
              left join fetch i.captacion
              left join fetch i.cliente cli
            where i.organizacionId = :idOrganizacion and i.id in :ids
            order by i.fechaHora desc, i.id desc
            """)
    List<InteraccionComercial> buscarFichaPorIds(@Param("idOrganizacion") long idOrganizacion,
                                                 @Param("ids") Collection<Long> ids);

    /**
     * La bitacora de UNOS ENCARGOS concretos: actividad de la ficha universal.
     *
     * <p>Son <b>dos ramas</b>, y las dos cuentan: la conversacion con el
     * propietario cuelga del encargo (contexto {@code CAPTACION}) y la
     * conversacion con el interesado cuelga de la oportunidad (contexto
     * {@code OPORTUNIDAD}), que a su vez cuelga del encargo. Quedarse solo con
     * la primera dejaria la ficha contando la mitad de la historia.
     *
     * <p>La oportunidad se une con {@code left join} porque una interaccion de
     * captacion no la tiene, y con un join interno esas filas desaparecerian
     * -- que es exactamente la rama del propietario.
     */
    @Query("""
            select i from InteraccionComercial i
              join fetch i.agente ag
              join fetch ag.rol agRol
              join fetch agRol.persona
              left join fetch i.cliente cli
              left join fetch cli.rol cliRol
              left join fetch cliRol.persona
              left join fetch i.oportunidad opo
              left join opo.captacion opoCap
            where i.organizacionId = :idOrganizacion
              and (i.captacion.id in :idsEncargos or opoCap.id in :idsEncargos)
            order by i.fechaHora desc, i.id desc
            """)
    List<InteraccionComercial> listarFichaPorEncargos(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idsEncargos") Collection<Long> idsEncargos);

    @Query("""
            select i from InteraccionComercial i
              join fetch i.agente ag
              join fetch ag.rol agRol
              join fetch agRol.persona
              left join fetch i.oportunidad o
              left join fetch i.prospeccion
              left join fetch i.captacion
              left join fetch i.cliente cli
            where i.organizacionId = :idOrganizacion and i.id = :id
            """)
    Optional<InteraccionComercial> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                               @Param("id") long id);

    /**
     * Consultas del reporte al propietario: bitacora directa de la captacion
     * mas bitacora de sus oportunidades, deduplicada por id. Los limites son
     * [desde, hastaExclusiva), equivalente al rango LocalDate inclusivo v1.
     */
    @Query("""
            select count(distinct i.id) from InteraccionComercial i
              left join i.oportunidad o
              left join i.captacion cap
            where i.organizacionId = :idOrganizacion
              and (cap.id = :idCaptacion or o.captacion.id = :idCaptacion)
              and i.fechaHora >= :desde
              and i.fechaHora < :hastaExclusiva
            """)
    long contarParaReporte(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idCaptacion") long idCaptacion,
            @Param("desde") OffsetDateTime desde,
            @Param("hastaExclusiva") OffsetDateTime hastaExclusiva);

    /** Interacciones directas del cliente y las ligadas a sus oportunidades. */
    @Query("""
            select i from InteraccionComercial i
              left join i.cliente cli
              left join i.oportunidad op
            where i.organizacionId = :idOrganizacion
              and (cli.id = :idCliente or op.cliente.id = :idCliente)
            order by i.id
            """)
    List<InteraccionComercial> listarFichaPorCliente(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idCliente") long idCliente);

    /**
     * Base de interacciones de los indicadores E4. Al ser polimorfica, la
     * captacion llega por dos caminos y las dos columnas viajan: el avance
     * cuenta la interaccion si CUALQUIERA de las dos apunta a la captacion.
     */
    @Query("""
            select i.id as id, i.agente.id as idAgente, i.fechaHora as fechaHora,
                   i.captacion.id as idCaptacion, op.id as idOportunidad
            from InteraccionComercial i left join i.oportunidad op
            where i.organizacionId = :idOrganizacion
              and (:sinScope = true or i.agente.id in :roles)
            """)
    List<IndicadorInteraccion> indicadores(@Param("idOrganizacion") long idOrganizacion,
                                           @Param("sinScope") boolean sinScope,
                                           @Param("roles") Collection<Long> roles);
}
