import { inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { ApiClient } from './api.client';

/** Espejo de `PerfilResponse` (E1 §5). */
export interface Perfil {
  nombre?: string;
  correo?: string;
  telefono?: string;
  /** Clave opaca del almacén, no una URL. */
  fotoClave?: string;
}

/** Espejo de `FotoResponse`. */
export interface FotoSubida {
  clave: string;
}

const RECURSO = 'perfil';

/**
 * Perfil propio de la sesión. Cualquier rol entra.
 *
 * **El alcance de este recurso es deliberadamente pequeño** y no hay que
 * ampliarlo por iniciativa propia: el cable solo cubre teléfono y foto.
 * En particular **no existe cambio de contraseña** —la pantalla Blazor que lo
 * ofrecía era un mock sin llamada HTTP (E1 §1, decisión 1)—, así que el SPA no
 * debe pintar ese formulario hasta que exista el endpoint.
 *
 * La foto viaja en **base64**, no como octet-stream: es el único camino que
 * expone `POST /perfil/foto`. Y la v1 valida **extensión, no firma binaria**;
 * aquí se valida además la firma con `ArchivosService` porque es gratis y no
 * cambia el cable —un archivo que la v1 aceptaría y este rechaza es un archivo
 * que no era una imagen—.
 */
@Injectable({ providedIn: 'root' })
export class PerfilService {
  private readonly api = inject(ApiClient);

  private readonly fotoActual = signal<string | null>(null);

  /**
   * La clave de la foto, compartida entre la pantalla de perfil y el avatar de
   * la topbar. Vive aquí y no en cada componente porque son **dos vistas del
   * mismo dato**: sin esto, subir una foto la cambiaría en el perfil y dejaría
   * las iniciales en la topbar hasta recargar la página.
   */
  readonly fotoClave = this.fotoActual.asReadonly();

  obtener(): Promise<Perfil> {
    return this.api.get<Perfil>(RECURSO).then((perfil) => {
      this.fotoActual.set(perfil.fotoClave ?? null);
      return perfil;
    });
  }

  obtener$(): Observable<Perfil> {
    return this.api
      .get$<Perfil>(RECURSO)
      .pipe(tap((perfil) => this.fotoActual.set(perfil.fotoClave ?? null)));
  }

  /** Se olvida al cerrar sesión: la foto del siguiente que entre no es esta. */
  olvidarFoto(): void {
    this.fotoActual.set(null);
  }

  /**
   * Solo actúa si llega `telefono`. El backend lo recorta y exige entre 6 y 15
   * **dígitos**, contando únicamente caracteres numéricos: "+51 999 888 777"
   * son 11 dígitos y es válido.
   */
  actualizarTelefono(telefono: string): Promise<Perfil> {
    return this.api.patch<Perfil>(RECURSO, { telefono });
  }

  /** `nombreArchivo` decide la validación: solo `.png`, `.jpg` o `.jpeg`. */
  subirFoto(nombreArchivo: string, contenidoBase64: string): Promise<FotoSubida> {
    return this.api
      .post<FotoSubida>(`${RECURSO}/foto`, { nombreArchivo, contenidoBase64 })
      .then((subida) => {
        this.fotoActual.set(subida.clave);
        return subida;
      });
  }
}
