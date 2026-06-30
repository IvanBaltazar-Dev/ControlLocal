package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.FotoLocalDAO;
import com.controllocal.model.inmueble.FotoLocal;

public class FotoLocalDAOImpl implements FotoLocalDAO {

    private static final String INSERT_SQL = """
            INSERT INTO foto_local (id_local, clave, nombre_archivo, orden)
            VALUES (?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT id_foto, id_local, clave, nombre_archivo, orden, fecha_registro
            FROM foto_local
            """;

    @Override
    public Long crear(FotoLocal foto) {
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, foto.getIdLocal());
            ps.setString(2, foto.getClave());
            ps.setString(3, foto.getNombreArchivo());
            ps.setInt(4, foto.getOrden());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    foto.setIdFoto(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de la foto del local.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear la foto del local.", e);
        }
    }

    @Override
    public List<FotoLocal> listarPorLocal(Long idLocal) {
        JdbcSupport.validarId(idLocal);
        List<FotoLocal> fotos = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SELECT_SQL + " WHERE id_local = ? ORDER BY orden, id_foto")) {
            ps.setLong(1, idLocal);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fotos.add(mapRow(rs));
                }
            }
            return fotos;
        } catch (SQLException e) {
            throw new DAOException("Error al listar las fotos del local " + idLocal + ".", e);
        }
    }

    @Override
    public Map<Long, String> listarClavesPortada(Collection<Long> idsLocal) {
        List<Long> ids = idsLocal == null ? List.of() : idsLocal.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> portadas = new LinkedHashMap<>();
        String sql = SELECT_SQL
                + " WHERE id_local IN (" + JdbcSupport.placeholders(ids.size()) + ")"
                + " ORDER BY id_local, orden, id_foto";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    portadas.putIfAbsent(rs.getLong("id_local"), rs.getString("clave"));
                }
            }
            return portadas;
        } catch (SQLException e) {
            throw new DAOException("Error al listar las portadas de locales.", e);
        }
    }

    @Override
    public Optional<FotoLocal> buscarPorId(Long idFoto) {
        JdbcSupport.validarId(idFoto);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_foto = ?")) {
            ps.setLong(1, idFoto);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar la foto " + idFoto + ".", e);
        }
    }

    @Override
    public boolean eliminar(Long idFoto) {
        JdbcSupport.validarId(idFoto);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM foto_local WHERE id_foto = ?")) {
            ps.setLong(1, idFoto);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al eliminar la foto " + idFoto + ".", e);
        }
    }

    private static FotoLocal mapRow(ResultSet rs) throws SQLException {
        FotoLocal foto = new FotoLocal();
        foto.setIdFoto(rs.getLong("id_foto"));
        foto.setIdLocal(rs.getLong("id_local"));
        foto.setClave(rs.getString("clave"));
        foto.setNombreArchivo(rs.getString("nombre_archivo"));
        foto.setOrden(rs.getInt("orden"));
        foto.setFechaRegistro(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_registro")));
        return foto;
    }
}
