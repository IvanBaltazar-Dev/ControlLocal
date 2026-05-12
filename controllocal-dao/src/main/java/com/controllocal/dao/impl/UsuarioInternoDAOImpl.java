package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.UsuarioInternoDAO;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.EstadoOperativoAgente;
import com.controllocal.model.usuario.RolUsuarioInterno;
import com.controllocal.model.usuario.UsuarioInterno;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementacion JDBC de UsuarioInternoDAO.
 * Usa LEFT JOIN con broker y agente_inmobiliario para devolver el subtipo correcto
 * segun el campo rol de cada registro.
 */
public class UsuarioInternoDAOImpl implements UsuarioInternoDAO {

    private static final String SELECT_BASE = """
            SELECT
                u.id_usuario,
                u.nombres,
                u.apellidos,
                u.correo,
                u.telefono,
                u.nombre_usuario,
                u.contrasena_hash,
                u.estado,
                u.rol,
                u.fecha_creacion,
                u.fecha_actualizacion,
                b.codigo_broker,
                b.fecha_designacion,
                b.es_administrador,
                a.codigo_agente,
                a.zona_asignada,
                a.fecha_ingreso,
                a.estado_operativo
            FROM usuario_interno u
            LEFT JOIN broker b ON u.id_usuario = b.id_broker
            LEFT JOIN agente_inmobiliario a ON u.id_usuario = a.id_agente
            """;

    private static final String SELECT_BY_ID_SQL = SELECT_BASE + "WHERE u.id_usuario = ?";

    private static final String SELECT_ALL_SQL = SELECT_BASE + "ORDER BY u.id_usuario";

    private static final String UPDATE_SQL = """
            UPDATE usuario_interno
            SET nombres = ?,
                apellidos = ?,
                correo = ?,
                telefono = ?,
                nombre_usuario = ?,
                contrasena_hash = ?,
                estado = ?
            WHERE id_usuario = ?
            """;

    private static final String DELETE_SQL = """
            UPDATE usuario_interno
            SET estado = 'INACTIVO'
            WHERE id_usuario = ?
            """;

    @Override
    public Optional<UsuarioInterno> buscarPorId(Long id) {
        validarId(id);

        try (Connection conn = DBManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar usuario interno con id " + id + ".", e);
        }
    }

    @Override
    public List<UsuarioInterno> listarTodos() {
        List<UsuarioInterno> usuarios = new ArrayList<>();

        try (Connection conn = DBManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                usuarios.add(mapRow(rs));
            }
            return usuarios;
        } catch (SQLException e) {
            throw new DAOException("Error al listar los usuarios internos.", e);
        }
    }

    @Override
    public boolean actualizar(UsuarioInterno usuario) {
        if (usuario == null) {
            throw new IllegalArgumentException("El usuario no puede ser null.");
        }
        validarId(usuario.getIdUsuarioInterno());

        try (Connection conn = DBManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {

            stmt.setString(1, usuario.getNombres());
            stmt.setString(2, usuario.getApellidos());
            stmt.setString(3, usuario.getCorreo());
            stmt.setString(4, usuario.getTelefono());
            stmt.setString(5, usuario.getNombreUsuario());
            stmt.setString(6, usuario.getContrasenaHash());
            stmt.setString(7, usuario.getEstado().name());
            stmt.setLong(8, usuario.getIdUsuarioInterno());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar usuario interno con id " + usuario.getIdUsuarioInterno() + ".", e);
        }
    }

    @Override
    public boolean eliminar(Long id) {
        validarId(id);

        try (Connection conn = DBManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {

            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al eliminar usuario interno con id " + id + ".", e);
        }
    }

    /**
     * Convierte una fila del ResultSet en el objeto Java correspondiente.
     * Segun el campo "rol", crea un Broker o un AgenteInmobiliario con todos sus datos.
     */
    private UsuarioInterno mapRow(ResultSet rs) throws SQLException {
        String rol = rs.getString("rol");
        UsuarioInterno usuario;

        if ("BROKER".equals(rol)) {
            Broker broker = new Broker();
            broker.setCodigoBroker(rs.getString("codigo_broker"));
            broker.setEsAdministrador(rs.getBoolean("es_administrador"));
            Date fechaDesig = rs.getDate("fecha_designacion");
            if (fechaDesig != null) {
                broker.setFechaDesignacion(fechaDesig.toLocalDate());
            }
            usuario = broker;
        } else if ("AGENTE".equals(rol)) {
            AgenteInmobiliario agente = new AgenteInmobiliario();
            agente.setCodigoAgente(rs.getString("codigo_agente"));
            agente.setZonaAsignada(rs.getString("zona_asignada"));
            Date fechaIngreso = rs.getDate("fecha_ingreso");
            if (fechaIngreso != null) {
                agente.setFechaIngreso(fechaIngreso.toLocalDate());
            }
            String estadoOp = rs.getString("estado_operativo");
            if (estadoOp != null) {
                agente.setEstadoOperativo(EstadoOperativoAgente.valueOf(estadoOp));
            }
            usuario = agente;
        } else {
            throw new DAOException("Rol de usuario interno no soportado: " + rol);
        }

        usuario.setIdUsuarioInterno(rs.getLong("id_usuario"));
        if (usuario instanceof Broker broker) {
            broker.setIdBroker(usuario.getIdUsuarioInterno());
        }
        if (usuario instanceof AgenteInmobiliario agente) {
            agente.setIdAgente(usuario.getIdUsuarioInterno());
        }
        usuario.setNombres(rs.getString("nombres"));
        usuario.setApellidos(rs.getString("apellidos"));
        usuario.setCorreo(rs.getString("correo"));
        usuario.setTelefono(rs.getString("telefono"));
        usuario.setNombreUsuario(rs.getString("nombre_usuario"));
        usuario.setContrasenaHash(rs.getString("contrasena_hash"));
        usuario.setEstado(EstadoActivoInactivo.valueOf(rs.getString("estado")));
        usuario.setRol(RolUsuarioInterno.valueOf(rol));

        Timestamp fechaCreacion = rs.getTimestamp("fecha_creacion");
        Timestamp fechaActualizacion = rs.getTimestamp("fecha_actualizacion");
        if (fechaCreacion != null) {
            usuario.setFechaCreacion(fechaCreacion.toLocalDateTime());
        }
        if (fechaActualizacion != null) {
            usuario.setFechaActualizacion(fechaActualizacion.toLocalDateTime());
        }

        return usuario;
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El id debe ser mayor que cero.");
        }
    }
}
