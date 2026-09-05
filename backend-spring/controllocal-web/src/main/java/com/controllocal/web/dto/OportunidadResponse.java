package com.controllocal.web.dto;

import com.controllocal.service.OportunidadService;
import com.controllocal.web.dto.OfertaDtos.CapacidadesMesaResponse;
import com.controllocal.web.dto.OfertaDtos.OfertaResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Espejo de la ficha de una oportunidad.
 *
 * <p>{@code ofertas} y {@code capacidades} son <b>extension aditiva</b> de WP-A
 * y solo viajan en {@code GET /oportunidades/{id}}. En los listados llegan nulos
 * y con Jackson {@code NON_NULL} no viajan: su ausencia significa «no calculado
 * aqui», no «no hay ofertas» ni «no puedes nada».
 */
public record OportunidadResponse(Long id, String codigoOportunidad, Long idCliente, String clienteNombre,
                                  Long idCaptacion, String codigoCaptacion, String direccionLocal,
                                  String distritoLocal, Long idAgente, String agenteNombre, String estado,
                                  LocalDateTime fechaRegistro, String motivoCierre, String observaciones,
                                  LocalDateTime fechaCierre, LocalDateTime fechaActualizacion,
                                  Long idPublicacionOrigen,
                                  List<OfertaResponse> ofertas,
                                  CapacidadesMesaResponse capacidades) {

    public static OportunidadResponse desde(OportunidadService.FichaOportunidad f) {
        return new OportunidadResponse(f.id(), f.codigoOportunidad(), f.idCliente(), f.clienteNombre(),
                f.idCaptacion(), f.codigoCaptacion(), f.direccionLocal(), f.distritoLocal(), f.idAgente(),
                f.agenteNombre(), f.estado(), f.fechaRegistro(), f.motivoCierre(), f.observaciones(),
                f.fechaCierre(), f.fechaActualizacion(), f.idPublicacionOrigen(),
                OfertaResponse.desde(f.ofertas()),
                CapacidadesMesaResponse.desde(f.capacidades()));
    }
}
