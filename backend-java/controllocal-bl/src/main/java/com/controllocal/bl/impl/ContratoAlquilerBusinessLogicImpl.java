package com.controllocal.bl.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.ContratoAlquilerBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AlertaDAO;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.ComisionLiquidacionDAO;
import com.controllocal.dao.ContratoAlquilerDAO;
import com.controllocal.dao.LocalComercialDAO;
import com.controllocal.dao.OportunidadComercialDAO;
import com.controllocal.dao.PrecioLocalDAO;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.dao.impl.AlertaDAOImpl;
import com.controllocal.dao.impl.CaptacionDAOImpl;
import com.controllocal.dao.impl.ComisionLiquidacionDAOImpl;
import com.controllocal.dao.impl.ContratoAlquilerDAOImpl;
import com.controllocal.dao.impl.LocalComercialDAOImpl;
import com.controllocal.dao.impl.OportunidadComercialDAOImpl;
import com.controllocal.dao.impl.PrecioLocalDAOImpl;
import com.controllocal.dao.impl.SolicitudAlquilerDAOImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.ComisionLiquidacion;
import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.enums.EstadoComision;
import com.controllocal.model.comercial.enums.EstadoContrato;
import com.controllocal.model.comercial.enums.EstadoOportunidadComercial;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.comercial.enums.Severidad;
import com.controllocal.model.comercial.enums.TipoAlerta;
import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.inmueble.enums.EstadoLocalComercial;

public class ContratoAlquilerBusinessLogicImpl implements ContratoAlquilerBusinessLogic {

    private final ContratoAlquilerDAO contratoDAO;
    private final SolicitudAlquilerDAO solicitudDAO;
    private final OportunidadComercialDAO oportunidadDAO;
    private final CaptacionDAO captacionDAO;
    private final LocalComercialDAO localDAO;
    private final ComisionLiquidacionDAO comisionDAO;
    private final PrecioLocalDAO precioLocalDAO;
    private final AlertaDAO alertaDAO;

    public ContratoAlquilerBusinessLogicImpl() {
        this(new ContratoAlquilerDAOImpl(), new SolicitudAlquilerDAOImpl(),
                new OportunidadComercialDAOImpl(), new CaptacionDAOImpl(), new LocalComercialDAOImpl(),
                new ComisionLiquidacionDAOImpl(), new PrecioLocalDAOImpl());
    }

    public ContratoAlquilerBusinessLogicImpl(
            ContratoAlquilerDAO contratoDAO,
            SolicitudAlquilerDAO solicitudDAO,
            OportunidadComercialDAO oportunidadDAO,
            CaptacionDAO captacionDAO,
            LocalComercialDAO localDAO,
            ComisionLiquidacionDAO comisionDAO,
            PrecioLocalDAO precioLocalDAO) {
        this(contratoDAO, solicitudDAO, oportunidadDAO, captacionDAO, localDAO, comisionDAO,
                precioLocalDAO, new AlertaDAOImpl());
    }

    public ContratoAlquilerBusinessLogicImpl(
            ContratoAlquilerDAO contratoDAO,
            SolicitudAlquilerDAO solicitudDAO,
            OportunidadComercialDAO oportunidadDAO,
            CaptacionDAO captacionDAO,
            LocalComercialDAO localDAO,
            ComisionLiquidacionDAO comisionDAO,
            PrecioLocalDAO precioLocalDAO,
            AlertaDAO alertaDAO) {
        this.contratoDAO = contratoDAO;
        this.solicitudDAO = solicitudDAO;
        this.oportunidadDAO = oportunidadDAO;
        this.captacionDAO = captacionDAO;
        this.localDAO = localDAO;
        this.comisionDAO = comisionDAO;
        this.precioLocalDAO = precioLocalDAO;
        this.alertaDAO = alertaDAO;
    }

    @Override
    public Long registrarPorSolicitud(Long idSolicitud) {
        BusinessValidations.id(idSolicitud, "El id de solicitud");
        return TransactionRunner.write(conn -> {
            SolicitudAlquiler solicitud = solicitudDAO.buscarPorId(idSolicitud)
                    .orElseThrow(() -> new BusinessException("Solicitud de alquiler no encontrada."));
            if (solicitud.getEstado() != EstadoSolicitudAlquiler.APROBADA) {
                throw new BusinessException("Solo se puede registrar el alquiler de una solicitud aprobada.");
            }

            Long idOportunidad = solicitud.getOportunidadComercial() != null
                    ? solicitud.getOportunidadComercial().getIdOportunidad() : null;
            BusinessValidations.id(idOportunidad, "La oportunidad de la solicitud");
            OportunidadComercial oportunidad = oportunidadDAO.buscarPorId(idOportunidad)
                    .orElseThrow(() -> new BusinessException("Oportunidad comercial no encontrada."));
            if (oportunidad.getEstado() != EstadoOportunidadComercial.ABIERTA
                    && oportunidad.getEstado() != EstadoOportunidadComercial.SOLICITUD_CREADA) {
                throw new BusinessException("La oportunidad ya esta cerrada; no admite un nuevo contrato.");
            }
            if (contratoDAO.buscarPorOportunidad(idOportunidad).isPresent()) {
                throw new BusinessException("Esta operacion ya tiene un contrato de alquiler registrado.");
            }

            Long idCaptacion = solicitud.getCaptacion() != null ? solicitud.getCaptacion().getIdCaptacion() : null;
            BusinessValidations.id(idCaptacion, "La captacion de la solicitud");
            Captacion captacion = captacionDAO.buscarPorId(idCaptacion)
                    .orElseThrow(() -> new BusinessException("Captacion no encontrada."));

            // Contrato minimo: vinculo + formalizacion. Las condiciones del trato viven en la solicitud.
            ContratoAlquiler contrato = new ContratoAlquiler();
            contrato.setOportunidad(oportunidad);
            contrato.setSolicitudAlquiler(solicitud);
            contrato.setFechaCierre(LocalDate.now());
            contrato.setEstadoContrato(EstadoContrato.VIGENTE);
            Long idContrato = contratoDAO.crear(contrato);
            contrato.setIdContratoAlquiler(idContrato);

            // Comision = % pactado x un mes de renta (= monto propuesto aprobado). Split 50/50.
            BigDecimal renta = solicitud.getMontoPropuesto();
            BigDecimal comisionTotal = calcularComision(captacion.getComisionPactada(), renta);
            BigDecimal montoAgente = comisionTotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            BigDecimal montoEmpresa = comisionTotal.subtract(montoAgente);

            ComisionLiquidacion comision = new ComisionLiquidacion();
            comision.setContratoAlquiler(contrato);
            comision.setMonto(comisionTotal);
            comision.setMontoAgente(montoAgente);
            comision.setMontoEmpresa(montoEmpresa);
            comision.setMoneda(Moneda.USD);
            comision.setEstado(EstadoComision.PENDIENTE);
            // fechaCobro queda NULL: el cobro se registra despues.
            comisionDAO.crear(comision);

            // Cierra la oportunidad como trato concretado (Finalizada exitosa).
            oportunidad.cerrarExitosa();
            BusinessValidations.oportunidad(oportunidad);
            oportunidadDAO.actualizar(oportunidad);

            // La solicitud pasa a CERRADA: el alquiler se concreto y no se reabre.
            solicitud.cerrar();
            solicitudDAO.actualizar(solicitud);

            // El local alquilado deja de estar disponible + precio CERRADO real del local.
            Long idLocal = captacion.getLocalComercial() != null
                    ? captacion.getLocalComercial().getIdLocal() : null;
            if (idLocal != null && idLocal > 0) {
                localDAO.buscarPorId(idLocal).ifPresent(local -> {
                    local.cambiarEstado(EstadoLocalComercial.NO_DISPONIBLE);
                    localDAO.actualizar(local);
                });
                precioLocalDAO.registrar(idLocal, "C", Moneda.USD.getCodigo(), renta, LocalDate.now());
            }

            // Aviso real al broker supervisor: el agente concreto el alquiler (cierre exitoso).
            // Se ata al agente de la solicitud (siempre poblado); el broker lo ve via broker_agente.
            if (solicitud.getAgenteResponsable() != null
                    && solicitud.getAgenteResponsable().getIdAgente() != null) {
                alertaDAO.crear(AlertaBusinessLogicImpl.construir(
                        TipoAlerta.OPORTUNIDAD_CERRADA, Severidad.INFO, TipoEntidad.OPORTUNIDAD,
                        idOportunidad, solicitud.getAgenteResponsable(),
                        "El agente concreto el alquiler de la oportunidad "
                                + oportunidad.getCodigoOportunidad() + "."));
            }
            return idContrato;
        });
    }

    @Override
    public Optional<ContratoAlquiler> buscarPorId(Long idContrato) {
        BusinessValidations.id(idContrato, "El id de contrato");
        return contratoDAO.buscarPorId(idContrato);
    }

    @Override
    public Optional<ContratoAlquiler> buscarPorOportunidad(Long idOportunidad) {
        BusinessValidations.id(idOportunidad, "El id de oportunidad");
        return contratoDAO.buscarPorOportunidad(idOportunidad);
    }

    @Override
    public List<ContratoAlquiler> listarTodos() {
        return contratoDAO.listarTodos();
    }

    // Comision del contrato = renta mensual * %comision pactada.
    private static BigDecimal calcularComision(BigDecimal comisionPactada, BigDecimal renta) {
        if (comisionPactada == null || renta == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return renta.multiply(comisionPactada)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
