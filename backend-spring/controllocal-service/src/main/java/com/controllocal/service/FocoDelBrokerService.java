package com.controllocal.service;

import com.controllocal.service.soporte.InterpretacionDelAsunto.Interpretacion;

import java.util.List;

/**
 * <b>Los asuntos del broker</b> (D-E2-5, E2.5).
 *
 * <p>La regla que gobierna esto está escrita en su decisión y conviene tenerla
 * delante:
 *
 * <blockquote>
 * La bandeja sigue sin ser un tablero de control. Y precisamente por eso el
 * broker tiene la suya. <b>Cada rol ve lo que él tiene que decidir, nunca lo que
 * otro tiene que hacer.</b>
 * </blockquote>
 *
 * <p>{@code GET /tareas} no se toca: sigue siendo del agente y sigue sin acceso
 * de ADMIN. Lo que cambia es que deja de ser la única bandeja del sistema.
 */
public interface FocoDelBrokerService {

    /** Aprobar u observar una captación es suyo y de nadie más. */
    String CAPTACION_POR_REVISAR = "CAPTACION_POR_REVISAR";

    /** Firmar la evaluación es «la más sensible de las 18» (matriz operación-rol). */
    String SOLICITUD_POR_EVALUAR = "SOLICITUD_POR_EVALUAR";

    /** Registrar el cobro es BROKER; en E2.2 este hecho vivía sin dueño. */
    String COMISION_SIN_COBRAR = "COMISION_SIN_COBRAR";

    /**
     * <b>Lo que separa la identidad del broker de la del agente.</b>
     *
     * <p>Av. Arequipa puede estar en las dos colas, y son dos asuntos distintos:
     * uno dice «recontacta», el otro dice «aprueba». Con un id compartido, la
     * regla del hogar único los trataría como el mismo y el encargo saldría dos
     * veces — pasó, y está documentado en D-E2-1 §7.1.
     */
    String SUFIJO_BROKER = "-b";

    /**
     * Un asunto del broker, ya interpretado y en el orden definitivo.
     *
     * @param id        identidad <b>del rol que mira</b>, con {@link #SUFIJO_BROKER}
     * @param destino   ruta real del SPA donde se decide
     * @param diasEsperando desde cuándo lleva esperando su decisión
     * @param freno     qué queda parado; {@code null} cuando no frena nada
     */
    record AsuntoDelBroker(String id, String tipo, String entidadTipo, Long entidadId,
                           String entidadCodigo, String destino, int diasEsperando,
                           String lado, String paso, Interpretacion interpretacion) {

        /** Lo que queda parado, leído de la interpretación. */
        public String freno() {
            return interpretacion == null ? null : interpretacion.comoEsta().hechos().stream()
                    .filter(h -> h.estado() == com.controllocal.service.soporte
                            .EstadoDelHecho.FRENO)
                    .map(h -> h.texto())
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Los asuntos vigentes del broker, del más urgente al menos.
     *
     * <p>Vacío para cualquier otro rol. <b>El TENANT_ADMIN no tiene asuntos</b>:
     * desde D-F4-5 no decide ninguna operación comercial, y un Inicio con
     * asuntos para quien no puede resolverlos es la definición de un tablero de
     * control.
     */
    List<AsuntoDelBroker> de(Actor actor);
}
