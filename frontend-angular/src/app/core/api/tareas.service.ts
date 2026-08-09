import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/**
 * Contrato CONGELADO F7 (`docs/ai/contrato-congelado-f6-f7-alertas-tareas.md`).
 * La bandeja del agente: **no es un CRUD**, es una lista derivada del estado
 * del flujo.
 *
 * Cuatro cosas que condicionan cómo se usa desde el SPA:
 *
 * - **`GET /tareas` ESCRIBE.** Deriva qué tareas deberían existir, crea las que
 *   faltan, auto-completa las que ya no aplican y recién entonces devuelve. Es
 *   la única forma de tener la bandeja al día sin planificador, así que abrirla
 *   dos veces no es gratis — pero tampoco es incorrecto.
 * - **No hay alta manual.** El agente resuelve una tarea *trabajando* (se
 *   auto-completa) o la cancela.
 * - **Cancelar la mata para siempre**: `CANCELADA` bloquea que el reconcile la
 *   vuelva a crear para esa entidad. No es "recordármelo más tarde", y la
 *   pantalla tiene que decirlo antes de confirmar.
 * - **La bandeja es estrictamente personal**: el recurso es solo del AGENTE
 *   (BROKER y ADMIN reciben 403). En el dashboard eso no se nota porque
 *   `/dashboard` les manda una bandeja vacía en vez de fallar.
 *
 * Ojo con el tope: la fuente se corta en **10** y el resto se descarta **en
 * silencio**, así que `totalRecords` es el tamaño de esa fuente recortada, no
 * el total histórico de tareas pendientes.
 */

/** Tope de la bandeja en el backend (`MAX_BANDEJA`). */
export const MAXIMO_BANDEJA = 10;

/** Tipos del cable. `PROPONER_OPORTUNIDAD` abre la ficha del CLIENTE. */
export type TipoTarea =
  | 'RECONTACTO'
  | 'SEGUIMIENTO'
  | 'SUBIR_DOCUMENTOS'
  | 'VISITA'
  | 'REPORTE_PROPIETARIO'
  | 'PROPONER_OPORTUNIDAD';

export type PrioridadTarea = 'ALTA' | 'MEDIA' | 'BAJA';

/**
 * Espejo de `TareaResponse`. Los cuatro campos derivados —`entidadCodigo`,
 * `rutaResolver`, `diasSinAccion` y `fechaVencimiento`— **no están en la
 * tabla**: se calculan al leer y su enriquecimiento es *best-effort*, así que
 * pueden faltar sin que eso signifique nada malo.
 *
 * `diasSinAccion` cuenta desde el **plazo real de la entidad** (recontacto,
 * cambio de estado, fecha de visita), no desde que se creó la tarea.
 */
export interface Tarea {
  id: number;
  tipo: string;
  entidadTipo: string;
  entidadId: number;
  entidadCodigo?: string;
  /** Ruta del legado (`solicitud-detail/{codigo}`, `visitas?focus={id}`…). */
  rutaResolver?: string;
  descripcion: string;
  estado: string;
  prioridad: string;
  fechaProgramada?: string;
  diasSinAccion?: number;
  fechaVencimiento?: string;
}

@Injectable({ providedIn: 'root' })
export class TareasService {
  private readonly api = inject(ApiClient);

  /**
   * La bandeja completa: **lista pelada, sin sobre de paginación**, máx. 10 y
   * ya ordenada por prioridad y días sin acción.
   */
  bandeja$(): Observable<Tarea[]> {
    return this.api.get$<Tarea[]>('tareas');
  }

  bandeja(): Promise<Tarea[]> {
    return this.api.get<Tarea[]>('tareas');
  }

  /** Misma fuente, paginada. `tamano` por defecto es **5**, no 10. */
  pendientes$(pagina = 1, tamano = 5): Observable<PageResponse<Tarea>> {
    return this.api.get$<PageResponse<Tarea>>('tareas/pendientes', { pagina, tamano });
  }

  /**
   * Cancelación definitiva (204 sin cuerpo). El backend exige que la tarea sea
   * del agente; a partir de aquí ningún disparador la vuelve a crear para esa
   * entidad.
   */
  cancelar(id: number): Promise<void> {
    return this.api.post<void>(`tareas/${id}/cancelar`);
  }
}
