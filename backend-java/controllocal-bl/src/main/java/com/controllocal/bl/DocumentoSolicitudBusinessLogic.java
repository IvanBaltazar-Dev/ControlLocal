package com.controllocal.bl;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.DocumentoSolicitud;
import com.controllocal.model.comercial.enums.ResultadoRevisionDocumento;

public interface DocumentoSolicitudBusinessLogic {

    public Long registrar(DocumentoSolicitud documento);
    public Optional<DocumentoSolicitud> buscarPorId(Long idDocumento);
    public List<DocumentoSolicitud> listarTodos();
    public List<DocumentoSolicitud> listarPorSolicitud(Long idSolicitud);
    public List<DocumentoSolicitud> listarPorSolicitudes(Collection<Long> idsSolicitud);
    public boolean actualizar(DocumentoSolicitud documento);
    public boolean eliminar(Long idDocumento);

    // Revision de un documento individual por el broker: lo deja Validado (conforme) u
    // Observado. Cuando lo observa, emite una alerta real y persistida al agente
    // responsable de la solicitud para que sepa que debe subsanar ese documento.
    public DocumentoSolicitud revisar(Long idSolicitud, Long idDocumento,
            ResultadoRevisionDocumento resultado, String observaciones);

    // Deja conformes (Validados) en bloque SOLO los documentos aun sin revisar (resultado Pendiente)
    // de una solicitud. Lo usa el broker durante la evaluacion ("Validar todos los documentos"): es
    // la contraparte masiva de revisar(). RESPETA los observados (no los pisa). Idempotente.
    // Devuelve la lista de documentos resultante.
    public List<DocumentoSolicitud> conformarPendientes(Long idSolicitud);
}
