package com.controllocal.web.dto;

import com.controllocal.service.FichaComercialService;

import java.util.List;

public record FichaSectionResponse(
        String section,
        long totalRecords,
        int page,
        int pageSize,
        List<FichaRowResponse> items) {

    public static FichaSectionResponse desde(FichaComercialService.SeccionFicha seccion) {
        return new FichaSectionResponse(
                seccion.section(), seccion.totalRecords(), seccion.page(), seccion.pageSize(),
                seccion.items().stream().map(FichaRowResponse::desde).toList());
    }
}
