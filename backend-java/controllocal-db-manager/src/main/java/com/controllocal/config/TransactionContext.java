package com.controllocal.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Mantiene una unica conexion por hilo durante una transaccion.
 */
public final class TransactionContext {

    private static final ThreadLocal<Connection> CONNECTION_HOLDER = new ThreadLocal<>();

    private TransactionContext() {
    }

    public static Connection getConnection() throws SQLException {
        Connection connection = CONNECTION_HOLDER.get();
        if (connection == null || connection.isClosed()) {
            connection = DBManager.getInstance().openConnection();
            try {
                connection.setAutoCommit(false);
                CONNECTION_HOLDER.set(connection);
            } catch (SQLException error) {
                try {
                    connection.close();
                } catch (SQLException closeError) {
                    error.addSuppressed(closeError);
                }
                throw error;
            }
        }
        return connectionWithoutClose(connection);
    }

    public static boolean isActive() {
        return CONNECTION_HOLDER.get() != null;
    }

    public static void commit() throws SQLException {
        Connection connection = CONNECTION_HOLDER.get();
        if (connection != null) {
            connection.commit();
        }
    }

    public static void rollback() throws SQLException {
        Connection connection = CONNECTION_HOLDER.get();
        if (connection != null) {
            connection.rollback();
        }
    }

    public static void close() throws SQLException {
        Connection connection = CONNECTION_HOLDER.get();
        CONNECTION_HOLDER.remove();
        if (connection != null) {
            connection.close();
        }
    }

    static Connection currentConnectionOrNew() throws SQLException {
        return isActive()
                ? connectionWithoutClose(CONNECTION_HOLDER.get())
                : DBManager.getInstance().openConnection();
    }

    static ThreadLocal<Connection> connectionHolder() {
        return CONNECTION_HOLDER;
    }

    // Los DAO usan try-with-resources. Durante una transaccion, close() debe
    // cerrar solo el recurso logico; TransactionContext.close() cierra el real.
    private static Connection connectionWithoutClose(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    if ("isClosed".equals(method.getName())) {
                        return connection.isClosed();
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException error) {
                        throw error.getTargetException();
                    }
                });
    }
}
