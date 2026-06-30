package com.controllocal.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.controllocal.bl.AgenteBusinessLogic;
import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.ContratoAlquilerBusinessLogic;
import com.controllocal.bl.InteraccionComercialBusinessLogic;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.ProspeccionBusinessLogic;
import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.bl.VisitaBusinessLogic;
import com.controllocal.bl.MotivoNoContinuidadBusinessLogic;
import com.controllocal.bl.impl.AgenteBusinessLogicImpl;
import com.controllocal.bl.impl.MotivoNoContinuidadBusinessLogicImpl;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.ContratoAlquilerBusinessLogicImpl;
import com.controllocal.bl.impl.InteraccionComercialBusinessLogicImpl;
import com.controllocal.bl.impl.OportunidadComercialBusinessLogicImpl;
import com.controllocal.bl.impl.ProspeccionBusinessLogicImpl;
import com.controllocal.bl.impl.SolicitudAlquilerBusinessLogicImpl;
import com.controllocal.bl.impl.VisitaBusinessLogicImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.comercial.MotivoNoContinuidad;
import com.controllocal.model.comercial.enums.EstadoCaptacion;
import com.controllocal.model.comercial.enums.EstadoOportunidadComercial;
import com.controllocal.model.comercial.enums.EstadoProspeccion;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.enums.EstadoVisita;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

/**
 * Indicadores agregados para los paneles (dashboard / reportes) y los contadores del
 * menu lateral. Reusa los listados de la capa BL y agrega en memoria; no agrega SQL.
 * El alcance se resuelve por rol: el agente ve lo suyo, el broker a su equipo y el
 * admin todo. Un solo GET alimenta tarjetas, graficas, embudo, desempeno y pills.
 */
@Path("indicadores")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IndicadoresRest {

    private static final String[] MESES = {
            "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    };

    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();
    private final SolicitudAlquilerBusinessLogic solicitudes = new SolicitudAlquilerBusinessLogicImpl();
    private final OportunidadComercialBusinessLogic oportunidades = new OportunidadComercialBusinessLogicImpl();
    private final ContratoAlquilerBusinessLogic contratos = new ContratoAlquilerBusinessLogicImpl();
    private final VisitaBusinessLogic visitas = new VisitaBusinessLogicImpl();
    private final ProspeccionBusinessLogic prospecciones = new ProspeccionBusinessLogicImpl();
    private final InteraccionComercialBusinessLogic interacciones = new InteraccionComercialBusinessLogicImpl();
    private final AgenteBusinessLogic agentes = new AgenteBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();
    private final MotivoNoContinuidadBusinessLogic motivosNoContinuidad = new MotivoNoContinuidadBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    @Path("resumen")
    public Dtos.IndicadoresResponse resumen(@QueryParam("periodo") String periodoParam) {
        return resumen(SeguridadRest.usuario(request), periodoParam);
    }

    // Reutilizable por DashboardRest: calcula el resumen para un usuario ya autenticado (sin tocar
    // el HttpServletRequest), de modo que el endpoint agregado no repita el computo por separado.
    Dtos.IndicadoresResponse resumen(UsuarioAutenticado usuario, String periodoParam) {
        boolean esAdmin = usuario.tieneRol("ADMIN");
        boolean esBroker = usuario.tieneRol("BROKER");
        // null = sin filtro (admin ve todo); en caso contrario, ids de agentes en alcance.
        Set<Long> alcance = agentesEnAlcance(usuario);
        Periodo periodo = Periodo.desde(periodoParam);
        LocalDate hoy = LocalDate.now();
        LocalDate inicio = hoy.minusDays(periodo.dias() - 1L);

        // Alcance por rol resuelto en SQL: el admin (alcance == null) agrega sobre todo; el
        // agente/broker solo carga las filas de su(s) agente(s) via listarPorAgentes(...), en
        // vez de traer la tabla completa y filtrar en memoria. El .filter posterior queda como
        // red de seguridad (no-op cuando la lista ya viene acotada).
        List<Captacion> caps = (alcance == null ? captaciones.listarTodos() : captaciones.listarPorAgentes(alcance))
                .stream().filter(c -> enAlcance(alcance, idAgente(c.getAgenteResponsable())))
                .toList();
        List<OportunidadComercial> opsTodas = alcance == null
                ? oportunidades.listarTodos() : oportunidades.listarPorAgentes(alcance);
        List<OportunidadComercial> ops = opsTodas.stream()
                .filter(o -> enAlcance(alcance, idAgente(o.getAgenteResponsable())))
                .toList();
        List<SolicitudAlquiler> solsTodas = alcance == null
                ? solicitudes.listarTodos() : solicitudes.listarPorAgentes(alcance);
        List<SolicitudAlquiler> sols = solsTodas.stream()
                .filter(s -> enAlcance(alcance, idAgente(s.getAgenteResponsable())))
                .toList();
        List<Visita> vis = (alcance == null ? visitas.listarTodos() : visitas.listarPorAgentes(alcance))
                .stream().filter(v -> enAlcance(alcance, idAgente(v.getAgenteResponsable())))
                .toList();
        List<InteraccionComercial> ints = (alcance == null ? interacciones.listarTodos() : interacciones.listarPorAgentes(alcance))
                .stream().filter(i -> enAlcance(alcance, idAgente(i.getAgenteResponsable())))
                .toList();
        // El DAO trae cada contrato con su solicitud/oportunidad "shallow" (solo el id, sin
        // agenteResponsable). Se reemplazan por las instancias completas ya cargadas para poder
        // resolver el agente del contrato; de lo contrario idAgenteContrato(c) devuelve null y
        // los cierres del agente/broker se filtran a 0. (Mismo join en memoria que ContratosRest.)
        Map<Long, SolicitudAlquiler> solPorId = new HashMap<>();
        for (SolicitudAlquiler s : solsTodas) {
            if (s.getIdSolicitud() != null) solPorId.putIfAbsent(s.getIdSolicitud(), s);
        }
        Map<Long, OportunidadComercial> opPorId = new HashMap<>();
        for (OportunidadComercial o : opsTodas) {
            if (o.getIdOportunidad() != null) opPorId.putIfAbsent(o.getIdOportunidad(), o);
        }
        List<ContratoAlquiler> conts = contratos.listarTodos();
        for (ContratoAlquiler c : conts) {
            enriquecerContrato(c, solPorId, opPorId);
        }
        conts = conts.stream()
                .filter(c -> enAlcance(alcance, idAgenteContrato(c)))
                .toList();
        List<Prospeccion> pros = (alcance == null ? prospecciones.listarTodos() : prospecciones.listarPorAgentes(alcance))
                .stream()
                .filter(p -> enAlcance(alcance, idAgente(p.getAgenteResponsable())))
                .toList();

        List<Captacion> capsPeriodo = caps.stream()
                .filter(c -> enPeriodo(c.getFechaCaptacion(), inicio, hoy))
                .toList();
        List<OportunidadComercial> opsPeriodo = ops.stream()
                .filter(o -> enPeriodo(o.getFechaRegistro(), inicio, hoy))
                .toList();
        List<SolicitudAlquiler> solsPeriodo = sols.stream()
                .filter(s -> enPeriodo(s.getFechaRegistro(), inicio, hoy))
                .toList();
        List<Visita> visPeriodo = vis.stream()
                .filter(v -> enPeriodo(v.getFechaVisita(), inicio, hoy))
                .toList();
        List<InteraccionComercial> intsPeriodo = ints.stream()
                .filter(i -> enPeriodo(i.getFechaHora(), inicio, hoy))
                .toList();
        List<ContratoAlquiler> contsPeriodo = conts.stream()
                .filter(c -> enPeriodo(c.getFechaCierre(), inicio, hoy))
                .toList();
        List<Prospeccion> prosPeriodo = pros.stream()
                .filter(p -> enPeriodo(p.getFechaRegistro(), inicio, hoy))
                .toList();

        int captacionesPorRevisar = (int) caps.stream().filter(c -> c.getEstado() == EstadoCaptacion.PENDIENTE_REVISION).count();
        int captacionesObservadas = (int) caps.stream().filter(c -> c.getEstado() == EstadoCaptacion.OBSERVADA).count();
        int captacionesActivas = (int) caps.stream().filter(c -> c.getEstado() == EstadoCaptacion.ACTIVA).count();
        int solicitudesPorEvaluar = (int) sols.stream().filter(IndicadoresRest::solicitudPorRevisar).count();
        int oportunidadesActivas = (int) ops.stream()
                .filter(o -> o.getEstado() == EstadoOportunidadComercial.ABIERTA
                        || o.getEstado() == EstadoOportunidadComercial.SOLICITUD_CREADA)
                .count();
        int propiedadesEquipo = (int) caps.stream()
                .filter(c -> c.getEstado() == EstadoCaptacion.ACTIVA)
                .map(c -> c.getLocalComercial() != null ? c.getLocalComercial().getIdLocal() : null)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        int cierres = contsPeriodo.size();

        // Etapas (donut): particion EXCLUSIVA de las captaciones por su etapa mas avanzada en el
        // embudo comercial (captada -> interesados -> con solicitud -> en evaluacion -> alquilada).
        // Cada captacion cuenta UNA sola vez, de modo que las porciones suman el total y el centro
        // del donut puede mostrar el % que llego a Alquilada. Solo entran las captaciones que
        // llegaron al mercado (ACTIVA), mas las alquiladas (aunque su captacion ya este CERRADA);
        // las que siguen en aprobacion del broker (pendiente/observada/rechazada) no son embudo.
        Set<Long> capsConContrato = conts.stream()
                .map(IndicadoresRest::idCaptacionDeContrato).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> capsConSolicitud = new HashSet<>();
        Set<Long> capsEnEvaluacion = new HashSet<>();
        for (SolicitudAlquiler s : sols) {
            Long idCap = idCaptacion(s.getCaptacion());
            if (idCap == null) {
                continue;
            }
            capsConSolicitud.add(idCap);
            EstadoSolicitudAlquiler es = s.getEstado();
            if (es == EstadoSolicitudAlquiler.EN_REVISION || es == EstadoSolicitudAlquiler.OBSERVADA
                    || es == EstadoSolicitudAlquiler.APROBADA) {
                capsEnEvaluacion.add(idCap);
            }
        }
        Set<Long> capsConOportunidad = ops.stream()
                .map(o -> idCaptacion(o.getCaptacion())).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int etCaptacion = 0, etInteresados = 0, etSolicitud = 0, etEvaluacion = 0, etAlquilada = 0;
        for (Captacion c : caps) {
            Long id = c.getIdCaptacion();
            if (id == null) {
                continue;
            }
            if (capsConContrato.contains(id)) {
                etAlquilada++; // la alquilada cuenta aunque la captacion ya figure CERRADA
                continue;
            }
            if (c.getEstado() != EstadoCaptacion.ACTIVA) {
                continue; // solo las captaciones vivas en el mercado entran a las etapas intermedias
            }
            if (capsEnEvaluacion.contains(id)) {
                etEvaluacion++;
            } else if (capsConSolicitud.contains(id)) {
                etSolicitud++;
            } else if (capsConOportunidad.contains(id)) {
                etInteresados++;
            } else {
                etCaptacion++;
            }
        }
        List<Dtos.IndicadorConteo> etapas = List.of(
                new Dtos.IndicadorConteo("Captacion activa", etCaptacion),
                new Dtos.IndicadorConteo("Clientes interesados", etInteresados),
                new Dtos.IndicadorConteo("Con solicitud", etSolicitud),
                new Dtos.IndicadorConteo("En evaluacion", etEvaluacion),
                new Dtos.IndicadorConteo("Alquilada", etAlquilada));
        // "Salud de captaciones" acotada al PERIODO (captaciones creadas en la ventana): asi el
        // desglose de la dona responde al filtro. Los escalares globales (captacionesPorRevisar,
        // etc.) se mantienen para los pills accionables del menu, que no dependen del periodo.
        int saludActivas = (int) capsPeriodo.stream().filter(c -> c.getEstado() == EstadoCaptacion.ACTIVA).count();
        int saludPorRevisar = (int) capsPeriodo.stream().filter(c -> c.getEstado() == EstadoCaptacion.PENDIENTE_REVISION).count();
        int saludObservadas = (int) capsPeriodo.stream().filter(c -> c.getEstado() == EstadoCaptacion.OBSERVADA).count();
        int saludBloqueadas = (int) capsPeriodo.stream()
                .filter(c -> c.getEstado() == EstadoCaptacion.RECHAZADA
                        || c.getEstado() == EstadoCaptacion.VENCIDA
                        || c.getEstado() == EstadoCaptacion.CERRADA)
                .count();
        List<Dtos.IndicadorConteo> captacionesSalud = List.of(
                new Dtos.IndicadorConteo("Activas", saludActivas),
                new Dtos.IndicadorConteo("Por revisar", saludPorRevisar),
                new Dtos.IndicadorConteo("Observadas", saludObservadas),
                new Dtos.IndicadorConteo("Bloqueadas/cerradas", saludBloqueadas));

        // Embudo de conversion sobre las oportunidades en alcance.
        Set<Long> oportunidadesConVisita = visPeriodo.stream()
                .map(v -> idOportunidad(v.getOportunidadComercial()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int base = opsPeriodo.size();
        int conVisita = (int) opsPeriodo.stream().map(o -> o.getIdOportunidad())
                .filter(id -> id != null && oportunidadesConVisita.contains(id)).count();
        int conSolicitud = (int) opsPeriodo.stream()
                .filter(o -> o.getEstado() == EstadoOportunidadComercial.SOLICITUD_CREADA
                        || o.getEstado() == EstadoOportunidadComercial.FINALIZADA_EXITOSA)
                .count();
        int cerradasExitosas = (int) opsPeriodo.stream()
                .filter(o -> o.getEstado() == EstadoOportunidadComercial.FINALIZADA_EXITOSA)
                .count();
        List<Dtos.IndicadorEmbudo> embudo = List.of(
                new Dtos.IndicadorEmbudo("Oportunidades activas", base, 100),
                new Dtos.IndicadorEmbudo("Con visita realizada", conVisita, porcentaje(conVisita, base)),
                new Dtos.IndicadorEmbudo("Con solicitud creada", conSolicitud, porcentaje(conSolicitud, base)),
                new Dtos.IndicadorEmbudo("Cerradas exitosas", cerradasExitosas, porcentaje(cerradasExitosas, base)));

        Serie cierresSerie = cierresSerie(contsPeriodo, periodo, inicio, hoy);
        Serie captacionesSerie = captacionesSerie(capsPeriodo, periodo, inicio, hoy);
        // Conversion propia por COHORTE: de las captaciones nacidas en el periodo, cuantas ya
        // cerraron (tienen contrato). La linea del ComboChart y la cifra agregada se calculan
        // sobre la MISMA cohorte (captaciones del periodo agrupadas por fecha_captacion), por lo
        // que nunca superan 100% -- corrige el antiguo cierres/captaciones que mezclaba cohortes.
        List<Captacion> capsPeriodoCerradas = capsPeriodo.stream()
                .filter(c -> c.getIdCaptacion() != null && capsConContrato.contains(c.getIdCaptacion()))
                .toList();
        Serie cierresCohorteSerie = captacionesSerie(capsPeriodoCerradas, periodo, inicio, hoy);
        List<Integer> conversionSerie = conversionSerie(cierresCohorteSerie.valores(), captacionesSerie.valores());
        int cierresCohorte = capsPeriodoCerradas.size();
        int conversionPropia = porcentaje(cierresCohorte, capsPeriodo.size());

        List<Dtos.IndicadorDesempeno> desempeno = esAdmin
                ? desempenoPorBroker(capsPeriodo, contsPeriodo)
                : desempenoPorAgente(alcance, capsPeriodo, contsPeriodo);

        int agentesActivos = alcance != null ? alcance.size() : agentes.listarTodos().size();
        // El admin es un Broker con esAdministrador=true; no se cuenta como broker productor.
        int brokersActivos = esAdmin
                ? (int) brokers.listarTodos().stream().filter(b -> !b.isEsAdministrador()).count()
                : (esBroker ? 1 : 0);

        String ambito = esAdmin ? "Reportes globales" : esBroker ? "Reportes de equipo" : "Mi actividad";

        Dtos.IndicadorOperativo operativo = operativo(prosPeriodo.isEmpty() ? pros : prosPeriodo, vis, sols);

        return new Dtos.IndicadoresResponse(
                ambito,
                captacionesPorRevisar,
                solicitudesPorEvaluar,
                capsPeriodo.size(),
                captacionesActivas,
                captacionesPorRevisar,
                captacionesObservadas,
                oportunidadesActivas,
                intsPeriodo.size(),
                visPeriodo.size(),
                cierres,
                cierresCohorte,
                conversionPropia,
                agentesActivos,
                brokersActivos,
                propiedadesEquipo,
                cierresSerie.etiquetas(),
                cierresSerie.valores(),
                conversionSerie,
                captacionesSerie.valores(),
                etapas,
                captacionesSalud,
                embudo,
                desempeno,
                operativo);
    }

    // RF-017: reporte de avance comercial por propiedad. Por cada captacion ACTIVA en alcance,
    // resume las oportunidades generadas y su avance en el embudo (visitas, solicitudes, cierres
    // por tipo), interesados, interacciones, tasas de conversion y motivo principal de no
    // continuidad. Es una lectura ACUMULADA del estado actual de cada propiedad (no por periodo).
    @GET
    @Path("avance")
    public Dtos.AvanceComercialResponse avance() {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        boolean esAdmin = usuario.tieneRol("ADMIN");
        boolean esBroker = usuario.tieneRol("BROKER");
        Set<Long> alcance = agentesEnAlcance(usuario);

        List<Captacion> caps = (alcance == null ? captaciones.listarTodos() : captaciones.listarPorAgentes(alcance))
                .stream()
                .filter(c -> enAlcance(alcance, idAgente(c.getAgenteResponsable())))
                .filter(c -> c.getEstado() == EstadoCaptacion.ACTIVA)
                .toList();
        List<OportunidadComercial> ops = (alcance == null ? oportunidades.listarTodos() : oportunidades.listarPorAgentes(alcance))
                .stream().filter(o -> enAlcance(alcance, idAgente(o.getAgenteResponsable()))).toList();
        List<SolicitudAlquiler> sols = (alcance == null ? solicitudes.listarTodos() : solicitudes.listarPorAgentes(alcance))
                .stream().filter(s -> enAlcance(alcance, idAgente(s.getAgenteResponsable()))).toList();
        List<Visita> vis = (alcance == null ? visitas.listarTodos() : visitas.listarPorAgentes(alcance))
                .stream().filter(v -> enAlcance(alcance, idAgente(v.getAgenteResponsable()))).toList();
        List<InteraccionComercial> ints = (alcance == null ? interacciones.listarTodos() : interacciones.listarPorAgentes(alcance))
                .stream().filter(i -> enAlcance(alcance, idAgente(i.getAgenteResponsable()))).toList();
        List<MotivoNoContinuidad> motivos = motivosNoContinuidad.listarTodos();

        List<Dtos.AvancePropiedad> detalle = new ArrayList<>();
        Set<Long> interesadosGlobal = new HashSet<>();
        int totalOpsGlobal = 0;

        for (Captacion c : caps) {
            Long idCap = c.getIdCaptacion();
            if (idCap == null) {
                continue;
            }
            List<OportunidadComercial> opsCap = ops.stream()
                    .filter(o -> idCap.equals(idCaptacion(o.getCaptacion()))).toList();
            Set<Long> idsOpsCap = opsCap.stream()
                    .map(IndicadoresRest::idOportunidad).filter(Objects::nonNull).collect(Collectors.toSet());

            int abiertas = (int) opsCap.stream().filter(o -> o.getEstado() == EstadoOportunidadComercial.ABIERTA).count();
            int cerradasExitosas = (int) opsCap.stream().filter(o -> o.getEstado() == EstadoOportunidadComercial.FINALIZADA_EXITOSA).count();
            int cerradasNoFav = (int) opsCap.stream().filter(o -> o.getEstado() == EstadoOportunidadComercial.FINALIZADA_NO_FAVORABLE).count();
            int cerradasNoCont = (int) opsCap.stream().filter(o -> o.getEstado() == EstadoOportunidadComercial.NO_CONTINUA).count();

            List<Visita> visCap = vis.stream()
                    .filter(v -> { Long io = idOportunidad(v.getOportunidadComercial()); return io != null && idsOpsCap.contains(io); })
                    .toList();
            int visitasProgramadas = (int) visCap.stream()
                    .filter(v -> v.getEstado() == EstadoVisita.PROGRAMADA || v.getEstado() == EstadoVisita.REPROGRAMADA).count();
            int visitasConcretadas = (int) visCap.stream().filter(v -> v.getEstado() == EstadoVisita.REALIZADA).count();
            Set<Long> opsConVisita = visCap.stream()
                    .map(v -> idOportunidad(v.getOportunidadComercial())).filter(Objects::nonNull).collect(Collectors.toSet());
            int oportunidadesConVisita = (int) idsOpsCap.stream().filter(opsConVisita::contains).count();

            List<SolicitudAlquiler> solsCap = sols.stream()
                    .filter(s -> idCap.equals(idCaptacion(s.getCaptacion()))
                            || (s.getOportunidadComercial() != null && idsOpsCap.contains(idOportunidad(s.getOportunidadComercial()))))
                    .toList();
            int solicitudesRecibidas = solsCap.size();
            Set<Long> opsConSolicitud = solsCap.stream()
                    .map(s -> idOportunidad(s.getOportunidadComercial())).filter(Objects::nonNull).collect(Collectors.toSet());
            int oportunidadesConSolicitud = (int) idsOpsCap.stream().filter(opsConSolicitud::contains).count();
            // Respaldo por estado (SOLICITUD_CREADA/FINALIZADA_EXITOSA) si la solicitud no quedo enlazada.
            int conSolicitudPorEstado = (int) opsCap.stream()
                    .filter(o -> o.getEstado() == EstadoOportunidadComercial.SOLICITUD_CREADA
                            || o.getEstado() == EstadoOportunidadComercial.FINALIZADA_EXITOSA).count();
            oportunidadesConSolicitud = Math.max(oportunidadesConSolicitud, conSolicitudPorEstado);

            Set<Long> interesados = new HashSet<>();
            for (OportunidadComercial o : opsCap) {
                if (o.getClienteInteresado() != null && o.getClienteInteresado().getIdCliente() != null) {
                    interesados.add(o.getClienteInteresado().getIdCliente());
                }
            }
            for (SolicitudAlquiler s : solsCap) {
                if (s.getClienteInteresado() != null && s.getClienteInteresado().getIdCliente() != null) {
                    interesados.add(s.getClienteInteresado().getIdCliente());
                }
            }
            interesadosGlobal.addAll(interesados);

            int interaccionesCap = (int) ints.stream().filter(i ->
                    idCap.equals(idCaptacion(i.getCaptacion()))
                            || (i.getOportunidadComercial() != null && idsOpsCap.contains(idOportunidad(i.getOportunidadComercial()))))
                    .count();

            int totalOps = opsCap.size();
            totalOpsGlobal += totalOps;
            int tasaOportVisita = porcentaje(oportunidadesConVisita, totalOps);
            int tasaOportSolicitud = porcentaje(oportunidadesConSolicitud, totalOps);
            String motivo = motivoPrincipal(motivos, idsOpsCap);

            var local = c.getLocalComercial();
            detalle.add(new Dtos.AvancePropiedad(
                    idCap,
                    textoSeguro(c.getCodigoCaptacion()),
                    local != null ? textoSeguro(local.getDireccion()) : "",
                    local != null ? textoSeguro(local.getDistrito()) : "",
                    c.getEstado() != null ? c.getEstado().getDescripcion() : "",
                    totalOps, abiertas, oportunidadesConVisita, oportunidadesConSolicitud,
                    cerradasExitosas, cerradasNoFav, cerradasNoCont,
                    interesados.size(), interaccionesCap, visitasProgramadas, visitasConcretadas,
                    solicitudesRecibidas, tasaOportVisita, tasaOportSolicitud, motivo));
        }

        detalle.sort(Comparator.comparingInt(Dtos.AvancePropiedad::oportunidadesAbiertas).reversed()
                .thenComparing(Comparator.comparingInt(Dtos.AvancePropiedad::interacciones).reversed()));

        int aAbiertas = detalle.stream().mapToInt(Dtos.AvancePropiedad::oportunidadesAbiertas).sum();
        int aConVisita = detalle.stream().mapToInt(Dtos.AvancePropiedad::oportunidadesConVisita).sum();
        int aConSolicitud = detalle.stream().mapToInt(Dtos.AvancePropiedad::oportunidadesConSolicitud).sum();
        int aExitosas = detalle.stream().mapToInt(Dtos.AvancePropiedad::cerradasExitosas).sum();
        int aNoFav = detalle.stream().mapToInt(Dtos.AvancePropiedad::cerradasNoFavorables).sum();
        int aNoCont = detalle.stream().mapToInt(Dtos.AvancePropiedad::cerradasNoContinuidad).sum();
        int aInteracciones = detalle.stream().mapToInt(Dtos.AvancePropiedad::interacciones).sum();
        int aVisProg = detalle.stream().mapToInt(Dtos.AvancePropiedad::visitasProgramadas).sum();
        int aVisConc = detalle.stream().mapToInt(Dtos.AvancePropiedad::visitasConcretadas).sum();
        int aSolicitudes = detalle.stream().mapToInt(Dtos.AvancePropiedad::solicitudesRecibidas).sum();
        int tasaVisitaAgg = porcentaje(aConVisita, totalOpsGlobal);
        int tasaSolicitudAgg = porcentaje(aConSolicitud, totalOpsGlobal);

        String ambito = esAdmin ? "Avance comercial global" : esBroker ? "Avance comercial del equipo" : "Mi avance comercial";
        return new Dtos.AvanceComercialResponse(
                ambito, detalle.size(), totalOpsGlobal, aAbiertas, aConVisita, aConSolicitud,
                aExitosas, aNoFav, aNoCont, interesadosGlobal.size(), aInteracciones,
                aVisProg, aVisConc, aSolicitudes, tasaVisitaAgg, tasaSolicitudAgg, detalle);
    }

    // Razon mas frecuente de no continuidad entre las oportunidades dadas; "" si no hay ninguna.
    private static String motivoPrincipal(List<MotivoNoContinuidad> motivos, Set<Long> idsOportunidad) {
        if (idsOportunidad.isEmpty()) {
            return "";
        }
        Map<String, Integer> conteo = new HashMap<>();
        for (MotivoNoContinuidad m : motivos) {
            Long idOp = m.getOportunidadComercial() != null ? idOportunidad(m.getOportunidadComercial()) : null;
            if (idOp == null || !idsOportunidad.contains(idOp) || m.getRazonPrincipal() == null) {
                continue;
            }
            conteo.merge(m.getRazonPrincipal().getDescripcion(), 1, Integer::sum);
        }
        return conteo.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private static String textoSeguro(String texto) {
        return texto == null ? "" : texto;
    }

    private Serie cierresSerie(
            List<ContratoAlquiler> conts,
            Periodo periodo,
            LocalDate inicio,
            LocalDate hoy) {
        List<String> etiquetas = new ArrayList<>();
        List<Integer> valores = new ArrayList<>();
        if (periodo.dias() <= 31) {
            LocalDate cursor = inicio;
            while (!cursor.isAfter(hoy)) {
                LocalDate dia = cursor;
                etiquetas.add(String.format("%02d/%02d", dia.getDayOfMonth(), dia.getMonthValue()));
                valores.add((int) conts.stream()
                        .map(ContratoAlquiler::getFechaCierre)
                        .filter(dia::equals)
                        .count());
                cursor = cursor.plusDays(1);
            }
            return new Serie(etiquetas, valores);
        }

        YearMonth cursor = YearMonth.from(inicio);
        YearMonth fin = YearMonth.from(hoy);
        while (!cursor.isAfter(fin)) {
            YearMonth ym = cursor;
            etiquetas.add(MESES[ym.getMonthValue() - 1] + " " + String.valueOf(ym.getYear()).substring(2));
            valores.add((int) conts.stream().filter(c -> {
                LocalDate f = c.getFechaCierre();
                return f != null && f.getYear() == ym.getYear() && f.getMonthValue() == ym.getMonthValue();
            }).count());
            cursor = cursor.plusMonths(1);
        }
        return new Serie(etiquetas, valores);
    }

    private Serie captacionesSerie(
            List<Captacion> caps,
            Periodo periodo,
            LocalDate inicio,
            LocalDate hoy) {
        List<String> etiquetas = new ArrayList<>();
        List<Integer> valores = new ArrayList<>();
        if (periodo.dias() <= 31) {
            LocalDate cursor = inicio;
            while (!cursor.isAfter(hoy)) {
                LocalDate dia = cursor;
                etiquetas.add(String.format("%02d/%02d", dia.getDayOfMonth(), dia.getMonthValue()));
                valores.add((int) caps.stream()
                        .map(Captacion::getFechaCaptacion)
                        .filter(dia::equals)
                        .count());
                cursor = cursor.plusDays(1);
            }
            return new Serie(etiquetas, valores);
        }

        YearMonth cursor = YearMonth.from(inicio);
        YearMonth fin = YearMonth.from(hoy);
        while (!cursor.isAfter(fin)) {
            YearMonth ym = cursor;
            etiquetas.add(MESES[ym.getMonthValue() - 1] + " " + String.valueOf(ym.getYear()).substring(2));
            valores.add((int) caps.stream().filter(c -> {
                LocalDate f = c.getFechaCaptacion();
                return f != null && f.getYear() == ym.getYear() && f.getMonthValue() == ym.getMonthValue();
            }).count());
            cursor = cursor.plusMonths(1);
        }
        return new Serie(etiquetas, valores);
    }

    private static List<Integer> conversionSerie(List<Integer> cierres, List<Integer> captaciones) {
        int n = Math.min(cierres.size(), captaciones.size());
        List<Integer> valores = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            valores.add(porcentaje(cierres.get(i), captaciones.get(i)));
        }
        return valores;
    }

    // Indicadores operativos del seguimiento (Etapa 9): recontactos, atraso de seguimiento,
    // visitas pendientes, solicitudes sin cierre y conversion prospeccion -> captacion.
    // "Recontacto vencido" usa el MISMO umbral que la bandeja de tareas (>= DIAS_RECONTACTO dias
    // de atraso): asi el numero de "Disciplina comercial" es coherente con los items que la
    // bandeja realmente genera, en vez de contar como vencido el mismo dia del recontacto.
    private static final int DIAS_RECONTACTO = 7;

    private Dtos.IndicadorOperativo operativo(List<Prospeccion> pros, List<Visita> vis, List<SolicitudAlquiler> sols) {
        LocalDate hoy = LocalDate.now();
        LocalDate limiteVencido = hoy.minusDays(DIAS_RECONTACTO);
        int recontactosVencidos = 0;
        int recontactosAlDia = 0;
        long sumaAtraso = 0;
        for (Prospeccion p : pros) {
            if (p.getEstado() == EstadoProspeccion.CAPTADO || p.getEstado() == EstadoProspeccion.DESCARTADO) {
                continue; // solo prospecciones en seguimiento activo
            }
            LocalDate fr = p.getFechaRecontacto();
            if (fr == null) {
                continue;
            }
            // Vencido = atrasado >= DIAS_RECONTACTO (igual que la bandeja). Los recontactos recien
            // cumplidos (entre hoy-7 y hoy) aun no escalan, cuentan como al dia.
            if (fr.isAfter(limiteVencido)) {
                recontactosAlDia++;
            } else {
                recontactosVencidos++;
                sumaAtraso += ChronoUnit.DAYS.between(fr, hoy);
            }
        }
        int diasPromedio = recontactosVencidos > 0 ? (int) Math.round((double) sumaAtraso / recontactosVencidos) : 0;
        int visitasPendientes = (int) vis.stream()
                .filter(v -> v.getEstado() == EstadoVisita.PROGRAMADA || v.getEstado() == EstadoVisita.REPROGRAMADA)
                .count();
        int solicitudesSinCierre = (int) sols.stream()
                .filter(s -> s.getEstado() == EstadoSolicitudAlquiler.APROBADA)
                .count();
        int captadas = (int) pros.stream().filter(p -> p.getEstado() == EstadoProspeccion.CAPTADO).count();
        int conversion = porcentaje(captadas, pros.size());
        return new Dtos.IndicadorOperativo(recontactosVencidos, recontactosAlDia, diasPromedio,
                visitasPendientes, solicitudesSinCierre, conversion);
    }

    // Desempeno por broker: para cada broker, captaciones y cierres de su equipo de agentes.
    private List<Dtos.IndicadorDesempeno> desempenoPorBroker(List<Captacion> caps, List<ContratoAlquiler> conts) {
        List<Dtos.IndicadorDesempeno> filas = new ArrayList<>();
        for (Broker broker : brokers.listarTodos()) {
            // El admin tambien es un Broker (esAdministrador=true), pero es un supervisor global,
            // no un broker productor: no debe aparecer comparandose a si mismo en "Carga del equipo".
            if (broker.getIdBroker() == null || broker.isEsAdministrador()) {
                continue;
            }
            Set<Long> equipo = agentes.listarPorBroker(broker.getIdBroker()).stream()
                    .map(AgenteInmobiliario::getIdAgente).filter(Objects::nonNull).collect(Collectors.toSet());
            if (equipo.isEmpty()) {
                continue;
            }
            int nCaps = (int) caps.stream().filter(c -> equipo.contains(idAgente(c.getAgenteResponsable()))).count();
            int nCierres = (int) conts.stream().filter(c -> equipo.contains(idAgenteContrato(c))).count();
            if (nCaps == 0 && nCierres == 0) {
                continue;
            }
            filas.add(new Dtos.IndicadorDesempeno(nombre(broker.getPersona()), nCaps, nCierres, porcentaje(nCierres, nCaps)));
        }
        filas.sort(Comparator.comparingInt(Dtos.IndicadorDesempeno::cierres).reversed());
        return filas.stream().limit(8).toList();
    }

    // Desempeno por agente (broker: su equipo; agente: el mismo).
    private List<Dtos.IndicadorDesempeno> desempenoPorAgente(Set<Long> alcance, List<Captacion> caps, List<ContratoAlquiler> conts) {
        List<AgenteInmobiliario> fuente = agentes.listarTodos().stream()
                .filter(a -> enAlcance(alcance, a.getIdAgente()))
                .toList();
        List<Dtos.IndicadorDesempeno> filas = new ArrayList<>();
        for (AgenteInmobiliario agente : fuente) {
            Long id = agente.getIdAgente();
            if (id == null) {
                continue;
            }
            int nCaps = (int) caps.stream().filter(c -> id.equals(idAgente(c.getAgenteResponsable()))).count();
            int nCierres = (int) conts.stream().filter(c -> id.equals(idAgenteContrato(c))).count();
            if (nCaps == 0 && nCierres == 0) {
                continue;
            }
            filas.add(new Dtos.IndicadorDesempeno(nombre(agente.getPersona()), nCaps, nCierres, porcentaje(nCierres, nCaps)));
        }
        filas.sort(Comparator.comparingInt(Dtos.IndicadorDesempeno::cierres).reversed());
        return filas.stream().limit(8).toList();
    }

    private Set<Long> agentesEnAlcance(UsuarioAutenticado usuario) {
        if (usuario.tieneRol("ADMIN")) {
            return null;
        }
        if (usuario.tieneRol("AGENTE")) {
            return Set.of(usuario.idDominio());
        }
        // BROKER: los agentes que supervisa.
        return agentes.listarPorBroker(usuario.idDominio()).stream()
                .map(AgenteInmobiliario::getIdAgente).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static boolean enAlcance(Set<Long> alcance, Long idAgente) {
        return alcance == null || (idAgente != null && alcance.contains(idAgente));
    }

    private static boolean enPeriodo(LocalDate fecha, LocalDate inicio, LocalDate fin) {
        return fecha != null && !fecha.isBefore(inicio) && !fecha.isAfter(fin);
    }

    private static boolean enPeriodo(LocalDateTime fecha, LocalDate inicio, LocalDate fin) {
        return fecha != null && enPeriodo(fecha.toLocalDate(), inicio, fin);
    }

    private static Long idAgente(AgenteInmobiliario agente) {
        return agente != null ? agente.getIdAgente() : null;
    }

    private static Long idOportunidad(OportunidadComercial oportunidad) {
        return oportunidad != null ? oportunidad.getIdOportunidad() : null;
    }

    private static Long idCaptacion(Captacion captacion) {
        return captacion != null ? captacion.getIdCaptacion() : null;
    }

    private static boolean solicitudPorRevisar(SolicitudAlquiler solicitud) {
        if (solicitud == null) {
            return false;
        }
        EstadoSolicitudAlquiler estado = solicitud.getEstado();
        return estado == EstadoSolicitudAlquiler.EN_REVISION
                || estado == EstadoSolicitudAlquiler.OBSERVADA;
    }

    // Captacion asociada a un contrato (via su solicitud y, en su defecto, su oportunidad);
    // sirve para clasificar la captacion como "Alquilada" en el donut de etapas.
    private static Long idCaptacionDeContrato(ContratoAlquiler contrato) {
        if (contrato == null) {
            return null;
        }
        SolicitudAlquiler solicitud = contrato.getSolicitudAlquiler();
        if (solicitud != null && idCaptacion(solicitud.getCaptacion()) != null) {
            return idCaptacion(solicitud.getCaptacion());
        }
        OportunidadComercial oportunidad = contrato.getOportunidad();
        return oportunidad != null ? idCaptacion(oportunidad.getCaptacion()) : null;
    }

    // El contrato no lleva agente directo: se resuelve via la solicitud (o la oportunidad).
    private static Long idAgenteContrato(ContratoAlquiler contrato) {
        if (contrato == null) {
            return null;
        }
        SolicitudAlquiler solicitud = contrato.getSolicitudAlquiler();
        if (solicitud != null && idAgente(solicitud.getAgenteResponsable()) != null) {
            return idAgente(solicitud.getAgenteResponsable());
        }
        OportunidadComercial oportunidad = contrato.getOportunidad();
        return oportunidad != null ? idAgente(oportunidad.getAgenteResponsable()) : null;
    }

    // Sustituye la solicitud/oportunidad "shallow" del contrato (solo id, sin relaciones) por
    // las instancias completas ya cargadas, para poder leer su agente responsable y su estado.
    private static void enriquecerContrato(ContratoAlquiler contrato,
            Map<Long, SolicitudAlquiler> solPorId, Map<Long, OportunidadComercial> opPorId) {
        SolicitudAlquiler sol = contrato.getSolicitudAlquiler();
        if (sol != null && sol.getIdSolicitud() != null) {
            SolicitudAlquiler full = solPorId.get(sol.getIdSolicitud());
            if (full != null) contrato.setSolicitudAlquiler(full);
        }
        OportunidadComercial op = contrato.getOportunidad();
        if (op != null && op.getIdOportunidad() != null) {
            OportunidadComercial full = opPorId.get(op.getIdOportunidad());
            if (full != null) contrato.setOportunidad(full);
        }
    }

    private static String nombre(com.controllocal.model.persona.Persona persona) {
        return persona != null ? persona.getNombresORazonSocial() : "—";
    }

    // Todos los usos son ratios parte<=total (etapas del embudo, conversion por cohorte). El
    // clamp a 100 es una red de seguridad: ninguna tasa de conversion debe pintarse > 100%.
    private static int porcentaje(int parte, int total) {
        return total <= 0 ? 0 : Math.min(100, (int) Math.round(parte * 100.0 / total));
    }

    private record Periodo(String codigo, int dias) {
        static Periodo desde(String valor) {
            String normal = valor == null ? "" : valor.trim().toLowerCase();
            return switch (normal) {
                case "7", "7d", "semana" -> new Periodo("7d", 7);
                case "15", "15d" -> new Periodo("15d", 15);
                case "1m", "30", "30d", "mes" -> new Periodo("1m", 30);
                case "3m", "90", "90d" -> new Periodo("3m", 90);
                case "1y", "12m", "365", "365d", "ano", "anio" -> new Periodo("1y", 365);
                default -> new Periodo("6m", 180);
            };
        }
    }

    private record Serie(List<String> etiquetas, List<Integer> valores) {
    }
}
