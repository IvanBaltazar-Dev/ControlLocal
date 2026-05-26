package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.InteraccionComercialBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.InteraccionComercialDAOImpl;
import com.controllocal.dao.impl.OportunidadComercialDAOImpl;
import com.controllocal.dao.InteraccionComercialDAO;
import com.controllocal.dao.OportunidadComercialDAO;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class InteraccionComercialBusinessLogicImpl implements InteraccionComercialBusinessLogic {

    private final InteraccionComercialDAO interaccionDAO;
    private final OportunidadComercialDAO oportunidadDAO;
    private final AgenteInmobiliarioDAO agenteDAO;

    public InteraccionComercialBusinessLogicImpl() {
        this(new InteraccionComercialDAOImpl(), new OportunidadComercialDAOImpl(), new AgenteInmobiliarioDAOImpl());
    }

    public InteraccionComercialBusinessLogicImpl(
            InteraccionComercialDAO interaccionDAO,
            OportunidadComercialDAO oportunidadDAO,
            AgenteInmobiliarioDAO agenteDAO
    ) {
        this.interaccionDAO = interaccionDAO;
        this.oportunidadDAO = oportunidadDAO;
        this.agenteDAO = agenteDAO;
    }

    public Long registrar(InteraccionComercial interaccion) {
        return TransactionRunner.write(conn -> {
            interaccion.registrar();
            BusinessValidations.interaccion(interaccion);
            OportunidadComercial oportunidad = oportunidadDAO
                    .buscarPorId(BusinessValidations.idOportunidad(interaccion.getOportunidadComercial()))
                    .orElseThrow(() -> new BusinessException("Oportunidad comercial no encontrada para interaccion."));
            BusinessValidations.oportunidadAbierta(oportunidad);
            validarAgenteDisponible(BusinessValidations.idAgente(interaccion.getAgenteResponsable()));
            interaccion.setClienteInteresado(oportunidad.getClienteInteresado());
            interaccion.setCaptacion(oportunidad.getCaptacion());
            return interaccionDAO.crear(interaccion);
        });
    }

    public Optional<InteraccionComercial> buscarPorId(Long idInteraccion) {
        BusinessValidations.id(idInteraccion, "El id de interaccion");
        return interaccionDAO.buscarPorId(idInteraccion);
    }

    public List<InteraccionComercial> listarTodos() {
        return interaccionDAO.listarTodos();
    }

    public boolean actualizar(InteraccionComercial interaccion) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(interaccion != null ? interaccion.getIdInteraccion() : null, "El id de interaccion");
            BusinessValidations.interaccion(interaccion);
            return interaccionDAO.actualizar(interaccion);
        });
    }

    public boolean eliminar(Long idInteraccion) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idInteraccion, "El id de interaccion");
            return interaccionDAO.eliminar(idInteraccion);
        });
    }

    private void validarAgenteDisponible(Long idAgente) {
        AgenteInmobiliario agente = agenteDAO.buscarPorId(idAgente)
                .orElseThrow(() -> new BusinessException("Agente no encontrado para interaccion."));
        BusinessValidations.agenteDisponible(agente);
    }
}
