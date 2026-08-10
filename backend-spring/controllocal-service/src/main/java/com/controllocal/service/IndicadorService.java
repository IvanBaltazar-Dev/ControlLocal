package com.controllocal.service;

import java.util.List;

/**
 * Agregados de E4: el resumen que alimenta el dashboard y los reportes, y el
 * avance comercial por propiedad (RF-017).
 *
 * <p>Los dos resuelven el alcance <b>solo por agente responsable</b> — la
 * captacion no amplia el de nadie aqui, al reves que en el seguimiento
 * comercial (§2 del contrato E4).
 */
public interface IndicadorService {

    Resumen resumen(String periodo, Actor actor);

    AvanceComercial avance(Actor actor);

    /** Conteo etiqueta/valor: etapas del donut y salud de captaciones. */
    record Conteo(String nombre, int valor) {
    }

    /** Tramo del embudo: valor absoluto + porcentaje sobre la base. */
    record Embudo(String etapa, int valor, int porcentaje) {
    }

    /** Fila de desempeno por responsable (broker si consulta el ADMIN, agente si no). */
    record Desempeno(String nombre, int captaciones, int cierres, int conversion) {
    }

    record Operativo(
            int recontactosVencidos,
            int recontactosAlDia,
            int diasPromedioSinSeguimiento,
            int visitasPendientes,
            int solicitudesSinCierre,
            int conversionProspeccionCaptacion) {
    }

    /**
     * Un hecho del tablero <b>ya interpretado</b> (R-07, E1).
     *
     * <p>Antes viajaba solo el numero y era el componente de Angular el que
     * decidia si dolia: ocho ternarios repartidos por rol, mas un {@code > 7}
     * que era la cuarta copia del plazo de recontacto. Ahora el dominio dice
     * <b>cuanto urge</b> ({@code nivelAtencion}) y <b>en que orden se atiende</b>
     * ({@code prioridad}); la pantalla elige el color y el rotulo.
     *
     * <p>{@code concepto} y {@code nivelAtencion} son nombres estables del
     * dominio ({@code PoliticaComercial.Concepto} y {@code NivelAtencion}), no
     * texto para mostrar: la pantalla nunca los pinta tal cual.
     */
    record Senal(String concepto, int valor, String nivelAtencion,
                 boolean requiereAtencion, int prioridad) {
    }

    record Resumen(
            String ambito,
            int captacionesPorRevisar,
            int solicitudesPorEvaluar,
            int captacionesTotales,
            int captacionesActivas,
            // `captacionesPendientes` se retiro el 2026-08-08 al descongelar el
            // contrato: repetia `captacionesPorRevisar` con otro nombre (D-E4-3)
            // porque la v1 lo emitia asi. Nadie lo pintaba — dos nombres para el
            // mismo numero solo invitan a que alguien crea que miden cosas
            // distintas.
            int captacionesObservadas,
            int oportunidadesActivas,
            int interacciones,
            int visitas,
            int cierres,
            int cierresCohorte,
            // NULO A PROPOSITO, y es el unico del resumen (E2.0, 2026-08-10).
            // Sin captaciones en el periodo no hay nada que convertir: la tasa
            // no es 0 %, es INEXISTENTE. Emitir 0 obligaba a la pantalla a
            // distinguir "medi y no converti" de "no habia que medir", y el
            // dashboard lo resolvia tomando prestada la conversion del primero
            // de la tabla de desempeno — un agente sin cierres veia como propia
            // la cifra del que mas cerro.
            Integer conversionPropia,
            int agentesActivos,
            int brokersActivos,
            int propiedadesEquipo,
            List<String> mesesEtiquetas,
            List<Integer> cierresPorMes,
            List<Integer> conversionPorPeriodo,
            List<Integer> captacionesPorPeriodo,
            List<Conteo> etapas,
            List<Conteo> captacionesSalud,
            List<Embudo> embudo,
            List<Desempeno> desempeno,
            Operativo operativo,
            // Los mismos numeros de arriba, clasificados por el dominio y
            // ordenados por lo que urge primero. Viene completa —un elemento por
            // concepto—; que subconjunto ve cada rol lo decide la pantalla.
            List<Senal> senales,
            // Cuantas COSAS reclaman atencion ahora mismo (E2.1): la suma de las
            // senales pendientes que cuentan unidades. No se puede derivar en el
            // cliente sumando `senales`, porque `DEMORA_DE_SEGUIMIENTO` vale dias
            // y colarla daria "11 cosas" donde hay 2 pendientes y 9 dias.
            int pendientesDeAtencion) {
    }

    /** Avance comercial de UNA propiedad con captacion ACTIVA. */
    record AvancePropiedad(
            long idCaptacion,
            String codigoCaptacion,
            String direccion,
            String distrito,
            String estadoComercial,
            int oportunidadesTotales,
            int oportunidadesAbiertas,
            int oportunidadesConVisita,
            int oportunidadesConSolicitud,
            int cerradasExitosas,
            int cerradasNoFavorables,
            int cerradasNoContinuidad,
            int interesados,
            int interacciones,
            int visitasProgramadas,
            int visitasConcretadas,
            int solicitudesRecibidas,
            int tasaOportVisita,
            int tasaOportSolicitud,
            String motivoNoContinuidad) {
    }

    record AvanceComercial(
            String ambito,
            int propiedades,
            int oportunidadesTotales,
            int oportunidadesAbiertas,
            int oportunidadesConVisita,
            int oportunidadesConSolicitud,
            int cerradasExitosas,
            int cerradasNoFavorables,
            int cerradasNoContinuidad,
            int interesados,
            int interacciones,
            int visitasProgramadas,
            int visitasConcretadas,
            int solicitudesRecibidas,
            int tasaOportVisita,
            int tasaOportSolicitud,
            List<AvancePropiedad> detalle) {
    }
}
