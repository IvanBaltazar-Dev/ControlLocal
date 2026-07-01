package com.controllocal.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
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
    private static final String COUNT_SQL = """
            SELECT COUNT(*)
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
    private static final String ORDEN_AGENDA_SQL = """
             ORDER BY
                CASE
                    WHEN v.fecha_visita > CURRENT_DATE
                        OR (v.fecha_visita = CURRENT_DATE AND v.hora_visita >= CURRENT_TIME)
                    THEN 0 ELSE 1
                END ASC,
                CASE
                    WHEN v.fecha_visita > CURRENT_DATE
                        OR (v.fecha_visita = CURRENT_DATE AND v.hora_visita >= CURRENT_TIME)
                    THEN v.fecha_visita
                END ASC,
                CASE
                    WHEN v.fecha_visita > CURRENT_DATE
                        OR (v.fecha_visita = CURRENT_DATE AND v.hora_visita >= CURRENT_TIME)
                    THEN v.hora_visita
                END ASC,
                CASE
                    WHEN v.fecha_visita < CURRENT_DATE
                        OR (v.fecha_visita = CURRENT_DATE AND v.hora_visita < CURRENT_TIME)
                    THEN v.fecha_visita
                END DESC,
                CASE
                    WHEN v.fecha_visita < CURRENT_DATE
                        OR (v.fecha_visita = CURRENT_DATE AND v.hora_visita < CURRENT_TIME)
                    THEN v.hora_visita
                END DESC,
                v.id_visita DESC
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
    public List<Visita> listarPorAgentes(java.util.Collection<Long> idsAgente) {
        List<Visita> resultado = new ArrayList<>();
        if (idsAgente == null || idsAgente.isEmpty()) {
            return resultado;
        }
        String sql = SELECT_SQL + " WHERE v.id_agente IN (" + JdbcSupport.placeholders(idsAgente.size()) + ") ORDER BY v.id_visita";
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
            throw new DAOException("Error al listar visita por agentes.", e);
        }
    }

    @Override
    public List<Visita> listarPorCaptaciones(java.util.Collection<Long> idsCaptacion) {
        List<Visita> resultado = new ArrayList<>();
        if (idsCaptacion == null || idsCaptacion.isEmpty()) {
            return resultado;
        }
        String sql = SELECT_SQL + " WHERE o.id_captacion IN (" + JdbcSupport.placeholders(idsCaptacion.size()) + ") ORDER BY v.id_visita";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Long id : idsCaptacion) {
                ps.setLong(idx++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(mapRow(rs));
                }
            }
            return resultado;
        } catch (SQLException e) {
            throw new DAOException("Error al listar visita por captaciones.", e);
        }
    }

    @Override
    public List<Visita> listarPorCliente(Long idCliente) {
        List<Visita> resultado = new ArrayList<>();
        if (idCliente == null) {
            return resultado;
        }
        String sql = SELECT_SQL + " WHERE o.id_cliente = ? ORDER BY v.id_visita";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(mapRow(rs));
                }
            }
            return resultado;
        } catch (SQLException e) {
            throw new DAOException("Error al listar visita por cliente.", e);
        }
    }

    public List<Visita> listarPagina(
            Collection<Long> idsAgente,
            Collection<Long> idsCaptacion,
            int offset,
            int limit) {
        return listarPagina(idsAgente, idsCaptacion, null, null, null, null, offset, limit);
    }

    public List<Visita> listarPagina(
            Collection<Long> idsAgente,
            Collection<Long> idsCaptacion,
            Long idOportunidad,
            int offset,
            int limit) {
        return listarPagina(idsAgente, idsCaptacion, idOportunidad, null, null, null, offset, limit);
    }

    public List<Visita> listarPagina(
            Collection<Long> idsAgente,
            Collection<Long> idsCaptacion,
            Long idOportunidad,
            String estado,
            String distrito,
            String query,
            int offset,
            int limit) {
        if (alcanceVacio(idsAgente, idsCaptacion)) {
            return new ArrayList<>();
        }
        List<Long> parametros = new ArrayList<>();
        List<String> textos = new ArrayList<>();
        String where = whereListado(idsAgente, idsCaptacion, idOportunidad, estado, distrito, query, parametros, textos);
        return listarSql(where + ORDEN_AGENDA_SQL + " LIMIT ? OFFSET ?",
                parametros, textos, offset, limit);
    }

    public long contar(Collection<Long> idsAgente, Collection<Long> idsCaptacion) {
        return contar(idsAgente, idsCaptacion, null);
    }

    public long contar(Collection<Long> idsAgente, Collection<Long> idsCaptacion, Long idOportunidad) {
        return contar(idsAgente, idsCaptacion, idOportunidad, null, null, null);
    }

    public long contar(
            Collection<Long> idsAgente,
            Collection<Long> idsCaptacion,
            Long idOportunidad,
            String estado,
            String distrito,
            String query) {
        if (alcanceVacio(idsAgente, idsCaptacion)) {
            return 0L;
        }
        List<Long> parametros = new ArrayList<>();
        List<String> textos = new ArrayList<>();
        String where = whereListado(idsAgente, idsCaptacion, idOportunidad, estado, distrito, query, parametros, textos);
        return contarSql(where, parametros, textos);
    }

    public List<Visita> listarProximas(
            Collection<Long> idsAgente,
            Collection<Long> idsCaptacion,
            int limit) {
        if (alcanceVacio(idsAgente, idsCaptacion)) {
            return new ArrayList<>();
        }
        List<Long> parametros = new ArrayList<>();
        List<String> textos = new ArrayList<>();
        String where = whereListado(idsAgente, idsCaptacion, null, null, null, null, parametros, textos);
        String condicionProximas = """
                v.estado IN ('P','G')
                AND (
                    v.fecha_visita > CURRENT_DATE
                    OR (v.fecha_visita = CURRENT_DATE AND v.hora_visita >= CURRENT_TIME)
                )
                """;
        where = where.isBlank()
                ? " WHERE " + condicionProximas
                : where + " AND " + condicionProximas;
        return listarSql(where + " ORDER BY v.fecha_visita ASC, v.hora_visita ASC, v.id_visita DESC LIMIT ? OFFSET ?",
                parametros, textos, 0, limit);
    }

    public List<Visita> listarMes(
            Collection<Long> idsAgente,
            Collection<Long> idsCaptacion,
            int anio,
            int mes) {
        if (alcanceVacio(idsAgente, idsCaptacion)) {
            return new ArrayList<>();
        }
        LocalDate desde = LocalDate.of(anio, mes, 1);
        LocalDate hasta = desde.plusMonths(1);
        List<Long> parametros = new ArrayList<>();
        List<String> textos = new ArrayList<>();
        String where = whereListado(idsAgente, idsCaptacion, null, null, null, null, parametros, textos);
        String condicionMes = "v.fecha_visita >= ? AND v.fecha_visita < ?";
        where = where.isBlank()
                ? " WHERE " + condicionMes
                : where + " AND " + condicionMes;

        List<Visita> resultado = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                     SELECT_SQL + where + " ORDER BY v.fecha_visita ASC, v.hora_visita ASC, v.id_visita DESC")) {
            int idx = bindLongs(ps, parametros, 1);
            idx = bindStrings(ps, textos, idx);
            JdbcSupport.setDate(ps, idx++, desde);
            JdbcSupport.setDate(ps, idx, hasta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(mapRow(rs));
                }
            }
            return resultado;
        } catch (SQLException e) {
            throw new DAOException("Error al listar visitas del mes.", e);
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

    private List<Visita> listarSql(String sufijoSql, List<Long> parametros, List<String> textos, int offset, int limit) {
        List<Visita> resultado = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + sufijoSql)) {
            int idx = bindLongs(ps, parametros, 1);
            idx = bindStrings(ps, textos, idx);
            ps.setInt(idx++, Math.max(1, Math.min(limit, 500)));
            ps.setInt(idx, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(mapRow(rs));
                }
            }
            return resultado;
        } catch (SQLException e) {
            throw new DAOException("Error al listar visitas paginadas.", e);
        }
    }

    private long contarSql(String where, List<Long> parametros, List<String> textos) {
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(COUNT_SQL + where)) {
            int idx = bindLongs(ps, parametros, 1);
            bindStrings(ps, textos, idx);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new DAOException("Error al contar visitas.", e);
        }
    }

    private static int bindLongs(PreparedStatement ps, List<Long> parametros, int idx) throws SQLException {
        for (Long id : parametros) {
            ps.setLong(idx++, id);
        }
        return idx;
    }

    private static int bindStrings(PreparedStatement ps, List<String> parametros, int idx) throws SQLException {
        for (String valor : parametros) {
            ps.setString(idx++, valor);
        }
        return idx;
    }

    private static boolean alcanceVacio(Collection<Long> idsAgente, Collection<Long> idsCaptacion) {
        return (idsAgente != null && idsAgente.isEmpty())
                || (idsCaptacion != null && idsCaptacion.isEmpty());
    }

    private static String whereListado(
            Collection<Long> idsAgente,
            Collection<Long> idsCaptacion,
            Long idOportunidad,
            String estado,
            String distrito,
            String query,
            List<Long> parametros,
            List<String> textos) {
        List<String> condiciones = new ArrayList<>();
        if (idsAgente != null) {
            condiciones.add("v.id_agente IN (" + JdbcSupport.placeholders(idsAgente.size()) + ")");
            parametros.addAll(idsAgente);
        }
        if (idsCaptacion != null) {
            condiciones.add("o.id_captacion IN (" + JdbcSupport.placeholders(idsCaptacion.size()) + ")");
            parametros.addAll(idsCaptacion);
        }
        if (idOportunidad != null) {
            condiciones.add("v.id_oportunidad = ?");
            parametros.add(idOportunidad);
        }
        if (estado != null && !estado.isBlank()) {
            condiciones.add("v.estado = ?");
            textos.add(estado.trim());
        }
        if (distrito != null && !distrito.isBlank()) {
            condiciones.add("LOWER(l.distrito) = ?");
            textos.add(distrito.trim().toLowerCase());
        }
        if (query != null && !query.isBlank()) {
            condiciones.add("""
                    (
                        LOWER(o.codigo_oportunidad) LIKE ?
                        OR LOWER(c.codigo_captacion) LIKE ?
                        OR LOWER(cp.nombres_o_razon_social) LIKE ?
                        OR LOWER(l.direccion) LIKE ?
                        OR LOWER(l.distrito) LIKE ?
                        OR LOWER(ap.nombres_o_razon_social) LIKE ?
                    )
                    """);
            String patron = "%" + query.trim().toLowerCase() + "%";
            for (int i = 0; i < 6; i++) {
                textos.add(patron);
            }
        }
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
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
        if (visita.getEstado() != EstadoVisita.REALIZADA
                && (visita.getResultado() != null
                        || visita.getNivelInteres() != null
                        || visita.getObjecionPrincipal() != null
                        || visita.getOpinionPrecio() != null
                        || visita.getProximaAccion() != null)) {
            throw new IllegalArgumentException("Solo una visita realizada puede tener desenlace comercial.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdOportunidad(visita.getOportunidadComercial()));
        JdbcSupport.validarId(JdbcSupport.getIdAgente(visita.getAgenteResponsable()));
    }
}
