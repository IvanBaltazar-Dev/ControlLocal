package com.controllocal.web.dto;

import com.controllocal.service.TareaService;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Contrato CONGELADO: espejo del TareaResponse de la v1 (vive dentro de
 * {@code TareasRest}, no en Dtos).
 *
 * <p>Los cuatro ultimos campos <b>no estan en la tabla</b>: {@code entidadCodigo}
 * y {@code rutaResolver} llevan al cliente directo al item que disparo la
 * tarea, y {@code diasSinAccion} / {@code fechaVencimiento} salen del plazo
 * real de la entidad de origen, no de cuando se creo la tarea.
 */
public record TareaResponse(Long id, String tipo, String entidadTipo, Long entidadId,
                            String entidadCodigo, String rutaResolver, String descripcion,
                            String estado, String prioridad, OffsetDateTime fechaProgramada,
                            Integer diasSinAccion, LocalDate fechaVencimiento,
                            boolean dependeDeMi, String lado, String paso) {

    public static TareaResponse desde(TareaService.FichaTarea f) {
        return new TareaResponse(f.id(), f.tipo(), f.entidadTipo(), f.entidadId(), f.entidadCodigo(),
                f.rutaResolver(), f.descripcion(), f.estado(), f.prioridad(), f.fechaProgramada(),
                f.diasSinAccion(), f.fechaVencimiento(),
                f.dependeDeMi(), f.lado(), f.paso());
    }
}
