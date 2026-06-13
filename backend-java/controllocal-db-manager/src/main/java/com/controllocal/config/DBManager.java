package com.controllocal.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Punto unico de acceso a conexiones. Usa un pool HikariCP para evitar abrir
 * una conexion fisica por consulta; las transacciones siguen ligadas al hilo
 * mediante DatabaseConfig (la misma conexion durante toda la transaccion).
 */
public final class DBManager {

    private static final DBManager INSTANCE = new DBManager();
    private static volatile HikariDataSource dataSource;

    private DBManager() {
    }

    public static DBManager getInstance() {
        return INSTANCE;
    }

    public static Connection getConnection() throws SQLException {
        if (!DatabaseConfig.isTransactionActive()) {
            return pool().getConnection();
        }

        Connection conn = DatabaseConfig.getConnectionHolder().get();
        if (conn == null || conn.isClosed()) {
            conn = pool().getConnection();
            conn.setAutoCommit(false);
            DatabaseConfig.getConnectionHolder().set(conn);
        }
        return closeSuppressingConnection(conn);
    }

    public static Connection beginTransaction() throws SQLException {
        DatabaseConfig.markTransactionActive();
        return getConnection();
    }

    public static void shutdown() {
        HikariDataSource ds = dataSource;
        if (ds != null) {
            ds.close();
            dataSource = null;
        }
    }

    private static HikariDataSource pool() {
        HikariDataSource ds = dataSource;
        if (ds == null) {
            synchronized (DBManager.class) {
                ds = dataSource;
                if (ds == null) {
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(DatabaseConfig.getJdbcUrl());
                    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                    config.setUsername(DatabaseConfig.getUsername());
                    config.setPassword(DatabaseConfig.getPassword());
                    config.setMaximumPoolSize(DatabaseConfig.getPoolMaxSize());
                    config.setMinimumIdle(0);
                    config.setConnectionTimeout(10_000);
                    config.setValidationTimeout(5_000);
                    config.setKeepaliveTime(60_000);
                    config.setIdleTimeout(2 * 60_000);
                    config.setMaxLifetime(5 * 60_000);
                    config.setPoolName("controllocal-pool");
                    ds = new HikariDataSource(config);
                    dataSource = ds;
                }
            }
        }
        return ds;
    }

    // Dentro de una transaccion los DAO no deben cerrar la conexion compartida;
    // el cierre real ocurre en DatabaseConfig.close() al terminar la transaccion.
    private static Connection closeSuppressingConnection(Connection conn) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    try {
                        return method.invoke(conn, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                }
        );
    }
}
