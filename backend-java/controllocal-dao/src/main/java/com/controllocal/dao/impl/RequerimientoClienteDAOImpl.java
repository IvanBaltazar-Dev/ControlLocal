package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.RequerimientoClienteDAO;
import com.controllocal.model.comercial.RequerimientoCliente;
import com.controllocal.model.comercial.enums.EstadoRequerimiento;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.comercial.enums.TipoInmuebleComercial;
import com.controllocal.model.inmueble.Distrito;

public class RequerimientoClienteDAOImpl extends AbstractJdbcCrudDAO<RequerimientoCliente>
        implements RequerimientoClienteDAO {
    private static final String INSERT = """
            INSERT INTO requerimiento_cliente (
                id_cliente, rubro, tipo_inmueble, renta_min, renta_max, moneda,
                metraje_min, metraje_max, frente_minimo, estado, observaciones
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT = """
            SELECT id_requerimiento, id_cliente, rubro, tipo_inmueble, renta_min,
                   renta_max, moneda, metraje_min, metraje_max, frente_minimo,
                   estado, observaciones, fecha_creacion, fecha_actualizacion
            FROM requerimiento_cliente
            """;
    private static final String UPDATE = """
            UPDATE requerimiento_cliente SET id_cliente = ?, rubro = ?,
                tipo_inmueble = ?, renta_min = ?, renta_max = ?, moneda = ?,
                metraje_min = ?, metraje_max = ?, frente_minimo = ?, estado = ?,
                observaciones = ?
            WHERE id_requerimiento = ?
            """;
    private static final String DELETE = "DELETE FROM requerimiento_cliente WHERE id_requerimiento = ?";

    @Override
    public Long crear(RequerimientoCliente r) {
        try (Connection conn = DBManager.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                Long id = crear(r, conn);
                conn.commit();
                return id;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw failure("crear", e);
        }
    }

    @Override
    public Long crear(RequerimientoCliente r, Connection conn) throws SQLException {
        Long id = super.crear(r, conn);
        guardarDistritos(conn, r);
        return id;
    }

    @Override
    public boolean actualizar(RequerimientoCliente r) {
        try (Connection conn = DBManager.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                boolean updated = actualizar(r, conn);
                conn.commit();
                return updated;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw failure("actualizar", e);
        }
    }

    @Override
    public boolean actualizar(RequerimientoCliente r, Connection conn) throws SQLException {
        boolean updated = super.actualizar(r, conn);
        if (updated) {
            guardarDistritos(conn, r);
        }
        return updated;
    }

    @Override
    public Optional<RequerimientoCliente> buscarPorId(Long id) {
        Optional<RequerimientoCliente> result = super.buscarPorId(id);
        result.ifPresent(this::cargarDistritos);
        return result;
    }

    @Override
    public List<RequerimientoCliente> listarTodos() {
        List<RequerimientoCliente> result = super.listarTodos();
        // Carga de distritos en bloque (1 consulta) en vez de una por requerimiento: el
        // matching de cartera (coincidencias) llama listarTodos y el N+1 lo hacia colgar.
        cargarDistritosEnBloque(result);
        return result;
    }

    @Override
    public List<RequerimientoCliente> listarPorCliente(Long idCliente) {
        JdbcSupport.validarId(idCliente);
        List<RequerimientoCliente> result = query(
                SELECT + " WHERE id_cliente = ? ORDER BY id_requerimiento",
                ps -> ps.setLong(1, idCliente));
        result.forEach(this::cargarDistritos);
        return result;
    }

    @Override
    public List<RequerimientoCliente> listarPorClientes(Collection<Long> idsCliente) {
        if (idsCliente == null || idsCliente.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = new ArrayList<>(idsCliente);
        String sql = SELECT + " WHERE id_cliente IN (" + JdbcSupport.placeholders(ids.size())
                + ") ORDER BY id_requerimiento";
        List<RequerimientoCliente> result = query(sql, ps -> {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
        });
        cargarDistritosEnBloque(result);
        return result;
    }

    private void guardarDistritos(Connection conn, RequerimientoCliente r) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement(
                "DELETE FROM requerimiento_distrito WHERE id_requerimiento = ?")) {
            delete.setLong(1, r.getIdRequerimiento());
            delete.executeUpdate();
        }
        if (r.getDistritos() == null || r.getDistritos().isEmpty()) {
            return;
        }
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO requerimiento_distrito (id_requerimiento, id_distrito) VALUES (?, ?)")) {
            for (Distrito distrito : r.getDistritos()) {
                JdbcSupport.validarId(distrito != null ? distrito.getIdDistrito() : null);
                insert.setLong(1, r.getIdRequerimiento());
                insert.setLong(2, distrito.getIdDistrito());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void cargarDistritos(RequerimientoCliente r) {
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT d.id_distrito, d.nombre, d.provincia, d.activo
                     FROM distrito d
                     INNER JOIN requerimiento_distrito rd ON rd.id_distrito = d.id_distrito
                     WHERE rd.id_requerimiento = ?
                     ORDER BY d.nombre
                     """)) {
            ps.setLong(1, r.getIdRequerimiento());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    r.getDistritos().add(new Distrito(
                            rs.getLong("id_distrito"),
                            rs.getString("nombre"),
                            rs.getString("provincia"),
                            rs.getBoolean("activo")));
                }
            }
        } catch (SQLException e) {
            throw new DAOException("Error al cargar los distritos del requerimiento.", e);
        }
    }

    // Carga los distritos de varios requerimientos en UNA sola consulta (vs. una por
    // requerimiento). Evita el N+1 que disparaba el matching de cartera de la bandeja.
    private void cargarDistritosEnBloque(List<RequerimientoCliente> requerimientos) {
        if (requerimientos == null || requerimientos.isEmpty()) {
            return;
        }
        Map<Long, RequerimientoCliente> porId = new HashMap<>();
        for (RequerimientoCliente r : requerimientos) {
            if (r.getIdRequerimiento() != null) {
                porId.put(r.getIdRequerimiento(), r);
            }
        }
        if (porId.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(porId.keySet());
        String sql = "SELECT rd.id_requerimiento, d.id_distrito, d.nombre, d.provincia, d.activo "
                + "FROM distrito d "
                + "INNER JOIN requerimiento_distrito rd ON rd.id_distrito = d.id_distrito "
                + "WHERE rd.id_requerimiento IN (" + JdbcSupport.placeholders(ids.size()) + ") "
                + "ORDER BY d.nombre";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RequerimientoCliente r = porId.get(rs.getLong("id_requerimiento"));
                    if (r == null) {
                        continue;
                    }
                    r.getDistritos().add(new Distrito(
                            rs.getLong("id_distrito"),
                            rs.getString("nombre"),
                            rs.getString("provincia"),
                            rs.getBoolean("activo")));
                }
            }
        } catch (SQLException e) {
            throw new DAOException("Error al cargar los distritos de los requerimientos.", e);
        }
    }

    @Override protected String insertSql() { return INSERT; }
    @Override protected String selectSql() { return SELECT; }
    @Override protected String updateSql() { return UPDATE; }
    @Override protected String deleteSql() { return DELETE; }
    @Override protected String idColumn() { return "id_requerimiento"; }
    @Override protected String entityName() { return "el requerimiento de cliente"; }
    @Override protected void bindInsert(PreparedStatement ps, RequerimientoCliente r) throws SQLException { bind(ps, r); }
    @Override protected void bindUpdate(PreparedStatement ps, RequerimientoCliente r) throws SQLException {
        bind(ps, r);
        ps.setLong(12, r.getIdRequerimiento());
    }

    private void bind(PreparedStatement ps, RequerimientoCliente r) throws SQLException {
        ps.setLong(1, r.getCliente().getIdCliente());
        ps.setString(2, r.getRubro());
        JdbcSupport.setEnum(ps, 3, r.getTipoInmueble());
        ps.setBigDecimal(4, r.getRentaMin());
        ps.setBigDecimal(5, r.getRentaMax());
        JdbcSupport.setEnum(ps, 6, r.getMoneda());
        ps.setBigDecimal(7, r.getMetrajeMin());
        ps.setBigDecimal(8, r.getMetrajeMax());
        ps.setBigDecimal(9, r.getFrenteMinimo());
        JdbcSupport.setEnum(ps, 10, r.getEstado());
        ps.setString(11, r.getObservaciones());
    }

    @Override protected void bindDelete(PreparedStatement ps, Long id) throws SQLException { ps.setLong(1, id); }

    @Override protected RequerimientoCliente mapRow(ResultSet rs) throws SQLException {
        RequerimientoCliente r = new RequerimientoCliente();
        r.setIdRequerimiento(rs.getLong("id_requerimiento"));
        r.setCliente(JdbcSupport.cliente(rs.getLong("id_cliente")));
        r.setRubro(rs.getString("rubro"));
        r.setTipoInmueble(JdbcSupport.getNullableEnum(rs, "tipo_inmueble", TipoInmuebleComercial.class));
        r.setRentaMin(rs.getBigDecimal("renta_min"));
        r.setRentaMax(rs.getBigDecimal("renta_max"));
        r.setMoneda(JdbcSupport.getEnum(rs, "moneda", Moneda.class));
        r.setMetrajeMin(rs.getBigDecimal("metraje_min"));
        r.setMetrajeMax(rs.getBigDecimal("metraje_max"));
        r.setFrenteMinimo(rs.getBigDecimal("frente_minimo"));
        r.setEstado(JdbcSupport.getEnum(rs, "estado", EstadoRequerimiento.class));
        r.setObservaciones(rs.getString("observaciones"));
        r.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        r.setFechaActualizacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion")));
        return r;
    }

    @Override protected void assignId(RequerimientoCliente r, Long id) { r.setIdRequerimiento(id); }

    @Override protected void validate(RequerimientoCliente r, boolean requireId) {
        if (r == null) throw new IllegalArgumentException("El requerimiento de cliente es obligatorio.");
        if (requireId) JdbcSupport.validarId(r.getIdRequerimiento());
        JdbcSupport.validarId(r.getCliente() != null ? r.getCliente().getIdCliente() : null);
        if (r.getRubro() == null || r.getRubro().isBlank()
                || r.getMoneda() == null || r.getEstado() == null) {
            throw new IllegalArgumentException("El requerimiento de cliente tiene campos obligatorios incompletos.");
        }
        if (r.getRentaMin() != null && r.getRentaMax() != null
                && r.getRentaMax().compareTo(r.getRentaMin()) < 0) {
            throw new IllegalArgumentException("La renta maxima no puede ser menor que la renta minima.");
        }
        if (r.getMetrajeMin() != null && r.getMetrajeMax() != null
                && r.getMetrajeMax().compareTo(r.getMetrajeMin()) < 0) {
            throw new IllegalArgumentException("El metraje maximo no puede ser menor que el metraje minimo.");
        }
    }
}
