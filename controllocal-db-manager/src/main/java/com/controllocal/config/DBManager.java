package com.controllocal.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBManager {

    private DBManager() {
    }

    public static Connection getConnection() throws SQLException {
        // 1. Intentamos obtener la conexión del hilo actual (la transacción activa)
        Connection conn = DatabaseConfig.getConnectionHolder().get();

        // 2. Si no hay una o está cerrada, creamos una nueva
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(
                    DatabaseConfig.getJdbcUrl(),
                    DatabaseConfig.getUsername(),
                    DatabaseConfig.getPassword()
            );

            conn.setAutoCommit(false); // Necesario para transacciones manuales
            DatabaseConfig.getConnectionHolder().set(conn);
        }
        return conn;
    }
}
