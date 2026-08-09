package com.controllocal.web.dto;

import com.controllocal.service.VisitaService;

/** Contrato CONGELADO: espejo de Dtos.ResultadoVisitaRequest de la v1. */
public record ResultadoVisitaRequest(String resultado, String observaciones, String razonNoContinuidad,
                                     Integer nivelInteres, String objecionPrincipal, String opinionPrecio,
                                     String proximaAccion) {

    public VisitaService.DesenlaceVisita aDesenlace() {
        return new VisitaService.DesenlaceVisita(resultado, observaciones, razonNoContinuidad,
                nivelInteres, objecionPrincipal, opinionPrecio, proximaAccion);
    }
}
