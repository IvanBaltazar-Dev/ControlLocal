package com.controllocal.web.dto;

import com.controllocal.service.SolicitudService;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Contrato CONGELADO: espejo de Dtos.SolicitudRequest de la v1.
 *
 * <p>Ojo con lo que NO se hace aqui: la v1 derivaba {@code plazoTentativo} de
 * {@code plazoMeses} y parseaba {@code formaPago} dentro de {@code aEntidad},
 * pero en la v2 las dos cosas viven en {@code SolicitudServiceImpl} (con el
 * mensaje del cable, "Valor invalido para forma de pago: {x}"). Este record es
 * transporte puro: no valida ni deriva, para no duplicar el mensaje.
 */
public record SolicitudRequest(String codigoSolicitud, LocalDate fechaRegistro, BigDecimal montoPropuesto,
                               String moneda,
                               String plazoTentativo, String observaciones, LocalDate fechaVigenciaOferta,
                               Long idOportunidad, Integer plazoMeses, LocalDate fechaInicio,
                               String formaPago, Integer mesesGarantia, Integer mesesAdelanto) {

    public SolicitudService.DatosSolicitud aDatos() {
        return new SolicitudService.DatosSolicitud(codigoSolicitud, fechaRegistro, montoPropuesto, moneda,
                plazoTentativo, observaciones, fechaVigenciaOferta, idOportunidad, plazoMeses,
                fechaInicio, formaPago, mesesGarantia, mesesAdelanto);
    }
}
