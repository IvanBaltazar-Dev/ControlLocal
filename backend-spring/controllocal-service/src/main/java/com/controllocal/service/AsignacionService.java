package com.controllocal.service;

import java.time.LocalDateTime;
import java.util.List;

public interface AsignacionService {

    record DatosReasignacion(Long idAgente, Long idBrokerDestino, String motivo) {
    }

    record AsignacionAgente(Long idAgente, String nombre,
                            String numeroDocumento, String estadoAdministrativo,
                            String estadoOperativo, String brokerActual) {
    }

    record AsignacionBroker(Long idBroker, String nombre, String zona,
                            String estadoAdministrativo,
                            boolean esAdministrador, int agentesACargo) {
    }

    record Reasignacion(Long id, Long idAgente, String agenteNombre,
                        Long idBrokerAnterior, String brokerAnteriorNombre,
                        Long idBrokerNuevo, String brokerNuevoNombre,
                        Long idBrokerAdministrador,
                        String brokerAdministradorNombre,
                        LocalDateTime fechaCambio, String motivo) {
    }

    List<AsignacionAgente> agentes(Actor actor);

    List<AsignacionBroker> brokers(Actor actor);

    List<Reasignacion> historial(Actor actor);

    Reasignacion reasignar(DatosReasignacion datos, Actor actor);
}
