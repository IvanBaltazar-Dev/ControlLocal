package com.controllocal.web.dto;

import com.controllocal.service.LocalComercialService;

import java.math.BigDecimal;
import java.util.List;

/** Advertencia no bloqueante previa al alta o edición de un local. */
public record PosibleDuplicadoLocalResponse(Long id, String codigoLocal, String direccion,
                                            String interiorUnidad, String piso, BigDecimal metraje,
                                            List<String> criteriosCoincidentes) {

    public static PosibleDuplicadoLocalResponse desde(LocalComercialService.PosibleDuplicado valor) {
        return new PosibleDuplicadoLocalResponse(valor.id(), valor.codigoLocal(), valor.direccion(),
                valor.interiorUnidad(), valor.piso(), valor.metraje(), valor.criteriosCoincidentes());
    }
}
