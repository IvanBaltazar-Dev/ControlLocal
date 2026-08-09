import { inject, Injectable } from '@angular/core';
import { ApiClient } from './api.client';
import { Evaluacion } from './solicitudes.service';

/**
 * Decisión del broker sobre una solicitud. El recurso completo es de
 * **BROKER/ADMIN**; el agente solo llega al historial de SU solicitud, que vive
 * en `SolicitudesService.evaluaciones$` (`GET /solicitudes/{id}/evaluaciones`).
 *
 * Dos reglas del cable que explican la forma de este servicio:
 *
 * - **El broker no elige el tipo: lo deriva el resultado** (`O` ⇒ OBSERVACION,
 *   `A`/`R` ⇒ FINAL). Pero el request lo exige **presente y válido** aunque
 *   luego lo pise —la v1 lo parseaba antes de que la BL lo sobrescribiera—, así
 *   que {@link EvaluacionesService.registrar} lo calcula y lo manda. No es un
 *   campo de pantalla.
 * - **Solo puede existir una evaluación FINAL por solicitud**, y el broker debe
 *   supervisar al agente responsable (el admin no).
 */
export interface DatosEvaluacion {
  /** Se deriva del resultado; viaja porque el cable exige su presencia. */
  tipoEvaluacion?: string;
  /** A aprobada, R rechazada, O observada. */
  resultado?: string;
  observaciones?: string;
  idSolicitud?: number;
}

const RECURSO = 'evaluaciones';

/**
 * Tipo que corresponde a cada resultado. Es lo mismo que hace el backend; se
 * repite aquí solo para poder mandar el campo que el request exige.
 */
export function tipoDeResultado(resultado: string): string {
  return resultado === 'O' ? 'O' : 'F';
}

@Injectable({ providedIn: 'root' })
export class EvaluacionesService {
  private readonly api = inject(ApiClient);

  /**
   * Registra la decisión y **mueve la solicitud en la misma transacción**:
   * aprobada, rechazada u observada (que la devuelve al agente para subsanar).
   * Por eso la pantalla no llama después a ningún endpoint de estado.
   */
  registrar(idSolicitud: number, resultado: string, observaciones?: string): Promise<Evaluacion> {
    const datos: DatosEvaluacion = {
      tipoEvaluacion: tipoDeResultado(resultado),
      resultado,
      observaciones,
      idSolicitud,
    };
    return this.api.post<Evaluacion>(RECURSO, datos);
  }
}
