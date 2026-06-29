package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.controllocal.config.DBManager;
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

    // Solo contratos que tienen solicitud (los que muestra la vista). El filtro por rol se
    // aplica en SQL para no traer la tabla completa por pagina.
    private static final String SELECT_CON_SOLICITUD = """
            SELECT c.id_contrato_alquiler, c.id_oportunidad, c.id_solicitud, c.fecha_cierre,
                   c.estado_contrato, c.incidencias, c.fecha_creacion, c.fecha_actualizacion
            FROM contrato_alquiler c
            INNER JOIN solicitud_alquiler s ON s.id_solicitud = c.id_solicitud
            """;
    private static final String COUNT_CON_SOLICITUD =
            "SELECT COUNT(*) FROM contrato_alquiler c"
            + " INNER JOIN solicitud_alquiler s ON s.id_solicitud = c.id_solicitud";

    @Override public Optional<ContratoAlquiler> buscarPorOportunidad(Long idOportunidad) {
        JdbcSupport.validarId(idOportunidad);
        return query(SELECT + " WHERE id_oportunidad = ?", ps -> ps.setLong(1, idOportunidad))
                .stream().findFirst();
    }

    @Override
    public List<ContratoAlquiler> listarPaginaFiltrado(Long idAgente, Collection<Long> idsCaptacion,
            int limite, int desplazamiento) {
        if (idsCaptacion != null && idsCaptacion.isEmpty()) {
            return new ArrayList<>();
        }
        StringBuilder sql = new StringBuilder(SELECT_CON_SOLICITUD);
        List<Object> params = new ArrayList<>();
        aplicarFiltroRol(sql, params, idAgente, idsCaptacion);
        sql.append(" ORDER BY c.id_contrato_alquiler LIMIT ? OFFSET ?");
        params.add(Math.max(0, limite));
        params.add(Math.max(0, desplazamiento));
        return query(sql.toString(), ps -> bind(ps, params));
    }

    @Override
    public long contarFiltrado(Long idAgente, Collection<Long> idsCaptacion) {
        if (idsCaptacion != null && idsCaptacion.isEmpty()) {
            return 0L;
        }
        StringBuilder sql = new StringBuilder(COUNT_CON_SOLICITUD);
        List<Object> params = new ArrayList<>();
        aplicarFiltroRol(sql, params, idAgente, idsCaptacion);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw failure("contar", e);
        }
    }

    // agente -> WHERE s.id_agente = ?; broker -> JOIN oportunidad + WHERE o.id_captacion IN (...);
    // admin (ambos null) -> sin filtro.
    private static void aplicarFiltroRol(StringBuilder sql, List<Object> params,
            Long idAgente, Collection<Long> idsCaptacion) {
        if (idAgente != null) {
            sql.append(" WHERE s.id_agente = ?");
            params.add(idAgente);
        } else if (idsCaptacion != null) {
            sql.append(" INNER JOIN oportunidad_comercial o ON o.id_oportunidad = s.id_oportunidad")
               .append(" WHERE o.id_captacion IN (")
               .append(JdbcSupport.placeholders(idsCaptacion.size()))
               .append(")");
            params.addAll(idsCaptacion);
        }
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
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
