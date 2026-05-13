package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.model.comercial.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.SolicitudAlquiler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SolicitudAlquilerDAOImpl implements SolicitudAlquilerDAO {

    private static final String INSERT_SQL = """
            INSERT INTO solicitud_alquiler (
                codigo_solicitud, fecha_registro, monto_propuesto, plazo_tentativo,
                observaciones, estado, fecha_actualizacion_estado,
                id_cliente, id_captacion, id_agente
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT id_solicitud, codigo_solicitud, fecha_registro, monto_propuesto,
                   plazo_tentativo, observaciones, estado, fecha_actualizacion_estado,
                   id_cliente, id_captacion, id_agente, fecha_creacion, fecha_actualizacion
            FROM solicitud_alquiler
            """;
    private static final String UPDATE_SQL = """
            UPDATE solicitud_alquiler
            SET codigo_solicitud = ?, fecha_registro = ?, monto_propuesto = ?,
                plazo_tentativo = ?, observaciones = ?, estado = ?,
                fecha_actualizacion_estado = ?, id_cliente = ?, id_captacion = ?, id_agente = ?
            WHERE id_solicitud = ?
            """;
    private static final String DELETE_SQL = """
            UPDATE solicitud_alquiler
            SET estado = 'DESISTIDA', fecha_actualizacion_estado = CURRENT_TIMESTAMP
            WHERE id_solicitud = ?
            """;

    @Override
    public Long crear(SolicitudAlquiler solicitud) {
        validar(solicitud, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, solicitud.getCodigoSolicitud());
            JdbcSupport.setDate(ps, 2, solicitud.getFechaRegistro());
            ps.setBigDecimal(3, solicitud.getMontoPropuesto());
            ps.setString(4, solicitud.getPlazoTentativo());
            ps.setString(5, solicitud.getObservaciones());
            ps.setString(6, solicitud.getEstado().name());
            JdbcSupport.setTimestamp(ps, 7, solicitud.getFechaActualizacionEstado());
            ps.setLong(8, solicitud.getClienteInteresado().getIdCliente());
            ps.setLong(9, solicitud.getCaptacion().getIdCaptacion());
            ps.setLong(10, solicitud.getAgenteResponsable().getIdAgente());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    solicitud.setIdSolicitud(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de solicitud de alquiler.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear solicitud de alquiler.", e);
        }
    }

    @Override
    public Optional<SolicitudAlquiler> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_solicitud = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar solicitud de alquiler con id " + id + ".", e);
        }
    }

    @Override
    public List<SolicitudAlquiler> listarTodos() {
        List<SolicitudAlquiler> solicitudes = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY id_solicitud");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                solicitudes.add(mapRow(rs));
            }
            return solicitudes;
        } catch (SQLException e) {
            throw new DAOException("Error al listar solicitudes de alquiler.", e);
        }
    }

    @Override
    public boolean actualizar(SolicitudAlquiler solicitud) {
        validar(solicitud, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setString(1, solicitud.getCodigoSolicitud());
            JdbcSupport.setDate(ps, 2, solicitud.getFechaRegistro());
            ps.setBigDecimal(3, solicitud.getMontoPropuesto());
            ps.setString(4, solicitud.getPlazoTentativo());
            ps.setString(5, solicitud.getObservaciones());
            ps.setString(6, solicitud.getEstado().name());
            JdbcSupport.setTimestamp(ps, 7, solicitud.getFechaActualizacionEstado());
            ps.setLong(8, solicitud.getClienteInteresado().getIdCliente());
            ps.setLong(9, solicitud.getCaptacion().getIdCaptacion());
            ps.setLong(10, solicitud.getAgenteResponsable().getIdAgente());
            ps.setLong(11, solicitud.getIdSolicitud());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar solicitud de alquiler con id " + solicitud.getIdSolicitud() + ".", e);
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
            throw new DAOException("Error al eliminar solicitud de alquiler con id " + id + ".", e);
        }
    }

    private SolicitudAlquiler mapRow(ResultSet rs) throws SQLException {
        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        solicitud.setIdSolicitud(rs.getLong("id_solicitud"));
        solicitud.setCodigoSolicitud(rs.getString("codigo_solicitud"));
        solicitud.setFechaRegistro(JdbcSupport.toLocalDate(rs.getDate("fecha_registro")));
        solicitud.setMontoPropuesto(rs.getBigDecimal("monto_propuesto"));
        solicitud.setPlazoTentativo(rs.getString("plazo_tentativo"));
        solicitud.setObservaciones(rs.getString("observaciones"));
        solicitud.setEstado(EstadoSolicitudAlquiler.valueOf(rs.getString("estado")));
        solicitud.setFechaActualizacionEstado(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion_estado")));
        solicitud.setClienteInteresado(JdbcSupport.cliente(rs.getLong("id_cliente")));
        solicitud.setCaptacion(JdbcSupport.captacion(rs.getLong("id_captacion")));
        solicitud.setAgenteResponsable(JdbcSupport.agente(rs.getLong("id_agente")));
        solicitud.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        solicitud.setFechaActualizacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion")));
        return solicitud;
    }

    private void validar(SolicitudAlquiler solicitud, boolean requiereId) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud de alquiler no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(solicitud.getIdSolicitud());
        }
        if (solicitud.getCodigoSolicitud() == null || solicitud.getCodigoSolicitud().isBlank()
                || solicitud.getFechaRegistro() == null || solicitud.getMontoPropuesto() == null
                || solicitud.getMontoPropuesto().signum() <= 0 || solicitud.getEstado() == null) {
            throw new IllegalArgumentException("La solicitud de alquiler tiene campos obligatorios incompletos.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdCliente(solicitud.getClienteInteresado()));
        JdbcSupport.validarId(JdbcSupport.getIdCaptacion(solicitud.getCaptacion()));
        JdbcSupport.validarId(JdbcSupport.getIdAgente(solicitud.getAgenteResponsable()));
    }
}
