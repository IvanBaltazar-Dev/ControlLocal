package com.controllocal.bl;

import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.InteraccionComercialDAO;
import com.controllocal.dao.impl.InteraccionComercialDAOImpl;
import com.controllocal.model.comercial.InteraccionComercial;

import java.util.List;
import java.util.Optional;

public class InteraccionComercialBusinessLogic {

    private final InteraccionComercialDAO interaccionDAO;

    public InteraccionComercialBusinessLogic() {
        this(new InteraccionComercialDAOImpl());
    }

    public InteraccionComercialBusinessLogic(InteraccionComercialDAO interaccionDAO) {
        this.interaccionDAO = interaccionDAO;
    }

    public Long registrar(InteraccionComercial interaccion) {
        return TransactionRunner.write(() -> {
            interaccion.registrar();
            BusinessValidations.interaccion(interaccion);
            return interaccionDAO.crear(interaccion);
        });
    }

    public Optional<InteraccionComercial> buscarPorId(Long idInteraccion) {
        BusinessValidations.id(idInteraccion, "El id de interaccion");
        return interaccionDAO.buscarPorId(idInteraccion);
    }

    public List<InteraccionComercial> listarTodos() {
        return interaccionDAO.listarTodos();
    }

    public boolean actualizar(InteraccionComercial interaccion) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(interaccion != null ? interaccion.getIdInteraccion() : null, "El id de interaccion");
            BusinessValidations.interaccion(interaccion);
            return interaccionDAO.actualizar(interaccion);
        });
    }

    public boolean eliminar(Long idInteraccion) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(idInteraccion, "El id de interaccion");
            return interaccionDAO.eliminar(idInteraccion);
        });
    }
}
