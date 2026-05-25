package com.controllocal.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface CrudDAO<T> {

    Long crear(T entidad);

    Optional<T> buscarPorId(Long id);

    List<T> listarTodos();

    boolean actualizar(T entidad);

    boolean eliminar(Long id);

    default Long crear(T entidad, Connection conn) throws SQLException {
        return crear(entidad);
    }

    default Optional<T> buscarPorId(Long id, Connection conn) throws SQLException {
        return buscarPorId(id);
    }

    default List<T> listarTodos(Connection conn) throws SQLException {
        return listarTodos();
    }

    default boolean actualizar(T entidad, Connection conn) throws SQLException {
        return actualizar(entidad);
    }

    default boolean eliminar(Long id, Connection conn) throws SQLException {
        return eliminar(id);
    }
}
