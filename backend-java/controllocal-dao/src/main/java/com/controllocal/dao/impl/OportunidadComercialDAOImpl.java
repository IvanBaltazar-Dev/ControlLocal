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
import com.controllocal.dao.OportunidadComercialDAO;
import com.controllocal.model.comercial.enums.EstadoOportunidadComercial;
import com.controllocal.model.comercial.OportunidadComercial;

public class OportunidadComercialDAOImpl implements OportunidadComercialDAO {

    private static final String INSERT_SQL = """
            INSERT INTO oportunidad_comercial (
                codigo_oportunidad, fecha_registro, estado, fecha_actualizacion_estado,
                motivo_cierre, observaciones, id_cliente, id_captacion, id_agente, fecha_cierre
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT id_oportunidad, codigo_oportunidad, fecha_registro, estado,
                   fecha_actualizacion_estado, motivo_cierre, observaciones,
                   id_cliente, id_captacion, id_agente, fecha_cierre,
                   fecha_creacion, fecha_actualizacion
            FROM oportunidad_comercial
            """;
    private static final String UPDATE_SQL = """
            UPDATE oportunidad_comercial
            SET codigo_oportunidad = ?, fecha_registro = ?, estado = ?,
                fecha_actualizacion_estado = ?, motivo_cierre = ?, observaciones = ?,
                id_cliente = ?, id_captacion = ?, id_agente = ?, fecha_cierre = ?
            WHERE id_oportunidad = ?
            """;
    private static final String DELETE_SQL = """
            UPDATE oportunidad_comercial
            SET estado = 'N', fecha_actualizacion_estado = CURRENT_TIMESTAMP,
                fecha_cierre = COALESCE(fecha_cierre, CURRENT_TIMESTAMP)
            WHERE id_oportunidad = ?
            """;

    @Override
    public Long crear(OportunidadComercial oportunidad) {
        validar(oportunidad, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bind(oportunidad, ps);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    oportunidad.setIdOportunidad(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de oportunidad comercial.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear oportunidad comercial.", e);
        }
    }

    @Override
    public Optional<OportunidadComercial> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_oportunidad = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar oportunidad comercial con id " + id + ".", e);
        }
    }

    @Override
    public List<OportunidadComercial> listarTodos() {
        List<OportunidadComercial> oportunidades = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY id_oportunidad");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                oportunidades.add(mapRow(rs));
            }
            return oportunidades;
        } catch (SQLException e) {
            throw new DAOException("Error al listar oportunidades comerciales.", e);
        }
    }

    @Override
    public boolean actualizar(OportunidadComercial oportunidad) {
        validar(oportunidad, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            bind(oportunidad, ps);
            ps.setLong(11, oportunidad.getIdOportunidad());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar oportunidad comercial con id "
                    + oportunidad.getIdOportunidad() + ".", e);
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
            throw new DAOException("Error al cerrar oportunidad comercial con id " + id + ".", e);
        }
    }

    private void bind(OportunidadComercial oportunidad, PreparedStatement ps) throws SQLException {
        ps.setString(1, oportunidad.getCodigoOportunidad());
        JdbcSupport.setTimestamp(ps, 2, oportunidad.getFechaRegistro());
        JdbcSupport.setEnum(ps, 3, oportunidad.getEstado());
        JdbcSupport.setTimestamp(ps, 4, oportunidad.getFechaActualizacionEstado());
        ps.setString(5, oportunidad.getMotivoCierre());
        ps.setString(6, oportunidad.getObservaciones());
        ps.setLong(7, oportunidad.getClienteInteresado().getIdCliente());
        ps.setLong(8, oportunidad.getCaptacion().getIdCaptacion());
        ps.setLong(9, oportunidad.getAgenteResponsable().getIdAgente());
        JdbcSupport.setTimestamp(ps, 10, oportunidad.getFechaCierre());
    }

    private OportunidadComercial mapRow(ResultSet rs) throws SQLException {
        OportunidadComercial oportunidad = new OportunidadComercial();
        oportunidad.setIdOportunidad(rs.getLong("id_oportunidad"));
        oportunidad.setCodigoOportunidad(rs.getString("codigo_oportunidad"));
        oportunidad.setFechaRegistro(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_registro")));
        oportunidad.setEstado(JdbcSupport.getEnum(rs, "estado", EstadoOportunidadComercial.class));
        oportunidad.setFechaActualizacionEstado(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion_estado")));
        oportunidad.setMotivoCierre(rs.getString("motivo_cierre"));
        oportunidad.setObservaciones(rs.getString("observaciones"));
        oportunidad.setClienteInteresado(JdbcSupport.cliente(rs.getLong("id_cliente")));
        oportunidad.setCaptacion(JdbcSupport.captacion(rs.getLong("id_captacion")));
        oportunidad.setAgenteResponsable(JdbcSupport.agente(rs.getLong("id_agente")));
        oportunidad.setFechaCierre(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_cierre")));
        oportunidad.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        oportunidad.setFechaActualizacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion")));
        return oportunidad;
    }

    private void validar(OportunidadComercial oportunidad, boolean requiereId) {
        if (oportunidad == null) {
            throw new IllegalArgumentException("La oportunidad comercial no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(oportunidad.getIdOportunidad());
        }
        if (oportunidad.getCodigoOportunidad() == null || oportunidad.getCodigoOportunidad().isBlank()
                || oportunidad.getFechaRegistro() == null || oportunidad.getEstado() == null) {
            throw new IllegalArgumentException("La oportunidad comercial tiene campos obligatorios incompletos.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdCliente(oportunidad.getClienteInteresado()));
        JdbcSupport.validarId(JdbcSupport.getIdCaptacion(oportunidad.getCaptacion()));
        JdbcSupport.validarId(JdbcSupport.getIdAgente(oportunidad.getAgenteResponsable()));
    }
}
