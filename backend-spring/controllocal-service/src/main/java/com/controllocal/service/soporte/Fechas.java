package com.controllocal.service.soporte;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Conversion al LocalDateTime del cable congelado: la v1 (MySQL DATETIME)
 * emitia fechas sin offset; la BD v2 guarda TIMESTAMPTZ, asi que las
 * respuestas se traducen a hora local del servidor antes de salir.
 */
public final class Fechas {

    private Fechas() {
    }

    public static LocalDateTime local(OffsetDateTime valor) {
        return valor == null ? null : valor.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
