package com.controllocal.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.inmueble.enums.EstadoLocalComercial;
import com.controllocal.model.inmueble.enums.EstadoPublicacion;
import com.controllocal.model.inmueble.enums.TipoInmueble;
import com.controllocal.model.inmueble.enums.UsoInmueble;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.persona.enums.EstadoActivoInactivo;
import com.controllocal.model.persona.enums.TipoDocumentoIdentidad;
import com.controllocal.model.persona.enums.TipoPersona;
import com.controllocal.rest.http.ApiException;

/**
 * Contratos JSON del API. Las respuestas exponen solo datos publicos del
 * dominio (nunca hashes de contrasena ni metadatos internos) y las peticiones
 * se convierten a entidades validadas por la capa de negocio.
 */
public final class Dtos {

    private Dtos() {
    }

    // ---------- Autenticacion ----------

    public record LoginRequest(String usuario, @JsonAlias("password") String contrasena) {
    }

    public record LoginResponse(
            String token,
            long expiraEnSegundos,
            String rol,
            long idUsuario,
            long idDominio,
            String nombre,
            String usuario,
            LocalDateTime expiraEn) {
    }

    // ---------- Propietarios ----------

    public record PropietarioRequest(
            String tipoPersona,
            String tipoDocumento,
            String numeroDocumento,
            String nombre,
            String telefono,
            String correo,
            Boolean consentimientoUsoDato,
            String estado) {

        public Propietario aEntidad() {
            Propietario propietario = new Propietario();
            propietario.setPersona(personaDesde(tipoPersona, tipoDocumento, numeroDocumento,
                    nombre, telefono, correo, consentimientoUsoDato, estado));
            return propietario;
        }
    }

    public record PropietarioResponse(
            Long id,
            String tipoPersona,
            String tipoDocumento,
            String numeroDocumento,
            String nombre,
            String telefono,
            String correo,
            String estado,
            Boolean consentimientoUsoDato,
            LocalDateTime fechaCreacion) {

        public static PropietarioResponse desde(Propietario p) {
            return new PropietarioResponse(
                    p.getIdPropietario(),
                    codigo(p.getTipoPersona()),
                    codigo(p.getTipoDocumento()),
                    p.getNumeroDocumento(),
                    p.getNombresORazonSocial(),
                    p.getTelefono(),
                    p.getCorreo(),
                    codigo(p.getEstado()),
                    p.getPersona() != null ? p.getPersona().getConsentimientoUsoDato() : null,
                    p.getFechaCreacion());
        }
    }

    // ---------- Clientes interesados ----------

    public record ClienteRequest(
            String tipoPersona,
            String tipoDocumento,
            String numeroDocumento,
            String nombre,
            String telefono,
            String correo,
            String rubroComercial,
            Boolean consentimientoContacto,
            Boolean consentimientoUsoDato,
            String estado) {

        public ClienteInteresado aEntidad() {
            ClienteInteresado cliente = new ClienteInteresado();
            cliente.setPersona(personaDesde(tipoPersona, tipoDocumento, numeroDocumento,
                    nombre, telefono, correo, consentimientoUsoDato, estado));
            cliente.setRubroComercial(rubroComercial);
            cliente.setConsentimientoContacto(consentimientoContacto);
            cliente.setConsentimientoUsoDato(consentimientoUsoDato);
            return cliente;
        }
    }

    public record ClienteResponse(
            Long id,
            String tipoPersona,
            String tipoDocumento,
            String numeroDocumento,
            String nombre,
            String telefono,
            String correo,
            String rubroComercial,
            String estado,
            Boolean consentimientoContacto,
            Boolean consentimientoUsoDato,
            LocalDateTime fechaCreacion) {

        public static ClienteResponse desde(ClienteInteresado c) {
            Persona persona = c.getPersona();
            return new ClienteResponse(
                    c.getIdCliente(),
                    persona != null ? codigo(persona.getTipoPersona()) : null,
                    persona != null ? codigo(persona.getTipoDocumento()) : null,
                    persona != null ? persona.getNumeroDocumento() : null,
                    persona != null ? persona.getNombresORazonSocial() : null,
                    persona != null ? persona.getTelefono() : null,
                    persona != null ? persona.getCorreo() : null,
                    c.getRubroComercial(),
                    persona != null ? codigo(persona.getEstado()) : null,
                    c.getConsentimientoContacto(),
                    c.getConsentimientoUsoDato(),
                    persona != null ? persona.getFechaCreacion() : null);
        }
    }

    // ---------- Locales comerciales ----------

    public record LocalRequest(
            String codigoLocal,
            String direccion,
            String distrito,
            BigDecimal metraje,
            BigDecimal precioReferencial,
            String rubroPermitido,
            String descripcion,
            Long idPropietario,
            String estado,
            String tipoInmueble,
            String uso,
            Integer ambientes,
            Integer antiguedadAnios,
            String zonaUrbanizacion,
            BigDecimal geoLat,
            BigDecimal geoLong,
            String estadoPublicacion) {

        public LocalComercial aEntidad() {
            LocalComercial local = new LocalComercial();
            local.setCodigoLocal(codigoLocal);
            local.setDireccion(direccion);
            local.setDistrito(distrito);
            local.setMetraje(metraje);
            local.setPrecioReferencial(precioReferencial);
            local.setRubroPermitido(rubroPermitido);
            local.setDescripcion(descripcion);
            local.setIdPropietario(idPropietario);
            local.setEstado(estado == null || estado.isBlank()
                    ? EstadoLocalComercial.DISPONIBLE
                    : enumDesde(EstadoLocalComercial.class, estado, "estado del local"));
            local.setTipoInmueble(enumOpcional(TipoInmueble.class, tipoInmueble, "tipo de inmueble"));
            local.setUso(enumOpcional(UsoInmueble.class, uso, "uso del inmueble"));
            local.setAmbientes(ambientes);
            local.setAntiguedadAnios(antiguedadAnios);
            local.setZonaUrbanizacion(zonaUrbanizacion);
            local.setGeoLat(geoLat);
            local.setGeoLong(geoLong);
            local.setEstadoPublicacion(
                    enumOpcional(EstadoPublicacion.class, estadoPublicacion, "estado de publicacion"));
            return local;
        }
    }

    public record LocalResponse(
            Long id,
            String codigoLocal,
            String direccion,
            String distrito,
            BigDecimal metraje,
            BigDecimal precioReferencial,
            String rubroPermitido,
            String descripcion,
            String estado,
            Long idPropietario,
            String propietarioNombre,
            String tipoInmueble,
            String uso,
            Integer ambientes,
            Integer antiguedadAnios,
            String zonaUrbanizacion,
            BigDecimal geoLat,
            BigDecimal geoLong,
            String estadoPublicacion,
            LocalDateTime fechaRegistro) {

        public static LocalResponse desde(LocalComercial l) {
            return new LocalResponse(
                    l.getIdLocal(),
                    l.getCodigoLocal(),
                    l.getDireccion(),
                    l.getDistrito(),
                    l.getMetraje(),
                    l.getPrecioReferencial(),
                    l.getRubroPermitido(),
                    l.getDescripcion(),
                    codigo(l.getEstado()),
                    l.getIdPropietario(),
                    l.getPropietario() != null ? l.getPropietario().getNombresORazonSocial() : null,
                    codigo(l.getTipoInmueble()),
                    codigo(l.getUso()),
                    l.getAmbientes(),
                    l.getAntiguedadAnios(),
                    l.getZonaUrbanizacion(),
                    l.getGeoLat(),
                    l.getGeoLong(),
                    codigo(l.getEstadoPublicacion()),
                    l.getFechaRegistro());
        }
    }

    // ---------- Captaciones ----------

    public record CaptacionRequest(
            String codigoCaptacion,
            LocalDate fechaCaptacion,
            LocalDate fechaInicioVigencia,
            LocalDate fechaFinVigencia,
            BigDecimal comisionPactada,
            String observaciones,
            Long idLocal,
            Long idAgente,
            String motivoOperacion,
            Integer urgencia,
            Boolean exclusividad) {
    }

    public record CaptacionResponse(
            Long id,
            String codigoCaptacion,
            LocalDate fechaCaptacion,
            LocalDate fechaInicioVigencia,
            LocalDate fechaFinVigencia,
            BigDecimal comisionPactada,
            String observaciones,
            String estado,
            String motivoOperacion,
            Integer urgencia,
            Boolean exclusividad,
            String observacionRevision,
            LocalDateTime fechaRevision,
            Long idLocal,
            String direccionLocal,
            String distritoLocal,
            BigDecimal areaM2,
            String rubro,
            String propietarioNombre,
            Long idAgente,
            String agenteNombre,
            Long idBrokerRevisor) {

        public static CaptacionResponse desde(Captacion c) {
            LocalComercial local = c.getLocalComercial();
            var agente = c.getAgenteResponsable();
            return new CaptacionResponse(
                    c.getIdCaptacion(),
                    c.getCodigoCaptacion(),
                    c.getFechaCaptacion(),
                    c.getFechaInicioVigencia(),
                    c.getFechaFinVigencia(),
                    c.getComisionPactada(),
                    c.getObservaciones(),
                    codigo(c.getEstado()),
                    codigo(c.getMotivoOperacion()),
                    c.getUrgencia(),
                    c.getExclusividad(),
                    c.getObservacionRevision(),
                    c.getFechaRevision(),
                    local != null ? local.getIdLocal() : null,
                    local != null ? local.getDireccion() : null,
                    local != null ? local.getDistrito() : null,
                    local != null ? local.getMetraje() : null,
                    local != null ? local.getRubroPermitido() : null,
                    local != null && local.getPropietario() != null
                            ? local.getPropietario().getNombresORazonSocial() : null,
                    agente != null ? agente.getIdAgente() : null,
                    agente != null ? agente.getPersona() != null
                            ? agente.getPersona().getNombresORazonSocial() : null : null,
                    c.getBrokerRevisor() != null ? c.getBrokerRevisor().getIdBroker() : null);
        }
    }

    public record DecisionRequest(String accion, String observacion) {
    }

    public record ReasignacionRequest(Long idAgenteNuevo, String motivo) {
    }

    public record CierreRequest(String motivo) {
    }

    // ---------- Soporte ----------

    private static Persona personaDesde(String tipoPersona, String tipoDocumento, String numeroDocumento,
                                        String nombre, String telefono, String correo, Boolean consentimiento,
                                        String estado) {
        Persona persona = new Persona();
        persona.setTipoPersona(enumDesde(TipoPersona.class, tipoPersona, "tipo de persona"));
        persona.setTipoDocumento(enumDesde(TipoDocumentoIdentidad.class, tipoDocumento, "tipo de documento"));
        persona.setNumeroDocumento(numeroDocumento);
        persona.setNombresORazonSocial(nombre);
        persona.setTelefono(telefono);
        persona.setCorreo(correo);
        persona.setEstado(estado == null || estado.isBlank()
                ? EstadoActivoInactivo.ACTIVO
                : enumDesde(EstadoActivoInactivo.class, estado, "estado de la persona"));
        persona.setConsentimientoUsoDato(consentimiento);
        return persona;
    }

    private static <E extends Enum<E> & com.controllocal.model.CodigoEnum> E enumOpcional(
            Class<E> tipo, String valor, String campo) {
        return valor == null || valor.isBlank() ? null : enumDesde(tipo, valor, campo);
    }

    private static <E extends Enum<E> & com.controllocal.model.CodigoEnum> E enumDesde(
            Class<E> tipo, String valor, String campo) {
        try {
            return com.controllocal.model.CodigoEnum.fromCodigo(tipo, valor);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Valor invalido para " + campo + ": " + valor);
        }
    }

    private static String codigo(com.controllocal.model.CodigoEnum valor) {
        return valor != null ? valor.getCodigo() : null;
    }
}
