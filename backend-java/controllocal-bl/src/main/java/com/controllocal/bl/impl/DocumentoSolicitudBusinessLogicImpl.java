package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.DocumentoSolicitudBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AlertaDAO;
import com.controllocal.dao.DocumentoSolicitudDAO;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.dao.impl.AlertaDAOImpl;
import com.controllocal.dao.impl.DocumentoSolicitudDAOImpl;
import com.controllocal.dao.impl.SolicitudAlquilerDAOImpl;
import com.controllocal.model.comercial.DocumentoSolicitud;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.enums.ResultadoRevisionDocumento;
import com.controllocal.model.comercial.enums.Severidad;
import com.controllocal.model.comercial.enums.TipoAlerta;
import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class DocumentoSolicitudBusinessLogicImpl implements DocumentoSolicitudBusinessLogic {

    private final DocumentoSolicitudDAO documentoDAO;
    private final SolicitudAlquilerDAO solicitudDAO;
    private final AlertaDAO alertaDAO;

    public DocumentoSolicitudBusinessLogicImpl() {
        this(new DocumentoSolicitudDAOImpl(), new SolicitudAlquilerDAOImpl(), new AlertaDAOImpl());
    }

    public DocumentoSolicitudBusinessLogicImpl(DocumentoSolicitudDAO documentoDAO) {
        this(documentoDAO, new SolicitudAlquilerDAOImpl(), new AlertaDAOImpl());
    }

    public DocumentoSolicitudBusinessLogicImpl(DocumentoSolicitudDAO documentoDAO,
            SolicitudAlquilerDAO solicitudDAO, AlertaDAO alertaDAO) {
        this.documentoDAO = documentoDAO;
        this.solicitudDAO = solicitudDAO;
        this.alertaDAO = alertaDAO;
    }

    public Long registrar(DocumentoSolicitud documento) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.documento(documento);
            Long idDocumento = documentoDAO.crear(documento);
            emitirAvisoDocumentoModificado(documento);
            return idDocumento;
        });
    }

    // SOLICITUD_DOCUMENTO: si el agente sube/reemplaza un documento mientras la solicitud
    // esta EN_REVISION (el broker la esta evaluando), se le avisa al broker que el expediente
    // cambio. En REGISTRADA/OBSERVADA no aplica: el aviso real llega al reenviar a evaluacion.
    private void emitirAvisoDocumentoModificado(DocumentoSolicitud documento) {
        Long idSolicitud = documento.getSolicitudAlquiler() != null
                ? documento.getSolicitudAlquiler().getIdSolicitud() : null;
        if (idSolicitud == null) {
            return;
        }
        SolicitudAlquiler solicitud = solicitudDAO.buscarPorId(idSolicitud).orElse(null);
        if (solicitud == null || solicitud.getEstado() != EstadoSolicitudAlquiler.EN_REVISION) {
            return;
        }
        AgenteInmobiliario agente = solicitud.getAgenteResponsable();
        if (agente == null || agente.getIdAgente() == null) {
            return;
        }
        String nombre = documento.getNombreArchivo() != null && !documento.getNombreArchivo().isBlank()
                ? documento.getNombreArchivo() : "un documento";
        alertaDAO.crear(AlertaBusinessLogicImpl.construir(
                TipoAlerta.SOLICITUD_DOCUMENTO, Severidad.MEDIA, TipoEntidad.SOLICITUD_ALQUILER,
                idSolicitud, agente,
                "El agente actualizo \"" + nombre + "\" en la solicitud " + solicitud.getCodigoSolicitud()
                        + " mientras esta en evaluacion."));
    }

    public Optional<DocumentoSolicitud> buscarPorId(Long idDocumento) {
        BusinessValidations.id(idDocumento, "El id de documento");
        return documentoDAO.buscarPorId(idDocumento);
    }

    public List<DocumentoSolicitud> listarTodos() {
        return documentoDAO.listarTodos();
    }

    public boolean actualizar(DocumentoSolicitud documento) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(documento != null ? documento.getIdDocumento() : null, "El id de documento");
            BusinessValidations.documento(documento);
            return documentoDAO.actualizar(documento);
        });
    }

    public boolean eliminar(Long idDocumento) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idDocumento, "El id de documento");
            return documentoDAO.eliminar(idDocumento);
        });
    }

    // Revision de un documento individual por el broker, en una sola operacion atomica:
    // valida que el documento pertenezca a la solicitud indicada y lo deja Validado
    // (conforme) u Observado. Al observarlo persiste una alerta real (SOLICITUD_DOCUMENTO_REVISADO)
    // al agente responsable para que sepa que documento debe subsanar.
    @Override
    public DocumentoSolicitud revisar(Long idSolicitud, Long idDocumento,
            ResultadoRevisionDocumento resultado, String observaciones) {
        return TransactionRunner.write((TransactionRunner.TransactionalConnectionSupplier<DocumentoSolicitud>) conn -> {
            BusinessValidations.id(idSolicitud, "El id de solicitud");
            BusinessValidations.id(idDocumento, "El id de documento");
            if (resultado == null) {
                throw new BusinessException("El resultado de la revision es obligatorio.");
            }
            if (resultado == ResultadoRevisionDocumento.OBSERVADO
                    && (observaciones == null || observaciones.isBlank())) {
                throw new BusinessException("La observacion del documento es obligatoria.");
            }
            DocumentoSolicitud documento = documentoDAO.buscarPorId(idDocumento)
                    .orElseThrow(() -> new BusinessException("Documento no encontrado para revisar."));
            Long idSolicitudDoc = documento.getSolicitudAlquiler() != null
                    ? documento.getSolicitudAlquiler().getIdSolicitud() : null;
            if (idSolicitudDoc == null || !idSolicitudDoc.equals(idSolicitud)) {
                throw new BusinessException("El documento no pertenece a la solicitud indicada.");
            }

            documento.actualizarRevision(resultado, observaciones);
            documentoDAO.actualizar(documento);
            if (resultado == ResultadoRevisionDocumento.OBSERVADO) {
                emitirAvisoDocumentoObservado(idSolicitud, documento, observaciones);
            }
            return documentoDAO.buscarPorId(idDocumento).orElse(documento);
        });
    }

    // SOLICITUD_DOCUMENTO_REVISADO: el broker observo un documento puntual -> aviso real al
    // agente. Se ata al agente (que la ve como propia) y a la solicitud (id de entidad) para
    // que la campana lo lleve al expediente de documentos a subsanar.
    private void emitirAvisoDocumentoObservado(Long idSolicitud, DocumentoSolicitud documento, String observacion) {
        SolicitudAlquiler solicitud = solicitudDAO.buscarPorId(idSolicitud).orElse(null);
        AgenteInmobiliario agente = solicitud != null ? solicitud.getAgenteResponsable() : null;
        if (agente == null || agente.getIdAgente() == null) {
            return;
        }
        String tipoDoc = documento.getTipoDocumentoRequerido() != null
                && documento.getTipoDocumentoRequerido().getTipoDocumento() != null
                ? documento.getTipoDocumentoRequerido().getTipoDocumento()
                : (documento.getNombreArchivo() != null ? documento.getNombreArchivo() : "un documento");
        String detalle = observacion != null && !observacion.isBlank() ? ": " + observacion : ".";
        alertaDAO.crear(AlertaBusinessLogicImpl.construir(
                TipoAlerta.SOLICITUD_DOCUMENTO_REVISADO, Severidad.MEDIA, TipoEntidad.SOLICITUD_ALQUILER,
                idSolicitud, agente,
                "El broker observo el documento \"" + tipoDoc + "\" de la solicitud "
                        + solicitud.getCodigoSolicitud() + detalle));
    }
}

