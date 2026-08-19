package com.controllocal.service.soporte;

import java.math.BigDecimal;

/**
 * Las medias de la propia casa, cada una con la muestra que la sostiene.
 *
 * <h2>Degradan una por una</h2>
 *
 * <p>Que exista «dias hasta contrato» no dice nada sobre «propuestas por visita».
 * Cada media se calcula, se comprueba contra su muestra minima y se publica o se
 * degrada <b>por separado</b>. Agruparlas en un «hay medias / no hay medias»
 * esconderia que dos de las tres no tienen ni un caso.
 *
 * <h2>Cero base no es cero valor</h2>
 *
 * <p>Medido el 2026-08-19: cero visitas realizadas y cero interacciones colgadas
 * de una prospeccion. La lectura correcta es <b>«todavia no hay con que
 * medirlo»</b>, no «tu media es cero propuestas por visita», que sonaria a un
 * reproche por un trabajo que nadie ha registrado.
 */
public record MediasPropias(Media propuestasPorVisita,
                            Media diasHastaContrato,
                            Media plazoRealDeRecontacto) {

    /**
     * Una media con su N.
     *
     * @param valor       la media; {@code null} si no hay base o no es suficiente
     * @param base        el denominador medido
     * @param concluye    si la muestra da para afirmar algo
     * @param descripcion como se lee en una frase, ya redactada por el dominio
     */
    public record Media(BigDecimal valor, int base, boolean concluye, String descripcion) {

        /** Una media que se sostiene. */
        public static Media de(BigDecimal valor, int base, String descripcion) {
            return new Media(valor, base, true, descripcion);
        }

        /**
         * No hay con que. La N viaja igual, porque «3 contratos» informa y
         * «sin datos» no dice si falta poco o todo.
         */
        public static Media sinBase(int base, String descripcion) {
            return new Media(null, base, false, descripcion);
        }
    }

    /**
     * Construye una media de <b>proporcion</b> —cuantos de cuantos— aplicando la
     * muestra minima de la politica.
     */
    public static Media proporcion(int casos, int base, String queSonLosCasos,
                                   String queEsLaBase) {
        if (base <= 0) {
            return Media.sinBase(0, "Todavia no hay " + queEsLaBase + " con que medirlo");
        }
        if (!PoliticaComercial.muestraConcluye(base)) {
            return Media.sinBase(base,
                    base + " " + queEsLaBase + ": pocas para concluir una media");
        }
        BigDecimal valor = BigDecimal.valueOf(casos)
                .divide(BigDecimal.valueOf(base), 2, java.math.RoundingMode.HALF_UP);
        return Media.de(valor, base,
                "tu media es " + casos + " " + queSonLosCasos + " cada " + base + " " + queEsLaBase);
    }

    /**
     * Construye una media de <b>magnitud</b> —cuanto de media— aplicando la misma
     * muestra minima.
     */
    public static Media magnitud(BigDecimal valor, int base, String unidad, String queEsLaBase) {
        if (base <= 0 || valor == null) {
            return Media.sinBase(Math.max(0, base),
                    "Todavia no hay " + queEsLaBase + " con que medirlo");
        }
        if (!PoliticaComercial.muestraConcluye(base)) {
            return Media.sinBase(base,
                    base + " " + queEsLaBase + ": pocos para concluir una media");
        }
        long redondeado = valor.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        return Media.de(valor, base,
                "tu media son " + redondeado + " " + unidad + " sobre " + base + " " + queEsLaBase);
    }
}
