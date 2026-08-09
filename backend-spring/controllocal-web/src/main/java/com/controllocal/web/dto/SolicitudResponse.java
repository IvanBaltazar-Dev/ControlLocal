package com.controllocal.web.dto;

import com.controllocal.service.SolicitudService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Contrato CONGELADO: espejo de Dtos.SolicitudResponse de la v1. Los dos
 * ultimos campos son el contador "X/Y" del checklist de documentos, y
 * {@code documentosRequeridos} vale SIEMPRE 6 (§1 del contrato: identidad,
 * ficha RUC, vigencia de poder, sustento economico, garantia y declaracion
 * jurada; poder de representacion y otro no suman).
 */
public record SolicitudResponse(Long id, String codigoSolicitud, LocalDate fechaRegistro,
                                BigDecimal montoPropuesto, String moneda,
                                String plazoTentativo, String observaciones,
                                String estado, LocalDateTime fechaActualizacionEstado,
                                LocalDate fechaVigenciaOferta, Long idOportunidad, String codigoOportunidad,
                                Long idCliente, String clienteNombre, Long idCaptacion, String codigoCaptacion,
                                String direccionLocal, String distritoLocal, Long idAgente, String agenteNombre,
                                Integer plazoMeses, LocalDate fechaInicio, String formaPago,
                                Integer mesesGarantia, Integer mesesAdelanto,
                                int documentosEntregados, int documentosRequeridos) {

    public static SolicitudResponse desde(SolicitudService.FichaSolicitud f) {
        return new SolicitudResponse(f.id(), f.codigoSolicitud(), f.fechaRegistro(), f.montoPropuesto(),
                f.moneda(), f.plazoTentativo(), f.observaciones(), f.estado(), f.fechaActualizacionEstado(),
                f.fechaVigenciaOferta(), f.idOportunidad(), f.codigoOportunidad(), f.idCliente(),
                f.clienteNombre(), f.idCaptacion(), f.codigoCaptacion(), f.direccionLocal(),
                f.distritoLocal(), f.idAgente(), f.agenteNombre(), f.plazoMeses(), f.fechaInicio(),
                f.formaPago(), f.mesesGarantia(), f.mesesAdelanto(),
                f.documentosEntregados(), f.documentosRequeridos());
    }
}
