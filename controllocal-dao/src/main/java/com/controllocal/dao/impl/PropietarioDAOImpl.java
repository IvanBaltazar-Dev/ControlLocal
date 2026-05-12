package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.PropietarioDAO;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.persona.TipoPersona;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class PropietarioDAOImpl implements PropietarioDAO {

    private static final String INSERT_SQL = """
    INSERT INTO  propietario(tipo_persona, tipo_documento, numero_documento,
        nombres_o_razon_social, telefono, correo, estado) -- Agregada la 's'
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String SELECT_BY_ID_SQL = "SELECT * FROM propietario where id_propietario = ?";
    private static final String SELECT_ALL_SQL = "SELECT * FROM propietario ORDER BY id_propietario";

    private static final String UPDATE_SQL = """
        UPDATE propietario 
        SET tipo_persona = ?, tipo_documento = ?, numero_documento = ?, 
            nombres_o_razon_social = ?, telefono = ?, correo = ?, estado = ? 
        WHERE id_propietario = ?
        """;

    private static final String DELETE_SQL = """
            UPDATE propietario
            SET estado = 'INACTIVO'
            WHERE id_propietario = ?
            """;

    @Override
    public Long crear(Propietario propietario) {
        validarPropietario(propietario, false);

        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, propietario.getTipoPersona().name());
            ps.setString(2,propietario.getTipoDocumento());
            ps.setString(3,propietario.getNumeroDocumento());
            ps.setString(4,propietario.getNombresORazonSocial());
            ps.setString(5,propietario.getTelefono());
            ps.setString(6,propietario.getCorreo());
            ps.setString(7,propietario.getEstado().name());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()){
                if(rs.next()){
                    long id = rs.getLong(1);
                    propietario.setIdPropietario(id);
                    return id;
                }
            }
            throw new DAOException("Error con el ID de Propietario");
        } catch (SQLException e) {
            throw new DAOException("Error al crear propietario.", e);
        }
    }
    private Propietario mapRow(ResultSet rs) throws SQLException {
        Propietario p = new Propietario();
        p.setIdPropietario(rs.getLong("id_propietario"));
        p.setTipoPersona(TipoPersona.valueOf(rs.getString("tipo_persona")));
        p.setTipoDocumento(rs.getString("tipo_documento"));
        p.setNumeroDocumento(rs.getString("numero_documento"));
        p.setNombresORazonSocial(rs.getString("nombres_o_razon_social"));
        p.setTelefono(rs.getString("telefono"));
        p.setCorreo(rs.getString("correo"));
        p.setEstado(EstadoActivoInactivo.valueOf(rs.getString("estado")));
        Timestamp fechaCreacion = rs.getTimestamp("fecha_creacion");
        if (fechaCreacion != null) {
            p.setFechaCreacion(fechaCreacion.toLocalDateTime());
        }
        Timestamp fechaActualizacion = rs.getTimestamp("fecha_actualizacion");
        if (fechaActualizacion != null) {
            p.setFechaActualizacion(fechaActualizacion.toLocalDateTime());
        }
        return p;
    }

    @Override
    public Optional<Propietario> buscarPorId(Long id) {
        validarId(id);

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID_SQL)) {

            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar al propietario " + id + ".", e);
        }
    }

    @Override
    public List<Propietario> listarTodos() {
        List<Propietario> locales = new ArrayList<>();

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                locales.add(mapRow(resultSet));
            }

            return locales;
        } catch (SQLException e) {
            throw new DAOException("Error al listar los propietarios.", e);
        }
    }

    @Override
    public boolean actualizar(Propietario propietario) {
        validarPropietario(propietario, true);

        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {

            ps.setString(1, propietario.getTipoPersona().name());
            ps.setString(2, propietario.getTipoDocumento());
            ps.setString(3, propietario.getNumeroDocumento());
            ps.setString(4, propietario.getNombresORazonSocial());
            ps.setString(5, propietario.getTelefono());
            ps.setString(6, propietario.getCorreo());
            ps.setString(7, propietario.getEstado().name());
            ps.setLong(8, propietario.getIdPropietario());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar propietario con ID: " + propietario.getIdPropietario(), e);
        }
    }

    @Override
    public boolean eliminar(Long id) {
        validarId(id);

        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {

            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al eliminar propietario con ID: " + id, e);
        }
    }

    private void validarPropietario(Propietario propietario, boolean requiereId) {
        if (propietario == null) {
            throw new IllegalArgumentException("El propietario no puede ser null.");
        }
        if (requiereId) {
            validarId(propietario.getIdPropietario());
        }
        if (propietario.getTipoPersona() == null) {
            throw new IllegalArgumentException("El tipo de persona es obligatorio.");
        }
        if (propietario.getTipoDocumento() == null || propietario.getTipoDocumento().isBlank()) {
            throw new IllegalArgumentException("El tipo de documento es obligatorio.");
        }
        if (propietario.getNumeroDocumento() == null || propietario.getNumeroDocumento().isBlank()) {
            throw new IllegalArgumentException("El numero de documento es obligatorio.");
        }
        if (propietario.getNombresORazonSocial() == null || propietario.getNombresORazonSocial().isBlank()) {
            throw new IllegalArgumentException("Los nombres o razon social son obligatorios.");
        }
        if (propietario.getEstado() == null) {
            throw new IllegalArgumentException("El estado del propietario es obligatorio.");
        }
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El id debe ser mayor que cero.");
        }
    }
}
