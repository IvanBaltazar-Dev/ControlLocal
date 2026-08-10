package com.controllocal.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Bandeja "Acciones Pendientes" del agente. Los records espejan el contrato
 * CONGELADO (TareasRest.TareaResponse v1).
 *
 * <p><b>No hay alta manual y la lectura ESCRIBE.</b> Las tareas se derivan del
 * estado del flujo (siete disparadores, §5.1 del contrato) y cada lectura
 * <i>reconcilia</i>: crea las que faltan y da por hechas las que ya no
 * aplican. Es la unica forma de que la bandeja este al dia sin planificador,
 * y es cable real (D-F7-3).
 *
 * <p>Alcance (§6): la bandeja es <b>estrictamente personal del AGENTE</b>. Ni
 * el broker ni el admin entran — es el unico recurso del sistema sin acceso de
 * ADMIN, coherente con lo que es: una lista de cosas que hacer, no un tablero.
 */
public interface TareaService {

    /**
     * Espejo de TareaResponse. Los cuatro ultimos campos <b>no estan en la
     * tabla</b>: se derivan al leer (§5.3).
     */
    record FichaTarea(Long id, String tipo, String entidadTipo, Long entidadId, String entidadCodigo,
                      String rutaResolver, String descripcion, String estado, String prioridad,
                      OffsetDateTime fechaProgramada, Integer diasSinAccion,
                      LocalDate fechaVencimiento) {
    }

    /**
     * Reconcilia y devuelve la bandeja: prioridad ALTA primero, luego lo que
     * lleva mas dias sin atencion. <b>Sin tope</b> — el corte en 10 con
     * descarte en silencio (D-F7-2) se retiro el 2026-08-08 al descongelar el
     * contrato, asi que esto puede devolver 30 o 50 fichas y quien las pinte
     * tiene que aguantarlas.
     */
    List<FichaTarea> bandejaDe(Actor actor);

    /**
     * Soft-cancel del agente. Ojo con lo que significa: cancelar <b>no
     * pospone</b>, el reconcile no vuelve a crear la tarea de esa entidad
     * nunca mas (§5.2, trampa 1).
     */
    void cancelar(long id, Actor actor);

    /**
     * <b>Efecto 7 de la cascada de F4</b>: al concretarse el alquiler se dan
     * por hechas las tareas abiertas de la operacion —oportunidad, solicitud,
     * captacion y local—. Lo llama {@code ContratoService} dentro de su
     * transaccion.
     */
    void resolverDeEntidad(String entidadTipo, Long entidadId, Actor actor);

    /**
     * Tarea obligatoria al finalizar, rescindir o anular un contrato.
     *
     * @param idContratoOrigen contrato que la produce. Es lo que permite
     *     cerrarla despues por la revision de ESE contrato y no por la de otro
     *     del mismo local.
     */
    void crearRevisionInmueble(Long idPropiedad, Long idAgente, String motivo,
                               Long idContratoOrigen, Actor actor);

    /**
     * Cierra las tareas que produjo un contrato. Lo usa la revision de
     * disponibilidad: resuelve SU tarea, no "alguna del inmueble".
     *
     * @return cuantas cerro.
     */
    int resolverDeContratoOrigen(long idContrato, Actor actor);
}
