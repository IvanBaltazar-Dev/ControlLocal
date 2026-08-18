import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';
import { IndicadoresResumen } from './indicadores.service';
import { Tarea } from './tareas.service';

/**
 * Un descubrimiento de BROX: algo que vale la pena mirar.
 *
 * NO es una tarea. Una tarea dice «hay algo que debes resolver»; esto dice
 * «encontré algo». Una coincidencia de cartera puede ser muy valiosa sin ser
 * una obligación, y por eso viaja en su propia colección: mientras compartió
 * lista con la bandeja, le ganaba el puesto a lo que de verdad reclamaba una
 * acción (E2.3).
 *
 * `porQue` llega REDACTADO por el dominio. No se compone aquí: si esta pantalla
 * escribiera su versión, KAIROS tendría que escribir la suya para decir lo mismo
 * por WhatsApp, y las dos empezarían a divergir.
 */
export interface Hallazgo {
  /** Identidad estable entre recargas: la misma coincidencia, el mismo id. */
  id: string;
  tipo: string;
  titulo: string;
  /** Por qué vale la pena mirarlo, ya escrito. */
  porQue: string;
  puntaje: number;
  cumple: string[];
  noCumple: string[];
  /** Ruta real del SPA donde se actúa. */
  destino: string;
  idCliente: number | null;
  idCaptacion: number | null;
  codigoCaptacion: string | null;
}

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
 * - **`tamano` por defecto es 5**, y desde que se retiró el tope de F7
 *   (2026-08-08) `totalRecords` es el **total real** de tareas abiertas del
 *   agente, no el tamaño de una fuente recortada. Puede ser 30 o 50, y la home
 *   solo compone las 5 primeras: el resto se pide aparte con `GET /tareas`.
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
  /** Lo que BROX encontro. Coleccion aparte, no una bandeja filtrada (E2.3). */
  hallazgos: Hallazgo[];
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
