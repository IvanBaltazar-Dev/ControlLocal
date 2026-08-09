import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/**
 * Contrato CONGELADO: `InteraccionResponse` de la v1.
 *
 * Es la **bitácora polimórfica** del sistema: una interacción cuelga de UNA de
 * cuatro entidades según `contexto`, y las otras tres FK vienen vacías. Desde
 * V7 lo garantiza un CHECK de la base (la v1 solo lo validaba en REST).
 *
 * `personaTipo`/`personaNombre` son el atajo de presentación del cable: quién
 * está al otro lado de la conversación, ya resuelto —cliente o propietario—
 * para no obligar a la pantalla a elegir entre `clienteNombre` y
 * `propietarioNombre` según el contexto.
 */
export interface Interaccion {
  id: number;
  /** OPORTUNIDAD | PROSPECCION | CAPTACION | CLIENTE. */
  contexto?: string;
  idOportunidad?: number;
  idProspeccion?: number;
  idCaptacion?: number;
  idCliente?: number;
  idPropietario?: number;
  codigoProspeccion?: string;
  fechaHora?: string;
  /** L llamada, W WhatsApp, E email, P presencial, R reunión, T portal, O otro. */
  canalContacto?: string;
  /** Enum MIXTO: códigos de una letra heredados y códigos-palabra por contexto. */
  resultado?: string;
  observaciones?: string;
  transcripcionNota?: string;
  clienteNombre?: string;
  propietarioNombre?: string;
  personaTipo?: string;
  personaNombre?: string;
  codigoCaptacion?: string;
  agenteNombre?: string;
}

/**
 * Filtros de `GET /interacciones`.
 *
 * Dos reglas del cable que se pagan con un 400 si se ignoran:
 * - **Solo un filtro de entidad a la vez** (`idOportunidad`, `idProspeccion`,
 *   `idCaptacion`, `idCliente`); combinar dos responde _"Filtra por una sola
 *   entidad de interaccion."_.
 * - `grupo = PROPIETARIO` significa contexto PROSPECCION **o** CAPTACION;
 *   cualquier otro valor distinto de `TODAS` devuelve el complemento (las del
 *   cliente). Es la partición que separa las dos conversaciones del negocio.
 *
 * El `tamano` por defecto de este recurso es **50**, no 10.
 */
export interface FiltrosInteracciones {
  pagina?: number;
  tamano?: number;
  contexto?: string;
  idOportunidad?: number;
  idProspeccion?: number;
  idCaptacion?: number;
  idCliente?: number;
  /** PROPIETARIO | CLIENTE | TODAS. */
  grupo?: string;
  resultado?: string;
  canal?: string;
  /** Búsqueda libre; se aplica ANTES de paginar. */
  q?: string;
}

/** Espejo de `InteraccionRequest`. */
export interface DatosInteraccion {
  contexto?: string;
  idOportunidad?: number;
  idProspeccion?: number;
  idCaptacion?: number;
  idCliente?: number;
  canalContacto?: string;
  resultado?: string;
  observaciones?: string;
  transcripcionNota?: string;
}

const RECURSO = 'interacciones';

@Injectable({ providedIn: 'root' })
export class InteraccionesService {
  private readonly api = inject(ApiClient);

  pagina$(filtros: FiltrosInteracciones = {}): Observable<PageResponse<Interaccion>> {
    return this.api.get$<PageResponse<Interaccion>>(RECURSO, { ...filtros });
  }

  pagina(filtros: FiltrosInteracciones = {}): Promise<PageResponse<Interaccion>> {
    return this.api.get<PageResponse<Interaccion>>(RECURSO, { ...filtros });
  }

  obtener(id: number): Promise<Interaccion> {
    return this.api.get<Interaccion>(`${RECURSO}/${id}`);
  }

  /**
   * Alta. El contexto se puede omitir: el backend lo infiere por el id
   * presente, en el orden prospección → captación → cliente → oportunidad. Se
   * envía explícito igual, para que la pantalla no dependa de esa inferencia.
   */
  registrar(datos: DatosInteraccion): Promise<Interaccion> {
    return this.api.post<Interaccion>(RECURSO, datos);
  }

  /**
   * Edición. El backend **solo toca `resultado` y `observaciones`**: ni el
   * contexto, ni la entidad colgada, ni el canal, ni la fecha. Por eso la firma
   * pide justo esos dos y no un `DatosInteraccion` completo, que prometería
   * cambios que el PUT descarta en silencio.
   */
  actualizar(id: number, resultado: string, observaciones?: string): Promise<Interaccion> {
    return this.api.put<Interaccion>(`${RECURSO}/${id}`, { resultado, observaciones });
  }

  /** La bitácora de un cliente (contexto CLIENTE), sin propiedad asociada. */
  porCliente$(idCliente: number, tamano = 100): Observable<PageResponse<Interaccion>> {
    return this.pagina$({ contexto: 'CLIENTE', idCliente, pagina: 1, tamano });
  }

  /** Las interacciones de una oportunidad concreta. */
  porOportunidad$(idOportunidad: number, tamano = 100): Observable<PageResponse<Interaccion>> {
    return this.pagina$({ contexto: 'OPORTUNIDAD', idOportunidad, pagina: 1, tamano });
  }
}
