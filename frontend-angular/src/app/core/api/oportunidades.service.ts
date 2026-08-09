import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/**
 * Contrato CONGELADO: `OportunidadResponse` de la v1.
 *
 * La oportunidad es **la entidad concentradora** del proceso comercial: existe
 * desde que un cliente se interesa por una propiedad captada y sobrevive aunque
 * nunca llegue a haber solicitud formal, que es lo que da trazabilidad a los
 * intentos fallidos.
 *
 * `idCliente` e `idCaptacion` son ids de **rol** y de **captación**, no de
 * persona ni de propiedad: el inmueble viaja por su captación, y por eso la
 * respuesta trae `direccionLocal`/`distritoLocal` ya resueltos.
 */
export interface Oportunidad {
  id: number;
  codigoOportunidad?: string;
  idCliente?: number;
  clienteNombre?: string;
  idCaptacion?: number;
  codigoCaptacion?: string;
  direccionLocal?: string;
  distritoLocal?: string;
  idAgente?: number;
  agenteNombre?: string;
  /** A abierta, S solicitud creada, N no continúa, F exitosa, X no favorable. */
  estado?: string;
  fechaRegistro?: string;
  /** Razón tipificada del cierre por no continuidad (`MOTIVO_NO_CONTINUIDAD`). */
  motivoCierre?: string;
  observaciones?: string;
  fechaCierre?: string;
  fechaActualizacion?: string;
  idPublicacionOrigen?: number;
}

/**
 * Filtros de `GET /oportunidades`. Ojo: la búsqueda libre se llama `query`
 * aquí, `q` en interacciones y `texto` en locales. No se unifica mientras el
 * contrato siga congelado.
 */
export interface FiltrosOportunidades {
  pagina?: number;
  tamano?: number;
  idCaptacion?: number;
  idCliente?: number;
  /** Extensión aditiva del v2; omitido, el cable responde como la v1. */
  estado?: string;
  query?: string;
}

/**
 * KPI de la bandeja por etapa, contados en la base sobre el MISMO conjunto que
 * pagina la lista. `total` suma **todos** los cubos, no solo los cinco con
 * nombre: si el backend añadiera un estado, el total seguiría cuadrando con la
 * lista en vez de quedarse corto.
 */
export interface ResumenOportunidades {
  total: number;
  abiertas: number;
  conSolicitud: number;
  noContinuan: number;
  exitosas: number;
  noFavorables: number;
}

/** Espejo de `OportunidadRequest`. */
export interface DatosOportunidad {
  /** Vacío ⇒ el backend autogenera `OP-yyMMddHHmmss`. */
  codigoOportunidad?: string;
  idCliente?: number;
  idCaptacion?: number;
  observaciones?: string;
  idPublicacionOrigen?: number;
}

const RECURSO = 'oportunidades';

/** Estados en los que la oportunidad sigue viva para el negocio. */
export const OPORTUNIDAD_ACTIVA = new Set(['A', 'S']);

@Injectable({ providedIn: 'root' })
export class OportunidadesService {
  private readonly api = inject(ApiClient);

  pagina$(filtros: FiltrosOportunidades = {}): Observable<PageResponse<Oportunidad>> {
    return this.api.get$<PageResponse<Oportunidad>>(RECURSO, { ...filtros });
  }

  pagina(filtros: FiltrosOportunidades = {}): Promise<PageResponse<Oportunidad>> {
    return this.api.get<PageResponse<Oportunidad>>(RECURSO, { ...filtros });
  }

  /** No recibe `estado`: es uno de los cubos que devuelve. */
  resumen$(
    filtros: Omit<FiltrosOportunidades, 'estado' | 'pagina' | 'tamano'> = {},
  ): Observable<ResumenOportunidades> {
    return this.api.get$<ResumenOportunidades>(`${RECURSO}/resumen`, { ...filtros });
  }

  obtener(id: number): Promise<Oportunidad> {
    return this.api.get<Oportunidad>(`${RECURSO}/${id}`);
  }

  obtener$(id: number): Observable<Oportunidad> {
    return this.api.get$<Oportunidad>(`${RECURSO}/${id}`);
  }

  /**
   * Alta. **La captación debe ser del agente que registra**: el backend lo
   * comprueba y responde 403, no 400, si no lo es.
   */
  registrar(datos: DatosOportunidad): Promise<Oportunidad> {
    return this.api.post<Oportunidad>(RECURSO, datos);
  }

  /** Cierre por no continuidad: razón tipificada + observaciones libres. */
  noContinuidad(id: number, razon: string, observaciones?: string): Promise<Oportunidad> {
    return this.api.post<Oportunidad>(`${RECURSO}/${id}/no-continuidad`, {
      razon,
      observaciones,
    });
  }

  /**
   * Las oportunidades de un cliente, para su bitácora de contacto.
   *
   * El tope de 100 replica el del Blazor y es deliberado: es la vista de UN
   * cliente, no una bandeja. Si algún día un cliente supera las 100 propuestas,
   * esta pantalla necesita paginación real, no un tope mayor.
   */
  porCliente$(idCliente: number, tamano = 100): Observable<PageResponse<Oportunidad>> {
    return this.pagina$({ idCliente, pagina: 1, tamano });
  }
}

/**
 * NO existe un `cerrarExitoso()` y no es un olvido: `POST /oportunidades/{id}/
 * cierre-exitoso` valida rol y acceso y luego responde **400 siempre**, con el
 * mensaje _"El cierre exitoso se registra desde la solicitud aprobada para
 * crear el contrato de alquiler."_. El cierre favorable lo produce la cascada
 * de `POST /contratos` (F4), no un botón. Exponerlo aquí solo serviría para que
 * una pantalla lo llamara.
 */
