package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.bl.VisitaBusinessLogic;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.OportunidadComercialDAOImpl;
import com.controllocal.dao.impl.VisitaDAOImpl;
import com.controllocal.dao.OportunidadComercialDAO;
import com.controllocal.dao.VisitaDAO;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class VisitaBusinessLogicImpl implements VisitaBusinessLogic {

    private final VisitaDAO visitaDAO;
    private final OportunidadComercialDAO oportunidadDAO;
    private final AgenteInmobiliarioDAO agenteDAO;

    public VisitaBusinessLogicImpl() {
        this(new VisitaDAOImpl(), new OportunidadComercialDAOImpl(), new AgenteInmobiliarioDAOImpl());
    }

    public VisitaBusinessLogicImpl(
            VisitaDAO visitaDAO,
            OportunidadComercialDAO oportunidadDAO,
            AgenteInmobiliarioDAO agenteDAO
    ) {
        this.visitaDAO = visitaDAO;
        this.oportunidadDAO = oportunidadDAO;
        this.agenteDAO = agenteDAO;
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

    private void validarAgenteDisponible(Long idAgente) {
        AgenteInmobiliario agente = agenteDAO.buscarPorId(idAgente)
                .orElseThrow(() -> new BusinessException("Agente no encontrado para visita."));
        BusinessValidations.agenteDisponible(agente);
    }
}
