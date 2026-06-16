package com.controllocal.dao;

import java.util.List;

import com.controllocal.model.comercial.ComisionLiquidacion;

public interface ComisionLiquidacionDAO extends CrudDAO<ComisionLiquidacion> {
    List<ComisionLiquidacion> listarPorContrato(Long idContrato);
}
