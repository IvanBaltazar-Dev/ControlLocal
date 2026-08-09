import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';
import { IndicadoresResumen } from './indicadores.service';
import { Tarea } from './tareas.service';

/**
 * Contrato CONGELADO E4: la home en **una sola llamada**. Compone el
 * `/indicadores/resumen` del mismo periodo con la primera página de la bandeja,
 * sin lógica propia.
 *
 * Dos formas del cable que la pantalla tiene que respetar:
 *
 * - **Solo el AGENTE tiene bandeja.** Para BROKER y ADMIN `bandeja` viaja con
 *   `items: []` y `totalRecords: 0`. **No es un 403 ni un error**: es una
 *   bandeja vacía, y pintar "no se pudo cargar" ahí sería mentir.
 * - **`tamano` por defecto es 5**, y la fuente ya viene cortada en 10 por la
 *   regla de F7, así que `totalRecords` es el tamaño de esa fuente recortada.
 *
 * La campana **no viaja aquí**: es chrome global y tiene su propio recurso
 * (`AlertasService`).
 *
 * Por qué una sola llamada y no dos: el Blazor pedía indicadores y bandeja por
 * separado para que el tablero pintara sin esperar a la reconciliación de
 * tareas. Aquí el backend ya las compone en un round-trip, y la reconciliación
 * corre en el mismo request — no hay forma de mostrar los KPI antes. A cambio,
 * la pantalla ofrece **recargar** en vez de dejar la mitad en blanco.
 */
export interface DashboardCarga {
  indicadores: IndicadoresResumen;
  bandeja: PageResponse<Tarea>;
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly api = inject(ApiClient);

  cargar$(periodo?: string, tamano = 5): Observable<DashboardCarga> {
    return this.api.get$<DashboardCarga>('dashboard', { periodo, tamano });
  }

  cargar(periodo?: string, tamano = 5): Promise<DashboardCarga> {
    return this.api.get<DashboardCarga>('dashboard', { periodo, tamano });
  }
}
