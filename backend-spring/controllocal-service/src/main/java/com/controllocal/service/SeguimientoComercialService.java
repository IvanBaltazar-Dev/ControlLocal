package com.controllocal.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vista transversal del proceso comercial (E4): las cinco etapas —prospeccion,
 * captacion, oportunidad, solicitud y cierre— en una sola lista homogenea, con
 * sus filtros, sus contadores clicables y las opciones de cada filtro.
 *
 * <p>Su alcance es la <b>union</b> de agente propio y agente de la captacion
 * (solo para el BROKER), no el {@code switch} de las verticales; no se unifica
 * con el de indicadores (§2 del contrato E4).
 */
public interface SeguimientoComercialService {

    /** Techo y defecto del tamano de pagina: el cable nunca devuelve mas de 8 filas. */
    int TAMANO_MAXIMO = 8;

    String TODOS = "Todos";

    Resultado listar(Filtros filtros, Actor actor);

    /**
     * Filtros ya resueltos por el controlador (los aliases del cable se colapsan
     * antes de llegar aqui). Cadena vacia = sin filtro.
     */
    record Filtros(
            String proceso,
            String busqueda,
            String agente,
            String propietario,
            String estado,
            String distrito,
            int pagina,
            int tamano) {
    }

    record Resultado(
            List<Fila> items,
            long totalRecords,
            int page,
            int pageSize,
            Conteos counts,
            Opciones options) {
    }

    /** KPI clicables: se cuentan con TODOS los filtros aplicados menos el de proceso. */
    record Conteos(
            int todos,
            int prospeccion,
            int captacion,
            int oportunidad,
            int solicitud,
            int cierre) {
    }

    /** Valores de los selects: se calculan sobre todas las filas visibles, sin filtrar. */
    record Opciones(
            List<String> agentes,
            List<String> propietarios,
            List<String> estados,
            List<String> distritos) {
    }

    record Fila(
            String proceso,
            String codigo,
            String cliente,
            Long clienteId,
            String local,
            String distrito,
            String agente,
            String propietario,
            Long propietarioId,
            String estado,
            String ultimoHito,
            String ruta,
            String rutaRevision,
            String icono,
            String tono,
            LocalDateTime fechaOrden,
            String monto) {
    }
}
