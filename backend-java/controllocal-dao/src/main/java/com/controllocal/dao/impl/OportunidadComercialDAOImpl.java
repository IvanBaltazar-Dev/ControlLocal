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
import com.controllocal.dao.OportunidadComercialDAO;
import com.controllocal.model.comercial.enums.EstadoOportunidadComercial;
import com.controllocal.model.comercial.enums.FuenteOrigen;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Publicacion;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class OportunidadComercialDAOImpl implements OportunidadComercialDAO {

    private static final String INSERT_SQL = """
            INSERT INTO oportunidad_comercial (
                codigo_oportunidad, fecha_registro, estado, fecha_actualizacion_estado,
                motivo_cierre, observaciones, id_cliente, id_captacion, id_agente, fecha_cierre,
                id_publicacion_origen, fuente_origen, codigo_origen_capturado, fecha_primera_consulta
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT o.id_oportunidad, o.codigo_oportunidad, o.fecha_registro, o.estado,
                   o.fecha_actualizacion_estado, o.motivo_cierre, o.observaciones,
                   o.id_cliente, o.id_captacion, o.id_agente, o.fecha_cierre,
                   o.id_publicacion_origen, o.fuente_origen, o.codigo_origen_capturado,
                   o.fecha_primera_consulta, o.fecha_creacion, o.fecha_actualizacion,
                   cp.nombres_o_razon_social AS cliente_nombre,
                   c.codigo_captacion,
                   l.id_local, l.codigo_local, l.direccion AS local_direccion,
                   l.distrito AS local_distrito,
                   ap.nombres_o_razon_social AS agente_nombre
            FROM oportunidad_comercial o
            INNER JOIN cliente_interesado ci ON ci.id_cliente = o.id_cliente
            INNER JOIN persona cp ON cp.id_persona = ci.id_persona
            INNER JOIN captacion c ON c.id_captacion = o.id_captacion
            INNER JOIN local_comercial l ON l.id_local = c.id_local
            INNER JOIN propietario p ON p.id_propietario = l.id_propietario
            INNER JOIN persona pp ON pp.id_persona = p.id_persona
            INNER JOIN agente_inmobiliario a ON a.id_agente = o.id_agente
            INNER JOIN usuario_interno au ON au.id_usuario = a.id_usuario
            INNER JOIN persona ap ON ap.id_persona = au.id_persona
            """;
    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM oportunidad_comercial o
            INNER JOIN cliente_interesado ci ON ci.id_cliente = o.id_cliente
            INNER JOIN persona cp ON cp.id_persona = ci.id_persona
            INNER JOIN captacion c ON c.id_captacion = o.id_captacion
            INNER JOIN local_comercial l ON l.id_local = c.id_local
            INNER JOIN propietario p ON p.id_propietario = l.id_propietario
            INNER JOIN persona pp ON pp.id_persona = p.id_persona
            INNER JOIN agente_inmobiliario a ON a.id_agente = o.id_agente
            INNER JOIN usuario_interno au ON au.id_usuario = a.id_usuario
            INNER JOIN persona ap ON ap.id_persona = au.id_persona
            """;
    private static final String UPDATE_SQL = """
            UPDATE oportunidad_comercial
            SET codigo_oportunidad = ?, fecha_registro = ?, estado = ?,
                fecha_actualizacion_estado = ?, motivo_cierre = ?, observaciones = ?,
                id_cliente = ?, id_captacion = ?, id_agente = ?, fecha_cierre = ?,
                id_publicacion_origen = ?, fuente_origen = ?, codigo_origen_capturado = ?,
                fecha_primera_consulta = ?
            WHERE id_oportunidad = ?
            """;
    private static final String DELETE_SQL = """
            UPDATE oportunidad_comercial
            SET estado = 'N', fecha_actualizacion_estado = CURRENT_TIMESTAMP,
                fecha_cierre = COALESCE(fecha_cierre, CURRENT_TIMESTAMP)
            WHERE id_oportunidad = ?
            """;

    @Override
    public Long crear(OportunidadComercial oportunidad) {
        validar(oportunidad, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bind(oportunidad, ps);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    oportunidad.setIdOportunidad(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de oportunidad comercial.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear oportunidad comercial.", e);
        }
    }

    @Override
    public Optional<OportunidadComercial> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE o.id_oportunidad = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar oportunidad comercial con id " + id + ".", e);
        }
    }

    @Override
    public List<OportunidadComercial> listarTodos() {
        List<OportunidadComercial> oportunidades = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY o.id_oportunidad");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                oportunidades.add(mapRow(rs));
            }
            return oportunidades;
        } catch (SQLException e) {
            throw new DAOException("Error al listar oportunidades comerciales.", e);
        }
    }

    @Override
    public List<OportunidadComercial> listarPorAgentes(java.util.Collection<Long> idsAgente) {
        List<OportunidadComercial> resultado = new ArrayList<>();
        if (idsAgente == null || idsAgente.isEmpty()) {
            return resultado;
        }
        String sql = SELECT_SQL + " WHERE o.id_agente IN (" + JdbcSupport.placeholders(idsAgente.size()) + ") ORDER BY o.id_oportunidad";
        try (java.sql.Connection conn = DBManager.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Long id : idsAgente) {
                ps.setLong(idx++, id);
            }
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(mapRow(rs));
                }
            }
            return resultado;
        } catch (java.sql.SQLException e) {
            throw new com.controllocal.dao.DAOException("Error al listar oportunidad por agentes.", e);
        }
    }

    @Override
    public List<OportunidadComercial> listarPorCaptaciones(java.util.Collection<Long> idsCaptacion) {
        List<OportunidadComercial> resultado = new ArrayList<>();
        if (idsCaptacion == null || idsCaptacion.isEmpty()) {
            return resultado;
        }
        String sql = SELECT_SQL + " WHERE o.id_captacion IN (" + JdbcSupport.placeholders(idsCaptacion.size()) + ") ORDER BY o.id_oportunidad";
        try (java.sql.Connection conn = DBManager.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Long id : idsCaptacion) {
                ps.setLong(idx++, id);
            }
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(mapRow(rs));
                }
            }
            return resultado;
        } catch (java.sql.SQLException e) {
            throw new com.controllocal.dao.DAOException("Error al listar oportunidad por captaciones.", e);
        }
    }

    @Override
    public List<OportunidadComercial> listarPorCliente(Long idCliente) {
        List<OportunidadComercial> resultado = new ArrayList<>();
        if (idCliente == null) {
            return resultado;
        }
        String sql = SELECT_SQL + " WHERE o.id_cliente = ? ORDER BY o.id_oportunidad";
        try (java.sql.Connection conn = DBManager.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idCliente);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(mapRow(rs));
                }
            }
            return resultado;
        } catch (java.sql.SQLException e) {
            throw new com.controllocal.dao.DAOException("Error al listar oportunidad por cliente.", e);
        }
    }

    @Override
    public List<OportunidadComercial> listarPorPropietario(Long idPropietario) {
        List<OportunidadComercial> resultado = new ArrayList<>();
        if (idPropietario == null) {
            return resultado;
        }
        String sql = SELECT_SQL + " WHERE l.id_propietario = ? ORDER BY o.id_oportunidad";
        try (java.sql.Connection conn = DBManager.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idPropietario);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(mapRow(rs));
                }
            }
            return resultado;
        } catch (java.sql.SQLException e) {
            throw new com.controllocal.dao.DAOException("Error al listar oportunidad por propietario.", e);
        }
    }

    @Override
    public List<OportunidadComercial> listarPorIds(java.util.Collection<Long> ids) {
        List<OportunidadComercial> resultado = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return resultado;
        }
        String sql = SELECT_SQL + " WHERE o.id_oportunidad IN (" + JdbcSupport.placeholders(ids.size()) + ")";
        try (java.sql.Connection conn = DBManager.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Long id : ids) {
                ps.setLong(idx++, id);
            }
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(mapRow(rs));
                }
            }
            return resultado;
        } catch (java.sql.SQLException e) {
            throw new com.controllocal.dao.DAOException("Error al listar oportunidad por ids.", e);
        }
    }

    public List<OportunidadComercial> listarPagina(
            Collection<Long> idsAgente,
            Collection<Long> idsCaptacionAlcance,
            Long idCaptacion,
            Long idCliente,
            String query,
            int offset,
            int limit) {
        if (alcanceVacio(idsAgente, idsCaptacionAlcance)) {
            return new ArrayList<>();
        }
        List<Long> parametros = new ArrayList<>();
        List<String> textos = new ArrayList<>();
        String where = whereListado(idsAgente, idsCaptacionAlcance, idCaptacion, idCliente, query, parametros, textos);
        return listarSql(where + " ORDER BY o.id_oportunidad DESC LIMIT ? OFFSET ?",
                parametros, textos, offset, limit);
    }

    public long contar(
            Collection<Long> idsAgente,
            Collection<Long> idsCaptacionAlcance,
            Long idCaptacion,
            Long idCliente,
            String query) {
        if (alcanceVacio(idsAgente, idsCaptacionAlcance)) {
            return 0L;
        }
        List<Long> parametros = new ArrayList<>();
        List<String> textos = new ArrayList<>();
        String where = whereListado(idsAgente, idsCaptacionAlcance, idCaptacion, idCliente, query, parametros, textos);
        return contarSql(where, parametros, textos);
    }

    @Override
    public boolean actualizar(OportunidadComercial oportunidad) {
        validar(oportunidad, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            bind(oportunidad, ps);
            ps.setLong(15, oportunidad.getIdOportunidad());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar oportunidad comercial con id "
                    + oportunidad.getIdOportunidad() + ".", e);
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
            throw new DAOException("Error al cerrar oportunidad comercial con id " + id + ".", e);
        }
    }

    private void bind(OportunidadComercial oportunidad, PreparedStatement ps) throws SQLException {
        ps.setString(1, oportunidad.getCodigoOportunidad());
        JdbcSupport.setTimestamp(ps, 2, oportunidad.getFechaRegistro());
        JdbcSupport.setEnum(ps, 3, oportunidad.getEstado());
        JdbcSupport.setTimestamp(ps, 4, oportunidad.getFechaActualizacionEstado());
        ps.setString(5, oportunidad.getMotivoCierre());
        ps.setString(6, oportunidad.getObservaciones());
        ps.setLong(7, oportunidad.getClienteInteresado().getIdCliente());
        ps.setLong(8, oportunidad.getCaptacion().getIdCaptacion());
        ps.setLong(9, oportunidad.getAgenteResponsable().getIdAgente());
        JdbcSupport.setTimestamp(ps, 10, oportunidad.getFechaCierre());
        JdbcSupport.setLong(ps, 11, oportunidad.getPublicacionOrigen() != null
                ? oportunidad.getPublicacionOrigen().getIdPublicacion() : null);
        JdbcSupport.setEnum(ps, 12, oportunidad.getFuenteOrigen());
        ps.setString(13, oportunidad.getCodigoOrigenCapturado());
        JdbcSupport.setTimestamp(ps, 14, oportunidad.getFechaPrimeraConsulta() != null
                ? oportunidad.getFechaPrimeraConsulta() : oportunidad.getFechaRegistro());
    }

    private OportunidadComercial mapRow(ResultSet rs) throws SQLException {
        OportunidadComercial oportunidad = new OportunidadComercial();
        oportunidad.setIdOportunidad(rs.getLong("id_oportunidad"));
        oportunidad.setCodigoOportunidad(rs.getString("codigo_oportunidad"));
        oportunidad.setFechaRegistro(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_registro")));
        oportunidad.setEstado(JdbcSupport.getEnum(rs, "estado", EstadoOportunidadComercial.class));
        oportunidad.setFechaActualizacionEstado(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion_estado")));
        oportunidad.setMotivoCierre(rs.getString("motivo_cierre"));
        oportunidad.setObservaciones(rs.getString("observaciones"));
        Persona personaCliente = new Persona();
        personaCliente.setNombresORazonSocial(rs.getString("cliente_nombre"));
        ClienteInteresado cliente = JdbcSupport.cliente(rs.getLong("id_cliente"));
        cliente.setPersona(personaCliente);
        oportunidad.setClienteInteresado(cliente);

        LocalComercial local = JdbcSupport.local(rs.getLong("id_local"));
        local.setCodigoLocal(rs.getString("codigo_local"));
        local.setDireccion(rs.getString("local_direccion"));
        local.setDistrito(rs.getString("local_distrito"));
        Captacion captacion = JdbcSupport.captacion(rs.getLong("id_captacion"));
        captacion.setCodigoCaptacion(rs.getString("codigo_captacion"));
        captacion.setLocalComercial(local);
        oportunidad.setCaptacion(captacion);

        Persona personaAgente = new Persona();
        personaAgente.setNombresORazonSocial(rs.getString("agente_nombre"));
        AgenteInmobiliario agente = JdbcSupport.agente(rs.getLong("id_agente"));
        agente.setPersona(personaAgente);
        oportunidad.setAgenteResponsable(agente);
        oportunidad.setFechaCierre(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_cierre")));
        Long idPublicacion = rs.getObject("id_publicacion_origen", Long.class);
        if (idPublicacion != null) {
            Publicacion publicacion = new Publicacion();
            publicacion.setIdPublicacion(idPublicacion);
            oportunidad.setPublicacionOrigen(publicacion);
        }
        oportunidad.setFuenteOrigen(JdbcSupport.getEnum(rs, "fuente_origen", FuenteOrigen.class));
        oportunidad.setCodigoOrigenCapturado(rs.getString("codigo_origen_capturado"));
        oportunidad.setFechaPrimeraConsulta(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_primera_consulta")));
        oportunidad.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        oportunidad.setFechaActualizacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion")));
        return oportunidad;
    }

    private List<OportunidadComercial> listarSql(
            String sufijoSql,
            List<Long> parametros,
            List<String> textos,
            int offset,
            int limit) {
        List<OportunidadComercial> resultado = new ArrayList<>();
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
            throw new DAOException("Error al listar oportunidades comerciales paginadas.", e);
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
            throw new DAOException("Error al contar oportunidades comerciales.", e);
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

    private static boolean alcanceVacio(Collection<Long> idsAgente, Collection<Long> idsCaptacionAlcance) {
        return (idsAgente != null && idsAgente.isEmpty())
                || (idsCaptacionAlcance != null && idsCaptacionAlcance.isEmpty());
    }

    private static String whereListado(
            Collection<Long> idsAgente,
            Collection<Long> idsCaptacionAlcance,
            Long idCaptacion,
            Long idCliente,
            String query,
            List<Long> parametros,
            List<String> textos) {
        List<String> condiciones = new ArrayList<>();
        if (idsAgente != null) {
            condiciones.add("o.id_agente IN (" + JdbcSupport.placeholders(idsAgente.size()) + ")");
            parametros.addAll(idsAgente);
        }
        if (idsCaptacionAlcance != null) {
            condiciones.add("o.id_captacion IN (" + JdbcSupport.placeholders(idsCaptacionAlcance.size()) + ")");
            parametros.addAll(idsCaptacionAlcance);
        }
        if (idCaptacion != null) {
            condiciones.add("o.id_captacion = ?");
            parametros.add(idCaptacion);
        }
        if (idCliente != null) {
            condiciones.add("o.id_cliente = ?");
            parametros.add(idCliente);
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
                        OR LOWER(pp.nombres_o_razon_social) LIKE ?
                    )
                    """);
            String patron = "%" + query.trim().toLowerCase() + "%";
            for (int i = 0; i < 7; i++) {
                textos.add(patron);
            }
        }
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
    }

    private void validar(OportunidadComercial oportunidad, boolean requiereId) {
        if (oportunidad == null) {
            throw new IllegalArgumentException("La oportunidad comercial no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(oportunidad.getIdOportunidad());
        }
        if (oportunidad.getCodigoOportunidad() == null || oportunidad.getCodigoOportunidad().isBlank()
                || oportunidad.getFechaRegistro() == null || oportunidad.getEstado() == null) {
            throw new IllegalArgumentException("La oportunidad comercial tiene campos obligatorios incompletos.");
        }
        if (oportunidad.getFuenteOrigen() == null) {
            oportunidad.setFuenteOrigen(FuenteOrigen.OTRO);
        }
        if (oportunidad.getFechaPrimeraConsulta() == null) {
            oportunidad.setFechaPrimeraConsulta(oportunidad.getFechaRegistro());
        }
        JdbcSupport.validarId(JdbcSupport.getIdCliente(oportunidad.getClienteInteresado()));
        JdbcSupport.validarId(JdbcSupport.getIdCaptacion(oportunidad.getCaptacion()));
        JdbcSupport.validarId(JdbcSupport.getIdAgente(oportunidad.getAgenteResponsable()));
    }
}
