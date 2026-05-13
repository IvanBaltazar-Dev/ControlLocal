package com.controllocal.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConfig {

    private static final String PROPERTIES_FILE = "db.properties";
    private static final Properties PROPERTIES = loadProperties();
    private static final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();
    private DatabaseConfig() {
    }

    public static String getHost() {
        return getConfigValue("DB_HOST", "db.host", "localhost");
    }

    public static int getPort() {
        String value = getConfigValue("DB_PORT", "db.port", "3306");

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Valor invalido para DB_PORT/db.port: " + value
                    + ". Se usara el puerto por defecto: 3306");
            return 3306;
        }
    }

    public static String getDatabaseName() {
        return getConfigValue("DB_NAME", "db.name", "controllocal");
    }
    public static ThreadLocal<Connection> getConnectionHolder() {
        return connectionHolder;
    }
    public static String getUsername() {
        return getConfigValue("DB_USERNAME", "db.username", "root");
    }

    public static String getPassword() {
        return getConfigValue("DB_PASSWORD", "db.password", "");
    }

    public static boolean isUseSsl() {
        return Boolean.parseBoolean(getConfigValue("DB_USE_SSL", "db.useSSL", "false"));
    }

    public static String getServerTimezone() {
        return getConfigValue("DB_SERVER_TIMEZONE", "db.serverTimezone", "UTC");
    }

    public static String getJdbcUrl() {
        return "jdbc:mysql://" + getHost() + ":" + getPort() + "/" + getDatabaseName()
                + "?useSSL=" + isUseSsl()
                + "&serverTimezone=" + getServerTimezone();
    }

    private static String getConfigValue(String environmentKey, String propertyKey, String defaultValue) {
        String environmentValue = System.getenv(environmentKey);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }

        String propertyValue = PROPERTIES.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }

        return defaultValue;
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();

        try (InputStream input = DatabaseConfig.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            System.err.println("No se pudo cargar " + PROPERTIES_FILE + ". Se usaran valores por defecto.");
        }

        return properties;
    }
    public static void commit() throws SQLException {
        Connection conn = connectionHolder.get();
        if (conn != null) {
            conn.commit();
        }
    }
    public static void rollback() {
        Connection conn = connectionHolder.get();
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    public static void close() {
        Connection conn = connectionHolder.get();
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            // CRITICAL: Always remove to prevent memory leaks in thread pools
            connectionHolder.remove();
        }
    }
}
