package com.controllocal.rest.http;

import java.util.List;

/**
 * Respuesta paginada estandar del API: GET /recurso?pagina=1&amp;tamano=20.
 */
public record PageResponse<T>(List<T> items, long totalRecords, int page, int pageSize) {
}
