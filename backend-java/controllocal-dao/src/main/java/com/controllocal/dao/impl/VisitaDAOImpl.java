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
import com.controllocal.dao.DAOException;
import com.controllocal.dao.VisitaDAO;
import com.controllocal.model.comercial.enums.EstadoVisita;
import com.controllocal.model.comercial.enums.ObjecionVisita;
import com.controllocal.model.comercial.enums.OpinionPrecio;
import com.controllocal.model.comercial.enums.ProximaAccionVisita;
import com.controllocal.model.comercial.enums.ResultadoInteraccion;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class VisitaDAOImpl implements VisitaDAO {

    private static final String INSERT_SQL = """
            INSERT INTO visita (
                fecha_visita, hora_visita, observaciones, estado, resultado,
                id_oportunidad, id_agente,
                nivel_interes, objecion_principal, opinion_precio, proxima_accion
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT v.id_visita, v.fecha_visita, v.hora_visita, v.observaciones, v.estado, v.resultado,
                   v.id_oportunidad, o.id_cliente, o.id_captacion, v.id_agente,
                   v.nivel_interes, v.objecion_principal, v.opinion_precio, v.proxima_accion,
                   v.fecha_creacion, v.fecha_actualizacion,
                   o.codigo_oportunidad,
                   cp.nombres_o_razon_social AS cliente_nombre,
                   c.codigo_captacion,
                   l.id_local, l.codigo_local, l.direccion AS local_direccion,
                   l.distrito AS local_distrito,
                   ap.nombres_o_razon_social AS agente_nombre
            FROM visita v
            INNER JOIN oportunidad_comercial o ON v.id_oportunidad = o.id_oportunidad
            INNER JOIN cliente_interesado ci ON ci.id_cliente = o.id_cliente
            INNER JOIN persona cp ON cp.id_persona = ci.id_persona
            INNER JOIN captacion c ON c.id_captacion = o.id_captacion
            INNER JOIN local_comercial l ON l.id_local = c.id_local
            INNER JOIN agente_inmobiliario a ON a.id_agente = v.id_agente
            INNER JOIN usuario_interno au ON au.id_usuario = a.id_usuario
            INNER JOIN persona ap ON ap.id_persona = au.id_persona
            """;
    private static final String UPDATE_SQL = """
            UPDATE visita
            SET fecha_visita = ?, hora_visita = ?, observaciones = ?, estado = ?, resultado = ?,
                id_oportunidad = ?, id_agente = ?,
                nivel_interes = ?, objecion_principal = ?, opinion_precio = ?, proxima_accion = ?
            WHERE id_visita = ?
            """;
    private static final String DELETE_SQL = "UPDATE visita SET estado = 'C' WHERE id_visita = ?";

    @Override
    public Long crear(Visita visita) {
        validar(visita, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            JdbcSupport.setDate(ps, 1, visita.getFechaVisita());
            JdbcSupport.setTime(ps, 2, visita.getHoraVisita());
            ps.setString(3, visita.getObservaciones());
            JdbcSupport.setEnum(ps, 4, visita.getEstado());
            JdbcSupport.setEnum(ps, 5, visita.getResultado());
            ps.setLong(6, visita.getOportunidadComercial().getIdOportunidad());
            ps.setLong(7, visita.getAgenteResponsable().getIdAgente());
            JdbcSupport.setInteger(ps, 8, visita.getNivelInteres());
            JdbcSupport.setEnum(ps, 9, visita.getObjecionPrincipal());
            JdbcSupport.setEnum(ps, 10, visita.getOpinionPrecio());
            JdbcSupport.setEnum(ps, 11, visita.getProximaAccion());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    visita.setIdVisita(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de visita.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear visita.", e);
        }
    }

    @Override
    public Optional<Visita> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE v.id_visita = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar visita con id " + id + ".", e);
        }
    }

    @Override
    public List<Visita> listarTodos() {
        List<Visita> visitas = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY v.id_visita");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                visitas.add(mapRow(rs));
            }
            return visitas;
        } catch (SQLException e) {
            throw new DAOException("Error al listar visitas.", e);
        }
    }

    @Override
    public boolean actualizar(Visita visita) {
        validar(visita, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            JdbcSupport.setDate(ps, 1, visita.getFechaVisita());
            JdbcSupport.setTime(ps, 2, visita.getHoraVisita());
            ps.setString(3, visita.getObservaciones());
            JdbcSupport.setEnum(ps, 4, visita.getEstado());
            JdbcSupport.setEnum(ps, 5, visita.getResultado());
            ps.setLong(6, visita.getOportunidadComercial().getIdOportunidad());
            ps.setLong(7, visita.getAgenteResponsable().getIdAgente());
            JdbcSupport.setInteger(ps, 8, visita.getNivelInteres());
            JdbcSupport.setEnum(ps, 9, visita.getObjecionPrincipal());
            JdbcSupport.setEnum(ps, 10, visita.getOpinionPrecio());
            JdbcSupport.setEnum(ps, 11, visita.getProximaAccion());
            ps.setLong(12, visita.getIdVisita());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar visita con id " + visita.getIdVisita() + ".", e);
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
            throw new DAOException("Error al eliminar visita con id " + id + ".", e);
        }
    }

    private Visita mapRow(ResultSet rs) throws SQLException {
        Visita visita = new Visita();
        visita.setIdVisita(rs.getLong("id_visita"));
        visita.setFechaVisita(JdbcSupport.toLocalDate(rs.getDate("fecha_visita")));
        visita.setHoraVisita(JdbcSupport.toLocalTime(rs.getTime("hora_visita")));
        visita.setObservaciones(rs.getString("observaciones"));
        visita.setEstado(JdbcSupport.getEnum(rs, "estado", EstadoVisita.class));
        visita.setResultado(JdbcSupport.getNullableEnum(rs, "resultado", ResultadoInteraccion.class));
        Persona personaCliente = new Persona();
        personaCliente.setNombresORazonSocial(rs.getString("cliente_nombre"));
        ClienteInteresado cliente = JdbcSupport.cliente(rs.getLong("id_cliente"));
        cliente.setPersona(personaCliente);

        LocalComercial local = JdbcSupport.local(rs.getLong("id_local"));
        local.setCodigoLocal(rs.getString("codigo_local"));
        local.setDireccion(rs.getString("local_direccion"));
        local.setDistrito(rs.getString("local_distrito"));
        Captacion captacion = JdbcSupport.captacion(rs.getLong("id_captacion"));
        captacion.setCodigoCaptacion(rs.getString("codigo_captacion"));
        captacion.setLocalComercial(local);

        OportunidadComercial oportunidad = JdbcSupport.oportunidad(rs.getLong("id_oportunidad"));
        oportunidad.setCodigoOportunidad(rs.getString("codigo_oportunidad"));
        oportunidad.setClienteInteresado(cliente);
        oportunidad.setCaptacion(captacion);

        Persona personaAgente = new Persona();
        personaAgente.setNombresORazonSocial(rs.getString("agente_nombre"));
        AgenteInmobiliario agente = JdbcSupport.agente(rs.getLong("id_agente"));
        agente.setPersona(personaAgente);

        visita.setOportunidadComercial(oportunidad);
        visita.setClienteInteresado(cliente);
        visita.setCaptacion(captacion);
        visita.setAgenteResponsable(agente);
        visita.setNivelInteres(JdbcSupport.getNullableInt(rs, "nivel_interes"));
        visita.setObjecionPrincipal(JdbcSupport.getNullableEnum(rs, "objecion_principal", ObjecionVisita.class));
        visita.setOpinionPrecio(JdbcSupport.getNullableEnum(rs, "opinion_precio", OpinionPrecio.class));
        visita.setProximaAccion(JdbcSupport.getNullableEnum(rs, "proxima_accion", ProximaAccionVisita.class));
        visita.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        visita.setFechaActualizacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion")));
        return visita;
    }

    private void validar(Visita visita, boolean requiereId) {
        if (visita == null) {
            throw new IllegalArgumentException("La visita no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(visita.getIdVisita());
        }
        if (visita.getFechaVisita() == null || visita.getHoraVisita() == null || visita.getEstado() == null) {
            throw new IllegalArgumentException("La visita tiene campos obligatorios incompletos.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdOportunidad(visita.getOportunidadComercial()));
        JdbcSupport.validarId(JdbcSupport.getIdAgente(visita.getAgenteResponsable()));
    }
}
