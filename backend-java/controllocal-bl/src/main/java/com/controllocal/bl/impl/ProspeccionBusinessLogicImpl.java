package com.controllocal.bl.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.ProspeccionBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.ProspeccionDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.CaptacionDAOImpl;
import com.controllocal.dao.impl.ProspeccionDAOImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class ProspeccionBusinessLogicImpl implements ProspeccionBusinessLogic {

    private final ProspeccionDAO prospeccionDAO;
    private final CaptacionDAO captacionDAO;
    private final AgenteInmobiliarioDAO agenteDAO;

    public ProspeccionBusinessLogicImpl() {
        this(new ProspeccionDAOImpl(), new CaptacionDAOImpl(), new AgenteInmobiliarioDAOImpl());
    }

    public ProspeccionBusinessLogicImpl(
            ProspeccionDAO prospeccionDAO,
            CaptacionDAO captacionDAO,
            AgenteInmobiliarioDAO agenteDAO
    ) {
        this.prospeccionDAO = prospeccionDAO;
        this.captacionDAO = captacionDAO;
        this.agenteDAO = agenteDAO;
    }

    public Long registrar(Prospeccion prospeccion) {
        return TransactionRunner.write(conn -> {
            prospeccion.registrar();
            BusinessValidations.prospeccion(prospeccion);
            validarAgenteDisponible(BusinessValidations.idAgente(prospeccion.getAgenteResponsable()));
            if (prospeccion.getCodigoProspeccion() == null || prospeccion.getCodigoProspeccion().isBlank()) {
                prospeccion.setCodigoProspeccion(generarCodigoProspeccion());
            }
            return prospeccionDAO.crear(prospeccion);
        });
    }

    public Optional<Prospeccion> buscarPorId(Long idProspeccion) {
        BusinessValidations.id(idProspeccion, "El id de prospeccion");
        return prospeccionDAO.buscarPorId(idProspeccion);
    }

    public List<Prospeccion> listarTodos() {
        return prospeccionDAO.listarTodos();
    }

    public List<Prospeccion> listarPorRecontactar(int diasAviso) {
        return prospeccionDAO.listarPorRecontactar(LocalDate.now().plusDays(Math.max(0, diasAviso)));
    }

    public boolean actualizar(Prospeccion prospeccion) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(prospeccion != null ? prospeccion.getIdProspeccion() : null, "El id de prospeccion");
            BusinessValidations.prospeccion(prospeccion);
            return prospeccionDAO.actualizar(prospeccion);
        });
    }

    public boolean eliminar(Long idProspeccion) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idProspeccion, "El id de prospeccion");
            return prospeccionDAO.eliminar(idProspeccion);
        });
    }

    public boolean contactar(Long idProspeccion) {
        return TransactionRunner.write(conn -> {
            Prospeccion p = prospeccionEnProceso(idProspeccion, "contactar");
            p.contactar();
            return prospeccionDAO.actualizar(p);
        });
    }

    public boolean registrarReunion(Long idProspeccion) {
        return TransactionRunner.write(conn -> {
            Prospeccion p = prospeccionEnProceso(idProspeccion, "registrar la reunion de");
            p.registrarReunion();
            return prospeccionDAO.actualizar(p);
        });
    }

    public boolean entregarPropuesta(Long idProspeccion) {
        return TransactionRunner.write(conn -> {
            Prospeccion p = prospeccionEnProceso(idProspeccion, "entregar la propuesta de");
            p.entregarPropuesta();
            return prospeccionDAO.actualizar(p);
        });
    }

    public boolean posponer(Long idProspeccion, LocalDate fechaRecontacto) {
        return TransactionRunner.write(conn -> {
            if (fechaRecontacto == null) {
                throw new BusinessException("La fecha de recontacto es obligatoria.");
            }
            LocalDate hoy = LocalDate.now();
            if (fechaRecontacto.isBefore(hoy)) {
                throw new BusinessException("La fecha de recontacto no puede estar en el pasado.");
            }
            if (fechaRecontacto.isAfter(hoy.plusDays(Prospeccion.DIAS_MAX_RECONTACTO))) {
                throw new BusinessException("El recontacto no puede superar los "
                        + Prospeccion.DIAS_MAX_RECONTACTO + " dias.");
            }
            Prospeccion p = prospeccionEnProceso(idProspeccion, "posponer");
            p.posponer(fechaRecontacto);
            return prospeccionDAO.actualizar(p);
        });
    }

    public boolean rechazar(Long idProspeccion, String motivo) {
        return TransactionRunner.write(conn -> {
            Prospeccion p = prospeccionEnProceso(idProspeccion, "rechazar");
            p.rechazarPropuesta(motivo);
            return prospeccionDAO.actualizar(p);
        });
    }

    public boolean descartar(Long idProspeccion, String motivo) {
        return TransactionRunner.write(conn -> {
            Prospeccion p = prospeccionEnProceso(idProspeccion, "descartar");
            p.descartar(motivo);
            return prospeccionDAO.actualizar(p);
        });
    }

    public Long captar(Long idProspeccion, BigDecimal comisionPactada) {
        return TransactionRunner.write(conn -> {
            if (comisionPactada == null || comisionPactada.signum() < 0) {
                throw new BusinessException("La comision pactada es obligatoria y no puede ser negativa.");
            }
            Prospeccion p = prospeccionEnProceso(idProspeccion, "captar");

            Captacion captacion = new Captacion();
            captacion.setCodigoCaptacion(generarCodigoCaptacion());
            captacion.setFechaCaptacion(LocalDate.now());
            captacion.setComisionPactada(comisionPactada);
            captacion.setLocalComercial(p.getLocalComercial());
            captacion.setAgenteResponsable(p.getAgenteResponsable());
            captacion.registrar(); // PENDIENTE_REVISION
            Long idCaptacion = captacionDAO.crear(captacion);

            p.aceptarPropuesta();
            p.setCaptacion(captacion);
            prospeccionDAO.actualizar(p);
            return idCaptacion;
        });
    }

    // Carga la prospeccion y valida que siga viva (ni CAPTADA ni DESCARTADA).
    private Prospeccion prospeccionEnProceso(Long idProspeccion, String accion) {
        BusinessValidations.id(idProspeccion, "El id de prospeccion");
        Prospeccion p = prospeccionDAO.buscarPorId(idProspeccion)
                .orElseThrow(() -> new BusinessException("Prospeccion no encontrada."));
        if (!p.getEstado().enProceso()) {
            throw new BusinessException("No se puede " + accion + " una prospeccion "
                    + p.getEstado().getDescripcion().toLowerCase() + ".");
        }
        return p;
    }

    private void validarAgenteDisponible(Long idAgente) {
        AgenteInmobiliario agente = agenteDAO.buscarPorId(idAgente)
                .orElseThrow(() -> new BusinessException("Agente no encontrado para prospeccion."));
        BusinessValidations.agenteDisponible(agente);
    }

    private String generarCodigoProspeccion() {
        return String.format("PRO-%04d", prospeccionDAO.listarTodos().size() + 1);
    }

    private String generarCodigoCaptacion() {
        return String.format("CAP-%04d", captacionDAO.listarTodos().size() + 1);
    }
}
