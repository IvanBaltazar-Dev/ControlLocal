package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.ProspeccionDAO;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.comercial.enums.EstadoProspeccion;
import com.controllocal.model.comercial.enums.ResultadoPropuesta;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class ProspeccionDAOImpl implements ProspeccionDAO {

    private static final String INSERT_SQL = """
            INSERT INTO prospeccion (
                codigo_prospeccion, fecha_registro, estado, resultado_propuesta,
                fecha_contacto, fecha_reunion, fecha_propuesta, fecha_recontacto,
                observaciones, id_local, id_agente, id_captacion
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT p.id_prospeccion, p.codigo_prospeccion, p.fecha_registro, p.estado, p.resultado_propuesta,
                   p.fecha_contacto, p.fecha_reunion, p.fecha_propuesta, p.fecha_recontacto,
                   p.observaciones, p.id_local, p.id_agente, p.id_captacion,
                   p.fecha_creacion, p.fecha_actualizacion,
                   l.codigo_local, l.direccion AS local_direccion, l.distrito AS local_distrito,
                   l.metraje AS local_metraje, l.rubro_permitido AS local_rubro,
                   l.precio_referencial AS local_precio,
                   pr.id_propietario, pp.nombres_o_razon_social AS propietario_nombre,
                   ap.nombres_o_razon_social AS agente_nombre,
                   c.codigo_captacion
            FROM prospeccion p
            INNER JOIN local_comercial l ON l.id_local = p.id_local
            INNER JOIN propietario pr ON pr.id_propietario = l.id_propietario
            INNER JOIN persona pp ON pp.id_persona = pr.id_persona
            INNER JOIN agente_inmobiliario a ON a.id_agente = p.id_agente
            INNER JOIN usuario_interno au ON au.id_usuario = a.id_usuario
            INNER JOIN persona ap ON ap.id_persona = au.id_persona
            LEFT JOIN captacion c ON c.id_captacion = p.id_captacion
            """;
    private static final String UPDATE_SQL = """
            UPDATE prospeccion
            SET codigo_prospeccion = ?, fecha_registro = ?, estado = ?, resultado_propuesta = ?,
                fecha_contacto = ?, fecha_reunion = ?, fecha_propuesta = ?, fecha_recontacto = ?,
                observaciones = ?, id_local = ?, id_agente = ?, id_captacion = ?
            WHERE id_prospeccion = ?
            """;
    private static final String DELETE_SQL = "UPDATE prospeccion SET estado = 'D' WHERE id_prospeccion = ?";

    @Override
    public Long crear(Prospeccion prospeccion) {
        validar(prospeccion, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bind(prospeccion, ps);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    prospeccion.setIdProspeccion(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de prospeccion.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear prospeccion.", e);
        }
    }

    @Override
    public Optional<Prospeccion> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE p.id_prospeccion = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar prospeccion con id " + id + ".", e);
        }
    }

    @Override
    public List<Prospeccion> listarTodos() {
        List<Prospeccion> prospecciones = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY p.id_prospeccion DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                prospecciones.add(mapRow(rs));
            }
            return prospecciones;
        } catch (SQLException e) {
            throw new DAOException("Error al listar prospecciones.", e);
        }
    }

    @Override
    public List<Prospeccion> listarPorRecontactar(LocalDate limite) {
        List<Prospeccion> prospecciones = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SELECT_SQL + " WHERE p.estado = 'S' AND p.fecha_recontacto IS NOT NULL"
                             + " AND p.fecha_recontacto <= ? ORDER BY p.fecha_recontacto")) {
            ps.setDate(1, Date.valueOf(limite));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    prospecciones.add(mapRow(rs));
                }
            }
            return prospecciones;
        } catch (SQLException e) {
            throw new DAOException("Error al listar prospecciones por recontactar.", e);
        }
    }

    @Override
    public boolean actualizar(Prospeccion prospeccion) {
        validar(prospeccion, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            bind(prospeccion, ps);
            ps.setLong(13, prospeccion.getIdProspeccion());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar prospeccion con id " + prospeccion.getIdProspeccion() + ".", e);
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
            throw new DAOException("Error al eliminar prospeccion con id " + id + ".", e);
        }
    }

    private void bind(Prospeccion p, PreparedStatement ps) throws SQLException {
        ps.setString(1, p.getCodigoProspeccion());
        JdbcSupport.setTimestamp(ps, 2, p.getFechaRegistro());
        JdbcSupport.setEnum(ps, 3, p.getEstado());
        JdbcSupport.setEnum(ps, 4, p.getResultadoPropuesta());
        JdbcSupport.setDate(ps, 5, p.getFechaContacto());
        JdbcSupport.setDate(ps, 6, p.getFechaReunion());
        JdbcSupport.setDate(ps, 7, p.getFechaPropuesta());
        JdbcSupport.setDate(ps, 8, p.getFechaRecontacto());
        ps.setString(9, p.getObservaciones());
        ps.setLong(10, p.getLocalComercial().getIdLocal());
        ps.setLong(11, p.getAgenteResponsable().getIdAgente());
        JdbcSupport.setLong(ps, 12, JdbcSupport.getIdCaptacion(p.getCaptacion()));
    }

    private Prospeccion mapRow(ResultSet rs) throws SQLException {
        Prospeccion p = new Prospeccion();
        p.setIdProspeccion(rs.getLong("id_prospeccion"));
        p.setCodigoProspeccion(rs.getString("codigo_prospeccion"));
        p.setFechaRegistro(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_registro")));
        p.setEstado(JdbcSupport.getEnum(rs, "estado", EstadoProspeccion.class));
        p.setResultadoPropuesta(JdbcSupport.getNullableEnum(rs, "resultado_propuesta", ResultadoPropuesta.class));
        p.setFechaContacto(JdbcSupport.toLocalDate(rs.getDate("fecha_contacto")));
        p.setFechaReunion(JdbcSupport.toLocalDate(rs.getDate("fecha_reunion")));
        p.setFechaPropuesta(JdbcSupport.toLocalDate(rs.getDate("fecha_propuesta")));
        p.setFechaRecontacto(JdbcSupport.toLocalDate(rs.getDate("fecha_recontacto")));
        p.setObservaciones(rs.getString("observaciones"));

        LocalComercial local = JdbcSupport.local(rs.getLong("id_local"));
        local.setCodigoLocal(rs.getString("codigo_local"));
        local.setDireccion(rs.getString("local_direccion"));
        local.setDistrito(rs.getString("local_distrito"));
        local.setMetraje(rs.getBigDecimal("local_metraje"));
        local.setRubroPermitido(rs.getString("local_rubro"));
        local.setPrecioReferencial(rs.getBigDecimal("local_precio"));
        Propietario propietario = JdbcSupport.propietario(rs.getLong("id_propietario"));
        propietario.setNombresORazonSocial(rs.getString("propietario_nombre"));
        local.setPropietario(propietario);
        p.setLocalComercial(local);

        AgenteInmobiliario agente = JdbcSupport.agente(rs.getLong("id_agente"));
        Persona personaAgente = new Persona();
        personaAgente.setNombresORazonSocial(rs.getString("agente_nombre"));
        agente.setPersona(personaAgente);
        p.setAgenteResponsable(agente);

        long idCaptacion = rs.getLong("id_captacion");
        if (rs.wasNull()) {
            p.setCaptacion(null);
        } else {
            Captacion captacion = JdbcSupport.captacion(idCaptacion);
            captacion.setCodigoCaptacion(rs.getString("codigo_captacion"));
            p.setCaptacion(captacion);
        }
        p.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        p.setFechaActualizacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion")));
        return p;
    }

    private void validar(Prospeccion p, boolean requiereId) {
        if (p == null) {
            throw new IllegalArgumentException("La prospeccion no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(p.getIdProspeccion());
        }
        if (p.getCodigoProspeccion() == null || p.getCodigoProspeccion().isBlank()
                || p.getFechaRegistro() == null || p.getEstado() == null) {
            throw new IllegalArgumentException("La prospeccion tiene campos obligatorios incompletos.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdLocal(p.getLocalComercial()));
        JdbcSupport.validarId(JdbcSupport.getIdAgente(p.getAgenteResponsable()));
    }
}
