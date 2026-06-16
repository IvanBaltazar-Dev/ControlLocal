package com.controllocal.rest.util;

import java.io.IOException;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import jakarta.servlet.http.HttpServletResponse;

public final class JsonUtils {

    private JsonUtils() {
    }

    public static void responderError(
            HttpServletResponse response,
            int estado,
            String mensaje) throws IOException {
        response.setStatus(estado);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");

        JsonObject cuerpo = Json.createObjectBuilder()
                .add("error", mensaje)
                .build();
        try (JsonWriter writer = Json.createWriter(response.getWriter())) {
            writer.writeObject(cuerpo);
        }
    }
}
