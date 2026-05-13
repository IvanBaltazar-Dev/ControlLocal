package com.controllocal.bl;

import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.model.usuario.AgenteInmobiliario;

import java.util.List;
import java.util.Optional;

public class AgenteBusinessLogic {

    private final AgenteInmobiliarioDAO agenteDAO;

    public AgenteBusinessLogic() {
        this(new AgenteInmobiliarioDAOImpl());
    }

    public AgenteBusinessLogic(AgenteInmobiliarioDAO agenteDAO) {
        this.agenteDAO = agenteDAO;
    }

    public Long registrar(AgenteInmobiliario agente) {
        return TransactionRunner.write(() -> {
            BusinessValidations.agente(agente);
            return agenteDAO.crear(agente);
        });
    }

    public Optional<AgenteInmobiliario> buscarPorId(Long idAgente) {
        BusinessValidations.id(idAgente, "El id de agente");
        return agenteDAO.buscarPorId(idAgente);
    }

    public List<AgenteInmobiliario> listarTodos() {
        return agenteDAO.listarTodos();
    }

    public boolean actualizar(AgenteInmobiliario agente) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(agente != null ? agente.getIdAgente() : null, "El id de agente");
            BusinessValidations.agente(agente);
            return agenteDAO.actualizar(agente);
        });
    }

    public boolean desactivar(Long idAgente) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(idAgente, "El id de agente");
            return agenteDAO.eliminar(idAgente);
        });
    }
}
