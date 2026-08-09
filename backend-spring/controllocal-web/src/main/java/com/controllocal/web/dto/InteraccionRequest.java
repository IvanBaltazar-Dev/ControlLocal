package com.controllocal.web.dto;

import com.controllocal.service.InteraccionService;

/** Contrato CONGELADO: espejo de Dtos.InteraccionRequest de la v1. */
public record InteraccionRequest(String contexto, Long idOportunidad, Long idProspeccion, Long idCaptacion,
                                 Long idCliente, String canalContacto, String resultado,
                                 String observaciones, String transcripcionNota) {

    public InteraccionService.DatosInteraccion aDatos() {
        return new InteraccionService.DatosInteraccion(contexto, idOportunidad, idProspeccion, idCaptacion,
                idCliente, canalContacto, resultado, observaciones, transcripcionNota);
    }
}
