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

    /**
     * <b>Una fecha dentro de una frase</b>: «el 5 de septiembre de 2026».
     *
     * <p>Vive aqui y no en el cliente por la misma razon que los rotulos: el
     * Core escribe la frase entera —{@code motivoNoEditable}— y con dos
     * consumidores, componerla dos veces serian dos redacciones que se separan
     * (D-A-1 §5). Sin {@code DateTimeFormatter} con {@code Locale}: el nombre
     * del mes depende de la configuracion de la maquina, y una frase del
     * producto no puede cambiar segun donde corra el servidor.
     *
     * @return {@code null} si no hay fecha. Una fecha que no consta no se
     *         escribe como «desconocida» dentro de una frase: la frase se
     *         escribe sin ella
     */
    public static String enPalabras(OffsetDateTime valor) {
        if (valor == null) {
            return null;
        }
        LocalDateTime fecha = local(valor);
        return fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1]
                + " de " + fecha.getYear();
    }

    private static final String[] MESES = {
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
}
