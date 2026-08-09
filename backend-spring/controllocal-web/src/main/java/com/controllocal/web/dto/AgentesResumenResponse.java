package com.controllocal.web.dto;

import com.controllocal.service.AgenteService;

import java.util.List;

/**
 * Cubos del catálogo de agentes. <b>Extensión aditiva</b> (no existe en la v1).
 *
 * <p>Se calculan en la base sobre el MISMO conjunto que pagina la lista, que es
 * la única forma de que cuadren: con paginación real, contar en el cliente solo
 * cuenta la página visible.
 *
 * <p>Dos cubos por dos máquinas distintas que no hay que mezclar: el estado
 * <b>administrativo</b> (activo/inactivo, vive en la credencial) y el
 * <b>operativo</b> (disponible/ocupado/vacaciones/suspendido, vive en el
 * agente). Un agente activo puede estar de vacaciones.
 *
 * <p>{@code zonas} recorre el <b>alcance completo</b> a propósito —ofrece las
 * opciones del selector— así que el endpoint no acepta el filtro {@code zona},
 * que es justo el que acotaría la lista que devuelve.
 */
public record AgentesResumenResponse(long total, long activos, long inactivos,
                                     long disponibles, long ocupados,
                                     long vacaciones, long suspendidos,
                                     List<String> zonas) {

    public static AgentesResumenResponse desde(AgenteService.ResumenAgentes r) {
        return new AgentesResumenResponse(r.total(), r.activos(), r.inactivos(),
                r.disponibles(), r.ocupados(), r.vacaciones(), r.suspendidos(), r.zonas());
    }
}
