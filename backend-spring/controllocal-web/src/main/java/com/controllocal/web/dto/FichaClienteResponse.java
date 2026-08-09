package com.controllocal.web.dto;

import com.controllocal.service.FichaComercialService;

import java.util.LinkedHashMap;
import java.util.Map;

public record FichaClienteResponse(
        ClienteResponse cliente,
        boolean requerimientoActivo,
        String ctaRuta,
        Map<String, FichaSectionResponse> sections) {

    public static FichaClienteResponse desde(FichaComercialService.FichaCliente ficha) {
        Map<String, FichaSectionResponse> secciones = new LinkedHashMap<>();
        ficha.sections().forEach((clave, valor) ->
                secciones.put(clave, FichaSectionResponse.desde(valor)));
        return new FichaClienteResponse(
                ClienteResponse.desde(ficha.cliente()), ficha.requerimientoActivo(),
                ficha.ctaRuta(), secciones);
    }
}
