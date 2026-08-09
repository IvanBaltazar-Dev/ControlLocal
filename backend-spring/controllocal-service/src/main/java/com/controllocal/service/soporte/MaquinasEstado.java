package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.DocumentoSolicitud;
import com.controllocal.domain.comun.EstadosDominio.Codigo;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.domain.comun.EstadosDominio.EstadoCaptacion;
import com.controllocal.domain.comun.EstadosDominio.EstadoComision;
import com.controllocal.domain.comun.EstadosDominio.EstadoContrato;
import com.controllocal.domain.comun.EstadosDominio.EstadoOportunidad;
import com.controllocal.domain.comun.EstadosDominio.EstadoProspeccion;
import com.controllocal.domain.comun.EstadosDominio.EstadoRegistroPropiedad;
import com.controllocal.domain.comun.EstadosDominio.EstadoSolicitud;
import com.controllocal.domain.comun.EstadosDominio.EstadoVisita;
import com.controllocal.service.excepcion.ReglaNegocioException;

import java.util.Map;
import java.util.Set;

/** Grafos permitidos por agregado. Las letras nunca tienen semantica global. */
public final class MaquinasEstado {
    private MaquinasEstado() { }

    private static final Map<String, Set<String>> CODIGOS = Map.of(
            "PROPIEDAD", codigos(EstadoRegistroPropiedad.values()),
            "DISPONIBILIDAD_PROPIEDAD", codigos(DisponibilidadComercial.values()),
            "CAPTACION", codigos(EstadoCaptacion.values()),
            "PROSPECCION", codigos(EstadoProspeccion.values()),
            "OPORTUNIDAD", codigos(EstadoOportunidad.values()),
            "VISITA", codigos(EstadoVisita.values()),
            "SOLICITUD_ALQUILER", codigos(EstadoSolicitud.values()),
            "CONTRATO_ALQUILER", codigos(EstadoContrato.values()),
            "COMISION_LIQUIDACION", codigos(EstadoComision.values()),
            "DOCUMENTO_SOLICITUD", Set.of(DocumentoSolicitud.REGISTRADO,
                    DocumentoSolicitud.OBSERVADO, DocumentoSolicitud.VALIDADO)
    );

    private static final Map<String, Map<String, Set<String>>> GRAFOS = Map.of(
            "PROPIEDAD", Map.of(c(EstadoRegistroPropiedad.ACTIVO),
                    Set.of(c(EstadoRegistroPropiedad.INACTIVO)),
                    c(EstadoRegistroPropiedad.INACTIVO), Set.of(c(EstadoRegistroPropiedad.ACTIVO))),
            "DISPONIBILIDAD_PROPIEDAD", Map.of(
                    c(DisponibilidadComercial.DISPONIBLE), Set.of(
                            c(DisponibilidadComercial.RESERVADO),
                            c(DisponibilidadComercial.ALQUILADO),
                            c(DisponibilidadComercial.RETIRADO)),
                    c(DisponibilidadComercial.RESERVADO), Set.of(
                            c(DisponibilidadComercial.DISPONIBLE),
                            c(DisponibilidadComercial.ALQUILADO),
                            c(DisponibilidadComercial.RETIRADO)),
                    c(DisponibilidadComercial.ALQUILADO), Set.of(
                            c(DisponibilidadComercial.DISPONIBLE),
                            c(DisponibilidadComercial.RETIRADO)),
                    c(DisponibilidadComercial.RETIRADO), Set.of(
                            c(DisponibilidadComercial.DISPONIBLE))),
            "CAPTACION", Map.of(
                    c(EstadoCaptacion.PENDIENTE), Set.of(c(EstadoCaptacion.ACTIVA),
                            c(EstadoCaptacion.OBSERVADA), c(EstadoCaptacion.RECHAZADA)),
                    c(EstadoCaptacion.OBSERVADA), Set.of(c(EstadoCaptacion.PENDIENTE),
                            c(EstadoCaptacion.ACTIVA), c(EstadoCaptacion.RECHAZADA)),
                    c(EstadoCaptacion.ACTIVA), Set.of(c(EstadoCaptacion.CERRADA),
                            c(EstadoCaptacion.VENCIDA))),
            "PROSPECCION", Map.of(
                    c(EstadoProspeccion.PROSPECTO), Set.of(c(EstadoProspeccion.CONTACTADO), c(EstadoProspeccion.REUNION), c(EstadoProspeccion.PROPUESTA_ENTREGADA), c(EstadoProspeccion.SEGUIMIENTO), c(EstadoProspeccion.CAPTADO), c(EstadoProspeccion.DESCARTADO)),
                    c(EstadoProspeccion.CONTACTADO), Set.of(c(EstadoProspeccion.REUNION), c(EstadoProspeccion.PROPUESTA_ENTREGADA), c(EstadoProspeccion.SEGUIMIENTO), c(EstadoProspeccion.CAPTADO), c(EstadoProspeccion.DESCARTADO)),
                    c(EstadoProspeccion.REUNION), Set.of(c(EstadoProspeccion.PROPUESTA_ENTREGADA), c(EstadoProspeccion.SEGUIMIENTO), c(EstadoProspeccion.CAPTADO), c(EstadoProspeccion.DESCARTADO)),
                    c(EstadoProspeccion.PROPUESTA_ENTREGADA), Set.of(c(EstadoProspeccion.SEGUIMIENTO), c(EstadoProspeccion.CAPTADO), c(EstadoProspeccion.DESCARTADO)),
                    c(EstadoProspeccion.SEGUIMIENTO), Set.of(c(EstadoProspeccion.CONTACTADO), c(EstadoProspeccion.REUNION), c(EstadoProspeccion.PROPUESTA_ENTREGADA), c(EstadoProspeccion.CAPTADO), c(EstadoProspeccion.DESCARTADO))),
            "OPORTUNIDAD", Map.of(c(EstadoOportunidad.ABIERTA), Set.of(c(EstadoOportunidad.SOLICITUD_CREADA), c(EstadoOportunidad.NO_CONTINUA), c(EstadoOportunidad.FINALIZADA_EXITOSA), c(EstadoOportunidad.FINALIZADA_NO_FAVORABLE)),
                    c(EstadoOportunidad.SOLICITUD_CREADA), Set.of(c(EstadoOportunidad.NO_CONTINUA), c(EstadoOportunidad.FINALIZADA_EXITOSA), c(EstadoOportunidad.FINALIZADA_NO_FAVORABLE))),
            "VISITA", Map.of(c(EstadoVisita.PROGRAMADA), Set.of(c(EstadoVisita.REPROGRAMADA), c(EstadoVisita.CANCELADA), c(EstadoVisita.NO_REALIZADA), c(EstadoVisita.REALIZADA)),
                    c(EstadoVisita.REPROGRAMADA), Set.of(c(EstadoVisita.REPROGRAMADA), c(EstadoVisita.CANCELADA), c(EstadoVisita.NO_REALIZADA), c(EstadoVisita.REALIZADA))),
            "SOLICITUD_ALQUILER", Map.of(
                    c(EstadoSolicitud.REGISTRADA), Set.of(c(EstadoSolicitud.EN_REVISION), c(EstadoSolicitud.DESISTIDA)),
                    c(EstadoSolicitud.OBSERVADA), Set.of(c(EstadoSolicitud.EN_REVISION), c(EstadoSolicitud.DESISTIDA)),
                    c(EstadoSolicitud.EN_REVISION), Set.of(c(EstadoSolicitud.OBSERVADA), c(EstadoSolicitud.APROBADA), c(EstadoSolicitud.RECHAZADA), c(EstadoSolicitud.DESISTIDA)),
                    c(EstadoSolicitud.APROBADA), Set.of(c(EstadoSolicitud.CERRADA), c(EstadoSolicitud.DESISTIDA))),
            "CONTRATO_ALQUILER", Map.of(
                    c(EstadoContrato.EN_PROCESO), Set.of(c(EstadoContrato.FIRMADO), c(EstadoContrato.ANULADO)),
                    c(EstadoContrato.FIRMADO), Set.of(c(EstadoContrato.VIGENTE), c(EstadoContrato.ANULADO)),
                    c(EstadoContrato.VIGENTE), Set.of(c(EstadoContrato.FINALIZADO), c(EstadoContrato.RESCINDIDO), c(EstadoContrato.RENOVADO))),
            "COMISION_LIQUIDACION", Map.of(c(EstadoComision.PENDIENTE), Set.of(c(EstadoComision.PARCIAL), c(EstadoComision.COBRADA), c(EstadoComision.ANULADA)),
                    c(EstadoComision.PARCIAL), Set.of(c(EstadoComision.COBRADA), c(EstadoComision.ANULADA), c(EstadoComision.PENDIENTE))),
            "DOCUMENTO_SOLICITUD", Map.of("R", Set.of("O", "V"), "O", Set.of("R", "V"))
    );

    private static String c(Codigo estado) {
        return estado.codigo();
    }

    private static Set<String> codigos(Codigo[] estados) {
        return java.util.Arrays.stream(estados).map(Codigo::codigo)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static void validarCodigo(String entidad, String codigo) {
        Set<String> permitidos = CODIGOS.get(entidad);
        if (permitidos == null || !permitidos.contains(codigo)) {
            throw new ReglaNegocioException("Estado no documentado para " + entidad + ": " + codigo + ".");
        }
    }

    public static void validarTransicion(String entidad, String origen, String destino) {
        validarCodigo(entidad, destino);
        if (origen == null) return;
        validarCodigo(entidad, origen);
        if (origen.equals(destino)) return;
        Set<String> destinos = GRAFOS.getOrDefault(entidad, Map.of()).getOrDefault(origen, Set.of());
        if (!destinos.contains(destino)) {
            throw new ReglaNegocioException(
                    "Transicion no permitida para " + entidad + ": " + origen + " -> " + destino + ".");
        }
    }
}
