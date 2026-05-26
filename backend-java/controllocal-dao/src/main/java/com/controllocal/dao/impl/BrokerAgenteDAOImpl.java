package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.controllocal.config.DBManager;
import com.controllocal.dao.BrokerAgenteDAO;
import com.controllocal.dao.DAOException;
import com.controllocal.model.persona.enums.EstadoActivoInactivo;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.BrokerAgente;

public class BrokerAgenteDAOImpl implements BrokerAgenteDAO {

    private static final String INSERT_SQL = """
            INSERT INTO broker_agente (
                id_broker, id_agente, fecha_asignacion, fecha_fin, estado
            ) VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT id_broker_agente, id_broker, id_agente, fecha_asignacion, fecha_fin, estado
            FROM broker_agente
            """;

    private static final String UPDATE_SQL = """
            UPDATE broker_agente
            SET id_broker = ?, id_agente = ?, fecha_asignacion = ?, fecha_fin = ?, estado = ?
            WHERE id_broker_agente = ?
            """;

    private static final String DELETE_SQL = """
            UPDATE broker_agente
            SET estado = 'I',
                fecha_fin = COALESCE(fecha_fin, CURRENT_DATE)
            WHERE id_broker_agente = ?
            """;

    @Override
    public Long crear(BrokerAgente brokerAgente) {
        validar(brokerAgente, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bind(brokerAgente, ps);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    brokerAgente.setIdBrokerAgente(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de asignacion broker-agente.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear asignacion broker-agente.", e);
        }
    }

    @Override
    public Optional<BrokerAgente> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_broker_agente = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar asignacion broker-agente con id " + id + ".", e);
        }
    }

    @Override
    public List<BrokerAgente> listarTodos() {
        return listar(SELECT_SQL + " ORDER BY id_broker_agente", null);
    }

    @Override
    public List<BrokerAgente> listarActivosPorBroker(Long idBroker) {
        JdbcSupport.validarId(idBroker);
        return listar(SELECT_SQL + " WHERE id_broker = ? AND estado = 'A' ORDER BY id_agente", idBroker);
    }

    @Override
    public Optional<BrokerAgente> buscarActivoPorAgente(Long idAgente) {
        JdbcSupport.validarId(idAgente);
        List<BrokerAgente> asignaciones = listar(
                SELECT_SQL + " WHERE id_agente = ? AND estado = 'A' ORDER BY id_broker_agente",
                idAgente
        );
        return asignaciones.stream().findFirst();
    }

    @Override
    public boolean existeAsignacionActiva(Long idBroker, Long idAgente) {
        JdbcSupport.validarId(idBroker);
        JdbcSupport.validarId(idAgente);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT 1
                     FROM broker_agente
                     WHERE id_broker = ? AND id_agente = ? AND estado = 'A'
                     LIMIT 1
                     """)) {
            ps.setLong(1, idBroker);
            ps.setLong(2, idAgente);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al validar asignacion activa broker-agente.", e);
        }
    }

    @Override
    public boolean actualizar(BrokerAgente brokerAgente) {
        validar(brokerAgente, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            bind(brokerAgente, ps);
            ps.setLong(6, brokerAgente.getIdBrokerAgente());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar asignacion broker-agente con id "
                    + brokerAgente.getIdBrokerAgente() + ".", e);
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
            throw new DAOException("Error al desactivar asignacion broker-agente con id " + id + ".", e);
        }
    }

    private List<BrokerAgente> listar(String sql, Long idFiltro) {
        List<BrokerAgente> asignaciones = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (idFiltro != null) {
                ps.setLong(1, idFiltro);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    asignaciones.add(mapRow(rs));
                }
            }
            return asignaciones;
        } catch (SQLException e) {
            throw new DAOException("Error al listar asignaciones broker-agente.", e);
        }
    }

    private void bind(BrokerAgente brokerAgente, PreparedStatement ps) throws SQLException {
        ps.setLong(1, brokerAgente.getIdBroker());
        ps.setLong(2, brokerAgente.getIdAgente());
        ps.setDate(3, Date.valueOf(brokerAgente.getFechaAsignacion()));
        setDate(ps, 4, brokerAgente.getFechaFin());
        JdbcSupport.setEnum(ps, 5, brokerAgente.getEstado());
    }

    private BrokerAgente mapRow(ResultSet rs) throws SQLException {
        BrokerAgente brokerAgente = new BrokerAgente();
        Broker broker = new Broker(rs.getLong("id_broker"));
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(rs.getLong("id_agente"));

        brokerAgente.setIdBrokerAgente(rs.getLong("id_broker_agente"));
        brokerAgente.setBroker(broker);
        brokerAgente.setAgente(agente);
        brokerAgente.setFechaAsignacion(JdbcSupport.toLocalDate(rs.getDate("fecha_asignacion")));
        brokerAgente.setFechaFin(JdbcSupport.toLocalDate(rs.getDate("fecha_fin")));
        brokerAgente.setEstado(JdbcSupport.getEnum(rs, "estado", EstadoActivoInactivo.class));
        return brokerAgente;
    }

    private void validar(BrokerAgente brokerAgente, boolean requiereId) {
        if (brokerAgente == null) {
            throw new IllegalArgumentException("La asignacion broker-agente no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(brokerAgente.getIdBrokerAgente());
        }
        JdbcSupport.validarId(brokerAgente.getIdBroker());
        JdbcSupport.validarId(brokerAgente.getIdAgente());
        if (brokerAgente.getFechaAsignacion() == null || brokerAgente.getEstado() == null) {
            throw new IllegalArgumentException("La asignacion broker-agente tiene campos obligatorios incompletos.");
        }
    }

    private void setDate(PreparedStatement ps, int parameterIndex, LocalDate value) throws SQLException {
        if (value != null) {
            ps.setDate(parameterIndex, Date.valueOf(value));
        } else {
            ps.setNull(parameterIndex, Types.DATE);
        }
    }
}
