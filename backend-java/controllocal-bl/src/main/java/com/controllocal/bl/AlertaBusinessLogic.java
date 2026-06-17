package com.controllocal.bl;

import java.util.List;

import com.controllocal.model.comercial.Alerta;

public interface AlertaBusinessLogic {

    Long crear(Alerta alerta);
    List<Alerta> listarActivasPorAgente(Long idAgente);
    List<Alerta> listarActivasPorBroker(Long idBroker);
    List<Alerta> listarActivas();
    boolean marcarAtendida(Long idAlerta);
}
