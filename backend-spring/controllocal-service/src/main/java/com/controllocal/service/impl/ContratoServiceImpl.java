package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ComisionLiquidacion;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.RevisionDisponibilidad;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.domain.comun.EstadosDominio.EstadoContrato;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.SupervisionAgente;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.PublicacionRepository;
import com.controllocal.persistence.repositorio.RevisionDisponibilidadRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.ComisionService;
import com.controllocal.service.TareaService;
import com.controllocal.service.ComisionService.FichaComision;
import com.controllocal.service.ContratoService;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.CondicionesEconomicas;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.soporte.Transiciones;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reglas y mensajes calcados de {@code ContratosRest} +
 * {@code ContratoAlquilerBusinessLogicImpl} (contrato congelado F4 §5 y §6).
 *
 * <p>{@link #registrar} es la operacion mas pesada del sistema: una sola
 * transaccion que toca <b>siete</b> entidades y cierra el ciclo comercial.
 * Las CUATRO que cambian de estado —oportunidad, solicitud, captacion y
 * propiedad— pasan por {@link Transiciones}, asi que el cierre deja cuatro
 * filas en {@code historial_estado} con actor y motivo. La v1 movia esos
 * estados a mano y no auditaba nada (MEJ-01).
 *
 * <p><b>El alcance del BROKER aqui es por CAPTACION</b> (las captaciones de
 * su equipo), no por el agente de la solicitud como en
 * {@code SolicitudServiceImpl}. Son dos reglas distintas del cable y no se
 * unifican; el parametro {@code porAgente} del repositorio distingue las
 * ramas.
 *
 * <p>Los dos gates de comision son del BROKER supervisor, <b>sin ADMIN</b>
 * (el admin solo lee): el rol lo exige el controlador, el alcance se
 * comprueba aqui.
 */
@Service
public class ContratoServiceImpl implements ContratoService {

    /** Estados de la oportunidad que todavia admiten un contrato (§6). */
    private static final Set<String> OPORTUNIDAD_ABIERTA_AL_CONTRATO = Set.of(
            OportunidadComercial.ABIERTA, OportunidadComercial.SOLICITUD_CREADA);

    private final ContratoAlquilerRepository contratos;
    private final SolicitudAlquilerRepository solicitudes;
    private final OportunidadComercialRepository oportunidades;
    private final CaptacionRepository captaciones;
    private final PropiedadRepository propiedades;
    private final PrecioPropiedadRepository precios;
    private final PublicacionRepository publicaciones;
    private final ComisionService comisiones;
    private final Alcances alcances;
    private final Transiciones transiciones;
    private final TareaService tareas;
    private final AlertaService alertas;
    private final SupervisionAgenteRepository supervisiones;
    private final RevisionDisponibilidadRepository revisiones;
    /** Proxy de si mismo: la relectura tras la carrera necesita transaccion NUEVA. */
    private final ContratoService autoinvocado;

    public ContratoServiceImpl(ContratoAlquilerRepository contratos,
                               SolicitudAlquilerRepository solicitudes,
                               OportunidadComercialRepository oportunidades,
                               CaptacionRepository captaciones, PropiedadRepository propiedades,
                               PrecioPropiedadRepository precios, PublicacionRepository publicaciones,
                               ComisionService comisiones, Alcances alcances,
                               Transiciones transiciones, TareaService tareas,
                               AlertaService alertas, SupervisionAgenteRepository supervisiones,
                               RevisionDisponibilidadRepository revisiones,
                               @Lazy ContratoService autoinvocado) {
        this.revisiones = revisiones;
        this.autoinvocado = autoinvocado;
        this.tareas = tareas;
        this.alertas = alertas;
        this.supervisiones = supervisiones;
        this.contratos = contratos;
        this.solicitudes = solicitudes;
        this.oportunidades = oportunidades;
        this.captaciones = captaciones;
        this.propiedades = propiedades;
        this.precios = precios;
        this.publicaciones = publicaciones;
        this.comisiones = comisiones;
        this.alcances = alcances;
        this.transiciones = transiciones;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaContrato> listar(int pagina, int tamano, Actor actor) {
        return listar(new FiltrosContrato(null, null, null, null, pagina, tamano), actor);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaContrato> listar(FiltrosContrato filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            // Un BROKER sin captaciones supervisadas obtiene lista VACIA, no 403.
            return Pagina.vacia();
        }
        String texto = limpiar(filtros.texto());
        if (texto != null) {
            return porTexto(alcance, filtros, texto, actor);
        }
        Page<ContratoAlquiler> page = contratos.buscar(
                alcance.idOrganizacion(), alcance.global(), actor.esAgente(), alcance.paramRoles(),
                limpiar(filtros.distrito()), filtros.idAgente(),
                PageRequest.of(Math.max(0, filtros.pagina() - 1), tamano(filtros.tamano()),
                        orden(filtros.orden())));
        return paginaDe(page.getContent(), page.getTotalElements(), actor);
    }

    /**
     * Camino de BUSQUEDA POR CONJUNTO DE CANDIDATOS (RC-003, §5). Una rama por
     * tabla, {@code UNION} en la base, y el MISMO conjunto para el conteo, la
     * pagina y los KPI de {@link #resumenCierres}. La proyeccion completa se
     * carga despues, solo para los ids ya resueltos.
     *
     * <p>Las dos variantes de orden comparten las mismas ramas: aqui solo se
     * elige cual de las dos se llama.
     */
    private Pagina<FichaContrato> porTexto(Alcance alcance, FiltrosContrato filtros,
                                            String texto, Actor actor) {
        String roles = alcance.paramRolesArray();
        String distrito = limpiar(filtros.distrito());
        int tamano = tamano(filtros.tamano());
        int pagina = Math.max(1, filtros.pagina());
        long total = contratos.contarPorTexto(alcance.idOrganizacion(), alcance.global(),
                actor.esAgente(), roles, texto, distrito, filtros.idAgente());
        if (total == 0) {
            return new Pagina<>(List.of(), 0);
        }
        int desplazamiento = (pagina - 1) * tamano;
        List<Long> ids = porCierre(filtros.orden())
                ? contratos.idsPorTextoPorCierre(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), roles, texto, distrito, filtros.idAgente(),
                        tamano, desplazamiento)
                : contratos.idsPorTextoPorId(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), roles, texto, distrito, filtros.idAgente(),
                        tamano, desplazamiento);
        if (ids.isEmpty()) {
            return new Pagina<>(List.of(), total);
        }
        // El `in` no conserva el orden de la lista: se repone con el de los ids,
        // que es el que decidio la base.
        Map<Long, ContratoAlquiler> porId = contratos
                .buscarFichaPorIds(alcance.idOrganizacion(), ids).stream()
                .collect(java.util.stream.Collectors.toMap(ContratoAlquiler::getId, c -> c,
                        (a, b) -> a, java.util.LinkedHashMap::new));
        List<ContratoAlquiler> ordenados = ids.stream().map(porId::get)
                .filter(Objects::nonNull).toList();
        return paginaDe(ordenados, total, actor);
    }

    /** Una sola lectura de comisiones para toda la pagina, nunca N+1. */
    private Pagina<FichaContrato> paginaDe(List<ContratoAlquiler> contenido, long total,
                                            Actor actor) {
        Map<Long, FichaComision> comisionPorContrato = comisiones.porContratos(
                contenido.stream().map(ContratoAlquiler::getId).filter(Objects::nonNull).toList(),
                actor);
        boolean verNeto = verNeto(actor);
        return new Pagina<>(
                contenido.stream()
                        .map(c -> ficha(c, comisionPorContrato.get(c.getId()), verNeto))
                        .toList(),
                total);
    }

    private static boolean porCierre(String orden) {
        return "cierre".equalsIgnoreCase(orden);
    }

    /**
     * El orden congelado es {@code id desc}; solo la pantalla de cierres pide
     * el otro. Se decide aqui y no en la consulta para no duplicarla.
     */
    private static Sort orden(String orden) {
        return porCierre(orden)
                ? Sort.by(Sort.Direction.DESC, "fechaCierre", "id")
                : Sort.by(Sort.Direction.DESC, "id");
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenCierres resumenCierres(FiltrosContrato filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return resumenVacio();
        }
        // Con texto, los cuatro agregados salen del MISMO conjunto de
        // candidatos que pagina la lista; sin texto, del mismo WHERE. Nunca de
        // dos criterios distintos, o el KPI y la tabla dirian cosas diferentes.
        // En ambos casos se calculan en la BD sobre TODO el universo filtrado,
        // no sobre la pagina visible.
        String texto = limpiar(filtros.texto());
        String distrito = limpiar(filtros.distrito());
        String rolesArray = texto == null ? null : alcance.paramRolesArray();
        var r = texto != null
                ? contratos.resumenCierresPorTexto(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), rolesArray, texto, distrito, filtros.idAgente())
                : contratos.resumenCierres(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles(), distrito, filtros.idAgente());
        if (r == null) {
            return resumenVacio();
        }
        List<ImportePorMoneda> importes = (texto != null
                ? contratos.comisionesGeneradasPorTexto(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), rolesArray, texto, distrito, filtros.idAgente())
                : contratos.comisionesGeneradas(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles(), distrito, filtros.idAgente()))
                .stream()
                .map(i -> new ImportePorMoneda(i.getMoneda(), i.getMonto()))
                .toList();
        Map<String, BigDecimal> generadas = importes.stream().collect(
                java.util.stream.Collectors.toMap(ImportePorMoneda::moneda, ImportePorMoneda::monto));
        Map<String, BigDecimal> partesAgente = (texto != null
                ? contratos.repartosPorMonedaPorTexto(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), rolesArray, texto, distrito, filtros.idAgente())
                : contratos.repartosPorMoneda(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles(), distrito, filtros.idAgente()))
                .stream()
                .collect(java.util.stream.Collectors.toMap(i -> i.getMoneda(),
                        i -> cero(i.getParteAgente())));
        var evidencia = texto != null
                ? contratos.movimientosPorMonedaPorTexto(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), rolesArray, texto, distrito, filtros.idAgente())
                : contratos.movimientosPorMoneda(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles(), distrito, filtros.idAgente());
        Map<String, BigDecimal> cobradas = evidencia.stream().collect(
                java.util.stream.Collectors.toMap(i -> i.getMoneda(),
                        i -> cero(i.getMontoCobrado()).max(BigDecimal.ZERO)));
        Map<String, BigDecimal> pagadasAgente = evidencia.stream().collect(
                java.util.stream.Collectors.toMap(i -> i.getMoneda(),
                        i -> cero(i.getMontoPagadoAgente()).max(BigDecimal.ZERO)));
        return new ResumenCierres(
                r.getCierres() == null ? 0 : r.getCierres(),
                importes,
                importes(cobradas),
                diferencia(generadas, cobradas),
                importes(pagadasAgente),
                diferencia(partesAgente, pagadasAgente),
                r.getPorLiquidar() == null ? 0 : r.getPorLiquidar(),
                r.getSinLiquidacion() == null ? 0 : r.getSinLiquidacion());
    }

    private static ResumenCierres resumenVacio() {
        return new ResumenCierres(0, List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0);
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static List<ImportePorMoneda> importes(Map<String, BigDecimal> valores) {
        return valores.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new ImportePorMoneda(e.getKey(), e.getValue()))
                .toList();
    }

    private static List<ImportePorMoneda> diferencia(Map<String, BigDecimal> total,
                                                      Map<String, BigDecimal> pagado) {
        return total.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new ImportePorMoneda(e.getKey(),
                        cero(e.getValue()).subtract(cero(pagado.get(e.getKey()))).max(BigDecimal.ZERO)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> distritosDeCierres(FiltrosContrato filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return List.of();
        }
        String texto = limpiar(filtros.texto());
        return texto != null
                ? contratos.distritosDeCierresPorTexto(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRolesArray(), texto,
                        limpiar(filtros.distrito()), filtros.idAgente())
                : contratos.distritosDeCierres(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles(),
                        limpiar(filtros.distrito()), filtros.idAgente());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgenteConCierres> agentesDeCierres(FiltrosContrato filtros, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return List.of();
        }
        String texto = limpiar(filtros.texto());
        return (texto != null
                ? contratos.agentesDeCierresPorTexto(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRolesArray(), texto,
                        limpiar(filtros.distrito()), filtros.idAgente())
                : contratos.agentesDeCierres(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles(),
                        limpiar(filtros.distrito()), filtros.idAgente()))
                .stream()
                .map(a -> new AgenteConCierres(a.getId(), a.getNombre()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FichaContrato obtenerPorOportunidad(long idOportunidad, Actor actor) {
        ContratoAlquiler contrato = contratos
                .buscarPorOportunidad(actor.idOrganizacion(), idOportunidad)
                .orElseThrow(() -> new NoEncontradoException("Contrato"));
        exigirAlcance(contrato, actor);
        return ficha(contrato, comisiones.porContrato(contrato.getId(), actor).orElse(null),
                verNeto(actor));
    }

    // ------------------------------------------------------------------
    // §6 — la cascada del cierre
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public FichaContrato registrar(DatosContrato datos, Actor actor) {
        if (datos == null || datos.idSolicitud() == null || datos.idSolicitud() <= 0) {
            throw new ReglaNegocioException("Selecciona la solicitud aprobada que se va a alquilar.");
        }
        SolicitudAlquiler solicitud = solicitudes
                .buscarFicha(actor.idOrganizacion(), datos.idSolicitud())
                .orElseThrow(() -> new NoEncontradoException("Solicitud"));
        // Aqui el contrato SI exige que la solicitud sea del agente que cierra
        // (a diferencia del alta de la solicitud, que no mira la oportunidad).
        if (solicitud.getAgente() == null
                || solicitud.getAgente().getId() != actor.idRolOperativo()) {
            throw new AccesoNoAutorizadoException();
        }
        String estadoCierre = estadoDeCierre(datos.estadoContrato());
        LocalDate cierre = datos.fechaCierre() != null ? datos.fechaCierre() : LocalDate.now();
        if (cierre.isAfter(LocalDate.now())) {
            throw new ReglaNegocioException("La fecha de cierre no puede ser futura.");
        }

        // --- Precondiciones de la cascada ---
        if (!solicitud.estaAprobada()) {
            throw new ReglaNegocioException(
                    "Solo se puede registrar el alquiler de una solicitud aprobada.");
        }
        OportunidadComercial oportunidad = solicitud.getOportunidad();
        if (oportunidad == null) {
            throw new ReglaNegocioException("Oportunidad comercial no encontrada.");
        }
        if (!OPORTUNIDAD_ABIERTA_AL_CONTRATO.contains(oportunidad.estadoActual())) {
            throw new ReglaNegocioException(
                    "La oportunidad ya esta cerrada; no admite un nuevo contrato.");
        }
        if (contratos.existeDeOportunidad(actor.idOrganizacion(), oportunidad.getId())) {
            throw new ReglaNegocioException(
                    "Esta operacion ya tiene un contrato de alquiler registrado.");
        }
        Captacion captacion = oportunidad.getCaptacion();
        if (captacion == null) {
            throw new ReglaNegocioException("Captacion no encontrada.");
        }
        BigDecimal renta = solicitud.getMontoPropuesto();
        String moneda = solicitud.getMoneda();
        String motivo = "Alquiler concretado con la solicitud " + solicitud.getCodigoSolicitud() + ".";

        // 1) El contrato: vinculo + formalizacion. Las condiciones del trato
        //    viven en la solicitud y NO se copian aqui.
        ContratoAlquiler contrato = new ContratoAlquiler();
        contrato.setOrganizacionId(solicitud.getOrganizacionId());
        contrato.setOportunidad(oportunidad);
        contrato.setSolicitud(solicitud);
        contrato.setFechaCierre(cierre);
        completarSnapshot(contrato, solicitud, estadoCierre);
        atribuir(contrato, solicitud, oportunidad);
        contrato.setIncidencias(texto(datos.incidencias()));
        transiciones.iniciar(contrato, estadoCierre);
        ContratoAlquiler guardado = contratos.save(contrato);

        // 2) La comision, PENDIENTE y con la bruta calculada sobre la renta.
        FichaComision comision = comisiones.crearPendienteNormalizada(
                guardado, captacion.getCondicionEconomica(), renta, moneda, actor);

        // 3) La oportunidad se cierra como EXITOSA. Esto es exactamente lo que
        //    faltaba en la v2 y el motivo de que /cierre-exitoso responda 400.
        oportunidad.marcarCierreExitoso();
        transiciones.aplicar(oportunidad, oportunidad.getId(),
                OportunidadComercial.FINALIZADA_EXITOSA, actor, motivo);
        oportunidades.save(oportunidad);

        // 4) La solicitud queda CERRADA: el alquiler se concreto y no se reabre.
        transiciones.aplicar(solicitud, solicitud.getId(), SolicitudAlquiler.CERRADA, actor, motivo);
        solicitudes.save(solicitud);

        // 5) La captacion cumplio su objetivo. El hecho de cierre se registra
        //    aparte del plazo pactado para conservar ambas fechas.
        transiciones.aplicar(captacion, captacion.getId(), Captacion.CERRADA, actor, motivo);
        captacion.cerrar(cierre, "A", motivo);
        captaciones.save(captacion);

        // 6) El local alquilado sale del mercado: NO DISPONIBLE, precio con
        //    hito 'C' (cerrado real) y publicaciones dadas de baja.
        cerrarLocal(captacion, renta, moneda, actor, motivo);

        // 7) Cerrado con F6/F7: se dan por hechas las tareas abiertas de las
        //    cuatro entidades de la operacion y se avisa al broker.
        //    OJO con el local: la tarea/alerta lo nombran INMUEBLE, que es como
        //    lo llama el cable; en la v2 la entidad se llama PROPIEDAD
        //    (MEJ-12/31, D-F6-4). No unificar hasta retirar el legado.
        tareas.resolverDeEntidad("OPORTUNIDAD", oportunidad.getId(), actor);
        tareas.resolverDeEntidad("SOLICITUD_ALQUILER", solicitud.getId(), actor);
        tareas.resolverDeEntidad("CAPTACION", captacion.getId(), actor);
        if (captacion.getPropiedad() != null) {
            tareas.resolverDeEntidad("INMUEBLE", captacion.getPropiedad().getId(), actor);
        }
        // El aviso se ata al AGENTE de la solicitud —siempre poblado—; el
        // broker lo ve a traves de la supervision.
        alertas.emitir(new AlertaService.DatosAlerta(Alerta.OPORTUNIDAD_CERRADA, Alerta.INFO,
                "OPORTUNIDAD", oportunidad.getId(),
                solicitud.getAgente() != null ? solicitud.getAgente().getId() : null,
                "El agente concreto el alquiler de la oportunidad "
                        + oportunidad.getCodigoOportunidad() + "."), actor);

        return ficha(guardado, comision, verNeto(actor));
    }

    @Override
    @Transactional
    public FichaContrato iniciarEnProceso(DatosContrato datos, Actor actor) {
        SolicitudAlquiler solicitud = solicitudAprobadaPropia(datos, actor);
        OportunidadComercial oportunidad = solicitud.getOportunidad();
        if (!OPORTUNIDAD_ABIERTA_AL_CONTRATO.contains(oportunidad.estadoActual())) {
            throw new ReglaNegocioException("La oportunidad ya esta cerrada; no admite un nuevo contrato.");
        }
        if (contratos.existeDeOportunidad(actor.idOrganizacion(), oportunidad.getId())) {
            throw new ReglaNegocioException("Esta operacion ya tiene un contrato de alquiler registrado.");
        }

        LocalDate fecha = fechaNoFutura(datos.fechaCierre(), "La fecha de cierre no puede ser futura.");
        ContratoAlquiler contrato = new ContratoAlquiler();
        contrato.setOrganizacionId(solicitud.getOrganizacionId());
        contrato.setOportunidad(oportunidad);
        contrato.setSolicitud(solicitud);
        contrato.setFechaCierre(fecha);
        contrato.setIncidencias(texto(datos.incidencias()));
        completarSnapshot(contrato, solicitud, EstadoContrato.EN_PROCESO.codigo());
        atribuir(contrato, solicitud, oportunidad);
        transiciones.iniciar(contrato, EstadoContrato.EN_PROCESO.codigo());
        ContratoAlquiler guardado = contratos.save(contrato);
        return ficha(guardado, null, verNeto(actor));
    }

    @Override
    @Transactional
    public FichaContrato firmar(long idContrato, DatosTransicion datos, Actor actor) {
        ContratoAlquiler contrato = cargarConAcceso(idContrato, actor);
        exigirCambioReal(contrato, EstadoContrato.FIRMADO);
        LocalDate fecha = fechaTransicion(datos);
        completarSnapshot(contrato, contrato.getSolicitud(), EstadoContrato.FIRMADO.codigo());
        contrato.setFechaEfectivaEstado(fecha);
        transiciones.aplicar(contrato, idContrato, EstadoContrato.FIRMADO.codigo(), actor,
                motivo(datos, "Contrato firmado"), fecha);
        contratos.save(contrato);
        FichaComision comision = cerrarOperacionFormalizada(contrato, fecha, actor);
        return ficha(contrato, comision, verNeto(actor));
    }

    @Override
    @Transactional
    public FichaContrato activar(long idContrato, DatosTransicion datos, Actor actor) {
        return transicionarContrato(idContrato, EstadoContrato.VIGENTE, datos, actor, false);
    }

    @Override
    @Transactional
    public FichaContrato finalizar(long idContrato, DatosTransicion datos, Actor actor) {
        return transicionarContrato(idContrato, EstadoContrato.FINALIZADO, datos, actor, true);
    }

    @Override
    @Transactional
    public FichaContrato rescindir(long idContrato, DatosTransicion datos, Actor actor) {
        return transicionarContrato(idContrato, EstadoContrato.RESCINDIDO, datos, actor, true);
    }

    @Override
    @Transactional
    public FichaContrato anular(long idContrato, DatosTransicion datos, Actor actor) {
        return transicionarContrato(idContrato, EstadoContrato.ANULADO, datos, actor, false);
    }

    @Override
    @Transactional
    public FichaContrato renovar(long idContrato, DatosRenovacion datos, Actor actor) {
        ContratoAlquiler anterior = cargarConAcceso(idContrato, actor);
        if (contratos.existsByOrganizacionIdAndContratoAnteriorId(
                actor.idOrganizacion(), idContrato)) {
            throw new ReglaNegocioException("El contrato ya tiene una renovacion registrada.");
        }
        if (datos == null || datos.fechaInicioContrato() == null || datos.fechaFinContrato() == null
                || !datos.fechaFinContrato().isAfter(datos.fechaInicioContrato())) {
            throw new ReglaNegocioException("La renovacion requiere fechas inicial y final validas.");
        }
        if (datos.rentaContractual() == null || datos.rentaContractual().signum() <= 0) {
            throw new ReglaNegocioException("La renta contractual debe ser mayor que cero.");
        }
        String moneda = CondicionesEconomicas.moneda(datos.moneda(), "de la renovacion");
        LocalDate fecha = datos.fechaInicioContrato();
        transiciones.aplicar(anterior, idContrato, EstadoContrato.RENOVADO.codigo(), actor,
                texto(datos.motivo()) != null ? texto(datos.motivo()) : "Contrato renovado", fecha);
        anterior.setFechaEfectivaEstado(fecha);
        // saveAndFlush, no save: `uq_contrato_vivo_por_propiedad` es un indice
        // PARCIAL y PostgreSQL no puede diferir su validacion, asi que se
        // evalua en el INSERT del sucesor. Hibernate ordena los INSERT ANTES
        // que los UPDATE dentro del mismo flush, de modo que sin este flush
        // explicito el sucesor nacia 'D' mientras el anterior seguia 'V' en la
        // base y la renovacion moria contra el indice. El orden logico
        // -primero libero, despues ocupo- tiene que llegar a la BD en ese
        // mismo orden.
        contratos.saveAndFlush(anterior);

        ContratoAlquiler renovacion = new ContratoAlquiler();
        renovacion.setOrganizacionId(anterior.getOrganizacionId());
        renovacion.setOportunidad(anterior.getOportunidad());
        renovacion.setSolicitud(anterior.getSolicitud());
        renovacion.setContratoAnterior(anterior);
        renovacion.setFechaCierre(fecha);
        renovacion.setFechaInicioContrato(datos.fechaInicioContrato());
        renovacion.setFechaFinContrato(datos.fechaFinContrato());
        renovacion.setRentaContractual(datos.rentaContractual());
        renovacion.setMoneda(moneda);
        renovacion.setFechaEfectivaEstado(fecha);
        // La renovacion es el MISMO alquiler continuando: hereda la atribucion
        // del contrato que renueva, salvo el broker, que se resuelve al
        // renovar. Derivarla otra vez de la cadena daria lo mismo hoy y algo
        // distinto tras una reasignacion, que es justo lo que V27 evita.
        renovacion.atribuir(anterior.getIdRolAgenteCierre(),
                brokerDe(anterior.getIdRolAgenteCierre(), anterior.getOrganizacionId()),
                anterior.getIdCaptacion(), anterior.getIdPropiedad(), anterior.getIdRolCliente());
        transiciones.iniciar(renovacion, EstadoContrato.FIRMADO.codigo());
        ContratoAlquiler guardada = contratos.save(renovacion);
        Captacion captacion = captacionDe(guardada);
        FichaComision comision = comisiones.crearPendienteNormalizada(guardada,
                captacion.getCondicionEconomica(), datos.rentaContractual(), moneda, actor);
        return ficha(guardada, comision, verNeto(actor));
    }

    /**
     * <b>Repetir una operacion es un error, no un no-op.</b>
     *
     * <p>{@code Transiciones.aplicar} ignora en silencio una transicion hacia
     * el mismo estado, y eso es correcto <i>en general</i>: da idempotencia a
     * captacion, solicitud, oportunidad y publicacion. Pero en el contrato
     * significaba que finalizar dos veces respondia 200 sin cambiar nada, como
     * si hubiera funcionado — y quien lo pulsa cree que acaba de cerrar algo.
     *
     * <p>Por eso el rechazo vive AQUI, en el caso de uso, y no en
     * {@code Transiciones}: bajarlo alli cambiaria el comportamiento de las
     * otras cuatro entidades de golpe.
     */
    private static void exigirCambioReal(ContratoAlquiler contrato, EstadoContrato destino) {
        if (destino.codigo().equals(contrato.estadoActual())) {
            throw new ReglaNegocioException("El contrato ya esta en estado "
                    + destino.descripcion().toUpperCase(Locale.ROOT) + ".");
        }
    }

    private FichaContrato transicionarContrato(long idContrato, EstadoContrato destino,
                                                DatosTransicion datos, Actor actor,
                                                boolean revisarInmueble) {
        ContratoAlquiler contrato = cargarConAcceso(idContrato, actor);
        // Se valida ANTES de tocar nada: ni historial, ni tareas, ni comision.
        // La transicion ilegal la corta `MaquinasEstado` dentro de `aplicar`;
        // esto cubre el unico caso que aquella deja pasar.
        exigirCambioReal(contrato, destino);
        LocalDate fecha = fechaTransicion(datos);
        transiciones.aplicar(contrato, idContrato, destino.codigo(), actor,
                motivo(datos, "Contrato " + destino.descripcion().toLowerCase(Locale.ROOT)), fecha);
        contrato.setFechaEfectivaEstado(fecha);
        contratos.save(contrato);
        // Anular el contrato arrastra su liquidacion: la comision nace al
        // FIRMAR y un contrato firmado todavia se anula, asi que sin esto
        // quedaba viva y cobrable la comision de un contrato inexistente.
        // Si ya se cobro, esto RECHAZA la anulacion y la transaccion revierte.
        if (destino == EstadoContrato.ANULADO) {
            comisiones.anularPorContratoAnulado(idContrato, actor);
        }
        if (revisarInmueble) {
            Captacion captacion = captacionDe(contrato);
            if (captacion != null && captacion.getPropiedad() != null) {
                tareas.crearRevisionInmueble(captacion.getPropiedad().getId(),
                        captacion.getAgente() != null ? captacion.getAgente().getId() : null,
                        "Revisar disponibilidad despues de " + destino.descripcion().toLowerCase(Locale.ROOT),
                        idContrato, actor);
            }
        }
        return ficha(contrato, comisiones.porContrato(idContrato, actor).orElse(null), verNeto(actor));
    }

    private FichaComision cerrarOperacionFormalizada(ContratoAlquiler contrato,
                                                      LocalDate fecha, Actor actor) {
        SolicitudAlquiler solicitud = contrato.getSolicitud();
        OportunidadComercial oportunidad = contrato.getOportunidad();
        Captacion captacion = captacionDe(contrato);
        if (solicitud == null || oportunidad == null || captacion == null) {
            throw new ReglaNegocioException("El contrato no tiene una operacion comercial completa.");
        }
        if (!OPORTUNIDAD_ABIERTA_AL_CONTRATO.contains(oportunidad.estadoActual())) {
            throw new ReglaNegocioException("La oportunidad ya esta cerrada.");
        }
        String motivo = "Alquiler concretado con la solicitud " + solicitud.getCodigoSolicitud() + ".";
        FichaComision comision = comisiones.crearPendienteNormalizada(contrato,
                captacion.getCondicionEconomica(), contrato.getRentaContractual(), contrato.getMoneda(), actor);
        transiciones.aplicar(oportunidad, oportunidad.getId(),
                OportunidadComercial.FINALIZADA_EXITOSA, actor, motivo, fecha);
        oportunidades.save(oportunidad);
        transiciones.aplicar(solicitud, solicitud.getId(), SolicitudAlquiler.CERRADA, actor, motivo, fecha);
        solicitudes.save(solicitud);
        transiciones.aplicar(captacion, captacion.getId(), Captacion.CERRADA, actor, motivo, fecha);
        captacion.cerrar(fecha, "A", motivo);
        captaciones.save(captacion);
        cerrarLocal(captacion, contrato.getRentaContractual(), contrato.getMoneda(), actor, motivo);
        tareas.resolverDeEntidad("OPORTUNIDAD", oportunidad.getId(), actor);
        tareas.resolverDeEntidad("SOLICITUD_ALQUILER", solicitud.getId(), actor);
        tareas.resolverDeEntidad("CAPTACION", captacion.getId(), actor);
        if (captacion.getPropiedad() != null) {
            tareas.resolverDeEntidad("INMUEBLE", captacion.getPropiedad().getId(), actor);
        }
        alertas.emitir(new AlertaService.DatosAlerta(Alerta.OPORTUNIDAD_CERRADA, Alerta.INFO,
                "OPORTUNIDAD", oportunidad.getId(),
                solicitud.getAgente() != null ? solicitud.getAgente().getId() : null,
                "El agente concreto el alquiler de la oportunidad "
                        + oportunidad.getCodigoOportunidad() + "."), actor);
        return comision;
    }

    private SolicitudAlquiler solicitudAprobadaPropia(DatosContrato datos, Actor actor) {
        if (datos == null || datos.idSolicitud() == null || datos.idSolicitud() <= 0) {
            throw new ReglaNegocioException("Selecciona la solicitud aprobada que se va a alquilar.");
        }
        SolicitudAlquiler solicitud = solicitudes.buscarFicha(actor.idOrganizacion(), datos.idSolicitud())
                .orElseThrow(() -> new NoEncontradoException("Solicitud"));
        if (solicitud.getAgente() == null || solicitud.getAgente().getId() != actor.idRolOperativo()) {
            throw new AccesoNoAutorizadoException();
        }
        if (!solicitud.estaAprobada()) {
            throw new ReglaNegocioException("Solo se puede registrar el alquiler de una solicitud aprobada.");
        }
        if (solicitud.getOportunidad() == null) {
            throw new ReglaNegocioException("Oportunidad comercial no encontrada.");
        }
        return solicitud;
    }

    private static LocalDate fechaTransicion(DatosTransicion datos) {
        return fechaNoFutura(datos != null ? datos.fechaEfectiva() : null,
                "La fecha efectiva no puede ser futura.");
    }

    private static LocalDate fechaNoFutura(LocalDate fecha, String mensaje) {
        LocalDate efectiva = fecha != null ? fecha : LocalDate.now();
        if (efectiva.isAfter(LocalDate.now())) throw new ReglaNegocioException(mensaje);
        return efectiva;
    }

    private static String motivo(DatosTransicion datos, String porDefecto) {
        String indicado = datos != null ? texto(datos.motivo()) : null;
        return indicado != null ? indicado : porDefecto;
    }

    /**
     * Congela la atribucion del cierre (V27). Antes de esto el agente, la
     * captacion, el inmueble y el cliente se releian de la cadena vigente en
     * cada consulta, asi que una reasignacion posterior reescribia a quien se
     * le atribuia un alquiler cerrado meses atras.
     *
     * <p>El agente es el de la SOLICITUD —el que la respuesta publica— y, en su
     * defecto, el de la oportunidad: misma precedencia que ya usaban la ficha y
     * E4. El broker es el supervisor VIGENTE al cerrar; queda nulo si el agente
     * no tenia ninguno, y no se rellena despues con el supervisor de turno.
     *
     * <p><b>No cambia ninguna regla de alcance.</b> El BROKER sigue alcanzando
     * los contratos por captacion supervisada HOY, como declara la matriz
     * operacion→rol; este campo es trazabilidad del hecho, no un permiso.
     */
    private void atribuir(ContratoAlquiler contrato, SolicitudAlquiler solicitud,
                          OportunidadComercial oportunidad) {
        DetalleAgente agente = solicitud != null && solicitud.getAgente() != null
                ? solicitud.getAgente()
                : oportunidad.getAgente();
        Captacion captacion = oportunidad.getCaptacion();
        Propiedad propiedad = captacion != null ? captacion.getPropiedad() : null;
        DetalleCliente cliente = oportunidad.getCliente();
        contrato.atribuir(
                agente != null ? agente.getId() : null,
                agente != null ? brokerDe(agente.getId(), contrato.getOrganizacionId()) : null,
                captacion != null ? captacion.getId() : null,
                propiedad != null ? propiedad.getId() : null,
                cliente != null ? cliente.getId() : null);
    }

    private Long brokerDe(Long idRolAgente, Long idOrganizacion) {
        if (idRolAgente == null || idOrganizacion == null) {
            return null;
        }
        return supervisiones.buscarActivaPorAgente(idOrganizacion, idRolAgente)
                .map(SupervisionAgente::getIdRolBroker)
                .orElse(null);
    }

    /**
     * Efecto 6 de la cascada. El precio de cierre conserva la moneda de la
     * renta final y usa la fecha de HOY —no la de cierre—, como la v1.
     */
    private void cerrarLocal(Captacion captacion, BigDecimal renta, String moneda,
                             Actor actor, String motivo) {
        Propiedad propiedad = captacion == null ? null : captacion.getPropiedad();
        if (propiedad == null || propiedad.getId() == null) {
            return;
        }
        transiciones.aplicarDisponibilidad(propiedad, propiedad.getId(),
                DisponibilidadComercial.ALQUILADO, actor, motivo);
        propiedades.save(propiedad);

        PrecioPropiedad precio = new PrecioPropiedad();
        precio.setOrganizacionId(propiedad.getOrganizacionId());
        precio.setIdPropiedad(propiedad.getId());
        // Un ContratoAlquiler cierra un alquiler; el cierre de una venta es el
        // expediente de compraventa (V51), que tiene sus propios hitos. La
        // operacion se declara, no se hereda de ningun defecto.
        precio.setOperacion(OperacionInmobiliaria.ALQUILER);
        precio.setHito("C");
        precio.setMoneda(moneda);
        precio.setMonto(renta);
        precio.setFecha(LocalDate.now());
        // De QUE encargo es este cierre (V76). Es el ultimo hito de su serie, y
        // sin el id la fila quedaba colgando de la propiedad: dos alquileres
        // sucesivos del mismo inmueble mezclaban sus cierres en una sola linea.
        // Lo detecto `tg_precio_exige_encargo`, que es exactamente su trabajo.
        precio.delEncargo(captacion.getId());
        precios.save(precio);

        for (Publicacion publicacion
                : publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(propiedad.getId())) {
            if (!Publicacion.ESTADO_CERRADO.equals(publicacion.getEstado())) {
                publicacion.setEstado(Publicacion.ESTADO_CERRADO);
                publicacion.setFechaBaja(OffsetDateTime.now());
                publicaciones.save(publicacion);
            }
        }
    }

    // ------------------------------------------------------------------
    // §5 — los dos gates de comision (BROKER supervisor, sin ADMIN)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public FichaContrato asignarComision(long idContrato, BigDecimal montoAgente, Actor actor) {
        // El cable valida el cuerpo ANTES de buscar el contrato: con el monto
        // ausente responde 400 aunque el contrato no exista.
        if (montoAgente == null) {
            throw new ReglaNegocioException("Indica el monto del agente.");
        }
        ContratoAlquiler contrato = cargarConAcceso(idContrato, actor);
        FichaComision comision = comisiones.asignarMontoAgente(idContrato, montoAgente, actor);
        // Estos dos endpoints son de BROKER, asi que el neto SIEMPRE viaja.
        return ficha(contrato, comision, true);
    }

    @Override
    @Transactional
    public FichaContrato registrarCobroComision(long idContrato, String estado, LocalDate fechaCobro,
                                                String formaPago, Actor actor) {
        if (estado == null || estado.isBlank()) {
            throw new ReglaNegocioException("Indica el estado del cobro (Cobrada o Anulada).");
        }
        ContratoAlquiler contrato = cargarConAcceso(idContrato, actor);
        FichaComision comision = comisiones.registrarCobro(
                idContrato, estado, fechaCobro, formaPago, actor);
        return ficha(contrato, comision, true);
    }

    /**
     * <b>Sin {@code @Transactional} a proposito.</b>
     *
     * <p>La carrera de dos peticiones simultaneas con la MISMA clave la corta
     * el indice unico {@code uq_movimiento_idempotencia}: una gana y la otra
     * recibe una violacion de unicidad. Para poder responderle con el
     * resultado de la que gano hay que LEER despues, y eso es imposible dentro
     * de la transaccion que acaba de marcarse como rollback-only. Por eso la
     * transaccion vive en el metodo interno y el reintento se resuelve aqui
     * fuera, en una lectura nueva.
     *
     * <p>La comprobacion previa que hace {@code ComisionServiceImpl} no
     * sustituye al indice: solo resuelve el caso normal —un reintento que
     * llega cuando el primero ya termino— sin llegar a intentar el INSERT.
     */
    @Override
    public FichaContrato registrarMovimientoComision(long idContrato, String tipo, BigDecimal monto,
                                                      String moneda, LocalDate fecha, String formaPago,
                                                      String observacion, String claveIdempotencia,
                                                      Actor actor) {
        try {
            return registrarMovimientoComisionEnTransaccion(idContrato, tipo, monto, moneda,
                    fecha, formaPago, observacion, claveIdempotencia, actor);
        } catch (DataIntegrityViolationException carrera) {
            if (Idempotencia.normalizar(claveIdempotencia) == null) {
                throw carrera;
            }
            // Perdio la carrera: el movimiento lo creo la peticion gemela. Se
            // devuelve SU resultado, que es exactamente lo que el cliente
            // habria recibido si su reintento hubiera llegado un poco despues.
            return autoinvocado.registrarMovimientoComisionEnTransaccion(idContrato, tipo, monto,
                    moneda, fecha, formaPago, observacion, claveIdempotencia, actor);
        }
    }

    @Override
    @Transactional
    public FichaContrato registrarMovimientoComisionEnTransaccion(
            long idContrato, String tipo, BigDecimal monto, String moneda, LocalDate fecha,
            String formaPago, String observacion, String claveIdempotencia, Actor actor) {
        ContratoAlquiler contrato = cargarConAcceso(idContrato, actor);
        return ficha(contrato, comisiones.registrarMovimiento(idContrato, tipo, monto, moneda,
                fecha, formaPago, observacion, claveIdempotencia, actor), true);
    }

    // ------------------------------------------------------------------
    // §7.3.2 — recuperacion de disponibilidad tras terminar el contrato
    // ------------------------------------------------------------------

    /**
     * <b>Terminar un contrato NO devuelve el local al mercado.</b> `F` y `S`
     * dejan la propiedad ALQUILADA y crean una tarea de revision; esta
     * operacion es la decision humana que cierra esa tarea, y lo unico que
     * toca es la disponibilidad comercial.
     *
     * <p>Lo que deliberadamente NO hace, aunque el local vuelva al mercado:
     * no reabre la captacion cerrada, no revive publicaciones, no toca
     * oportunidad, solicitud ni liquidacion. Esas filas son la evidencia del
     * ciclo comercial que produjo el contrato anterior; volver a comercializar
     * es empezar un ciclo NUEVO, no resucitar el viejo. Sin esta regla una
     * captacion pasaria a ser una entidad que muere y revive, y se perderia el
     * limite de cada periodo de comercializacion.
     */
    @Override
    @Transactional
    public FichaRevisionDisponibilidad revisarDisponibilidad(long idContrato, String resultado,
                                                             String motivo, Actor actor) {
        String destino = destinoDeRevision(resultado);
        String texto = texto(motivo);
        if (texto == null) {
            throw new ReglaNegocioException("Indica el motivo de la revision.");
        }
        // 1) Contrato con tenant y alcance: un contrato de otra organizacion es
        //    404, no 403 (convencion del cable).
        ContratoAlquiler contrato = cargarConAcceso(idContrato, actor);

        // 2) Estado: solo un contrato que dejo de ocupar el inmueble se revisa.
        if (!ESTADOS_REVISABLES.contains(contrato.estadoActual())) {
            throw new ReglaNegocioException(
                    "Solo se revisa la disponibilidad de un contrato finalizado, rescindido o anulado.");
        }

        // 6) Idempotencia ANTES de escribir: `uq_revision_contrato` es la
        //    ultima barrera, pero exponer su violacion como 500 no es una
        //    respuesta. Misma decision devuelve la revision original; una
        //    distinta es un conflicto.
        var previa = revisiones.porContrato(actor.idOrganizacion(), idContrato);
        if (previa.isPresent()) {
            RevisionDisponibilidad ya = previa.get();
            if (!ya.getDisponibilidadNueva().equals(destino)) {
                throw new ConflictoException(
                        "Este contrato ya se reviso con un resultado distinto ("
                                + descripcionDestino(ya.getDisponibilidadNueva()) + ").");
            }
            return ficha(ya, true);
        }

        // 3) Propiedad.
        Propiedad propiedad = contrato.getIdPropiedad() == null ? null
                : propiedades.findByOrganizacionIdAndId(actor.idOrganizacion(), contrato.getIdPropiedad())
                        .orElse(null);
        if (propiedad == null) {
            throw new NoEncontradoException("Local comercial");
        }
        // Un contrato ANULADO que nunca formalizo el cierre no dejo el local
        // ocupado: no hay nada que recuperar y no se inventa una revision.
        if (!DisponibilidadComercial.ALQUILADO.codigo().equals(propiedad.getDisponibilidadComercial())) {
            throw new ReglaNegocioException(
                    "El local no esta alquilado: este contrato no lo saco del mercado.");
        }

        // 4) Ocupacion: nadie libera un local que otro contrato sigue ocupando.
        //    Es el caso de la renovacion —anterior en R, sucesor en D/V— y la
        //    razon de que revisar el contrato anterior tenga que fallar.
        long vivos = contratos.contarVivosDePropiedad(
                actor.idOrganizacion(), propiedad.getId(), idContrato);
        if (vivos > 0) {
            throw new ReglaNegocioException(
                    "El local sigue ocupado por otro contrato vigente; no se puede liberar.");
        }

        String anterior = propiedad.getDisponibilidadComercial();

        // 5) La evidencia de la decision, con el contrato origen que
        //    `historial_estado` no puede expresar.
        RevisionDisponibilidad revision = new RevisionDisponibilidad();
        revision.setOrganizacionId(actor.idOrganizacion());
        revision.setIdContratoAlquiler(idContrato);
        revision.setIdPropiedad(propiedad.getId());
        revision.setDisponibilidadAnterior(anterior);
        revision.setDisponibilidadNueva(destino);
        revision.setMotivo(texto);
        revision.setIdActor(actor.idPersona());
        revision.setTipoRolActor(actor.tipoRolOperativo());
        revision.setFechaRevision(LocalDate.now());
        RevisionDisponibilidad guardada = revisiones.save(revision);

        // 6-7) La transicion pasa por Transiciones, asi que queda en
        //      historial_estado con actor, rol, motivo y fecha efectiva.
        transiciones.aplicarDisponibilidad(propiedad, propiedad.getId(),
                DisponibilidadComercial.desde(destino), actor,
                "Revision del contrato " + idContrato + ": " + texto);
        propiedades.save(propiedad);

        // 8) Su tarea, no "alguna del inmueble": el vinculo lo da V43.
        tareas.resolverDeContratoOrigen(idContrato, actor);
        return ficha(guardada, false);
    }

    /** Contratos que dejaron de ocupar el inmueble y por tanto admiten revision. */
    private static final Set<String> ESTADOS_REVISABLES = Set.of(
            EstadoContrato.FINALIZADO.codigo(), EstadoContrato.RESCINDIDO.codigo(),
            EstadoContrato.ANULADO.codigo());

    /**
     * El cliente manda el RESULTADO funcional, no la letra de disponibilidad:
     * traducir es cosa del backend. {@code R} (Reservado) no es un desenlace
     * posible aqui —reservar pertenece a otra causa de negocio—.
     */
    private static String destinoDeRevision(String resultado) {
        String codigo = resultado == null ? "" : resultado.trim().toUpperCase(Locale.ROOT);
        return switch (codigo) {
            case "VOLVER_AL_MERCADO" -> DisponibilidadComercial.DISPONIBLE.codigo();
            case "RETIRAR_DEL_MERCADO" -> DisponibilidadComercial.RETIRADO.codigo();
            default -> throw new ReglaNegocioException(
                    "Resultado de revision invalido: usa VOLVER_AL_MERCADO o RETIRAR_DEL_MERCADO.");
        };
    }

    private static String descripcionDestino(String codigo) {
        return DisponibilidadComercial.DISPONIBLE.codigo().equals(codigo)
                ? "VOLVER_AL_MERCADO" : "RETIRAR_DEL_MERCADO";
    }

    private static FichaRevisionDisponibilidad ficha(RevisionDisponibilidad r, boolean repetida) {
        return new FichaRevisionDisponibilidad(r.getId(), r.getIdContratoAlquiler(),
                r.getIdPropiedad(), r.getDisponibilidadAnterior(), r.getDisponibilidadNueva(),
                descripcionDestino(r.getDisponibilidadNueva()), r.getMotivo(),
                r.getFechaRevision(), repetida);
    }

    // ------------------------------------------------------------------
    // Alcance por rol: BROKER por CAPTACION (§7), no por agente.
    // ------------------------------------------------------------------

    private ContratoAlquiler cargarConAcceso(long id, Actor actor) {
        ContratoAlquiler contrato = contratos.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Contrato"));
        exigirAlcance(contrato, actor);
        return contrato;
    }

    private void exigirAlcance(ContratoAlquiler contrato, Actor actor) {
        if (!alcanza(contrato, actor)) {
            throw new AccesoNoAutorizadoException();
        }
    }

    private boolean alcanza(ContratoAlquiler contrato, Actor actor) {
        if (actor.esTenantAdmin()) {
            return true;
        }
        SolicitudAlquiler solicitud = contrato.getSolicitud();
        if (actor.esAgente()) {
            // El agente llega por SU solicitud, no por la captacion.
            return solicitud != null && solicitud.getAgente() != null
                    && solicitud.getAgente().getId() == actor.idRolOperativo();
        }
        Captacion captacion = captacionDe(contrato);
        return captacion != null && captacion.getAgente() != null
                && alcances.supervisados(actor.idOrganizacion(), actor.idRolOperativo())
                        .contains(captacion.getAgente().getId());
    }

    private static Captacion captacionDe(ContratoAlquiler contrato) {
        OportunidadComercial oportunidad = contrato.getOportunidad();
        return oportunidad != null ? oportunidad.getCaptacion() : null;
    }

    /** Solo ADMIN y BROKER ven el reparto agente/empresa de la liquidacion (§5). */
    private static boolean verNeto(Actor actor) {
        return !actor.esAgente();
    }

    // ------------------------------------------------------------------
    // Derivaciones del cable
    // ------------------------------------------------------------------

    /**
     * El cierre solo admite Firmado o Vigente; por defecto Vigente. Los dos
     * mensajes son los del REST v1, que corta antes que su BL —la BL tiene un
     * texto distinto ("El contrato solo puede cerrarse como Firmado o
     * Vigente.") que nunca llega al cable.
     */
    private static String estadoDeCierre(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return ContratoAlquiler.VIGENTE;
        }
        String estado = codigo.trim().toUpperCase(Locale.ROOT);
        if (!ContratoAlquiler.ESTADOS.contains(estado)) {
            throw new ReglaNegocioException("Estado de contrato invalido.");
        }
        if (!ContratoAlquiler.ESTADOS_DE_CIERRE.contains(estado)) {
            throw new ReglaNegocioException("El cierre solo admite los estados Firmado o Vigente.");
        }
        return estado;
    }

    /**
     * Plazo en meses: de la solicitud y, si falta, parseado del texto libre
     * ("24 meses" -> 24). Es lo que hace el DTO de la v1.
     */
    private static Integer plazoMeses(SolicitudAlquiler solicitud) {
        if (solicitud == null) {
            return null;
        }
        if (solicitud.getPlazoContratoMeses() != null) {
            return solicitud.getPlazoContratoMeses();
        }
        String texto = solicitud.getPlazoTentativo();
        if (texto == null) {
            return null;
        }
        String digitos = texto.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : Integer.valueOf(digitos);
    }

    private static String texto(String valor) {
        return valor != null && !valor.isBlank() ? valor.trim() : null;
    }

    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }

    private static FichaContrato ficha(ContratoAlquiler c, FichaComision comision, boolean verNeto) {
        SolicitudAlquiler solicitud = c.getSolicitud();
        OportunidadComercial oportunidad = solicitud != null && solicitud.getOportunidad() != null
                ? solicitud.getOportunidad()
                : c.getOportunidad();
        DetalleCliente cliente = oportunidad != null ? oportunidad.getCliente() : null;
        Captacion captacion = oportunidad != null ? oportunidad.getCaptacion() : null;
        Propiedad propiedad = captacion != null ? captacion.getPropiedad() : null;
        PersonaRol propietario = propiedad != null ? propiedad.getRolPropietario() : null;
        // El agente publicado es el ATRIBUIDO al cerrar (V27), no el que hoy
        // cuelgue de la cadena. Hasta V27 salia de la solicitud; para toda fila
        // existente el backfill dejo el mismo valor, asi que el cable no cambia
        // —cambia el dia que alguien reasigna, que es de lo que se trata—.
        DetalleAgente agente = c.getAgenteCierre() != null
                ? c.getAgenteCierre()
                : (solicitud != null ? solicitud.getAgente() : null);
        Integer plazo = plazoMeses(solicitud);
        LocalDate inicio = solicitud != null ? solicitud.getFechaInicioContrato() : null;
        return new FichaContrato(
                c.getId(),
                solicitud != null ? solicitud.getId() : null,
                solicitud != null ? solicitud.getCodigoSolicitud() : null,
                oportunidad != null ? oportunidad.getId() : null,
                oportunidad != null ? oportunidad.getCodigoOportunidad() : null,
                nombre(cliente != null ? cliente.getRol() : null),
                propiedad != null ? propiedad.getDireccion() : null,
                propiedad != null ? propiedad.getDistrito() : null,
                propiedad != null ? propiedad.estadoLegado() : null,
                captacion != null ? captacion.getCodigoCaptacion() : null,
                nombre(agente != null ? agente.getRol() : null),
                c.getRentaContractual(), c.getMoneda(),
                plazo,
                comision != null ? comision.monto() : null,
                comision != null ? comision.moneda() : null,
                c.getFechaInicioContrato(), c.getFechaFinContrato(),
                c.getFechaCierre(),
                c.estadoActual(),
                comision != null ? comision.estado() : null,
                c.getIncidencias(),
                comision != null ? comision.id() : null,
                agente != null ? agente.getId() : null,
                propietario != null ? propietario.getId() : null,
                nombre(propietario),
                verNeto && comision != null ? comision.montoAgente() : null,
                verNeto && comision != null ? comision.montoEmpresa() : null,
                comision != null ? comision.formaPago() : null,
                comision != null ? comision.fechaCobro() : null,
                c.getContratoAnterior() != null ? c.getContratoAnterior().getId() : null,
                comision != null ? comision.montoCobrado() : null,
                comision != null ? comision.saldoCobro() : null,
                comision != null ? comision.montoPagadoAgente() : null,
                comision != null ? comision.saldoPagoAgente() : null);
    }

    private static void completarSnapshot(ContratoAlquiler contrato, SolicitudAlquiler solicitud,
                                           String estado) {
        LocalDate inicio = solicitud.getFechaInicioContrato();
        Integer plazo = plazoMeses(solicitud);
        LocalDate fin = inicio != null && plazo != null ? inicio.plusMonths(plazo) : null;
        if (Set.of(ContratoAlquiler.FIRMADO, ContratoAlquiler.VIGENTE).contains(estado)) {
            if (inicio == null || fin == null || !fin.isAfter(inicio)) {
                throw new ReglaNegocioException(
                        "Un contrato firmado o vigente requiere fecha inicial y fecha final validas.");
            }
        }
        contrato.setFechaInicioContrato(inicio);
        contrato.setFechaFinContrato(fin);
        contrato.setRentaContractual(solicitud.getMontoPropuesto());
        contrato.setMoneda(CondicionesEconomicas.moneda(solicitud.getMoneda(), "del contrato"));
        contrato.setFechaEfectivaEstado(contrato.getFechaCierre());
    }

    private static String nombre(PersonaRol rol) {
        return rol == null || rol.getPersona() == null ? null : rol.getPersona().getNombresORazonSocial();
    }
}
