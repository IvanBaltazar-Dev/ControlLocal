package com.controllocal.dao;

import java.util.List;

import com.controllocal.model.inmueble.PrecioLocal;

/**
 * Contrato de persistencia del historico de precios de un local.
 */
public interface PrecioLocalDAO extends CrudDAO<PrecioLocal> {
    public void registrar(Long idLocal, String hito, String moneda, java.math.BigDecimal monto, java.time.LocalDate fecha);
    List<PrecioLocal> listarPorLocal(Long idLocal);
}
