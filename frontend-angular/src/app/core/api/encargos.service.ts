import { inject, Injectable } from '@angular/core';
import { ApiClient } from './api.client';

/**
 * **Un anuncio de un encargo.**
 *
 * `importe` no es «el precio de la propiedad» ni siquiera «el precio del
 * anuncio»: es lo que este anuncio publica **de este encargo**. Una propiedad en
 * venta y en alquiler tiene dos series de anuncios que no se mezclan.
 */
export interface Publicacion {
  id: number;
  /** De qué encargo es. Nunca se pierde. */
  idEncargo?: number | null;
  /** URBANIA, ADONDEVIVIR, FACEBOOK… */
  canal: string;
  tituloAnuncio?: string | null;
  importePublicado?: number | null;
  moneda?: string | null;
  /**
   * «precio de venta» o «renta mensual», según la operación del encargo.
   *
   * Lo publica el backend. Decidirlo aquí con un ternario sobre la operación
   * sería semántica inmobiliaria escrita en la interfaz (D-A-1 §5).
   */
  importeRotulo?: string | null;
  /** B borrador · P publicada · S pausada · C cerrada. */
  estado: string;
  /** «Publicada», «Pausada»… Lo que se lee. */
  estadoRotulo?: string | null;
  fechaPublicacion?: string | null;
  fechaBaja?: string | null;
  urlPublicacion?: string | null;
  codigoOrigen?: string | null;
}

/** Cuerpo de alta y edición de un anuncio. */
export interface PublicacionRequest {
  canal: string;
  urlPublicacion: string | null;
  importePublicado: number;
  moneda: string;
  tituloAnuncio: string | null;
  codigoOrigen: string | null;
  estado: string | null;
}

/**
 * **Lo que cuelga de un encargo.** Hoy, sus publicaciones.
 *
 * ## Por qué no vive en `LocalesService`
 *
 * Porque un anuncio no anuncia «una propiedad»: anuncia que esta propiedad se
 * ofrece en **esta** operación a **este** precio. El endpoint heredado
 * `/locales/{id}/publicaciones` devolvía las series de venta y alquiler juntas
 * sin poder decir cuál publicaba qué — y el cliente no tenía forma de
 * separarlas, porque la publicación no llevaba la operación.
 *
 * La URL nueva no es un cambio de nombre: es la relación real del modelo.
 *
 * ```
 * Propiedad → Encargo → Publicación
 * ```
 */
@Injectable({ providedIn: 'root' })
export class EncargosService {
  private readonly api = inject(ApiClient);

  /** Los anuncios de este encargo, del más reciente al más antiguo. */
  publicaciones(idEncargo: number): Promise<Publicacion[]> {
    return this.api.get<Publicacion[]>(`encargos/${idEncargo}/publicaciones`);
  }

  /**
   * Publica el encargo en un canal.
   *
   * El backend rechaza el encargo no vigente: publicar uno cerrado pondría en
   * el mercado algo que ya no se ofrece. Esa regla **no** se comprueba aquí.
   */
  crearPublicacion(idEncargo: number, datos: PublicacionRequest): Promise<Publicacion> {
    return this.api.post<Publicacion>(`encargos/${idEncargo}/publicaciones`, datos);
  }

  actualizarPublicacion(
    idEncargo: number,
    idPublicacion: number,
    datos: PublicacionRequest,
  ): Promise<Publicacion> {
    return this.api.put<Publicacion>(
      `encargos/${idEncargo}/publicaciones/${idPublicacion}`,
      datos,
    );
  }

  /** Publicar (`P`), pausar (`S`) o cerrar (`C`) el anuncio. */
  cambiarEstadoPublicacion(
    idEncargo: number,
    idPublicacion: number,
    estado: string,
  ): Promise<Publicacion> {
    return this.api.post<Publicacion>(
      `encargos/${idEncargo}/publicaciones/${idPublicacion}/estado`,
      { estado },
    );
  }
}
