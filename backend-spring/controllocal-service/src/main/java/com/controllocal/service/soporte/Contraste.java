package com.controllocal.service.soporte;

import java.math.BigDecimal;

/**
 * Donde cae un dato respecto de <b>nuestra propia operacion</b>. Nunca respecto
 * del sector.
 *
 * <h2>La regla que este tipo existe para hacer cumplir</h2>
 *
 * <p>Un contraste que no tiene muestra <b>no se dibuja</b>. Se declara
 * {@link Motivo#SIN_REFERENCIA_INTERNA_SUFICIENTE}, se conserva la N y se dice
 * cuantas observaciones habia. Lo que no se hace nunca es convertir dos filas
 * propias en una supuesta senal de mercado.
 *
 * <p>No es prudencia abstracta: se midio el 2026-08-19 y la mejor celda zona x
 * banda de la cartera tenia <b>cuatro</b> propiedades, con catorce de las
 * diecisiete celdas a una sola. Con eso, «el rango de Miraflores» son dos puntos
 * sueltos con un nombre grande encima.
 *
 * <h2>Objeto, portafolio y mercado son tres escalas</h2>
 *
 * <p>Este tipo solo habla de las dos primeras: este inmueble, contra la cartera
 * de esta corredora. La tercera —lo que hace el mercado— necesita una cobertura
 * que BROX no tiene todavia, y fingirla aqui seria el salto que el producto
 * existe para no dar. Por eso ningun texto de aqui puede decir «sector»,
 * «mercado» ni «industria»: no es una preferencia de estilo, es que seria falso.
 */
public record Contraste(Forma forma,
                        Motivo motivo,
                        BigDecimal minimo,
                        BigDecimal maximo,
                        BigDecimal valor,
                        Integer posicionPorcentaje,
                        String moneda,
                        String zona,
                        String banda,
                        int observaciones) {

    /** Como se compara. */
    public enum Forma {
        /** Donde cae dentro de un rango real de la cartera. */
        POSICION_EN_RANGO,
        /** Contra una media propia. */
        DESVIACION_CONTRA_MEDIA,
        /** No se compara: no hay con que. */
        NINGUNA
    }

    /** Por que no hay contraste. Solo tiene sentido con {@link Forma#NINGUNA}. */
    public enum Motivo {
        /** Hay contraste: el motivo no aplica. */
        NINGUNO,
        /**
         * Hay observaciones, pero no las bastantes para que un rango signifique
         * algo. Se conserva cuantas hay, porque «3 propiedades» informa y
         * «no hay datos» no.
         */
        SIN_REFERENCIA_INTERNA_SUFICIENTE,
        /**
         * No hay ni una observacion valida. Distinto de lo anterior: alli falta
         * volumen, aqui falta el hecho.
         */
        SIN_OBSERVACIONES,
        /**
         * Falta lo que hace comparable a la propiedad —su zona o su metraje—, asi
         * que no se sabe contra que grupo compararla.
         */
        SIN_GRUPO_COMPARABLE
    }

    /** Un rango real, con la posicion del valor dentro. */
    public static Contraste enRango(BigDecimal minimo, BigDecimal maximo, BigDecimal valor,
                                    String moneda, String zona, String banda, int observaciones) {
        return new Contraste(Forma.POSICION_EN_RANGO, Motivo.NINGUNO,
                minimo, maximo, valor, posicion(minimo, maximo, valor),
                moneda, zona, banda, observaciones);
    }

    /** Hay observaciones, pero pocas para concluir. Se conserva cuantas. */
    public static Contraste sinReferenciaSuficiente(String zona, String banda, int observaciones) {
        return new Contraste(Forma.NINGUNA,
                observaciones == 0 ? Motivo.SIN_OBSERVACIONES
                        : Motivo.SIN_REFERENCIA_INTERNA_SUFICIENTE,
                null, null, null, null, null, zona, banda, observaciones);
    }

    /** No se sabe con que grupo compararla. */
    public static Contraste sinGrupoComparable() {
        return new Contraste(Forma.NINGUNA, Motivo.SIN_GRUPO_COMPARABLE,
                null, null, null, null, null, null, null, 0);
    }

    /**
     * Donde cae el valor dentro del rango, en porcentaje.
     *
     * <p>Con minimo y maximo iguales —todas las observaciones al mismo importe—
     * no hay recorrido y la posicion es {@code null}: decir «esta en el 0 %» o
     * «en el 100 %» de un rango de ancho cero seria un numero sin significado.
     */
    private static Integer posicion(BigDecimal minimo, BigDecimal maximo, BigDecimal valor) {
        if (minimo == null || maximo == null || valor == null) {
            return null;
        }
        BigDecimal ancho = maximo.subtract(minimo);
        if (ancho.signum() <= 0) {
            return null;
        }
        BigDecimal dentro = valor.subtract(minimo);
        int posicion = dentro.multiply(BigDecimal.valueOf(100))
                .divide(ancho, 0, java.math.RoundingMode.HALF_UP)
                .intValue();
        // Una renta fuera del rango propio es informacion, no un error: se
        // pega al borde para poder dibujarla, y el importe sigue viajando.
        return Math.max(0, Math.min(100, posicion));
    }

    /** Si hay algo que dibujar. */
    public boolean concluye() {
        return forma != Forma.NINGUNA;
    }
}
