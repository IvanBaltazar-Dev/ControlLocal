package com.controllocal.config;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Compatibilidad temporal para codigo que usaba DatabaseConfig.
 * El codigo nuevo debe usar DBManager y TransactionContext.
 */
@Deprecated
public final class DatabaseConfig {

    private DatabaseConfig() {
    }

    public static String getJdbcUrl() {
        return DBManager.getInstance().getUrl();
    }

    public static String getUser() {
        return DBManager.getInstance().getUser();
    }

    public static String getPassword() {
        return DBManager.getInstance().getPassword();
    }

    public static ThreadLocal<Connection> getConnectionHolder() {
        return TransactionContext.connectionHolder();
    }

    public static boolean isTransactionActive() {
        return TransactionContext.isActive();
    }

    public static void commit() throws SQLException {
        TransactionContext.commit();
    }

    public static void rollback() {
        try {
            TransactionContext.rollback();
        } catch (SQLException error) {
            throw new IllegalStateException("No se pudo revertir la transaccion JDBC.", error);
        }
    }

    public static void close() {
        try {
            TransactionContext.close();
        } catch (SQLException error) {
            throw new IllegalStateException("No se pudo cerrar la conexion JDBC.", error);
        }
    }
}
