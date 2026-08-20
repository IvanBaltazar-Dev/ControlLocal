package com.controllocal.persistence.query;

import java.time.LocalDate;

/**
 * Los hechos con los que se sostiene una conversación sobre una prospección.
 *
 * <h2>Por qué existe, si ya hay {@link ExpedienteDeLaPropiedad}</h2>
 *
 * <p>Porque una prospección es <b>anterior a la captación</b>: no hay encargo
 * firmado, no hay renta publicada y no hay visitas. Pedirle esos cuatro
 * renglones daría cuatro huecos, y un expediente de huecos es peor que ninguno.
 *
 * <p>Lo que sí tiene es historia propia —cuándo apareció, cuándo se le habló,
 * hasta dónde llegó y con quién se está tratando—, y esos son sus cuatro
 * renglones. La composición la hace {@code InterpreteDeLaBandeja}, la misma para
 * los dos tipos: aquí solo están los datos.
 *
 * <h2>Todas las fechas son nulables, y eso es información</h2>
 *
 * <p>Una prospección sin contacto todavía no es un error: es el estado normal de
 * las 42 que había en la base el 2026-08-19. El renglón lo dice —«sin contacto
 * registrado»— en vez de inventar una fecha o dejar el hueco.
 */
public interface ExpedienteDeLaProspeccion {

    Long getIdProspeccion();

    /** Código unitario: P prospecto, C contactado, R reunión, E propuesta… */
    String getEstado();

    /**
     * Desde cuándo existe, ya recortada a día.
     *
     * <p>La consulta es nativa y {@code timestamptz} llega como {@code Instant},
     * que Spring Data no proyecta a {@code OffsetDateTime}. Se corta en SQL con
     * {@code ::date} porque el renglón solo enseña el día: arrastrar la hora
     * sería pedir una conversión de zona horaria para un dato que nadie mira.
     */
    LocalDate getFechaRegistro();

    /** Cuándo se le habló por última vez. Nulo si todavía no se le habló. */
    LocalDate getFechaContacto();

    /** Nulo mientras no haya habido reunión. */
    LocalDate getFechaReunion();

    /** Nulo mientras no se haya entregado propuesta. */
    LocalDate getFechaPropuesta();

    /** Cuándo toca volver. Es lo previsto, no lo ocurrido. */
    LocalDate getFechaRecontacto();

    /** Con quién se está tratando. */
    String getPropietario();

    /** Qué inmueble se le está persiguiendo, para que el hecho nombre su objeto. */
    String getDireccion();

    String getDistrito();
}
