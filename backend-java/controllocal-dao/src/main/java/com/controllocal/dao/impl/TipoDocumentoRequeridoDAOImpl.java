package com.controllocal.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.controllocal.dao.TipoDocumentoRequeridoDAO;
import com.controllocal.model.comercial.TipoDocumentoRequerido;
import com.controllocal.model.comercial.enums.OperacionRequerimiento;

public class TipoDocumentoRequeridoDAOImpl extends AbstractJdbcCrudDAO<TipoDocumentoRequerido>
        implements TipoDocumentoRequeridoDAO {
    private static final String INSERT = """
            INSERT INTO tipo_documento_requerido (
                tipo_operacion, tipo_documento, obligatorio, activo, descripcion
            ) VALUES (?, ?, ?, ?, ?)
            """;
    private static final String SELECT = """
            SELECT id_tipo_documento_requerido, tipo_operacion, tipo_documento,
                   obligatorio, activo, descripcion
            FROM tipo_documento_requerido
            """;
    private static final String UPDATE = """
            UPDATE tipo_documento_requerido SET tipo_operacion = ?, tipo_documento = ?,
                obligatorio = ?, activo = ?, descripcion = ?
            WHERE id_tipo_documento_requerido = ?
            """;
    private static final String DELETE =
            "UPDATE tipo_documento_requerido SET activo = FALSE WHERE id_tipo_documento_requerido = ?";

    @Override public List<TipoDocumentoRequerido> listarRequeridos(OperacionRequerimiento tipoOperacion) {
        if (tipoOperacion == null) throw new IllegalArgumentException("El tipo de operacion es obligatorio.");
        return query(SELECT + """
                 WHERE tipo_operacion = ? AND obligatorio = TRUE AND activo = TRUE
                 ORDER BY tipo_documento
                """, ps -> JdbcSupport.setEnum(ps, 1, tipoOperacion));
    }

    @Override public List<TipoDocumentoRequerido> listarFaltantes(
            Long idSolicitud, OperacionRequerimiento tipoOperacion) {
        JdbcSupport.validarId(idSolicitud);
        if (tipoOperacion == null) throw new IllegalArgumentException("El tipo de operacion es obligatorio.");
        return query(SELECT + """
                 t WHERE t.tipo_operacion = ? AND t.obligatorio = TRUE AND t.activo = TRUE
                   AND NOT EXISTS (
                       SELECT 1 FROM documento_solicitud d
                       WHERE d.id_solicitud = ?
                         AND d.id_tipo_documento_requerido = t.id_tipo_documento_requerido
                         AND d.estado IN ('R', 'V')
                   )
                 ORDER BY t.tipo_documento
                """, ps -> {
                    JdbcSupport.setEnum(ps, 1, tipoOperacion);
                    ps.setLong(2, idSolicitud);
                });
    }

    @Override protected String insertSql() { return INSERT; }
    @Override protected String selectSql() { return SELECT; }
    @Override protected String updateSql() { return UPDATE; }
    @Override protected String deleteSql() { return DELETE; }
    @Override protected String idColumn() { return "id_tipo_documento_requerido"; }
    @Override protected String entityName() { return "el tipo de documento requerido"; }
    @Override protected void bindInsert(PreparedStatement ps, TipoDocumentoRequerido t) throws SQLException { bind(ps, t); }
    @Override protected void bindUpdate(PreparedStatement ps, TipoDocumentoRequerido t) throws SQLException {
        bind(ps, t);
        ps.setLong(6, t.getIdTipoDocumentoRequerido());
    }

    private void bind(PreparedStatement ps, TipoDocumentoRequerido t) throws SQLException {
        JdbcSupport.setEnum(ps, 1, t.getTipoOperacion());
        ps.setString(2, t.getTipoDocumento());
        ps.setBoolean(3, t.isObligatorio());
        ps.setBoolean(4, t.isActivo());
        ps.setString(5, t.getDescripcion());
    }

    @Override protected void bindDelete(PreparedStatement ps, Long id) throws SQLException { ps.setLong(1, id); }

    @Override protected TipoDocumentoRequerido mapRow(ResultSet rs) throws SQLException {
        TipoDocumentoRequerido t = new TipoDocumentoRequerido();
        t.setIdTipoDocumentoRequerido(rs.getLong("id_tipo_documento_requerido"));
        t.setTipoOperacion(JdbcSupport.getEnum(rs, "tipo_operacion", OperacionRequerimiento.class));
        t.setTipoDocumento(rs.getString("tipo_documento"));
        t.setObligatorio(rs.getBoolean("obligatorio"));
        t.setActivo(rs.getBoolean("activo"));
        t.setDescripcion(rs.getString("descripcion"));
        return t;
    }

    @Override protected void assignId(TipoDocumentoRequerido t, Long id) { t.setIdTipoDocumentoRequerido(id); }

    @Override protected void validate(TipoDocumentoRequerido t, boolean requireId) {
        if (t == null) throw new IllegalArgumentException("El tipo de documento requerido es obligatorio.");
        if (requireId) JdbcSupport.validarId(t.getIdTipoDocumentoRequerido());
        if (t.getTipoOperacion() == null || t.getTipoDocumento() == null || t.getTipoDocumento().isBlank()) {
            throw new IllegalArgumentException("El tipo de documento requerido tiene campos obligatorios incompletos.");
        }
    }
}
