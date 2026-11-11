package com.controllocal.web.capacidades;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ConfiguracionCapacidades implements WebMvcConfigurer {
    private final RegistroAdaptadoresHttp adaptadores;
    public ConfiguracionCapacidades(RegistroAdaptadoresHttp adaptadores) { this.adaptadores = adaptadores; }

    /** Mismo evaluador para @PreAuthorize y para su descripción; ninguna política paralela. */
    @Bean
    static MethodSecurityExpressionHandler expresionesDeSeguridad() {
        return new DefaultMethodSecurityExpressionHandler();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(adaptadores); }
}
