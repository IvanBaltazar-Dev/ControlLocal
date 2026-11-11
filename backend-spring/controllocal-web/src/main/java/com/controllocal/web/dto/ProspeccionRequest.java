package com.controllocal.web.dto;

import com.controllocal.service.ProspeccionService;

/** Captura progresiva: destinatario declarado opcional, sin acreditar titularidad ni crear Persona. */
public record ProspeccionRequest(Long idLocal, String observaciones,
                                 ProspeccionService.DestinatarioDeclarado destinatarioDeclarado) {

    public ProspeccionService.DatosProspeccion aDatos() {
        return new ProspeccionService.DatosProspeccion(idLocal, observaciones,destinatarioDeclarado);
    }
}
