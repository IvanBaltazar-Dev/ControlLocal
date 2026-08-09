import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from './api.config';

/** Respuesta de una invitación emitida por gobierno. El token viaja **una sola vez**. */
export interface InvitacionEmitida {
  token: string;
  expiraEn: string;
  entregadoAlTitular: boolean;
}

/** Contraseña temporal generada por el sistema. También de una sola vez. */
export interface ContrasenaTemporalEmitida {
  usuario: string;
  contrasenaTemporal: string;
  debeCambiarla: boolean;
}

/**
 * Contraseñas y recuperación de acceso (Bloque 4, Plan S0 §4.2–§4.5).
 *
 * Todo aquí es **aditivo**: la v1 no tenía ninguna de estas operaciones y sus
 * pantallas Blazor eran mocks sin endpoint detrás.
 */
@Injectable({ providedIn: 'root' })
export class ContrasenasService {
  private readonly http = inject(HttpClient);

  /**
   * Cambio autenticado. Devuelve 204 y **deja la sesión muerta**: cambiar la
   * contraseña invalida todas las sesiones de la cuenta, incluida esta. Quien
   * llame tiene que llevar al usuario al login — no es un fallo, es el efecto
   * que hace que el cambio sirva de algo frente a una sesión robada.
   */
  async cambiar(contrasenaActual: string, contrasenaNueva: string): Promise<void> {
    await firstValueFrom(
      this.http.post<void>(`${API_BASE_URL}/perfil/contrasena`, {
        contrasenaActual,
        contrasenaNueva,
      }),
    );
  }

  /**
   * Solicitud de recuperación. **Siempre responde 202**, exista o no la cuenta:
   * la pantalla nunca puede decir si el usuario existe, porque eso convertiría
   * el formulario en un padrón.
   */
  async solicitarRecuperacion(usuario: string): Promise<void> {
    await firstValueFrom(
      this.http.post<void>(`${API_BASE_URL}/auth/recuperacion`, { usuario }),
    );
  }

  /** Canje del token de un solo uso. Sirve para recuperación y para invitación. */
  async canjear(token: string, contrasenaNueva: string): Promise<void> {
    await firstValueFrom(
      this.http.post<void>(`${API_BASE_URL}/auth/recuperacion/canje`, {
        token,
        contrasenaNueva,
      }),
    );
  }

  /** Gobierno del tenant (solo ADMIN): emite invitación para otra persona. */
  async emitirInvitacion(idPersona: number, motivo: string): Promise<InvitacionEmitida> {
    return firstValueFrom(
      this.http.post<InvitacionEmitida>(
        `${API_BASE_URL}/accesos/${idPersona}/invitacion`,
        { motivo },
      ),
    );
  }

  /** Gobierno del tenant (solo ADMIN): genera contraseña temporal para otra persona. */
  async emitirContrasenaTemporal(
    idPersona: number,
    motivo: string,
  ): Promise<ContrasenaTemporalEmitida> {
    return firstValueFrom(
      this.http.post<ContrasenaTemporalEmitida>(
        `${API_BASE_URL}/accesos/${idPersona}/contrasena-temporal`,
        { motivo },
      ),
    );
  }
}
