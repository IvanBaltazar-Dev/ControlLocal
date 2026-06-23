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
import com.controllocal.dao.EvaluacionSolicitudDAO;
import com.controllocal.model.comercial.enums.ResultadoEvaluacionSolicitud;
import com.controllocal.model.comercial.enums.TipoEvaluacionSolicitud;
import com.controllocal.model.comercial.EvaluacionSolicitud;

public class EvaluacionSolicitudDAOImpl implements EvaluacionSolicitudDAO {

    private static final String INSERT_SQL = """
            INSERT INTO evaluacion_solicitud (
                fecha_evaluacion, resultado, observaciones, responsable_evaluacion,
                tipo_evaluacion, id_solicitud
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT id_evaluacion, fecha_evaluacion, resultado, observaciones,
                   responsable_evaluacion, tipo_evaluacion, id_solicitud
            FROM evaluacion_solicitud
            """;
    private static final String UPDATE_SQL = """
            UPDATE evaluacion_solicitud
            SET fecha_evaluacion = ?, resultado = ?, observaciones = ?,
                responsable_evaluacion = ?, tipo_evaluacion = ?, id_solicitud = ?
            WHERE id_evaluacion = ?
            """;
    private static final String DELETE_SQL = "DELETE FROM evaluacion_solicitud WHERE id_evaluacion = ?";

    @Override
    public Long crear(EvaluacionSolicitud evaluacion) {
        validar(evaluacion, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bind(evaluacion, ps);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    evaluacion.setIdEvaluacion(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de evaluacion de solicitud.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear evaluacion de solicitud.", e);
        }
    }

    @Override
    public Optional<EvaluacionSolicitud> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_evaluacion = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar evaluacion de solicitud con id " + id + ".", e);
        }
    }

    @Override
    public List<EvaluacionSolicitud> listarTodos() {
        List<EvaluacionSolicitud> evaluaciones = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY id_evaluacion");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                evaluaciones.add(mapRow(rs));
            }
            return evaluaciones;
        } catch (SQLException e) {
            throw new DAOException("Error al listar evaluaciones de solicitud.", e);
        }
    }

    @Override
    public List<EvaluacionSolicitud> listarPorSolicitud(Long idSolicitud) {
        JdbcSupport.validarId(idSolicitud);
        List<EvaluacionSolicitud> evaluaciones = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SELECT_SQL + " WHERE id_solicitud = ? ORDER BY fecha_evaluacion, id_evaluacion")) {
            ps.setLong(1, idSolicitud);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    evaluaciones.add(mapRow(rs));
                }
            }
            return evaluaciones;
        } catch (SQLException e) {
            throw new DAOException("Error al listar evaluaciones de la solicitud " + idSolicitud + ".", e);
        }
    }

    @Override
    public boolean actualizar(EvaluacionSolicitud evaluacion) {
        validar(evaluacion, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            bind(evaluacion, ps);
            ps.setLong(7, evaluacion.getIdEvaluacion());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar evaluacion de solicitud con id " + evaluacion.getIdEvaluacion() + ".", e);
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
            throw new DAOException("Error al eliminar evaluacion de solicitud con id " + id + ".", e);
        }
    }

    private void bind(EvaluacionSolicitud evaluacion, PreparedStatement ps) throws SQLException {
        JdbcSupport.setTimestamp(ps, 1, evaluacion.getFechaEvaluacion());
        JdbcSupport.setEnum(ps, 2, evaluacion.getResultado());
        ps.setString(3, evaluacion.getObservaciones());
        ps.setLong(4, evaluacion.getResponsableEvaluacion().getIdBroker());
        JdbcSupport.setEnum(ps, 5, evaluacion.getTipoEvaluacion());
        ps.setLong(6, evaluacion.getSolicitudAlquiler().getIdSolicitud());
    }

    private EvaluacionSolicitud mapRow(ResultSet rs) throws SQLException {
        EvaluacionSolicitud evaluacion = new EvaluacionSolicitud();
        evaluacion.setIdEvaluacion(rs.getLong("id_evaluacion"));
        evaluacion.setFechaEvaluacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_evaluacion")));
        evaluacion.setResultado(JdbcSupport.getEnum(rs, "resultado", ResultadoEvaluacionSolicitud.class));
        evaluacion.setObservaciones(rs.getString("observaciones"));
        evaluacion.setResponsableEvaluacion(JdbcSupport.broker(rs.getLong("responsable_evaluacion")));
        evaluacion.setTipoEvaluacion(JdbcSupport.getEnum(rs, "tipo_evaluacion", TipoEvaluacionSolicitud.class));
        evaluacion.setSolicitudAlquiler(JdbcSupport.solicitud(rs.getLong("id_solicitud")));
        return evaluacion;
    }

    private void validar(EvaluacionSolicitud evaluacion, boolean requiereId) {
        if (evaluacion == null) {
            throw new IllegalArgumentException("La evaluacion de solicitud no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(evaluacion.getIdEvaluacion());
        }
        if (evaluacion.getFechaEvaluacion() == null || evaluacion.getResultado() == null
                || evaluacion.getTipoEvaluacion() == null) {
            throw new IllegalArgumentException("La evaluacion de solicitud tiene campos obligatorios incompletos.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdBroker(evaluacion.getResponsableEvaluacion()));
        JdbcSupport.validarId(JdbcSupport.getIdSolicitud(evaluacion.getSolicitudAlquiler()));
    }
}

