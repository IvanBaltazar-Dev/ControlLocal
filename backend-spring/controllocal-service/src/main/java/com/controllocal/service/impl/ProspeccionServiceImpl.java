package com.controllocal.service.impl;

import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.actividad.SeguimientoDeProspeccion;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comun.EstadosDominio;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.domain.comercial.Prospeccion;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
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
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValoresGobernados;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.PoliticaComercial;
import com.controllocal.service.soporte.TitularParaEncargar;
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
import java.util.Objects;

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
    private final LectorPorAutoridad lector;
    private final Transiciones transiciones;

    private final SupervisionAgenteRepository supervisiones;

    /**
     * Para avisar al broker de la captacion que nace aqui. Ver {@link #captar}:
     * este era el camino por el que la alerta NO salia.
     */

    /**
     * La serie economica del encargo que nace al captar. Desde V75 el importe
     * autorizado se declara aqui, asi que aqui empieza su historico.
     */
    private final EncargoService encargos;
    private final SeguimientoDeProspeccion seguimiento;

    public ProspeccionServiceImpl(ProspeccionRepository prospecciones, CaptacionRepository captaciones,
                                  PropiedadRepository propiedades, DetalleAgenteRepository agentes,
                                  Alcances alcances, Transiciones transiciones,
                                  SupervisionAgenteRepository supervisiones,
                                  LectorPorAutoridad lector,
                                  EncargoService encargos,SeguimientoDeProspeccion seguimiento) {
        this.prospecciones = prospecciones;
        this.captaciones = captaciones;
        this.propiedades = propiedades;
        this.agentes = agentes;
        this.alcances = alcances;
        this.transiciones = transiciones;
        this.supervisiones = supervisiones;
        this.lector = lector;
        this.encargos = encargos;
        this.seguimiento=seguimiento;
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
        return new Pagina<>(fichas(page.getContent()),
                page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaProspeccion> recontactar(int pagina,int tamano,Actor actor) {
        var ids=seguimiento.vencidas(actor).stream().map(RevisionSeguimiento::idProspeccion).distinct().sorted(java.util.Comparator.reverseOrder()).toList();
        int desde=(int)Math.min(ids.size(),(long)(Math.max(1,pagina)-1)*tamano(tamano));
        var seleccion=ids.subList(desde,Math.min(ids.size(),desde+tamano(tamano)));
        var filas=seleccion.stream().map(id->cargarConAcceso(id,actor)).toList();return new Pagina<>(fichas(filas),ids.size());
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
        prospecciones.saveAndFlush(prospeccion);seguimiento.iniciar(prospeccion.getId(),datos.destinatarioDeclarado(),actor);
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

    @Override @Transactional(readOnly=true)
    public List<RevisionSeguimiento> seguimientos(long id,Actor actor){return seguimiento.revisar(id,actor);}
    @Override @Transactional(readOnly=true)
    public void exigirLecturaRevisionSeguimiento(RevisionSeguimiento revision,Actor actor){seguimiento.exigirLecturaOriginal(revision,actor);}
    @Override @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public RevisionSeguimiento planificarSeguimiento(long id,long idSeguimiento,PlanificacionSeguimiento d,Actor actor,ContextoSesion sid,Procedencia proc){return seguimiento.planificar(id,idSeguimiento,d,actor,sid,proc);}
    @Override @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public RevisionSeguimiento posponerSeguimiento(long id,long idSeguimiento,PosposicionSeguimiento d,Actor actor,ContextoSesion sid,Procedencia proc){return seguimiento.posponer(id,idSeguimiento,d,actor,sid,proc);}

    @Override
    @Transactional
    public FichaProspeccion rechazar(long id, String motivo, Actor actor) {
        Prospeccion p = cargarEnProceso(id, actor, "rechazar");
        p.marcarRechazoDelPropietario(motivo);
        transiciones.aplicar(p, p.getId(), Prospeccion.DESCARTADO, actor, motivoCierre(motivo, "Propuesta rechazada."));
        prospecciones.flush();seguimiento.actualizarDesde(java.util.Set.of(id),actor);
        return ficha(p);
    }

    @Override
    @Transactional
    public FichaProspeccion descartar(long id, String motivo, Actor actor) {
        Prospeccion p = cargarEnProceso(id, actor, "descartar");
        p.marcarDescartePorAgente(motivo);
        transiciones.aplicar(p, p.getId(), Prospeccion.DESCARTADO, actor, motivoCierre(motivo, "Prospeccion descartada."));
        prospecciones.flush();seguimiento.actualizarDesde(java.util.Set.of(id),actor);
        return ficha(p);
    }

    @Override
    @Transactional
    public FichaProspeccion captar(long id, DatosCaptura datos, Actor actor) {
        if (datos == null) throw new ReglaNegocioException("Faltan las condiciones de la captacion.");
        OperacionInmobiliaria operacion = operacionDeclarada(datos.operacion());
        Prospeccion p = cargarEnProceso(id, actor, "captar");
        EncargoService.Condiciones pacto = new EncargoService.Condiciones(
                datos.importe(), datos.moneda(), operacion == OperacionInmobiliaria.ALQUILER ? "MENSUAL" : null,
                datos.tipoComision(), datos.baseCalculo(), valorDeComision(datos.comisionPactada(), datos.tipoComision()), null,
                datos.tratamientoIgv(), null, null, null, null, null, null, List.of(), null,null);
        var abierto = encargos.abrir(new EncargoService.DatosApertura(p.getPropiedad().getId(),
                p.getAgente().getId(), null, operacion, datos.inicioEncargo(), datos.finEncargo(),
                datos.exclusividad(), null, pacto, Procedencia.oPantalla(null),List.of(),
                new EncargoService.OrigenProspeccion(EncargoService.TipoOrigenProspeccion.PROSPECCION,p.getId())), actor);
        Captacion captacion = captaciones.buscarFicha(actor.idOrganizacion(), abierto.id())
                .orElseThrow(() -> new IllegalStateException("El Encargo abierto no conserva su identidad."));
        // El resultado aceptado de la prospección no equivale a habilitación del Encargo.
        p.marcarAceptada(captacion);
        transiciones.aplicar(p, p.getId(), Prospeccion.CAPTADO, actor,
                "Propuesta aceptada; preparación de Encargo " + abierto.codigo() + ".");
        prospecciones.flush();seguimiento.actualizarDesde(java.util.Set.of(id),actor);
        return ficha(p);
    }

    /**
     * La operacion, dicha con todas sus letras. Sin defecto y con los dos
     * nombres en el error: "falta la operacion" no le dice a nadie cuales hay.
     */
    private static OperacionInmobiliaria operacionDeclarada(String operacion) {
        if (operacion == null || operacion.isBlank()) {
            throw new ReglaNegocioException(
                    "Declara la operacion del encargo: VENTA o ALQUILER. No se supone ninguna, "
                            + "porque el mismo importe significa un precio de venta o una renta "
                            + "mensual segun cual sea.");
        }
        try {
            return OperacionInmobiliaria.desde(operacion);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(e.getMessage());
        }
    }

    private static BigDecimal valorDeComision(BigDecimal valor, String tipo) {
        if (valor == null) return null;
        CondicionesEconomicas.comisionPactada(valor);
        return CondicionEconomicaCaptacion.MONTO_FIJO.equals(tipo) ? valor : valor.divide(BigDecimal.valueOf(100));
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
        if(!encargos.vincularOrigenProspeccion(captacion.getId(),p.getId(),actor))return ficha(p);
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
    /** Una sola ficha: hidrata sus gobernados y delega. */
    private FichaProspeccion ficha(Prospeccion p) {
        return ficha(p, gobernadosDe(List.of(p)));
    }

    /** Una pagina entera, con UNA consulta de gobernados para todas. */
    private List<FichaProspeccion> fichas(List<Prospeccion> lote) {
        Map<Long, ValoresGobernados> gobernados = gobernadosDe(lote);
        return lote.stream().map(p -> ficha(p, gobernados)).toList();
    }

    /**
     * Los valores gobernados de estas prospecciones, en una sola consulta.
     *
     * <p>El rubro dejo de tener columna propia en V71. En lote y no fila a
     * fila: estas listas son paginadas y una consulta por fila seria el N+1
     * que RC-003 quito.
     */
    private Map<Long, ValoresGobernados> gobernadosDe(List<Prospeccion> lote) {
        List<Long> ids = lote.stream().map(Prospeccion::getPropiedad).filter(Objects::nonNull)
                .map(Propiedad::getId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : lector.gobernadosDeVarias(ids);
    }

    private static FichaProspeccion ficha(Prospeccion p, Map<Long, ValoresGobernados> gobernados) {
        Propiedad prop = p.getPropiedad();
        ValoresGobernados valores = prop == null ? ValoresGobernados.vacio()
                : LectorPorAutoridad.de(gobernados, prop.getId());
        DetalleAgente agente = p.getAgente();
        Captacion captacion = p.getCaptacion();
        return new FichaProspeccion(
                p.getId(), p.getCodigoProspeccion(),
                prop != null ? prop.getId() : null,
                prop != null ? prop.getCodigo() : null,
                prop != null ? prop.getDireccion() : null,
                prop != null ? prop.getDistrito() : null,
                prop != null ? prop.getMetraje() : null,
                valores.texto(CatalogoAtributo.CLAVE_RUBRO_PERMITIDO),
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
                prop != null ? prop.estadoLegado() : null,p.getVersion());
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
