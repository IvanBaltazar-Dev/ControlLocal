import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/**
 * Contrato CONGELADO F6 (`docs/ai/contrato-congelado-f6-f7-alertas-tareas.md`).
 * La campana: avisos generados por once puntos repartidos por todo el flujo.
 *
 * Lo que hay que saber para usarla bien:
 *
 * - **No hay columna de destinatario.** Una alerta se ata siempre a un AGENTE y
 *   su broker la ve por la supervisión, así que **quién la lee lo decide el
 *   TIPO** (`CAPTACION_CREADA` cuelga del agente pero está escrita para el
 *   broker). No inventar un filtro por destinatario.
 * - **`GET /alertas` escribe**: materializa el barrido de recontacto vencido,
 *   como mucho una vez cada 5 minutos y tragándose cualquier fallo del barrido.
 *   Consultar la campana seguido no lo dispara más.
 * - **Atender comprueba VISIBILIDAD, no propiedad**: si la alerta no está en la
 *   lista que ese usuario vería responde **404**, no 403. Un broker puede
 *   atender la de cualquiera de sus agentes.
 * - **`ruta` es derivada y puede faltar**: la v1 no enruta algunos tipos de
 *   entidad (entre ellos `INMUEBLE` y `CAPTACION`) y, con Jackson en
 *   `non_null`, esos avisos llegan sin `ruta`. Sin ella la alerta se lee, pero
 *   no navega.
 */

/** Severidades del cable; ordenan y colorean la campana. */
export type SeveridadAlerta = 'ALTA' | 'MEDIA' | 'BAJA';

/** Espejo de `AlertaResponse`. */
export interface Alerta {
  id: number;
  tipo: string;
  severidad: string;
  entidadTipo: string;
  entidadId: number;
  idAgente?: number;
  agenteNombre?: string;
  mensaje: string;
  /** `A` activa · `T` atendida (el estado que decide si sigue en la campana). */
  estado: string;
  fechaGeneracion?: string;
  fechaResolucion?: string;
  /** Ruta del legado al origen del aviso. Ausente para los tipos sin enrutar. */
  ruta?: string;
}

/** Sobre de un solo booleano: `{"atendida": true}`. */
export interface AlertaAtendida {
  atendida: boolean;
}

@Injectable({ providedIn: 'root' })
export class AlertasService {
  private readonly api = inject(ApiClient);

  /** `tamano` por defecto del recurso es **20**. */
  pagina$(pagina = 1, tamano = 20): Observable<PageResponse<Alerta>> {
    return this.api.get$<PageResponse<Alerta>>('alertas', { pagina, tamano });
  }

  pagina(pagina = 1, tamano = 20): Promise<PageResponse<Alerta>> {
    return this.api.get<PageResponse<Alerta>>('alertas', { pagina, tamano });
  }

  /**
   * Marca la alerta como atendida. Los dos verbos (POST y PATCH) existen en el
   * cable y hacen lo mismo; se usa POST, que es el del Blazor.
   */
  atender(id: number): Promise<AlertaAtendida> {
    return this.api.post<AlertaAtendida>(`alertas/${id}/atender`);
  }
}
