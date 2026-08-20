package com.controllocal.service.impl;

import com.controllocal.domain.comercial.MetaComercial;
import com.controllocal.domain.comercial.MetaRevision;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.MetaComercialRepository;
import com.controllocal.persistence.repositorio.MetaRevisionRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.RendimientoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.MetaComercialService;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.KpiCanonico;
import com.controllocal.service.soporte.PeriodoCalendario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fijar, proponer y revisar metas.
 *
 * <h2>Las tres comprobaciones que no se pueden saltar</h2>
 *
 * <ol>
 *   <li><b>Un broker solo toca las de sus agentes.</b> Sin esto, conocer un id
 *       bastaría para ponerle objetivos a alguien de otro equipo, y el semáforo
 *       de esa persona pasaría a depender de un extraño.</li>
 *   <li><b>Un agente solo propone sobre la suya.</b> Proponer la de otro sería
 *       fijar la de otro con un paso intermedio.</li>
 *   <li><b>Toda escritura deja revisión.</b> No hay ningún camino que cambie
 *       {@code meta_comercial} sin escribir en la serie: es lo que impide que el
 *       valor vigente y su historia diverjan.</li>
 * </ol>
 */
@Service
public class MetaComercialServiceImpl implements MetaComercialService {

    private final MetaComercialRepository metas;
    private final MetaRevisionRepository revisiones;
    private final DetalleAgenteRepository agentes;
    private final PersonaRolRepository roles;
    private final RendimientoRepository rendimiento;
    private final Alcances alcances;

    public MetaComercialServiceImpl(MetaComercialRepository metas,
                                    MetaRevisionRepository revisiones,
                                    DetalleAgenteRepository agentes,
                                    PersonaRolRepository roles,
                                    RendimientoRepository rendimiento,
                                    Alcances alcances) {
        this.metas = metas;
        this.revisiones = revisiones;
        this.agentes = agentes;
        this.roles = roles;
        this.rendimiento = rendimiento;
        this.alcances = alcances;
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<MetaDeAgente> del(String mes, Actor actor) {
        Alcances.Alcance alcance = alcances.de(actor);
        PeriodoCalendario periodo = PeriodoCalendario.desde(mes, LocalDate.now());
        return filas(alcance.idOrganizacion(), periodo, agentesDelAlcance(alcance));
    }

    // ------------------------------------------------------------------
    // El broker fija
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public List<MetaDeAgente> fijar(String mes, List<Asignacion> asignaciones, Actor actor) {
        exigirBroker(actor, "fijar metas");
        Alcances.Alcance alcance = alcances.de(actor);
        long organizacion = alcance.idOrganizacion();
        PeriodoCalendario periodo = PeriodoCalendario.desde(mes, LocalDate.now());
        List<Long> visibles = agentesDelAlcance(alcance);

        for (Asignacion asignacion : asignaciones == null ? List.<Asignacion>of() : asignaciones) {
            if (!visibles.contains(asignacion.idRolAgente())) {
                throw new ReglaNegocioException(
                        "No puedes fijar la meta de un agente que no supervisas.");
            }
            KpiCanonico kpi = KpiCanonico.porCodigo(asignacion.kpi());
            aplicar(organizacion, periodo, asignacion.idRolAgente(), kpi, asignacion.valor(),
                    asignacion.motivo(), actor.idRolOperativo(), MetaRevision.ORIGEN_BROKER, null);
        }
        return filas(organizacion, periodo, visibles);
    }

    // ------------------------------------------------------------------
    // El agente propone
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public List<MetaDeAgente> proponer(String mes, Propuesta propuesta, Actor actor) {
        if (!actor.esAgente()) {
            throw new ReglaNegocioException(
                    "Solo un agente propone un ajuste de su propia meta. Un broker la fija "
                            + "directamente.");
        }
        if (propuesta == null) {
            throw new ReglaNegocioException("Falta la propuesta.");
        }
        Alcances.Alcance alcance = alcances.de(actor);
        long organizacion = alcance.idOrganizacion();
        long agente = actor.idRolOperativo();
        PeriodoCalendario periodo = PeriodoCalendario.desde(mes, LocalDate.now());
        KpiCanonico kpi = KpiCanonico.porCodigo(propuesta.kpi());

        // Una sola propuesta viva por KPI y mes. La segunda REEMPLAZA a la
        // primera en vez de acumularse: el broker tiene que ver lo que el agente
        // pide ahora, no la lista de todo lo que fue pidiendo.
        revisiones.findByOrganizacionIdAndIdRolAgenteAndKpiAndAnioAndMesAndEstado(
                        organizacion, agente, kpi.codigo(), periodo.anio(), periodo.mes(),
                        MetaRevision.ESTADO_EN_ESPERA)
                .ifPresent(viva -> {
                    viva.resolver(MetaRevision.ESTADO_RECHAZADA, agente,
                            "Reemplazada por una propuesta posterior del propio agente.");
                    revisiones.save(viva);
                });

        MetaRevision nueva = revision(organizacion, periodo, agente, kpi,
                valorVigente(organizacion, periodo, agente, kpi), propuesta.valor(),
                propuesta.motivo(), agente, MetaRevision.ORIGEN_PROPUESTA,
                MetaRevision.ESTADO_EN_ESPERA);
        revisiones.save(nueva);

        return filas(organizacion, periodo, List.of(agente));
    }

    // ------------------------------------------------------------------
    // El broker decide
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<PropuestaPendiente> propuestasPendientes(Actor actor) {
        Alcances.Alcance alcance = alcances.de(actor);
        List<Long> visibles = agentesDelAlcance(alcance);
        if (visibles.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nombres = nombresDe(alcance.idOrganizacion(), visibles);
        List<PropuestaPendiente> pendientes = new ArrayList<>();
        for (MetaRevision revision : revisiones
                .findByOrganizacionIdAndEstadoAndIdRolAgenteInOrderByIdAsc(
                        alcance.idOrganizacion(), MetaRevision.ESTADO_EN_ESPERA, visibles)) {
            pendientes.add(pendiente(revision, nombres));
        }
        return pendientes;
    }

    @Override
    @Transactional
    public List<MetaDeAgente> resolver(long idRevision, boolean acepta, String motivo,
                                       Actor actor) {
        exigirBroker(actor, "decidir sobre una propuesta de meta");
        Alcances.Alcance alcance = alcances.de(actor);
        long organizacion = alcance.idOrganizacion();
        List<Long> visibles = agentesDelAlcance(alcance);

        MetaRevision revision = revisiones.findById(idRevision)
                .filter(r -> organizacion == r.getOrganizacionId())
                .orElseThrow(() -> new NoEncontradoException(
                        "No existe esa propuesta de meta."));
        if (!visibles.contains(revision.getIdRolAgente())) {
            throw new ReglaNegocioException(
                    "No puedes decidir sobre la meta de un agente que no supervisas.");
        }
        if (!revision.enEspera()) {
            throw new ReglaNegocioException(
                    "Esa propuesta ya se resolvio: reabrirla seria reescribir la historia.");
        }

        String explicacion = exigirMotivo(motivo);
        revision.resolver(acepta ? MetaRevision.ESTADO_APLICADA : MetaRevision.ESTADO_RECHAZADA,
                actor.idRolOperativo(), explicacion);
        revisiones.save(revision);

        PeriodoCalendario periodo = PeriodoCalendario.de(
                java.time.YearMonth.of(revision.getAnio(), revision.getMes()), LocalDate.now());
        if (acepta) {
            // Solo el valor vigente: la revision YA quedo escrita arriba, y
            // escribir otra aqui contaria dos veces la misma decision.
            escribirVigente(organizacion, periodo, revision.getIdRolAgente(),
                    KpiCanonico.porCodigo(revision.getKpi()), revision.getValorPropuesto(),
                    actor.idRolOperativo());
        }
        return filas(organizacion, periodo, visibles);
    }

    // ------------------------------------------------------------------
    // Escritura, en un solo sitio
    // ------------------------------------------------------------------

    /**
     * Aplica un valor y deja su revisión. Es el <b>único</b> camino por el que el
     * broker cambia una meta, y por eso el valor vigente nunca puede quedar sin
     * historia que lo explique.
     */
    private void aplicar(long organizacion, PeriodoCalendario periodo, long agente,
                         KpiCanonico kpi, int valor, String motivo, long autor,
                         String origen, String ignorado) {
        Integer anterior = valorVigente(organizacion, periodo, agente, kpi);
        if (anterior != null && anterior == valor) {
            // Nada que revisar: escribir una revision «de 8 a 8» ensuciaria el
            // historial con ruido y haria mas dificil leer los cambios reales.
            return;
        }
        revisiones.save(revision(organizacion, periodo, agente, kpi, anterior, valor,
                motivo, autor, origen, MetaRevision.ESTADO_APLICADA));
        escribirVigente(organizacion, periodo, agente, kpi, valor, autor);
    }

    private void escribirVigente(long organizacion, PeriodoCalendario periodo, long agente,
                                 KpiCanonico kpi, int valor, long autor) {
        MetaComercial meta = metas
                .findByOrganizacionIdAndIdRolAgenteAndKpiAndAnioAndMes(
                        organizacion, agente, kpi.codigo(), periodo.anio(), periodo.mes())
                .orElseGet(MetaComercial::new);
        boolean existia = meta.getId() != null;

        meta.setOrganizacionId(organizacion);
        meta.setIdRolAgente(agente);
        meta.setKpi(kpi.codigo());
        meta.setAnio(periodo.anio());
        meta.setMes(periodo.mes());
        meta.setValor(valor);
        meta.setIdRolAutor(autor);
        if (existia) {
            meta.setFechaActualizacion(OffsetDateTime.now());
        }
        metas.save(meta);
    }

    private MetaRevision revision(long organizacion, PeriodoCalendario periodo, long agente,
                                  KpiCanonico kpi, Integer anterior, int valor, String motivo,
                                  long autor, String origen, String estado) {
        MetaRevision revision = new MetaRevision();
        revision.setOrganizacionId(organizacion);
        revision.setIdRolAgente(agente);
        revision.setKpi(kpi.codigo());
        revision.setAnio(periodo.anio());
        revision.setMes(periodo.mes());
        revision.setOrigen(origen);
        revision.setEstado(estado);
        revision.setValorAnterior(anterior);
        revision.setValorPropuesto(valor);
        revision.setMotivo(exigirMotivo(motivo));
        revision.setIdRolAutor(autor);
        return revision;
    }

    /**
     * El motivo es obligatorio en los dos sentidos: al fijar y al decidir.
     *
     * <p>Es lo único que quedará para entender el cambio dentro de seis meses, y
     * un «ok» no explica nada — el mismo criterio que la política aplica al
     * motivo de una reasignación.
     */
    private static String exigirMotivo(String motivo) {
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.length() < MetaRevision.MOTIVO_MINIMO) {
            throw new ReglaNegocioException(
                    "Explica el cambio de meta con al menos " + MetaRevision.MOTIVO_MINIMO
                            + " caracteres: queda en el historial y tiene que entenderse dentro "
                            + "de unos meses.");
        }
        return texto;
    }

    private static void exigirBroker(Actor actor, String queIntenta) {
        if (!actor.esBroker()) {
            throw new ReglaNegocioException(
                    "Solo un broker puede " + queIntenta + ". Administrar usuarios no es dirigir "
                            + "produccion: un administrador que ademas dirija comercialmente lo "
                            + "hace con su rol de broker.");
        }
    }

    // ------------------------------------------------------------------
    // Composición de la respuesta
    // ------------------------------------------------------------------

    private List<Long> agentesDelAlcance(Alcances.Alcance alcance) {
        if (alcance.global()) {
            // El administrador LEE los de su organizacion; fijar es otra cosa y
            // se le niega en exigirBroker.
            return rendimiento.agentesVigentes(alcance.idOrganizacion(), LocalDate.now());
        }
        return alcance.rolesAgente();
    }

    private Integer valorVigente(long organizacion, PeriodoCalendario periodo, long agente,
                                 KpiCanonico kpi) {
        return metas.findByOrganizacionIdAndIdRolAgenteAndKpiAndAnioAndMes(
                        organizacion, agente, kpi.codigo(), periodo.anio(), periodo.mes())
                .map(MetaComercial::getValor)
                .orElse(null);
    }

    /**
     * Una fila por agente y KPI, <b>incluidas las que no tienen meta</b>.
     *
     * <p>Devolver solo las fijadas dejaría la pantalla sin saber a quién le
     * falta, que es justo lo que hay que ver para poder fijarla, y haría
     * invisible la cobertura incompleta que deja al equipo sin semáforo.
     */
    private List<MetaDeAgente> filas(long organizacion, PeriodoCalendario periodo,
                                     List<Long> visibles) {
        if (visibles.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> fijadas = new HashMap<>();
        for (MetaComercial meta : metas.findByOrganizacionIdAndAnioAndMesAndIdRolAgenteIn(
                organizacion, periodo.anio(), periodo.mes(), visibles)) {
            fijadas.put(clave(meta.getIdRolAgente(), meta.getKpi()), meta.getValor());
        }

        Map<Long, String> nombres = nombresDe(organizacion, visibles);
        Map<String, List<Revision>> historial = new HashMap<>();
        Map<String, PropuestaPendiente> propuestas = new HashMap<>();
        for (MetaRevision revision : revisiones
                .findByOrganizacionIdAndAnioAndMesAndIdRolAgenteInOrderByIdAsc(
                        organizacion, periodo.anio(), periodo.mes(), visibles)) {
            String clave = clave(revision.getIdRolAgente(), revision.getKpi());
            historial.computeIfAbsent(clave, k -> new ArrayList<>())
                    .add(comoRevision(revision, nombres));
            if (revision.enEspera()) {
                propuestas.put(clave, pendiente(revision, nombres));
            }
        }

        List<MetaDeAgente> filas = new ArrayList<>();
        for (Long agente : visibles) {
            for (KpiCanonico kpi : KpiCanonico.TODOS) {
                String clave = clave(agente, kpi.codigo());
                filas.add(new MetaDeAgente(agente, nombres.getOrDefault(agente, "—"),
                        kpi.codigo(), kpi.rotulo(), fijadas.get(clave),
                        propuestas.get(clave),
                        List.copyOf(historial.getOrDefault(clave, List.of()))));
            }
        }
        return filas;
    }

    private Revision comoRevision(MetaRevision revision, Map<Long, String> nombres) {
        return new Revision(revision.getId(), revision.getOrigen(), revision.getEstado(),
                revision.getValorAnterior(), revision.getValorPropuesto(), revision.getMotivo(),
                nombreDe(revision.getIdRolAutor(), nombres), revision.getFechaCreacion(),
                revision.getIdRolDecisor() == null ? null
                        : nombreDe(revision.getIdRolDecisor(), nombres),
                revision.getMotivoDecision());
    }

    private PropuestaPendiente pendiente(MetaRevision revision, Map<Long, String> nombres) {
        KpiCanonico kpi = KpiCanonico.porCodigo(revision.getKpi());
        return new PropuestaPendiente(revision.getId(), revision.getIdRolAgente(),
                nombres.getOrDefault(revision.getIdRolAgente(), "—"),
                kpi.codigo(), kpi.rotulo(), revision.getValorAnterior(),
                revision.getValorPropuesto(), revision.getMotivo(), revision.getFechaCreacion());
    }

    private static String clave(Long agente, String kpi) {
        return agente + "|" + kpi;
    }

    /**
     * El nombre de quien firmó una revisión.
     *
     * <p>Se resuelve fuera del mapa de agentes porque el autor puede ser un
     * broker, y un broker no está en la lista de agentes supervisados.
     */
    private String nombreDe(Long idRol, Map<Long, String> nombres) {
        String conocido = nombres.get(idRol);
        if (conocido != null) {
            return conocido;
        }
        return roles.findById(idRol)
                .map(PersonaRol::getPersona)
                .map(Persona::getNombresORazonSocial)
                .filter(n -> !n.isBlank())
                .orElse("—");
    }

    private Map<Long, String> nombresDe(long organizacion, List<Long> visibles) {
        Map<Long, String> nombres = new HashMap<>();
        for (DetalleAgente ficha : agentes.listarFichas(organizacion)) {
            PersonaRol rol = ficha.getRol();
            if (rol == null || !visibles.contains(rol.getId())) {
                continue;
            }
            Persona persona = rol.getPersona();
            String nombre = persona != null ? persona.getNombresORazonSocial() : null;
            nombres.put(rol.getId(), nombre == null || nombre.isBlank() ? "—" : nombre);
        }
        return nombres;
    }
}
