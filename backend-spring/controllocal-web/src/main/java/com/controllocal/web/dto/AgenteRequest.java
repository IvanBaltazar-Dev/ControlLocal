package com.controllocal.web.dto;
import com.controllocal.service.AgenteService;

/** Edición profesional dentro de la organización; nunca credenciales de la cuenta personal. */
public record AgenteRequest(String nombre,String telefono,String correoLaboral,String zona,
                            String estadoOperativo) {
    public AgenteService.DatosAgente aDatos() {return new AgenteService.DatosAgente(nombre,telefono,correoLaboral,zona,estadoOperativo);}
}
