package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.UsuarioInternoDAO;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.usuario.RolUsuarioInterno;
import com.controllocal.model.usuario.UsuarioInterno;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UsuarioInternoDAOImpl implements UsuarioInternoDAO {

    private static final String INSERT_SQL = """
            INSERT INTO usuario_interno (
                id_persona, nombre_usuario, contrasena_hash, estado_administrativo, rol
            ) VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT u.id_usuario, u.nombre_usuario, u.contrasena_hash, u.estado_administrativo, u.rol,
                   u.fecha_creacion AS usuario_fecha_creacion,
                   u.fecha_actualizacion AS usuario_fecha_actualizacion,
                   p.id_persona, p.tipo_persona, p.tipo_documento, p.numero_documento,
                   p.nombres_o_razon_social, p.telefono, p.correo, p.estado,
                   p.fecha_creacion, p.fecha_actualizacion
            FROM usuario_interno u
            INNER JOIN persona p ON u.id_persona = p.id_persona
            """;

    private static final String UPDATE_SQL = """
            UPDATE usuario_interno
            SET id_persona = ?, nombre_usuario = ?, contrasena_hash = ?,
                estado_administrativo = ?, rol = ?
            WHERE id_usuario = ?
            """;

    private static final String DELETE_SQL = "UPDATE usuario_interno SET estado_administrativo = 'INACTIVO' WHERE id_usuario = ?";

    @Override
    public Long crear(UsuarioInterno usuario) {
        validar(usuario, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, usuario.getPersona().getIdPersona());
            ps.setString(2, usuario.getNombreUsuario());
            ps.setString(3, usuario.getContrasenaHash());
            ps.setString(4, usuario.getEstadoAdministrativo().name());
            ps.setString(5, usuario.getRol().name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    usuario.setIdUsuarioInterno(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de usuario interno.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear usuario interno.", e);
        }
    }

    @Override
    public Optional<UsuarioInterno> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE u.id_usuario = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar usuario interno con id " + id + ".", e);
        }
    }

    @Override
    public List<UsuarioInterno> listarTodos() {
        List<UsuarioInterno> usuarios = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY u.id_usuario");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                usuarios.add(mapRow(rs));
            }
            return usuarios;
        } catch (SQLException e) {
            throw new DAOException("Error al listar usuarios internos.", e);
        }
    }

    @Override
    public boolean actualizar(UsuarioInterno usuario) {
        validar(usuario, true);
        new PersonaDAOImpl().actualizar(usuario.getPersona());
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setLong(1, usuario.getPersona().getIdPersona());
            ps.setString(2, usuario.getNombreUsuario());
            ps.setString(3, usuario.getContrasenaHash());
            ps.setString(4, usuario.getEstadoAdministrativo().name());
            ps.setString(5, usuario.getRol().name());
            ps.setLong(6, usuario.getIdUsuarioInterno());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar usuario interno con id " + usuario.getIdUsuarioInterno() + ".", e);
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
            throw new DAOException("Error al eliminar usuario interno con id " + id + ".", e);
        }
    }

    static UsuarioInterno mapUsuario(ResultSet rs, UsuarioInterno usuario) throws SQLException {
        usuario.setIdUsuarioInterno(rs.getLong("id_usuario"));
        usuario.setPersona(JdbcSupport.mapPersona(rs));
        usuario.setNombreUsuario(rs.getString("nombre_usuario"));
        usuario.setContrasenaHash(rs.getString("contrasena_hash"));
        usuario.setEstadoAdministrativo(EstadoActivoInactivo.valueOf(rs.getString("estado_administrativo")));
        usuario.setRol(RolUsuarioInterno.valueOf(rs.getString("rol")));
        usuario.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("usuario_fecha_creacion")));
        usuario.setFechaActualizacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("usuario_fecha_actualizacion")));
        return usuario;
    }

    private UsuarioInterno mapRow(ResultSet rs) throws SQLException {
        return mapUsuario(rs, new UsuarioInterno());
    }

    private void validar(UsuarioInterno usuario, boolean requiereId) {
        if (usuario == null) {
            throw new IllegalArgumentException("El usuario interno no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(usuario.getIdUsuarioInterno());
        }
        JdbcSupport.validarId(JdbcSupport.getIdPersona(usuario.getPersona()));
        if (usuario.getNombreUsuario() == null || usuario.getNombreUsuario().isBlank()
                || usuario.getContrasenaHash() == null || usuario.getContrasenaHash().isBlank()
                || usuario.getEstadoAdministrativo() == null || usuario.getRol() == null) {
            throw new IllegalArgumentException("El usuario interno tiene campos obligatorios incompletos.");
        }
    }
}
