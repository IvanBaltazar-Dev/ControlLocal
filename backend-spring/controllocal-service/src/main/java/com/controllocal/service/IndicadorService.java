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
            int conversionPropia,
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
            Operativo operativo) {
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
