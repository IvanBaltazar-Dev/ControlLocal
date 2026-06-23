package com.controllocal.dao.impl;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.controllocal.model.CodigoEnum;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.Publicacion;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.inmueble.Distrito;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.persona.enums.EstadoActivoInactivo;
import com.controllocal.model.persona.enums.TipoDocumentoIdentidad;
import com.controllocal.model.persona.enums.TipoPersona;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.enums.RolUsuarioInterno;

final class JdbcSupport {

    private JdbcSupport() {
    }

    static void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El id debe ser mayor que cero.");
        }
    }

    static void setDate(PreparedStatement ps, int index, LocalDate value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setDate(index, Date.valueOf(value));
        }
    }

    static void setTime(PreparedStatement ps, int index, LocalTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIME);
        } else {
            ps.setTime(index, Time.valueOf(value));
        }
    }

    static void setTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    static void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    static void setEnum(PreparedStatement ps, int index, CodigoEnum value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.CHAR);
        } else {
            ps.setString(index, value.getCodigo());
        }
    }

    static <E extends Enum<E> & CodigoEnum> E getEnum(ResultSet rs, String column, Class<E> enumType) throws SQLException {
        return CodigoEnum.fromCodigo(enumType, rs.getString(column));
    }

    static <E extends Enum<E> & CodigoEnum> E getNullableEnum(ResultSet rs, String column, Class<E> enumType) throws SQLException {
        String codigo = rs.getString(column);
        return codigo != null ? CodigoEnum.fromCodigo(enumType, codigo) : null;
    }

    static LocalDate toLocalDate(Date date) {
        return date != null ? date.toLocalDate() : null;
    }

    static LocalTime toLocalTime(Time time) {
        return time != null ? time.toLocalTime() : null;
    }

    static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    static void setBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, value);
        }
    }

    static Boolean getNullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean valor = rs.getBoolean(column);
        return rs.wasNull() ? null : valor;
    }

    static void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int valor = rs.getInt(column);
        return rs.wasNull() ? null : valor;
    }

    static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long valor = rs.getLong(column);
        return rs.wasNull() ? null : valor;
    }

    static Persona mapPersona(ResultSet rs) throws SQLException {
        Persona persona = new Persona();
        persona.setIdPersona(rs.getLong("id_persona"));
        persona.setTipoPersona(getEnum(rs, "tipo_persona", TipoPersona.class));
        persona.setTipoDocumento(getEnum(rs, "tipo_documento", TipoDocumentoIdentidad.class));
        persona.setNumeroDocumento(rs.getString("numero_documento"));
        persona.setNombresORazonSocial(rs.getString("nombres_o_razon_social"));
        persona.setTelefono(rs.getString("telefono"));
        persona.setCorreo(rs.getString("correo"));
        persona.setEstado(getEnum(rs, "estado", EstadoActivoInactivo.class));
        persona.setConsentimientoUsoDato(getNullableBoolean(rs, "consentimiento_uso_dato"));
        persona.setFechaCreacion(toLocalDateTime(rs.getTimestamp("fecha_creacion")));
        persona.setFechaActualizacion(toLocalDateTime(rs.getTimestamp("fecha_actualizacion")));
        return persona;
    }

    static Propietario propietario(Long id) {
        Propietario propietario = new Propietario();
        propietario.setIdPropietario(id);
        return propietario;
    }

    static LocalComercial local(Long id) {
        LocalComercial local = new LocalComercial();
        local.setIdLocal(id);
        return local;
    }

    static Captacion captacion(Long id) {
        Captacion captacion = new Captacion();
        captacion.setIdCaptacion(id);
        return captacion;
    }

    static ClienteInteresado cliente(Long id) {
        ClienteInteresado cliente = new ClienteInteresado();
        cliente.setIdCliente(id);
        return cliente;
    }

    static Broker broker(Long id) {
        Broker broker = new Broker();
        broker.setIdBroker(id);
        broker.setRol(RolUsuarioInterno.BROKER);
        return broker;
    }

    static AgenteInmobiliario agente(Long id) {
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(id);
        agente.setRol(RolUsuarioInterno.AGENTE);
        return agente;
    }

    static InteraccionComercial interaccion(Long id) {
        InteraccionComercial interaccion = new InteraccionComercial();
        interaccion.setIdInteraccion(id);
        return interaccion;
    }

    static Visita visita(Long id) {
        Visita visita = new Visita();
        visita.setIdVisita(id);
        return visita;
    }

    static SolicitudAlquiler solicitud(Long id) {
        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        solicitud.setIdSolicitud(id);
        return solicitud;
    }

    static com.controllocal.model.comercial.Prospeccion prospeccion(Long id) {
        com.controllocal.model.comercial.Prospeccion prospeccion = new com.controllocal.model.comercial.Prospeccion();
        prospeccion.setIdProspeccion(id);
        return prospeccion;
    }

    static Long getIdProspeccion(com.controllocal.model.comercial.Prospeccion prospeccion) {
        return prospeccion != null ? prospeccion.getIdProspeccion() : null;
    }

    static OportunidadComercial oportunidad(Long id) {
        OportunidadComercial oportunidad = new OportunidadComercial();
        oportunidad.setIdOportunidad(id);
        return oportunidad;
    }

    static Publicacion publicacion(Long id) {
        Publicacion publicacion = new Publicacion();
        publicacion.setIdPublicacion(id);
        return publicacion;
    }

    static ContratoAlquiler contrato(Long id) {
        ContratoAlquiler contrato = new ContratoAlquiler();
        contrato.setIdContratoAlquiler(id);
        return contrato;
    }

    static Distrito distrito(Long id) {
        Distrito distrito = new Distrito();
        distrito.setIdDistrito(id);
        return distrito;
    }

    static com.controllocal.model.usuario.UsuarioInterno usuario(Long id) {
        com.controllocal.model.usuario.UsuarioInterno usuario =
                new com.controllocal.model.usuario.UsuarioInterno();
        usuario.setIdUsuarioInterno(id);
        return usuario;
    }

    static Long getIdPropietario(Propietario propietario) {
        return propietario != null ? propietario.getIdPropietario() : null;
    }

    static Long getIdPersona(Persona persona) {
        return persona != null ? persona.getIdPersona() : null;
    }

    static Long getIdUsuario(com.controllocal.model.usuario.UsuarioInterno usuario) {
        return usuario != null ? usuario.getIdUsuarioInterno() : null;
    }

    static Long getIdLocal(LocalComercial local) {
        return local != null ? local.getIdLocal() : null;
    }

    static Long getIdCaptacion(Captacion captacion) {
        return captacion != null ? captacion.getIdCaptacion() : null;
    }

    static Long getIdCliente(ClienteInteresado cliente) {
        return cliente != null ? cliente.getIdCliente() : null;
    }

    static Long getIdAgente(AgenteInmobiliario agente) {
        return agente != null ? agente.getIdAgente() : null;
    }

    static Long getIdBroker(Broker broker) {
        return broker != null ? broker.getIdBroker() : null;
    }

    static Long getIdSolicitud(SolicitudAlquiler solicitud) {
        return solicitud != null ? solicitud.getIdSolicitud() : null;
    }

    static Long getIdOportunidad(OportunidadComercial oportunidad) {
        return oportunidad != null ? oportunidad.getIdOportunidad() : null;
    }

    static Long getIdInteraccion(InteraccionComercial interaccion) {
        return interaccion != null ? interaccion.getIdInteraccion() : null;
    }

    static Long getIdVisita(Visita visita) {
        return visita != null ? visita.getIdVisita() : null;
    }
}
