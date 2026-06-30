package com.controllocal.dao;

import java.util.Collection;
import java.util.List;

import com.controllocal.model.comercial.ReportePropietario;

public interface ReportePropietarioDAO extends CrudDAO<ReportePropietario> {
    List<ReportePropietario> listarPorCaptacion(Long idCaptacion);

    /**
     * Reportes de varias captaciones en una sola consulta (IN). Evita la consulta por
     * captacion cuando la bandeja del agente necesita el ultimo reporte de cada una.
     */
    List<ReportePropietario> listarPorCaptaciones(Collection<Long> idsCaptacion);
}
