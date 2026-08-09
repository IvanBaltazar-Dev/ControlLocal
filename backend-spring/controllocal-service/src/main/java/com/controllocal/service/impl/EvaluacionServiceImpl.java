package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.EvaluacionSolicitud;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.EvaluacionSolicitudRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.EvaluacionService;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AccesoSolicitud;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.Transiciones;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reglas y mensajes calcados de {@code EvaluacionRest} +
 * {@code EvaluacionSolicitudBusinessLogicImpl} (contrato congelado F4 §4).
 *
 * <p>Tres cosas del cable que hay que respetar:
 * <ol>
 *   <li>el <b>tipo se deriva del resultado</b> —observada ⇒ OBSERVACION,
 *       aprobada/rechazada ⇒ FINAL—, el broker no lo elige. Pero el campo
 *       {@code tipoEvaluacion} del request <b>debe venir y ser valido</b>: la
 *       v1 lo parsea en el DTO antes de pisarlo, asi que mandarlo vacio es un
 *       400. Se ignora su VALOR, no su presencia;</li>
 *   <li>solo cabe <b>una evaluacion FINAL por solicitud</b> (la BD lo
 *       garantiza con un unico parcial; aqui se corta antes para responder el
 *       mensaje del cable en vez de un choque de integridad);</li>
 *   <li>el <b>broker debe supervisar al agente responsable</b>; el admin
 *       no.</li>
 * </ol>
 *
 * <p>La evaluacion mueve la solicitud en la MISMA transaccion, y ahora esa
 * transicion pasa por {@link Transiciones}: cada decision del broker deja
 * fila en {@code historial_estado} (MEJ-01).
 */
@Service
public class EvaluacionServiceImpl implements EvaluacionService {

    /** Cada resultado del cable mueve la solicitud a un estado distinto. */
    private static final Map<String, String> DESTINO_SOLICITUD = Map.of(
            EvaluacionSolicitud.APROBADA, SolicitudAlquiler.APROBADA,
            EvaluacionSolicitud.RECHAZADA, SolicitudAlquiler.RECHAZADA,
            EvaluacionSolicitud.OBSERVADA, SolicitudAlquiler.OBSERVADA);

    private static final Set<String> TIPOS = Set.of(
            EvaluacionSolicitud.PRELIMINAR, EvaluacionSolicitud.OBSERVACION, EvaluacionSolicitud.FINAL);

    private final EvaluacionSolicitudRepository evaluaciones;
    private final SolicitudAlquilerRepository solicitudes;
    private final DetalleBrokerRepository brokers;
    private final AccesoSolicitud acceso;
    private final Alcances alcances;
    private final Transiciones transiciones;
    private final AlertaService alertas;

    /** Descripcion del resultado tal como la escribe el mensaje del cable. */
    private static final Map<String, String> DESCRIPCION_RESULTADO = Map.of(
            EvaluacionSolicitud.APROBADA, "Aprobada",
            EvaluacionSolicitud.RECHAZADA, "Rechazada",
            EvaluacionSolicitud.OBSERVADA, "Observada");

    /** Severidad por desenlace: rechazar es ALTA, observar MEDIA, aprobar INFO. */
    private static final Map<String, String> SEVERIDAD_RESULTADO = Map.of(
            EvaluacionSolicitud.APROBADA, Alerta.INFO,
            EvaluacionSolicitud.RECHAZADA, Alerta.ALTA,
            EvaluacionSolicitud.OBSERVADA, Alerta.MEDIA);

    public EvaluacionServiceImpl(EvaluacionSolicitudRepository evaluaciones,
                                 SolicitudAlquilerRepository solicitudes,
                                 DetalleBrokerRepository brokers, AccesoSolicitud acceso,
                                 Alcances alcances, Transiciones transiciones,
                                 AlertaService alertas) {
        this.alertas = alertas;
        this.evaluaciones = evaluaciones;
        this.solicitudes = solicitudes;
        this.brokers = brokers;
        this.acceso = acceso;
        this.alcances = alcances;
        this.transiciones = transiciones;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaEvaluacion> listar(int pagina, int tamano, Actor actor) {
        // Deuda de la v1 cerrada sin tocar el cable: alli listar() traia
        // listarTodos() y cortaba con subList; aqui el LIMIT/OFFSET esta en
        // SQL (MEJ-05 / RC-003) y la respuesta es identica.
        Page<EvaluacionSolicitud> page = evaluaciones.buscar(
                actor.idOrganizacion(), actor.esTenantAdmin(), actor.idRolOperativo(),
                PageRequest.of(Math.max(0, pagina - 1), tamano(tamano)));
        return new Pagina<>(page.getContent().stream().map(EvaluacionServiceImpl::ficha).toList(),
                page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public FichaEvaluacion obtener(long id, Actor actor) {
        // La evaluacion de OTRO broker responde 404, no 403: la v1 filtra su
        // propia lista y no encuentra nada.
        return evaluaciones
                .buscarFicha(actor.idOrganizacion(), id, actor.esTenantAdmin(), actor.idRolOperativo())
                .map(EvaluacionServiceImpl::ficha)
                .orElseThrow(() -> new NoEncontradoException("Evaluacion"));
    }

    @Override
    @Transactional
    public FichaEvaluacion registrar(DatosEvaluacion datos, Actor actor) {
        if (datos == null || datos.idSolicitud() == null) {
            throw new ReglaNegocioException("Los datos de la evaluacion son obligatorios.");
        }
        // Mismo ORDEN que Dtos.EvaluacionRequest.aEntidad: el tipo se valida
        // ANTES que el resultado, asi que con los dos mal el mensaje que gana
        // es el del tipo. Y si, se valida un campo que luego se pisa.
        exigirCodigo(datos.tipoEvaluacion(), TIPOS, "tipo de evaluacion");
        String resultado = exigirCodigo(datos.resultado(), EvaluacionSolicitud.RESULTADOS,
                "resultado de evaluacion");

        SolicitudAlquiler solicitud = solicitudes
                .buscarFicha(actor.idOrganizacion(), datos.idSolicitud())
                .orElseThrow(() -> new ReglaNegocioException("Solicitud no encontrada para evaluacion."));
        DetalleBroker broker = brokers.findById(actor.idRolOperativo())
                .orElseThrow(() -> new ReglaNegocioException("Broker responsable no encontrado."));
        exigirSupervisionSobreElAgente(solicitud, actor);

        EvaluacionSolicitud evaluacion = new EvaluacionSolicitud();
        evaluacion.setOrganizacionId(solicitud.getOrganizacionId());
        evaluacion.setSolicitud(solicitud);
        evaluacion.setBroker(broker);
        evaluacion.setObservaciones(datos.observaciones());
        // Aqui es donde el tipo del request deja de importar: lo decide el resultado.
        evaluacion.fijarResultado(resultado);
        evaluacion.sellarFecha();
        if (evaluacion.esFinal()
                && evaluaciones.existeFinalDe(actor.idOrganizacion(), solicitud.getId())) {
            throw new ReglaNegocioException("Solo puede existir una evaluacion final por solicitud.");
        }
        EvaluacionSolicitud guardada = evaluaciones.save(evaluacion);

        // El evento mueve la solicitud en la misma transaccion. OBSERVADA la
        // devuelve al estado desde el que el agente puede reenviarla.
        transiciones.aplicar(solicitud, solicitud.getId(), DESTINO_SOLICITUD.get(resultado), actor,
                motivo(resultado, datos.observaciones()));
        solicitudes.save(solicitud);
        // Aviso al agente responsable (§4 F6, punto 8). La severidad la marca
        // el desenlace, no el tipo de alerta.
        alertas.emitir(new AlertaService.DatosAlerta(Alerta.SOLICITUD_EVALUADA,
                SEVERIDAD_RESULTADO.getOrDefault(resultado, Alerta.MEDIA),
                "SOLICITUD_ALQUILER", solicitud.getId(),
                solicitud.getAgente() != null ? solicitud.getAgente().getId() : null,
                "La solicitud " + solicitud.getCodigoSolicitud() + " fue evaluada con resultado "
                        + DESCRIPCION_RESULTADO.getOrDefault(resultado, resultado) + "."), actor);
        return ficha(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichaEvaluacion> historialDeSolicitud(long idSolicitud, Actor actor) {
        // Este SI lo ve el agente dueno: el alcance es el de la solicitud, no
        // el del recurso /evaluaciones (que es de broker/admin).
        acceso.conAcceso(idSolicitud, actor);
        return evaluaciones.porSolicitud(actor.idOrganizacion(), idSolicitud).stream()
                .map(EvaluacionServiceImpl::ficha)
                .toList();
    }

    // ------------------------------------------------------------------

    /**
     * "El broker no supervisa al agente responsable de esta solicitud."
     *
     * <p><b>Ya no hay exencion de gobierno.</b> La habia porque el ADMIN
     * heredaba las capacidades del broker y firmaba evaluaciones; la fila 13 de
     * D-S0-17 se la retira —es la decision que desemboca en contrato y
     * comision, y firmarla es responsabilidad profesional del broker—, asi que
     * el TENANT_ADMIN ni siquiera llega hasta aqui: el gate lo corta antes con
     * 403. Dejar la exencion escrita sugeriria un camino que ya no existe.
     */
    private void exigirSupervisionSobreElAgente(SolicitudAlquiler solicitud, Actor actor) {
        DetalleAgente agente = solicitud.getAgente();
        if (agente == null || !alcances.alcanza(actor, agente.getId())) {
            throw new ReglaNegocioException(
                    "El broker no supervisa al agente responsable de esta solicitud.");
        }
    }

    /**
     * Mensaje identico al {@code enumDesde} del DTO v1, incluido el caso
     * ausente: alli {@code CodigoEnum.fromCodigo} lanza y el DTO reescribe,
     * asi que un campo vacio sale como "Valor invalido para ...: null".
     *
     * <p>La comparacion es EXACTA (no se normaliza la caja), como
     * {@code CodigoEnum.fromCodigo}: "a" no es "A". Lo unico que se tolera es
     * el espacio sobrante, misma licencia que toma {@code Vocabulario}. Ojo,
     * los dos gates de comision SI normalizan —alli la v1 hace
     * {@code valueOf(trim().toUpperCase())}—, asi que no unificar.
     */
    private static String exigirCodigo(String valor, Set<String> validos, String campo) {
        String codigo = valor == null ? null : valor.trim();
        if (codigo == null || codigo.isBlank() || !validos.contains(codigo)) {
            throw new ReglaNegocioException("Valor invalido para " + campo + ": " + valor);
        }
        return codigo;
    }

    private static String motivo(String resultado, String observaciones) {
        String base = switch (resultado) {
            case EvaluacionSolicitud.APROBADA -> "Solicitud aprobada por el broker.";
            case EvaluacionSolicitud.RECHAZADA -> "Solicitud rechazada por el broker.";
            default -> "Solicitud observada por el broker.";
        };
        return observaciones == null || observaciones.isBlank() ? base : base + " " + observaciones.trim();
    }

    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }

    private static FichaEvaluacion ficha(EvaluacionSolicitud e) {
        DetalleBroker broker = e.getBroker();
        SolicitudAlquiler solicitud = e.getSolicitud();
        return new FichaEvaluacion(
                e.getId(), Fechas.local(e.getFechaEvaluacion()), e.getResultado(), e.getObservaciones(),
                broker != null ? broker.getId() : null,
                nombre(broker != null ? broker.getRol() : null),
                e.getTipoEvaluacion(),
                solicitud != null ? solicitud.getId() : null);
    }

    private static String nombre(PersonaRol rol) {
        return rol == null || rol.getPersona() == null ? null : rol.getPersona().getNombresORazonSocial();
    }
}
