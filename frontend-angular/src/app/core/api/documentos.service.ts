import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';

/**
 * Descarga y visualización de binarios (documentos del expediente, fotos de
 * locales, foto de perfil) **con el token de sesión**.
 *
 * <b>Por qué no se usa la URL pública.</b> El backend expone
 * `GET /documentos/contenido?clave=…` sin autenticación, y eso es réplica del
 * cable v1: existía para que el visor del Blazor cargara archivos sin propagar
 * el JWT al navegador. Angular no tiene esa restricción —el token ya está en
 * memoria—, y la clave pública no aguanta el escrutinio: **es la ruta física**
 * (filtra el correlativo de la solicitud y el nombre original del archivo),
 * tiene **32 bits** de aleatoriedad, **no caduca ni se revoca**, y viaja en el
 * query string, que es lo que registran los access logs y el `Referer`. Ahí
 * viven DNI, comprobantes de ingresos y contratos.
 *
 * Apoyar el SPA en esa URL volvería imposible retirarla en el corte. Con este
 * servicio, el día que el Blazor muera el endpoint público se puede cerrar sin
 * tocar el frontend. Detalle en `docs/ai/checklist-migracion.md` §1.
 */
@Injectable({ providedIn: 'root' })
export class DocumentosService {
  private readonly api = inject(ApiClient);

  /** Bytes del binario, pidiéndolo con `Authorization`. */
  contenido$(clave: string): Observable<Blob> {
    return this.api.descargar$('documentos/contenido', { clave });
  }

  contenido(clave: string): Promise<Blob> {
    return this.api.descargar('documentos/contenido', { clave });
  }

  /**
   * URL efímera para incrustar el archivo en un `<img>` o un `<iframe>`.
   *
   * Quien la pide es dueño de liberarla con {@link liberar}: un object URL
   * vive hasta que se revoca o se recarga la página, así que olvidarlo es una
   * fuga de memoria silenciosa en pantallas con listas de documentos.
   */
  async urlIncrustable(clave: string): Promise<string> {
    return URL.createObjectURL(await this.contenido(clave));
  }

  liberar(url: string | null | undefined): void {
    if (url?.startsWith('blob:')) {
      URL.revokeObjectURL(url);
    }
  }

  /** Dispara la descarga en el navegador, con nombre de archivo propio. */
  async descargar(clave: string, nombreArchivo: string): Promise<void> {
    const url = await this.urlIncrustable(clave);
    try {
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = nombreArchivo;
      enlace.click();
    } finally {
      this.liberar(url);
    }
  }

  /**
   * Solo PDF e imágenes se incrustan en un visor; el resto se ofrece como
   * descarga. Es la misma regla del `Archivos.cs` del Blazor, y sigue la
   * extensión porque es lo que el backend usa para decidir el `Content-Type`.
   */
  esIncrustable(nombreOClave: string | null | undefined): boolean {
    return ['application/pdf', 'image/png', 'image/jpeg'].includes(
      this.tipoContenido(nombreOClave),
    );
  }

  esImagen(nombreOClave: string | null | undefined): boolean {
    return this.tipoContenido(nombreOClave).startsWith('image/');
  }

  tipoContenido(nombreOClave: string | null | undefined): string {
    const nombre = nombreOClave ?? '';
    if (/\.pdf$/i.test(nombre)) {
      return 'application/pdf';
    }
    if (/\.png$/i.test(nombre)) {
      return 'image/png';
    }
    if (/\.jpe?g$/i.test(nombre)) {
      return 'image/jpeg';
    }
    if (/\.csv$/i.test(nombre)) {
      return 'text/csv';
    }
    return 'application/octet-stream';
  }
}
