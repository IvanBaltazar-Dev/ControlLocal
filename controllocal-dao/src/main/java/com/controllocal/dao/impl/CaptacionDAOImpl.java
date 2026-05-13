package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.DAOException;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.EstadoCaptacion;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CaptacionDAOImpl implements CaptacionDAO {

    private static final String INSERT_SQL = """
            INSERT INTO captacion (
                codigo_captacion,
                fecha_captacion,
                fecha_inicio_vigencia,
                fecha_fin_vigencia,
                comision_pactada,
                observaciones,
                estado,
                fecha_revision,
                observacion_revision,
                id_local,
                id_agente,
                id_broker_revisor
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID_SQL = """
            SELECT
                id_captacion,
                codigo_captacion,
                fecha_captacion,
                fecha_inicio_vigencia,
                fecha_fin_vigencia,
                comision_pactada,
                observaciones,
                estado,
                fecha_revision,
                observacion_revision,
                id_local,
                id_agente,
                id_broker_revisor,
                fecha_creacion,
                fecha_actualizacion
            FROM captacion
            WHERE id_captacion = ?
            """;

    private static final String SELECT_ALL_SQL = """
            SELECT
                id_captacion,
                codigo_captacion,
                fecha_captacion,
                fecha_inicio_vigencia,
                fecha_fin_vigencia,
                comision_pactada,
                observaciones,
                estado,
                fecha_revision,
                observacion_revision,
                id_local,
                id_agente,
                id_broker_revisor,
                fecha_creacion,
                fecha_actualizacion
            FROM captacion
            ORDER BY id_captacion
            """;

    private static final String UPDATE_SQL = """
            UPDATE captacion
            SET codigo_captacion = ?,
                fecha_captacion = ?,
                fecha_inicio_vigencia = ?,
                fecha_fin_vigencia = ?,
                comision_pactada = ?,
                observaciones = ?,
                estado = ?,
                fecha_revision = ?,
                observacion_revision = ?,
                id_local = ?,
                id_agente = ?,
                id_broker_revisor = ?
            WHERE id_captacion = ?
            """;

    private static final String DELETE_SQL = """
            UPDATE captacion
            SET estado = 'CERRADA',
                fecha_fin_vigencia = COALESCE(fecha_fin_vigencia, CURRENT_DATE),
                fecha_revision = COALESCE(fecha_revision, CURRENT_TIMESTAMP)
            WHERE id_captacion = ?
            """;

    @Override
    public Long crear(Captacion captacion) {
        validarCaptacionParaPersistencia(captacion, false);

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, captacion.getCodigoCaptacion());
            statement.setDate(2, Date.valueOf(captacion.getFechaCaptacion()));
            setDate(statement, 3, captacion.getFechaInicioVigencia());
            setDate(statement, 4, captacion.getFechaFinVigencia());
            statement.setBigDecimal(5, captacion.getComisionPactada());
            statement.setString(6, captacion.getObservaciones());
            statement.setString(7, captacion.getEstado().name());
            setTimestamp(statement, 8, captacion.getFechaRevision());
            statement.setString(9, captacion.getObservacionRevision());
            statement.setLong(10, captacion.getLocalComercial().getIdLocal());
            statement.setLong(11, captacion.getAgenteResponsable().getIdAgente());
            setLong(statement, 12, captacion.getBrokerRevisor() != null ? captacion.getBrokerRevisor().getIdBroker() : null);

            if (statement.executeUpdate() == 0) {
                throw new DAOException("No se pudo insertar la captacion.");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long idGenerado = generatedKeys.getLong(1);
                    captacion.setIdCaptacion(idGenerado);
                    return idGenerado;
                }
            }

            throw new DAOException("La insercion no devolvio el id generado de la captacion.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear la captacion con codigo " + captacion.getCodigoCaptacion() + ".", e);
        }
    }

    @Override
    public Optional<Captacion> buscarPorId(Long id) {
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
            throw new DAOException("Error al buscar la captacion con id " + id + ".", e);
        }
    }

    @Override
    public List<Captacion> listarTodos() {
        List<Captacion> captaciones = new ArrayList<>();

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                captaciones.add(mapRow(resultSet));
            }

            return captaciones;
        } catch (SQLException e) {
            throw new DAOException("Error al listar las captaciones.", e);
        }
    }

    @Override
    public boolean actualizar(Captacion captacion) {
        validarCaptacionParaPersistencia(captacion, true);

        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {

            statement.setString(1, captacion.getCodigoCaptacion());
            statement.setDate(2, Date.valueOf(captacion.getFechaCaptacion()));
            setDate(statement, 3, captacion.getFechaInicioVigencia());
            setDate(statement, 4, captacion.getFechaFinVigencia());
            statement.setBigDecimal(5, captacion.getComisionPactada());
            statement.setString(6, captacion.getObservaciones());
            statement.setString(7, captacion.getEstado().name());
            setTimestamp(statement, 8, captacion.getFechaRevision());
            statement.setString(9, captacion.getObservacionRevision());
            statement.setLong(10, captacion.getLocalComercial().getIdLocal());
            statement.setLong(11, captacion.getAgenteResponsable().getIdAgente());
            setLong(statement, 12, captacion.getBrokerRevisor() != null ? captacion.getBrokerRevisor().getIdBroker() : null);
            statement.setLong(13, captacion.getIdCaptacion());

            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar la captacion con id " + captacion.getIdCaptacion() + ".", e);
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
            throw new DAOException("Error al cerrar la captacion con id " + id + ".", e);
        }
    }

    private Captacion mapRow(ResultSet rs) throws SQLException {
        LocalComercial local = new LocalComercial();
        local.setIdLocal(rs.getLong("id_local"));

        AgenteInmobiliario agente = new AgenteInmobiliario();
        long idAgente = rs.getLong("id_agente");
        agente.setIdAgente(idAgente);
        agente.setIdUsuarioInterno(idAgente);

        Long idBroker = rs.getObject("id_broker_revisor", Long.class);
        Broker broker = null;
        if (idBroker != null) {
            broker = new Broker();
            broker.setIdBroker(idBroker);
            broker.setIdUsuarioInterno(idBroker);
        }

        Captacion captacion = new Captacion();
        captacion.setIdCaptacion(rs.getLong("id_captacion"));
        captacion.setCodigoCaptacion(rs.getString("codigo_captacion"));
        captacion.setFechaCaptacion(rs.getDate("fecha_captacion").toLocalDate());

        Date fechaInicio = rs.getDate("fecha_inicio_vigencia");
        if (fechaInicio != null) {
            captacion.setFechaInicioVigencia(fechaInicio.toLocalDate());
        }

        Date fechaFin = rs.getDate("fecha_fin_vigencia");
        if (fechaFin != null) {
            captacion.setFechaFinVigencia(fechaFin.toLocalDate());
        }

        captacion.setComisionPactada(rs.getBigDecimal("comision_pactada"));
        captacion.setObservaciones(rs.getString("observaciones"));
        captacion.setEstado(EstadoCaptacion.valueOf(rs.getString("estado")));

        Timestamp fechaRevision = rs.getTimestamp("fecha_revision");
        if (fechaRevision != null) {
            captacion.setFechaRevision(fechaRevision.toLocalDateTime());
        }

        captacion.setObservacionRevision(rs.getString("observacion_revision"));
        captacion.setLocalComercial(local);
        captacion.setAgenteResponsable(agente);
        captacion.setBrokerRevisor(broker);

        Timestamp fechaCreacion = rs.getTimestamp("fecha_creacion");
        if (fechaCreacion != null) {
            captacion.setFechaCreacion(fechaCreacion.toLocalDateTime());
        }

        Timestamp fechaActualizacion = rs.getTimestamp("fecha_actualizacion");
        if (fechaActualizacion != null) {
            captacion.setFechaActualizacion(fechaActualizacion.toLocalDateTime());
        }

        return captacion;
    }

    private void validarCaptacionParaPersistencia(Captacion captacion, boolean requiereId) {
        if (captacion == null) {
            throw new IllegalArgumentException("La captacion no puede ser null.");
        }
        if (requiereId) {
            validarId(captacion.getIdCaptacion());
        }
        if (captacion.getCodigoCaptacion() == null || captacion.getCodigoCaptacion().isBlank()) {
            throw new IllegalArgumentException("El codigo de captacion es obligatorio.");
        }
        if (captacion.getFechaCaptacion() == null) {
            throw new IllegalArgumentException("La fecha de captacion es obligatoria.");
        }
        if (captacion.getComisionPactada() == null) {
            throw new IllegalArgumentException("La comision pactada es obligatoria.");
        }
        if (captacion.getComisionPactada().signum() < 0) {
            throw new IllegalArgumentException("La comision pactada no puede ser negativa.");
        }
        if (captacion.getEstado() == null) {
            throw new IllegalArgumentException("El estado de la captacion es obligatorio.");
        }
        if (captacion.getLocalComercial() == null
                || captacion.getLocalComercial().getIdLocal() == null
                || captacion.getLocalComercial().getIdLocal() <= 0) {
            throw new IllegalArgumentException("El local comercial asociado es obligatorio.");
        }
        if (captacion.getAgenteResponsable() == null
                || captacion.getAgenteResponsable().getIdAgente() == null
                || captacion.getAgenteResponsable().getIdAgente() <= 0) {
            throw new IllegalArgumentException("El agente responsable es obligatorio.");
        }
        if (captacion.getFechaInicioVigencia() != null
                && captacion.getFechaFinVigencia() != null
                && captacion.getFechaFinVigencia().isBefore(captacion.getFechaInicioVigencia())) {
            throw new IllegalArgumentException("La fecha fin no puede ser anterior a la fecha inicio.");
        }
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El id debe ser mayor que cero.");
        }
    }

    private void setDate(PreparedStatement statement, int parameterIndex, LocalDate value) throws SQLException {
        if (value != null) {
            statement.setDate(parameterIndex, Date.valueOf(value));
        } else {
            statement.setNull(parameterIndex, Types.DATE);
        }
    }

    private void setTimestamp(PreparedStatement statement, int parameterIndex, LocalDateTime value) throws SQLException {
        if (value != null) {
            statement.setTimestamp(parameterIndex, Timestamp.valueOf(value));
        } else {
            statement.setNull(parameterIndex, Types.TIMESTAMP);
        }
    }

    private void setLong(PreparedStatement statement, int parameterIndex, Long value) throws SQLException {
        if (value != null) {
            statement.setLong(parameterIndex, value);
        } else {
            statement.setNull(parameterIndex, Types.BIGINT);
        }
    }
}
