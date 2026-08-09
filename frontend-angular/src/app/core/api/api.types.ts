/**
 * Formas del contrato CONGELADO que se repiten en todos los módulos. Los
 * nombres de campo son los del backend (`PageResponse`, `ErrorResponse`); no
 * se "mejoran" mientras el legado siga vivo.
 */

/** Sobre de paginación del cable. Ojo: `page` es 1-based, no 0-based. */
export interface PageResponse<T> {
  items: T[];
  totalRecords: number;
  page: number;
  pageSize: number;
}

/**
 * Cuerpo de error del cable: siempre `{"error": "..."}`.
 *
 * `codigo` es **aditivo y casi siempre ausente** (el backend omite los nulos).
 * Lo llevan las situaciones en las que el cliente tiene que hacer algo
 * distinto y no le basta el `status`: la sesión capada (contraseña temporal o
 * segundo factor pendiente) y los fallos de MFA. **Es lo único comparable**;
 * el `error` es texto en español, traducible, y comparar por él ata el SPA a
 * la redacción del servidor.
 */
export interface ErrorResponse {
  error: string;
  codigo?: string;
}

/**
 * Error del API ya interpretado. `mensaje` sale del cuerpo `{error}` cuando
 * el backend lo manda; si no (fallo de red, CORS, 502 sin cuerpo), trae un
 * texto propio y `status` queda en 0.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    override readonly message: string,
    /** Código estable del cuerpo, cuando el backend lo manda. Ver `ErrorResponse`. */
    readonly codigo?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  /** Sin sesión válida. El interceptor ya cerró sesión cuando llega aquí. */
  get noAutenticado(): boolean {
    return this.status === 401;
  }

  /** Fuera del alcance o del rol. Mensaje congelado del backend. */
  get sinPermiso(): boolean {
    return this.status === 403;
  }

  get noEncontrado(): boolean {
    return this.status === 404;
  }

  /**
   * Conflicto de unicidad. El código está congelado, el texto NO: no se
   * compara el mensaje para decidir nada.
   */
  get conflicto(): boolean {
    return this.status === 409;
  }

  /** Se agotó el límite de intentos (solo `/auth/login`: 10 por minuto). */
  get demasiadasSolicitudes(): boolean {
    return this.status === 429;
  }

  /** El almacén de binarios no respondió (502 del contrato). */
  get errorDeAlmacen(): boolean {
    return this.status === 502;
  }
}

/**
 * Parámetros de consulta. `undefined` y `null` se omiten — el backend
 * distingue "sin filtro" de "filtro vacío" en varios recursos.
 */
export type ParametrosConsulta = Record<
  string,
  string | number | boolean | undefined | null
>;

/** Página vacía, para inicializar señales sin `null`. */
export function paginaVacia<T>(pageSize = 10): PageResponse<T> {
  return { items: [], totalRecords: 0, page: 1, pageSize };
}
