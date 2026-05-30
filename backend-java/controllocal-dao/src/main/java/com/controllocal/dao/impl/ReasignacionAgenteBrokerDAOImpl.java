package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.ReasignacionAgenteBrokerDAO;
import com.controllocal.model.usuario.ReasignacionAgenteBroker;

public class ReasignacionAgenteBrokerDAOImpl implements ReasignacionAgenteBrokerDAO {

    private static final String INSERT_SQL = """
            INSERT INTO reasignacion_agente_broker (
                fecha_cambio, motivo, id_agente, id_broker_anterior, id_broker_nuevo, id_broker_administrador
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT id_reasignacion, fecha_cambio, motivo, id_agente,
                   id_broker_anterior, id_broker_nuevo, id_broker_administrador
            FROM reasignacion_agente_broker
            """;
    private static final String UPDATE_SQL = """
            UPDATE reasignacion_agente_broker
            SET fecha_cambio = ?, motivo = ?, id_agente = ?, id_broker_anterior = ?,
                id_broker_nuevo = ?, id_broker_administrador = ?
            WHERE id_reasignacion = ?
            """;
    private static final String DELETE_SQL = "DELETE FROM reasignacion_agente_broker WHERE id_reasignacion = ?";

    @Override
    public Long crear(ReasignacionAgenteBroker reasignacion) {
        validar(reasignacion, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bind(reasignacion, ps);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    reasignacion.setIdReasignacion(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de reasignacion agente-broker.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear reasignacion agente-broker.", e);
        }
    }

    @Override
    public Optional<ReasignacionAgenteBroker> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_reasignacion = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar reasignacion agente-broker con id " + id + ".", e);
        }
    }

    @Override
    public List<ReasignacionAgenteBroker> listarTodos() {
        List<ReasignacionAgenteBroker> reasignaciones = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY id_reasignacion");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                reasignaciones.add(mapRow(rs));
            }
            return reasignaciones;
        } catch (SQLException e) {
            throw new DAOException("Error al listar reasignaciones agente-broker.", e);
        }
    }

    @Override
    public boolean actualizar(ReasignacionAgenteBroker reasignacion) {
        validar(reasignacion, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            bind(reasignacion, ps);
            ps.setLong(7, reasignacion.getIdReasignacion());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar reasignacion agente-broker con id "
                    + reasignacion.getIdReasignacion() + ".", e);
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
            throw new DAOException("Error al eliminar reasignacion agente-broker con id " + id + ".", e);
        }
    }

    private void bind(ReasignacionAgenteBroker reasignacion, PreparedStatement ps) throws SQLException {
        JdbcSupport.setTimestamp(ps, 1, reasignacion.getFechaCambio());
        ps.setString(2, reasignacion.getMotivo());
        ps.setLong(3, reasignacion.getAgente().getIdAgente());
        if (reasignacion.getBrokerAnterior() != null && reasignacion.getBrokerAnterior().getIdBroker() != null) {
            ps.setLong(4, reasignacion.getBrokerAnterior().getIdBroker());
        } else {
            ps.setNull(4, Types.BIGINT);
        }
        ps.setLong(5, reasignacion.getBrokerNuevo().getIdBroker());
        ps.setLong(6, reasignacion.getBrokerAdministrador().getIdBroker());
    }

    private ReasignacionAgenteBroker mapRow(ResultSet rs) throws SQLException {
        ReasignacionAgenteBroker reasignacion = new ReasignacionAgenteBroker();
        reasignacion.setIdReasignacion(rs.getLong("id_reasignacion"));
        reasignacion.setFechaCambio(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_cambio")));
        reasignacion.setMotivo(rs.getString("motivo"));
        reasignacion.setAgente(JdbcSupport.agente(rs.getLong("id_agente")));
        long idBrokerAnterior = rs.getLong("id_broker_anterior");
        reasignacion.setBrokerAnterior(rs.wasNull() ? null : JdbcSupport.broker(idBrokerAnterior));
        reasignacion.setBrokerNuevo(JdbcSupport.broker(rs.getLong("id_broker_nuevo")));
        reasignacion.setBrokerAdministrador(JdbcSupport.broker(rs.getLong("id_broker_administrador")));
        return reasignacion;
    }

    private void validar(ReasignacionAgenteBroker reasignacion, boolean requiereId) {
        if (reasignacion == null) {
            throw new IllegalArgumentException("La reasignacion agente-broker no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(reasignacion.getIdReasignacion());
        }
        if (reasignacion.getFechaCambio() == null || reasignacion.getMotivo() == null
                || reasignacion.getMotivo().isBlank()) {
            throw new IllegalArgumentException("La reasignacion agente-broker tiene campos obligatorios incompletos.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdAgente(reasignacion.getAgente()));
        JdbcSupport.validarId(JdbcSupport.getIdBroker(reasignacion.getBrokerNuevo()));
        JdbcSupport.validarId(JdbcSupport.getIdBroker(reasignacion.getBrokerAdministrador()));
    }
}
