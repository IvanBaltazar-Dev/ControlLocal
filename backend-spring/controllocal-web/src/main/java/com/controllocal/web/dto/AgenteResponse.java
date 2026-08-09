package com.controllocal.web.dto;

import com.controllocal.service.AgenteService;

import java.time.LocalDate;

public record AgenteResponse(Long id, String codigoAgente, String nombre,
                             String tipoPersona, String tipoDocumento,
                             String numeroDocumento, String telefono, String correo,
                             String usuario, String zona, LocalDate fechaIngreso,
                             String estadoAdministrativo, String estadoOperativo,
                             int captacionesActivas, int operacionesActivas) {

    public static AgenteResponse desde(AgenteService.FichaAgente ficha) {
        return new AgenteResponse(ficha.id(), ficha.codigoAgente(), ficha.nombre(),
                ficha.tipoPersona(), ficha.tipoDocumento(), ficha.numeroDocumento(),
                ficha.telefono(), ficha.correo(), ficha.usuario(), ficha.zona(),
                ficha.fechaIngreso(), ficha.estadoAdministrativo(),
                ficha.estadoOperativo(), ficha.captacionesActivas(),
                ficha.operacionesActivas());
    }
}
