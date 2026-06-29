package com.controllocal.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.Alerta;
import com.controllocal.model.comercial.ComisionLiquidacion;
import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.DocumentoSolicitud;
import com.controllocal.model.comercial.EvaluacionSolicitud;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.comercial.ReasignacionCaptacion;
import com.controllocal.model.comercial.RequerimientoCliente;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.Publicacion;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.comercial.enums.CanalPublicacion;
import com.controllocal.model.comercial.enums.EstadoRequerimiento;
import com.controllocal.model.comercial.enums.FormaPago;
import com.controllocal.model.comercial.enums.HitoPrecio;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.comercial.enums.ResultadoEvaluacionSolicitud;
import com.controllocal.model.comercial.enums.TipoDocumentoSolicitud;
import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.comercial.enums.TipoEvaluacionSolicitud;
import com.controllocal.model.comercial.enums.TipoInmuebleComercial;
import com.controllocal.model.inmueble.Distrito;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.inmueble.PrecioLocal;
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
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.ReasignacionAgenteBroker;
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
                                 LocalDate fechaIngreso, String estadoAdministrativo, String estadoOperativo,
                                 int captacionesActivas, int operacionesActivas) {

        public static AgenteResponse desde(AgenteInmobiliario a) {
            return desde(a, 0, 0);
        }

        public static AgenteResponse desde(AgenteInmobiliario a, int captacionesActivas, int operacionesActivas) {
            Persona persona = a.getPersona();
            return new AgenteResponse(a.getIdAgente(), a.getCodigoAgente(), persona != null ? persona.getNombresORazonSocial() : null, persona != null ? codigo(persona.getTipoPersona()) : null, persona != null ? codigo(persona.getTipoDocumento()) : null, persona != null ? persona.getNumeroDocumento() : null, persona != null ? persona.getTelefono() : null, persona != null ? persona.getCorreo() : null, a.getNombreUsuario(), a.getZonaAsignada(), a.getFechaIngreso(), codigo(a.getEstadoAdministrativo()), codigo(a.getEstadoOperativo()), captacionesActivas, operacionesActivas);
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
                                      Boolean consentimientoUsoDato, LocalDateTime fechaCreacion,
                                      int cantidadLocales) {

        public static PropietarioResponse desde(Propietario p) {
            return desde(p, 0);
        }

        public static PropietarioResponse desde(Propietario p, int cantidadLocales) {
            return new PropietarioResponse(p.getIdPropietario(), codigo(p.getTipoPersona()), codigo(p.getTipoDocumento()), p.getNumeroDocumento(), p.getNombresORazonSocial(), p.getTelefono(), p.getCorreo(), codigo(p.getEstado()), p.getPersona() != null ? p.getPersona().getConsentimientoUsoDato() : null, p.getFechaCreacion(), Math.max(0, cantidadLocales));
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

    // ---------- Requerimientos de cliente (perfil de busqueda) ----------

    public record RequerimientoRequest(Long idCliente, String rubro, String tipoInmueble,
                                       BigDecimal rentaMin, BigDecimal rentaMax, String moneda,
                                       BigDecimal metrajeMin, BigDecimal metrajeMax, BigDecimal frenteMinimo,
                                       String estado, String observaciones, List<String> distritos) {

        public RequerimientoCliente aEntidad() {
            RequerimientoCliente r = new RequerimientoCliente();
            if (idCliente != null) {
                ClienteInteresado cliente = new ClienteInteresado();
                cliente.setIdCliente(idCliente);
                r.setCliente(cliente);
            }
            r.setRubro(rubro);
            r.setTipoInmueble(enumOpcional(TipoInmuebleComercial.class, tipoInmueble, "tipo de inmueble"));
            r.setRentaMin(rentaMin);
            r.setRentaMax(rentaMax);
            r.setMoneda(moneda == null || moneda.isBlank() ? Moneda.PEN : enumDesde(Moneda.class, moneda, "moneda"));
            r.setMetrajeMin(metrajeMin);
            r.setMetrajeMax(metrajeMax);
            r.setFrenteMinimo(frenteMinimo);
            r.setEstado(estado == null || estado.isBlank()
                    ? EstadoRequerimiento.ACTIVO
                    : enumDesde(EstadoRequerimiento.class, estado, "estado del requerimiento"));
            r.setObservaciones(observaciones);
            if (distritos != null) {
                List<Distrito> ds = new ArrayList<>();
                for (String nombre : distritos) {
                    if (nombre != null && !nombre.isBlank()) {
                        Distrito d = new Distrito();
                        d.setNombre(nombre.trim());
                        ds.add(d);
                    }
                }
                r.setDistritos(ds);
            }
            return r;
        }
    }

    public record RequerimientoResponse(Long id, Long idCliente, String rubro, String tipoInmueble,
                                        BigDecimal rentaMin, BigDecimal rentaMax, String moneda,
                                        BigDecimal metrajeMin, BigDecimal metrajeMax, BigDecimal frenteMinimo,
                                        String estado, String observaciones, List<String> distritos,
                                        LocalDateTime fechaCreacion, LocalDateTime fechaActualizacion) {

        public static RequerimientoResponse desde(RequerimientoCliente r) {
            List<String> ds = r.getDistritos() == null ? List.of()
                    : r.getDistritos().stream().map(Distrito::getNombre).filter(Objects::nonNull).toList();
            return new RequerimientoResponse(
                    r.getIdRequerimiento(),
                    r.getCliente() != null ? r.getCliente().getIdCliente() : null,
                    r.getRubro(),
                    codigo(r.getTipoInmueble()),
                    r.getRentaMin(),
                    r.getRentaMax(),
                    codigo(r.getMoneda()),
                    r.getMetrajeMin(),
                    r.getMetrajeMax(),
                    r.getFrenteMinimo(),
                    codigo(r.getEstado()),
                    r.getObservaciones(),
                    ds,
                    r.getFechaCreacion(),
                    r.getFechaActualizacion());
        }
    }

    public record EstadoRequerimientoRequest(String estado) {
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
                                Long idDistrito, LocalDateTime fechaRegistro) {

        public static LocalResponse desde(LocalComercial l) {
            return desde(l, null);
        }

        public static LocalResponse desde(LocalComercial l, String estadoPublicacion) {
            return new LocalResponse(l.getIdLocal(), l.getCodigoLocal(), l.getDireccion(), l.getDistrito(), l.getMetraje(), l.getPrecioReferencial(), l.getRubroPermitido(), l.getDescripcion(), codigo(l.getEstado()), l.getIdPropietario(), l.getPropietario() != null ? l.getPropietario().getNombresORazonSocial() : null, codigo(l.getTipoInmueble()), codigo(l.getUso()), l.getAmbientes(), l.getAntiguedadAnios(), l.getZonaUrbanizacion(), l.getGeoLat(), l.getGeoLong(), estadoPublicacion, l.getFrente(), l.getZonificacion(), l.getAptoLicenciaFuncionamiento(), l.getCargaElectricaKw(), l.getNumeroEstacionamientos(), l.getCuotaMantenimiento(), l.getIdDistrito(), l.getFechaRegistro());
        }
    }

    // ---------- Precios del local (historico) ----------

    public record PrecioRequest(String hito, String moneda, BigDecimal monto, LocalDate fecha) {

        public PrecioLocal aEntidad(long idLocal) {
            PrecioLocal precio = new PrecioLocal();
            precio.setIdLocal(idLocal);
            precio.setHito(enumDesde(HitoPrecio.class, hito, "hito de precio"));
            precio.setMoneda(moneda == null || moneda.isBlank()
                    ? Moneda.PEN
                    : enumDesde(Moneda.class, moneda, "moneda del precio"));
            precio.setMonto(monto);
            precio.setFecha(fecha != null ? fecha : LocalDate.now());
            return precio;
        }
    }

    public record PrecioResponse(Long id, Long idLocal, String hito, String moneda, BigDecimal monto,
                                 LocalDate fecha, LocalDateTime fechaCreacion) {

        public static PrecioResponse desde(PrecioLocal p) {
            return new PrecioResponse(p.getIdPrecio(), p.getIdLocal(), codigo(p.getHito()), codigo(p.getMoneda()),
                    p.getMonto(), p.getFecha(), p.getFechaCreacion());
        }
    }

    // ---------- Publicaciones del local ----------

    public record PublicacionResponse(Long id, String canal, String tituloAnuncio, BigDecimal rentaPublicada,
                                      String moneda, String estado, LocalDateTime fechaPublicacion,
                                      LocalDateTime fechaBaja, String urlPublicacion, String codigoOrigen) {

        public static PublicacionResponse desde(Publicacion p) {
            return new PublicacionResponse(p.getIdPublicacion(), codigo(p.getCanal()), p.getTituloAnuncio(),
                    p.getRentaPublicada(), codigo(p.getMoneda()), codigo(p.getEstado()), p.getFechaPublicacion(),
                    p.getFechaBaja(), p.getUrlPublicacion(), p.getCodigoOrigen());
        }
    }

    public record PublicacionRequest(String canal, String urlPublicacion, BigDecimal rentaPublicada,
                                     String moneda, String tituloAnuncio, String codigoOrigen, String estado) {

        public Publicacion aEntidad() {
            Publicacion p = new Publicacion();
            p.setCanal(enumOpcional(CanalPublicacion.class, canal, "canal de publicacion"));
            p.setUrlPublicacion(urlPublicacion);
            p.setRentaPublicada(rentaPublicada);
            p.setMoneda(moneda == null || moneda.isBlank() ? null : enumDesde(Moneda.class, moneda, "moneda"));
            p.setTituloAnuncio(tituloAnuncio);
            p.setCodigoOrigen(codigoOrigen);
            p.setEstado(enumOpcional(EstadoPublicacion.class, estado, "estado de publicacion"));
            return p;
        }
    }

    public record EstadoPublicacionRequest(String estado) {
    }

    // ---------- Prospecciones ----------

    public record ProspeccionRequest(Long idLocal, String observaciones) {
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
                                      String captacionCodigo, String disponibilidad) {

        public static ProspeccionResponse desde(Prospeccion p) {
            LocalComercial local = p.getLocalComercial();
            AgenteInmobiliario agente = p.getAgenteResponsable();
            Captacion captacion = p.getCaptacion();
            return new ProspeccionResponse(p.getIdProspeccion(), p.getCodigoProspeccion(), local != null ? local.getIdLocal() : null, local != null ? local.getCodigoLocal() : null, local != null ? local.getDireccion() : null, local != null ? local.getDistrito() : null, local != null ? local.getMetraje() : null, local != null ? local.getRubroPermitido() : null, local != null ? local.getPrecioReferencial() : null, local != null && local.getPropietario() != null ? local.getPropietario().getNombresORazonSocial() : null, agente != null ? agente.getIdAgente() : null, agente != null && agente.getPersona() != null ? agente.getPersona().getNombresORazonSocial() : null, codigo(p.getEstado()), codigo(p.getResultadoPropuesta()), p.getFechaContacto(), p.getFechaReunion(), p.getFechaPropuesta(), p.getFechaRecontacto(), p.getObservaciones(), captacion != null ? captacion.getIdCaptacion() : null, captacion != null ? captacion.getCodigoCaptacion() : null, local != null ? codigo(local.getEstado()) : null);
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
                                      LocalDateTime fechaCierre, LocalDateTime fechaActualizacion,
                                      Long idPublicacionOrigen) {

        public static OportunidadResponse desde(OportunidadComercial o) {
            var cliente = o.getClienteInteresado();
            var captacion = o.getCaptacion();
            var local = captacion != null ? captacion.getLocalComercial() : null;
            var agente = o.getAgenteResponsable();
            Long idPublicacionOrigen = o.getPublicacionOrigen() != null ? o.getPublicacionOrigen().getIdPublicacion() : null;
            return new OportunidadResponse(o.getIdOportunidad(), o.getCodigoOportunidad(), cliente != null ? cliente.getIdCliente() : null, cliente != null && cliente.getPersona() != null ? cliente.getPersona().getNombresORazonSocial() : null, captacion != null ? captacion.getIdCaptacion() : null, captacion != null ? captacion.getCodigoCaptacion() : null, local != null ? local.getDireccion() : null, local != null ? local.getDistrito() : null, agente != null ? agente.getIdAgente() : null, agente != null && agente.getPersona() != null ? agente.getPersona().getNombresORazonSocial() : null, codigo(o.getEstado()), o.getFechaRegistro(), o.getMotivoCierre(), o.getObservaciones(), o.getFechaCierre(), o.getFechaActualizacion(), idPublicacionOrigen);
        }
    }

    public record NoContinuidadRequest(String razon, String observaciones) {
    }

    public record OportunidadRequest(String codigoOportunidad, Long idCliente, Long idCaptacion, String observaciones,
                                     Long idPublicacionOrigen) {

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

            // Atribución a la publicación de origen (opcional): el cliente llegó por ese anuncio.
            if (idPublicacionOrigen != null && idPublicacionOrigen > 0) {
                Publicacion publicacion = new Publicacion();
                publicacion.setIdPublicacion(idPublicacionOrigen);
                oportunidad.setPublicacionOrigen(publicacion);
            }

            AgenteInmobiliario agente = new AgenteInmobiliario();
            agente.setIdAgente(idAgente);
            oportunidad.setAgenteResponsable(agente);
            return oportunidad;
        }
    }

    // ---------- Solicitudes de alquiler ----------

    public record SolicitudRequest(String codigoSolicitud, LocalDate fechaRegistro, BigDecimal montoPropuesto,
                                   String plazoTentativo, String observaciones, LocalDate fechaVigenciaOferta,
                                   Long idOportunidad, Integer plazoMeses, LocalDate fechaInicio, String formaPago,
                                   Integer mesesGarantia, Integer mesesAdelanto) {

        public SolicitudAlquiler aEntidad(long idAgente) {
            SolicitudAlquiler solicitud = new SolicitudAlquiler();
            solicitud.setCodigoSolicitud(codigoSolicitud);
            solicitud.setFechaRegistro(fechaRegistro != null ? fechaRegistro : LocalDate.now());
            solicitud.setMontoPropuesto(montoPropuesto);
            // El plazo del contrato (meses) es la fuente; plazoTentativo se deriva para mostrar.
            solicitud.setPlazoContratoMeses(plazoMeses);
            solicitud.setPlazoTentativo(plazoMeses != null && plazoMeses > 0 ? plazoMeses + " meses" : plazoTentativo);
            solicitud.setObservaciones(observaciones);
            solicitud.setFechaVigenciaOferta(fechaVigenciaOferta);
            solicitud.setFechaInicioContrato(fechaInicio);
            solicitud.setFormaPago(enumOpcional(FormaPago.class, formaPago, "forma de pago"));
            solicitud.setMesesGarantia(mesesGarantia);
            solicitud.setMesesAdelanto(mesesAdelanto);

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
                                    String distritoLocal, Long idAgente, String agenteNombre,
                                    Integer plazoMeses, LocalDate fechaInicio, String formaPago,
                                    Integer mesesGarantia, Integer mesesAdelanto,
                                    int documentosEntregados, int documentosRequeridos) {

        public static SolicitudResponse desde(SolicitudAlquiler s, int documentosEntregados, int documentosRequeridos) {
            var oportunidad = s.getOportunidadComercial();
            var cliente = s.getClienteInteresado();
            var captacion = s.getCaptacion();
            var local = captacion != null ? captacion.getLocalComercial() : null;
            var agente = s.getAgenteResponsable();
            return new SolicitudResponse(s.getIdSolicitud(), s.getCodigoSolicitud(), s.getFechaRegistro(), s.getMontoPropuesto(), s.getPlazoTentativo(), s.getObservaciones(), codigo(s.getEstado()), s.getFechaActualizacionEstado(), s.getFechaVigenciaOferta(), oportunidad != null ? oportunidad.getIdOportunidad() : null, oportunidad != null ? oportunidad.getCodigoOportunidad() : null, cliente != null ? cliente.getIdCliente() : null, cliente != null && cliente.getPersona() != null ? cliente.getPersona().getNombresORazonSocial() : null, captacion != null ? captacion.getIdCaptacion() : null, captacion != null ? captacion.getCodigoCaptacion() : null, local != null ? local.getDireccion() : null, local != null ? local.getDistrito() : null, agente != null ? agente.getIdAgente() : null, agente != null && agente.getPersona() != null ? agente.getPersona().getNombresORazonSocial() : null, s.getPlazoContratoMeses(), s.getFechaInicioContrato(), codigo(s.getFormaPago()), s.getMesesGarantia(), s.getMesesAdelanto(), documentosEntregados, documentosRequeridos);
        }
    }

    // ---------- Contrato de alquiler (cierre del trato) ----------

    // Las condiciones (renta, plazo, forma de pago, garantia, adelanto) se leen de la solicitud y la
    // comision se deriva en el backend. El agente solo captura la formalizacion del cierre: fecha de
    // cierre, estado del contrato (FIRMADO o VIGENTE) e incidencias. Los tres son opcionales: por
    // defecto cierre = hoy, estado = VIGENTE, sin incidencias (compatible con clientes previos).
    public record ContratoRequest(Long idSolicitud, LocalDate fechaCierre,
                                  String estadoContrato, String incidencias) {
    }

    // El broker supervisor define el monto real del agente; la empresa se calcula sola.
    public record ComisionAsignarRequest(BigDecimal montoAgente) {
    }

    // El broker administrador registra el desenlace del cobro: COBRADA (con fecha y forma
    // de pago) o ANULADA.
    public record ComisionCobroRequest(String estado, LocalDate fechaCobro, String formaPago) {
    }

    public record ContratoResponse(Long id, Long idSolicitud, String codigoSolicitud,
                                   Long idOportunidad, String codigoOportunidad, String clienteNombre,
                                   String direccionLocal, String distritoLocal, String codigoCaptacion,
                                   String agenteNombre, BigDecimal rentaMensual, String moneda,
                                   Integer plazoContratoMeses, BigDecimal comisionGenerada,
                                   LocalDate fechaInicioContrato, LocalDate fechaFinContrato,
                                   LocalDate fechaCierre, String estadoContrato, String comisionEstado,
                                   String incidencias, Long idComision, Long agenteId,
                                   Long propietarioId, String propietarioNombre,
                                   BigDecimal montoAgente, BigDecimal montoEmpresa,
                                   String formaPago, LocalDate fechaCobro) {

        public static ContratoResponse desde(ContratoAlquiler c, SolicitudAlquiler s) {
            return desde(c, s, null, false);
        }

        public static ContratoResponse desde(ContratoAlquiler c, SolicitudAlquiler s, ComisionLiquidacion comision) {
            return desde(c, s, comision, false);
        }

        // El contrato es minimo: renta/plazo/fecha de inicio se leen de la solicitud (no se duplican);
        // la comision (bruto + estado + reparto) viene de comision_liquidacion. La fecha fin se deriva.
        // verNeto = true para broker/admin (ven monto agente y empresa); false para el agente, que solo
        // ve comision bruta, estado, fecha de cobro y forma de pago (privacidad de la liquidacion).
        public static ContratoResponse desde(ContratoAlquiler c, SolicitudAlquiler s,
                ComisionLiquidacion comision, boolean verNeto) {
            var oportunidad = s != null ? s.getOportunidadComercial() : c.getOportunidad();
            var cliente = s != null ? s.getClienteInteresado() : null;
            var captacion = s != null ? s.getCaptacion() : null;
            var local = captacion != null ? captacion.getLocalComercial() : null;
            var propietario = local != null ? local.getPropietario() : null;
            var agente = s != null ? s.getAgenteResponsable() : null;
            Integer plazo = plazoMeses(s);
            LocalDate inicio = s != null ? s.getFechaInicioContrato() : null;
            LocalDate fin = (inicio != null && plazo != null) ? inicio.plusMonths(plazo) : null;
            return new ContratoResponse(
                    c.getIdContratoAlquiler(),
                    s != null ? s.getIdSolicitud() : null,
                    s != null ? s.getCodigoSolicitud() : null,
                    oportunidad != null ? oportunidad.getIdOportunidad() : null,
                    oportunidad != null ? oportunidad.getCodigoOportunidad() : null,
                    cliente != null && cliente.getPersona() != null ? cliente.getPersona().getNombresORazonSocial() : null,
                    local != null ? local.getDireccion() : null,
                    local != null ? local.getDistrito() : null,
                    captacion != null ? captacion.getCodigoCaptacion() : null,
                    agente != null && agente.getPersona() != null ? agente.getPersona().getNombresORazonSocial() : null,
                    s != null ? s.getMontoPropuesto() : null,
                    "USD",
                    plazo,
                    comision != null ? comision.getMonto() : null,
                    inicio,
                    fin,
                    c.getFechaCierre(),
                    codigo(c.getEstadoContrato()),
                    comision != null ? codigo(comision.getEstado()) : null,
                    c.getIncidencias(),
                    comision != null ? comision.getIdComisionLiquidacion() : null,
                    agente != null ? agente.getIdAgente() : null,
                    local != null ? local.getIdPropietario() : null,
                    propietario != null ? propietario.getNombresORazonSocial() : null,
                    verNeto && comision != null ? comision.getMontoAgente() : null,
                    verNeto && comision != null ? comision.getMontoEmpresa() : null,
                    comision != null ? codigo(comision.getFormaPago()) : null,
                    comision != null ? comision.getFechaCobro() : null);
        }

        // Plazo en meses: de la solicitud; si falta, se parsea del plazo tentativo ("24 meses").
        private static Integer plazoMeses(SolicitudAlquiler s) {
            if (s == null) {
                return null;
            }
            if (s.getPlazoContratoMeses() != null) {
                return s.getPlazoContratoMeses();
            }
            String texto = s.getPlazoTentativo();
            if (texto == null) {
                return null;
            }
            String digitos = texto.replaceAll("\\D", "");
            return digitos.isEmpty() ? null : Integer.valueOf(digitos);
        }
    }

    // ---------- Documentos de solicitud ----------

    public record DocumentoSolicitudRequest(String tipoDocumento, String nombreArchivo,
                                            String rutaArchivo, String observaciones,
                                            String contenidoBase64) {
    }

    // Un trozo de una subida por partes (el archivo viaja en varios POST JSON pequenos).
    public record DocumentoChunkRequest(String uploadId, Integer indice, Integer total, Boolean ultimo,
                                        String tipoDocumento, String nombreArchivo, String dataBase64) {
    }

    // Subida por handoff local: solo el nombre del archivo temporal que el frontend dejo en
    // la carpeta compartida; el backend lo lee de ahi (POST chico, sin cuerpo grande).
    public record DocumentoLocalRequest(String tipoDocumento, String nombreArchivo, String archivoTemporal) {
    }

    // Revision de un documento por el broker: resultado "C" (conforme/validar) u "O"
    // (observado) con la observacion especifica para el agente.
    public record RevisionDocumentoRequest(String resultado, String observaciones) {
    }

    public record DocumentoSolicitudResponse(Long id, Long idSolicitud, String tipoDocumento, String tipoNombre,
                                             String nombreArchivo, String rutaArchivo, LocalDateTime fechaEntrega,
                                             String estado, String resultadoRevision, String observaciones) {

        // El tipo del catalogo se traduce a su codigo de negocio (I, R, V, ...) por
        // id de catalogo; estado y revision viajan como codigo, igual que el resto del API.
        public static DocumentoSolicitudResponse desde(DocumentoSolicitud d) {
            var tipo = d.getTipoDocumentoRequerido();
            TipoDocumentoSolicitud tipoEnum = tipo != null
                    ? TipoDocumentoSolicitud.porIdCatalogo(tipo.getIdTipoDocumentoRequerido())
                    : null;
            return new DocumentoSolicitudResponse(
                    d.getIdDocumento(),
                    d.getSolicitudAlquiler() != null ? d.getSolicitudAlquiler().getIdSolicitud() : null,
                    tipoEnum != null ? tipoEnum.getCodigo() : null,
                    tipo != null && tipo.getTipoDocumento() != null
                            ? tipo.getTipoDocumento()
                            : (tipoEnum != null ? tipoEnum.getDescripcion() : null),
                    d.getNombreArchivo(),
                    d.getRutaArchivo(),
                    d.getFechaEntrega(),
                    codigo(d.getEstado()),
                    codigo(d.getResultadoRevision()),
                    d.getObservaciones());
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

    // ---------- Interacciones comerciales ----------

    public record InteraccionRequest(String contexto, Long idOportunidad, Long idProspeccion,
                                     Long idCaptacion, Long idCliente,
                                     String canalContacto, String resultado, String observaciones,
                                     String transcripcionNota) {
    }

    public record InteraccionResponse(Long id, String contexto, Long idOportunidad, Long idProspeccion,
                                      Long idCaptacion, Long idCliente, Long idPropietario,
                                      String codigoProspeccion, LocalDateTime fechaHora, String canalContacto,
                                      String resultado, String observaciones, String transcripcionNota,
                                      String clienteNombre, String propietarioNombre, String personaTipo,
                                      String personaNombre, String codigoCaptacion, String agenteNombre) {

        // Los nombres se toman de la oportunidad (ya hidratada por OportunidadDAO);
        // la interaccion del DAO solo trae ids en sus relaciones.
        public static InteraccionResponse desde(InteraccionComercial i, OportunidadComercial oportunidad) {
            var cliente = oportunidad != null ? oportunidad.getClienteInteresado() : null;
            var captacion = oportunidad != null ? oportunidad.getCaptacion() : null;
            var agente = oportunidad != null ? oportunidad.getAgenteResponsable() : null;
            var agenteDirecto = i.getAgenteResponsable();
            var prospeccion = i.getProspeccion();
            var clienteDirecto = i.getClienteInteresado();
            var captacionDirecta = i.getCaptacion();
            String contexto = i.getContexto() == null || i.getContexto().isBlank() ? "OPORTUNIDAD" : i.getContexto();
            var localPropietario = switch (contexto) {
                case "PROSPECCION" -> prospeccion != null ? prospeccion.getLocalComercial() : null;
                case "CAPTACION" -> captacionDirecta != null ? captacionDirecta.getLocalComercial() : null;
                default -> captacion != null ? captacion.getLocalComercial() : null;
            };
            Long idPropietario = localPropietario != null ? localPropietario.getIdPropietario() : null;
            String propietarioNombre = localPropietario != null && localPropietario.getPropietario() != null
                    ? localPropietario.getPropietario().getNombresORazonSocial()
                    : null;
            String clienteNombre = clienteDirecto != null && clienteDirecto.getPersona() != null
                    ? clienteDirecto.getPersona().getNombresORazonSocial()
                    : cliente != null && cliente.getPersona() != null ? cliente.getPersona().getNombresORazonSocial() : null;
            boolean esPropietario = "PROSPECCION".equals(contexto) || "CAPTACION".equals(contexto);
            String personaTipo = esPropietario ? "Propietario" : "Cliente";
            String personaNombre = esPropietario ? propietarioNombre : clienteNombre;
            Long idClienteRespuesta = clienteDirecto != null ? clienteDirecto.getIdCliente()
                    : cliente != null ? cliente.getIdCliente() : null;
            Long idCaptacionRespuesta = captacionDirecta != null ? captacionDirecta.getIdCaptacion()
                    : captacion != null ? captacion.getIdCaptacion() : null;
            return new InteraccionResponse(
                    i.getIdInteraccion(),
                    contexto,
                    i.getOportunidadComercial() != null ? i.getOportunidadComercial().getIdOportunidad() : null,
                    prospeccion != null ? prospeccion.getIdProspeccion() : null,
                    idCaptacionRespuesta,
                    idClienteRespuesta,
                    idPropietario,
                    prospeccion != null ? prospeccion.getCodigoProspeccion() : null,
                    i.getFechaHora(),
                    codigo(i.getCanalContacto()),
                    codigo(i.getResultado()),
                    i.getObservaciones(),
                    i.getTranscripcionNota(),
                    clienteNombre,
                    propietarioNombre,
                    personaTipo,
                    personaNombre,
                    captacionDirecta != null ? captacionDirecta.getCodigoCaptacion()
                            : captacion != null ? captacion.getCodigoCaptacion() : null,
                    agenteDirecto != null && agenteDirecto.getPersona() != null
                            ? agenteDirecto.getPersona().getNombresORazonSocial()
                            : agente != null && agente.getPersona() != null ? agente.getPersona().getNombresORazonSocial() : null);
        }
    }

    // ---------- Reasignaciones de captacion ----------

    public record ReasignacionCaptacionResponse(Long idReasignacion, Long idCaptacion, String codigoCaptacion,
                                                String direccionLocal, Long idAgenteAnterior, String agenteAnteriorNombre,
                                                Long idAgenteNuevo, String agenteNuevoNombre, Long idBroker,
                                                String brokerNombre, LocalDateTime fechaCambio, String motivo) {

        // Los nombres/codigos se reciben ya resueltos (la fila del DAO solo trae ids).
        public static ReasignacionCaptacionResponse desde(ReasignacionCaptacion r, String codigoCaptacion,
                String direccionLocal, String agenteAnteriorNombre, String agenteNuevoNombre, String brokerNombre) {
            return new ReasignacionCaptacionResponse(
                    r.getIdReasignacion(),
                    r.getCaptacion() != null ? r.getCaptacion().getIdCaptacion() : null,
                    codigoCaptacion,
                    direccionLocal,
                    r.getAgenteAnterior() != null ? r.getAgenteAnterior().getIdAgente() : null,
                    agenteAnteriorNombre,
                    r.getAgenteNuevo() != null ? r.getAgenteNuevo().getIdAgente() : null,
                    agenteNuevoNombre,
                    r.getBrokerResponsable() != null ? r.getBrokerResponsable().getIdBroker() : null,
                    brokerNombre,
                    r.getFechaCambio(),
                    r.getMotivo());
        }
    }

    // ---------- Asignaciones agente-broker ----------

    public record ReasignarAgenteRequest(Long idAgente, Long idBrokerDestino, String motivo) {
    }

    public record AsignacionAgenteResponse(Long idAgente, String nombre, String numeroDocumento,
                                           String estadoAdministrativo, String estadoOperativo, String brokerActual) {

        public static AsignacionAgenteResponse desde(AgenteInmobiliario a, String brokerActual) {
            var persona = a.getPersona();
            return new AsignacionAgenteResponse(
                    a.getIdAgente(),
                    persona != null ? persona.getNombresORazonSocial() : null,
                    persona != null ? persona.getNumeroDocumento() : null,
                    codigo(a.getEstadoAdministrativo()),
                    codigo(a.getEstadoOperativo()),
                    brokerActual);
        }
    }

    public record AsignacionBrokerResponse(Long idBroker, String nombre, String zona, String estadoAdministrativo,
                                           boolean esAdministrador, int agentesACargo) {

        public static AsignacionBrokerResponse desde(Broker b, int agentesACargo) {
            var persona = b.getPersona();
            return new AsignacionBrokerResponse(
                    b.getIdBroker(),
                    persona != null ? persona.getNombresORazonSocial() : null,
                    b.getZona(),
                    codigo(b.getEstadoAdministrativo()),
                    b.isEsAdministrador(),
                    agentesACargo);
        }
    }

    public record BrokerAgenteResponse(Long id, Long idAgente, String agenteNombre, Long idBrokerAnterior,
                                       String brokerAnteriorNombre, Long idBrokerNuevo, String brokerNuevoNombre,
                                       Long idBrokerAdministrador, String brokerAdministradorNombre,
                                       LocalDateTime fechaCambio, String motivo) {

        public static BrokerAgenteResponse desde(ReasignacionAgenteBroker r, String agenteNombre,
                String brokerAnteriorNombre, String brokerNuevoNombre, String brokerAdministradorNombre) {
            return new BrokerAgenteResponse(
                    r.getIdReasignacion(),
                    r.getAgente() != null ? r.getAgente().getIdAgente() : null, agenteNombre,
                    r.getBrokerAnterior() != null ? r.getBrokerAnterior().getIdBroker() : null, brokerAnteriorNombre,
                    r.getBrokerNuevo() != null ? r.getBrokerNuevo().getIdBroker() : null, brokerNuevoNombre,
                    r.getBrokerAdministrador() != null ? r.getBrokerAdministrador().getIdBroker() : null, brokerAdministradorNombre,
                    r.getFechaCambio(), r.getMotivo());
        }
    }

    // ---------- Brokers ----------

    public record BrokerRequest(String nombre, String tipoPersona, String tipoDocumento, String numeroDocumento,
                                String telefono, String correo, String usuario, String contrasena, String zona,
                                String codigoBroker, String estado, Boolean esAdministrador) {
    }

    // Alta/edición de agente inmobiliario (lo crea el broker supervisor en sesión).
    public record AgenteRequest(String nombre, String tipoPersona, String tipoDocumento, String numeroDocumento,
                                String telefono, String correo, String usuario, String contrasena, String zona,
                                String codigoAgente, String estado, String estadoOperativo) {
    }

    public record BrokerResponse(Long id, String codigoBroker, String nombre, String tipoPersona, String tipoDocumento,
                                 String numeroDocumento, String telefono, String correo, String usuario, String zona,
                                 LocalDate fechaDesignacion, String estadoAdministrativo, boolean esAdministrador,
                                 int agentesACargo) {

        public static BrokerResponse desde(Broker b, int agentesACargo) {
            var persona = b.getPersona();
            return new BrokerResponse(
                    b.getIdBroker(), b.getCodigoBroker(),
                    persona != null ? persona.getNombresORazonSocial() : null,
                    persona != null ? codigo(persona.getTipoPersona()) : null,
                    persona != null ? codigo(persona.getTipoDocumento()) : null,
                    persona != null ? persona.getNumeroDocumento() : null,
                    persona != null ? persona.getTelefono() : null,
                    persona != null ? persona.getCorreo() : null,
                    b.getNombreUsuario(), b.getZona(), b.getFechaDesignacion(),
                    codigo(b.getEstadoAdministrativo()), b.isEsAdministrador(), agentesACargo);
        }
    }

    // ---------- Indicadores (dashboard / reportes) ----------

    // Conteo simple etiqueta/valor (etapas del donut).
    public record IndicadorConteo(String nombre, int valor) {
    }

    // Tramo del embudo de conversion (valor absoluto + porcentaje sobre la base).
    public record IndicadorEmbudo(String etapa, int valor, int porcentaje) {
    }

    // Fila de desempeno por responsable (broker o agente, segun el rol que consulta).
    public record IndicadorDesempeno(String nombre, int captaciones, int cierres, int conversion) {
    }

    // Indicadores operativos del seguimiento (Etapa 9). Vista personal del agente; para broker/admin
    // agregan su alcance. "AlDia" = recontactos cuyo proximo contacto aun no vence (atendidos a tiempo).
    public record IndicadorOperativo(
            int recontactosVencidos,
            int recontactosAlDia,
            int diasPromedioSinSeguimiento,
            int visitasPendientes,
            int solicitudesSinCierre,
            int conversionProspeccionCaptacion) {
    }

    // Resumen agregado para los paneles. Una sola lectura alimenta las tarjetas, las
    // graficas, el embudo, la tabla de desempeno y los contadores del menu lateral.
    public record IndicadoresResponse(
            String ambito,
            int captacionesPorRevisar,
            int solicitudesPorEvaluar,
            int captacionesTotales,
            int captacionesActivas,
            int captacionesPendientes,
            int captacionesObservadas,
            int oportunidadesActivas,
            int interacciones,
            int visitas,
            int cierres,
            int agentesActivos,
            int brokersActivos,
            List<String> mesesEtiquetas,
            List<Integer> cierresPorMes,
            List<Integer> conversionPorPeriodo,
            List<Integer> captacionesPorPeriodo,
            List<IndicadorConteo> etapas,
            List<IndicadorConteo> captacionesSalud,
            List<IndicadorEmbudo> embudo,
            List<IndicadorDesempeno> desempeno,
            IndicadorOperativo operativo) {
    }

    // ---------- Soporte ----------

    private static String ruta(Alerta alerta) {
        Long id = alerta.getEntidadId();
        if (id == null || id <= 0 || alerta.getEntidadTipo() == null) {
            return null;
        }
        return switch (alerta.getEntidadTipo()) {
            case SOLICITUD_ALQUILER -> "solicitud-detail/" + id;
            case OPORTUNIDAD -> "oportunidad-detail/" + id;
            case VISITA -> "visitas?focus=" + id;
            case CLIENTE_INTERESADO -> "cliente-detail/" + id;
            case PROPIETARIO -> "owner-detail/" + id;
            case PROSPECCION -> "prospeccion-detail/" + id;
            case CONTRATO_ALQUILER -> "comisiones";
            default -> null;
        };
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
