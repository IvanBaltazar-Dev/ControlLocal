package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.DAOException;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.EstadoOperativoAgente;
import com.controllocal.model.usuario.RolUsuarioInterno;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AgenteInmobiliarioDAOImpl implements AgenteInmobiliarioDAO {

    private static final String INSERT_SQL = """
            INSERT INTO agente_inmobiliario (
                id_usuario, codigo_agente, zona_asignada, fecha_ingreso, estado_operativo
            ) VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT a.id_agente, a.codigo_agente, a.zona_asignada, a.fecha_ingreso, a.estado_operativo,
                   u.id_usuario, u.nombre_usuario, u.contrasena_hash, u.estado_administrativo, u.rol,
                   u.fecha_creacion AS usuario_fecha_creacion,
                   u.fecha_actualizacion AS usuario_fecha_actualizacion,
                   p.id_persona, p.tipo_persona, p.tipo_documento, p.numero_documento,
                   p.nombres_o_razon_social, p.telefono, p.correo, p.estado,
                   p.fecha_creacion, p.fecha_actualizacion
            FROM agente_inmobiliario a
            INNER JOIN usuario_interno u ON a.id_usuario = u.id_usuario
            INNER JOIN persona p ON u.id_persona = p.id_persona
            """;

    private static final String UPDATE_SQL = """
            UPDATE agente_inmobiliario
            SET id_usuario = ?, codigo_agente = ?, zona_asignada = ?, fecha_ingreso = ?, estado_operativo = ?
            WHERE id_agente = ?
            """;

    private static final String DELETE_SQL = """
            UPDATE agente_inmobiliario a
            INNER JOIN usuario_interno u ON a.id_usuario = u.id_usuario
            SET a.estado_operativo = 'NO_DISPONIBLE', u.estado_administrativo = 'INACTIVO'
            WHERE a.id_agente = ?
            """;

    @Override
    public Long crear(AgenteInmobiliario agente) {
        validar(agente, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, agente.getIdUsuarioInterno());
            ps.setString(2, agente.getCodigoAgente());
            ps.setString(3, agente.getZonaAsignada());
            ps.setDate(4, Date.valueOf(agente.getFechaIngreso()));
            ps.setString(5, agente.getEstadoOperativo().name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    agente.setIdAgente(id);
                    agente.setRol(RolUsuarioInterno.AGENTE);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de agente inmobiliario.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear agente inmobiliario.", e);
        }
    }

    @Override
    public Optional<AgenteInmobiliario> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE a.id_agente = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar agente inmobiliario con id " + id + ".", e);
        }
    }

    @Override
    public List<AgenteInmobiliario> listarTodos() {
        List<AgenteInmobiliario> agentes = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY a.id_agente");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                agentes.add(mapRow(rs));
            }
            return agentes;
        } catch (SQLException e) {
            throw new DAOException("Error al listar agentes inmobiliarios.", e);
        }
    }

    @Override
    public boolean actualizar(AgenteInmobiliario agente) {
        validar(agente, true);
        new UsuarioInternoDAOImpl().actualizar(agente);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setLong(1, agente.getIdUsuarioInterno());
            ps.setString(2, agente.getCodigoAgente());
            ps.setString(3, agente.getZonaAsignada());
            ps.setDate(4, Date.valueOf(agente.getFechaIngreso()));
            ps.setString(5, agente.getEstadoOperativo().name());
            ps.setLong(6, agente.getIdAgente());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar agente inmobiliario con id " + agente.getIdAgente() + ".", e);
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
            throw new DAOException("Error al eliminar agente inmobiliario con id " + id + ".", e);
        }
    }

    private AgenteInmobiliario mapRow(ResultSet rs) throws SQLException {
        AgenteInmobiliario agente = new AgenteInmobiliario();
        UsuarioInternoDAOImpl.mapUsuario(rs, agente);
        agente.setIdAgente(rs.getLong("id_agente"));
        agente.setCodigoAgente(rs.getString("codigo_agente"));
        agente.setZonaAsignada(rs.getString("zona_asignada"));
        agente.setFechaIngreso(JdbcSupport.toLocalDate(rs.getDate("fecha_ingreso")));
        agente.setEstadoOperativo(EstadoOperativoAgente.valueOf(rs.getString("estado_operativo")));
        return agente;
    }

    private void validar(AgenteInmobiliario agente, boolean requiereId) {
        if (agente == null) {
            throw new IllegalArgumentException("El agente inmobiliario no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(agente.getIdAgente());
        }
        JdbcSupport.validarId(agente.getIdUsuarioInterno());
        if (agente.getCodigoAgente() == null || agente.getCodigoAgente().isBlank()
                || agente.getFechaIngreso() == null || agente.getEstadoOperativo() == null) {
            throw new IllegalArgumentException("El agente inmobiliario tiene campos obligatorios incompletos.");
        }
    }
}
