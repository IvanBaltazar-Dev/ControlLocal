import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { Cliente } from './clientes.service';
import { Propietario } from './propietarios.service';

/**
 * Fila de cualquier sección de una ficha comercial. Es deliberadamente
 * genérica: la misma forma sirve a requerimientos, oportunidades, visitas,
 * solicitudes o cierres, y por eso la pantalla dibuja una sola tabla.
 *
 * `ruta` viaja como **cadena relativa del cable** (`cliente-detail/{id}`,
 * `local-detail/{id}`…), no como URL del SPA: hay que traducirla, y una ruta
 * que apunte a una pantalla todavía no migrada no se ofrece como enlace.
 */
export interface FilaFicha {
  id?: string;
  codigo?: string;
  proceso?: string;
  titulo?: string;
  subtitulo?: string;
  local?: string;
  distrito?: string;
  cliente?: string;
  clienteId?: number;
  propietario?: string;
  propietarioId?: number;
  agente?: string;
  estado?: string;
  fecha?: string;
  ruta?: string;
  icono?: string;
  tono?: string;
  fechaOrden?: string;
}

/**
 * Una sección paginada.
 *
 * **`totalRecords: -1` NO es un error ni un cero**: es el marcador de "sección
 * pendiente" que usa la carga inicial parcial. La ficha de cliente solo trae
 * resueltos los `requerimientos`; el resto llega con este marcador y se pide
 * cuando el usuario abre la pestaña. Tratarlo como un total mostraría "-1
 * registros" y, peor, daría por cargada una sección vacía que nunca se pidió.
 */
export interface SeccionFicha {
  section: string;
  totalRecords: number;
  page: number;
  pageSize: number;
  items: FilaFicha[];
}

export interface FichaCliente {
  cliente: Cliente;
  requerimientoActivo: boolean;
  /** `/oportunidad-form?clienteId={id}` solo para AGENTE con requerimiento activo. */
  ctaRuta: string;
  sections: Record<string, SeccionFicha>;
}

/**
 * Cabecera de la ficha de propietario. **No trae `ctaRuta` ni un equivalente de
 * `requerimientoActivo`**: del lado de la oferta no hay una acción que ofrecer
 * desde aquí.
 *
 * Rareza legacy que se conserva y que la pantalla NO debe pintar: dentro de
 * esta respuesta `propietario.cantidadLocales` viaja en **0** aunque la sección
 * `locales` tenga registros. El número bueno es el `totalRecords` de esa
 * sección, no el contador de la cabecera.
 */
export interface FichaPropietario {
  propietario: Propietario;
  sections: Record<string, SeccionFicha>;
}

/** Secciones de la ficha de cliente, en el orden que fija el contrato E3. */
export const SECCIONES_CLIENTE: readonly string[] = [
  'requerimientos',
  'propiedades',
  'oportunidades',
  'interacciones',
  'visitas',
  'solicitudes',
  'cierres',
  'agentes',
];

/**
 * Secciones de la ficha de propietario, en el orden que fija el contrato E3.
 * **Son otras siete, no un subconjunto de las del cliente**: aquí se mira la
 * oferta (locales, prospecciones, captaciones) y allí la demanda.
 */
export const SECCIONES_PROPIETARIO: readonly string[] = [
  'locales',
  'prospecciones',
  'captaciones',
  'oportunidades',
  'solicitudes',
  'cierres',
  'agentes',
];

/** Tope real del cable: pedir más no trae más. */
export const FILAS_POR_SECCION = 8;

/**
 * Una sección que todavía no se ha resuelto. Se decide por el marcador del
 * cable (`totalRecords` negativo), no por `items.length`: una sección
 * legítimamente vacía trae `totalRecords: 0` y no debe volver a pedirse.
 */
export function esPendiente(seccion: SeccionFicha | undefined): boolean {
  return !seccion || seccion.totalRecords < 0;
}

@Injectable({ providedIn: 'root' })
export class FichaComercialService {
  private readonly api = inject(ApiClient);

  /** Carga inicial: cabecera + primera página de `requerimientos`. */
  fichaCliente$(id: number): Observable<FichaCliente> {
    return this.api.get$<FichaCliente>(`clientes/${id}/ficha-comercial`, {
      tamano: FILAS_POR_SECCION,
    });
  }

  /**
   * Una sección concreta. El recurso acepta los dos juegos de alias
   * (`page`/`pagina` y `page_size`/`tamano`); se usa el corto, que es el que
   * comparte con el resto de los listados de esta vertical.
   */
  seccionCliente$(id: number, section: string, pagina: number): Observable<SeccionFicha> {
    return this.api.get$<SeccionFicha>(`clientes/${id}/ficha-comercial/${section}`, {
      pagina,
      tamano: FILAS_POR_SECCION,
    });
  }

  /**
   * Carga inicial: cabecera + primera página de `locales`.
   *
   * Ojo con `prospecciones` y `captaciones`: llegan con el **total calculado**
   * pero `items` vacío. No son marcadores pendientes —su `totalRecords` no es
   * negativo— así que `esPendiente` las da por resueltas y hay que pedirlas
   * igualmente al abrir la pestaña. Es la única asimetría entre las dos fichas.
   */
  fichaPropietario$(id: number): Observable<FichaPropietario> {
    return this.api.get$<FichaPropietario>(`propietarios/${id}/ficha-comercial`, {
      tamano: FILAS_POR_SECCION,
    });
  }

  seccionPropietario$(id: number, section: string, pagina: number): Observable<SeccionFicha> {
    return this.api.get$<SeccionFicha>(`propietarios/${id}/ficha-comercial/${section}`, {
      pagina,
      tamano: FILAS_POR_SECCION,
    });
  }
}
