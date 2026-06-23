package com.controllocal.rest.almacen;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuracion del almacen de documentos (S3 y disco local). Espeja la mecanica
 * de {@code ApiConfig}, pero sobre "aws.properties":
 *   1. Propiedad de sistema (-Dclave=...).
 *   2. Variable de entorno.
 *   3. Recurso "aws.properties" empaquetado en el classpath del WAR.
 *
 * "aws.properties" esta ignorado en git (lleva credenciales). El proyecto es solo
 * de desarrollo local, asi que las claves temporales (STS) viven en ese archivo.
 */
public final class AwsConfig {

    private static final String CLASSPATH_RESOURCE = "aws.properties";
    private static final Properties PROPERTIES = loadProperties();

    private AwsConfig() {
    }

    public static String get(String propertyKey, String environmentKey, String defaultValue) {
        String systemValue = System.getProperty(propertyKey);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue.trim();
        }
        if (environmentKey != null) {
            String environmentValue = System.getenv(environmentKey);
            if (environmentValue != null && !environmentValue.isBlank()) {
                return environmentValue.trim();
            }
        }
        String propertyValue = PROPERTIES.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }
        return defaultValue;
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream classpathInput = AwsConfig.class.getClassLoader()
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
}
