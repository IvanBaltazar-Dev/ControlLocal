package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.VisitaDAO;
import com.controllocal.model.comercial.enums.EstadoVisita;
import com.controllocal.model.comercial.Visita;

public class VisitaDAOImpl implements VisitaDAO {

    private static final String INSERT_SQL = """
            INSERT INTO visita (
                fecha_visita, hora_visita, observaciones, estado, resultado,
                id_oportunidad, id_agente
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT id_visita, fecha_visita, hora_visita, observaciones, estado, resultado,
                   v.id_oportunidad, o.id_cliente, o.id_captacion, v.id_agente, v.fecha_creacion, v.fecha_actualizacion
            FROM visita v
            INNER JOIN oportunidad_comercial o ON v.id_oportunidad = o.id_oportunidad
            """;
    private static final String UPDATE_SQL = """
            UPDATE visita
            SET fecha_visita = ?, hora_visita = ?, observaciones = ?, estado = ?, resultado = ?,
                id_oportunidad = ?, id_agente = ?
            WHERE id_visita = ?
            """;
    private static final String DELETE_SQL = "UPDATE visita SET estado = 'C' WHERE id_visita = ?";

    @Override
    public Long crear(Visita visita) {
        validar(visita, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            JdbcSupport.setDate(ps, 1, visita.getFechaVisita());
            JdbcSupport.setTime(ps, 2, visita.getHoraVisita());
            ps.setString(3, visita.getObservaciones());
            JdbcSupport.setEnum(ps, 4, visita.getEstado());
            ps.setString(5, visita.getResultado());
            ps.setLong(6, visita.getOportunidadComercial().getIdOportunidad());
            ps.setLong(7, visita.getAgenteResponsable().getIdAgente());
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
            JdbcSupport.setEnum(ps, 4, visita.getEstado());
            ps.setString(5, visita.getResultado());
            ps.setLong(6, visita.getOportunidadComercial().getIdOportunidad());
            ps.setLong(7, visita.getAgenteResponsable().getIdAgente());
            ps.setLong(8, visita.getIdVisita());
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
        visita.setEstado(JdbcSupport.getEnum(rs, "estado", EstadoVisita.class));
        visita.setResultado(rs.getString("resultado"));
        visita.setOportunidadComercial(JdbcSupport.oportunidad(rs.getLong("id_oportunidad")));
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
        JdbcSupport.validarId(JdbcSupport.getIdOportunidad(visita.getOportunidadComercial()));
        JdbcSupport.validarId(JdbcSupport.getIdAgente(visita.getAgenteResponsable()));
    }
}

