package com.controllocal.web.dto;

import com.controllocal.service.ContratoService;

/** Preparación privada; fecha de firma y estado contractual provienen del hito confirmado. */
public record ContratoRequest(Long idSolicitud,String incidencias) {
    public ContratoService.DatosContrato aDatos(){return new ContratoService.DatosContrato(idSolicitud,incidencias);}
}
