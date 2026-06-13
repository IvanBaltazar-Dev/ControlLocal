package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.DistritoDAO;
import com.controllocal.model.inmueble.Distrito;

public class DistritoDAOImpl implements DistritoDAO {

    private static final String SELECT_SQL = """
            SELECT id_distrito, nombre, provincia, activo
            FROM distrito
            """;

    @Override
    public List<Distrito> listarActivos() {
        List<Distrito> distritos = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE activo = TRUE ORDER BY nombre");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                distritos.add(mapRow(rs));
            }
            return distritos;
        } catch (SQLException e) {
            throw new DAOException("Error al listar el catalogo de distritos.", e);
        }
    }

    @Override
    public Optional<Distrito> buscarPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE nombre = ?")) {
            ps.setString(1, nombre.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar el distrito " + nombre + ".", e);
        }
    }

    private Distrito mapRow(ResultSet rs) throws SQLException {
        return new Distrito(
                rs.getLong("id_distrito"),
                rs.getString("nombre"),
                rs.getString("provincia"),
                rs.getBoolean("activo")
        );
    }
}
