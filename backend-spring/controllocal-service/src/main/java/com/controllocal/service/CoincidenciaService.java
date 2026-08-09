package com.controllocal.service;

import java.util.List;

/**
 * Matching de cartera: cruza la DEMANDA (requerimientos activos) con la OFERTA
 * (captaciones activas sobre locales disponibles) en los dos sentidos, con
 * puntaje y explicacion criterio a criterio. Era la deuda de F2 (§7 del
 * contrato F3).
 *
 * <p>El scoring vive en {@code soporte/CoincidenciaCartera} (logica pura);
 * aqui se aplican la "vista personal" (que ve cada rol) y el shaping al cable.
 * La visibilidad no es la del listado general: el universo de clientes de un
 * agente son los que YA trabaja (los de sus oportunidades), no el catalogo
 * entero.
 */
public interface CoincidenciaService {

    /** Espejo de CoincidenciaCarteraSupport.CoincidenciaResponse (v1). */
    record Coincidencia(String tipo, Long id, String codigo, String titulo, String subtitulo,
                        String distrito, String renta, String area, String frente, int puntaje,
                        List<String> cumple, List<String> noCumple, Long clienteId, Long captacionId,
                        String proponerRuta) {
    }

    /** Espejo de CoincidenciaCarteraSupport.CoincidenciasResponse (v1). */
    record Coincidencias(String origen, int total, int page, int pageSize, List<Coincidencia> items) {
    }

    /** Cliente -> propiedades: captaciones activas del alcance con local disponible. */
    Coincidencias propiedadesParaCliente(long idCliente, int page, int pageSize, Actor actor);

    /** Captacion -> clientes: demanda propia compatible. Accionable ("Proponer"). */
    Coincidencias clientesParaCaptacion(String idOrCodigo, int page, int pageSize, Actor actor);

    /** Prospeccion -> clientes: senal temprana; accionable solo si ya tiene captacion. */
    Coincidencias clientesParaProspeccion(long idProspeccion, int page, int pageSize, Actor actor);
}
