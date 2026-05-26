package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.usuario.AgenteInmobiliario;

public interface AgenteBusinessLogic {

    public Long registrar(AgenteInmobiliario agente);
    public Long registrar(Long idBrokerSupervisor, AgenteInmobiliario agente);
    public Optional<AgenteInmobiliario> buscarPorId(Long idAgente);
    public List<AgenteInmobiliario> listarTodos();
    public List<AgenteInmobiliario> listarPorBroker(Long idBroker);
    public boolean actualizar(AgenteInmobiliario agente);
    public boolean actualizar(Long idBrokerSupervisor, AgenteInmobiliario agente);
    public boolean desactivar(Long idAgente);
    public boolean desactivar(Long idBrokerSupervisor, Long idAgente);
}

