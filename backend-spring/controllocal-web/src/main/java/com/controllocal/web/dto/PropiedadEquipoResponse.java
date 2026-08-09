package com.controllocal.web.dto;

import com.controllocal.service.CaptacionService;

import java.math.BigDecimal;

/**
 * Extension ADITIVA (no es contrato congelado de la v1): una fila de la
 * cartera del equipo vista POR INMUEBLE, con los datos de su captacion mas
 * reciente. La v1 no tenia este recurso — su pantalla se armaba descargando
 * todas las captaciones del equipo y deduplicando en el navegador.
 */
public record PropiedadEquipoResponse(Long idPropiedad, Long idCaptacion, String codigoCaptacion,
                                      String estado, String codigoLocal, String direccion,
                                      String distrito, String rubro, BigDecimal areaM2,
                                      Long idAgente, String agenteNombre) {

    public static PropiedadEquipoResponse desde(CaptacionService.PropiedadEquipo p) {
        return new PropiedadEquipoResponse(p.idPropiedad(), p.idCaptacion(), p.codigoCaptacion(),
                p.estado(), p.codigoLocal(), p.direccion(), p.distrito(), p.rubro(), p.areaM2(),
                p.idAgente(), p.agenteNombre());
    }
}
