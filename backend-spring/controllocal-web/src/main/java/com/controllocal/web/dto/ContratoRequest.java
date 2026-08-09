package com.controllocal.web.dto;

import com.controllocal.service.ContratoService;

import java.time.LocalDate;

/**
 * Contrato CONGELADO: espejo de Dtos.ContratoRequest de la v1.
 *
 * <p>Las condiciones del trato (renta, plazo, forma de pago, garantia,
 * adelanto) NO viajan aqui: se leen de la solicitud, y la comision la deriva
 * el backend. El agente solo captura la formalizacion del cierre, y los tres
 * campos opcionales tienen default en el service: cierre = hoy, estado =
 * VIGENTE, sin incidencias.
 */
public record ContratoRequest(Long idSolicitud, LocalDate fechaCierre, String estadoContrato,
                              String incidencias) {

    public ContratoService.DatosContrato aDatos() {
        return new ContratoService.DatosContrato(idSolicitud, fechaCierre, estadoContrato, incidencias);
    }
}
