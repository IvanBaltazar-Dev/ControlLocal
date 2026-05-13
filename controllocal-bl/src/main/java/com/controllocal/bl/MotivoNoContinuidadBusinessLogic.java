package com.controllocal.bl;

import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.MotivoNoContinuidadDAO;
import com.controllocal.dao.impl.MotivoNoContinuidadDAOImpl;
import com.controllocal.model.comercial.MotivoNoContinuidad;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class MotivoNoContinuidadBusinessLogic {

    private final MotivoNoContinuidadDAO motivoDAO;

    public MotivoNoContinuidadBusinessLogic() {
        this(new MotivoNoContinuidadDAOImpl());
    }

    public MotivoNoContinuidadBusinessLogic(MotivoNoContinuidadDAO motivoDAO) {
        this.motivoDAO = motivoDAO;
    }

    public Long registrar(MotivoNoContinuidad motivo) {
        return TransactionRunner.write(() -> {
            BusinessValidations.motivo(motivo);
            if (motivo.getFechaHora() == null) {
                motivo.setFechaHora(LocalDateTime.now());
            }
            return motivoDAO.crear(motivo);
        });
    }

    public Optional<MotivoNoContinuidad> buscarPorId(Long idMotivo) {
        BusinessValidations.id(idMotivo, "El id de motivo");
        return motivoDAO.buscarPorId(idMotivo);
    }

    public List<MotivoNoContinuidad> listarTodos() {
        return motivoDAO.listarTodos();
    }

    public boolean actualizar(MotivoNoContinuidad motivo) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(motivo != null ? motivo.getIdMotivoNoContinuidad() : null, "El id de motivo");
            BusinessValidations.motivo(motivo);
            return motivoDAO.actualizar(motivo);
        });
    }

    public boolean eliminar(Long idMotivo) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(idMotivo, "El id de motivo");
            return motivoDAO.eliminar(idMotivo);
        });
    }
}
