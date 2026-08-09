package com.controllocal.web.dto;

import com.controllocal.service.FichaComercialService;

import java.util.LinkedHashMap;
import java.util.Map;

public record FichaPropietarioResponse(
        PropietarioResponse propietario,
        Map<String, FichaSectionResponse> sections) {

    public static FichaPropietarioResponse desde(FichaComercialService.FichaPropietario ficha) {
        Map<String, FichaSectionResponse> secciones = new LinkedHashMap<>();
        ficha.sections().forEach((clave, valor) ->
                secciones.put(clave, FichaSectionResponse.desde(valor)));
        return new FichaPropietarioResponse(
                PropietarioResponse.desde(ficha.propietario()), secciones);
    }
}
