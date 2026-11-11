package com.controllocal.service.impl;

import com.controllocal.service.Actor;
import com.controllocal.service.FocoDelBrokerService;
import com.controllocal.service.FocoService;
import com.controllocal.service.HallazgoService;
import com.controllocal.service.IndicadorService;
import com.controllocal.service.TareaService;
import org.springframework.stereotype.Service;

import java.util.List;

/** Reutiliza la composición antes alojada en DashboardController, sin otra política de despacho. */
@Service
public class FocoServiceImpl implements FocoService {
    private final IndicadorService indicadores;
    private final TareaService tareas;
    private final HallazgoService hallazgos;
    private final FocoDelBrokerService asuntos;
    private final com.controllocal.service.soporte.PendientesComercialesRadar actuaciones;

    public FocoServiceImpl(IndicadorService indicadores, TareaService tareas,
                           HallazgoService hallazgos, FocoDelBrokerService asuntos,
                           com.controllocal.service.soporte.PendientesComercialesRadar actuaciones) {
        this.indicadores = indicadores;
        this.tareas = tareas;
        this.hallazgos = hallazgos;
        this.asuntos = asuntos;
        this.actuaciones = actuaciones;
    }

    @Override
    public Foco de(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta consulta,String periodo, Actor actor) {
        var resumen = indicadores.resumen(periodo, actor);
        if (!actor.esAgente()) {
            var propios = asuntos.de(consulta,actor);
            return new Foco(resumen, List.of(), hallazgos.de(actor), propios, actuaciones.de(consulta,actor));
        }
        var bandeja = tareas.bandejaDe(consulta,actor);
        var descubiertos = hallazgos.de(actor);
        return new Foco(resumen, bandeja, descubiertos, asuntos.de(consulta,actor), actuaciones.de(consulta,actor));
    }
}
