package com.controllocal.bl.impl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.bl.VisitaBusinessLogic;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.MotivoNoContinuidadDAOImpl;
import com.controllocal.dao.impl.OportunidadComercialDAOImpl;
import com.controllocal.dao.impl.VisitaDAOImpl;
import com.controllocal.dao.MotivoNoContinuidadDAO;
import com.controllocal.dao.OportunidadComercialDAO;
import com.controllocal.dao.VisitaDAO;
import com.controllocal.model.comercial.MotivoNoContinuidad;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.comercial.enums.MotivoNoContinuidadTipo;
import com.controllocal.model.comercial.enums.ResultadoInteraccion;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class VisitaBusinessLogicImpl implements VisitaBusinessLogic {

    private final VisitaDAO visitaDAO;
    private final OportunidadComercialDAO oportunidadDAO;
    private final AgenteInmobiliarioDAO agenteDAO;
    private final MotivoNoContinuidadDAO motivoDAO;

    public VisitaBusinessLogicImpl() {
        this(new VisitaDAOImpl(), new OportunidadComercialDAOImpl(),
                new AgenteInmobiliarioDAOImpl(), new MotivoNoContinuidadDAOImpl());
    }

    public VisitaBusinessLogicImpl(
            VisitaDAO visitaDAO,
            OportunidadComercialDAO oportunidadDAO,
            AgenteInmobiliarioDAO agenteDAO
    ) {
        this(visitaDAO, oportunidadDAO, agenteDAO, new MotivoNoContinuidadDAOImpl());
    }

    public VisitaBusinessLogicImpl(
            VisitaDAO visitaDAO,
            OportunidadComercialDAO oportunidadDAO,
            AgenteInmobiliarioDAO agenteDAO,
            MotivoNoContinuidadDAO motivoDAO
    ) {
        this.visitaDAO = visitaDAO;
        this.oportunidadDAO = oportunidadDAO;
        this.agenteDAO = agenteDAO;
        this.motivoDAO = motivoDAO;
    }

    public Long registrar(Visita visita) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.visita(visita);
            OportunidadComercial oportunidad = oportunidadDAO
                    .buscarPorId(BusinessValidations.idOportunidad(visita.getOportunidadComercial()))
                    .orElseThrow(() -> new BusinessException("Oportunidad comercial no encontrada para visita."));
            BusinessValidations.oportunidadAbierta(oportunidad);
            validarAgenteDisponible(BusinessValidations.idAgente(visita.getAgenteResponsable()));
            visita.setClienteInteresado(oportunidad.getClienteInteresado());
            visita.setCaptacion(oportunidad.getCaptacion());
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
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(visita != null ? visita.getIdVisita() : null, "El id de visita");
            BusinessValidations.visita(visita);
            return visitaDAO.actualizar(visita);
        });
    }

    public boolean eliminar(Long idVisita) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idVisita, "El id de visita");
            return visitaDAO.eliminar(idVisita);
        });
    }

    public boolean reprogramar(Long idVisita, LocalDate nuevaFecha, LocalTime nuevaHora) {
        return TransactionRunner.write(conn -> {
            if (nuevaFecha == null || nuevaHora == null) {
                throw new BusinessException("La nueva fecha y hora son obligatorias para reprogramar.");
            }
            Visita visita = visitaModificable(idVisita, "reprogramar");
            visita.reprogramar(nuevaFecha, nuevaHora);
            return visitaDAO.actualizar(visita);
        });
    }

    public boolean cancelar(Long idVisita, String motivo) {
        return TransactionRunner.write(conn -> {
            Visita visita = visitaModificable(idVisita, "cancelar");
            visita.cancelar(motivo);
            return visitaDAO.actualizar(visita);
        });
    }

    public boolean registrarResultado(Long idVisita, ResultadoInteraccion resultado,
            String observaciones, MotivoNoContinuidadTipo razonNoContinuidad) {
        return TransactionRunner.write(conn -> {
            if (resultado == null) {
                throw new BusinessException("El resultado de la visita es obligatorio.");
            }
            Visita visita = visitaModificable(idVisita, "registrar el resultado de");
            if (observaciones != null && !observaciones.isBlank()) {
                visita.setObservaciones(observaciones);
            }
            visita.registrarResultado(resultado);
            boolean actualizada = visitaDAO.actualizar(visita);

            // Si el desenlace es de no continuidad, en la misma transaccion se
            // registra el motivo (ligado a esta visita) y se cierra la oportunidad.
            if (resultado.implicaNoContinuidad()) {
                cerrarPorNoContinuidad(visita, razonNoContinuidad, observaciones);
            }
            return actualizada;
        });
    }

    private void cerrarPorNoContinuidad(Visita visita, MotivoNoContinuidadTipo razon, String observaciones) {
        if (razon == null) {
            throw new BusinessException("Debe indicar el motivo de no continuidad cuando el cliente no continua.");
        }
        OportunidadComercial oportunidad = oportunidadDAO
                .buscarPorId(BusinessValidations.idOportunidad(visita.getOportunidadComercial()))
                .orElseThrow(() -> new BusinessException("Oportunidad comercial no encontrada para no continuidad."));
        BusinessValidations.oportunidadAbierta(oportunidad);

        MotivoNoContinuidad motivo = new MotivoNoContinuidad();
        motivo.setRazonPrincipal(razon);
        motivo.setObservaciones(observaciones);
        motivo.setOportunidadComercial(oportunidad);
        motivo.setAgenteResponsable(visita.getAgenteResponsable());
        motivo.setVisita(visita);
        motivo.registrarMotivo();
        BusinessValidations.motivo(motivo);
        motivoDAO.crear(motivo);

        oportunidad.cerrarNoContinua(razon.getDescripcion());
        oportunidadDAO.actualizar(oportunidad);
    }

    // Carga la visita y valida que aun admita cambios (PROGRAMADA o REPROGRAMADA).
    private Visita visitaModificable(Long idVisita, String accion) {
        BusinessValidations.id(idVisita, "El id de visita");
        Visita visita = visitaDAO.buscarPorId(idVisita)
                .orElseThrow(() -> new BusinessException("Visita no encontrada."));
        if (!visita.esModificable()) {
            throw new BusinessException("No se puede " + accion + " una visita "
                    + visita.getEstado().getDescripcion().toLowerCase() + ".");
        }
        return visita;
    }

    private void validarAgenteDisponible(Long idAgente) {
        AgenteInmobiliario agente = agenteDAO.buscarPorId(idAgente)
                .orElseThrow(() -> new BusinessException("Agente no encontrado para visita."));
        BusinessValidations.agenteDisponible(agente);
    }
}
