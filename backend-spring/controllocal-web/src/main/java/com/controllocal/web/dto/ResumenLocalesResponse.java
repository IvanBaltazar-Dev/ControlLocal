package com.controllocal.web.dto;

import com.controllocal.service.LocalComercialService;

/**
 * Contadores del listado de locales ({@code GET /locales/resumen}).
 *
 * <p>DTO <b>nuevo</b>, no congelado: la v1 no tiene este endpoint. Se agrega
 * para que los KPI de la pantalla salgan de la base de datos y no de contar
 * las filas que el cliente descargo — que solo puede contar la pagina visible.
 *
 * <p>{@code total} es la suma de los tres estados, calculada junto a ellos:
 * asi el encabezado no puede discrepar de su propio desglose.
 */
public record ResumenLocalesResponse(long total, long disponibles, long noDisponibles, long inactivos) {

    public static ResumenLocalesResponse desde(LocalComercialService.ResumenLocales r) {
        return new ResumenLocalesResponse(r.total(), r.disponibles(), r.noDisponibles(), r.inactivos());
    }
}
