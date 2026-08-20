package com.controllocal.service.impl;

import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comercial.Tarea;
import com.controllocal.domain.comercial.Visita;
import com.controllocal.persistence.query.CandidatoTarea;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.ReportePropietarioRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.TareaRepository;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.TareaService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.InterpreteDeLaBandeja;
import com.controllocal.service.soporte.LadoDeLaOperacion;
import com.controllocal.service.soporte.NaturalezaDelAsunto;
import com.controllocal.service.soporte.PoliticaComercial;
import com.controllocal.service.soporte.PoliticaDeDespacho;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * El motor de la bandeja: deriva, reconcilia, enriquece, ordena y corta.
 *
 * <p><b>Que cambia respecto de la v1 y que no.</b> El conjunto de tareas es el
 * mismo, disparador por disparador. Lo que cambia es como se llega a el: la v1
 * cargaba en memoria TODAS las prospecciones, solicitudes, oportunidades,
 * captaciones, visitas y contratos del agente y filtraba en Java; aqui cada
 * disparador pregunta por lo suyo y su condicion baja al WHERE (MEJ-05 /
 * RC-003). El enriquecimiento ya no necesita el mapa-por-id de la v1 porque la
 * consulta trae el codigo y la fecha de plazo consigo.
 *
 * <p>Desde E2.3 <b>todos</b> los disparadores bajan su condicion al WHERE. El
 * septimo -- coincidencias de cartera, que si trabajaba en memoria porque el
 * puntaje no se puede expresar en SQL sin duplicar la regla -- se fue entero a
 * {@code HallazgoService}: una coincidencia no es una tarea.
 */
@Service
public class TareaServiceImpl implements TareaService {

    // Los cuatro plazos que gobiernan los disparadores viven en
    // PoliticaComercial, no aqui: hasta E1 este archivo tenia su propia copia
    // del plazo de recontacto y otras tres andaban sueltas por el sistema.
    //
    // El tope de 10 con descarte EN SILENCIO (D-F7-2) se retiro el 2026-08-08 al
    // descongelar el contrato. Era una replica de la v1 y hacia que el agente no
    // pudiera distinguir "tengo 10 tareas" de "tengo 40": la bandeja se veia
    // igual en los dos casos, y las 30 que faltaban no aparecian en ningun
    // sitio ni dejaban rastro. El orden por prioridad ya estaba; lo que faltaba
    // era no mentir sobre el total.

    /**
     * Las unicas {@code entidad_tipo} que se auto-resuelven al reconciliar. Una
     * tarea sobre cualquier otra entidad no se cierra sola nunca (§5.2,
     * trampa 2).
     */
    private static final Set<String> ENTIDADES_AUTO = Set.of(
            Tarea.ENTIDAD_PROSPECCION, "SOLICITUD_ALQUILER", "CONTRATO_ALQUILER",
            "VISITA", "CAPTACION", "REQUERIMIENTO");

    /**
     * Anade a cada asunto su interpretacion: como esta, su expediente y la
     * lectura que lo sintetiza (E2.4).
     *
     * <p>Va aparte del mapeo porque necesita el contexto de TODA la pagina, y
     * ese contexto se carga una vez.
     */
    private List<FichaTarea> interpretadas(long org, List<FichaTarea> asuntos, LocalDate hoy) {
        if (asuntos.isEmpty()) {
            return asuntos;
        }
        List<InterpreteDeLaBandeja.AsuntoADescribir> aDescribir = asuntos.stream()
                .map(TareaServiceImpl::aDescribir)
                .toList();
        InterpreteDeLaBandeja.Contexto contexto = interprete.contextoDe(org, aDescribir);

        List<FichaTarea> conLectura = new java.util.ArrayList<>(asuntos.size());
        for (int i = 0; i < asuntos.size(); i++) {
            FichaTarea t = asuntos.get(i);
            conLectura.add(new FichaTarea(t.id(), t.tipo(), t.entidadTipo(), t.entidadId(),
                    t.entidadCodigo(), t.rutaResolver(), t.descripcion(), t.estado(),
                    t.prioridad(), t.fechaProgramada(), t.diasSinAccion(), t.fechaVencimiento(),
                    t.dependeDeMi(), t.lado(), t.paso(),
                    interprete.de(aDescribir.get(i), contexto, hoy)));
        }
        return List.copyOf(conLectura);
    }

    private static InterpreteDeLaBandeja.AsuntoADescribir aDescribir(FichaTarea t) {
        return new InterpreteDeLaBandeja.AsuntoADescribir(t.tipo(), t.entidadTipo(),
                t.entidadId(), t.descripcion(), t.diasSinAccion(), t.fechaVencimiento(),
                t.dependeDeMi());
    }

    /**
     * <b>El orden ya no se decide aqui.</b>
     *
     * <p>Aqui vivia `ALTA antes que MEDIA, y a igual prioridad lo mas rezagado
     * primero`: dos criterios, mientras la pantalla aplicaba los suyos y el
     * diseno declaraba seis. Tres opiniones sobre lo mismo.
     *
     * <p>Ahora la unica es {@link PoliticaDeDespacho}, y este servicio solo le
     * dice los HECHOS de cada asunto.
     */
    private static PoliticaDeDespacho.Asunto comoAsunto(FichaTarea t, LocalDate hoy) {
        return new PoliticaDeDespacho.Asunto(
                t.id() == null ? 0L : t.id(),
                t.dependeDeMi(),
                PoliticaDeDespacho.margenHasta(t.fechaVencimiento(), hoy),
                NaturalezaDelAsunto.esOcasion(t.tipo()),
                NaturalezaDelAsunto.desbloquea(t.tipo()),
                t.diasSinAccion() == null ? 0 : t.diasSinAccion());
    }

    private final TareaRepository tareas;
    private final ProspeccionRepository prospecciones;
    private final SolicitudAlquilerRepository solicitudes;
    private final VisitaRepository visitas;
    private final CaptacionRepository captaciones;
    private final ContratoAlquilerRepository contratos;
    private final ReportePropietarioRepository reportes;
    private final DetalleAgenteRepository agentes;
    private final InterpreteDeLaBandeja interprete;

    public TareaServiceImpl(TareaRepository tareas, ProspeccionRepository prospecciones,
                            SolicitudAlquilerRepository solicitudes, VisitaRepository visitas,
                            CaptacionRepository captaciones, ContratoAlquilerRepository contratos,
                            ReportePropietarioRepository reportes,
                            DetalleAgenteRepository agentes,
                            InterpreteDeLaBandeja interprete) {
        this.tareas = tareas;
        this.prospecciones = prospecciones;
        this.solicitudes = solicitudes;
        this.visitas = visitas;
        this.captaciones = captaciones;
        this.contratos = contratos;
        this.reportes = reportes;
        this.agentes = agentes;
        this.interprete = interprete;
    }

    @Override
    @Transactional
    public List<FichaTarea> bandejaDe(Actor actor) {
        if (!actor.esAgente()) {
            // El gate de rol lo pone el controlador; esto protege el service si
            // alguien lo llama desde otro sitio.
            throw new AccesoNoAutorizadoException();
        }
        long org = actor.idOrganizacion();
        long idAgente = actor.idRolOperativo();

        List<Derivada> vigentes = derivar(org, idAgente);
        Set<String> clavesVigentes = new HashSet<>();
        for (Derivada d : vigentes) {
            clavesVigentes.add(Tarea.claveEntidad(d.entidadTipo(), d.entidadId()));
        }

        List<Tarea> existentes = tareas.porAgente(org, idAgente);
        // Lo que bloquea la creacion NO es solo lo abierto: una CANCELADA
        // tambien bloquea, y para siempre (§5.2, trampa 1).
        Set<String> bloqueadas = new HashSet<>();
        Map<String, Derivada> derivadaPorClave = new HashMap<>();
        for (Derivada d : vigentes) {
            derivadaPorClave.putIfAbsent(Tarea.claveEntidad(d.entidadTipo(), d.entidadId()), d);
        }
        for (Tarea t : existentes) {
            if (t.estaAbierta() || Tarea.CANCELADA.equals(t.getEstado())) {
                bloqueadas.add(t.claveEntidad());
            }
        }

        for (Derivada d : vigentes) {
            if (!bloqueadas.contains(Tarea.claveEntidad(d.entidadTipo(), d.entidadId()))) {
                tareas.save(nueva(d, org, idAgente));
            }
        }
        for (Tarea t : existentes) {
            if (Tarea.PENDIENTE.equals(t.getEstado())
                    && ENTIDADES_AUTO.contains(t.getEntidadTipo())
                    && !clavesVigentes.contains(t.claveEntidad())) {
                t.completar();
                tareas.save(t);
            }
        }

        LocalDate hoy = LocalDate.now();
        List<FichaTarea> abiertas = tareas.porAgente(org, idAgente).stream()
                .filter(Tarea::estaAbierta)
                .map(t -> ficha(t, derivadaPorClave.get(t.claveEntidad()), hoy))
                .toList();

        // La interpretacion se compone DESPUES y por lote: tres consultas para
        // toda la pagina. Hacerlo dentro del `map` de arriba serian tres por
        // fila, que es el N+1 que vuelve cada vez que se anade una capa.
        abiertas = interpretadas(org, abiertas, hoy);

        // El orden lo decide la politica, y solo ella. Sin orden previo en esta
        // llamada el criterio 6 desempata por id, que es determinista: dos
        // peticiones seguidas con los mismos datos devuelven lo mismo.
        return PoliticaDeDespacho.despachar(abiertas, t -> comoAsunto(t, hoy), List.of());
    }

    @Override
    @Transactional
    public void cancelar(long id, Actor actor) {
        Tarea tarea = tareas.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new ReglaNegocioException("Tarea no encontrada."));
        if (tarea.getAgente() == null || tarea.getAgente().getId() != actor.idRolOperativo()) {
            throw new ReglaNegocioException("La tarea no pertenece al agente.");
        }
        tarea.cancelar();
        tareas.save(tarea);
    }

    @Override
    @Transactional
    public void resolverDeEntidad(String entidadTipo, Long entidadId, Actor actor) {
        if (entidadTipo == null || entidadId == null || entidadId <= 0) {
            return;
        }
        for (Tarea t : tareas.abiertasDeEntidad(actor.idOrganizacion(), entidadTipo, entidadId)) {
            t.completar();
            tareas.save(t);
        }
    }

    @Override
    @Transactional
    public int resolverDeContratoOrigen(long idContrato, Actor actor) {
        int cerradas = 0;
        for (Tarea t : tareas.abiertasDeContratoOrigen(actor.idOrganizacion(), idContrato)) {
            t.completar();
            tareas.save(t);
            cerradas++;
        }
        return cerradas;
    }

    @Override
    @Transactional
    public void crearRevisionInmueble(Long idPropiedad, Long idAgente, String motivo,
                                      Long idContratoOrigen, Actor actor) {
        if (idPropiedad == null || idAgente == null) return;
        // INMUEBLE, no "PROPIEDAD": es el vocabulario que declara
        // `ck_tarea_tipo_entidad`. Con la cadena equivocada esta tarea no se
        // podia insertar, asi que finalizar y rescindir un contrato fallaban
        // SIEMPRE con 409 — y la deduplicacion de arriba buscaba un valor que
        // no puede existir, asi que tampoco deduplicaba.
        // La deduplicacion es POR CONTRATO cuando hay contrato origen: dos
        // contratos sucesivos del mismo local merecen cada uno su revision, y
        // deduplicar por inmueble dejaria al segundo sin tarea que resolver.
        boolean yaExiste = idContratoOrigen != null
                ? !tareas.abiertasDeContratoOrigen(actor.idOrganizacion(), idContratoOrigen).isEmpty()
                : !tareas.abiertasDeEntidad(actor.idOrganizacion(),
                        Tarea.ENTIDAD_INMUEBLE, idPropiedad).isEmpty();
        if (yaExiste) return;
        Derivada derivada = new Derivada(Tarea.REVISION_INMUEBLE, Tarea.ENTIDAD_INMUEBLE, idPropiedad,
                "", Tarea.ALTA,
                "Revisar entrega, estado fisico y decision del propietario antes de reactivar el local."
                        + (motivo == null || motivo.isBlank() ? "" : " " + motivo.trim()),
                null, LocalDate.now(), "locales/" + idPropiedad);
        Tarea tarea = nueva(derivada, actor.idOrganizacion(), idAgente);
        tarea.setIdContratoOrigen(idContratoOrigen);
        tareas.save(tarea);
    }

    // ------------------------------------------------------------------
    // Derivacion: que tareas justifica hoy el estado del flujo (§5.1)
    // ------------------------------------------------------------------

    /**
     * Una tarea que el estado del flujo justifica ahora mismo. Lleva ya
     * resueltos el codigo de la entidad, su plazo y la base de "dias sin
     * accion", porque los trajo la propia consulta del disparador.
     */
    private record Derivada(String tipo, String entidadTipo, Long entidadId, String entidadCodigo,
                            String prioridad, String descripcion, LocalDate fechaVencimiento,
                            LocalDate fechaBase, String rutaExplicita) {

        /** Para los seis disparadores cuya ruta sale del tipo de entidad. */
        Derivada(String tipo, String entidadTipo, Long entidadId, String entidadCodigo,
                 String prioridad, String descripcion, LocalDate fechaVencimiento, LocalDate fechaBase) {
            this(tipo, entidadTipo, entidadId, entidadCodigo, prioridad, descripcion,
                    fechaVencimiento, fechaBase, null);
        }
    }

    private List<Derivada> derivar(long org, long idAgente) {
        List<Derivada> out = new ArrayList<>();
        LocalDate hoy = LocalDate.now();

        // 1) Recontactos vencidos: prospecciones que ya pasaron su plazo.
        for (CandidatoTarea c : prospecciones.paraRecontactar(
                org, idAgente, PoliticaComercial.limiteDeRecontacto(hoy))) {
            String codigo = nz(c.getEntidadCodigo());
            out.add(new Derivada(Tarea.RECONTACTO, Tarea.ENTIDAD_PROSPECCION, c.getEntidadId(), codigo,
                    Tarea.ALTA, "Recontacta o evalua descartar la prospeccion " + codigo + ".",
                    c.getFechaPlazo(), c.getFechaPlazo()));
        }

        // 2) Solicitudes aprobadas sin cierre (el cierre las pasa a CERRADA).
        for (CandidatoTarea c : solicitudes.porEstadoDelAgente(org, idAgente, SolicitudAlquiler.APROBADA)) {
            out.add(new Derivada(Tarea.SEGUIMIENTO, "SOLICITUD_ALQUILER", c.getEntidadId(),
                    nz(c.getEntidadCodigo()), Tarea.ALTA, "Solicitud aprobada pendiente de cierre.",
                    c.getFechaPlazo(), fechaDeMarca(c.getMarca())));
        }

        // 3) Comisiones PENDIENTES con monto del agente ya asignado: listas para cobro.
        for (CandidatoTarea c : contratos.conComisionListaParaCobro(org, idAgente)) {
            out.add(new Derivada(Tarea.SEGUIMIENTO, "CONTRATO_ALQUILER", c.getEntidadId(),
                    nz(c.getEntidadCodigo()), Tarea.MEDIA,
                    "Comision lista para cobro (contrato " + c.getEntidadId() + ").",
                    null, c.getFechaPlazo()));
        }

        // 4) Documentos observados: el broker devolvio la solicitud OBSERVADA.
        for (CandidatoTarea c : solicitudes.porEstadoDelAgente(org, idAgente, SolicitudAlquiler.OBSERVADA)) {
            out.add(new Derivada(Tarea.SUBIR_DOCUMENTOS, "SOLICITUD_ALQUILER", c.getEntidadId(),
                    nz(c.getEntidadCodigo()), Tarea.ALTA, "Documentos observados: subsana la solicitud.",
                    c.getFechaPlazo(), fechaDeMarca(c.getMarca())));
        }

        // 5) Visitas que requieren accion: caidas, vencidas sin cerrar o proximas.
        for (CandidatoTarea c : visitas.queExigenAccion(
                org, idAgente, PoliticaComercial.horizonteDeVisitas(hoy))) {
            LocalDate fecha = c.getFechaPlazo();
            boolean noRealizada = Visita.NO_REALIZADA.equals(c.getMarca());
            boolean vencida = fecha != null && fecha.isBefore(hoy);
            String descripcion;
            if (noRealizada) {
                descripcion = "Visita no realizada: reprograma o descarta.";
            } else if (vencida) {
                descripcion = "Visita del " + fecha + " sin cerrar: marca el resultado (¿se realizo?).";
            } else {
                descripcion = "Visita proxima (" + fecha + "): prepara, reprograma o cancela.";
            }
            out.add(new Derivada(Tarea.VISITA, "VISITA", c.getEntidadId(), nz(c.getEntidadCodigo()),
                    noRealizada || vencida ? Tarea.ALTA : Tarea.MEDIA, descripcion, fecha, fecha));
        }

        // 6) Reporte periodico al propietario de las captaciones ACTIVAS.
        derivarReportes(org, idAgente, hoy, out);

        // El septimo disparador se fue en E2.3: una coincidencia de cartera NO
        // es una tarea. Vive en `HallazgoService`, que usa exactamente la misma
        // evaluacion de `CoincidenciaCartera` y la publica en `hallazgos[]`.
        //
        // Mientras estuvo aqui competia por los cinco puestos del foco -- y los
        // ganaba, porque la politica la trata como ocasion, que lo es. El agente
        // abria su Inicio y encontraba sugerencias por encima de lo que de
        // verdad le reclamaba algo. `PoliticaDeOrdenUnicaTest` y
        // `HallazgoFueraDelFocoTest` impiden que vuelva.

        return out;
    }

    /**
     * Disparador 6. El reloj arranca en el ultimo reporte de la captacion o, si
     * no hay ninguno, en su fecha de captacion; vencido el periodo, la tarea
     * vuelve a pedirse. Registrar un reporte lo reinicia y la tarea se
     * auto-resuelve (CAPTACION esta en ENTIDADES_AUTO).
     */
    private void derivarReportes(long org, long idAgente, LocalDate hoy, List<Derivada> out) {
        List<CandidatoTarea> activas = captaciones.activasDelAgente(org, idAgente);
        if (activas.isEmpty()) {
            return;
        }
        List<Long> ids = activas.stream().map(CandidatoTarea::getEntidadId).toList();
        Map<Long, LocalDate> ultimoReporte = new HashMap<>();
        for (Object[] fila : reportes.ultimoPorCaptaciones(org, ids)) {
            ultimoReporte.put((Long) fila[0], (LocalDate) fila[1]);
        }
        for (CandidatoTarea c : activas) {
            LocalDate desde = ultimoReporte.get(c.getEntidadId());
            if (desde == null) {
                desde = c.getFechaPlazo() != null ? c.getFechaPlazo() : hoy;
            }
            LocalDate vence = PoliticaComercial.proximoReporteAlPropietario(desde);
            if (vence.isAfter(hoy)) {
                continue;
            }
            String codigo = nz(c.getEntidadCodigo());
            out.add(new Derivada(Tarea.REPORTE_PROPIETARIO, "CAPTACION", c.getEntidadId(), codigo,
                    Tarea.MEDIA,
                    "Reporta avances al propietario de la captacion " + codigo + ".",
                    vence, desde));
        }
    }

    // ------------------------------------------------------------------
    // Persistencia y lectura
    // ------------------------------------------------------------------

    private Tarea nueva(Derivada d, long org, long idAgente) {
        Tarea tarea = new Tarea();
        tarea.setOrganizacionId(org);
        tarea.setTipo(d.tipo());
        tarea.setEntidadTipo(d.entidadTipo());
        tarea.setEntidadId(d.entidadId());
        tarea.setAgente(agentes.getReferenceById(idAgente));
        tarea.setDescripcion(d.descripcion());
        tarea.setFechaProgramada(OffsetDateTime.now());
        tarea.nacer(d.prioridad());
        return tarea;
    }

    /**
     * Enriquecimiento (§5.3). Los cuatro derivados salen de la {@code Derivada}
     * que la justifica hoy; si una tarea abierta ya no tiene disparador —caso
     * de las entidades que no se auto-resuelven— se cae a lo que hay en la
     * fila, que es exactamente lo que hacia el {@code try/catch} best-effort de
     * la v1.
     *
     * <p><b>El detalle que mas facil se porta mal</b>: {@code diasSinAccion} se
     * cuenta desde el plazo REAL de la entidad, no desde que se creo la tarea.
     * Con la fecha de la tarea daria casi siempre 0.
     */
    private static FichaTarea ficha(Tarea t, Derivada d, LocalDate hoy) {
        String codigo = d != null ? d.entidadCodigo() : "";
        LocalDate base = d != null && d.fechaBase() != null
                ? d.fechaBase()
                : t.getFechaProgramada().toLocalDate();
        String ruta = d != null && d.rutaExplicita() != null
                ? d.rutaExplicita()
                : ruta(t.getEntidadTipo(), t.getEntidadId(), codigo);
        // Los tres derivados de E2.2. Salen del tipo del asunto y del tipo de su
        // entidad: ninguno es columna, y por eso una tarea vieja los responde
        // igual que una recien creada.
        LadoDeLaOperacion.Ubicacion ubicacion = LadoDeLaOperacion.de(t.getEntidadTipo());
        boolean dependeDeMi = NaturalezaDelAsunto.dependeDelAgente(t.getTipo(), t.getEntidadTipo());

        return new FichaTarea(t.getId(), t.getTipo(), t.getEntidadTipo(), t.getEntidadId(),
                codigo, ruta, t.getDescripcion(),
                t.getEstado(), t.getPrioridad(), t.getFechaProgramada(), dias(base, hoy),
                d != null ? d.fechaVencimiento() : null,
                dependeDeMi,
                ubicacion == null ? null : ubicacion.lado().name(),
                ubicacion == null ? null : ubicacion.paso(),
                null);
    }

    /**
     * Ruta exacta de "Resolver". Calcada de la v1: las pantallas de detalle
     * enrutan por CODIGO (solicitud, captacion) o por id (prospeccion), las
     * visitas por deep-link y contrato/comision caen en su lista.
     * {@code REQUERIMIENTO} abre la ficha del CLIENTE, no el requerimiento.
     */
    private static String ruta(String entidadTipo, Long id, String codigo) {
        boolean conCodigo = codigo != null && !codigo.isBlank();
        return switch (entidadTipo) {
            case Tarea.ENTIDAD_PROSPECCION -> "prospeccion-detail/" + id;
            case "SOLICITUD_ALQUILER" -> "solicitud-detail/" + (conCodigo ? codigo : id);
            case "CAPTACION" -> conCodigo ? "captacion-detail/" + codigo : "captaciones";
            case "CONTRATO_ALQUILER" -> "comisiones";
            case "VISITA" -> "visitas?focus=" + id;
            case "CLIENTE_INTERESADO" -> "cliente-detail/" + id;
            case "PROPIETARIO" -> "owner-detail/" + id;
            case "OPORTUNIDAD" -> "oportunidad-detail/" + id;
            // REQUERIMIENTO incluido: su Resolver es la ficha del cliente, que
            // se resuelve fuera de aqui; sin cliente, el dashboard.
            default -> "dashboard";
        };
    }

    /** La marca de las solicitudes es su {@code fechaActualizacionEstado} como texto ISO. */
    private static LocalDate fechaDeMarca(String marca) {
        if (marca == null || marca.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(marca.substring(0, 10));
        } catch (RuntimeException ignorada) {
            return null;
        }
    }

    private static int dias(LocalDate base, LocalDate hoy) {
        if (base == null) {
            return 0;
        }
        long dias = ChronoUnit.DAYS.between(base, hoy);
        return dias < 0 ? 0 : (int) dias;
    }

    private static String nz(String texto) {
        return texto == null ? "" : texto;
    }
}
