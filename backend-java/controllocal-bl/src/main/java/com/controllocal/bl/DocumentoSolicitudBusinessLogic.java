package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.DocumentoSolicitud;

public interface DocumentoSolicitudBusinessLogic {

    public Long registrar(DocumentoSolicitud documento);
    public Optional<DocumentoSolicitud> buscarPorId(Long idDocumento);
    public List<DocumentoSolicitud> listarTodos();
    public boolean actualizar(DocumentoSolicitud documento);
    public boolean eliminar(Long idDocumento);
}

