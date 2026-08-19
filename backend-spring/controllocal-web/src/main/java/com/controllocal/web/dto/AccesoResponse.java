package com.controllocal.web.dto;

import com.controllocal.service.Actor;
import com.controllocal.service.soporte.AccesosDelInicio;

import java.util.List;

/**
 * Un acceso rápido del Inicio (D-E2-1 §6.1, E2.5).
 *
 * <p>El `destino` viaja para el `href`, <b>no para leerse</b>: un Inicio que
 * muestra `/captaciones/pendientes` como texto está enseñando su propia
 * fontanería.
 */
public record AccesoResponse(String etiqueta, String destino) {

    public static List<AccesoResponse> desde(Actor actor) {
        return AccesosDelInicio.de(actor).stream()
                .map(a -> new AccesoResponse(a.etiqueta(), a.destino()))
                .toList();
    }
}
