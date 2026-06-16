package com.controllocal.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.controllocal.dao.HistorialEstadoDAO;
import com.controllocal.model.comercial.HistorialEstado;
import com.controllocal.model.comercial.enums.TipoEntidad;

public class HistorialEstadoDAOImpl extends AbstractJdbcCrudDAO<HistorialEstado>
        implements HistorialEstadoDAO {
    private static final String INSERT = """
            INSERT INTO historial_estado (
                entidad_tipo, entidad_id, estado_anterior, estado_nuevo,
                id_usuario, fecha_evento, observacion
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT = """
            SELECT id_historial_estado, entidad_tipo, entidad_id, estado_anterior,
                   estado_nuevo, id_usuario, fecha_evento, observacion
            FROM historial_estado
            """;
    private static final String UPDATE = """
            UPDATE historial_estado SET entidad_tipo = ?, entidad_id = ?,
                estado_anterior = ?, estado_nuevo = ?, id_usuario = ?,
                fecha_evento = ?, observacion = ?
            WHERE id_historial_estado = ?
            """;
    private static final String DELETE = "DELETE FROM historial_estado WHERE id_historial_estado = ?";

    @Override public List<HistorialEstado> listarPorEntidad(TipoEntidad tipo, Long entidadId) {
        if (tipo == null) throw new IllegalArgumentException("El tipo de entidad es obligatorio.");
        JdbcSupport.validarId(entidadId);
        return query(SELECT + " WHERE entidad_tipo = ? AND entidad_id = ? ORDER BY fecha_evento",
                ps -> {
                    JdbcSupport.setEnum(ps, 1, tipo);
                    ps.setLong(2, entidadId);
                });
    }

    @Override protected String insertSql() { return INSERT; }
    @Override protected String selectSql() { return SELECT; }
    @Override protected String updateSql() { return UPDATE; }
    @Override protected String deleteSql() { return DELETE; }
    @Override protected String idColumn() { return "id_historial_estado"; }
    @Override protected String entityName() { return "el historial de estado"; }
    @Override protected void bindInsert(PreparedStatement ps, HistorialEstado h) throws SQLException { bind(ps, h); }
    @Override protected void bindUpdate(PreparedStatement ps, HistorialEstado h) throws SQLException {
        bind(ps, h);
        ps.setLong(8, h.getIdHistorialEstado());
    }

    private void bind(PreparedStatement ps, HistorialEstado h) throws SQLException {
        JdbcSupport.setEnum(ps, 1, h.getEntidadTipo());
        ps.setLong(2, h.getEntidadId());
        ps.setString(3, h.getEstadoAnterior());
        ps.setString(4, h.getEstadoNuevo());
        ps.setLong(5, h.getUsuario().getIdUsuarioInterno());
        JdbcSupport.setTimestamp(ps, 6, h.getFechaEvento());
        ps.setString(7, h.getObservacion());
    }

    @Override protected void bindDelete(PreparedStatement ps, Long id) throws SQLException { ps.setLong(1, id); }

    @Override protected HistorialEstado mapRow(ResultSet rs) throws SQLException {
        HistorialEstado h = new HistorialEstado();
        h.setIdHistorialEstado(rs.getLong("id_historial_estado"));
        h.setEntidadTipo(JdbcSupport.getEnum(rs, "entidad_tipo", TipoEntidad.class));
        h.setEntidadId(rs.getLong("entidad_id"));
        h.setEstadoAnterior(rs.getString("estado_anterior"));
        h.setEstadoNuevo(rs.getString("estado_nuevo"));
        h.setUsuario(JdbcSupport.usuario(rs.getLong("id_usuario")));
        h.setFechaEvento(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_evento")));
        h.setObservacion(rs.getString("observacion"));
        return h;
    }

    @Override protected void assignId(HistorialEstado h, Long id) { h.setIdHistorialEstado(id); }

    @Override protected void validate(HistorialEstado h, boolean requireId) {
        if (h == null) throw new IllegalArgumentException("El historial de estado es obligatorio.");
        if (requireId) JdbcSupport.validarId(h.getIdHistorialEstado());
        JdbcSupport.validarId(h.getEntidadId());
        JdbcSupport.validarId(h.getUsuario() != null ? h.getUsuario().getIdUsuarioInterno() : null);
        if (h.getEntidadTipo() == null || h.getEstadoNuevo() == null || h.getEstadoNuevo().isBlank()
                || h.getFechaEvento() == null) {
            throw new IllegalArgumentException("El historial de estado tiene campos obligatorios incompletos.");
        }
    }
}
