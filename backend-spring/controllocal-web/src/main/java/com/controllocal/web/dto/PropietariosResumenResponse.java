package com.controllocal.web.dto;

import com.controllocal.service.PropietarioService;

/**
 * Cubos del catálogo de propietarios. <b>Extensión aditiva</b> (no existe en la
 * v1), contada en la base sobre el MISMO conjunto que pagina la lista.
 *
 * <p>Como el listado, lleva <b>alcance</b>: el BROKER cuenta solo los
 * propietarios de sus propiedades, así que dos actores ven totales distintos del
 * mismo catálogo y ninguno está mal.
 */
public record PropietariosResumenResponse(long total, long activos, long inactivos) {

    public static PropietariosResumenResponse desde(PropietarioService.ResumenPropietarios r) {
        return new PropietariosResumenResponse(r.total(), r.activos(), r.inactivos());
    }
}
