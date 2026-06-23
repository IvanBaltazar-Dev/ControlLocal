package com.controllocal.bl.impl;

import java.time.LocalDate;
import java.util.List;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.PrecioLocalBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.PrecioLocalDAO;
import com.controllocal.dao.impl.PrecioLocalDAOImpl;
import com.controllocal.model.inmueble.PrecioLocal;

public class PrecioLocalBusinessLogicImpl implements PrecioLocalBusinessLogic {

    private final PrecioLocalDAO precioLocalDAO;

    public PrecioLocalBusinessLogicImpl() {
        this(new PrecioLocalDAOImpl());
    }

    public PrecioLocalBusinessLogicImpl(PrecioLocalDAO precioLocalDAO) {
        this.precioLocalDAO = precioLocalDAO;
    }

    @Override
    public List<PrecioLocal> listarPorLocal(Long idLocal) {
        BusinessValidations.id(idLocal, "El id de local comercial");
        return precioLocalDAO.listarPorLocal(idLocal);
    }

    @Override
    public Long registrar(PrecioLocal precio) {
        return TransactionRunner.write(conn -> {
            if (precio == null) {
                throw new BusinessException("El precio del local es obligatorio.");
            }
            BusinessValidations.id(precio.getIdLocal(), "El id de local comercial");
            if (precio.getHito() == null) {
                throw new BusinessException("El hito del precio es obligatorio.");
            }
            if (precio.getMoneda() == null) {
                throw new BusinessException("La moneda del precio es obligatoria.");
            }
            if (precio.getMonto() == null || precio.getMonto().signum() < 0) {
                throw new BusinessException("El monto del precio no puede ser negativo.");
            }
            if (precio.getFecha() == null) {
                precio.setFecha(LocalDate.now());
            }
            return precioLocalDAO.crear(precio);
        });
    }
}
