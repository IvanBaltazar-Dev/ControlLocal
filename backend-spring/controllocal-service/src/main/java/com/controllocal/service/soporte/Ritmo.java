package com.controllocal.service.soporte;

/**
 * El semaforo de un KPI contra su meta, calculado <b>una vez y aqui</b>.
 *
 * <h2>De donde viene</h2>
 *
 * <p>Este calculo vivia en {@code docs/ai/prototipos/indicadores.html:1253} y,
 * copiado entero, en {@code inicio.html:1678}. Baja al dominio porque D-E2-2 §8
 * lo exige —«las reglas verde/ambar/rojo/gris se determinan en el dominio, nunca
 * en Angular»— y porque una regla de negocio escrita dos veces en una vista es
 * exactamente lo que E1 se cerro para impedir.
 *
 * <h2>Que mide: proyeccion, no consumo</h2>
 *
 * <p>La pregunta no es «¿ya llegue a la meta?» sino <b>«con este ritmo, ¿llego?»</b>.
 * Meta 15, dia 5 de 30, llevo 5: he consumido el 33 % de la meta —que suena
 * mal— pero proyecto 30, el doble. Es <b>en ritmo</b>, y decir «ambar, vas por
 * el 33 %» seria mentir sobre el hecho.
 *
 * <h2>Los cuatro estados</h2>
 *
 * <p>{@code EN_RITMO} · {@code ATENCION} · {@code FUERA_DE_RITMO} ·
 * {@code SIN_BASE}. El cuarto <b>no es decorativo</b>: sin meta, sin cobertura o
 * sin recorrido no hay nada que concluir, y un gris honesto vale mas que un
 * verde inventado. {@link Motivo} dice cual de las tres cosas falta, porque «sin
 * base» sin causa obliga a adivinar.
 *
 * <h2>Nada se rellena</h2>
 *
 * <p>{@code metaEsperadaAHoy}, {@code porcentajeMeta}, {@code faltante} y
 * {@code proyeccionCierre} son <b>nulables</b>. Sin meta no valen cero: no
 * existen. Es la misma decision que E2.0 tomo con {@code conversionPropia}, y la
 * razon por la que un cero de la pantalla siempre significa un cero de verdad.
 */
public record Ritmo(int actual,
                    Integer metaPeriodo,
                    Integer metaEsperadaAHoy,
                    Integer porcentajeMeta,
                    Integer faltante,
                    Integer proyeccionCierre,
                    Integer porcentajeProyectado,
                    Estado estado,
                    Motivo motivo,
                    boolean sinCadencia) {

    /** El semaforo de D-E2-2 §4. */
    public enum Estado {
        /** Va acorde o por encima del ritmo necesario. */
        EN_RITMO,
        /** Hay desviacion, todavia razonablemente recuperable. */
        ATENCION,
        /** La brecha ya necesita intervencion. */
        FUERA_DE_RITMO,
        /** No hay con que concluir. Ver {@link Motivo}. */
        SIN_BASE
    }

    /** Por que no se pudo concluir. Solo tiene sentido con {@link Estado#SIN_BASE}. */
    public enum Motivo {
        /** Hay meta y periodo: el estado significa algo. */
        NINGUNO,
        /** Nadie ha fijado la meta de este KPI para este mes. */
        SIN_META,
        /**
         * Es una meta de equipo y falta la de algun agente. No se compara contra
         * una meta parcial: daria una brecha inventada, siempre a favor.
         */
        COBERTURA_INCOMPLETA,
        /** El mes todavia no ha empezado: no hay recorrido que proyectar. */
        PERIODO_SIN_RECORRIDO
    }

    // ------------------------------------------------------------------

    /** Sin meta fijada. Se dice, no se supone cero. */
    public static Ritmo sinMeta(int actual) {
        return vacio(actual, Motivo.SIN_META);
    }

    /** Meta de equipo incompleta: falta la de al menos un agente del alcance. */
    public static Ritmo coberturaIncompleta(int actual) {
        return vacio(actual, Motivo.COBERTURA_INCOMPLETA);
    }

    private static Ritmo vacio(int actual, Motivo motivo) {
        return new Ritmo(actual, null, null, null, null, null, null,
                Estado.SIN_BASE, motivo, false);
    }

    /**
     * El calculo completo.
     *
     * @param actual  lo conseguido en el periodo
     * @param meta    la meta vigente del periodo, ya sumada si es de equipo
     * @param periodo el mes de calendario, con su corte
     */
    public static Ritmo de(int actual, int meta, PeriodoCalendario periodo) {
        if (meta <= 0) {
            return sinMeta(actual);
        }
        if (periodo.diasTranscurridos() == 0) {
            // Mes futuro: hay meta, pero proyectar cero dias no dice nada.
            return new Ritmo(actual, meta, 0, porcentaje(actual, meta),
                    Math.max(0, meta - actual), null, null,
                    Estado.SIN_BASE, Motivo.PERIODO_SIN_RECORRIDO, false);
        }

        int faltante = Math.max(0, meta - actual);
        int porcentajeMeta = porcentaje(actual, meta);

        if (!PoliticaComercial.tieneCadencia(meta)) {
            // Sin cadencia diaria: solo cuenta si ya se cumplio y cuanto queda.
            return new Ritmo(actual, meta, null, porcentajeMeta, faltante, actual, null,
                    estadoSinCadencia(actual, meta, periodo), Motivo.NINGUNO, true);
        }

        int transcurridos = periodo.diasTranscurridos();
        int restantes = periodo.diasRestantes();
        int metaEsperadaAHoy = (int) Math.round(meta * (double) transcurridos / periodo.diasTotales());

        double proyeccion = actual + ((double) actual / transcurridos) * restantes;
        // Se trunca: no se van a firmar 0,6 contratos.
        int proyeccionCierre = (int) Math.floor(proyeccion);
        int porcentajeProyectado = (int) Math.round(proyeccion / meta * 100);

        return new Ritmo(actual, meta, metaEsperadaAHoy, porcentajeMeta, faltante,
                proyeccionCierre, porcentajeProyectado,
                estadoProyectado(porcentajeProyectado, actual, meta, periodo),
                Motivo.NINGUNO, false);
    }

    // ------------------------------------------------------------------

    /**
     * Con meta pequena no se proyecta: se mira si ya se cumplio, y si no, cuanto
     * periodo queda para conseguirlo.
     */
    private static Estado estadoSinCadencia(int actual, int meta, PeriodoCalendario periodo) {
        if (actual >= meta) {
            return Estado.EN_RITMO;
        }
        // Con un cuarto del mes por delante todavia da tiempo; con menos, no.
        boolean quedaMargen = periodo.diasRestantes() * 4 >= periodo.diasTotales();
        return quedaMargen ? Estado.ATENCION : Estado.FUERA_DE_RITMO;
    }

    /**
     * El estado por proyeccion, con las dos guardas que evitan un rojo que nadie
     * se creeria.
     */
    private static Estado estadoProyectado(int porcentajeProyectado, int actual, int meta,
                                           PeriodoCalendario periodo) {
        Estado estado;
        if (porcentajeProyectado >= PoliticaComercial.RITMO_LLEGA.valor()) {
            estado = Estado.EN_RITMO;
        } else if (porcentajeProyectado >= PoliticaComercial.RITMO_CERCA.valor()) {
            estado = Estado.ATENCION;
        } else {
            estado = Estado.FUERA_DE_RITMO;
        }

        if (estado != Estado.FUERA_DE_RITMO) {
            return estado;
        }
        // Al principio del mes la proyeccion todavia no vale como sentencia.
        if (PoliticaComercial.enArranque(periodo)) {
            return Estado.ATENCION;
        }
        // Y a una unidad de la meta nunca es rojo: es una firma de distancia.
        if (meta - actual <= 1) {
            return Estado.ATENCION;
        }
        return estado;
    }

    private static int porcentaje(int parte, int total) {
        return total <= 0 ? 0 : (int) Math.round(parte * 100.0 / total);
    }

    /** Si el estado concluye algo. Lo usa la pantalla para decidir si pinta color. */
    public boolean concluye() {
        return estado != Estado.SIN_BASE;
    }
}
