package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.query.ConteoPorPropietario;
import com.controllocal.persistence.query.LocalListado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PropiedadRepository extends JpaRepository<Propiedad, Long> {

    /**
     * Ficha completa en una sola consulta (propietario con su persona +
     * detalle de local): evita el N+1 de las asociaciones LAZY al mapear
     * la respuesta del cable.
     */
    @Query("""
            select p from Propiedad p
            join fetch p.rolPropietario rp
            join fetch rp.persona
            left join fetch p.detalleLocal
            where p.organizacionId = :idOrganizacion and p.id = :id
            """)
    Optional<Propiedad> buscarFicha(@Param("idOrganizacion") long idOrganizacion, @Param("id") long id);

    /**
     * Filtro del listado, compartido por la pagina y por el conteo del resumen
     * para que <b>los KPI no puedan discrepar de la lista</b>: si el WHERE se
     * escribiera dos veces, cualquier retoque en uno dejaria contadores que no
     * cuadran con las filas.
     *
     * <p>El orden de los predicados es el de siempre: <b>tenant primero</b>
     * (V6: el ADMIN es global dentro de SU corredora), despues los filtros.
     * {@code /locales} no lleva alcance por rol —la cartera es de toda la
     * organizacion, ver la matriz operacion-rol—, por eso aqui no hay
     * {@code sinScope}/{@code rolesAgente} como en prospecciones.
     *
     * <p>{@code cast(:texto as string)} es obligatorio en Postgres: sin el,
     * Hibernate manda el parametro como {@code bytea} y el {@code like} falla.
     */
    String ASOCIACIONES_LISTADO = """
            from Propiedad p
              join p.rolPropietario rp
              join rp.persona per
            """;

    String FILTRO_TEXTO_LISTADO = """
              and (:texto is null
                   or lower(p.codigo)    like lower(concat('%', cast(:texto as string), '%'))
                   or lower(p.direccion) like lower(concat('%', cast(:texto as string), '%'))
                   or lower(p.distrito)  like lower(concat('%', cast(:texto as string), '%'))
                   or lower(per.nombresORazonSocial) like lower(concat('%', cast(:texto as string), '%')))
            """;

    /** Adaptador JPQL del contrato legado D/N/I; no existe como columna. */
    String ESTADO_LEGADO = " case "
            + "when p.estadoRegistro = 'I' then 'I' "
            + "when p.disponibilidadComercial = 'D' then 'D' "
            + "else 'N' end ";

    String FILTRO_ESTADO_LEGADO = """
              and (:estado is null
                   or (:estado = 'I' and p.estadoRegistro = 'I')
                   or (:estado = 'D' and p.estadoRegistro = 'A' and p.disponibilidadComercial = 'D')
                   or (:estado = 'N' and p.estadoRegistro = 'A' and p.disponibilidadComercial <> 'D'))
            """;

    String DESDE = ASOCIACIONES_LISTADO + """
            where p.organizacionId = :idOrganizacion
            """ + FILTRO_ESTADO_LEGADO + FILTRO_TEXTO_LISTADO;

    /**
     * Pagina filtrada de la organizacion, ordenada por id (paridad v1).
     *
     * <p>Ordena por la CLAVE PRIMARIA, que es unica: el orden es total y
     * estable, asi que dos peticiones de la misma pagina devuelven las mismas
     * filas y ninguna fila se cuela o se pierde entre paginas aunque se
     * inserten locales entre consulta y consulta.
     *
     * <p>La consulta devuelve una proyeccion, no entidades administradas:
     * propietario y detalle se resuelven en el mismo SELECT sin inicializar
     * asociaciones LAZY una por una. La paginacion se resuelve en SQL con
     * LIMIT/OFFSET y el {@code countQuery} solo cuenta.
     */
    /**
     * Proyeccion del listado. Vive aparte porque la sirven DOS caminos: el
     * listado sin texto (filtro en el propio WHERE) y el listado con texto,
     * que primero resuelve el conjunto de candidatos y despues carga solo la
     * pagina. La forma de la fila tiene que ser identica en ambos.
     */
    String PROYECCION_LISTADO = """
            select p.id as id,
                   p.codigo as codigoLocal,
                   p.direccion as direccion,
                   p.distrito as distrito,
                   p.metraje as metraje,
                   p.precioReferencial as precioReferencial,
                   p.monedaReferencial as monedaReferencial,
                   d.rubroPermitido as rubroPermitido,
                   p.descripcion as descripcion,
                   """ + ESTADO_LEGADO + """
                    as estado,
                   rp.id as idPropietario,
                   per.nombresORazonSocial as propietarioNombre,
                   p.tipoInmueble as tipoInmueble,
                   p.uso as uso,
                   p.ambientes as ambientes,
                   p.antiguedadAnios as antiguedadAnios,
                   p.zonaUrbanizacion as zonaUrbanizacion,
                   p.geoLat as geoLat,
                   p.geoLong as geoLong,
                   p.frente as frente,
                   p.zonificacion as zonificacion,
                   d.aptoLicenciaFuncionamiento as aptoLicenciaFuncionamiento,
                   d.cargaElectricaKw as cargaElectricaKw,
                   p.numeroEstacionamientos as numeroEstacionamientos,
                   p.cuotaMantenimiento as cuotaMantenimiento,
                   p.idDistrito as idDistrito,
                   p.fechaRegistro as fechaRegistro
            """ + ASOCIACIONES_LISTADO + """
              left join p.detalleLocal d
            """;

    @Query(value = PROYECCION_LISTADO + """
            where p.organizacionId = :idOrganizacion
            """ + FILTRO_ESTADO_LEGADO + FILTRO_TEXTO_LISTADO + """
              order by p.id
            """,
            countQuery = "select count(p) " + DESDE)
    Page<LocalListado> buscar(@Param("idOrganizacion") long idOrganizacion,
                              @Param("texto") String texto,
                              @Param("estado") String estado,
                              Pageable pageable);

    /**
     * La MISMA proyeccion, cargada solo para los ids de la pagina ya resuelta.
     * Es la segunda mitad de la busqueda por conjunto de candidatos: la lista
     * de ids nunca pasa de {@code tamano} elementos, asi que este {@code in}
     * es un acceso por clave, no un barrido.
     */
    @Query(PROYECCION_LISTADO + """
            where p.organizacionId = :idOrganizacion and p.id in :ids
              order by p.id
            """)
    List<LocalListado> buscarPorIds(@Param("idOrganizacion") long idOrganizacion,
                                    @Param("ids") Collection<Long> ids);

    // ------------------------------------------------------------------
    // Busqueda por CONJUNTO DE CANDIDATOS (RC-003).
    //
    // El filtro de texto del listado mira cuatro campos que viven en TRES
    // tablas: propiedad (codigo, direccion, distrito), su detalle de local
    // (rubro) y la persona del propietario. Escrito como un unico OR, ese
    // predicado cruza tablas y PostgreSQL no puede combinar los indices
    // trigrama de cada una: cae a un Seq Scan con el LIKE como Join Filter, y
    // el conteo del PageResponse paga ese barrido entero en cada peticion
    // (medido: 383 ms fijos sobre 100.000 locales).
    //
    // Aqui cada rama pregunta a UNA sola tabla, asi que cada una puede usar su
    // indice trigrama, y el UNION las junta. UNION —no UNION ALL— porque un
    // local puede casar por varias ramas a la vez y no debe contarse dos veces.
    //
    // Tenant, estado y normalizacion del texto viajan en LAS TRES ramas: el
    // conjunto tiene que quedar cerrado antes de unirse, no despues, para que
    // el conteo y la pagina no puedan discrepar.
    // ------------------------------------------------------------------

    /** Filtro de estado legado en SQL nativo; espejo exacto de {@link #FILTRO_ESTADO_LEGADO}. */
    String FILTRO_ESTADO_NATIVO = """
              and (cast(:estado as varchar) is null
                   or (cast(:estado as varchar) = 'I' and p.estado_registro = 'I')
                   or (cast(:estado as varchar) = 'D' and p.estado_registro = 'A'
                       and p.disponibilidad_comercial = 'D')
                   or (cast(:estado as varchar) = 'N' and p.estado_registro = 'A'
                       and p.disponibilidad_comercial <> 'D'))
            """;

    /**
     * El {@code lower(concat(...))} se repite en las tres ramas a proposito:
     * es la MISMA expresion que usa {@link #FILTRO_TEXTO_LISTADO}, asi que la
     * normalizacion del texto es identica por construccion y no depende de que
     * Java y PostgreSQL bajen las mayusculas igual.
     */
    String PATRON_TEXTO = " like lower(concat('%', cast(:texto as varchar), '%'))";

    String RAMAS_TEXTO = """
            select p.id_propiedad as id
              from propiedad p
             where p.organizacion_id = :idOrganizacion
            """ + FILTRO_ESTADO_NATIVO + """
               and (lower(p.codigo)""" + PATRON_TEXTO + """
                    or lower(p.direccion)""" + PATRON_TEXTO + """
                    or lower(p.distrito)""" + PATRON_TEXTO + """
                   )
            union
            select d.id_propiedad as id
              from detalle_local_comercial d
              join propiedad p on p.id_propiedad = d.id_propiedad
             where d.organizacion_id = :idOrganizacion and p.organizacion_id = :idOrganizacion
            """ + FILTRO_ESTADO_NATIVO + """
               and lower(d.rubro_permitido)""" + PATRON_TEXTO + """

            union
            select p.id_propiedad as id
              from propiedad p
              join persona_rol rp on rp.id_persona_rol = p.id_rol_propietario
              join persona per on per.id_persona = rp.id_persona
             where p.organizacion_id = :idOrganizacion
            """ + FILTRO_ESTADO_NATIVO + """
               and lower(per.nombres_o_razon_social)""" + PATRON_TEXTO + """

            """;

    /**
     * Ids de la pagina, ya ordenados y recortados EN LA BASE. Nunca sube a Java
     * el conjunto completo de candidatos: eso seria el barrido que RC-003 vino
     * a quitar.
     */
    @Query(value = "select c.id from (" + RAMAS_TEXTO + ") c order by c.id limit :limite offset :desplazamiento",
            nativeQuery = true)
    List<Long> idsPorTexto(@Param("idOrganizacion") long idOrganizacion,
                           @Param("texto") String texto,
                           @Param("estado") String estado,
                           @Param("limite") int limite,
                           @Param("desplazamiento") int desplazamiento);

    /** Total del mismo conjunto que pagina {@link #idsPorTexto}: no pueden discrepar. */
    @Query(value = "select count(*) from (" + RAMAS_TEXTO + ") c", nativeQuery = true)
    long contarPorTexto(@Param("idOrganizacion") long idOrganizacion,
                        @Param("texto") String texto,
                        @Param("estado") String estado);

    /**
     * KPI del resumen sobre el MISMO conjunto de candidatos, agrupado por el
     * estado legado. Se invoca con {@code estado = null} —el resumen cuenta los
     * tres cubos, no filtra por uno— y por eso el {@code join} a propiedad: el
     * estado se deriva de sus dos columnas.
     */
    @Query(value = """
            select case when p.estado_registro = 'I' then 'I'
                        when p.disponibilidad_comercial = 'D' then 'D'
                        else 'N' end as estado,
                   count(*) as total
              from (""" + RAMAS_TEXTO + """
                   ) c
              join propiedad p on p.id_propiedad = c.id
             group by 1
            """, nativeQuery = true)
    List<ConteoPorEstado> contarPorEstadoConTexto(@Param("idOrganizacion") long idOrganizacion,
                                                  @Param("texto") String texto,
                                                  @Param("estado") String estado);

    /**
     * Los contadores del resumen (KPI) en UNA consulta agrupada, con el mismo
     * filtro que la lista salvo el estado — que es justamente lo que se cuenta.
     *
     * <p>Devuelve solo los estados presentes; el service completa con cero los
     * que falten. Asi el resumen no depende de las filas descargadas por el
     * cliente ni de cuantas paginas haya.
     */
    @Query("""
            select """ + ESTADO_LEGADO + """
             as estado, count(p) as total
            """ + ASOCIACIONES_LISTADO + """
            where p.organizacionId = :idOrganizacion
            """ + FILTRO_TEXTO_LISTADO + """
            group by """ + ESTADO_LEGADO + """

            """)
    List<ConteoPorEstado> contarPorEstado(@Param("idOrganizacion") long idOrganizacion,
                                          @Param("texto") String texto);

    /** Carga sin fetch joins, ya acotada al tenant (para los casos de uso que solo mutan). */
    Optional<Propiedad> findByOrganizacionIdAndId(long idOrganizacion, long id);

    /**
     * Candidatos del mismo propietario para la advertencia de posible
     * duplicado. La comparación técnica se hace en el servicio porque combina
     * normalización de texto, campos opcionales y una tolerancia de metraje.
     */
    List<Propiedad> findByOrganizacionIdAndRolPropietarioIdAndIdNotOrderById(
            long idOrganizacion, long idRolPropietario, long idExcluir);

    /** Existencia dentro del tenant: un local de otra corredora "no existe". */
    boolean existsByOrganizacionIdAndId(long idOrganizacion, long id);

    // ------------------------------------------------------------------
    // Propietario -> locales EN SEGUIMIENTO (contador del cable y alcance
    // del BROKER). Ambas consultas comparten la misma union y el mismo
    // filtro de alcance; se separan solo por lo que proyectan.
    // ------------------------------------------------------------------

    /**
     * {@code cantidadLocales} de cada propietario de la pagina, en UNA lectura
     * (la v1 escaneaba captaciones y prospecciones en memoria).
     *
     * <p>La asimetria entre las dos ramas <b>es del cable</b> y no hay que
     * "corregirla": la rama de captacion admite tambien las que el broker
     * revisa, la de prospeccion solo mira al agente. Un broker ve el local de
     * una captacion que supervisa aunque su prospeccion fuera de otro equipo.
     *
     * @param sinScope   ADMIN: sin filtro de rol (la frontera de tenant sigue).
     * @param rolesAgente nunca vacia (centinela {@code -1} como en Alcances).
     * @param rolBroker  {@code -1} salvo para el BROKER que consulta.
     */
    @Query(value = """
            select id_rol_propietario as idPropietario, count(distinct id_propiedad) as total
            from (
                select p.id_rol_propietario, p.id_propiedad
                from propiedad p
                  join captacion c on c.id_propiedad = p.id_propiedad
                where p.organizacion_id = :idOrganizacion
                  and p.id_rol_propietario in (:idsPropietario)
                  and (:sinScope = true
                       or c.id_rol_agente in (:rolesAgente)
                       or c.id_rol_broker_revisor = :rolBroker)
                union
                select p.id_rol_propietario, p.id_propiedad
                from propiedad p
                  join prospeccion pr on pr.id_propiedad = p.id_propiedad
                where p.organizacion_id = :idOrganizacion
                  and p.id_rol_propietario in (:idsPropietario)
                  and (:sinScope = true or pr.id_rol_agente in (:rolesAgente))
            ) t
            group by id_rol_propietario
            """, nativeQuery = true)
    List<ConteoPorPropietario> contarLocalesEnSeguimiento(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idsPropietario") Collection<Long> idsPropietario,
            @Param("sinScope") boolean sinScope,
            @Param("rolesAgente") Collection<Long> rolesAgente,
            @Param("rolBroker") long rolBroker);

    /**
     * Alcance del BROKER sobre el catalogo de propietarios: los duenos de algun
     * local que su equipo prospecta o capta, mas los de las captaciones que el
     * revisa. ADMIN y AGENTE no pasan por aqui — ven el catalogo entero (§ del
     * cable v1, {@code puedeVerPropietario}).
     *
     * <p>Se resuelve en SQL y devuelve solo ids: es el conjunto sobre el que
     * despues se pagina, asi que traer las filas enteras seria justo el escaneo
     * que RC-003 vino a quitar.
     */
    @Query(value = """
            select distinct p.id_rol_propietario
            from propiedad p
              join captacion c on c.id_propiedad = p.id_propiedad
            where p.organizacion_id = :idOrganizacion
              and (c.id_rol_agente in (:rolesAgente) or c.id_rol_broker_revisor = :rolBroker)
            union
            select distinct p.id_rol_propietario
            from propiedad p
              join prospeccion pr on pr.id_propiedad = p.id_propiedad
            where p.organizacion_id = :idOrganizacion
              and pr.id_rol_agente in (:rolesAgente)
            """, nativeQuery = true)
    List<Long> idsPropietarioDelBroker(@Param("idOrganizacion") long idOrganizacion,
                                       @Param("rolesAgente") Collection<Long> rolesAgente,
                                       @Param("rolBroker") long rolBroker);
}
