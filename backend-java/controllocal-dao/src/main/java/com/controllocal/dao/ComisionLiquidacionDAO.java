package com.controllocal.dao;

import java.util.Collection;
import java.util.List;

import com.controllocal.model.comercial.ComisionLiquidacion;

public interface ComisionLiquidacionDAO extends CrudDAO<ComisionLiquidacion> {
    List<ComisionLiquidacion> listarPorContrato(Long idContrato);

    // Carga en bloque las comisiones de los contratos dados (para enriquecer una pagina sin
    // recorrer la tabla completa). Lista vacia si la coleccion viene vacia.
    List<ComisionLiquidacion> listarPorContratos(Collection<Long> idsContrato);
}
