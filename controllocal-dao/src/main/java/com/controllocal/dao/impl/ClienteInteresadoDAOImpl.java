package com.controllocal.dao.impl;

import com.controllocal.config.DBManager;
import com.controllocal.dao.ClienteInteresadoDAO;
import com.controllocal.dao.DAOException;
import com.controllocal.model.persona.ClienteInteresado;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClienteInteresadoDAOImpl implements ClienteInteresadoDAO {

    private static final String INSERT_SQL = "INSERT INTO cliente_interesado (id_persona, rubro_comercial) VALUES (?, ?)";
    private static final String SELECT_SQL = """
            SELECT c.id_cliente, c.rubro_comercial,
                   p.id_persona, p.tipo_persona, p.tipo_documento, p.numero_documento,
                   p.nombres_o_razon_social, p.telefono, p.correo, p.estado,
                   p.fecha_creacion, p.fecha_actualizacion
            FROM cliente_interesado c
            INNER JOIN persona p ON c.id_persona = p.id_persona
            """;
    private static final String UPDATE_SQL = "UPDATE cliente_interesado SET id_persona = ?, rubro_comercial = ? WHERE id_cliente = ?";
    private static final String DELETE_SQL = """
            UPDATE persona p
            INNER JOIN cliente_interesado c ON c.id_persona = p.id_persona
            SET p.estado = 'INACTIVO'
            WHERE c.id_cliente = ?
            """;

    @Override
    public Long crear(ClienteInteresado cliente) {
        validar(cliente, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, cliente.getPersona().getIdPersona());
            ps.setString(2, cliente.getRubroComercial());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    cliente.setIdCliente(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de cliente interesado.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear cliente interesado.", e);
        }
    }

    @Override
    public Optional<ClienteInteresado> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE c.id_cliente = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar cliente interesado con id " + id + ".", e);
        }
    }

    @Override
    public List<ClienteInteresado> listarTodos() {
        List<ClienteInteresado> clientes = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY c.id_cliente");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                clientes.add(mapRow(rs));
            }
            return clientes;
        } catch (SQLException e) {
            throw new DAOException("Error al listar clientes interesados.", e);
        }
    }

    @Override
    public boolean actualizar(ClienteInteresado cliente) {
        validar(cliente, true);
        new PersonaDAOImpl().actualizar(cliente.getPersona());
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setLong(1, cliente.getPersona().getIdPersona());
            ps.setString(2, cliente.getRubroComercial());
            ps.setLong(3, cliente.getIdCliente());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar cliente interesado con id " + cliente.getIdCliente() + ".", e);
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
            throw new DAOException("Error al eliminar cliente interesado con id " + id + ".", e);
        }
    }

    private ClienteInteresado mapRow(ResultSet rs) throws SQLException {
        ClienteInteresado cliente = new ClienteInteresado();
        cliente.setIdCliente(rs.getLong("id_cliente"));
        cliente.setRubroComercial(rs.getString("rubro_comercial"));
        cliente.setPersona(JdbcSupport.mapPersona(rs));
        return cliente;
    }

    private void validar(ClienteInteresado cliente, boolean requiereId) {
        if (cliente == null) {
            throw new IllegalArgumentException("El cliente interesado no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(cliente.getIdCliente());
        }
        JdbcSupport.validarId(JdbcSupport.getIdPersona(cliente.getPersona()));
    }
}
