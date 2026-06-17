package com.controllocal.dao;

import java.util.List;

import com.controllocal.model.comercial.ReportePropietario;

public interface ReportePropietarioDAO extends CrudDAO<ReportePropietario> {
    List<ReportePropietario> listarPorCaptacion(Long idCaptacion);
}
