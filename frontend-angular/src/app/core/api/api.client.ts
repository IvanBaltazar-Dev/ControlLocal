import {
  HttpClient,
  HttpErrorResponse,
  HttpEvent,
  HttpParams,
  HttpResponse,
} from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  catchError,
  filter,
  firstValueFrom,
  from,
  map,
  mergeMap,
  Observable,
  throwError,
} from 'rxjs';
import { API_BASE_URL } from './api.config';
import { ApiError, ErrorResponse, ParametrosConsulta } from './api.types';

/**
 * Único punto por el que el SPA habla con el API v2. Equivale al
 * `ApiClient.cs` del Blazor y existe por las mismas tres razones: centralizar
 * la base de la URL, traducir el cuerpo de error del cable (`{"error": ...}`)
 * a algo que las pantallas puedan preguntar sin comparar cadenas, y dar una
 * superficie `async/await` en vez de obligar a cada componente a manejar
 * observables.
 *
 * El token NO se pone aquí: lo adjunta `authInterceptor`, que además es quien
 * convierte cualquier 401 en cierre de sesión completo.
 */
@Injectable({ providedIn: 'root' })
export class ApiClient {
  private readonly http = inject(HttpClient);

  /**
   * Variante observable para listados reactivos. Al usarla con `switchMap`,
   * Angular cancela la peticion HTTP anterior cuando cambia la URL o el filtro.
   */
  get$<T>(ruta: string, params?: ParametrosConsulta): Observable<T> {
    return this.http
      .get<T>(this.url(ruta), { params: this.query(params) })
      .pipe(catchError((error) => throwError(() => traducir(error))));
  }

  get<T>(ruta: string, params?: ParametrosConsulta): Promise<T> {
    return firstValueFrom(this.get$<T>(ruta, params));
  }

  /**
   * `cabeceras` existe por `Idempotency-Key`, que identifica una OPERACIÓN
   * económica y no puede añadirse en un interceptor genérico: un retry
   * automático la cambiaría y anularía la garantía.
   */
  post<T>(
    ruta: string,
    cuerpo?: unknown,
    params?: ParametrosConsulta,
    cabeceras?: Record<string, string>,
  ): Promise<T> {
    return this.pedir(
      this.http.post<T>(this.url(ruta), cuerpo ?? {}, {
        params: this.query(params),
        headers: cabeceras,
      }),
    );
  }

  put<T>(ruta: string, cuerpo?: unknown): Promise<T> {
    return this.pedir(this.http.put<T>(this.url(ruta), cuerpo ?? {}));
  }

  patch<T>(ruta: string, cuerpo?: unknown): Promise<T> {
    return this.pedir(this.http.patch<T>(this.url(ruta), cuerpo ?? {}));
  }

  /**
   * `cuerpo` y `cabeceras` son opcionales y existen por el segundo factor: la
   * revocación de un factor **lleva cuerpo** (el motivo, obligatorio) y la
   * ajena lleva además el token de elevación **en cabecera**, nunca en la URL
   * —una URL viaja a los logs del servidor y al historial del navegador—.
   */
  delete<T>(ruta: string, cuerpo?: unknown, cabeceras?: Record<string, string>): Promise<T> {
    return this.pedir(this.http.delete<T>(this.url(ruta), { body: cuerpo, headers: cabeceras }));
  }

  /**
   * Sube un archivo como octet-stream sin convertir sus bytes a base64.
   * Los eventos permiten mostrar progreso y la desuscripción cancela la carga.
   */
  postBinario$<T>(
    ruta: string,
    contenido: Blob,
    params?: ParametrosConsulta,
  ): Observable<HttpEvent<T>> {
    return this.http
      .post<T>(this.url(ruta), contenido, {
        headers: { 'Content-Type': 'application/octet-stream' },
        params: this.query(params),
        observe: 'events',
        reportProgress: true,
      })
      .pipe(catchError((error) => throwError(() => traducir(error))));
  }

  /** Variante Promise para pantallas que no necesitan progreso. */
  postBinario<T>(ruta: string, contenido: Blob, params?: ParametrosConsulta): Promise<T> {
    return firstValueFrom(
      this.postBinario$<T>(ruta, contenido, params).pipe(
        filter((evento): evento is HttpResponse<T> => evento instanceof HttpResponse),
        map((respuesta) => respuesta.body as T),
      ),
    );
  }

  /**
   * Descarga un binario CON el token de sesión, en vez de por la clave
   * pública de `GET /documentos/contenido`.
   *
   * Ese endpoint es público por una restricción del Blazor —que el visor
   * cargara archivos sin propagar el JWT al navegador— que Angular no tiene:
   * aquí el token ya está en memoria. Apoyarse en la URL pública volvería
   * imposible retirarla en el corte, y hoy esa clave no caduca, se puede
   * adivinar con 32 bits y viaja en el query string (ver la deuda de
   * seguridad en `docs/ai/checklist-migracion.md` §1).
   */
  descargar$(ruta: string, params?: ParametrosConsulta): Observable<Blob> {
    return this.http
      .get(this.url(ruta), {
          params: this.query(params),
          responseType: 'blob',
        })
      .pipe(
        catchError((error) =>
          from(traducirBlob(error)).pipe(
            mergeMap((traducido) => throwError(() => traducido)),
          ),
        ),
      );
  }

  descargar(ruta: string, params?: ParametrosConsulta): Promise<Blob> {
    return firstValueFrom(this.descargar$(ruta, params));
  }

  private url(ruta: string): string {
    return `${API_BASE_URL}/${ruta.replace(/^\/+/, '')}`;
  }

  /** Omite `undefined`/`null`: el cable distingue "sin filtro" de filtro vacío. */
  private query(params?: ParametrosConsulta): HttpParams {
    let query = new HttpParams();
    if (!params) {
      return query;
    }
    for (const [clave, valor] of Object.entries(params)) {
      if (valor !== undefined && valor !== null && valor !== '') {
        query = query.set(clave, String(valor));
      }
    }
    return query;
  }

  private pedir<T>(peticion: Observable<T>): Promise<T> {
    return firstValueFrom(peticion.pipe(catchError((error) => throwError(() => traducir(error)))));
  }
}

/**
 * `HttpErrorResponse` -> `ApiError`. El backend siempre responde
 * `{"error": "..."}`, pero un fallo de red o de CORS no trae cuerpo: ahí el
 * `status` es 0 y hay que decirlo con palabras, no mostrar "undefined".
 */
function traducir(error: unknown): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return new ApiError(0, 'No se pudo completar la operación.');
  }
  if (error.status === 0) {
    return new ApiError(0, 'No se pudo conectar con el servidor. Verifica que el API esté arriba.');
  }
  const cuerpo = error.error as ErrorResponse | string | null;
  if (cuerpo && typeof cuerpo === 'object' && typeof cuerpo.error === 'string') {
    return new ApiError(error.status, cuerpo.error, cuerpo.codigo);
  }
  if (typeof cuerpo === 'string' && cuerpo.trim()) {
    return new ApiError(error.status, cuerpo);
  }
  return new ApiError(error.status, error.statusText || 'Error del servidor.');
}

/** Igual que `traducir`, pero leyendo antes el cuerpo `{error}` que vino como Blob. */
async function traducirBlob(error: unknown): Promise<ApiError> {
  if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
    try {
      const texto = await error.error.text();
      const cuerpo = JSON.parse(texto) as ErrorResponse;
      if (typeof cuerpo?.error === 'string') {
        return new ApiError(error.status, cuerpo.error);
      }
    } catch {
      // El cuerpo no era el JSON del cable; cae al camino normal.
    }
  }
  return traducir(error);
}
