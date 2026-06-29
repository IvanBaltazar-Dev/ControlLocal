package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.controllocal.config.DBManager;
import com.controllocal.dao.DAOException;
import com.controllocal.dao.InteraccionComercialDAO;
import com.controllocal.model.comercial.enums.CanalContacto;
import com.controllocal.model.comercial.enums.ResultadoInteraccion;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.persona.Persona;

public class InteraccionComercialDAOImpl implements InteraccionComercialDAO {

    private static final String INSERT_SQL = """
            INSERT INTO interaccion_comercial (
                contexto, fecha_hora, canal_contacto, observaciones, resultado,
                id_oportunidad, id_prospeccion, id_captacion, id_cliente, id_agente, transcripcion_nota
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT i.id_interaccion, i.contexto, i.fecha_hora, i.canal_contacto, i.observaciones, i.resultado,
                   i.id_oportunidad, o.id_cliente, o.id_captacion AS oportunidad_id_captacion,
                   i.id_prospeccion, p.id_captacion AS prospeccion_id_captacion, p.codigo_prospeccion,
                   i.id_captacion, cd.codigo_captacion AS interaccion_codigo_captacion,
                   i.id_cliente AS interaccion_id_cliente, pc.nombres_o_razon_social AS interaccion_cliente_nombre,
                   i.id_agente, ap.nombres_o_razon_social AS agente_nombre,
                   i.transcripcion_nota, i.fecha_creacion
            FROM interaccion_comercial i
            LEFT JOIN oportunidad_comercial o ON i.id_oportunidad = o.id_oportunidad
            LEFT JOIN prospeccion p ON i.id_prospeccion = p.id_prospeccion
            LEFT JOIN captacion cd ON i.id_captacion = cd.id_captacion
            LEFT JOIN cliente_interesado ci ON i.id_cliente = ci.id_cliente
            LEFT JOIN persona pc ON ci.id_persona = pc.id_persona
            LEFT JOIN agente_inmobiliario ia ON i.id_agente = ia.id_agente
            LEFT JOIN usuario_interno iu ON ia.id_usuario = iu.id_usuario
            LEFT JOIN persona ap ON iu.id_persona = ap.id_persona
            """;
    private static final String UPDATE_SQL = """
            UPDATE interaccion_comercial
            SET contexto = ?, fecha_hora = ?, canal_contacto = ?, observaciones = ?, resultado = ?,
                id_oportunidad = ?, id_prospeccion = ?, id_captacion = ?, id_cliente = ?, id_agente = ?, transcripcion_nota = ?
            WHERE id_interaccion = ?
            """;
    private static final String DELETE_SQL = "DELETE FROM interaccion_comercial WHERE id_interaccion = ?";

    @Override
    public Long crear(InteraccionComercial interaccion) {
        validar(interaccion, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, contexto(interaccion));
            JdbcSupport.setTimestamp(ps, 2, interaccion.getFechaHora());
            JdbcSupport.setEnum(ps, 3, interaccion.getCanalContacto());
            ps.setString(4, interaccion.getObservaciones());
            JdbcSupport.setEnum(ps, 5, interaccion.getResultado());
            JdbcSupport.setLong(ps, 6, JdbcSupport.getIdOportunidad(interaccion.getOportunidadComercial()));
            JdbcSupport.setLong(ps, 7, JdbcSupport.getIdProspeccion(interaccion.getProspeccion()));
            JdbcSupport.setLong(ps, 8, JdbcSupport.getIdCaptacion(interaccion.getCaptacion()));
            JdbcSupport.setLong(ps, 9, JdbcSupport.getIdCliente(interaccion.getClienteInteresado()));
            ps.setLong(10, interaccion.getAgenteResponsable().getIdAgente());
            ps.setString(11, interaccion.getTranscripcionNota());
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
    public List<InteraccionComercial> listarPorOportunidad(Long idOportunidad) {
        JdbcSupport.validarId(idOportunidad);
        return listarPorEntidad("i.id_oportunidad = ?", idOportunidad);
    }

    @Override
    public List<InteraccionComercial> listarPorProspeccion(Long idProspeccion) {
        JdbcSupport.validarId(idProspeccion);
        return listarPorEntidad("i.id_prospeccion = ?", idProspeccion);
    }

    @Override
    public List<InteraccionComercial> listarPorCaptacion(Long idCaptacion) {
        JdbcSupport.validarId(idCaptacion);
        return listarPorEntidad("i.id_captacion = ?", idCaptacion);
    }

    @Override
    public List<InteraccionComercial> listarPorCliente(Long idCliente) {
        JdbcSupport.validarId(idCliente);
        return listarPorEntidad("i.id_cliente = ?", idCliente);
    }

    @Override
    public List<InteraccionComercial> listarPorAgentes(Collection<Long> idsAgente) {
        List<InteraccionComercial> resultado = new ArrayList<>();
        if (idsAgente == null || idsAgente.isEmpty()) {
            return resultado;
        }
        String sql = SELECT_SQL + " WHERE i.id_agente IN (" + JdbcSupport.placeholders(idsAgente.size()) + ") ORDER BY i.id_interaccion";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Long id : idsAgente) {
                ps.setLong(idx++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(mapRow(rs));
                }
            }
            return resultado;
        } catch (SQLException e) {
            throw new DAOException("Error al listar interaccion por agentes.", e);
        }
    }

    @Override
    public boolean actualizar(InteraccionComercial interaccion) {
        validar(interaccion, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setString(1, contexto(interaccion));
            JdbcSupport.setTimestamp(ps, 2, interaccion.getFechaHora());
            JdbcSupport.setEnum(ps, 3, interaccion.getCanalContacto());
            ps.setString(4, interaccion.getObservaciones());
            JdbcSupport.setEnum(ps, 5, interaccion.getResultado());
            JdbcSupport.setLong(ps, 6, JdbcSupport.getIdOportunidad(interaccion.getOportunidadComercial()));
            JdbcSupport.setLong(ps, 7, JdbcSupport.getIdProspeccion(interaccion.getProspeccion()));
            JdbcSupport.setLong(ps, 8, JdbcSupport.getIdCaptacion(interaccion.getCaptacion()));
            JdbcSupport.setLong(ps, 9, JdbcSupport.getIdCliente(interaccion.getClienteInteresado()));
            ps.setLong(10, interaccion.getAgenteResponsable().getIdAgente());
            ps.setString(11, interaccion.getTranscripcionNota());
            ps.setLong(12, interaccion.getIdInteraccion());
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
        interaccion.setContexto(rs.getString("contexto"));
        interaccion.setFechaHora(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_hora")));
        interaccion.setCanalContacto(JdbcSupport.getEnum(rs, "canal_contacto", CanalContacto.class));
        interaccion.setObservaciones(rs.getString("observaciones"));
        interaccion.setResultado(JdbcSupport.getEnum(rs, "resultado", ResultadoInteraccion.class));
        Long idOportunidad = JdbcSupport.getNullableLong(rs, "id_oportunidad");
        if (idOportunidad != null) {
            interaccion.setOportunidadComercial(JdbcSupport.oportunidad(idOportunidad));
        }
        Long idProspeccion = JdbcSupport.getNullableLong(rs, "id_prospeccion");
        if (idProspeccion != null) {
            var prospeccion = JdbcSupport.prospeccion(idProspeccion);
            prospeccion.setCodigoProspeccion(rs.getString("codigo_prospeccion"));
            interaccion.setProspeccion(prospeccion);
        }
        Long idCaptacion = JdbcSupport.getNullableLong(rs, "id_captacion");
        if (idCaptacion != null) {
            var captacion = JdbcSupport.captacion(idCaptacion);
            captacion.setCodigoCaptacion(rs.getString("interaccion_codigo_captacion"));
            interaccion.setCaptacion(captacion);
        }
        Long idCliente = JdbcSupport.getNullableLong(rs, "interaccion_id_cliente");
        if (idCliente != null) {
            var cliente = JdbcSupport.cliente(idCliente);
            String nombreCliente = rs.getString("interaccion_cliente_nombre");
            if (nombreCliente != null) {
                Persona persona = new Persona();
                persona.setNombresORazonSocial(nombreCliente);
                cliente.setPersona(persona);
            }
            interaccion.setClienteInteresado(cliente);
        }
        var agente = JdbcSupport.agente(rs.getLong("id_agente"));
        String nombreAgente = rs.getString("agente_nombre");
        if (nombreAgente != null) {
            Persona persona = new Persona();
            persona.setNombresORazonSocial(nombreAgente);
            agente.setPersona(persona);
        }
        interaccion.setAgenteResponsable(agente);
        interaccion.setTranscripcionNota(rs.getString("transcripcion_nota"));
        interaccion.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        return interaccion;
    }

    private List<InteraccionComercial> listarPorEntidad(String filtro, Long idEntidad) {
        List<InteraccionComercial> interacciones = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SELECT_SQL + " WHERE " + filtro + " ORDER BY i.fecha_hora DESC, i.id_interaccion DESC")) {
            ps.setLong(1, idEntidad);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    interacciones.add(mapRow(rs));
                }
            }
            return interacciones;
        } catch (SQLException e) {
            throw new DAOException("Error al listar interacciones por entidad.", e);
        }
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
        String contexto = contexto(interaccion);
        Long idOportunidad = JdbcSupport.getIdOportunidad(interaccion.getOportunidadComercial());
        Long idProspeccion = JdbcSupport.getIdProspeccion(interaccion.getProspeccion());
        Long idCaptacion = JdbcSupport.getIdCaptacion(interaccion.getCaptacion());
        Long idCliente = JdbcSupport.getIdCliente(interaccion.getClienteInteresado());
        int referencias = 0;
        referencias += idOportunidad != null ? 1 : 0;
        referencias += idProspeccion != null ? 1 : 0;
        referencias += idCaptacion != null ? 1 : 0;
        referencias += idCliente != null ? 1 : 0;
        if (referencias != 1) {
            throw new IllegalArgumentException("La interaccion comercial debe pertenecer exactamente a una entidad.");
        }
        if ("PROSPECCION".equals(contexto)) {
            JdbcSupport.validarId(idProspeccion);
        } else if ("OPORTUNIDAD".equals(contexto)) {
            JdbcSupport.validarId(idOportunidad);
        } else if ("CAPTACION".equals(contexto)) {
            JdbcSupport.validarId(idCaptacion);
        } else if ("CLIENTE".equals(contexto)) {
            JdbcSupport.validarId(idCliente);
        } else {
            throw new IllegalArgumentException("Contexto de interaccion invalido.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdAgente(interaccion.getAgenteResponsable()));
    }

    private static String contexto(InteraccionComercial interaccion) {
        String contexto = interaccion.getContexto();
        return contexto == null || contexto.isBlank() ? "OPORTUNIDAD" : contexto.trim().toUpperCase();
    }
}
