package com.controllocal.service;

import com.controllocal.service.soporte.InterpretacionDelAsunto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Bandeja "Acciones Pendientes" del agente. Los records espejan el contrato
 * CONGELADO (TareasRest.TareaResponse v1).
 *
 * <p><b>No hay alta manual y la lectura ESCRIBE.</b> Las tareas se derivan del
 * estado del flujo (siete disparadores, §5.1 del contrato) y cada lectura
 * <i>reconcilia</i>: crea o actualiza las proyecciones justificadas por su fuente.
 * R27 conserva sus necesidades y episodios canónicos. En C6 la causa de
 * conservación tiene un estado propio: retirarla no acredita una actuación
 * humana realizada; perder lectura la oculta sin inventar su finalización.
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
    /**
     * Un asunto de la bandeja.
     *
     * <p>{@code dependeDeMi}, {@code lado} y {@code paso} son <b>derivados</b>, no
     * columnas: salen del tipo del asunto y del tipo de su entidad, que ya estan
     * guardados. Existen para que el cliente no tenga que deducirlos -- que es
     * como Angular acabo con su propia tabla de urgencia y su propio orden.
     *
     * <p>{@code prioridad} sigue viajando porque el filtro de la bandeja la usa,
     * pero <b>ya no ordena</b>: el orden lo decide {@code PoliticaDeDespacho} con
     * sus seis criterios, y la lista llega ordenada. El aviso informativo C6
     * usa prioridad nula (no aplicable), sin asignarle una importancia comercial.
     */
    record FichaTarea(Long id, String tipo, String entidadTipo, Long entidadId, String entidadCodigo,
                      String rutaResolver, String descripcion, String estado, String prioridad,
                      OffsetDateTime fechaProgramada, Integer diasSinAccion,
                      LocalDate fechaVencimiento,
                      boolean dependeDeMi, String lado, String paso,
                      InterpretacionDelAsunto.Interpretacion interpretacion,
                      java.time.LocalTime horaActuacion,
                      com.controllocal.service.soporte.PoliticaDeDespacho.Clasificacion orden) {
    }

    /**
     * Reconcilia y devuelve la bandeja <b>en el orden definitivo</b>, el que
     * decide {@code PoliticaDeDespacho} con los seis criterios de D-E2-1 seccion 3.
     * Quien la pinta no reordena: consume el orden.
     *
     * <p>Lo accionable va primero; lo que espera a otro va detras, tambien
     * ordenado, porque la cola completa se lee y merece un orden estable.
     *
     * <b>Sin tope</b> — el corte en 10 con
     * descarte en silencio (D-F7-2) se retiro el 2026-08-08 al descongelar el
     * contrato, asi que esto puede devolver 30 o 50 fichas y quien las pinte
     * tiene que aguantarlas.
     */
    List<FichaTarea> bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta consulta,Actor actor);

    /**
     * Cancelación voluntaria del agente; no pospone ni altera el vencimiento.
     * R27 y C6 conservan la causa cancelada y no la reabren por leer, recortar
     * un grupo o cambiar una versión técnica. Un hecho material nuevo acreditado
     * por su fuente puede originar otra ocurrencia vinculada, conforme a D6.
     * Las demás familias conservan su conciliación y autoridad vigentes.
     */
    void cancelar(long id, Actor actor);

    /**
     * Efecto del cierre contractual confirmado. Core deriva los recursos de la
     * revisión y exige la autoridad de decisión del Encargo, sin abrir la
     * lectura de expedientes vinculados ni aceptar IDs libres del llamante.
     */
    void resolverPorCierreConfirmado(long revisionHito, Actor actor);

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
