package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.DocumentoSolicitud;
import com.controllocal.domain.comercial.Oferta;
import com.controllocal.domain.inmueble.FotoPropiedad;
import com.controllocal.domain.comun.EstadosDominio.Codigo;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.domain.comun.EstadosDominio.EstadoCaptacion;
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

    private static final Map<String, Set<String>> CODIGOS = Map.ofEntries(
            Map.entry("PROPIEDAD", codigos(EstadoRegistroPropiedad.values())),
            Map.entry("DISPONIBILIDAD_PROPIEDAD", codigos(DisponibilidadComercial.values())),
            Map.entry("CAPTACION", codigos(EstadoCaptacion.values())),
            Map.entry("PETICION_REVISION_ENCARGO", Set.of("P", "O", "A", "R")),
            Map.entry("SOLICITUD_FIN_ENCARGO", Set.of("P", "O", "A", "R", "C")),
            Map.entry("PROSPECCION", codigos(EstadoProspeccion.values())),
            Map.entry("DEMANDA", Set.of("A", "R", "F")),
            Map.entry("SOLICITUD_COLABORACION", Set.of("P", "A", "R")),
            Map.entry("PARTICIPACION_NETWORK", Set.of("N", "A", "S", "I")),
            Map.entry("SOLICITUD_NETWORK", Set.of("P", "O", "A", "R")),
            Map.entry("OPORTUNIDAD", codigos(EstadoOportunidad.values())),
            Map.entry("VISITA", codigos(EstadoVisita.values())),
            Map.entry("SOLICITUD_ALQUILER", codigos(EstadoSolicitud.values())),
            Map.entry("CONTRATO_ALQUILER", codigos(EstadoContrato.values())),
            Map.entry("DOCUMENTO_SOLICITUD", Set.of(DocumentoSolicitud.REGISTRADO,
                    DocumentoSolicitud.OBSERVADO, DocumentoSolicitud.VALIDADO)),
            Map.entry(FotoPropiedad.ENTIDAD_TIPO, FotoPropiedad.ESTADOS),
            // La OFERTA declara su vocabulario en la entidad, como
            // DOCUMENTO_SOLICITUD y por la misma razon: no todo agregado con
            // maquina necesita un enum en EstadosDominio, y anadir uno obliga a
            // documentarlo en una matriz que este corte no gobierna.
            Map.entry(Oferta.ENTIDAD_TIPO, Oferta.ESTADOS)
    );

    private static final Map<String, Map<String, Set<String>>> GRAFOS = Map.ofEntries(
            // CAT-E18/RF030: el retiro o fin de una demanda no se reabre por edición.
            Map.entry("DEMANDA", Map.of("A", Set.of("R", "F"))),
            // CAT-E19/figuras47 y82: las dos decisiones legítimas producen el desenlace.
            Map.entry("SOLICITUD_COLABORACION", Map.of("P", Set.of("A", "R"))),
            Map.entry("PARTICIPACION_NETWORK", Map.of("N",Set.of("A"),"A",Set.of("S","I"),
                    "S",Set.of("A","I"),"I",Set.of("A"))),
            Map.entry("SOLICITUD_NETWORK", Map.of("P",Set.of("O","A","R"))),
            // Retirar de la galería conserva el original y su vínculo histórico. No hay reactivación implícita.
            Map.entry(FotoPropiedad.ENTIDAD_TIPO, Map.of(FotoPropiedad.ACTIVA, Set.of(FotoPropiedad.RETIRADA))),
            // La OFERTA: de PRESENTADA salen los cuatro desenlaces y ninguno
            // vuelve. Rechazada NO es lo mismo que superada -- una la responde
            // un no, la otra una cifra nueva -- ni retirada lo mismo que
            // rechazada: quien se echa atras es el que ofrecio, no el que
            // escuchaba. Fundirlos borraria de la historia quien cedio.
            Map.entry(Oferta.ENTIDAD_TIPO, Map.of(
                    Oferta.PRESENTADA, Set.of(Oferta.ACEPTADA, Oferta.RECHAZADA,
                            Oferta.SUPERADA, Oferta.RETIRADA))),
            Map.entry("PROPIEDAD", Map.of(c(EstadoRegistroPropiedad.ACTIVO),
                    Set.of(c(EstadoRegistroPropiedad.INACTIVO)),
                    c(EstadoRegistroPropiedad.INACTIVO), Set.of(c(EstadoRegistroPropiedad.ACTIVO)))),
            // VENDIDO (V91) entra desde los tres estados en los que un inmueble
            // puede estar cuando se cierra su venta -- disponible, reservado o
            // alquilado, porque vender con inquilino dentro es un caso real -- y
            // NO sale a ninguno: no hay operacion que "desvenda" un inmueble, y
            // declarar una transicion sin productor seria vocabulario que nadie
            // escribe. RETIRADO si vuelve a DISPONIBLE, y esa asimetria es el
            // punto: retirar es una decision reversible, vender no lo es.
            Map.entry("DISPONIBILIDAD_PROPIEDAD", Map.of(
                    c(DisponibilidadComercial.DISPONIBLE), Set.of(
                            c(DisponibilidadComercial.RESERVADO),
                            c(DisponibilidadComercial.ALQUILADO),
                            c(DisponibilidadComercial.RETIRADO),
                            c(DisponibilidadComercial.VENDIDO)),
                    c(DisponibilidadComercial.RESERVADO), Set.of(
                            c(DisponibilidadComercial.DISPONIBLE),
                            c(DisponibilidadComercial.ALQUILADO),
                            c(DisponibilidadComercial.RETIRADO),
                            c(DisponibilidadComercial.VENDIDO)),
                    c(DisponibilidadComercial.ALQUILADO), Set.of(
                            c(DisponibilidadComercial.DISPONIBLE),
                            c(DisponibilidadComercial.RETIRADO),
                            c(DisponibilidadComercial.VENDIDO)),
                    c(DisponibilidadComercial.RETIRADO), Set.of(
                            c(DisponibilidadComercial.DISPONIBLE),
                            c(DisponibilidadComercial.VENDIDO)))),
            Map.entry("CAPTACION", Map.of(
                    c(EstadoCaptacion.EN_PREPARACION), Set.of(c(EstadoCaptacion.APROBADO),
                            c(EstadoCaptacion.FINALIZADO), c(EstadoCaptacion.RESCINDIDO)),
                    c(EstadoCaptacion.APROBADO), Set.of(c(EstadoCaptacion.FINALIZADO),
                            c(EstadoCaptacion.RESCINDIDO)))),
            Map.entry("PETICION_REVISION_ENCARGO", Map.of(
                    "P", Set.of("O", "A", "R"), "O", Set.of("P"))),
            Map.entry("SOLICITUD_FIN_ENCARGO", Map.of(
                    "P", Set.of("O", "A", "R", "C"), "O", Set.of("P", "C"))),
            Map.entry("PROSPECCION", Map.of(
                    c(EstadoProspeccion.PROSPECTO), Set.of(c(EstadoProspeccion.CONTACTADO), c(EstadoProspeccion.REUNION), c(EstadoProspeccion.PROPUESTA_ENTREGADA), c(EstadoProspeccion.SEGUIMIENTO), c(EstadoProspeccion.CAPTADO), c(EstadoProspeccion.DESCARTADO)),
                    c(EstadoProspeccion.CONTACTADO), Set.of(c(EstadoProspeccion.REUNION), c(EstadoProspeccion.PROPUESTA_ENTREGADA), c(EstadoProspeccion.SEGUIMIENTO), c(EstadoProspeccion.CAPTADO), c(EstadoProspeccion.DESCARTADO)),
                    c(EstadoProspeccion.REUNION), Set.of(c(EstadoProspeccion.PROPUESTA_ENTREGADA), c(EstadoProspeccion.SEGUIMIENTO), c(EstadoProspeccion.CAPTADO), c(EstadoProspeccion.DESCARTADO)),
                    c(EstadoProspeccion.PROPUESTA_ENTREGADA), Set.of(c(EstadoProspeccion.SEGUIMIENTO), c(EstadoProspeccion.CAPTADO), c(EstadoProspeccion.DESCARTADO)),
                    c(EstadoProspeccion.SEGUIMIENTO), Set.of(c(EstadoProspeccion.CONTACTADO), c(EstadoProspeccion.REUNION), c(EstadoProspeccion.PROPUESTA_ENTREGADA), c(EstadoProspeccion.CAPTADO), c(EstadoProspeccion.DESCARTADO)))),
            Map.entry("OPORTUNIDAD", Map.of(c(EstadoOportunidad.ABIERTA), Set.of(c(EstadoOportunidad.SOLICITUD_CREADA), c(EstadoOportunidad.NO_CONTINUA), c(EstadoOportunidad.FINALIZADA_EXITOSA), c(EstadoOportunidad.FINALIZADA_NO_FAVORABLE)),
                    c(EstadoOportunidad.SOLICITUD_CREADA), Set.of(c(EstadoOportunidad.NO_CONTINUA), c(EstadoOportunidad.FINALIZADA_EXITOSA), c(EstadoOportunidad.FINALIZADA_NO_FAVORABLE)))),
            Map.entry("VISITA", Map.of(c(EstadoVisita.PROGRAMADA), Set.of(c(EstadoVisita.REPROGRAMADA), c(EstadoVisita.CANCELADA), c(EstadoVisita.NO_REALIZADA), c(EstadoVisita.REALIZADA)),
                    c(EstadoVisita.REPROGRAMADA), Set.of(c(EstadoVisita.REPROGRAMADA), c(EstadoVisita.CANCELADA), c(EstadoVisita.NO_REALIZADA), c(EstadoVisita.REALIZADA)),
                    c(EstadoVisita.REALIZADA),Set.of(c(EstadoVisita.NO_REALIZADA)),
                    c(EstadoVisita.NO_REALIZADA),Set.of(c(EstadoVisita.REALIZADA)))),
            Map.entry("SOLICITUD_ALQUILER", Map.of(
                    c(EstadoSolicitud.REGISTRADA), Set.of(c(EstadoSolicitud.EN_REVISION), c(EstadoSolicitud.DESISTIDA)),
                    c(EstadoSolicitud.OBSERVADA), Set.of(c(EstadoSolicitud.EN_REVISION), c(EstadoSolicitud.DESISTIDA)),
                    c(EstadoSolicitud.EN_REVISION), Set.of(c(EstadoSolicitud.OBSERVADA), c(EstadoSolicitud.APROBADA), c(EstadoSolicitud.RECHAZADA), c(EstadoSolicitud.DESISTIDA)),
                    c(EstadoSolicitud.APROBADA), Set.of(c(EstadoSolicitud.CERRADA), c(EstadoSolicitud.DESISTIDA)))),
            Map.entry("CONTRATO_ALQUILER", Map.of(
                    c(EstadoContrato.EN_PROCESO), Set.of(c(EstadoContrato.FIRMADO), c(EstadoContrato.ANULADO)),
                    c(EstadoContrato.FIRMADO), Set.of(c(EstadoContrato.VIGENTE), c(EstadoContrato.ANULADO)),
                    c(EstadoContrato.VIGENTE), Set.of(c(EstadoContrato.FINALIZADO), c(EstadoContrato.RESCINDIDO), c(EstadoContrato.RENOVADO)))),
            Map.entry("DOCUMENTO_SOLICITUD", Map.of("R", Set.of("O", "V"), "O", Set.of("R", "V")))
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
