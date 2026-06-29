package com.controllocal.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.DocumentoSolicitud;

public interface DocumentoSolicitudDAO extends CrudDAO<DocumentoSolicitud> {
    Long crear(DocumentoSolicitud documento);
    Optional<DocumentoSolicitud> buscarPorId(Long id);
    List<DocumentoSolicitud> listarTodos();
    List<DocumentoSolicitud> listarPorSolicitud(Long idSolicitud);
    List<DocumentoSolicitud> listarPorSolicitudes(Collection<Long> idsSolicitud);
    boolean actualizar(DocumentoSolicitud documento);
    boolean eliminar(Long id);
}
