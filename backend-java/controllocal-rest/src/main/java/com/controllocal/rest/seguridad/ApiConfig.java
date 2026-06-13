package com.controllocal.rest.seguridad;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuracion privada del API cargada desde api.properties.
 * Las propiedades JVM y variables de entorno se conservan como alternativas
 * para un despliegue futuro en EC2.
 */
public final class ApiConfig {

    private static final String PROPERTIES_FILE = "api.properties";
    private static final Properties PROPERTIES = cargarPropiedades();

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

    private static Properties cargarPropiedades() {
        Properties properties = new Properties();
        try (InputStream input = ApiConfig.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo cargar " + PROPERTIES_FILE + ".", error);
        }
        return properties;
    }
}
