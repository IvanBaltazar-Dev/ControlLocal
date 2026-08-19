package com.controllocal.web.dto;

import com.controllocal.service.RendimientoComercialService;

/**
 * Como se reparte el resultado entre los agentes del equipo.
 *
 * <p>Existe porque el total y la distribucion son dos cosas distintas (D-E2-2
 * §6.1): meta 20 y resultado 21 puede esconder dos agentes que hicieron 17 y
 * tres que hicieron cero. Un verde de equipo sobre un equipo roto es peor que
 * no tener el indicador.
 *
 * <p><b>Sin nombres</b> (instruccion 13): esto es una distribucion, no un
 * ranking. Quien necesita intervencion se ve en la gestion por excepcion, con su
 * brecha delante, no en una lista ordenada de mejor a peor.
 *
 * <p>{@code sinBase} son los agentes a los que <b>nadie fijo meta</b>. No cuentan
 * como fuera de ritmo: no se le puede reprochar a alguien una brecha contra un
 * objetivo que no existe.
 */
public record PulsoResponse(int enRitmo, int atencion, int fueraDeRitmo, int sinBase,
                            int agentes) {

    public static PulsoResponse desde(RendimientoComercialService.Pulso p) {
        return p == null ? null
                : new PulsoResponse(p.enRitmo(), p.atencion(), p.fueraDeRitmo(), p.sinBase(),
                p.agentes());
    }
}
