package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.query.ConteoPorPropietario;
import com.controllocal.persistence.query.LocalListado;
import com.controllocal.persistence.query.PropiedadListado;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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
            left join fetch p.rolPropietario rp
            left join fetch rp.persona
            where p.organizacionId = :idOrganizacion and p.id = :id
            """)
    Optional<Propiedad> buscarFicha(@Param("idOrganizacion") long idOrganizacion, @Param("id") long id);

    /**
     * Las asociaciones que necesitan las dos proyecciones del listado.
     *
     * <p>{@code left join} y no {@code join}: una propiedad puede no tener
     * representante de la titularidad, y en ese caso sigue estando en la
     * cartera —con {@code idPropietario} y {@code propietarioNombre} ausentes,
     * que es la verdad— en vez de desaparecer de la lista.
     *
     * <p><b>Aqui ya no hay ningun filtro.</b> El WHERE del listado —tenant,
     * texto libre, estado y los filtros propios de cada recurso— lo compone
     * {@code MotorBusquedaInmobiliaria}, que es el unico que busca sobre la
     * cartera desde el 2026-09-02. Lo que queda en este fichero es la forma de
     * la FILA, no el criterio de la busqueda.
     */
    String ASOCIACIONES_LISTADO = """
            from Propiedad p
              left join p.rolPropietario rp
              left join rp.persona per
            """;

    /**
     * Adaptador JPQL del contrato legado D/N/I; no existe como columna.
     *
     * <p>Desde V75 {@code disponibilidadComercial} puede ser NULL —propiedad
     * registrada que todavia nadie ha encargado—, y cae al {@code else 'N'}:
     * <b>no disponible</b>, que es la verdad. El filtro de abajo tiene que
     * nombrar el NULL explicitamente porque en SQL {@code NULL <> 'D'} es
     * UNKNOWN, no TRUE: sin eso la fila salia como 'N' en el listado y no
     * aparecia al filtrar por 'N'.
     */
    String ESTADO_LEGADO = " case "
            + "when p.estadoRegistro = 'I' then 'I' "
            + "when p.disponibilidadComercial = 'D' then 'D' "
            + "else 'N' end ";

    /**
     * Proyeccion del listado. Vive aparte porque la sirven DOS caminos: el
     * listado sin texto (filtro en el propio WHERE) y el listado con texto,
     * que primero resuelve el conjunto de candidatos y despues carga solo la
     * pagina. La forma de la fila tiene que ser identica en ambos.
     *
     * <p><b>Aqui NO estan las seis claves gobernadas</b> —ambientes, frente,
     * zonificacion, cuota de mantenimiento, estacionamientos y antiguedad—
     * porque su autoridad dejo de ser la columna de {@code propiedad} (D-E4-3).
     * Se hidratan despues, para los ids de la pagina, con una sola consulta a
     * {@code atributo_propiedad}: dos consultas por pagina, no N+1.
     *
     * <p>{@code metraje} SI se queda, y no por comodidad: es el unico de los
     * siete que quedo clasificado como estructural, y un listado tiene que poder
     * ordenar y filtrar por el — y eso solo se hace en SQL, antes del
     * {@code LIMIT}. El dia que alguna de las seis entre en un filtro o en un
     * orden, la solucion NO es devolverla a esta proyeccion sino unir contra
     * {@code atributo_propiedad} antes de paginar (§4 bis de D-E4-3).
     */
    String PROYECCION_LISTADO = """
            select p.id as id,
                   p.codigo as codigoLocal,
                   p.direccion as direccion,
                   p.distrito as distrito,
                   p.metraje as metraje,
                   p.precioReferencial as precioReferencial,
                   p.monedaReferencial as monedaReferencial,
                   p.descripcion as descripcion,
                   """ + ESTADO_LEGADO + """
                    as estado,
                   rp.id as idPropietario,
                   per.nombresORazonSocial as propietarioNombre,
                   p.tipoInmueble as tipoInmueble,
                   p.uso as uso,
                   p.zonaUrbanizacion as zonaUrbanizacion,
                   p.geoLat as geoLat,
                   p.geoLong as geoLong,
                   p.idDistrito as idDistrito,
                   p.fechaRegistro as fechaRegistro
            """ + ASOCIACIONES_LISTADO + """
            """;

    // ==================================================================
    // Listado UNIVERSAL (D-E4-1)
    //
    // El de arriba es el heredado: una fila por local, con `precio_referencial`
    // dentro y sin saber que operacion es. Este es el del modelo universal, y
    // la diferencia que importa es que NO trae ni operacion ni precio: los trae
    // el encargo, y una propiedad puede tener dos.
    // ==================================================================

    String PROYECCION_UNIVERSAL = """
            select p.id as id,
                   p.codigo as codigo,
                   p.tipoInmueble as tipoPropiedad,
                   p.uso as uso,
                   p.direccion as direccion,
                   p.distrito as distrito,
                   p.metraje as metraje,
                   """ + ESTADO_LEGADO + """
                    as estado,
                   rp.id as idPropietario,
                   per.nombresORazonSocial as propietarioNombre,
                   (select count(t) from TitularidadPropiedad t
                     where t.idPropiedad = p.id and t.vigenteHasta is null) as titulares,
                   p.fechaRegistro as fechaRegistro
            """ + ASOCIACIONES_LISTADO;

    /** Los distritos con cartera, para que el filtro ofrezca solo lo que existe. */
    @Query("""
            select distinct p.distrito from Propiedad p
             where p.organizacionId = :idOrganizacion and p.distrito is not null
             order by p.distrito
            """)
    List<String> distritosConCartera(@Param("idOrganizacion") long idOrganizacion);

    // ==================================================================
    // La segunda mitad de la busqueda: cargar la pagina YA resuelta.
    //
    // Quien decide QUE ids entran es `MotorBusquedaInmobiliaria`, que es uno
    // solo para los dos recursos. Lo que sigue no busca nada: carga la
    // proyeccion de cada uno para una lista de ids que nunca pasa del tamano de
    // pagina, asi que el `in` es un acceso por clave y no un barrido.
    //
    // Son dos metodos y no uno porque las DOS PROYECCIONES son distintas -y esa
    // es justamente la diferencia legitima entre los recursos-: `/locales`
    // publica un precio suelto sin operacion; el universal publica el tipo y
    // deja los precios en los encargos, que pueden ser dos.
    //
    // NINGUNO ordena de forma significativa: el orden lo fijo el motor y lo
    // lleva la lista de ids. Reordenar las filas para que coincidan es del
    // servicio (`ordenadasComo`), porque un `in (...)` no promete ningun orden.
    // ==================================================================

    @Query(PROYECCION_LISTADO + """
            where p.organizacionId = :idOrganizacion and p.id in :ids
            """)
    List<LocalListado> buscarPorIds(@Param("idOrganizacion") long idOrganizacion,
                                    @Param("ids") Collection<Long> ids);

    @Query(PROYECCION_UNIVERSAL + """
            where p.organizacionId = :idOrganizacion and p.id in :ids
            """)
    List<PropiedadListado> buscarUniversalPorIds(@Param("idOrganizacion") long idOrganizacion,
                                                 @Param("ids") Collection<Long> ids);

    /** Carga sin fetch joins, ya acotada al tenant (para los casos de uso que solo mutan). */
    Optional<Propiedad> findByOrganizacionIdAndId(long idOrganizacion, long id);

    // ------------------------------------------------------------------
    // F2.10: la autoridad de EDICION se comprueba sobre la fila tomada
    // ------------------------------------------------------------------

    /**
     * <b>Toma la fila de la propiedad para que su autoridad de edicion no
     * cambie entre comprobarla y escribir</b> (F2.10, 2026-09-02).
     *
     * <h2>Que defecto cierra</h2>
     * Es el TOCTOU de D-P0-13 dicho sobre la <b>otra</b> autoridad. Los comandos
     * que escriben hechos de la propiedad cargaban la fila, preguntaban
     * {@code AutoridadDePropiedad.exigirEdicion} sobre lo que acababan de leer y
     * escribian <b>despues</b>, sin nada que sujetara la fila entre las dos
     * cosas. En esa ventana cabe un traspaso entero —su compare-and-set toma la
     * fila un instante y la suelta al comitear—, y la edicion del agente
     * <b>saliente</b> aterrizaba sobre una propiedad que ya era de otro. Ninguna
     * guarda mentia: cada una dijo la verdad en el instante en que se pregunto.
     *
     * <p><b>No es lo que arreglo F2.1.</b> {@code updatable = false} sobre
     * {@code id_rol_responsable} impide que esa edicion tardia <b>revierta</b> la
     * autoridad; nunca impidio que <b>se escriba</b>.
     *
     * <h2>Quien tiene que tomarlo</h2>
     * Lo toman los comandos que <b>ESCRIBEN</b> hechos de la propiedad, para que
     * la autoridad se compruebe sobre el estado que seguira siendo verdad al
     * escribir: el traspaso espera a que la edicion termine, o la edicion espera
     * al traspaso y entonces <b>ve al nuevo responsable</b> y recibe el 403 que
     * el Core ya produce ({@code OTRO_RESPONSABLE}). No hay regla nueva: hay una
     * regla vieja comprobada a tiempo.
     *
     * <p>Las lecturas <b>no</b> lo toman —ni pueden: una transaccion
     * {@code readOnly} no ejecuta {@code SELECT ... FOR UPDATE}—, y por eso
     * {@link #findByOrganizacionIdAndId} y {@link #buscarFicha} siguen ahi.
     *
     * <h2>La regla que hace que esto funcione, y que es facil romper</h2>
     * <b>Esta tiene que ser la PRIMERA carga de esa fila en la transaccion.</b>
     * Hibernate devuelve la instancia que ya esta en el contexto de persistencia
     * <b>sin refrescar su estado</b>: si el caso de uso cargo la propiedad antes
     * sin candado, este metodo tomaria el bloqueo de verdad y la autoridad se
     * comprobaria igualmente sobre el valor viejo — el defecto intacto y con
     * aspecto de arreglado. Cargar primero y bloquear despues no vale.
     *
     * <h2>Orden de candados (para que no haya interbloqueo)</h2>
     * <pre>
     *   detalle_agente  -&gt;  propiedad  -&gt;  captacion
     * </pre>
     * <ul>
     *   <li><b>traspaso</b>: {@code detalle_agente} del destino (D-P0-13) y
     *       despues la fila {@code propiedad} (el compare-and-set);</li>
     *   <li><b>edicion de la propiedad</b>: la fila {@code propiedad} —esta— y,
     *       si la misma peticion toca encargos, sus filas {@code captacion}
     *       despues. No toma agentes;</li>
     *   <li><b>reasignacion del encargo</b>: {@code detalle_agente} del destino
     *       y despues la fila {@code captacion}.</li>
     * </ul>
     * Nadie va en sentido contrario —ninguna via toma {@code captacion} y
     * despues {@code propiedad}—, asi que no hay ciclo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Propiedad p where p.organizacionId = :idOrganizacion and p.id = :id")
    Optional<Propiedad> bloquearParaEscritura(@Param("idOrganizacion") long idOrganizacion,
                                              @Param("id") long id);

    /**
     * <b>Compare-and-set del responsable: exactamente UNA transicion legitima
     * puede partir de un estado concreto</b> (D-P0-9).
     *
     * <p>Mueve {@code id_rol_responsable} <b>solo si</b> hoy vale lo que el
     * actor observo cuando decidio. Devuelve las filas afectadas: {@code 1} el
     * traspaso que gano, {@code 0} el que llego tarde.
     *
     * <p><b>Por que esto y no una comprobacion en memoria.</b> Comprobar en
     * Java «¿sigue siendo A?» y escribir despues deja una ventana entre la
     * lectura y la escritura, y esa ventana es justo donde vive la carrera: dos
     * transacciones que arranquen de A leen A las dos, las dos deciden que
     * pueden, y la segunda pisa a la primera. La comprobacion tiene que ocurrir
     * <b>dentro</b> de la escritura, y eso solo lo hace la base.
     *
     * <p><b>Por que funciona bajo READ COMMITTED de PostgreSQL</b>, que es el
     * nivel por defecto: un UPDATE que encuentra la fila bloqueada por otra
     * transaccion <b>espera y re-evalua el WHERE sobre la fila ya
     * actualizada</b>; por eso el segundo comando A&rarr;C ve B y afecta 0
     * filas. No hace falta {@code SERIALIZABLE}, ni {@code SELECT ... FOR
     * UPDATE} previo, ni una columna {@code @Version} nueva —que ademas seria
     * una migracion—: el predicado del propio UPDATE es el candado.
     *
     * <p>{@code esperado} nulo es <b>FALTANTE observado</b> y se compara como
     * tal: en SQL {@code null = null} es UNKNOWN, asi que la rama nula va
     * escrita aparte. Sin ella, asignar una propiedad sin responsable no
     * afectaria nunca ninguna fila.
     *
     * <p>Va {@code flushAutomatically} para que cualquier cambio pendiente de
     * la propiedad llegue a la base antes del candado, y <b>no</b> lleva
     * {@code clearAutomatically}: el llamador necesita seguir con la MISMA
     * instancia gestionada para que el rastro, el evento y la ficha devuelta
     * lean el mismo valor.
     *
     * <p><b>Solo la escribe {@code AutoridadDePropiedad}</b>, y lo comprueba
     * {@code AutoridadDeLaPropiedadTest}: es una escritura de
     * {@code id_rol_responsable}, o sea la autoridad misma.
     *
     * @return 1 si el responsable seguia siendo el observado; 0 si cambio.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Propiedad p set p.idRolResponsable = :nuevo
             where p.organizacionId = :idOrganizacion
               and p.id = :idPropiedad
               and ((:esperado is null and p.idRolResponsable is null)
                    or p.idRolResponsable = :esperado)
            """)
    int cambiarResponsableSi(@Param("idOrganizacion") long idOrganizacion,
                             @Param("idPropiedad") long idPropiedad,
                             @Param("esperado") Long esperado,
                             @Param("nuevo") long nuevo);

    /**
     * Candidatos del mismo propietario para la advertencia de posible
     * duplicado. La comparación técnica se hace en el servicio porque combina
     * normalización de texto, campos opcionales y una tolerancia de metraje.
     */
    List<Propiedad> findByOrganizacionIdAndRolPropietarioIdAndIdNotOrderById(
            long idOrganizacion, long idRolPropietario, long idExcluir);

    /** Existencia dentro del tenant: un local de otra corredora "no existe". */
    boolean existsByOrganizacionIdAndId(long idOrganizacion, long id);

    /**
     * Correlativo PROP-#### por organizacion, igual que las captaciones (V6.3):
     * cada corredora numera desde 0001 y no deduce del codigo cuantas
     * propiedades lleva la de al lado.
     */
    long countByOrganizacionId(long idOrganizacion);

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
