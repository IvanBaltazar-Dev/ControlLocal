package com.controllocal.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.controllocal.dao.ReportePropietarioDAO;
import com.controllocal.model.comercial.ReportePropietario;
import com.controllocal.model.comercial.enums.CanalContacto;

public class ReportePropietarioDAOImpl extends AbstractJdbcCrudDAO<ReportePropietario>
        implements ReportePropietarioDAO {
    private static final String INSERT = """
            INSERT INTO reporte_propietario (
                id_captacion, id_agente, fecha_reporte, periodo_inicio, periodo_fin,
                consultas_reportadas, visitas_reportadas, objeciones_frecuentes,
                ajustes_recomendados, canal_envio
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT = """
            SELECT id_reporte_propietario, id_captacion, id_agente, fecha_reporte,
                   periodo_inicio, periodo_fin, consultas_reportadas, visitas_reportadas,
                   objeciones_frecuentes, ajustes_recomendados, canal_envio, fecha_creacion
            FROM reporte_propietario
            """;
    private static final String UPDATE = """
            UPDATE reporte_propietario SET id_captacion = ?, id_agente = ?,
                fecha_reporte = ?, periodo_inicio = ?, periodo_fin = ?,
                consultas_reportadas = ?, visitas_reportadas = ?,
                objeciones_frecuentes = ?, ajustes_recomendados = ?, canal_envio = ?
            WHERE id_reporte_propietario = ?
            """;
    private static final String DELETE = "DELETE FROM reporte_propietario WHERE id_reporte_propietario = ?";

    @Override public List<ReportePropietario> listarPorCaptacion(Long idCaptacion) {
        JdbcSupport.validarId(idCaptacion);
        return query(SELECT + " WHERE id_captacion = ? ORDER BY fecha_reporte DESC",
                ps -> ps.setLong(1, idCaptacion));
    }

    @Override protected String insertSql() { return INSERT; }
    @Override protected String selectSql() { return SELECT; }
    @Override protected String updateSql() { return UPDATE; }
    @Override protected String deleteSql() { return DELETE; }
    @Override protected String idColumn() { return "id_reporte_propietario"; }
    @Override protected String entityName() { return "el reporte de propietario"; }
    @Override protected void bindInsert(PreparedStatement ps, ReportePropietario r) throws SQLException { bind(ps, r); }
    @Override protected void bindUpdate(PreparedStatement ps, ReportePropietario r) throws SQLException {
        bind(ps, r);
        ps.setLong(11, r.getIdReportePropietario());
    }

    private void bind(PreparedStatement ps, ReportePropietario r) throws SQLException {
        ps.setLong(1, r.getCaptacion().getIdCaptacion());
        ps.setLong(2, r.getAgente().getIdAgente());
        JdbcSupport.setDate(ps, 3, r.getFechaReporte());
        JdbcSupport.setDate(ps, 4, r.getPeriodoInicio());
        JdbcSupport.setDate(ps, 5, r.getPeriodoFin());
        ps.setInt(6, r.getConsultasReportadas());
        ps.setInt(7, r.getVisitasReportadas());
        ps.setString(8, r.getObjecionesFrecuentes());
        ps.setString(9, r.getAjustesRecomendados());
        JdbcSupport.setEnum(ps, 10, r.getCanalEnvio());
    }

    @Override protected void bindDelete(PreparedStatement ps, Long id) throws SQLException { ps.setLong(1, id); }

    @Override protected ReportePropietario mapRow(ResultSet rs) throws SQLException {
        ReportePropietario r = new ReportePropietario();
        r.setIdReportePropietario(rs.getLong("id_reporte_propietario"));
        r.setCaptacion(JdbcSupport.captacion(rs.getLong("id_captacion")));
        r.setAgente(JdbcSupport.agente(rs.getLong("id_agente")));
        r.setFechaReporte(JdbcSupport.toLocalDate(rs.getDate("fecha_reporte")));
        r.setPeriodoInicio(JdbcSupport.toLocalDate(rs.getDate("periodo_inicio")));
        r.setPeriodoFin(JdbcSupport.toLocalDate(rs.getDate("periodo_fin")));
        r.setConsultasReportadas(rs.getInt("consultas_reportadas"));
        r.setVisitasReportadas(rs.getInt("visitas_reportadas"));
        r.setObjecionesFrecuentes(rs.getString("objeciones_frecuentes"));
        r.setAjustesRecomendados(rs.getString("ajustes_recomendados"));
        r.setCanalEnvio(JdbcSupport.getEnum(rs, "canal_envio", CanalContacto.class));
        r.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        return r;
    }

    @Override protected void assignId(ReportePropietario r, Long id) { r.setIdReportePropietario(id); }

    @Override protected void validate(ReportePropietario r, boolean requireId) {
        if (r == null) throw new IllegalArgumentException("El reporte de propietario es obligatorio.");
        if (requireId) JdbcSupport.validarId(r.getIdReportePropietario());
        JdbcSupport.validarId(r.getCaptacion() != null ? r.getCaptacion().getIdCaptacion() : null);
        JdbcSupport.validarId(r.getAgente() != null ? r.getAgente().getIdAgente() : null);
        if (r.getFechaReporte() == null || r.getConsultasReportadas() == null
                || r.getVisitasReportadas() == null || r.getCanalEnvio() == null) {
            throw new IllegalArgumentException("El reporte de propietario tiene campos obligatorios incompletos.");
        }
    }
}
