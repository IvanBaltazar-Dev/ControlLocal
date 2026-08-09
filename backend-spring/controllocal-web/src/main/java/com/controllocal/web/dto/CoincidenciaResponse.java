package com.controllocal.web.dto;

import com.controllocal.service.CoincidenciaService;

import java.util.List;

/**
 * Contrato CONGELADO: espejo de CoincidenciaCarteraSupport.CoincidenciaResponse
 * de la v1. {@code cumple}/{@code noCumple} son las frases que el frontend
 * pinta literalmente, y {@code proponerRuta} la ruta del boton "Proponer"
 * (vacia cuando todavia no hay captacion sobre la que proponer).
 */
public record CoincidenciaResponse(String tipo, Long id, String codigo, String titulo, String subtitulo,
                                   String distrito, String renta, String area, String frente, int puntaje,
                                   List<String> cumple, List<String> noCumple, Long clienteId,
                                   Long captacionId, String proponerRuta) {

    public static CoincidenciaResponse desde(CoincidenciaService.Coincidencia c) {
        return new CoincidenciaResponse(c.tipo(), c.id(), c.codigo(), c.titulo(), c.subtitulo(),
                c.distrito(), c.renta(), c.area(), c.frente(), c.puntaje(), c.cumple(), c.noCumple(),
                c.clienteId(), c.captacionId(), c.proponerRuta());
    }
}
