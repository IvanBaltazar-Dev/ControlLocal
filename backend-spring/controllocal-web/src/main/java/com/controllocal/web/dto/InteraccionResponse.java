package com.controllocal.web.dto;

import com.controllocal.service.InteraccionService;

import java.time.LocalDateTime;

/** Contrato CONGELADO: espejo de Dtos.InteraccionResponse de la v1. */
public record InteraccionResponse(Long id, String contexto, Long idOportunidad, Long idProspeccion,
                                  Long idCaptacion, Long idCliente, Long idPropietario,
                                  String codigoProspeccion, LocalDateTime fechaHora, String canalContacto,
                                  String resultado, String observaciones, String transcripcionNota,
                                  String clienteNombre, String propietarioNombre, String personaTipo,
                                  String personaNombre, String codigoCaptacion, String agenteNombre) {

    public static InteraccionResponse desde(InteraccionService.FichaInteraccion f) {
        return new InteraccionResponse(f.id(), f.contexto(), f.idOportunidad(), f.idProspeccion(),
                f.idCaptacion(), f.idCliente(), f.idPropietario(), f.codigoProspeccion(), f.fechaHora(),
                f.canalContacto(), f.resultado(), f.observaciones(), f.transcripcionNota(),
                f.clienteNombre(), f.propietarioNombre(), f.personaTipo(), f.personaNombre(),
                f.codigoCaptacion(), f.agenteNombre());
    }
}
