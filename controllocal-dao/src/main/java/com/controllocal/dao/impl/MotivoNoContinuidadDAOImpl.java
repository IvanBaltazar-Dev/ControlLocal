package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.MotivoNoContinuidadDAO;
import com.controllocal.model.comercial.MotivoNoContinuidad;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MotivoNoContinuidadDAOImpl implements MotivoNoContinuidadDAO {

    private static final String INSERT_SQL = """
            INSERT INTO motivo_no_continuidad (
                fecha_hora, razon_principal, observaciones, id_agente,
                id_interaccion, id_visita, id_solicitud
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT id_motivo_no_continuidad, fecha_hora, razon_principal, observaciones,
                   id_agente, id_interaccion, id_visita, id_solicitud
            FROM motivo_no_continuidad
            """;
    private static final String UPDATE_SQL = """
            UPDATE motivo_no_continuidad
            SET fecha_hora = ?, razon_principal = ?, observaciones = ?, id_agente = ?,
                id_interaccion = ?, id_visita = ?, id_solicitud = ?
            WHERE id_motivo_no_continuidad = ?
            """;
    private static final String DELETE_SQL = "DELETE FROM motivo_no_continuidad WHERE id_motivo_no_continuidad = ?";

    @Override
    public Long crear(MotivoNoContinuidad motivo) {
        validar(motivo, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bind(motivo, ps);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    motivo.setIdMotivoNoContinuidad(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de motivo de no continuidad.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear motivo de no continuidad.", e);
        }
    }

    @Override
    public Optional<MotivoNoContinuidad> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_motivo_no_continuidad = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar motivo de no continuidad con id " + id + ".", e);
        }
    }

    @Override
    public List<MotivoNoContinuidad> listarTodos() {
        List<MotivoNoContinuidad> motivos = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY id_motivo_no_continuidad");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                motivos.add(mapRow(rs));
            }
            return motivos;
        } catch (SQLException e) {
            throw new DAOException("Error al listar motivos de no continuidad.", e);
        }
    }

    @Override
    public boolean actualizar(MotivoNoContinuidad motivo) {
        validar(motivo, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            bind(motivo, ps);
            ps.setLong(8, motivo.getIdMotivoNoContinuidad());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar motivo de no continuidad con id "
                    + motivo.getIdMotivoNoContinuidad() + ".", e);
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
            throw new DAOException("Error al eliminar motivo de no continuidad con id " + id + ".", e);
        }
    }

    private void bind(MotivoNoContinuidad motivo, PreparedStatement ps) throws SQLException {
        JdbcSupport.setTimestamp(ps, 1, motivo.getFechaHora());
        ps.setString(2, motivo.getRazonPrincipal());
        ps.setString(3, motivo.getObservaciones());
        ps.setLong(4, motivo.getAgenteResponsable().getIdAgente());
        JdbcSupport.setLong(ps, 5, JdbcSupport.getIdInteraccion(motivo.getInteraccionComercial()));
        JdbcSupport.setLong(ps, 6, JdbcSupport.getIdVisita(motivo.getVisita()));
        JdbcSupport.setLong(ps, 7, JdbcSupport.getIdSolicitud(motivo.getSolicitudAlquiler()));
    }

    private MotivoNoContinuidad mapRow(ResultSet rs) throws SQLException {
        MotivoNoContinuidad motivo = new MotivoNoContinuidad();
        motivo.setIdMotivoNoContinuidad(rs.getLong("id_motivo_no_continuidad"));
        motivo.setFechaHora(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_hora")));
        motivo.setRazonPrincipal(rs.getString("razon_principal"));
        motivo.setObservaciones(rs.getString("observaciones"));
        motivo.setAgenteResponsable(JdbcSupport.agente(rs.getLong("id_agente")));
        Long idInteraccion = rs.getObject("id_interaccion", Long.class);
        Long idVisita = rs.getObject("id_visita", Long.class);
        Long idSolicitud = rs.getObject("id_solicitud", Long.class);
        if (idInteraccion != null) {
            motivo.setInteraccionComercial(JdbcSupport.interaccion(idInteraccion));
        }
        if (idVisita != null) {
            motivo.setVisita(JdbcSupport.visita(idVisita));
        }
        if (idSolicitud != null) {
            motivo.setSolicitudAlquiler(JdbcSupport.solicitud(idSolicitud));
        }
        return motivo;
    }

    private void validar(MotivoNoContinuidad motivo, boolean requiereId) {
        if (motivo == null) {
            throw new IllegalArgumentException("El motivo de no continuidad no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(motivo.getIdMotivoNoContinuidad());
        }
        motivo.validarReferenciaUnica();
        if (motivo.getFechaHora() == null || motivo.getRazonPrincipal() == null
                || motivo.getRazonPrincipal().isBlank()) {
            throw new IllegalArgumentException("El motivo de no continuidad tiene campos obligatorios incompletos.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdAgente(motivo.getAgenteResponsable()));
        int referencias = 0;
        referencias += JdbcSupport.getIdInteraccion(motivo.getInteraccionComercial()) != null ? 1 : 0;
        referencias += JdbcSupport.getIdVisita(motivo.getVisita()) != null ? 1 : 0;
        referencias += JdbcSupport.getIdSolicitud(motivo.getSolicitudAlquiler()) != null ? 1 : 0;
        if (referencias != 1) {
            throw new IllegalArgumentException("Debe existir exactamente una referencia principal.");
        }
    }
}
