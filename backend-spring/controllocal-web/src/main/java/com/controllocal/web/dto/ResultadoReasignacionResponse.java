package com.controllocal.web.dto;

import com.controllocal.service.CaptacionService;

/** Resultado del cambio real; una consulta comercial conserva su propia autorización. */
public record ResultadoReasignacionResponse(Long id, String codigoCaptacion, String estado,
        Long idAgenteAnterior, Long idAgente, Long idReasignacion,long version) {
    public static ResultadoReasignacionResponse desde(CaptacionService.ResultadoReasignacion f) {
        return new ResultadoReasignacionResponse(f.id(), f.codigoCaptacion(), f.estado(),
                f.idAgenteAnterior(), f.idAgente(), f.idReasignacion(),f.version());
    }
}
