package com.controllocal.bl.impl;

import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.config.DatabaseConfig;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.ReasignacionCaptacionDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.dao.impl.CaptacionDAOImpl;
import com.controllocal.dao.impl.ReasignacionCaptacionDAOImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.EstadoCaptacion;
import com.controllocal.model.comercial.ReasignacionCaptacion;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.EstadoOperativoAgente;

import java.time.LocalDateTime;
import java.util.List;

public class CaptacionBusinessLogicImpl implements CaptacionBusinessLogic {

    // Inyectamos las implementaciones que ya tienes listas
    private final CaptacionDAO captacionDAO = new CaptacionDAOImpl();
    private final AgenteInmobiliarioDAO agenteDAO = new AgenteInmobiliarioDAOImpl();
    private final ReasignacionCaptacionDAO reasignacionDAO = new ReasignacionCaptacionDAOImpl();
    private final BrokerDAO brokerDAO = new BrokerDAOImpl();

    @Override
    public Long registerAcquisition(Captacion acquisition) { //create
        // Regla 1: Validar disponibilidad del agente
        try {
            AgenteInmobiliario agente = agenteDAO.buscarPorId(acquisition.getAgenteResponsable().getIdAgente())
                    .orElseThrow(() -> new RuntimeException("Agente no encontrado para registro."));

            if (agente.getEstadoOperativo() != EstadoOperativoAgente.DISPONIBLE) {
                throw new RuntimeException("No se puede registrar: El agente seleccionado no está disponible.");
            }

            // Regla 2: Forzar estado inicial
            acquisition.setEstado(EstadoCaptacion.PENDIENTE_REVISION);
            Long id = captacionDAO.crear(acquisition);

            DatabaseConfig.commit();

            return id;

        }catch (Exception ex) {
            DatabaseConfig.rollback();
            throw new RuntimeException("Error al registrar: " + ex.getMessage());
        } finally {
            DatabaseConfig.close();
        }
    }

    @Override
    public void reassignAgent(Long acquisitionId, Long newAgentId, Long brokerId, String reason) {
        // Buscamos la captación actual
        try {
            Captacion captacion = captacionDAO.buscarPorId(acquisitionId)
                    .orElseThrow(() -> new RuntimeException("Captación no encontrada."));

            AgenteInmobiliario agenteAnterior = captacion.getAgenteResponsable();
            AgenteInmobiliario nuevoAgente = agenteDAO.buscarPorId(newAgentId)
                    .orElseThrow(() -> new RuntimeException("Nuevo agente no encontrado."));

            // Creamos el objeto de auditoría para la tabla reasignacion_captacion
            ReasignacionCaptacion log = new ReasignacionCaptacion();
            log.setCaptacion(captacion);
            log.setAgenteAnterior(agenteAnterior);
            log.setAgenteNuevo(nuevoAgente);
            log.setBrokerResponsable(new Broker(brokerId));
            log.setFechaCambio(LocalDateTime.now());
            log.setMotivo(reason);

            // Actualizamos la captación y guardamos el log
            captacion.setAgenteResponsable(nuevoAgente);
            captacionDAO.actualizar(captacion);
            reasignacionDAO.crear(log);

            DatabaseConfig.commit();
        }catch (Exception ex) {
            // Si algo falló (ej. se cayó el internet entre el update y el insert), deshacemos todo
            DatabaseConfig.rollback();
            throw new RuntimeException("Error en reasignación: " + ex.getMessage());
        } finally {
            DatabaseConfig.close();
        }
    }

    @Override
    public void reviewAcquisition(Long acquisitionId, Long brokerId, boolean approved, String remarks) {
        Captacion captacion = captacionDAO.buscarPorId(acquisitionId)
                .orElseThrow(() -> new RuntimeException("Captación no encontrada para revisión."));

        Broker broker = brokerDAO.buscarPorId(brokerId)
                .orElseThrow(() -> new RuntimeException("Broker no autorizado."));

        // Lógica de aprobación
        captacion.setEstado(approved ? EstadoCaptacion.ACTIVA : EstadoCaptacion.RECHAZADA);
        captacion.setBrokerRevisor(broker);
        captacion.setFechaRevision(LocalDateTime.now());
        captacion.setObservacionRevision(remarks);

        captacionDAO.actualizar(captacion);
    }

    @Override
    public void closeAcquisition(Long acquisitionId) {
        // El DAO ya tiene un método eliminar que hace el soft-delete
        captacionDAO.eliminar(acquisitionId);
    }

    @Override
    public List<Captacion> listPendingReviews() {
        return captacionDAO.listarTodos().stream()
                .filter(c -> c.getEstado() == EstadoCaptacion.PENDIENTE_REVISION)
                .toList();
    }
}