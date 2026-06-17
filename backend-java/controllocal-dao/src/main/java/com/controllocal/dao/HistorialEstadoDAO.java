package com.controllocal.dao;

import java.util.List;

import com.controllocal.model.comercial.HistorialEstado;
import com.controllocal.model.comercial.enums.TipoEntidad;

public interface HistorialEstadoDAO extends CrudDAO<HistorialEstado> {
    List<HistorialEstado> listarPorEntidad(TipoEntidad tipo, Long entidadId);
    public void registrar(String entidadTipo, Long entidadId, String estadoAnterior, String estadoNuevo, Long idUsuario, java.time.LocalDateTime fechaEvento, String observacion);
}
