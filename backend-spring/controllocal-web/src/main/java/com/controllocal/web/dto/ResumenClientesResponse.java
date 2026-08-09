package com.controllocal.web.dto;

import com.controllocal.service.ClienteService;

import java.util.List;

/**
 * Extension ADITIVA de F3: KPI de la bandeja de clientes. No existe en la v1,
 * asi que no hay forma congelada que respetar; sigue la del resto de resumenes
 * del v2 (contratos, propiedades de equipo): contadores calculados en la base
 * sobre el mismo conjunto que la lista, mas los valores disponibles para que el
 * selector no obligue a descargar la cartera.
 */
public record ResumenClientesResponse(long total, long activos, long inactivos,
                                      long contactoAutorizado, long usoDatoAutorizado,
                                      List<String> rubros) {

    public static ResumenClientesResponse desde(ClienteService.ResumenClientes r) {
        return new ResumenClientesResponse(r.total(), r.activos(), r.inactivos(),
                r.contactoAutorizado(), r.usoDatoAutorizado(), r.rubros());
    }
}
