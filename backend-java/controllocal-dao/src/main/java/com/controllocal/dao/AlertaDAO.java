package com.controllocal.dao;

import java.util.List;

import com.controllocal.model.comercial.Alerta;

public interface AlertaDAO extends CrudDAO<Alerta> {
    List<Alerta> listarActivasPorAgente(Long idAgente);

    // Alertas activas de todos los agentes que supervisa el broker (via broker_agente).
    List<Alerta> listarActivasPorBroker(Long idBroker);

    // Todas las alertas activas (vista de administrador).
    List<Alerta> listarActivas();

    // Marca una alerta activa como atendida (leida) sin descartarla.
    boolean marcarAtendida(Long idAlerta);
}
