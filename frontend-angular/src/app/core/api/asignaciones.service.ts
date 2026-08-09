import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from './api.client';

/** Fila del panel de agentes: quién supervisa hoy a cada uno. */
export interface AsignacionAgente {
  idAgente: number;
  nombre?: string;
  numeroDocumento?: string;
  /** A activo, I inactivo. */
  estadoAdministrativo?: string;
  /** D disponible, O ocupado, V vacaciones, S suspendido. */
  estadoOperativo?: string;
  /** Nombre del broker supervisor vigente; vacío si no tiene. */
  brokerActual?: string;
}

/** Fila del panel de brokers: carga de cada equipo. */
export interface AsignacionBroker {
  idBroker: number;
  nombre?: string;
  zona?: string;
  estadoAdministrativo?: string;
  esAdministrador?: boolean;
  agentesACargo?: number;
}

/**
 * Evento histórico de reasignación. **No es una inferencia sobre la supervisión
 * vigente**: broker anterior, nuevo, autorizador y motivo se guardaron cuando
 * ocurrió el cambio (tabla-evento `reasignacion_agente_broker`), así que el
 * historial no se reescribe cuando el organigrama vuelve a moverse.
 */
export interface ReasignacionAgente {
  id: number;
  idAgente: number;
  agenteNombre?: string;
  idBrokerAnterior?: number;
  brokerAnteriorNombre?: string;
  idBrokerNuevo?: number;
  brokerNuevoNombre?: string;
  idBrokerAdministrador?: number;
  brokerAdministradorNombre?: string;
  fechaCambio?: string;
  motivo?: string;
}

export interface DatosReasignacion {
  idAgente: number;
  idBrokerDestino: number;
  motivo: string;
}

const RECURSO = 'asignaciones';

/**
 * Reasignación agente ↔ broker. **Las cuatro operaciones exigen ADMIN**
 * (E1 §4), y no hay que confundirla con la reasignación de CAPTACIONES, que es
 * del broker y vive en `reasignaciones-captacion`: aquella mueve un encargo
 * concreto, esta mueve a la persona de equipo.
 *
 * Cuatro reglas del cable que la pantalla debe anticipar en vez de dejar que
 * las explique un 400:
 *
 * - agente y broker destino son obligatorios;
 * - el **motivo no vacío** es obligatorio;
 * - el **broker administrador no puede ser destino**: no supervisa equipos;
 * - el agente tiene que estar administrativo **ACTIVO** y operativo
 *   **DISPONIBLE**, y no puede reasignarse al broker que ya lo supervisa.
 */
@Injectable({ providedIn: 'root' })
export class AsignacionesService {
  private readonly api = inject(ApiClient);

  agentes$(): Observable<AsignacionAgente[]> {
    return this.api.get$<AsignacionAgente[]>(`${RECURSO}/agentes`);
  }

  brokers$(): Observable<AsignacionBroker[]> {
    return this.api.get$<AsignacionBroker[]>(`${RECURSO}/brokers`);
  }

  /** Ordenado por `id` descendente: lo más reciente primero. */
  historial$(): Observable<ReasignacionAgente[]> {
    return this.api.get$<ReasignacionAgente[]>(`${RECURSO}/historial`);
  }

  historial(): Promise<ReasignacionAgente[]> {
    return this.api.get<ReasignacionAgente[]>(`${RECURSO}/historial`);
  }

  /**
   * Cierra la supervisión anterior, crea la nueva y registra el evento
   * histórico **en la misma transacción**: no hay estado intermedio en el que
   * un agente se quede sin supervisor.
   */
  reasignar(datos: DatosReasignacion): Promise<ReasignacionAgente> {
    return this.api.post<ReasignacionAgente>(`${RECURSO}/reasignar`, datos);
  }
}
