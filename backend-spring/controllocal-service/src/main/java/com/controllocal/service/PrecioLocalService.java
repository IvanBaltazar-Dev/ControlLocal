package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Historico de precios del local por hito comercial (E/R/U/P/O/A/C).
 */
public interface PrecioLocalService {

    /**
     * Datos de un hito del historico.
     *
     * <p>{@code operacion} es <b>VENTA</b> o <b>ALQUILER</b> (D-E4-1). Es
     * opcional en el cuerpo y no por comodidad: si la propiedad tiene un unico
     * encargo vivo, la operacion se lee de ahi, que es la fuente que el modelo
     * declara. Lo que ya <b>no</b> ocurre es que se suponga alquiler cuando no
     * hay de donde deducirla — eso archivaba un precio de venta en la serie de
     * alquiler sin que nadie pudiera notarlo.
     */
    record DatosPrecio(String hito, String moneda, BigDecimal monto, LocalDate fecha,
                       String operacion) {

        /** Compatibilidad con los llamantes que no declaran operacion. */
        public DatosPrecio(String hito, String moneda, BigDecimal monto, LocalDate fecha) {
            this(hito, moneda, monto, fecha, null);
        }
    }

    /** Un hito ya escrito, con la operacion a la que pertenece. */
    record FichaPrecio(Long id, Long idLocal, String hito, String moneda, BigDecimal monto,
                       LocalDate fecha, LocalDateTime fechaCreacion, String operacion) {
    }

    List<FichaPrecio> listarPorLocal(long idPropiedad);

    /** El {@code actor} aporta la organizacion que se estampa en el hito nuevo (D-20). */
    FichaPrecio registrar(long idPropiedad, DatosPrecio datos, Actor actor);
}
