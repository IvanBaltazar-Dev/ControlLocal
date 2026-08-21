package com.controllocal.service.soporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * El valor de una clave logica, ya resuelto y sin decir de donde salio.
 *
 * <p>Es lo que devuelve {@link LectorPorAutoridad}: el consumidor pide
 * {@code ambientes} y recibe esto, sin enterarse de si vino de una fila de
 * {@code atributo_propiedad} o de un campo canonico del agregado (D-E4-3).
 *
 * <p><b>Los huecos son excluyentes</b>, igual que en la tabla: exactamente uno
 * lleva valor. La forma se repite a proposito —no se colapsa todo a texto—
 * porque un numero que viaja como cadena pierde su tipo y lo recupera cada
 * consumidor a su manera, que es como se cuelan tres parseos distintos.
 *
 * <h2>Los dos que no son escalares (V72)</h2>
 * {@code moneda} <b>no</b> es un hueco mas: acompana a {@code numero} y solo
 * viene con el, porque un importe sin su moneda no es un importe. Y
 * {@code valores} es la unica forma que no es un escalar -- es un multivalor,
 * y por eso no se puede representar con {@code texto} sin volver al defecto que
 * este corte cierra: «agua si, desague no» y «agua no, desague si» son la misma
 * cadena para cualquier comparacion.
 */
public record ValorLogico(String texto, BigDecimal numero, Boolean booleano,
                          LocalDate fecha, String moneda, List<String> valores) {

    public static ValorLogico deTexto(String valor) {
        return valor == null || valor.isBlank()
                ? null : new ValorLogico(valor, null, null, null, null, null);
    }

    public static ValorLogico deNumero(BigDecimal valor) {
        return valor == null ? null : new ValorLogico(null, canonico(valor), null, null, null, null);
    }

    public static ValorLogico deBooleano(Boolean valor) {
        return valor == null ? null : new ValorLogico(null, null, valor, null, null, null);
    }

    public static ValorLogico deFecha(LocalDate valor) {
        return valor == null ? null : new ValorLogico(null, null, null, valor, null, null);
    }

    /** Monto y moneda, o nada: los dos juntos o ninguno. */
    public static ValorLogico deImporte(BigDecimal monto, String moneda) {
        return monto == null || moneda == null
                ? null : new ValorLogico(null, canonico(monto), null, null, moneda, null);
    }

    /**
     * Varios valores del mismo vocabulario. Una lista vacia es {@code null}:
     * «respondio y no marco nada» y «no respondio» son lo mismo aqui, y fingir
     * lo contrario obligaria a cada consumidor a distinguirlos sin poder.
     */
    public static ValorLogico deValores(List<String> valores) {
        return valores == null || valores.isEmpty()
                ? null : new ValorLogico(null, null, null, null, null, List.copyOf(valores));
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

    /**
     * Como se muestra: sin ceros de mas ni {@code "true"} crudo.
     *
     * <p>Un importe sale <b>con su moneda</b> y un multivalor con sus valores
     * separados por coma. Eso es presentacion, si -- pero la alternativa es que
     * cada consumidor se invente la suya, y entonces el mismo dato se lee
     * distinto en la ficha, en el listado y en KAIROS.
     */
    public String comoTexto() {
        if (texto != null) {
            return texto;
        }
        if (numero != null) {
            return moneda == null ? numero.toPlainString() : moneda + " " + numero.toPlainString();
        }
        if (fecha != null) {
            return fecha.toString();
        }
        if (valores != null) {
            return String.join(", ", valores);
        }
        return booleano == null ? null : booleano.toString();
    }

    /** El entero, o {@code null} si la clave no llevaba numero. */
    public Integer comoEntero() {
        return numero == null ? null : numero.intValueExact();
    }

    /** Si este valor es una coleccion y no un escalar. */
    public boolean esMultivalor() {
        return valores != null;
    }
}
