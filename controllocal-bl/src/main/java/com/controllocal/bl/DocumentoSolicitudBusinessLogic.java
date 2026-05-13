package com.controllocal.bl;

import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.DocumentoSolicitudDAO;
import com.controllocal.dao.impl.DocumentoSolicitudDAOImpl;
import com.controllocal.model.comercial.DocumentoSolicitud;

import java.util.List;
import java.util.Optional;

public class DocumentoSolicitudBusinessLogic {

    private final DocumentoSolicitudDAO documentoDAO;

    public DocumentoSolicitudBusinessLogic() {
        this(new DocumentoSolicitudDAOImpl());
    }

    public DocumentoSolicitudBusinessLogic(DocumentoSolicitudDAO documentoDAO) {
        this.documentoDAO = documentoDAO;
    }

    public Long registrar(DocumentoSolicitud documento) {
        return TransactionRunner.write(() -> {
            BusinessValidations.documento(documento);
            return documentoDAO.crear(documento);
        });
    }

    public Optional<DocumentoSolicitud> buscarPorId(Long idDocumento) {
        BusinessValidations.id(idDocumento, "El id de documento");
        return documentoDAO.buscarPorId(idDocumento);
    }

    public List<DocumentoSolicitud> listarTodos() {
        return documentoDAO.listarTodos();
    }

    public boolean actualizar(DocumentoSolicitud documento) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(documento != null ? documento.getIdDocumento() : null, "El id de documento");
            BusinessValidations.documento(documento);
            return documentoDAO.actualizar(documento);
        });
    }

    public boolean eliminar(Long idDocumento) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(idDocumento, "El id de documento");
            return documentoDAO.eliminar(idDocumento);
        });
    }
}
