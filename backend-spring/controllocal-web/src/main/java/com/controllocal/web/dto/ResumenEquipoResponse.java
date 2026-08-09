package com.controllocal.web.dto;

import com.controllocal.service.CaptacionService;

import java.util.List;

/**
 * KPI de la cartera del equipo. Extension ADITIVA, misma razon que
 * {@code /locales/resumen}: los contadores se calculan en la BASE para que no
 * dependan de las filas descargadas. Aqui ademas cuentan INMUEBLES distintos,
 * que es algo que una pagina de captaciones no permite deducir.
 *
 * <p>{@code distritosDisponibles} viaja con el resumen para que el filtro de
 * distrito sea data-driven sin una llamada extra: se ofrece solo lo que la
 * cartera tiene.
 */
public record ResumenEquipoResponse(long propiedades, long conCaptacionActiva,
                                    long agentesConCartera, long distritos,
                                    List<String> distritosDisponibles) {

    public static ResumenEquipoResponse desde(CaptacionService.ResumenEquipo r,
                                              List<String> distritosDisponibles) {
        return new ResumenEquipoResponse(r.propiedades(), r.conCaptacionActiva(),
                r.agentesConCartera(), r.distritos(), distritosDisponibles);
    }
}
