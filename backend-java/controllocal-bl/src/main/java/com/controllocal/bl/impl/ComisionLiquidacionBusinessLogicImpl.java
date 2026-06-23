package com.controllocal.bl.impl;

import java.util.List;

import com.controllocal.bl.ComisionLiquidacionBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.dao.ComisionLiquidacionDAO;
import com.controllocal.dao.impl.ComisionLiquidacionDAOImpl;
import com.controllocal.model.comercial.ComisionLiquidacion;

public class ComisionLiquidacionBusinessLogicImpl implements ComisionLiquidacionBusinessLogic {

    private final ComisionLiquidacionDAO comisionDAO;

    public ComisionLiquidacionBusinessLogicImpl() {
        this(new ComisionLiquidacionDAOImpl());
    }

    public ComisionLiquidacionBusinessLogicImpl(ComisionLiquidacionDAO comisionDAO) {
        this.comisionDAO = comisionDAO;
    }

    @Override
    public List<ComisionLiquidacion> listarPorContrato(Long idContrato) {
        BusinessValidations.id(idContrato, "El id de contrato");
        return comisionDAO.listarPorContrato(idContrato);
    }
}
