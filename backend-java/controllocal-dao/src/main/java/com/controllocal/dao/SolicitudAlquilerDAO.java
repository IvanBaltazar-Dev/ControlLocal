package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.SolicitudAlquiler;

public interface SolicitudAlquilerDAO extends CrudDAO<SolicitudAlquiler> {
    Long crear(SolicitudAlquiler solicitud);
    Optional<SolicitudAlquiler> buscarPorId(Long id);
    List<SolicitudAlquiler> listarTodos();
    boolean actualizar(SolicitudAlquiler solicitud);
    boolean eliminar(Long id);
}
