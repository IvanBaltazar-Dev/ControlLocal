package com.controllocal.service.disponibilidad;

import com.controllocal.service.disponibilidad.DisponibilidadContextualService.PrecisionHecho;
import com.controllocal.service.matching.ContratoMatching.Causa;
import com.controllocal.service.matching.ContratoMatching.ConfirmacionDisponibilidad;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** D1/D2-16/21/31: un único selector temporal para cartera propia y proyección Network autorizada. */
public final class SelectorConfirmacionVigente {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private SelectorConfirmacionVigente() { }

    /** Sólo los campos admitidos para seleccionar; no identidad, fuente o evidencia del confirmante. */
    public record Hecho(long id, long version, boolean disponible, PrecisionHecho precision,
                         Instant baseFrescura, LocalDate fechaSinHora, Instant registradoEn) { }
    public record Seleccion(long versionSecuencial, Hecho vigente, Causa indeterminacion, Set<Long> posiblesUltimos) {
        public Seleccion { posiblesUltimos = Set.copyOf(posiblesUltimos); }
        public ConfirmacionDisponibilidad paraMatching() {
            if (indeterminacion != null) return new ConfirmacionDisponibilidad(null, false, null, indeterminacion);
            return vigente == null ? null : new ConfirmacionDisponibilidad(vigente.baseFrescura(),
                    !vigente.disponible(), "confirmacion:" + vigente.id());
        }
    }

    public static Seleccion seleccionar(List<Hecho> admitidos) {
        List<Hecho> hechos = List.copyOf(admitidos);
        if (hechos.isEmpty()) return new Seleccion(0, null, null, Set.of());
        for (Hecho hecho : hechos) validar(hecho);
        long version = hechos.stream().mapToLong(Hecho::version).max().orElseThrow();
        Instant inicioAcreditado = hechos.stream().map(Hecho::baseFrescura).max(Comparator.naturalOrder()).orElseThrow();
        List<Hecho> posibles = hechos.stream().filter(h -> puedeSerUltimo(h, inicioAcreditado)).toList();
        Set<Long> ids = posibles.stream().map(Hecho::id).collect(Collectors.toSet());
        if (posibles.stream().map(Hecho::disponible).distinct().count() > 1)
            return new Seleccion(version, null, Causa.CONFLICTO, ids);
        Hecho vigente = posibles.stream().max(Comparator.comparing(Hecho::baseFrescura)
                .thenComparingLong(Hecho::version)).orElseThrow();
        return new Seleccion(version, vigente, null, ids);
    }

    private static boolean puedeSerUltimo(Hecho h, Instant otroInicio) {
        if (h.precision() == PrecisionHecho.INSTANTE) return !h.baseFrescura().isBefore(otroInicio);
        Instant finDiaExclusivo = h.fechaSinHora().plusDays(1).atStartOfDay(LIMA).toInstant();
        // RegistradoEn aporta una COTA de no futuro, nunca se declara como hora del hecho.
        if (h.registradoEn().isBefore(finDiaExclusivo)) return !h.registradoEn().isBefore(otroInicio);
        return finDiaExclusivo.isAfter(otroInicio);
    }

    private static void validar(Hecho h) {
        if (h.id() <= 0 || h.version() <= 0 || h.precision() == null || h.baseFrescura() == null || h.registradoEn() == null
                || h.baseFrescura().isAfter(h.registradoEn())) throw new IllegalStateException("Confirmación persistida incoherente.");
        if (h.precision() == PrecisionHecho.FECHA && (h.fechaSinHora() == null
                || !h.fechaSinHora().atStartOfDay(LIMA).toInstant().equals(h.baseFrescura()))
                || h.precision() == PrecisionHecho.INSTANTE && h.fechaSinHora() != null)
            throw new IllegalStateException("La precisión de la confirmación no está acreditada.");
    }
}
