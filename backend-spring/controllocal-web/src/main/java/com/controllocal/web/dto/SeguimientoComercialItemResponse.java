package com.controllocal.web.dto;

import com.controllocal.service.SeguimientoComercialService;

import java.time.LocalDateTime;

/**
 * Fila homogenea de las cinco etapas del proceso. {@code icono} y {@code tono}
 * son datos del cable, no presentacion del servidor: la pantalla los usa como
 * claves de su propio catalogo de iconos y colores.
 *
 * <p>{@code rutaRevision} solo viene con valor cuando la fila tiene una accion
 * de revision pendiente (captacion en P, solicitud en E); en el resto es cadena
 * vacia. {@code clienteId} y {@code propietarioId} se omiten del JSON cuando no
 * aplican.
 */
public record SeguimientoComercialItemResponse(
        String proceso,
        String codigo,
        String cliente,
        Long clienteId,
        String local,
        String distrito,
        String agente,
        String propietario,
        Long propietarioId,
        String estado,
        String ultimoHito,
        String ruta,
        String rutaRevision,
        String icono,
        String tono,
        LocalDateTime fechaOrden,
        String monto) {

    public static SeguimientoComercialItemResponse desde(SeguimientoComercialService.Fila fila) {
        return new SeguimientoComercialItemResponse(
                fila.proceso(), fila.codigo(), fila.cliente(), fila.clienteId(),
                fila.local(), fila.distrito(), fila.agente(), fila.propietario(),
                fila.propietarioId(), fila.estado(), fila.ultimoHito(), fila.ruta(),
                fila.rutaRevision(), fila.icono(), fila.tono(), fila.fechaOrden(),
                fila.monto());
    }
}
