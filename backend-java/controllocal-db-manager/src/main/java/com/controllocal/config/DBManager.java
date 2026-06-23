package com.controllocal.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

/**
 * Carga la configuracion de la base de datos y abre conexiones JDBC.
 */
public final class DBManager {

    private static final String CONFIG_PATH_PROPERTY = "db.config.path";
    private static final String CREDENTIALS_FILE = "db.properties";
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final List<Path> DEFAULT_CONFIG_PATHS = List.of(
            Path.of("controllocal-db-manager", "src", "main", "resources", CREDENTIALS_FILE),
            Path.of("backend-java", "controllocal-db-manager", "src", "main", "resources", CREDENTIALS_FILE)
    );
    private static final DBManager INSTANCE = new DBManager();

    private final String url;
    private final String user;
    private final String password;

    private DBManager() {
        loadDriver();
        Properties properties = loadProperties();
        String host = required(properties, "db.host", "host");
        String port = required(properties, "db.port", "port");
        String database = required(properties, "db.name", "database");
        boolean ssl = Boolean.parseBoolean(optional(properties, "db.ssl", "ssl", "false"));

        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?sslMode=" + (ssl ? "REQUIRED" : "DISABLED")
                + "&serverTimezone=UTC"
                + "&useUnicode=true"
                + "&characterEncoding=UTF-8"
                + "&allowPublicKeyRetrieval=false"
                + "&fallbackToSystemKeyStore=false"
                + "&fallbackToSystemTrustStore=false";
        this.user = required(properties, "db.user", "user");
        this.password = required(properties, "db.password", "password");
    }

    public static DBManager getInstance() {
        return INSTANCE;
    }

    /**
     * Devuelve una conexion normal o la conexion de la transaccion actual.
     */
    public static Connection getConnection() throws SQLException {
        return TransactionContext.currentConnectionOrNew();
    }

    Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    String getUrl() {
        return url;
    }

    String getUser() {
        return user;
    }

    String getPassword() {
        return password;
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();

        try (InputStream input = openProperties()) {
            properties.load(input);
            return properties;
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo cargar " + CREDENTIALS_FILE + ".", error);
        }
    }

    private static void loadDriver() {
        try {
            Class.forName(MYSQL_DRIVER);
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException(
                    "No se encontro MySQL Connector/J en el classpath.", error);
        }
    }

    private static InputStream openProperties() throws IOException {
        String configuredPath = System.getProperty(CONFIG_PATH_PROPERTY);
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path path = Path.of(configuredPath.trim()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("No existe el archivo configurado en -Ddb.config.path: " + path);
            }
            return Files.newInputStream(path);
        }

        InputStream classpathInput = DBManager.class.getClassLoader().getResourceAsStream(CREDENTIALS_FILE);
        if (classpathInput != null) {
            return classpathInput;
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            for (Path relativePath : DEFAULT_CONFIG_PATHS) {
                Path candidate = current.resolve(relativePath).normalize();
                if (Files.isRegularFile(candidate)) {
                    return Files.newInputStream(candidate);
                }
            }
            current = current.getParent();
        }

        throw new IllegalStateException(
                "No se encontro db.properties. Crea el archivo en "
                        + "controllocal-db-manager/src/main/resources/db.properties "
                        + "o define -Ddb.config.path=/ruta/db.properties.");
    }

    private static String required(Properties properties, String primaryKey, String simpleKey) {
        String value = optional(properties, primaryKey, simpleKey, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Falta la propiedad obligatoria " + primaryKey + " en " + CREDENTIALS_FILE + ".");
        }
        return value.trim();
    }

    private static String optional(
            Properties properties,
            String primaryKey,
            String simpleKey,
            String defaultValue) {
        String value = properties.getProperty(primaryKey);
        if (value == null || value.isBlank()) {
            value = properties.getProperty(simpleKey);
        }
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
