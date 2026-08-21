package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.persistence.query.AgenteConCierres;
import com.controllocal.persistence.query.CandidatoTarea;
import com.controllocal.persistence.query.ComisionGeneradaPorMoneda;
import com.controllocal.persistence.query.IndicadorContrato;
import com.controllocal.persistence.query.MovimientoComisionPorMoneda;
import com.controllocal.persistence.query.RepartoComisionPorMoneda;
import com.controllocal.persistence.query.ResumenCierres;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Contratos con el scope del actor en el WHERE (RC-001/RC-003) y el tenant por
 * delante (V6).
 *
 * <p><b>Ojo: aqui el BROKER alcanza por CAPTACION</b> (los contratos de las
 * captaciones que supervisa), no por el agente de la solicitud —al reves que
 * en {@code SolicitudAlquilerRepository}, donde alcanza por agente
 * supervisado—. Son dos reglas distintas del cable v1 y NO se unifican; el
 * parametro {@code porAgente} distingue las ramas, igual que en oportunidades.
 */
public interface ContratoAlquilerRepository extends JpaRepository<ContratoAlquiler, Long> {

    /**
     * Solo los joins que el WHERE necesita: es lo que se cuenta, sin traer nada.
     *
     * <p>Los joins de propiedad y cliente estan aqui porque los usa
     * {@link #FILTROS}. Son INNER igual que en {@link #FICHA}, asi que el
     * conteo y la pagina siguen viendo el mismo conjunto.
     *
     * <p><b>El rol y la persona del agente NO estan aqui.</b> Solo los nombra
     * {@link #agentesDeCierres}, que los anade por su cuenta con
     * {@link #JOINS_PERSONA_AGENTE}; tenerlos en el tronco comun metia dos
     * INNER join de mas en los otros siete consumidores de {@link #FILTROS},
     * sin cambiar ni una fila del resultado.
     */
    String DESDE_JOINS = """
            from ContratoAlquiler c
              join c.oportunidad op
              join op.captacion cap
              join cap.propiedad prop
              join cap.agente capAg
              join op.cliente cli
              join cli.rol cliRol
              join cliRol.persona cliPer
              join c.solicitud s
              join s.agente ag
            """;

    /** Lo que {@link #agentesDeCierres} necesita y nadie mas. */
    String JOINS_PERSONA_AGENTE = """
              join ag.rol agRol
              join agRol.persona agPer
            """;

    String DESDE_WHERE = """
            where c.organizacionId = :idOrganizacion
              and (:sinScope = true
                   or (:porAgente = true  and ag.id in :roles)
                   or (:porAgente = false and capAg.id in :roles))
            """;

    String DESDE = DESDE_JOINS + DESDE_WHERE;

    /**
     * La liquidacion no es una asociacion del contrato —la relacion se declara
     * del lado de {@code ComisionLiquidacion}—, asi que se une explicitamente.
     *
     * <p><b>LEFT de verdad</b>, y solo para {@link #resumenCierres}: ahi el
     * contrato SIN liquidacion es parte de la respuesta
     * ({@code sinLiquidacion}), y un INNER lo dejaria fuera del conteo de
     * cierres, que es lo primero que se muestra.
     */
    String DESDE_CON_COMISION = DESDE_JOINS
            + " left join ComisionLiquidacion com on com.contrato = c "
            + DESDE_WHERE;

    /**
     * Para los agregados de importes, que solo miran liquidaciones existentes.
     *
     * <p>Antes usaban {@link #DESDE_CON_COMISION} y colaban un
     * {@code and com.id is not null} en el WHERE: un INNER escrito como OUTER.
     * El planificador tenia que resolver el join externo entero para luego
     * descartar las filas sin pareja.
     */
    String DESDE_CON_LIQUIDACION = DESDE_JOINS
            + " join ComisionLiquidacion com on com.contrato = c "
            + DESDE_WHERE;

    String DESDE_CON_MOVIMIENTO = DESDE_JOINS
            + " join ComisionLiquidacion com on com.contrato = c "
            + " join ComisionMovimiento mov on mov.liquidacion = com "
            + DESDE_WHERE;

    /**
     * Filtros ADITIVOS del listado de cierres, ambos opcionales: con los dos en
     * null la consulta responde exactamente lo mismo que antes de que
     * existieran, asi que el cable congelado de {@code GET /contratos} no
     * cambia.
     *
     * <p>El agente se filtra por el de la SOLICITUD, que es el que la
     * respuesta publica como {@code agenteNombre}; filtrar por el de la
     * captacion daria una lista que no cuadra con la columna que se ve.
     *
     * <p><b>Aqui NO hay filtro de texto.</b> Lo habia —un OR sobre cuatro tablas—
     * y era la razon de que la bandeja no usara ninguno de sus cuatro indices
     * trigrama. La busqueda textual vive ahora en {@link #RAMAS_TEXTO}, y
     * estas consultas se llaman solo cuando no hay texto.
     *
     * <p>Se elimino en vez de dejarlo inerte con {@code :texto is null}: con
     * las dos formas vivas habria dos definiciones de que contratos casan con
     * un texto, y la que quedaba aqui es la lenta.
     */
    String FILTROS = """
              and (:distrito is null or prop.distrito = :distrito)
              and (:idAgente is null or ag.id = :idAgente)
            """;

    /**
     * Ficha completa: todo lo que la respuesta congelada necesita en un solo
     * select. Son fetch join a-UNO, asi que la paginacion sigue bajando a SQL
     * (RC-003) y el listado no cae en el N+1 que la v1 ya evitaba con sus dos
     * consultas en bloque.
     */
    // Los alias existen para que CONDICION y FILTROS puedan referirlos: un
    // `join fetch` sin alias trae la fila pero no se puede nombrar en el WHERE.
    String FICHA = """
            select c from ContratoAlquiler c
              join fetch c.oportunidad op
              join fetch op.cliente cli
              join fetch cli.rol cliRol
              join fetch cliRol.persona cliPer
              join fetch op.captacion cap
              join fetch cap.propiedad prop
              join fetch prop.rolPropietario propRol
              join fetch propRol.persona
              join fetch cap.agente capAg
              join fetch c.solicitud s
              join fetch s.agente ag
              join fetch ag.rol agRol
              join fetch agRol.persona agPer
              join fetch c.agenteCierre agc
              join fetch agc.rol agcRol
              join fetch agcRol.persona
            """;

    String CONDICION = """
             where c.organizacionId = :idOrganizacion
               and (:sinScope = true
                    or (:porAgente = true  and ag.id in :roles)
                    or (:porAgente = false and capAg.id in :roles))
            """;

    /**
     * Listado con filtros aditivos. <b>El orden lo pone el {@code Pageable}</b>
     * y no la consulta: el cable congelado ordena por {@code id desc} y la
     * pantalla de cierres por fecha de cierre, asi que decide el service. Con
     * el orden dentro del {@code @Query} no se podria elegir sin duplicar la
     * consulta.
     */
    @Query(value = FICHA + CONDICION + FILTROS,
            countQuery = "select count(c) " + DESDE + FILTROS)
    Page<ContratoAlquiler> buscar(@Param("idOrganizacion") long idOrganizacion,
                                  @Param("sinScope") boolean sinScope,
                                  @Param("porAgente") boolean porAgente,
                                  @Param("roles") Collection<Long> roles,
                                  @Param("distrito") String distrito,
                                  @Param("idAgente") Long idAgente,
                                  Pageable pageable);

    /**
     * Los tres KPI de la pantalla de cierres, en una consulta y sobre la BASE.
     *
     * <p>Dos cosas que hay que respetar aqui:
     * <ul>
     *   <li>la liquidacion se une con un join explicito y <b>LEFT</b>: un
     *       contrato podria no tenerla, y un inner la dejaria fuera del conteo
     *       de cierres, que es lo primero que se muestra;</li>
     *   <li>su estado viaja con el <b>NOMBRE</b> del enum
     *       ({@code 'PENDIENTE'}), no como CHAR(1). Es una de las dos rupturas
     *       de la convencion en esta vertical, y comparar con {@code 'P'} da
     *       cero en silencio.</li>
     * </ul>
     */
    @Query("select count(c) as cierres,"
            + " count(case when com.estado in ('P', 'R') then 1 end) as porLiquidar,"
            + " count(case when com.id is null then 1 end) as sinLiquidacion "
            + DESDE_CON_COMISION + FILTROS)
    ResumenCierres resumenCierres(@Param("idOrganizacion") long idOrganizacion,
                                  @Param("sinScope") boolean sinScope,
                                  @Param("porAgente") boolean porAgente,
                                  @Param("roles") Collection<Long> roles,
                                  @Param("distrito") String distrito,
                                  @Param("idAgente") Long idAgente);

    /** Excluye ANULADA y agrupa por moneda para no sumar unidades distintas. */
    @Query("select com.moneda as moneda, sum(com.montoBruto) as monto "
            + DESDE_CON_LIQUIDACION + FILTROS
            + " and com.estado <> 'A' "
            + " group by com.moneda order by com.moneda")
    List<ComisionGeneradaPorMoneda> comisionesGeneradas(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("sinScope") boolean sinScope,
            @Param("porAgente") boolean porAgente,
            @Param("roles") Collection<Long> roles,
            @Param("distrito") String distrito,
            @Param("idAgente") Long idAgente);

    /** Reparto asignado, excluyendo anulaciones, para derivar el saldo del agente. */
    @Query("select com.moneda as moneda, sum(coalesce(com.parteAgente, 0)) as parteAgente "
            + DESDE_CON_LIQUIDACION + FILTROS
            + " and com.estado <> 'A' "
            + " group by com.moneda order by com.moneda")
    List<RepartoComisionPorMoneda> repartosPorMoneda(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("sinScope") boolean sinScope,
            @Param("porAgente") boolean porAgente,
            @Param("roles") Collection<Long> roles,
            @Param("distrito") String distrito,
            @Param("idAgente") Long idAgente);

    /** Evidencia economica real: cobros/reversiones y pagos, por moneda. */
    @Query("select mov.moneda as moneda, "
            + "sum(case when mov.tipo = 'C' then mov.monto "
            + "         when mov.tipo = 'R' then -mov.monto else 0 end) as montoCobrado, "
            + "sum(case when mov.tipo = 'P' then mov.monto else 0 end) as montoPagadoAgente "
            + DESDE_CON_MOVIMIENTO + FILTROS
            + " and com.estado <> 'A' "
            + " group by mov.moneda order by mov.moneda")
    List<MovimientoComisionPorMoneda> movimientosPorMoneda(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("sinScope") boolean sinScope,
            @Param("porAgente") boolean porAgente,
            @Param("roles") Collection<Long> roles,
            @Param("distrito") String distrito,
            @Param("idAgente") Long idAgente);

    // ------------------------------------------------------------------
    // Ficha del agente: por ATRIBUCION HISTORICA (V27), no por la cadena
    // ------------------------------------------------------------------
    //
    // Estas cinco consultas son la razon practica de V27. Antes de esa
    // migracion, "los cierres de este agente" habia que preguntarlo saltando
    // contrato -> solicitud -> agente, y la respuesta cambiaba sola cuando el
    // agente se movia de equipo: la ficha de una persona se reescribia al
    // reorganizar el organigrama. Ahora el contrato recuerda a quien se le
    // atribuyo el alquiler, asi que el filtro es una columna propia y el
    // historial de cada quien queda fijo.
    //
    // Ojo: NO llevan filtro de alcance. Quien puede abrir la ficha de un
    // agente lo decide el service ANTES de llamar aqui (el BROKER solo a los
    // que supervisa, el ADMIN a todos); meter el scope otra vez aqui daria
    // numeros distintos a los de la propia ficha.

    @Query("""
            select count(c) from ContratoAlquiler c
            where c.organizacionId = :idOrganizacion
              and c.idRolAgenteCierre = :idRolAgente
            """)
    long contarCierresDeAgente(@Param("idOrganizacion") long idOrganizacion,
                               @Param("idRolAgente") long idRolAgente);

    /** Comision GENERADA: el bruto pactado, excluyendo liquidaciones anuladas. */
    @Query("""
            select com.moneda as moneda, sum(com.montoBruto) as monto
            from ContratoAlquiler c
              join ComisionLiquidacion com on com.contrato = c
            where c.organizacionId = :idOrganizacion
              and c.idRolAgenteCierre = :idRolAgente
              and com.estado <> 'A'
            group by com.moneda order by com.moneda
            """)
    List<ComisionGeneradaPorMoneda> comisionesGeneradasDeAgente(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idRolAgente") long idRolAgente);

    /** Reparto ASIGNADO al agente por el broker; base del saldo por pagarle. */
    @Query("""
            select com.moneda as moneda, sum(coalesce(com.parteAgente, 0)) as parteAgente
            from ContratoAlquiler c
              join ComisionLiquidacion com on com.contrato = c
            where c.organizacionId = :idOrganizacion
              and c.idRolAgenteCierre = :idRolAgente
              and com.estado <> 'A'
            group by com.moneda order by com.moneda
            """)
    List<RepartoComisionPorMoneda> repartosDeAgente(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idRolAgente") long idRolAgente);

    /**
     * Evidencia economica REAL, que es cosa distinta de lo generado: lo cobrado
     * por la corredora (cobros menos reversiones) y lo pagado al agente.
     */
    @Query("""
            select mov.moneda as moneda,
                   sum(case when mov.tipo = 'C' then mov.monto
                            when mov.tipo = 'R' then -mov.monto else 0 end) as montoCobrado,
                   sum(case when mov.tipo = 'P' then mov.monto else 0 end) as montoPagadoAgente
            from ContratoAlquiler c
              join ComisionLiquidacion com on com.contrato = c
              join ComisionMovimiento mov on mov.liquidacion = com
            where c.organizacionId = :idOrganizacion
              and c.idRolAgenteCierre = :idRolAgente
              and com.estado <> 'A'
            group by mov.moneda order by mov.moneda
            """)
    List<MovimientoComisionPorMoneda> movimientosDeAgente(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idRolAgente") long idRolAgente);

    /** Cierres del agente para la ficha, del mas reciente al mas antiguo. */
    @Query(value = FICHA + """
            where c.organizacionId = :idOrganizacion
              and c.idRolAgenteCierre = :idRolAgente
            """,
            countQuery = """
                    select count(c) from ContratoAlquiler c
                    where c.organizacionId = :idOrganizacion
                      and c.idRolAgenteCierre = :idRolAgente
                    """)
    Page<ContratoAlquiler> cierresDeAgente(@Param("idOrganizacion") long idOrganizacion,
                                           @Param("idRolAgente") long idRolAgente,
                                           Pageable pageable);

    /** Distritos presentes en los cierres del alcance, para el filtro data-driven. */
    @Query("select distinct prop.distrito " + DESDE + FILTROS + " order by prop.distrito")
    List<String> distritosDeCierres(@Param("idOrganizacion") long idOrganizacion,
                                    @Param("sinScope") boolean sinScope,
                                    @Param("porAgente") boolean porAgente,
                                    @Param("roles") Collection<Long> roles,
                                    @Param("distrito") String distrito,
                                    @Param("idAgente") Long idAgente);

    /** Agentes con al menos un cierre en el alcance. */
    @Query("select distinct ag.id as id, agPer.nombresORazonSocial as nombre "
            + DESDE_JOINS + JOINS_PERSONA_AGENTE + DESDE_WHERE + FILTROS
            + " order by agPer.nombresORazonSocial")
    List<AgenteConCierres> agentesDeCierres(@Param("idOrganizacion") long idOrganizacion,
                                            @Param("sinScope") boolean sinScope,
                                            @Param("porAgente") boolean porAgente,
                                            @Param("roles") Collection<Long> roles,
                                            @Param("distrito") String distrito,
                                            @Param("idAgente") Long idAgente);

    // ------------------------------------------------------------------
    // Busqueda por CONJUNTO DE CANDIDATOS (RC-003, §5 del contrato de listados)
    //
    // El texto de esta bandeja mira CUATRO tablas: la direccion de la
    // propiedad, el codigo de la captacion, el de la oportunidad y el nombre
    // del cliente. Escrito como un unico OR —que es como estaba— el predicado
    // cruza tablas, PostgreSQL no combina los cuatro trigramas y los degrada a
    // `Join Filter` con Seq Scan paralelo sobre las cuatro tablas grandes.
    // Medido sobre 100.000 contratos: 100.000 filas construidas y descartadas
    // para devolver 20, entre 258 y 450 ms, y ninguno de los cuatro indices
    // usado. La misma busqueda por ramas baja a 1-3 ms (V39 documenta la tabla
    // completa).
    //
    // Cada rama pregunta a UNA sola tabla y vuelve al contrato por su columna
    // de atribucion indexada (V27 + V39). UNION, no UNION ALL: un contrato
    // puede casar por varias. Tenant, alcance y filtros activos viajan en LAS
    // CUATRO ramas, cerrados ANTES del union.
    //
    // Este conjunto es el UNICO universo de la pantalla: lo pagina
    // idsPorTexto*, lo cuenta contarPorTexto y sobre el se agregan los KPI.
    // Los KPI no miran la pagina visible, miran el conjunto entero.
    // ------------------------------------------------------------------

    String PATRON = " like lower(concat('%', cast(:texto as varchar), '%'))";

    /**
     * Alcance y filtros activos, identicos en las cuatro ramas.
     *
     * <p>El alcance del BROKER es por CAPTACION y el del AGENTE por la
     * solicitud, igual que en {@link #DESDE_WHERE}: {@code porAgente} elige.
     */
    String COMUN_TEXTO = """
             where c.organizacion_id = :idOrganizacion
               and (:sinScope = true
                    or (:porAgente = true
                        and s.id_rol_agente = any(cast(:roles as bigint[])))
                    or (:porAgente = false
                        and cap.id_rol_agente = any(cast(:roles as bigint[]))))
               and (cast(:distrito as varchar) is null
                    or prop.distrito = cast(:distrito as varchar))
               and (cast(:idAgente as bigint) is null
                    or s.id_rol_agente = cast(:idAgente as bigint))
            """;

    /**
     * Tronco de las cuatro ramas. La solicitud se une con INNER a proposito:
     * {@code contrato_alquiler.id_solicitud} es NULLABLE y {@link #DESDE_JOINS}
     * tambien la exige, asi que un contrato sin solicitud queda fuera de la
     * bandeja igual que antes. Las otras tres son FK NOT NULL y no descartan
     * ninguna fila; estan porque el alcance y el filtro por distrito viven en
     * ellas.
     */
    String DESDE_TEXTO = """
              from contrato_alquiler c
              join solicitud_alquiler s on s.id_solicitud = c.id_solicitud
              join oportunidad_comercial op on op.id_oportunidad = c.id_oportunidad
              join captacion cap on cap.id_captacion = c.id_captacion
              join propiedad prop on prop.id_propiedad = c.id_propiedad
            """;

    /** Se proyecta tambien la fecha para que el orden por cierre no rejoin. */
    String SELECT_TEXTO = "select c.id_contrato_alquiler as id, c.fecha_cierre as fecha_cierre";

    String RAMAS_TEXTO = SELECT_TEXTO + DESDE_TEXTO + COMUN_TEXTO + """
               and lower(prop.direccion)""" + PATRON + """

            union
            """ + SELECT_TEXTO + DESDE_TEXTO + COMUN_TEXTO + """
               and lower(cap.codigo_captacion)""" + PATRON + """

            union
            """ + SELECT_TEXTO + DESDE_TEXTO + COMUN_TEXTO + """
               and lower(op.codigo_oportunidad)""" + PATRON + """

            union
            """ + SELECT_TEXTO + DESDE_TEXTO + """
              join detalle_cliente cli on cli.id_persona_rol = c.id_rol_cliente
              join persona_rol cliRol on cliRol.id_persona_rol = cli.id_persona_rol
              join persona cliPer on cliPer.id_persona = cliRol.id_persona
            """ + COMUN_TEXTO + """
               and lower(cliPer.nombres_o_razon_social)""" + PATRON + """

            """;

    /**
     * Ids de la pagina en el orden congelado ({@code id desc}), recortados EN
     * LA BASE. Nunca sube a Java el conjunto completo.
     */
    @Query(value = "select x.id from (" + RAMAS_TEXTO
            + ") x order by x.id desc limit :limite offset :desplazamiento",
            nativeQuery = true)
    List<Long> idsPorTextoPorId(@Param("idOrganizacion") long idOrganizacion,
                                @Param("sinScope") boolean sinScope,
                                @Param("porAgente") boolean porAgente,
                                @Param("roles") String roles,
                                @Param("texto") String texto,
                                @Param("distrito") String distrito,
                                @Param("idAgente") Long idAgente,
                                @Param("limite") int limite,
                                @Param("desplazamiento") int desplazamiento);

    /**
     * El otro orden de la pantalla de cierres. <b>Mismas {@code RAMAS_TEXTO}
     * exactamente</b>: lo unico que cambia entre las dos variantes es el
     * {@code order by}, para que no exista una segunda definicion de "que
     * contratos casan con el texto".
     */
    @Query(value = "select x.id from (" + RAMAS_TEXTO
            + ") x order by x.fecha_cierre desc, x.id desc limit :limite offset :desplazamiento",
            nativeQuery = true)
    List<Long> idsPorTextoPorCierre(@Param("idOrganizacion") long idOrganizacion,
                                    @Param("sinScope") boolean sinScope,
                                    @Param("porAgente") boolean porAgente,
                                    @Param("roles") String roles,
                                    @Param("texto") String texto,
                                    @Param("distrito") String distrito,
                                    @Param("idAgente") Long idAgente,
                                    @Param("limite") int limite,
                                    @Param("desplazamiento") int desplazamiento);

    /** Total del MISMO conjunto que paginan las dos variantes de arriba. */
    @Query(value = "select count(*) from (" + RAMAS_TEXTO + ") x", nativeQuery = true)
    long contarPorTexto(@Param("idOrganizacion") long idOrganizacion,
                        @Param("sinScope") boolean sinScope,
                        @Param("porAgente") boolean porAgente,
                        @Param("roles") String roles,
                        @Param("texto") String texto,
                        @Param("distrito") String distrito,
                        @Param("idAgente") Long idAgente);

    /**
     * Los tres contadores de la cabecera sobre el MISMO conjunto. El LEFT es
     * el legitimo: {@code sinLiquidacion} cuenta justo los que no casan.
     */
    @Query(value = "select count(*) as cierres,"
            + " count(case when com.estado in ('P', 'R') then 1 end) as \"porLiquidar\","
            + " count(case when com.id_comision_liquidacion is null then 1 end) as \"sinLiquidacion\""
            + " from (" + RAMAS_TEXTO + ") x"
            + " left join comision_liquidacion com"
            + "   on com.id_contrato_alquiler = x.id and com.organizacion_id = :idOrganizacion",
            nativeQuery = true)
    ResumenCierres resumenCierresPorTexto(@Param("idOrganizacion") long idOrganizacion,
                                          @Param("sinScope") boolean sinScope,
                                          @Param("porAgente") boolean porAgente,
                                          @Param("roles") String roles,
                                          @Param("texto") String texto,
                                          @Param("distrito") String distrito,
                                          @Param("idAgente") Long idAgente);

    @Query(value = "select com.moneda as moneda, sum(com.monto_bruto) as monto"
            + " from comision_liquidacion com"
            + " join (" + RAMAS_TEXTO + ") x on x.id = com.id_contrato_alquiler"
            + " where com.organizacion_id = :idOrganizacion and com.estado <> 'A'"
            + " group by com.moneda order by com.moneda",
            nativeQuery = true)
    List<ComisionGeneradaPorMoneda> comisionesGeneradasPorTexto(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("sinScope") boolean sinScope,
            @Param("porAgente") boolean porAgente,
            @Param("roles") String roles,
            @Param("texto") String texto,
            @Param("distrito") String distrito,
            @Param("idAgente") Long idAgente);

    @Query(value = "select com.moneda as moneda,"
            + " sum(coalesce(com.parte_agente, 0)) as \"parteAgente\""
            + " from comision_liquidacion com"
            + " join (" + RAMAS_TEXTO + ") x on x.id = com.id_contrato_alquiler"
            + " where com.organizacion_id = :idOrganizacion and com.estado <> 'A'"
            + " group by com.moneda order by com.moneda",
            nativeQuery = true)
    List<RepartoComisionPorMoneda> repartosPorMonedaPorTexto(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("sinScope") boolean sinScope,
            @Param("porAgente") boolean porAgente,
            @Param("roles") String roles,
            @Param("texto") String texto,
            @Param("distrito") String distrito,
            @Param("idAgente") Long idAgente);

    @Query(value = "select mov.moneda as moneda,"
            + " sum(case when mov.tipo = 'C' then mov.monto"
            + "          when mov.tipo = 'R' then -mov.monto else 0 end) as \"montoCobrado\","
            + " sum(case when mov.tipo = 'P' then mov.monto else 0 end) as \"montoPagadoAgente\""
            + " from comision_movimiento mov"
            + " join comision_liquidacion com"
            + "   on com.id_comision_liquidacion = mov.id_comision_liquidacion"
            + " join (" + RAMAS_TEXTO + ") x on x.id = com.id_contrato_alquiler"
            + " where mov.organizacion_id = :idOrganizacion and com.estado <> 'A'"
            + " group by mov.moneda order by mov.moneda",
            nativeQuery = true)
    List<MovimientoComisionPorMoneda> movimientosPorMonedaPorTexto(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("sinScope") boolean sinScope,
            @Param("porAgente") boolean porAgente,
            @Param("roles") String roles,
            @Param("texto") String texto,
            @Param("distrito") String distrito,
            @Param("idAgente") Long idAgente);

    /** Distritos del MISMO conjunto, para el filtro data-driven. */
    @Query(value = "select distinct prop.distrito from (" + RAMAS_TEXTO + ") x"
            + " join contrato_alquiler c on c.id_contrato_alquiler = x.id"
            + " join propiedad prop on prop.id_propiedad = c.id_propiedad"
            + " where prop.distrito is not null order by prop.distrito",
            nativeQuery = true)
    List<String> distritosDeCierresPorTexto(@Param("idOrganizacion") long idOrganizacion,
                                            @Param("sinScope") boolean sinScope,
                                            @Param("porAgente") boolean porAgente,
                                            @Param("roles") String roles,
                                            @Param("texto") String texto,
                                            @Param("distrito") String distrito,
                                            @Param("idAgente") Long idAgente);

    /** Agentes del MISMO conjunto, para el filtro data-driven. */
    @Query(value = "select distinct s.id_rol_agente as id,"
            + " per.nombres_o_razon_social as nombre"
            + " from (" + RAMAS_TEXTO + ") x"
            + " join contrato_alquiler c on c.id_contrato_alquiler = x.id"
            + " join solicitud_alquiler s on s.id_solicitud = c.id_solicitud"
            + " join persona_rol rol on rol.id_persona_rol = s.id_rol_agente"
            + " join persona per on per.id_persona = rol.id_persona"
            + " order by per.nombres_o_razon_social",
            nativeQuery = true)
    List<AgenteConCierres> agentesDeCierresPorTexto(@Param("idOrganizacion") long idOrganizacion,
                                                    @Param("sinScope") boolean sinScope,
                                                    @Param("porAgente") boolean porAgente,
                                                    @Param("roles") String roles,
                                                    @Param("texto") String texto,
                                                    @Param("distrito") String distrito,
                                                    @Param("idAgente") Long idAgente);

    /**
     * Proyeccion completa de la pagina ya resuelta por texto: acceso por clave.
     * El orden lo repone el service, porque un {@code in} no lo conserva.
     */
    @Query(FICHA + " where c.organizacionId = :idOrganizacion and c.id in :ids")
    List<ContratoAlquiler> buscarFichaPorIds(@Param("idOrganizacion") long idOrganizacion,
                                             @Param("ids") Collection<Long> ids);

    @Query(FICHA + " where c.organizacionId = :idOrganizacion and c.id = :id")
    Optional<ContratoAlquiler> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                           @Param("id") long id);

    @Query(FICHA + " where c.organizacionId = :idOrganizacion and op.id = :idOportunidad"
            + " and not exists (select s.id from ContratoAlquiler s where s.contratoAnterior = c)")
    Optional<ContratoAlquiler> buscarPorOportunidad(@Param("idOrganizacion") long idOrganizacion,
                                                    @Param("idOportunidad") long idOportunidad);

    /** Precondicion de la cascada: una oportunidad no admite un segundo contrato. */
    @Query("""
            select count(c) > 0 from ContratoAlquiler c
            where c.organizacionId = :idOrganizacion and c.oportunidad.id = :idOportunidad
              and c.contratoAnterior is null
            """)
    boolean existeDeOportunidad(@Param("idOrganizacion") long idOrganizacion,
                                @Param("idOportunidad") long idOportunidad);

    boolean existsByOrganizacionIdAndContratoAnteriorId(long idOrganizacion, long idContratoAnterior);

    /**
     * Contratos VIVOS de una propiedad, excluyendo uno.
     *
     * <p>Vivo es {@code D} firmado o {@code V} vigente: son los que ocupan el
     * inmueble. Se usa para no devolver al mercado un local que un contrato
     * sucesor sigue ocupando —el caso de la renovacion, donde el anterior queda
     * en {@code R} y el sucesor nace {@code D}—. El indice parcial
     * {@code uq_contrato_vivo_por_propiedad} es la ultima barrera; esta
     * consulta existe para dar un error funcional en vez de una violacion de
     * unicidad.
     */
    @Query("""
            select count(c) from ContratoAlquiler c
            where c.organizacionId = :idOrganizacion
              and c.idPropiedad = :idPropiedad
              and c.id <> :idContratoExcluido
              and c.estadoContrato in ('D', 'V')
            """)
    long contarVivosDePropiedad(@Param("idOrganizacion") long idOrganizacion,
                                @Param("idPropiedad") long idPropiedad,
                                @Param("idContratoExcluido") long idContratoExcluido);

    /**
     * Disparador 3 de la bandeja (F7): contratos del agente cuya comision ya
     * tiene monto asignado y sigue PENDIENTE, es decir <b>lista para cobro</b>.
     * La v1 traia todos los contratos del agente y despues sus liquidaciones
     * en bloque; aqui el join resuelve las dos condiciones de una vez.
     *
     * <p>El alcance es <b>por agente de la solicitud</b>, igual que el listado
     * de contratos para un AGENTE (§7 de F4).
     */
    @Query("""
            select c.id as entidadId, s.codigoSolicitud as entidadCodigo,
                   c.fechaCierre as fechaPlazo, cast(null as string) as marca
            from ContratoAlquiler c
              join c.solicitud s
              join ComisionLiquidacion com on com.contrato.id = c.id
            where c.organizacionId = :idOrganizacion
              and s.agente.id = :idRolAgente
              and com.estado in ('P', 'R')
              and com.parteAgente is not null
            """)
    List<CandidatoTarea> conComisionListaParaCobro(@Param("idOrganizacion") long idOrganizacion,
                                                   @Param("idRolAgente") long idRolAgente);

    @Query(FICHA + """
             where c.organizacionId = :idOrganizacion
               and cli.id = :idCliente
             order by c.id
            """)
    List<ContratoAlquiler> listarFichaPorCliente(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idCliente") long idCliente);

    @Query(FICHA + """
             where c.organizacionId = :idOrganizacion
               and propRol.id = :idPropietario
             order by c.id
            """)
    List<ContratoAlquiler> listarFichaPorPropietario(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idPropietario") long idPropietario);

    /**
     * Los contratos de UNOS ENCARGOS concretos: actividad de la ficha
     * universal.
     *
     * <p>Es el unico de los cinco procesos que <b>no</b> se podia alcanzar
     * desde el cliente: {@code GET /contratos} filtra por texto, distrito y
     * agente, y no por captacion ni por propiedad. Resolverlo desde el SPA
     * habria significado barrer la lista de cierres y cribarla a mano.
     */
    @Query(FICHA + """
             where c.organizacionId = :idOrganizacion
               and op.captacion.id in :idsEncargos
             order by c.fechaCierre desc, c.id desc
            """)
    List<ContratoAlquiler> listarFichaPorEncargos(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idsEncargos") Collection<Long> idsEncargos);

    /**
     * Base de contratos de los indicadores E4. <b>No lleva filtro de rol a
     * proposito</b>: el contrato no tiene agente propio y la v1 decide el
     * alcance comparando las dos ramas heredadas (solicitud y oportunidad),
     * asi que las dos viajan y el filtro se aplica arriba (§2 del contrato E4).
     * Es la entidad mas escasa del modelo, de modo que leer el tenant entero no
     * es el escaneo que RC-003 vino a matar.
     */
    @Query("""
            select c.id as id, c.fechaCierre as fechaCierre,
                   sol.agente.id as idAgenteSolicitud,
                   solOp.captacion.id as idCaptacionSolicitud,
                   op.agente.id as idAgenteOportunidad,
                   op.captacion.id as idCaptacionOportunidad
            from ContratoAlquiler c
              join c.oportunidad op
              left join c.solicitud sol
              left join sol.oportunidad solOp
            where c.organizacionId = :idOrganizacion
            """)
    List<IndicadorContrato> indicadores(@Param("idOrganizacion") long idOrganizacion);

    /**
     * Fuente de cierres del seguimiento comercial: el tenant completo. La fila
     * del cierre se arma <b>desde la solicitud</b>, no desde el contrato, asi
     * que lo que se trae es el grafo de ELLA —su oportunidad, su cliente, su
     * captacion y su agente—. La v1 traia los contratos y despues buscaba la
     * solicitud de cada uno por id (N+1); aqui viene en el mismo select.
     */
    @Query("""
            select c from ContratoAlquiler c
              join fetch c.solicitud s
              join fetch s.agente ag
              join fetch ag.rol agRol
              join fetch agRol.persona
              join fetch s.oportunidad solOp
              join fetch solOp.cliente cli
              join fetch cli.rol cliRol
              join fetch cliRol.persona
              join fetch solOp.captacion solCap
              join fetch solCap.propiedad
            where c.organizacionId = :idOrganizacion
            """)
    List<ContratoAlquiler> listarSeguimiento(@Param("idOrganizacion") long idOrganizacion);
}
