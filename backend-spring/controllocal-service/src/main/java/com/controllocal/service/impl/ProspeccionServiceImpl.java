package com.controllocal.service.impl;

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
    private final AlertaService alertas;

    /**
     * La serie economica del encargo que nace al captar. Desde V75 el importe
     * autorizado se declara aqui, asi que aqui empieza su historico.
     */
    private final PrecioPropiedadRepository precios;

    public ProspeccionServiceImpl(ProspeccionRepository prospecciones, CaptacionRepository captaciones,
                                  PropiedadRepository propiedades, DetalleAgenteRepository agentes,
                                  Alcances alcances, Transiciones transiciones,
                                  SupervisionAgenteRepository supervisiones,
                                  AlertaService alertas, LectorPorAutoridad lector,
                                  PrecioPropiedadRepository precios) {
        this.prospecciones = prospecciones;
        this.captaciones = captaciones;
        this.propiedades = propiedades;
        this.agentes = agentes;
        this.alcances = alcances;
        this.transiciones = transiciones;
        this.supervisiones = supervisiones;
        this.alertas = alertas;
        this.lector = lector;
        this.precios = precios;
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
    public Pagina<FichaProspeccion> recontactar(int dias, int pagina, int tamano, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        LocalDate limite = LocalDate.now().minusDays(Math.max(0, dias));
        Page<Prospeccion> page = prospecciones.recontactables(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles(), limite, PageRequest.of(Math.max(0, pagina - 1), tamano(tamano)));
        return new Pagina<>(fichas(page.getContent()),
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
    public FichaProspeccion captar(long id, DatosCaptura datos, Actor actor) {
        if (datos == null) {
            throw new ReglaNegocioException("Faltan las condiciones de la captacion.");
        }
        // La OPERACION, primero y sin defecto. Hasta V75 estaba escrita a fuego
        // como ALQUILER y una propiedad captada para venderse nacia con un
        // encargo de alquiler -- sin excepcion, sin aviso, y con su historico
        // economico entero bajo la operacion equivocada.
        OperacionInmobiliaria operacion = operacionDeclarada(datos.operacion());
        BigDecimal importe = importeDeclarado(datos.importe(), operacion);
        String moneda = CondicionesEconomicas.moneda(datos.moneda(), "de la operacion");
        // La comision tambien es parte del COMANDO, asi que se valida antes de
        // tocar nada: "la comision pactada no puede ser negativa" es un mensaje
        // sobre lo que se pidio, y llega igual exista o no la prospeccion.
        CondicionesEconomicas.comisionPactada(datos.comisionPactada());

        Prospeccion p = cargarEnProceso(id, actor, "captar");
        Propiedad propiedad = p.getPropiedad();
        // La invariante que V50 escribio en el indice, dicha en palabras. Sin
        // esto llegaba como violacion de integridad de PostgreSQL, que no le
        // explica a nadie que la OTRA operacion si se puede abrir.
        exigirEncargoLibre(actor.idOrganizacion(), propiedad, operacion);

        Captacion captacion = new Captacion();
        captacion.setOrganizacionId(actor.idOrganizacion());
        captacion.setCodigoCaptacion(generarCodigoCaptacion(actor.idOrganizacion()));
        LocalDate inicioEncargo = datos.inicioEncargo() != null
                ? datos.inicioEncargo() : LocalDate.now();
        captacion.setFechaCaptacion(LocalDate.now());
        // El periodo del encargo es obligatorio SIEMPRE (decision de equipo,
        // 2026-08-01). La v1 dejaba nacer el borrador sin fechas y solo las
        // exigia al activar; aqui se completa con el defecto de la casa -el
        // mismo que propone el formulario Angular- para que ningun camino de la
        // v2 produzca una captacion sin periodo. El agente puede cambiarlo con
        // PUT /captaciones/{id} mientras siga PENDIENTE u OBSERVADA.
        captacion.setFechaInicioVigencia(inicioEncargo);
        captacion.setFechaFinVigencia(datos.finEncargo() != null
                ? datos.finEncargo() : PoliticaComercial.finDelEncargo(inicioEncargo));
        captacion.setExclusividad(datos.exclusividad() != null ? datos.exclusividad() : Boolean.FALSE);
        captacion.setPropiedad(propiedad);
        captacion.setAgente(p.getAgente());

        CondicionEconomicaCaptacion condicion = new CondicionEconomicaCaptacion();
        condicion.setOrganizacionId(actor.idOrganizacion());
        condicion.setTipoOperacion(operacion.codigo());
        // El importe y la moneda los declara QUIEN CAPTA. Antes se copiaban de
        // `propiedad.precio_referencial`, que es la proyeccion de otro encargo y
        // desde V75 puede estar vacia: tomarla convertia un dato de registro en
        // precio autorizado sin que nadie lo autorizara.
        condicion.setImporteReferencia(importe);
        condicion.setMonedaReferencia(moneda);
        // La base la implica la operacion, y es la misma regla que aplica el
        // alta al abrir un encargo: una comision de venta calculada sobre
        // "renta mensual" trataria un precio de venta como si fuera un alquiler.
        condicion.setTipoComision(datos.tipoComision() != null
                ? datos.tipoComision() : tipoComisionDe(operacion));
        condicion.setBaseCalculo(datos.baseCalculo() != null
                ? datos.baseCalculo() : baseDe(operacion));
        condicion.setValorComision(valorDeComision(datos.comisionPactada(),
                condicion.getTipoComision()));
        condicion.setMonedaComision(moneda);
        condicion.setTratamientoIgv(datos.tratamientoIgv() != null
                ? datos.tratamientoIgv() : CondicionEconomicaCaptacion.IGV_NO_APLICA);
        // `ck_condicion_sin_comision` exige que una comision de CERO diga por
        // que. La regla es buena y no se rodea.
        if (condicion.getValorComision().signum() == 0) {
            condicion.setMotivoSinComision(
                    "Comision no pactada al captar; se define antes de activar el encargo.");
        }
        captacion.setCondicionEconomica(condicion);
        // La operacion del encargo se toma de SU condicion economica, que es
        // quien acaba de declararla. Escribirla dos veces desde fuentes
        // distintas seria dos sitios que pueden divergir, y el trigger
        // `tg_captacion_operacion_coherente` (V50) existe justamente por eso.
        captacion.setMotivoOperacion(condicion.getTipoOperacion());
        transiciones.iniciar(captacion, Captacion.PENDIENTE_REVISION);
        captaciones.save(captacion);

        // La columna espejo sigue lo que lee el cable heredado. Si la propiedad
        // llega sin precio -registrada para prospectarla, V75- este encargo es
        // el primero que se lo da; si ya lo tenia, manda el alquiler, que es lo
        // que media docena de sitios llaman "renta referencial".
        if (propiedad.getPrecioReferencial() == null
                || operacion == OperacionInmobiliaria.ALQUILER) {
            propiedad.setPrecioReferencial(importe);
            propiedad.setMonedaReferencial(moneda);
        }

        // Y la propiedad entra en el mercado: es el ENCARGO el que la pone en
        // oferta, no el alta (V75). Si ya estaba alquilada o retirada, no se
        // toca. Va por Transiciones para que quede en su expediente: «entra al
        // mercado» es el hecho comercial mas importante de una propiedad.
        if (!propiedad.estaOfrecida()) {
            transiciones.aplicarDisponibilidad(propiedad, propiedad.getId(),
                    EstadosDominio.DisponibilidadComercial.DISPONIBLE, actor,
                    "Entra al mercado: el propietario acepto y nacio el encargo "
                            + captacion.getCodigoCaptacion() + ".");
        }
        propiedades.save(propiedad);

        // El importe autorizado abre la serie economica de ESTE encargo, igual
        // que en el alta comercial. Sin esto, un encargo nacido de prospeccion
        // no tendria historico y su ficha empezaria en blanco.
        precios.save(PrecioPropiedad.hito(actor.idOrganizacion(), propiedad.getId(), operacion,
                        PrecioPropiedad.HITO_AUTORIZADO, moneda, importe, LocalDate.now())
                .delEncargo(captacion.getId()));

        // 3.5 CORREGIDO (2026-08-08). Aqui NO se emitia nada, y era el bug mas
        // silencioso del lote: este metodo construye la captacion a mano en vez
        // de pasar por `CaptacionServiceImpl.registrar`, que es donde vive el
        // aviso. Como captar desde una prospeccion es el camino NORMAL, el
        // resultado era que el broker casi nunca se enteraba de que tenia una
        // captacion esperando su revision.
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

    private static BigDecimal importeDeclarado(BigDecimal importe, OperacionInmobiliaria operacion) {
        if (importe == null || importe.signum() < 0) {
            throw new ReglaNegocioException(
                    "Falta el " + operacion.nombreDelImporte() + " del encargo. Una propiedad que "
                            + "solo se prospectaba no tiene precio del que tirar: lo trae quien "
                            + "capta, porque es lo que el propietario acaba de aceptar.");
        }
        return importe;
    }

    private static String tipoComisionDe(OperacionInmobiliaria operacion) {
        return operacion == OperacionInmobiliaria.VENTA
                ? CondicionEconomicaCaptacion.PORCENTAJE
                : CondicionEconomicaCaptacion.EQUIVALENTE_MENSUALIDADES;
    }

    private static String baseDe(OperacionInmobiliaria operacion) {
        return operacion == OperacionInmobiliaria.VENTA
                ? CondicionEconomicaCaptacion.PRECIO_VENTA
                : CondicionEconomicaCaptacion.RENTA_MENSUAL;
    }

    /**
     * El porcentaje pactado, en la unidad que su tipo espera.
     *
     * <p>{@code comisionPactada} viaja como porcentaje -lo llevaba asi el
     * contrato heredado- y tanto EQUIVALENTE_MENSUALIDADES como PORCENTAJE lo
     * guardan como fraccion: 100 % es una mensualidad, 5 % es 0,05 del precio.
     */
    private static BigDecimal valorDeComision(BigDecimal comisionPactada, String tipoComision) {
        if (CondicionEconomicaCaptacion.MONTO_FIJO.equals(tipoComision)) {
            return CondicionesEconomicas.comisionPactada(comisionPactada);
        }
        return CondicionesEconomicas.comisionPactada(comisionPactada)
                .divide(BigDecimal.valueOf(100));
    }

    /**
     * Un encargo vivo por operacion, dicho con palabras y no con un 23505.
     *
     * <p>Es la misma guarda que {@code CaptacionServiceImpl}, y hasta V75 aqui
     * no hacia falta: toda propiedad nacia con su encargo, asi que captar
     * chocaba SIEMPRE. Ahora que el encargo nace aqui, la invariante vuelve a
     * significar lo que decia -- y su mensaje tiene que explicar que la OTRA
     * operacion si se puede abrir.
     */
    private void exigirEncargoLibre(long idOrganizacion, Propiedad propiedad,
                                    OperacionInmobiliaria operacion) {
        if (propiedad == null || propiedad.getId() == null) {
            return;
        }
        captaciones.encargoVivoDe(idOrganizacion, propiedad.getId(), operacion.codigo()).stream()
                .findFirst()
                .ifPresent(otro -> {
                    throw new ReglaNegocioException(
                            "Esta propiedad ya tiene un encargo de " + operacion.name()
                                    + " vivo (" + otro.getCodigoCaptacion() + "). Cierralo antes de "
                                    + "abrir otro. Un encargo de la OTRA operacion si es posible.");
                });
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
