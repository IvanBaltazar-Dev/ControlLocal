package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.domain.comercial.InteraccionComercial;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.Prospeccion;
import com.controllocal.domain.comercial.RequerimientoCliente;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comercial.Visita;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.DetalleClienteRepository;
import com.controllocal.persistence.repositorio.InteraccionComercialRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.RequerimientoClienteRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ClienteService;
import com.controllocal.service.FichaComercialService;
import com.controllocal.service.PropietarioService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValoresDePropiedad;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agregador E3. Replica el shaping y la carga parcial de
 * FichaComercialSupport, pero todas las lecturas parten del tenant.
 */
@Service
public class FichaComercialServiceImpl implements FichaComercialService {

    private static final List<String> SECCIONES_CLIENTE = List.of(
            "requerimientos", "propiedades", "oportunidades", "interacciones",
            "visitas", "solicitudes", "cierres", "agentes");
    private static final List<String> SECCIONES_PROPIETARIO = List.of(
            "locales", "prospecciones", "captaciones", "oportunidades",
            "solicitudes", "cierres", "agentes");

    private final DetalleClienteRepository clientes;
    private final PersonaRolRepository roles;
    private final RequerimientoClienteRepository requerimientos;
    private final ProspeccionRepository prospecciones;
    private final CaptacionRepository captaciones;
    private final OportunidadComercialRepository oportunidades;
    private final InteraccionComercialRepository interacciones;
    private final VisitaRepository visitas;
    private final SolicitudAlquilerRepository solicitudes;
    private final ContratoAlquilerRepository contratos;
    private final Alcances alcances;
    private final LectorPorAutoridad lector;

    public FichaComercialServiceImpl(DetalleClienteRepository clientes,
                                     PersonaRolRepository roles,
                                     RequerimientoClienteRepository requerimientos,
                                     ProspeccionRepository prospecciones,
                                     CaptacionRepository captaciones,
                                     OportunidadComercialRepository oportunidades,
                                     InteraccionComercialRepository interacciones,
                                     VisitaRepository visitas,
                                     SolicitudAlquilerRepository solicitudes,
                                     ContratoAlquilerRepository contratos,
                                     Alcances alcances,
                                     LectorPorAutoridad lector) {
        this.clientes = clientes;
        this.roles = roles;
        this.requerimientos = requerimientos;
        this.prospecciones = prospecciones;
        this.captaciones = captaciones;
        this.oportunidades = oportunidades;
        this.interacciones = interacciones;
        this.visitas = visitas;
        this.solicitudes = solicitudes;
        this.contratos = contratos;
        this.alcances = alcances;
        this.lector = lector;
    }

    @Override
    @Transactional(readOnly = true)
    public FichaCliente fichaCliente(long idCliente, int tamano, Actor actor) {
        Contexto contexto = contexto(actor);
        DatosCliente datos = datosCliente(idCliente, contexto);
        int tamanoValido = tamano(tamano);
        Map<String, SeccionFicha> sections = new LinkedHashMap<>();
        sections.put("requerimientos",
                pagina("requerimientos", filasCliente(datos, "requerimientos", contexto), 1, tamanoValido));
        for (String section : SECCIONES_CLIENTE) {
            sections.putIfAbsent(section, pendiente(section, tamanoValido));
        }
        boolean activo = datos.requerimientos().stream()
                .anyMatch(RequerimientoCliente::estaActivo);
        String cta = activo && actor.esAgente()
                ? "/oportunidad-form?clienteId=" + idCliente
                : "";
        return new FichaCliente(ficha(datos.cliente()), activo, cta, sections);
    }

    @Override
    @Transactional(readOnly = true)
    public FichaPropietario fichaPropietario(long idPropietario, int tamano, Actor actor) {
        Contexto contexto = contexto(actor);
        DatosPropietario datos = datosPropietario(idPropietario, contexto);
        int tamanoValido = tamano(tamano);
        Map<String, SeccionFicha> sections = new LinkedHashMap<>();
        sections.put("locales",
                pagina("locales", filasPropietario(datos, "locales", contexto), 1, tamanoValido));
        sections.put("prospecciones",
                resumen("prospecciones", filasPropietario(datos, "prospecciones", contexto), tamanoValido));
        sections.put("captaciones",
                resumen("captaciones", filasPropietario(datos, "captaciones", contexto), tamanoValido));
        for (String section : SECCIONES_PROPIETARIO) {
            sections.putIfAbsent(section, pendiente(section, tamanoValido));
        }
        // Rareza congelada: la ficha legacy no inyecta el contador del CRUD.
        return new FichaPropietario(ficha(datos.propietario(), 0), sections);
    }

    @Override
    @Transactional(readOnly = true)
    public SeccionFicha seccionCliente(long idCliente, String seccion, int pagina, int tamano, Actor actor) {
        Contexto contexto = contexto(actor);
        DatosCliente datos = datosCliente(idCliente, contexto);
        String clave = normal(seccion);
        return pagina(clave, filasCliente(datos, clave, contexto), pagina, tamano);
    }

    @Override
    @Transactional(readOnly = true)
    public SeccionFicha seccionPropietario(
            long idPropietario, String seccion, int pagina, int tamano, Actor actor) {
        Contexto contexto = contexto(actor);
        DatosPropietario datos = datosPropietario(idPropietario, contexto);
        String clave = normal(seccion);
        return pagina(clave, filasPropietario(datos, clave, contexto), pagina, tamano);
    }

    private DatosCliente datosCliente(long idCliente, Contexto contexto) {
        long organizacion = contexto.actor().idOrganizacion();
        DetalleCliente cliente = clientes.buscarFicha(organizacion, idCliente)
                .orElseThrow(() -> new NoEncontradoException("Cliente"));
        DatosCliente datos = new DatosCliente(
                cliente,
                requerimientos.listarPorCliente(organizacion, idCliente),
                oportunidades.listarFichaPorCliente(organizacion, idCliente),
                interacciones.listarFichaPorCliente(organizacion, idCliente),
                visitas.listarFichaPorCliente(organizacion, idCliente),
                solicitudes.listarFichaPorCliente(organizacion, idCliente),
                contratos.listarFichaPorCliente(organizacion, idCliente));
        if (esBroker(contexto.actor()) && !historiaVisible(datos, contexto)) {
            throw new AccesoNoAutorizadoException();
        }
        return datos;
    }

    private DatosPropietario datosPropietario(long idPropietario, Contexto contexto) {
        long organizacion = contexto.actor().idOrganizacion();
        PersonaRol propietario = roles.buscarPropietario(organizacion, idPropietario)
                .orElseThrow(() -> new NoEncontradoException("Propietario"));
        DatosPropietario datos = new DatosPropietario(
                propietario,
                prospecciones.listarFichaPorPropietario(organizacion, idPropietario),
                captaciones.listarFichaPorPropietario(organizacion, idPropietario),
                oportunidades.listarFichaPorPropietario(organizacion, idPropietario),
                solicitudes.listarFichaPorPropietario(organizacion, idPropietario),
                contratos.listarFichaPorPropietario(organizacion, idPropietario));
        if (esBroker(contexto.actor()) && !historiaVisible(datos, contexto)) {
            throw new AccesoNoAutorizadoException();
        }
        return datos;
    }

    private boolean historiaVisible(DatosCliente datos, Contexto contexto) {
        return datos.oportunidades().stream().anyMatch(o -> visible(o, contexto))
                || datos.solicitudes().stream().anyMatch(s -> visible(s, contexto))
                || datos.visitas().stream().anyMatch(v -> visible(v, contexto))
                || datos.interacciones().stream().anyMatch(i -> visible(i, contexto))
                || datos.contratos().stream().anyMatch(c -> visible(c.getSolicitud(), contexto));
    }

    private boolean historiaVisible(DatosPropietario datos, Contexto contexto) {
        return datos.prospecciones().stream().anyMatch(p -> visible(p, contexto))
                || datos.captaciones().stream().anyMatch(c -> visible(c, contexto))
                || datos.oportunidades().stream().anyMatch(o -> visible(o, contexto))
                || datos.solicitudes().stream().anyMatch(s -> visible(s, contexto))
                || datos.contratos().stream().anyMatch(c -> visible(c.getSolicitud(), contexto));
    }

    private List<FilaFicha> filasCliente(DatosCliente datos, String clave, Contexto contexto) {
        List<FilaFicha> filas = switch (clave) {
            case "requerimientos" -> datos.requerimientos().stream()
                    .map(this::filaRequerimiento).sorted(orden()).toList();
            case "propiedades" -> filasPropiedadesCliente(datos, contexto);
            case "oportunidades" -> datos.oportunidades().stream()
                    .filter(o -> visible(o, contexto)).map(this::filaOportunidad)
                    .sorted(orden()).toList();
            case "interacciones" -> datos.interacciones().stream()
                    .filter(i -> visible(i, contexto)).map(this::filaInteraccion)
                    .sorted(orden()).toList();
            case "visitas" -> datos.visitas().stream()
                    .filter(v -> visible(v, contexto)).map(this::filaVisita)
                    .sorted(orden()).toList();
            case "solicitudes" -> datos.solicitudes().stream()
                    .filter(s -> visible(s, contexto)).map(this::filaSolicitud)
                    .sorted(orden()).toList();
            case "cierres" -> datos.contratos().stream()
                    .filter(c -> visible(c.getSolicitud(), contexto)).map(this::filaContrato)
                    .sorted(orden()).toList();
            case "agentes" -> filasAgentesCliente(datos, contexto);
            default -> throw new ReglaNegocioException("Seccion de ficha de cliente no valida.");
        };
        return privacidad(filas, clave, contexto);
    }

    private List<FilaFicha> filasPropietario(
            DatosPropietario datos, String clave, Contexto contexto) {
        List<FilaFicha> filas = switch (clave) {
            case "locales" -> filasLocalesPropietario(datos, contexto);
            case "prospecciones" -> datos.prospecciones().stream()
                    .filter(p -> visible(p, contexto)).map(this::filaProspeccion)
                    .sorted(orden()).toList();
            case "captaciones" -> {
                Map<Long, CierreCaptacion> cierres = cierresPorCaptacion(datos, contexto);
                yield datos.captaciones().stream()
                        .filter(c -> visible(c, contexto))
                        .map(c -> filaCaptacion(c, cierres.get(c.getId())))
                        .sorted(orden()).toList();
            }
            case "oportunidades" -> datos.oportunidades().stream()
                    .filter(o -> visible(o, contexto)).map(this::filaOportunidad)
                    .sorted(orden()).toList();
            case "solicitudes" -> datos.solicitudes().stream()
                    .filter(s -> visible(s, contexto)).map(this::filaSolicitud)
                    .sorted(orden()).toList();
            case "cierres" -> datos.contratos().stream()
                    .filter(c -> visible(c.getSolicitud(), contexto)).map(this::filaContrato)
                    .sorted(orden()).toList();
            case "agentes" -> filasAgentesPropietario(datos, contexto);
            default -> throw new ReglaNegocioException("Seccion de ficha de propietario no valida.");
        };
        return privacidad(filas, clave, contexto);
    }

    private List<FilaFicha> filasPropiedadesCliente(DatosCliente datos, Contexto contexto) {
        Map<Object, FilaFicha> filas = new LinkedHashMap<>();
        List<Propiedad> locales = new ArrayList<>();
        datos.oportunidades().stream().filter(o -> visible(o, contexto)).map(o -> local(o))
                .forEach(locales::add);
        datos.visitas().stream().filter(v -> visible(v, contexto)).map(v -> local(v))
                .forEach(locales::add);
        datos.solicitudes().stream().filter(s -> visible(s, contexto)).map(s -> local(s))
                .forEach(locales::add);
        Map<Long, ValoresDePropiedad> gobernados = gobernadosDe(locales);

        datos.oportunidades().stream().filter(o -> visible(o, contexto))
                .forEach(o -> putLocal(filas, gobernados, local(o), o.getCaptacion(), o.getAgente(),
                        "Propiedad mostrada", "oportunidad-detail/" + textoId(o.getId()),
                        local(o.getFechaRegistro())));
        datos.visitas().stream().filter(v -> visible(v, contexto))
                .forEach(v -> putLocal(filas, gobernados, local(v), captacion(v), v.getAgente(),
                        "Propiedad visitada",
                        v.getOportunidad() != null && v.getOportunidad().getId() != null
                                ? "oportunidad-detail/" + v.getOportunidad().getId() : "",
                        dia(v.getFechaVisita())));
        datos.solicitudes().stream().filter(s -> visible(s, contexto))
                .forEach(s -> putLocal(filas, gobernados, local(s), captacion(s), s.getAgente(),
                        "Solicitud enviada",
                        s.getCodigoSolicitud() != null ? "solicitud-detail/" + s.getCodigoSolicitud() : "",
                        dia(s.getFechaRegistro())));
        return filas.values().stream().sorted(orden()).toList();
    }

    /**
     * Los valores gobernados de todos los locales de la ficha, <b>en una sola
     * consulta</b>.
     *
     * <p>El rubro dejo de ser una columna del agregado en V71 y pasa a ser una
     * clave gobernada mas. Leerlo fila a fila seria un N+1 en una pantalla que
     * pinta una linea por hecho, que es exactamente lo que RC-003 vino a quitar;
     * asi que se hidrata en lote antes de construir ninguna fila.
     */
    private Map<Long, ValoresDePropiedad> gobernadosDe(Collection<Propiedad> locales) {
        List<Long> ids = locales.stream().filter(Objects::nonNull)
                .map(Propiedad::getId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : lector.gobernadosDeVarias(ids);
    }

    private List<FilaFicha> filasAgentesCliente(DatosCliente datos, Contexto contexto) {
        Map<Long, FilaFicha> agentes = new LinkedHashMap<>();
        datos.oportunidades().stream().filter(o -> visible(o, contexto))
                .forEach(o -> putAgente(agentes, o.getAgente(), "Oportunidad", local(o.getFechaRegistro())));
        datos.solicitudes().stream().filter(s -> visible(s, contexto))
                .forEach(s -> putAgente(agentes, s.getAgente(), "Solicitud", dia(s.getFechaRegistro())));
        datos.visitas().stream().filter(v -> visible(v, contexto))
                .forEach(v -> putAgente(agentes, v.getAgente(), "Visita", dia(v.getFechaVisita())));
        datos.interacciones().stream().filter(i -> visible(i, contexto))
                .forEach(i -> putAgente(agentes, agente(i), "Interaccion", local(i.getFechaHora())));
        return agentes.values().stream().sorted(orden()).toList();
    }

    private List<FilaFicha> filasLocalesPropietario(
            DatosPropietario datos, Contexto contexto) {
        Map<Object, FilaFicha> filas = new LinkedHashMap<>();
        Map<Long, CierreCaptacion> cierres = cierresPorCaptacion(datos, contexto);
        List<Propiedad> locales = new ArrayList<>();
        datos.captaciones().stream().filter(c -> visible(c, contexto))
                .forEach(c -> locales.add(c.getPropiedad()));
        datos.prospecciones().stream().filter(p -> visible(p, contexto))
                .forEach(p -> locales.add(p.getPropiedad()));
        Map<Long, ValoresDePropiedad> gobernados = gobernadosDe(locales);

        datos.captaciones().stream().filter(c -> visible(c, contexto))
                .forEach(c -> putLocalCaptacion(filas, gobernados, c, cierres.get(c.getId())));
        datos.prospecciones().stream().filter(p -> visible(p, contexto))
                .forEach(p -> putLocal(filas, gobernados, p.getPropiedad(), p.getCaptacion(),
                        p.getAgente(), "Local en prospeccion", rutaLocal(p.getPropiedad()),
                        local(p.getFechaRegistro())));
        return filas.values().stream().sorted(orden()).toList();
    }

    private List<FilaFicha> filasAgentesPropietario(
            DatosPropietario datos, Contexto contexto) {
        Map<Long, FilaFicha> agentes = new LinkedHashMap<>();
        datos.prospecciones().stream().filter(p -> visible(p, contexto))
                .forEach(p -> putAgente(agentes, p.getAgente(), "Prospeccion", local(p.getFechaRegistro())));
        datos.captaciones().stream().filter(c -> visible(c, contexto))
                .forEach(c -> putAgente(agentes, c.getAgente(), "Captacion", dia(c.getFechaCaptacion())));
        datos.oportunidades().stream().filter(o -> visible(o, contexto))
                .forEach(o -> putAgente(agentes, o.getAgente(), "Oportunidad", local(o.getFechaRegistro())));
        datos.solicitudes().stream().filter(s -> visible(s, contexto))
                .forEach(s -> putAgente(agentes, s.getAgente(), "Solicitud", dia(s.getFechaRegistro())));
        return agentes.values().stream().sorted(orden()).toList();
    }

    private Map<Long, CierreCaptacion> cierresPorCaptacion(
            DatosPropietario datos, Contexto contexto) {
        Map<Long, SolicitudAlquiler> solicitudesVisibles = datos.solicitudes().stream()
                .filter(s -> visible(s, contexto))
                .collect(Collectors.toMap(SolicitudAlquiler::getId, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
        Map<Long, CierreCaptacion> cierres = new LinkedHashMap<>();
        datos.contratos().stream()
                .filter(c -> c.getSolicitud() != null
                        && solicitudesVisibles.containsKey(c.getSolicitud().getId()))
                .sorted(Comparator
                        .comparing((ContratoAlquiler c) -> dia(c.getFechaCierre()),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .forEach(c -> {
                    SolicitudAlquiler solicitud = solicitudesVisibles.get(c.getSolicitud().getId());
                    Captacion captacion = captacion(solicitud);
                    if (captacion != null && captacion.getId() != null) {
                        cierres.putIfAbsent(captacion.getId(),
                                new CierreCaptacion(solicitud, c.getFechaCierre()));
                    }
                });
        return cierres;
    }

    private FilaFicha filaRequerimiento(RequerimientoCliente r) {
        DetalleCliente cliente = r.getCliente();
        return new FilaFicha(
                textoId(r.getId()), texto("REQ-" + textoId(r.getId())),
                "Requerimiento", texto(r.getRubro(), "Perfil de busqueda"),
                requerimientoResumen(r), "-", distritos(r),
                nombre(cliente), cliente != null ? cliente.getId() : null,
                "-", null, "-", estadoRequerimiento(r.getEstado()),
                fecha(local(r.getFechaActualizacion()), local(r.getFechaCreacion())),
                cliente != null && cliente.getId() != null ? "cliente-detail/" + cliente.getId() : "",
                "target", r.estaActivo() ? "green" : "gray",
                primera(local(r.getFechaActualizacion()), local(r.getFechaCreacion())));
    }

    private FilaFicha filaProspeccion(Prospeccion p) {
        LocalDateTime fecha = primera(
                primeraDia(p.getFechaPropuesta(), p.getFechaReunion(), p.getFechaContacto()),
                local(p.getFechaRegistro()));
        return filaBase(textoId(p.getId()), texto(p.getCodigoProspeccion(), codigoLocal(p.getPropiedad())),
                "Prospeccion", "Seguimiento del propietario", hitoProspeccion(p),
                p.getPropiedad(), null, p.getAgente(), estadoProspeccion(p.estadoActual()),
                fecha(fecha), rutaLocal(p.getPropiedad()), "store", "blue", fecha);
    }

    private FilaFicha filaCaptacion(Captacion c, CierreCaptacion cierre) {
        DetalleCliente cliente = cierre != null ? cliente(cierre.solicitud()) : null;
        String clienteCierre = nombre(cliente);
        String subtitulo = cierre != null
                ? ("-".equals(clienteCierre) ? "Alquiler cerrado" : "Alquilada a " + clienteCierre)
                : vigencia(c);
        return filaBase(textoId(c.getId()), texto(c.getCodigoCaptacion()), "Captacion",
                "Expediente de captacion", subtitulo, c.getPropiedad(), cliente, c.getAgente(),
                cierre != null ? "Cerrada" : estadoCaptacion(c.estadoActual()),
                fecha(c.getFechaCaptacion()), rutaCaptacion(c), "pin",
                cierre != null ? "green" : "blue",
                primeraDia(c.getFechaCaptacion(), c.getFechaInicioVigencia()));
    }

    private FilaFicha filaOportunidad(OportunidadComercial o) {
        DetalleCliente cliente = o.getCliente();
        return filaBase(textoId(o.getId()), texto(o.getCodigoOportunidad()), "Oportunidad",
                nombre(cliente), texto(o.getObservaciones(), "Seguimiento comercial"),
                local(o), cliente, o.getAgente(), estadoOportunidad(o.estadoActual()),
                fecha(local(o.getFechaActualizacion()), local(o.getFechaRegistro())),
                o.getId() != null ? "oportunidad-detail/" + o.getId() : "",
                "target", "info",
                primera(local(o.getFechaActualizacion()), local(o.getFechaRegistro())));
    }

    private FilaFicha filaSolicitud(SolicitudAlquiler s) {
        DetalleCliente cliente = cliente(s);
        LocalDateTime actualizacion = local(s.getFechaActualizacionEstado());
        LocalDateTime registro = dia(s.getFechaRegistro());
        return filaBase(textoId(s.getId()), texto(s.getCodigoSolicitud()), "Solicitud",
                nombre(cliente),
                s.getMontoPropuesto() != null
                        ? "Oferta " + codigoMoneda(s.getMoneda()) + " "
                                + s.getMontoPropuesto().toPlainString()
                        : "Solicitud de alquiler",
                local(s), cliente, s.getAgente(), estadoSolicitud(s.estadoActual()),
                fecha(actualizacion, registro),
                s.getCodigoSolicitud() != null ? "solicitud-detail/" + s.getCodigoSolicitud() : "",
                "fileText", "gray", primera(actualizacion, registro));
    }

    private FilaFicha filaInteraccion(InteraccionComercial i) {
        OportunidadComercial oportunidad = i.getOportunidad();
        DetalleCliente cliente = cliente(i);
        LocalDateTime fecha = local(i.getFechaHora());
        return filaBase(textoId(i.getId()),
                oportunidad != null ? texto(oportunidad.getCodigoOportunidad()) : textoId(i.getId()),
                "Interaccion", texto(canal(i.getCanalContacto()), "Contacto comercial"),
                texto(resultado(i.getResultado()), i.getObservaciones()),
                local(oportunidad), cliente, agente(i), resultado(i.getResultado()),
                fecha(fecha), i.getId() != null ? "interaccion-detail/" + i.getId() : "",
                "activity", "blue", fecha);
    }

    private FilaFicha filaVisita(Visita v) {
        DetalleCliente cliente = cliente(v);
        LocalDateTime fecha = dia(v.getFechaVisita());
        return filaBase(textoId(v.getId()),
                texto(v.getOportunidad() != null ? v.getOportunidad().getCodigoOportunidad() : null,
                        "VIS-" + textoId(v.getId())),
                "Visita", "Visita comercial",
                texto(v.getObservaciones(), resultado(v.getResultado())),
                local(v), cliente, v.getAgente(), estadoVisita(v.estadoActual()),
                fecha(fecha),
                v.getOportunidad() != null && v.getOportunidad().getId() != null
                        ? "oportunidad-detail/" + v.getOportunidad().getId() : "",
                "calendar", "blue", fecha);
    }

    private FilaFicha filaContrato(ContratoAlquiler c) {
        SolicitudAlquiler solicitud = c.getSolicitud();
        DetalleCliente cliente = cliente(solicitud);
        String estadoContrato = estadoContrato(c.estadoActual());
        String clienteCierre = nombre(cliente);
        return filaBase(textoId(c.getId()),
                texto(solicitud != null ? solicitud.getCodigoSolicitud() : null,
                        c.getOportunidad() != null ? c.getOportunidad().getCodigoOportunidad() : null),
                "Cierre", "Alquiler cerrado",
                "-".equals(clienteCierre)
                        ? "Contrato " + estadoContrato.toLowerCase(Locale.ROOT) + "."
                        : clienteCierre + " alquilo este local. Contrato "
                                + estadoContrato.toLowerCase(Locale.ROOT) + ".",
                local(solicitud), cliente, solicitud != null ? solicitud.getAgente() : null,
                "Alquiler cerrado", fecha(c.getFechaCierre()),
                solicitud != null && solicitud.getCodigoSolicitud() != null
                        ? "solicitud-detail/" + solicitud.getCodigoSolicitud() : "",
                "checkCircle", "green", dia(c.getFechaCierre()));
    }

    private FilaFicha filaBase(
            String id, String codigo, String proceso, String titulo, String subtitulo,
            Propiedad local, DetalleCliente cliente, DetalleAgente agente, String estado,
            String fecha, String ruta, String icono, String tono, LocalDateTime fechaOrden) {
        return new FilaFicha(id, codigo, proceso, texto(titulo), texto(subtitulo),
                direccion(local), distrito(local), nombre(cliente),
                cliente != null ? cliente.getId() : null,
                propietario(local), propietarioId(local), nombre(agente),
                texto(estado), texto(fecha), texto(ruta, ""), icono, tono, fechaOrden);
    }

    private void putLocal(Map<Object, FilaFicha> filas, Map<Long, ValoresDePropiedad> gobernados,
                          Propiedad local, Captacion captacion,
                          DetalleAgente agente, String titulo, String ruta,
                          LocalDateTime fechaOrden) {
        if (local == null) {
            return;
        }
        Object clave = local.getId() != null ? local.getId() : direccion(local);
        filas.putIfAbsent(clave, filaBase(
                textoId(local.getId()),
                texto(captacion != null ? captacion.getCodigoCaptacion() : null, codigoLocal(local)),
                "Propiedad", titulo, texto(rubro(local, gobernados), local.getDescripcion()),
                local, null, agente, estadoLocal(local.estadoLegado()), fecha(fechaOrden),
                texto(ruta, rutaLocal(local)), "store", "blue", fechaOrden));
    }

    private void putLocalCaptacion(Map<Object, FilaFicha> filas,
            Map<Long, ValoresDePropiedad> gobernados,
            Captacion captacion, CierreCaptacion cierre) {
        Propiedad local = captacion != null ? captacion.getPropiedad() : null;
        if (local == null || local.getId() == null) {
            return;
        }
        DetalleCliente cliente = cierre != null ? cliente(cierre.solicitud()) : null;
        String clienteNombre = nombre(cliente);
        boolean alquilada = cierre != null;
        LocalDateTime fechaCierre = cierre != null ? dia(cierre.fechaCierre()) : null;
        filas.putIfAbsent(local.getId(), filaBase(
                textoId(local.getId()), texto(captacion.getCodigoCaptacion(), codigoLocal(local)),
                "Propiedad", alquilada ? "Alquiler cerrado" : "Local captado",
                alquilada && !"-".equals(clienteNombre)
                        ? "Alquilada a " + clienteNombre
                        : texto(rubro(local, gobernados), local.getDescripcion()),
                local, cliente, captacion.getAgente(),
                alquilada ? "Alquilada" : estadoLocal(local.estadoLegado()),
                fecha(alquilada && fechaCierre != null ? fechaCierre : dia(captacion.getFechaCaptacion())),
                texto(rutaLocal(local)), "store", alquilada ? "green" : "blue",
                alquilada
                        ? primera(fechaCierre, dia(captacion.getFechaCaptacion()))
                        : dia(captacion.getFechaCaptacion())));
    }

    private void putAgente(Map<Long, FilaFicha> agentes, DetalleAgente agente,
                           String proceso, LocalDateTime fechaOrden) {
        if (agente == null || agente.getId() == null) {
            return;
        }
        agentes.putIfAbsent(agente.getId(), new FilaFicha(
                textoId(agente.getId()), texto(agente.getCodigoAgente()), "Agente",
                nombre(agente), "Vinculado por " + proceso, "-", "-", "-", null,
                "-", null, nombre(agente), estadoAgente(agente.getEstadoOperativo()),
                fecha(fechaOrden), "", "user", "gray", fechaOrden));
    }

    private List<FilaFicha> privacidad(
            List<FilaFicha> filas, String seccion, Contexto contexto) {
        if (!contexto.actor().esAgente()) {
            return filas;
        }
        if ("agentes".equals(seccion)) {
            return List.of();
        }
        return filas.stream().map(row -> new FilaFicha(
                row.id(), row.codigo(), row.proceso(), row.titulo(), row.subtitulo(),
                row.local(), row.distrito(), row.cliente(), row.clienteId(),
                row.propietario(), row.propietarioId(), "-", row.estado(), row.fecha(),
                row.ruta(), row.icono(), row.tono(), row.fechaOrden())).toList();
    }

    private Contexto contexto(Actor actor) {
        Set<Long> agentes = new LinkedHashSet<>(alcances.de(actor).rolesAgente());
        return new Contexto(actor, agentes);
    }

    private boolean visible(Prospeccion p, Contexto contexto) {
        return visible(p != null ? p.getAgente() : null, contexto);
    }

    private boolean visible(Captacion c, Contexto contexto) {
        if (contexto.actor().esTenantAdmin()) {
            return true;
        }
        return c != null && (visible(c.getAgente(), contexto)
                || (esBroker(contexto.actor()) && c.getBrokerRevisor() != null
                        && Objects.equals(c.getBrokerRevisor().getId(),
                                contexto.actor().idRolOperativo())));
    }

    private boolean visible(OportunidadComercial o, Contexto contexto) {
        return o != null && (visible(o.getAgente(), contexto) || visible(o.getCaptacion(), contexto));
    }

    private boolean visible(SolicitudAlquiler s, Contexto contexto) {
        return s != null && (visible(s.getAgente(), contexto) || visible(captacion(s), contexto));
    }

    private boolean visible(Visita v, Contexto contexto) {
        return v != null && (visible(v.getAgente(), contexto) || visible(captacion(v), contexto));
    }

    private boolean visible(InteraccionComercial i, Contexto contexto) {
        return i != null && (i.getOportunidad() != null
                ? visible(i.getOportunidad(), contexto)
                : visible(i.getAgente(), contexto));
    }

    private boolean visible(DetalleAgente agente, Contexto contexto) {
        if (contexto.actor().esTenantAdmin()) {
            return true;
        }
        return agente != null && agente.getId() != null
                && contexto.agentesVisibles().contains(agente.getId());
    }

    private static boolean esBroker(Actor actor) {
        return !actor.esTenantAdmin() && !actor.esAgente();
    }

    private static SeccionFicha pagina(
            String seccion, List<FilaFicha> filas, int pagina, int tamano) {
        int paginaValida = Math.max(1, pagina);
        int tamanoValido = tamano(tamano);
        long inicio = Math.min((long) (paginaValida - 1) * tamanoValido, filas.size());
        int desde = Math.toIntExact(inicio);
        int hasta = Math.min(desde + tamanoValido, filas.size());
        return new SeccionFicha(
                seccion, filas.size(), paginaValida, tamanoValido, filas.subList(desde, hasta));
    }

    private static SeccionFicha resumen(
            String seccion, List<FilaFicha> filas, int tamano) {
        return new SeccionFicha(seccion, filas.size(), 1, tamano(tamano), List.of());
    }

    private static SeccionFicha pendiente(String seccion, int tamano) {
        return new SeccionFicha(seccion, -1, 0, tamano(tamano), List.of());
    }

    private static Comparator<FilaFicha> orden() {
        return Comparator
                .comparing(FilaFicha::fechaOrden,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(FilaFicha::proceso)
                .thenComparing(FilaFicha::codigo);
    }

    private static int tamano(int valor) {
        return Math.max(1, Math.min(TAMANO_POR_DEFECTO, valor));
    }

    private static String normal(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private static ClienteService.FichaCliente ficha(DetalleCliente cliente) {
        Persona persona = cliente.getRol() != null ? cliente.getRol().getPersona() : null;
        return new ClienteService.FichaCliente(
                cliente.getId(),
                persona != null ? persona.getTipoPersona() : null,
                persona != null ? persona.getTipoDocumento() : null,
                persona != null ? persona.getNumeroDocumento() : null,
                persona != null ? persona.getNombresORazonSocial() : null,
                persona != null ? persona.getTelefono() : null,
                persona != null ? persona.getCorreo() : null,
                cliente.getRubroComercial(),
                persona != null ? persona.getEstado() : null,
                cliente.getConsentimientoContacto(),
                persona != null ? persona.getConsentimientoUsoDato() : null,
                persona != null ? Fechas.local(persona.getFechaCreacion()) : null);
    }

    private static PropietarioService.FichaPropietario ficha(PersonaRol rol, int cantidadLocales) {
        Persona persona = rol.getPersona();
        return new PropietarioService.FichaPropietario(
                rol.getId(),
                persona != null ? persona.getTipoPersona() : null,
                persona != null ? persona.getTipoDocumento() : null,
                persona != null ? persona.getNumeroDocumento() : null,
                persona != null ? persona.getNombresORazonSocial() : null,
                persona != null ? persona.getTelefono() : null,
                persona != null ? persona.getCorreo() : null,
                persona != null ? persona.getEstado() : null,
                persona != null ? persona.getConsentimientoUsoDato() : null,
                persona != null ? Fechas.local(persona.getFechaCreacion()) : null,
                Math.max(0, cantidadLocales));
    }

    private static String requerimientoResumen(RequerimientoCliente r) {
        List<String> partes = new ArrayList<>();
        if (r.getTipoInmueble() != null) {
            partes.add(r.getTipoInmueble().replace('_', ' '));
        }
        if (r.getRentaMin() != null || r.getRentaMax() != null) {
            partes.add("Renta " + rango(r.getRentaMin(), r.getRentaMax(), moneda(r.getMoneda())));
        }
        if (r.getMetrajeMin() != null || r.getMetrajeMax() != null) {
            partes.add("Area " + rango(r.getMetrajeMin(), r.getMetrajeMax(), "m2"));
        }
        if (r.getFrenteMinimo() != null) {
            partes.add("Frente desde " + numero(r.getFrenteMinimo()) + " m");
        }
        if (partes.isEmpty() && r.getObservaciones() != null) {
            partes.add(r.getObservaciones());
        }
        return partes.isEmpty() ? "Sin parametros registrados" : String.join(" | ", partes);
    }

    private static String rango(BigDecimal minimo, BigDecimal maximo, String unidad) {
        if (minimo != null && maximo != null) {
            return numero(minimo) + "-" + numero(maximo) + " " + unidad;
        }
        if (minimo != null) {
            return "desde " + numero(minimo) + " " + unidad;
        }
        return "hasta " + numero(maximo) + " " + unidad;
    }

    private static String distritos(RequerimientoCliente r) {
        return r.getDistritos() == null || r.getDistritos().isEmpty()
                ? "-"
                : r.getDistritos().stream().map(Distrito::getNombre)
                        .filter(Objects::nonNull).collect(Collectors.joining(", "));
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

    private static Propiedad local(OportunidadComercial oportunidad) {
        Captacion captacion = oportunidad != null ? oportunidad.getCaptacion() : null;
        return captacion != null ? captacion.getPropiedad() : null;
    }

    private static Propiedad local(SolicitudAlquiler solicitud) {
        return local(solicitud != null ? solicitud.getOportunidad() : null);
    }

    private static Propiedad local(Visita visita) {
        return local(visita != null ? visita.getOportunidad() : null);
    }

    private static Captacion captacion(SolicitudAlquiler solicitud) {
        OportunidadComercial oportunidad = solicitud != null ? solicitud.getOportunidad() : null;
        return oportunidad != null ? oportunidad.getCaptacion() : null;
    }

    private static Captacion captacion(Visita visita) {
        return visita != null ? captacionDesde(visita.getOportunidad()) : null;
    }

    private static Captacion captacionDesde(OportunidadComercial oportunidad) {
        return oportunidad != null ? oportunidad.getCaptacion() : null;
    }

    private static DetalleCliente cliente(SolicitudAlquiler solicitud) {
        OportunidadComercial oportunidad = solicitud != null ? solicitud.getOportunidad() : null;
        return oportunidad != null ? oportunidad.getCliente() : null;
    }

    private static DetalleCliente cliente(Visita visita) {
        OportunidadComercial oportunidad = visita != null ? visita.getOportunidad() : null;
        return oportunidad != null ? oportunidad.getCliente() : null;
    }

    private static DetalleCliente cliente(InteraccionComercial interaccion) {
        if (interaccion == null) {
            return null;
        }
        return interaccion.getCliente() != null
                ? interaccion.getCliente()
                : interaccion.getOportunidad() != null
                        ? interaccion.getOportunidad().getCliente() : null;
    }

    private static DetalleAgente agente(InteraccionComercial interaccion) {
        if (interaccion == null) {
            return null;
        }
        return interaccion.getAgente() != null
                ? interaccion.getAgente()
                : interaccion.getOportunidad() != null
                        ? interaccion.getOportunidad().getAgente() : null;
    }

    private static String texto(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return "-";
    }

    private static String textoId(Long valor) {
        return valor != null ? valor.toString() : "";
    }

    private static String numero(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }

    private static LocalDateTime local(OffsetDateTime fecha) {
        return Fechas.local(fecha);
    }

    private static LocalDateTime dia(LocalDate fecha) {
        return fecha != null ? fecha.atStartOfDay() : null;
    }

    private static LocalDateTime primera(LocalDateTime... fechas) {
        for (LocalDateTime fecha : fechas) {
            if (fecha != null) {
                return fecha;
            }
        }
        return null;
    }

    private static LocalDateTime primeraDia(LocalDate... fechas) {
        for (LocalDate fecha : fechas) {
            if (fecha != null) {
                return fecha.atStartOfDay();
            }
        }
        return null;
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

    private static String direccion(Propiedad propiedad) {
        return propiedad != null ? texto(propiedad.getDireccion()) : "-";
    }

    private static String distrito(Propiedad propiedad) {
        return propiedad != null ? texto(propiedad.getDistrito()) : "-";
    }

    private static String codigoLocal(Propiedad propiedad) {
        return propiedad != null ? texto(propiedad.getCodigo()) : "-";
    }

    /**
     * El rubro, leido por su AUTORIDAD y no por una columna.
     *
     * <p>Hasta V71 salia de `detalle_local_comercial`. Ahora es una clave
     * gobernada como las demas, y este metodo no sabe donde vive: pide por
     * clave logica al lote ya hidratado.
     */
    private static String rubro(Propiedad propiedad, Map<Long, ValoresDePropiedad> gobernados) {
        if (propiedad == null || propiedad.getId() == null) {
            return null;
        }
        return LectorPorAutoridad.de(gobernados, propiedad.getId())
                .texto(CatalogoAtributo.CLAVE_RUBRO_PERMITIDO);
    }

    private static String propietario(Propiedad propiedad) {
        PersonaRol rol = propiedad != null ? propiedad.getRolPropietario() : null;
        return rol != null ? nombre(rol.getPersona()) : "-";
    }

    private static Long propietarioId(Propiedad propiedad) {
        PersonaRol rol = propiedad != null ? propiedad.getRolPropietario() : null;
        return rol != null ? rol.getId() : null;
    }

    private static String nombre(DetalleCliente cliente) {
        return cliente != null && cliente.getRol() != null
                ? nombre(cliente.getRol().getPersona()) : "-";
    }

    private static String nombre(DetalleAgente agente) {
        return agente != null && agente.getRol() != null
                ? nombre(agente.getRol().getPersona()) : "-";
    }

    private static String nombre(Persona persona) {
        return persona != null ? texto(persona.getNombresORazonSocial()) : "-";
    }

    private static String rutaLocal(Propiedad propiedad) {
        return propiedad != null && propiedad.getId() != null
                ? "local-detail/" + propiedad.getId() : "";
    }

    private static String rutaCaptacion(Captacion captacion) {
        return captacion != null && captacion.getCodigoCaptacion() != null
                ? "captacion-detail/" + captacion.getCodigoCaptacion() : "";
    }

    private static String moneda(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return "Moneda no definida";
        }
        return switch (codigo.toUpperCase(Locale.ROOT)) {
            case "PEN" -> "Soles";
            case "USD" -> "Dolares";
            default -> codigo;
        };
    }

    private static String codigoMoneda(String codigo) {
        return codigo == null || codigo.isBlank() ? "Moneda no definida" : codigo;
    }

    private static String estadoRequerimiento(String codigo) {
        return codigo != null ? codigo : "-";
    }

    private static String estadoProspeccion(String codigo) {
        return switch (texto(codigo)) {
            case "P" -> "Prospecto";
            case "C" -> "Contactado";
            case "R" -> "Reunion";
            case "E" -> "Propuesta entregada";
            case "S" -> "En seguimiento";
            case "T" -> "Captado";
            case "D" -> "Descartado";
            default -> texto(codigo);
        };
    }

    private static String estadoCaptacion(String codigo) {
        return switch (texto(codigo)) {
            case "P" -> "Pendiente de revision";
            case "O" -> "Observada";
            case "R" -> "Rechazada";
            case "A" -> "Activa";
            case "C" -> "Cerrada";
            case "V" -> "Vencida";
            default -> texto(codigo);
        };
    }

    private static String estadoOportunidad(String codigo) {
        return switch (texto(codigo)) {
            case "A" -> "Abierta";
            case "S" -> "Solicitud creada";
            case "N" -> "No continua";
            case "F" -> "Finalizada exitosa";
            case "X" -> "Finalizada no favorable";
            default -> texto(codigo);
        };
    }

    private static String estadoSolicitud(String codigo) {
        return switch (texto(codigo)) {
            case "G" -> "Registrada";
            case "E" -> "En revision";
            case "O" -> "Observada";
            case "A" -> "Aprobada";
            case "R" -> "Rechazada";
            case "D" -> "Desistida";
            case "C" -> "Cerrada";
            default -> texto(codigo);
        };
    }

    private static String estadoVisita(String codigo) {
        return switch (texto(codigo)) {
            case "P" -> "Programada";
            case "G" -> "Reprogramada";
            case "C" -> "Cancelada";
            case "N" -> "No realizada";
            case "R" -> "Realizada";
            default -> texto(codigo);
        };
    }

    private static String estadoContrato(String codigo) {
        return switch (texto(codigo, "V")) {
            case "P" -> "En proceso";
            case "D" -> "Firmado";
            case "V" -> "Vigente";
            case "R" -> "Renovado";
            case "F" -> "Finalizado";
            case "S" -> "Rescindido";
            case "A" -> "Anulado";
            default -> texto(codigo, "Vigente");
        };
    }

    /**
     * Traduce la proyeccion LEGADA {@code D/N/I}, no {@code estado_registro}.
     *
     * <p>Los dos call sites le pasaban {@code local.estadoActual()}, que es
     * {@code estado_registro} ({@code A}/{@code I}): la {@code A} no casaba con
     * ningun caso, caia por el {@code default} y llegaba a la ficha del
     * propietario tal cual —una columna ESTADO con una letra suelta—. La
     * proyeccion que este metodo espera la da {@code estadoLegado()}, que
     * combina registro y disponibilidad.
     *
     * <p>Es la misma confusion de dos vocabularios sobre la propiedad que ya
     * aparecio en {@code LocalComercialServiceImpl}: por eso las constantes se
     * renombraron a {@code LEGADO_*}.
     */
    private static String estadoLocal(String codigo) {
        return switch (texto(codigo)) {
            case "D" -> "Disponible";
            case "N" -> "No disponible";
            case "I" -> "Inactivo";
            default -> texto(codigo);
        };
    }

    private static String estadoAgente(String codigo) {
        return switch (texto(codigo)) {
            case "D" -> "Disponible";
            case "L" -> "Licencia";
            case "N" -> "No disponible";
            default -> texto(codigo);
        };
    }

    private static String canal(String codigo) {
        return switch (texto(codigo)) {
            case "L" -> "Llamada";
            case "W" -> "WhatsApp";
            case "E" -> "Email";
            case "P" -> "Presencial";
            case "R" -> "Reunion";
            case "T" -> "Portal";
            case "O" -> "Otro";
            default -> texto(codigo);
        };
    }

    private static String resultado(String codigo) {
        return switch (texto(codigo)) {
            case "P" -> "Pendiente";
            case "I", "INTERESADO" -> "Interesado";
            case "N", "NO_INTERESADO" -> "No interesado";
            case "S", "SEGUIMIENTO" -> "Seguimiento";
            case "D", "DESCARTADO" -> "Descartado";
            case "CONTACTADO" -> "Contactado";
            case "REUNION_AGENDADA" -> "Reunion agendada";
            case "PROPUESTA_ENVIADA" -> "Propuesta enviada";
            case "ACEPTA_CAPTAR" -> "Acepta captar";
            case "NO_ACEPTA" -> "No acepta";
            case "RECONTACTAR" -> "Recontactar";
            case "DOCS_SOLICITADOS" -> "Documentos solicitados";
            case "CONDICIONES_AJUSTADAS" -> "Condiciones ajustadas";
            case "PUBLICACION_COORDINADA" -> "Publicacion coordinada";
            case "PROPIETARIO_OBSERVA" -> "Propietario observa";
            case "LISTO_PARA_PUBLICAR" -> "Listo para publicar";
            case "PAUSAR_GESTION" -> "Pausar gestion";
            case "VISITA_AGENDADA" -> "Visita agendada";
            case "OFERTA_SOLICITADA" -> "Oferta solicitada";
            case "NEGOCIANDO" -> "Negociando";
            case "BUSQUEDA_LEVANTADA" -> "Busqueda levantada";
            case "REQUIERE_OPCIONES" -> "Requiere opciones";
            case "NO_RESPONDE" -> "No responde";
            default -> texto(codigo);
        };
    }

    private record Contexto(Actor actor, Set<Long> agentesVisibles) {
    }

    private record DatosCliente(
            DetalleCliente cliente,
            List<RequerimientoCliente> requerimientos,
            List<OportunidadComercial> oportunidades,
            List<InteraccionComercial> interacciones,
            List<Visita> visitas,
            List<SolicitudAlquiler> solicitudes,
            List<ContratoAlquiler> contratos) {
    }

    private record DatosPropietario(
            PersonaRol propietario,
            List<Prospeccion> prospecciones,
            List<Captacion> captaciones,
            List<OportunidadComercial> oportunidades,
            List<SolicitudAlquiler> solicitudes,
            List<ContratoAlquiler> contratos) {
    }

    private record CierreCaptacion(SolicitudAlquiler solicitud, LocalDate fechaCierre) {
    }
}
