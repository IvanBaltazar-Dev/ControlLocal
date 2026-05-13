package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.VisitaDAO;
import com.controllocal.model.comercial.EstadoVisita;
import com.controllocal.model.comercial.Visita;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VisitaDAOImpl implements VisitaDAO {

    private static final String INSERT_SQL = """
            INSERT INTO visita (
                fecha_visita, hora_visita, observaciones, estado, resultado,
                id_cliente, id_captacion, id_agente
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT id_visita, fecha_visita, hora_visita, observaciones, estado, resultado,
                   id_cliente, id_captacion, id_agente, fecha_creacion, fecha_actualizacion
            FROM visita
            """;
    private static final String UPDATE_SQL = """
            UPDATE visita
            SET fecha_visita = ?, hora_visita = ?, observaciones = ?, estado = ?, resultado = ?,
                id_cliente = ?, id_captacion = ?, id_agente = ?
            WHERE id_visita = ?
            """;
    private static final String DELETE_SQL = "UPDATE visita SET estado = 'CANCELADA' WHERE id_visita = ?";

    @Override
    public Long crear(Visita visita) {
        validar(visita, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            JdbcSupport.setDate(ps, 1, visita.getFechaVisita());
            JdbcSupport.setTime(ps, 2, visita.getHoraVisita());
            ps.setString(3, visita.getObservaciones());
            ps.setString(4, visita.getEstado().name());
            ps.setString(5, visita.getResultado());
            ps.setLong(6, visita.getClienteInteresado().getIdCliente());
            ps.setLong(7, visita.getCaptacion().getIdCaptacion());
            ps.setLong(8, visita.getAgenteResponsable().getIdAgente());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    visita.setIdVisita(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de visita.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear visita.", e);
        }
    }

    @Override
    public Optional<Visita> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_visita = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar visita con id " + id + ".", e);
        }
    }

    @Override
    public List<Visita> listarTodos() {
        List<Visita> visitas = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY id_visita");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                visitas.add(mapRow(rs));
            }
            return visitas;
        } catch (SQLException e) {
            throw new DAOException("Error al listar visitas.", e);
        }
    }

    @Override
    public boolean actualizar(Visita visita) {
        validar(visita, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            JdbcSupport.setDate(ps, 1, visita.getFechaVisita());
            JdbcSupport.setTime(ps, 2, visita.getHoraVisita());
            ps.setString(3, visita.getObservaciones());
            ps.setString(4, visita.getEstado().name());
            ps.setString(5, visita.getResultado());
            ps.setLong(6, visita.getClienteInteresado().getIdCliente());
            ps.setLong(7, visita.getCaptacion().getIdCaptacion());
            ps.setLong(8, visita.getAgenteResponsable().getIdAgente());
            ps.setLong(9, visita.getIdVisita());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar visita con id " + visita.getIdVisita() + ".", e);
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
            throw new DAOException("Error al eliminar visita con id " + id + ".", e);
        }
    }

    private Visita mapRow(ResultSet rs) throws SQLException {
        Visita visita = new Visita();
        visita.setIdVisita(rs.getLong("id_visita"));
        visita.setFechaVisita(JdbcSupport.toLocalDate(rs.getDate("fecha_visita")));
        visita.setHoraVisita(JdbcSupport.toLocalTime(rs.getTime("hora_visita")));
        visita.setObservaciones(rs.getString("observaciones"));
        visita.setEstado(EstadoVisita.valueOf(rs.getString("estado")));
        visita.setResultado(rs.getString("resultado"));
        visita.setClienteInteresado(JdbcSupport.cliente(rs.getLong("id_cliente")));
        visita.setCaptacion(JdbcSupport.captacion(rs.getLong("id_captacion")));
        visita.setAgenteResponsable(JdbcSupport.agente(rs.getLong("id_agente")));
        visita.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        visita.setFechaActualizacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion")));
        return visita;
    }

    private void validar(Visita visita, boolean requiereId) {
        if (visita == null) {
            throw new IllegalArgumentException("La visita no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(visita.getIdVisita());
        }
        if (visita.getFechaVisita() == null || visita.getHoraVisita() == null || visita.getEstado() == null) {
            throw new IllegalArgumentException("La visita tiene campos obligatorios incompletos.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdCliente(visita.getClienteInteresado()));
        JdbcSupport.validarId(JdbcSupport.getIdCaptacion(visita.getCaptacion()));
        JdbcSupport.validarId(JdbcSupport.getIdAgente(visita.getAgenteResponsable()));
    }
}
