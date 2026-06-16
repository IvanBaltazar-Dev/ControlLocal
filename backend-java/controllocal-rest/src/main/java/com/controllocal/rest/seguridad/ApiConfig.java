package com.controllocal.rest.seguridad;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuracion privada del API cargada desde un archivo externo al WAR.
 */
public final class ApiConfig {

    private static final String CONFIG_PATH_PROPERTY = "api.config.path";
    private static final Path DEFAULT_CONFIG_PATH = Path.of("config", "api.properties");
    private static final Properties PROPERTIES = loadProperties();

    private ApiConfig() {
    }

    public static String get(String propertyKey, String environmentKey, String defaultValue) {
        String systemValue = System.getProperty(propertyKey);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue.trim();
        }

        String environmentValue = System.getenv(environmentKey);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }

        String propertyValue = PROPERTIES.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }

        return defaultValue;
    }

    private static Properties loadProperties() {
        Path configPath = resolveConfigPath();
        Properties properties = new Properties();

        if (!Files.isRegularFile(configPath)) {
            return properties;
        }

        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
            return properties;
        } catch (IOException error) {
            throw new IllegalStateException(
                    "No se pudo cargar la configuracion externa del API.", error);
        }
    }

    private static Path resolveConfigPath() {
        String configuredPath = System.getProperty(CONFIG_PATH_PROPERTY);
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath.trim()).toAbsolutePath().normalize();
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(DEFAULT_CONFIG_PATH).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }

        return Path.of("").toAbsolutePath().resolve(DEFAULT_CONFIG_PATH).normalize();
    }
}
