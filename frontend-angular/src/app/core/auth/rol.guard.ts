import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { MODULOS, moduloDeRuta, puedeEntrar } from './acceso';
import { RolSesion } from './sesion.model';

/**
 * Corta la navegación a una pantalla que el rol no puede ver, usando el mismo
 * mapa que dibuja el menú (`acceso.ts`). Así el menú y las rutas no pueden
 * discrepar: si un módulo no aparece, tampoco se llega escribiendo la URL.
 *
 * <b>No sustituye a la autorización</b>, que la impone el backend en cada
 * request (RC-001); esto solo evita el viaje y el 403 en pantalla.
 *
 * Las acciones con `data.roles` explícito conservan su entrada propia.
 * Gobierno sólo hereda la navegación de un módulo de gobierno autorizado.
 */
export const rolGuard: CanActivateFn = (ruta, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.autenticado() || auth.sesion()?.contextoRequerido !== false || auth.sesion()?.rol === 'IDENTIDAD') {
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
  const camino = estado.url.split(/[?#]/)[0].replace(/^\/+|\/+$/g, '');
  if (rol === 'TENANT_ADMIN') {
    // El tablero inicial consume información comercial; gobierno entra a su
    // superficie existente antes de crear el componente o pedir esos datos.
    if (!camino) {
      return router.createUrlTree(['/seguridad']);
    }
    if (rolesRuta?.includes(rol)) {
      return true;
    }
  }

  const modulo = moduloDeRuta(camino);
  if (!modulo) {
    if (rol === 'TENANT_ADMIN') {
      const padre = MODULOS
        .filter((m) => !!m.ruta && camino.startsWith(`${m.ruta}/`))
        .sort((a, b) => b.ruta.length - a.ruta.length)[0];
      return padre && puedeEntrar(padre, rol)
        ? true
        : router.createUrlTree(['/acceso-denegado']);
    }
    return true; // sin data explícita: la autorización definitiva sigue en el backend
  }

  return puedeEntrar(modulo, auth.sesion()?.rol) ? true : router.createUrlTree(['/acceso-denegado']);
};
