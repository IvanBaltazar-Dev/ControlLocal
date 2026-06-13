package com.controllocal.rest.util;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.HttpServletResponse;

public final class JsonUtils {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private JsonUtils() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static void responder(HttpServletResponse response, int estado, Object cuerpo) throws IOException {
        response.setStatus(estado);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        MAPPER.writeValue(response.getWriter(), cuerpo);
    }
}
