package com.controllocal.rest.http;

import java.util.List;


public record PageResponse<T>(List<T> items, long totalRecords, int page, int pageSize) {
}
