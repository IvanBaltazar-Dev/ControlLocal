package com.controllocal.service.impl;

import com.controllocal.domain.comun.EstadosDominio.EstadoVisita;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.query.IndicadorCaptacion;
import com.controllocal.persistence.query.IndicadorContrato;
import com.controllocal.persistence.query.IndicadorInteraccion;
import com.controllocal.persistence.query.IndicadorOportunidad;
import com.controllocal.persistence.query.IndicadorProspeccion;
import com.controllocal.persistence.query.IndicadorSolicitud;
import com.controllocal.persistence.query.IndicadorVisita;
import com.controllocal.persistence.query.MotivoPorCaptacion;
import com.controllocal.persistence.query.SupervisionVigente;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.InteraccionComercialRepository;
import com.controllocal.persistence.repositorio.MotivoNoContinuidadRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.IndicadorService;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Descripciones;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.PoliticaComercial;
import com.controllocal.service.soporte.PoliticaComercial.Concepto;
import com.controllocal.service.soporte.PoliticaComercial.NivelAtencion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Agregador E4. Replica campo por campo el computo de {@code IndicadoresRest},
 * incluidos sus bugs congelados (D-E4-3), pero **el alcance y la ventana bajan
 * al WHERE** y lo que sube son proyecciones estrechas en vez de grafos de
 * entidades (D-E4-2): la v1 cargaba seis tablas completas en cada carga del
 * dashboard.
 *
 * <p>Reglas del cable faciles de perder, todas cubiertas por la suite:
 * <ul>
 *   <li>el donut de {@code etapas} es una particion EXCLUSIVA y NO depende del
 *       periodo; {@code captacionesSalud} si;</li>
 *   <li>{@code conversionPorPeriodo} se calcula por COHORTE (captaciones del
 *       periodo que cerraron, agrupadas por fecha de captacion), por eso nunca
 *       supera 100;</li>
 *   <li>si no hubo prospecciones en la ventana, el indicador operativo cae a
 *       TODAS las del alcance;</li>
 *   <li>el contrato no tiene agente propio: lo hereda de su solicitud y, en su
 *       defecto, de su oportunidad (§2 del contrato E4).</li>
 * </ul>
 */
@Service
public class IndicadorServiceImpl implements IndicadorService {

    private static final String[] MESES = {
            "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    };

    private static final int TOPE_DESEMPENO = 8;

    private final CaptacionRepository captaciones;
    private final OportunidadComercialRepository oportunidades;
    private final SolicitudAlquilerRepository solicitudes;
    private final ContratoAlquilerRepository contratos;
    private final VisitaRepository visitas;
    private final InteraccionComercialRepository interacciones;
    private final ProspeccionRepository prospecciones;
    private final MotivoNoContinuidadRepository motivos;
    private final DetalleAgenteRepository agentes;
    private final DetalleBrokerRepository brokers;
    private final SupervisionAgenteRepository supervisiones;
    private final Alcances alcances;

    public IndicadorServiceImpl(CaptacionRepository captaciones,
                                OportunidadComercialRepository oportunidades,
                                SolicitudAlquilerRepository solicitudes,
                                ContratoAlquilerRepository contratos,
                                VisitaRepository visitas,
                                InteraccionComercialRepository interacciones,
                                ProspeccionRepository prospecciones,
                                MotivoNoContinuidadRepository motivos,
                                DetalleAgenteRepository agentes,
                                DetalleBrokerRepository brokers,
                                SupervisionAgenteRepository supervisiones,
                                Alcances alcances) {
        this.captaciones = captaciones;
        this.oportunidades = oportunidades;
        this.solicitudes = solicitudes;
        this.contratos = contratos;
        this.visitas = visitas;
        this.interacciones = interacciones;
        this.prospecciones = prospecciones;
        this.motivos = motivos;
        this.agentes = agentes;
        this.brokers = brokers;
        this.supervisiones = supervisiones;
        this.alcances = alcances;
    }

    @Override
    @Transactional(readOnly = true)
    public Resumen resumen(String periodoParam, Actor actor) {
        Alcances.Alcance alcance = alcances.de(actor);
        long organizacion = alcance.idOrganizacion();
        Periodo periodo = Periodo.desde(periodoParam);
        LocalDate hoy = LocalDate.now();
        LocalDate inicio = hoy.minusDays(periodo.dias() - 1L);

        List<IndicadorCaptacion> caps = captaciones.indicadores(
                organizacion, alcance.global(), alcance.paramRoles());
        List<IndicadorOportunidad> ops = oportunidades.indicadores(
                organizacion, alcance.global(), alcance.paramRoles());
        List<IndicadorSolicitud> sols = solicitudes.indicadores(
                organizacion, alcance.global(), alcance.paramRoles());
        List<IndicadorVisita> vis = visitas.indicadores(
                organizacion, alcance.global(), alcance.paramRoles());
        List<IndicadorInteraccion> ints = interacciones.indicadores(
                organizacion, alcance.global(), alcance.paramRoles());
        List<IndicadorProspeccion> pros = prospecciones.indicadores(
                organizacion, alcance.global(), alcance.paramRoles());
        List<Cierre> conts = cierresEnAlcance(contratos.indicadores(organizacion), alcance);

        List<IndicadorCaptacion> capsPeriodo = caps.stream()
                .filter(c -> enPeriodo(c.getFechaCaptacion(), inicio, hoy)).toList();
        List<IndicadorOportunidad> opsPeriodo = ops.stream()
                .filter(o -> enPeriodo(dia(o.getFechaRegistro()), inicio, hoy)).toList();
        List<IndicadorVisita> visPeriodo = vis.stream()
                .filter(v -> enPeriodo(v.getFechaVisita(), inicio, hoy)).toList();
        List<IndicadorInteraccion> intsPeriodo = ints.stream()
                .filter(i -> enPeriodo(dia(i.getFechaHora()), inicio, hoy)).toList();
        List<Cierre> contsPeriodo = conts.stream()
                .filter(c -> enPeriodo(c.fecha(), inicio, hoy)).toList();
        List<IndicadorProspeccion> prosPeriodo = pros.stream()
                .filter(p -> enPeriodo(dia(p.getFechaRegistro()), inicio, hoy)).toList();

        int captacionesPorRevisar = contarEstado(caps, "P");
        int captacionesObservadas = contarEstado(caps, "O");
        int captacionesActivas = contarEstado(caps, "A");
        int solicitudesPorEvaluar = (int) sols.stream()
                .filter(s -> "E".equals(s.getEstado()) || "O".equals(s.getEstado())).count();
        int oportunidadesActivas = (int) ops.stream()
                .filter(o -> "A".equals(o.getEstado()) || "S".equals(o.getEstado())).count();
        int propiedadesEquipo = (int) caps.stream()
                .filter(c -> "A".equals(c.getEstado()))
                .map(IndicadorCaptacion::getIdPropiedad)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        Set<Long> capsConContrato = conts.stream()
                .map(Cierre::idCaptacion).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Conteo> etapas = etapas(caps, ops, sols, capsConContrato);
        List<Conteo> salud = List.of(
                new Conteo("Activas", contarEstado(capsPeriodo, "A")),
                new Conteo("Por revisar", contarEstado(capsPeriodo, "P")),
                new Conteo("Observadas", contarEstado(capsPeriodo, "O")),
                new Conteo("Bloqueadas/cerradas", (int) capsPeriodo.stream()
                        .filter(c -> "R".equals(c.getEstado()) || "V".equals(c.getEstado())
                                || "C".equals(c.getEstado()))
                        .count()));

        List<Embudo> embudo = embudo(opsPeriodo, visPeriodo);

        Serie cierresSerie = serie(contsPeriodo.stream().map(Cierre::fecha).toList(), periodo, inicio, hoy);
        Serie captacionesSerie = serie(
                capsPeriodo.stream().map(IndicadorCaptacion::getFechaCaptacion).toList(), periodo, inicio, hoy);
        // La linea de conversion y la cifra agregada salen de la MISMA cohorte: las
        // captaciones nacidas en la ventana que ya cerraron, agrupadas por su fecha de
        // captacion (no por la del contrato). Es lo que garantiza que nunca pase de 100 %.
        List<IndicadorCaptacion> capsPeriodoCerradas = capsPeriodo.stream()
                .filter(c -> c.getId() != null && capsConContrato.contains(c.getId())).toList();
        Serie cierresCohorteSerie = serie(
                capsPeriodoCerradas.stream().map(IndicadorCaptacion::getFechaCaptacion).toList(),
                periodo, inicio, hoy);
        List<Integer> conversionSerie = conversionSerie(
                cierresCohorteSerie.valores(), captacionesSerie.valores());

        List<Desempeno> desempeno = actor.esTenantAdmin()
                ? desempenoPorBroker(organizacion, capsPeriodo, contsPeriodo)
                : desempenoPorAgente(organizacion, alcance, capsPeriodo, contsPeriodo);

        int agentesActivos = alcance.global()
                ? (int) agentes.countByOrganizacionId(organizacion)
                : alcance.rolesAgente().size();
        // El ADMIN es un broker con es_administrador: supervisa, no produce, y no se
        // cuenta a si mismo entre los brokers activos.
        int brokersActivos = actor.esTenantAdmin()
                ? (int) brokers.listarFichas(organizacion).stream()
                        .filter(b -> !b.isEsAdministrador()).count()
                : (actor.esAgente() ? 0 : 1);

        // Descongelado 2026-08-08: antes decia `prosPeriodo.isEmpty() ? pros : prosPeriodo`.
        // Si la ventana no tenia ni una prospeccion, el operativo caia a TODO el
        // historial del alcance, asi que "ultimos 7 dias" pasaba a significar
        // "desde siempre" sin avisar — y los recontactos vencidos de hace un ano
        // aparecian como si fueran de esta semana. Un periodo vacio ahora se
        // informa vacio, que es lo que es.
        Operativo operativo = operativo(prosPeriodo, vis, sols, hoy);

        Map<Concepto, Integer> valores = valoresPorConcepto(captacionesPorRevisar,
                solicitudesPorEvaluar, contsPeriodo.size(), agentesActivos, operativo);

        return new Resumen(
                ambito(actor),
                captacionesPorRevisar,
                solicitudesPorEvaluar,
                capsPeriodo.size(),
                captacionesActivas,
                captacionesObservadas,
                oportunidadesActivas,
                intsPeriodo.size(),
                visPeriodo.size(),
                contsPeriodo.size(),
                capsPeriodoCerradas.size(),
                conversionPropia(capsPeriodoCerradas.size(), capsPeriodo.size()),
                agentesActivos,
                brokersActivos,
                propiedadesEquipo,
                cierresSerie.etiquetas(),
                cierresSerie.valores(),
                conversionSerie,
                captacionesSerie.valores(),
                etapas,
                salud,
                embudo,
                desempeno,
                operativo,
                senales(valores),
                pendientesDeAtencion(valores));
    }

    /** El valor que le toca a cada concepto del tablero, en un solo sitio. */
    private static Map<Concepto, Integer> valoresPorConcepto(int captacionesPorRevisar,
                                                             int solicitudesPorEvaluar,
                                                             int cierres,
                                                             int agentesActivos,
                                                             Operativo op) {
        Map<Concepto, Integer> valores = new EnumMap<>(Concepto.class);
        valores.put(Concepto.SOLICITUD_POR_EVALUAR, solicitudesPorEvaluar);
        valores.put(Concepto.RECONTACTO_VENCIDO, op.recontactosVencidos());
        valores.put(Concepto.CAPTACION_POR_REVISAR, captacionesPorRevisar);
        valores.put(Concepto.SOLICITUD_APROBADA_SIN_CIERRE, op.solicitudesSinCierre());
        valores.put(Concepto.DEMORA_DE_SEGUIMIENTO, op.diasPromedioSinSeguimiento());
        valores.put(Concepto.VISITA_PENDIENTE, op.visitasPendientes());
        valores.put(Concepto.CIERRE_REGISTRADO, cierres);
        valores.put(Concepto.COBERTURA_DE_AGENTES, agentesActivos);
        return valores;
    }

    /**
     * Cuantas <b>cosas</b> reclaman atencion ahora mismo (E2.1). Es el numero
     * con el que abre el tablero, y por eso no puede salir de sumar lo que haya:
     * solo entran los conceptos que cuentan unidades —{@code
     * DEMORA_DE_SEGUIMIENTO} vale dias— y solo los que el dominio clasifico como
     * pendientes.
     */
    private static int pendientesDeAtencion(Map<Concepto, Integer> valores) {
        int total = 0;
        for (Map.Entry<Concepto, Integer> entrada : valores.entrySet()) {
            Concepto concepto = entrada.getKey();
            int valor = entrada.getValue();
            if (concepto.cuentaCosas()
                    && PoliticaComercial.requiereAtencion(
                            PoliticaComercial.clasificar(concepto, valor))) {
                total += valor;
            }
        }
        return total;
    }

    /**
     * Los hechos del tablero, ya interpretados (R-07). Uno por concepto y en el
     * orden en que la politica dice que se atienden; la pantalla filtra los que
     * su rol muestra, pero no reclasifica ni reordena.
     *
     * <p>Se emiten <b>todos</b>, incluidos los que estan en cero: un cero
     * clasificado como {@code SIN_PENDIENTES} es informacion ("no hay nada
     * atrasado"), y omitirlo obligaria al cliente a distinguir "no vino" de "no
     * hay", que es justo la ambiguedad que este bloque viene a quitar.
     */
    private static List<IndicadorService.Senal> senales(Map<Concepto, Integer> valores) {
        return Arrays.stream(Concepto.values())
                .sorted(Comparator.comparingInt(Concepto::prioridad))
                .map(concepto -> {
                    int valor = valores.getOrDefault(concepto, 0);
                    NivelAtencion nivel = PoliticaComercial.clasificar(concepto, valor);
                    return new IndicadorService.Senal(concepto.name(), valor, nivel.name(),
                            PoliticaComercial.requiereAtencion(nivel), concepto.prioridad());
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AvanceComercial avance(Actor actor) {
        Alcances.Alcance alcance = alcances.de(actor);
        long organizacion = alcance.idOrganizacion();

        List<IndicadorCaptacion> caps = captaciones.indicadores(
                        organizacion, alcance.global(), alcance.paramRoles()).stream()
                .filter(c -> "A".equals(c.getEstado()))
                .toList();
        List<IndicadorOportunidad> ops = oportunidades.indicadores(
                organizacion, alcance.global(), alcance.paramRoles());
        List<IndicadorSolicitud> sols = solicitudes.indicadores(
                organizacion, alcance.global(), alcance.paramRoles());
        List<IndicadorVisita> vis = visitas.indicadores(
                organizacion, alcance.global(), alcance.paramRoles());
        List<IndicadorInteraccion> ints = interacciones.indicadores(
                organizacion, alcance.global(), alcance.paramRoles());
        Map<Long, String> motivoPrincipal = motivosPrincipales(organizacion, alcance);

        Map<Long, List<IndicadorOportunidad>> opsPorCaptacion = agrupar(
                ops, IndicadorOportunidad::getIdCaptacion);
        Map<Long, List<IndicadorVisita>> visPorOportunidad = agrupar(
                vis, IndicadorVisita::getIdOportunidad);
        Map<Long, List<IndicadorSolicitud>> solsPorCaptacion = agrupar(
                sols, IndicadorSolicitud::getIdCaptacion);
        Map<Long, List<IndicadorSolicitud>> solsPorOportunidad = agrupar(
                sols, IndicadorSolicitud::getIdOportunidad);
        Map<Long, List<IndicadorInteraccion>> intsPorCaptacion = agrupar(
                ints, IndicadorInteraccion::getIdCaptacion);
        Map<Long, List<IndicadorInteraccion>> intsPorOportunidad = agrupar(
                ints, IndicadorInteraccion::getIdOportunidad);

        List<AvancePropiedad> detalle = new ArrayList<>();
        Set<Long> interesadosGlobal = new HashSet<>();
        int totalOpsGlobal = 0;

        for (IndicadorCaptacion c : caps) {
            Long idCap = c.getId();
            if (idCap == null) {
                continue;
            }
            List<IndicadorOportunidad> opsCap = opsPorCaptacion.getOrDefault(idCap, List.of());
            Set<Long> idsOpsCap = opsCap.stream()
                    .map(IndicadorOportunidad::getId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            List<IndicadorVisita> visCap = idsOpsCap.stream()
                    .flatMap(idOp -> visPorOportunidad.getOrDefault(idOp, List.<IndicadorVisita>of()).stream())
                    .toList();
            int oportunidadesConVisita = (int) idsOpsCap.stream()
                    .filter(idOp -> !visPorOportunidad.getOrDefault(idOp, List.of()).isEmpty())
                    .count();

            // La solicitud entra por su captacion O por una oportunidad de la captacion.
            Set<Long> idsSolsCap = new LinkedHashSet<>();
            List<IndicadorSolicitud> solsCap = new ArrayList<>();
            for (IndicadorSolicitud s : solsPorCaptacion.getOrDefault(idCap, List.of())) {
                if (s.getId() != null && idsSolsCap.add(s.getId())) {
                    solsCap.add(s);
                }
            }
            for (Long idOp : idsOpsCap) {
                for (IndicadorSolicitud s : solsPorOportunidad.getOrDefault(idOp, List.of())) {
                    if (s.getId() != null && idsSolsCap.add(s.getId())) {
                        solsCap.add(s);
                    }
                }
            }
            Set<Long> opsConSolicitud = solsCap.stream()
                    .map(IndicadorSolicitud::getIdOportunidad).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            // Respaldo por estado, del cable: si la solicitud no quedo enlazada, la
            // oportunidad en S o F igual cuenta como "con solicitud".
            int conSolicitudPorEstado = (int) opsCap.stream()
                    .filter(o -> "S".equals(o.getEstado()) || "F".equals(o.getEstado())).count();
            int oportunidadesConSolicitud = Math.max(
                    (int) idsOpsCap.stream().filter(opsConSolicitud::contains).count(),
                    conSolicitudPorEstado);

            Set<Long> interesados = new HashSet<>();
            opsCap.forEach(o -> agregar(interesados, o.getIdCliente()));
            solsCap.forEach(s -> agregar(interesados, s.getIdCliente()));
            interesadosGlobal.addAll(interesados);

            Set<Long> idsInts = new HashSet<>();
            intsPorCaptacion.getOrDefault(idCap, List.of())
                    .forEach(i -> agregar(idsInts, i.getId()));
            idsOpsCap.forEach(idOp -> intsPorOportunidad.getOrDefault(idOp, List.<IndicadorInteraccion>of())
                    .forEach(i -> agregar(idsInts, i.getId())));

            int totalOps = opsCap.size();
            totalOpsGlobal += totalOps;
            detalle.add(new AvancePropiedad(
                    idCap,
                    textoSeguro(c.getCodigo()),
                    textoSeguro(c.getDireccion()),
                    textoSeguro(c.getDistrito()),
                    Descripciones.captacion(c.getEstado()),
                    totalOps,
                    (int) opsCap.stream().filter(o -> "A".equals(o.getEstado())).count(),
                    oportunidadesConVisita,
                    oportunidadesConSolicitud,
                    (int) opsCap.stream().filter(o -> "F".equals(o.getEstado())).count(),
                    (int) opsCap.stream().filter(o -> "X".equals(o.getEstado())).count(),
                    (int) opsCap.stream().filter(o -> "N".equals(o.getEstado())).count(),
                    interesados.size(),
                    idsInts.size(),
                    (int) visCap.stream()
                            .filter(v -> "P".equals(v.getEstado()) || "G".equals(v.getEstado())).count(),
                    (int) visCap.stream().filter(v -> "R".equals(v.getEstado())).count(),
                    solsCap.size(),
                    porcentaje(oportunidadesConVisita, totalOps),
                    porcentaje(oportunidadesConSolicitud, totalOps),
                    motivoPrincipal.getOrDefault(idCap, "")));
        }

        detalle.sort(Comparator.comparingInt(AvancePropiedad::oportunidadesAbiertas).reversed()
                .thenComparing(Comparator.comparingInt(AvancePropiedad::interacciones).reversed()));

        int aConVisita = suma(detalle, AvancePropiedad::oportunidadesConVisita);
        int aConSolicitud = suma(detalle, AvancePropiedad::oportunidadesConSolicitud);
        return new AvanceComercial(
                actor.esTenantAdmin() ? "Avance comercial global"
                        : actor.esAgente() ? "Mi avance comercial" : "Avance comercial del equipo",
                detalle.size(),
                totalOpsGlobal,
                suma(detalle, AvancePropiedad::oportunidadesAbiertas),
                aConVisita,
                aConSolicitud,
                suma(detalle, AvancePropiedad::cerradasExitosas),
                suma(detalle, AvancePropiedad::cerradasNoFavorables),
                suma(detalle, AvancePropiedad::cerradasNoContinuidad),
                interesadosGlobal.size(),
                suma(detalle, AvancePropiedad::interacciones),
                suma(detalle, AvancePropiedad::visitasProgramadas),
                suma(detalle, AvancePropiedad::visitasConcretadas),
                suma(detalle, AvancePropiedad::solicitudesRecibidas),
                porcentaje(aConVisita, totalOpsGlobal),
                porcentaje(aConSolicitud, totalOpsGlobal),
                detalle);
    }

    // ---------- etapas, salud y embudo ----------

    /**
     * Particion EXCLUSIVA de las captaciones por su etapa mas avanzada. Cada
     * captacion cuenta UNA vez, de modo que las porciones suman el total
     * clasificable y el centro del donut puede mostrar el % que llego a
     * Alquilada. Las que siguen en aprobacion del broker no son embudo y no
     * entran a ninguna etapa intermedia.
     */
    private List<Conteo> etapas(List<IndicadorCaptacion> caps,
                                List<IndicadorOportunidad> ops,
                                List<IndicadorSolicitud> sols,
                                Set<Long> capsConContrato) {
        Set<Long> capsConSolicitud = new HashSet<>();
        Set<Long> capsEnEvaluacion = new HashSet<>();
        for (IndicadorSolicitud s : sols) {
            Long idCap = s.getIdCaptacion();
            if (idCap == null) {
                continue;
            }
            capsConSolicitud.add(idCap);
            String estado = s.getEstado();
            if ("E".equals(estado) || "O".equals(estado) || "A".equals(estado)) {
                capsEnEvaluacion.add(idCap);
            }
        }
        Set<Long> capsConOportunidad = ops.stream()
                .map(IndicadorOportunidad::getIdCaptacion).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int captacion = 0, interesados = 0, solicitud = 0, evaluacion = 0, alquilada = 0;
        for (IndicadorCaptacion c : caps) {
            Long id = c.getId();
            if (id == null) {
                continue;
            }
            if (capsConContrato.contains(id)) {
                alquilada++; // la alquilada cuenta aunque su captacion ya figure CERRADA
                continue;
            }
            if (!"A".equals(c.getEstado())) {
                continue;
            }
            if (capsEnEvaluacion.contains(id)) {
                evaluacion++;
            } else if (capsConSolicitud.contains(id)) {
                solicitud++;
            } else if (capsConOportunidad.contains(id)) {
                interesados++;
            } else {
                captacion++;
            }
        }
        return List.of(
                new Conteo("Captacion activa", captacion),
                new Conteo("Clientes interesados", interesados),
                new Conteo("Con solicitud", solicitud),
                new Conteo("En evaluacion", evaluacion),
                new Conteo("Alquilada", alquilada));
    }

    /**
     * Embudo del periodo. <b>Las dos rarezas de la v1 se corrigieron el
     * 2026-08-08</b> al descongelar el contrato:
     *
     * <ol>
     *   <li>La primera fila llevaba {@code 100} fijo <b>aunque la base fuera
     *       0</b>: un periodo sin una sola oportunidad pintaba "100 %" en el
     *       tramo de cabecera. Ahora es 0 cuando no hay nada, que es lo que
     *       significa.</li>
     *   <li>"Con visita realizada" <b>no miraba el estado de la visita</b>:
     *       contaba oportunidades con visita de cualquier estado, canceladas y
     *       no realizadas incluidas. El nombre prometia una cosa y el numero
     *       decia otra, y encima inflaba la conversion justo donde se mide si
     *       el equipo esta trabajando.</li>
     * </ol>
     */
    private List<Embudo> embudo(List<IndicadorOportunidad> opsPeriodo, List<IndicadorVisita> visPeriodo) {
        Set<Long> conVisitaEnPeriodo = visPeriodo.stream()
                .filter(v -> EstadoVisita.REALIZADA.codigo().equals(v.getEstado()))
                .map(IndicadorVisita::getIdOportunidad).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int base = opsPeriodo.size();
        int conVisita = (int) opsPeriodo.stream()
                .map(IndicadorOportunidad::getId)
                .filter(id -> id != null && conVisitaEnPeriodo.contains(id)).count();
        int conSolicitud = (int) opsPeriodo.stream()
                .filter(o -> "S".equals(o.getEstado()) || "F".equals(o.getEstado())).count();
        int cerradasExitosas = (int) opsPeriodo.stream()
                .filter(o -> "F".equals(o.getEstado())).count();
        return List.of(
                new Embudo("Oportunidades activas", base, base > 0 ? 100 : 0),
                new Embudo("Con visita realizada", conVisita, porcentaje(conVisita, base)),
                new Embudo("Con solicitud creada", conSolicitud, porcentaje(conSolicitud, base)),
                new Embudo("Cerradas exitosas", cerradasExitosas, porcentaje(cerradasExitosas, base)));
    }

    // ---------- desempeno ----------

    private List<Desempeno> desempenoPorBroker(long organizacion,
                                               List<IndicadorCaptacion> capsPeriodo,
                                               List<Cierre> contsPeriodo) {
        Map<Long, Set<Long>> equipos = supervisiones.equiposVigentes(organizacion).stream()
                .filter(s -> s.getIdBroker() != null && s.getIdAgente() != null)
                .collect(Collectors.groupingBy(SupervisionVigente::getIdBroker,
                        Collectors.mapping(SupervisionVigente::getIdAgente, Collectors.toSet())));
        List<Desempeno> filas = new ArrayList<>();
        for (DetalleBroker broker : brokers.listarFichas(organizacion)) {
            if (broker.getId() == null || broker.isEsAdministrador()) {
                continue;
            }
            Set<Long> equipo = equipos.getOrDefault(broker.getId(), Set.of());
            if (equipo.isEmpty()) {
                continue;
            }
            agregarFila(filas, nombre(broker.getRol()), capsPeriodo, contsPeriodo, equipo::contains);
        }
        return recortar(filas);
    }

    private List<Desempeno> desempenoPorAgente(long organizacion,
                                               Alcances.Alcance alcance,
                                               List<IndicadorCaptacion> capsPeriodo,
                                               List<Cierre> contsPeriodo) {
        List<DetalleAgente> fuente = alcance.global()
                ? agentes.listarFichas(organizacion)
                : agentes.buscarFichas(organizacion, alcance.paramRoles());
        List<Desempeno> filas = new ArrayList<>();
        for (DetalleAgente agente : fuente) {
            Long id = agente.getId();
            if (id == null) {
                continue;
            }
            agregarFila(filas, nombre(agente.getRol()), capsPeriodo, contsPeriodo, id::equals);
        }
        return recortar(filas);
    }

    /** Una fila de desempeno; se omite la que no tiene ni captaciones ni cierres. */
    private void agregarFila(List<Desempeno> filas,
                             String nombre,
                             List<IndicadorCaptacion> capsPeriodo,
                             List<Cierre> contsPeriodo,
                             Predicate<Long> suyo) {
        int nCaps = (int) capsPeriodo.stream()
                .filter(c -> c.getIdAgente() != null && suyo.test(c.getIdAgente())).count();
        int nCierres = (int) contsPeriodo.stream()
                .filter(c -> c.idAgente() != null && suyo.test(c.idAgente())).count();
        if (nCaps == 0 && nCierres == 0) {
            return;
        }
        filas.add(new Desempeno(nombre, nCaps, nCierres, porcentaje(nCierres, nCaps)));
    }

    /**
     * Cierres desc y tope de 8, como la v1. Los dos desempates (captaciones y
     * nombre) solo hacen determinista lo que alli dependia del orden en que el
     * DAO devolvia los responsables — la regla observable sigue siendo "los que
     * mas cerraron primero".
     */
    private List<Desempeno> recortar(List<Desempeno> filas) {
        filas.sort(Comparator.comparingInt(Desempeno::cierres).reversed()
                .thenComparing(Comparator.comparingInt(Desempeno::captaciones).reversed())
                .thenComparing(Desempeno::nombre));
        return filas.stream().limit(TOPE_DESEMPENO).toList();
    }

    // ---------- operativo ----------

    /**
     * Indicadores operativos del seguimiento, sobre las prospecciones <b>del
     * periodo pedido</b>.
     *
     * <p>El fallback de la v1 —si la ventana venia vacia, se usaban todas las
     * del alcance— se retiro el 2026-08-08. Visitas y solicitudes, en cambio,
     * siguen sin acotarse al periodo: eso es del contrato y no es un descuido.
     */
    private Operativo operativo(List<IndicadorProspeccion> pros,
                                List<IndicadorVisita> vis,
                                List<IndicadorSolicitud> sols,
                                LocalDate hoy) {
        LocalDate limiteVencido = PoliticaComercial.limiteDeRecontacto(hoy);
        int vencidos = 0;
        int alDia = 0;
        long sumaAtraso = 0;
        for (IndicadorProspeccion p : pros) {
            if ("T".equals(p.getEstado()) || "D".equals(p.getEstado())) {
                continue; // solo prospecciones en seguimiento activo
            }
            LocalDate recontacto = p.getFechaRecontacto();
            if (recontacto == null) {
                continue;
            }
            // Vencido = pasado el plazo de la politica, igual que la bandeja. Un
            // recontacto recien cumplido todavia no escala: cuenta como al dia.
            if (recontacto.isAfter(limiteVencido)) {
                alDia++;
            } else {
                vencidos++;
                sumaAtraso += ChronoUnit.DAYS.between(recontacto, hoy);
            }
        }
        int captadas = (int) pros.stream().filter(p -> "T".equals(p.getEstado())).count();
        return new Operativo(
                vencidos,
                alDia,
                vencidos > 0 ? (int) Math.round((double) sumaAtraso / vencidos) : 0,
                (int) vis.stream()
                        .filter(v -> "P".equals(v.getEstado()) || "G".equals(v.getEstado())).count(),
                (int) sols.stream().filter(s -> "A".equals(s.getEstado())).count(),
                porcentaje(captadas, pros.size()));
    }

    // ---------- contratos ----------

    /**
     * Cierre ya resuelto: el contrato con el agente y la captacion que hereda.
     */
    private record Cierre(long id, LocalDate fecha, Long idAgente, Long idCaptacion) {
    }

    /**
     * Alcance indirecto del contrato (§2 del contrato E4). La v1 lo consigue
     * como efecto colateral de que el DAO devuelve la solicitud "shallow" y
     * solo la completa si esta en el alcance; aqui la regla queda explicita:
     * manda la solicitud cuando su agente esta en alcance, y si no, la
     * oportunidad. Para el ADMIN "estar en alcance" es siempre cierto, asi que
     * manda la solicitud salvo que el contrato no tenga.
     */
    private List<Cierre> cierresEnAlcance(List<IndicadorContrato> fuente, Alcances.Alcance alcance) {
        List<Cierre> resultado = new ArrayList<>();
        for (IndicadorContrato c : fuente) {
            boolean porSolicitud = c.getIdAgenteSolicitud() != null
                    && enAlcance(alcance, c.getIdAgenteSolicitud());
            Long idAgente = porSolicitud ? c.getIdAgenteSolicitud() : c.getIdAgenteOportunidad();
            if (!enAlcance(alcance, idAgente)) {
                continue;
            }
            resultado.add(new Cierre(
                    c.getId(),
                    c.getFechaCierre(),
                    idAgente,
                    porSolicitud ? c.getIdCaptacionSolicitud() : c.getIdCaptacionOportunidad()));
        }
        return resultado;
    }

    // ---------- series ----------

    private record Serie(List<String> etiquetas, List<Integer> valores) {
    }

    /**
     * Un cubo por dia hasta 31 dias de ventana y por mes en adelante. Las dos
     * series del resumen comparten estas etiquetas, asi que el grafico combinado
     * puede superponerlas.
     */
    private Serie serie(List<LocalDate> fechas, Periodo periodo, LocalDate inicio, LocalDate hoy) {
        List<String> etiquetas = new ArrayList<>();
        List<Integer> valores = new ArrayList<>();
        if (periodo.dias() <= 31) {
            for (LocalDate cursor = inicio; !cursor.isAfter(hoy); cursor = cursor.plusDays(1)) {
                LocalDate dia = cursor;
                etiquetas.add(String.format("%02d/%02d", dia.getDayOfMonth(), dia.getMonthValue()));
                valores.add((int) fechas.stream().filter(dia::equals).count());
            }
            return new Serie(etiquetas, valores);
        }
        YearMonth fin = YearMonth.from(hoy);
        for (YearMonth cursor = YearMonth.from(inicio); !cursor.isAfter(fin); cursor = cursor.plusMonths(1)) {
            YearMonth mes = cursor;
            etiquetas.add(MESES[mes.getMonthValue() - 1] + " "
                    + String.valueOf(mes.getYear()).substring(2));
            valores.add((int) fechas.stream()
                    .filter(f -> f != null && mes.equals(YearMonth.from(f))).count());
        }
        return new Serie(etiquetas, valores);
    }

    private static List<Integer> conversionSerie(List<Integer> cierres, List<Integer> captaciones) {
        int n = Math.min(cierres.size(), captaciones.size());
        List<Integer> valores = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            valores.add(porcentaje(cierres.get(i), captaciones.get(i)));
        }
        return valores;
    }

    // ---------- soporte ----------

    private Map<Long, String> motivosPrincipales(long organizacion, Alcances.Alcance alcance) {
        Map<Long, String> principal = new HashMap<>();
        // La consulta llega ordenada por captacion y frecuencia descendente: la
        // primera fila de cada captacion es su motivo principal.
        for (MotivoPorCaptacion fila : motivos.principalPorCaptacion(
                organizacion, alcance.global(), alcance.paramRoles())) {
            if (fila.getIdCaptacion() != null) {
                principal.putIfAbsent(fila.getIdCaptacion(),
                        Descripciones.razonNoContinuidad(fila.getRazon()));
            }
        }
        return principal;
    }

    private static <T> Map<Long, List<T>> agrupar(List<T> filas, Function<T, Long> clave) {
        Map<Long, List<T>> agrupado = new HashMap<>();
        for (T fila : filas) {
            Long id = clave.apply(fila);
            if (id != null) {
                agrupado.computeIfAbsent(id, k -> new ArrayList<>()).add(fila);
            }
        }
        return agrupado;
    }

    private static void agregar(Set<Long> destino, Long valor) {
        if (valor != null) {
            destino.add(valor);
        }
    }

    private static int suma(List<AvancePropiedad> detalle, ToIntFunction<AvancePropiedad> campo) {
        return detalle.stream().mapToInt(campo).sum();
    }

    private static int contarEstado(List<IndicadorCaptacion> caps, String estado) {
        return (int) caps.stream().filter(c -> estado.equals(c.getEstado())).count();
    }

    private static String ambito(Actor actor) {
        return actor.esTenantAdmin() ? "Reportes globales"
                : actor.esAgente() ? "Mi actividad" : "Reportes de equipo";
    }

    private static boolean enAlcance(Alcances.Alcance alcance, Long idAgente) {
        return alcance.global() || (idAgente != null && alcance.rolesAgente().contains(idAgente));
    }

    private static boolean enPeriodo(LocalDate fecha, LocalDate inicio, LocalDate fin) {
        return fecha != null && !fecha.isBefore(inicio) && !fecha.isAfter(fin);
    }

    private static LocalDate dia(OffsetDateTime fecha) {
        java.time.LocalDateTime local = Fechas.local(fecha);
        return local != null ? local.toLocalDate() : null;
    }

    private static String nombre(PersonaRol rol) {
        Persona persona = rol != null ? rol.getPersona() : null;
        String valor = persona != null ? persona.getNombresORazonSocial() : null;
        return valor == null || valor.isBlank() ? "—" : valor;
    }

    private static String textoSeguro(String texto) {
        return texto == null ? "" : texto;
    }

    /**
     * Conversion de la cohorte, o {@code null} cuando <b>no hay cohorte</b>
     * (E2.0).
     *
     * <p>Es la unica tasa del resumen que puede no existir, y por eso no usa
     * {@link #porcentaje}: sin captaciones en el periodo no se convirtio nada
     * <i>porque no habia nada que convertir</i>, que no es lo mismo que haber
     * trabajado doce y no cerrar ninguna. Las dos situaciones daban 0 y la
     * pantalla no podia distinguirlas.
     */
    private static Integer conversionPropia(int cerradas, int captaciones) {
        return captaciones <= 0 ? null : porcentaje(cerradas, captaciones);
    }

    /**
     * Todos los usos son ratios parte&lt;=total. El clamp a 100 es la red de
     * seguridad del cable: ninguna tasa se pinta por encima de 100 %.
     */
    private static int porcentaje(int parte, int total) {
        return total <= 0 ? 0 : Math.min(100, (int) Math.round(parte * 100.0 / total));
    }

    /** Ventanas del cable: cualquier valor no reconocido cae en 6 meses. */
    private record Periodo(String codigo, int dias) {

        static Periodo desde(String valor) {
            return switch (valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT)) {
                case "7", "7d", "semana" -> new Periodo("7d", 7);
                case "15", "15d" -> new Periodo("15d", 15);
                case "1m", "30", "30d", "mes" -> new Periodo("1m", 30);
                case "3m", "90", "90d" -> new Periodo("3m", 90);
                case "1y", "12m", "365", "365d", "ano", "anio" -> new Periodo("1y", 365);
                default -> new Periodo("6m", 180);
            };
        }
    }
}
