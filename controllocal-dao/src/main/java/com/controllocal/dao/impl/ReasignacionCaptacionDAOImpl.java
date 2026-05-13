package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.ReasignacionCaptacionDAO;
import com.controllocal.model.comercial.ReasignacionCaptacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReasignacionCaptacionDAOImpl implements ReasignacionCaptacionDAO {

    private static final String INSERT_SQL = """
            INSERT INTO reasignacion_captacion (
                fecha_cambio, motivo, id_captacion, id_agente_anterior, id_agente_nuevo, id_broker
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT id_reasignacion, fecha_cambio, motivo, id_captacion,
                   id_agente_anterior, id_agente_nuevo, id_broker
            FROM reasignacion_captacion
            """;
    private static final String UPDATE_SQL = """
            UPDATE reasignacion_captacion
            SET fecha_cambio = ?, motivo = ?, id_captacion = ?, id_agente_anterior = ?,
                id_agente_nuevo = ?, id_broker = ?
            WHERE id_reasignacion = ?
            """;
    private static final String DELETE_SQL = "DELETE FROM reasignacion_captacion WHERE id_reasignacion = ?";

    @Override
    public Long crear(ReasignacionCaptacion reasignacion) {
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
            throw new DAOException("No se genero el id de reasignacion de captacion.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear reasignacion de captacion.", e);
        }
    }

    @Override
    public Optional<ReasignacionCaptacion> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_reasignacion = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar reasignacion de captacion con id " + id + ".", e);
        }
    }

    @Override
    public List<ReasignacionCaptacion> listarTodos() {
        List<ReasignacionCaptacion> reasignaciones = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY id_reasignacion");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                reasignaciones.add(mapRow(rs));
            }
            return reasignaciones;
        } catch (SQLException e) {
            throw new DAOException("Error al listar reasignaciones de captacion.", e);
        }
    }

    @Override
    public boolean actualizar(ReasignacionCaptacion reasignacion) {
        validar(reasignacion, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            bind(reasignacion, ps);
            ps.setLong(7, reasignacion.getIdReasignacion());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar reasignacion de captacion con id " + reasignacion.getIdReasignacion() + ".", e);
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
            throw new DAOException("Error al eliminar reasignacion de captacion con id " + id + ".", e);
        }
    }

    private void bind(ReasignacionCaptacion reasignacion, PreparedStatement ps) throws SQLException {
        JdbcSupport.setTimestamp(ps, 1, reasignacion.getFechaCambio());
        ps.setString(2, reasignacion.getMotivo());
        ps.setLong(3, reasignacion.getCaptacion().getIdCaptacion());
        ps.setLong(4, reasignacion.getAgenteAnterior().getIdAgente());
        ps.setLong(5, reasignacion.getAgenteNuevo().getIdAgente());
        ps.setLong(6, reasignacion.getBrokerResponsable().getIdBroker());
    }

    private ReasignacionCaptacion mapRow(ResultSet rs) throws SQLException {
        ReasignacionCaptacion reasignacion = new ReasignacionCaptacion();
        reasignacion.setIdReasignacion(rs.getLong("id_reasignacion"));
        reasignacion.setFechaCambio(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_cambio")));
        reasignacion.setMotivo(rs.getString("motivo"));
        reasignacion.setCaptacion(JdbcSupport.captacion(rs.getLong("id_captacion")));
        reasignacion.setAgenteAnterior(JdbcSupport.agente(rs.getLong("id_agente_anterior")));
        reasignacion.setAgenteNuevo(JdbcSupport.agente(rs.getLong("id_agente_nuevo")));
        reasignacion.setBrokerResponsable(JdbcSupport.broker(rs.getLong("id_broker")));
        return reasignacion;
    }

    private void validar(ReasignacionCaptacion reasignacion, boolean requiereId) {
        if (reasignacion == null) {
            throw new IllegalArgumentException("La reasignacion de captacion no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(reasignacion.getIdReasignacion());
        }
        if (reasignacion.getFechaCambio() == null || reasignacion.getMotivo() == null
                || reasignacion.getMotivo().isBlank()) {
            throw new IllegalArgumentException("La reasignacion de captacion tiene campos obligatorios incompletos.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdCaptacion(reasignacion.getCaptacion()));
        JdbcSupport.validarId(JdbcSupport.getIdAgente(reasignacion.getAgenteAnterior()));
        JdbcSupport.validarId(JdbcSupport.getIdAgente(reasignacion.getAgenteNuevo()));
        JdbcSupport.validarId(JdbcSupport.getIdBroker(reasignacion.getBrokerResponsable()));
    }
}
