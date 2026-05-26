package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.DocumentoSolicitudBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.DocumentoSolicitudDAO;
import com.controllocal.dao.impl.DocumentoSolicitudDAOImpl;
import com.controllocal.model.comercial.DocumentoSolicitud;

public class DocumentoSolicitudBusinessLogicImpl implements DocumentoSolicitudBusinessLogic {

    private final DocumentoSolicitudDAO documentoDAO;

    public DocumentoSolicitudBusinessLogicImpl() {
        this(new DocumentoSolicitudDAOImpl());
    }

    public DocumentoSolicitudBusinessLogicImpl(DocumentoSolicitudDAO documentoDAO) {
        this.documentoDAO = documentoDAO;
    }

    public Long registrar(DocumentoSolicitud documento) {
        return TransactionRunner.write(conn -> {
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
}

