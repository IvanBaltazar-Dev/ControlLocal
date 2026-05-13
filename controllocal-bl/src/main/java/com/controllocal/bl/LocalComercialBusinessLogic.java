package com.controllocal.bl;

import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.LocalComercialDAO;
import com.controllocal.dao.impl.LocalComercialDAOImpl;
import com.controllocal.model.inmueble.LocalComercial;

import java.util.List;
import java.util.Optional;

public class LocalComercialBusinessLogic {

    private final LocalComercialDAO localDAO;

    public LocalComercialBusinessLogic() {
        this(new LocalComercialDAOImpl());
    }

    public LocalComercialBusinessLogic(LocalComercialDAO localDAO) {
        this.localDAO = localDAO;
    }

    public Long registrar(LocalComercial local) {
        return TransactionRunner.write(() -> {
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
        return TransactionRunner.write(() -> {
            BusinessValidations.id(local != null ? local.getIdLocal() : null, "El id de local comercial");
            BusinessValidations.local(local);
            return localDAO.actualizar(local);
        });
    }

    public boolean desactivar(Long idLocal) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(idLocal, "El id de local comercial");
            return localDAO.eliminar(idLocal);
        });
    }
}
