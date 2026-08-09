package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.Prospeccion;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.SeguimientoComercialService;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Descripciones;
import com.controllocal.service.soporte.Fechas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Vista transversal E4. Replica {@code SeguimientoComercialRest} fila por fila,
 * incluidos los textos de icono/tono y las rutas del cliente, que la pantalla
 * usa como datos.
 *
 * <p>Tres cosas que hay que saber antes de tocarla:
 * <ul>
 *   <li>el <b>cierre se arma desde su solicitud</b>, no desde el contrato: si
 *       la solicitud no esta en el alcance, el contrato desaparece de la lista;</li>
 *   <li>el <b>propietario se resuelve por mapa</b> {@code id_local → propietario}
 *       construido con TODAS las captaciones del tenant, sin filtro de rol
 *       (§6.3 del contrato) — es del cable y se replica;</li>
 *   <li>{@code counts} y {@code options} miran conjuntos distintos: los
 *       contadores llevan todos los filtros menos el de proceso, las opciones no
 *       llevan ninguno.</li>
 * </ul>
 */
@Service
public class SeguimientoComercialServiceImpl implements SeguimientoComercialService {

    private static final String PROSPECCION = "Prospeccion";
    private static final String CAPTACION = "Captacion";
    private static final String OPORTUNIDAD = "Oportunidad";
    private static final String SOLICITUD = "Solicitud";
    private static final String CIERRE = "Cierre";

    /** Formato del cable para la vigencia de la captacion: "29 Jul 2026", en ingles. */
    private static final DateTimeFormatter FECHA_LEGIBLE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final ProspeccionRepository prospecciones;
    private final CaptacionRepository captaciones;
    private final OportunidadComercialRepository oportunidades;
    private final SolicitudAlquilerRepository solicitudes;
    private final ContratoAlquilerRepository contratos;
    private final Alcances alcances;

    public SeguimientoComercialServiceImpl(ProspeccionRepository prospecciones,
                                           CaptacionRepository captaciones,
                                           OportunidadComercialRepository oportunidades,
                                           SolicitudAlquilerRepository solicitudes,
                                           ContratoAlquilerRepository contratos,
                                           Alcances alcances) {
        this.prospecciones = prospecciones;
        this.captaciones = captaciones;
        this.oportunidades = oportunidades;
        this.solicitudes = solicitudes;
        this.contratos = contratos;
        this.alcances = alcances;
    }

    @Override
    @Transactional(readOnly = true)
    public Resultado listar(Filtros filtros, Actor actor) {
        List<Fila> todas = filasPermitidas(actor);
        List<Fila> base = filtrar(todas, TODOS, filtros);
        List<Fila> filtradas = filtrar(todas, filtros.proceso(), filtros);

        int pagina = Math.max(1, filtros.pagina());
        int tamano = Math.min(TAMANO_MAXIMO, Math.max(1, Math.min(100, filtros.tamano())));
        int desde = Math.min((pagina - 1) * tamano, filtradas.size());
        int hasta = Math.min(desde + tamano, filtradas.size());

        return new Resultado(
                List.copyOf(filtradas.subList(desde, hasta)),
                filtradas.size(),
                pagina,
                tamano,
                conteos(base),
                opciones(todas));
    }

    // ---------- armado de filas ----------

    private List<Fila> filasPermitidas(Actor actor) {
        Alcances.Alcance alcance = alcances.de(actor);
        long organizacion = alcance.idOrganizacion();
        boolean esBroker = !alcance.global() && !actor.esAgente();
        Set<Long> agentesBroker = esBroker ? Set.copyOf(alcance.rolesAgente()) : Set.of();

        // El propietario solo viene cargado en la captacion; oportunidad, solicitud y
        // cierre traen el local sin el. La v1 arma un mapa id_local -> propietario con
        // TODAS las captaciones del tenant para enriquecer esas filas sin consultas
        // extra, y ese mapa NO lleva filtro de rol.
        List<Captacion> todasCaptaciones = captaciones.listarSeguimiento(organizacion);
        Map<Long, PersonaRol> propietarioPorLocal = new HashMap<>();
        for (Captacion c : todasCaptaciones) {
            Propiedad local = c.getPropiedad();
            if (local == null || local.getId() == null || local.getId() <= 0
                    || local.getRolPropietario() == null) {
                continue;
            }
            propietarioPorLocal.putIfAbsent(local.getId(), local.getRolPropietario());
        }
        // Captaciones del equipo: la segunda rama de alcance de oportunidad, solicitud
        // y cierre. Sale del listado ya cargado, no de otra consulta.
        Set<Long> captacionesBroker = new HashSet<>();
        if (esBroker) {
            for (Captacion c : todasCaptaciones) {
                if (c.getId() != null && agentesBroker.contains(idAgente(c.getAgente()))) {
                    captacionesBroker.add(c.getId());
                }
            }
        }

        List<Fila> filas = new ArrayList<>();
        for (Prospeccion p : prospecciones.listarSeguimiento(
                organizacion, alcance.global(), alcance.paramRoles())) {
            if (permitidoPorAgente(p.getAgente(), actor, agentesBroker)) {
                filas.add(filaProspeccion(p));
            }
        }
        for (Captacion c : todasCaptaciones) {
            if (permitidoPorAgente(c.getAgente(), actor, agentesBroker)
                    || captacionesBroker.contains(c.getId())) {
                filas.add(filaCaptacion(c));
            }
        }
        for (OportunidadComercial o : oportunidades.listarSeguimiento(
                organizacion, alcance.global(), esBroker, alcance.paramRoles())) {
            if (permitidoPorAgente(o.getAgente(), actor, agentesBroker)
                    || captacionPermitida(o.getCaptacion(), captacionesBroker, actor)) {
                filas.add(filaOportunidad(o, propietarioPorLocal));
            }
        }
        for (SolicitudAlquiler s : solicitudes.listarSeguimiento(
                organizacion, alcance.global(), esBroker, alcance.paramRoles())) {
            if (permitidoPorAgente(s.getAgente(), actor, agentesBroker)
                    || captacionPermitida(captacion(s), captacionesBroker, actor)) {
                filas.add(filaSolicitud(s, propietarioPorLocal));
            }
        }
        for (ContratoAlquiler c : contratos.listarSeguimiento(organizacion)) {
            SolicitudAlquiler solicitud = c.getSolicitud();
            if (solicitud == null) {
                continue;
            }
            if (permitidoPorAgente(solicitud.getAgente(), actor, agentesBroker)
                    || captacionPermitida(captacion(solicitud), captacionesBroker, actor)) {
                filas.add(filaContrato(c, solicitud, propietarioPorLocal));
            }
        }

        // Orden del cable, con una consecuencia contraintuitiva que hay que conservar:
        // el .reversed() invierte tambien el tratamiento de los nulos, asi que el
        // nullsLast de dentro se vuelve nulls-FIRST y una fila sin fechaOrden encabeza
        // la lista. Sacar el reversed de en medio cambiaria el orden que la pantalla ve.
        filas.sort(Comparator
                .comparing(Fila::fechaOrden, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
                .reversed()
                .thenComparing(Fila::proceso)
                .thenComparing(Fila::codigo));
        return filas;
    }

    private Fila filaProspeccion(Prospeccion p) {
        Propiedad local = p.getPropiedad();
        String codigoCaptacion = p.getCaptacion() != null ? p.getCaptacion().getCodigoCaptacion() : null;
        return new Fila(
                PROSPECCION,
                texto(p.getCodigoProspeccion(), local != null ? local.getCodigo() : null),
                "-",
                null,
                direccion(local),
                distrito(local),
                nombre(p.getAgente()),
                propietario(local != null ? local.getRolPropietario() : null),
                propietarioId(local != null ? local.getRolPropietario() : null),
                Descripciones.prospeccion(p.estadoActual()),
                hitoProspeccion(p, codigoCaptacion),
                p.getId() != null ? "prospeccion-detail/" + p.getId() : "",
                "",
                "store",
                "blue",
                primerDia(p.getFechaPropuesta(), p.getFechaReunion(), p.getFechaContacto()),
                "");
    }

    private Fila filaCaptacion(Captacion c) {
        Propiedad local = c.getPropiedad();
        return new Fila(
                CAPTACION,
                texto(c.getCodigoCaptacion()),
                "-",
                null,
                direccion(local),
                distrito(local),
                nombre(c.getAgente()),
                propietario(local != null ? local.getRolPropietario() : null),
                propietarioId(local != null ? local.getRolPropietario() : null),
                Descripciones.captacion(c.estadoActual()),
                vigencia(c),
                c.getCodigoCaptacion() != null ? "captacion-detail/" + c.getCodigoCaptacion() : "",
                "P".equals(c.estadoActual()) && c.getCodigoCaptacion() != null
                        ? "captacion-review/" + c.getCodigoCaptacion() : "",
                "pin",
                "blue",
                primerDia(c.getFechaCaptacion(), c.getFechaInicioVigencia()),
                "");
    }

    private Fila filaOportunidad(OportunidadComercial o, Map<Long, PersonaRol> propietarioPorLocal) {
        Propiedad local = local(o.getCaptacion());
        LocalDateTime marca = Fechas.local(
                o.getFechaActualizacion() != null ? o.getFechaActualizacion() : o.getFechaRegistro());
        PersonaRol propietario = propietarioDeFila(local, propietarioPorLocal);
        return new Fila(
                OPORTUNIDAD,
                texto(o.getCodigoOportunidad()),
                nombre(o.getCliente()),
                idCliente(o.getCliente()),
                direccion(local),
                distrito(local),
                nombre(o.getAgente()),
                propietario(propietario),
                propietarioId(propietario),
                Descripciones.oportunidad(o.estadoActual()),
                fechaHora(marca),
                o.getId() != null ? "oportunidad-detail/" + o.getId() : "",
                "",
                "target",
                "info",
                marca,
                "");
    }

    private Fila filaSolicitud(SolicitudAlquiler s, Map<Long, PersonaRol> propietarioPorLocal) {
        Propiedad local = local(captacion(s));
        LocalDateTime actualizacion = Fechas.local(s.getFechaActualizacionEstado());
        PersonaRol propietario = propietarioDeFila(local, propietarioPorLocal);
        return new Fila(
                SOLICITUD,
                texto(s.getCodigoSolicitud()),
                nombre(cliente(s)),
                idCliente(cliente(s)),
                direccion(local),
                distrito(local),
                nombre(s.getAgente()),
                propietario(propietario),
                propietarioId(propietario),
                Descripciones.solicitud(s.estadoActual()),
                fechaHora(actualizacion),
                s.getCodigoSolicitud() != null ? "solicitud-detail/" + s.getCodigoSolicitud() : "",
                "E".equals(s.estadoActual()) && s.getCodigoSolicitud() != null
                        ? "evaluacion/" + s.getCodigoSolicitud() : "",
                "fileText",
                "gray",
                actualizacion != null ? actualizacion : primerDia(s.getFechaRegistro()),
                monto(s.getMontoPropuesto()));
    }

    /**
     * El cierre toma TODO de la solicitud —codigo, cliente, agente, local y
     * monto—; del contrato solo salen el estado y la fecha de cierre.
     */
    private Fila filaContrato(ContratoAlquiler contrato,
                              SolicitudAlquiler solicitud,
                              Map<Long, PersonaRol> propietarioPorLocal) {
        Propiedad local = local(captacion(solicitud));
        OportunidadComercial oportunidad = solicitud.getOportunidad();
        PersonaRol propietario = propietarioDeFila(local, propietarioPorLocal);
        return new Fila(
                CIERRE,
                texto(oportunidad != null ? oportunidad.getCodigoOportunidad() : null,
                        solicitud.getCodigoSolicitud()),
                nombre(cliente(solicitud)),
                idCliente(cliente(solicitud)),
                direccion(local),
                distrito(local),
                nombre(solicitud.getAgente()),
                propietario(propietario),
                propietarioId(propietario),
                Descripciones.contrato(contrato.estadoActual()),
                fecha(contrato.getFechaCierre()),
                solicitud.getCodigoSolicitud() != null
                        ? "solicitud-detail/" + solicitud.getCodigoSolicitud()
                        : "propiedades-alquiladas",
                "",
                "checkCircle",
                "green",
                primerDia(contrato.getFechaCierre()),
                monto(solicitud.getMontoPropuesto()));
    }

    // ---------- filtros, conteos y opciones ----------

    private List<Fila> filtrar(List<Fila> filas, String proceso, Filtros filtros) {
        String tipo = normal(proceso);
        return filas.stream()
                .filter(f -> tipo.isBlank() || normal(TODOS).equals(tipo) || normal(f.proceso()).equals(tipo))
                .filter(f -> contiene(f.agente(), filtros.agente()))
                .filter(f -> contiene(f.propietario(), filtros.propietario()))
                .filter(f -> contiene(f.estado(), filtros.estado()))
                .filter(f -> contiene(f.distrito(), filtros.distrito()))
                .filter(f -> coincideBusqueda(f, filtros.busqueda()))
                .toList();
    }

    private static boolean coincideBusqueda(Fila f, String query) {
        return query == null || query.isBlank()
                || contiene(f.proceso(), query)
                || contiene(f.codigo(), query)
                || contiene(f.cliente(), query)
                || contiene(f.local(), query)
                || contiene(f.distrito(), query)
                || contiene(f.agente(), query)
                || contiene(f.propietario(), query)
                || contiene(f.estado(), query);
    }

    private static Conteos conteos(List<Fila> filas) {
        return new Conteos(
                filas.size(),
                contar(filas, PROSPECCION),
                contar(filas, CAPTACION),
                contar(filas, OPORTUNIDAD),
                contar(filas, SOLICITUD),
                contar(filas, CIERRE));
    }

    private static Opciones opciones(List<Fila> filas) {
        return new Opciones(
                valores(filas, Fila::agente),
                valores(filas, Fila::propietario),
                valores(filas, Fila::estado),
                valores(filas, Fila::distrito));
    }

    private static int contar(List<Fila> filas, String proceso) {
        return (int) filas.stream().filter(f -> proceso.equals(f.proceso())).count();
    }

    /** Descarta nulos, vacios y el relleno "-", deduplica y ordena sin distinguir mayusculas. */
    private static List<String> valores(List<Fila> filas, Function<Fila, String> campo) {
        return filas.stream()
                .map(campo)
                .filter(v -> v != null && !v.isBlank() && !"-".equals(v))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    // ---------- alcance ----------

    /** ADMIN todo; AGENTE lo suyo; BROKER lo de su equipo vigente. */
    private static boolean permitidoPorAgente(DetalleAgente agente, Actor actor, Set<Long> agentesBroker) {
        if (actor.esTenantAdmin()) {
            return true;
        }
        Long id = idAgente(agente);
        if (id == null) {
            return false;
        }
        return actor.esAgente() ? id == actor.idRolOperativo() : agentesBroker.contains(id);
    }

    private static boolean captacionPermitida(Captacion captacion, Set<Long> captacionesBroker, Actor actor) {
        if (actor.esTenantAdmin()) {
            return true;
        }
        return captacion != null && captacion.getId() != null
                && captacionesBroker.contains(captacion.getId());
    }

    // ---------- shaping ----------

    private static String hitoProspeccion(Prospeccion p, String codigoCaptacion) {
        if (codigoCaptacion != null && !codigoCaptacion.isBlank()) {
            return codigoCaptacion;
        }
        if (p.getFechaPropuesta() != null) {
            return "Propuesta entregada " + fecha(p.getFechaPropuesta());
        }
        if (p.getFechaReunion() != null) {
            return "Reunion " + fecha(p.getFechaReunion());
        }
        if (p.getFechaContacto() != null) {
            return "Contacto " + fecha(p.getFechaContacto());
        }
        return "Prospecto";
    }

    /** Avance de una captacion: hasta cuando esta vigente; si no hay vigencia, cuando se capto. */
    private static String vigencia(Captacion c) {
        if (c.getFechaFinVigencia() != null) {
            return "Vigente hasta " + c.getFechaFinVigencia().format(FECHA_LEGIBLE);
        }
        if (c.getFechaCaptacion() != null) {
            return "Captada el " + c.getFechaCaptacion().format(FECHA_LEGIBLE);
        }
        return "-";
    }

    /**
     * Propietario de las filas que NO lo traen cargado —oportunidad, solicitud
     * y cierre—: se resuelve por {@code id_local} contra el mapa de captaciones.
     * Se consulta el mapa y no {@code local.getRolPropietario()} a proposito:
     * esas consultas no hacen fetch del propietario, asi que leerlo de la
     * entidad dispararia una carga diferida por fila (el N+1 que el mapa evita).
     */
    private static PersonaRol propietarioDeFila(Propiedad local, Map<Long, PersonaRol> porLocal) {
        return local != null && local.getId() != null ? porLocal.get(local.getId()) : null;
    }

    private static Propiedad local(Captacion captacion) {
        return captacion != null ? captacion.getPropiedad() : null;
    }

    private static Captacion captacion(SolicitudAlquiler solicitud) {
        OportunidadComercial oportunidad = solicitud != null ? solicitud.getOportunidad() : null;
        return oportunidad != null ? oportunidad.getCaptacion() : null;
    }

    private static DetalleCliente cliente(SolicitudAlquiler solicitud) {
        OportunidadComercial oportunidad = solicitud != null ? solicitud.getOportunidad() : null;
        return oportunidad != null ? oportunidad.getCliente() : null;
    }

    private static String direccion(Propiedad local) {
        return local != null ? texto(local.getDireccion()) : "-";
    }

    private static String distrito(Propiedad local) {
        return local != null ? texto(local.getDistrito()) : "-";
    }

    private static String propietario(PersonaRol rol) {
        return rol != null ? texto(nombre(rol.getPersona())) : "-";
    }

    /** Habilita el enlace a la ficha del propietario; el cable omite el 0 y el nulo. */
    private static Long propietarioId(PersonaRol rol) {
        Long id = rol != null ? rol.getId() : null;
        return id != null && id > 0 ? id : null;
    }

    private static Long idAgente(DetalleAgente agente) {
        return agente != null ? agente.getId() : null;
    }

    private static Long idCliente(DetalleCliente cliente) {
        return cliente != null ? cliente.getId() : null;
    }

    private static String nombre(DetalleAgente agente) {
        return agente != null && agente.getRol() != null
                ? texto(nombre(agente.getRol().getPersona())) : "-";
    }

    private static String nombre(DetalleCliente cliente) {
        return cliente != null && cliente.getRol() != null
                ? texto(nombre(cliente.getRol().getPersona())) : "-";
    }

    private static String nombre(Persona persona) {
        return persona != null ? persona.getNombresORazonSocial() : null;
    }

    private static String monto(BigDecimal valor) {
        return valor != null ? valor.toPlainString() : "";
    }

    private static String fecha(LocalDate fecha) {
        return fecha != null ? fecha.toString() : "";
    }

    private static String fechaHora(LocalDateTime fecha) {
        return fecha != null ? fecha.toString() : "";
    }

    private static LocalDateTime primerDia(LocalDate... fechas) {
        for (LocalDate fecha : fechas) {
            if (fecha != null) {
                return fecha.atStartOfDay();
            }
        }
        return null;
    }

    private static boolean contiene(String valor, String filtro) {
        return filtro == null || filtro.isBlank() || normal(valor).contains(normal(filtro));
    }

    private static String normal(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    /** Primer valor no vacio; "-" si no hay ninguno, que es el relleno del cable. */
    private static String texto(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return "-";
    }
}
