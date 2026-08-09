import { catchError, map, Observable, of } from 'rxjs';

import { ApiError } from './api/api.types';

/**
 * Un bloque complementario de una pantalla de detalle: se dibuja aunque falle,
 * con su propio error.
 *
 * Una ficha no es todo-o-nada. Si el histórico de precios responde 500 pero el
 * recurso principal se leyó bien, esconder la pantalla entera es peor que
 * enseñarla con un aviso en esa tarjeta — y el `try/catch` mudo del Blazor,
 * que dejaba la lista vacía sin decir nada, es peor todavía: no se distingue
 * "no hay datos" de "no se pudieron leer".
 *
 * La regla es: **el recurso principal es fatal, lo complementario degrada**.
 */
export interface Bloque<T> {
  readonly datos: T;
  readonly error: string | null;
}

export function bloque<T>(datos: T, error: string | null = null): Bloque<T> {
  return { datos, error };
}

/**
 * Envuelve una lectura secundaria para que **nunca** propague su error al
 * `forkJoin` que la acompaña: lo guarda en el bloque junto al `respaldo`.
 *
 * `mensaje` es el respaldo de texto para lo que no llega del cable (fallo de
 * red, CORS, 502 sin cuerpo); cuando el backend manda su `{error}`, se muestra
 * ese, que es más concreto.
 */
export function complementario<T>(
  fuente: Observable<T>,
  respaldo: T,
  mensaje: string,
): Observable<Bloque<T>> {
  return fuente.pipe(
    map((datos) => bloque(datos)),
    catchError((error: unknown) =>
      of(bloque(respaldo, error instanceof ApiError ? error.message : mensaje)),
    ),
  );
}
