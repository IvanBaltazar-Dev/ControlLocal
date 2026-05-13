package com.controllocal.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBManager {

    private DBManager() {
    }

    public static Connection getConnection() throws SQLException {
        if (!DatabaseConfig.isTransactionActive()) {
            return DriverManager.getConnection(
                    DatabaseConfig.getJdbcUrl(),
                    DatabaseConfig.getUsername(),
                    DatabaseConfig.getPassword()
            );
        }

        Connection conn = DatabaseConfig.getConnectionHolder().get();
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(
                    DatabaseConfig.getJdbcUrl(),
                    DatabaseConfig.getUsername(),
                    DatabaseConfig.getPassword()
            );
            conn.setAutoCommit(false);
            DatabaseConfig.getConnectionHolder().set(conn);
        }
        return closeSuppressingConnection(conn);
    }

    public static void beginTransaction() throws SQLException {
        DatabaseConfig.markTransactionActive();
        Connection conn = DatabaseConfig.getConnectionHolder().get();
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(
                    DatabaseConfig.getJdbcUrl(),
                    DatabaseConfig.getUsername(),
                    DatabaseConfig.getPassword()
            );
            DatabaseConfig.getConnectionHolder().set(conn);
        }
        conn.setAutoCommit(false);
    }

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
