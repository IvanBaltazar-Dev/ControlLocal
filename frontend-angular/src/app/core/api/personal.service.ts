import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/** Proyección mínima de AgenteResponse para selectores de alcance. */
export interface AgenteOpcion {
  id: number;
  codigoAgente?: string;
  nombre: string;
  numeroDocumento?: string;
  zona?: string;
  /** A activo, I inactivo. */
  estadoAdministrativo?: string;
  /** D disponible, O ocupado, V vacaciones, S suspendido. */
  estadoOperativo?: string;
}

/** Proyección mínima de BrokerResponse para selectores de equipo. */
export interface BrokerOpcion {
  id: number;
  codigoBroker?: string;
  nombre: string;
}

/**
 * Catálogos de personal reutilizables por las bandejas.
 *
 * No descarga personas para reconstruir alcance: el backend ya devuelve
 * `/agentes` acotado al broker (o completo para ADMIN). El selector solo
 * envía el id elegido; la autorización sigue en el API.
 */
@Injectable({ providedIn: 'root' })
export class PersonalService {
  private readonly api = inject(ApiClient);

  agentes$(tamano = 100): Observable<PageResponse<AgenteOpcion>> {
    return this.api.get$<PageResponse<AgenteOpcion>>('agentes', { pagina: 1, tamano });
  }

  brokers$(tamano = 100): Observable<PageResponse<BrokerOpcion>> {
    return this.api.get$<PageResponse<BrokerOpcion>>('brokers', { pagina: 1, tamano });
  }

  agentesDelBroker$(idBroker: number): Observable<AgenteOpcion[]> {
    return this.api.get$<AgenteOpcion[]>(`brokers/${idBroker}/agentes`);
  }
}
