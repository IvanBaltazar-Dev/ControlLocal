package com.controllocal.dao;

import java.util.Optional;

import com.controllocal.model.comercial.ContratoAlquiler;

public interface ContratoAlquilerDAO extends CrudDAO<ContratoAlquiler> {
    Optional<ContratoAlquiler> buscarPorOportunidad(Long idOportunidad);
}
