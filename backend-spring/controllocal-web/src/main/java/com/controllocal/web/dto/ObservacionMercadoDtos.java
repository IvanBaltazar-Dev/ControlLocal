package com.controllocal.web.dto;

import com.controllocal.service.ObservacionMercadoService;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * El cable de lo que se <b>vio</b> del mercado (V76).
 *
 * <p>Deliberadamente parecido y deliberadamente distinto del historico del
 * encargo. Parecido porque un importe con su moneda y su operacion se lee igual;
 * distinto porque esto no tiene {@code hito}: no hay nada que autorizar,
 * publicar ni cerrar. <b>Se vio, y ya.</b>
 */
public final class ObservacionMercadoDtos {

    private ObservacionMercadoDtos() {
    }

    /**
     * @param fechaObservada cuando se vio, no cuando se anota
     * @param operacion      VENTA o ALQUILER, con palabras y sin defecto
     * @param fuente         de donde salio. Obligatoria: sin fuente es un rumor
     * @param detalle        el enlace del aviso, la referencia del cartel, la nota
     */
    public record ObservacionRequest(LocalDate fechaObservada, String operacion,
                                     BigDecimal importe, String moneda, String fuente,
                                     String detalle) {

        public ObservacionMercadoService.DatosObservacion aDatos(long idPropiedad) {
            return new ObservacionMercadoService.DatosObservacion(idPropiedad, fechaObservada,
                    operacion, importe, moneda, fuente, detalle);
        }
    }

    /**
     * @param importeRotulo «precio de venta» o «renta mensual». Viaja porque el
     *                      nombre del importe lo decide la OPERACION: con el
     *                      ternario escrito en el cliente habria uno por
     *                      interfaz, y un precio de venta rotulado «renta» es un
     *                      error de bulto (D-A-1 §5)
     */
    public record ObservacionResponse(Long id, LocalDate fechaObservada, String operacion,
                                      String operacionRotulo, BigDecimal importe, String moneda,
                                      String importeRotulo, String fuente, String detalle,
                                      Long idRolActor, String actorNombre) {

        public static ObservacionResponse desde(ObservacionMercadoService.FichaObservacion f) {
            return new ObservacionResponse(f.id(), f.fechaObservada(), f.operacion(),
                    f.operacionRotulo(), f.importe(), f.moneda(), f.importeRotulo(),
                    f.fuente(), f.detalle(), f.idRolActor(), f.actorNombre());
        }
    }
}
