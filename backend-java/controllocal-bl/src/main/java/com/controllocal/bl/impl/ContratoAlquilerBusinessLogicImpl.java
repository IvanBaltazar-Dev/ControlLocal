package com.controllocal.bl.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
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
import com.controllocal.dao.PublicacionDAO;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.dao.TareaDAO;
import com.controllocal.dao.impl.AlertaDAOImpl;
import com.controllocal.dao.impl.CaptacionDAOImpl;
import com.controllocal.dao.impl.ComisionLiquidacionDAOImpl;
import com.controllocal.dao.impl.ContratoAlquilerDAOImpl;
import com.controllocal.dao.impl.LocalComercialDAOImpl;
import com.controllocal.dao.impl.OportunidadComercialDAOImpl;
import com.controllocal.dao.impl.PrecioLocalDAOImpl;
import com.controllocal.dao.impl.PublicacionDAOImpl;
import com.controllocal.dao.impl.SolicitudAlquilerDAOImpl;
import com.controllocal.dao.impl.TareaDAOImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.ComisionLiquidacion;
import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Publicacion;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.Tarea;
import com.controllocal.model.comercial.enums.EstadoComision;
import com.controllocal.model.comercial.enums.EstadoContrato;
import com.controllocal.model.comercial.enums.EstadoOportunidadComercial;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.enums.EstadoTarea;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.comercial.enums.Severidad;
import com.controllocal.model.comercial.enums.TipoAlerta;
import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.inmueble.enums.EstadoLocalComercial;
import com.controllocal.model.inmueble.enums.EstadoPublicacion;

public class ContratoAlquilerBusinessLogicImpl implements ContratoAlquilerBusinessLogic {

    private final ContratoAlquilerDAO contratoDAO;
    private final SolicitudAlquilerDAO solicitudDAO;
    private final OportunidadComercialDAO oportunidadDAO;
    private final CaptacionDAO captacionDAO;
    private final LocalComercialDAO localDAO;
    private final ComisionLiquidacionDAO comisionDAO;
    private final PrecioLocalDAO precioLocalDAO;
    private final AlertaDAO alertaDAO;
    private final PublicacionDAO publicacionDAO;
    private final TareaDAO tareaDAO;

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
        this(contratoDAO, solicitudDAO, oportunidadDAO, captacionDAO, localDAO, comisionDAO,
                precioLocalDAO, alertaDAO, new PublicacionDAOImpl(), new TareaDAOImpl());
    }

    public ContratoAlquilerBusinessLogicImpl(
            ContratoAlquilerDAO contratoDAO,
            SolicitudAlquilerDAO solicitudDAO,
            OportunidadComercialDAO oportunidadDAO,
            CaptacionDAO captacionDAO,
            LocalComercialDAO localDAO,
            ComisionLiquidacionDAO comisionDAO,
            PrecioLocalDAO precioLocalDAO,
            AlertaDAO alertaDAO,
            PublicacionDAO publicacionDAO,
            TareaDAO tareaDAO) {
        this.contratoDAO = contratoDAO;
        this.solicitudDAO = solicitudDAO;
        this.oportunidadDAO = oportunidadDAO;
        this.captacionDAO = captacionDAO;
        this.localDAO = localDAO;
        this.comisionDAO = comisionDAO;
        this.precioLocalDAO = precioLocalDAO;
        this.alertaDAO = alertaDAO;
        this.publicacionDAO = publicacionDAO;
        this.tareaDAO = tareaDAO;
    }

    @Override
    public Long registrarPorSolicitud(Long idSolicitud) {
        return registrarPorSolicitud(idSolicitud, LocalDate.now(), EstadoContrato.VIGENTE, null);
    }

    @Override
    public Long registrarPorSolicitud(Long idSolicitud, LocalDate fechaCierre,
            EstadoContrato estadoContrato, String incidencias) {
        BusinessValidations.id(idSolicitud, "El id de solicitud");
        LocalDate cierre = fechaCierre != null ? fechaCierre : LocalDate.now();
        if (cierre.isAfter(LocalDate.now())) {
            throw new BusinessException("La fecha de cierre no puede ser futura.");
        }
        EstadoContrato estado = estadoContrato != null ? estadoContrato : EstadoContrato.VIGENTE;
        if (estado != EstadoContrato.FIRMADO && estado != EstadoContrato.VIGENTE) {
            throw new BusinessException("El contrato solo puede cerrarse como Firmado o Vigente.");
        }
        String notas = incidencias != null && !incidencias.isBlank() ? incidencias.trim() : null;

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
            contrato.setFechaCierre(cierre);
            contrato.setEstadoContrato(estado);
            contrato.setIncidencias(notas);
            Long idContrato = contratoDAO.crear(contrato);
            contrato.setIdContratoAlquiler(idContrato);

            // Comision bruta = % pactado x un mes de renta (= monto propuesto aprobado).
            // Sin reparto automatico 50/50: el broker supervisor definira el monto del agente
            // y el sistema calculara el de la empresa (Etapa 2). La liquidacion nace PENDIENTE.
            BigDecimal renta = solicitud.getMontoPropuesto();
            BigDecimal comisionBruta = calcularComision(captacion.getComisionPactada(), renta);

            ComisionLiquidacion comision = new ComisionLiquidacion();
            comision.setContratoAlquiler(contrato);
            comision.setMonto(comisionBruta);
            comision.setMoneda(Moneda.USD);
            comision.setEstado(EstadoComision.PENDIENTE);
            // montoAgente / montoEmpresa / fechaCobro / formaPago quedan NULL: se definen despues.
            comisionDAO.crear(comision);

            // Cierra la oportunidad como trato concretado (Finalizada exitosa).
            oportunidad.cerrarExitosa();
            BusinessValidations.oportunidad(oportunidad);
            oportunidadDAO.actualizar(oportunidad);

            // La solicitud pasa a CERRADA: el alquiler se concreto y no se reabre.
            solicitud.cerrar();
            solicitudDAO.actualizar(solicitud);

            // La captacion ya cumplio su objetivo: queda cerrada por alquiler concretado.
            captacion.cerrar();
            captacion.setFechaFinVigencia(cierre);
            captacionDAO.actualizar(captacion);

            // El local alquilado deja de estar disponible + precio CERRADO real del local,
            // y se dan de baja sus publicaciones.
            Long idLocal = captacion.getLocalComercial() != null
                    ? captacion.getLocalComercial().getIdLocal() : null;
            if (idLocal != null && idLocal > 0) {
                localDAO.buscarPorId(idLocal).ifPresent(local -> {
                    local.cambiarEstado(EstadoLocalComercial.NO_DISPONIBLE);
                    localDAO.actualizar(local);
                });
                precioLocalDAO.registrar(idLocal, "C", Moneda.USD.getCodigo(), renta, LocalDate.now());
                cerrarPublicaciones(idLocal);
            }

            // Resuelve (Completadas) las tareas abiertas atadas a la operacion ya cerrada.
            resolverTareas(idOportunidad, idSolicitud, idCaptacion, idLocal);

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

    // Cierra (estado Cerrado + fecha de baja) las publicaciones aun vigentes del local alquilado.
    private void cerrarPublicaciones(Long idLocal) {
        for (Publicacion publicacion : publicacionDAO.listarPorInmueble(idLocal)) {
            if (publicacion.getEstado() != EstadoPublicacion.CERRADO) {
                publicacion.setEstado(EstadoPublicacion.CERRADO);
                publicacion.setFechaBaja(LocalDateTime.now());
                publicacionDAO.actualizar(publicacion);
            }
        }
    }

    // Marca como Completadas las tareas pendientes/en proceso atadas a las entidades de la operacion.
    private void resolverTareas(Long idOportunidad, Long idSolicitud, Long idCaptacion, Long idLocal) {
        resolverTareasDe(TipoEntidad.OPORTUNIDAD, idOportunidad);
        resolverTareasDe(TipoEntidad.SOLICITUD_ALQUILER, idSolicitud);
        resolverTareasDe(TipoEntidad.CAPTACION, idCaptacion);
        resolverTareasDe(TipoEntidad.INMUEBLE, idLocal);
    }

    private void resolverTareasDe(TipoEntidad tipo, Long entidadId) {
        if (entidadId == null || entidadId <= 0) {
            return;
        }
        for (Tarea tarea : tareaDAO.listarPorEntidad(tipo, entidadId)) {
            if (tarea.getEstado() == EstadoTarea.PENDIENTE || tarea.getEstado() == EstadoTarea.EN_PROCESO) {
                tarea.setEstado(EstadoTarea.COMPLETADA);
                tarea.setFechaCompletada(LocalDateTime.now());
                tareaDAO.actualizar(tarea);
            }
        }
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

    @Override
    public List<ContratoAlquiler> listarPaginaFiltrado(Long idAgente, Collection<Long> idsCaptacion,
            int limite, int desplazamiento) {
        return contratoDAO.listarPaginaFiltrado(idAgente, idsCaptacion, limite, desplazamiento);
    }

    @Override
    public long contarFiltrado(Long idAgente, Collection<Long> idsCaptacion) {
        return contratoDAO.contarFiltrado(idAgente, idsCaptacion);
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
