package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.InteraccionComercialDAO;
import com.controllocal.model.comercial.CanalContacto;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.ResultadoInteraccion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InteraccionComercialDAOImpl implements InteraccionComercialDAO {

    private static final String INSERT_SQL = """
            INSERT INTO interaccion_comercial (
                fecha_hora, canal_contacto, observaciones, resultado,
                id_cliente, id_captacion, id_agente
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT id_interaccion, fecha_hora, canal_contacto, observaciones, resultado,
                   id_cliente, id_captacion, id_agente, fecha_creacion
            FROM interaccion_comercial
            """;
    private static final String UPDATE_SQL = """
            UPDATE interaccion_comercial
            SET fecha_hora = ?, canal_contacto = ?, observaciones = ?, resultado = ?,
                id_cliente = ?, id_captacion = ?, id_agente = ?
            WHERE id_interaccion = ?
            """;
    private static final String DELETE_SQL = "DELETE FROM interaccion_comercial WHERE id_interaccion = ?";

    @Override
    public Long crear(InteraccionComercial interaccion) {
        validar(interaccion, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            JdbcSupport.setTimestamp(ps, 1, interaccion.getFechaHora());
            ps.setString(2, interaccion.getCanalContacto().name());
            ps.setString(3, interaccion.getObservaciones());
            ps.setString(4, interaccion.getResultado().name());
            ps.setLong(5, interaccion.getClienteInteresado().getIdCliente());
            ps.setLong(6, interaccion.getCaptacion().getIdCaptacion());
            ps.setLong(7, interaccion.getAgenteResponsable().getIdAgente());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    interaccion.setIdInteraccion(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de interaccion comercial.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear interaccion comercial.", e);
        }
    }

    @Override
    public Optional<InteraccionComercial> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_interaccion = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar interaccion comercial con id " + id + ".", e);
        }
    }

    @Override
    public List<InteraccionComercial> listarTodos() {
        List<InteraccionComercial> interacciones = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY id_interaccion");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                interacciones.add(mapRow(rs));
            }
            return interacciones;
        } catch (SQLException e) {
            throw new DAOException("Error al listar interacciones comerciales.", e);
        }
    }

    @Override
    public boolean actualizar(InteraccionComercial interaccion) {
        validar(interaccion, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            JdbcSupport.setTimestamp(ps, 1, interaccion.getFechaHora());
            ps.setString(2, interaccion.getCanalContacto().name());
            ps.setString(3, interaccion.getObservaciones());
            ps.setString(4, interaccion.getResultado().name());
            ps.setLong(5, interaccion.getClienteInteresado().getIdCliente());
            ps.setLong(6, interaccion.getCaptacion().getIdCaptacion());
            ps.setLong(7, interaccion.getAgenteResponsable().getIdAgente());
            ps.setLong(8, interaccion.getIdInteraccion());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar interaccion comercial con id " + interaccion.getIdInteraccion() + ".", e);
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
            throw new DAOException("Error al eliminar interaccion comercial con id " + id + ".", e);
        }
    }

    private InteraccionComercial mapRow(ResultSet rs) throws SQLException {
        InteraccionComercial interaccion = new InteraccionComercial();
        interaccion.setIdInteraccion(rs.getLong("id_interaccion"));
        interaccion.setFechaHora(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_hora")));
        interaccion.setCanalContacto(CanalContacto.valueOf(rs.getString("canal_contacto")));
        interaccion.setObservaciones(rs.getString("observaciones"));
        interaccion.setResultado(ResultadoInteraccion.valueOf(rs.getString("resultado")));
        interaccion.setClienteInteresado(JdbcSupport.cliente(rs.getLong("id_cliente")));
        interaccion.setCaptacion(JdbcSupport.captacion(rs.getLong("id_captacion")));
        interaccion.setAgenteResponsable(JdbcSupport.agente(rs.getLong("id_agente")));
        interaccion.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        return interaccion;
    }

    private void validar(InteraccionComercial interaccion, boolean requiereId) {
        if (interaccion == null) {
            throw new IllegalArgumentException("La interaccion comercial no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(interaccion.getIdInteraccion());
        }
        if (interaccion.getFechaHora() == null || interaccion.getCanalContacto() == null
                || interaccion.getResultado() == null) {
            throw new IllegalArgumentException("La interaccion comercial tiene campos obligatorios incompletos.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdCliente(interaccion.getClienteInteresado()));
        JdbcSupport.validarId(JdbcSupport.getIdCaptacion(interaccion.getCaptacion()));
        JdbcSupport.validarId(JdbcSupport.getIdAgente(interaccion.getAgenteResponsable()));
    }
}
