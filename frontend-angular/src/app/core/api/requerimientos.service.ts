import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';

/**
 * Contrato CONGELADO: `RequerimientoResponse` de la v1. Es **la búsqueda
 * declarada por el cliente**, y lo único que alimenta el matching de cartera:
 * solo entran los `ACTIVO`.
 *
 * Dos rarezas del cable que hay que respetar al construir el formulario:
 * - `tipoInmueble` viaja con el **nombre del enum** (`LOCAL_COMERCIAL`), a
 *   diferencia de casi todo lo demás. `estado`, en cambio, **sí** usa la letra
 *   (`A`/`P`/`C`) desde la normalización V15–V20: el §3 del contrato F3 es
 *   anterior y todavía dice `ACTIVO`; manda `docs/ai/matriz-codigos-estado.md`.
 * - `distritos` viaja como **nombres**, no como ids del catálogo.
 *
 * Todos los límites son opcionales: un requerimiento sin renta ni metraje es
 * válido, y en el matching esos criterios cuentan como NO_APLICA en vez de
 * fallar. Por eso el formulario no puede exigirlos.
 */
export interface Requerimiento {
  id: number;
  idCliente?: number;
  rubro?: string;
  tipoInmueble?: string;
  rentaMin?: number;
  rentaMax?: number;
  moneda?: string;
  metrajeMin?: number;
  metrajeMax?: number;
  frenteMinimo?: number;
  /** A activo, P pausado, C cerrado. */
  estado?: string;
  observaciones?: string;
  distritos?: string[];
  fechaCreacion?: string;
  fechaActualizacion?: string;
}

/** Espejo de `RequerimientoRequest`. */
export interface DatosRequerimiento {
  idCliente?: number;
  rubro?: string;
  tipoInmueble?: string;
  rentaMin?: number;
  rentaMax?: number;
  moneda?: string;
  metrajeMin?: number;
  metrajeMax?: number;
  frenteMinimo?: number;
  estado?: string;
  observaciones?: string;
  distritos?: string[];
}

const RECURSO = 'requerimientos';

@Injectable({ providedIn: 'root' })
export class RequerimientosService {
  private readonly api = inject(ApiClient);

  /**
   * Los requerimientos de un cliente. Devuelve una **lista suelta**, no un
   * sobre paginado: es la única lectura de F3 que no pagina.
   */
  porCliente$(idCliente: number): Observable<Requerimiento[]> {
    return this.api.get$<Requerimiento[]>(`${RECURSO}/cliente/${idCliente}`);
  }

  porCliente(idCliente: number): Promise<Requerimiento[]> {
    return this.api.get<Requerimiento[]>(`${RECURSO}/cliente/${idCliente}`);
  }

  crear(datos: DatosRequerimiento): Promise<Requerimiento> {
    return this.api.post<Requerimiento>(RECURSO, datos);
  }

  /** El PUT conserva el cliente actual si el request no trae `idCliente`. */
  actualizar(id: number, datos: DatosRequerimiento): Promise<Requerimiento> {
    return this.api.put<Requerimiento>(`${RECURSO}/${id}`, datos);
  }

  /** Pausar/cerrar/reactivar. Tiene endpoint propio: no es parte del PUT. */
  cambiarEstado(id: number, estado: string): Promise<Requerimiento> {
    return this.api.post<Requerimiento>(`${RECURSO}/${id}/estado`, { estado });
  }
}
