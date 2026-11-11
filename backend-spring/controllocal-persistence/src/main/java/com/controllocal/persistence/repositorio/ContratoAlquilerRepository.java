package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.persistence.query.AgenteConCierres;
import com.controllocal.persistence.query.CandidatoTarea;
import com.controllocal.persistence.query.ComisionGeneradaPorMoneda;
import com.controllocal.persistence.query.IndicadorContrato;
import com.controllocal.persistence.query.MovimientoComisionPorMoneda;
import com.controllocal.persistence.query.RepartoComisionPorMoneda;
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
    /** Sólo datos actuales; esta proyección no concede permiso ni inventa una versión de contrato. */
    interface PreparacionActual {
        Long getId(); String getEstado(); Long getEncargo(); Long getOportunidad(); Long getSolicitud(); Long getAnterior();
        Long getAgenteSolicitud();Long getResponsableEncargo();
        java.math.BigDecimal getRenta();String getMoneda();java.time.LocalDate getInicio();java.time.LocalDate getFin();
    }
    @Query("""
        select c.id as id,c.estadoContrato as estado,cap.id as encargo,op.id as oportunidad,
               s.id as solicitud,a.id as anterior,s.agente.id as agenteSolicitud,cap.agente.id as responsableEncargo,
               c.rentaContractual as renta,c.moneda as moneda,
               c.fechaInicioContrato as inicio,c.fechaFinContrato as fin
          from ContratoAlquiler c join c.oportunidad op join op.captacion cap
          left join c.solicitud s left join c.contratoAnterior a
         where c.organizacionId=:org and c.id=:id
        """)
    Optional<PreparacionActual> preparacionActual(@Param("org")long org,@Param("id")long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContratoAlquiler c where c.organizacionId=:org and c.id=:id")
    Optional<ContratoAlquiler> bloquear(@Param("org")long org,@Param("id")long id);

    /**
     * Solo los joins que el WHERE necesita: es lo que se cuenta, sin traer nada.
     *
     * <p>Solicitud y rol Cliente son opcionales reales; sus LEFT JOIN conservan
     * el mismo conjunto autorizado en ficha, conteo y página.
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
              left join op.cliente cli
              left join cli.rol cliRol
              left join cliRol.persona cliPer
              left join c.solicitud s
              left join s.agente ag
            """;

    /** Lo que {@link #agentesDeCierres} necesita y nadie mas. */
    String JOINS_PERSONA_AGENTE = """
              left join ag.rol agRol
              left join agRol.persona agPer
              left join c.agenteCierre cierreAg
              left join cierreAg.rol cierreRol
              left join cierreRol.persona cierrePer
            """;

    String DESDE_WHERE = """
            where c.organizacionId = :idOrganizacion
              and c.estadoContrato <> 'P' and c.fechaCierre is not null
              and (:sinScope = true
                   or (:porAgente = true and ((s.id is not null and ag.id in :roles) or (s.id is null and capAg.id in :roles)))
                   or (:porAgente = false and capAg.id in :roles))
            """;

    String DESDE = DESDE_JOINS + DESDE_WHERE;

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
              and (:idAgente is null or coalesce(ag.id,c.idRolAgenteCierre) = :idAgente)
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
              left join fetch op.cliente cli
              left join fetch cli.rol cliRol
              left join fetch cliRol.persona cliPer
              join fetch op.captacion cap
              join fetch cap.propiedad prop
              left join fetch prop.rolPropietario propRol
              left join fetch propRol.persona
              join fetch cap.agente capAg
              left join fetch c.solicitud s
              left join fetch s.agente ag
              left join fetch ag.rol agRol
              left join fetch agRol.persona agPer
              join fetch c.agenteCierre agc
              join fetch agc.rol agcRol
              join fetch agcRol.persona
            """;

    String CONDICION = """
             where c.organizacionId = :idOrganizacion
               and c.estadoContrato <> 'P' and c.fechaCierre is not null
               and (:sinScope = true
                    or (:porAgente = true and ((s.id is not null and ag.id in :roles) or (s.id is null and capAg.id in :roles)))
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
    // agente lo decide el service ANTES de llamar aqui (sólo BROKER supervisor;
    // gobierno no obtiene cartera); meter otro scope aqui daria
    // numeros distintos a los de la propia ficha.

    @Query("""
            select count(c) from ContratoAlquiler c
            where c.organizacionId = :idOrganizacion
              and c.idRolAgenteCierre = :idRolAgente
              and c.estadoContrato <> 'P' and c.fechaCierre is not null
            """)
    long contarCierresDeAgente(@Param("idOrganizacion") long idOrganizacion,
                               @Param("idRolAgente") long idRolAgente);

    String HISTORIA_CIERRES_AGENTE="""
            from contrato_alquiler c
            left join comision_liquidacion com on com.organizacion_id=c.organizacion_id and com.id_contrato_alquiler=c.id_contrato_alquiler
            """;
    String FILTRO_HISTORICO_AGENTE="""
            where c.organizacion_id=:idOrganizacion and c.id_rol_agente_cierre=:idRolAgente
              and c.estado_contrato<>'P' and c.fecha_cierre is not null
            """;

    String BASE_ECONOMICA_AGENTE="with base as (select com.id_comision_liquidacion as comision,com.id_ultima_evaluacion,com.id_evaluacion_base_reparto,com.parte_agente "
            +HISTORIA_CIERRES_AGENTE+FILTRO_HISTORICO_AGENTE+"), fondos as materialized ("
            +"select b.comision,m.moneda,sum(case when m.tipo='C' then m.monto when m.tipo='R' then -m.monto end) as recibido,"
            +"sum(case when m.tipo='P' then m.monto end) as pagado,"
            +"bool_or(m.tipo not in ('C','R','P','A') or m.moneda is null or m.moneda!~'^[A-Z]{3}$') as ilegible "
            +"from base b join comision_movimiento m on m.organizacion_id=:idOrganizacion and m.id_comision_liquidacion=b.comision group by b.comision,m.moneda) ";

    @Query(value=BASE_ECONOMICA_AGENTE+", cuantias as (select b.comision,e.resultado::jsonb#>'{calculo,cuantia}' as q "
            +"from base b left join comision_evaluacion e on e.organizacion_id=:idOrganizacion and e.id_comision_liquidacion=b.comision and e.id_comision_evaluacion=b.id_ultima_evaluacion), "
            +"valores as (select comision,q->>'moneda' as moneda,case when q->>'moneda' is not null then (q->>'importe')::numeric end as monto from cuantias), "
            +"saldos as (select v.*,case when monto is not null and not exists(select 1 from fondos f where f.comision=v.comision and f.recibido is not null and (f.moneda<>v.moneda or f.recibido<0 or f.ilegible)) "
            +"then greatest(monto-coalesce((select f.recibido from fondos f where f.comision=v.comision and f.moneda=v.moneda),0),0) end as saldo from valores v) "
            +"select moneda,sum(monto) as monto,sum(saldo) as \"saldoInformativoSobreCalculo\",count(monto) as determinados,count(*)-count(monto) as pendientes,"
            +"count(saldo) as \"saldoDeterminados\",count(*)-count(saldo) as \"saldoPendientes\" from saldos group by moneda order by moneda nulls last",nativeQuery=true)
    List<ComisionGeneradaPorMoneda> comisionesGeneradasDeAgente(@Param("idOrganizacion") long org,@Param("idRolAgente") long agente);

    @Query(value=BASE_ECONOMICA_AGENTE+", valores as (select b.comision,e.resultado::jsonb#>>'{calculo,cuantia,moneda}' as moneda,"
            +"case when e.resultado::jsonb#>>'{calculo,cuantia,moneda}' is not null then b.parte_agente end as monto "
            +"from base b left join comision_evaluacion e on e.organizacion_id=:idOrganizacion and e.id_comision_liquidacion=b.comision and e.id_comision_evaluacion=b.id_evaluacion_base_reparto), "
            +"saldos as (select v.*,case when monto is not null and not exists(select 1 from fondos f where f.comision=v.comision and f.pagado is not null and (f.moneda<>v.moneda or f.ilegible)) "
            +"then greatest(monto-coalesce((select f.pagado from fondos f where f.comision=v.comision and f.moneda=v.moneda),0),0) end as saldo from valores v) "
            +"select moneda,sum(monto) as \"parteAgente\",sum(saldo) as \"saldoPagoAgente\",count(monto) as determinados,count(*)-count(monto) as pendientes,"
            +"count(saldo) as \"saldoDeterminados\",count(*)-count(saldo) as \"saldoPendientes\" from saldos group by moneda order by moneda nulls last",nativeQuery=true)
    List<RepartoComisionPorMoneda> repartosDeAgente(@Param("idOrganizacion") long org,@Param("idRolAgente") long agente);

    // Ingresos C-R y pagos P históricos. No se emite ningún movimiento nuevo.
    // Ledger autorizado conocido: ninguna fila P acredita cero pagado, no una base de cálculo desconocida.
    @Query(value=BASE_ECONOMICA_AGENTE+"select moneda,coalesce(sum(recibido),0) as \"montoCobrado\",coalesce(sum(pagado),0) as \"montoPagadoAgente\","
            +"bool_or(ilegible or coalesce(recibido<0,false)) as anomalia from fondos group by moneda order by moneda nulls last",nativeQuery=true)
    List<MovimientoComisionPorMoneda> movimientosDeAgente(@Param("idOrganizacion") long org,@Param("idRolAgente") long agente);
    /** Cierres del agente para la ficha, del mas reciente al mas antiguo. */
    @Query(value = FICHA + """
            where c.organizacionId = :idOrganizacion
              and c.idRolAgenteCierre = :idRolAgente
              and c.estadoContrato <> 'P' and c.fechaCierre is not null
            """,
            countQuery = """
                    select count(c) from ContratoAlquiler c
                    where c.organizacionId = :idOrganizacion
                      and c.idRolAgenteCierre = :idRolAgente
              and c.estadoContrato <> 'P' and c.fechaCierre is not null
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
    @Query("select distinct coalesce(ag.id,c.idRolAgenteCierre) as id, coalesce(agPer.nombresORazonSocial,cierrePer.nombresORazonSocial) as nombre "
            + DESDE_JOINS + JOINS_PERSONA_AGENTE + DESDE_WHERE + FILTROS
            + " order by coalesce(agPer.nombresORazonSocial,cierrePer.nombresORazonSocial)")
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
               and c.estado_contrato <> 'P' and c.fecha_cierre is not null
               and (:sinScope = true
                    or (:porAgente = true
                        and ((s.id_solicitud is not null and s.id_rol_agente = any(cast(:roles as bigint[])))
                             or (s.id_solicitud is null and cap.id_rol_agente = any(cast(:roles as bigint[])))))
                    or (:porAgente = false
                        and cap.id_rol_agente = any(cast(:roles as bigint[]))))
               and (cast(:distrito as varchar) is null
                    or prop.distrito = cast(:distrito as varchar))
               and (cast(:idAgente as bigint) is null
                    or coalesce(s.id_rol_agente,c.id_rol_agente_cierre) = cast(:idAgente as bigint))
            """;

    /**
     * RF059: la solicitud es opcional real. Su ausencia no oculta el contrato;
     * el alcance de ese caso es el responsable actual del Encargo, igual que
     * el lector canónico. Preparaciones sin firma no pertenecen a este conjunto.
     */
    String DESDE_TEXTO = """
              from contrato_alquiler c
              left join solicitud_alquiler s on s.id_solicitud = c.id_solicitud
              join oportunidad_comercial op on op.id_oportunidad = c.id_oportunidad
              join captacion cap on cap.id_captacion = op.id_captacion
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

    /** Proyección de lectura: cálculo/devengo de la evaluación exacta, fondos del ledger. */
    interface FinanzasCierres {
        String getDimension(); String getMoneda(); java.math.BigDecimal getMonto();
        Long getDeterminados(); Long getPendientes(); Long getCierres();
        Long getConLecturaFinanciera(); Long getSinEvaluacion(); Boolean getAnomalia();
    }

    // seleccion conserva el mismo conjunto que lista/conteo. La autorización financiera
    // se intersecta ANTES de leer la evaluación o los movimientos. Sin comisión, el
    // recurso potencial sigue el Encargo; con ella, su Encargo actual como en Money.
    String FINANZAS_CIERRES = """
            ), poblacion as materialized (
                select c.id_contrato_alquiler as id,cap.id_captacion as encargo,cap.id_rol_agente
                from seleccion x join contrato_alquiler c on c.id_contrato_alquiler=x.id
                join oportunidad_comercial op on op.id_oportunidad=c.id_oportunidad
                join captacion cap on cap.id_captacion=op.id_captacion
                where c.organizacion_id=:idOrganizacion and cap.organizacion_id=:idOrganizacion
            ), financiera as materialized (
                select p.id,p.encargo,com.id_comision_liquidacion as comision,
                       com.id_captacion as encargo_comision,com.id_ultima_evaluacion
                from poblacion p
                left join comision_liquidacion com on com.organizacion_id=:idOrganizacion and com.id_contrato_alquiler=p.id
                left join captacion cap_com on cap_com.organizacion_id=:idOrganizacion and cap_com.id_captacion=com.id_captacion
                where (:sinScope or (case when com.id_comision_liquidacion is null then p.id_rol_agente
                                    else cap_com.id_rol_agente end)=any(cast(:roles as bigint[])))
            ), evaluada as materialized (
                select f.*,e.id_comision_evaluacion as evaluacion,
                       e.resultado::jsonb as material
                from financiera f left join comision_evaluacion e
                  on e.organizacion_id=:idOrganizacion and e.id_comision_liquidacion=f.comision
                 and e.id_comision_evaluacion=f.id_ultima_evaluacion
            ), importes as materialized (
                select e.*,
                  (material#>>'{calculo,cuantia,importe}')::numeric as calculo,
                  material#>>'{calculo,cuantia,moneda}' as moneda_calculo,
                  (material#>>'{devengo,cuantiaAcreditada,importe}')::numeric as devengo,
                  material#>>'{devengo,cuantiaAcreditada,moneda}' as moneda_devengo
                from evaluada e
            ), fondos as materialized (
                select f.comision,m.moneda,
                       sum(case when m.tipo='C' then m.monto when m.tipo='R' then -m.monto end) as recibido
                from financiera f join comision_movimiento m
                  on m.organizacion_id=:idOrganizacion and m.id_comision_liquidacion=f.comision
                where m.tipo in ('C','R') group by f.comision,m.moneda
            ), datos as (
                select 'CALCULADO'::text as dimension,moneda_calculo as moneda,
                       case when moneda_calculo is not null then calculo end as monto from importes
                union all
                select 'DEVENGADO',moneda_devengo,case when moneda_devengo is not null then devengo end from importes
                union all
                select 'RECIBIDO',moneda,recibido from fondos
                union all
                select 'SALDO',i.moneda_calculo,
                  case when i.calculo is not null and i.moneda_calculo is not null
                        and not exists(select 1 from fondos f where f.comision=i.comision and f.moneda<>i.moneda_calculo)
                       then greatest(i.calculo-coalesce((select f.recibido from fondos f
                                where f.comision=i.comision and f.moneda=i.moneda_calculo),0),0) end
                from importes i
            ), control as (
                select (select count(*) from poblacion) as cierres,
                       (select count(*) from financiera) as financieros,
                       (select count(*) from evaluada where id_ultima_evaluacion is null) as sin_evaluacion,
                       (exists(select 1 from evaluada where (id_ultima_evaluacion is not null and evaluacion is null)
                                 or encargo_comision<>encargo)
                        or exists(select 1 from fondos where recibido<0)
                        or exists(select 1 from financiera f join comision_movimiento m
                          on m.organizacion_id=:idOrganizacion and m.id_comision_liquidacion=f.comision
                          where m.tipo not in ('C','R','P','A'))) as anomalia
            )
            select 'CONTADORES' as dimension,null::text as moneda,null::numeric as monto,
                   0::bigint as determinados,0::bigint as pendientes,cierres,
                   financieros as "conLecturaFinanciera",sin_evaluacion as "sinEvaluacion",anomalia from control
            union all
            select dimension,moneda,sum(monto),count(monto),count(*)-count(monto),
                   0::bigint,0::bigint,0::bigint,false
            from datos group by dimension,moneda
            order by dimension,moneda nulls last
            """;

    @Query(value="with seleccion as ("+SELECT_TEXTO+DESDE_TEXTO+COMUN_TEXTO+FINANZAS_CIERRES,nativeQuery=true)
    List<FinanzasCierres> finanzasDeCierres(@Param("idOrganizacion") long org,@Param("sinScope") boolean global,
        @Param("porAgente") boolean porAgente,@Param("roles") String roles,@Param("distrito") String distrito,@Param("idAgente") Long agente);

    @Query(value="with seleccion as ("+RAMAS_TEXTO+FINANZAS_CIERRES,nativeQuery=true)
    List<FinanzasCierres> finanzasDeCierresPorTexto(@Param("idOrganizacion") long org,@Param("sinScope") boolean global,
        @Param("porAgente") boolean porAgente,@Param("roles") String roles,@Param("texto") String texto,
        @Param("distrito") String distrito,@Param("idAgente") Long agente);

    /** Total del MISMO conjunto que paginan las dos variantes de arriba. */
    @Query(value = "select count(*) from (" + RAMAS_TEXTO + ") x", nativeQuery = true)
    long contarPorTexto(@Param("idOrganizacion") long idOrganizacion,
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
    @Query(value = "select distinct coalesce(s.id_rol_agente,c.id_rol_agente_cierre) as id,"
            + " per.nombres_o_razon_social as nombre"
            + " from (" + RAMAS_TEXTO + ") x"
            + " join contrato_alquiler c on c.id_contrato_alquiler = x.id"
            + " left join solicitud_alquiler s on s.id_solicitud = c.id_solicitud"
            + " join persona_rol rol on rol.id_persona_rol = coalesce(s.id_rol_agente,c.id_rol_agente_cierre)"
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
              left join c.solicitud s
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

    /** Mismo conjunto autorizado de cierres; Solicitud/Cliente opcionales, sin N+1. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={
        "solicitud.agente.rol.persona","oportunidad.cliente.rol.persona",
        "oportunidad.captacion.propiedad.rolPropietario.persona","agenteCierre.rol.persona"})
    @Query("select c " + DESDE)
    List<ContratoAlquiler> listarSeguimiento(@Param("idOrganizacion") long idOrganizacion,
        @Param("sinScope") boolean sinScope,@Param("porAgente") boolean porAgente,@Param("roles") Collection<Long> roles);
}
