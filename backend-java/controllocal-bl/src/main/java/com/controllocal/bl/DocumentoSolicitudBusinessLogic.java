package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.DocumentoSolicitud;
import com.controllocal.model.comercial.enums.ResultadoRevisionDocumento;

public interface DocumentoSolicitudBusinessLogic {

    public Long registrar(DocumentoSolicitud documento);
    public Optional<DocumentoSolicitud> buscarPorId(Long idDocumento);
    public List<DocumentoSolicitud> listarTodos();
    public boolean actualizar(DocumentoSolicitud documento);
    public boolean eliminar(Long idDocumento);

    // Revision de un documento individual por el broker: lo deja Validado (conforme) u
    // Observado. Cuando lo observa, emite una alerta real y persistida al agente
    // responsable de la solicitud para que sepa que debe subsanar ese documento.
    public DocumentoSolicitud revisar(Long idSolicitud, Long idDocumento,
            ResultadoRevisionDocumento resultado, String observaciones);
}

