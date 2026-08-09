import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { moduloDeRuta, puedeEntrar } from './acceso';
import { RolSesion } from './sesion.model';

/**
 * Corta la navegación a una pantalla que el rol no puede ver, usando el mismo
 * mapa que dibuja el menú (`acceso.ts`). Así el menú y las rutas no pueden
 * discrepar: si un módulo no aparece, tampoco se llega escribiendo la URL.
 *
 * <b>No sustituye a la autorización</b>, que la impone el backend en cada
 * request (RC-001); esto solo evita el viaje y el 403 en pantalla.
 *
 * Se aplica por ruta y lee su `path` de la propia configuración, de modo que
 * añadir una pantalla es añadir su fila en `MODULOS` y nada más.
 */
export const rolGuard: CanActivateFn = (ruta, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.autenticado()) {
    return router.createUrlTree(['/login']);
  }

  // Formularios y detalles pueden restringir una operación concreta sin
  // inventar un módulo de menú. Los roles salen de la matriz operación→rol.
  const rolesRuta = ruta.data['roles'] as readonly RolSesion[] | undefined;
  const rol = auth.sesion()?.rol;
  if (rolesRuta && (!rol || !rolesRuta.includes(rol))) {
    return router.createUrlTree(['/acceso-denegado']);
  }

  // `estado.url` trae la ruta completa resuelta (con la barra inicial y, si
  // los hubiera, los query params): se limpia para casar con `MODULOS`.
  const modulo = moduloDeRuta(estado.url.split('?')[0]);
  if (!modulo) {
    return true; // sin data explícita: la autorización definitiva sigue en el backend
  }

  return puedeEntrar(modulo, auth.sesion()?.rol) ? true : router.createUrlTree(['/acceso-denegado']);
};
