package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.LocalComercialBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.impl.LocalComercialDAOImpl;
import com.controllocal.dao.LocalComercialDAO;
import com.controllocal.model.inmueble.LocalComercial;

public class LocalComercialBusinessLogicImpl implements LocalComercialBusinessLogic {

    private final LocalComercialDAO localDAO;

    public LocalComercialBusinessLogicImpl() {
        this(new LocalComercialDAOImpl());
    }

    public LocalComercialBusinessLogicImpl(LocalComercialDAO localDAO) {
        this.localDAO = localDAO;
    }

    public Long registrar(LocalComercial local) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.local(local);
            return localDAO.crear(local);
        });
    }

    public Optional<LocalComercial> buscarPorId(Long idLocal) {
        BusinessValidations.id(idLocal, "El id de local comercial");
        return localDAO.buscarPorId(idLocal);
    }

    public List<LocalComercial> listarTodos() {
        return localDAO.listarTodos();
    }

    public boolean actualizar(LocalComercial local) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(local != null ? local.getIdLocal() : null, "El id de local comercial");
            BusinessValidations.local(local);
            return localDAO.actualizar(local);
        });
    }

    public boolean desactivar(Long idLocal) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idLocal, "El id de local comercial");
            return localDAO.eliminar(idLocal);
        });
    }
}

