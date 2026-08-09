import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from './api.client';
import { Agente } from './agentes.service';
import { PageResponse } from './api.types';

/**
 * Contrato CONGELADO: `BrokerResponse` de la v1 (E1 §3).
 *
 * `id` es el `persona_rol.id` del rol BROKER. `agentesACargo` cuenta
 * supervisiones **vigentes**, no históricas.
 */
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
  /** A activo, I inactivo. */
  estadoAdministrativo?: string;
  esAdministrador?: boolean;
  agentesACargo?: number;
}

/**
 * Espejo de `BrokerRequest`.
 *
 * El PUT **no cambia `esAdministrador`**, ni documento, tipos, usuario,
 * contraseña, código ni fecha de designación. Quien administra se decide al
 * dar de alta y no se edita desde aquí.
 */
export interface DatosBroker {
  nombre?: string;
  tipoPersona?: string;
  tipoDocumento?: string;
  numeroDocumento?: string;
  telefono?: string;
  correo?: string;
  usuario?: string;
  contrasena?: string;
  zona?: string;
  codigoBroker?: string;
  /** Estado administrativo: A activo, I inactivo. */
  estado?: string;
  esAdministrador?: boolean;
}

const RECURSO = 'brokers';

/**
 * Las tres lecturas admiten **cualquier sesión autenticada** —el catálogo de
 * brokers no lleva alcance— pero **el alta y la edición son de ADMIN**.
 *
 * Regla que la pantalla tiene que respetar: **solo puede existir un broker
 * administrador por organización**. Un segundo alta con `esAdministrador` no
 * da un error de formulario, da un 400 del backend.
 */
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
   * viajan en 0 aunque el agente tenga captaciones y oportunidades abiertas
   * (rareza del cable, E1 §3). Para los números reales hay que ir a
   * `GET /agentes`.
   */
  agentes$(id: number): Observable<Agente[]> {
    return this.api.get$<Agente[]>(`${RECURSO}/${id}/agentes`);
  }

  agentes(id: number): Promise<Agente[]> {
    return this.api.get<Agente[]>(`${RECURSO}/${id}/agentes`);
  }

  registrar(datos: DatosBroker): Promise<Broker> {
    return this.api.post<Broker>(RECURSO, datos);
  }

  actualizar(id: number, datos: DatosBroker): Promise<Broker> {
    return this.api.put<Broker>(`${RECURSO}/${id}`, datos);
  }

  /** No hay DELETE: la baja es el PUT con estado `I`. */
  desactivar(id: number, broker: Broker): Promise<Broker> {
    return this.actualizar(id, this.editables(broker, 'I'));
  }

  reactivar(id: number, broker: Broker): Promise<Broker> {
    return this.actualizar(id, this.editables(broker, 'A'));
  }

  private editables(broker: Broker, estado: string): DatosBroker {
    return {
      nombre: broker.nombre,
      telefono: broker.telefono,
      correo: broker.correo,
      zona: broker.zona,
      estado,
    };
  }
}
