package com.controllocal.web.dto;

import com.controllocal.service.OportunidadService;

/** Contrato CONGELADO: espejo de Dtos.OportunidadRequest de la v1. */
public record OportunidadRequest(String codigoOportunidad, Long idCliente, Long idCaptacion,
                                 String observaciones, Long idPublicacionOrigen) {

    public OportunidadService.DatosOportunidad aDatos() {
        return new OportunidadService.DatosOportunidad(codigoOportunidad, idCliente, idCaptacion,
                observaciones, idPublicacionOrigen);
    }
}
