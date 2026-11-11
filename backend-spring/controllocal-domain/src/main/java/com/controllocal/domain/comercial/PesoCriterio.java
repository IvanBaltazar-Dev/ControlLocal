package com.controllocal.domain.comercial;

import java.util.Locale;

/**
 * <b>Cuanto pesa lo que el cliente pide</b> (V92, D-M-3).
 *
 * <p>Es la distincion que hace util el resultado de un matcher, y la que el
 * modelo universal declaro desde el principio:
 *
 * <pre>
 *   INDISPENSABLE  si no se cumple, DESCARTA
 *   DESEABLE       si no se cumple, solo BAJA el puntaje
 * </pre>
 *
 * <p>Sin ella, un cruce solo sabe devolver un porcentaje, y un 80 % no dice si
 * lo que falta es el ascensor o la licencia de funcionamiento. Con ella se
 * puede decir <i>«encaja en 4 de 5; falta confirmar la potencia»</i>, que es lo
 * que alguien puede usar para decidir a quien llama.
 *
 * <p><b>Sin valor por defecto.</b> Un criterio cuyo peso no se declaro no es
 * «deseable por si acaso»: es un criterio a medias, y la puerta que lo acepta
 * es la que acaba descartando ofertas por un requisito que nadie dijo que fuera
 * obligatorio.
 */
public enum PesoCriterio {

    /** Si no se cumple, la oferta queda fuera y se dice por cual. */
    INDISPENSABLE("I", "Indispensable"),

    /** Si no se cumple, la oferta sigue en la lista con menos puntaje. */
    DESEABLE("D", "Deseable");

    private final String codigo;
    private final String rotulo;

    PesoCriterio(String codigo, String rotulo) {
        this.codigo = codigo;
        this.rotulo = rotulo;
    }

    public String codigo() {
        return codigo;
    }

    public String rotulo() {
        return rotulo;
    }

    public boolean descarta() {
        return this == INDISPENSABLE;
    }

    /** El peso a partir del codigo persistido o del nombre. Sin defecto. */
    public static PesoCriterio desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "Falta el peso del criterio: declara INDISPENSABLE o DESEABLE. No se asume "
                            + "ninguno, porque uno descarta la oferta y el otro no.");
        }
        String limpio = valor.trim().toUpperCase(Locale.ROOT);
        for (PesoCriterio peso : values()) {
            if (peso.codigo.equals(limpio) || peso.name().equals(limpio)) {
                return peso;
            }
        }
        throw new IllegalArgumentException(
                "Peso de criterio desconocido: \"" + valor + "\". Solo existen INDISPENSABLE y "
                        + "DESEABLE.");
    }
}
