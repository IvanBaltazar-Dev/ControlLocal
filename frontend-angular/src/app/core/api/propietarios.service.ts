import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ConstanciaAutorizacion } from '../autorizacion';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

export interface Propietario {
  id: number;
  tipoPersona: string;
  tipoDocumento: string;
  numeroDocumento: string;
  nombre: string;
  telefono?: string;
  correo?: string;
  estado: string;
  consentimientoUsoDato?: boolean;
  fechaCreacion?: string;
  cantidadLocales: number;
}

/**
 * Filtros del catálogo. Los dos son **aditivos y opcionales**: omitidos, el
 * endpoint responde exactamente lo que respondía antes de que existieran.
 * `texto` busca en nombre o razón social, documento y correo.
 */
export interface FiltrosPropietarios {
  pagina?: number;
  tamano?: number;
  texto?: string;
  estado?: string;
}

export interface ResumenPropietarios {
  total: number;
  activos: number;
  inactivos: number;
}

/**
 * Espejo de `PropietarioRequest`. El **documento no se cambia** en el PUT, como
 * en clientes: identifica a la persona.
 */
export interface DatosPropietario {
  tipoPersona?: string;
  tipoDocumento?: string;
  numeroDocumento?: string;
  nombre?: string;
  telefono?: string;
  correo?: string;
  consentimientoUsoDato?: boolean;
  estado?: string;
}

@Injectable({ providedIn: 'root' })
export class PropietariosService {
  private readonly api = inject(ApiClient);

  pagina(pagina = 1, tamano = 50): Promise<PageResponse<Propietario>> {
    return this.api.get<PageResponse<Propietario>>('propietarios', { pagina, tamano });
  }

  /** Cancelable: `switchMap` aborta la lectura anterior al cambiar de filtro. */
  pagina$(filtros: FiltrosPropietarios = {}): Observable<PageResponse<Propietario>> {
    return this.api.get$<PageResponse<Propietario>>('propietarios', { ...filtros });
  }

  /**
   * Cubos del catálogo. **Llevan alcance**, igual que la lista: el BROKER
   * cuenta solo los propietarios de sus propiedades, así que dos actores ven
   * totales distintos del mismo catálogo y ninguno está mal.
   *
   * No recibe `estado`: es el cubo que devuelve.
   */
  resumen$(
    filtros: Omit<FiltrosPropietarios, 'estado' | 'pagina' | 'tamano'> = {},
  ): Observable<ResumenPropietarios> {
    return this.api.get$<ResumenPropietarios>('propietarios/resumen', { ...filtros });
  }

  obtener(id: number): Promise<Propietario> {
    return this.api.get<Propietario>(`propietarios/${id}`);
  }

  /**
   * Constancia de la autorización de datos (D-27). Misma forma y mismo alcance
   * que la de cliente: es el mismo hecho sobre la misma persona.
   */
  autorizacion(id: number): Promise<ConstanciaAutorizacion> {
    return this.api.get<ConstanciaAutorizacion>(`propietarios/${id}/autorizacion`);
  }

  /**
   * Alta y edición son de **AGENTE**, no de broker ni admin.
   *
   * Las dos responden `cantidadLocales` en **0** por paridad con el cable: la
   * v1 no lo recalcula al escribir. La pantalla no debe pintar ese 0 como si
   * fuera el contador real —hay que releer el listado o la ficha—.
   */
  registrar(datos: DatosPropietario): Promise<Propietario> {
    return this.api.post<Propietario>('propietarios', datos);
  }

  actualizar(id: number, datos: DatosPropietario): Promise<Propietario> {
    return this.api.put<Propietario>(`propietarios/${id}`, datos);
  }

  /** Baja LÓGICA: la persona queda en estado `I`. */
  desactivar(id: number): Promise<void> {
    return this.api.delete<void>(`propietarios/${id}`);
  }

  /** Reactivar no tiene endpoint propio: es el PUT con `estado = 'A'`. */
  reactivar(id: number, propietario: Propietario): Promise<Propietario> {
    return this.actualizar(id, {
      nombre: propietario.nombre,
      telefono: propietario.telefono,
      correo: propietario.correo,
      consentimientoUsoDato: propietario.consentimientoUsoDato,
      estado: 'A',
    });
  }

  /**
   * Variante cancelable, para encadenar con `switchMap`.
   *
   * El alcance lo decide el backend y **no es el mismo para los tres roles**:
   * ADMIN y AGENTE ven el catálogo del tenant, pero el BROKER solo alcanza los
   * propietarios de **sus propiedades** (vía captación o prospección). Un id
   * fuera de su alcance no responde el registro.
   */
  obtener$(id: number): Observable<Propietario> {
    return this.api.get$<Propietario>(`propietarios/${id}`);
  }
}
