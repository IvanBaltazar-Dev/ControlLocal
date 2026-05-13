package com.controllocal.bl;

import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.VisitaDAO;
import com.controllocal.dao.impl.VisitaDAOImpl;
import com.controllocal.model.comercial.Visita;

import java.util.List;
import java.util.Optional;

public class VisitaBusinessLogic {

    private final VisitaDAO visitaDAO;

    public VisitaBusinessLogic() {
        this(new VisitaDAOImpl());
    }

    public VisitaBusinessLogic(VisitaDAO visitaDAO) {
        this.visitaDAO = visitaDAO;
    }

    public Long registrar(Visita visita) {
        return TransactionRunner.write(() -> {
            BusinessValidations.visita(visita);
            return visitaDAO.crear(visita);
        });
    }

    public Optional<Visita> buscarPorId(Long idVisita) {
        BusinessValidations.id(idVisita, "El id de visita");
        return visitaDAO.buscarPorId(idVisita);
    }

    public List<Visita> listarTodos() {
        return visitaDAO.listarTodos();
    }

    public boolean actualizar(Visita visita) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(visita != null ? visita.getIdVisita() : null, "El id de visita");
            BusinessValidations.visita(visita);
            return visitaDAO.actualizar(visita);
        });
    }

    public boolean eliminar(Long idVisita) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(idVisita, "El id de visita");
            return visitaDAO.eliminar(idVisita);
        });
    }
}
