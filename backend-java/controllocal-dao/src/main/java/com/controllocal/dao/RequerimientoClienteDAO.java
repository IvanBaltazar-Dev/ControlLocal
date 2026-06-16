package com.controllocal.dao;

import java.util.List;

import com.controllocal.model.comercial.RequerimientoCliente;

public interface RequerimientoClienteDAO extends CrudDAO<RequerimientoCliente> {
    List<RequerimientoCliente> listarPorCliente(Long idCliente);
}
