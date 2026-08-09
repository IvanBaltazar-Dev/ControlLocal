package com.controllocal.web.dto;

import com.controllocal.service.CoincidenciaService;

import java.util.List;

/**
 * Contrato CONGELADO: espejo de CoincidenciaCarteraSupport.CoincidenciasResponse
 * de la v1. Misma forma para las tres entradas de matching (cliente, captacion
 * y prospeccion); {@code origen} identifica cual las produjo.
 */
public record CoincidenciasResponse(String origen, int total, int page, int pageSize,
                                    List<CoincidenciaResponse> items) {

    public static CoincidenciasResponse desde(CoincidenciaService.Coincidencias c) {
        return new CoincidenciasResponse(c.origen(), c.total(), c.page(), c.pageSize(),
                c.items().stream().map(CoincidenciaResponse::desde).toList());
    }
}
