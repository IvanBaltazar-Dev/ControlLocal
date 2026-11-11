package com.controllocal.web.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** IDs, versiones y cantidades enteras no pueden obtenerse truncando un decimal recibido. */
@Configuration(proxyBeanMethods=false)
public class ConfiguracionJson {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer enterosSinTruncar() {
        return constructor -> constructor.featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
