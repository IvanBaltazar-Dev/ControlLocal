package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.DAOException;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.usuario.Broker;
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
 * Implementacion JDBC de BrokerDAO usando PreparedStatement.
 *
 * Broker utiliza herencia de tablas: los datos comunes se guardan en usuario_interno
 * y los datos especificos del broker en la tabla broker (mismo id como clave primaria
 * y foranea a la vez). Por eso crear y actualizar requieren una transaccion que
 * toca ambas tablas.
 */
public class BrokerDAOImpl implements BrokerDAO {

    private static final String INSERT_USUARIO_SQL = """
            INSERT INTO usuario_interno (
                nombres, apellidos, correo, telefono,
                nombre_usuario, contrasena_hash, estado, rol
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'BROKER')
            """;

    private static final String INSERT_BROKER_SQL = """
            INSERT INTO broker (id_broker, codigo_broker, fecha_designacion, es_administrador)
            VALUES (?, ?, ?, ?)
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
                b.codigo_broker,
                b.fecha_designacion,
                b.es_administrador
            FROM broker b
            INNER JOIN usuario_interno u ON b.id_broker = u.id_usuario
            """;

    private static final String SELECT_BY_ID_SQL = SELECT_BASE + "WHERE b.id_broker = ?";

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

    private static final String UPDATE_BROKER_SQL = """
            UPDATE broker
            SET codigo_broker = ?,
                fecha_designacion = ?,
                es_administrador = ?
            WHERE id_broker = ?
            """;

    private static final String DELETE_SQL = """
            UPDATE usuario_interno
            SET estado = 'INACTIVO'
            WHERE id_usuario = ?
            """;

    private static final String EXISTS_OTHER_ADMIN_SQL = """
            SELECT COUNT(*)
            FROM broker
            WHERE es_administrador = TRUE
              AND id_broker <> ?
            """;

    private static final String SELECT_ADMIN_FLAG_SQL = """
            SELECT es_administrador
            FROM broker
            WHERE id_broker = ?
            """;

    @Override
    public Long crear(Broker broker) {
        validarBroker(broker, false);

        try (Connection conn = DBManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long idGenerado;

                try (PreparedStatement stmtUsuario = conn.prepareStatement(
                        INSERT_USUARIO_SQL, Statement.RETURN_GENERATED_KEYS)) {

                    stmtUsuario.setString(1, broker.getNombres());
                    stmtUsuario.setString(2, broker.getApellidos());
                    stmtUsuario.setString(3, broker.getCorreo());
                    stmtUsuario.setString(4, broker.getTelefono());
                    stmtUsuario.setString(5, broker.getNombreUsuario());
                    stmtUsuario.setString(6, broker.getContrasenaHash());
                    stmtUsuario.setString(7, broker.getEstado() != null
                            ? broker.getEstado().name() : "ACTIVO");
                    stmtUsuario.executeUpdate();

                    try (ResultSet keys = stmtUsuario.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new DAOException("No se genero el id del usuario al crear el broker.");
                        }
                        idGenerado = keys.getLong(1);
                    }
                }

                try (PreparedStatement stmtBroker = conn.prepareStatement(INSERT_BROKER_SQL)) {
                    validarAdministrador(conn, idGenerado, broker.isEsAdministrador());
                    stmtBroker.setLong(1, idGenerado);
                    stmtBroker.setString(2, broker.getCodigoBroker());
                    stmtBroker.setDate(3, Date.valueOf(broker.getFechaDesignacion()));
                    stmtBroker.setBoolean(4, broker.isEsAdministrador());
                    stmtBroker.executeUpdate();
                }

                conn.commit();
                broker.setIdUsuarioInterno(idGenerado);
                broker.setIdBroker(idGenerado);
                broker.setRol(RolUsuarioInterno.BROKER);
                return idGenerado;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (DAOException e) {
            throw e;
        } catch (SQLException e) {
            throw new DAOException("Error al crear el broker.", e);
        }
    }

    @Override
    public Optional<Broker> buscarPorId(Long id) {
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
            throw new DAOException("Error al buscar broker con id " + id + ".", e);
        }
    }

    @Override
    public List<Broker> listarTodos() {
        List<Broker> brokers = new ArrayList<>();

        try (Connection conn = DBManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                brokers.add(mapRow(rs));
            }
            return brokers;
        } catch (SQLException e) {
            throw new DAOException("Error al listar los brokers.", e);
        }
    }

    @Override
    public boolean actualizar(Broker broker) {
        validarBroker(broker, true);

        try (Connection conn = DBManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                validarAdministrador(conn, broker.getIdBroker(), broker.isEsAdministrador());

                try (PreparedStatement stmtU = conn.prepareStatement(UPDATE_USUARIO_SQL)) {
                    stmtU.setString(1, broker.getNombres());
                    stmtU.setString(2, broker.getApellidos());
                    stmtU.setString(3, broker.getCorreo());
                    stmtU.setString(4, broker.getTelefono());
                    stmtU.setString(5, broker.getNombreUsuario());
                    stmtU.setString(6, broker.getContrasenaHash());
                    stmtU.setString(7, broker.getEstado().name());
                    stmtU.setLong(8, broker.getIdUsuarioInterno());
                    stmtU.executeUpdate();
                }

                int filas;
                try (PreparedStatement stmtB = conn.prepareStatement(UPDATE_BROKER_SQL)) {
                    stmtB.setString(1, broker.getCodigoBroker());
                    stmtB.setDate(2, Date.valueOf(broker.getFechaDesignacion()));
                    stmtB.setBoolean(3, broker.isEsAdministrador());
                    stmtB.setLong(4, broker.getIdBroker());
                    filas = stmtB.executeUpdate();
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
            throw new DAOException("Error al actualizar broker con id " + broker.getIdBroker() + ".", e);
        }
    }

    @Override
    public boolean eliminar(Long id) {
        validarId(id);

        try (Connection conn = DBManager.getConnection()) {
            if (esAdministrador(conn, id)) {
                throw new DAOException("No se puede desactivar el broker administrador unico.");
            }

            try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
                stmt.setLong(1, id);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new DAOException("Error al eliminar broker con id " + id + ".", e);
        }
    }

    private Broker mapRow(ResultSet rs) throws SQLException {
        Broker broker = new Broker();
        long idUsuario = rs.getLong("id_usuario");
        broker.setIdUsuarioInterno(idUsuario);
        broker.setIdBroker(idUsuario);
        broker.setNombres(rs.getString("nombres"));
        broker.setApellidos(rs.getString("apellidos"));
        broker.setCorreo(rs.getString("correo"));
        broker.setTelefono(rs.getString("telefono"));
        broker.setNombreUsuario(rs.getString("nombre_usuario"));
        broker.setContrasenaHash(rs.getString("contrasena_hash"));
        broker.setEstado(EstadoActivoInactivo.valueOf(rs.getString("estado")));
        broker.setRol(RolUsuarioInterno.valueOf(rs.getString("rol")));

        Timestamp fechaCreacion = rs.getTimestamp("fecha_creacion");
        Timestamp fechaActualizacion = rs.getTimestamp("fecha_actualizacion");
        if (fechaCreacion != null) {
            broker.setFechaCreacion(fechaCreacion.toLocalDateTime());
        }
        if (fechaActualizacion != null) {
            broker.setFechaActualizacion(fechaActualizacion.toLocalDateTime());
        }

        broker.setCodigoBroker(rs.getString("codigo_broker"));
        Date fechaDesig = rs.getDate("fecha_designacion");
        if (fechaDesig != null) {
            broker.setFechaDesignacion(fechaDesig.toLocalDate());
        }
        broker.setEsAdministrador(rs.getBoolean("es_administrador"));
        return broker;
    }

    private void validarBroker(Broker broker, boolean requiereId) {
        if (broker == null) {
            throw new IllegalArgumentException("El broker no puede ser null.");
        }
        if (requiereId) {
            validarId(broker.getIdBroker());
        }
        if (broker.getNombres() == null || broker.getNombres().isBlank()) {
            throw new IllegalArgumentException("Los nombres del broker son obligatorios.");
        }
        if (broker.getApellidos() == null || broker.getApellidos().isBlank()) {
            throw new IllegalArgumentException("Los apellidos del broker son obligatorios.");
        }
        if (broker.getCorreo() == null || broker.getCorreo().isBlank()) {
            throw new IllegalArgumentException("El correo del broker es obligatorio.");
        }
        if (broker.getNombreUsuario() == null || broker.getNombreUsuario().isBlank()) {
            throw new IllegalArgumentException("El nombre de usuario del broker es obligatorio.");
        }
        if (broker.getContrasenaHash() == null || broker.getContrasenaHash().isBlank()) {
            throw new IllegalArgumentException("La contrasena del broker es obligatoria.");
        }
        if (broker.getCodigoBroker() == null || broker.getCodigoBroker().isBlank()) {
            throw new IllegalArgumentException("El codigo del broker es obligatorio.");
        }
        if (broker.getFechaDesignacion() == null) {
            throw new IllegalArgumentException("La fecha de designacion del broker es obligatoria.");
        }
    }

    private void validarAdministrador(Connection conn, long idBroker, boolean esAdministrador) throws SQLException {
        if (!esAdministrador) {
            return;
        }

        try (PreparedStatement stmt = conn.prepareStatement(EXISTS_OTHER_ADMIN_SQL)) {
            stmt.setLong(1, idBroker);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new DAOException("Ya existe un broker administrador registrado.");
                }
            }
        }
    }

    private boolean esAdministrador(Connection conn, Long id) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_ADMIN_FLAG_SQL)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean("es_administrador");
            }
        }
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El id debe ser mayor que cero.");
        }
    }
}
