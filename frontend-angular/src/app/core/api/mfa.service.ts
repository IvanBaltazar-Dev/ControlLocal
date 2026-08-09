import { inject, Injectable } from '@angular/core';
import { ApiClient } from './api.client';

/** Lo que el titular ve de su propio factor. **Nunca incluye el secreto.** */
export interface EstadoMfa {
  activo: boolean;
  debeEnrolar: boolean;
  codigosDisponibles: number;
  codigosPorAgotarse: boolean;
  activadoEn?: string;
}

/**
 * Secreto y URI del QR. **Es la única vez que el secreto sale del servidor**:
 * no hay endpoint que lo relea, y perder el enrolamiento a la mitad se
 * resuelve empezando otro.
 */
export interface EnrolamientoMfa {
  secreto: string;
  uri: string;
}

/** Los ocho códigos de respaldo, también una sola vez. */
export interface CodigosRespaldo {
  codigos: string[];
}

/**
 * Reautenticación reforzada (D-S0-34): contraseña **y** código vigente.
 *
 * **Una sesión abierta no basta** para tocar el factor. Si bastara, robar la
 * sesión equivaldría a quedarse con la cuenta para siempre — que es
 * exactamente lo que el segundo factor viene a impedir.
 */
export interface Reautenticacion {
  contrasena: string;
  codigo: string;
}

/** Token de elevación: 5 minutos, un solo uso, para UNA acción. */
export interface Elevacion {
  token: string;
  expiraEn: string;
}

/**
 * Segundo factor del **propio** titular (V37).
 *
 * Nada de esto se guarda: ni el secreto, ni la URI, ni los códigos de
 * respaldo. Viven en la señal del componente mientras la pantalla está
 * abierta y se van con ella. Persistirlos en `localStorage` para sobrevivir a
 * una recarga convertiría el navegador en una copia permanente del segundo
 * factor —justo lo que el factor viene a evitar—, así que recargar **empieza
 * un enrolamiento nuevo** y el anterior queda inservible.
 */
@Injectable({ providedIn: 'root' })
export class MfaService {
  private readonly api = inject(ApiClient);

  estado(): Promise<EstadoMfa> {
    return this.api.get<EstadoMfa>('perfil/mfa');
  }

  /**
   * Crea un factor PENDIENTE y devuelve su secreto. Llamarlo otra vez
   * **descarta el anterior**: el backend borra los pendientes de la cuenta
   * antes de crear el nuevo.
   */
  iniciar(): Promise<EnrolamientoMfa> {
    return this.api.post<EnrolamientoMfa>('perfil/mfa');
  }

  /**
   * Activa el factor con el primer código y devuelve los de respaldo.
   *
   * **Deja la sesión muerta**: activar invalida todas las sesiones de la
   * cuenta —nacieron sin segundo factor, incluida esta—, así que quien llame
   * tiene que llevar al usuario al login. Y el código que se use aquí queda
   * consumido: no sirve para el ingreso siguiente.
   */
  confirmar(codigo: string): Promise<CodigosRespaldo> {
    return this.api.post<CodigosRespaldo>('perfil/mfa/confirmar', { codigo });
  }

  /**
   * Regenera los ocho códigos e **invalida todos los anteriores**, usados o no.
   *
   * **No invalida sesiones**, y eso es deliberado: regenerar códigos no cambia
   * quién eres ni cómo entras, así que echar al usuario aquí sería castigo sin
   * motivo. Compárese con `reemplazar`, donde sí cambia el factor.
   */
  regenerarCodigos(reautenticacion: Reautenticacion): Promise<CodigosRespaldo> {
    return this.api.post<CodigosRespaldo>('perfil/mfa/codigos', reautenticacion);
  }

  /**
   * Revoca el factor propio, que es el primer paso de **reemplazar el
   * autenticador**: no hay un "cambiar" en un solo acto, porque el secreto
   * nuevo solo puede nacer de un enrolamiento del titular.
   *
   * Deja la cuenta con `debe_enrolar_mfa` y **mata todas las sesiones**: la
   * siguiente entrada llega capada y solo alcanza el enrolamiento. Se rechaza
   * si dejara al tenant sin administrador operativo.
   */
  reemplazar(reautenticacion: Reautenticacion): Promise<void> {
    return this.api.delete<void>('perfil/mfa', reautenticacion);
  }

  /**
   * Token de elevación para una acción sensible sobre OTRA persona. Existe
   * porque el token de sesión está congelado y **no lleva** cuándo se probó el
   * segundo factor: dar por buena una sesión que nació con MFA hace horas
   * dejaría pasar una sesión robada a media tarde.
   */
  elevar(reautenticacion: Reautenticacion): Promise<Elevacion> {
    return this.api.post<Elevacion>('perfil/elevacion', reautenticacion);
  }

  /**
   * Nivel 2 de la recuperación: el gobierno **revoca** el factor de otra
   * persona. No lo ve ni lo fija — la cuenta queda obligada a enrolar y el
   * titular lo hace él mismo.
   */
  revocarAjeno(idPersona: number, motivo: string, tokenElevacion: string): Promise<void> {
    return this.api.delete<void>(
      `accesos/${idPersona}/mfa`,
      { motivo },
      { 'X-Elevacion': tokenElevacion },
    );
  }
}
