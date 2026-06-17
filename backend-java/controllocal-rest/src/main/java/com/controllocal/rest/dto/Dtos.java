package com.controllocal.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.Alerta;
import com.controllocal.model.comercial.EvaluacionSolicitud;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.comercial.enums.ResultadoEvaluacionSolicitud;
import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.comercial.enums.TipoEvaluacionSolicitud;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.inmueble.enums.EstadoLocalComercial;
import com.controllocal.model.inmueble.enums.TipoInmueble;
import com.controllocal.model.inmueble.enums.UsoInmueble;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.persona.enums.EstadoActivoInactivo;
import com.controllocal.model.persona.enums.TipoDocumentoIdentidad;
import com.controllocal.model.persona.enums.TipoPersona;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.rest.http.ApiException;

public final class Dtos {

    private Dtos() {
    }

    // ---------- Autenticacion ----------

    public record LoginRequest(String usuario, String contrasena) {
    }

    public record LoginResponse(String token, long expiraEnSegundos, String rol, long idUsuario, long idDominio,
                                String nombre, String usuario, LocalDateTime expiraEn) {
    }

    // ---------- Agentes inmobiliarios ----------

    public record AgenteResponse(Long id, String codigoAgente, String nombre, String tipoPersona, String tipoDocumento,
                                 String numeroDocumento, String telefono, String correo, String usuario, String zona,
                                 LocalDate fechaIngreso, String estadoAdministrativo, String estadoOperativo) {

        public static AgenteResponse desde(AgenteInmobiliario a) {
            Persona persona = a.getPersona();
            return new AgenteResponse(a.getIdAgente(), a.getCodigoAgente(), persona != null ? persona.getNombresORazonSocial() : null, persona != null ? codigo(persona.getTipoPersona()) : null, persona != null ? codigo(persona.getTipoDocumento()) : null, persona != null ? persona.getNumeroDocumento() : null, persona != null ? persona.getTelefono() : null, persona != null ? persona.getCorreo() : null, a.getNombreUsuario(), a.getZonaAsignada(), a.getFechaIngreso(), codigo(a.getEstadoAdministrativo()), codigo(a.getEstadoOperativo()));
        }
    }

    // ---------- Propietarios ----------

    public record PropietarioRequest(String tipoPersona, String tipoDocumento, String numeroDocumento, String nombre,
                                     String telefono, String correo, Boolean consentimientoUsoDato, String estado) {

        public Propietario aEntidad() {
            Propietario propietario = new Propietario();
            propietario.setPersona(personaDesde(tipoPersona, tipoDocumento, numeroDocumento, nombre, telefono, correo, consentimientoUsoDato, estado));
            return propietario;
        }
    }

    public record PropietarioResponse(Long id, String tipoPersona, String tipoDocumento, String numeroDocumento,
                                      String nombre, String telefono, String correo, String estado,
                                      Boolean consentimientoUsoDato, LocalDateTime fechaCreacion) {

        public static PropietarioResponse desde(Propietario p) {
            return new PropietarioResponse(p.getIdPropietario(), codigo(p.getTipoPersona()), codigo(p.getTipoDocumento()), p.getNumeroDocumento(), p.getNombresORazonSocial(), p.getTelefono(), p.getCorreo(), codigo(p.getEstado()), p.getPersona() != null ? p.getPersona().getConsentimientoUsoDato() : null, p.getFechaCreacion());
        }
    }

    // ---------- Clientes interesados ----------

    public record ClienteRequest(String tipoPersona, String tipoDocumento, String numeroDocumento, String nombre,
                                 String telefono, String correo, String rubroComercial, Boolean consentimientoContacto,
                                 Boolean consentimientoUsoDato, String estado) {

        public ClienteInteresado aEntidad() {
            ClienteInteresado cliente = new ClienteInteresado();
            cliente.setPersona(personaDesde(tipoPersona, tipoDocumento, numeroDocumento, nombre, telefono, correo, consentimientoUsoDato, estado));
            cliente.setRubroComercial(rubroComercial);
            cliente.setConsentimientoContacto(consentimientoContacto);
            cliente.setConsentimientoUsoDato(consentimientoUsoDato);
            return cliente;
        }
    }

    public record ClienteResponse(Long id, String tipoPersona, String tipoDocumento, String numeroDocumento,
                                  String nombre, String telefono, String correo, String rubroComercial, String estado,
                                  Boolean consentimientoContacto, Boolean consentimientoUsoDato,
                                  LocalDateTime fechaCreacion) {

        public static ClienteResponse desde(ClienteInteresado c) {
            Persona persona = c.getPersona();
            return new ClienteResponse(c.getIdCliente(), persona != null ? codigo(persona.getTipoPersona()) : null, persona != null ? codigo(persona.getTipoDocumento()) : null, persona != null ? persona.getNumeroDocumento() : null, persona != null ? persona.getNombresORazonSocial() : null, persona != null ? persona.getTelefono() : null, persona != null ? persona.getCorreo() : null, c.getRubroComercial(), persona != null ? codigo(persona.getEstado()) : null, c.getConsentimientoContacto(), c.getConsentimientoUsoDato(), persona != null ? persona.getFechaCreacion() : null);
        }
    }

    // ---------- Locales comerciales ----------

    public record LocalRequest(String codigoLocal, String direccion, String distrito, BigDecimal metraje,
                               BigDecimal precioReferencial, String rubroPermitido, String descripcion,
                               Long idPropietario, String estado, String tipoInmueble, String uso, Integer ambientes,
                               Integer antiguedadAnios, String zonaUrbanizacion, BigDecimal geoLat, BigDecimal geoLong,
                               String estadoPublicacion, BigDecimal frente, String zonificacion,
                               Boolean aptoLicenciaFuncionamiento, BigDecimal cargaElectricaKw,
                               Integer numeroEstacionamientos, BigDecimal cuotaMantenimiento) {

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
            local.setEstado(estado == null || estado.isBlank() ? EstadoLocalComercial.DISPONIBLE : enumDesde(EstadoLocalComercial.class, estado, "estado del local"));
            TipoInmueble tipo = enumOpcional(TipoInmueble.class, tipoInmueble, "tipo de inmueble");
            if (tipo == null) {
                tipo = TipoInmueble.LOCAL;
            }
            if (tipo != TipoInmueble.LOCAL && tipo != TipoInmueble.OFICINA) {
                throw ApiException.badRequest("ControlLocal solo admite local u oficina como tipo de inmueble comercial.");
            }
            local.setTipoInmueble(tipo);
            if (uso != null && !uso.isBlank() && !"C".equals(uso)) {
                throw ApiException.badRequest("ControlLocal solo admite inmuebles de uso comercial.");
            }
            local.setUso(UsoInmueble.COMERCIAL);
            local.setAmbientes(ambientes);
            local.setAntiguedadAnios(antiguedadAnios);
            local.setZonaUrbanizacion(zonaUrbanizacion);
            local.setGeoLat(geoLat);
            local.setGeoLong(geoLong);
            local.setFrente(frente);
            local.setZonificacion(zonificacion);
            local.setAptoLicenciaFuncionamiento(aptoLicenciaFuncionamiento);
            local.setCargaElectricaKw(cargaElectricaKw);
            local.setNumeroEstacionamientos(numeroEstacionamientos);
            local.setCuotaMantenimiento(cuotaMantenimiento);
            return local;
        }
    }

    public record LocalResponse(Long id, String codigoLocal, String direccion, String distrito, BigDecimal metraje,
                                BigDecimal precioReferencial, String rubroPermitido, String descripcion, String estado,
                                Long idPropietario, String propietarioNombre, String tipoInmueble, String uso,
                                Integer ambientes, Integer antiguedadAnios, String zonaUrbanizacion, BigDecimal geoLat,
                                BigDecimal geoLong, String estadoPublicacion, BigDecimal frente, String zonificacion,
                                Boolean aptoLicenciaFuncionamiento, BigDecimal cargaElectricaKw,
                                Integer numeroEstacionamientos, BigDecimal cuotaMantenimiento,
                                LocalDateTime fechaRegistro) {

        public static LocalResponse desde(LocalComercial l) {
            return desde(l, null);
        }

        public static LocalResponse desde(LocalComercial l, String estadoPublicacion) {
            return new LocalResponse(l.getIdLocal(), l.getCodigoLocal(), l.getDireccion(), l.getDistrito(), l.getMetraje(), l.getPrecioReferencial(), l.getRubroPermitido(), l.getDescripcion(), codigo(l.getEstado()), l.getIdPropietario(), l.getPropietario() != null ? l.getPropietario().getNombresORazonSocial() : null, codigo(l.getTipoInmueble()), codigo(l.getUso()), l.getAmbientes(), l.getAntiguedadAnios(), l.getZonaUrbanizacion(), l.getGeoLat(), l.getGeoLong(), estadoPublicacion, l.getFrente(), l.getZonificacion(), l.getAptoLicenciaFuncionamiento(), l.getCargaElectricaKw(), l.getNumeroEstacionamientos(), l.getCuotaMantenimiento(), l.getFechaRegistro());
        }
    }

    // ---------- Prospecciones ----------

    public record ProspeccionRequest(Long idLocal, String observaciones) {
    }

    public record RecontactoRequest(LocalDate fechaRecontacto) {
    }

    public record RechazoProspeccionRequest(String motivo) {
    }

    public record CaptarProspeccionRequest(BigDecimal comisionPactada) {
    }

    public record MarcarProspeccionCaptadaRequest(Long idCaptacion, String codigoCaptacion) {
    }

    public record ProspeccionResponse(Long id, String codigoProspeccion, Long localId, String localCodigo,
                                      String direccion, String distrito, BigDecimal areaM2, String rubro,
                                      BigDecimal precioReferencial, String propietarioNombre, Long idAgente,
                                      String agenteNombre, String estado, String resultadoPropuesta,
                                      LocalDate fechaContacto, LocalDate fechaReunion, LocalDate fechaPropuesta,
                                      LocalDate fechaRecontacto, String observaciones, Long idCaptacion,
                                      String captacionCodigo) {

        public static ProspeccionResponse desde(Prospeccion p) {
            LocalComercial local = p.getLocalComercial();
            AgenteInmobiliario agente = p.getAgenteResponsable();
            Captacion captacion = p.getCaptacion();
            return new ProspeccionResponse(p.getIdProspeccion(), p.getCodigoProspeccion(), local != null ? local.getIdLocal() : null, local != null ? local.getCodigoLocal() : null, local != null ? local.getDireccion() : null, local != null ? local.getDistrito() : null, local != null ? local.getMetraje() : null, local != null ? local.getRubroPermitido() : null, local != null ? local.getPrecioReferencial() : null, local != null && local.getPropietario() != null ? local.getPropietario().getNombresORazonSocial() : null, agente != null ? agente.getIdAgente() : null, agente != null && agente.getPersona() != null ? agente.getPersona().getNombresORazonSocial() : null, codigo(p.getEstado()), codigo(p.getResultadoPropuesta()), p.getFechaContacto(), p.getFechaReunion(), p.getFechaPropuesta(), p.getFechaRecontacto(), p.getObservaciones(), captacion != null ? captacion.getIdCaptacion() : null, captacion != null ? captacion.getCodigoCaptacion() : null);
        }
    }

    // ---------- Captaciones ----------

    public record CaptacionRequest(String codigoCaptacion, LocalDate fechaCaptacion, LocalDate fechaInicioVigencia,
                                   LocalDate fechaFinVigencia, BigDecimal comisionPactada, String observaciones,
                                   Long idLocal, Long idAgente, String motivoOperacion, Integer urgencia,
                                   Boolean exclusividad) {
    }

    public record CaptacionResponse(Long id, String codigoCaptacion, LocalDate fechaCaptacion,
                                    LocalDate fechaInicioVigencia, LocalDate fechaFinVigencia,
                                    BigDecimal comisionPactada, String observaciones, String estado,
                                    String motivoOperacion, Integer urgencia, Boolean exclusividad,
                                    String observacionRevision, LocalDateTime fechaRevision, Long idLocal,
                                    String direccionLocal, String distritoLocal, BigDecimal areaM2, String rubro,
                                    String propietarioNombre, Long idAgente, String agenteNombre,
                                    Long idBrokerRevisor) {

        public static CaptacionResponse desde(Captacion c) {
            LocalComercial local = c.getLocalComercial();
            var agente = c.getAgenteResponsable();
            return new CaptacionResponse(c.getIdCaptacion(), c.getCodigoCaptacion(), c.getFechaCaptacion(), c.getFechaInicioVigencia(), c.getFechaFinVigencia(), c.getComisionPactada(), c.getObservaciones(), codigo(c.getEstado()), codigo(c.getMotivoOperacion()), c.getUrgencia(), c.getExclusividad(), c.getObservacionRevision(), c.getFechaRevision(), local != null ? local.getIdLocal() : null, local != null ? local.getDireccion() : null, local != null ? local.getDistrito() : null, local != null ? local.getMetraje() : null, local != null ? local.getRubroPermitido() : null, local != null && local.getPropietario() != null ? local.getPropietario().getNombresORazonSocial() : null, agente != null ? agente.getIdAgente() : null, agente != null ? agente.getPersona() != null ? agente.getPersona().getNombresORazonSocial() : null : null, c.getBrokerRevisor() != null ? c.getBrokerRevisor().getIdBroker() : null);
        }
    }

    public record DecisionRequest(String accion, String observacion) {
    }

    public record ReasignacionRequest(Long idAgenteNuevo, String motivo) {
    }

    public record CierreRequest(String motivo) {
    }

    // ---------- Oportunidades comerciales ----------

    public record OportunidadResponse(Long id, String codigoOportunidad, Long idCliente, String clienteNombre,
                                      Long idCaptacion, String codigoCaptacion, String direccionLocal,
                                      String distritoLocal, Long idAgente, String agenteNombre, String estado,
                                      LocalDateTime fechaRegistro, String motivoCierre, String observaciones,
                                      LocalDateTime fechaCierre) {

        public static OportunidadResponse desde(OportunidadComercial o) {
            var cliente = o.getClienteInteresado();
            var captacion = o.getCaptacion();
            var local = captacion != null ? captacion.getLocalComercial() : null;
            var agente = o.getAgenteResponsable();
            return new OportunidadResponse(o.getIdOportunidad(), o.getCodigoOportunidad(), cliente != null ? cliente.getIdCliente() : null, cliente != null && cliente.getPersona() != null ? cliente.getPersona().getNombresORazonSocial() : null, captacion != null ? captacion.getIdCaptacion() : null, captacion != null ? captacion.getCodigoCaptacion() : null, local != null ? local.getDireccion() : null, local != null ? local.getDistrito() : null, agente != null ? agente.getIdAgente() : null, agente != null && agente.getPersona() != null ? agente.getPersona().getNombresORazonSocial() : null, codigo(o.getEstado()), o.getFechaRegistro(), o.getMotivoCierre(), o.getObservaciones(), o.getFechaCierre());
        }
    }

    public record NoContinuidadRequest(String razon, String observaciones) {
    }

    public record OportunidadRequest(String codigoOportunidad, Long idCliente, Long idCaptacion, String observaciones) {

        public OportunidadComercial aEntidad(long idAgente) {
            OportunidadComercial oportunidad = new OportunidadComercial();
            oportunidad.setCodigoOportunidad(codigoOportunidad != null && !codigoOportunidad.isBlank() ? codigoOportunidad.trim() : "OP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss")));
            oportunidad.setObservaciones(observaciones);

            ClienteInteresado cliente = new ClienteInteresado();
            cliente.setIdCliente(idCliente);
            oportunidad.setClienteInteresado(cliente);

            Captacion captacion = new Captacion();
            captacion.setIdCaptacion(idCaptacion);
            oportunidad.setCaptacion(captacion);

            AgenteInmobiliario agente = new AgenteInmobiliario();
            agente.setIdAgente(idAgente);
            oportunidad.setAgenteResponsable(agente);
            return oportunidad;
        }
    }

    // ---------- Solicitudes de alquiler ----------

    public record SolicitudRequest(String codigoSolicitud, LocalDate fechaRegistro, BigDecimal montoPropuesto,
                                   String plazoTentativo, String observaciones, LocalDate fechaVigenciaOferta,
                                   Long idOportunidad) {

        public SolicitudAlquiler aEntidad(long idAgente) {
            SolicitudAlquiler solicitud = new SolicitudAlquiler();
            solicitud.setCodigoSolicitud(codigoSolicitud);
            solicitud.setFechaRegistro(fechaRegistro != null ? fechaRegistro : LocalDate.now());
            solicitud.setMontoPropuesto(montoPropuesto);
            solicitud.setPlazoTentativo(plazoTentativo);
            solicitud.setObservaciones(observaciones);
            solicitud.setFechaVigenciaOferta(fechaVigenciaOferta);

            OportunidadComercial oportunidad = new OportunidadComercial();
            oportunidad.setIdOportunidad(idOportunidad);
            solicitud.setOportunidadComercial(oportunidad);

            AgenteInmobiliario agente = new AgenteInmobiliario();
            agente.setIdAgente(idAgente);
            solicitud.setAgenteResponsable(agente);
            return solicitud;
        }
    }

    public record SolicitudResponse(Long id, String codigoSolicitud, LocalDate fechaRegistro, BigDecimal montoPropuesto,
                                    String plazoTentativo, String observaciones, String estado,
                                    LocalDateTime fechaActualizacionEstado, LocalDate fechaVigenciaOferta,
                                    Long idOportunidad, String codigoOportunidad, Long idCliente, String clienteNombre,
                                    Long idCaptacion, String codigoCaptacion, String direccionLocal,
                                    String distritoLocal, Long idAgente, String agenteNombre) {

        public static SolicitudResponse desde(SolicitudAlquiler s) {
            var oportunidad = s.getOportunidadComercial();
            var cliente = s.getClienteInteresado();
            var captacion = s.getCaptacion();
            var local = captacion != null ? captacion.getLocalComercial() : null;
            var agente = s.getAgenteResponsable();
            return new SolicitudResponse(s.getIdSolicitud(), s.getCodigoSolicitud(), s.getFechaRegistro(), s.getMontoPropuesto(), s.getPlazoTentativo(), s.getObservaciones(), codigo(s.getEstado()), s.getFechaActualizacionEstado(), s.getFechaVigenciaOferta(), oportunidad != null ? oportunidad.getIdOportunidad() : null, oportunidad != null ? oportunidad.getCodigoOportunidad() : null, cliente != null ? cliente.getIdCliente() : null, cliente != null && cliente.getPersona() != null ? cliente.getPersona().getNombresORazonSocial() : null, captacion != null ? captacion.getIdCaptacion() : null, captacion != null ? captacion.getCodigoCaptacion() : null, local != null ? local.getDireccion() : null, local != null ? local.getDistrito() : null, agente != null ? agente.getIdAgente() : null, agente != null && agente.getPersona() != null ? agente.getPersona().getNombresORazonSocial() : null);
        }
    }

    // ---------- Evaluaciones de solicitud ----------

    public record EvaluacionRequest(String tipoEvaluacion, String resultado, String observaciones, Long idSolicitud) {

        public EvaluacionSolicitud aEntidad(long idBroker) {
            EvaluacionSolicitud evaluacion = new EvaluacionSolicitud();
            evaluacion.setTipoEvaluacion(enumDesde(TipoEvaluacionSolicitud.class, tipoEvaluacion, "tipo de evaluacion"));
            evaluacion.setResultado(enumDesde(ResultadoEvaluacionSolicitud.class, resultado, "resultado de evaluacion"));
            evaluacion.setObservaciones(observaciones);

            Broker broker = new Broker();
            broker.setIdBroker(idBroker);
            evaluacion.setResponsableEvaluacion(broker);

            SolicitudAlquiler solicitud = new SolicitudAlquiler();
            solicitud.setIdSolicitud(idSolicitud);
            evaluacion.setSolicitudAlquiler(solicitud);
            return evaluacion;
        }
    }

    public record EvaluacionResponse(Long id, LocalDateTime fechaEvaluacion, String resultado, String observaciones,
                                     Long idBroker, String brokerNombre, String tipoEvaluacion, Long idSolicitud) {

        public static EvaluacionResponse desde(EvaluacionSolicitud e) {
            var broker = e.getResponsableEvaluacion();
            var solicitud = e.getSolicitudAlquiler();
            return new EvaluacionResponse(e.getIdEvaluacion(), e.getFechaEvaluacion(), codigo(e.getResultado()), e.getObservaciones(), broker != null ? broker.getIdBroker() : null, broker != null && broker.getPersona() != null ? broker.getPersona().getNombresORazonSocial() : null, codigo(e.getTipoEvaluacion()), solicitud != null ? solicitud.getIdSolicitud() : null);
        }
    }

    // ---------- Alertas ----------

    public record AlertaResponse(Long id, String tipo, String severidad, String entidadTipo, Long entidadId,
                                 Long idAgente, String agenteNombre, String mensaje, String estado,
                                 LocalDateTime fechaGeneracion, LocalDateTime fechaResolucion, String ruta) {

        public static AlertaResponse desde(Alerta a) {
            var agente = a.getAgente();
            return new AlertaResponse(a.getIdAlerta(), codigo(a.getTipo()), codigo(a.getSeveridad()), codigo(a.getEntidadTipo()), a.getEntidadId(), agente != null ? agente.getIdAgente() : null, agente != null && agente.getPersona() != null ? agente.getPersona().getNombresORazonSocial() : null, a.getMensaje(), codigo(a.getEstado()), a.getFechaGeneracion(), a.getFechaResolucion(), Dtos.ruta(a));
        }
    }

    public record AtenderAlertaResponse(boolean atendida) {
    }

    // ---------- Visitas ----------

    public record VisitaRequest(Long idOportunidad, LocalDate fechaVisita, LocalTime horaVisita, String observaciones) {
    }

    public record ReprogramarVisitaRequest(LocalDate fechaVisita, LocalTime horaVisita) {
    }

    public record CancelarVisitaRequest(String motivo) {
    }

    public record NoRealizadaVisitaRequest(String motivo) {
    }

    public record ResultadoVisitaRequest(String resultado, String observaciones, String razonNoContinuidad,
                                         Integer nivelInteres, String objecionPrincipal, String opinionPrecio,
                                         String proximaAccion) {
    }

    public record VisitaResponse(Long id, Long idOportunidad, String codigoOportunidad, LocalDate fechaVisita,
                                 LocalTime horaVisita, String observaciones, String estado, String resultado,
                                 Long idCliente, String clienteNombre, Long idCaptacion, String codigoCaptacion,
                                 String direccionLocal, String distritoLocal, Long idAgente, String agenteNombre,
                                 Integer nivelInteres, String objecionPrincipal, String opinionPrecio,
                                 String proximaAccion) {

        public static VisitaResponse desde(Visita v) {
            var oportunidad = v.getOportunidadComercial();
            var cliente = v.getClienteInteresado();
            var captacion = v.getCaptacion();
            var local = captacion != null ? captacion.getLocalComercial() : null;
            var agente = v.getAgenteResponsable();
            return new VisitaResponse(v.getIdVisita(), oportunidad != null ? oportunidad.getIdOportunidad() : null, oportunidad != null ? oportunidad.getCodigoOportunidad() : null, v.getFechaVisita(), v.getHoraVisita(), v.getObservaciones(), codigo(v.getEstado()), codigo(v.getResultado()), cliente != null ? cliente.getIdCliente() : null, cliente != null && cliente.getPersona() != null ? cliente.getPersona().getNombresORazonSocial() : null, captacion != null ? captacion.getIdCaptacion() : null, captacion != null ? captacion.getCodigoCaptacion() : null, local != null ? local.getDireccion() : null, local != null ? local.getDistrito() : null, agente != null ? agente.getIdAgente() : null, agente != null && agente.getPersona() != null ? agente.getPersona().getNombresORazonSocial() : null, v.getNivelInteres(), codigo(v.getObjecionPrincipal()), codigo(v.getOpinionPrecio()), codigo(v.getProximaAccion()));
        }
    }

    // ---------- Soporte ----------

    private static String ruta(Alerta alerta) {
        if (alerta.getEntidadTipo() == TipoEntidad.SOLICITUD_ALQUILER) {
            return "solicitudes";
        }
        return null;
    }

    private static Persona personaDesde(String tipoPersona, String tipoDocumento, String numeroDocumento, String nombre, String telefono, String correo, Boolean consentimiento, String estado) {
        Persona persona = new Persona();
        persona.setTipoPersona(enumDesde(TipoPersona.class, tipoPersona, "tipo de persona"));
        persona.setTipoDocumento(enumDesde(TipoDocumentoIdentidad.class, tipoDocumento, "tipo de documento"));
        persona.setNumeroDocumento(numeroDocumento);
        persona.setNombresORazonSocial(nombre);
        persona.setTelefono(telefono);
        persona.setCorreo(correo);
        persona.setEstado(estado == null || estado.isBlank() ? EstadoActivoInactivo.ACTIVO : enumDesde(EstadoActivoInactivo.class, estado, "estado de la persona"));
        persona.setConsentimientoUsoDato(consentimiento);
        return persona;
    }

    private static <E extends Enum<E> & com.controllocal.model.CodigoEnum> E enumOpcional(Class<E> tipo, String valor, String campo) {
        return valor == null || valor.isBlank() ? null : enumDesde(tipo, valor, campo);
    }

    private static <E extends Enum<E> & com.controllocal.model.CodigoEnum> E enumDesde(Class<E> tipo, String valor, String campo) {
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
