package com.controllocal.web.dto;

import com.controllocal.service.VisitaService;

import java.time.LocalDate;
import java.time.LocalTime;

/** Contrato CONGELADO: espejo de Dtos.VisitaResponse de la v1. */
public record VisitaResponse(Long id, Long idOportunidad, String codigoOportunidad, LocalDate fechaVisita,
                             LocalTime horaVisita, String observaciones, String estado, String resultado,
                             Long idCliente, String clienteNombre, Long idCaptacion, String codigoCaptacion,
                             String direccionLocal, String distritoLocal, Long idAgente, String agenteNombre,
                             Integer nivelInteres, String objecionPrincipal, String opinionPrecio,
                             String proximaAccion) {

    public static VisitaResponse desde(VisitaService.FichaVisita f) {
        return new VisitaResponse(f.id(), f.idOportunidad(), f.codigoOportunidad(), f.fechaVisita(),
                f.horaVisita(), f.observaciones(), f.estado(), f.resultado(), f.idCliente(),
                f.clienteNombre(), f.idCaptacion(), f.codigoCaptacion(), f.direccionLocal(),
                f.distritoLocal(), f.idAgente(), f.agenteNombre(), f.nivelInteres(),
                f.objecionPrincipal(), f.opinionPrecio(), f.proximaAccion());
    }
}
