package com.controllocal.dao;

import java.util.List;

import com.controllocal.model.inmueble.PrecioLocal;

/**
 * Contrato de persistencia del historico de precios de un local.
 */
public interface PrecioLocalDAO extends CrudDAO<PrecioLocal> {

    List<PrecioLocal> listarPorLocal(Long idLocal);
}
