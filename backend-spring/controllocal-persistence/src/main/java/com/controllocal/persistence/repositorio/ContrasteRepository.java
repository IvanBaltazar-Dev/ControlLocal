package com.controllocal.persistence.repositorio;

import com.controllocal.domain.organizacion.Organizacion;
import com.controllocal.persistence.query.MediaPropia;
import com.controllocal.persistence.query.RangoDeRenta;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * Las consultas que sitúan un dato contra la operacion de la propia casa.
 *
 * <h2>Todo sale de la base de la organizacion</h2>
 *
 * <p>Ni una cifra de aqui viene de fuera. El rango es el de <b>nuestras</b>
 * propiedades en esa zona y ese tramo; las medias son las de <b>nuestro</b>
 * equipo. Es lo que hace que el dato pese y lo que permite comprobarlo abriendo
 * las filas que lo componen.
 *
 * <h2>Y todo puede no haber</h2>
 *
 * <p>Las cuatro consultas devuelven su <b>N</b> junto al resultado, siempre. Sin
 * N, el servicio no puede distinguir «la media es 1 de cada 3» de «hubo una
 * visita y acabo en propuesta», y esa distincion es la diferencia entre informar
 * y aparentar.
 */
public interface ContrasteRepository extends Repository<Organizacion, Long> {

    /**
     * El rango de renta <b>publicada</b> de una zona y un tramo de metraje.
     *
     * <h3>Manda el hito P, y no hay sustituto</h3>
     *
     * <p>Solo cuenta {@code hito = 'P'}: la renta que el mercado VE. {@code U}
     * —lo que el propietario autoriza pedir— es otra cosa y muchas veces otro
     * importe; sustituir uno por otro cuando falta el primero daria un rango que
     * se presenta como «lo que se pide» y es «lo que se autorizo pedir».
     *
     * <p>Medido el 2026-08-19: hay <b>cero</b> hitos {@code P} en la base, con
     * cinco publicaciones vivas. El productor existe desde E0.2 pero las
     * publicaciones son anteriores. Asi que hoy esta consulta devuelve N=0 y el
     * contraste degrada — que es la respuesta correcta, no un fallo.
     *
     * <h3>Una observacion por propiedad</h3>
     *
     * <p>{@code distinct on} se queda con el hito {@code P} mas reciente de cada
     * propiedad. Sin eso, una propiedad republicada cinco veces pesaria cinco
     * veces en el rango y la casa se estaria comparando consigo misma.
     */
    @Query(value = """
            with observacion as (
                select distinct on (pp.id_propiedad)
                       pp.id_propiedad as id_propiedad,
                       pp.monto        as monto
                  from precio_propiedad pp
                  join propiedad pr on pr.id_propiedad = pp.id_propiedad
                 where pp.organizacion_id = :idOrganizacion
                   and pp.hito = 'P'
                   and pp.operacion = 'A'
                   and pp.moneda = :moneda
                   and pr.distrito = :zona
                   and pr.metraje >= :metrajeDesde
                   and pr.metraje <  :metrajeHasta
                 order by pp.id_propiedad, pp.fecha desc, pp.id_precio desc
            )
            select count(*)    as observaciones,
                   min(monto)  as minimo,
                   max(monto)  as maximo
              from observacion
            """, nativeQuery = true)
    RangoDeRenta rangoDeRenta(@Param("idOrganizacion") long idOrganizacion,
                              @Param("zona") String zona,
                              @Param("metrajeDesde") BigDecimal metrajeDesde,
                              @Param("metrajeHasta") BigDecimal metrajeHasta,
                              @Param("moneda") String moneda);

    /**
     * Propuestas por visita: cuantas visitas <b>realizadas</b> acabaron en
     * solicitud.
     *
     * <p>El denominador son las visitas en estado {@code R}, no las agendadas.
     * Una visita programada que todavia no ocurrio no puede haber producido nada,
     * y contarla como fracaso castigaria al agente por tener agenda.
     *
     * <p>Medido el 2026-08-19: las 8 visitas de la base estan en {@code P}
     * (programada) con resultado nulo. <b>Cero visitas realizadas</b>, asi que la
     * media no existe. Que es distinto de que valga cero.
     */
    @Query(value = """
            select count(distinct v.id_visita)    filter (where v.estado = 'R') as base,
                   count(distinct s.id_solicitud) filter (where v.estado = 'R') as casos,
                   null::numeric                                                as valor
              from visita v
              left join solicitud_alquiler s
                     on s.id_oportunidad = v.id_oportunidad
                    and s.fecha_registro >= v.fecha_visita
             where v.organizacion_id = :idOrganizacion
               and (:sinScope = true or v.id_rol_agente in (:rolesAgente))
            """, nativeQuery = true)
    MediaPropia propuestasPorVisita(@Param("idOrganizacion") long idOrganizacion,
                                    @Param("sinScope") boolean sinScope,
                                    @Param("rolesAgente") Collection<Long> rolesAgente);

    /**
     * Dias desde que se registra la solicitud hasta que se firma el contrato.
     *
     * <h3>La cronologia imposible se descarta, no se promedia</h3>
     *
     * <p>{@code c.fecha_cierre >= s.fecha_registro} no es una precaucion
     * teorica: los cuatro contratos de la base cierran <b>un dia antes</b> de que
     * se registre su oportunidad. Sin este filtro, «dias hasta contrato» seria un
     * numero negativo presentado como media de la casa.
     *
     * <p>Se mide desde la <b>solicitud</b> y no desde la oportunidad porque es el
     * hito formal del que arranca el cierre: la oportunidad puede llevar meses
     * abierta sin que nadie haya pedido nada.
     */
    @Query(value = """
            select count(*)                                                      as base,
                   count(*)                                                      as casos,
                   avg(c.fecha_cierre - s.fecha_registro::date)                  as valor
              from contrato_alquiler c
              join solicitud_alquiler s on s.id_solicitud = c.id_solicitud
             where c.organizacion_id = :idOrganizacion
               and c.fecha_cierre is not null
               and c.fecha_cierre >= s.fecha_registro::date
               and (:sinScope = true or c.id_rol_agente_cierre in (:rolesAgente))
            """, nativeQuery = true)
    MediaPropia diasHastaContrato(@Param("idOrganizacion") long idOrganizacion,
                                  @Param("sinScope") boolean sinScope,
                                  @Param("rolesAgente") Collection<Long> rolesAgente);

    /**
     * Cada cuantos dias vuelve a contactarse <b>de verdad</b> una prospeccion.
     *
     * <p>No confundir con {@code PoliticaComercial.RECONTACTO}, que son los 7
     * dias que la casa <b>se propone</b>. Esto es lo que de hecho tarda, y el
     * contraste entre las dos cifras es justamente la informacion.
     *
     * <p>Sale de los intervalos entre interacciones consecutivas sobre la misma
     * prospeccion. Medido el 2026-08-19: hay <b>cero</b> interacciones con
     * {@code id_prospeccion}, con 63 prospecciones en la base, asi que la media
     * no existe todavia. Se descartan los intervalos de cero dias —dos apuntes
     * del mismo dia son un apunte doble, no dos contactos— porque incluirlos
     * daria «tu casa recontacta en 0 dias».
     */
    @Query(value = """
            with contacto as (
                select i.id_prospeccion as id_prospeccion,
                       i.fecha_hora     as fecha_hora,
                       lag(i.fecha_hora) over (partition by i.id_prospeccion
                                               order by i.fecha_hora) as anterior
                  from interaccion_comercial i
                 where i.organizacion_id = :idOrganizacion
                   and i.id_prospeccion is not null
                   and (:sinScope = true or i.id_rol_agente in (:rolesAgente))
            )
            select count(*)                                                      as base,
                   count(*)                                                      as casos,
                   avg(extract(epoch from (fecha_hora - anterior)) / 86400)      as valor
              from contacto
             where anterior is not null
               and fecha_hora::date > anterior::date
            """, nativeQuery = true)
    MediaPropia plazoRealDeRecontacto(@Param("idOrganizacion") long idOrganizacion,
                                      @Param("sinScope") boolean sinScope,
                                      @Param("rolesAgente") Collection<Long> rolesAgente);

    /** Las zonas y tramos que hoy tienen muestra, para saber donde SI hay rango. */
    @Query(value = """
            select pr.distrito as zona, count(distinct pp.id_propiedad) as observaciones
              from precio_propiedad pp
              join propiedad pr on pr.id_propiedad = pp.id_propiedad
             where pp.organizacion_id = :idOrganizacion
               and pp.hito = 'P'
               and pp.operacion = 'A'
             group by pr.distrito
             order by count(distinct pp.id_propiedad) desc
            """, nativeQuery = true)
    List<Object[]> coberturaDelRango(@Param("idOrganizacion") long idOrganizacion);
}
