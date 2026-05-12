package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.DAOException;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.EstadoOperativoAgente;
import com.controllocal.model.usuario.RolUsuarioInterno;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementacion JDBC de AgenteInmobiliarioDAO usando PreparedStatement.
 *
 * Al igual que Broker, AgenteInmobiliario usa herencia de tablas: los datos comunes
 * van en usuario_interno y los especificos en agente_inmobiliario (mismo id).
 * Crear y actualizar requieren una transaccion que toca ambas tablas.
 */
public class AgenteInmobiliarioDAOImpl implements AgenteInmobiliarioDAO {

    private static final String INSERT_USUARIO_SQL = """
            INSERT INTO usuario_interno (
                nombres, apellidos, correo, telefono,
                nombre_usuario, contrasena_hash, estado, rol
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'AGENTE')
            """;

    private static final String INSERT_AGENTE_SQL = """
            INSERT INTO agente_inmobiliario (
                id_agente, codigo_agente, zona_asignada, fecha_ingreso, estado_operativo
            ) VALUES (?, ?, ?, ?, ?)
            """;

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
                a.codigo_agente,
                a.zona_asignada,
                a.fecha_ingreso,
                a.estado_operativo
            FROM agente_inmobiliario a
            INNER JOIN usuario_interno u ON a.id_agente = u.id_usuario
            """;

    private static final String SELECT_BY_ID_SQL = SELECT_BASE + "WHERE a.id_agente = ?";

    private static final String SELECT_ALL_SQL = SELECT_BASE + "ORDER BY u.nombres";

    private static final String UPDATE_USUARIO_SQL = """
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

    private static final String UPDATE_AGENTE_SQL = """
            UPDATE agente_inmobiliario
            SET codigo_agente = ?,
                zona_asignada = ?,
                fecha_ingreso = ?,
                estado_operativo = ?
            WHERE id_agente = ?
            """;

    private static final String DELETE_SQL = """
            UPDATE usuario_interno
            SET estado = 'INACTIVO'
            WHERE id_usuario = ?
            """;

    private static final String UPDATE_ESTADO_OPERATIVO_SQL = """
            UPDATE agente_inmobiliario
            SET estado_operativo = 'NO_DISPONIBLE'
            WHERE id_agente = ?
            """;

    @Override
    public Long crear(AgenteInmobiliario agente) {
        validarAgente(agente, false);

        try (Connection conn = DBManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long idGenerado;

                try (PreparedStatement stmtUsuario = conn.prepareStatement(
                        INSERT_USUARIO_SQL, Statement.RETURN_GENERATED_KEYS)) {

                    stmtUsuario.setString(1, agente.getNombres());
                    stmtUsuario.setString(2, agente.getApellidos());
                    stmtUsuario.setString(3, agente.getCorreo());
                    stmtUsuario.setString(4, agente.getTelefono());
                    stmtUsuario.setString(5, agente.getNombreUsuario());
                    stmtUsuario.setString(6, agente.getContrasenaHash());
                    stmtUsuario.setString(7, agente.getEstado() != null
                            ? agente.getEstado().name() : "ACTIVO");
                    stmtUsuario.executeUpdate();

                    try (ResultSet keys = stmtUsuario.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new DAOException("No se genero el id del usuario al crear el agente.");
                        }
                        idGenerado = keys.getLong(1);
                    }
                }

                try (PreparedStatement stmtAgente = conn.prepareStatement(INSERT_AGENTE_SQL)) {
                    stmtAgente.setLong(1, idGenerado);
                    stmtAgente.setString(2, agente.getCodigoAgente());
                    stmtAgente.setString(3, agente.getZonaAsignada());
                    stmtAgente.setDate(4, Date.valueOf(agente.getFechaIngreso()));
                    stmtAgente.setString(5, agente.getEstadoOperativo() != null
                            ? agente.getEstadoOperativo().name() : "DISPONIBLE");
                    stmtAgente.executeUpdate();
                }

                conn.commit();
                agente.setIdUsuarioInterno(idGenerado);
                agente.setIdAgente(idGenerado);
                agente.setRol(RolUsuarioInterno.AGENTE);
                return idGenerado;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (DAOException e) {
            throw e;
        } catch (SQLException e) {
            throw new DAOException("Error al crear el agente inmobiliario.", e);
        }
    }

    @Override
    public Optional<AgenteInmobiliario> buscarPorId(Long id) {
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
            throw new DAOException("Error al buscar agente con id " + id + ".", e);
        }
    }

    @Override
    public List<AgenteInmobiliario> listarTodos() {
        List<AgenteInmobiliario> agentes = new ArrayList<>();

        try (Connection conn = DBManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                agentes.add(mapRow(rs));
            }
            return agentes;
        } catch (SQLException e) {
            throw new DAOException("Error al listar los agentes inmobiliarios.", e);
        }
    }

    @Override
    public boolean actualizar(AgenteInmobiliario agente) {
        validarAgente(agente, true);

        try (Connection conn = DBManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmtU = conn.prepareStatement(UPDATE_USUARIO_SQL)) {
                    stmtU.setString(1, agente.getNombres());
                    stmtU.setString(2, agente.getApellidos());
                    stmtU.setString(3, agente.getCorreo());
                    stmtU.setString(4, agente.getTelefono());
                    stmtU.setString(5, agente.getNombreUsuario());
                    stmtU.setString(6, agente.getContrasenaHash());
                    stmtU.setString(7, agente.getEstado().name());
                    stmtU.setLong(8, agente.getIdUsuarioInterno());
                    stmtU.executeUpdate();
                }

                int filas;
                try (PreparedStatement stmtA = conn.prepareStatement(UPDATE_AGENTE_SQL)) {
                    stmtA.setString(1, agente.getCodigoAgente());
                    stmtA.setString(2, agente.getZonaAsignada());
                    stmtA.setDate(3, Date.valueOf(agente.getFechaIngreso()));
                    stmtA.setString(4, agente.getEstadoOperativo().name());
                    stmtA.setLong(5, agente.getIdAgente());
                    filas = stmtA.executeUpdate();
                }

                conn.commit();
                return filas > 0;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (DAOException e) {
            throw e;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar agente con id " + agente.getIdAgente() + ".", e);
        }
    }

    @Override
    public boolean eliminar(Long id) {
        validarId(id);

        try (Connection conn = DBManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int filas;

                try (PreparedStatement stmtAgente = conn.prepareStatement(UPDATE_ESTADO_OPERATIVO_SQL)) {
                    stmtAgente.setLong(1, id);
                    stmtAgente.executeUpdate();
                }

                try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
                    stmt.setLong(1, id);
                    filas = stmt.executeUpdate();
                }

                conn.commit();
                return filas > 0;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DAOException("Error al eliminar agente con id " + id + ".", e);
        }
    }

    private AgenteInmobiliario mapRow(ResultSet rs) throws SQLException {
        AgenteInmobiliario agente = new AgenteInmobiliario();
        long idUsuario = rs.getLong("id_usuario");
        agente.setIdUsuarioInterno(idUsuario);
        agente.setIdAgente(idUsuario);
        agente.setNombres(rs.getString("nombres"));
        agente.setApellidos(rs.getString("apellidos"));
        agente.setCorreo(rs.getString("correo"));
        agente.setTelefono(rs.getString("telefono"));
        agente.setNombreUsuario(rs.getString("nombre_usuario"));
        agente.setContrasenaHash(rs.getString("contrasena_hash"));
        agente.setEstado(EstadoActivoInactivo.valueOf(rs.getString("estado")));
        agente.setRol(RolUsuarioInterno.valueOf(rs.getString("rol")));

        Timestamp fechaCreacion = rs.getTimestamp("fecha_creacion");
        Timestamp fechaActualizacion = rs.getTimestamp("fecha_actualizacion");
        if (fechaCreacion != null) {
            agente.setFechaCreacion(fechaCreacion.toLocalDateTime());
        }
        if (fechaActualizacion != null) {
            agente.setFechaActualizacion(fechaActualizacion.toLocalDateTime());
        }

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
        return agente;
    }

    private void validarAgente(AgenteInmobiliario agente, boolean requiereId) {
        if (agente == null) {
            throw new IllegalArgumentException("El agente no puede ser null.");
        }
        if (requiereId) {
            validarId(agente.getIdAgente());
        }
        if (agente.getNombres() == null || agente.getNombres().isBlank()) {
            throw new IllegalArgumentException("Los nombres del agente son obligatorios.");
        }
        if (agente.getApellidos() == null || agente.getApellidos().isBlank()) {
            throw new IllegalArgumentException("Los apellidos del agente son obligatorios.");
        }
        if (agente.getCorreo() == null || agente.getCorreo().isBlank()) {
            throw new IllegalArgumentException("El correo del agente es obligatorio.");
        }
        if (agente.getNombreUsuario() == null || agente.getNombreUsuario().isBlank()) {
            throw new IllegalArgumentException("El nombre de usuario del agente es obligatorio.");
        }
        if (agente.getContrasenaHash() == null || agente.getContrasenaHash().isBlank()) {
            throw new IllegalArgumentException("La contrasena del agente es obligatoria.");
        }
        if (agente.getCodigoAgente() == null || agente.getCodigoAgente().isBlank()) {
            throw new IllegalArgumentException("El codigo del agente es obligatorio.");
        }
        if (agente.getFechaIngreso() == null) {
            throw new IllegalArgumentException("La fecha de ingreso del agente es obligatoria.");
        }
        if (agente.getEstadoOperativo() == null) {
            throw new IllegalArgumentException("El estado operativo del agente es obligatorio.");
        }
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El id debe ser mayor que cero.");
        }
    }
}
