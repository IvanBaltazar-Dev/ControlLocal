package com.controllocal.config;

public final class DatabaseConfig {

    private static final String DEFAULT_HOST = "prog3-labs-1inf30-2026-1.cyhb9uxxuaip.us-east-1.rds.amazonaws.com";
    private static final int DEFAULT_PORT = 3306;
    private static final String DEFAULT_DATABASE = "controllocal";
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "labs1inf3020261";
    private static final boolean DEFAULT_USE_SSL = true;
    private static final String DEFAULT_SERVER_TIMEZONE = "UTC";

    private DatabaseConfig() {
    }

    public static String getHost() {
        String value = System.getenv("DB_HOST");
        return (value == null || value.isBlank()) ? DEFAULT_HOST : value;
    }

    public static int getPort() {
        String value = System.getenv("DB_PORT");

        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Valor inválido para DB_PORT: " + value +
                    ". Se usará el puerto por defecto: " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    public static String getDatabaseName() {
        String value = System.getenv("DB_NAME");
        return (value == null || value.isBlank()) ? DEFAULT_DATABASE : value;
    }

    public static String getUsername() {
        String value = System.getenv("DB_USERNAME");
        return (value == null || value.isBlank()) ? DEFAULT_USERNAME : value;
    }

    public static String getPassword() {
        String value = System.getenv("DB_PASSWORD");
        return (value == null) ? DEFAULT_PASSWORD : value;
    }

    public static boolean isUseSsl() {
        String value = System.getenv("DB_USE_SSL");
        return (value == null || value.isBlank()) ? DEFAULT_USE_SSL : Boolean.parseBoolean(value);
    }

    public static String getServerTimezone() {
        String value = System.getenv("DB_SERVER_TIMEZONE");
        return (value == null || value.isBlank()) ? DEFAULT_SERVER_TIMEZONE : value;
    }

    public static String getJdbcUrl() {
        return "jdbc:mysql://" + getHost() + ":" + getPort() + "/" + getDatabaseName()
                + "?useSSL=" + isUseSsl()
                + "&serverTimezone=" + getServerTimezone();
    }
}
