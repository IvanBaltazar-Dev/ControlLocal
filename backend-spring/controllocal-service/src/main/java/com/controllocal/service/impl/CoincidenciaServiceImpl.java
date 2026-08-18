package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.Prospeccion;
import com.controllocal.domain.comercial.RequerimientoCliente;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.DetalleLocalComercial;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleClienteRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.RequerimientoClienteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.CoincidenciaService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.CoincidenciaCartera;
import com.controllocal.service.soporte.CoincidenciaCartera.Evaluacion;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValoresDePropiedad;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shaping y visibilidad del matching, calcados de CoincidenciaCarteraSupport
 * de la v1 (las frases y las rutas viajan literales al frontend).
 *
 * <p>La "vista personal" es lo delicado: para captacion→clientes y
 * prospeccion→clientes el universo NO es el catalogo de clientes, sino los que
 * el equipo ya trabaja (los de sus oportunidades). Un ADMIN no tiene ese
 * limite. Las solicitudes de alquiler, que en la v1 tambien aportaban clientes
 * a ese conjunto, llegan con F4.
 */
@Service
public class CoincidenciaServiceImpl implements CoincidenciaService {

    private static final int MAX_PAGE_SIZE = 24;

    private final CaptacionRepository captaciones;
    private final ProspeccionRepository prospecciones;
    private final RequerimientoClienteRepository requerimientos;
    private final DetalleClienteRepository clientes;
    private final OportunidadComercialRepository oportunidades;
    private final Alcances alcances;
    private final LectorPorAutoridad lector;

    public CoincidenciaServiceImpl(CaptacionRepository captaciones, ProspeccionRepository prospecciones,
                                   RequerimientoClienteRepository requerimientos,
                                   DetalleClienteRepository clientes,
                                   OportunidadComercialRepository oportunidades, Alcances alcances,
                                   LectorPorAutoridad lector) {
        this.captaciones = captaciones;
        this.prospecciones = prospecciones;
        this.requerimientos = requerimientos;
        this.clientes = clientes;
        this.oportunidades = oportunidades;
        this.alcances = alcances;
        this.lector = lector;
    }

    @Override
    @Transactional(readOnly = true)
    public Coincidencias propiedadesParaCliente(long idCliente, int page, int pageSize, Actor actor) {
        clienteConAcceso(idCliente, actor);
        List<RequerimientoCliente> activos =
                requerimientos.listarActivosPorCliente(actor.idOrganizacion(), idCliente);
        List<Coincidencia> items = new ArrayList<>();
        if (!activos.isEmpty()) {
            List<Captacion> candidatas = captacionesCandidatas(actor);
            // Los atributos gobernados de TODA la cartera candidata, en una sola
            // consulta. Dentro del bucle serian tantas como candidatas: el N+1
            // que RC-003 quito, reintroducido por la puerta de la autoridad.
            Map<Long, ValoresDePropiedad> valores = lector.deVarias(actor.idOrganizacion(),
                    candidatas.stream().map(Captacion::getPropiedad).filter(Objects::nonNull)
                            .distinct().toList());
            for (Captacion captacion : candidatas) {
                Propiedad propiedad = captacion.getPropiedad();
                if (propiedad == null || !Propiedad.LEGADO_DISPONIBLE.equals(propiedad.estadoLegado())) {
                    continue;
                }
                ValoresDePropiedad suyos =
                        valores.getOrDefault(propiedad.getId(), ValoresDePropiedad.vacio());
                Evaluacion mejor = null;
                for (RequerimientoCliente r : activos) {
                    Evaluacion e = CoincidenciaCartera.evaluar(r, propiedad, suyos);
                    if (mejor == null || e.puntaje() > mejor.puntaje()) {
                        mejor = e;
                    }
                }
                if (mejor != null && mejor.puntaje() > 0) {
                    items.add(filaPropiedad(captacion, propiedad, mejor, idCliente, suyos));
                }
            }
        }
        return armar("cliente:" + idCliente, items, page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public Coincidencias clientesParaCaptacion(String idOrCodigo, int page, int pageSize, Actor actor) {
        Captacion captacion = captacionConAcceso(idOrCodigo, actor);
        List<Coincidencia> items =
                clientesCompatibles(captacion.getPropiedad(), captacion.getId(), actor);
        return armar("captacion:" + nz(captacion.getCodigoCaptacion()), items, page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public Coincidencias clientesParaProspeccion(long idProspeccion, int page, int pageSize, Actor actor) {
        Prospeccion prospeccion = prospecciones.buscarFicha(actor.idOrganizacion(), idProspeccion)
                .orElseThrow(() -> new NoEncontradoException("Prospeccion"));
        if (!alcances.alcanza(actor, prospeccion.getAgente() != null
                ? prospeccion.getAgente().getId() : null)) {
            throw new AccesoNoAutorizadoException();
        }
        // Solo es accionable si la prospeccion YA tiene captacion: sin ella no
        // hay nada que proponer todavia.
        Long idCaptacion = prospeccion.getCaptacion() != null ? prospeccion.getCaptacion().getId() : null;
        List<Coincidencia> items =
                clientesCompatibles(prospeccion.getPropiedad(), idCaptacion, actor);
        return armar("prospeccion:" + idProspeccion, items, page, pageSize);
    }

    // ------------------------------------------------------------------
    // Construccion de coincidencias
    // ------------------------------------------------------------------

    private List<Coincidencia> clientesCompatibles(Propiedad propiedad, Long idCaptacion, Actor actor) {
        if (propiedad == null) {
            return List.of();
        }
        Set<Long> permitidos = idsClientesDelActor(actor); // null = sin limite (ADMIN)
        ValoresDePropiedad valores = lector.de(actor.idOrganizacion(), propiedad);
        Map<Long, Evaluacion> mejorPorCliente = new LinkedHashMap<>();
        Map<Long, RequerimientoCliente> reqPorCliente = new LinkedHashMap<>();

        for (RequerimientoCliente r : requerimientos.listarActivos(actor.idOrganizacion())) {
            DetalleCliente cliente = r.getCliente();
            if (cliente == null || cliente.getId() == null) {
                continue;
            }
            Long idCliente = cliente.getId();
            if (permitidos != null && !permitidos.contains(idCliente)) {
                continue;
            }
            Evaluacion e = CoincidenciaCartera.evaluar(r, propiedad, valores);
            if (e.puntaje() <= 0) {
                continue;
            }
            Evaluacion previa = mejorPorCliente.get(idCliente);
            if (previa == null || e.puntaje() > previa.puntaje()) {
                mejorPorCliente.put(idCliente, e);
                reqPorCliente.put(idCliente, r);
            }
        }

        List<Coincidencia> items = new ArrayList<>();
        for (Map.Entry<Long, Evaluacion> entrada : mejorPorCliente.entrySet()) {
            items.add(filaCliente(reqPorCliente.get(entrada.getKey()), entrada.getValue(), idCaptacion));
        }
        return items;
    }

    private Coincidencias armar(String origen, List<Coincidencia> items, int page, int pageSize) {
        List<Coincidencia> ordenado = items.stream()
                .sorted(Comparator.comparingInt(Coincidencia::puntaje).reversed()
                        .thenComparing(c -> c.cumple().size(), Comparator.reverseOrder())
                        .thenComparing(Coincidencia::titulo))
                .toList();
        int pagina = Math.max(1, page);
        int tamano = Math.min(MAX_PAGE_SIZE, Math.max(1, Math.min(100, pageSize)));
        int desde = Math.min((pagina - 1) * tamano, ordenado.size());
        int hasta = Math.min(desde + tamano, ordenado.size());
        return new Coincidencias(origen, ordenado.size(), pagina, tamano, ordenado.subList(desde, hasta));
    }

    private static Coincidencia filaPropiedad(Captacion captacion, Propiedad propiedad,
                                              Evaluacion e, long idCliente,
                                              ValoresDePropiedad valores) {
        DetalleLocalComercial detalle = propiedad.getDetalleLocal();
        String proponer = captacion.getId() != null
                ? "oportunidad-form?clienteId=" + idCliente + "&captacionId=" + captacion.getId()
                : "";
        return new Coincidencia(
                "PROPIEDAD",
                captacion.getId(),
                texto(captacion.getCodigoCaptacion(), propiedad.getCodigo()),
                texto(propiedad.getDireccion(), "Local sin direccion"),
                texto(detalle != null ? detalle.getRubroPermitido() : null, propiedad.getDescripcion()),
                texto(propiedad.getDistrito()),
                monto(propiedad.getPrecioReferencial()),
                medida(propiedad.getMetraje(), "m2"),
                medida(valores.decimal(CatalogoAtributo.CLAVE_FRENTE), "m de frente"),
                e.puntaje(), e.cumple(), e.noCumple(),
                idCliente, captacion.getId(), proponer);
    }

    private static Coincidencia filaCliente(RequerimientoCliente r, Evaluacion e, Long idCaptacion) {
        DetalleCliente cliente = r.getCliente();
        Long idCliente = cliente != null ? cliente.getId() : null;
        String nombre = nombre(cliente != null ? cliente.getRol() : null);
        String proponer = (idCaptacion != null && idCliente != null)
                ? "oportunidad-form?clienteId=" + idCliente + "&captacionId=" + idCaptacion
                : "";
        String moneda = r.getMoneda() != null ? r.getMoneda() + " " : "";
        return new Coincidencia(
                "CLIENTE",
                idCliente,
                texto(r.getRubro(), "Requerimiento"),
                nombre != null ? texto(nombre) : "Cliente interesado",
                requerimientoResumen(r),
                distritosTexto(r),
                moneda + CoincidenciaCartera.rango(r.getRentaMin(), r.getRentaMax()),
                CoincidenciaCartera.rango(r.getMetrajeMin(), r.getMetrajeMax()) + " m2",
                r.getFrenteMinimo() != null ? ">= " + CoincidenciaCartera.plain(r.getFrenteMinimo()) + " m" : "-",
                e.puntaje(), e.cumple(), e.noCumple(),
                idCliente, idCaptacion, proponer);
    }

    // ------------------------------------------------------------------
    // Visibilidad ("vista personal")
    // ------------------------------------------------------------------

    /** Oferta candidata: captaciones ACTIVAS del alcance del actor. */
    private List<Captacion> captacionesCandidatas(Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return List.of();
        }
        return captaciones.activasEnAlcance(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles());
    }

    /** Clientes que cuentan como demanda propia. null = sin restriccion (ADMIN). */
    private Set<Long> idsClientesDelActor(Actor actor) {
        if (actor.esTenantAdmin()) {
            return null;
        }
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Set.of();
        }
        return Set.copyOf(oportunidades.idsClienteDelEquipo(alcance.idOrganizacion(),
                alcance.paramRoles()));
    }

    private DetalleCliente clienteConAcceso(long idCliente, Actor actor) {
        DetalleCliente cliente = clientes.buscarFicha(actor.idOrganizacion(), idCliente)
                .orElseThrow(() -> new NoEncontradoException("Cliente"));
        if (actor.esTenantAdmin() || actor.esAgente()) {
            return cliente;
        }
        Set<Long> permitidos = idsClientesDelActor(actor);
        if (permitidos == null || permitidos.contains(idCliente)) {
            return cliente;
        }
        throw new AccesoNoAutorizadoException();
    }

    /**
     * Admite id o codigo. La v1 busca dentro de las captaciones VISIBLES, asi
     * que los desenlaces difieren segun como se pidio: por id, una ajena
     * responde 403 y una inexistente 404; por codigo, ambas responden 404
     * (nunca sale de la lista visible). Se replica esa asimetria.
     */
    private Captacion captacionConAcceso(String idOrCodigo, Actor actor) {
        Long id = parseLong(idOrCodigo);
        if (id == null) {
            Captacion porCodigo = captaciones.buscarFichaPorCodigoIgnorandoMayusculas(
                            actor.idOrganizacion(), nz(idOrCodigo).trim())
                    .filter(cap -> visible(cap, actor))
                    .orElseThrow(() -> new NoEncontradoException("Captacion"));
            return porCodigo;
        }
        Captacion captacion = captaciones.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Captacion"));
        if (!visible(captacion, actor)) {
            throw new AccesoNoAutorizadoException();
        }
        return captacion;
    }

    private boolean visible(Captacion captacion, Actor actor) {
        return alcances.alcanza(actor, captacion.getAgente() != null
                ? captacion.getAgente().getId() : null);
    }

    // ------------------------------------------------------------------
    // Formato (frases literales del cable)
    // ------------------------------------------------------------------

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
            partes.add(r.getTipoInmueble().replace('_', ' '));
        }
        if (r.getRentaMin() != null || r.getRentaMax() != null) {
            partes.add("Renta " + (r.getMoneda() != null ? r.getMoneda() + " " : "")
                    + CoincidenciaCartera.rango(r.getRentaMin(), r.getRentaMax()));
        }
        if (r.getMetrajeMin() != null || r.getMetrajeMax() != null) {
            partes.add("Area " + CoincidenciaCartera.rango(r.getMetrajeMin(), r.getMetrajeMax()) + " m2");
        }
        if (r.getFrenteMinimo() != null) {
            partes.add("Frente desde " + CoincidenciaCartera.plain(r.getFrenteMinimo()) + " m");
        }
        if (partes.isEmpty()) {
            return texto(r.getObservaciones(), "Perfil de busqueda");
        }
        return String.join(" | ", partes);
    }

    private static String nombre(PersonaRol rol) {
        return rol == null || rol.getPersona() == null ? null : rol.getPersona().getNombresORazonSocial();
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

    /** Primer valor no vacio, o "-" (asi rellena la v1 las celdas de la tarjeta). */
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

    private static String monto(BigDecimal valor) {
        return valor == null ? "-" : CoincidenciaCartera.plain(valor);
    }

    private static String medida(BigDecimal valor, String unidad) {
        return valor == null ? "-" : CoincidenciaCartera.plain(valor) + " " + unidad;
    }
}
