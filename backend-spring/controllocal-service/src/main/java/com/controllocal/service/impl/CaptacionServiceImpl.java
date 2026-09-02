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
import com.controllocal.service.CaptacionService.CandidatoAgente;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.ElegibilidadDeResponsable;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValoresGobernados;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.OperacionDelEncargo;
import com.controllocal.service.soporte.PoliticaComercial;
import com.controllocal.service.soporte.TitularParaEncargar;
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
    /** Conocer un inmueble no es poder venderlo: el ENCARGO si exige titular (V76). */
    private final TitularParaEncargar titularParaEncargar;
    /** Quien puede escribir el TRATO. Ver {@link #actualizar}. */
    private final AutoridadDePropiedad autoridad;
    /**
     * <b>Quien puede RECIBIR el encargo</b> (D-P0-7). El mismo componente que
     * decide los destinos de una propiedad: la pregunta es la misma y una
     * segunda copia divergiria hacia el lado que ofrece lo que el comando
     * rechaza.
     */
    private final ElegibilidadDeResponsable elegibilidad;

    public CaptacionServiceImpl(CaptacionRepository captaciones, ReasignacionCaptacionRepository reasignaciones,
                                PropiedadRepository propiedades, DetalleAgenteRepository agentes,
                                DetalleBrokerRepository brokers, FotoPropiedadRepository fotos,
                                Alcances alcances, Transiciones transiciones,
                                AlertaService alertas, LectorPorAutoridad lector,
                                TitularParaEncargar titularParaEncargar,
                                AutoridadDePropiedad autoridad,
                                ElegibilidadDeResponsable elegibilidad) {
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
        this.titularParaEncargar = titularParaEncargar;
        this.autoridad = autoridad;
        this.elegibilidad = elegibilidad;
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
        return fichaIndividual(cargarConAcceso(id, actor), actor);
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
        // Es la SEGUNDA puerta al mismo recurso, asi que publica lo mismo que la
        // primera: el mismo productor de ficha individual, con las mismas
        // capacidades. Dos formas de leer un encargo que devolvieran formas
        // distintas obligarian al cliente a llevar la regla en una de las dos.
        return fichaIndividual(cap, actor);
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
        // Abrir un encargo es empezar una relacion comercial, y esa nace de
        // alguien que puede encargarla (V76). Va ANTES de la condicion economica
        // para que el mensaje hable de titularidad y no de un importe invalido.
        titularParaEncargar.exigirParaEncargo(propiedad);
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
        Captacion cap = cargarConAccesoParaEscribir(id, actor);
        // P0-4: lo que sigue escribe el TRATO -- importe, exclusividad,
        // vigencia, urgencia y observaciones-- y eso lo edita SU propio agente.
        // `cargarConAccesoParaEscribir` resuelve otra pregunta: el tenant (404)
        // y el alcance de LECTURA, que dice SI al broker supervisor y al
        // gobierno del tenant. Hasta aqui lo unico que los frenaba era el
        // @PreAuthorize del controlador, y una anotacion protege UNA PUERTA
        // mientras la autoridad protege EL HECHO: KAIROS entra por este mismo
        // caso de uso.
        //
        // Y va bajo el candado de la fila (F2.10): comprobar aqui y escribir
        // despues dejaba una ventana donde cabia una reasignacion entera, con
        // lo que esta edicion aterrizaba sobre un encargo que ya llevaba otro.
        autoridad.exigirEdicionDelEncargo(actor, cap);
        if (!cap.editable()) {
            throw new ReglaNegocioException("Solo se puede editar una captacion pendiente u observada.");
        }
        if (datos.fechaCaptacion() != null) {
            cap.setFechaCaptacion(datos.fechaCaptacion());
        }
        cap.setFechaInicioVigencia(datos.fechaInicioVigencia());
        cap.setFechaFinVigencia(datos.fechaFinVigencia());
        validarEncargo(datos.fechaInicioVigencia(), datos.fechaFinVigencia());
        exigirMismaOperacion(cap, datos);
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
        // La BANDA, en el Core y no solo en la anotacion (D-S0-17 fila 5). Hasta
        // aqui lo unico que frenaba al TENANT_ADMIN era el @PreAuthorize del
        // controlador, y una anotacion protege UNA PUERTA mientras la regla tiene
        // que proteger EL HECHO: KAIROS entra por este mismo caso de uso, y
        // `cargarConAcceso` le dice que si -- alcanza el encargo para leerlo.
        //
        // Va DESPUES de `cargarConAcceso` para conservar el 404 de otro tenant: un
        // id ajeno no puede empezar a responder 403, que confirmaria que existe.
        exigirBandaComercial(actor, "decidir sobre un encargo");
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

    /**
     * <b>La UNICA puerta canonica que mueve el agente de un encargo</b>
     * (D-P0-9 y D-P0-10, aplicados al ENCARGO).
     *
     * <h2>Que estaba mal</h2>
     * Reasignar era «pon a B». Dos comandos que salieran del mismo agente A —uno
     * hacia B y otro hacia C— pasaban exactamente las mismas guardas y el
     * segundo pisaba al primero: <b>la ultima escritura ganaba</b>, y
     * {@code reasignacion_captacion} quedaba afirmando «de A a C» sobre un
     * encargo que en ese momento ya llevaba B. Nadie habia decidido eso. Y
     * ademas la columna se movia por <b>dirty checking</b>: bastaba con que una
     * edicion del encargo hubiera cargado la fila antes del traspaso para que su
     * <i>flush</i> la devolviera al agente anterior, sin fila que lo explicara.
     *
     * <h2>Lo que se congela, y lo que NO cambia</h2>
     * Se congela el <b>comportamiento</b>: de un estado concreto parte
     * <b>exactamente una</b> reasignacion legitima; la segunda es <b>409</b> y
     * <b>no se reinterpreta</b> sobre el estado nuevo. Lo que <b>no</b> cambia
     * es <b>quien puede</b>: la banda y el alcance siguen siendo los de D-S0-17
     * fila 6 —BROKER dentro de su equipo, TENANT_ADMIN entre equipos—, ni un rol
     * mas. El responsable de la PROPIEDAD no gana con esto ninguna autoridad
     * sobre el ENCARGO: son dos autoridades distintas y siguen separadas.
     *
     * <h2>El orden de las guardas, que es contrato</h2>
     * <ol>
     *   <li>{@code cargarConAcceso} — tenant (404) y alcance sobre el agente
     *       <b>actual</b>, que es el saliente;</li>
     *   <li>el motivo, con el minimo de {@code PoliticaComercial};</li>
     *   <li>el destino <b>existe en esta organizacion</b>. El filtro de tenant
     *       es nuevo: {@code findById} no lo lleva, asi que un id de otra
     *       corredora llegaba a la comprobacion de alcance en vez de comportarse
     *       como inexistente;</li>
     *   <li>el alcance sobre el <b>destino</b>;</li>
     *   <li>reasignar al mismo agente es 400;</li>
     *   <li>{@link ElegibilidadDeResponsable#exigirElegible} — D-P0-7 se aplica
     *       en <b>toda</b> reasignacion de autoridad, no solo en la de la
     *       propiedad. Alcanzar a un agente no es que ese agente pueda operar:
     *       un supervisado de baja pasa la cuarta y falla esta. Va la ultima de
     *       las guardas y antes de escribir nada, igual que en el traspaso de
     *       propiedad, para no cambiar el motivo de los rechazos ya medidos;</li>
     *   <li>y solo entonces, <b>desde donde</b>.</li>
     * </ol>
     *
     * <h2>Desde donde: dos comprobaciones, dos preguntas</h2>
     * <pre>
     *   (a) EN MEMORIA -&gt; el comando llego obsoleto o equivocado. Se corta sin
     *       escribir y con el agente de HOY en el mensaje, que es lo unico que
     *       permite volver a decidir.
     *   (b) EN LA BASE -&gt; la carrera. Entre (a) y el UPDATE cabe otra
     *       transaccion; dentro del UPDATE no cabe ninguna.
     * </pre>
     * Las dos responden <b>409</b>, porque para quien reasigna el hecho es el
     * mismo: el estado cambio y hay que volver a mirar. Lo que cambia es cuanta
     * informacion se puede dar — la transaccion que pierde la carrera no puede
     * afirmar quien gano.
     *
     * <p><b>Y el rastro dice de donde salio de verdad</b>: el predecesor de la
     * fila es el <b>observado</b>, que despues de (a) y (b) es exactamente el
     * valor que tenia la columna. No se toma «el que estaba cargado», que es la
     * misma cosa por casualidad y dejaria de serlo el dia que algo cambie.
     */
    @Override
    @Transactional
    public FichaCaptacion reasignar(long id, long idAgenteNuevo, String motivo,
                                    long idAgenteObservado, Actor actor) {
        Captacion cap = cargarConAcceso(id, actor);
        // Hasta E1 aqui solo se exigia que el motivo no viniera vacio: la regla
        // de longitud minima vivia SOLO en el formulario de Angular, asi que un
        // POST directo colaba un "ok" en el historial de la captacion.
        String motivoValidado = PoliticaComercial.exigirMotivoDeReasignacion(motivo);
        // FRONTERA DE TENANT, y va antes del alcance. `findById` no filtra por
        // organizacion, asi que un rol de otra corredora entraba aqui y lo unico
        // que lo paraba despues era que el broker no lo supervisara -- con un
        // mensaje que hablaba de supervision sobre un agente que no existe en
        // esta organizacion. Se responde lo mismo que si no existiera en ninguna
        // parte, como ya hace AutoridadDePropiedad.asignar.
        DetalleAgente agenteNuevo = agentes.findById(idAgenteNuevo)
                .filter(a -> Objects.equals(a.getOrganizacionId(), actor.idOrganizacion()))
                .orElseThrow(() -> new ReglaNegocioException("Agente no encontrado."));
        if (!alcances.alcanza(actor, idAgenteNuevo)) {
            throw new ReglaNegocioException("El broker no supervisa al agente responsable de esta operacion.");
        }
        DetalleAgente agenteAnterior = cap.getAgente();
        if (agenteAnterior.getId() == idAgenteNuevo) {
            throw new ReglaNegocioException("La captacion ya esta asignada a ese agente.");
        }
        // D-P0-7 EN TODA REASIGNACION DE AUTORIDAD. Antes esto solo lo exigia el
        // traspaso de la propiedad, y el encargo -que es la otra autoridad
        // mutable de P0- se podia entregar a un agente suspendido, de baja o
        // fuera de la organizacion. Es una pregunta DISTINTA del alcance:
        // aquella dice si el actor llega hasta ese agente, esta dice si ese
        // agente esta en condiciones de responder por trabajo comercial hoy.
        elegibilidad.exigirElegible(actor, idAgenteNuevo);
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

        // (a) La precondicion EN MEMORIA. No resuelve la carrera -- para eso
        //     esta el CAS de abajo-- y no esta aqui por eso: esta para cortar el
        //     comando obsoleto ANTES de tocar la base y para poder decir quien
        //     lleva HOY el encargo, que es lo unico que permite volver a decidir.
        if (agenteAnterior.getId() != idAgenteObservado) {
            throw new ConflictoException(
                    "El agente de este encargo cambio desde que se miro: hoy lo lleva "
                            + agenteAnterior.getId() + ". No se ha hecho nada. Vuelve a cargar la "
                            + "lista y decide sobre el agente actual: esta reasignacion NO se "
                            + "reinterpreta sobre un estado que no es el que miraste.");
        }

        // (b) Y el COMPARE-AND-SET, que es lo que resuelve la carrera real. Es
        //     ademas la UNICA escritura de id_rol_agente en un UPDATE: la
        //     asociacion esta mapeada `updatable = false`, asi que ningun flush
        //     de entidad gestionada puede mover la columna por su cuenta.
        if (captaciones.cambiarAgenteSi(actor.idOrganizacion(), cap.getId(),
                idAgenteObservado, idAgenteNuevo) == 0) {
            throw new ConflictoException(
                    "El agente de este encargo cambio mientras se ejecutaba esta reasignacion, "
                            + "asi que no se ha hecho nada. Vuelve a cargar la lista y decide "
                            + "sobre el agente actual: esta reasignacion NO se reinterpreta "
                            + "sobre un estado que no es el que miraste.");
        }

        // (c) Y solo entonces la instancia en memoria, para que el rastro y la
        //     ficha devuelta lean el MISMO valor que acaba de quedar en la fila.
        //     Si esto fuera antes del CAS, un rechazo dejaria la entidad
        //     gestionada diciendo una cosa y la base otra.
        cap.setAgente(agenteNuevo);
        captaciones.save(cap);

        // Evento de actor (no transicion de estado): cambia el responsable y se
        // conserva en la tabla-evento para el timeline (Doc 5 §7).
        ReasignacionCaptacion evento = new ReasignacionCaptacion();
        evento.setOrganizacionId(cap.getOrganizacionId());
        evento.setCaptacion(cap);
        // El predecesor es EL OBSERVADO -- que despues de (a) y (b) es
        // exactamente el mismo valor que tenia la fila. El rastro dice de donde
        // salio la reasignacion que de verdad ocurrio.
        evento.setAgenteAnterior(agenteAnterior);
        evento.setAgenteNuevo(agenteNuevo);
        evento.setBroker(broker);
        evento.setIdPersonaActor(actor.idPersona());
        evento.setTipoRolActor(actor.rolEfectivo());
        evento.setMotivo(motivoValidado);
        reasignaciones.save(evento);
        return fichaDe(id, actor);
    }

    /**
     * <b>A quien puedo pasarle este encargo</b> — los candidatos ya elegibles
     * para ESTE encargo y ESTE actor (D-P0-7 + D-P0-12).
     *
     * <p>Sale del <b>mismo</b> componente que ofrece los destinos de una
     * propiedad, con la unica diferencia de a quien excluye: alli el responsable
     * actual, aqui el agente actual del encargo. Las reglas son las mismas
     * —mismo tenant, rol AGENTE vigente, cuenta habilitada, membresia vigente,
     * disponibilidad y, si el actor es BROKER, supervision vigente—, asi que el
     * conjunto es el mismo componente con distinta exclusion. Escribir una
     * segunda version "para encargos" seria la forma habitual de que las dos
     * listas terminen ofreciendo cosas distintas.
     *
     * <p><b>Y la guarda es la misma que apaga el boton</b>: si el actor no puede
     * reasignar este encargo, la respuesta es <b>403</b> y no una lista vacia —
     * «no hay candidatos» y «no te corresponde» son dos respuestas distintas.
     * Un id de otro tenant es <b>404</b>, delante de todo, por
     * {@code cargarConAcceso}.
     *
     * <p><b>No autoriza nada</b>: el POST revalida banda, tenant, destino,
     * elegibilidad y estado observado. Entre pedir esta lista y usarla, una
     * cuenta se puede suspender.
     */
    @Override
    @Transactional(readOnly = true)
    public Pagina<CandidatoAgente> candidatosAReasignacion(long id, String texto, int pagina,
                                                           int tamano, Actor actor) {
        Captacion cap = cargarConAcceso(id, actor);
        if (!puedeReasignar(actor, cap)) {
            throw new AccesoNoAutorizadoException(
                    "No puedes cambiar quien lleva este encargo, asi que tampoco hay destinos que "
                            + "ofrecerte. Es el mismo motivo por el que el comando te lo negaria: "
                            + "o no es tu banda, o lo lleva hoy un agente al que no supervisas.");
        }
        Page<DetalleAgente> page = elegibilidad.candidatosExcluyendo(actor, cap.getAgente().getId(),
                texto, PageRequest.of(Math.max(0, pagina - 1), tamano(tamano)));
        return new Pagina<>(page.getContent().stream()
                .map(a -> new CandidatoAgente(a.getId(), nombreDe(a), a.getCodigoAgente(),
                        a.getZonaAsignada()))
                .toList(), page.getTotalElements());
    }

    private static String nombreDe(DetalleAgente agente) {
        PersonaRol rol = agente.getRol();
        return rol == null || rol.getPersona() == null
                ? null : rol.getPersona().getNombresORazonSocial();
    }

    @Override
    @Transactional
    public FichaCaptacion cerrar(long id, String motivo, Actor actor) {
        Captacion cap = cargarConAcceso(id, actor);
        // Misma razon que en `decidir` (D-S0-17 fila 7): cerrar un encargo tiene
        // efecto sobre disponibilidad y cartera, es operacion comercial y el
        // gobierno del tenant no la hereda. `cerrarPorContrato` NO lleva esta
        // guarda a proposito: es la cascada del contrato, no una decision de
        // banda, y exigirla ahi dejaria contratos que no se pueden firmar.
        exigirBandaComercial(actor, "cerrar un encargo");
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

    /**
     * <b>El rastro de reasignaciones se acota con el alcance del ENCARGO</b>
     * (F3-bis, 2026-09-02 — interpretacion de D-P0-6).
     *
     * <p>Hasta aqui devolvia {@code findByOrganizacionIdOrderByIdDesc}: <b>todo
     * el tenant</b>, sin pasar por {@link Alcances}. Un BROKER leia desde que
     * agente salio cada encargo, hacia cual fue y <b>con que motivo</b> en
     * equipos que no supervisa, y ese motivo es texto libre de gobierno.
     *
     * <p>D-P0-6 decide la lectura de historicos de ENCARGO —BROKER los que estan
     * dentro de su alcance, TENANT_ADMIN todo el tenant—, asi que esto se acota
     * con <b>el mismo alcance que ya usa {@link #listar}</b>, sobre la misma
     * columna ({@code captacion.id_rol_agente}). No hay regla nueva: quien ve el
     * encargo ve su rastro.
     *
     * <p><b>El alcance es el del encargo de HOY</b>, y se declara porque es
     * interpretacion: no es el del agente saliente —un encargo que ya no lleva
     * su equipo seguiria en el rastro del broker para siempre— ni el del broker
     * que firmo la reasignacion —una reasignacion de gobierno no la veria
     * ningun broker—.
     *
     * <p>La banda va <b>delante</b> del alcance y no la sustituye:
     * {@code Alcances.de} le daria a un AGENTE «lo suyo», que aqui es
     * exactamente lo que no le corresponde. El controlador ya lo declara con
     * {@code @PreAuthorize("hasAnyRole('BROKER','TENANT_ADMIN')")}; el Core dice
     * lo mismo para que KAIROS y cualquier consumidor que no pase por HTTP no
     * entren por debajo de la anotacion.
     */
    @Override
    @Transactional(readOnly = true)
    public List<FichaReasignacion> listarReasignaciones(Actor actor) {
        exigirBandaQueGobiernaElEncargo(actor);
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return List.of();
        }
        return reasignaciones.bitacora(alcance.idOrganizacion(), alcance.global(),
                        alcance.paramRoles()).stream()
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

    /**
     * <b>Lo mismo, con la fila TOMADA</b> (F2.10).
     *
     * <p>Mismo orden de guardas —tenant (404) primero, alcance despues—, misma
     * excepcion y mismos mensajes: lo unico que cambia es que la fila queda
     * bloqueada, de modo que el alcance y el {@code exigirEdicionDelEncargo} que
     * viene detras deciden sobre el agente que <b>seguira siendo verdad</b>
     * cuando la transaccion escriba. Sin esto, entre la comprobacion y el flush
     * cabia una reasignacion entera y la edicion del saliente aterrizaba sobre
     * un encargo ajeno.
     *
     * <p><b>Tiene que ser la PRIMERA carga de esa fila en la transaccion</b>, y
     * por eso es un metodo aparte y no una bandera de {@code cargarConAcceso}:
     * Hibernate devuelve sin refrescar la instancia que ya este en el contexto,
     * asi que bloquear despues de haber cargado tomaria el candado y decidiria
     * sobre el valor viejo.
     *
     * <p>Lo llama <b>solo</b> {@code actualizar}. {@code decidir},
     * {@code cerrar}, {@code cerrarPorContrato} y {@code reasignar} siguen con
     * {@link #cargarConAcceso}: ninguno escribe hechos del trato bajo la
     * autoridad del agente —los tres primeros son gobierno del BROKER sobre el
     * ciclo del encargo y el cuarto ya resuelve su carrera con el
     * compare-and-set—, y meterles un candado cambiaria el orden de bloqueos de
     * casos que hoy no lo necesitan.
     */
    private Captacion cargarConAccesoParaEscribir(long id, Actor actor) {
        Captacion cap = captaciones.bloquearParaEscritura(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Captacion"));
        if (!alcances.alcanza(actor, cap.getAgente().getId())) {
            throw new AccesoNoAutorizadoException();
        }
        return cap;
    }

    private FichaCaptacion fichaDe(long id, Actor actor) {
        return fichaIndividual(captaciones.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Captacion")), actor);
    }

    /**
     * <b>La ficha de UN encargo, con sus capacidades resueltas</b> (D-P0-12).
     *
     * <p>Es el unico productor de una ficha individual, y pasa por aqui tanto la
     * consulta por id como la consulta por codigo: son <b>dos puertas al mismo
     * recurso</b>, y una que publicara `capacidades` y la otra no obligaria al
     * SPA a llevar su propia copia de la regla justo en la puerta que se olvida.
     *
     * <p>Los listados NO pasan por aqui: su pregunta es «que hay», no «que puedo
     * hacer con este», y calcular tres capacidades por fila serian tres
     * comprobaciones de alcance por elemento de la pagina.
     */
    private FichaCaptacion fichaIndividual(Captacion cap, Actor actor) {
        return ficha(cap, portadaDe(cap), gobernadosDe(List.of(cap)), capacidadesDe(actor, cap));
    }

    private String portadaDe(Captacion cap) {
        return fotos.findByIdPropiedadOrderByOrdenAscIdAsc(cap.getPropiedad().getId()).stream()
                .findFirst().map(FotoPropiedad::getClave).orElse(null);
    }

    /**
     * <b>Las tres capacidades, con las MISMAS guardas que los comandos.</b>
     *
     * <p>{@code puedeEditar} se pregunta con el propio
     * {@code exigirEdicionDelEncargo} —el metodo que despues deniega—, en vez de
     * reescribir «soy el agente» aqui: un segundo criterio "solo para pintar" es
     * exactamente como se llega a un boton activo que el backend rechaza cuando
     * la persona ya escribio. Es el mismo patron que {@code autoridadDelEncargo}
     * en la ficha universal.
     *
     * <p>{@code puedeRevisar} y {@code puedeCerrar} exigen la banda BROKER de
     * forma explicita: decidir y cerrar un encargo son operaciones comerciales y
     * el gobierno del tenant <b>no las hereda</b> (D-S0-17, filas 5 y 7). El
     * TENANT_ADMIN alcanza el encargo para leerlo —{@code alcances.alcanza} le
     * dice que si— y aun asi recibe {@code false}, que es lo mismo que le
     * responden los comandos.
     *
     * <p>{@code puedeReasignar} es la excepcion y por eso lo dice aparte: el
     * TENANT_ADMIN <b>si</b> reasigna (D-S0-17 fila 6 — entre equipos es
     * organigrama, no operacion comercial), y sale del <b>mismo</b> predicado
     * que decide si se le ofrecen candidatos.
     */
    private CaptacionService.Capacidades capacidadesDe(Actor actor, Captacion cap) {
        boolean editable = cap.editable();
        boolean puedeEditar = editable && autoridadDelEncargo(actor, cap);
        boolean brokerConAlcance = actor.esBroker()
                && cap.getAgente() != null
                && alcances.alcanza(actor, cap.getAgente().getId());
        return new CaptacionService.Capacidades(
                puedeEditar,
                brokerConAlcance && editable,
                brokerConAlcance && Captacion.ACTIVA.equals(cap.estadoActual()),
                puedeReasignar(actor, cap));
    }

    /**
     * <b>¿Puede este actor mover el encargo a otro agente?</b> (D-S0-17 fila 6).
     *
     * <p>Son las guardas del comando <b>sin el destino</b>, que en la ficha
     * todavia no existe: el BROKER que supervisa hoy al agente que lo lleva, y
     * el TENANT_ADMIN de la organizacion. Un AGENTE nunca —tampoco sobre el
     * suyo: quien lleva un encargo no decide dejar de llevarlo.
     *
     * <p>Existe una sola vez y la usan las <b>dos</b> superficies —la capacidad
     * de la ficha y el 403 de la lista de candidatos— porque son la misma
     * pregunta. El comando revalida ademas el destino, su elegibilidad y el
     * estado observado: esto no autoriza nada, dice si tiene sentido ofrecerlo.
     */
    private boolean puedeReasignar(Actor actor, Captacion cap) {
        if (cap.getAgente() == null) {
            return false;
        }
        return (actor.esBroker() && alcances.alcanza(actor, cap.getAgente().getId()))
                || actor.esTenantAdmin();
    }

    /** Se pregunta con el mismo metodo que despues deniega. */
    private boolean autoridadDelEncargo(Actor actor, Captacion cap) {
        try {
            autoridad.exigirEdicionDelEncargo(actor, cap);
            return true;
        } catch (AccesoNoAutorizadoException denegado) {
            return false;
        }
    }

    private Pagina<FichaCaptacion> paginaConPortada(Page<Captacion> page) {
        List<Long> idsPropiedad = page.getContent().stream().map(c -> c.getPropiedad().getId()).distinct().toList();
        var portadas = idsPropiedad.isEmpty() ? java.util.Map.<Long, String>of()
                : fotos.portadas(idsPropiedad).stream()
                    .collect(java.util.stream.Collectors.toMap(f -> f.getIdPropiedad(), f -> f.getClave()));
        Map<Long, ValoresGobernados> gobernados = gobernadosDe(page.getContent());
        // Sin capacidades: un listado responde «que hay», no «que puedo hacer con
        // este». Nulo -> NON_NULL -> no viaja, y el cliente no puede confundirlo
        // con "no puedes nada".
        List<FichaCaptacion> items = page.getContent().stream()
                .map(c -> ficha(c, portadas.get(c.getPropiedad().getId()), gobernados, null))
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
    private Map<Long, ValoresGobernados> gobernadosDe(List<Captacion> captacionesDeLaPagina) {
        List<Long> ids = captacionesDeLaPagina.stream()
                .map(Captacion::getPropiedad).filter(Objects::nonNull)
                .map(Propiedad::getId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : lector.gobernadosDeVarias(ids);
    }

    /**
     * <b>Decidir y cerrar un encargo son operaciones COMERCIALES</b> (D-S0-17,
     * filas 5 y 7): las firma el BROKER, y el gobierno del tenant no las hereda.
     *
     * <p>Existe una sola vez para los dos comandos porque es la misma regla, y
     * dicha con las mismas palabras: dos redacciones del mismo rechazo se
     * separan en el primer cambio, igual que dos comprobaciones del mismo
     * permiso.
     */
    /**
     * <b>El rastro de gobierno del encargo no es del AGENTE</b> (F3-bis,
     * interpretacion de D-P0-6).
     *
     * <p>Es la hermana de {@link #exigirBandaComercial} por el otro lado: alli
     * el gobierno no hereda la operacion, aqui la operacion no hereda el
     * gobierno. Existe <b>en el Core</b> y no solo en el
     * {@code @PreAuthorize("hasAnyRole('BROKER','TENANT_ADMIN')")} del
     * controlador porque el alcance por si solo <b>concederia</b>: a un AGENTE,
     * {@code Alcances.de} le devuelve «lo suyo», y sus propias reasignaciones
     * son justamente lo que no le toca leer — quien lleva un encargo sabe que lo
     * lleva, y no hereda por eso el motivo con el que se lo quitaron a otro.
     */
    private static void exigirBandaQueGobiernaElEncargo(Actor actor) {
        if (actor.esAgente()) {
            throw new AccesoNoAutorizadoException(
                    "El historial de reasignaciones es rastro de gobierno del encargo: lo leen el "
                            + "BROKER que supervisa hoy a quien lo lleva y el gobierno del tenant. "
                            + "Quien opera un encargo sabe que lo lleva, y eso no le concede los "
                            + "motivos con los que se movio la cartera.");
        }
    }

    private static void exigirBandaComercial(Actor actor, String queSeIntenta) {
        if (!actor.esBroker()) {
            throw new AccesoNoAutorizadoException(
                    "Para " + queSeIntenta + " hace falta la banda BROKER: es el juicio "
                            + "profesional sobre la relacion comercial, y el gobierno de la "
                            + "organizacion no lo hereda. Quien gobierne y ademas supervise lo "
                            + "hace actuando como broker.");
        }
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
                                        Map<Long, ValoresGobernados> gobernados,
                                        CaptacionService.Capacidades capacidades) {
        Propiedad prop = c.getPropiedad();
        ValoresGobernados valores = prop == null ? ValoresGobernados.vacio()
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
                c.getFechaCierre(), c.getMotivoCierre(), c.getDetalleMotivoCierre(),
                capacidades);
    }

    private static void validarEncargo(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null) {
            throw new ReglaNegocioException("El inicio y fin del encargo son obligatorios.");
        }
        if (!fin.isAfter(inicio)) {
            throw new ReglaNegocioException("La fecha final del encargo debe ser posterior a la inicial.");
        }
    }

    /**
     * <b>La operacion de un encargo no se edita</b> (V76).
     *
     * <p>Es su identidad, no uno de sus campos: cambiarla convierte el encargo
     * de venta de un inmueble en el encargo de alquiler del mismo inmueble
     * <b>conservando su historico</b>, con lo que los 350 000 USD que se
     * pidieron por venderlo pasan a leerse como rentas mensuales. Es la misma
     * clase de reinterpretacion que el Corte 0C cerro por el otro lado: dos
     * alquileres sucesivos de la misma propiedad son dos episodios.
     *
     * <p>Ademas era un error tardio y feo: si el inmueble ya tenia un encargo
     * vivo de la operacion de destino, esto salia como un 409 de indice unico
     * -- y si no lo tenia, salia bien y corrompia la serie en silencio.
     */
    private static void exigirMismaOperacion(Captacion cap, DatosCaptacion datos) {
        String pedida = textoO(datos.tipoOperacion(), datos.motivoOperacion(), null);
        if (pedida == null || pedida.isBlank()) {
            return;
        }
        String actual = cap.getMotivoOperacion();
        if (actual != null && !actual.equals(OperacionDelEncargo.deTexto(pedida).codigo())) {
            throw new ReglaNegocioException(
                    "Este encargo es de " + OperacionInmobiliaria.deCodigo(actual).name().toLowerCase(Locale.ROOT)
                            + " y su operacion no se edita: es lo que decide si su importe es una "
                            + "renta o un precio de venta, y cambiarla reinterpretaria todo su "
                            + "historico. Cierra este encargo y abre el de la otra operacion.");
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
        // El defecto de la base lo dicta la OPERACION (V76): una venta no tiene
        // renta mensual sobre la que calcular nada.
        String base = textoO(datos.baseCalculo(), CondicionesEconomicas.basePorDefecto(operacion), null);
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
        CondicionesEconomicas.exigirBaseCoherente(operacion, tipo, base);
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
