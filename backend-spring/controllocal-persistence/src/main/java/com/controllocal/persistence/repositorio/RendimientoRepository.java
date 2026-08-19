package com.controllocal.persistence.repositorio;

import com.controllocal.domain.organizacion.Organizacion;
import com.controllocal.persistence.query.CierrePosible;
import com.controllocal.persistence.query.ConteoKpi;
import com.controllocal.persistence.query.ConteoKpiPorAgente;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Las consultas del rendimiento comercial: los cuatro KPI canonicos del mes y lo
 * que puede cerrarse.
 *
 * <h2>Por que estan juntas y en SQL</h2>
 *
 * <p>Cuenta <b>PostgreSQL</b>, no Java. {@code IndicadorServiceImpl} descarga
 * las listas completas y filtra en memoria —un patron que RC-003 vino a
 * retirar—; lo nuevo no lo hereda. Ademas, los cuatro KPI en una sola consulta
 * garantizan que se midan contra el mismo instante y el mismo alcance: cuatro
 * viajes podrian caer a los dos lados de una escritura.
 *
 * <p>Cuelga de {@link Organizacion} porque la pregunta es siempre «esta
 * organizacion, este mes, estos agentes». La frontera de tenant es el primer
 * termino de los cuatro {@code WHERE}, no un filtro que se anade despues.
 *
 * <h2>El alcance</h2>
 *
 * <p>{@code sinScope} es el ADMIN: sin filtro de rol <b>dentro</b> de su
 * organizacion. Nunca levanta el tenant. Para agente y broker,
 * {@code rolesAgente} trae los roles que alcanza —el suyo, o los que supervisa—.
 */
public interface RendimientoRepository extends Repository<Organizacion, Long> {

    /**
     * Los cuatro KPI canonicos del mes, cada uno contra el <b>evento</b> que lo
     * define y no contra un estado parecido.
     *
     * <ul>
     *   <li><b>C · Propietarios contactados</b> — {@code prospeccion.fecha_contacto}.
     *       No el estado: {@code D} (descartado) se sale de la escalera y SI
     *       hubo contacto. Contar por estado perderia esos casos y premiaria
     *       dejar la prospeccion a medias.</li>
     *   <li><b>P · Propiedades captadas</b> — la transicion {@code -> A} de
     *       {@code historial_estado}. No el estado actual, que perderia las que
     *       ya cerraron en contrato, ni {@code fecha_captacion}, que es cuando
     *       se registro y no cuando el broker la aprobo. Medido el 2026-08-19:
     *       por estado salen 5 y por evento salen 9, y las 9 son las que de
     *       verdad entraron a cartera.</li>
     *   <li><b>S · Solicitudes ingresadas</b> — {@code fecha_registro}.</li>
     *   <li><b>F · Contratos firmados</b> — {@code fecha_cierre}, que es la firma.</li>
     * </ul>
     *
     * <p>Un KPI sin filas <b>no aparece</b> en el resultado. El servicio lo
     * completa con cero, que aqui si es cero de verdad: la ausencia de eventos
     * es un hecho medido, no un dato que falte.
     */
    @Query(value = """
            select 'C' as kpi, count(*) as cantidad
              from prospeccion p
             where p.organizacion_id = :idOrganizacion
               and p.fecha_contacto between :desde and :hasta
               and (:sinScope = true or p.id_rol_agente in (:rolesAgente))
            union all
            select 'P', count(*)
              from historial_estado h
              join captacion c on c.id_captacion = h.id_entidad
             where h.entidad_tipo = 'CAPTACION'
               and h.estado_nuevo = 'A'
               and c.organizacion_id = :idOrganizacion
               and h.fecha_evento::date between :desde and :hasta
               and (:sinScope = true or c.id_rol_agente in (:rolesAgente))
            union all
            select 'S', count(*)
              from solicitud_alquiler s
             where s.organizacion_id = :idOrganizacion
               and s.fecha_registro::date between :desde and :hasta
               and (:sinScope = true or s.id_rol_agente in (:rolesAgente))
            union all
            select 'F', count(*)
              from contrato_alquiler t
             where t.organizacion_id = :idOrganizacion
               and t.fecha_cierre between :desde and :hasta
               and (:sinScope = true or t.id_rol_agente_cierre in (:rolesAgente))
            """, nativeQuery = true)
    List<ConteoKpi> kpisDelPeriodo(@Param("idOrganizacion") long idOrganizacion,
                                   @Param("sinScope") boolean sinScope,
                                   @Param("rolesAgente") Collection<Long> rolesAgente,
                                   @Param("desde") LocalDate desde,
                                   @Param("hasta") LocalDate hasta);

    /**
     * Los mismos cuatro KPI, <b>desglosados por agente</b>. Es lo que necesita el
     * pulso del equipo: un total verde puede esconder dos agentes que producen
     * todo y tres que no producen nada (D-E2-2 §6.1).
     *
     * <p>Es una consulta y no N: preguntar agente por agente daria una foto
     * distinta por cada uno y, con ocho agentes, ocho viajes para dibujar una
     * franja de una sola linea.
     */
    @Query(value = """
            select p.id_rol_agente as idAgente, 'C' as kpi, count(*) as cantidad
              from prospeccion p
             where p.organizacion_id = :idOrganizacion
               and p.fecha_contacto between :desde and :hasta
               and (:sinScope = true or p.id_rol_agente in (:rolesAgente))
             group by p.id_rol_agente
            union all
            select c.id_rol_agente, 'P', count(*)
              from historial_estado h
              join captacion c on c.id_captacion = h.id_entidad
             where h.entidad_tipo = 'CAPTACION'
               and h.estado_nuevo = 'A'
               and c.organizacion_id = :idOrganizacion
               and h.fecha_evento::date between :desde and :hasta
               and (:sinScope = true or c.id_rol_agente in (:rolesAgente))
             group by c.id_rol_agente
            union all
            select s.id_rol_agente, 'S', count(*)
              from solicitud_alquiler s
             where s.organizacion_id = :idOrganizacion
               and s.fecha_registro::date between :desde and :hasta
               and (:sinScope = true or s.id_rol_agente in (:rolesAgente))
             group by s.id_rol_agente
            union all
            select t.id_rol_agente_cierre, 'F', count(*)
              from contrato_alquiler t
             where t.organizacion_id = :idOrganizacion
               and t.fecha_cierre between :desde and :hasta
               and (:sinScope = true or t.id_rol_agente_cierre in (:rolesAgente))
             group by t.id_rol_agente_cierre
            """, nativeQuery = true)
    List<ConteoKpiPorAgente> kpisPorAgente(@Param("idOrganizacion") long idOrganizacion,
                                           @Param("sinScope") boolean sinScope,
                                           @Param("rolesAgente") Collection<Long> rolesAgente,
                                           @Param("desde") LocalDate desde,
                                           @Param("hasta") LocalDate hasta);

    /**
     * Lo que <b>puede</b> cerrarse: determinista, no pronostico.
     *
     * <p>Tres condiciones, y las tres son hechos comprobables:
     *
     * <ol>
     *   <li>{@code estado = 'A'} — <b>aprobada</b>. Es la fase formal de cierre:
     *       el broker ya evaluo y lo que falta es firmar. Una solicitud en
     *       revision ({@code E}) u observada ({@code O}) tiene un bloqueante sin
     *       resolver; una registrada ({@code G}) ni siquiera entro a revision. Y
     *       una oportunidad o una visita no entran de ninguna manera.</li>
     *   <li><b>Sin contrato todavia</b>. Con contrato ya no puede cerrarse: se
     *       cerro, y contarla de nuevo sumaria dos veces el mismo dinero.</li>
     *   <li><b>Oferta vigente</b>. Una oferta cuya vigencia paso no se puede
     *       firmar, por muy aprobada que este.</li>
     * </ol>
     *
     * <p><b>El importe sale de la propia operacion</b> ({@code monto_propuesto})
     * y conserva su moneda. No se convierte a dolares para que la cifra quede
     * redonda: una conversion sin tipo de cambio declarado es un numero
     * inventado. Se agrupa POR MONEDA, y si hay dos, el servicio lo dice en vez
     * de sumar peras con manzanas.
     *
     * <p>Medido el 2026-08-19 con esta definicion: <b>cero operaciones</b>. La
     * unica solicitud viva estaba en revision y ademas con la oferta vencida el
     * dia 15. La maqueta ensenaba «3 operaciones · US$ 9,300»; eran fijos.
     */
    @Query(value = """
            select s.moneda            as moneda,
                   count(*)            as operaciones,
                   sum(s.monto_propuesto) as importe
              from solicitud_alquiler s
             where s.organizacion_id = :idOrganizacion
               and s.estado = 'A'
               and (:sinScope = true or s.id_rol_agente in (:rolesAgente))
               and not exists (select 1 from contrato_alquiler c
                                where c.id_solicitud = s.id_solicitud)
               and (s.fecha_vigencia_oferta is null or s.fecha_vigencia_oferta >= :hoy)
             group by s.moneda
             order by sum(s.monto_propuesto) desc
            """, nativeQuery = true)
    List<CierrePosible> cierrePosible(@Param("idOrganizacion") long idOrganizacion,
                                      @Param("sinScope") boolean sinScope,
                                      @Param("rolesAgente") Collection<Long> rolesAgente,
                                      @Param("hoy") LocalDate hoy);

    /**
     * Las solicitudes que esperan una decision del broker. Es su palanca sobre la
     * cifra en juego (D-E2-2 §8): lo unico de esa franja sobre lo que el actua
     * directamente.
     */
    @Query(value = """
            select count(*)
              from solicitud_alquiler s
             where s.organizacion_id = :idOrganizacion
               and s.estado in ('E', 'O')
               and (:sinScope = true or s.id_rol_agente in (:rolesAgente))
            """, nativeQuery = true)
    int esperanDecision(@Param("idOrganizacion") long idOrganizacion,
                        @Param("sinScope") boolean sinScope,
                        @Param("rolesAgente") Collection<Long> rolesAgente);

    /**
     * Los agentes vigentes de la organizacion. Los necesita la cobertura de
     * metas del ADMIN, cuyo alcance no trae lista de roles porque es «todos».
     */
    @Query(value = """
            select r.id_persona_rol
              from persona_rol r
             where r.organizacion_id = :idOrganizacion
               and r.tipo_rol = 'AGENTE'
               and (r.vigencia_hasta is null or r.vigencia_hasta >= :hoy)
             order by r.id_persona_rol
            """, nativeQuery = true)
    List<Long> agentesVigentes(@Param("idOrganizacion") long idOrganizacion,
                               @Param("hoy") LocalDate hoy);
}
