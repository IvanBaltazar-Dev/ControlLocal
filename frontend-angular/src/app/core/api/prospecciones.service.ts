import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/**
 * Contrato CONGELADO: los 22 campos de `ProspeccionResponse`, con los mismos
 * nombres del cable.
 *
 * Dos rarezas del contrato que hay que conocer antes de leer `estado`:
 * - **La v1 nunca emite `E`** ("propuesta entregada"). Entregar la propuesta
 *   deja el estado en `S` y la marca real es `fechaPropuesta` +
 *   `resultadoPropuesta = 'P'`. Una pantalla que espere `E` para dar por
 *   entregada la propuesta no lo verá nunca.
 * - `resultadoPropuesta` es A/R/S/P, no un estado de la máquina.
 */
export interface Prospeccion {
  id: number;
  codigoProspeccion?: string;
  localId?: number;
  localCodigo?: string;
  direccion?: string;
  distrito?: string;
  areaM2?: number;
  rubro?: string;
  precioReferencial?: number;
  monedaReferencial?: string;
  propietarioNombre?: string;
  idAgente?: number;
  agenteNombre?: string;
  /** P prospecto, C contactado, R reunión, S seguimiento, T captado, D descartado. */
  estado?: string;
  /** A aceptada, R rechazada, S recontactar, P pendiente. */
  resultadoPropuesta?: string;
  fechaContacto?: string;
  fechaReunion?: string;
  fechaPropuesta?: string;
  fechaRecontacto?: string;
  observaciones?: string;
  idCaptacion?: number;
  captacionCodigo?: string;
  disponibilidad?: string;
}

/**
 * Filtros de `GET /prospecciones`. Ojo: este recurso pagina con
 * `pagina`/`tamano` y **no** acepta los alias `page`/`page_size` que sí
 * tienen `/locales` y `/clientes`.
 */
export interface FiltrosProspecciones {
  pagina?: number;
  tamano?: number;
  estado?: string;
  distrito?: string;
  idCaptacion?: number;
  idLocal?: number;
  idAgente?: number;
  /** Equipo del broker seleccionado; se aplica además del alcance del actor. */
  idBrokerSupervisor?: number;
  /** Búsqueda libre. */
  q?: string;
  orden?: string;
}

/**
 * Orden de avance de la máquina de estados, para elegir la prospección más
 * avanzada cuando un local tiene varias. Réplica del `RangoEstado` del
 * Blazor; `E` comparte nivel con `S` porque el cable no lo emite.
 */
const AVANCE: Readonly<Record<string, number>> = {
  T: 6,
  S: 5,
  E: 4,
  R: 3,
  C: 2,
  P: 1,
};

@Injectable({ providedIn: 'root' })
export class ProspeccionesService {
  private readonly api = inject(ApiClient);

  pagina$(filtros: FiltrosProspecciones = {}): Observable<PageResponse<Prospeccion>> {
    return this.api.get$<PageResponse<Prospeccion>>('prospecciones', { ...filtros });
  }

  pagina(filtros: FiltrosProspecciones = {}): Promise<PageResponse<Prospeccion>> {
    return this.api.get<PageResponse<Prospeccion>>('prospecciones', { ...filtros });
  }

  obtener(id: number): Promise<Prospeccion> {
    return this.api.get<Prospeccion>(`prospecciones/${id}`);
  }

  obtener$(id: number): Observable<Prospeccion> {
    return this.api.get$<Prospeccion>(`prospecciones/${id}`);
  }

  contactar(id: number): Promise<Prospeccion> {
    return this.accion(id, 'contactar');
  }

  registrarReunion(id: number): Promise<Prospeccion> {
    return this.accion(id, 'reunion');
  }

  entregarPropuesta(id: number): Promise<Prospeccion> {
    return this.accion(id, 'propuesta');
  }

  registrarSeguimiento(id: number): Promise<Prospeccion> {
    return this.accion(id, 'seguimiento');
  }

  rechazar(id: number, motivo: string): Promise<Prospeccion> {
    return this.api.post<Prospeccion>(`prospecciones/${id}/rechazar`, { motivo });
  }

  descartar(id: number, motivo: string): Promise<Prospeccion> {
    return this.api.post<Prospeccion>(`prospecciones/${id}/descartar`, { motivo });
  }

  marcarCaptada(
    id: number,
    idCaptacion: number,
    codigoCaptacion: string,
  ): Promise<Prospeccion> {
    return this.api.post<Prospeccion>(`prospecciones/${id}/marcar-captado`, {
      idCaptacion,
      codigoCaptacion,
    });
  }

  /** Prospecciones activas cuyo reloj de recontacto ya venció. */
  recontactar$(dias = 7, pagina = 1, tamano = 10): Observable<PageResponse<Prospeccion>> {
    return this.api.get$<PageResponse<Prospeccion>>('prospecciones/recontactar', {
      dias,
      pagina,
      tamano,
    });
  }

  /**
   * Las prospecciones de un local, filtradas **en el servidor**.
   *
   * El Blazor descargaba la bandeja entera y la filtraba en memoria por
   * `LocalId` o por dirección coincidente; aquí se usa el filtro `idLocal`
   * que el recurso ya expone (RC-003: ninguna pantalla nueva carga listas
   * completas). Se pierde el emparejamiento por dirección, que era un parche
   * para filas viejas sin `localId`: en la v2 el alta del local crea siempre
   * su prospección inicial enlazada.
   *
   * El alcance lo sigue decidiendo el backend: un AGENTE solo ve las suyas,
   * así que la lista puede venir vacía para un local que no le pertenece.
   */
  porLocal$(idLocal: number, tamano = 20): Observable<PageResponse<Prospeccion>> {
    return this.pagina$({ idLocal, pagina: 1, tamano });
  }

  private accion(id: number, nombre: string): Promise<Prospeccion> {
    return this.api.post<Prospeccion>(`prospecciones/${id}/${nombre}`, null);
  }
}

/** La prospección más avanzada del local, o `null` si no hay ninguna. */
export function masAvanzada(prospecciones: readonly Prospeccion[]): Prospeccion | null {
  return (
    [...prospecciones].sort((a, b) => avance(b.estado) - avance(a.estado))[0] ?? null
  );
}

/** Nivel de avance de un estado de prospección; desconocido = 0. */
export function avance(estado: string | null | undefined): number {
  return estado ? (AVANCE[estado] ?? 0) : 0;
}
