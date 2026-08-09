import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/**
 * Contrato CONGELADO: `VisitaResponse` de la v1.
 *
 * `fechaVisita` viaja como `LocalDate` (`2026-08-15`) y `horaVisita` como
 * `LocalTime` sin segundos (`16:00`), que es justo lo que producen y aceptan
 * los `<input type="date">` y `<input type="time">` del navegador.
 *
 * Los cuatro campos del desenlace (`nivelInteres`, `objecionPrincipal`,
 * `opinionPrecio`, `proximaAccion`) solo existen si la visita se realizó: un
 * CHECK de la base lo impone, y cancelar o marcar no realizada **los limpia**.
 */
export interface Visita {
  id: number;
  idOportunidad?: number;
  codigoOportunidad?: string;
  fechaVisita?: string;
  horaVisita?: string;
  observaciones?: string;
  /** P programada, G reprogramada, R realizada, N no realizada, C cancelada. */
  estado?: string;
  /** Desenlace comercial (`RESULTADO_VISITA`); solo tras marcarla realizada. */
  resultado?: string;
  idCliente?: number;
  clienteNombre?: string;
  idCaptacion?: number;
  codigoCaptacion?: string;
  direccionLocal?: string;
  distritoLocal?: string;
  idAgente?: number;
  agenteNombre?: string;
  /** 1 a 5. */
  nivelInteres?: number;
  objecionPrincipal?: string;
  opinionPrecio?: string;
  proximaAccion?: string;
}

/** Filtros de `GET /visitas`. La búsqueda libre se llama `query`, como en oportunidades. */
export interface FiltrosVisitas {
  pagina?: number;
  tamano?: number;
  idOportunidad?: number;
  estado?: string;
  distrito?: string;
  query?: string;
}

/**
 * KPI de la bandeja por estado + los distritos del alcance, contados en la base
 * sobre el mismo conjunto que pagina la lista. Los distritos vienen aquí para
 * que el selector sea data-driven sin descargar la agenda.
 */
export interface ResumenVisitas {
  total: number;
  programadas: number;
  reprogramadas: number;
  realizadas: number;
  noRealizadas: number;
  canceladas: number;
  distritos: string[];
}

/** Espejo de `VisitaRequest`: programar una visita de una oportunidad propia. */
export interface DatosVisita {
  idOportunidad?: number;
  fechaVisita?: string;
  horaVisita?: string;
  observaciones?: string;
}

/**
 * Espejo de `ResultadoVisitaRequest`. `razonNoContinuidad` es la razón
 * tipificada (`MOTIVO_NO_CONTINUIDAD`) que el backend usa para cerrar la
 * oportunidad cuando el resultado implica que el cliente no continúa.
 */
export interface DesenlaceVisita {
  resultado?: string;
  observaciones?: string;
  razonNoContinuidad?: string;
  nivelInteres?: number;
  objecionPrincipal?: string;
  opinionPrecio?: string;
  proximaAccion?: string;
}

const RECURSO = 'visitas';

/** Estados desde los que la visita todavía puede realizarse o moverse. */
export const VISITA_PENDIENTE = new Set(['P', 'G']);

@Injectable({ providedIn: 'root' })
export class VisitasService {
  private readonly api = inject(ApiClient);

  pagina$(filtros: FiltrosVisitas = {}): Observable<PageResponse<Visita>> {
    return this.api.get$<PageResponse<Visita>>(RECURSO, { ...filtros });
  }

  pagina(filtros: FiltrosVisitas = {}): Promise<PageResponse<Visita>> {
    return this.api.get<PageResponse<Visita>>(RECURSO, { ...filtros });
  }

  /** No recibe `estado` ni `distrito`: son los filtros que este resumen acota. */
  resumen$(
    filtros: Omit<FiltrosVisitas, 'estado' | 'distrito' | 'pagina' | 'tamano'> = {},
  ): Observable<ResumenVisitas> {
    return this.api.get$<ResumenVisitas>(`${RECURSO}/resumen`, { ...filtros });
  }

  /**
   * Agenda inmediata. **El tope de 8 es duro en el backend**: pedir más no
   * devuelve más. El sobre viene con `total` = número de items y `page` = 1,
   * así que no se puede paginar sobre él.
   */
  proximas$(tamano = 8): Observable<PageResponse<Visita>> {
    return this.api.get$<PageResponse<Visita>>(`${RECURSO}/proximas`, { tamano });
  }

  /**
   * Todas las visitas de un mes, **sin paginar**, para el calendario.
   * El backend valida `2000 ≤ anio ≤ 2100` y `1 ≤ mes ≤ 12`.
   */
  mes$(anio: number, mes: number): Observable<PageResponse<Visita>> {
    return this.api.get$<PageResponse<Visita>>(`${RECURSO}/mes`, { anio, mes });
  }

  obtener(id: number): Promise<Visita> {
    return this.api.get<Visita>(`${RECURSO}/${id}`);
  }

  /**
   * Programar. Exige que la oportunidad sea **del propio agente**: aquí el
   * broker no tiene alcance que valga, se compara directo con su rol operativo.
   */
  programar(datos: DatosVisita): Promise<Visita> {
    return this.api.post<Visita>(RECURSO, datos);
  }

  /** Reprogramar desde P o G; deja la visita en G. */
  reprogramar(id: number, fechaVisita: string, horaVisita: string): Promise<Visita> {
    return this.api.patch<Visita>(`${RECURSO}/${id}/reprogramar`, { fechaVisita, horaVisita });
  }

  /** Cancelar: el motivo se escribe en `observaciones` y limpia el desenlace. */
  cancelar(id: number, motivo: string): Promise<Visita> {
    return this.api.patch<Visita>(`${RECURSO}/${id}/cancelar`, { motivo });
  }

  /** Marcar realizada. Solo desde P o G; es el paso previo al desenlace. */
  realizar(id: number): Promise<Visita> {
    return this.api.patch<Visita>(`${RECURSO}/${id}/realizar`, {});
  }

  /** No realizada: mismo origen que `realizar`, con motivo obligatorio. */
  noRealizada(id: number, motivo: string): Promise<Visita> {
    return this.api.patch<Visita>(`${RECURSO}/${id}/no-realizada`, { motivo });
  }

  /**
   * Desenlace comercial. Exige la visita **realizada** y es **irrepetible**:
   * el segundo intento responde _"La visita ya tiene un resultado
   * registrado."_. Por eso la pantalla lo trata como un formulario de una sola
   * oportunidad, no como un campo editable.
   */
  registrarResultado(id: number, desenlace: DesenlaceVisita): Promise<Visita> {
    return this.api.patch<Visita>(`${RECURSO}/${id}/resultado`, desenlace);
  }

  /** Las visitas de una oportunidad, para su expediente. */
  porOportunidad$(idOportunidad: number, tamano = 100): Observable<PageResponse<Visita>> {
    return this.pagina$({ idOportunidad, pagina: 1, tamano });
  }
}
