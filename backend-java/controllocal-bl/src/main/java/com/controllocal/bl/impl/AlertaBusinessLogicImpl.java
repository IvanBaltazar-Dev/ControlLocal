package com.controllocal.bl.impl;

import java.time.LocalDateTime;
import java.util.List;

import com.controllocal.bl.AlertaBusinessLogic;
import com.controllocal.bl.BusinessException;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AlertaDAO;
import com.controllocal.dao.impl.AlertaDAOImpl;
import com.controllocal.model.comercial.Alerta;
import com.controllocal.model.comercial.enums.EstadoAlerta;

public class AlertaBusinessLogicImpl implements AlertaBusinessLogic {

    private final AlertaDAO alertaDAO;

    public AlertaBusinessLogicImpl() {
        this(new AlertaDAOImpl());
    }

    public AlertaBusinessLogicImpl(AlertaDAO alertaDAO) {
        this.alertaDAO = alertaDAO;
    }

    @Override
    public Long crear(Alerta alerta) {
        return TransactionRunner.write(conn -> {
            prepararNueva(alerta);
            return alertaDAO.crear(alerta);
        });
    }

    @Override
    public List<Alerta> listarActivasPorAgente(Long idAgente) {
        return alertaDAO.listarActivasPorAgente(idAgente);
    }

    @Override
    public List<Alerta> listarActivasPorBroker(Long idBroker) {
        return alertaDAO.listarActivasPorBroker(idBroker);
    }

    @Override
    public List<Alerta> listarActivas() {
        return alertaDAO.listarActivas();
    }

    @Override
    public boolean marcarAtendida(Long idAlerta) {
        return TransactionRunner.write(
                (TransactionRunner.TransactionalConnectionSupplier<Boolean>)
                        conn -> alertaDAO.marcarAtendida(idAlerta));
    }

    // Completa los campos de control de una alerta recien generada. Reutilizable por
    // otras BL que crean alertas dentro de su propia transaccion (sin anidar otra).
    public static void prepararNueva(Alerta alerta) {
        if (alerta == null) {
            throw new BusinessException("La alerta es obligatoria.");
        }
        if (alerta.getEstado() == null) {
            alerta.setEstado(EstadoAlerta.ACTIVA);
        }
        if (alerta.getFechaGeneracion() == null) {
            alerta.setFechaGeneracion(LocalDateTime.now());
        }
    }
}
