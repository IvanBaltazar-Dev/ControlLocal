package com.controllocal.service.actuacion;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.InteraccionService;
import com.controllocal.service.InteraccionService.DatosInteraccion;
import com.controllocal.service.InteraccionService.FichaInteraccion;
import com.controllocal.service.VisitaService;
import com.controllocal.service.VisitaService.FichaVisita;
import com.controllocal.service.ProspeccionService;
import com.controllocal.service.ProspeccionService.RevisionSeguimiento;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.PropietarioService;
import com.controllocal.service.PropietarioService.DatosPropietario;
import com.controllocal.service.PropietarioService.FichaPropietario;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.captura.MotorDeCaptura.EstadoCaptura;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * UC-K-07 / CAT-E36/E40/E41: durabilidad de las escrituras AUTO ya habilitadas.
 * Delega los hechos al caso canónico; identidad, resultado original y procedencia
 * se confirman en su misma transacción. No prepara una aprobación humana.
 */
@Service
public class ComandosAutomaticosService {
    public static final int VERSION = 1;
    public static final String REGISTRAR_PROPIETARIO = "registrar_propietario";
    public static final String REGISTRAR_INTERACCION = "registrar_interaccion";
    public static final String VINCULAR_INTERACCION = "vincular_interaccion";
    public static final String OBSERVAR_INTERACCION = "anotar_observacion_interaccion";
    public static final String PROGRAMAR_VISITA = "programar_visita";
    public static final String REPROGRAMAR_VISITA = "reprogramar_visita";
    public static final String RESULTADO_VISITA = "registrar_resultado_visita";
    public static final String PLANIFICAR_SEGUIMIENTO = "planificar_seguimiento_prospeccion";
    public static final String POSPONER_SEGUIMIENTO = "posponer_seguimiento_prospeccion";
    public static final String ABRIR_CAPTURA = "abrir_sesion_captura";
    public static final String RESPONDER_CAPTURA = "responder_captura";

    /** Identificadores originales completos del turno; no los recortes de auditoría. */
    public record CorrelacionAutomatico(String conversacionId, String turnoId, String mensajeId) {
        public CorrelacionAutomatico {
            if (conversacionId == null || conversacionId.isBlank() || turnoId == null || turnoId.isBlank()) {
                throw new ReglaNegocioException("Se requiere la correlación original de la operación.");
            }
        }
    }

    public record ResultadoAutomatico<T>(String clave, String capacidad, int version, T resultado) { }

    private final PropietarioService propietarios;
    private final InteraccionService interacciones;
    private final VisitaService visitas;
    private final ProspeccionService prospecciones;
    private final ContextosDeActividad contextos;
    private final MotorDeCaptura captura;
    private final ComandosIdempotentes comandos;
    private final ComandoIdempotenteRepository registros;
    private final EventoDominioRepository eventos;
    private final Documentos documentos;
    private final ObjectMapper json;
    private final IdentidadComandoAutomatico identidades;

    public ComandosAutomaticosService(PropietarioService propietarios, InteraccionService interacciones,
                                      VisitaService visitas, ProspeccionService prospecciones, ContextosDeActividad contextos,
                                      MotorDeCaptura captura, ComandosIdempotentes comandos,
                                      ComandoIdempotenteRepository registros, EventoDominioRepository eventos,
                                      Documentos documentos, ObjectMapper json,IdentidadComandoAutomatico identidades) {
        this.propietarios = propietarios;
        this.interacciones = interacciones;
        this.visitas = visitas;
        this.prospecciones = prospecciones;
        this.contextos = contextos;
        this.captura = captura;
        this.comandos = comandos;
        this.registros = registros;
        this.eventos = eventos;
        this.documentos = documentos;
        this.json = json;
        this.identidades = identidades;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoAutomatico<FichaPropietario> registrarPropietario(DatosPropietario datos, Actor actor,
            CorrelacionAutomatico correlacion, Procedencia origen) {
        return ejecutar(REGISTRAR_PROPIETARIO, mapa(datos), actor, correlacion, origen,
                () -> propietarios.registrar(datos, actor), FichaPropietario.class, FichaPropietario::id,
                "PROPIETARIO", "PROPIETARIO_REGISTRADO", f -> propietarios.obtener(f.id(), actor));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoAutomatico<FichaInteraccion> registrarInteraccion(DatosInteraccion datos, Actor actor,
            ContextoSesion sesion, CorrelacionAutomatico correlacion, Procedencia origen) {
        contextos.iniciar(actor, sesion);
        return ejecutar(REGISTRAR_INTERACCION, mapa(datos), actor, correlacion, origen,
                () -> interacciones.registrar(datos, actor, sesion, procedencia(origen, correlacion, REGISTRAR_INTERACCION)),
                FichaInteraccion.class, FichaInteraccion::id,
                "INTERACCION", "INTERACCION_REGISTRADA", f -> interacciones.obtenerVersion(f.id(), f.version(), actor));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoAutomatico<FichaInteraccion> vincularInteraccion(long id, InteraccionService.AdicionVinculo datos,
            Actor actor, ContextoSesion sesion, CorrelacionAutomatico correlacion, Procedencia origen) {
        contextos.iniciar(actor, sesion);
        return ejecutar(VINCULAR_INTERACCION, entradaInteraccion(id, datos), actor, correlacion, origen,
                () -> interacciones.vincular(id, datos, actor, sesion, procedencia(origen, correlacion, VINCULAR_INTERACCION)),
                FichaInteraccion.class, FichaInteraccion::id, "INTERACCION", "INTERACCION_VINCULADA",
                f -> interacciones.obtenerVersion(f.id(), f.version(), actor));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoAutomatico<FichaInteraccion> anotarObservacionInteraccion(long id, InteraccionService.ObservacionPosterior datos,
            Actor actor, ContextoSesion sesion, CorrelacionAutomatico correlacion, Procedencia origen) {
        contextos.iniciar(actor, sesion);
        return ejecutar(OBSERVAR_INTERACCION, entradaInteraccion(id, datos), actor, correlacion, origen,
                () -> interacciones.observar(id, datos, actor, sesion, procedencia(origen, correlacion, OBSERVAR_INTERACCION)),
                FichaInteraccion.class, FichaInteraccion::id, "INTERACCION", "INTERACCION_OBSERVACION_REGISTRADA",
                f -> interacciones.obtenerVersion(f.id(), f.version(), actor));
    }

    private Map<String,Object> entradaInteraccion(long id,Object datos) {
        var entrada=new LinkedHashMap<String,Object>();
        entrada.put("idInteraccion",id); entrada.put("datos", datos==null?null:mapa(datos));
        return entrada;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoAutomatico<RevisionSeguimiento> planificarSeguimiento(long idProspeccion,long idSeguimiento,
            ProspeccionService.PlanificacionSeguimiento datos,Actor actor,ContextoSesion sesion,
            CorrelacionAutomatico correlacion,Procedencia origen) {
        contextos.iniciar(actor,sesion);
        return ejecutar(PLANIFICAR_SEGUIMIENTO,entradaSeguimiento(idProspeccion,idSeguimiento,datos),actor,correlacion,origen,
                ()->prospecciones.planificarSeguimiento(idProspeccion,idSeguimiento,datos,actor,sesion,procedencia(origen,correlacion,PLANIFICAR_SEGUIMIENTO)),
                RevisionSeguimiento.class,RevisionSeguimiento::id,"SEGUIMIENTO_PROSPECCION","SEGUIMIENTO_PROSPECCION_PLANIFICADO",
                f->prospecciones.exigirLecturaRevisionSeguimiento(f,actor));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoAutomatico<RevisionSeguimiento> posponerSeguimiento(long idProspeccion,long idSeguimiento,
            ProspeccionService.PosposicionSeguimiento datos,Actor actor,ContextoSesion sesion,
            CorrelacionAutomatico correlacion,Procedencia origen) {
        contextos.iniciar(actor,sesion);
        return ejecutar(POSPONER_SEGUIMIENTO,entradaSeguimiento(idProspeccion,idSeguimiento,datos),actor,correlacion,origen,
                ()->prospecciones.posponerSeguimiento(idProspeccion,idSeguimiento,datos,actor,sesion,procedencia(origen,correlacion,POSPONER_SEGUIMIENTO)),
                RevisionSeguimiento.class,RevisionSeguimiento::id,"SEGUIMIENTO_PROSPECCION","SEGUIMIENTO_PROSPECCION_POSPUESTO",
                f->prospecciones.exigirLecturaRevisionSeguimiento(f,actor));
    }

    private Map<String,Object> entradaSeguimiento(long idProspeccion,long idSeguimiento,Object datos) {
        var entrada=new LinkedHashMap<String,Object>();
        entrada.put("idProspeccion",idProspeccion);entrada.put("idSeguimiento",idSeguimiento);
        entrada.put("datos",datos==null?null:mapa(datos));return entrada;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoAutomatico<FichaVisita> programarVisita(VisitaService.DatosVisita datos, Actor actor,
            ContextoSesion sesion, CorrelacionAutomatico correlacion, Procedencia origen) {
        contextos.iniciar(actor, sesion);
        return ejecutar(PROGRAMAR_VISITA, mapa(datos), actor, correlacion, origen,
                () -> visitas.programar(datos, actor, sesion, procedencia(origen, correlacion, PROGRAMAR_VISITA)),
                FichaVisita.class, FichaVisita::id, "VISITA", "VISITA_PROGRAMADA",
                f -> visitas.obtenerVersion(f.id(), f.version(), actor));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoAutomatico<FichaVisita> reprogramarVisita(VisitaService.Reprogramacion datos, Actor actor,
            ContextoSesion sesion, CorrelacionAutomatico correlacion, Procedencia origen) {
        contextos.iniciar(actor, sesion);
        return ejecutar(REPROGRAMAR_VISITA, mapa(datos), actor, correlacion, origen,
                () -> visitas.reprogramar(datos, actor, sesion, procedencia(origen, correlacion, REPROGRAMAR_VISITA)),
                FichaVisita.class, FichaVisita::id, "VISITA", "VISITA_REPROGRAMADA",
                f -> visitas.obtenerVersion(f.id(), f.version(), actor));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoAutomatico<FichaVisita> registrarResultadoVisita(long id, VisitaService.DesenlaceVisita datos,
            Actor actor, ContextoSesion sesion, CorrelacionAutomatico correlacion, Procedencia origen) {
        contextos.iniciar(actor, sesion);
        Map<String,Object> entrada = new LinkedHashMap<>();
        entrada.put("idVisita", id);
        entrada.put("resultadoVisita", datos == null ? null : mapa(datos));
        return ejecutar(RESULTADO_VISITA, entrada, actor, correlacion, origen,
                () -> visitas.registrarResultado(id, datos, actor, sesion, procedencia(origen, correlacion, RESULTADO_VISITA)),
                FichaVisita.class, FichaVisita::id, "VISITA", "VISITA_RESULTADO_REGISTRADO",
                f -> visitas.obtenerVersion(f.id(), f.version(), actor));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoAutomatico<EstadoCaptura> avanzarCaptura(com.controllocal.service.captura.MotorDeCaptura.Avance avance,
            Actor actor, CorrelacionAutomatico correlacion, Procedencia origen) {
        if (avance == null) throw new ReglaNegocioException("El avance es obligatorio.");
        String capacidad = avance.idBorrador() == null ? ABRIR_CAPTURA : RESPONDER_CAPTURA;
        Map<String, Object> entrada = mapa(avance);
        return ejecutar(capacidad, entrada, actor, correlacion, origen,
                () -> captura.avanzar(avance, procedencia(origen, correlacion, capacidad), actor),
                EstadoCaptura.class, EstadoCaptura::idBorrador, "BORRADOR_CAPTURA", null,
                f -> captura.consultar(f.idBorrador(), actor));
    }

    @Transactional(readOnly = true)
    public Optional<ResultadoAutomatico<FichaPropietario>> consultarPropietario(Actor actor, String clave) {
        return consultar(REGISTRAR_PROPIETARIO, actor, clave, FichaPropietario.class,
                f -> propietarios.obtener(f.id(), actor));
    }

    /** El orquestador concilia antes de buscar una persona existente; nunca emite de nuevo el alta. */
    @Transactional(readOnly = true)
    public Optional<ResultadoAutomatico<FichaPropietario>> recuperarPropietario(DatosPropietario datos, Actor actor,
            CorrelacionAutomatico correlacion) {
        String clave = clavePara(REGISTRAR_PROPIETARIO, actor, correlacion);
        String huella = Idempotencia.huellaExacta(documentos.objetoCanonico(mapa(datos)));
        return buscar(actor, clave, REGISTRAR_PROPIETARIO, huella)
                .map(c -> restaurar(c, actor, REGISTRAR_PROPIETARIO, FichaPropietario.class,
                        f -> propietarios.obtener(f.id(), actor)));
    }

    @Transactional(readOnly = true)
    public Optional<ResultadoAutomatico<FichaInteraccion>> consultarInteraccion(String capacidad, Actor actor, String clave) {
        if (!REGISTRAR_INTERACCION.equals(capacidad) && !VINCULAR_INTERACCION.equals(capacidad)
                && !OBSERVAR_INTERACCION.equals(capacidad)) {
            throw new ReglaNegocioException("La capacidad no corresponde a una actuación de interacción.");
        }
        return consultar(capacidad, actor, clave, FichaInteraccion.class,
                f -> interacciones.obtenerVersion(f.id(), f.version(), actor));
    }

    @Transactional(readOnly = true)
    public Optional<ResultadoAutomatico<FichaVisita>> consultarVisita(String capacidad, Actor actor, String clave) {
        if (!PROGRAMAR_VISITA.equals(capacidad) && !REPROGRAMAR_VISITA.equals(capacidad)
                && !RESULTADO_VISITA.equals(capacidad)) {
            throw new ReglaNegocioException("La capacidad no corresponde a una actuación de visita.");
        }
        return consultar(capacidad, actor, clave, FichaVisita.class,
                f -> visitas.obtenerVersion(f.id(), f.version(), actor));
    }

    @Transactional(readOnly=true)
    public Optional<ResultadoAutomatico<RevisionSeguimiento>> consultarSeguimiento(String capacidad,Actor actor,String clave) {
        if(!PLANIFICAR_SEGUIMIENTO.equals(capacidad)&&!POSPONER_SEGUIMIENTO.equals(capacidad))
            throw new ReglaNegocioException("La capacidad no corresponde a una planificación de seguimiento.");
        return consultar(capacidad,actor,clave,RevisionSeguimiento.class,f->prospecciones.exigirLecturaRevisionSeguimiento(f,actor));
    }

    @Transactional(readOnly = true)
    public Optional<ResultadoAutomatico<EstadoCaptura>> consultarCaptura(Actor actor, String capacidad, String clave) {
        if (!ABRIR_CAPTURA.equals(capacidad) && !RESPONDER_CAPTURA.equals(capacidad)) {
            throw new ReglaNegocioException("La capacidad no corresponde a un avance de captura.");
        }
        return consultar(capacidad, actor, clave, EstadoCaptura.class, f -> captura.consultar(f.idBorrador(), actor));
    }

    /** Recupera la misma identidad aun cuando no se recibió la respuesta inicial. No ejecuta ni escribe. */
    public String clavePara(String capacidad, Actor actor, CorrelacionAutomatico correlacion) {
        reconocer(capacidad);
        return identidades.clavePara(capacidad,actor,correlacion);
    }

    private <T> ResultadoAutomatico<T> ejecutar(String capacidad, Map<String, Object> entrada, Actor actor,
            CorrelacionAutomatico correlacion, Procedencia origen, Supplier<T> hecho, Class<T> tipo,
            Function<T, Long> identificador, String entidad, String evento, Consumer<T> reautorizar) {
        Integer aislamiento = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (aislamiento != null && aislamiento != TransactionDefinition.ISOLATION_READ_COMMITTED) {
            throw new IllegalStateException("La operación automática requiere aislamiento READ_COMMITTED.");
        }
        String clave = clavePara(capacidad, actor, correlacion);
        String huella = Idempotencia.huellaExacta(documentos.objetoCanonico(entrada));
        Procedencia procedencia = procedencia(origen, correlacion, capacidad);
        // El bloqueo no exige una fila previa ni una caché local. Tras esperar,
        // READ_COMMITTED ve el commit de quien ejecutó la misma identidad.
        registros.bloquearIdentidad(Long.parseUnsignedLong(clave.substring(0, 16), 16));
        Optional<ComandoIdempotente> previo = buscar(actor, clave, capacidad, huella);
        if (previo.isPresent()) return restaurar(previo.get(), actor, capacidad, tipo, reautorizar);

        T resultado = hecho.get();
        Long id = identificador.apply(resultado);
        Map<String, Object> resultadoGuardado = mapa(resultado);
        String huellaResultado = Idempotencia.huellaExacta(documentos.objetoCanonico(resultadoGuardado));
        Map<String, Object> instantanea = new LinkedHashMap<>();
        instantanea.put("version", VERSION);
        instantanea.put("capacidad", capacidad);
        instantanea.put("actor", mapa(actor));
        instantanea.put("correlacion", mapa(correlacion));
        instantanea.put("entrada", entrada);
        instantanea.put("procedencia", mapa(procedencia));
        instantanea.put("resultado", resultadoGuardado);
        instantanea.put("huellaResultado", huellaResultado);
        comandos.registrar(actor, clave, tipoComando(capacidad), huella, entidad, id, procedencia,
                documentos.objetoCanonico(instantanea));
        Procedencia referenciaEvento=esActividadDirecta(capacidad)?procedencia.sinPeticion():procedencia;
        if (evento != null) eventos.save(referenciaEvento.sellar(EventoDominio.de(actor.idOrganizacion(), evento, entidad, id,
                actor.idRolOperativo(), procedencia.canal()).con(documentos.objetoCanonico(Map.of(
                        "claveComando", clave, "capacidad", capacidad, "version", VERSION, "huella", huella,
                        "correlacion", mapa(correlacion), "huellaResultado", huellaResultado)))));
        reautorizar.accept(resultado);
        return new ResultadoAutomatico<>(clave, capacidad, VERSION, resultado);
    }

    private <T> Optional<ResultadoAutomatico<T>> consultar(String capacidad, Actor actor, String clave,
            Class<T> tipo, Consumer<T> reautorizar) {
        com.controllocal.service.soporte.Alcances.exigirOperador(actor);
        return comandos.consultarResultado(actor, clave, tipoComando(capacidad))
                .map(c -> restaurar(c, actor, capacidad, tipo, reautorizar));
    }

    private Optional<ComandoIdempotente> buscar(Actor actor, String clave, String capacidad, String huella) {
        com.controllocal.service.soporte.Alcances.exigirOperador(actor);
        try {
            return comandos.buscar(actor, clave, tipoComando(capacidad), huella);
        } catch (ReglaNegocioException conflictoDeContenido) {
            // La clave ya fue construida y validada por Core. La discrepancia
            // restante es reutilización de identidad, no otra regla comercial.
            throw new ConflictoException("La identidad de la operación no admite otro contenido.");
        }
    }

    private <T> ResultadoAutomatico<T> restaurar(ComandoIdempotente comando, Actor actor, String capacidad,
            Class<T> tipo, Consumer<T> reautorizar) {
        Map<String, Object> instantanea = documentos.comoMapa(comando.getResultado());
        Actor original = json.convertValue(instantanea.get("actor"), Actor.class);
        if (!actor.equals(original)) throw new AccesoNoAutorizadoException();
        CorrelacionAutomatico correlacion = json.convertValue(instantanea.get("correlacion"), CorrelacionAutomatico.class);
        Map<String, Object> resultado = mapa(instantanea.get("resultado"));
        if(tipo==FichaInteraccion.class&&!resultado.containsKey("versionDiccionario"))
            throw new IllegalStateException("El recibo histórico conserva otro contrato; no se reinterpreta como comunicación C4.");
        Object idResultado = resultado.get(tipo == EstadoCaptura.class ? "idBorrador" : "id");
        if (!Integer.valueOf(VERSION).equals(instantanea.get("version"))
                || !capacidad.equals(instantanea.get("capacidad"))
                || !tipoComando(capacidad).equals(comando.getTipoComando())
                || !clavePara(capacidad, actor, correlacion).equals(comando.getClaveIdempotencia())
                || !Idempotencia.huellaExacta(documentos.objetoCanonico(mapa(instantanea.get("entrada")))).equals(comando.getHuella())
                || !Idempotencia.huellaExacta(documentos.objetoCanonico(resultado)).equals(instantanea.get("huellaResultado"))
                || !(idResultado instanceof Number numero) || numero.longValue() != comando.getEntidadId()) {
            throw new IllegalStateException("La operación durable no conserva su contrato original.");
        }
        // La autoridad actual debe cubrir los datos de esta versión ORIGINAL.
        // La ficha actual podría tener otros contactos o vínculos y no basta para autorizarla.
        T originalGuardado = json.convertValue(instantanea.get("resultado"), tipo);
        reautorizar.accept(originalGuardado);
        return new ResultadoAutomatico<>(comando.getClaveIdempotencia(), capacidad, VERSION, originalGuardado);
    }

    private static Procedencia procedencia(Procedencia origen, CorrelacionAutomatico correlacion, String capacidad) {
        if (origen != null && !origen.laFormuloUnAgente() && esActividadDirecta(capacidad)) {
            // C4 §9: Web ejecuta el mismo caso Core, conservando autoría humana.
            return new Procedencia(origen.canal(), null, null, null, correlacion.conversacionId(),
                    correlacion.turnoId(), correlacion.mensajeId(), origen.peticion(), capacidad);
        }
        if (origen == null || !origen.laFormuloUnAgente()) {
            throw new ReglaNegocioException("Una operación automática requiere procedencia del asistente.");
        }
        return Procedencia.deAgente(origen.canal(), origen.agente(), origen.modelo(), origen.modeloVersion(),
                correlacion.conversacionId(), correlacion.turnoId(), correlacion.mensajeId(), origen.peticion())
                .invocando(capacidad);
    }

    private Map<String, Object> mapa(Object valor) {
        return valor == null ? new LinkedHashMap<>() : json.convertValue(valor, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private static String tipoComando(String capacidad) {
        reconocer(capacidad);
        return "AUTO_" + capacidad.toUpperCase(java.util.Locale.ROOT);
    }

    private static void reconocer(String capacidad) {
        if (!REGISTRAR_PROPIETARIO.equals(capacidad) && !REGISTRAR_INTERACCION.equals(capacidad)
                && !ABRIR_CAPTURA.equals(capacidad) && !RESPONDER_CAPTURA.equals(capacidad)
                && !esActividadDirecta(capacidad)) {
            throw new ReglaNegocioException("La capacidad automática no está reconocida.");
        }
    }

    private static boolean esActividadDirecta(String capacidad) {
        return REGISTRAR_INTERACCION.equals(capacidad) || PROGRAMAR_VISITA.equals(capacidad)
                || REPROGRAMAR_VISITA.equals(capacidad) || RESULTADO_VISITA.equals(capacidad)
                || VINCULAR_INTERACCION.equals(capacidad) || OBSERVAR_INTERACCION.equals(capacidad)
                || PLANIFICAR_SEGUIMIENTO.equals(capacidad) || POSPONER_SEGUIMIENTO.equals(capacidad);
    }

}
