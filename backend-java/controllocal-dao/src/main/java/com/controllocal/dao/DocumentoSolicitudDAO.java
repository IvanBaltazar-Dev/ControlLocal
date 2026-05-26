package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.DocumentoSolicitud;

public interface DocumentoSolicitudDAO extends CrudDAO<DocumentoSolicitud> {
    Long crear(DocumentoSolicitud documento);
    Optional<DocumentoSolicitud> buscarPorId(Long id);
    List<DocumentoSolicitud> listarTodos();
    boolean actualizar(DocumentoSolicitud documento);
    boolean eliminar(Long id);
}
