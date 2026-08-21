package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.AtributoEncargo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Los valores de las condiciones comerciales de cada encargo (V73).
 *
 * <p>Gemelo exacto de {@code AtributoPropiedadRepository}, y esa simetria es el
 * punto: <b>si el escritor enruta por sujeto, el lector tambien</b>. Un lector
 * paralelo que resolviera las condiciones comerciales por su cuenta es la misma
 * clase de fuga que aparecio en la ficha durante el Corte 0B --un importe
 * llegando sin su moneda porque alguien releyo la tabla a mano-- y volveria a
 * aparecer aqui con un sujeto mas.
 *
 * <p>Todas las lecturas de lote van <b>por lote</b>: un expediente con tres
 * encargos cuesta una consulta, no tres.
 */
public interface AtributoEncargoRepository extends JpaRepository<AtributoEncargo, Long> {

    List<AtributoEncargo> findByIdCaptacionOrderByClaveAsc(long idCaptacion);

    Optional<AtributoEncargo> findByIdCaptacionAndClave(long idCaptacion, String clave);

    /** De un lote de encargos, en una sola consulta. */
    @Query("""
            select a from AtributoEncargo a
            where a.idCaptacion in :ids
            order by a.idCaptacion asc, a.clave asc
            """)
    List<AtributoEncargo> deVarios(@Param("ids") Collection<Long> ids);

    /**
     * <b>Que condiciones le faltan a ESTE encargo para poder anunciarse.</b>
     *
     * <p>Mira {@code catalogo_atributo_operacion} y no la tabla por tipo: la
     * exigencia comercial depende de las dos dimensiones. {@code garantia_meses}
     * puede bloquear la publicacion de un alquiler y no significar nada en una
     * venta del mismo inmueble; con una sola dimension habria que elegir entre
     * no exigirla nunca o exigirla tambien donde no aplica.
     *
     * <p>Y el {@code not exists} cuelga de {@code idCaptacion}: lo que se
     * pregunta es si le falta a <b>este</b> episodio, no a la propiedad ni a
     * "los alquileres" en general.
     */
    @Query("""
            select c.clave from CatalogoAtributo c join c.aplicacionesOperacion o
            where c.activo = true
              and c.sujeto = 'ENCARGO'
              and o.exigencia in ('ALT', 'PUB')
              and o.tipoPropiedad = :tipoPropiedad
              and o.tipoOperacion = :tipoOperacion
              and (c.organizacionId is null or c.organizacionId = :idOrganizacion)
              and not exists (select 1 from AtributoEncargo ae
                               where ae.idCaptacion = :idCaptacion and ae.clave = c.clave)
            order by c.orden asc
            """)
    List<String> clavesQueImpidenPublicar(@Param("idOrganizacion") long idOrganizacion,
                                          @Param("idCaptacion") long idCaptacion,
                                          @Param("tipoPropiedad") String tipoPropiedad,
                                          @Param("tipoOperacion") String tipoOperacion);

    /**
     * Lo que le falta para cerrar el alta del encargo. Solo ALT, y por el mismo
     * motivo que en la propiedad: basta que un consumidor lea «lo que no sea
     * OPC» para que registrar un encargo empiece a exigir de golpe todo lo que
     * solo debia exigir el anuncio.
     */
    @Query("""
            select c.clave from CatalogoAtributo c join c.aplicacionesOperacion o
            where c.activo = true
              and c.sujeto = 'ENCARGO'
              and o.exigencia = 'ALT'
              and o.tipoPropiedad = :tipoPropiedad
              and o.tipoOperacion = :tipoOperacion
              and (c.organizacionId is null or c.organizacionId = :idOrganizacion)
              and not exists (select 1 from AtributoEncargo ae
                               where ae.idCaptacion = :idCaptacion and ae.clave = c.clave)
            order by c.orden asc
            """)
    List<String> clavesObligatoriasQueFaltan(@Param("idOrganizacion") long idOrganizacion,
                                             @Param("idCaptacion") long idCaptacion,
                                             @Param("tipoPropiedad") String tipoPropiedad,
                                             @Param("tipoOperacion") String tipoOperacion);

    void deleteByIdCaptacionAndClave(long idCaptacion, String clave);
}
