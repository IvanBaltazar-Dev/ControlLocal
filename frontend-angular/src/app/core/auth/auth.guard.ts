import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { destinoSeguro, PARAM_DESTINO } from './destino-tras-login';

/**
 * Guarda de sesión del shell. Los guards por rol (matriz página→rol que hoy
 * vive en RouteAccess.cs) se añaden al migrar cada pantalla; el backend
 * respalda siempre la autorización real (RC-001).
 *
 * Se lleva la ruta pedida al login para volver a ella después. Sin esto,
 * abrir un enlace directo a una ficha sin sesión te dejaba en el panel y con
 * el enlace perdido.
 */
export const authGuard: CanActivateFn = (_ruta, estado) => {
  const auth = inject(AuthService);
  if (auth.autenticado()) {
    return true;
  }
  const destino = destinoSeguro(estado.url);
  const router = inject(Router);
  return destino
    ? router.createUrlTree(['/login'], { queryParams: { [PARAM_DESTINO]: destino } })
    : router.createUrlTree(['/login']);
};
