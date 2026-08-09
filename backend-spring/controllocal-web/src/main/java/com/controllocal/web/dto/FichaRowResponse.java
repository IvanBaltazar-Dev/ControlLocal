package com.controllocal.web.dto;

import com.controllocal.service.FichaComercialService;

import java.time.LocalDateTime;

public record FichaRowResponse(
        String id,
        String codigo,
        String proceso,
        String titulo,
        String subtitulo,
        String local,
        String distrito,
        String cliente,
        Long clienteId,
        String propietario,
        Long propietarioId,
        String agente,
        String estado,
        String fecha,
        String ruta,
        String icono,
        String tono,
        LocalDateTime fechaOrden) {

    public static FichaRowResponse desde(FichaComercialService.FilaFicha fila) {
        return new FichaRowResponse(
                fila.id(), fila.codigo(), fila.proceso(), fila.titulo(), fila.subtitulo(),
                fila.local(), fila.distrito(), fila.cliente(), fila.clienteId(),
                fila.propietario(), fila.propietarioId(), fila.agente(), fila.estado(),
                fila.fecha(), fila.ruta(), fila.icono(), fila.tono(), fila.fechaOrden());
    }
}
