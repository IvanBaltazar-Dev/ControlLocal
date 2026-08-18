package com.controllocal.service.soporte;

import java.math.BigDecimal;

/**
 * El valor de una clave logica, ya resuelto y sin decir de donde salio.
 *
 * <p>Es lo que devuelve {@link LectorPorAutoridad}: el consumidor pide
 * {@code ambientes} y recibe esto, sin enterarse de si vino de una fila de
 * {@code atributo_propiedad} o de un campo canonico del agregado (D-E4-3).
 *
 * <p><b>Los tres huecos son excluyentes</b>, igual que en la tabla: exactamente
 * uno lleva valor. La forma se repite a proposito —no se colapsa todo a texto—
 * porque un numero que viaja como cadena pierde su tipo y lo recupera cada
 * consumidor a su manera, que es como se cuelan tres parseos distintos.
 */
public record ValorLogico(String texto, BigDecimal numero, Boolean booleano) {

    public static ValorLogico deTexto(String valor) {
        return valor == null || valor.isBlank() ? null : new ValorLogico(valor, null, null);
    }

    public static ValorLogico deNumero(BigDecimal valor) {
        return valor == null ? null : new ValorLogico(null, canonico(valor), null);
    }

    public static ValorLogico deBooleano(Boolean valor) {
        return valor == null ? null : new ValorLogico(null, null, valor);
    }

    /**
     * El numero sin la escala del ALMACEN.
     *
     * <p>{@code atributo_propiedad.valor_numero} es {@code NUMERIC(14,4)} para
     * todas las claves: una columna compartida no puede llevar la escala de
     * cada concepto. Devolver el valor crudo publicaria {@code 350.0000} donde
     * la columna espejo decia {@code 350.00} — es decir, publicaria la escala
     * del almacenamiento, que es justo lo que el consumidor no debe ver.
     *
     * <p>El {@code toPlainString()} intermedio no es adorno: sin el,
     * {@code stripTrailingZeros()} deja {@code 3.5E+2} y Jackson serializa un
     * BigDecimal por su {@code toString()}, asi que el cable llevaria notacion
     * cientifica donde iba un area.
     */
    private static BigDecimal canonico(BigDecimal valor) {
        return new BigDecimal(valor.stripTrailingZeros().toPlainString());
    }

    /** Como se muestra: sin ceros de mas ni {@code "true"} crudo. */
    public String comoTexto() {
        if (texto != null) {
            return texto;
        }
        if (numero != null) {
            return numero.toPlainString();
        }
        return booleano == null ? null : booleano.toString();
    }

    /** El entero, o {@code null} si la clave no llevaba numero. */
    public Integer comoEntero() {
        return numero == null ? null : numero.intValueExact();
    }
}
