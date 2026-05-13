package com.controllocal.dao;

import com.controllocal.model.comercial.DocumentoSolicitud;

import java.util.List;
import java.util.Optional;

public interface DocumentoSolicitudDAO {
    Long crear(DocumentoSolicitud documento);
    Optional<DocumentoSolicitud> buscarPorId(Long id);
    List<DocumentoSolicitud> listarTodos();
    boolean actualizar(DocumentoSolicitud documento);
    boolean eliminar(Long id);
}
