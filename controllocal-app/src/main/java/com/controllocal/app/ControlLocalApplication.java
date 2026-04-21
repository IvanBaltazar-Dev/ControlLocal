package com.controllocal.app;

import com.controllocal.config.DBManager;
import com.controllocal.config.DatabaseConfig;
import java.sql.Connection;
import java.sql.SQLException;

public class ControlLocalApplication {

    public static void main(String[] args) {

        try (Connection conn = DBManager.getConnection()) {
            System.out.println("Conexión exitosa a la base de datos.");
            System.out.println("AutoCommit: " + conn.getAutoCommit());
        } catch (SQLException e) {
            System.err.println("Error al conectar a la base de datos:");
            e.printStackTrace();
        }
    }
}
