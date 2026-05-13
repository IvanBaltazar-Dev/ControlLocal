package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.PropietarioDAO;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PropietarioDAOImpl implements PropietarioDAO {

    private static final String INSERT_SQL = "INSERT INTO propietario (id_persona) VALUES (?)";
    private static final String SELECT_SQL = """
            SELECT pr.id_propietario,
                   p.id_persona, p.tipo_persona, p.tipo_documento, p.numero_documento,
                   p.nombres_o_razon_social, p.telefono, p.correo, p.estado,
                   p.fecha_creacion, p.fecha_actualizacion
            FROM propietario pr
            INNER JOIN persona p ON pr.id_persona = p.id_persona
            """;
    private static final String UPDATE_SQL = "UPDATE propietario SET id_persona = ? WHERE id_propietario = ?";
    private static final String DELETE_SQL = """
            UPDATE persona p
            INNER JOIN propietario pr ON pr.id_persona = p.id_persona
            SET p.estado = 'INACTIVO'
            WHERE pr.id_propietario = ?
            """;

    @Override
    public Long crear(Propietario propietario) {
        validar(propietario, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, propietario.getPersona().getIdPersona());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    propietario.setIdPropietario(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de propietario.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear propietario.", e);
        }
    }

    @Override
    public Optional<Propietario> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE pr.id_propietario = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar propietario con id " + id + ".", e);
        }
    }

    @Override
    public List<Propietario> listarTodos() {
        List<Propietario> propietarios = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY pr.id_propietario");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                propietarios.add(mapRow(rs));
            }
            return propietarios;
        } catch (SQLException e) {
            throw new DAOException("Error al listar propietarios.", e);
        }
    }

    @Override
    public boolean actualizar(Propietario propietario) {
        validar(propietario, true);
        new PersonaDAOImpl().actualizar(propietario.getPersona());
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setLong(1, propietario.getPersona().getIdPersona());
            ps.setLong(2, propietario.getIdPropietario());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar propietario con id " + propietario.getIdPropietario() + ".", e);
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
            throw new DAOException("Error al eliminar propietario con id " + id + ".", e);
        }
    }

    private Propietario mapRow(ResultSet rs) throws SQLException {
        Propietario propietario = new Propietario();
        propietario.setIdPropietario(rs.getLong("id_propietario"));
        propietario.setPersona(JdbcSupport.mapPersona(rs));
        return propietario;
    }

    private void validar(Propietario propietario, boolean requiereId) {
        if (propietario == null) {
            throw new IllegalArgumentException("El propietario no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(propietario.getIdPropietario());
        }
        Persona persona = propietario.getPersona();
        JdbcSupport.validarId(JdbcSupport.getIdPersona(persona));
    }
}
