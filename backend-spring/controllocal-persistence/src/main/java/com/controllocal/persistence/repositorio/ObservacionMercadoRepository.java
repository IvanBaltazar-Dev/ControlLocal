package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.ObservacionMercado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Las observaciones de mercado de una propiedad (V76).
 *
 * <p><b>No hay borrado ni actualizacion, y la ausencia es la regla.</b> Una
 * observacion es un hecho fechado: corregirla borraria la muestra que la hace
 * util. Cuando el precio cambia se observa otra vez, y las dos filas juntas son
 * las que dicen como se movio. La base lo impone con un trigger; aqui
 * sencillamente no existen los metodos.
 */
public interface ObservacionMercadoRepository extends JpaRepository<ObservacionMercado, Long> {

    /**
     * Lo observado de una propiedad, de lo mas reciente a lo mas antiguo.
     *
     * <p>Ese orden y no el cronologico porque la pregunta que se hace es «a
     * cuanto esta», y la respuesta es la ultima observacion. La serie completa
     * sigue estando debajo, que es lo que dice como llego hasta ahi.
     */
    List<ObservacionMercado> findByIdPropiedadOrderByFechaObservadaDescIdDesc(long idPropiedad);

    /**
     * De un lote de propiedades, en una sola consulta.
     *
     * <p>Existe antes de tener consumidor a proposito: en cuanto un listado
     * quiera pintar «visto a» junto a cada fila, la via por lote ya esta, y no
     * hace falta el N+1 que RC-003 retiro del repositorio.
     */
    @Query("""
            select o from ObservacionMercado o
            where o.idPropiedad in :ids
            order by o.idPropiedad asc, o.fechaObservada desc, o.id desc
            """)
    List<ObservacionMercado> deVarias(@Param("ids") Collection<Long> ids);
}
