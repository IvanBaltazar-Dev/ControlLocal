package com.controllocal.service.soporte;

/**
 * <b>Lo que dejo una retirada</b> (4.P).
 *
 * <p>Hasta 4.P {@code retirar} devolvia un {@code boolean} —«¿era una clave del
 * catalogo?»— y el valor que borraba <b>se perdia sin que nadie lo hubiera
 * visto</b>. Con el linaje por valor eso deja de ser aceptable: la retirada es
 * la superficie que decide la forma del modelo, porque la fila vigente
 * desaparece y lo unico que queda de ese dato es lo que se anoto al quitarlo.
 *
 * <p>Se devuelve ademas de anotarse porque son dos cosas distintas: anotarlo es
 * la garantia, devolverlo es lo que permite comprobarla desde fuera sin leer la
 * tabla de linaje.
 *
 * @param gobernada {@code false} si la clave no esta en el catalogo. El llamante
 *                  lo usa para probar el otro espacio de nombres —los campos
 *                  logicos que publica la propia ficha— antes de rechazarla
 * @param hallado   el valor que se quito, o {@code null} si la clave estaba en el
 *                  catalogo y no tenia ninguno. La diferencia importa: «se
 *                  retiro X» y «no habia nada que retirar» son dos hechos
 */
public record ValorRetirado(boolean gobernada, ValorLogico hallado) {

    /** La clave no esta en el catalogo: no es de este espacio de nombres. */
    public static final ValorRetirado NO_ES_DEL_CATALOGO = new ValorRetirado(false, null);

    /** Se retiro una clave gobernada, y esto es lo que tenia. */
    public static ValorRetirado de(ValorLogico hallado) {
        return new ValorRetirado(true, hallado);
    }
}
