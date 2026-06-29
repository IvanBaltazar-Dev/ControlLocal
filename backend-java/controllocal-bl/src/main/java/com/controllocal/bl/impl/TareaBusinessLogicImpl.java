package com.controllocal.bl.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.ComisionLiquidacionBusinessLogic;
import com.controllocal.bl.ContratoAlquilerBusinessLogic;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.ProspeccionBusinessLogic;
import com.controllocal.bl.ReportePropietarioBusinessLogic;
import com.controllocal.bl.RequerimientoClienteBusinessLogic;
import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.bl.TareaBusinessLogic;
import com.controllocal.bl.VisitaBusinessLogic;
import com.controllocal.bl.support.CoincidenciaCartera;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.TareaDAO;
import com.controllocal.dao.impl.TareaDAOImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.ComisionLiquidacion;
import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.comercial.RequerimientoCliente;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.Tarea;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.comercial.enums.EstadoCaptacion;
import com.controllocal.model.comercial.enums.EstadoComision;
import com.controllocal.model.comercial.enums.EstadoRequerimiento;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.enums.EstadoTarea;
import com.controllocal.model.comercial.enums.EstadoVisita;
import com.controllocal.model.comercial.enums.Prioridad;
import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.comercial.enums.TipoTarea;
import com.controllocal.model.inmueble.enums.EstadoLocalComercial;
import com.controllocal.model.usuario.AgenteInmobiliario;

/**
 * Bandeja del agente (Etapa 5): 6 disparadores derivados del estado del flujo:
 * recontactos vencidos, solicitudes aprobadas sin cierre, comisiones pendientes con
 * monto asignado, documentos observados, visitas proximas y reporte al propietario de
 * captaciones activas. El reconcile es idempotente: crea las tareas que faltan (sin
 * duplicar las abiertas/canceladas) y auto-resuelve las que ya no aplican.
 */
public class TareaBusinessLogicImpl implements TareaBusinessLogic {

    private static final int DIAS_RECONTACTO = 7;
    private static final int DIAS_VISITA_PROXIMA = 3;
    // Cadencia del reporte al propietario: se vuelve a pedir si el ultimo reporte supera este plazo.
    private static final int DIAS_REPORTE = 15;
    // Puntaje minimo de coincidencia para sugerir una oportunidad en la bandeja (evita ruido).
    private static final int UMBRAL_PROPUESTA = 60;

    // entidad_tipo que gestiona este motor; solo estas se auto-resuelven al reconciliar.
    private static final Set<TipoEntidad> ENTIDADES_AUTO = Set.of(
            TipoEntidad.PROSPECCION, TipoEntidad.SOLICITUD_ALQUILER, TipoEntidad.CONTRATO_ALQUILER,
            TipoEntidad.VISITA, TipoEntidad.CAPTACION, TipoEntidad.REQUERIMIENTO);

    private final TareaDAO tareaDAO;
    private final ProspeccionBusinessLogic prospecciones;
    private final SolicitudAlquilerBusinessLogic solicitudes;
    private final ContratoAlquilerBusinessLogic contratos;
    private final OportunidadComercialBusinessLogic oportunidades;
    private final ComisionLiquidacionBusinessLogic comisiones;
    private final VisitaBusinessLogic visitas;
    private final CaptacionBusinessLogic captaciones;
    private final RequerimientoClienteBusinessLogic requerimientos;
    private final ReportePropietarioBusinessLogic reportes;

    public TareaBusinessLogicImpl() {
        this(new TareaDAOImpl(), new ProspeccionBusinessLogicImpl(),
                new SolicitudAlquilerBusinessLogicImpl(), new ContratoAlquilerBusinessLogicImpl(),
                new OportunidadComercialBusinessLogicImpl(), new ComisionLiquidacionBusinessLogicImpl(),
                new VisitaBusinessLogicImpl(),
                new CaptacionBusinessLogicImpl(), new RequerimientoClienteBusinessLogicImpl(),
                new ReportePropietarioBusinessLogicImpl());
    }

    public TareaBusinessLogicImpl(TareaDAO tareaDAO, ProspeccionBusinessLogic prospecciones,
            SolicitudAlquilerBusinessLogic solicitudes, ContratoAlquilerBusinessLogic contratos,
            OportunidadComercialBusinessLogic oportunidades, ComisionLiquidacionBusinessLogic comisiones,
            VisitaBusinessLogic visitas, CaptacionBusinessLogic captaciones,
            RequerimientoClienteBusinessLogic requerimientos, ReportePropietarioBusinessLogic reportes) {
        this.tareaDAO = tareaDAO;
        this.prospecciones = prospecciones;
        this.solicitudes = solicitudes;
        this.contratos = contratos;
        this.oportunidades = oportunidades;
        this.comisiones = comisiones;
        this.visitas = visitas;
        this.captaciones = captaciones;
        this.requerimientos = requerimientos;
        this.reportes = reportes;
    }

    @Override
    public List<Tarea> bandejaDe(Long idAgente) {
        if (idAgente == null || idAgente <= 0) {
            throw new BusinessException("El agente es obligatorio.");
        }

        // Datos base del agente: se cargan UNA vez y se reutilizan para derivar y para enriquecer.
        // Antes enriquecer re-consultaba cada entidad por id (N+1); ahora lee de mapas en memoria.
        DatosAgente datos = cargarDatos(idAgente);

        // Lecturas (fuera de transaccion): que deberia existir vs que existe.
        List<Tarea> vigentes = derivarTareas(idAgente, datos);
        Set<String> clavesVigentes = new HashSet<>();
        for (Tarea t : vigentes) {
            clavesVigentes.add(clave(t.getEntidadTipo(), t.getEntidadId()));
        }

        List<Tarea> existentes = tareaDAO.listarPorAgente(idAgente, null);
        Set<String> clavesBloqueo = new HashSet<>();
        for (Tarea t : existentes) {
            if (t.getEstado() == EstadoTarea.PENDIENTE || t.getEstado() == EstadoTarea.EN_PROCESO
                    || t.getEstado() == EstadoTarea.CANCELADA) {
                clavesBloqueo.add(clave(t.getEntidadTipo(), t.getEntidadId()));
            }
        }

        // Escritura (una transaccion): crea faltantes, auto-resuelve las que ya no aplican.
        TransactionRunner.write((TransactionRunner.TransactionalConnectionSupplier<Integer>) conn -> {
            for (Tarea t : vigentes) {
                if (!clavesBloqueo.contains(clave(t.getEntidadTipo(), t.getEntidadId()))) {
                    tareaDAO.crear(t);
                }
            }
            for (Tarea t : existentes) {
                if (t.getEstado() == EstadoTarea.PENDIENTE
                        && ENTIDADES_AUTO.contains(t.getEntidadTipo())
                        && !clavesVigentes.contains(clave(t.getEntidadTipo(), t.getEntidadId()))) {
                    t.setEstado(EstadoTarea.COMPLETADA);
                    t.setFechaCompletada(LocalDateTime.now());
                    tareaDAO.actualizar(t);
                }
            }
            return 0;
        });

        return tareaDAO.listarPorAgente(idAgente, null).stream()
                .filter(t -> t.getEstado() == EstadoTarea.PENDIENTE || t.getEstado() == EstadoTarea.EN_PROCESO)
                .map(t -> enriquecer(t, datos))
                .toList();
    }

    @Override
    public boolean cancelar(Long idTarea, Long idAgente) {
        Tarea tarea = tareaDAO.buscarPorId(idTarea)
                .orElseThrow(() -> new BusinessException("Tarea no encontrada."));
        if (!esDelAgente(tarea.getAgente(), idAgente)) {
            throw new BusinessException("La tarea no pertenece al agente.");
        }
        // eliminar() del DAO es un soft-cancel (UPDATE estado = 'CANCELADA').
        return TransactionRunner.write(
                (TransactionRunner.TransactionalConnectionSupplier<Boolean>) conn -> tareaDAO.eliminar(idTarea));
    }

    // Carga las listas base del agente UNA sola vez (acotadas por rol en SQL) y las indexa por id
    // para que el enriquecimiento no vuelva a consultar la BD por cada tarea. Todo lo que antes
    // se consultaba por cliente (requerimientos) o por captacion (reportes) se trae aqui en bloque
    // para que la bandeja tenga un numero fijo de consultas, independiente del volumen del agente.
    private DatosAgente cargarDatos(Long idAgente) {
        List<Long> ids = List.of(idAgente);
        long totalContratos = contratos.contarFiltrado(idAgente, null);
        List<ContratoAlquiler> contratosAgente = totalContratos <= 0
                ? List.of()
                : contratos.listarPaginaFiltrado(idAgente, null, (int) Math.min(totalContratos, 5000), 0);

        List<Prospeccion> prospeccionesAgente = prospecciones.listarPorAgentes(ids);
        List<SolicitudAlquiler> solicitudesAgente = solicitudes.listarPorAgentes(ids);
        List<OportunidadComercial> oportunidadesAgente = oportunidades.listarPorAgentes(ids);
        List<Captacion> captacionesAgente = captaciones.listarPorAgente(idAgente);
        List<Visita> visitasAgente = visitas.listarPorAgentes(ids);

        // Requerimientos de los clientes del agente, en UNA consulta (con distritos en bloque):
        // alimenta el matching de cartera y el enriquecimiento de tareas PROPONER_OPORTUNIDAD.
        Set<Long> misClientes = idsClientesDelAgente(idAgente, oportunidadesAgente, solicitudesAgente);
        List<RequerimientoCliente> requerimientosAgente = requerimientos.listarPorClientes(misClientes);

        // Fecha del ultimo reporte por captacion, en UNA consulta: el disparador y el
        // enriquecimiento del reporte periodico ya no consultan captacion por captacion.
        List<Long> idsCaptacion = new ArrayList<>();
        for (Captacion c : captacionesAgente) {
            if (c.getIdCaptacion() != null) {
                idsCaptacion.add(c.getIdCaptacion());
            }
        }
        Map<Long, LocalDate> ultimoReporte = reportes.ultimoReportePorCaptaciones(idsCaptacion);

        return new DatosAgente(prospeccionesAgente, solicitudesAgente, oportunidadesAgente,
                captacionesAgente, visitasAgente, contratosAgente, requerimientosAgente, ultimoReporte);
    }

    // Snapshot de las entidades del agente para construir su bandeja sin N+1: listas para derivar
    // y mapas por id para enriquecer.
    private static final class DatosAgente {
        final List<Prospeccion> prospecciones;
        final List<SolicitudAlquiler> solicitudes;
        final List<OportunidadComercial> oportunidades;
        final List<Captacion> captaciones;
        final List<Visita> visitas;
        final List<ContratoAlquiler> contratos;
        // Requerimientos de los clientes del agente (precargados): lista para el matching de cartera
        // e indice por id para enriquecer las tareas PROPONER_OPORTUNIDAD sin volver a la BD.
        final List<RequerimientoCliente> requerimientos;
        final Map<Long, RequerimientoCliente> requerimientoPorId;
        // Fecha del ultimo reporte por captacion (precargada): vencimiento del reporte periodico.
        final Map<Long, LocalDate> ultimoReportePorCaptacion;
        final Map<Long, Prospeccion> prospeccionPorId;
        final Map<Long, SolicitudAlquiler> solicitudPorId;
        final Map<Long, OportunidadComercial> oportunidadPorId;
        final Map<Long, Captacion> captacionPorId;
        final Map<Long, Visita> visitaPorId;
        final Map<Long, ContratoAlquiler> contratoPorId;

        DatosAgente(List<Prospeccion> prospecciones, List<SolicitudAlquiler> solicitudes,
                List<OportunidadComercial> oportunidades, List<Captacion> captaciones, List<Visita> visitas,
                List<ContratoAlquiler> contratos, List<RequerimientoCliente> requerimientos,
                Map<Long, LocalDate> ultimoReportePorCaptacion) {
            this.prospecciones = prospecciones;
            this.solicitudes = solicitudes;
            this.oportunidades = oportunidades;
            this.captaciones = captaciones;
            this.visitas = visitas;
            this.contratos = contratos;
            this.requerimientos = requerimientos;
            this.ultimoReportePorCaptacion = ultimoReportePorCaptacion;
            this.requerimientoPorId = indice(requerimientos, RequerimientoCliente::getIdRequerimiento);
            this.prospeccionPorId = indice(prospecciones, Prospeccion::getIdProspeccion);
            this.solicitudPorId = indice(solicitudes, SolicitudAlquiler::getIdSolicitud);
            this.oportunidadPorId = indice(oportunidades, OportunidadComercial::getIdOportunidad);
            this.captacionPorId = indice(captaciones, Captacion::getIdCaptacion);
            this.visitaPorId = indice(visitas, Visita::getIdVisita);
            this.contratoPorId = indice(contratos, ContratoAlquiler::getIdContratoAlquiler);
        }

        private static <T> Map<Long, T> indice(List<T> items, Function<T, Long> id) {
            Map<Long, T> mapa = new HashMap<>();
            for (T item : items) {
                Long clave = id.apply(item);
                if (clave != null) {
                    mapa.putIfAbsent(clave, item);
                }
            }
            return mapa;
        }
    }

    // Deriva las tareas que el estado actual del flujo justifica para el agente.
    private List<Tarea> derivarTareas(Long idAgente, DatosAgente datos) {
        List<Tarea> out = new ArrayList<>();

        // Listas base acotadas al agente (ya cargadas en bandejaDe): la bandeja es personal y no
        // debe depender del volumen global de solicitudes/oportunidades/visitas/requerimientos.
        List<SolicitudAlquiler> misSolicitudes = datos.solicitudes;
        List<OportunidadComercial> misOportunidades = datos.oportunidades;
        List<Captacion> misCaptaciones = datos.captaciones;

        // 1) Recontactos vencidos (>= 7 dias sin accion de seguimiento).
        LocalDate hoy = LocalDate.now();
        LocalDate limiteRecontacto = hoy.minusDays(DIAS_RECONTACTO);
        for (Prospeccion p : datos.prospecciones) {
            if (esDelAgente(p.getAgenteResponsable(), idAgente)
                    && p.getEstado() != null
                    && p.getEstado().enProceso()
                    && p.getFechaRecontacto() != null
                    && !p.getFechaRecontacto().isAfter(limiteRecontacto)) {
                out.add(construir(TipoTarea.RECONTACTO, TipoEntidad.PROSPECCION, p.getIdProspeccion(),
                        nz(p.getCodigoProspeccion()), idAgente, Prioridad.ALTA,
                        "Recontacta o evalua descartar la prospeccion " + nz(p.getCodigoProspeccion()) + "."));
            }
        }

        // 2) Solicitudes aprobadas sin cierre (estado APROBADA; el cierre la pasa a CERRADA).
        for (SolicitudAlquiler s : misSolicitudes) {
            if (s.getEstado() == EstadoSolicitudAlquiler.APROBADA) {
                out.add(construir(TipoTarea.SEGUIMIENTO, TipoEntidad.SOLICITUD_ALQUILER, s.getIdSolicitud(),
                        nz(s.getCodigoSolicitud()), idAgente, Prioridad.ALTA, "Solicitud aprobada pendiente de cierre."));
            }
        }

        // 3) Comisiones pendientes con monto del agente ya asignado (lista para cobro). Los
        // contratos del agente y sus liquidaciones se traen en bloque (sin buscar uno por uno).
        List<Long> idsContratoCerrados = new ArrayList<>();
        for (ContratoAlquiler contrato : datos.contratos) {
            if (contrato.getIdContratoAlquiler() != null) {
                idsContratoCerrados.add(contrato.getIdContratoAlquiler());
            }
        }
        if (!idsContratoCerrados.isEmpty()) {
            Set<Long> contratosConComisionLista = new java.util.LinkedHashSet<>();
            for (ComisionLiquidacion c : comisiones.listarPorContratos(idsContratoCerrados)) {
                Long idContrato = c.getContratoAlquiler() != null
                        ? c.getContratoAlquiler().getIdContratoAlquiler() : null;
                if (idContrato != null && c.getEstado() == EstadoComision.PENDIENTE && c.getMontoAgente() != null) {
                    contratosConComisionLista.add(idContrato);
                }
            }
            for (Long idContrato : contratosConComisionLista) {
                out.add(construir(TipoTarea.SEGUIMIENTO, TipoEntidad.CONTRATO_ALQUILER, idContrato,
                        null, idAgente, Prioridad.MEDIA, "Comision lista para cobro (contrato " + idContrato + ")."));
            }
        }

        // 4) Documentos observados: solicitudes que el broker devolvio OBSERVADA.
        for (SolicitudAlquiler s : misSolicitudes) {
            if (s.getEstado() == EstadoSolicitudAlquiler.OBSERVADA) {
                out.add(construir(TipoTarea.SUBIR_DOCUMENTOS, TipoEntidad.SOLICITUD_ALQUILER, s.getIdSolicitud(),
                        nz(s.getCodigoSolicitud()), idAgente, Prioridad.ALTA, "Documentos observados: subsana la solicitud."));
            }
        }

        // 5) Visitas que requieren accion: no realizadas (se cayeron), vencidas sin cerrar, o proximas.
        // REALIZADA/CANCELADA son terminales -> no disparan y se auto-resuelven (VISITA esta en ENTIDADES_AUTO).
        LocalDate limiteVisita = hoy.plusDays(DIAS_VISITA_PROXIMA);
        for (Visita v : datos.visitas) {
            LocalDate fecha = v.getFechaVisita();
            boolean pendiente = v.getEstado() == EstadoVisita.PROGRAMADA || v.getEstado() == EstadoVisita.REPROGRAMADA;
            if (v.getEstado() == EstadoVisita.NO_REALIZADA) {
                out.add(construir(TipoTarea.VISITA, TipoEntidad.VISITA, v.getIdVisita(), null, idAgente,
                        Prioridad.ALTA, "Visita no realizada: reprograma o descarta."));
            } else if (pendiente && fecha != null && !fecha.isAfter(limiteVisita)) {
                boolean vencida = fecha.isBefore(hoy);
                out.add(construir(TipoTarea.VISITA, TipoEntidad.VISITA, v.getIdVisita(), null, idAgente,
                        vencida ? Prioridad.ALTA : Prioridad.MEDIA,
                        vencida
                                ? "Visita del " + fecha + " sin cerrar: marca el resultado (¿se realizo?)."
                                : "Visita proxima (" + fecha + "): prepara, reprograma o cancela."));
            }
        }

        // 6) Reporte periodico al propietario: mientras la captacion siga ACTIVA y el ultimo
        // reporte (o el inicio de la captacion, si no hay ninguno) supere la cadencia DIAS_REPORTE.
        // Registrar un reporte reinicia el reloj y la tarea se auto-resuelve (CAPTACION esta en
        // ENTIDADES_AUTO), reapareciendo en el siguiente periodo.
        for (Captacion c : misCaptaciones) {
            if (c.getEstado() == EstadoCaptacion.ACTIVA && c.getIdCaptacion() != null && reporteVencido(c, datos)) {
                out.add(construir(TipoTarea.REPORTE_PROPIETARIO, TipoEntidad.CAPTACION, c.getIdCaptacion(),
                        nz(c.getCodigoCaptacion()), idAgente, Prioridad.MEDIA,
                        "Reporta avances al propietario de la captacion " + nz(c.getCodigoCaptacion()) + "."));
            }
        }

        // 7) Coincidencias de cartera (gancho Etapa 8): proponer oportunidad prellenada.
        derivarCoincidenciasCartera(idAgente, out, datos);

        return out;
    }

    // Etapa 8: por cada requerimiento ACTIVO de un cliente del agente, si existe una captacion
    // ACTIVA propia (local DISPONIBLE) con puntaje de coincidencia >= UMBRAL_PROPUESTA y aun sin
    // oportunidad para ese par, deriva una tarea PROPONER_OPORTUNIDAD. Dedup por REQUERIMIENTO:id;
    // su Resolver abre la ficha del cliente (panel de propiedades compatibles). REQUERIMIENTO esta
    // en ENTIDADES_AUTO: la tarea se auto-resuelve cuando el requerimiento deja de estar activo,
    // ya no hay captacion compatible, o ya se creo la oportunidad.
    private void derivarCoincidenciasCartera(Long idAgente, List<Tarea> out, DatosAgente datos) {
        List<Captacion> activas = datos.captaciones.stream()
                .filter(c -> c.getEstado() == EstadoCaptacion.ACTIVA
                        && c.getIdCaptacion() != null
                        && c.getLocalComercial() != null
                        && c.getLocalComercial().getEstado() == EstadoLocalComercial.DISPONIBLE)
                .toList();
        if (activas.isEmpty()) {
            return;
        }
        Set<Long> misClientes = idsClientesDelAgente(idAgente, datos.oportunidades, datos.solicitudes);
        if (misClientes.isEmpty()) {
            return;
        }
        // Pares (cliente, captacion) que ya tienen oportunidad: no se vuelven a proponer.
        Set<String> yaPropuesto = new HashSet<>();
        for (OportunidadComercial o : datos.oportunidades) {
            Long cli = o.getClienteInteresado() != null ? o.getClienteInteresado().getIdCliente() : null;
            Long cap = o.getCaptacion() != null ? o.getCaptacion().getIdCaptacion() : null;
            if (cli != null && cap != null) {
                yaPropuesto.add(cli + "#" + cap);
            }
        }
        // Requerimientos ya precargados en bloque (datos.requerimientos = los de los clientes del
        // agente, con distritos): el matching trabaja en memoria, sin una consulta por cliente.
        for (RequerimientoCliente r : datos.requerimientos) {
            if (r.getEstado() != EstadoRequerimiento.ACTIVO || r.getCliente() == null) {
                continue;
            }
            Long idCliente = r.getCliente().getIdCliente();
            if (idCliente == null || !misClientes.contains(idCliente)) {
                continue;
            }
            Captacion mejor = null;
            int mejorPuntaje = 0;
            for (Captacion c : activas) {
                if (yaPropuesto.contains(idCliente + "#" + c.getIdCaptacion())) {
                    continue;
                }
                int puntaje = CoincidenciaCartera.evaluar(r, c.getLocalComercial()).puntaje();
                if (puntaje >= UMBRAL_PROPUESTA && puntaje > mejorPuntaje) {
                    mejor = c;
                    mejorPuntaje = puntaje;
                }
            }
            if (mejor != null) {
                out.add(construir(TipoTarea.PROPONER_OPORTUNIDAD, TipoEntidad.REQUERIMIENTO,
                        r.getIdRequerimiento(), "REQ-" + r.getIdRequerimiento(), idAgente, Prioridad.MEDIA,
                        "Coincidencia de cartera (" + mejorPuntaje + "%): propon "
                                + nz(mejor.getCodigoCaptacion()) + " a un cliente interesado."));
            }
        }
    }

    // Clientes "del agente": los vinculados por sus propias oportunidades o solicitudes.
    private Set<Long> idsClientesDelAgente(Long idAgente,
            List<OportunidadComercial> todasOportunidades, List<SolicitudAlquiler> todasSolicitudes) {
        Set<Long> ids = new HashSet<>();
        for (OportunidadComercial o : todasOportunidades) {
            if (esDelAgente(o.getAgenteResponsable(), idAgente)
                    && o.getClienteInteresado() != null && o.getClienteInteresado().getIdCliente() != null) {
                ids.add(o.getClienteInteresado().getIdCliente());
            }
        }
        for (SolicitudAlquiler s : todasSolicitudes) {
            if (esDelAgente(s.getAgenteResponsable(), idAgente)
                    && s.getClienteInteresado() != null && s.getClienteInteresado().getIdCliente() != null) {
                ids.add(s.getClienteInteresado().getIdCliente());
            }
        }
        return ids;
    }

    private static Tarea construir(TipoTarea tipo, TipoEntidad entidadTipo, Long entidadId,
            String entidadCodigo, Long idAgente, Prioridad prioridad, String descripcion) {
        Tarea tarea = new Tarea();
        tarea.setTipo(tipo);
        tarea.setEntidadTipo(entidadTipo);
        tarea.setEntidadId(entidadId);
        tarea.setEntidadCodigo(entidadCodigo);
        tarea.setRutaResolver(rutaPara(entidadTipo, entidadId, entidadCodigo));
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(idAgente);
        tarea.setAgente(agente);
        tarea.setDescripcion(descripcion);
        tarea.setFechaProgramada(LocalDateTime.now());
        tarea.setEstado(EstadoTarea.PENDIENTE);
        tarea.setPrioridad(prioridad);
        return tarea;
    }

    // Ruta exacta de "Resolver": lleva directo al item que disparo la tarea. Las pantallas de
    // detalle enrutan por CODIGO (solicitud, captacion) o por id (prospeccion); las visitas se
    // gestionan inline en su lista (deep-link ?focus); contrato/comision viven en su lista.
    private static String rutaPara(TipoEntidad tipo, Long id, String codigo) {
        boolean conCodigo = codigo != null && !codigo.isBlank();
        return switch (tipo) {
            case PROSPECCION -> "prospeccion-detail/" + id;
            case SOLICITUD_ALQUILER -> "solicitud-detail/" + (conCodigo ? codigo : id);
            case CAPTACION -> conCodigo ? "captacion-detail/" + codigo : "captaciones";
            case CONTRATO_ALQUILER -> "comisiones";
            case VISITA -> "visitas?focus=" + id;
            case CLIENTE_INTERESADO -> "cliente-detail/" + id;
            case PROPIETARIO -> "owner-detail/" + id;
            case OPORTUNIDAD -> "oportunidad-detail/" + id;
            default -> "dashboard";
        };
    }

    private Tarea enriquecer(Tarea tarea, DatosAgente datos) {
        LocalDate hoy = LocalDate.now();
        // PROPONER_OPORTUNIDAD (entidad REQUERIMIENTO): el Resolver abre la ficha del cliente,
        // donde vive el panel de propiedades de cartera compatibles para proponer. Sin vencimiento.
        if (tarea.getEntidadTipo() == TipoEntidad.REQUERIMIENTO) {
            if (tarea.getEntidadCodigo() == null || tarea.getEntidadCodigo().isBlank()) {
                tarea.setEntidadCodigo("REQ-" + tarea.getEntidadId());
            }
            Long idCliente = idClienteDeRequerimiento(tarea.getEntidadId(), datos);
            tarea.setRutaResolver(idCliente != null ? "cliente-detail/" + idCliente : "dashboard");
            tarea.setDiasSinAccion(diasDesde(baseProgramada(tarea), hoy));
            return tarea;
        }
        enriquecerEntidad(tarea, datos, hoy);
        return tarea;
    }

    // Con UNA lectura de la entidad de origen resuelve: el código (para la ruta de Resolver),
    // la fecha de vencimiento (cuando la entidad impone un plazo) y la fecha base de "días sin
    // acción" (la del plazo real de la entidad, no la fecha en que se creó la tarea).
    private void enriquecerEntidad(Tarea tarea, DatosAgente datos, LocalDate hoy) {
        Long id = tarea.getEntidadId();
        String codigo = "";
        LocalDate vencimiento = null;
        LocalDate base = baseProgramada(tarea);
        if (tarea.getEntidadTipo() != null && id != null) {
            try {
                switch (tarea.getEntidadTipo()) {
                    case PROSPECCION -> {
                        Prospeccion p = datos.prospeccionPorId.get(id);
                        if (p != null) {
                            codigo = nz(p.getCodigoProspeccion());
                            if (p.getFechaRecontacto() != null) {
                                vencimiento = p.getFechaRecontacto();
                                base = p.getFechaRecontacto();
                            }
                        }
                    }
                    case SOLICITUD_ALQUILER -> {
                        SolicitudAlquiler s = datos.solicitudPorId.get(id);
                        if (s != null) {
                            codigo = nz(s.getCodigoSolicitud());
                            if (s.getFechaActualizacionEstado() != null) {
                                base = s.getFechaActualizacionEstado().toLocalDate();
                            }
                            if (s.getFechaVigenciaOferta() != null) {
                                vencimiento = s.getFechaVigenciaOferta();
                            }
                        }
                    }
                    case CAPTACION -> {
                        Captacion c = datos.captacionPorId.get(id);
                        if (c != null) {
                            codigo = nz(c.getCodigoCaptacion());
                            // Reporte periódico al propietario: vence cuando toca el siguiente.
                            vencimiento = vencimientoReporte(c, base, datos);
                        }
                    }
                    case VISITA -> {
                        Visita v = datos.visitaPorId.get(id);
                        if (v != null) {
                            codigo = v.getOportunidadComercial() != null
                                    ? nz(v.getOportunidadComercial().getCodigoOportunidad())
                                    : "VIS-" + id;
                            if (v.getFechaVisita() != null) {
                                vencimiento = v.getFechaVisita();
                                if (v.getFechaVisita().isBefore(base)) {
                                    base = v.getFechaVisita();
                                }
                            }
                        }
                    }
                    case CONTRATO_ALQUILER -> {
                        ContratoAlquiler c = datos.contratoPorId.get(id);
                        codigo = c != null && c.getSolicitudAlquiler() != null
                                ? "SOL-" + c.getSolicitudAlquiler().getIdSolicitud()
                                : "CON-" + id;
                    }
                    case OPORTUNIDAD -> {
                        OportunidadComercial o = datos.oportunidadPorId.get(id);
                        codigo = o != null ? nz(o.getCodigoOportunidad()) : "";
                    }
                    default -> { }
                }
            } catch (RuntimeException ignored) {
                // best-effort: el enriquecimiento no debe tumbar la bandeja.
            }
        }
        if (tarea.getEntidadCodigo() == null || tarea.getEntidadCodigo().isBlank()) {
            tarea.setEntidadCodigo(nz(codigo));
        }
        tarea.setRutaResolver(rutaPara(tarea.getEntidadTipo(), id, tarea.getEntidadCodigo()));
        tarea.setFechaVencimiento(vencimiento);
        tarea.setDiasSinAccion(diasDesde(base, hoy));
    }

    // Próximo reporte al propietario: un periodo (DIAS_REPORTE) después del último reporte de la
    // captación, o después de iniciar la captación si aún no hay ninguno. El último reporte por
    // captación viene precargado en bloque (datos.ultimoReportePorCaptacion), sin consultar la BD.
    private LocalDate vencimientoReporte(Captacion captacion, LocalDate base, DatosAgente datos) {
        LocalDate ultimo = captacion.getIdCaptacion() != null
                ? datos.ultimoReportePorCaptacion.get(captacion.getIdCaptacion())
                : null;
        if (ultimo == null) {
            ultimo = captacion.getFechaCaptacion() != null ? captacion.getFechaCaptacion() : base;
        }
        return ultimo.plusDays(DIAS_REPORTE);
    }

    // Hay reporte pendiente cuando el vencimiento del periodo ya llego (o paso) respecto a hoy.
    private boolean reporteVencido(Captacion captacion, DatosAgente datos) {
        LocalDate hoy = LocalDate.now();
        LocalDate vencimiento = vencimientoReporte(captacion, hoy, datos);
        return vencimiento == null || !vencimiento.isAfter(hoy);
    }

    // Cliente del requerimiento (para la ruta "Resolver" de PROPONER_OPORTUNIDAD). Lee del bloque
    // precargado; solo si la tarea apunta a un requerimiento fuera de la cartera actual del agente
    // (caso raro de tarea EN_PROCESO heredada) cae a una única lectura puntual.
    private Long idClienteDeRequerimiento(Long idRequerimiento, DatosAgente datos) {
        RequerimientoCliente r = datos.requerimientoPorId.get(idRequerimiento);
        if (r == null && idRequerimiento != null) {
            r = requerimientos.buscarPorId(idRequerimiento).orElse(null);
        }
        return r != null && r.getCliente() != null ? r.getCliente().getIdCliente() : null;
    }

    private static LocalDate baseProgramada(Tarea tarea) {
        return tarea.getFechaProgramada() != null ? tarea.getFechaProgramada().toLocalDate() : LocalDate.now();
    }

    private static int diasDesde(LocalDate base, LocalDate hoy) {
        if (base == null) {
            return 0;
        }
        long dias = ChronoUnit.DAYS.between(base, hoy);
        return dias < 0 ? 0 : (int) dias;
    }

    private static boolean esDelAgente(AgenteInmobiliario agente, Long idAgente) {
        return agente != null && idAgente != null && idAgente.equals(agente.getIdAgente());
    }

    private static String clave(TipoEntidad entidadTipo, Long entidadId) {
        return (entidadTipo == null ? "?" : entidadTipo.name()) + ":" + entidadId;
    }

    private static String nz(String texto) {
        return texto == null ? "" : texto;
    }
}
