package com.controllocal.bl;

import java.util.List;

import com.controllocal.model.comercial.Tarea;

/**
 * Bandeja de "Acciones Pendientes" del agente (Etapa 5). Las tareas se derivan del
 * estado actual del flujo y se reconcilian de forma idempotente: se crean las que
 * faltan y se auto-resuelven las que la accion de negocio ya atendio. No hay creacion
 * manual; el agente solo puede resolverlas (en su pantalla) o cancelarlas desde la lista.
 */
public interface TareaBusinessLogic {

    /** Reconcilia y devuelve las tareas abiertas (PENDIENTE/EN_PROCESO) del agente. */
    List<Tarea> bandejaDe(Long idAgente);

    /** Cancela (descarta) una tarea de la lista; solo el agente dueno de la tarea. */
    boolean cancelar(Long idTarea, Long idAgente);
}
