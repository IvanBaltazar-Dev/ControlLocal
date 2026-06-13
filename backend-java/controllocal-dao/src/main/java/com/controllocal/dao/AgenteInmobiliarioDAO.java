package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.usuario.AgenteInmobiliario;

/**
 * Contrato de persistencia para la entidad AgenteInmobiliario.
 */

public interface AgenteInmobiliarioDAO extends CrudDAO<AgenteInmobiliario> {

    Long crear(AgenteInmobiliario agente);

    Optional<AgenteInmobiliario> buscarPorId(Long id);

    // Resuelve el agente a partir de su usuario interno (login del API REST).
    Optional<AgenteInmobiliario> buscarPorUsuario(Long idUsuario);

    List<AgenteInmobiliario> listarTodos();

    boolean actualizar(AgenteInmobiliario agente);

    boolean eliminar(Long id);
}
