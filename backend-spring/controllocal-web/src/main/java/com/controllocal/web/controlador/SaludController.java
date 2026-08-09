package com.controllocal.web.controlador;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Contrato congelado: mismo cuerpo que el SaludRest del backend Jakarta. */
@RestController
public class SaludController {

    @GetMapping("/salud")
    public Map<String, Object> salud() {
        return Map.of(
                "estado", "ok",
                "servicio", "ControlLocal API",
                "fecha", Instant.now().toString());
    }
}
