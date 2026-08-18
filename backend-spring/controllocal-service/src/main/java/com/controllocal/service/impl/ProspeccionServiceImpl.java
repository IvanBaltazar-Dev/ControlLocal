package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.domain.comercial.Prospeccion;
import com.controllocal.domain.inmueble.DetalleLocalComercial;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.AlertaService;
import com.controllocal.service.Actor;
import com.controllocal.service.Pagina;
import com.controllocal.service.ProspeccionService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.PoliticaComercial;
import com.controllocal.service.soporte.Transiciones;
import com.controllocal.service.soporte.CondicionesEconomicas;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Reglas y mensajes calcados del ProspeccionBusinessLogicImpl v1, con dos
 * mejoras del stack nuevo: las transiciones se auditan en historial_estado
 * (via Transiciones) y el alcance por rol se aplica en el WHERE (Alcances).
 * Las interacciones/alertas del flujo v1 se difieren a F6 (modulo transversal).
 */
@Service
public class ProspeccionServiceImpl implements ProspeccionService {

    private static final Map<String, String> DESCRIPCION_ESTADO = Map.of(
            "P", "prospecto", "C", "contactado", "R", "reunion", "E", "propuesta entregada",
            "S", "en seguimiento", "T", "captado", "D", "descartado");

    private final ProspeccionRepository prospecciones;
    private final CaptacionRepository captaciones;
    private final PropiedadRepository propiedades;
    private final DetalleAgenteRepository agentes;
    private final Alcances alcances;
    private final Transiciones transiciones;

    private final SupervisionAgenteRepository supervisiones;

    /**
     * Para avisar al broker de la captacion que nace aqui. Ver {@link #captar}:
     * este era el camino por el que la alerta NO salia.
     */
    private final AlertaService alertas;

    public ProspeccionServiceImpl(ProspeccionRepository prospecciones, CaptacionRepository captaciones,
                                  PropiedadRepository propiedades, DetalleAgenteRepository agentes,
                                  Alcances alcances, Transiciones transiciones,
                                  SupervisionAgenteRepository supervisiones,
                                  AlertaService alertas) {
        this.prospecciones = prospecciones;
        this.captaciones = captaciones;
        this.propiedades = propiedades;
        this.agentes = agentes;
        this.alcances = alcances;
        this.transiciones = transiciones;
        this.supervisiones = supervisiones;
        this.alertas = alertas;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaProspeccion> listar(FiltrosProspeccion f, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        Pageable pageable = PageRequest.of(Math.max(0, f.pagina() - 1), tamano(f.tamano()));
        boolean porUltimoContacto = f.orden() != null && "ultimo_contacto".equalsIgnoreCase(f.orden().trim());

        // idBrokerSupervisor acota al EQUIPO de ese broker, ademas del alcance
        // del actor. Si el broker no supervisa a nadie, el resultado tiene que
        // ser vacio —no "sin filtro"—, de ahi el centinela en vez de una lista
        // vacia, que en un IN de JPQL es un error de sintaxis.
        boolean filtrarPorBroker = f.idBrokerSupervisor() != null && f.idBrokerSupervisor() > 0;
        List<Long> agentesDelBroker = filtrarPorBroker
                ? sinVacios(supervisiones.agentesSupervisados(alcance.idOrganizacion(),
                        f.idBrokerSupervisor()))
                : List.of(-1L);

        String estado = normalizarEstado(f.estado());
        Page<Prospeccion> page = porUltimoContacto
                ? prospecciones.buscarPorUltimoContacto(alcance.idOrganizacion(), alcance.global(),
                    alcance.paramRoles(), estado, vacioNull(f.distrito()),
                    f.idCaptacion(), f.idLocal(), f.idAgente(), filtrarPorBroker, agentesDelBroker,
                    vacioNull(f.q()), pageable)
                : prospecciones.buscar(alcance.idOrganizacion(), alcance.global(),
                    alcance.paramRoles(), estado, vacioNull(f.distrito()),
                    f.idCaptacion(), f.idLocal(), f.idAgente(), filtrarPorBroker, agentesDelBroker,
                    vacioNull(f.q()), pageable);
        return new Pagina<>(page.getContent().stream().map(ProspeccionServiceImpl::ficha).toList(),
                page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaProspeccion> recontactar(int dias, int pagina, int tamano, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        LocalDate limite = LocalDate.now().minusDays(Math.max(0, dias));
        Page<Prospeccion> page = prospecciones.recontactables(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles(), limite, PageRequest.of(Math.max(0, pagina - 1), tamano(tamano)));
        return new Pagina<>(page.getContent().stream().map(ProspeccionServiceImpl::ficha).toList(),
                page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public FichaProspeccion obtener(long id, Actor actor) {
        return ficha(cargarConAcceso(id, actor));
    }

    @Override
    @Transactional
    public FichaProspeccion registrar(DatosProspeccion datos, Actor actor) {
        if (datos == null || datos.idLocal() == null || datos.idLocal() <= 0) {
            throw new ReglaNegocioException("El local de la prospeccion es obligatorio.");
        }
        DetalleAgente agente = agentes.findById(actor.idRolOperativo())
                .orElseThrow(() -> new ReglaNegocioException("Agente no encontrado para prospeccion."));
        Propiedad propiedad = propiedades
                .findByOrganizacionIdAndId(actor.idOrganizacion(), datos.idLocal())
                .orElseThrow(() -> new ReglaNegocioException("El local de la prospeccion no existe."));

        Prospeccion prospeccion = new Prospeccion();
        prospeccion.setOrganizacionId(actor.idOrganizacion());
        prospeccion.setPropiedad(propiedad);
        prospeccion.setAgente(agente);
        prospeccion.setObservaciones(datos.observaciones());
        prospeccion.setCodigoProspeccion(generarCodigoProspeccion(actor.idOrganizacion()));
        transiciones.iniciar(prospeccion, Prospeccion.PROSPECTO);
        prospecciones.save(prospeccion);
        return fichaDe(prospeccion.getId(), actor);
    }

    @Override
    @Transactional
    public FichaProspeccion contactar(long id, Actor actor) {
        Prospeccion p = cargarEnProceso(id, actor, "contactar");
        p.marcarContacto(LocalDate.now());
        transiciones.aplicar(p, p.getId(), Prospeccion.CONTACTADO, actor, "Contacto inicial con el propietario.");
        return ficha(p);
    }

    @Override
    @Transactional
    public FichaProspeccion registrarReunion(long id, Actor actor) {
        Prospeccion p = cargarEnProceso(id, actor, "registrar la reunion de");
        p.marcarReunion(LocalDate.now());
        transiciones.aplicar(p, p.getId(), Prospeccion.REUNION, actor, "Reunion registrada con el propietario.");
        return ficha(p);
    }

    @Override
    @Transactional
    public FichaProspeccion entregarPropuesta(long id, Actor actor) {
        Prospeccion p = cargarEnProceso(id, actor, "entregar la propuesta de");
        p.marcarPropuesta(LocalDate.now());
        transiciones.aplicar(p, p.getId(), Prospeccion.EN_SEGUIMIENTO, actor,
                "Propuesta entregada al propietario.");
        return ficha(p);
    }

    @Override
    @Transactional
    public FichaProspeccion registrarSeguimiento(long id, Actor actor) {
        Prospeccion p = cargarEnProceso(id, actor, "registrar el seguimiento de");
        p.marcarSeguimiento(LocalDate.now());
        transiciones.aplicar(p, p.getId(), Prospeccion.EN_SEGUIMIENTO, actor,
                "Accion de seguimiento con el propietario.");
        return ficha(p);
    }

    @Override
    @Transactional
    public FichaProspeccion rechazar(long id, String motivo, Actor actor) {
        Prospeccion p = cargarEnProceso(id, actor, "rechazar");
        p.marcarRechazoDelPropietario(motivo);
        transiciones.aplicar(p, p.getId(), Prospeccion.DESCARTADO, actor, motivoCierre(motivo, "Propuesta rechazada."));
        return ficha(p);
    }

    @Override
    @Transactional
    public FichaProspeccion descartar(long id, String motivo, Actor actor) {
        Prospeccion p = cargarEnProceso(id, actor, "descartar");
        p.marcarDescartePorAgente(motivo);
        transiciones.aplicar(p, p.getId(), Prospeccion.DESCARTADO, actor, motivoCierre(motivo, "Prospeccion descartada."));
        return ficha(p);
    }

    @Override
    @Transactional
    public FichaProspeccion captar(long id, BigDecimal comisionPactada, Actor actor) {
        CondicionesEconomicas.comisionPactada(comisionPactada);
        Prospeccion p = cargarEnProceso(id, actor, "captar");

        Captacion captacion = new Captacion();
        captacion.setOrganizacionId(actor.idOrganizacion());
        captacion.setCodigoCaptacion(generarCodigoCaptacion(actor.idOrganizacion()));
        LocalDate inicioEncargo = LocalDate.now();
        captacion.setFechaCaptacion(inicioEncargo);
        // El periodo del encargo es obligatorio SIEMPRE (decision de equipo,
        // 2026-08-01). La v1 dejaba nacer el borrador sin fechas y solo las
        // exigia al activar; aqui se completa con el defecto de la casa —el
        // mismo que propone el formulario Angular— para que ningun camino de la
        // v2 produzca una captacion sin periodo. El agente puede cambiarlo con
        // PUT /captaciones/{id} mientras siga PENDIENTE u OBSERVADA. Divergencia
        // de DATOS con la v1, no de contrato: el request y la respuesta de
        // /captar no cambian.
        captacion.setFechaInicioVigencia(inicioEncargo);
        captacion.setFechaFinVigencia(PoliticaComercial.finDelEncargo(inicioEncargo));
        captacion.setPropiedad(p.getPropiedad());
        captacion.setAgente(p.getAgente());
        CondicionEconomicaCaptacion condicion = new CondicionEconomicaCaptacion();
        condicion.setOrganizacionId(actor.idOrganizacion());
        condicion.setTipoOperacion(CondicionEconomicaCaptacion.ARRENDAMIENTO);
        condicion.setImporteReferencia(p.getPropiedad().getPrecioReferencial());
        condicion.setMonedaReferencia(CondicionesEconomicas.moneda(
                p.getPropiedad().getMonedaReferencial(), "de referencia"));
        condicion.setTipoComision(CondicionEconomicaCaptacion.EQUIVALENTE_MENSUALIDADES);
        condicion.setBaseCalculo(CondicionEconomicaCaptacion.RENTA_MENSUAL);
        condicion.setValorComision(CondicionesEconomicas.comisionPactada(comisionPactada)
                .divide(BigDecimal.valueOf(100)));
        condicion.setMonedaComision(condicion.getMonedaReferencia());
        condicion.setTratamientoIgv(CondicionEconomicaCaptacion.IGV_NO_APLICA);
        captacion.setCondicionEconomica(condicion);
        // La operacion del encargo se toma de SU condicion economica, que es
        // quien acaba de declararla tres lineas mas arriba. Escribirla dos veces
        // seria dos sitios que pueden divergir, y el trigger
        // `tg_captacion_operacion_coherente` (V50) existe justamente porque
        // divergen: rechaza un encargo cuya operacion no coincide con la de su
        // condicion.
        //
        // Este camino —captar desde una prospeccion— es el NORMAL, y dependia
        // del defecto `= "A"` de la entidad para rellenar `motivo_operacion`.
        // Al retirarlo (D-E4-1) dejo de escribirse y la columna es NOT NULL:
        // captar desde una prospeccion fallaba entero. Lo encontro `f4-solicitud`.
        captacion.setMotivoOperacion(condicion.getTipoOperacion());
        transiciones.iniciar(captacion, Captacion.PENDIENTE_REVISION);
        captaciones.save(captacion);

        // 3.5 CORREGIDO (2026-08-08). Aqui NO se emitia nada, y era el bug mas
        // silencioso del lote: este metodo construye la captacion a mano en vez
        // de pasar por `CaptacionServiceImpl.registrar`, que es donde vive el
        // aviso. Como captar desde una prospeccion es el camino NORMAL, el
        // resultado era que el broker casi nunca se enteraba de que tenia una
        // captacion esperando su revision — quedaba PENDIENTE_REVISION sin que
        // nadie lo supiera hasta que alguien mirara la bandeja por su cuenta.
        //
        // Se emite el MISMO tipo y severidad que el otro camino: para quien la
        // recibe es el mismo hecho, y dos tipos distintos para "hay una
        // captacion que revisar" solo obligarian a tratarlos por separado en la
        // campana. Lo que cambia es el texto, porque el origen sí es distinto.
        if (captacion.getId() != null && p.getAgente() != null && p.getAgente().getId() != null) {
            alertas.emitir(new AlertaService.DatosAlerta(Alerta.CAPTACION_CREADA, Alerta.MEDIA,
                    "CAPTACION", captacion.getId(), p.getAgente().getId(),
                    "El propietario acepto la propuesta: la captacion "
                            + captacion.getCodigoCaptacion() + " espera tu revision."), actor);
        }

        p.marcarAceptada(captacion);
        transiciones.aplicar(p, p.getId(), Prospeccion.CAPTADO, actor,
                "Propietario acepto; captacion " + captacion.getCodigoCaptacion() + " creada.");
        return ficha(p);
    }

    @Override
    @Transactional
    public FichaProspeccion marcarCaptado(long id, Long idCaptacion, String codigoCaptacion, Actor actor) {
        if (idCaptacion == null && (codigoCaptacion == null || codigoCaptacion.isBlank())) {
            throw new ReglaNegocioException("La captacion creada es obligatoria.");
        }
        Prospeccion p = cargarConAcceso(id, actor);

        Captacion captacion = (idCaptacion != null
                ? captaciones.buscarFicha(actor.idOrganizacion(), idCaptacion)
                : captaciones.buscarFichaPorCodigo(actor.idOrganizacion(), codigoCaptacion.trim()))
                .filter(c -> c.getAgente().getId().equals(actor.idRolOperativo()))
                .orElseThrow(() -> new NoEncontradoException("Captacion"));

        if (p.getPropiedad() == null || !p.getPropiedad().getId().equals(captacion.getPropiedad().getId())) {
            throw new ReglaNegocioException("La captacion no corresponde al local de la prospeccion.");
        }

        p.marcarAceptada(captacion);
        transiciones.aplicar(p, p.getId(), Prospeccion.CAPTADO, actor,
                "Marcada como captada: " + captacion.getCodigoCaptacion() + ".");
        return ficha(p);
    }

    // ------------------------------------------------------------------
    // Carga con alcance + guardas (mensajes identicos a la v1).
    // ------------------------------------------------------------------

    private Prospeccion cargarConAcceso(long id, Actor actor) {
        Prospeccion p = prospecciones.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Prospeccion"));
        if (!alcances.alcanza(actor, p.getAgente().getId())) {
            throw new AccesoNoAutorizadoException();
        }
        return p;
    }

    private Prospeccion cargarEnProceso(long id, Actor actor, String accion) {
        Prospeccion p = cargarConAcceso(id, actor);
        if (!p.enProceso()) {
            throw new ReglaNegocioException(
                    "No se puede " + accion + " una prospeccion " + descripcion(p.estadoActual()) + ".");
        }
        return p;
    }

    private FichaProspeccion fichaDe(long id, Actor actor) {
        return ficha(prospecciones.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Prospeccion")));
    }

    // Correlativos por organizacion (V6.3): el codigo es unico DENTRO del
    // tenant, asi que cada corredora numera su propia serie desde 0001.

    private String generarCodigoProspeccion(long idOrganizacion) {
        return String.format("PRO-%04d", prospecciones.countByOrganizacionId(idOrganizacion) + 1);
    }

    private String generarCodigoCaptacion(long idOrganizacion) {
        return String.format("CAP-%04d", captaciones.countByOrganizacionId(idOrganizacion) + 1);
    }

    private static String descripcion(String estado) {
        return DESCRIPCION_ESTADO.getOrDefault(estado, estado);
    }

    private static String motivoCierre(String motivo, String porDefecto) {
        return motivo != null && !motivo.isBlank() ? motivo : porDefecto;
    }

    /** Tamano de pagina acotado a [1,100], como SeguridadRest.tamano de la v1. */
    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }

    private static String vacioNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    /**
     * `GESTION` en mayusculas, el resto tal cual. La v1 compara el estado sin
     * distinguir mayusculas ({@code equalsIgnoreCase}), asi que un
     * {@code estado=gestion} tiene que funcionar igual.
     */
    private static String normalizarEstado(String estado) {
        String limpio = vacioNull(estado);
        return limpio != null && "GESTION".equalsIgnoreCase(limpio.trim()) ? "GESTION" : limpio;
    }

    /** Nunca una lista vacia: en un IN de JPQL es un error de sintaxis. */
    private static List<Long> sinVacios(List<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of(-1L) : ids;
    }

    /** Mapea la entidad al contrato congelado; usa las asociaciones ya cargadas. */
    private static FichaProspeccion ficha(Prospeccion p) {
        Propiedad prop = p.getPropiedad();
        DetalleLocalComercial detalle = prop != null ? prop.getDetalleLocal() : null;
        DetalleAgente agente = p.getAgente();
        Captacion captacion = p.getCaptacion();
        return new FichaProspeccion(
                p.getId(), p.getCodigoProspeccion(),
                prop != null ? prop.getId() : null,
                prop != null ? prop.getCodigo() : null,
                prop != null ? prop.getDireccion() : null,
                prop != null ? prop.getDistrito() : null,
                prop != null ? prop.getMetraje() : null,
                detalle != null ? detalle.getRubroPermitido() : null,
                prop != null ? prop.getPrecioReferencial() : null,
                prop != null ? prop.getMonedaReferencial() : null,
                nombrePropietario(prop),
                agente != null ? agente.getId() : null,
                nombreAgente(agente),
                p.estadoActual(), p.getResultadoPropuesta(),
                p.getFechaContacto(), p.getFechaReunion(), p.getFechaPropuesta(), p.getFechaRecontacto(),
                p.getObservaciones(),
                captacion != null ? captacion.getId() : null,
                captacion != null ? captacion.getCodigoCaptacion() : null,
                prop != null ? prop.estadoLegado() : null);
    }

    private static String nombrePropietario(Propiedad prop) {
        if (prop == null || prop.getRolPropietario() == null || prop.getRolPropietario().getPersona() == null) {
            return null;
        }
        return prop.getRolPropietario().getPersona().getNombresORazonSocial();
    }

    private static String nombreAgente(DetalleAgente agente) {
        PersonaRol rol = agente != null ? agente.getRol() : null;
        if (rol == null || rol.getPersona() == null) {
            return null;
        }
        return rol.getPersona().getNombresORazonSocial();
    }
}
