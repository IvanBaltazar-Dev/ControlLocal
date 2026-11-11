import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from './api.client';
import { Agente } from './agentes.service';
import { PageResponse } from './api.types';

/** Directorio organizacional; el acceso personal y la pertenencia tienen contratos propios. */
export interface Broker {
  id: number;
  codigoBroker?: string;
  nombre?: string;
  tipoPersona?: string;
  tipoDocumento?: string;
  numeroDocumento?: string;
  telefono?: string;
  correo?: string;
  usuario?: string;
  zona?: string;
  fechaDesignacion?: string;
  estadoAdministrativo?: string;
  esAdministrador?: boolean;
  agentesACargo?: number;
}

/** Edición laboral autorizada; sin credenciales ni alta/baja de pertenencia. */
export interface DatosBroker {
  nombre?: string;
  telefono?: string;
  correoLaboral?: string;
  zona?: string;
}

const RECURSO = 'brokers';

/** Consultas y edición local; Core aplica autoridad y aislamiento. */
@Injectable({ providedIn: 'root' })
export class BrokersService {
  private readonly api = inject(ApiClient);

  pagina$(pagina = 1, tamano = 50): Observable<PageResponse<Broker>> {
    return this.api.get$<PageResponse<Broker>>(RECURSO, { pagina, tamano });
  }

  pagina(pagina = 1, tamano = 50): Promise<PageResponse<Broker>> {
    return this.api.get<PageResponse<Broker>>(RECURSO, { pagina, tamano });
  }

  obtener(id: number): Promise<Broker> {
    return this.api.get<Broker>(`${RECURSO}/${id}`);
  }

  obtener$(id: number): Observable<Broker> {
    return this.api.get$<Broker>(`${RECURSO}/${id}`);
  }

  /**
   * El equipo vigente del broker. Llega **sin contadores comerciales**: los dos
   * no se incluyen en la proyección mínima de equipo
   * (rareza del cable, E1 §3). Para los números reales hay que ir a
   * `GET /agentes`.
   */
  agentes$(id: number): Observable<Agente[]> {
    return this.api.get$<Agente[]>(`${RECURSO}/${id}/agentes`);
  }

  agentes(id: number): Promise<Agente[]> {
    return this.api.get<Agente[]>(`${RECURSO}/${id}/agentes`);
  }

  actualizar(id: number, datos: DatosBroker): Promise<Broker> {
    return this.api.put<Broker>(`${RECURSO}/${id}`, datos);
  }
}
