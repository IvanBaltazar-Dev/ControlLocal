package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.PrecioPropiedad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrecioPropiedadRepository extends JpaRepository<PrecioPropiedad, Long> {

    /** Historico cronologico del local (mismo orden que la v1: fecha, id). */
    List<PrecioPropiedad> findByIdPropiedadOrderByFechaAscIdAsc(long idPropiedad);

    /**
     * Ultimo hito de un tipo. Lo usa E0.2 para no duplicar la renta publicada:
     * ordena por id ademas de por fecha porque varios hitos del mismo dia son
     * normales —una edicion de precio y su propagacion a la publicacion caen
     * ambas hoy— y solo el id los desempata en el orden real de escritura.
     */
    Optional<PrecioPropiedad> findFirstByIdPropiedadAndHitoOrderByFechaDescIdDesc(
            long idPropiedad, String hito);

    /**
     * <b>Lo mismo, pero por ENCARGO</b> (WP-A). Es la consulta que congela la
     * referencia de una oferta (D-N-1), y tiene que ser por encargo y no por
     * propiedad: un inmueble en venta Y en alquiler tiene dos series, y
     * preguntar por la propiedad devolveria la renta mensual como referencia de
     * una oferta de compra.
     *
     * <p>Mismo desempate que la de arriba —fecha y despues id—: varios hitos del
     * mismo dia son normales y solo el id los ordena como se escribieron.
     */
    Optional<PrecioPropiedad> findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(
            long idCaptacion, String hito);

    /** Los hitos de una vuelta concreta de la negociacion. */
    List<PrecioPropiedad> findByIdOfertaOrderByIdAsc(long idOferta);
}
