package com.controllocal.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Arranque del backend v2 (fat jar; reemplaza WAR+GlassFish).
 * El context-path replica la base del backend Jakarta (/controllocal/Api)
 * para que el reverse-proxy del Strangler pueda enrutar por ruta sin
 * reescrituras (Doc 5 §9).
 */
@SpringBootApplication(scanBasePackages = "com.controllocal")
@EntityScan("com.controllocal.domain")
@EnableJpaRepositories("com.controllocal.persistence")
public class ControlLocalApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlLocalApplication.class, args);
    }
}
