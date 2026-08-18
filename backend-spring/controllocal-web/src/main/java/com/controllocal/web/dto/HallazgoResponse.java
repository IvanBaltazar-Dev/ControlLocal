package com.controllocal.web.dto;

import com.controllocal.service.HallazgoService;

import java.util.List;

/**
 * Un descubrimiento de BROX, con su evidencia y su destino (E2.3).
 *
 * <p>Viaja aparte de la bandeja a propósito: <b>una tarea reclama, un hallazgo
 * propone</b>. `porQue` llega redactado desde el dominio — si el cliente
 * compusiera esa frase, KAIROS tendría que escribir la suya para decir lo mismo
 * por WhatsApp, y las dos empezarían a divergir.
 */
public record HallazgoResponse(String id, String tipo, String titulo, String porQue,
                               int puntaje, List<String> cumple, List<String> noCumple,
                               String destino, Long idCliente, Long idCaptacion,
                               String codigoCaptacion) {

    public static HallazgoResponse desde(HallazgoService.Hallazgo h) {
        return new HallazgoResponse(h.id(), h.tipo(), h.titulo(), h.porQue(), h.puntaje(),
                h.cumple(), h.noCumple(), h.destino(), h.idCliente(), h.idCaptacion(),
                h.codigoCaptacion());
    }
}
