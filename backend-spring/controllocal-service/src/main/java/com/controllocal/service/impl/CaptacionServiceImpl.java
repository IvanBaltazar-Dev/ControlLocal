package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.domain.comercial.ReasignacionCaptacion;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.FotoPropiedad;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.FotoPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.ReasignacionCaptacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.CaptacionService;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValoresDePropiedad;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.OperacionDelEncargo;
import com.controllocal.service.soporte.PoliticaComercial;
import com.controllocal.service.soporte.Transiciones;
import com.controllocal.service.soporte.CondicionesEconomicas;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Reglas y mensajes calcados del CaptacionBusinessLogicImpl v1. La revision del
 * broker exige observacion en O/R (MEJ-03), la reasignacion es un evento de
 * actor (no transicion) y todo cambio de estado se audita en historial_estado
 * (Transiciones). Alcance por rol en el service (RC-001).
 *
 * <p>Emite las <b>tres alertas</b> de esta vertical (§4 del contrato F6):
 * {@code CAPTACION_CREADA} al broker cuando el agente registra,
 * {@code CAPTACION_REVISADA} al agente con la decision y
 * {@code CAPTACION_CERRADA} al agente al cerrar. Las tres cuelgan del AGENTE
 * aunque una este escrita para el broker: quien la lee lo decide el TIPO.
 */
@Service
public class CaptacionServiceImpl implements CaptacionService {

    private final CaptacionRepository captaciones;
    private final ReasignacionCaptacionRepository reasignaciones;
    private final PropiedadRepository propiedades;
    private final DetalleAgenteRepository agentes;
    private final DetalleBrokerRepository brokers;
    private final FotoPropiedadRepository fotos;
    private final Alcances alcances;
    private final Transiciones transiciones;
    private final AlertaService alertas;
    private final LectorPorAutoridad lector;

    public CaptacionServiceImpl(CaptacionRepository captaciones, ReasignacionCaptacionRepository reasignaciones,
                                PropiedadRepository propiedades, DetalleAgenteRepository agentes,
                                DetalleBrokerRepository brokers, FotoPropiedadRepository fotos,
                                Alcances alcances, Transiciones transiciones,
                                AlertaService alertas, LectorPorAutoridad lector) {
        this.captaciones = captaciones;
        this.reasignaciones = reasignaciones;
        this.propiedades = propiedades;
        this.agentes = agentes;
        this.brokers = brokers;
        this.fotos = fotos;
        this.alcances = alcances;
        this.transiciones = transiciones;
        this.alertas = alertas;
        this.lector = lector;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaCaptacion> listar(FiltrosCaptacion filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        Long idAgente = filtros.idAgente() != null && filtros.idAgente() > 0
                ? filtros.idAgente()
                : null;
        Page<Captacion> page = captaciones.buscar(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles(), enBlancoANull(filtros.estado()), idAgente,
                enBlancoANull(filtros.q()),
                PageRequest.of(Math.max(0, filtros.pagina() - 1), tamano(filtros.tamano())));
        return paginaConPortada(page);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<PropiedadEquipo> carteraDelEquipo(FiltrosEquipo filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        Page<com.controllocal.persistence.query.PropiedadDeEquipo> page = captaciones.carteraDelEquipo(
                alcance.idOrganizacion(), alcance.global(), alcance.paramRoles(),
                enBlancoANull(filtros.texto()), enBlancoANull(filtros.distrito()),
                PageRequest.of(Math.max(0, filtros.pagina() - 1), tamano(filtros.tamano())));
        return new Pagina<>(page.getContent().stream()
                .map(p -> new PropiedadEquipo(p.getIdPropiedad(), p.getIdCaptacion(),
                        p.getCodigoCaptacion(), p.getEstado(), p.getCodigoLocal(), p.getDireccion(),
                        p.getDistrito(), p.getRubro(), p.getAreaM2(), p.getIdAgente(),
                        p.getAgenteNombre()))
                .toList(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenEquipo resumenCarteraDelEquipo(String texto, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return new ResumenEquipo(0, 0, 0, 0);
        }
        // El resumen NO filtra por distrito a proposito: es justamente el
        // desglose que el filtro de distrito acota, igual que /locales/resumen.
        var r = captaciones.resumenCarteraDelEquipo(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles(), enBlancoANull(texto), null);
        return r == null
                ? new ResumenEquipo(0, 0, 0, 0)
                : new ResumenEquipo(valor(r.getPropiedades()), valor(r.getConCaptacionActiva()),
                        valor(r.getAgentesConCartera()), valor(r.getDistritos()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> distritosDelEquipo(String texto, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return List.of();
        }
        return captaciones.distritosDelEquipo(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles(), enBlancoANull(texto), null);
    }

    private static long valor(Long valor) {
        return valor == null ? 0L : valor;
    }

    private static String enBlancoANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaCaptacion> pendientes(FiltrosPendientes filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        Long idAgente = filtros.idAgente() != null && filtros.idAgente() > 0
                ? filtros.idAgente()
                : null;
        Page<Captacion> page = captaciones.pendientes(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles(), enBlancoANull(filtros.estado()), idAgente,
                enBlancoANull(filtros.q()),
                PageRequest.of(Math.max(0, filtros.pagina() - 1), tamano(filtros.tamano())));
        return paginaConPortada(page);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaCaptacion> reasignables(int pagina, int tamano, String q, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        Page<Captacion> page = captaciones.buscar(alcance.idOrganizacion(), alcance.global(),
                alcance.paramRoles(), Captacion.ACTIVA, null, vacioNull(q),
                PageRequest.of(Math.max(0, pagina - 1), tamano(tamano)));
        return paginaConPortada(page);
    }

    @Override
    @Transactional(readOnly = true)
    public FichaCaptacion obtener(long id, Actor actor) {
        return fichaConPortada(cargarConAcceso(id, actor));
    }

    @Override
    @Transactional(readOnly = true)
    public FichaCaptacion obtenerPorCodigo(String codigo, Actor actor) {
        Captacion cap = captaciones
                .buscarFichaPorCodigo(actor.idOrganizacion(), codigo == null ? "" : codigo.trim())
                .orElseThrow(() -> new NoEncontradoException("Captacion"));
        if (!alcances.alcanza(actor, cap.getAgente().getId())) {
            throw new AccesoNoAutorizadoException();
        }
        return fichaConPortada(cap);
    }

    @Override
    @Transactional
    public FichaCaptacion registrar(DatosCaptacion datos, Actor actor) {
        if (datos == null) {
            throw new ReglaNegocioException("Los datos de la captacion son obligatorios.");
        }
        exigirTexto(datos.codigoCaptacion(), "El codigo de captacion");
        if (datos.fechaCaptacion() == null) {
            throw new ReglaNegocioException("La fecha de captacion es obligatoria.");
        }
        if (datos.idLocal() == null || datos.idLocal() <= 0) {
            throw new ReglaNegocioException("El local de la captacion debe ser mayor que cero.");
        }
        DetalleAgente agente = agentes.findById(actor.idRolOperativo())
                .orElseThrow(() -> new ReglaNegocioException("Agente no encontrado."));
        Propiedad propiedad = propiedades
                .findByOrganizacionIdAndId(actor.idOrganizacion(), datos.idLocal())
                .orElseThrow(() -> new ReglaNegocioException("El local de la captacion no existe."));
        validarEncargo(datos.fechaInicioVigencia(), datos.fechaFinVigencia());
        CondicionEconomicaCaptacion condicion = condicion(datos, propiedad, actor.idOrganizacion());
        exigirEncargoLibre(actor.idOrganizacion(), propiedad.getId(),
                OperacionInmobiliaria.deCodigo(condicion.getTipoOperacion()), null);

        Captacion cap = new Captacion();
        cap.setOrganizacionId(actor.idOrganizacion());
        cap.setCodigoCaptacion(datos.codigoCaptacion());
        cap.setFechaCaptacion(datos.fechaCaptacion());
        cap.setFechaInicioVigencia(datos.fechaInicioVigencia());
        cap.setFechaFinVigencia(datos.fechaFinVigencia());
        cap.setCondicionEconomica(condicion);
        cap.setObservaciones(datos.observaciones());
        cap.setMotivoOperacion(datos.motivoOperacion());
        cap.setUrgencia(datos.urgencia());
        cap.setExclusividad(datos.exclusividad());
        cap.setPropiedad(propiedad);
        cap.setAgente(agente);
        transiciones.iniciar(cap, Captacion.PENDIENTE_REVISION);
        captaciones.save(cap);
        // Aviso al broker supervisor (§4 F6, punto 2): tiene una captacion que revisar.
        emitirAlerta(Alerta.CAPTACION_CREADA, Alerta.MEDIA, cap, agente.getId(),
                "El agente registro la captacion " + cap.getCodigoCaptacion()
                        + " para tu revision.", actor);
        return fichaDe(cap.getId(), actor);
    }

    @Override
    @Transactional
    public FichaCaptacion actualizar(long id, DatosCaptacion datos, Actor actor) {
        if (datos == null) {
            throw new ReglaNegocioException("Los datos de la captacion son obligatorios.");
        }
        Captacion cap = cargarConAcceso(id, actor);
        if (!cap.editable()) {
            throw new ReglaNegocioException("Solo se puede editar una captacion pendiente u observada.");
        }
        if (datos.fechaCaptacion() != null) {
            cap.setFechaCaptacion(datos.fechaCaptacion());
        }
        cap.setFechaInicioVigencia(datos.fechaInicioVigencia());
        cap.setFechaFinVigencia(datos.fechaFinVigencia());
        validarEncargo(datos.fechaInicioVigencia(), datos.fechaFinVigencia());
        cap.setCondicionEconomica(condicion(datos, cap.getPropiedad(), actor.idOrganizacion()));
        cap.setObservaciones(datos.observaciones());
        cap.setMotivoOperacion(datos.motivoOperacion());
        cap.setUrgencia(datos.urgencia());
        cap.setExclusividad(datos.exclusividad());

        // Una captacion OBSERVADA que el agente edita vuelve a la cola del broker (O -> P).
        if (Captacion.OBSERVADA.equals(cap.estadoActual())) {
            transiciones.aplicar(cap, id, Captacion.PENDIENTE_REVISION, actor,
                    "Reenvio a revision tras corregir observaciones.");
        }
        captaciones.save(cap);
        return fichaDe(id, actor);
    }

    @Override
    @Transactional
    public FichaCaptacion decidir(long id, String accion, String observacion, Actor actor) {
        if (accion == null || accion.isBlank()) {
            throw new ReglaNegocioException("La decision es obligatoria.");
        }
        Captacion cap = cargarConAcceso(id, actor);
        if (!cap.editable()) {
            throw new ReglaNegocioException("La captacion debe estar pendiente de revision u observada.");
        }
        DetalleBroker broker = brokers.findById(actor.idRolOperativo())
                .orElseThrow(() -> new ReglaNegocioException("Broker no encontrado."));

        switch (accion.trim().toUpperCase(Locale.ROOT)) {
            case "APROBAR", "A" -> {
                exigirEncargoLibre(actor.idOrganizacion(), cap.getPropiedad().getId(),
                        cap.operacion(), cap.getId());
                validarActivacion(cap);
                cap.registrarRevision(broker, observacion);
                transiciones.aplicar(cap, id, Captacion.ACTIVA, actor, "Captacion aprobada por el broker.");
            }
            case "OBSERVAR", "O" -> {
                exigirTexto(observacion, "La observacion de revision");
                cap.registrarRevision(broker, observacion);
                transiciones.aplicar(cap, id, Captacion.OBSERVADA, actor, observacion);
            }
            case "RECHAZAR", "R" -> {
                exigirTexto(observacion, "La observacion de revision");
                cap.registrarRevision(broker, observacion);
                transiciones.aplicar(cap, id, Captacion.RECHAZADA, actor, observacion);
            }
            default -> throw new ReglaNegocioException("Decision no valida.");
        }
        // Aviso al agente (§4 F6, punto 3). La severidad la marca el desenlace
        // —rechazar es ALTA, observar MEDIA, aprobar INFO— y el detalle es
        // literal: ": " + observacion cuando la hay, "." cuando no.
        String detalle = observacion == null || observacion.isBlank() ? "." : ": " + observacion;
        emitirAlerta(Alerta.CAPTACION_REVISADA, severidadRevision(cap.estadoActual()), cap,
                cap.getAgente() != null ? cap.getAgente().getId() : null,
                "Tu captacion " + cap.getCodigoCaptacion() + " fue "
                        + descripcionRevision(cap.estadoActual()) + detalle, actor);
        return fichaDe(id, actor);
    }

    @Override
    @Transactional
    public FichaCaptacion reasignar(long id, Long idAgenteNuevo, String motivo, Actor actor) {
        if (idAgenteNuevo == null || idAgenteNuevo <= 0) {
            throw new ReglaNegocioException("El agente destino es obligatorio.");
        }
        Captacion cap = cargarConAcceso(id, actor);
        // Hasta E1 aqui solo se exigia que el motivo no viniera vacio: la regla
        // de longitud minima vivia SOLO en el formulario de Angular, asi que un
        // POST directo colaba un "ok" en el historial de la captacion.
        String motivoValidado = PoliticaComercial.exigirMotivoDeReasignacion(motivo);
        DetalleAgente agenteNuevo = agentes.findById(idAgenteNuevo)
                .orElseThrow(() -> new ReglaNegocioException("Agente no encontrado."));
        if (!alcances.alcanza(actor, idAgenteNuevo)) {
            throw new ReglaNegocioException("El broker no supervisa al agente responsable de esta operacion.");
        }
        DetalleAgente agenteAnterior = cap.getAgente();
        if (agenteAnterior.getId().equals(idAgenteNuevo)) {
            throw new ReglaNegocioException("La captacion ya esta asignada a ese agente.");
        }
        // D-S0-17 fila 6: reasignar lo conservan los DOS roles con alcances
        // distintos —dentro del equipo es supervision, entre equipos es
        // organigrama—, asi que el autor ya no es necesariamente un broker.
        // Buscarlo siempre entre los brokers fallaba con "Broker no encontrado"
        // en cuanto el actor era un TENANT_ADMIN, cuyo rol operativo es el de
        // gobierno (V35).
        DetalleBroker broker = actor.esBroker()
                ? brokers.findById(actor.idRolOperativo())
                        .orElseThrow(() -> new ReglaNegocioException("Broker no encontrado."))
                : null;

        // Evento de actor (no transicion de estado): cambia el responsable y se
        // conserva en la tabla-evento para el timeline (Doc 5 §7).
        cap.setAgente(agenteNuevo);
        captaciones.save(cap);

        ReasignacionCaptacion evento = new ReasignacionCaptacion();
        evento.setOrganizacionId(cap.getOrganizacionId());
        evento.setCaptacion(cap);
        evento.setAgenteAnterior(agenteAnterior);
        evento.setAgenteNuevo(agenteNuevo);
        evento.setBroker(broker);
        evento.setIdPersonaActor(actor.idPersona());
        evento.setTipoRolActor(actor.rolEfectivo());
        evento.setMotivo(motivoValidado);
        reasignaciones.save(evento);
        return fichaDe(id, actor);
    }

    @Override
    @Transactional
    public FichaCaptacion cerrar(long id, String motivo, Actor actor) {
        Captacion cap = cargarConAcceso(id, actor);
        exigirTexto(motivo, "El motivo de cierre");
        if (!Captacion.ACTIVA.equals(cap.estadoActual())) {
            throw new ReglaNegocioException("La captacion debe estar ACTIVA.");
        }
        cap.cerrar(LocalDate.now(), "M", motivo);
        transiciones.aplicar(cap, id, Captacion.CERRADA, actor, motivo);
        // Aviso al agente (§4 F6, punto 4).
        emitirAlerta(Alerta.CAPTACION_CERRADA, Alerta.MEDIA, cap,
                cap.getAgente() != null ? cap.getAgente().getId() : null,
                "Tu captacion " + cap.getCodigoCaptacion() + " fue cerrada: " + motivo, actor);
        return fichaDe(id, actor);
    }

    @Override
    @Transactional
    public FichaCaptacion cerrarPorContrato(long id, LocalDate fecha, Actor actor, String detalle) {
        Captacion cap = cargarConAcceso(id, actor);
        if (!Captacion.ACTIVA.equals(cap.estadoActual())) {
            throw new ReglaNegocioException("La captacion debe estar ACTIVA.");
        }
        LocalDate efectiva = fecha == null ? LocalDate.now() : fecha;
        cap.cerrar(efectiva, "A", detalle);
        transiciones.aplicar(cap, id, Captacion.CERRADA, actor, detalle, efectiva);
        return fichaDe(id, actor);
    }

    /**
     * Las tres emisiones de esta vertical. Se salta en silencio si falta la
     * captacion o el agente: un aviso no debe tumbar la operacion principal
     * (mismo criterio que la v1).
     */
    private void emitirAlerta(String tipo, String severidad, Captacion cap, Long idAgente,
                              String mensaje, Actor actor) {
        if (cap == null || cap.getId() == null || idAgente == null) {
            return;
        }
        alertas.emitir(new AlertaService.DatosAlerta(tipo, severidad, "CAPTACION", cap.getId(),
                idAgente, mensaje), actor);
    }

    private static String severidadRevision(String estado) {
        return switch (estado) {
            case Captacion.RECHAZADA -> Alerta.ALTA;
            case Captacion.OBSERVADA -> Alerta.MEDIA;
            default -> Alerta.INFO;
        };
    }

    private static String descripcionRevision(String estado) {
        return switch (estado) {
            case Captacion.ACTIVA -> "aprobada";
            case Captacion.OBSERVADA -> "observada";
            case Captacion.RECHAZADA -> "rechazada";
            default -> "revisada";
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichaReasignacion> listarReasignaciones(Actor actor) {
        return reasignaciones.findByOrganizacionIdOrderByIdDesc(actor.idOrganizacion()).stream()
                .map(CaptacionServiceImpl::fichaReasignacion)
                .toList();
    }

    // ------------------------------------------------------------------
    // Carga con alcance + soporte.
    // ------------------------------------------------------------------

    private Captacion cargarConAcceso(long id, Actor actor) {
        Captacion cap = captaciones.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Captacion"));
        if (!alcances.alcanza(actor, cap.getAgente().getId())) {
            throw new AccesoNoAutorizadoException();
        }
        return cap;
    }

    private FichaCaptacion fichaDe(long id, Actor actor) {
        return fichaConPortada(captaciones.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Captacion")));
    }

    private FichaCaptacion fichaConPortada(Captacion cap) {
        String portada = fotos.findByIdPropiedadOrderByOrdenAscIdAsc(cap.getPropiedad().getId()).stream()
                .findFirst().map(FotoPropiedad::getClave).orElse(null);
        return ficha(cap, portada, gobernadosDe(List.of(cap)));
    }

    private Pagina<FichaCaptacion> paginaConPortada(Page<Captacion> page) {
        List<Long> idsPropiedad = page.getContent().stream().map(c -> c.getPropiedad().getId()).distinct().toList();
        var portadas = idsPropiedad.isEmpty() ? java.util.Map.<Long, String>of()
                : fotos.portadas(idsPropiedad).stream()
                    .collect(java.util.stream.Collectors.toMap(f -> f.getIdPropiedad(), f -> f.getClave()));
        Map<Long, ValoresDePropiedad> gobernados = gobernadosDe(page.getContent());
        List<FichaCaptacion> items = page.getContent().stream()
                .map(c -> ficha(c, portadas.get(c.getPropiedad().getId()), gobernados))
                .toList();
        return new Pagina<>(items, page.getTotalElements());
    }

    /**
     * Los valores gobernados de las propiedades de estas captaciones, en una
     * sola consulta.
     *
     * <p>El rubro dejo de tener columna propia en V71 y se lee por autoridad
     * como el resto. En lote y no fila a fila: la lista de captaciones es
     * paginada, y una consulta por fila seria el N+1 que RC-003 quito.
     */
    private Map<Long, ValoresDePropiedad> gobernadosDe(List<Captacion> captacionesDeLaPagina) {
        List<Long> ids = captacionesDeLaPagina.stream()
                .map(Captacion::getPropiedad).filter(Objects::nonNull)
                .map(Propiedad::getId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : lector.gobernadosDeVarias(ids);
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException(campo + " es obligatorio.");
        }
    }

    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }

    private static String vacioNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static FichaCaptacion ficha(Captacion c, String fotoPortadaClave,
                                        Map<Long, ValoresDePropiedad> gobernados) {
        Propiedad prop = c.getPropiedad();
        ValoresDePropiedad valores = prop == null ? ValoresDePropiedad.vacio()
                : LectorPorAutoridad.de(gobernados, prop.getId());
        DetalleAgente agente = c.getAgente();
        DetalleBroker broker = c.getBrokerRevisor();
        CondicionEconomicaCaptacion ce = c.getCondicionEconomica();
        return new FichaCaptacion(
                c.getId(), c.getCodigoCaptacion(), c.getFechaCaptacion(), c.getFechaInicioVigencia(),
                c.getFechaFinVigencia(), c.getComisionPactada(), c.getObservaciones(), c.estadoActual(),
                c.getMotivoOperacion(), c.getUrgencia(), c.getExclusividad(), c.getObservacionRevision(),
                Fechas.local(c.getFechaRevision()),
                prop != null ? prop.getId() : null,
                prop != null ? prop.getDireccion() : null,
                prop != null ? prop.getDistrito() : null,
                prop != null ? prop.getMetraje() : null,
                valores.texto(CatalogoAtributo.CLAVE_RUBRO_PERMITIDO),
                nombrePropietario(prop),
                agente != null ? agente.getId() : null,
                nombrePersona(agente != null ? agente.getRol() : null),
                broker != null ? broker.getId() : null,
                fotoPortadaClave,
                ce != null ? ce.getTipoOperacion() : null,
                ce != null ? ce.getImporteReferencia() : null,
                ce != null ? ce.getMonedaReferencia() : null,
                ce != null ? ce.getTipoComision() : null,
                ce != null ? ce.getBaseCalculo() : null,
                ce != null ? ce.getValorComision() : null,
                ce != null ? ce.getMonedaComision() : null,
                ce != null ? ce.getTratamientoIgv() : null,
                ce != null ? ce.getMotivoSinComision() : null,
                c.getFechaCierre(), c.getMotivoCierre(), c.getDetalleMotivoCierre());
    }

    private static void validarEncargo(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null) {
            throw new ReglaNegocioException("El inicio y fin del encargo son obligatorios.");
        }
        if (!fin.isAfter(inicio)) {
            throw new ReglaNegocioException("La fecha final del encargo debe ser posterior a la inicial.");
        }
    }

    private static void validarActivacion(Captacion cap) {
        validarEncargo(cap.getFechaInicioVigencia(), cap.getFechaFinVigencia());
        if (cap.getCondicionEconomica() == null || cap.getPropiedad() == null || cap.getAgente() == null
                || cap.getExclusividad() == null) {
            throw new ReglaNegocioException(
                    "La captacion no puede activarse sin condicion economica, moneda, exclusividad, propietario, agente y local.");
        }
    }

    private static CondicionEconomicaCaptacion condicion(DatosCaptacion datos, Propiedad propiedad,
                                                           long organizacionId) {
        CondicionEconomicaCaptacion ce = new CondicionEconomicaCaptacion();
        ce.setOrganizacionId(organizacionId);
        // Sin ultimo recurso: si el cuerpo no declara la operacion, no se supone
        // alquiler. `deTexto` da el mismo mensaje que el dominio pero como
        // ReglaNegocioException, que es lo que la web convierte en 400: un
        // IllegalArgumentException escapando de aqui seria un 500 por un dato
        // que el cliente escribio mal.
        String operacion = OperacionDelEncargo
                .deTexto(textoO(datos.tipoOperacion(), datos.motivoOperacion(), null)).codigo();
        BigDecimal referencia = datos.importeReferencia() != null
                ? datos.importeReferencia() : propiedad.getPrecioReferencial();
        String monedaReferencia = CondicionesEconomicas.moneda(
                textoO(datos.monedaReferencia(), propiedad.getMonedaReferencial(), null),
                "de referencia");
        String tipo = textoO(datos.tipoComision(),
                datos.comisionPactada() == null ? null : CondicionEconomicaCaptacion.EQUIVALENTE_MENSUALIDADES,
                null);
        String base = textoO(datos.baseCalculo(), CondicionEconomicaCaptacion.RENTA_MENSUAL, null);
        BigDecimal valor = datos.valorComision();
        if (valor == null && datos.comisionPactada() != null) {
            valor = CondicionesEconomicas.comisionPactada(datos.comisionPactada())
                    .divide(BigDecimal.valueOf(100));
        }
        String monedaComision = textoO(datos.monedaComision(), monedaReferencia, null);
        String igv = textoO(datos.tratamientoIgv(), CondicionEconomicaCaptacion.IGV_NO_APLICA, null);
        if (!List.of("A", "V").contains(operacion) || referencia == null || referencia.signum() < 0
                || !List.of("E", "P", "F").contains(tipo) || valor == null || valor.signum() < 0
                || !List.of("I", "A", "N").contains(igv)) {
            throw new ReglaNegocioException("La condicion economica de la captacion es invalida.");
        }
        boolean combinacionValida = ("E".equals(tipo) && "R".equals(base))
                || ("P".equals(tipo) && List.of("R", "V").contains(base))
                || ("F".equals(tipo) && "N".equals(base));
        if (!combinacionValida) {
            throw new ReglaNegocioException("La combinacion de tipo y base de comision es invalida.");
        }
        if (!"F".equals(tipo) && !monedaReferencia.equals(monedaComision)) {
            throw new ReglaNegocioException("La comision derivada debe usar la moneda de su base.");
        }
        if (valor.signum() == 0 && (datos.motivoSinComision() == null || datos.motivoSinComision().isBlank())) {
            throw new ReglaNegocioException("Una captacion sin comision requiere un motivo expreso.");
        }
        ce.setTipoOperacion(operacion);
        ce.setImporteReferencia(referencia);
        ce.setMonedaReferencia(monedaReferencia);
        ce.setTipoComision(tipo);
        ce.setBaseCalculo(base);
        ce.setValorComision(valor);
        ce.setMonedaComision(CondicionesEconomicas.moneda(monedaComision, "de la comision"));
        ce.setTratamientoIgv(igv);
        ce.setMotivoSinComision(datos.motivoSinComision() == null ? null : datos.motivoSinComision().trim());
        return ce;
    }

    /**
     * <b>Un encargo vivo por (propiedad, OPERACION)</b>, no uno por propiedad.
     *
     * <p>La regla de la v1 era "una propiedad, una captacion activa", y tenia
     * sentido cuando una propiedad solo podia alquilarse. Con venta y alquiler
     * en el modelo, esa regla prohibe justamente el caso que el modelo
     * universal existe para admitir: la misma casa en venta y en alquiler a la
     * vez, cada una con su precio y su historico (D-E4-1).
     *
     * <p>Lo que sigue prohibido — y ahora tambien en estado PENDIENTE y
     * OBSERVADA, no solo ACTIVA — es un segundo encargo de la MISMA operacion.
     * Es la invariante que impone {@code uq_captacion_viva_por_operacion}
     * (V50); esto solo la anticipa con un mensaje que se entiende.
     *
     * @param idExcluir el encargo que se esta aprobando, para que no se
     *                  detecte a si mismo
     */
    private void exigirEncargoLibre(long idOrganizacion, Long idPropiedad,
                                    OperacionInmobiliaria operacion, Long idExcluir) {
        if (idPropiedad == null || operacion == null) {
            return;
        }
        captaciones.encargoVivoDe(idOrganizacion, idPropiedad, operacion.codigo()).stream()
                .filter(otro -> !otro.getId().equals(idExcluir))
                .findFirst()
                .ifPresent(otro -> {
                    throw new ReglaNegocioException(
                            "Esta propiedad ya tiene un encargo de " + operacion.name()
                                    + " vivo (" + otro.getCodigoCaptacion() + "). Cierralo antes de "
                                    + "abrir otro. Un encargo de la OTRA operacion si es posible.");
                });
    }

    private static String textoO(String primero, String segundo, String tercero) {
        if (primero != null && !primero.isBlank()) return primero.trim().toUpperCase(Locale.ROOT);
        if (segundo != null && !segundo.isBlank()) return segundo.trim().toUpperCase(Locale.ROOT);
        return tercero;
    }

    private static FichaReasignacion fichaReasignacion(ReasignacionCaptacion r) {
        Captacion cap = r.getCaptacion();
        Propiedad prop = cap != null ? cap.getPropiedad() : null;
        return new FichaReasignacion(
                r.getId(),
                cap != null ? cap.getId() : null,
                cap != null ? cap.getCodigoCaptacion() : null,
                prop != null ? prop.getDireccion() : null,
                r.getAgenteAnterior() != null ? r.getAgenteAnterior().getId() : null,
                nombrePersona(r.getAgenteAnterior() != null ? r.getAgenteAnterior().getRol() : null),
                r.getAgenteNuevo() != null ? r.getAgenteNuevo().getId() : null,
                nombrePersona(r.getAgenteNuevo() != null ? r.getAgenteNuevo().getRol() : null),
                r.getBroker() != null ? r.getBroker().getId() : null,
                nombrePersona(r.getBroker() != null ? r.getBroker().getRol() : null),
                Fechas.local(r.getFechaCambio()),
                r.getMotivo());
    }

    private static String nombrePropietario(Propiedad prop) {
        if (prop == null || prop.getRolPropietario() == null) {
            return null;
        }
        return nombrePersona(prop.getRolPropietario());
    }

    private static String nombrePersona(PersonaRol rol) {
        if (rol == null || rol.getPersona() == null) {
            return null;
        }
        return rol.getPersona().getNombresORazonSocial();
    }
}
