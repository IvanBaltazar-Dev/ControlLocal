package com.controllocal.dao;

import com.controllocal.model.comercial.SolicitudAlquiler;

import java.util.List;
import java.util.Optional;

public interface SolicitudAlquilerDAO {
    Long crear(SolicitudAlquiler solicitud);
    Optional<SolicitudAlquiler> buscarPorId(Long id);
    List<SolicitudAlquiler> listarTodos();
    boolean actualizar(SolicitudAlquiler solicitud);
    boolean eliminar(Long id);
}
