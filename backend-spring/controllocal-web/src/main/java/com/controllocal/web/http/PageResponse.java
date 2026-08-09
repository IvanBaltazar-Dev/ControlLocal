package com.controllocal.web.http;

import java.util.List;

/**
 * Sobre de paginacion del contrato congelado (mismos nombres de campo que
 * el PageResponse del backend Jakarta).
 */
public record PageResponse<T>(List<T> items, long totalRecords, int page, int pageSize) {
}
