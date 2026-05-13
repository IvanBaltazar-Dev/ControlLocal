package com.controllocal.dao.impl;

import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.persona.TipoPersona;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.RolUsuarioInterno;

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

    static LocalDate toLocalDate(Date date) {
        return date != null ? date.toLocalDate() : null;
    }

    static LocalTime toLocalTime(Time time) {
        return time != null ? time.toLocalTime() : null;
    }

    static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    static Persona mapPersona(ResultSet rs) throws SQLException {
        Persona persona = new Persona();
        persona.setIdPersona(rs.getLong("id_persona"));
        persona.setTipoPersona(TipoPersona.valueOf(rs.getString("tipo_persona")));
        persona.setTipoDocumento(rs.getString("tipo_documento"));
        persona.setNumeroDocumento(rs.getString("numero_documento"));
        persona.setNombresORazonSocial(rs.getString("nombres_o_razon_social"));
        persona.setTelefono(rs.getString("telefono"));
        persona.setCorreo(rs.getString("correo"));
        persona.setEstado(EstadoActivoInactivo.valueOf(rs.getString("estado")));
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

    static Long getIdInteraccion(InteraccionComercial interaccion) {
        return interaccion != null ? interaccion.getIdInteraccion() : null;
    }

    static Long getIdVisita(Visita visita) {
        return visita != null ? visita.getIdVisita() : null;
    }
}
