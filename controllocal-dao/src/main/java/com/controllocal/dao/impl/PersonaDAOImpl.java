package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.PersonaDAO;
import com.controllocal.model.persona.Persona;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PersonaDAOImpl implements PersonaDAO {

    private static final String INSERT_SQL = """
            INSERT INTO persona (
                tipo_persona, tipo_documento, numero_documento,
                nombres_o_razon_social, telefono, correo, estado
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT id_persona, tipo_persona, tipo_documento, numero_documento,
                   nombres_o_razon_social, telefono, correo, estado,
                   fecha_creacion, fecha_actualizacion
            FROM persona
            """;

    private static final String UPDATE_SQL = """
            UPDATE persona
            SET tipo_persona = ?, tipo_documento = ?, numero_documento = ?,
                nombres_o_razon_social = ?, telefono = ?, correo = ?, estado = ?
            WHERE id_persona = ?
            """;

    private static final String DELETE_SQL = "UPDATE persona SET estado = 'INACTIVO' WHERE id_persona = ?";

    @Override
    public Long crear(Persona persona) {
        validar(persona, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, persona.getTipoPersona().name());
            ps.setString(2, persona.getTipoDocumento());
            ps.setString(3, persona.getNumeroDocumento());
            ps.setString(4, persona.getNombresORazonSocial());
            ps.setString(5, persona.getTelefono());
            ps.setString(6, persona.getCorreo());
            ps.setString(7, persona.getEstado().name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    persona.setIdPersona(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de persona.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear persona.", e);
        }
    }

    @Override
    public Optional<Persona> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_persona = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(JdbcSupport.mapPersona(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar persona con id " + id + ".", e);
        }
    }

    @Override
    public List<Persona> listarTodos() {
        List<Persona> personas = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY id_persona");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                personas.add(JdbcSupport.mapPersona(rs));
            }
            return personas;
        } catch (SQLException e) {
            throw new DAOException("Error al listar personas.", e);
        }
    }

    @Override
    public boolean actualizar(Persona persona) {
        validar(persona, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setString(1, persona.getTipoPersona().name());
            ps.setString(2, persona.getTipoDocumento());
            ps.setString(3, persona.getNumeroDocumento());
            ps.setString(4, persona.getNombresORazonSocial());
            ps.setString(5, persona.getTelefono());
            ps.setString(6, persona.getCorreo());
            ps.setString(7, persona.getEstado().name());
            ps.setLong(8, persona.getIdPersona());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar persona con id " + persona.getIdPersona() + ".", e);
        }
    }

    @Override
    public boolean eliminar(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al eliminar persona con id " + id + ".", e);
        }
    }

    private void validar(Persona persona, boolean requiereId) {
        if (persona == null) {
            throw new IllegalArgumentException("La persona no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(persona.getIdPersona());
        }
        if (persona.getTipoPersona() == null || persona.getTipoDocumento() == null
                || persona.getTipoDocumento().isBlank() || persona.getNumeroDocumento() == null
                || persona.getNumeroDocumento().isBlank() || persona.getNombresORazonSocial() == null
                || persona.getNombresORazonSocial().isBlank() || persona.getEstado() == null) {
            throw new IllegalArgumentException("La persona tiene campos obligatorios incompletos.");
        }
    }
}
