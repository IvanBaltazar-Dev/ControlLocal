import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ConstanciaAutorizacion } from '../autorizacion';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/**
 * Contrato CONGELADO: `ClienteResponse` de la v1, con los mismos nombres del
 * cable.
 *
 * `id` es el **`persona_rol.id` del rol CLIENTE**, no el de la persona: el
 * cliente es un rol (Party-Role), igual que `idPropietario` en la oferta. Dos
 * personas jurídicas distintas pueden compartir razón social, pero nunca id.
 *
 * Los dos consentimientos **no viven en el mismo sitio** aunque viajen juntos:
 * el de contacto es del rol y el de uso de dato es de la persona, porque vale
 * para todos sus roles. La pantalla los muestra por separado por eso mismo.
 */
export interface Cliente {
  id: number;
  /** N natural, J jurídica. */
  tipoPersona?: string;
  /** D DNI, R RUC, C carnet, P pasaporte. */
  tipoDocumento?: string;
  numeroDocumento?: string;
  nombre?: string;
  telefono?: string;
  correo?: string;
  rubroComercial?: string;
  /** A activo, I inactivo. */
  estado?: string;
  consentimientoContacto?: boolean;
  consentimientoUsoDato?: boolean;
  fechaCreacion?: string;
}

/**
 * Filtros de `GET /clientes`. Los cuatro son **aditivos y opcionales**:
 * omitidos, el endpoint responde exactamente lo que respondía antes de que
 * existieran. `rubro` es coincidencia exacta (sale del selector data-driven);
 * `texto` busca en nombre o razón social, documento y rubro.
 *
 * Ojo con la paginación: este recurso usa `pagina`/`tamano`, no los alias
 * `page`/`page_size`.
 */
export interface FiltrosClientes {
  pagina?: number;
  tamano?: number;
  texto?: string;
  tipoPersona?: string;
  rubro?: string;
  estado?: string;
}

/**
 * KPI de la bandeja, calculados en la base sobre el MISMO conjunto que la
 * lista. `rubros` llega aquí para que el selector sea data-driven sin una
 * llamada extra ni descargar la cartera, que es lo que hacía el Blazor.
 */
export interface ResumenClientes {
  total: number;
  activos: number;
  inactivos: number;
  contactoAutorizado: number;
  usoDatoAutorizado: number;
  rubros: string[];
}

/** Espejo de `ClienteRequest`. El documento NO se puede cambiar en el PUT. */
export interface DatosCliente {
  tipoPersona?: string;
  tipoDocumento?: string;
  numeroDocumento?: string;
  nombre?: string;
  telefono?: string;
  correo?: string;
  rubroComercial?: string;
  consentimientoContacto?: boolean;
  consentimientoUsoDato?: boolean;
  estado?: string;
}

const RECURSO = 'clientes';

@Injectable({ providedIn: 'root' })
export class ClientesService {
  private readonly api = inject(ApiClient);

  /** Cancelable: `switchMap` aborta la lectura anterior al cambiar de filtro. */
  pagina$(filtros: FiltrosClientes = {}): Observable<PageResponse<Cliente>> {
    return this.api.get$<PageResponse<Cliente>>(RECURSO, { ...filtros });
  }

  pagina(filtros: FiltrosClientes = {}): Promise<PageResponse<Cliente>> {
    return this.api.get<PageResponse<Cliente>>(RECURSO, { ...filtros });
  }

  /** No recibe `estado`: es uno de los cubos que devuelve. */
  resumen$(filtros: Omit<FiltrosClientes, 'estado' | 'pagina' | 'tamano'> = {}): Observable<ResumenClientes> {
    return this.api.get$<ResumenClientes>(`${RECURSO}/resumen`, { ...filtros });
  }

  obtener(id: number): Promise<Cliente> {
    return this.api.get<Cliente>(`${RECURSO}/${id}`);
  }

  /**
   * Constancia de la autorización de datos (D-27). **Endpoint aparte** y no un
   * campo de `Cliente`: `ClienteResponse` es contrato congelado, y ampliarla la
   * separaría del cable de la v1. Mismo alcance que `obtener`.
   */
  autorizacion(id: number): Promise<ConstanciaAutorizacion> {
    return this.api.get<ConstanciaAutorizacion>(`${RECURSO}/${id}/autorizacion`);
  }

  registrar(datos: DatosCliente): Promise<Cliente> {
    return this.api.post<Cliente>(RECURSO, datos);
  }

  actualizar(id: number, datos: DatosCliente): Promise<Cliente> {
    return this.api.put<Cliente>(`${RECURSO}/${id}`, datos);
  }

  /** Baja LÓGICA: la persona pasa a estado `I`. */
  desactivar(id: number): Promise<void> {
    return this.api.delete<void>(`${RECURSO}/${id}`);
  }

  /**
   * Reactivar no tiene endpoint propio: es el PUT con `estado = 'A'`. Se
   * expone con nombre propio para que la pantalla no tenga que saberlo.
   */
  reactivar(id: number, cliente: Cliente): Promise<Cliente> {
    return this.actualizar(id, {
      nombre: cliente.nombre,
      telefono: cliente.telefono,
      correo: cliente.correo,
      rubroComercial: cliente.rubroComercial,
      consentimientoContacto: cliente.consentimientoContacto,
      consentimientoUsoDato: cliente.consentimientoUsoDato,
      estado: 'A',
    });
  }
}
