package com.controllocal.service;

import java.util.List;

/** Composición canónica del foco actual, compartida por Web y coordinación de asistencia. */
public interface FocoService {
    record Foco(IndicadorService.Resumen indicadores, List<TareaService.FichaTarea> bandeja,
                List<HallazgoService.Hallazgo> hallazgos,
                List<FocoDelBrokerService.AsuntoDelBroker> asuntosDelBroker,
                List<com.controllocal.service.soporte.PendientesComercialesRadar.Actuacion> actuaciones) {
        public Foco {
            bandeja = List.copyOf(bandeja);
            hallazgos = List.copyOf(hallazgos);
            asuntosDelBroker = List.copyOf(asuntosDelBroker);
            actuaciones = List.copyOf(actuaciones);
        }
    }

    Foco de(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta consulta,String periodo, Actor actor);
}
