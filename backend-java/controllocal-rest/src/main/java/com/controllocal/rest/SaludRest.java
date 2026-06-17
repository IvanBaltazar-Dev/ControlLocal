package com.controllocal.rest;

import java.time.Instant;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("salud")
@Produces(MediaType.APPLICATION_JSON)
public class SaludRest {

    @GET
    public Map<String, Object> salud() {
        return Map.of(
                "estado", "ok",
                "servicio", "ControlLocal API",
                "fecha", Instant.now().toString());
    }
}
