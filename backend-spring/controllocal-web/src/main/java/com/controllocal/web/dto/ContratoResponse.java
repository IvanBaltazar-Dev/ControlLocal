package com.controllocal.web.dto;

import com.controllocal.service.ContratoService;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Contrato CONGELADO: espejo de Dtos.ContratoResponse de la v1. El contrato es
 * minimo —renta, plazo y fecha de inicio se leen de la solicitud y no se
 * duplican— y la comision (bruta, estado, reparto y cobro) viene de la
 * liquidacion.
 *
 * <p>{@code montoAgente} y {@code montoEmpresa} solo llegan con valor para
 * ADMIN/BROKER: el filtro por rol lo aplica {@code ContratoServiceImpl}, que
 * es quien conoce el alcance. Con Jackson en {@code non_null}, al AGENTE esos
 * dos campos ni siquiera le viajan (misma salida que el JSON-B de la v1).
 */
public record ContratoResponse(Long id, Long idSolicitud, String codigoSolicitud, Long idOportunidad,
                               String codigoOportunidad, String clienteNombre, String direccionLocal,
                               String distritoLocal, String estadoDisponibilidadLocal,
                               String codigoCaptacion, String agenteNombre,
                               BigDecimal rentaMensual, String moneda, Integer plazoContratoMeses,
                               BigDecimal comisionGenerada, String monedaComision,
                               LocalDate fechaInicioContrato,
                               LocalDate fechaFinContrato, LocalDate fechaCierre, String estadoContrato,
                               String comisionEstado, String incidencias, Long idComision, Long agenteId,
                               Long propietarioId, String propietarioNombre, BigDecimal montoAgente,
                               BigDecimal montoEmpresa, String formaPago, LocalDate fechaCobro,
                               Long idContratoAnterior, BigDecimal montoCobrado, BigDecimal saldoCobro,
                               BigDecimal montoPagadoAgente, BigDecimal saldoPagoAgente) {

    public static ContratoResponse desde(ContratoService.FichaContrato f) {
        return new ContratoResponse(f.id(), f.idSolicitud(), f.codigoSolicitud(), f.idOportunidad(),
                f.codigoOportunidad(), f.clienteNombre(), f.direccionLocal(), f.distritoLocal(),
                f.estadoDisponibilidadLocal(), f.codigoCaptacion(), f.agenteNombre(),
                f.rentaMensual(), f.moneda(), f.plazoContratoMeses(), f.comisionGenerada(),
                f.monedaComision(), f.fechaInicioContrato(),
                f.fechaFinContrato(), f.fechaCierre(), f.estadoContrato(), f.comisionEstado(),
                f.incidencias(), f.idComision(), f.agenteId(), f.propietarioId(), f.propietarioNombre(),
                f.montoAgente(), f.montoEmpresa(), f.formaPago(), f.fechaCobro(),
                f.idContratoAnterior(), f.montoCobrado(), f.saldoCobro(), f.montoPagadoAgente(),
                f.saldoPagoAgente());
    }
}
