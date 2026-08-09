package com.controllocal.web.dto;

import com.controllocal.service.AlertaService;

import java.time.OffsetDateTime;

/**
 * Contrato CONGELADO: espejo de Dtos.AlertaResponse de la v1.
 *
 * <p>{@code ruta} es <b>derivada</b>, no columna: la calcula el service para
 * que la campana navegue directo al origen del aviso. Viaja como {@code null}
 * —y con Jackson en {@code non_null} ni siquiera viaja— para los tipos de
 * entidad que la v1 no enruta, entre ellos {@code INMUEBLE} y
 * {@code CAPTACION}.
 */
public record AlertaResponse(Long id, String tipo, String severidad, String entidadTipo,
                             Long entidadId, Long idAgente, String agenteNombre, String mensaje,
                             String estado, OffsetDateTime fechaGeneracion,
                             OffsetDateTime fechaResolucion, String ruta) {

    public static AlertaResponse desde(AlertaService.FichaAlerta f) {
        return new AlertaResponse(f.id(), f.tipo(), f.severidad(), f.entidadTipo(), f.entidadId(),
                f.idAgente(), f.agenteNombre(), f.mensaje(), f.estado(), f.fechaGeneracion(),
                f.fechaResolucion(), f.ruta());
    }
}
