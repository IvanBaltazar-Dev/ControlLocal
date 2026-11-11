package com.controllocal.web.dto;

import com.controllocal.service.CaptacionService;

/** RF017: proyección de gobierno, sin condiciones o historia comercial. */
public record EncargoReasignableResponse(Long id, String codigoCaptacion, String estado,
        String direccionLocal, Long idAgente, String agenteNombre,long version) {
    public static EncargoReasignableResponse desde(CaptacionService.EncargoReasignable f) {
        return new EncargoReasignableResponse(f.id(), f.codigoCaptacion(), f.estado(),
                f.direccionLocal(), f.idAgente(), f.agenteNombre(),f.version());
    }
}
