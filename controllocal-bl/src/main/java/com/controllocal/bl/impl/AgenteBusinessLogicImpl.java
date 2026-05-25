package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.AgenteBusinessLogic;
import com.controllocal.bl.BusinessException;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class AgenteBusinessLogicImpl implements AgenteBusinessLogic {

    private final AgenteInmobiliarioDAO agenteDAO;

    public AgenteBusinessLogicImpl() {
        this(new AgenteInmobiliarioDAOImpl());
    }

    public AgenteBusinessLogicImpl(AgenteInmobiliarioDAO agenteDAO) {
        this.agenteDAO = agenteDAO;
    }

    public Long registrar(AgenteInmobiliario agente) {
        return TransactionRunner.write(conn -> {
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
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(agente != null ? agente.getIdAgente() : null, "El id de agente");
            BusinessValidations.agente(agente);
            return agenteDAO.actualizar(agente);
        });
    }

    public boolean desactivar(Long idAgente) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idAgente, "El id de agente");
            return agenteDAO.eliminar(idAgente);
        });
    }
}

