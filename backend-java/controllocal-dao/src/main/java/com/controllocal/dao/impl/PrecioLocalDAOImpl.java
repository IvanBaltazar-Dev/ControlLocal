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
import com.controllocal.dao.PrecioLocalDAO;
import com.controllocal.model.comercial.enums.HitoPrecio;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.inmueble.PrecioLocal;

public class PrecioLocalDAOImpl implements PrecioLocalDAO {

    private static final String INSERT_SQL = """
            INSERT INTO precio_local (id_local, hito, moneda, monto, fecha)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT id_precio, id_local, hito, moneda, monto, fecha, fecha_creacion
            FROM precio_local
            """;
    private static final String UPDATE_SQL = """
            UPDATE precio_local
            SET id_local = ?, hito = ?, moneda = ?, monto = ?, fecha = ?
            WHERE id_precio = ?
            """;
    private static final String DELETE_SQL = "DELETE FROM precio_local WHERE id_precio = ?";
    private static final String INSERT_PRECIO_SQL =
            "INSERT INTO precio_local (id_local, hito, moneda, monto, fecha) VALUES (?, ?, ?, ?, ?)";
    @Override
    public Long crear(PrecioLocal precio) {
        validar(precio, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, precio.getIdLocal());
            JdbcSupport.setEnum(ps, 2, precio.getHito());
            JdbcSupport.setEnum(ps, 3, precio.getMoneda());
            ps.setBigDecimal(4, precio.getMonto());
            JdbcSupport.setDate(ps, 5, precio.getFecha());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    precio.setIdPrecio(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id del precio del local.");
        } catch (SQLException e) {
            throw new DAOException("Error al registrar el precio del local " + precio.getIdLocal() + ".", e);
        }
    }

    @Override
    public Optional<PrecioLocal> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_precio = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar el precio con id " + id + ".", e);
        }
    }

    @Override
    public List<PrecioLocal> listarTodos() {
        return listar(SELECT_SQL + " ORDER BY id_local, fecha, id_precio", ps -> { });
    }

    @Override
    public List<PrecioLocal> listarPorLocal(Long idLocal) {
        JdbcSupport.validarId(idLocal);
        return listar(SELECT_SQL + " WHERE id_local = ? ORDER BY fecha, id_precio",
                ps -> ps.setLong(1, idLocal));
    }

    @Override
    public boolean actualizar(PrecioLocal precio) {
        validar(precio, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setLong(1, precio.getIdLocal());
            JdbcSupport.setEnum(ps, 2, precio.getHito());
            JdbcSupport.setEnum(ps, 3, precio.getMoneda());
            ps.setBigDecimal(4, precio.getMonto());
            JdbcSupport.setDate(ps, 5, precio.getFecha());
            ps.setLong(6, precio.getIdPrecio());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar el precio con id " + precio.getIdPrecio() + ".", e);
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
            throw new DAOException("Error al eliminar el precio con id " + id + ".", e);
        }
    }
    @Override
    public void registrar(Long idLocal, String hito, String moneda, java.math.BigDecimal monto, java.time.LocalDate fecha) {
        try (Connection connection = DBManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_PRECIO_SQL)) {

            statement.setLong(1, idLocal);
            statement.setString(2, hito);
            statement.setString(3, moneda);
            statement.setBigDecimal(4, monto);
            statement.setDate(5, java.sql.Date.valueOf(fecha));

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DAOException("Error al registrar historial de precio", e);
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private List<PrecioLocal> listar(String sql, Binder binder) {
        List<PrecioLocal> precios = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    precios.add(mapRow(rs));
                }
            }
            return precios;
        } catch (SQLException e) {
            throw new DAOException("Error al listar precios de locales.", e);
        }
    }

    private PrecioLocal mapRow(ResultSet rs) throws SQLException {
        PrecioLocal precio = new PrecioLocal();
        precio.setIdPrecio(rs.getLong("id_precio"));
        precio.setIdLocal(rs.getLong("id_local"));
        precio.setHito(JdbcSupport.getEnum(rs, "hito", HitoPrecio.class));
        precio.setMoneda(JdbcSupport.getEnum(rs, "moneda", Moneda.class));
        precio.setMonto(rs.getBigDecimal("monto"));
        precio.setFecha(JdbcSupport.toLocalDate(rs.getDate("fecha")));
        precio.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        return precio;
    }

    private void validar(PrecioLocal precio, boolean requiereId) {
        if (precio == null) {
            throw new IllegalArgumentException("El precio del local no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(precio.getIdPrecio());
        }
        JdbcSupport.validarId(precio.getIdLocal());
        if (precio.getHito() == null || precio.getMoneda() == null) {
            throw new IllegalArgumentException("El hito y la moneda del precio son obligatorios.");
        }
        if (precio.getMonto() == null || precio.getMonto().signum() < 0) {
            throw new IllegalArgumentException("El monto del precio no puede ser negativo.");
        }
        if (precio.getFecha() == null) {
            throw new IllegalArgumentException("La fecha del precio es obligatoria.");
        }
    }
}
