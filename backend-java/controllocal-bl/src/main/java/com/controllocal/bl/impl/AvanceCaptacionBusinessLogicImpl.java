package com.controllocal.bl.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.controllocal.bl.AvanceCaptacionBusinessLogic;
import com.controllocal.bl.InteraccionComercialBusinessLogic;
import com.controllocal.bl.MotivoNoContinuidadBusinessLogic;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.VisitaBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.MotivoNoContinuidad;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.enums.EstadoVisita;

/**
 * Deriva el resumen de avance de una captacion (consultas, visitas, objeciones) a partir de la
 * actividad real, sin contadores manuales. Mismo criterio que el reporte de avance global de
 * IndicadoresRest, pero acotado a una captacion y a un rango de fechas.
 */
public class AvanceCaptacionBusinessLogicImpl implements AvanceCaptacionBusinessLogic {

    private final OportunidadComercialBusinessLogic oportunidades;
    private final InteraccionComercialBusinessLogic interacciones;
    private final VisitaBusinessLogic visitas;
    private final MotivoNoContinuidadBusinessLogic motivos;

    public AvanceCaptacionBusinessLogicImpl() {
        this(new OportunidadComercialBusinessLogicImpl(), new InteraccionComercialBusinessLogicImpl(),
                new VisitaBusinessLogicImpl(), new MotivoNoContinuidadBusinessLogicImpl());
    }

    public AvanceCaptacionBusinessLogicImpl(OportunidadComercialBusinessLogic oportunidades,
            InteraccionComercialBusinessLogic interacciones, VisitaBusinessLogic visitas,
            MotivoNoContinuidadBusinessLogic motivos) {
        this.oportunidades = oportunidades;
        this.interacciones = interacciones;
        this.visitas = visitas;
        this.motivos = motivos;
    }

    @Override
    public ResumenAvance resumen(Long idCaptacion, LocalDate desde, LocalDate hasta) {
        BusinessValidations.id(idCaptacion, "El id de captacion");
        List<Long> ids = List.of(idCaptacion);

        Set<Long> idsOportunidad = oportunidades.listarPorCaptaciones(ids).stream()
                .map(OportunidadComercial::getIdOportunidad)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int consultas = contarConsultas(idCaptacion, idsOportunidad, desde, hasta);
        int visitasRealizadas = contarVisitasRealizadas(ids, desde, hasta);
        String objeciones = resumirObjeciones(idsOportunidad, desde, hasta);

        return new ResumenAvance(consultas, visitasRealizadas, objeciones);
    }

    // Consultas = interacciones ligadas a la captacion o a sus oportunidades, dentro del rango,
    // deduplicadas por id para que una misma interaccion no cuente dos veces.
    private int contarConsultas(Long idCaptacion, Set<Long> idsOportunidad, LocalDate desde, LocalDate hasta) {
        Map<Long, LocalDateTime> fechaPorInteraccion = new HashMap<>();
        for (InteraccionComercial i : interacciones.listarPorCaptacion(idCaptacion)) {
            if (i.getIdInteraccion() != null) {
                fechaPorInteraccion.put(i.getIdInteraccion(), i.getFechaHora());
            }
        }
        for (Long idOportunidad : idsOportunidad) {
            for (InteraccionComercial i : interacciones.listarPorOportunidad(idOportunidad)) {
                if (i.getIdInteraccion() != null) {
                    fechaPorInteraccion.put(i.getIdInteraccion(), i.getFechaHora());
                }
            }
        }
        return (int) fechaPorInteraccion.values().stream()
                .filter(f -> enRango(f != null ? f.toLocalDate() : null, desde, hasta))
                .count();
    }

    private int contarVisitasRealizadas(List<Long> idsCaptacion, LocalDate desde, LocalDate hasta) {
        return (int) visitas.listarPorCaptaciones(idsCaptacion).stream()
                .filter(v -> v.getEstado() == EstadoVisita.REALIZADA)
                .filter(v -> enRango(v.getFechaVisita(), desde, hasta))
                .count();
    }

    // Objeciones = motivos de no continuidad de las oportunidades de la captacion en el rango,
    // como un breakdown ordenado por frecuencia (ej. "Precio (2), Ubicacion (1)"). "" si no hay.
    private String resumirObjeciones(Set<Long> idsOportunidad, LocalDate desde, LocalDate hasta) {
        if (idsOportunidad.isEmpty()) {
            return "";
        }
        Map<String, Integer> conteo = new HashMap<>();
        for (MotivoNoContinuidad m : motivos.listarTodos()) {
            Long idOportunidad = m.getOportunidadComercial() != null
                    ? m.getOportunidadComercial().getIdOportunidad() : null;
            if (idOportunidad == null || !idsOportunidad.contains(idOportunidad) || m.getRazonPrincipal() == null) {
                continue;
            }
            LocalDate fecha = m.getFechaHora() != null ? m.getFechaHora().toLocalDate() : null;
            if (!enRango(fecha, desde, hasta)) {
                continue;
            }
            conteo.merge(m.getRazonPrincipal().getDescripcion(), 1, Integer::sum);
        }
        return conteo.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
    }

    // Rango inclusive; null en un extremo lo deja abierto. Sin fecha, no cuenta.
    private static boolean enRango(LocalDate fecha, LocalDate desde, LocalDate hasta) {
        if (fecha == null) {
            return false;
        }
        if (desde != null && fecha.isBefore(desde)) {
            return false;
        }
        return hasta == null || !fecha.isAfter(hasta);
    }
}
