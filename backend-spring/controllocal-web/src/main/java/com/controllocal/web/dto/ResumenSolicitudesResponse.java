package com.controllocal.web.dto;

import com.controllocal.service.SolicitudService;

import java.util.List;

/**
 * KPI de la bandeja de solicitudes por estado + los distritos y agentes del
 * alcance. <b>Extension aditiva</b>: no existe en la v1, donde los contadores,
 * la lista de distritos y la de agentes se derivaban de la cartera entera
 * descargada en memoria — con paginacion real eso solo contaria la pagina
 * visible.
 *
 * <p>{@code pendientes} es {@code enRevision + observadas}: el cubo que la
 * bandeja del broker usa como vista por defecto. Viaja calculado para que la
 * pantalla no lo sume por su cuenta y se desincronice del filtro
 * {@code estado=PENDIENTES} que pide esa misma cola.
 */
public record ResumenSolicitudesResponse(long total, long registradas, long enRevision,
                                         long observadas, long aprobadas, long rechazadas,
                                         long desistidas, long cerradas, long pendientes,
                                         List<String> distritos,
                                         List<AgenteResumen> agentes) {

    /** Agente con al menos una solicitud en el alcance, para el filtro data-driven. */
    public record AgenteResumen(long id, String nombre) {
    }

    public static ResumenSolicitudesResponse desde(SolicitudService.ResumenSolicitudes r) {
        return new ResumenSolicitudesResponse(r.total(), r.registradas(), r.enRevision(),
                r.observadas(), r.aprobadas(), r.rechazadas(), r.desistidas(), r.cerradas(),
                r.pendientes(), r.distritos(),
                r.agentes().stream().map(a -> new AgenteResumen(a.id(), a.nombre())).toList());
    }
}
