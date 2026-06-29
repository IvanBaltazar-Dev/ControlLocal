package com.controllocal.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.ClienteInteresadoBusinessLogic;
import com.controllocal.bl.ContratoAlquilerBusinessLogic;
import com.controllocal.bl.InteraccionComercialBusinessLogic;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.PropietarioBusinessLogic;
import com.controllocal.bl.ProspeccionBusinessLogic;
import com.controllocal.bl.RequerimientoClienteBusinessLogic;
import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.bl.VisitaBusinessLogic;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.ClienteInteresadoBusinessLogicImpl;
import com.controllocal.bl.impl.ContratoAlquilerBusinessLogicImpl;
import com.controllocal.bl.impl.InteraccionComercialBusinessLogicImpl;
import com.controllocal.bl.impl.OportunidadComercialBusinessLogicImpl;
import com.controllocal.bl.impl.PropietarioBusinessLogicImpl;
import com.controllocal.bl.impl.ProspeccionBusinessLogicImpl;
import com.controllocal.bl.impl.RequerimientoClienteBusinessLogicImpl;
import com.controllocal.bl.impl.SolicitudAlquilerBusinessLogicImpl;
import com.controllocal.bl.impl.VisitaBusinessLogicImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.comercial.RequerimientoCliente;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.comercial.enums.EstadoRequerimiento;
import com.controllocal.model.inmueble.Distrito;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.BrokerAgente;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

public final class FichaComercialSupport {

    public static final int DEFAULT_PAGE_SIZE = 8;
    private static final List<String> SECCIONES_CLIENTE = List.of(
            "requerimientos", "propiedades", "oportunidades", "interacciones",
            "visitas", "solicitudes", "cierres", "agentes");
    private static final List<String> SECCIONES_PROPIETARIO = List.of(
            "locales", "prospecciones", "captaciones", "oportunidades",
            "solicitudes", "cierres", "agentes");

    private final ClienteInteresadoBusinessLogic clientes = new ClienteInteresadoBusinessLogicImpl();
    private final PropietarioBusinessLogic propietarios = new PropietarioBusinessLogicImpl();
    private final ProspeccionBusinessLogic prospecciones = new ProspeccionBusinessLogicImpl();
    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();
    private final OportunidadComercialBusinessLogic oportunidades =
            new OportunidadComercialBusinessLogicImpl();
    private final SolicitudAlquilerBusinessLogic solicitudes = new SolicitudAlquilerBusinessLogicImpl();
    private final ContratoAlquilerBusinessLogic contratos = new ContratoAlquilerBusinessLogicImpl();
    private final InteraccionComercialBusinessLogic interacciones =
            new InteraccionComercialBusinessLogicImpl();
    private final VisitaBusinessLogic visitas = new VisitaBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();
    private final RequerimientoClienteBusinessLogic requerimientos =
            new RequerimientoClienteBusinessLogicImpl();

    public ClienteFichaResponse fichaCliente(long idCliente, UsuarioAutenticado usuario, int pageSize) {
        ClienteInteresado cliente = clienteConAcceso(idCliente, usuario);
        Contexto contexto = contexto(usuario);
        int tamano = tamano(pageSize);
        Map<String, FichaSectionResponse> sections = new LinkedHashMap<>();
        sections.put("requerimientos", seccionClienteInterna(idCliente, "requerimientos", contexto, 1, tamano));
        for (String section : SECCIONES_CLIENTE) {
            sections.putIfAbsent(section, pendiente(section, tamano));
        }
        boolean activo = requerimientos.listarPorCliente(idCliente).stream()
                .anyMatch(item -> item.getEstado() == EstadoRequerimiento.ACTIVO);
        String cta = activo
                ? ("AGENTE".equals(usuario.rol())
                        ? "/oportunidad-form?clienteId=" + idCliente
                        : "")
                : "";
        return new ClienteFichaResponse(Dtos.ClienteResponse.desde(cliente), activo, cta, sections);
    }

    public PropietarioFichaResponse fichaPropietario(long idPropietario, UsuarioAutenticado usuario, int pageSize) {
        Propietario propietario = propietarioConAcceso(idPropietario, usuario);
        Contexto contexto = contexto(usuario);
        int tamano = tamano(pageSize);
        Map<String, FichaSectionResponse> sections = new LinkedHashMap<>();
        sections.put("locales", seccionPropietarioInterna(idPropietario, "locales", contexto, 1, tamano));
        for (String section : SECCIONES_PROPIETARIO) {
            sections.putIfAbsent(section, pendiente(section, tamano));
        }
        return new PropietarioFichaResponse(Dtos.PropietarioResponse.desde(propietario), sections);
    }

    public FichaSectionResponse seccionCliente(
            long idCliente,
            String section,
            UsuarioAutenticado usuario,
            int page,
            int pageSize) {
        clienteConAcceso(idCliente, usuario);
        return seccionClienteInterna(idCliente, section, contexto(usuario), page, pageSize);
    }

    private FichaSectionResponse seccionClienteInterna(
            long idCliente,
            String section,
            Contexto contexto,
            int page,
            int pageSize) {
        String clave = normal(section);
        List<FichaRowResponse> rows = filasCliente(idCliente, clave, contexto);
        rows = aplicarPrivacidadAgente(rows, contexto, clave);
        return pagina(clave, rows, page, pageSize);
    }

    private FichaSectionResponse resumenClienteInterna(
            long idCliente,
            String section,
            Contexto contexto,
            int pageSize) {
        String clave = normal(section);
        List<FichaRowResponse> rows = aplicarPrivacidadAgente(filasCliente(idCliente, clave, contexto), contexto, clave);
        return resumen(clave, rows, pageSize);
    }

    public FichaSectionResponse seccionPropietario(
            long idPropietario,
            String section,
            UsuarioAutenticado usuario,
            int page,
            int pageSize) {
        propietarioConAcceso(idPropietario, usuario);
        return seccionPropietarioInterna(idPropietario, section, contexto(usuario), page, pageSize);
    }

    private FichaSectionResponse seccionPropietarioInterna(
            long idPropietario,
            String section,
            Contexto contexto,
            int page,
            int pageSize) {
        String clave = normal(section);
        List<FichaRowResponse> rows = filasPropietario(idPropietario, clave, contexto);
        rows = aplicarPrivacidadAgente(rows, contexto, clave);
        return pagina(clave, rows, page, pageSize);
    }

    private FichaSectionResponse resumenPropietarioInterna(
            long idPropietario,
            String section,
            Contexto contexto,
            int pageSize) {
        String clave = normal(section);
        List<FichaRowResponse> rows = aplicarPrivacidadAgente(
                filasPropietario(idPropietario, clave, contexto), contexto, clave);
        return resumen(clave, rows, pageSize);
    }

    private List<FichaRowResponse> filasCliente(long idCliente, String clave, Contexto contexto) {
        return switch (clave) {
            case "requerimientos" -> filasRequerimientos(idCliente);
            case "propiedades" -> filasPropiedadesCliente(idCliente, contexto);
            case "oportunidades" -> filasOportunidadesCliente(idCliente, contexto);
            case "interacciones" -> filasInteraccionesCliente(idCliente, contexto);
            case "visitas" -> filasVisitasCliente(idCliente, contexto);
            case "solicitudes" -> filasSolicitudesCliente(idCliente, contexto);
            case "cierres" -> filasCierresCliente(idCliente, contexto);
            case "agentes" -> filasAgentesCliente(idCliente, contexto);
            default -> throw ApiException.badRequest("Seccion de ficha de cliente no valida.");
        };
    }

    private List<FichaRowResponse> filasPropietario(long idPropietario, String clave, Contexto contexto) {
        return switch (clave) {
            case "locales" -> filasLocalesPropietario(idPropietario, contexto);
            case "prospecciones" -> filasProspeccionesPropietario(idPropietario, contexto);
            case "captaciones" -> filasCaptacionesPropietario(idPropietario, contexto);
            case "oportunidades" -> filasOportunidadesPropietario(idPropietario, contexto);
            case "solicitudes" -> filasSolicitudesPropietario(idPropietario, contexto);
            case "cierres" -> filasCierresPropietario(idPropietario, contexto);
            case "agentes" -> filasAgentesPropietario(idPropietario, contexto);
            default -> throw ApiException.badRequest("Seccion de ficha de propietario no valida.");
        };
    }

    private ClienteInteresado clienteConAcceso(long idCliente, UsuarioAutenticado usuario) {
        ClienteInteresado cliente = clientes.buscarPorId(idCliente)
                .orElseThrow(() -> ApiException.noEncontrado("Cliente"));
        if ("ADMIN".equals(usuario.rol()) || "AGENTE".equals(usuario.rol())
                || clienteTieneHistoriaVisible(idCliente, contexto(usuario))) {
            return cliente;
        }
        throw ApiException.prohibido();
    }

    private Propietario propietarioConAcceso(long idPropietario, UsuarioAutenticado usuario) {
        Propietario propietario = propietarios.buscarPorId(idPropietario)
                .orElseThrow(() -> ApiException.noEncontrado("Propietario"));
        if ("ADMIN".equals(usuario.rol()) || "AGENTE".equals(usuario.rol())
                || propietarioTieneHistoriaVisible(idPropietario, contexto(usuario))) {
            return propietario;
        }
        throw ApiException.prohibido();
    }

    private boolean clienteTieneHistoriaVisible(long idCliente, Contexto contexto) {
        return oportunidades.listarPorCliente(idCliente).stream().anyMatch(o -> mismoCliente(o, idCliente) && visible(o, contexto))
                || solicitudes.listarPorCliente(idCliente).stream().anyMatch(s -> mismoCliente(s, idCliente) && visible(s, contexto))
                || visitas.listarPorCliente(idCliente).stream().anyMatch(v -> mismoCliente(v, idCliente) && visible(v, contexto))
                || interacciones.listarTodos().stream().anyMatch(i -> mismoCliente(i, idCliente) && visible(i, contexto))
                || contratos.listarTodos().stream().anyMatch(c -> {
                    SolicitudAlquiler solicitud = solicitudContrato(c);
                    return solicitud != null && mismoCliente(solicitud, idCliente) && visible(solicitud, contexto);
                });
    }

    private boolean propietarioTieneHistoriaVisible(long idPropietario, Contexto contexto) {
        return prospecciones.listarPorPropietario(idPropietario).stream().anyMatch(p -> mismoPropietario(p.getLocalComercial(), idPropietario) && visible(p, contexto))
                || captaciones.listarPorPropietario(idPropietario).stream().anyMatch(c -> mismoPropietario(c.getLocalComercial(), idPropietario) && visible(c, contexto))
                || oportunidades.listarPorPropietario(idPropietario).stream().anyMatch(o -> mismoPropietario(local(o), idPropietario) && visible(o, contexto))
                || solicitudes.listarPorPropietario(idPropietario).stream().anyMatch(s -> mismoPropietario(local(s), idPropietario) && visible(s, contexto))
                || contratos.listarTodos().stream().anyMatch(c -> {
                    SolicitudAlquiler solicitud = solicitudContrato(c);
                    return solicitud != null && mismoPropietario(local(solicitud), idPropietario)
                            && visible(solicitud, contexto);
                });
    }

    private List<FichaRowResponse> filasRequerimientos(long idCliente) {
        return requerimientos.listarPorCliente(idCliente).stream()
                .map(this::filaRequerimiento)
                .sorted(orden())
                .toList();
    }

    private List<FichaRowResponse> filasPropiedadesCliente(long idCliente, Contexto contexto) {
        Map<String, FichaRowResponse> filas = new LinkedHashMap<>();
        oportunidades.listarPorCliente(idCliente).stream()
                .filter(o -> mismoCliente(o, idCliente) && visible(o, contexto))
                .forEach(o -> putLocal(filas, local(o), o.getCaptacion(), o.getAgenteResponsable(), "Propiedad mostrada",
                        "oportunidad-detail/" + textoId(o.getIdOportunidad()), o.getFechaRegistro()));
        visitas.listarPorCliente(idCliente).stream()
                .filter(v -> mismoCliente(v, idCliente) && visible(v, contexto))
                .forEach(v -> putLocal(filas, local(v), v.getCaptacion(), v.getAgenteResponsable(), "Propiedad visitada",
                        v.getOportunidadComercial() != null && v.getOportunidadComercial().getIdOportunidad() != null
                                ? "oportunidad-detail/" + v.getOportunidadComercial().getIdOportunidad()
                                : "",
                        fechaOrden(v.getFechaVisita())));
        solicitudes.listarPorCliente(idCliente).stream()
                .filter(s -> mismoCliente(s, idCliente) && visible(s, contexto))
                .forEach(s -> putLocal(filas, local(s), s.getCaptacion(), s.getAgenteResponsable(), "Solicitud enviada",
                        s.getCodigoSolicitud() != null ? "solicitud-detail/" + s.getCodigoSolicitud() : "",
                        fechaOrden(s.getFechaRegistro())));
        return filas.values().stream().sorted(orden()).toList();
    }

    private List<FichaRowResponse> filasOportunidadesCliente(long idCliente, Contexto contexto) {
        return oportunidades.listarPorCliente(idCliente).stream()
                .filter(o -> mismoCliente(o, idCliente) && visible(o, contexto))
                .map(this::filaOportunidad)
                .sorted(orden())
                .toList();
    }

    private List<FichaRowResponse> filasInteraccionesCliente(long idCliente, Contexto contexto) {
        return interacciones.listarTodos().stream()
                .filter(i -> mismoCliente(i, idCliente) && visible(i, contexto))
                .map(this::filaInteraccion)
                .sorted(orden())
                .toList();
    }

    private List<FichaRowResponse> filasVisitasCliente(long idCliente, Contexto contexto) {
        return visitas.listarPorCliente(idCliente).stream()
                .filter(v -> mismoCliente(v, idCliente) && visible(v, contexto))
                .map(this::filaVisita)
                .sorted(orden())
                .toList();
    }

    private List<FichaRowResponse> filasSolicitudesCliente(long idCliente, Contexto contexto) {
        return solicitudes.listarPorCliente(idCliente).stream()
                .filter(s -> mismoCliente(s, idCliente) && visible(s, contexto))
                .map(this::filaSolicitud)
                .sorted(orden())
                .toList();
    }

    private List<FichaRowResponse> filasCierresCliente(long idCliente, Contexto contexto) {
        return contratos.listarTodos().stream()
                .map(c -> filaContrato(c, contexto))
                .filter(Objects::nonNull)
                .filter(row -> Objects.equals(row.clienteId(), idCliente))
                .sorted(orden())
                .toList();
    }

    private List<FichaRowResponse> filasAgentesCliente(long idCliente, Contexto contexto) {
        Map<Long, FichaRowResponse> agentes = new LinkedHashMap<>();
        oportunidades.listarPorCliente(idCliente).stream()
                .filter(o -> mismoCliente(o, idCliente) && visible(o, contexto))
                .forEach(o -> putAgente(agentes, o.getAgenteResponsable(), "Oportunidad", o.getFechaRegistro()));
        solicitudes.listarPorCliente(idCliente).stream()
                .filter(s -> mismoCliente(s, idCliente) && visible(s, contexto))
                .forEach(s -> putAgente(agentes, s.getAgenteResponsable(), "Solicitud", fechaOrden(s.getFechaRegistro())));
        visitas.listarPorCliente(idCliente).stream()
                .filter(v -> mismoCliente(v, idCliente) && visible(v, contexto))
                .forEach(v -> putAgente(agentes, v.getAgenteResponsable(), "Visita", fechaOrden(v.getFechaVisita())));
        interacciones.listarTodos().stream()
                .filter(i -> mismoCliente(i, idCliente) && visible(i, contexto))
                .forEach(i -> putAgente(agentes, agente(i), "Interaccion", i.getFechaHora()));
        return agentes.values().stream().sorted(orden()).toList();
    }

    private List<FichaRowResponse> filasLocalesPropietario(long idPropietario, Contexto contexto) {
        Map<Long, FichaRowResponse> filas = new LinkedHashMap<>();
        prospecciones.listarPorPropietario(idPropietario).stream()
                .filter(p -> mismoPropietario(p.getLocalComercial(), idPropietario) && visible(p, contexto))
                .forEach(p -> putLocal(filas, p.getLocalComercial(), p.getCaptacion(), p.getAgenteResponsable(),
                        "Local en prospeccion", rutaLocal(p.getLocalComercial()), p.getFechaRegistro()));
        captaciones.listarPorPropietario(idPropietario).stream()
                .filter(c -> mismoPropietario(c.getLocalComercial(), idPropietario) && visible(c, contexto))
                .forEach(c -> putLocal(filas, c.getLocalComercial(), c, c.getAgenteResponsable(),
                        "Local captado", rutaCaptacion(c), fechaOrden(c.getFechaCaptacion())));
        return filas.values().stream().sorted(orden()).toList();
    }

    private List<FichaRowResponse> filasProspeccionesPropietario(long idPropietario, Contexto contexto) {
        return prospecciones.listarPorPropietario(idPropietario).stream()
                .filter(p -> mismoPropietario(p.getLocalComercial(), idPropietario) && visible(p, contexto))
                .map(this::filaProspeccion)
                .sorted(orden())
                .toList();
    }

    private List<FichaRowResponse> filasCaptacionesPropietario(long idPropietario, Contexto contexto) {
        return captaciones.listarPorPropietario(idPropietario).stream()
                .filter(c -> mismoPropietario(c.getLocalComercial(), idPropietario) && visible(c, contexto))
                .map(this::filaCaptacion)
                .sorted(orden())
                .toList();
    }

    private List<FichaRowResponse> filasOportunidadesPropietario(long idPropietario, Contexto contexto) {
        return oportunidades.listarPorPropietario(idPropietario).stream()
                .filter(o -> mismoPropietario(local(o), idPropietario) && visible(o, contexto))
                .map(this::filaOportunidad)
                .sorted(orden())
                .toList();
    }

    private List<FichaRowResponse> filasSolicitudesPropietario(long idPropietario, Contexto contexto) {
        return solicitudes.listarPorPropietario(idPropietario).stream()
                .filter(s -> mismoPropietario(local(s), idPropietario) && visible(s, contexto))
                .map(this::filaSolicitud)
                .sorted(orden())
                .toList();
    }

    private List<FichaRowResponse> filasCierresPropietario(long idPropietario, Contexto contexto) {
        return contratos.listarTodos().stream()
                .map(c -> filaContrato(c, contexto))
                .filter(Objects::nonNull)
                .filter(row -> Objects.equals(row.propietarioId(), idPropietario))
                .sorted(orden())
                .toList();
    }

    private List<FichaRowResponse> filasAgentesPropietario(long idPropietario, Contexto contexto) {
        Map<Long, FichaRowResponse> agentes = new LinkedHashMap<>();
        prospecciones.listarPorPropietario(idPropietario).stream()
                .filter(p -> mismoPropietario(p.getLocalComercial(), idPropietario) && visible(p, contexto))
                .forEach(p -> putAgente(agentes, p.getAgenteResponsable(), "Prospeccion", p.getFechaRegistro()));
        captaciones.listarPorPropietario(idPropietario).stream()
                .filter(c -> mismoPropietario(c.getLocalComercial(), idPropietario) && visible(c, contexto))
                .forEach(c -> putAgente(agentes, c.getAgenteResponsable(), "Captacion", fechaOrden(c.getFechaCaptacion())));
        oportunidades.listarPorPropietario(idPropietario).stream()
                .filter(o -> mismoPropietario(local(o), idPropietario) && visible(o, contexto))
                .forEach(o -> putAgente(agentes, o.getAgenteResponsable(), "Oportunidad", o.getFechaRegistro()));
        solicitudes.listarPorPropietario(idPropietario).stream()
                .filter(s -> mismoPropietario(local(s), idPropietario) && visible(s, contexto))
                .forEach(s -> putAgente(agentes, s.getAgenteResponsable(), "Solicitud", fechaOrden(s.getFechaRegistro())));
        return agentes.values().stream().sorted(orden()).toList();
    }

    private FichaRowResponse filaRequerimiento(RequerimientoCliente r) {
        ClienteInteresado cliente = r.getCliente();
        return new FichaRowResponse(
                textoId(r.getIdRequerimiento()),
                texto("REQ-" + textoId(r.getIdRequerimiento())),
                "Requerimiento",
                texto(r.getRubro(), "Perfil de busqueda"),
                requerimientoResumen(r),
                "-",
                distritos(r),
                clienteNombre(cliente),
                clienteId(cliente),
                "-",
                null,
                "-",
                estado(r.getEstado()),
                fecha(r.getFechaActualizacion(), r.getFechaCreacion()),
                clienteId(cliente) != null ? "cliente-detail/" + clienteId(cliente) : "",
                "target",
                r.getEstado() == EstadoRequerimiento.ACTIVO ? "green" : "gray",
                fechaOrden(r.getFechaActualizacion(), r.getFechaCreacion()));
    }

    private FichaRowResponse filaProspeccion(Prospeccion p) {
        LocalComercial local = p.getLocalComercial();
        LocalDateTime fecha = fechaOrden(
                fechaOrden(p.getFechaPropuesta(), p.getFechaReunion(), p.getFechaContacto()),
                p.getFechaRegistro());
        return filaBase(
                textoId(p.getIdProspeccion()),
                texto(p.getCodigoProspeccion(), codigoLocal(local)),
                "Prospeccion",
                "Seguimiento del propietario",
                hitoProspeccion(p),
                local,
                null,
                p.getAgenteResponsable(),
                estado(p.getEstado()),
                fecha(fecha),
                rutaLocal(local),
                "store",
                "blue",
                fecha);
    }

    private FichaRowResponse filaCaptacion(Captacion c) {
        LocalComercial local = c.getLocalComercial();
        return filaBase(
                textoId(c.getIdCaptacion()),
                texto(c.getCodigoCaptacion()),
                "Captacion",
                "Expediente de captacion",
                vigencia(c),
                local,
                null,
                c.getAgenteResponsable(),
                estado(c.getEstado()),
                fecha(c.getFechaCaptacion()),
                rutaCaptacion(c),
                "pin",
                "blue",
                fechaOrden(c.getFechaCaptacion(), c.getFechaInicioVigencia()));
    }

    private FichaRowResponse filaOportunidad(OportunidadComercial o) {
        LocalComercial local = local(o);
        ClienteInteresado cliente = o.getClienteInteresado();
        return filaBase(
                textoId(o.getIdOportunidad()),
                texto(o.getCodigoOportunidad()),
                "Oportunidad",
                clienteNombre(cliente),
                texto(o.getObservaciones(), "Seguimiento comercial"),
                local,
                cliente,
                o.getAgenteResponsable(),
                estado(o.getEstado()),
                fecha(o.getFechaActualizacion(), o.getFechaRegistro()),
                o.getIdOportunidad() != null ? "oportunidad-detail/" + o.getIdOportunidad() : "",
                "target",
                "info",
                fechaOrden(o.getFechaActualizacion(), o.getFechaRegistro()));
    }

    private FichaRowResponse filaSolicitud(SolicitudAlquiler s) {
        LocalComercial local = local(s);
        ClienteInteresado cliente = s.getClienteInteresado();
        return filaBase(
                textoId(s.getIdSolicitud()),
                texto(s.getCodigoSolicitud()),
                "Solicitud",
                clienteNombre(cliente),
                s.getMontoPropuesto() != null ? "Oferta USD " + s.getMontoPropuesto().toPlainString() : "Solicitud de alquiler",
                local,
                cliente,
                s.getAgenteResponsable(),
                estado(s.getEstado()),
                fecha(s.getFechaActualizacionEstado(), fechaOrden(s.getFechaRegistro())),
                s.getCodigoSolicitud() != null ? "solicitud-detail/" + s.getCodigoSolicitud() : "",
                "fileText",
                "gray",
                fechaOrden(s.getFechaActualizacionEstado(), fechaOrden(s.getFechaRegistro())));
    }

    private FichaRowResponse filaInteraccion(InteraccionComercial i) {
        OportunidadComercial oportunidad = oportunidad(i);
        LocalComercial local = local(oportunidad);
        ClienteInteresado cliente = cliente(i);
        return filaBase(
                textoId(i.getIdInteraccion()),
                oportunidad != null ? texto(oportunidad.getCodigoOportunidad()) : textoId(i.getIdInteraccion()),
                "Interaccion",
                texto(i.getCanalContacto() != null ? i.getCanalContacto().getDescripcion() : null, "Contacto comercial"),
                texto(i.getResultado() != null ? i.getResultado().getDescripcion() : null, i.getObservaciones()),
                local,
                cliente,
                agente(i),
                i.getResultado() != null ? i.getResultado().getDescripcion() : "-",
                fecha(i.getFechaHora()),
                i.getIdInteraccion() != null ? "interaccion-detail/" + i.getIdInteraccion() : "",
                "activity",
                "blue",
                i.getFechaHora());
    }

    private FichaRowResponse filaVisita(Visita v) {
        LocalComercial local = local(v);
        ClienteInteresado cliente = v.getClienteInteresado();
        LocalDateTime fecha = v.getFechaVisita() != null ? v.getFechaVisita().atStartOfDay() : null;
        return filaBase(
                textoId(v.getIdVisita()),
                texto(v.getOportunidadComercial() != null ? v.getOportunidadComercial().getCodigoOportunidad() : null,
                        "VIS-" + textoId(v.getIdVisita())),
                "Visita",
                "Visita comercial",
                texto(v.getObservaciones(), v.getResultado() != null ? v.getResultado().getDescripcion() : null),
                local,
                cliente,
                v.getAgenteResponsable(),
                estado(v.getEstado()),
                fecha(fecha),
                v.getOportunidadComercial() != null && v.getOportunidadComercial().getIdOportunidad() != null
                        ? "oportunidad-detail/" + v.getOportunidadComercial().getIdOportunidad()
                        : "",
                "calendar",
                "blue",
                fecha);
    }

    private FichaRowResponse filaContrato(ContratoAlquiler c, Contexto contexto) {
        SolicitudAlquiler solicitud = solicitudContrato(c);
        if (solicitud == null || !visible(solicitud, contexto)) {
            return null;
        }
        LocalComercial local = local(solicitud);
        ClienteInteresado cliente = solicitud.getClienteInteresado();
        return filaBase(
                textoId(c.getIdContratoAlquiler()),
                texto(solicitud.getCodigoSolicitud(),
                        solicitud.getOportunidadComercial() != null ? solicitud.getOportunidadComercial().getCodigoOportunidad() : null),
                "Cierre",
                clienteNombre(cliente),
                c.getEstadoContrato() != null ? c.getEstadoContrato().getDescripcion() : "Alquilada",
                local,
                cliente,
                solicitud.getAgenteResponsable(),
                c.getEstadoContrato() != null ? c.getEstadoContrato().getDescripcion() : "Alquilada",
                fecha(c.getFechaCierre()),
                solicitud.getCodigoSolicitud() != null ? "solicitud-detail/" + solicitud.getCodigoSolicitud() : "",
                "checkCircle",
                "green",
                fechaOrden(c.getFechaCierre()));
    }

    private FichaRowResponse filaBase(
            String id,
            String codigo,
            String proceso,
            String titulo,
            String subtitulo,
            LocalComercial local,
            ClienteInteresado cliente,
            AgenteInmobiliario agente,
            String estado,
            String fecha,
            String ruta,
            String icono,
            String tono,
            LocalDateTime fechaOrden) {
        return new FichaRowResponse(
                id,
                codigo,
                proceso,
                texto(titulo),
                texto(subtitulo),
                direccion(local),
                distrito(local),
                clienteNombre(cliente),
                clienteId(cliente),
                propietario(local),
                propietarioId(local),
                agente(agente),
                texto(estado),
                texto(fecha),
                texto(ruta, ""),
                icono,
                tono,
                fechaOrden);
    }

    private void putLocal(
            Map<?, FichaRowResponse> raw,
            LocalComercial local,
            Captacion captacion,
            AgenteInmobiliario agente,
            String titulo,
            String ruta,
            LocalDateTime fechaOrden) {
        if (local == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<Object, FichaRowResponse> filas = (Map<Object, FichaRowResponse>) raw;
        Object key = local.getIdLocal() != null ? local.getIdLocal() : direccion(local);
        filas.putIfAbsent(key, filaBase(
                textoId(local.getIdLocal()),
                texto(captacion != null ? captacion.getCodigoCaptacion() : null, codigoLocal(local)),
                "Propiedad",
                titulo,
                texto(local.getRubroPermitido(), local.getDescripcion()),
                local,
                null,
                agente,
                estado(local.getEstado()),
                fecha(fechaOrden),
                texto(ruta, rutaLocal(local)),
                "store",
                "blue",
                fechaOrden));
    }

    private void putAgente(Map<Long, FichaRowResponse> agentes, AgenteInmobiliario agente, String proceso, LocalDateTime fechaOrden) {
        if (agente == null || agente.getIdAgente() == null) {
            return;
        }
        agentes.putIfAbsent(agente.getIdAgente(), new FichaRowResponse(
                textoId(agente.getIdAgente()),
                texto(agente.getCodigoAgente()),
                "Agente",
                agente(agente),
                "Vinculado por " + proceso,
                "-",
                "-",
                "-",
                null,
                "-",
                null,
                agente(agente),
                agente.getEstadoOperativo() != null ? agente.getEstadoOperativo().getDescripcion() : "-",
                fecha(fechaOrden),
                "",
                "user",
                "gray",
                fechaOrden));
    }

    private List<FichaRowResponse> aplicarPrivacidadAgente(
            List<FichaRowResponse> rows,
            Contexto contexto,
            String section) {
        if (!"AGENTE".equals(contexto.usuario().rol())) {
            return rows;
        }
        if ("agentes".equals(section)) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new FichaRowResponse(
                        row.id(),
                        row.codigo(),
                        row.proceso(),
                        row.titulo(),
                        row.subtitulo(),
                        row.local(),
                        row.distrito(),
                        row.cliente(),
                        row.clienteId(),
                        row.propietario(),
                        row.propietarioId(),
                        "-",
                        row.estado(),
                        row.fecha(),
                        row.ruta(),
                        row.icono(),
                        row.tono(),
                        row.fechaOrden()))
                .toList();
    }

    private FichaSectionResponse pagina(String section, List<FichaRowResponse> rows, int page, int pageSize) {
        int pagina = SeguridadRest.pagina(page);
        int tamano = tamano(pageSize);
        int desde = Math.min((pagina - 1) * tamano, rows.size());
        int hasta = Math.min(desde + tamano, rows.size());
        return new FichaSectionResponse(section, rows.size(), pagina, tamano, rows.subList(desde, hasta));
    }

    private FichaSectionResponse resumen(String section, List<FichaRowResponse> rows, int pageSize) {
        return new FichaSectionResponse(section, rows.size(), 1, tamano(pageSize), List.of());
    }

    private FichaSectionResponse pendiente(String section, int pageSize) {
        return new FichaSectionResponse(section, -1, 0, tamano(pageSize), List.of());
    }

    private Contexto contexto(UsuarioAutenticado usuario) {
        Set<Long> agentesBroker = agentesSupervisados(usuario);
        Set<Long> captacionesBroker = "BROKER".equals(usuario.rol())
                ? captaciones.listarPorBroker(usuario.idDominio()).stream()
                        .map(Captacion::getIdCaptacion)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
                : Set.of();
        return new Contexto(usuario, agentesBroker, captacionesBroker);
    }

    private Set<Long> agentesSupervisados(UsuarioAutenticado usuario) {
        if (!"BROKER".equals(usuario.rol())) {
            return Set.of();
        }
        return brokers.listarAgentesSupervisados(usuario.idDominio()).stream()
                .map(BrokerAgente::getIdAgente)
                .collect(Collectors.toSet());
    }

    private boolean visible(Prospeccion p, Contexto contexto) {
        return visibleAgente(p.getAgenteResponsable(), contexto);
    }

    private boolean visible(Captacion c, Contexto contexto) {
        if ("ADMIN".equals(contexto.usuario().rol())) {
            return true;
        }
        return c != null
                && (visibleAgente(c.getAgenteResponsable(), contexto)
                        || (c.getIdCaptacion() != null && contexto.captacionesBroker().contains(c.getIdCaptacion())));
    }

    private boolean visible(OportunidadComercial o, Contexto contexto) {
        return visibleAgente(o.getAgenteResponsable(), contexto) || visible(o.getCaptacion(), contexto);
    }

    private boolean visible(SolicitudAlquiler s, Contexto contexto) {
        return visibleAgente(s.getAgenteResponsable(), contexto) || visible(s.getCaptacion(), contexto);
    }

    private boolean visible(Visita v, Contexto contexto) {
        return visibleAgente(v.getAgenteResponsable(), contexto) || visible(v.getCaptacion(), contexto);
    }

    private boolean visible(InteraccionComercial i, Contexto contexto) {
        OportunidadComercial oportunidad = oportunidad(i);
        return oportunidad != null
                ? visible(oportunidad, contexto)
                : visibleAgente(i.getAgenteResponsable(), contexto);
    }

    private boolean visibleAgente(AgenteInmobiliario agente, Contexto contexto) {
        String rol = contexto.usuario().rol();
        if ("ADMIN".equals(rol)) {
            return true;
        }
        if (agente == null || agente.getIdAgente() == null) {
            return false;
        }
        if ("AGENTE".equals(rol)) {
            return contexto.usuario().idDominio() == agente.getIdAgente();
        }
        return "BROKER".equals(rol) && contexto.agentesBroker().contains(agente.getIdAgente());
    }

    private boolean mismoCliente(OportunidadComercial oportunidad, long idCliente) {
        return Objects.equals(clienteId(oportunidad.getClienteInteresado()), idCliente);
    }

    private boolean mismoCliente(SolicitudAlquiler solicitud, long idCliente) {
        return Objects.equals(clienteId(solicitud.getClienteInteresado()), idCliente);
    }

    private boolean mismoCliente(Visita visita, long idCliente) {
        return Objects.equals(clienteId(visita.getClienteInteresado()), idCliente)
                || (visita.getOportunidadComercial() != null && mismoCliente(visita.getOportunidadComercial(), idCliente));
    }

    private boolean mismoCliente(InteraccionComercial interaccion, long idCliente) {
        return Objects.equals(clienteId(interaccion.getClienteInteresado()), idCliente)
                || (oportunidad(interaccion) != null && mismoCliente(oportunidad(interaccion), idCliente));
    }

    private boolean mismoPropietario(LocalComercial local, long idPropietario) {
        return Objects.equals(propietarioId(local), idPropietario);
    }

    private SolicitudAlquiler solicitudContrato(ContratoAlquiler contrato) {
        Long idSolicitud = contrato.getSolicitudAlquiler() != null
                ? contrato.getSolicitudAlquiler().getIdSolicitud()
                : null;
        if (idSolicitud != null) {
            return solicitudes.buscarPorId(idSolicitud).orElse(null);
        }
        Long idOportunidad = contrato.getOportunidad() != null ? contrato.getOportunidad().getIdOportunidad() : null;
        if (idOportunidad == null) {
            return null;
        }
        return solicitudes.listarTodos().stream()
                .filter(s -> s.getOportunidadComercial() != null
                        && Objects.equals(s.getOportunidadComercial().getIdOportunidad(), idOportunidad))
                .findFirst()
                .orElse(null);
    }

    private OportunidadComercial oportunidad(InteraccionComercial interaccion) {
        Long idOportunidad = interaccion.getOportunidadComercial() != null
                ? interaccion.getOportunidadComercial().getIdOportunidad()
                : null;
        if (idOportunidad == null) {
            return null;
        }
        return oportunidades.buscarPorId(idOportunidad).orElse(null);
    }

    private ClienteInteresado cliente(InteraccionComercial interaccion) {
        if (interaccion.getClienteInteresado() != null) {
            return interaccion.getClienteInteresado();
        }
        OportunidadComercial oportunidad = oportunidad(interaccion);
        return oportunidad != null ? oportunidad.getClienteInteresado() : null;
    }

    private AgenteInmobiliario agente(InteraccionComercial interaccion) {
        if (interaccion.getAgenteResponsable() != null) {
            return interaccion.getAgenteResponsable();
        }
        OportunidadComercial oportunidad = oportunidad(interaccion);
        return oportunidad != null ? oportunidad.getAgenteResponsable() : null;
    }

    private LocalComercial local(OportunidadComercial oportunidad) {
        Captacion captacion = oportunidad != null ? oportunidad.getCaptacion() : null;
        return captacion != null ? captacion.getLocalComercial() : null;
    }

    private LocalComercial local(SolicitudAlquiler solicitud) {
        Captacion captacion = solicitud != null ? solicitud.getCaptacion() : null;
        return captacion != null ? captacion.getLocalComercial() : null;
    }

    private LocalComercial local(Visita visita) {
        Captacion captacion = visita != null ? visita.getCaptacion() : null;
        if (captacion == null && visita != null && visita.getOportunidadComercial() != null) {
            captacion = visita.getOportunidadComercial().getCaptacion();
        }
        return captacion != null ? captacion.getLocalComercial() : null;
    }

    private static Comparator<FichaRowResponse> orden() {
        return Comparator
                .comparing(FichaRowResponse::fechaOrden, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(FichaRowResponse::proceso)
                .thenComparing(FichaRowResponse::codigo);
    }

    private static int tamano(int pageSize) {
        return Math.min(DEFAULT_PAGE_SIZE, SeguridadRest.tamano(pageSize));
    }

    private static String normal(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private static String texto(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return "-";
    }

    private static String textoId(Long id) {
        return id != null ? id.toString() : "";
    }

    private static String direccion(LocalComercial local) {
        return local != null ? texto(local.getDireccion()) : "-";
    }

    private static String distrito(LocalComercial local) {
        return local != null ? texto(local.getDistrito()) : "-";
    }

    private static String codigoLocal(LocalComercial local) {
        return local != null ? texto(local.getCodigoLocal()) : "-";
    }

    private static String propietario(LocalComercial local) {
        return local != null && local.getPropietario() != null
                ? texto(local.getPropietario().getNombresORazonSocial())
                : "-";
    }

    private static Long propietarioId(LocalComercial local) {
        return local != null ? local.getIdPropietario() : null;
    }

    private static String clienteNombre(ClienteInteresado cliente) {
        return cliente != null && cliente.getPersona() != null
                ? texto(cliente.getPersona().getNombresORazonSocial())
                : "-";
    }

    private static Long clienteId(ClienteInteresado cliente) {
        return cliente != null ? cliente.getIdCliente() : null;
    }

    private static String agente(AgenteInmobiliario agente) {
        return agente != null && agente.getPersona() != null
                ? texto(agente.getPersona().getNombresORazonSocial())
                : "-";
    }

    private static String estado(com.controllocal.model.CodigoEnum valor) {
        return valor != null ? valor.getDescripcion() : "-";
    }

    private static String fecha(LocalDate fecha) {
        return fecha != null ? fecha.toString() : "";
    }

    private static String fecha(LocalDateTime fecha) {
        return fecha != null ? fecha.toString() : "";
    }

    private static String fecha(LocalDateTime primera, LocalDateTime segunda) {
        return fecha(primera != null ? primera : segunda);
    }

    private static LocalDateTime fechaOrden(LocalDate... fechas) {
        for (LocalDate fecha : fechas) {
            if (fecha != null) {
                return fecha.atStartOfDay();
            }
        }
        return null;
    }

    private static LocalDateTime fechaOrden(LocalDateTime... fechas) {
        for (LocalDateTime fecha : fechas) {
            if (fecha != null) {
                return fecha;
            }
        }
        return null;
    }

    private static String hitoProspeccion(Prospeccion p) {
        if (p.getCaptacion() != null && p.getCaptacion().getCodigoCaptacion() != null) {
            return "Captada como " + p.getCaptacion().getCodigoCaptacion();
        }
        if (p.getFechaPropuesta() != null) {
            return "Propuesta entregada";
        }
        if (p.getFechaReunion() != null) {
            return "Reunion registrada";
        }
        if (p.getFechaContacto() != null) {
            return "Contacto registrado";
        }
        return "Prospecto";
    }

    private static String vigencia(Captacion c) {
        if (c.getFechaFinVigencia() != null) {
            return "Vigente hasta " + c.getFechaFinVigencia();
        }
        if (c.getFechaCaptacion() != null) {
            return "Captada el " + c.getFechaCaptacion();
        }
        return "Sin vigencia registrada";
    }

    private static String rutaLocal(LocalComercial local) {
        return local != null && local.getIdLocal() != null ? "local-detail/" + local.getIdLocal() : "";
    }

    private static String rutaCaptacion(Captacion captacion) {
        return captacion != null && captacion.getCodigoCaptacion() != null
                ? "captacion-detail/" + captacion.getCodigoCaptacion()
                : "";
    }

    private static String requerimientoResumen(RequerimientoCliente r) {
        List<String> partes = new ArrayList<>();
        if (r.getTipoInmueble() != null) {
            partes.add(r.getTipoInmueble().getDescripcion());
        }
        if (r.getRentaMin() != null || r.getRentaMax() != null) {
            partes.add("Renta " + rango(r.getRentaMin(), r.getRentaMax(), r.getMoneda() != null ? r.getMoneda().getDescripcion() : "USD"));
        }
        if (r.getMetrajeMin() != null || r.getMetrajeMax() != null) {
            partes.add("Area " + rango(r.getMetrajeMin(), r.getMetrajeMax(), "m2"));
        }
        if (r.getFrenteMinimo() != null) {
            partes.add("Frente desde " + r.getFrenteMinimo().stripTrailingZeros().toPlainString() + " m");
        }
        if (partes.isEmpty() && r.getObservaciones() != null) {
            partes.add(r.getObservaciones());
        }
        return partes.isEmpty() ? "Sin parametros registrados" : String.join(" | ", partes);
    }

    private static String rango(BigDecimal min, BigDecimal max, String unidad) {
        if (min != null && max != null) {
            return min.stripTrailingZeros().toPlainString() + "-" + max.stripTrailingZeros().toPlainString() + " " + unidad;
        }
        if (min != null) {
            return "desde " + min.stripTrailingZeros().toPlainString() + " " + unidad;
        }
        return "hasta " + max.stripTrailingZeros().toPlainString() + " " + unidad;
    }

    private static String distritos(RequerimientoCliente r) {
        return r.getDistritos() == null || r.getDistritos().isEmpty()
                ? "-"
                : r.getDistritos().stream()
                        .map(Distrito::getNombre)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(", "));
    }

    private record Contexto(
            UsuarioAutenticado usuario,
            Set<Long> agentesBroker,
            Set<Long> captacionesBroker) {
    }

    public record ClienteFichaResponse(
            Dtos.ClienteResponse cliente,
            boolean requerimientoActivo,
            String ctaRuta,
            Map<String, FichaSectionResponse> sections) {
    }

    public record PropietarioFichaResponse(
            Dtos.PropietarioResponse propietario,
            Map<String, FichaSectionResponse> sections) {
    }

    public record FichaSectionResponse(
            String section,
            long totalRecords,
            int page,
            int pageSize,
            List<FichaRowResponse> items) {
    }

    public record FichaRowResponse(
            String id,
            String codigo,
            String proceso,
            String titulo,
            String subtitulo,
            String local,
            String distrito,
            String cliente,
            Long clienteId,
            String propietario,
            Long propietarioId,
            String agente,
            String estado,
            String fecha,
            String ruta,
            String icono,
            String tono,
            LocalDateTime fechaOrden) {
    }
}
