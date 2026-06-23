package com.controllocal.rest;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.controllocal.bl.AgenteBusinessLogic;
import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.ContratoAlquilerBusinessLogic;
import com.controllocal.bl.InteraccionComercialBusinessLogic;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.bl.VisitaBusinessLogic;
import com.controllocal.bl.impl.AgenteBusinessLogicImpl;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.ContratoAlquilerBusinessLogicImpl;
import com.controllocal.bl.impl.InteraccionComercialBusinessLogicImpl;
import com.controllocal.bl.impl.OportunidadComercialBusinessLogicImpl;
import com.controllocal.bl.impl.SolicitudAlquilerBusinessLogicImpl;
import com.controllocal.bl.impl.VisitaBusinessLogicImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.comercial.enums.EstadoCaptacion;
import com.controllocal.model.comercial.enums.EstadoOportunidadComercial;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
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
    private final InteraccionComercialBusinessLogic interacciones = new InteraccionComercialBusinessLogicImpl();
    private final AgenteBusinessLogic agentes = new AgenteBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    @Path("resumen")
    public Dtos.IndicadoresResponse resumen() {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        boolean esAdmin = usuario.tieneRol("ADMIN");
        boolean esBroker = usuario.tieneRol("BROKER");
        // null = sin filtro (admin ve todo); en caso contrario, ids de agentes en alcance.
        Set<Long> alcance = agentesEnAlcance(usuario);

        List<Captacion> caps = captaciones.listarTodos().stream()
                .filter(c -> enAlcance(alcance, idAgente(c.getAgenteResponsable())))
                .toList();
        List<OportunidadComercial> ops = oportunidades.listarTodos().stream()
                .filter(o -> enAlcance(alcance, idAgente(o.getAgenteResponsable())))
                .toList();
        List<SolicitudAlquiler> sols = solicitudes.listarTodos().stream()
                .filter(s -> enAlcance(alcance, idAgente(s.getAgenteResponsable())))
                .toList();
        List<Visita> vis = visitas.listarTodos().stream()
                .filter(v -> enAlcance(alcance, idAgente(v.getAgenteResponsable())))
                .toList();
        List<InteraccionComercial> ints = interacciones.listarTodos().stream()
                .filter(i -> enAlcance(alcance, idAgente(i.getAgenteResponsable())))
                .toList();
        List<ContratoAlquiler> conts = contratos.listarTodos().stream()
                .filter(c -> enAlcance(alcance, idAgenteContrato(c)))
                .toList();

        int captacionesPorRevisar = (int) caps.stream().filter(c -> c.getEstado() == EstadoCaptacion.PENDIENTE_REVISION).count();
        int captacionesObservadas = (int) caps.stream().filter(c -> c.getEstado() == EstadoCaptacion.OBSERVADA).count();
        int captacionesActivas = (int) caps.stream().filter(c -> c.getEstado() == EstadoCaptacion.ACTIVA).count();
        int solicitudesPorEvaluar = (int) sols.stream().filter(s -> s.getEstado() == EstadoSolicitudAlquiler.EN_REVISION).count();
        int oportunidadesActivas = (int) ops.stream()
                .filter(o -> o.getEstado() == EstadoOportunidadComercial.ABIERTA
                        || o.getEstado() == EstadoOportunidadComercial.SOLICITUD_CREADA)
                .count();
        int cierres = conts.size();

        // Etapas (donut): captaciones por estado.
        List<Dtos.IndicadorConteo> etapas = List.of(
                new Dtos.IndicadorConteo("Activa", captacionesActivas),
                new Dtos.IndicadorConteo("Pendiente de revision", captacionesPorRevisar),
                new Dtos.IndicadorConteo("Observada", captacionesObservadas),
                new Dtos.IndicadorConteo("Rechazada", (int) caps.stream().filter(c -> c.getEstado() == EstadoCaptacion.RECHAZADA).count()),
                new Dtos.IndicadorConteo("Cerrada", (int) caps.stream().filter(c -> c.getEstado() == EstadoCaptacion.CERRADA).count()));

        // Embudo de conversion sobre las oportunidades en alcance.
        Set<Long> oportunidadesConVisita = vis.stream()
                .map(v -> idOportunidad(v.getOportunidadComercial()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int base = ops.size();
        int conVisita = (int) ops.stream().map(o -> o.getIdOportunidad())
                .filter(id -> id != null && oportunidadesConVisita.contains(id)).count();
        int conSolicitud = (int) ops.stream()
                .filter(o -> o.getEstado() == EstadoOportunidadComercial.SOLICITUD_CREADA
                        || o.getEstado() == EstadoOportunidadComercial.FINALIZADA_EXITOSA)
                .count();
        int cerradasExitosas = (int) ops.stream()
                .filter(o -> o.getEstado() == EstadoOportunidadComercial.FINALIZADA_EXITOSA)
                .count();
        List<Dtos.IndicadorEmbudo> embudo = List.of(
                new Dtos.IndicadorEmbudo("Oportunidades activas", base, 100),
                new Dtos.IndicadorEmbudo("Con visita realizada", conVisita, porcentaje(conVisita, base)),
                new Dtos.IndicadorEmbudo("Con solicitud creada", conSolicitud, porcentaje(conSolicitud, base)),
                new Dtos.IndicadorEmbudo("Cerradas exitosas", cerradasExitosas, porcentaje(cerradasExitosas, base)));

        // Cierres por mes (ultimos 6 meses) a partir de la fecha de cierre del contrato.
        List<String> mesesEtiquetas = new ArrayList<>();
        List<Integer> cierresPorMes = new ArrayList<>();
        YearMonth actual = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = actual.minusMonths(i);
            mesesEtiquetas.add(MESES[ym.getMonthValue() - 1]);
            int n = (int) conts.stream().filter(c -> {
                LocalDate f = c.getFechaCierre();
                return f != null && f.getYear() == ym.getYear() && f.getMonthValue() == ym.getMonthValue();
            }).count();
            cierresPorMes.add(n);
        }

        List<Dtos.IndicadorDesempeno> desempeno = esAdmin
                ? desempenoPorBroker(caps, conts)
                : desempenoPorAgente(alcance, caps, conts);

        int agentesActivos = alcance != null ? alcance.size() : agentes.listarTodos().size();
        int brokersActivos = esAdmin ? brokers.listarTodos().size() : (esBroker ? 1 : 0);

        String ambito = esAdmin ? "Reportes globales" : esBroker ? "Reportes de equipo" : "Mi actividad";

        return new Dtos.IndicadoresResponse(
                ambito,
                captacionesPorRevisar,
                solicitudesPorEvaluar,
                caps.size(),
                captacionesActivas,
                captacionesPorRevisar,
                captacionesObservadas,
                oportunidadesActivas,
                ints.size(),
                vis.size(),
                cierres,
                agentesActivos,
                brokersActivos,
                mesesEtiquetas,
                cierresPorMes,
                etapas,
                embudo,
                desempeno);
    }

    // Desempeno por broker: para cada broker, captaciones y cierres de su equipo de agentes.
    private List<Dtos.IndicadorDesempeno> desempenoPorBroker(List<Captacion> caps, List<ContratoAlquiler> conts) {
        List<Dtos.IndicadorDesempeno> filas = new ArrayList<>();
        for (Broker broker : brokers.listarTodos()) {
            if (broker.getIdBroker() == null) {
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

    private static Long idAgente(AgenteInmobiliario agente) {
        return agente != null ? agente.getIdAgente() : null;
    }

    private static Long idOportunidad(OportunidadComercial oportunidad) {
        return oportunidad != null ? oportunidad.getIdOportunidad() : null;
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

    private static String nombre(com.controllocal.model.persona.Persona persona) {
        return persona != null ? persona.getNombresORazonSocial() : "—";
    }

    private static int porcentaje(int parte, int total) {
        return total <= 0 ? 0 : (int) Math.round(parte * 100.0 / total);
    }
}
