import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';

/**
 * Contrato CONGELADO E4: la vista transversal del proceso comercial. Cinco
 * tipos de fila —prospección, captación, oportunidad, solicitud y cierre—
 * mezclados en una sola lista ordenada por su fecha propia.
 *
 * Lo que hay que saber antes de tocarla:
 *
 * - **El techo de página es 8**, no solo el defecto: `min(8, clamp(valor, 1,
 *   100))`. Pedir 50 devuelve 8.
 * - **`counts` ignora el filtro de proceso** (pero aplica todos los demás), y
 *   por eso los KPI siguen sirviendo de atajo sin perder el contexto.
 * - **`options` se calcula SIN ningún filtro**: son las opciones del alcance
 *   completo, no las de la página. Si se recalcularan con el filtro puesto, el
 *   selector se vaciaría solo.
 * - **Las filas sin fecha ENCABEZAN la lista.** Es el `.reversed()` del
 *   comparador del cable invirtiendo también el `nullsLast`. Es contraintuitivo
 *   y está verificado; "arreglarlo" en el cliente sería divergir.
 * - **El texto ausente viaja como `-`**, no como null ni vacío. No hay que
 *   sustituirlo por un guion largo: ya viene con el suyo.
 * - `ruta` y `rutaRevision` son **rutas del Blazor** y se traducen con
 *   `core/navegacion-legado`.
 */

export interface FilaSeguimiento {
  /** `Prospeccion` · `Captacion` · `Oportunidad` · `Solicitud` · `Cierre`. */
  proceso: string;
  codigo: string;
  cliente: string;
  clienteId?: number;
  local: string;
  distrito: string;
  agente: string;
  propietario: string;
  propietarioId?: number;
  /** Descripción del estado; es texto de cable y también valor de filtro. */
  estado: string;
  ultimoHito: string;
  ruta: string;
  /** Solo en captación `P` y solicitud `E`: el atajo a la cola de revisión. */
  rutaRevision: string;
  icono: string;
  tono: string;
  fechaOrden?: string;
  /** Solo solicitud y cierre; el resto manda cadena vacía. */
  monto: string;
}

export interface ConteosSeguimiento {
  todos: number;
  prospeccion: number;
  captacion: number;
  oportunidad: number;
  solicitud: number;
  cierre: number;
}

export interface OpcionesSeguimiento {
  agentes: string[];
  propietarios: string[];
  estados: string[];
  distritos: string[];
}

export interface PaginaSeguimiento {
  items: FilaSeguimiento[];
  totalRecords: number;
  page: number;
  pageSize: number;
  counts: ConteosSeguimiento;
  options: OpcionesSeguimiento;
}

/**
 * Filtros. Cada uno acepta hasta cuatro nombres en el cable; se usa **uno solo
 * por filtro** —el corto— porque los aliases existen para clientes viejos, no
 * para elegir.
 */
export interface FiltrosSeguimiento {
  /** `Todos` por defecto. Comparación exacta sobre el texto normalizado. */
  tipo?: string;
  q?: string;
  agente?: string;
  propietario?: string;
  estado?: string;
  distrito?: string;
  pagina?: number;
  tamano?: number;
}

/** Techo real de página del recurso. */
export const TAMANO_SEGUIMIENTO = 8;

@Injectable({ providedIn: 'root' })
export class SeguimientoService {
  private readonly api = inject(ApiClient);

  pagina$(filtros: FiltrosSeguimiento = {}): Observable<PaginaSeguimiento> {
    return this.api.get$<PaginaSeguimiento>('seguimiento-comercial', { ...filtros });
  }

  pagina(filtros: FiltrosSeguimiento = {}): Promise<PaginaSeguimiento> {
    return this.api.get<PaginaSeguimiento>('seguimiento-comercial', { ...filtros });
  }
}
