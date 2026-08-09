package com.controllocal.service;

import java.util.List;

/**
 * Pagina de resultados del service: items + total de la consulta con el
 * scope y los filtros aplicados. La capa web la envuelve en su
 * PageResponse (contrato congelado).
 */
public record Pagina<T>(List<T> items, long total) {

    public static <T> Pagina<T> vacia() {
        return new Pagina<>(List.of(), 0);
    }
}
