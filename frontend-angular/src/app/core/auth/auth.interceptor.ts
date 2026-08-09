import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL } from '../api/api.config';
import { AuthService } from './auth.service';
import { CODIGO_ENROLAMIENTO_REQUERIDO } from './codigos-mfa';
import { CODIGO_CAMBIO_OBLIGATORIO } from './politica-contrasena';

/**
 * Endpoints de entrada: **públicos**, y por eso ni llevan token ni su 401
 * cierra sesión. Antes bastaba con `/auth/login`; desde V37 el ingreso pasa
 * por los dos de MFA, y sin exceptuarlos un usuario que se equivoca de
 * contraseña vería cómo el 401 lo "desloguea" y recarga la pantalla en la que
 * ya estaba, borrando de paso el mensaje de error.
 */
const ENTRADAS_PUBLICAS = ['/auth/login', '/auth/mfa/desafio', '/auth/mfa/verificar'];

/**
 * Adjunta el Bearer token y traduce dos respuestas del servidor en navegación:
 *
 * - **401** → cierre de sesión completo. Cubre el token expirado y, desde
 *   D-S0-12, el token *revocado*: bien firmado y sin caducar, pero muerto
 *   porque la cuenta cerró sesión o cambió su contraseña.
 * - **403 con `CAMBIO_CONTRASENA_REQUERIDO` o `ENROLAMIENTO_MFA_REQUERIDO`** →
 *   la pantalla del paso que falta. La sesión existe pero está **capada** y el
 *   backend solo deja pasar lo justo para salir de ahí. Se distinguen por el
 *   `codigo` y no por el texto, que es traducible.
 */
export const authInterceptor: HttpInterceptorFn = (solicitud, siguiente) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const esPeticionApi = solicitud.url === API_BASE_URL || solicitud.url.startsWith(`${API_BASE_URL}/`);
  const esEntradaPublica = ENTRADAS_PUBLICAS.some((ruta) => solicitud.url === `${API_BASE_URL}${ruta}`);

  // Nunca filtrar el JWT a una URL ajena si una integración futura usa HttpClient.
  const conToken = auth.token && esPeticionApi && !esEntradaPublica
    ? solicitud.clone({ setHeaders: { Authorization: `Bearer ${auth.token}` } })
    : solicitud;

  return siguiente(conToken).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && esPeticionApi && !esEntradaPublica) {
        auth.cerrarSesion();
      }
      if (error.status === 403 && esPeticionApi) {
        // `replaceUrl` para que el botón "atrás" no devuelva a la pantalla que
        // acaba de rebotar: volvería a chocar contra el mismo 403.
        if (error.error?.codigo === CODIGO_CAMBIO_OBLIGATORIO) {
          void router.navigate(['/cambiar-contrasena'], {
            queryParams: { obligatorio: '1' },
            replaceUrl: true,
          });
        }
        if (error.error?.codigo === CODIGO_ENROLAMIENTO_REQUERIDO) {
          void router.navigate(['/enrolar-mfa'], {
            queryParams: { obligatorio: '1' },
            replaceUrl: true,
          });
        }
      }
      return throwError(() => error);
    }),
  );
};
