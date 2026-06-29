package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.InteraccionComercialBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.ClienteInteresadoDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.CaptacionDAOImpl;
import com.controllocal.dao.impl.ClienteInteresadoDAOImpl;
import com.controllocal.dao.impl.InteraccionComercialDAOImpl;
import com.controllocal.dao.impl.OportunidadComercialDAOImpl;
import com.controllocal.dao.impl.ProspeccionDAOImpl;
import com.controllocal.dao.InteraccionComercialDAO;
import com.controllocal.dao.OportunidadComercialDAO;
import com.controllocal.dao.ProspeccionDAO;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.enums.EstadoProspeccion;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class InteraccionComercialBusinessLogicImpl implements InteraccionComercialBusinessLogic {

    private final InteraccionComercialDAO interaccionDAO;
    private final OportunidadComercialDAO oportunidadDAO;
    private final ProspeccionDAO prospeccionDAO;
    private final CaptacionDAO captacionDAO;
    private final ClienteInteresadoDAO clienteDAO;
    private final AgenteInmobiliarioDAO agenteDAO;

    public InteraccionComercialBusinessLogicImpl() {
        this(
                new InteraccionComercialDAOImpl(),
                new OportunidadComercialDAOImpl(),
                new ProspeccionDAOImpl(),
                new CaptacionDAOImpl(),
                new ClienteInteresadoDAOImpl(),
                new AgenteInmobiliarioDAOImpl());
    }

    public InteraccionComercialBusinessLogicImpl(
            InteraccionComercialDAO interaccionDAO,
            OportunidadComercialDAO oportunidadDAO,
            ProspeccionDAO prospeccionDAO,
            CaptacionDAO captacionDAO,
            ClienteInteresadoDAO clienteDAO,
            AgenteInmobiliarioDAO agenteDAO
    ) {
        this.interaccionDAO = interaccionDAO;
        this.oportunidadDAO = oportunidadDAO;
        this.prospeccionDAO = prospeccionDAO;
        this.captacionDAO = captacionDAO;
        this.clienteDAO = clienteDAO;
        this.agenteDAO = agenteDAO;
    }

    public Long registrar(InteraccionComercial interaccion) {
        return TransactionRunner.write(conn -> {
            interaccion.registrar();
            BusinessValidations.interaccion(interaccion);
            validarAgenteDisponible(BusinessValidations.idAgente(interaccion.getAgenteResponsable()));
            return switch (contexto(interaccion)) {
                case "PROSPECCION" -> registrarProspeccion(interaccion);
                case "CAPTACION" -> registrarCaptacion(interaccion);
                case "CLIENTE" -> registrarCliente(interaccion);
                case "OPORTUNIDAD" -> registrarOportunidad(interaccion);
                default -> throw new BusinessException("Contexto de interaccion invalido.");
            };
        });
    }

    public Optional<InteraccionComercial> buscarPorId(Long idInteraccion) {
        BusinessValidations.id(idInteraccion, "El id de interaccion");
        return interaccionDAO.buscarPorId(idInteraccion);
    }

    public List<InteraccionComercial> listarTodos() {
        return interaccionDAO.listarTodos();
    }

    public List<InteraccionComercial> listarPorOportunidad(Long idOportunidad) {
        BusinessValidations.id(idOportunidad, "La oportunidad comercial");
        return interaccionDAO.listarPorOportunidad(idOportunidad);
    }

    public List<InteraccionComercial> listarPorProspeccion(Long idProspeccion) {
        BusinessValidations.id(idProspeccion, "La prospeccion");
        return interaccionDAO.listarPorProspeccion(idProspeccion);
    }

    public List<InteraccionComercial> listarPorCaptacion(Long idCaptacion) {
        BusinessValidations.id(idCaptacion, "La captacion");
        return interaccionDAO.listarPorCaptacion(idCaptacion);
    }

    public List<InteraccionComercial> listarPorCliente(Long idCliente) {
        BusinessValidations.id(idCliente, "El cliente interesado");
        return interaccionDAO.listarPorCliente(idCliente);
    }

    public List<InteraccionComercial> listarPorAgentes(java.util.Collection<Long> idsAgente) {
        return interaccionDAO.listarPorAgentes(idsAgente);
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

    private Long registrarProspeccion(InteraccionComercial interaccion) {
        Prospeccion prospeccion = prospeccionDAO
                .buscarPorId(BusinessValidations.idProspeccion(interaccion.getProspeccion()))
                .orElseThrow(() -> new BusinessException("Prospeccion no encontrada para interaccion."));
        if (prospeccion.getEstado() == null || prospeccion.getEstado() == EstadoProspeccion.DESCARTADO
                || prospeccion.getEstado() == EstadoProspeccion.CAPTADO) {
            throw new BusinessException("La prospeccion debe estar activa y sin captar para registrar interacciones.");
        }
        interaccion.setProspeccion(prospeccion);

        Long id = interaccionDAO.crear(interaccion);
        if (interaccion.getResultado() != null) {
            switch (interaccion.getResultado()) {
                case CONTACTADO -> prospeccion.contactar();
                case REUNION_AGENDADA -> prospeccion.registrarReunion();
                case PROPUESTA_ENVIADA -> prospeccion.entregarPropuesta();
                case RECONTACTAR -> prospeccion.registrarSeguimiento();
                default -> {
                    if (prospeccion.getEstado() == EstadoProspeccion.PROSPECTO) {
                        prospeccion.contactar();
                    }
                }
            }
        }
        if (prospeccion.getEstado().enProceso()) {
            prospeccionDAO.actualizar(prospeccion);
        }
        return id;
    }

    private Long registrarOportunidad(InteraccionComercial interaccion) {
        OportunidadComercial oportunidad = oportunidadDAO
                .buscarPorId(BusinessValidations.idOportunidad(interaccion.getOportunidadComercial()))
                .orElseThrow(() -> new BusinessException("Oportunidad comercial no encontrada para interaccion."));
        BusinessValidations.oportunidadAbierta(oportunidad);
        interaccion.setOportunidadComercial(oportunidad);
        return interaccionDAO.crear(interaccion);
    }

    private Long registrarCaptacion(InteraccionComercial interaccion) {
        Captacion captacion = captacionDAO
                .buscarPorId(BusinessValidations.idCaptacion(interaccion.getCaptacion()))
                .orElseThrow(() -> new BusinessException("Captacion no encontrada para interaccion."));
        interaccion.setCaptacion(captacion);
        return interaccionDAO.crear(interaccion);
    }

    private Long registrarCliente(InteraccionComercial interaccion) {
        ClienteInteresado cliente = clienteDAO
                .buscarPorId(BusinessValidations.idCliente(interaccion.getClienteInteresado()))
                .orElseThrow(() -> new BusinessException("Cliente interesado no encontrado para interaccion."));
        interaccion.setClienteInteresado(cliente);
        return interaccionDAO.crear(interaccion);
    }

    private static String contexto(InteraccionComercial interaccion) {
        String contexto = interaccion.getContexto();
        return contexto == null || contexto.isBlank() ? "OPORTUNIDAD" : contexto.trim().toUpperCase();
    }
}
