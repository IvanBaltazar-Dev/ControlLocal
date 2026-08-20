package com.controllocal.web.dto;

import com.controllocal.service.MetaComercialService;

import java.util.List;

/**
 * Fijar o revisar metas. Solo el broker.
 *
 * <p><b>Lo que no viene no se borra.</b> Se actualiza lo enviado y se deja lo
 * demás como estaba: un formulario a medio enviar no puede costarle al equipo
 * los objetivos que ya tenía.
 *
 * <p><b>El motivo es obligatorio en cada asignación</b>, no uno global para
 * todas: bajar la meta de Luis por incorporación tardía y subir la de Andrea por
 * cambio de cartera son dos decisiones distintas, y compartir una sola
 * explicación las volvería ilegibles dentro de seis meses.
 */
public record MetasRequest(String mes, List<Asignacion> metas) {

    /** A quién, qué KPI (código unitario C/P/S/F), cuánto y por qué. */
    public record Asignacion(long idRolAgente, String kpi, int valor, String motivo) {
    }

    public List<MetaComercialService.Asignacion> aDatos() {
        return metas == null ? List.of()
                : metas.stream()
                .map(a -> new MetaComercialService.Asignacion(a.idRolAgente(), a.kpi(), a.valor(),
                        a.motivo()))
                .toList();
    }
}
