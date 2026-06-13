package com.controllocal.dao.impl;

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

import com.controllocal.config.DBManager;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.DAOException;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.enums.EstadoCaptacion;
import com.controllocal.model.comercial.enums.OperacionRequerimiento;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;

public class CaptacionDAOImpl implements CaptacionDAO {

    private static final String INSERT_SQL = """
            INSERT INTO captacion (
                codigo_captacion, fecha_captacion, fecha_inicio_vigencia, fecha_fin_vigencia,
                comision_pactada, observaciones, estado, fecha_revision,
                observacion_revision, id_local, id_agente, id_broker_revisor,
                motivo_operacion, urgencia, exclusividad
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT c.id_captacion, c.codigo_captacion, c.fecha_captacion, c.fecha_inicio_vigencia,
                   c.fecha_fin_vigencia, c.comision_pactada, c.observaciones, c.estado, c.fecha_revision,
                   c.observacion_revision, c.id_local, c.id_agente, c.id_broker_revisor,
                   c.motivo_operacion, c.urgencia, c.exclusividad,
                   c.fecha_creacion, c.fecha_actualizacion,
                   l.codigo_local, l.direccion AS local_direccion, l.distrito AS local_distrito,
                   l.metraje AS local_metraje, l.precio_referencial AS local_precio,
                   l.rubro_permitido AS local_rubro, l.descripcion AS local_descripcion,
                   l.estado AS local_estado, l.id_propietario,
                   pp.nombres_o_razon_social AS propietario_nombre,
                   ap.nombres_o_razon_social AS agente_nombre
            FROM captacion c
            INNER JOIN local_comercial l ON l.id_local = c.id_local
            INNER JOIN propietario p ON p.id_propietario = l.id_propietario
            INNER JOIN persona pp ON pp.id_persona = p.id_persona
            INNER JOIN agente_inmobiliario a ON a.id_agente = c.id_agente
            INNER JOIN usuario_interno au ON au.id_usuario = a.id_usuario
            INNER JOIN persona ap ON ap.id_persona = au.id_persona
            """;

    private static final String SELECT_BY_ID_SQL =
            SELECT_SQL + " WHERE c.id_captacion = ?";

    private static final String SELECT_ALL_SQL =
            SELECT_SQL + " ORDER BY c.id_captacion";

    private static final String SELECT_PAGE_SQL =
            SELECT_SQL + " ORDER BY c.id_captacion LIMIT ? OFFSET ?";

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM captacion";

    private static final String UPDATE_SQL = """
            UPDATE captacion
            SET codigo_captacion = ?, fecha_captacion = ?, fecha_inicio_vigencia = ?,
                fecha_fin_vigencia = ?, comision_pactada = ?, observaciones = ?,
                estado = ?, fecha_revision = ?, observacion_revision = ?,
                id_local = ?, id_agente = ?, id_broker_revisor = ?,
                motivo_operacion = ?, urgencia = ?, exclusividad = ?
            WHERE id_captacion = ?
            """;

    private static final String DELETE_SQL = """
            UPDATE captacion
            SET estado = 'C',
                fecha_fin_vigencia = COALESCE(fecha_fin_vigencia, CURRENT_DATE),
                fecha_revision = COALESCE(fecha_revision, CURRENT_TIMESTAMP)
            WHERE id_captacion = ?
            """;

    @Override
    public Long crear(Captacion captacion) {
        validarCaptacionParaPersistencia(captacion, false);
        try (Connection connection = DBManager.getConnection()) {
            return crear(captacion, connection);
        } catch (SQLException e) {
            throw new DAOException("Error al crear la captacion con codigo " + captacion.getCodigoCaptacion() + ".", e);
        }
    }

    public Long crear(Captacion captacion, Connection connection) throws SQLException {
        validarCaptacionParaPersistencia(captacion, false);
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, captacion.getCodigoCaptacion());
            statement.setDate(2, Date.valueOf(captacion.getFechaCaptacion()));
            setDate(statement, 3, captacion.getFechaInicioVigencia());
            setDate(statement, 4, captacion.getFechaFinVigencia());
            statement.setBigDecimal(5, captacion.getComisionPactada());
            statement.setString(6, captacion.getObservaciones());
            JdbcSupport.setEnum(statement, 7, captacion.getEstado());
            setTimestamp(statement, 8, captacion.getFechaRevision());
            statement.setString(9, captacion.getObservacionRevision());
            statement.setLong(10, captacion.getLocalComercial().getIdLocal());
            statement.setLong(11, captacion.getAgenteResponsable().getIdAgente());
            setLong(statement, 12, captacion.getBrokerRevisor() != null ? captacion.getBrokerRevisor().getIdBroker() : null);
            JdbcSupport.setEnum(statement, 13, captacion.getMotivoOperacion());
            JdbcSupport.setInteger(statement, 14, captacion.getUrgencia());
            JdbcSupport.setBoolean(statement, 15, captacion.getExclusividad());

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
            throw new DAOException("La insercion no devolvio el id generado.");
        }
    }

    @Override
    public Optional<Captacion> buscarPorId(Long id) {
        validarId(id);
        try (Connection connection = DBManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID_SQL)) {
                statement.setLong(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(mapRow(resultSet));
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar la captacion con id " + id + ".", e);
        }
    }

    @Override
    public List<Captacion> listarTodos() {
        List<Captacion> captaciones = new ArrayList<>();
        try (Connection connection = DBManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SQL);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    captaciones.add(mapRow(resultSet));
                }
                return captaciones;
            }
        } catch (SQLException e) {
            throw new DAOException("Error al listar las captaciones.", e);
        }
    }

    @Override
    public List<Captacion> listarPagina(int limite, int desplazamiento) {
        List<Captacion> captaciones = new ArrayList<>();
        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_PAGE_SQL)) {
            statement.setInt(1, limite);
            statement.setInt(2, desplazamiento);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    captaciones.add(mapRow(resultSet));
                }
            }
            return captaciones;
        } catch (SQLException e) {
            throw new DAOException("Error al listar la pagina de captaciones.", e);
        }
    }

    @Override
    public long contar() {
        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DAOException("Error al contar las captaciones.", e);
        }
    }

    @Override
    public boolean actualizar(Captacion captacion) {
        validarCaptacionParaPersistencia(captacion, true);
        try (Connection connection = DBManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
                statement.setString(1, captacion.getCodigoCaptacion());
                statement.setDate(2, Date.valueOf(captacion.getFechaCaptacion()));
                setDate(statement, 3, captacion.getFechaInicioVigencia());
                setDate(statement, 4, captacion.getFechaFinVigencia());
                statement.setBigDecimal(5, captacion.getComisionPactada());
                statement.setString(6, captacion.getObservaciones());
                JdbcSupport.setEnum(statement, 7, captacion.getEstado());
                setTimestamp(statement, 8, captacion.getFechaRevision());
                statement.setString(9, captacion.getObservacionRevision());
                statement.setLong(10, captacion.getLocalComercial().getIdLocal());
                statement.setLong(11, captacion.getAgenteResponsable().getIdAgente());
                setLong(statement, 12, captacion.getBrokerRevisor() != null ? captacion.getBrokerRevisor().getIdBroker() : null);
                JdbcSupport.setEnum(statement, 13, captacion.getMotivoOperacion());
                JdbcSupport.setInteger(statement, 14, captacion.getUrgencia());
                JdbcSupport.setBoolean(statement, 15, captacion.getExclusividad());
                statement.setLong(16, captacion.getIdCaptacion());

                return statement.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar la captacion con id " + captacion.getIdCaptacion() + ".", e);
        }
    }

    @Override
    public boolean eliminar(Long id) {
        validarId(id);
        try (Connection connection = DBManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
                statement.setLong(1, id);
                return statement.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new DAOException("Error al cerrar la captacion con id " + id + ".", e);
        }
    }

    // --- Metodos privados de apoyo (mapRow, validaciones, setters) ---

    private Captacion mapRow(ResultSet rs) throws SQLException {
        LocalComercial local = new LocalComercial();
        local.setIdLocal(rs.getLong("id_local"));
        local.setCodigoLocal(rs.getString("codigo_local"));
        local.setDireccion(rs.getString("local_direccion"));
        local.setDistrito(rs.getString("local_distrito"));
        local.setMetraje(rs.getBigDecimal("local_metraje"));
        local.setPrecioReferencial(rs.getBigDecimal("local_precio"));
        local.setRubroPermitido(rs.getString("local_rubro"));
        local.setDescripcion(rs.getString("local_descripcion"));
        local.setEstado(JdbcSupport.getEnum(rs, "local_estado",
                com.controllocal.model.inmueble.enums.EstadoLocalComercial.class));

        Persona personaPropietario = new Persona();
        personaPropietario.setNombresORazonSocial(rs.getString("propietario_nombre"));
        Propietario propietario = new Propietario();
        propietario.setIdPropietario(rs.getLong("id_propietario"));
        propietario.setPersona(personaPropietario);
        local.setPropietario(propietario);

        AgenteInmobiliario agente = new AgenteInmobiliario();
        long idAgente = rs.getLong("id_agente");
        agente.setIdAgente(idAgente);
        Persona personaAgente = new Persona();
        personaAgente.setNombresORazonSocial(rs.getString("agente_nombre"));
        agente.setPersona(personaAgente);

        Long idBroker = rs.getObject("id_broker_revisor", Long.class);
        Broker broker = (idBroker != null) ? new Broker(idBroker) : null;

        Captacion captacion = new Captacion();
        captacion.setIdCaptacion(rs.getLong("id_captacion"));
        captacion.setCodigoCaptacion(rs.getString("codigo_captacion"));
        captacion.setFechaCaptacion(rs.getDate("fecha_captacion").toLocalDate());

        Date fIni = rs.getDate("fecha_inicio_vigencia");
        if (fIni != null) captacion.setFechaInicioVigencia(fIni.toLocalDate());

        Date fFin = rs.getDate("fecha_fin_vigencia");
        if (fFin != null) captacion.setFechaFinVigencia(fFin.toLocalDate());

        captacion.setComisionPactada(rs.getBigDecimal("comision_pactada"));
        captacion.setObservaciones(rs.getString("observaciones"));
        captacion.setEstado(JdbcSupport.getEnum(rs, "estado", EstadoCaptacion.class));

        Timestamp fRev = rs.getTimestamp("fecha_revision");
        if (fRev != null) captacion.setFechaRevision(fRev.toLocalDateTime());

        captacion.setObservacionRevision(rs.getString("observacion_revision"));
        captacion.setMotivoOperacion(JdbcSupport.getNullableEnum(rs, "motivo_operacion", OperacionRequerimiento.class));
        captacion.setUrgencia(JdbcSupport.getNullableInt(rs, "urgencia"));
        captacion.setExclusividad(JdbcSupport.getNullableBoolean(rs, "exclusividad"));
        captacion.setLocalComercial(local);
        captacion.setAgenteResponsable(agente);
        captacion.setBrokerRevisor(broker);

        return captacion;
    }

    private void validarCaptacionParaPersistencia(Captacion captacion, boolean requiereId) {
        if (captacion == null) throw new IllegalArgumentException("La captacion no puede ser null.");
        if (requiereId) validarId(captacion.getIdCaptacion());
        if (captacion.getCodigoCaptacion() == null || captacion.getCodigoCaptacion().isBlank())
            throw new IllegalArgumentException("Codigo obligatorio.");
        if (captacion.getLocalComercial() == null || captacion.getLocalComercial().getIdLocal() <= 0)
            throw new IllegalArgumentException("Local asociado obligatorio.");
        if (captacion.getAgenteResponsable() == null || captacion.getAgenteResponsable().getIdAgente() <= 0)
            throw new IllegalArgumentException("Agente responsable obligatorio.");
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("El id debe ser mayor que cero.");
    }

    private void setDate(PreparedStatement statement, int parameterIndex, LocalDate value) throws SQLException {
        if (value != null) statement.setDate(parameterIndex, Date.valueOf(value));
        else statement.setNull(parameterIndex, Types.DATE);
    }

    private void setTimestamp(PreparedStatement statement, int parameterIndex, LocalDateTime value) throws SQLException {
        if (value != null) statement.setTimestamp(parameterIndex, Timestamp.valueOf(value));
        else statement.setNull(parameterIndex, Types.TIMESTAMP);
    }

    private void setLong(PreparedStatement statement, int parameterIndex, Long value) throws SQLException {
        if (value != null) statement.setLong(parameterIndex, value);
        else statement.setNull(parameterIndex, Types.BIGINT);
    }
}
