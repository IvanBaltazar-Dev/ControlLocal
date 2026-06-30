package com.controllocal.dao;

import java.util.Collection;
import java.util.List;

import com.controllocal.model.comercial.RequerimientoCliente;

public interface RequerimientoClienteDAO extends CrudDAO<RequerimientoCliente> {
    List<RequerimientoCliente> listarPorCliente(Long idCliente);

    /**
     * Requerimientos de varios clientes en una sola consulta, con sus distritos resueltos
     * en bloque (una consulta extra, no una por requerimiento). La usa la bandeja del agente
     * para el matching de cartera sin caer en N+1.
     */
    List<RequerimientoCliente> listarPorClientes(Collection<Long> idsCliente);
}
