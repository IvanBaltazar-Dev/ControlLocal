import { inject, Injectable } from '@angular/core';
import { Params, Router } from '@angular/router';
import { SolicitudesService } from './api/solicitudes.service';

/**
 * Traduce las rutas que vienen **dentro del cable** a rutas del SPA.
 *
 * Las alertas (`ruta`) y las tareas (`rutaResolver`) no son texto libre: el
 * backend las calcula para que el cliente navegue directo al origen del aviso,
 * y las calcula con **las rutas del Blazor** (`solicitud-detail/12`,
 * `visitas?focus=3`, `owner-detail/7`…). Están congeladas junto con el resto
 * del contrato, así que el que se adapta es el SPA.
 *
 * Se traducen aquí, en un solo sitio y con función pura, en vez de en cada
 * pantalla que muestre un aviso: la campana, la bandeja del dashboard y
 * cualquier futura vista de tareas hablan del mismo mapa.
 *
 * **Una ruta que no se sabe traducir devuelve `null`, y eso es una respuesta
 * válida**: el aviso se muestra sin enlace. Ya pasa en el cable —los tipos de
 * entidad que la v1 no enruta viajan sin `ruta` (D-F6-4)— y es mejor que
 * inventar un destino que no existe.
 */

/** Destino ya resuelto: comandos del router + query params opcionales. */
export interface DestinoLegado {
  comandos: readonly unknown[];
  queryParams?: Params;
}

/**
 * Lo que el cable pide abrir. `solicitud-por-id` es el caso raro que necesita
 * una llamada extra: **las alertas de solicitud viajan con el id numérico**
 * mientras que la ficha del SPA enruta por código (`SOL-…`), igual que el
 * Blazor. Se resuelve al pulsar, no al listar.
 */
export type PeticionLegado =
  | ({ tipo: 'ruta' } & DestinoLegado)
  | { tipo: 'solicitud-por-id'; id: number };

/** ¿Parece un código de negocio (`SOL-…`, `CAP-0001`) y no un id? */
function esCodigo(valor: string): boolean {
  return valor.length > 0 && !/^\d+$/.test(valor);
}

/**
 * Parte `"visitas?focus=3"` en ruta y parámetros. El cable solo usa una query
 * y sin codificar, así que no hace falta un parser completo.
 */
function partir(ruta: string): { camino: string; queryParams?: Params } {
  const [camino, consulta] = ruta.split('?', 2);
  if (!consulta) {
    return { camino };
  }
  const queryParams: Params = {};
  for (const par of consulta.split('&')) {
    const [clave, valor = ''] = par.split('=', 2);
    if (clave) {
      queryParams[clave] = decodeURIComponent(valor);
    }
  }
  return { camino, queryParams };
}

/**
 * Ruta del cable → petición de navegación del SPA. Función pura: es la pieza
 * que se prueba.
 */
export function traducirRutaLegado(ruta: string | null | undefined): PeticionLegado | null {
  const limpia = (ruta ?? '').trim().replace(/^\/+/, '');
  if (!limpia) {
    return null;
  }

  const { camino, queryParams } = partir(limpia);
  const [recurso, ...resto] = camino.split('/');
  const clave = resto.join('/');

  switch (recurso) {
    case 'prospeccion-detail':
      return destino(['/prospecciones', clave], clave, queryParams);
    case 'oportunidad-detail':
      return destino(['/oportunidades', clave], clave, queryParams);
    case 'cliente-detail':
      return destino(['/clientes', clave], clave, queryParams);
    case 'owner-detail':
      return destino(['/propietarios', clave], clave, queryParams);
    case 'captacion-detail':
      // La ficha del SPA enruta por código, igual que el Blazor. Un id suelto
      // aquí no es resoluble, así que cae a la bandeja de captaciones.
      return esCodigo(clave)
        ? { tipo: 'ruta', comandos: ['/captaciones', clave], queryParams }
        : { tipo: 'ruta', comandos: ['/captaciones'], queryParams };
    case 'captacion-review':
      return esCodigo(clave)
        ? { tipo: 'ruta', comandos: ['/captaciones', clave, 'revisar'], queryParams }
        : { tipo: 'ruta', comandos: ['/captaciones/pendientes'], queryParams };
    case 'solicitud-detail':
      if (!clave) {
        return { tipo: 'ruta', comandos: ['/solicitudes'], queryParams };
      }
      return esCodigo(clave)
        ? { tipo: 'ruta', comandos: ['/solicitudes', clave], queryParams }
        : { tipo: 'solicitud-por-id', id: Number(clave) };
    case 'evaluacion':
      return esCodigo(clave)
        ? { tipo: 'ruta', comandos: ['/solicitudes', clave, 'evaluar'], queryParams }
        : { tipo: 'ruta', comandos: ['/solicitudes/revisar'], queryParams };
    case 'captaciones':
    case 'visitas':
    case 'interacciones':
    case 'oportunidades':
    case 'solicitudes':
    case 'clientes':
    case 'prospecciones':
    case 'propietarios':
    case 'locales':
    case 'comisiones':
    case 'seguimiento-comercial':
    case 'indicadores':
      // Listados: el nombre coincide con la ruta del SPA.
      return { tipo: 'ruta', comandos: ['/' + camino], queryParams };
    case 'propiedades-alquiladas':
      return { tipo: 'ruta', comandos: ['/propiedades-alquiladas'], queryParams };
    default:
      return null;
  }
}

/** Detalle por id: sin id no hay destino, y enviar al listado sería adivinar. */
function destino(
  comandos: readonly unknown[],
  clave: string,
  queryParams?: Params,
): PeticionLegado | null {
  if (!clave) {
    return null;
  }
  return { tipo: 'ruta', comandos, queryParams };
}

/**
 * Ejecuta la navegación. Vive aparte de la traducción porque el caso
 * `solicitud-por-id` necesita el API: se resuelve el código y recién entonces
 * se navega.
 */
@Injectable({ providedIn: 'root' })
export class NavegacionLegado {
  private readonly router = inject(Router);
  private readonly solicitudes = inject(SolicitudesService);

  /** ¿Este aviso lleva a alguna parte? Decide si se pinta como enlace. */
  puedeAbrir(ruta: string | null | undefined): boolean {
    return traducirRutaLegado(ruta) !== null;
  }

  /**
   * Navega al origen del aviso. Devuelve `false` si no se pudo (ruta
   * desconocida o la solicitud ya no es visible), para que quien llame lo
   * cuente en vez de dejar al usuario mirando la misma pantalla.
   */
  async abrir(ruta: string | null | undefined): Promise<boolean> {
    const peticion = traducirRutaLegado(ruta);
    if (!peticion) {
      return false;
    }
    if (peticion.tipo === 'solicitud-por-id') {
      try {
        const solicitud = await this.solicitudes.obtener(peticion.id);
        const codigo = solicitud.codigoSolicitud;
        if (!codigo) {
          return false;
        }
        return this.router.navigate(['/solicitudes', codigo]);
      } catch {
        return false;
      }
    }
    return this.router.navigate([...peticion.comandos], {
      queryParams: peticion.queryParams,
    });
  }
}
