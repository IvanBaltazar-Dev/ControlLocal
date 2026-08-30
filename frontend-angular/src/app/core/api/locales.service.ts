import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';
// La autoridad de escritura tiene UNA sola definición en el cliente, igual que
// tiene una sola en el Core. Un segundo `interface Responsabilidad` aquí sería
// la copia que diverge en el primer cambio.
import { Responsabilidad } from './propiedades.service';

/** Contrato CONGELADO: mismos campos y nombres que `LocalResponse` del backend. */
export interface Local {
  id: number;
  codigoLocal?: string;
  direccion?: string;
  distrito?: string;
  metraje?: number;
  precioReferencial?: number;
  monedaReferencial?: string;
  rubroPermitido?: string;
  descripcion?: string;
  /** D disponible, N no disponible, I inactivo. */
  estado?: string;
  idPropietario?: number;
  propietarioNombre?: string;
  tipoInmueble?: string;
  uso?: string;
  ambientes?: number;
  antiguedadAnios?: number;
  zonaUrbanizacion?: string;
  geoLat?: number;
  geoLong?: number;
  estadoPublicacion?: string;
  frente?: number;
  zonificacion?: string;
  aptoLicenciaFuncionamiento?: boolean;
  cargaElectricaKw?: number;
  numeroEstacionamientos?: number;
  cuotaMantenimiento?: number;
  idDistrito?: number;
  fechaRegistro?: string;
  fotoPortadaClave?: string;
  /** Registro maestro A/I, independiente de la disponibilidad comercial. */
  estadoRegistro?: string;
  /** D disponible, R reservado, A alquilado, T retirado. */
  disponibilidadComercial?: string;
  interiorUnidad?: string;
  piso?: string;
  referenciaInterna?: string;
  nombreEdificioGaleria?: string;
  /**
   * **Quién responde por el inmueble, y qué puede hacer quien está mirando**
   * (P0).
   *
   * Llega **resuelto por el Core** y es el **mismo tipo** que trae la ficha
   * universal: una sola definición para las dos pantallas. La galería de fotos
   * se pinta desde aquí, y `POST`/`DELETE /locales/{id}/fotos` los cierra
   * `AutoridadDePropiedad` desde V87 — así que esta pantalla no puede tener su
   * propia versión de la regla.
   *
   * Sólo viaja en el **detalle** (`GET /locales/{id}`). En los listados no
   * aparece: Jackson va `NON_NULL`, de modo que aquí es `undefined` y hay que
   * compararlo con `== null` — con `=== null` no se ve el caso real.
   */
  responsabilidad?: Responsabilidad | null;
}

/** Cuerpo congelado de POST/PUT /locales. */
export interface LocalRequest {
  codigoLocal: string;
  direccion: string;
  distrito: string;
  metraje: number;
  precioReferencial: number;
  monedaReferencial: string;
  rubroPermitido: string;
  descripcion: string | null;
  idPropietario: number;
  estado: string;
  tipoInmueble: string;
  uso: 'C';
  ambientes: number | null;
  antiguedadAnios: number | null;
  zonaUrbanizacion: string | null;
  geoLat: number | null;
  geoLong: number | null;
  estadoPublicacion: string;
  frente: number | null;
  zonificacion: string | null;
  aptoLicenciaFuncionamiento: boolean | null;
  cargaElectricaKw: number | null;
  numeroEstacionamientos: number | null;
  cuotaMantenimiento: number | null;
  interiorUnidad: string | null;
  piso: string | null;
  referenciaInterna: string | null;
  nombreEdificioGaleria: string | null;
}

export interface PosibleDuplicadoLocal {
  id: number;
  codigoLocal?: string;
  direccion?: string;
  interiorUnidad?: string;
  piso?: string;
  metraje?: number;
  criteriosCoincidentes: string[];
}

export interface FiltrosLocales {
  /** Página 1-based del contrato. */
  page?: number;
  tamano?: number;
  texto?: string;
  /** D disponible, N no disponible, I inactivo. */
  estado?: string;
}

export interface ResumenLocales {
  total: number;
  disponibles: number;
  noDisponibles: number;
  inactivos: number;
}

/** Espejo de `PrecioResponse`. Hito E/R/U/P/O/A/C. */
export interface PrecioLocal {
  id: number;
  idLocal?: number;
  hito?: string;
  moneda?: string;
  monto?: number;
  /** ISO `YYYY-MM-DD`. */
  fecha?: string;
  fechaCreacion?: string;
  /**
   * `VENTA` o `ALQUILER` (D-E4-1). Una propiedad puede tener las dos series a
   * la vez, y sin este campo llegan mezcladas: 180 000 y 2 900 en la misma
   * lista solo se distinguen por magnitud, que es adivinar.
   */
  operacion?: 'VENTA' | 'ALQUILER';
}

/**
 * Contrato CONGELADO: espejo de `FotoLocalResponse`.
 *
 * `clave` es la clave opaca del almacén. **No se pone en el `src` de una
 * imagen**: se pide el binario con el token por `DocumentosService` (ver la
 * regla del visor en `contrato-transversales-frontend.md` §3).
 */
export interface FotoLocal {
  idFoto: number;
  clave: string;
  nombre?: string;
  /** `S3` o el almacén en disco. */
  proveedor?: string;
}

@Injectable({ providedIn: 'root' })
export class LocalesService {
  private readonly api = inject(ApiClient);

  /** Página filtrada; búsqueda, orden, conteo y paginación viven en el backend. */
  pagina$(filtros: FiltrosLocales = {}): Observable<PageResponse<Local>> {
    return this.api.get$<PageResponse<Local>>('locales', {
      page: filtros.page,
      tamano: filtros.tamano,
      texto: filtros.texto,
      estado: filtros.estado,
    });
  }

  pagina(filtros: FiltrosLocales = {}): Promise<PageResponse<Local>> {
    return this.api.get<PageResponse<Local>>('locales', {
      page: filtros.page,
      tamano: filtros.tamano,
      texto: filtros.texto,
      estado: filtros.estado,
    });
  }

  /** KPI completos del tenant, con el mismo texto que la pagina. */
  resumen$(texto?: string): Observable<ResumenLocales> {
    return this.api.get$<ResumenLocales>('locales/resumen', { texto });
  }

  resumen(texto?: string): Promise<ResumenLocales> {
    return this.api.get<ResumenLocales>('locales/resumen', { texto });
  }

  /**
   * Los locales de las captaciones del agente (RF-004). Solo rol AGENTE.
   *
   * **No acepta `texto` ni `estado`**, solo `pagina` y `tamano` — y el nombre
   * del parámetro es `pagina`, no `page` como en el listado general. Los dos
   * detalles son del cable congelado, así que quien lo consuma no puede
   * ofrecer búsqueda sobre esta vista sin cambiar el backend: filtrar en el
   * cliente solo alcanzaría a la página descargada (RC-003).
   */
  misLocales(pagina = 1, tamano = 10): Promise<PageResponse<Local>> {
    return this.api.get<PageResponse<Local>>('locales/mis-locales', { pagina, tamano });
  }

  misLocales$(pagina = 1, tamano = 10): Observable<PageResponse<Local>> {
    return this.api.get$<PageResponse<Local>>('locales/mis-locales', { pagina, tamano });
  }

  obtener(id: number): Promise<Local> {
    return this.api.get<Local>(`locales/${id}`);
  }

  obtener$(id: number): Observable<Local> {
    return this.api.get$<Local>(`locales/${id}`);
  }

  posiblesDuplicados(
    datos: LocalRequest,
    idExcluir?: number,
  ): Promise<PosibleDuplicadoLocal[]> {
    return this.api.post<PosibleDuplicadoLocal[]>('locales/posibles-duplicados', datos, {
      idExcluir,
    });
  }

  // =====================================================================
  // Colecciones hijas del local. Las tres se alcanzan por el id del padre,
  // que sí va filtrado por organización: no llevan filtro de tenant propio
  // (ver `matriz-operacion-rol.md`). Ninguna de las tres pagina en el cable
  // —responden una lista suelta, no un `PageResponse`—.
  // =====================================================================

  /** Histórico de precios, ordenado por el backend. */
  precios$(idLocal: number): Observable<PrecioLocal[]> {
    return this.api.get$<PrecioLocal[]>(`locales/${idLocal}/precios`);
  }

  precios(idLocal: number): Promise<PrecioLocal[]> {
    return this.api.get<PrecioLocal[]>(`locales/${idLocal}/precios`);
  }

  // Las publicaciones se fueron a `EncargosService` (V70). No es un cambio de
  // URL: un anuncio publica un ENCARGO —esta propiedad, en esta operación, a
  // este precio—, y colgado del local devolvía las series de venta y alquiler
  // juntas sin poder decir cuál publicaba qué. Los endpoints heredados ya no
  // existen en el backend.

  /** Galería del local. El tope de 6 lo impone el backend. */
  fotos$(idLocal: number): Observable<FotoLocal[]> {
    return this.api.get$<FotoLocal[]>(`locales/${idLocal}/fotos`);
  }

  fotos(idLocal: number): Promise<FotoLocal[]> {
    return this.api.get<FotoLocal[]>(`locales/${idLocal}/fotos`);
  }

  /**
   * Alta de foto. Va en **base64 dentro de un JSON** y no como octet-stream
   * porque así está congelado el cable (el POST binario rompía el HttpClient
   * de .NET contra GlassFish). Es una de las dos únicas llamadas del SPA que
   * usan `ArchivosService.base64()`.
   */
  subirFoto(
    idLocal: number,
    nombreArchivo: string,
    contenidoBase64: string,
  ): Promise<FotoLocal> {
    return this.api.post<FotoLocal>(`locales/${idLocal}/fotos`, {
      nombreArchivo,
      contenidoBase64,
    });
  }

  /** Borra el registro y el binario. Responde 204 sin cuerpo. */
  eliminarFoto(idLocal: number, idFoto: number): Promise<void> {
    return this.api.delete<void>(`locales/${idLocal}/fotos/${idFoto}`);
  }
}
