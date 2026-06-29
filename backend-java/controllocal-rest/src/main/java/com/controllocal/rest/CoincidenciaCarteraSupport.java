package com.controllocal.rest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.ClienteInteresadoBusinessLogic;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.ProspeccionBusinessLogic;
import com.controllocal.bl.RequerimientoClienteBusinessLogic;
import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.bl.VisitaBusinessLogic;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.ClienteInteresadoBusinessLogicImpl;
import com.controllocal.bl.impl.OportunidadComercialBusinessLogicImpl;
import com.controllocal.bl.impl.ProspeccionBusinessLogicImpl;
import com.controllocal.bl.impl.RequerimientoClienteBusinessLogicImpl;
import com.controllocal.bl.impl.SolicitudAlquilerBusinessLogicImpl;
import com.controllocal.bl.impl.VisitaBusinessLogicImpl;
import com.controllocal.bl.support.CoincidenciaCartera;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.comercial.RequerimientoCliente;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.comercial.enums.EstadoCaptacion;
import com.controllocal.model.comercial.enums.EstadoRequerimiento;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.inmueble.Distrito;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.inmueble.enums.EstadoLocalComercial;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.BrokerAgente;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

/**
 * Recomendacion de cartera (Etapa 8): cruza la demanda (requerimientos activos de clientes)
 * con la oferta (captaciones activas sobre locales disponibles) en ambos sentidos, con puntaje
 * y explicacion por criterio. El scoring vive en {@link CoincidenciaCartera} (capa BL) y aqui
 * se aplican la visibilidad por rol ("vista personal") y el shaping a DTO, igual que
 * {@link FichaComercialSupport}.
 */
public final class CoincidenciaCarteraSupport {

    private static final int DEFAULT_PAGE_SIZE = 6;
    private static final int MAX_PAGE_SIZE = 24;

    private final ClienteInteresadoBusinessLogic clientes = new ClienteInteresadoBusinessLogicImpl();
    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();
    private final ProspeccionBusinessLogic prospecciones = new ProspeccionBusinessLogicImpl();
    private final RequerimientoClienteBusinessLogic requerimientos = new RequerimientoClienteBusinessLogicImpl();
    private final OportunidadComercialBusinessLogic oportunidades = new OportunidadComercialBusinessLogicImpl();
    private final SolicitudAlquilerBusinessLogic solicitudes = new SolicitudAlquilerBusinessLogicImpl();
    private final VisitaBusinessLogic visitas = new VisitaBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();

    // ====================================================================
    // API publica
    // ====================================================================

    /** Cliente -> propiedades: captaciones activas (propias) con local disponible compatibles. */
    public CoincidenciasResponse propiedadesParaCliente(long idCliente, UsuarioAutenticado usuario) {
        return propiedadesParaCliente(idCliente, usuario, 1, DEFAULT_PAGE_SIZE);
    }

    /** Cliente -> propiedades: captaciones activas (propias) con local disponible compatibles. */
    public CoincidenciasResponse propiedadesParaCliente(long idCliente, UsuarioAutenticado usuario, int page, int pageSize) {
        clienteConAcceso(idCliente, usuario);
        List<RequerimientoCliente> activos = requerimientos.listarPorCliente(idCliente).stream()
                .filter(r -> r.getEstado() == EstadoRequerimiento.ACTIVO)
                .toList();
        List<CoincidenciaResponse> items = new ArrayList<>();
        if (!activos.isEmpty()) {
            for (Captacion captacion : captacionesCandidatas(usuario)) {
                LocalComercial local = captacion.getLocalComercial();
                if (local == null || local.getEstado() != EstadoLocalComercial.DISPONIBLE) {
                    continue;
                }
                CoincidenciaCartera.Evaluacion mejor = null;
                for (RequerimientoCliente r : activos) {
                    CoincidenciaCartera.Evaluacion e = CoincidenciaCartera.evaluar(r, local);
                    if (mejor == null || e.puntaje() > mejor.puntaje()) {
                        mejor = e;
                    }
                }
                if (mejor != null && mejor.puntaje() > 0) {
                    items.add(filaPropiedad(captacion, local, mejor, idCliente));
                }
            }
        }
        return armar("cliente:" + idCliente, items, page, pageSize);
    }

    /** Captacion -> clientes: demanda propia con requerimiento activo compatible. Accionable (Proponer). */
    public CoincidenciasResponse clientesParaCaptacion(String idOrCodigo, UsuarioAutenticado usuario) {
        return clientesParaCaptacion(idOrCodigo, usuario, 1, DEFAULT_PAGE_SIZE);
    }

    /** Captacion -> clientes: demanda propia con requerimiento activo compatible. Accionable (Proponer). */
    public CoincidenciasResponse clientesParaCaptacion(
            String idOrCodigo, UsuarioAutenticado usuario, int page, int pageSize) {
        Captacion captacion = captacionConAcceso(idOrCodigo, usuario);
        List<CoincidenciaResponse> items =
                clientesCompatibles(captacion.getLocalComercial(), usuario, captacion.getIdCaptacion());
        return armar("captacion:" + nz(captacion.getCodigoCaptacion()), items, page, pageSize);
    }

    /** Prospeccion -> clientes: senal de demanda temprana. Solo accionable si la prospeccion ya tiene captacion. */
    public CoincidenciasResponse clientesParaProspeccion(long idProspeccion, UsuarioAutenticado usuario) {
        return clientesParaProspeccion(idProspeccion, usuario, 1, DEFAULT_PAGE_SIZE);
    }

    /** Prospeccion -> clientes: senal de demanda temprana. Solo accionable si la prospeccion ya tiene captacion. */
    public CoincidenciasResponse clientesParaProspeccion(
            long idProspeccion, UsuarioAutenticado usuario, int page, int pageSize) {
        Prospeccion prospeccion = prospeccionConAcceso(idProspeccion, usuario);
        Long idCaptacion = prospeccion.getCaptacion() != null
                ? prospeccion.getCaptacion().getIdCaptacion()
                : null;
        List<CoincidenciaResponse> items =
                clientesCompatibles(prospeccion.getLocalComercial(), usuario, idCaptacion);
        return armar("prospeccion:" + idProspeccion, items, page, pageSize);
    }

    // ====================================================================
    // Construccion de coincidencias
    // ====================================================================

    private List<CoincidenciaResponse> clientesCompatibles(
            LocalComercial local, UsuarioAutenticado usuario, Long idCaptacion) {
        if (local == null) {
            return List.of();
        }
        Contexto contexto = contexto(usuario);
        Set<Long> idsPermitidos = idsClientesDelUsuario(contexto); // null = todos (ADMIN)
        Map<Long, ClienteInteresado> directorio = clientes.listarTodos().stream()
                .filter(c -> c.getIdCliente() != null)
                .collect(Collectors.toMap(ClienteInteresado::getIdCliente, c -> c, (a, b) -> a));

        Map<Long, CoincidenciaCartera.Evaluacion> mejorPorCliente = new LinkedHashMap<>();
        Map<Long, RequerimientoCliente> reqPorCliente = new LinkedHashMap<>();
        for (RequerimientoCliente r : requerimientos.listarTodos()) {
            if (r.getEstado() != EstadoRequerimiento.ACTIVO || r.getCliente() == null) {
                continue;
            }
            Long idCliente = r.getCliente().getIdCliente();
            if (idCliente == null || (idsPermitidos != null && !idsPermitidos.contains(idCliente))) {
                continue;
            }
            CoincidenciaCartera.Evaluacion e = CoincidenciaCartera.evaluar(r, local);
            if (e.puntaje() <= 0) {
                continue;
            }
            CoincidenciaCartera.Evaluacion previa = mejorPorCliente.get(idCliente);
            if (previa == null || e.puntaje() > previa.puntaje()) {
                mejorPorCliente.put(idCliente, e);
                reqPorCliente.put(idCliente, r);
            }
        }

        List<CoincidenciaResponse> items = new ArrayList<>();
        for (Map.Entry<Long, CoincidenciaCartera.Evaluacion> entry : mejorPorCliente.entrySet()) {
            items.add(filaCliente(
                    directorio.get(entry.getKey()), reqPorCliente.get(entry.getKey()), entry.getValue(), idCaptacion));
        }
        return items;
    }

    private CoincidenciasResponse armar(String origen, List<CoincidenciaResponse> items, int page, int pageSize) {
        List<CoincidenciaResponse> ordenado = items.stream()
                .sorted(Comparator.comparingInt(CoincidenciaResponse::puntaje).reversed()
                        .thenComparing(c -> c.cumple().size(), Comparator.reverseOrder())
                        .thenComparing(CoincidenciaResponse::titulo))
                .toList();
        int pagina = SeguridadRest.pagina(page);
        int tamano = Math.min(MAX_PAGE_SIZE, SeguridadRest.tamano(pageSize));
        int desde = Math.min((pagina - 1) * tamano, ordenado.size());
        int hasta = Math.min(desde + tamano, ordenado.size());
        return new CoincidenciasResponse(origen, ordenado.size(), pagina, tamano, ordenado.subList(desde, hasta));
    }

    private CoincidenciaResponse filaPropiedad(
            Captacion captacion, LocalComercial local, CoincidenciaCartera.Evaluacion e, long idCliente) {
        String proponer = captacion.getIdCaptacion() != null
                ? "oportunidad-form?clienteId=" + idCliente + "&captacionId=" + captacion.getIdCaptacion()
                : "";
        return new CoincidenciaResponse(
                "PROPIEDAD",
                captacion.getIdCaptacion(),
                texto(captacion.getCodigoCaptacion(), local.getCodigoLocal()),
                texto(local.getDireccion(), "Local sin direccion"),
                texto(local.getRubroPermitido(), local.getDescripcion()),
                texto(local.getDistrito()),
                monto(local.getPrecioReferencial()),
                medida(local.getMetraje(), "m2"),
                medida(local.getFrente(), "m de frente"),
                e.puntaje(),
                e.cumple(),
                e.noCumple(),
                idCliente,
                captacion.getIdCaptacion(),
                proponer);
    }

    private CoincidenciaResponse filaCliente(
            ClienteInteresado cliente, RequerimientoCliente r, CoincidenciaCartera.Evaluacion e, Long idCaptacion) {
        Long idCliente = cliente != null ? cliente.getIdCliente()
                : (r.getCliente() != null ? r.getCliente().getIdCliente() : null);
        String nombre = cliente != null && cliente.getPersona() != null
                ? texto(cliente.getPersona().getNombresORazonSocial())
                : "Cliente interesado";
        String proponer = (idCaptacion != null && idCliente != null)
                ? "oportunidad-form?clienteId=" + idCliente + "&captacionId=" + idCaptacion
                : "";
        String monedaTxt = r.getMoneda() != null ? r.getMoneda().getCodigo() + " " : "";
        return new CoincidenciaResponse(
                "CLIENTE",
                idCliente,
                texto(r.getRubro(), "Requerimiento"),
                nombre,
                requerimientoResumen(r),
                distritosTexto(r),
                monedaTxt + rango(r.getRentaMin(), r.getRentaMax()),
                rango(r.getMetrajeMin(), r.getMetrajeMax()) + " m2",
                r.getFrenteMinimo() != null ? ">= " + plain(r.getFrenteMinimo()) + " m" : "-",
                e.puntaje(),
                e.cumple(),
                e.noCumple(),
                idCliente,
                idCaptacion,
                proponer);
    }

    // ====================================================================
    // Visibilidad ("vista personal")
    // ====================================================================

    private List<Captacion> captacionesCandidatas(UsuarioAutenticado usuario) {
        return captacionesVisibles(usuario).stream()
                .filter(c -> c.getEstado() == EstadoCaptacion.ACTIVA)
                .toList();
    }

    private List<Captacion> captacionesVisibles(UsuarioAutenticado usuario) {
        return switch (usuario.rol()) {
            case "AGENTE" -> captaciones.listarPorAgente(usuario.idDominio());
            case "BROKER" -> captaciones.listarPorBroker(usuario.idDominio());
            case "ADMIN" -> captaciones.listarTodos();
            default -> throw ApiException.prohibido();
        };
    }

    // Clientes que cuentan como demanda propia. null = sin restriccion (ADMIN).
    private Set<Long> idsClientesDelUsuario(Contexto contexto) {
        if ("ADMIN".equals(contexto.usuario().rol())) {
            return null;
        }
        // Origen acotado en SQL (antes se escaneaban tablas completas). visible() sigue siendo
        // el filtro autoritativo sobre este superconjunto: por agente del alcance UNION por
        // captacion del alcance (incluye captaciones cuyo agente esta en el alcance).
        Set<Long> scopeAgentes = "AGENTE".equals(contexto.usuario().rol())
                ? Set.of(contexto.usuario().idDominio())
                : contexto.agentesBroker();
        Set<Long> capScope = new HashSet<>(contexto.captacionesBroker());
        captaciones.listarPorAgentes(scopeAgentes).stream()
                .map(Captacion::getIdCaptacion)
                .filter(Objects::nonNull)
                .forEach(capScope::add);

        Set<Long> ids = new HashSet<>();
        fuente(oportunidades.listarPorAgentes(scopeAgentes), oportunidades.listarPorCaptaciones(capScope)).stream()
                .filter(o -> visible(o, contexto))
                .forEach(o -> add(ids, o.getClienteInteresado()));
        fuente(solicitudes.listarPorAgentes(scopeAgentes), solicitudes.listarPorCaptaciones(capScope)).stream()
                .filter(s -> visible(s, contexto))
                .forEach(s -> add(ids, s.getClienteInteresado()));
        fuente(visitas.listarPorAgentes(scopeAgentes), visitas.listarPorCaptaciones(capScope)).stream()
                .filter(v -> visible(v, contexto))
                .forEach(v -> {
                    add(ids, v.getClienteInteresado());
                    if (v.getOportunidadComercial() != null) {
                        add(ids, v.getOportunidadComercial().getClienteInteresado());
                    }
                });
        return ids;
    }

    private static <T> List<T> fuente(List<T> porAgente, List<T> porCaptacion) {
        List<T> union = new ArrayList<>(porAgente);
        union.addAll(porCaptacion);
        return union;
    }

    private Contexto contexto(UsuarioAutenticado usuario) {
        Set<Long> agentesBroker = "BROKER".equals(usuario.rol())
                ? brokers.listarAgentesSupervisados(usuario.idDominio()).stream()
                        .map(BrokerAgente::getIdAgente).collect(Collectors.toSet())
                : Set.of();
        Set<Long> captacionesBroker = "BROKER".equals(usuario.rol())
                ? captaciones.listarPorBroker(usuario.idDominio()).stream()
                        .map(Captacion::getIdCaptacion).filter(Objects::nonNull).collect(Collectors.toSet())
                : Set.of();
        return new Contexto(usuario, agentesBroker, captacionesBroker);
    }

    private boolean visible(OportunidadComercial o, Contexto c) {
        return visibleAgente(o.getAgenteResponsable(), c) || visibleCaptacion(o.getCaptacion(), c);
    }

    private boolean visible(SolicitudAlquiler s, Contexto c) {
        return visibleAgente(s.getAgenteResponsable(), c) || visibleCaptacion(s.getCaptacion(), c);
    }

    private boolean visible(Visita v, Contexto c) {
        return visibleAgente(v.getAgenteResponsable(), c) || visibleCaptacion(v.getCaptacion(), c);
    }

    private boolean visibleCaptacion(Captacion cap, Contexto c) {
        if (cap == null) {
            return false;
        }
        return visibleAgente(cap.getAgenteResponsable(), c)
                || (cap.getIdCaptacion() != null && c.captacionesBroker().contains(cap.getIdCaptacion()));
    }

    private boolean visibleAgente(AgenteInmobiliario agente, Contexto c) {
        String rol = c.usuario().rol();
        if ("ADMIN".equals(rol)) {
            return true;
        }
        if (agente == null || agente.getIdAgente() == null) {
            return false;
        }
        if ("AGENTE".equals(rol)) {
            return c.usuario().idDominio() == agente.getIdAgente();
        }
        return "BROKER".equals(rol) && c.agentesBroker().contains(agente.getIdAgente());
    }

    // ====================================================================
    // Acceso a la entidad de origen
    // ====================================================================

    private ClienteInteresado clienteConAcceso(long idCliente, UsuarioAutenticado usuario) {
        ClienteInteresado cliente = clientes.buscarPorId(idCliente)
                .orElseThrow(() -> ApiException.noEncontrado("Cliente"));
        String rol = usuario.rol();
        if ("ADMIN".equals(rol) || "AGENTE".equals(rol)) {
            return cliente;
        }
        Set<Long> ids = idsClientesDelUsuario(contexto(usuario));
        if (ids == null || ids.contains(idCliente)) {
            return cliente;
        }
        throw ApiException.prohibido();
    }

    private Captacion captacionConAcceso(String idOrCodigo, UsuarioAutenticado usuario) {
        List<Captacion> visibles = captacionesVisibles(usuario);
        Long id = parseLong(idOrCodigo);
        if (id != null) {
            return visibles.stream()
                    .filter(c -> id.equals(c.getIdCaptacion()))
                    .findFirst()
                    .orElseThrow(() -> captaciones.buscarPorId(id).isPresent()
                            ? ApiException.prohibido()
                            : ApiException.noEncontrado("Captacion"));
        }
        return visibles.stream()
                .filter(c -> c.getCodigoCaptacion() != null && c.getCodigoCaptacion().equalsIgnoreCase(idOrCodigo))
                .findFirst()
                .orElseThrow(() -> ApiException.noEncontrado("Captacion"));
    }

    private Prospeccion prospeccionConAcceso(long idProspeccion, UsuarioAutenticado usuario) {
        Prospeccion prospeccion = prospecciones.buscarPorId(idProspeccion)
                .orElseThrow(() -> ApiException.noEncontrado("Prospeccion"));
        if (visibleAgente(prospeccion.getAgenteResponsable(), contexto(usuario))) {
            return prospeccion;
        }
        throw ApiException.prohibido();
    }

    // ====================================================================
    // Utilidades de formato
    // ====================================================================

    private static void add(Set<Long> ids, ClienteInteresado cliente) {
        if (cliente != null && cliente.getIdCliente() != null) {
            ids.add(cliente.getIdCliente());
        }
    }

    private static Long parseLong(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return Long.parseLong(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String texto(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return "-";
    }

    private static String nz(String valor) {
        return valor == null ? "" : valor;
    }

    private static String plain(BigDecimal valor) {
        return valor == null ? "-" : valor.stripTrailingZeros().toPlainString();
    }

    private static String monto(BigDecimal valor) {
        return valor == null ? "-" : plain(valor);
    }

    private static String medida(BigDecimal valor, String unidad) {
        return valor == null ? "-" : plain(valor) + " " + unidad;
    }

    private static String rango(BigDecimal min, BigDecimal max) {
        if (min != null && max != null) {
            return plain(min) + " - " + plain(max);
        }
        if (min != null) {
            return "desde " + plain(min);
        }
        if (max != null) {
            return "hasta " + plain(max);
        }
        return "sin limite";
    }

    private static String distritosTexto(RequerimientoCliente r) {
        if (r.getDistritos() == null || r.getDistritos().isEmpty()) {
            return "Cualquier distrito";
        }
        return r.getDistritos().stream()
                .map(Distrito::getNombre)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    private static String requerimientoResumen(RequerimientoCliente r) {
        List<String> partes = new ArrayList<>();
        if (r.getTipoInmueble() != null) {
            partes.add(r.getTipoInmueble().getDescripcion());
        }
        if (r.getRentaMin() != null || r.getRentaMax() != null) {
            Moneda moneda = r.getMoneda();
            partes.add("Renta " + (moneda != null ? moneda.getCodigo() + " " : "")
                    + rango(r.getRentaMin(), r.getRentaMax()));
        }
        if (r.getMetrajeMin() != null || r.getMetrajeMax() != null) {
            partes.add("Area " + rango(r.getMetrajeMin(), r.getMetrajeMax()) + " m2");
        }
        if (r.getFrenteMinimo() != null) {
            partes.add("Frente desde " + plain(r.getFrenteMinimo()) + " m");
        }
        if (partes.isEmpty()) {
            return texto(r.getObservaciones(), "Perfil de busqueda");
        }
        return String.join(" | ", partes);
    }

    // ====================================================================
    // Tipos
    // ====================================================================

    private record Contexto(UsuarioAutenticado usuario, Set<Long> agentesBroker, Set<Long> captacionesBroker) {
    }

    public record CoincidenciasResponse(String origen, int total, int page, int pageSize,
                                        List<CoincidenciaResponse> items) {
    }

    public record CoincidenciaResponse(
            String tipo,
            Long id,
            String codigo,
            String titulo,
            String subtitulo,
            String distrito,
            String renta,
            String area,
            String frente,
            int puntaje,
            List<String> cumple,
            List<String> noCumple,
            Long clienteId,
            Long captacionId,
            String proponerRuta) {
    }
}
