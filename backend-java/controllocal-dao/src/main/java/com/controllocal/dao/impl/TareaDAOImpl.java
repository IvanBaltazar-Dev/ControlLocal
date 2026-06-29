package com.controllocal.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.controllocal.dao.TareaDAO;
import com.controllocal.model.comercial.Tarea;
import com.controllocal.model.comercial.enums.EstadoTarea;
import com.controllocal.model.comercial.enums.Prioridad;
import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.comercial.enums.TipoTarea;

public class TareaDAOImpl extends AbstractJdbcCrudDAO<Tarea> implements TareaDAO {
    private static final String INSERT = """
            INSERT INTO tarea (
                tipo, entidad_tipo, entidad_id, id_agente, descripcion,
                fecha_programada, fecha_recordatorio, fecha_completada, estado, prioridad
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT = """
            SELECT id_tarea, tipo, entidad_tipo, entidad_id, id_agente, descripcion,
                   fecha_programada, fecha_recordatorio, fecha_completada, estado,
                   prioridad, fecha_creacion, fecha_actualizacion
            FROM tarea
            """;
    private static final String UPDATE = """
            UPDATE tarea SET tipo = ?, entidad_tipo = ?, entidad_id = ?, id_agente = ?,
                descripcion = ?, fecha_programada = ?, fecha_recordatorio = ?,
                fecha_completada = ?, estado = ?, prioridad = ?
            WHERE id_tarea = ?
            """;
    private static final String DELETE = "UPDATE tarea SET estado = 'CANCELADA' WHERE id_tarea = ?";

    @Override public List<Tarea> listarPorAgente(Long idAgente, EstadoTarea estado) {
        JdbcSupport.validarId(idAgente);
        if (estado == null) {
            return query(SELECT + " WHERE id_agente = ? ORDER BY fecha_programada",
                    ps -> ps.setLong(1, idAgente));
        }
        return query(SELECT + " WHERE id_agente = ? AND estado = ? ORDER BY fecha_programada",
                ps -> {
                    ps.setLong(1, idAgente);
                    JdbcSupport.setEnum(ps, 2, estado);
                });
    }

    @Override public List<Tarea> listarPorEntidad(TipoEntidad entidadTipo, Long entidadId) {
        JdbcSupport.validarId(entidadId);
        if (entidadTipo == null) {
            throw new IllegalArgumentException("El tipo de entidad es obligatorio.");
        }
        return query(SELECT + " WHERE entidad_tipo = ? AND entidad_id = ? ORDER BY fecha_programada",
                ps -> {
                    JdbcSupport.setEnum(ps, 1, entidadTipo);
                    ps.setLong(2, entidadId);
                });
    }

    @Override protected String insertSql() { return INSERT; }
    @Override protected String selectSql() { return SELECT; }
    @Override protected String updateSql() { return UPDATE; }
    @Override protected String deleteSql() { return DELETE; }
    @Override protected String idColumn() { return "id_tarea"; }
    @Override protected String entityName() { return "la tarea"; }
    @Override protected void bindInsert(PreparedStatement ps, Tarea t) throws SQLException { bind(ps, t); }
    @Override protected void bindUpdate(PreparedStatement ps, Tarea t) throws SQLException {
        bind(ps, t);
        ps.setLong(11, t.getIdTarea());
    }

    private void bind(PreparedStatement ps, Tarea t) throws SQLException {
        JdbcSupport.setEnum(ps, 1, t.getTipo());
        JdbcSupport.setEnum(ps, 2, t.getEntidadTipo());
        ps.setLong(3, t.getEntidadId());
        ps.setLong(4, t.getAgente().getIdAgente());
        ps.setString(5, t.getDescripcion());
        JdbcSupport.setTimestamp(ps, 6, t.getFechaProgramada());
        JdbcSupport.setTimestamp(ps, 7, t.getFechaRecordatorio());
        JdbcSupport.setTimestamp(ps, 8, t.getFechaCompletada());
        JdbcSupport.setEnum(ps, 9, t.getEstado());
        JdbcSupport.setEnum(ps, 10, t.getPrioridad());
    }

    @Override protected void bindDelete(PreparedStatement ps, Long id) throws SQLException { ps.setLong(1, id); }

    @Override protected Tarea mapRow(ResultSet rs) throws SQLException {
        Tarea t = new Tarea();
        t.setIdTarea(rs.getLong("id_tarea"));
        t.setTipo(JdbcSupport.getEnum(rs, "tipo", TipoTarea.class));
        t.setEntidadTipo(JdbcSupport.getEnum(rs, "entidad_tipo", TipoEntidad.class));
        t.setEntidadId(rs.getLong("entidad_id"));
        t.setAgente(JdbcSupport.agente(rs.getLong("id_agente")));
        t.setDescripcion(rs.getString("descripcion"));
        t.setFechaProgramada(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_programada")));
        t.setFechaRecordatorio(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_recordatorio")));
        t.setFechaCompletada(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_completada")));
        t.setEstado(JdbcSupport.getEnum(rs, "estado", EstadoTarea.class));
        t.setPrioridad(JdbcSupport.getEnum(rs, "prioridad", Prioridad.class));
        t.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        t.setFechaActualizacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion")));
        return t;
    }

    @Override protected void assignId(Tarea t, Long id) { t.setIdTarea(id); }

    @Override protected void validate(Tarea t, boolean requireId) {
        if (t == null) throw new IllegalArgumentException("La tarea es obligatoria.");
        if (requireId) JdbcSupport.validarId(t.getIdTarea());
        JdbcSupport.validarId(t.getEntidadId());
        JdbcSupport.validarId(t.getAgente() != null ? t.getAgente().getIdAgente() : null);
        if (t.getTipo() == null || t.getEntidadTipo() == null
                || t.getDescripcion() == null || t.getDescripcion().isBlank()
                || t.getFechaProgramada() == null || t.getEstado() == null || t.getPrioridad() == null) {
            throw new IllegalArgumentException("La tarea tiene campos obligatorios incompletos.");
        }
    }
}
