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
        System.out.println("----- PRUEBA DE CONEXION A BASE DE DATOS -----");
        System.out.println("JDBC URL: " + DatabaseConfig.getJdbcUrl());
        System.out.println("Usuario: " + DatabaseConfig.getUsername());

        try (Connection conn = DBManager.getConnection()) {
            imprimirDatosConexion(conn);
            validarTablasParaCaptacion(conn);
        } catch (Exception e) {
            System.err.println("No se pudo validar la base de datos.");
            System.err.println("Host: " + DatabaseConfig.getHost());
            System.err.println("Puerto: " + DatabaseConfig.getPort());
            System.err.println("Base de datos: " + DatabaseConfig.getDatabaseName());
            System.err.println("Usuario: " + DatabaseConfig.getUsername());
            e.printStackTrace();
        }
    }

    private static void imprimirDatosConexion(Connection conn) throws SQLException {
        try (
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT DATABASE(), NOW()")
        ) {
            if (rs.next()) {
                System.out.println("Conexion OK.");
                System.out.println("Base de datos actual: " + rs.getString(1));
                System.out.println("Fecha servidor: " + rs.getString(2));
            }
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

        System.out.println("Tablas encontradas: " + tablasExistentes);

        Set<String> faltantes = new TreeSet<>(TABLAS_REQUERIDAS_PARA_CAPTACION);
        faltantes.removeAll(tablasExistentes);

        if (faltantes.isEmpty()) {
            System.out.println("Esquema OK para PruebaCaptacion.");
        } else {
            System.err.println("Faltan tablas para PruebaCaptacion: " + faltantes);
            System.err.println("Ejecuta database/ddl/01_create_schema_controllocal_v3.sql en esta base,");
            System.err.println("o cambia db.name a la base que ya tenga esas tablas creadas.");
        }
    }
}
