package com.controllocal.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrato OpenAPI (RC-005). Durante el Strangler documenta la forma
 * CONGELADA del cable; crece modulo a modulo conforme se migran.
 * UI: /swagger-ui.html · JSON: /v3/api-docs (relativos al context-path).
 */
@Configuration
public class ConfiguracionOpenApi {

    @Bean
    public OpenAPI apiControlLocal() {
        return new OpenAPI().info(new Info()
                .title("ControlLocal API")
                .description("Contrato REST congelado del Strangler (backend v2 Spring). "
                        + "Módulos migrados: salud, auth. Los ids de sesión mapean al modelo "
                        + "Party-Role: idUsuario = persona, idDominio = rol operativo (broker/agente).")
                .version("2.0.0-F0"));
    }
}
