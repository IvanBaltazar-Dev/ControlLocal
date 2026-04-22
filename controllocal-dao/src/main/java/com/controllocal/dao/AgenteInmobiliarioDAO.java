package com.controllocal.dao;

import com.controllocal.model.usuario.AgenteInmobiliario;

import java.util.List;
import java.util.Optional;
import java.util.List;
import java.util.Optional;

/**
 * Contrato de persistencia para la entidad AgenteInmobiliario.
 */

public interface AgenteInmobiliarioDAO {

    Long crear(AgenteInmobiliario agente);

    Optional<AgenteInmobiliario> buscarPorId(Long id);

    List<AgenteInmobiliario> listarTodos();

    boolean actualizar(AgenteInmobiliario agente);

    boolean eliminar(Long id);
}
