import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/**
 * Contrato CONGELADO: los 24 campos de `CaptacionResponse`.
 *
 * Lo importante para quien la consuma: **la captación trae `idLocal`**. Toda
 * la ficha de una propiedad se resuelve encadenando ids desde aquí
 * (`idLocal` → local → `idPropietario` → propietario), sin buscar el local por
 * dirección ni el propietario por nombre.
 */
export interface Captacion {
  id: number;
  codigoCaptacion?: string;
  fechaCaptacion?: string;
  fechaInicioVigencia?: string;
  fechaFinVigencia?: string;
  comisionPactada?: number;
  /** A arrendamiento, V venta. */
  tipoOperacion?: string;
  importeReferencia?: number;
  monedaReferencia?: string;
  /** E mensualidades, P porcentaje, F importe fijo. */
  tipoComision?: string;
  /** R renta, V precio de venta, N no aplica. */
  baseCalculo?: string;
  valorComision?: number;
  monedaComision?: string;
  /** I incluido, A adicional, N no aplica. */
  tratamientoIgv?: string;
  motivoSinComision?: string;
  fechaCierre?: string;
  motivoCierre?: string;
  detalleMotivoCierre?: string;
  observaciones?: string;
  /** P pendiente, O observada, R rechazada, A activa, C cerrada, V vencida. */
  estado?: string;
  motivoOperacion?: string;
  /** 1 a 5. */
  urgencia?: number;
  exclusividad?: boolean;
  observacionRevision?: string;
  fechaRevision?: string;
  idLocal?: number;
  direccionLocal?: string;
  distritoLocal?: string;
  areaM2?: number;
  rubro?: string;
  propietarioNombre?: string;
  idAgente?: number;
  agenteNombre?: string;
  idBrokerRevisor?: number;
  fotoPortadaClave?: string;
}

export interface FiltrosCaptaciones {
  pagina?: number;
  tamano?: number;
  estado?: string;
  idAgente?: number;
  q?: string;
}

export interface FiltrosCaptacionesPendientes {
  pagina?: number;
  tamano?: number;
  /** Vacío = P y O; acepta P u O para las pestañas de revisión. */
  estado?: string;
  idAgente?: number;
  q?: string;
}

export interface FiltrosCaptacionesReasignables {
  pagina?: number;
  tamano?: number;
  q?: string;
}

/** Evento congelado del historial de cambios de agente responsable. */
export interface ReasignacionCaptacion {
  idReasignacion: number;
  idCaptacion: number;
  codigoCaptacion?: string;
  direccionLocal?: string;
  idAgenteAnterior?: number;
  agenteAnteriorNombre?: string;
  idAgenteNuevo?: number;
  agenteNuevoNombre?: string;
  idBroker?: number;
  brokerNombre?: string;
  fechaCambio?: string;
  motivo?: string;
}

/** Cuerpo congelado de POST/PUT /captaciones. */
export interface CaptacionRequest {
  codigoCaptacion: string;
  fechaCaptacion: string;
  fechaInicioVigencia: string;
  fechaFinVigencia: string;
  /** Adaptador del contrato histórico; el backend prioriza los campos tipados. */
  comisionPactada: number | null;
  observaciones: string | null;
  idLocal: number;
  idAgente: number;
  /**
   * Espejo heredado de `tipoOperacion`; el backend exige que coincidan
   * (`tg_captacion_operacion_coherente`). Deja de ir fijo a `'A'` en V76: la
   * pantalla declara la operacion, no la supone.
   */
  motivoOperacion: 'A' | 'V';
  urgencia: number;
  exclusividad: boolean;
  tipoOperacion: 'A' | 'V';
  importeReferencia: number;
  monedaReferencia: 'PEN' | 'USD';
  tipoComision: 'E' | 'P' | 'F';
  baseCalculo: 'R' | 'V' | 'N';
  valorComision: number;
  monedaComision: 'PEN' | 'USD';
  tratamientoIgv: 'I' | 'A' | 'N';
  motivoSinComision: string | null;
}

/**
 * Una fila de la cartera del equipo, vista **por inmueble**: la captación más
 * reciente de cada propiedad.
 *
 * No es contrato congelado de la v1 — es una **extensión aditiva** del backend
 * (`GET /captaciones/propiedades-equipo`). Existe porque deduplicar por
 * propiedad no se puede hacer sobre una página de captaciones: obligaría a
 * descargarlas todas, que es lo que hacía el Blazor.
 */
export interface PropiedadEquipo {
  idPropiedad: number;
  idCaptacion?: number;
  codigoCaptacion?: string;
  /** Estado de la captación más reciente: P, O, R, A, C o V. */
  estado?: string;
  codigoLocal?: string;
  direccion?: string;
  distrito?: string;
  rubro?: string;
  areaM2?: number;
  idAgente?: number;
  agenteNombre?: string;
}

export interface FiltrosPropiedadesEquipo {
  pagina?: number;
  tamano?: number;
  texto?: string;
  distrito?: string;
}

export interface ResumenPropiedadesEquipo {
  propiedades: number;
  conCaptacionActiva: number;
  agentesConCartera: number;
  distritos: number;
  /** Distritos presentes en la cartera, para que el filtro sea data-driven. */
  distritosDisponibles: string[];
}

@Injectable({ providedIn: 'root' })
export class CaptacionesService {
  private readonly api = inject(ApiClient);

  pagina$(filtros: FiltrosCaptaciones = {}): Observable<PageResponse<Captacion>> {
    return this.api.get$<PageResponse<Captacion>>('captaciones', { ...filtros });
  }

  /** Variante Promise, para los selectores que buscan bajo demanda. */
  pagina(filtros: FiltrosCaptaciones = {}): Promise<PageResponse<Captacion>> {
    return this.api.get<PageResponse<Captacion>>('captaciones', { ...filtros });
  }

  /** Bandeja de revisión BROKER/ADMIN: solo P/O dentro de su alcance. */
  pendientes$(
    filtros: FiltrosCaptacionesPendientes = {},
  ): Observable<PageResponse<Captacion>> {
    return this.api.get$<PageResponse<Captacion>>('captaciones/pendientes', {
      ...filtros,
    });
  }

  /** Captaciones activas que BROKER/ADMIN pueden mover dentro de su alcance. */
  reasignables$(
    filtros: FiltrosCaptacionesReasignables = {},
  ): Observable<PageResponse<Captacion>> {
    return this.api.get$<PageResponse<Captacion>>('captaciones/reasignables', {
      ...filtros,
    });
  }

  /** Historial completo congelado, ordenado del movimiento más reciente al más antiguo. */
  historialReasignaciones$(): Observable<ReasignacionCaptacion[]> {
    return this.api.get$<ReasignacionCaptacion[]>('captaciones/reasignaciones');
  }

  obtener$(id: number): Observable<Captacion> {
    return this.api.get$<Captacion>(`captaciones/${id}`);
  }

  obtener(id: number): Promise<Captacion> {
    return this.api.get<Captacion>(`captaciones/${id}`);
  }

  /**
   * Captación por su correlativo (`CAP-0001`).
   *
   * El alcance lo impone el backend: el AGENTE solo alcanza las suyas, el
   * BROKER las de sus supervisados y el ADMIN las del tenant; fuera de ahí
   * responde 403 (no 404), así que un código ajeno no se distingue de uno
   * inexistente por el cuerpo, sino por el estado.
   */
  obtenerPorCodigo$(codigo: string): Observable<Captacion> {
    return this.api.get$<Captacion>(`captaciones/codigo/${encodeURIComponent(codigo)}`);
  }

  obtenerPorCodigo(codigo: string): Promise<Captacion> {
    return this.api.get<Captacion>(`captaciones/codigo/${encodeURIComponent(codigo)}`);
  }

  registrar(datos: CaptacionRequest): Promise<Captacion> {
    return this.api.post<Captacion>('captaciones', datos);
  }

  actualizar(id: number, datos: CaptacionRequest): Promise<Captacion> {
    return this.api.put<Captacion>(`captaciones/${id}`, datos);
  }

  decidir(id: number, accion: 'A' | 'O' | 'R', observacion: string | null): Promise<Captacion> {
    return this.api.post<Captacion>(`captaciones/${id}/decision`, { accion, observacion });
  }

  reasignar(id: number, idAgenteNuevo: number, motivo: string): Promise<Captacion> {
    return this.api.post<Captacion>(`captaciones/${id}/reasignar`, {
      idAgenteNuevo,
      motivo,
    });
  }

  cerrar(id: number, motivo: string): Promise<Captacion> {
    return this.api.post<Captacion>(`captaciones/${id}/cierre`, { motivo });
  }

  /**
   * Cartera del equipo, una fila por inmueble. Solo BROKER y ADMIN; el
   * alcance (supervisados o tenant) lo decide el backend.
   */
  propiedadesEquipo$(
    filtros: FiltrosPropiedadesEquipo = {},
  ): Observable<PageResponse<PropiedadEquipo>> {
    return this.api.get$<PageResponse<PropiedadEquipo>>('captaciones/propiedades-equipo', {
      ...filtros,
    });
  }

  /** KPI con el mismo `texto` que la lista. No acepta `distrito` a propósito. */
  resumenPropiedadesEquipo$(texto?: string): Observable<ResumenPropiedadesEquipo> {
    return this.api.get$<ResumenPropiedadesEquipo>(
      'captaciones/propiedades-equipo/resumen',
      { texto },
    );
  }
}
