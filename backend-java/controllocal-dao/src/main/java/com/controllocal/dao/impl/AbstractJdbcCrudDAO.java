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
import com.controllocal.dao.CrudDAO;
import com.controllocal.dao.DAOException;

abstract class AbstractJdbcCrudDAO<T> implements CrudDAO<T> {

    protected abstract String insertSql();
    protected abstract String selectSql();
    protected abstract String updateSql();
    protected abstract String deleteSql();
    protected abstract String idColumn();
    protected abstract String entityName();
    protected abstract void bindInsert(PreparedStatement ps, T entity) throws SQLException;
    protected abstract void bindUpdate(PreparedStatement ps, T entity) throws SQLException;
    protected abstract void bindDelete(PreparedStatement ps, Long id) throws SQLException;
    protected abstract T mapRow(ResultSet rs) throws SQLException;
    protected abstract void assignId(T entity, Long id);
    protected abstract void validate(T entity, boolean requireId);

    @Override
    public Long crear(T entity) {
        try (Connection conn = DBManager.getConnection()) {
            return crear(entity, conn);
        } catch (SQLException e) {
            throw failure("crear", e);
        }
    }

    @Override
    public Long crear(T entity, Connection conn) throws SQLException {
        validate(entity, false);
        try (PreparedStatement ps = conn.prepareStatement(insertSql(), Statement.RETURN_GENERATED_KEYS)) {
            bindInsert(ps, entity);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    assignId(entity, id);
                    return id;
                }
            }
        }
        throw new DAOException("No se genero el id de " + entityName() + ".");
    }

    @Override
    public Optional<T> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection()) {
            return buscarPorId(id, conn);
        } catch (SQLException e) {
            throw failure("buscar", e);
        }
    }

    @Override
    public Optional<T> buscarPorId(Long id, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(selectSql() + " WHERE " + idColumn() + " = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<T> listarTodos() {
        try (Connection conn = DBManager.getConnection()) {
            return listarTodos(conn);
        } catch (SQLException e) {
            throw failure("listar", e);
        }
    }

    @Override
    public List<T> listarTodos(Connection conn) throws SQLException {
        return query(conn, selectSql() + " ORDER BY " + idColumn(), ps -> {});
    }

    @Override
    public boolean actualizar(T entity) {
        try (Connection conn = DBManager.getConnection()) {
            return actualizar(entity, conn);
        } catch (SQLException e) {
            throw failure("actualizar", e);
        }
    }

    @Override
    public boolean actualizar(T entity, Connection conn) throws SQLException {
        validate(entity, true);
        try (PreparedStatement ps = conn.prepareStatement(updateSql())) {
            bindUpdate(ps, entity);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean eliminar(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection()) {
            return eliminar(id, conn);
        } catch (SQLException e) {
            throw failure("eliminar", e);
        }
    }

    @Override
    public boolean eliminar(Long id, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(deleteSql())) {
            bindDelete(ps, id);
            return ps.executeUpdate() > 0;
        }
    }

    protected List<T> query(String sql, SqlBinder binder) {
        try (Connection conn = DBManager.getConnection()) {
            return query(conn, sql, binder);
        } catch (SQLException e) {
            throw failure("consultar", e);
        }
    }

    protected List<T> query(Connection conn, String sql, SqlBinder binder) throws SQLException {
        List<T> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    protected DAOException failure(String operation, SQLException cause) {
        return new DAOException("Error al " + operation + " " + entityName() + ".", cause);
    }

    @FunctionalInterface
    protected interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }
}
