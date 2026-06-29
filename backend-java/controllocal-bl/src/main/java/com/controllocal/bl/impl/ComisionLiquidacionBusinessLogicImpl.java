package com.controllocal.bl.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.ComisionLiquidacionBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AlertaDAO;
import com.controllocal.dao.ComisionLiquidacionDAO;
import com.controllocal.dao.ContratoAlquilerDAO;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.dao.impl.AlertaDAOImpl;
import com.controllocal.dao.impl.ComisionLiquidacionDAOImpl;
import com.controllocal.dao.impl.ContratoAlquilerDAOImpl;
import com.controllocal.dao.impl.SolicitudAlquilerDAOImpl;
import com.controllocal.model.comercial.ComisionLiquidacion;
import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.enums.EstadoComision;
import com.controllocal.model.comercial.enums.FormaPago;
import com.controllocal.model.comercial.enums.Severidad;
import com.controllocal.model.comercial.enums.TipoAlerta;
import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class ComisionLiquidacionBusinessLogicImpl implements ComisionLiquidacionBusinessLogic {

    private final ComisionLiquidacionDAO comisionDAO;
    private final ContratoAlquilerDAO contratoDAO;
    private final SolicitudAlquilerDAO solicitudDAO;
    private final AlertaDAO alertaDAO;

    public ComisionLiquidacionBusinessLogicImpl() {
        this(new ComisionLiquidacionDAOImpl(), new ContratoAlquilerDAOImpl(),
                new SolicitudAlquilerDAOImpl(), new AlertaDAOImpl());
    }

    public ComisionLiquidacionBusinessLogicImpl(ComisionLiquidacionDAO comisionDAO,
            ContratoAlquilerDAO contratoDAO, SolicitudAlquilerDAO solicitudDAO, AlertaDAO alertaDAO) {
        this.comisionDAO = comisionDAO;
        this.contratoDAO = contratoDAO;
        this.solicitudDAO = solicitudDAO;
        this.alertaDAO = alertaDAO;
    }

    @Override
    public List<ComisionLiquidacion> listarPorContrato(Long idContrato) {
        BusinessValidations.id(idContrato, "El id de contrato");
        return comisionDAO.listarPorContrato(idContrato);
    }

    @Override
    public List<ComisionLiquidacion> listarTodos() {
        return comisionDAO.listarTodos();
    }

    @Override
    public List<ComisionLiquidacion> listarPorContratos(java.util.Collection<Long> idsContrato) {
        return comisionDAO.listarPorContratos(idsContrato);
    }

    @Override
    public Optional<ComisionLiquidacion> buscarPorId(Long idComision) {
        BusinessValidations.id(idComision, "El id de la liquidacion");
        return comisionDAO.buscarPorId(idComision);
    }

    @Override
    public ComisionLiquidacion asignarMontoAgente(Long idComision, BigDecimal montoAgente) {
        BusinessValidations.id(idComision, "El id de la liquidacion");
        if (montoAgente == null || montoAgente.signum() < 0) {
            throw new BusinessException("El monto del agente debe ser cero o positivo.");
        }
        return TransactionRunner.write(conn -> {
            ComisionLiquidacion comision = comisionDAO.buscarPorId(idComision)
                    .orElseThrow(() -> new BusinessException("Liquidacion de comision no encontrada."));
            if (comision.getEstado() != EstadoComision.PENDIENTE) {
                throw new BusinessException("Solo se puede asignar el monto del agente mientras la comision esta Pendiente.");
            }
            BigDecimal bruto = comision.getMonto() != null ? comision.getMonto() : BigDecimal.ZERO;
            if (montoAgente.compareTo(bruto) > 0) {
                throw new BusinessException("El monto del agente no puede superar la comision bruta.");
            }
            boolean primeraAsignacion = comision.getMontoAgente() == null;
            comision.setMontoAgente(montoAgente);
            comision.setMontoEmpresa(bruto.subtract(montoAgente));
            comisionDAO.actualizar(comision);

            // Etapa 3: al completar la asignacion, avisar al agente que su comision esta lista
            // para cobro. El mensaje nunca expone el monto neto asignado. Solo en la primera
            // asignacion, para no repetir el aviso en cada reajuste.
            if (primeraAsignacion) {
                Operacion op = operacion(comision);
                if (op != null && op.agente() != null) {
                    alertaDAO.crear(AlertaBusinessLogicImpl.construir(
                            TipoAlerta.COMISION_ASIGNADA, Severidad.INFO, TipoEntidad.CONTRATO_ALQUILER,
                            op.idContrato(), op.agente(),
                            "Tu comision de la operacion " + op.codigo() + " esta lista para cobro."));
                }
            }
            return comision;
        });
    }

    @Override
    public ComisionLiquidacion registrarCobro(Long idComision, EstadoComision estado,
            LocalDate fechaCobro, FormaPago formaPago) {
        BusinessValidations.id(idComision, "El id de la liquidacion");
        if (estado != EstadoComision.COBRADA && estado != EstadoComision.ANULADA) {
            throw new BusinessException("El cobro solo admite los estados Cobrada o Anulada.");
        }
        return TransactionRunner.write(conn -> {
            ComisionLiquidacion comision = comisionDAO.buscarPorId(idComision)
                    .orElseThrow(() -> new BusinessException("Liquidacion de comision no encontrada."));
            if (comision.getEstado() == EstadoComision.COBRADA || comision.getEstado() == EstadoComision.ANULADA) {
                throw new BusinessException("La comision ya tiene un cobro registrado (Cobrada o Anulada).");
            }

            if (estado == EstadoComision.COBRADA) {
                if (comision.getMontoAgente() == null) {
                    throw new BusinessException("Antes de cobrar, el broker supervisor debe asignar el monto del agente.");
                }
                if (fechaCobro == null) {
                    throw new BusinessException("Registra la fecha de cobro.");
                }
                if (fechaCobro.isAfter(LocalDate.now())) {
                    throw new BusinessException("La fecha de cobro no puede ser futura.");
                }
                if (formaPago == null) {
                    throw new BusinessException("Registra la forma de pago.");
                }
                comision.setFechaCobro(fechaCobro);
                comision.setFormaPago(formaPago);
            } else {
                // ANULADA: no conserva datos de cobro.
                comision.setFechaCobro(null);
                comision.setFormaPago(null);
            }
            comision.setEstado(estado);
            comisionDAO.actualizar(comision);

            // Etapa 3: al registrar el cobro, avisar al agente que su comision fue cobrada.
            if (estado == EstadoComision.COBRADA) {
                Operacion op = operacion(comision);
                if (op != null && op.agente() != null) {
                    alertaDAO.crear(AlertaBusinessLogicImpl.construir(
                            TipoAlerta.COMISION_COBRADA, Severidad.INFO, TipoEntidad.CONTRATO_ALQUILER,
                            op.idContrato(), op.agente(),
                            "Tu comision de la operacion " + op.codigo() + " fue cobrada."));
                }
            }
            return comision;
        });
    }

    // Resuelve contrato -> solicitud para obtener el agente destinatario de la alerta y el
    // codigo de operacion que se muestra (sin montos). Best-effort: si falta el vinculo, no
    // se emite alerta.
    private Operacion operacion(ComisionLiquidacion comision) {
        Long idContrato = comision.getContratoAlquiler() != null
                ? comision.getContratoAlquiler().getIdContratoAlquiler() : null;
        if (idContrato == null) {
            return null;
        }
        ContratoAlquiler contrato = contratoDAO.buscarPorId(idContrato).orElse(null);
        Long idSolicitud = contrato != null && contrato.getSolicitudAlquiler() != null
                ? contrato.getSolicitudAlquiler().getIdSolicitud() : null;
        if (idSolicitud == null) {
            return new Operacion(idContrato, null, "");
        }
        SolicitudAlquiler solicitud = solicitudDAO.buscarPorId(idSolicitud).orElse(null);
        if (solicitud == null) {
            return new Operacion(idContrato, null, "");
        }
        String codigo = solicitud.getCodigoSolicitud() != null ? solicitud.getCodigoSolicitud() : "";
        return new Operacion(idContrato, solicitud.getAgenteResponsable(), codigo);
    }

    private record Operacion(Long idContrato, AgenteInmobiliario agente, String codigo) {
    }
}
