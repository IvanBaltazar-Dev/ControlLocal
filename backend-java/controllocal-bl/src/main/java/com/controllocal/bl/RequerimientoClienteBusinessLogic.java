package com.controllocal.bl;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.RequerimientoCliente;
import com.controllocal.model.comercial.enums.EstadoRequerimiento;

public interface RequerimientoClienteBusinessLogic {

    Optional<RequerimientoCliente> buscarPorId(Long idRequerimiento);

    List<RequerimientoCliente> listarTodos();

    List<RequerimientoCliente> listarPorCliente(Long idCliente);

    /**
     * Requerimientos de varios clientes en una sola consulta (con distritos en bloque).
     * La usa la bandeja del agente para el matching de cartera sin N+1 por cliente.
     */
    List<RequerimientoCliente> listarPorClientes(Collection<Long> idsCliente);

    /** Crea el requerimiento y lo vincula a su cliente (entra al matching de cartera). */
    Long crear(RequerimientoCliente requerimiento);

    boolean actualizar(RequerimientoCliente requerimiento);

    boolean cambiarEstado(Long idRequerimiento, EstadoRequerimiento estado);
}
