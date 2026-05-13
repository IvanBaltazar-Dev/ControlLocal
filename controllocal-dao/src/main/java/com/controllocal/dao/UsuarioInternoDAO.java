package com.controllocal.dao;

import com.controllocal.model.usuario.UsuarioInterno;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de persistencia para la entidad UsuarioInterno.
 * La creacion de usuarios se realiza a traves de BrokerDAO o AgenteInmobiliarioDAO,
 * ya que todo usuario interno es siempre un Broker o un Agente.
 */
public interface UsuarioInternoDAO {

    Long crear(UsuarioInterno usuario);

    Optional<UsuarioInterno> buscarPorId(Long id);

    List<UsuarioInterno> listarTodos();

    boolean actualizar(UsuarioInterno usuario);

    boolean eliminar(Long id);
}
