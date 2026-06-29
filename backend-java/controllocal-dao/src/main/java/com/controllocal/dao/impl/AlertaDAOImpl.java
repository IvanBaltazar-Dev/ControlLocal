package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.controllocal.config.DBManager;
import com.controllocal.dao.AlertaDAO;
import com.controllocal.dao.DAOException;
import com.controllocal.model.comercial.Alerta;
import com.controllocal.model.comercial.enums.EstadoAlerta;
import com.controllocal.model.comercial.enums.Severidad;
import com.controllocal.model.comercial.enums.TipoAlerta;
import com.controllocal.model.comercial.enums.TipoEntidad;

public class AlertaDAOImpl extends AbstractJdbcCrudDAO<Alerta> implements AlertaDAO {
    private static final String INSERT = """
            INSERT INTO alerta (
                tipo, severidad, entidad_tipo, entidad_id, id_agente,
                mensaje, estado, fecha_generacion, fecha_resolucion
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT = """
            SELECT id_alerta, tipo, severidad, entidad_tipo, entidad_id, id_agente,
                   mensaje, estado, fecha_generacion, fecha_resolucion
            FROM alerta
            """;
    private static final String UPDATE = """
            UPDATE alerta SET tipo = ?, severidad = ?, entidad_tipo = ?, entidad_id = ?,
                id_agente = ?, mensaje = ?, estado = ?, fecha_generacion = ?,
                fecha_resolucion = ?
            WHERE id_alerta = ?
            """;
    private static final String DELETE =
            "UPDATE alerta SET estado = 'DESCARTADA', fecha_resolucion = CURRENT_TIMESTAMP WHERE id_alerta = ?";
    private static final String INSERT_ALERTA_SQL = """
            INSERT INTO alerta (
                tipo, severidad, entidad_tipo, entidad_id, 
                id_agente, mensaje, estado, fecha_generacion
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    @Override public List<Alerta> listarActivasPorAgente(Long idAgente) {
        JdbcSupport.validarId(idAgente);
        return query(SELECT + " WHERE id_agente = ? AND estado = 'ACTIVA' ORDER BY fecha_generacion DESC",
                ps -> ps.setLong(1, idAgente));
    }

    @Override public List<Alerta> listarActivasPorBroker(Long idBroker) {
        JdbcSupport.validarId(idBroker);
        return query("""
                SELECT a.id_alerta, a.tipo, a.severidad, a.entidad_tipo, a.entidad_id, a.id_agente,
                       a.mensaje, a.estado, a.fecha_generacion, a.fecha_resolucion
                FROM alerta a
                JOIN broker_agente ba ON ba.id_agente = a.id_agente AND ba.estado = 'A'
                WHERE ba.id_broker = ? AND a.estado = 'ACTIVA'
                ORDER BY a.fecha_generacion DESC
                """, ps -> ps.setLong(1, idBroker));
    }

    @Override public List<Alerta> listarActivas() {
        return query(SELECT + " WHERE estado = 'ACTIVA' ORDER BY fecha_generacion DESC", ps -> { });
    }

    @Override public List<Alerta> listarActivasPorEntidad(TipoEntidad entidadTipo, Long entidadId) {
        JdbcSupport.validarId(entidadId);
        return query(SELECT + " WHERE entidad_tipo = ? AND entidad_id = ? AND estado = 'ACTIVA'"
                + " ORDER BY fecha_generacion DESC",
                ps -> {
                    JdbcSupport.setEnum(ps, 1, entidadTipo);
                    ps.setLong(2, entidadId);
                });
    }

    @Override public boolean marcarAtendida(Long idAlerta) {
        JdbcSupport.validarId(idAlerta);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE alerta SET estado = 'ATENDIDA', fecha_resolucion = CURRENT_TIMESTAMP "
                             + "WHERE id_alerta = ? AND estado = 'ACTIVA'")) {
            ps.setLong(1, idAlerta);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al marcar la alerta como atendida.", e);
        }
    }

    @Override
    public void crearAlertaSensible(Long idLocal, Long idAgente, String mensaje) {
        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_ALERTA_SQL)) {

            // Se utiliza 'SOLICITUD_EVALUADA' por las restricciones del CHECK ck_alerta_tipo en la BD
            statement.setString(1, "SOLICITUD_EVALUADA");
            statement.setString(2, "MEDIA");
            statement.setString(3, "INMUEBLE");
            statement.setLong(4, idLocal);
            statement.setLong(5, idAgente);
            statement.setString(6, mensaje);
            statement.setString(7, "ACTIVA");
            statement.setTimestamp(8, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DAOException("Error al registrar la alerta comercial", e);
        }
    }

    @Override protected String insertSql() { return INSERT; }
    @Override protected String selectSql() { return SELECT; }
    @Override protected String updateSql() { return UPDATE; }
    @Override protected String deleteSql() { return DELETE; }
    @Override protected String idColumn() { return "id_alerta"; }
    @Override protected String entityName() { return "la alerta"; }
    @Override protected void bindInsert(PreparedStatement ps, Alerta a) throws SQLException { bind(ps, a); }
    @Override protected void bindUpdate(PreparedStatement ps, Alerta a) throws SQLException {
        bind(ps, a);
        ps.setLong(10, a.getIdAlerta());
    }

    private void bind(PreparedStatement ps, Alerta a) throws SQLException {
        JdbcSupport.setEnum(ps, 1, a.getTipo());
        JdbcSupport.setEnum(ps, 2, a.getSeveridad());
        JdbcSupport.setEnum(ps, 3, a.getEntidadTipo());
        ps.setLong(4, a.getEntidadId());
        ps.setLong(5, a.getAgente().getIdAgente());
        ps.setString(6, a.getMensaje());
        JdbcSupport.setEnum(ps, 7, a.getEstado());
        JdbcSupport.setTimestamp(ps, 8, a.getFechaGeneracion());
        JdbcSupport.setTimestamp(ps, 9, a.getFechaResolucion());
    }

    @Override protected void bindDelete(PreparedStatement ps, Long id) throws SQLException { ps.setLong(1, id); }

    @Override protected Alerta mapRow(ResultSet rs) throws SQLException {
        Alerta a = new Alerta();
        a.setIdAlerta(rs.getLong("id_alerta"));
        a.setTipo(JdbcSupport.getEnum(rs, "tipo", TipoAlerta.class));
        a.setSeveridad(JdbcSupport.getEnum(rs, "severidad", Severidad.class));
        a.setEntidadTipo(JdbcSupport.getEnum(rs, "entidad_tipo", TipoEntidad.class));
        a.setEntidadId(rs.getLong("entidad_id"));
        a.setAgente(JdbcSupport.agente(rs.getLong("id_agente")));
        a.setMensaje(rs.getString("mensaje"));
        a.setEstado(JdbcSupport.getEnum(rs, "estado", EstadoAlerta.class));
        a.setFechaGeneracion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_generacion")));
        a.setFechaResolucion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_resolucion")));
        return a;
    }

    @Override protected void assignId(Alerta a, Long id) { a.setIdAlerta(id); }

    @Override protected void validate(Alerta a, boolean requireId) {
        if (a == null) throw new IllegalArgumentException("La alerta es obligatoria.");
        if (requireId) JdbcSupport.validarId(a.getIdAlerta());
        JdbcSupport.validarId(a.getEntidadId());
        JdbcSupport.validarId(a.getAgente() != null ? a.getAgente().getIdAgente() : null);
        if (a.getTipo() == null || a.getSeveridad() == null || a.getEntidadTipo() == null
                || a.getMensaje() == null || a.getMensaje().isBlank()
                || a.getEstado() == null || a.getFechaGeneracion() == null) {
            throw new IllegalArgumentException("La alerta tiene campos obligatorios incompletos.");
        }
    }
}
