import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';

/**
 * Contrato CONGELADO: `CoincidenciaResponse`. Una fila del matching de cartera.
 *
 * Tres cosas que **no** son lo que parecen y se pagan caras si se asumen:
 * - **`id` cambia de significado según el origen**: en `cliente → propiedades`
 *   es el id de la **captación** (la oferta viaja por su captación, no por la
 *   propiedad); en `captación → clientes` es el del cliente.
 * - `renta`, `area` y `frente` son **cadenas ya formateadas** por el backend,
 *   no números: se pintan tal cual, no se recalculan.
 * - `proponerRuta` llega como cadena **vacía** —no `null`— cuando la
 *   coincidencia no es accionable. Es lo que distingue una señal temprana
 *   (prospección sin captación) de una propuesta que se puede crear ya.
 */
export interface Coincidencia {
  /** CLIENTE | PROPIEDAD, según la dirección del matching. */
  tipo?: string;
  id: number;
  codigo?: string;
  titulo?: string;
  subtitulo?: string;
  distrito?: string;
  renta?: string;
  area?: string;
  frente?: string;
  /** 0..100: `round(100 × cumplidos / aplicables)`. */
  puntaje: number;
  /** Frases legibles de los criterios que cumple. */
  cumple?: string[];
  noCumple?: string[];
  clienteId?: number;
  captacionId?: number;
  /** Ruta del legado; vacía si no es accionable. */
  proponerRuta?: string;
}

/** Sobre del matching. Pagina con su propio esquema: `page`/`pageSize`. */
export interface Coincidencias {
  origen?: string;
  total: number;
  page: number;
  pageSize: number;
  items: Coincidencia[];
}

/**
 * Matching de cartera: las tres entradas del §7 del contrato F3.
 *
 * El puntaje sale de seis criterios —distrito, rubro, tipo de inmueble, renta,
 * área y frente—, cada uno CUMPLE / NO_CUMPLE / **NO_APLICA** cuando el dato
 * falta en el requerimiento o en el local. Un 100 % no significa "cumple todo":
 * significa "cumple todo lo que se pudo evaluar", y por eso la pantalla muestra
 * siempre las listas `cumple`/`noCumple` junto al número.
 *
 * **Vista personal del matching**: para un actor que no es ADMIN, la demanda
 * propia son los clientes que YA tienen oportunidad del equipo. Un cliente
 * recién creado no aparece todavía en `captación → clientes` aunque case al
 * 100 %; la dirección inversa (`cliente → propiedades`) no tiene esa
 * restricción.
 */
@Injectable({ providedIn: 'root' })
export class CoincidenciasService {
  private readonly api = inject(ApiClient);

  /** Cliente → propiedades: captaciones activas con local disponible. */
  paraCliente$(idCliente: number, pagina = 1, tamano = 6): Observable<Coincidencias> {
    return this.api.get$<Coincidencias>(`clientes/${idCliente}/coincidencias`, {
      pagina,
      tamano,
    });
  }

  /** Captación → clientes. Acepta id o código en la misma ruta. */
  paraCaptacion$(idOCodigo: string | number, pagina = 1, tamano = 6): Observable<Coincidencias> {
    return this.api.get$<Coincidencias>(`captaciones/${idOCodigo}/coincidencias`, {
      pagina,
      tamano,
    });
  }

  /** Prospección → clientes: señal temprana, accionable solo si ya hay captación. */
  paraProspeccion$(idProspeccion: number, pagina = 1, tamano = 6): Observable<Coincidencias> {
    return this.api.get$<Coincidencias>(`prospecciones/${idProspeccion}/coincidencias`, {
      pagina,
      tamano,
    });
  }
}

/** El tope real del backend: pedir más de 24 no devuelve más. */
export const MAXIMO_COINCIDENCIAS = 24;
