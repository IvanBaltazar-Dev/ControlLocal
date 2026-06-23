package com.controllocal.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import com.controllocal.dao.ContratoAlquilerDAO;
import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.enums.EstadoContrato;

public class ContratoAlquilerDAOImpl extends AbstractJdbcCrudDAO<ContratoAlquiler>
        implements ContratoAlquilerDAO {
    private static final String INSERT = """
            INSERT INTO contrato_alquiler (
                id_oportunidad, id_solicitud, fecha_cierre, estado_contrato, incidencias
            ) VALUES (?, ?, ?, ?, ?)
            """;
    private static final String SELECT = """
            SELECT id_contrato_alquiler, id_oportunidad, id_solicitud, fecha_cierre,
                   estado_contrato, incidencias, fecha_creacion, fecha_actualizacion
            FROM contrato_alquiler
            """;
    private static final String UPDATE = """
            UPDATE contrato_alquiler SET id_oportunidad = ?, id_solicitud = ?,
                fecha_cierre = ?, estado_contrato = ?, incidencias = ?
            WHERE id_contrato_alquiler = ?
            """;
    private static final String DELETE =
            "UPDATE contrato_alquiler SET estado_contrato = 'ANULADO' WHERE id_contrato_alquiler = ?";

    @Override public Optional<ContratoAlquiler> buscarPorOportunidad(Long idOportunidad) {
        JdbcSupport.validarId(idOportunidad);
        return query(SELECT + " WHERE id_oportunidad = ?", ps -> ps.setLong(1, idOportunidad))
                .stream().findFirst();
    }

    @Override protected String insertSql() { return INSERT; }
    @Override protected String selectSql() { return SELECT; }
    @Override protected String updateSql() { return UPDATE; }
    @Override protected String deleteSql() { return DELETE; }
    @Override protected String idColumn() { return "id_contrato_alquiler"; }
    @Override protected String entityName() { return "el contrato de alquiler"; }
    @Override protected void bindInsert(PreparedStatement ps, ContratoAlquiler c) throws SQLException { bind(ps, c); }
    @Override protected void bindUpdate(PreparedStatement ps, ContratoAlquiler c) throws SQLException {
        bind(ps, c);
        ps.setLong(6, c.getIdContratoAlquiler());
    }

    private void bind(PreparedStatement ps, ContratoAlquiler c) throws SQLException {
        ps.setLong(1, c.getOportunidad().getIdOportunidad());
        JdbcSupport.setLong(ps, 2, c.getSolicitudAlquiler() != null ? c.getSolicitudAlquiler().getIdSolicitud() : null);
        JdbcSupport.setDate(ps, 3, c.getFechaCierre());
        JdbcSupport.setEnum(ps, 4, c.getEstadoContrato());
        ps.setString(5, c.getIncidencias());
    }

    @Override protected void bindDelete(PreparedStatement ps, Long id) throws SQLException { ps.setLong(1, id); }

    @Override protected ContratoAlquiler mapRow(ResultSet rs) throws SQLException {
        ContratoAlquiler c = new ContratoAlquiler();
        c.setIdContratoAlquiler(rs.getLong("id_contrato_alquiler"));
        c.setOportunidad(JdbcSupport.oportunidad(rs.getLong("id_oportunidad")));
        Long idSolicitud = rs.getObject("id_solicitud", Long.class);
        if (idSolicitud != null) c.setSolicitudAlquiler(JdbcSupport.solicitud(idSolicitud));
        c.setFechaCierre(JdbcSupport.toLocalDate(rs.getDate("fecha_cierre")));
        c.setEstadoContrato(JdbcSupport.getEnum(rs, "estado_contrato", EstadoContrato.class));
        c.setIncidencias(rs.getString("incidencias"));
        c.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        c.setFechaActualizacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion")));
        return c;
    }

    @Override protected void assignId(ContratoAlquiler c, Long id) { c.setIdContratoAlquiler(id); }

    @Override protected void validate(ContratoAlquiler c, boolean requireId) {
        if (c == null) throw new IllegalArgumentException("El contrato de alquiler es obligatorio.");
        if (requireId) JdbcSupport.validarId(c.getIdContratoAlquiler());
        JdbcSupport.validarId(c.getOportunidad() != null ? c.getOportunidad().getIdOportunidad() : null);
        if (c.getSolicitudAlquiler() != null) JdbcSupport.validarId(c.getSolicitudAlquiler().getIdSolicitud());
        if (c.getFechaCierre() == null || c.getEstadoContrato() == null) {
            throw new IllegalArgumentException("El contrato de alquiler tiene campos obligatorios incompletos.");
        }
    }
}
