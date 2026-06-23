package com.controllocal.rest.seguridad;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuracion privada del API. Se busca en este orden:
 *   1. Ruta absoluta indicada por -Dapi.config.path=...
 *   2. Recurso "api.properties" empaquetado en el classpath del WAR.
 */
public final class ApiConfig {

    private static final String CONFIG_PATH_PROPERTY = "api.config.path";
    private static final String CLASSPATH_RESOURCE = "api.properties";
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
        Properties properties = new Properties();

        String configuredPath = System.getProperty(CONFIG_PATH_PROPERTY);
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path path = Path.of(configuredPath.trim()).toAbsolutePath().normalize();
            return cargarDesde(path, properties, true);
        }

        try (InputStream classpathInput = ApiConfig.class.getClassLoader()
                .getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (classpathInput != null) {
                properties.load(classpathInput);
                return properties;
            }
        } catch (IOException error) {
            throw new IllegalStateException(
                    "No se pudo cargar " + CLASSPATH_RESOURCE + " desde el classpath.", error);
        }

        return properties;
    }

    private static Properties cargarDesde(Path path, Properties properties, boolean obligatorio) {
        if (!Files.isRegularFile(path)) {
            if (obligatorio) {
                throw new IllegalStateException("No existe el archivo configurado en -D"
                        + CONFIG_PATH_PROPERTY + ": " + path);
            }
            return properties;
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException error) {
            throw new IllegalStateException(
                    "No se pudo cargar la configuracion externa del API en " + path + ".", error);
        }
    }
}
