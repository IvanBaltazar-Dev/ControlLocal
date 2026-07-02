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
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.enums.FormaPago;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class SolicitudAlquilerDAOImpl implements SolicitudAlquilerDAO {

    private static final String INSERT_SQL = """
            INSERT INTO solicitud_alquiler (
                codigo_solicitud, fecha_registro, monto_propuesto, plazo_tentativo,
                observaciones, estado, fecha_actualizacion_estado,
                fecha_vigencia_oferta, id_oportunidad, id_agente,
                plazo_contrato_meses, fecha_inicio_contrato, forma_pago, meses_garantia, meses_adelanto
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT s.id_solicitud, s.codigo_solicitud, s.fecha_registro, s.monto_propuesto,
                   s.plazo_tentativo, s.observaciones, s.estado, s.fecha_actualizacion_estado,
                   s.fecha_vigencia_oferta,
                   s.plazo_contrato_meses, s.fecha_inicio_contrato, s.forma_pago,
                   s.meses_garantia, s.meses_adelanto,
                   s.id_oportunidad, o.codigo_oportunidad, o.id_cliente, o.id_captacion,
                   s.id_agente, s.fecha_creacion AS solicitud_fecha_creacion,
                   s.fecha_actualizacion AS solicitud_fecha_actualizacion,
                   cp.nombres_o_razon_social AS cliente_nombre,
                   c.codigo_captacion,
                   l.id_local, l.codigo_local, l.direccion AS local_direccion,
                   l.distrito AS local_distrito, l.id_propietario,
                   pp.nombres_o_razon_social AS propietario_nombre,
                   ap.nombres_o_razon_social AS agente_nombre
            FROM solicitud_alquiler s
            INNER JOIN oportunidad_comercial o ON s.id_oportunidad = o.id_oportunidad
            INNER JOIN cliente_interesado ci ON ci.id_cliente = o.id_cliente
            INNER JOIN persona cp ON cp.id_persona = ci.id_persona
            INNER JOIN captacion c ON c.id_captacion = o.id_captacion
            INNER JOIN local_comercial l ON l.id_local = c.id_local
            INNER JOIN propietario pr ON pr.id_propietario = l.id_propietario
            INNER JOIN persona pp ON pp.id_persona = pr.id_persona
            INNER JOIN agente_inmobiliario a ON a.id_agente = s.id_agente
            INNER JOIN usuario_interno au ON au.id_usuario = a.id_usuario
            INNER JOIN persona ap ON ap.id_persona = au.id_persona
            """;
    private static final String UPDATE_SQL = """
            UPDATE solicitud_alquiler
            SET codigo_solicitud = ?, fecha_registro = ?, monto_propuesto = ?,
                plazo_tentativo = ?, observaciones = ?, estado = ?,
                fecha_actualizacion_estado = ?, fecha_vigencia_oferta = ?,
                id_oportunidad = ?, id_agente = ?,
                plazo_contrato_meses = ?, fecha_inicio_contrato = ?, forma_pago = ?,
                meses_garantia = ?, meses_adelanto = ?
            WHERE id_solicitud = ?
            """;
    private static final String DELETE_SQL = """
            UPDATE solicitud_alquiler
            SET estado = 'D', fecha_actualizacion_estado = CURRENT_TIMESTAMP
            WHERE id_solicitud = ?
            """;

    @Override
    public Long crear(SolicitudAlquiler solicitud) {
        validar(solicitud, false);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, solicitud.getCodigoSolicitud());
            JdbcSupport.setDate(ps, 2, solicitud.getFechaRegistro());
            ps.setBigDecimal(3, solicitud.getMontoPropuesto());
            ps.setString(4, solicitud.getPlazoTentativo());
            ps.setString(5, solicitud.getObservaciones());
            JdbcSupport.setEnum(ps, 6, solicitud.getEstado());
            JdbcSupport.setTimestamp(ps, 7, solicitud.getFechaActualizacionEstado());
            JdbcSupport.setDate(ps, 8, solicitud.getFechaVigenciaOferta());
            ps.setLong(9, solicitud.getOportunidadComercial().getIdOportunidad());
            ps.setLong(10, solicitud.getAgenteResponsable().getIdAgente());
            JdbcSupport.setInteger(ps, 11, solicitud.getPlazoContratoMeses());
            JdbcSupport.setDate(ps, 12, solicitud.getFechaInicioContrato());
            JdbcSupport.setEnum(ps, 13, solicitud.getFormaPago());
            JdbcSupport.setInteger(ps, 14, solicitud.getMesesGarantia());
            JdbcSupport.setInteger(ps, 15, solicitud.getMesesAdelanto());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    solicitud.setIdSolicitud(id);
                    return id;
                }
            }
            throw new DAOException("No se genero el id de solicitud de alquiler.");
        } catch (SQLException e) {
            throw new DAOException("Error al crear solicitud de alquiler.", e);
        }
    }

    @Override
    public Optional<SolicitudAlquiler> buscarPorId(Long id) {
        JdbcSupport.validarId(id);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE id_solicitud = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar solicitud de alquiler con id " + id + ".", e);
        }
    }

    @Override
    public Optional<SolicitudAlquiler> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " WHERE s.codigo_solicitud = ?")) {
            ps.setString(1, codigo.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error al buscar solicitud de alquiler con codigo " + codigo + ".", e);
        }
    }

    @Override
    public List<SolicitudAlquiler> listarTodos() {
        List<SolicitudAlquiler> solicitudes = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + " ORDER BY id_solicitud");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                solicitudes.add(mapRow(rs));
            }
            return solicitudes;
        } catch (SQLException e) {
            throw new DAOException("Error al listar solicitudes de alquiler.", e);
        }
    }

    @Override
    public List<SolicitudAlquiler> listarPorIds(Collection<Long> ids) {
        List<SolicitudAlquiler> solicitudes = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return solicitudes;
        }
        String sql = SELECT_SQL + " WHERE s.id_solicitud IN (" + JdbcSupport.placeholders(ids.size()) + ")";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Long id : ids) {
                ps.setLong(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    solicitudes.add(mapRow(rs));
                }
            }
            return solicitudes;
        } catch (SQLException e) {
            throw new DAOException("Error al listar solicitudes de alquiler por ids.", e);
        }
    }

    @Override
    public List<SolicitudAlquiler> listarPorAgentes(Collection<Long> idsAgente) {
        List<SolicitudAlquiler> solicitudes = new ArrayList<>();
        if (idsAgente == null || idsAgente.isEmpty()) {
            return solicitudes;
        }
        String sql = SELECT_SQL + " WHERE s.id_agente IN (" + JdbcSupport.placeholders(idsAgente.size())
                + ") ORDER BY s.id_solicitud";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Long id : idsAgente) {
                ps.setLong(idx++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    solicitudes.add(mapRow(rs));
                }
            }
            return solicitudes;
        } catch (SQLException e) {
            throw new DAOException("Error al listar solicitudes de alquiler por agentes.", e);
        }
    }

    @Override
    public List<SolicitudAlquiler> listarPorCaptaciones(Collection<Long> idsCaptacion) {
        List<SolicitudAlquiler> solicitudes = new ArrayList<>();
        if (idsCaptacion == null || idsCaptacion.isEmpty()) {
            return solicitudes;
        }
        String sql = SELECT_SQL + " WHERE o.id_captacion IN (" + JdbcSupport.placeholders(idsCaptacion.size())
                + ") ORDER BY s.id_solicitud";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Long id : idsCaptacion) {
                ps.setLong(idx++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    solicitudes.add(mapRow(rs));
                }
            }
            return solicitudes;
        } catch (SQLException e) {
            throw new DAOException("Error al listar solicitudes de alquiler por captaciones.", e);
        }
    }

    @Override
    public List<SolicitudAlquiler> listarPorCliente(Long idCliente) {
        List<SolicitudAlquiler> solicitudes = new ArrayList<>();
        if (idCliente == null) {
            return solicitudes;
        }
        String sql = SELECT_SQL + " WHERE o.id_cliente = ? ORDER BY s.id_solicitud";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    solicitudes.add(mapRow(rs));
                }
            }
            return solicitudes;
        } catch (SQLException e) {
            throw new DAOException("Error al listar solicitudes de alquiler por cliente.", e);
        }
    }

    @Override
    public List<SolicitudAlquiler> listarPorPropietario(Long idPropietario) {
        List<SolicitudAlquiler> solicitudes = new ArrayList<>();
        if (idPropietario == null) {
            return solicitudes;
        }
        String sql = SELECT_SQL + " WHERE l.id_propietario = ? ORDER BY s.id_solicitud";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idPropietario);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    solicitudes.add(mapRow(rs));
                }
            }
            return solicitudes;
        } catch (SQLException e) {
            throw new DAOException("Error al listar solicitudes de alquiler por propietario.", e);
        }
    }

    @Override
    public List<SolicitudAlquiler> listarPagina(
            Collection<Long> idsAgente, Long idOportunidad, Long idCaptacion, int offset, int limite) {
        if (idsAgente != null && idsAgente.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> params = new ArrayList<>();
        String where = construirWhere(idsAgente, idOportunidad, idCaptacion, params);
        String sql = SELECT_SQL + where + " ORDER BY s.id_solicitud DESC LIMIT ? OFFSET ?";
        List<SolicitudAlquiler> solicitudes = new ArrayList<>();
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Long p : params) {
                ps.setLong(idx++, p);
            }
            ps.setInt(idx++, Math.max(1, Math.min(limite, 500)));
            ps.setInt(idx, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    solicitudes.add(mapRow(rs));
                }
            }
            return solicitudes;
        } catch (SQLException e) {
            throw new DAOException("Error al paginar solicitudes de alquiler.", e);
        }
    }

    @Override
    public long contar(Collection<Long> idsAgente, Long idOportunidad, Long idCaptacion) {
        if (idsAgente != null && idsAgente.isEmpty()) {
            return 0L;
        }
        List<Long> params = new ArrayList<>();
        String where = construirWhere(idsAgente, idOportunidad, idCaptacion, params);
        // El INNER JOIN a oportunidad no altera el conteo (id_oportunidad es obligatorio y unico
        // por solicitud) y habilita el filtro por id_captacion.
        String sql = "SELECT COUNT(*) FROM solicitud_alquiler s "
                + "INNER JOIN oportunidad_comercial o ON s.id_oportunidad = o.id_oportunidad" + where;
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Long p : params) {
                ps.setLong(idx++, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new DAOException("Error al contar solicitudes de alquiler.", e);
        }
    }

    // Arma el WHERE (y acumula los parametros) para listarPagina/contar. Alcance por agentes
    // (null = sin filtro) y filtros opcionales por oportunidad/captacion.
    private static String construirWhere(
            Collection<Long> idsAgente, Long idOportunidad, Long idCaptacion, List<Long> params) {
        List<String> conds = new ArrayList<>();
        if (idsAgente != null) {
            conds.add("s.id_agente IN (" + JdbcSupport.placeholders(idsAgente.size()) + ")");
            params.addAll(idsAgente);
        }
        if (idOportunidad != null) {
            conds.add("s.id_oportunidad = ?");
            params.add(idOportunidad);
        }
        if (idCaptacion != null) {
            conds.add("o.id_captacion = ?");
            params.add(idCaptacion);
        }
        return conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);
    }

    @Override
    public boolean actualizar(SolicitudAlquiler solicitud) {
        validar(solicitud, true);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setString(1, solicitud.getCodigoSolicitud());
            JdbcSupport.setDate(ps, 2, solicitud.getFechaRegistro());
            ps.setBigDecimal(3, solicitud.getMontoPropuesto());
            ps.setString(4, solicitud.getPlazoTentativo());
            ps.setString(5, solicitud.getObservaciones());
            JdbcSupport.setEnum(ps, 6, solicitud.getEstado());
            JdbcSupport.setTimestamp(ps, 7, solicitud.getFechaActualizacionEstado());
            JdbcSupport.setDate(ps, 8, solicitud.getFechaVigenciaOferta());
            ps.setLong(9, solicitud.getOportunidadComercial().getIdOportunidad());
            ps.setLong(10, solicitud.getAgenteResponsable().getIdAgente());
            JdbcSupport.setInteger(ps, 11, solicitud.getPlazoContratoMeses());
            JdbcSupport.setDate(ps, 12, solicitud.getFechaInicioContrato());
            JdbcSupport.setEnum(ps, 13, solicitud.getFormaPago());
            JdbcSupport.setInteger(ps, 14, solicitud.getMesesGarantia());
            JdbcSupport.setInteger(ps, 15, solicitud.getMesesAdelanto());
            ps.setLong(16, solicitud.getIdSolicitud());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DAOException("Error al actualizar solicitud de alquiler con id " + solicitud.getIdSolicitud() + ".", e);
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
            throw new DAOException("Error al eliminar solicitud de alquiler con id " + id + ".", e);
        }
    }

    private SolicitudAlquiler mapRow(ResultSet rs) throws SQLException {
        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        solicitud.setIdSolicitud(rs.getLong("id_solicitud"));
        solicitud.setCodigoSolicitud(rs.getString("codigo_solicitud"));
        solicitud.setFechaRegistro(JdbcSupport.toLocalDate(rs.getDate("fecha_registro")));
        solicitud.setMontoPropuesto(rs.getBigDecimal("monto_propuesto"));
        solicitud.setPlazoTentativo(rs.getString("plazo_tentativo"));
        solicitud.setObservaciones(rs.getString("observaciones"));
        solicitud.setEstado(JdbcSupport.getEnum(rs, "estado", EstadoSolicitudAlquiler.class));
        solicitud.setFechaActualizacionEstado(JdbcSupport.toLocalDateTime(rs.getTimestamp("fecha_actualizacion_estado")));
        solicitud.setFechaVigenciaOferta(JdbcSupport.toLocalDate(rs.getDate("fecha_vigencia_oferta")));
        solicitud.setPlazoContratoMeses(JdbcSupport.getNullableInt(rs, "plazo_contrato_meses"));
        solicitud.setFechaInicioContrato(JdbcSupport.toLocalDate(rs.getDate("fecha_inicio_contrato")));
        solicitud.setFormaPago(JdbcSupport.getNullableEnum(rs, "forma_pago", FormaPago.class));
        solicitud.setMesesGarantia(JdbcSupport.getNullableInt(rs, "meses_garantia"));
        solicitud.setMesesAdelanto(JdbcSupport.getNullableInt(rs, "meses_adelanto"));
        Persona personaCliente = new Persona();
        personaCliente.setNombresORazonSocial(rs.getString("cliente_nombre"));
        ClienteInteresado cliente = JdbcSupport.cliente(rs.getLong("id_cliente"));
        cliente.setPersona(personaCliente);

        LocalComercial local = JdbcSupport.local(rs.getLong("id_local"));
        local.setCodigoLocal(rs.getString("codigo_local"));
        local.setDireccion(rs.getString("local_direccion"));
        local.setDistrito(rs.getString("local_distrito"));
        Propietario propietario = JdbcSupport.propietario(rs.getLong("id_propietario"));
        propietario.setNombresORazonSocial(rs.getString("propietario_nombre"));
        local.setPropietario(propietario);
        Captacion captacion = JdbcSupport.captacion(rs.getLong("id_captacion"));
        captacion.setCodigoCaptacion(rs.getString("codigo_captacion"));
        captacion.setLocalComercial(local);

        Persona personaAgente = new Persona();
        personaAgente.setNombresORazonSocial(rs.getString("agente_nombre"));
        AgenteInmobiliario agente = JdbcSupport.agente(rs.getLong("id_agente"));
        agente.setPersona(personaAgente);

        OportunidadComercial oportunidad = JdbcSupport.oportunidad(rs.getLong("id_oportunidad"));
        oportunidad.setCodigoOportunidad(rs.getString("codigo_oportunidad"));
        oportunidad.setClienteInteresado(cliente);
        oportunidad.setCaptacion(captacion);
        oportunidad.setAgenteResponsable(agente);

        solicitud.setOportunidadComercial(oportunidad);
        solicitud.setClienteInteresado(cliente);
        solicitud.setCaptacion(captacion);
        solicitud.setAgenteResponsable(agente);
        solicitud.setFechaCreacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("solicitud_fecha_creacion")));
        solicitud.setFechaActualizacion(JdbcSupport.toLocalDateTime(rs.getTimestamp("solicitud_fecha_actualizacion")));
        return solicitud;
    }

    private void validar(SolicitudAlquiler solicitud, boolean requiereId) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud de alquiler no puede ser null.");
        }
        if (requiereId) {
            JdbcSupport.validarId(solicitud.getIdSolicitud());
        }
        if (solicitud.getCodigoSolicitud() == null || solicitud.getCodigoSolicitud().isBlank()
                || solicitud.getFechaRegistro() == null || solicitud.getMontoPropuesto() == null
                || solicitud.getMontoPropuesto().signum() <= 0 || solicitud.getEstado() == null) {
            throw new IllegalArgumentException("La solicitud de alquiler tiene campos obligatorios incompletos.");
        }
        JdbcSupport.validarId(JdbcSupport.getIdOportunidad(solicitud.getOportunidadComercial()));
        JdbcSupport.validarId(JdbcSupport.getIdAgente(solicitud.getAgenteResponsable()));
    }
}
