package com.controllocal.service.soporte;

import java.math.BigDecimal;
import java.util.List;

/**
 * Los tramos de metraje con los que se agrupan propiedades comparables.
 *
 * <p>Un local de 40 m2 y uno de 300 no compiten por el mismo inquilino ni se
 * pagan al mismo precio, asi que meterlos en el mismo rango produce un intervalo
 * amplisimo dentro del cual cualquier renta «cae bien». Un rango que nunca
 * senala nada no es informacion.
 *
 * <p><b>Los tres cortes no estan aqui</b>: viven en {@link PoliticaComercial},
 * porque tambien deciden que significa un numero —dicen contra QUIEN se compara
 * una renta— y ese es el unico sitio donde eso se decide. Aqui esta la forma de
 * los tramos; alli, donde se cortan. Salen de la practica de la casa —el local
 * de calle pequeno, el estandar, el grande y la superficie mayor—, no de un
 * estudio de mercado que BROX no tiene ni simula.
 */
public enum BandaDeMetraje {

    HASTA_50("Hasta 50 m2", null, corte(PoliticaComercial.BANDA_METRAJE_PEQUENO)),
    DE_50_A_100("50 a 100 m2", corte(PoliticaComercial.BANDA_METRAJE_PEQUENO),
            corte(PoliticaComercial.BANDA_METRAJE_ESTANDAR)),
    DE_100_A_200("100 a 200 m2", corte(PoliticaComercial.BANDA_METRAJE_ESTANDAR),
            corte(PoliticaComercial.BANDA_METRAJE_GRANDE)),
    MAS_DE_200("Mas de 200 m2", corte(PoliticaComercial.BANDA_METRAJE_GRANDE), null);

    private static BigDecimal corte(PoliticaComercial.Regla regla) {
        return BigDecimal.valueOf(regla.valor());
    }

    private final String rotulo;
    private final BigDecimal desde;
    private final BigDecimal hasta;

    BandaDeMetraje(String rotulo, BigDecimal desde, BigDecimal hasta) {
        this.rotulo = rotulo;
        this.desde = desde;
        this.hasta = hasta;
    }

    /** El nombre visible del tramo. */
    public String rotulo() {
        return rotulo;
    }

    /** Limite inferior, incluido. {@code null} en el primer tramo. */
    public BigDecimal desde() {
        return desde;
    }

    /** Limite superior, excluido. {@code null} en el ultimo. */
    public BigDecimal hasta() {
        return hasta;
    }

    /** Para el SQL, que no admite nulos comodos en un BETWEEN. */
    public BigDecimal desdeODesdeCero() {
        return desde == null ? BigDecimal.ZERO : desde;
    }

    /** Para el SQL: un techo por encima de cualquier inmueble real. */
    public BigDecimal hastaOInfinito() {
        return hasta == null ? SIN_TECHO : hasta;
    }

    /** No es un umbral de negocio: es el infinito practico de un BETWEEN. */
    private static final BigDecimal SIN_TECHO = BigDecimal.valueOf(99_999_999L);

    /**
     * La banda de un metraje. {@code null} si no se sabe cuanto mide: sin
     * metraje no hay banda, y adivinarla pondria la propiedad a competir con un
     * grupo que no es el suyo.
     */
    public static BandaDeMetraje de(BigDecimal metraje) {
        if (metraje == null || metraje.signum() <= 0) {
            return null;
        }
        for (BandaDeMetraje banda : values()) {
            boolean porEncimaDelPiso = banda.desde == null || metraje.compareTo(banda.desde) >= 0;
            boolean pordebajoDelTecho = banda.hasta == null || metraje.compareTo(banda.hasta) < 0;
            if (porEncimaDelPiso && pordebajoDelTecho) {
                return banda;
            }
        }
        return MAS_DE_200;
    }

    public static final List<BandaDeMetraje> TODAS = List.of(values());
}
