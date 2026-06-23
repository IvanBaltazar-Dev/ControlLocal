package com.controllocal.bl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.enums.EstadoCaptacion;
import com.controllocal.model.comercial.ReasignacionCaptacion;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;

public interface CaptacionBusinessLogic {

    public Long registrar(Captacion captacion);
    public Long registerAcquisition(Captacion acquisition);
    public void aprobarCaptacion(Long idCaptacion, Long idBroker, String observacion);
    public void observarCaptacion(Long idCaptacion, Long idBroker, String observacion);
    public void rechazarCaptacion(Long idCaptacion, Long idBroker, String observacion);
    public void reviewAcquisition(Long acquisitionId, Long brokerId, boolean approved, String remarks);
    public void revisarCaptacion(Long idCaptacion, Long idBroker, EstadoCaptacion estadoRevision, String observacion);
    public void reassignAgent(Long acquisitionId, Long newAgentId, Long brokerId, String reason);
    public void reasignarCaptacion(Long idCaptacion, Long idAgenteNuevo, Long idBroker, String motivo);
    public void cerrarCaptacion(Long idCaptacion, Long idBroker, String motivo);
    public void closeAcquisition(Long acquisitionId, Long brokerId, String reason);
    public void closeAcquisition(Long acquisitionId);
    public Optional<Captacion> buscarPorId(Long idCaptacion);
    public List<Captacion> listarTodos();
    public List<Captacion> listarPagina(int limite, int desplazamiento);
    public long contar();
    public List<Captacion> listarPorBroker(Long idBroker);
    public List<Captacion> listarPorAgente(Long idAgente);
    public List<Captacion> listPendingReviews();
    public List<Captacion> listPendingReviews(Long idBroker);
    public boolean actualizar(Captacion captacion);
    public List<ReasignacionCaptacion> listarReasignaciones();
    public Broker validarBroker(Long brokerId);
    public Broker validarBrokerAdministrador(Long brokerId);
    public AgenteInmobiliario validarAgenteDisponible(Long agenteId);
    public void validarCaptacionPendienteRevision(Captacion captacion);
    public void validarCaptacionActiva(Captacion captacion);
    public void validarUnicaCaptacionActivaPorLocal(Long idLocal);
}

