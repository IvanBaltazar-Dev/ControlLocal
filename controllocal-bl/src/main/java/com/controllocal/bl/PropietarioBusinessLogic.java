package com.controllocal.bl;

import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.PropietarioDAO;
import com.controllocal.dao.impl.PropietarioDAOImpl;
import com.controllocal.model.persona.Propietario;

import java.util.List;
import java.util.Optional;

public class PropietarioBusinessLogic {

    private final PropietarioDAO propietarioDAO;

    public PropietarioBusinessLogic() {
        this(new PropietarioDAOImpl());
    }

    public PropietarioBusinessLogic(PropietarioDAO propietarioDAO) {
        this.propietarioDAO = propietarioDAO;
    }

    public Long registrar(Propietario propietario) {
        return TransactionRunner.write(() -> {
            BusinessValidations.propietario(propietario);
            return propietarioDAO.crear(propietario);
        });
    }

    public Optional<Propietario> buscarPorId(Long idPropietario) {
        BusinessValidations.id(idPropietario, "El id de propietario");
        return propietarioDAO.buscarPorId(idPropietario);
    }

    public List<Propietario> listarTodos() {
        return propietarioDAO.listarTodos();
    }

    public boolean actualizar(Propietario propietario) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(propietario != null ? propietario.getIdPropietario() : null, "El id de propietario");
            BusinessValidations.propietario(propietario);
            return propietarioDAO.actualizar(propietario);
        });
    }

    public boolean desactivar(Long idPropietario) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(idPropietario, "El id de propietario");
            return propietarioDAO.eliminar(idPropietario);
        });
    }
}
