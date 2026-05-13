package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.DAOException;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.RolUsuarioInterno;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BrokerDAOImpl implements BrokerDAO {

    private static final String INSERT_SQL = """
            INSERT INTO broker (id_usuario, codigo_broker, fecha_designacion, es_administrador)
            VALUES (?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT b.id_broker, b.codigo_broker, b.fecha_designacion, b.es_administrador,
                   u.id_usuario, u.nombre_usuario, u.contrasena_hash, u.estado_administrativo, u.rol,
                   u.fecha_creacion AS usuario_fecha_creacion,
                   u.fecha_actualizacion AS usuario_fecha_actualizacion,
                   p.id_persona, p.tipo_persona, p.tipo_documento, p.numero_documento,
                   p.nombres_o_razon_social, p.telefono, p.correo, p.estado,
                   p.fecha_creacion, p.fecha_actualizacion
            FROM broker b
            INNER JOIN usuario_interno u ON b.id_usuario = u.id_usuario
            INNER JOIN persona p ON u.id_persona = p.id_persona
            """;

    private static final String UPDATE_SQL = """
            UPDATE broker
            SET id_usuario = ?, codigo_broker = ?, fecha_designacion = ?, es_administrador = ?
            WHERE id_broker = ?
            """;

    private static final String DELETE_SQL = """
            UPDATE usuario_interno u
            INNER JOIN broker b ON b.id_usuario = u.id_usuario
            SET u.estado_administrativo = 'INACTIVO'
            WHERE b.id_broker = ?
            """;

    @Override
    public Long crear(Broker broker) {
        validar(broker, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, broker.getIdUsuarioInterno());
            ps.setString(2, broker.getCodigoBroker());
            ps.setDate(3, Date.valueOf(broker.getFechaDesignacion()));
            ps.setBoolean(4, broker.isEsAdministrador());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    broker.setIdBroker(id);
                    broker.setRol(RolUsuarioInterno.BROKER);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de broker.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear broker.", e);
        }
    }

    @Override
    public Optional<Broker> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE b.id_broker = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar broker con id " + id + ".", e);
        }
    }

    @Override
    public List<Broker> listarTodos() {
        List<Broker> brokers = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY b.id_broker");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                brokers.add(mapRow(rs));
            }
            return brokers;
        } catch (SQLException e) {
            throw new DAOException("Error al listar brokers.", e);
        }
    }

    @Override
    public boolean actualizar(Broker broker) {
        validar(broker, true);
        new UsuarioInternoDAOImpl().actualizar(broker);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setLong(1, broker.getIdUsuarioInterno());
            ps.setString(2, broker.getCodigoBroker());
            ps.setDate(3, Date.valueOf(broker.getFechaDesignacion()));
            ps.setBoolean(4, broker.isEsAdministrador());
            ps.setLong(5, broker.getIdBroker());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar broker con id " + broker.getIdBroker() + ".", e);
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
            throw new DAOException("Error al eliminar broker con id " + id + ".", e);
        }
    }

    private Broker mapRow(ResultSet rs) throws SQLException {
        Broker broker = new Broker();
        UsuarioInternoDAOImpl.mapUsuario(rs, broker);
        broker.setIdBroker(rs.getLong("id_broker"));
        broker.setCodigoBroker(rs.getString("codigo_broker"));
        broker.setFechaDesignacion(JdbcSupport.toLocalDate(rs.getDate("fecha_designacion")));
        broker.setEsAdministrador(rs.getBoolean("es_administrador"));
        return broker;
    }

    private void validar(Broker broker, boolean requiereId) {
        if (broker == null) {
            throw new IllegalArgumentException("El broker no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(broker.getIdBroker());
        }
        JdbcSupport.validarId(broker.getIdUsuarioInterno());
        if (broker.getCodigoBroker() == null || broker.getCodigoBroker().isBlank()
                || broker.getFechaDesignacion() == null) {
            throw new IllegalArgumentException("El broker tiene campos obligatorios incompletos.");
        }
    }
}
