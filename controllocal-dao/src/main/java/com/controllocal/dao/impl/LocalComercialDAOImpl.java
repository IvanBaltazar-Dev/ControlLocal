package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.LocalComercialDAO;
import com.controllocal.model.inmueble.EstadoLocalComercial;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.Propietario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementacion JDBC de LocalComercialDAO usando PreparedStatement.
 */
public class LocalComercialDAOImpl implements LocalComercialDAO {

    private static final String INSERT_SQL = """
            INSERT INTO local_comercial (
                codigo_local,
                direccion,
                distrito,
                metraje,
                precio_referencial,
                rubro_permitido,
                descripcion,
                estado,
                id_propietario
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID_SQL = """
            SELECT
                id_local,
                codigo_local,
                direccion,
                distrito,
                metraje,
                precio_referencial,
                rubro_permitido,
                descripcion,
                estado,
                id_propietario,
                fecha_registro,
                fecha_actualizacion
            FROM local_comercial
            WHERE id_local = ?
            """;

    private static final String SELECT_ALL_SQL = """
            SELECT
                id_local,
                codigo_local,
                direccion,
                distrito,
                metraje,
                precio_referencial,
                rubro_permitido,
                descripcion,
                estado,
                id_propietario,
                fecha_registro,
                fecha_actualizacion
            FROM local_comercial
            ORDER BY id_local
            """;

    private static final String UPDATE_SQL = """
            UPDATE local_comercial
            SET codigo_local = ?,
                direccion = ?,
                distrito = ?,
                metraje = ?,
                precio_referencial = ?,
                rubro_permitido = ?,
                descripcion = ?,
                estado = ?,
                id_propietario = ?
            WHERE id_local = ?
            """;

    private static final String DELETE_SQL = """
            UPDATE local_comercial
            SET estado = 'INACTIVO'
            WHERE id_local = ?
            """;

    @Override
    public Long crear(LocalComercial local) {
        validarLocalParaPersistencia(local, false);

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, local.getCodigoLocal());
            statement.setString(2, local.getDireccion());
            statement.setString(3, local.getDistrito());
            statement.setBigDecimal(4, local.getMetraje());
            statement.setBigDecimal(5, local.getPrecioReferencial());
            statement.setString(6, local.getRubroPermitido());
            statement.setString(7, local.getDescripcion());
            statement.setString(8, local.getEstado().name());
            statement.setLong(9, local.getIdPropietario());

            int affectedRows = statement.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("No se pudo insertar el local comercial.");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long idGenerado = generatedKeys.getLong(1);
                    local.setIdLocal(idGenerado);
                    return idGenerado;
                }
            }

            throw new DAOException("La insercion no devolvio el id generado del local comercial.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear el local comercial con codigo " + local.getCodigoLocal() + ".", e);
        }
    }

    @Override
    public Optional<LocalComercial> buscarPorId(Long id) {
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
            throw new DAOException("Error al buscar el local comercial con id " + id + ".", e);
        }
    }

    @Override
    public List<LocalComercial> listarTodos() {
        List<LocalComercial> locales = new ArrayList<>();

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                locales.add(mapRow(resultSet));
            }

            return locales;
        } catch (SQLException e) {
            throw new DAOException("Error al listar los locales comerciales.", e);
        }
    }

    @Override
    public boolean actualizar(LocalComercial local) {
        validarLocalParaPersistencia(local, true);

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {

            statement.setString(1, local.getCodigoLocal());
            statement.setString(2, local.getDireccion());
            statement.setString(3, local.getDistrito());
            statement.setBigDecimal(4, local.getMetraje());
            statement.setBigDecimal(5, local.getPrecioReferencial());
            statement.setString(6, local.getRubroPermitido());
            statement.setString(7, local.getDescripcion());
            statement.setString(8, local.getEstado().name());
            statement.setLong(9, local.getIdPropietario());
            statement.setLong(10, local.getIdLocal());

            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar el local comercial con id " + local.getIdLocal() + ".", e);
        }
    }

    @Override
    public boolean eliminar(Long id) {
        validarId(id);

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {

            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al eliminar el local comercial con id " + id + ".", e);
        }
    }

    private LocalComercial mapRow(ResultSet rs) throws SQLException {
        Timestamp fechaRegistro = rs.getTimestamp("fecha_registro");
        Timestamp fechaActualizacion = rs.getTimestamp("fecha_actualizacion");
        long idPropietario = rs.getLong("id_propietario");

        LocalComercial local = new LocalComercial(
                rs.getLong("id_local"),
                rs.getString("codigo_local"),
                rs.getString("direccion"),
                rs.getString("distrito"),
                rs.getBigDecimal("metraje"),
                rs.getBigDecimal("precio_referencial"),
                rs.getString("rubro_permitido"),
                rs.getString("descripcion"),
                EstadoLocalComercial.valueOf(rs.getString("estado")),
                idPropietario,
                fechaRegistro != null ? fechaRegistro.toLocalDateTime() : null,
                fechaActualizacion != null ? fechaActualizacion.toLocalDateTime() : null
        );

        Propietario propietario = new Propietario();
        propietario.setIdPropietario(idPropietario);
        local.setPropietario(propietario);
        return local;
    }

    private void validarLocalParaPersistencia(LocalComercial local, boolean requiereId) {
        if (local == null) {
            throw new IllegalArgumentException("El local comercial no puede ser null.");
        }
        if (requiereId) {
            validarId(local.getIdLocal());
        }
        if (local.getCodigoLocal() == null || local.getCodigoLocal().isBlank()) {
            throw new IllegalArgumentException("El codigo del local es obligatorio.");
        }
        if (local.getDireccion() == null || local.getDireccion().isBlank()) {
            throw new IllegalArgumentException("La direccion del local es obligatoria.");
        }
        if (local.getDistrito() == null || local.getDistrito().isBlank()) {
            throw new IllegalArgumentException("El distrito del local es obligatorio.");
        }
        if (local.getMetraje() == null) {
            throw new IllegalArgumentException("El metraje del local es obligatorio.");
        }
        if (local.getMetraje().signum() <= 0) {
            throw new IllegalArgumentException("El metraje del local debe ser mayor que cero.");
        }
        if (local.getPrecioReferencial() == null) {
            throw new IllegalArgumentException("El precio referencial del local es obligatorio.");
        }
        if (local.getPrecioReferencial().signum() < 0) {
            throw new IllegalArgumentException("El precio referencial del local no puede ser negativo.");
        }
        if (local.getRubroPermitido() == null || local.getRubroPermitido().isBlank()) {
            throw new IllegalArgumentException("El rubro permitido es obligatorio.");
        }
        if (local.getEstado() == null) {
            throw new IllegalArgumentException("El estado del local es obligatorio.");
        }
        validarId(local.getIdPropietario());
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El id debe ser mayor que cero.");
        }
    }
}
