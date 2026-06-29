package com.controllocal.config;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Properties;

/**
 * Carga la configuracion de la base de datos y entrega conexiones JDBC desde un
 * pool propio (solo java.sql, sin librerias externas).
 *
 * Antes cada consulta abria una conexion nueva con {@link DriverManager}. Contra
 * un RDS remoto con SSL, ese handshake (TCP + TLS + autenticacion) cuesta cientos de
 * ms por consulta; sumado a los patrones N+1 del dashboard saturaba el servidor y las
 * peticiones tardaban minutos. El pool mantiene conexiones fisicas ya abiertas y las
 * reutiliza: cada consulta paga solo el ida-vuelta de la query, no el de la conexion.
 *
 * {@link #openConnection()} entrega un envoltorio cuyo {@code close()} devuelve la
 * conexion al pool en lugar de cerrarla. El resto del codigo (DAO con try-with-resources,
 * {@link TransactionContext}) sigue igual: cierra "su" conexion y el pool la recicla.
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

    // --- Pool JDBC ---
    private final Deque<Ociosa> ociosas = new ArrayDeque<>();
    private int vivas;                 // conexiones fisicas abiertas (ociosas + en uso)
    private boolean cerrado;           // el pool ya no entrega conexiones
    private final int maxPool;         // tope de conexiones fisicas simultaneas
    private final long esperaMaxMs;    // espera maxima por una conexion antes de fallar
    private final long revalidarMs;    // re-valida una conexion ociosa pasado este tiempo

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

        this.maxPool = Math.max(1, intProp(properties, "db.pool.max", 10));
        this.esperaMaxMs = longProp(properties, "db.pool.timeoutMs", 10_000L);
        this.revalidarMs = longProp(properties, "db.pool.revalidarMs", 30_000L);

        Runtime.getRuntime().addShutdownHook(new Thread(DBManager::shutdown, "controllocal-pool-shutdown"));
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

    /** Cierra el pool y todas sus conexiones ociosas. Idempotente. */
    public static void shutdown() {
        INSTANCE.cerrarPool();
    }

    /**
     * Toma una conexion del pool. Se devuelve al pool (no se cierra fisicamente)
     * cuando el llamante hace close() via try-with-resources.
     */
    Connection openConnection() throws SQLException {
        Connection real = tomarFisica();
        return envolver(real);
    }

    // Reserva/crea una conexion fisica del pool. El handshake (crear conexion) y la
    // validacion se hacen FUERA del lock para no serializar a los demas hilos.
    private Connection tomarFisica() throws SQLException {
        long limite = System.currentTimeMillis() + esperaMaxMs;
        while (true) {
            Ociosa candidata = null;
            boolean crear = false;
            synchronized (this) {
                while (true) {
                    if (cerrado) {
                        throw new SQLException("El pool de conexiones esta cerrado.");
                    }
                    if (!ociosas.isEmpty()) {
                        candidata = ociosas.pollFirst();
                        break;
                    }
                    if (vivas < maxPool) {
                        vivas++;        // reservo el cupo; creo la conexion fuera del lock
                        crear = true;
                        break;
                    }
                    long restante = limite - System.currentTimeMillis();
                    if (restante <= 0) {
                        throw new SQLException(
                                "Pool de conexiones agotado (max " + maxPool + ").");
                    }
                    try {
                        wait(restante);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Interrumpido esperando una conexion del pool.", e);
                    }
                }
            }

            if (crear) {
                try {
                    return DriverManager.getConnection(url, user, password);
                } catch (SQLException e) {
                    synchronized (this) {
                        vivas--;
                        notifyAll();
                    }
                    throw e;
                }
            }

            // Reusar una ociosa: solo se valida (round-trip) si lleva rato parada.
            boolean revisar = System.currentTimeMillis() - candidata.ociosaDesde >= revalidarMs;
            if (!revisar || esValida(candidata.conexion)) {
                return candidata.conexion;
            }
            cerrarSilencioso(candidata.conexion);
            synchronized (this) {
                vivas--;
                notifyAll();
            }
            // descarto la rota y vuelvo a intentar (crear o tomar otra)
        }
    }

    // Devuelve la conexion fisica al pool, restaurando autoCommit=true (las
    // transacciones lo dejan en false). Si esta rota o el pool ya cerro, se descarta.
    private void devolverFisica(Connection real) {
        boolean conservar = false;
        synchronized (this) {
            if (cerrado) {
                vivas--;
            } else {
                conservar = true;
            }
        }
        if (!conservar) {
            cerrarSilencioso(real);
            synchronized (this) {
                notifyAll();
            }
            return;
        }
        try {
            if (real.isClosed()) {
                throw new SQLException("conexion cerrada");
            }
            if (!real.getAutoCommit()) {
                real.setAutoCommit(true);
            }
            synchronized (this) {
                ociosas.addLast(new Ociosa(real));
                notifyAll();
            }
        } catch (SQLException e) {
            cerrarSilencioso(real);
            synchronized (this) {
                vivas--;
                notifyAll();
            }
        }
    }

    private void cerrarPool() {
        Deque<Ociosa> aCerrar;
        synchronized (this) {
            if (cerrado) {
                return;
            }
            cerrado = true;
            aCerrar = new ArrayDeque<>(ociosas);
            ociosas.clear();
            vivas = 0;
            notifyAll();
        }
        for (Ociosa o : aCerrar) {
            cerrarSilencioso(o.conexion);
        }
    }

    private static boolean esValida(Connection c) {
        try {
            return !c.isClosed() && c.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private static void cerrarSilencioso(Connection c) {
        try {
            c.close();
        } catch (SQLException ignored) {
            // cierre mejor-esfeurzo
        }
    }

    private Connection envolver(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new PooledConnectionHandler(real));
    }

    // Entrada del pool con marca de tiempo para decidir cuando revalidar.
    private static final class Ociosa {
        private final Connection conexion;
        private final long ociosaDesde;

        Ociosa(Connection conexion) {
            this.conexion = conexion;
            this.ociosaDesde = System.currentTimeMillis();
        }
    }

    // Envoltorio cuyo close() devuelve la conexion al pool en vez de cerrarla.
    private final class PooledConnectionHandler implements InvocationHandler {
        private final Connection real;
        private boolean devuelta;

        PooledConnectionHandler(Connection real) {
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("close".equals(name)) {
                if (!devuelta) {
                    devuelta = true;
                    devolverFisica(real);
                }
                return null;
            }
            if ("isClosed".equals(name)) {
                return devuelta || real.isClosed();
            }
            if (devuelta) {
                throw new SQLException("La conexion ya fue devuelta al pool.");
            }
            try {
                return method.invoke(real, args);
            } catch (InvocationTargetException error) {
                throw error.getTargetException();
            }
        }
    }

    private static int intProp(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static long longProp(Properties properties, String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
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
