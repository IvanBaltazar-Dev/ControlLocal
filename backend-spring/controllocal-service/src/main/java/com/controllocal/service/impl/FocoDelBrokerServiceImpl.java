package com.controllocal.service.impl;

import com.controllocal.persistence.query.CandidatoTarea;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.FocoDelBrokerService;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.EstadoDelHecho;
import com.controllocal.service.soporte.InterpretacionDelAsunto;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Avance;
import com.controllocal.service.soporte.InterpretacionDelAsunto.ComoEsta;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Hecho;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Interpretacion;
import com.controllocal.service.soporte.LadoDeLaOperacion;
import com.controllocal.service.soporte.PoliticaDeDespacho;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <b>Los asuntos del broker: lo que él tiene que decidir</b> (D-E2-5, E2.5).
 *
 * <h2>Qué NO es</h2>
 * No es la bandeja del agente filtrada, ni una ventana a ella. `GET /tareas`
 * sigue siendo del agente y sigue sin acceso de ADMIN: <b>la bandeja no es un
 * tablero de control</b>, y esa regla no se toca. Lo que cambia es que deja de
 * ser la única bandeja del sistema.
 *
 * <h2>Qué sí es</h2>
 * Las decisiones que <b>sólo el broker</b> puede tomar, según la matriz
 * operación-rol:
 *
 * <pre>
 *   captaciones por revisar   aprobar u observar es suyo
 *   solicitudes por evaluar   firmar la evaluacion es suyo
 *   comisiones sin cobrar     registrar el cobro es suyo
 * </pre>
 *
 * <p>Un agente no ve estas, y el broker no ve las del agente. Si los dos
 * conjuntos se solaparan, sería señal de que alguien copió el disparador
 * equivocado.
 *
 * <h2>El mismo motor, sin excepciones</h2>
 * Pasa por la <b>misma</b> {@link PoliticaDeDespacho} de E2.2 y produce la
 * <b>misma</b> {@link Interpretacion} de E2.4. Si el broker necesitara pesos
 * distintos, se añadiría un criterio a la política — no una política nueva.
 *
 * <h2>Y nunca comparte identidad con el agente</h2>
 * D-E2-1 §7.1 lo aprendió a golpes: con un solo id, el mismo encargo salía dos
 * veces en el broker porque la regla del hogar único no vale si el
 * identificador no es el del rol que mira. Por eso los ids de este foco llevan
 * su propio prefijo, y hay un gate que lo comprueba.
 */
@Service
public class FocoDelBrokerServiceImpl implements FocoDelBrokerService {

    private final CaptacionRepository captaciones;
    private final SolicitudAlquilerRepository solicitudes;
    private final Alcances alcances;

    public FocoDelBrokerServiceImpl(CaptacionRepository captaciones,
                                    SolicitudAlquilerRepository solicitudes,
                                    Alcances alcances) {
        this.captaciones = captaciones;
        this.solicitudes = solicitudes;
        this.alcances = alcances;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AsuntoDelBroker> de(Actor actor) {
        if (!actor.esBroker()) {
            // El TENANT_ADMIN no tiene asuntos, y es deliberado: desde D-F4-5 no
            // decide ninguna operacion comercial. Un Inicio con asuntos para
            // quien no puede resolverlos es la definicion de un tablero de
            // control (D-E2-5).
            return List.of();
        }
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return List.of();
        }
        LocalDate hoy = LocalDate.now();
        List<AsuntoDelBroker> asuntos = new ArrayList<>();

        for (CandidatoTarea c : captaciones.porRevisarDelBroker(
                alcance.idOrganizacion(), alcance.global(), alcance.paramRoles())) {
            asuntos.add(asunto(CAPTACION_POR_REVISAR, "CAPTACION", c, hoy,
                    "Falta tu decision sobre la captacion",
                    "El local no se puede ofrecer hasta que la apruebes",
                    "captaciones/pendientes"));
        }
        // Las solicitudes producen UN asunto cada una, y sus documentos deciden
        // cual: conformar va antes de evaluar. Dos asuntos para la misma
        // solicitud serian la duplicacion que D-E2-1 seccion 11 prohibe -- y no
        // es hipotetico: la solicitud 1 estaba EN_REVISION con 3 de 5 documentos
        // pendientes.
        List<CandidatoTarea> porEvaluar = solicitudes.porEvaluarDelBroker(
                alcance.idOrganizacion(), alcance.global(), alcance.paramRoles());
        Map<Long, int[]> documentos = documentosDe(alcance.idOrganizacion(), porEvaluar);

        for (CandidatoTarea c : porEvaluar) {
            int[] cuenta = documentos.get(c.getEntidadId());
            boolean faltaConformar = cuenta != null && cuenta[1] > 0;

            if (faltaConformar) {
                int total = cuenta[0];
                int conformados = total - cuenta[1];
                asuntos.add(asunto(DOCUMENTOS_POR_CONFORMAR, "SOLICITUD_ALQUILER", c, hoy,
                        "Faltan documentos por conformar",
                        "Sin conformidad no se puede evaluar la solicitud",
                        "solicitud-detail/" + nz(c.getEntidadCodigo(), c.getEntidadId()),
                        new Avance(conformados, total, "documentos conformados")));
            } else {
                asuntos.add(asunto(SOLICITUD_POR_EVALUAR, "SOLICITUD_ALQUILER", c, hoy,
                        "Falta tu evaluacion de la solicitud",
                        "El interesado espera respuesta y el contrato no puede firmarse",
                        "solicitudes/revisar"));
            }
        }
        for (CandidatoTarea c : captaciones.comisionesSinCobrarDelBroker(
                alcance.idOrganizacion(), alcance.global(), alcance.paramRoles())) {
            asuntos.add(asunto(COMISION_SIN_COBRAR, "CONTRATO_ALQUILER", c, hoy,
                    "Falta registrar el cobro de la comision",
                    null,
                    "comisiones"));
        }

        // La MISMA politica de despacho del agente. Sin orden previo, el criterio
        // 6 desempata por id y dos lecturas seguidas devuelven lo mismo.
        return PoliticaDeDespacho.despachar(asuntos,
                a -> new PoliticaDeDespacho.Asunto(
                        a.entidadId() == null ? 0L : a.entidadId(),
                        true,                       // todo lo que llega aqui es suyo
                        null,                       // ningun disparador trae plazo propio
                        false,                      // la ocasion es del agente
                        a.freno() != null,          // desbloquea cuando algo queda parado
                        a.diasEsperando()),
                List.of());
    }

    // ------------------------------------------------------------------

    /**
     * Cuantos documentos hay y cuantos esperan conformidad, por solicitud.
     *
     * <p>UNA consulta para toda la pagina. Pedirlo dentro del bucle serian
     * tantas como solicitudes, que es el N+1 que vuelve cada vez que se anade un
     * disparador.
     *
     * @return por id de solicitud, {@code [total, pendientes]}
     */
    private Map<Long, int[]> documentosDe(long idOrganizacion, List<CandidatoTarea> solicitudesDelBroker) {
        List<Long> ids = solicitudesDelBroker.stream()
                .map(CandidatoTarea::getEntidadId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, int[]> porSolicitud = new java.util.HashMap<>();
        for (Object[] fila : solicitudes.documentosPorConformar(idOrganizacion, ids)) {
            porSolicitud.put(((Number) fila[0]).longValue(), new int[]{
                    ((Number) fila[1]).intValue(), ((Number) fila[2]).intValue()});
        }
        return porSolicitud;
    }

    private static String nz(String codigo, Long id) {
        return codigo == null || codigo.isBlank() ? String.valueOf(id) : codigo;
    }

    /**
     * Compone un asunto con su interpretación.
     *
     * <p>El {@code freno} es {@code null} cuando de verdad no frena nada — la
     * comisión sin cobrar es dinero que espera, pero no deja ningún proceso
     * parado. Inventarle una consecuencia convertiría la marca roja en ruido.
     */
    private static AsuntoDelBroker asunto(String tipo, String entidadTipo, CandidatoTarea c,
                                          LocalDate hoy, String falta, String freno,
                                          String destino) {
        return asunto(tipo, entidadTipo, c, hoy, falta, freno, destino, null);
    }

    private static AsuntoDelBroker asunto(String tipo, String entidadTipo, CandidatoTarea c,
                                          LocalDate hoy, String falta, String freno,
                                          String destino, Avance avance) {
        int dias = c.getFechaPlazo() == null ? 0
                : (int) Math.max(0, ChronoUnit.DAYS.between(c.getFechaPlazo(), hoy));

        List<Hecho> hechos = new ArrayList<>();
        hechos.add(new Hecho(EstadoDelHecho.FALTA, falta));
        if (dias > 0) {
            hechos.add(new Hecho(EstadoDelHecho.DATO, "Esperando desde hace " + dias + " dias"));
        }
        if (freno != null) {
            hechos.add(new Hecho(EstadoDelHecho.FRENO, freno));
        }

        LadoDeLaOperacion.Ubicacion ubicacion = LadoDeLaOperacion.de(entidadTipo);
        return new AsuntoDelBroker(
                idDelBroker(entidadTipo, c.getEntidadId()),
                tipo, entidadTipo, c.getEntidadId(), c.getEntidadCodigo(),
                destino, dias,
                ubicacion == null ? null : ubicacion.lado().name(),
                ubicacion == null ? null : ubicacion.paso(),
                new Interpretacion(ComoEsta.de(avance, hechos), List.of(),
                        // Sin expediente todavia no hay cuatro renglones que
                        // relacionar, y una sintesis de uno solo seria un eco
                        // (E2.4). Se respeta el null.
                        InterpretacionDelAsunto.sintetizar(List.of())));
    }

    /**
     * <b>El id del asunto lleva el rol que lo mira.</b>
     *
     * <p>El sufijo no es decorativo. Av. Arequipa está en la cola del agente y
     * puede estar también en la del broker, y son dos asuntos distintos: uno
     * dice «recontacta», el otro dice «aprueba». Con un id compartido, la regla
     * del hogar único los trataría como el mismo y el encargo saldría dos veces
     * (D-E2-1 §7.1).
     */
    private static String idDelBroker(String entidadTipo, Long entidadId) {
        return entidadTipo + ":" + entidadId + SUFIJO_BROKER;
    }
}
