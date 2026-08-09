package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.MotivoNoContinuidad;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.Visita;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.repositorio.MotivoNoContinuidadRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PlanDeConsulta;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.Pagina;
import com.controllocal.service.VisitaService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Transiciones;
import com.controllocal.service.soporte.Vocabulario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reglas y mensajes calcados de VisitasRest + VisitaBusinessLogicImpl + el
 * modelo Visita de la v1.
 *
 * <p>Ojo con los mensajes de estado: la guarda que responde en el cable es la
 * del BL ("No se puede {accion} una visita {estado}."), no la del modelo — el
 * BL corta antes. Se replica la del BL.
 *
 * <p>Mejora del stack nuevo, sin tocar el cable: cada cambio de agenda pasa por
 * {@link Transiciones} y deja fila en historial_estado con actor y motivo.
 */
@Service
public class VisitaServiceImpl implements VisitaService {

    /** Descripciones de EstadoVisita v1: forman parte del mensaje de error. */
    private static final Map<String, String> DESCRIPCION_ESTADO = Map.of(
            "P", "programada", "G", "reprogramada", "C", "cancelada",
            "N", "no realizada", "R", "realizada");

    /** MotivoNoContinuidadTipo v1: el cierre guarda la DESCRIPCION, no el codigo. */
    private static final Map<String, String> RAZONES = Map.of(
            "P", "Precio",
            "U", "Ubicacion",
            "C", "Condiciones del contrato",
            "L", "Local no adecuado",
            "N", "Cliente no responde",
            "E", "Encontro otra opcion",
            "O", "Otro");

    /** Tope duro del cable para la agenda "proximas". */
    private static final int MAX_PROXIMAS = 8;

    private final VisitaRepository visitas;
    private final OportunidadComercialRepository oportunidades;
    private final MotivoNoContinuidadRepository motivos;
    private final Alcances alcances;
    private final Transiciones transiciones;
    private final PlanDeConsulta plan;

    public VisitaServiceImpl(VisitaRepository visitas, OportunidadComercialRepository oportunidades,
                             MotivoNoContinuidadRepository motivos, Alcances alcances,
                             Transiciones transiciones, PlanDeConsulta plan) {
        this.visitas = visitas;
        this.oportunidades = oportunidades;
        this.motivos = motivos;
        this.alcances = alcances;
        this.transiciones = transiciones;
        this.plan = plan;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaVisita> listar(FiltrosVisita f, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        int tamanoValido = tamano(f.tamano());
        String texto = vacioNull(f.query());
        if (texto != null) {
            return porTexto(alcance, actor, f, texto, Math.max(1, f.pagina()), tamanoValido);
        }
        Page<Visita> page = visitas.buscar(alcance.idOrganizacion(), alcance.global(), actor.esAgente(),
                alcance.paramRoles(), f.idOportunidad(), vacioNull(f.estado()), vacioNull(f.distrito()),
                PageRequest.of(Math.max(0, f.pagina() - 1), tamanoValido));
        return new Pagina<>(page.getContent().stream().map(VisitaServiceImpl::ficha).toList(),
                page.getTotalElements());
    }

    /**
     * Camino de BUSQUEDA POR CONJUNTO DE CANDIDATOS (§5 del contrato de
     * listados): una rama por tabla, {@code UNION} en la base y el mismo
     * conjunto para el conteo y la pagina.
     */
    private Pagina<FichaVisita> porTexto(Alcance alcance, Actor actor, FiltrosVisita f,
                                         String texto, int pagina, int tamano) {
        plan.forzarPersonalizado();
        String roles = alcance.paramRolesArray();
        String estado = vacioNull(f.estado());
        String distrito = vacioNull(f.distrito());
        long total = visitas.contarPorTexto(alcance.idOrganizacion(), alcance.global(),
                actor.esAgente(), roles, f.idOportunidad(), estado, distrito, texto);
        if (total == 0) {
            return new Pagina<>(List.of(), 0);
        }
        List<Long> ids = visitas.idsPorTexto(alcance.idOrganizacion(), alcance.global(),
                actor.esAgente(), roles, f.idOportunidad(), estado, distrito, texto,
                tamano, (pagina - 1) * tamano);
        if (ids.isEmpty()) {
            return new Pagina<>(List.of(), total);
        }
        return new Pagina<>(
                visitas.buscarFichaPorIds(alcance.idOrganizacion(), ids).stream()
                        .map(VisitaServiceImpl::ficha).toList(),
                total);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenVisitas resumen(FiltrosVisita f, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return new ResumenVisitas(0, 0, 0, 0, 0, 0, List.of());
        }
        // Estado y distrito viajan nulos: son los filtros que este resumen acota.
        // Con texto se cuenta sobre el MISMO conjunto de candidatos que pagina
        // la lista; sin texto, sobre el mismo WHERE.
        String texto = vacioNull(f.query());
        if (texto != null) {
            plan.forzarPersonalizado();
        }
        List<ConteoPorEstado> conteos = texto != null
                ? visitas.contarPorEstadoConTexto(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRolesArray(), f.idOportunidad(), null, null, texto)
                : visitas.contarPorEstado(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles(), f.idOportunidad(), null, null);
        Map<String, Long> porEstado = conteos.stream()
                .collect(Collectors.toMap(ConteoPorEstado::getEstado, ConteoPorEstado::getTotal));
        return new ResumenVisitas(
                porEstado.values().stream().mapToLong(Long::longValue).sum(),
                porEstado.getOrDefault("P", 0L),
                porEstado.getOrDefault("G", 0L),
                porEstado.getOrDefault("R", 0L),
                porEstado.getOrDefault("N", 0L),
                porEstado.getOrDefault("C", 0L),
                visitas.distritosDisponibles(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles()));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaVisita> proximas(int tamano, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        List<Visita> fuente = visitas.listarProximas(alcance.idOrganizacion(), alcance.global(),
                actor.esAgente(), alcance.paramRoles(), LocalDate.now(),
                PageRequest.of(0, Math.min(tamano(tamano), MAX_PROXIMAS)));
        return paginaSinTotal(fuente);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaVisita> mes(int anio, int mes, Actor actor) {
        if (anio < 2000 || anio > 2100 || mes < 1 || mes > 12) {
            throw new ReglaNegocioException("El mes solicitado no es valido.");
        }
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        YearMonth periodo = YearMonth.of(anio, mes);
        List<Visita> fuente = visitas.listarMes(alcance.idOrganizacion(), alcance.global(), actor.esAgente(),
                alcance.paramRoles(), periodo.atDay(1), periodo.atEndOfMonth());
        return paginaSinTotal(fuente);
    }

    @Override
    @Transactional(readOnly = true)
    public FichaVisita obtener(long id, Actor actor) {
        return ficha(cargarConAcceso(id, actor));
    }

    @Override
    @Transactional
    public FichaVisita programar(DatosVisita datos, Actor actor) {
        if (datos == null || datos.idOportunidad() == null) {
            throw new ReglaNegocioException("Los datos de la visita son obligatorios.");
        }
        OportunidadComercial oportunidad = oportunidades
                .buscarFicha(actor.idOrganizacion(), datos.idOportunidad())
                .orElseThrow(() -> new NoEncontradoException("Oportunidad"));
        // El alta compara DIRECTO con el rol del actor: sin alcance de broker.
        if (oportunidad.getAgente() == null || oportunidad.getAgente().getId() != actor.idRolOperativo()) {
            throw new AccesoNoAutorizadoException();
        }
        if (datos.fechaVisita() == null || datos.horaVisita() == null) {
            throw new ReglaNegocioException("La visita debe tener fecha, hora y estado.");
        }
        if (!oportunidad.estaAbierta()) {
            throw new ReglaNegocioException("La oportunidad comercial debe estar ABIERTA.");
        }

        Visita visita = new Visita();
        visita.setOrganizacionId(actor.idOrganizacion());
        visita.setOportunidad(oportunidad);
        visita.setAgente(oportunidad.getAgente());
        visita.setFechaVisita(datos.fechaVisita());
        visita.setHoraVisita(datos.horaVisita());
        visita.setObservaciones(datos.observaciones());
        transiciones.iniciar(visita, Visita.PROGRAMADA);
        return ficha(visitas.save(visita));
    }

    @Override
    @Transactional
    public FichaVisita reprogramar(long id, LocalDate fechaVisita, LocalTime horaVisita, Actor actor) {
        if (fechaVisita == null || horaVisita == null) {
            throw new ReglaNegocioException("La nueva fecha y hora son obligatorias para reprogramar.");
        }
        Visita visita = cargarModificable(id, actor, "reprogramar");
        visita.moverA(fechaVisita, horaVisita);
        transiciones.aplicar(visita, id, Visita.REPROGRAMADA, actor, "Visita reprogramada.");
        return ficha(visitas.save(visita));
    }

    @Override
    @Transactional
    public FichaVisita cancelar(long id, String motivo, Actor actor) {
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("El motivo de cancelacion es obligatorio.");
        }
        Visita visita = cargarModificable(id, actor, "cancelar");
        visita.registrarMotivoYLimpiarDesenlace(motivo.trim());
        transiciones.aplicar(visita, id, Visita.CANCELADA, actor, motivo.trim());
        return ficha(visitas.save(visita));
    }

    @Override
    @Transactional
    public FichaVisita marcarRealizada(long id, Actor actor) {
        Visita visita = cargarModificable(id, actor, "marcar como realizada");
        transiciones.aplicar(visita, id, Visita.REALIZADA, actor, "Visita realizada.");
        return ficha(visitas.save(visita));
    }

    @Override
    @Transactional
    public FichaVisita marcarNoRealizada(long id, String motivo, Actor actor) {
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("El motivo de la visita no realizada es obligatorio.");
        }
        Visita visita = cargarModificable(id, actor, "marcar como no realizada");
        visita.registrarMotivoYLimpiarDesenlace(motivo.trim());
        transiciones.aplicar(visita, id, Visita.NO_REALIZADA, actor, motivo.trim());
        return ficha(visitas.save(visita));
    }

    @Override
    @Transactional
    public FichaVisita registrarResultado(long id, DesenlaceVisita desenlace, Actor actor) {
        if (desenlace == null || desenlace.resultado() == null || desenlace.resultado().isBlank()) {
            throw new ReglaNegocioException("El resultado de la visita es obligatorio.");
        }
        // Las visitas admiten CUALQUIER codigo de ResultadoInteraccion: la
        // allow-list por contexto es solo de interacciones.
        String resultado = Vocabulario.exigir(desenlace.resultado(), Vocabulario.RESULTADOS,
                "ResultadoInteraccion");
        String razon = Vocabulario.opcional(desenlace.razonNoContinuidad(),
                Vocabulario.RAZONES_NO_CONTINUIDAD, "MotivoNoContinuidadTipo");
        String objecion = Vocabulario.opcional(desenlace.objecionPrincipal(),
                Vocabulario.OBJECIONES_VISITA, "ObjecionVisita");
        String opinion = Vocabulario.opcional(desenlace.opinionPrecio(),
                Vocabulario.OPINIONES_PRECIO, "OpinionPrecio");
        String proxima = Vocabulario.opcional(desenlace.proximaAccion(),
                Vocabulario.PROXIMAS_ACCIONES, "ProximaAccionVisita");

        Visita visita = cargarConAcceso(id, actor);
        if (!Visita.REALIZADA.equals(visita.estadoActual()) || visita.tieneDesenlace()) {
            throw new ReglaNegocioException(
                    "Solo una visita realizada y sin resultado admite registrar el desenlace.");
        }
        boolean noContinua = Vocabulario.implicaNoContinuidad(resultado);
        if (noContinua && desenlace.nivelInteres() != null) {
            throw new ReglaNegocioException(
                    "No se debe registrar nivel de interes cuando el resultado es de no continuidad.");
        }
        visita.registrarDesenlace(resultado, vacioNull(desenlace.observaciones()), razon,
                desenlace.nivelInteres(), objecion, opinion, proxima);
        visitas.save(visita);

        // El desenlace de no continuidad cierra la oportunidad en la MISMA
        // transaccion, con su motivo tipificado (cable v1).
        if (noContinua) {
            cerrarPorNoContinuidad(visita, razon, desenlace.observaciones(), actor);
        }
        return ficha(visita);
    }

    private void cerrarPorNoContinuidad(Visita visita, String razon, String observaciones, Actor actor) {
        if (razon == null) {
            throw new ReglaNegocioException(
                    "Debe indicar el motivo de no continuidad cuando el cliente no continua.");
        }
        OportunidadComercial oportunidad = visita.getOportunidad();
        if (!oportunidad.estaAbierta()) {
            throw new ReglaNegocioException("La oportunidad comercial debe estar ABIERTA.");
        }
        MotivoNoContinuidad motivo = new MotivoNoContinuidad();
        motivo.setOrganizacionId(oportunidad.getOrganizacionId());
        motivo.setOportunidad(oportunidad);
        motivo.setRazonPrincipal(razon);
        motivo.setObservaciones(observaciones);
        motivo.setAgente(visita.getAgente());
        motivos.save(motivo);

        String descripcion = RAZONES.get(razon);
        oportunidad.marcarCierre(descripcion);
        transiciones.aplicar(oportunidad, oportunidad.getId(), OportunidadComercial.NO_CONTINUA,
                actor, descripcion);
        oportunidades.save(oportunidad);
    }

    // ------------------------------------------------------------------
    // Alcance por rol: BROKER por CAPTACION de la oportunidad (§5).
    // ------------------------------------------------------------------

    private Visita cargarConAcceso(long id, Actor actor) {
        Visita visita = visitas.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Visita"));
        if (!alcanza(visita, actor)) {
            throw new AccesoNoAutorizadoException();
        }
        return visita;
    }

    private Visita cargarModificable(long id, Actor actor, String accion) {
        Visita visita = cargarConAcceso(id, actor);
        if (!visita.esModificable()) {
            throw new ReglaNegocioException("No se puede " + accion + " una visita "
                    + DESCRIPCION_ESTADO.getOrDefault(visita.estadoActual(), "") + ".");
        }
        return visita;
    }

    private boolean alcanza(Visita visita, Actor actor) {
        if (actor.esTenantAdmin()) {
            return true;
        }
        if (actor.esAgente()) {
            return visita.getAgente() != null && visita.getAgente().getId() == actor.idRolOperativo();
        }
        Captacion captacion = visita.getOportunidad() != null
                ? visita.getOportunidad().getCaptacion()
                : null;
        return captacion != null && captacion.getAgente() != null
                && alcances.supervisados(actor.idOrganizacion(), actor.idRolOperativo())
                        .contains(captacion.getAgente().getId());
    }

    private static Pagina<FichaVisita> paginaSinTotal(List<Visita> fuente) {
        List<FichaVisita> items = fuente.stream().map(VisitaServiceImpl::ficha).toList();
        return new Pagina<>(items, items.size());
    }

    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }

    private static String vacioNull(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    private static FichaVisita ficha(Visita v) {
        OportunidadComercial oportunidad = v.getOportunidad();
        DetalleCliente cliente = oportunidad != null ? oportunidad.getCliente() : null;
        Captacion captacion = oportunidad != null ? oportunidad.getCaptacion() : null;
        Propiedad propiedad = captacion != null ? captacion.getPropiedad() : null;
        DetalleAgente agente = v.getAgente();
        return new FichaVisita(
                v.getId(),
                oportunidad != null ? oportunidad.getId() : null,
                oportunidad != null ? oportunidad.getCodigoOportunidad() : null,
                v.getFechaVisita(), v.getHoraVisita(), v.getObservaciones(), v.estadoActual(),
                v.getResultado(),
                cliente != null ? cliente.getId() : null,
                nombre(cliente != null ? cliente.getRol() : null),
                captacion != null ? captacion.getId() : null,
                captacion != null ? captacion.getCodigoCaptacion() : null,
                propiedad != null ? propiedad.getDireccion() : null,
                propiedad != null ? propiedad.getDistrito() : null,
                agente != null ? agente.getId() : null,
                nombre(agente != null ? agente.getRol() : null),
                v.getNivelInteres(), v.getObjecionPrincipal(), v.getOpinionPrecio(), v.getProximaAccion());
    }

    private static String nombre(PersonaRol rol) {
        return rol == null || rol.getPersona() == null ? null : rol.getPersona().getNombresORazonSocial();
    }
}
