package com.controllocal.web.dto;

import com.controllocal.service.OportunidadService;

import java.time.LocalDateTime;

/** Contrato CONGELADO: espejo de Dtos.OportunidadResponse de la v1. */
public record OportunidadResponse(Long id, String codigoOportunidad, Long idCliente, String clienteNombre,
                                  Long idCaptacion, String codigoCaptacion, String direccionLocal,
                                  String distritoLocal, Long idAgente, String agenteNombre, String estado,
                                  LocalDateTime fechaRegistro, String motivoCierre, String observaciones,
                                  LocalDateTime fechaCierre, LocalDateTime fechaActualizacion,
                                  Long idPublicacionOrigen) {

    public static OportunidadResponse desde(OportunidadService.FichaOportunidad f) {
        return new OportunidadResponse(f.id(), f.codigoOportunidad(), f.idCliente(), f.clienteNombre(),
                f.idCaptacion(), f.codigoCaptacion(), f.direccionLocal(), f.distritoLocal(), f.idAgente(),
                f.agenteNombre(), f.estado(), f.fechaRegistro(), f.motivoCierre(), f.observaciones(),
                f.fechaCierre(), f.fechaActualizacion(), f.idPublicacionOrigen());
    }
}
