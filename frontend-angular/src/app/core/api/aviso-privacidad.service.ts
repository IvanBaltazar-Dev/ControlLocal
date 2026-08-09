import { inject, Injectable } from '@angular/core';

import { ApiClient } from './api.client';

/**
 * Aviso de privacidad vigente. Aditivo de la v2 (D-27): la v1 no tiene aviso.
 *
 * `GET /aviso-privacidad` es **público**: el titular de los datos tiene que
 * poder leerlo sin cuenta. El interceptor no adjunta token a esta llamada
 * porque no hay sesión, y el endpoint no lo exige.
 */
export interface AvisoPrivacidad {
  version: string;
  vigenteDesde: string;
  /**
   * Cuando una versión se publica con cambio material, las autorizaciones
   * otorgadas contra versiones anteriores dejan de estar vigentes y se vuelven
   * a pedir. Una corrección de redacción viaja en `false` y no molesta a nadie.
   */
  cambioMaterial: boolean;
  contenido: string;
}

@Injectable({ providedIn: 'root' })
export class AvisoPrivacidadService {
  private readonly api = inject(ApiClient);

  vigente(): Promise<AvisoPrivacidad> {
    return this.api.get<AvisoPrivacidad>('aviso-privacidad');
  }
}
