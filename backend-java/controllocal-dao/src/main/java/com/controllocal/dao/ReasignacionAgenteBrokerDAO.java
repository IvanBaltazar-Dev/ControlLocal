package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.usuario.ReasignacionAgenteBroker;

public interface ReasignacionAgenteBrokerDAO extends CrudDAO<ReasignacionAgenteBroker> {
    Long crear(ReasignacionAgenteBroker reasignacion);
    Optional<ReasignacionAgenteBroker> buscarPorId(Long id);
    List<ReasignacionAgenteBroker> listarTodos();
    boolean actualizar(ReasignacionAgenteBroker reasignacion);
    boolean eliminar(Long id);
}
