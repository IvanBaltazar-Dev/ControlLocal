import { inject, Injectable } from '@angular/core';
import { ApiClient } from './api.client';
export interface DatosInvitacion {
  correoCuenta: string;
  persona?: { idPersona?: number; nombre?: string; telefono?: string; correoLaboral?: string };
  rolPrincipal: 'AGENTE' | 'BROKER' | null;
  administrar: boolean;
  idBrokerSupervisor?: number;
  zona?: string;
  estadoOperativo?: string;
  venceEn: string;
  motivo: string;
}
export interface InvitacionEmitida { referencia: string; venceEn: string; rolPrincipal: string | null; administrar: boolean }
@Injectable({ providedIn: 'root' })
export class MembresiasService {
  private readonly api=inject(ApiClient);
  invitar(datos: DatosInvitacion,claveComando: string): Promise<InvitacionEmitida> {
    return this.api.post<InvitacionEmitida>('membresias/invitaciones',datos,undefined,{'Idempotency-Key':claveComando});
  }
  darBaja(idMembresia: number,version: number,motivo: string): Promise<unknown> {
    return this.api.post(`membresias/${idMembresia}/baja`,{version,motivo});
  }
}
