package com.controllocal.web.dto;
import com.controllocal.service.BrokerService;

/** Edición local; la atribución administrativa se gobierna por pertenencia. */
public record BrokerRequest(String nombre,String telefono,String correoLaboral,String zona) {
    public BrokerService.DatosBroker aDatos() {return new BrokerService.DatosBroker(nombre,telefono,correoLaboral,zona);}
}
