package com.controllocal.web.dto;

import com.controllocal.service.ProspeccionService;

/** Contrato CONGELADO: espejo de Dtos.ProspeccionRequest de la v1. */
public record ProspeccionRequest(Long idLocal, String observaciones) {

    public ProspeccionService.DatosProspeccion aDatos() {
        return new ProspeccionService.DatosProspeccion(idLocal, observaciones);
    }
}
