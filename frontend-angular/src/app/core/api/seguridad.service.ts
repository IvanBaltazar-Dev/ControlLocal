import { inject, Injectable } from '@angular/core';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/**
 * Una cuenta del tenant, vista por el gobierno.
 *
 * **Los dos identificadores viajan a propósito.** Las fichas comerciales
 * (agente, broker) usan `idRol` — `persona_rol.id` —, y todas las operaciones
 * de acceso usan `idPersona`. Confundirlos aquí significaría revocarle el
 * segundo factor a otra persona, así que los dos van con su nombre y ninguno
 * se llama `id` a secas.
 */
export interface CuentaDeGobierno {
  idPersona: number;
  idRol: number;
  nombre: string;
  usuario: string;
  /** `TENANT_ADMIN` | `BROKER` | `AGENTE`, o ausente si no tiene membresía activa. */
  rolDeGobierno?: string;
  activa: boolean;
  debeCambiarContrasena: boolean;
  debeEnrolarMfa: boolean;
  mfaActivo: boolean;
  codigosRespaldoDisponibles: number;
}

/** Un hecho de gobierno ya legible: quién tocó accesos, factores o roles. */
export interface AvisoDeGobierno {
  id: number;
  fecha: string;
  tipo: string;
  resultado: string;
  idActor?: number;
  actor?: string;
  idAfectado?: number;
  afectado?: string;
  motivo?: string;
  ip?: string;
}

/**
 * Gobierno del tenant: padrón de cuentas y aviso persistente (§11).
 *
 * El aviso **no se puede atender ni silenciar**, y eso no es una limitación:
 * sale de `evento_seguridad`, que es append-only y de un solo escritor. Quien
 * más interés tendría en hacer desaparecer un «se revocó el factor de X» es
 * precisamente quien lo revocó sin permiso.
 */
@Injectable({ providedIn: 'root' })
export class SeguridadService {
  private readonly api = inject(ApiClient);

  cuentas(): Promise<CuentaDeGobierno[]> {
    return this.api.get<CuentaDeGobierno[]>('accesos');
  }

  avisos(pagina = 1, tamano = 20): Promise<PageResponse<AvisoDeGobierno>> {
    return this.api.get<PageResponse<AvisoDeGobierno>>('seguridad/avisos', { pagina, tamano });
  }
}
