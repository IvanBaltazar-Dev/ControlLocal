package com.controllocal.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;

public class PruebaConexionBD {

    private static final Set<String> TABLAS_REQUERIDAS_PARA_CAPTACION = Set.of(
            "persona",
            "usuario_interno",
            "propietario",
            "local_comercial",
            "agente_inmobiliario",
            "captacion"
    );

    public static void main(String[] args) {
        try (Connection connection = DBManager.getConnection()) {
            validarConexion(connection);
            mostrarMotor(connection);
            validarTablasParaCaptacion(connection);
        } catch (Exception error) {
            throw new IllegalStateException("Error de conexion: revisar configuracion local.", error);
        }

        validarContextoTransaccional();
    }

    private static void validarConexion(Connection connection) throws SQLException {
        try (
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT 1")
        ) {
            if (result.next()) {
                System.out.println("Conexion JDBC exitosa.");
            }
        }
    }

    private static void mostrarMotor(Connection connection) {
        try (
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT AURORA_VERSION()")
        ) {
            if (result.next()) {
                System.out.println("Motor detectado: Amazon Aurora MySQL.");
            }
        } catch (SQLException error) {
            System.out.println("Motor detectado: Amazon RDS MySQL.");
        }
    }

    private static void validarTablasParaCaptacion(Connection conn) throws SQLException {
        Set<String> tablasExistentes = new TreeSet<>();

        try (
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SHOW TABLES")
        ) {
            while (rs.next()) {
                tablasExistentes.add(rs.getString(1));
            }
        }

        Set<String> faltantes = new TreeSet<>(TABLAS_REQUERIDAS_PARA_CAPTACION);
        faltantes.removeAll(tablasExistentes);

        if (faltantes.isEmpty()) {
            System.out.println("Esquema minimo validado.");
        } else {
            System.err.println("El esquema no contiene todas las tablas requeridas.");
        }
    }

    private static void validarContextoTransaccional() {
        try {
            Connection transactionConnection = TransactionContext.getConnection();

            try (Connection daoConnection = DBManager.getConnection()) {
                validarConexion(daoConnection);
            }

            if (transactionConnection.isClosed()) {
                throw new IllegalStateException(
                        "El cierre logico del DAO cerro la conexion transaccional.");
            }

            validarConexion(transactionConnection);
            TransactionContext.rollback();
            System.out.println("Contexto transaccional validado.");
        } catch (SQLException error) {
            throw new IllegalStateException("No se pudo validar el contexto transaccional.", error);
        } finally {
            try {
                TransactionContext.close();
            } catch (SQLException error) {
                throw new IllegalStateException("No se pudo cerrar la prueba transaccional.", error);
            }
        }
    }
}
