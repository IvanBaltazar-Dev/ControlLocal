package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ComisionLiquidacion;
import com.controllocal.domain.comercial.DocumentoSolicitud;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comercial.TipoDocumentoRequerido;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DocumentoSolicitudRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PlanDeConsulta;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.Pagina;
import com.controllocal.service.SolicitudService;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AccesoSolicitud;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.CondicionesEconomicas;
import com.controllocal.service.soporte.Transiciones;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reglas y mensajes calcados de {@code SolicitudesRest} +
 * {@code SolicitudAlquilerBusinessLogicImpl} (contrato congelado F4 §2).
 *
 * <p>Dos reglas del cable NO se ven en el REST y son faciles de perder al
 * portar; van anotadas en su sitio:
 * <ol>
 *   <li>el alta exige <b>captacion ACTIVA</b> y <b>oportunidad ABIERTA</b>, y
 *       transiciona la oportunidad a {@code S} (Solicitud creada). En la v2 ese
 *       efecto pasa por {@link Transiciones}, asi que queda auditado gratis
 *       (MEJ-01) — la v1 lo hacia a mano y no dejaba rastro;</li>
 *   <li>el reenvio a evaluacion exige que el agente responsable tenga
 *       <b>broker supervisor activo</b>: sin el no habria quien evalue.</li>
 * </ol>
 *
 * <p>Alcance (§7): las solicitudes alcanzan <b>por agente</b> —el BROKER ve las
 * de su equipo—, a diferencia de contratos y oportunidades, que alcanzan por
 * captacion. No unificar.
 */
@Service
public class SolicitudServiceImpl implements SolicitudService {

    /** D-F4-4: marca de tiempo, no correlativo como PRO-####/CAP-####. */
    private static final DateTimeFormatter CODIGO = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    /**
     * Cubo de la bandeja del broker: lo que espera decision suya. NO es un
     * estado —no existe en {@code ck_solicitud_estado}—, es {@code E} + {@code
     * O}, igual que {@code GESTION} en prospecciones.
     */
    private static final String PENDIENTES = "PENDIENTES";

    /** EstadoOperativoAgente.DISPONIBLE; el default de la entidad ya es "D". */
    private static final String AGENTE_DISPONIBLE = "D";

    private final SolicitudAlquilerRepository solicitudes;
    private final OportunidadComercialRepository oportunidades;
    private final DocumentoSolicitudRepository documentos;
    private final DetalleAgenteRepository agentes;
    private final SupervisionAgenteRepository supervisiones;
    private final Alcances alcances;
    private final AccesoSolicitud acceso;
    private final Transiciones transiciones;
    private final AlertaService alertas;
    private final PlanDeConsulta plan;

    public SolicitudServiceImpl(SolicitudAlquilerRepository solicitudes,
                                OportunidadComercialRepository oportunidades,
                                DocumentoSolicitudRepository documentos,
                                DetalleAgenteRepository agentes,
                                SupervisionAgenteRepository supervisiones,
                                Alcances alcances, AccesoSolicitud acceso, Transiciones transiciones,
                                AlertaService alertas, PlanDeConsulta plan) {
        this.alertas = alertas;
        this.plan = plan;
        this.solicitudes = solicitudes;
        this.oportunidades = oportunidades;
        this.documentos = documentos;
        this.agentes = agentes;
        this.supervisiones = supervisiones;
        this.alcances = alcances;
        this.acceso = acceso;
        this.transiciones = transiciones;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaSolicitud> listar(FiltrosSolicitud f, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            // Un BROKER sin agentes supervisados obtiene lista VACIA, no 403 (§7).
            return Pagina.vacia();
        }
        int tamanoValido = tamano(f.tamano());
        String texto = vacioNull(f.query());
        if (texto != null) {
            return porTexto(alcance, f, texto, Math.max(1, f.pagina()), tamanoValido);
        }
        Page<SolicitudAlquiler> page = solicitudes.buscar(
                alcance.idOrganizacion(), alcance.global(), alcance.paramRoles(),
                f.idOportunidad(), f.idCaptacion(), f.idAgente(), estado(f.estado()),
                vacioNull(f.distrito()),
                PageRequest.of(Math.max(0, f.pagina() - 1), tamanoValido));
        return new Pagina<>(conChecklist(alcance.idOrganizacion(), page.getContent()),
                page.getTotalElements());
    }

    /**
     * Camino de BUSQUEDA POR CONJUNTO DE CANDIDATOS (§5 del contrato de
     * listados): una rama por tabla, {@code UNION} en la base, y el mismo
     * conjunto para el conteo y para la pagina. La proyeccion completa se carga
     * despues, solo para los ids ya resueltos.
     */
    private Pagina<FichaSolicitud> porTexto(Alcance alcance, FiltrosSolicitud f, String texto,
                                            int pagina, int tamano) {
        plan.forzarPersonalizado();
        String roles = alcance.paramRolesArray();
        String estado = estado(f.estado());
        String distrito = vacioNull(f.distrito());
        long total = solicitudes.contarPorTexto(alcance.idOrganizacion(), alcance.global(), roles,
                f.idOportunidad(), f.idCaptacion(), f.idAgente(), estado, distrito, texto);
        if (total == 0) {
            return new Pagina<>(List.of(), 0);
        }
        List<Long> ids = solicitudes.idsPorTexto(alcance.idOrganizacion(), alcance.global(), roles,
                f.idOportunidad(), f.idCaptacion(), f.idAgente(), estado, distrito, texto,
                tamano, (pagina - 1) * tamano);
        if (ids.isEmpty()) {
            return new Pagina<>(List.of(), total);
        }
        return new Pagina<>(
                conChecklist(alcance.idOrganizacion(),
                        solicitudes.buscarFichaPorIds(alcance.idOrganizacion(), ids)),
                total);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenSolicitudes resumen(FiltrosSolicitud f, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return new ResumenSolicitudes(0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
        }
        // Estado, distrito y agente viajan NULOS: son los filtros que este
        // resumen acota. Con texto cuenta sobre el MISMO conjunto de candidatos
        // que pagina la lista; sin texto, sobre el mismo WHERE. Nunca sobre dos
        // criterios distintos, o el KPI y la tabla dirian cosas diferentes.
        String texto = vacioNull(f.query());
        if (texto != null) {
            plan.forzarPersonalizado();
        }
        List<ConteoPorEstado> conteos = texto != null
                ? solicitudes.contarPorEstadoConTexto(alcance.idOrganizacion(), alcance.global(),
                        alcance.paramRolesArray(), f.idOportunidad(), f.idCaptacion(), null, null,
                        null, texto)
                : solicitudes.contarPorEstado(alcance.idOrganizacion(), alcance.global(),
                        alcance.paramRoles(), f.idOportunidad(), f.idCaptacion(), null, null, null);
        Map<String, Long> porEstado = conteos.stream()
                .collect(Collectors.toMap(ConteoPorEstado::getEstado, ConteoPorEstado::getTotal));
        long enRevision = porEstado.getOrDefault(SolicitudAlquiler.EN_REVISION, 0L);
        long observadas = porEstado.getOrDefault(SolicitudAlquiler.OBSERVADA, 0L);
        return new ResumenSolicitudes(
                // El total suma TODOS los cubos, no solo los siete con nombre:
                // si apareciera un estado nuevo, seguiria cuadrando con la lista.
                porEstado.values().stream().mapToLong(Long::longValue).sum(),
                porEstado.getOrDefault(SolicitudAlquiler.REGISTRADA, 0L),
                enRevision, observadas,
                porEstado.getOrDefault(SolicitudAlquiler.APROBADA, 0L),
                porEstado.getOrDefault(SolicitudAlquiler.RECHAZADA, 0L),
                porEstado.getOrDefault(SolicitudAlquiler.DESISTIDA, 0L),
                porEstado.getOrDefault(SolicitudAlquiler.CERRADA, 0L),
                // El cubo de la bandeja del broker, derivado de los dos que lo
                // componen: no se pide otra vez a la base.
                enRevision + observadas,
                solicitudes.distritosDisponibles(alcance.idOrganizacion(), alcance.global(),
                        alcance.paramRoles(), f.idOportunidad(), f.idCaptacion(), null, null, null),
                solicitudes.agentesDisponibles(alcance.idOrganizacion(), alcance.global(),
                                alcance.paramRoles(), f.idOportunidad(), f.idCaptacion(), null, null)
                        .stream()
                        .map(a -> new AgenteConSolicitudes(a.getId(), a.getNombre()))
                        .toList());
    }

    /** Contador "X/6" de toda la pagina en una sola lectura (sin N+1). */
    private List<FichaSolicitud> conChecklist(long idOrganizacion, List<SolicitudAlquiler> pagina) {
        Map<Long, Integer> entregados = entregadosPorSolicitud(idOrganizacion,
                pagina.stream().map(SolicitudAlquiler::getId).filter(Objects::nonNull).toList());
        return pagina.stream().map(s -> ficha(s, entregados)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FichaSolicitud obtener(long id, Actor actor) {
        SolicitudAlquiler solicitud = acceso.conAcceso(id, actor);
        return ficha(solicitud, entregadosPorSolicitud(actor.idOrganizacion(), List.of(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public FichaSolicitud obtenerPorCodigo(String codigo, Actor actor) {
        SolicitudAlquiler solicitud = acceso.porCodigo(codigo, actor);
        return ficha(solicitud,
                entregadosPorSolicitud(actor.idOrganizacion(), List.of(solicitud.getId())));
    }

    @Override
    @Transactional
    public FichaSolicitud registrar(DatosSolicitud datos, Actor actor) {
        if (datos == null || datos.idOportunidad() == null) {
            throw new ReglaNegocioException("Los datos de la solicitud son obligatorios.");
        }
        // Orden calcado de BusinessValidations.solicitud(): primero la FORMA del
        // dato, despues el estado del mundo. El codigo y la fecha de registro no
        // llegan a fallar nunca porque el cable los completa (codigo autogenerado,
        // fecha = hoy), igual que Dtos.SolicitudRequest.aEntidad.
        if (datos.montoPropuesto() == null || datos.montoPropuesto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReglaNegocioException("El monto propuesto debe ser mayor que cero.");
        }
        String moneda = CondicionesEconomicas.moneda(datos.moneda(), "de la renta propuesta");
        if (datos.idOportunidad() <= 0) {
            throw new ReglaNegocioException(
                    "La oportunidad comercial de la solicitud debe ser mayor que cero.");
        }
        String formaPago = formaPago(datos.formaPago());

        // El tenant SI acota (buscarFicha lleva la organizacion), pero el cable NO
        // comprueba que la oportunidad sea del agente que registra: solo lo fija
        // como responsable (§2). Se replica tal cual.
        OportunidadComercial oportunidad = oportunidades
                .buscarFicha(actor.idOrganizacion(), datos.idOportunidad())
                .orElseThrow(() -> new ReglaNegocioException(
                        "Oportunidad comercial no encontrada para solicitud."));
        Captacion captacion = oportunidad.getCaptacion();
        if (captacion == null) {
            throw new ReglaNegocioException("Captacion no encontrada para solicitud.");
        }
        if (!Captacion.ACTIVA.equals(captacion.estadoActual())) {
            throw new ReglaNegocioException("La captacion debe estar ACTIVA.");
        }
        if (!oportunidad.estaAbierta()) {
            throw new ReglaNegocioException("La oportunidad comercial debe estar ABIERTA.");
        }
        DetalleAgente agente = agentes.findById(actor.idRolOperativo())
                .orElseThrow(() -> new ReglaNegocioException("Agente no encontrado para solicitud."));
        exigirAgenteDisponible(agente);
        // "Una sola solicitud por oportunidad" NO se comprueba aqui: lo defiende
        // uq_solicitud_oportunidad, y el E2E demostro que este camino es
        // INALCANZABLE —el alta anterior dejo la oportunidad en S y la
        // precondicion "ABIERTA" corta antes—. La comprobacion que habia era
        // codigo muerto y ademas respondia 400 donde el cable responde 409
        // (decision de equipo, 2026-07-29).

        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        solicitud.setOrganizacionId(actor.idOrganizacion());
        solicitud.setCodigoSolicitud(codigo(datos.codigoSolicitud(), actor.idOrganizacion()));
        solicitud.setFechaRegistro(
                datos.fechaRegistro() != null ? datos.fechaRegistro() : LocalDate.now());
        solicitud.setMontoPropuesto(datos.montoPropuesto());
        solicitud.setMoneda(moneda);
        solicitud.setPlazoContratoMeses(datos.plazoMeses());
        solicitud.setPlazoTentativo(plazoTentativo(datos));
        solicitud.setObservaciones(datos.observaciones());
        solicitud.setFechaVigenciaOferta(datos.fechaVigenciaOferta());
        solicitud.setFechaInicioContrato(datos.fechaInicio());
        solicitud.setFormaPago(formaPago);
        solicitud.setMesesGarantia(datos.mesesGarantia());
        solicitud.setMesesAdelanto(datos.mesesAdelanto());
        solicitud.setOportunidad(oportunidad);
        solicitud.setAgente(agente);
        transiciones.iniciar(solicitud, SolicitudAlquiler.REGISTRADA);
        SolicitudAlquiler guardada = solicitudes.save(solicitud);

        // Efecto lateral del alta (§2): la oportunidad pasa a S. Por Transiciones,
        // asi que a diferencia de la v1 deja fila en historial_estado.
        transiciones.aplicar(oportunidad, oportunidad.getId(), OportunidadComercial.SOLICITUD_CREADA,
                actor, "Solicitud " + guardada.getCodigoSolicitud() + " registrada.");
        oportunidades.save(oportunidad);

        // Recien creada: aun no tiene documentos, el checklist arranca en 0/6.
        return ficha(guardada, Map.of());
    }

    @Override
    @Transactional
    public FichaSolicitud reenviarAEvaluacion(long id, Actor actor) {
        SolicitudAlquiler solicitud = acceso.conAcceso(id, actor);
        if (!solicitud.puedeEnviarseAEvaluacion()) {
            throw new ReglaNegocioException(
                    "Solo una solicitud registrada u observada puede enviarse a evaluacion.");
        }
        DetalleAgente responsable = solicitud.getAgente();
        if (responsable == null || !supervisiones.tieneSupervisorActivo(
                solicitud.getOrganizacionId(), responsable.getId())) {
            throw new ReglaNegocioException("El agente responsable no tiene broker supervisor activo.");
        }
        transiciones.aplicar(solicitud, id, SolicitudAlquiler.EN_REVISION, actor,
                "Solicitud enviada a evaluacion del broker.");
        // Aviso al broker supervisor (§4 F6, punto 5). Cuelga del AGENTE
        // responsable, no del broker: quien lo lee lo decide el tipo, y el
        // broker lo ve a traves de la supervision.
        alertas.emitir(new AlertaService.DatosAlerta(Alerta.SOLICITUD_REENVIADA, Alerta.MEDIA,
                "SOLICITUD_ALQUILER", id, responsable.getId(),
                "La solicitud " + solicitud.getCodigoSolicitud()
                        + " fue enviada a evaluacion del broker supervisor."), actor);
        return ficha(solicitudes.save(solicitud),
                entregadosPorSolicitud(actor.idOrganizacion(), List.of(id)));
    }

    // ------------------------------------------------------------------
    // Alcance por rol: solicitudes por AGENTE (§7), no por captacion. La
    // carga con alcance vive en AccesoSolicitud porque tambien entran por
    // ella los documentos y el historial de evaluaciones.
    // ------------------------------------------------------------------

    /**
     * BusinessValidations.agenteDisponible() de la v1. En Party-Role el "estado
     * administrativo ACTIVO" del agente es la VIGENCIA de su persona_rol, que es
     * donde la v2 guarda la baja del rol.
     */
    private static void exigirAgenteDisponible(DetalleAgente agente) {
        if (agente.getRol() == null || !agente.getRol().estaVigente()) {
            throw new ReglaNegocioException("El agente debe estar ACTIVO.");
        }
        if (!AGENTE_DISPONIBLE.equals(agente.getEstadoOperativo())) {
            throw new ReglaNegocioException("El agente debe estar DISPONIBLE.");
        }
    }

    // ------------------------------------------------------------------
    // Checklist "X/Y" y armado de la ficha
    // ------------------------------------------------------------------

    /**
     * Contador del checklist para TODA la pagina en una sola lectura (MEJ-05,
     * sin N+1): cuantos de los SEIS tipos requeridos tienen documento que cuenta
     * como entregado. Poder de representacion y "otro" se pueden subir pero NO
     * suman, y un documento OBSERVADO deja de contar — asi lo hace la v1.
     *
     * <p>{@code getSolicitud().getId()} lee el identificador del proxy LAZY sin
     * inicializarlo, por eso la consulta no necesita traerse la solicitud.
     */
    private Map<Long, Integer> entregadosPorSolicitud(long idOrganizacion, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<String>> tiposPorSolicitud = new HashMap<>();
        for (DocumentoSolicitud documento : documentos.porSolicitudes(idOrganizacion, ids)) {
            if (!documento.cuentaComoEntregado() || documento.getTipoDocumento() == null) {
                continue;
            }
            String codigoTipo = TipoDocumentoRequerido.codigoDe(documento.getTipoDocumento().getId());
            if (codigoTipo == null || !TipoDocumentoRequerido.REQUERIDOS.contains(codigoTipo)) {
                continue;
            }
            tiposPorSolicitud
                    .computeIfAbsent(documento.getSolicitud().getId(), clave -> new HashSet<>())
                    .add(codigoTipo);
        }
        Map<Long, Integer> conteo = new HashMap<>();
        tiposPorSolicitud.forEach((idSolicitud, tipos) -> conteo.put(idSolicitud, tipos.size()));
        return conteo;
    }

    private static FichaSolicitud ficha(SolicitudAlquiler s, Map<Long, Integer> entregados) {
        OportunidadComercial oportunidad = s.getOportunidad();
        DetalleCliente cliente = oportunidad != null ? oportunidad.getCliente() : null;
        Captacion captacion = oportunidad != null ? oportunidad.getCaptacion() : null;
        Propiedad propiedad = captacion != null ? captacion.getPropiedad() : null;
        DetalleAgente agente = s.getAgente();
        return new FichaSolicitud(
                s.getId(), s.getCodigoSolicitud(), s.getFechaRegistro(),
                s.getMontoPropuesto(), s.getMoneda(), s.getPlazoTentativo(), s.getObservaciones(),
                s.estadoActual(), Fechas.local(s.getFechaActualizacionEstado()),
                s.getFechaVigenciaOferta(),
                oportunidad != null ? oportunidad.getId() : null,
                oportunidad != null ? oportunidad.getCodigoOportunidad() : null,
                cliente != null ? cliente.getId() : null,
                nombre(cliente != null ? cliente.getRol() : null),
                captacion != null ? captacion.getId() : null,
                captacion != null ? captacion.getCodigoCaptacion() : null,
                propiedad != null ? propiedad.getDireccion() : null,
                propiedad != null ? propiedad.getDistrito() : null,
                agente != null ? agente.getId() : null,
                nombre(agente != null ? agente.getRol() : null),
                s.getPlazoContratoMeses(), s.getFechaInicioContrato(), s.getFormaPago(),
                s.getMesesGarantia(), s.getMesesAdelanto(),
                s.getId() != null ? entregados.getOrDefault(s.getId(), 0) : 0,
                TipoDocumentoRequerido.REQUERIDOS.size());
    }

    private static String nombre(PersonaRol rol) {
        return rol == null || rol.getPersona() == null ? null : rol.getPersona().getNombresORazonSocial();
    }

    // ------------------------------------------------------------------
    // Derivaciones del cable
    // ------------------------------------------------------------------

    /**
     * SOL-yyMMddHHmmss cuando el cliente no manda codigo (D-F4-4). El unico es
     * POR ORGANIZACION (uq_solicitud_codigo), asi que un codigo propuesto se
     * comprueba dentro del tenant.
     *
     * <p>Responde <b>409</b>, igual que el cable v1 —alli lo produce la
     * violacion del UNIQUE, que el mapper traduce—. Adelantarse a la BD solo
     * sirve para nombrar el codigo en conflicto en vez del generico "un dato
     * unico esta duplicado"; el indice sigue siendo el guardian real y es quien
     * cubre la carrera entre dos altas simultaneas.
     */
    private String codigo(String codigoSolicitado, long idOrganizacion) {
        if (codigoSolicitado == null || codigoSolicitado.isBlank()) {
            return "SOL-" + LocalDateTime.now().format(CODIGO);
        }
        String propuesto = codigoSolicitado.trim();
        if (solicitudes.existeCodigo(idOrganizacion, propuesto)) {
            throw new ConflictoException("Ya existe una solicitud con el codigo " + propuesto + ".");
        }
        return propuesto;
    }

    /**
     * El cable DERIVA el texto del plazo: si vienen meses, "N meses" gana sobre
     * el plazoTentativo que envie el cliente (Dtos.SolicitudRequest.aEntidad).
     */
    private static String plazoTentativo(DatosSolicitud datos) {
        return datos.plazoMeses() != null && datos.plazoMeses() > 0
                ? datos.plazoMeses() + " meses"
                : datos.plazoTentativo();
    }

    /** FormaPago viaja con el NOMBRE del enum, no con codigo de una letra (§1). */
    private static String formaPago(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String nombre = valor.trim().toUpperCase(Locale.ROOT);
        if (!ComisionLiquidacion.FORMAS_PAGO.contains(nombre)) {
            throw new ReglaNegocioException("Valor invalido para forma de pago: " + valor);
        }
        return nombre;
    }

    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }

    /**
     * Normaliza {@code PENDIENTES} a mayusculas y deja el resto tal cual: el
     * cubo se compara por nombre y los estados son codigos de una letra. Mismo
     * criterio que {@code GESTION} en prospecciones.
     */
    private static String estado(String valor) {
        String limpio = vacioNull(valor);
        return limpio != null && PENDIENTES.equalsIgnoreCase(limpio.trim())
                ? PENDIENTES
                : limpio;
    }

    private static String vacioNull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
