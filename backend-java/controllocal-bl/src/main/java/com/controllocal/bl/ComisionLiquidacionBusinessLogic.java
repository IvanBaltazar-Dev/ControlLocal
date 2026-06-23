package com.controllocal.bl;

import java.util.List;

import com.controllocal.model.comercial.ComisionLiquidacion;

/**
 * Lectura de las liquidaciones de comision de un contrato (tabla comision_liquidacion).
 * La creacion ocurre dentro del cierre del alquiler (ContratoAlquilerBusinessLogic).
 */
public interface ComisionLiquidacionBusinessLogic {

    List<ComisionLiquidacion> listarPorContrato(Long idContrato);
}
