package com.controllocal.service.soporte;

import com.controllocal.service.excepcion.ReglaNegocioException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/** Reglas transversales del contrato economico de alquiler. */
public final class CondicionesEconomicas {

    public static final BigDecimal COMISION_MINIMA = BigDecimal.ZERO;

    /**
     * El tope no se escribe aqui: lo fija {@link PoliticaComercial#COMISION_MAXIMA}
     * junto al resto de reglas del negocio. Esta clase valida; la politica decide.
     */
    public static final BigDecimal COMISION_MAXIMA = PoliticaComercial.comisionMaxima();

    public static final Set<String> MONEDAS = Set.of("PEN", "USD");

    private CondicionesEconomicas() {
    }

    public static BigDecimal comisionPactada(BigDecimal porcentaje) {
        if (porcentaje == null || porcentaje.compareTo(COMISION_MINIMA) < 0) {
            throw new ReglaNegocioException(
                    "La comision pactada es obligatoria y no puede ser negativa.");
        }
        if (porcentaje.compareTo(COMISION_MAXIMA) > 0) {
            throw new ReglaNegocioException(
                    "La comision pactada no puede superar "
                            + PoliticaComercial.COMISION_MAXIMA.valor()
                            + " % de la renta mensual.");
        }
        return porcentaje;
    }

    /**
     * <b>La base de la comision la decide la OPERACION, no un defecto</b> (V76).
     *
     * <p>Hasta V76 el defecto era siempre {@code R} (renta mensual). Con venta
     * en el modelo eso significa que un encargo de VENTA al que el cliente no
     * le declara base sale con «renta mensual» como base de calculo: una venta
     * no tiene renta, asi que la comision quedaba anclada a un importe que no
     * existe. El defecto sigue existiendo --el cuerpo puede callar la base--
     * pero ahora dice lo unico que puede decir sin inventar nada: la base
     * natural de esa operacion.
     */
    public static String basePorDefecto(String operacion) {
        return "V".equals(operacion) ? "V" : "R";
    }

    /**
     * <b>Tipo y base tienen que caber en la operacion.</b>
     *
     * <p>«Un mes de alquiler» no es una comision expresable en una venta, y «%
     * de la renta mensual» tampoco: en una venta no hay renta que multiplicar.
     * Al reves, «% del precio de venta» sobre un alquiler apunta a un importe
     * que el encargo no pacto. La combinacion tipo+base ya se validaba; lo que
     * faltaba era comprobarla <b>contra la operacion</b>, que es la que dice
     * cual de los dos importes existe.
     *
     * @param operacion {@code A} o {@code V}, ya normalizada
     */
    public static void exigirBaseCoherente(String operacion, String tipo, String base) {
        if ("F".equals(tipo)) {
            return; // Monto fijo: no se apoya en ningun importe del encargo.
        }
        boolean venta = "V".equals(operacion);
        if (venta && "E".equals(tipo)) {
            throw new ReglaNegocioException(
                    "Una venta no se comisiona en mensualidades: no hay renta que contar. "
                            + "Usa un porcentaje del precio de venta o un monto fijo.");
        }
        String esperada = basePorDefecto(operacion);
        if (!esperada.equals(base)) {
            throw new ReglaNegocioException(venta
                    ? "La comision de una venta se calcula sobre el precio de venta, no sobre una renta mensual."
                    : "La comision de un alquiler se calcula sobre la renta mensual, no sobre un precio de venta.");
        }
    }

    public static String moneda(String valor, String concepto) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("La moneda " + concepto + " es obligatoria.");
        }
        String normalizada = valor.trim().toUpperCase(Locale.ROOT);
        if (!MONEDAS.contains(normalizada)) {
            throw new ReglaNegocioException("Valor invalido para moneda " + concepto + ": " + valor);
        }
        return normalizada;
    }
}
