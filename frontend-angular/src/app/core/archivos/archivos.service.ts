import { Injectable } from '@angular/core';

export const TAMANO_MAXIMO_ARCHIVO = 5 * 1024 * 1024;

export type ExtensionArchivo = '.csv' | '.pdf' | '.png' | '.jpg' | '.jpeg';

export const EXTENSIONES_ARCHIVO: readonly ExtensionArchivo[] = [
  '.csv',
  '.pdf',
  '.png',
  '.jpg',
  '.jpeg',
];

const TIPOS_PERMITIDOS: Readonly<Record<ExtensionArchivo, readonly string[]>> = {
  '.csv': ['text/csv', 'application/vnd.ms-excel', 'text/plain'],
  '.pdf': ['application/pdf'],
  '.png': ['image/png'],
  '.jpg': ['image/jpeg'],
  '.jpeg': ['image/jpeg'],
};

const FIRMAS: Partial<Record<ExtensionArchivo, readonly number[]>> = {
  '.pdf': [0x25, 0x50, 0x44, 0x46],
  '.png': [0x89, 0x50, 0x4e, 0x47],
  '.jpg': [0xff, 0xd8, 0xff],
  '.jpeg': [0xff, 0xd8, 0xff],
};

export interface ArchivoPreparado {
  readonly archivo: File;
  readonly nombreOriginal: string;
  readonly nombreSeguro: string;
  readonly extension: ExtensionArchivo;
  readonly tipoContenido: string;
  readonly tamano: number;
}

export type ResultadoArchivo =
  | { readonly valido: true; readonly archivo: ArchivoPreparado }
  | { readonly valido: false; readonly error: string };

export interface OpcionesArchivo {
  readonly extensiones?: readonly ExtensionArchivo[];
  readonly tamanoMaximo?: number;
}

/**
 * Único validador de archivos del SPA. Espeja las reglas de `Archivos.cs` del
 * Blazor y las fronteras de 5 MB de fotos/documentos del backend congelado.
 */
@Injectable({ providedIn: 'root' })
export class ArchivosService {
  async validar(
    archivo: File,
    opciones: OpcionesArchivo = {},
  ): Promise<ResultadoArchivo> {
    const extension = extensionDe(archivo.name);
    if (!extension) {
      return { valido: false, error: `Tipo de archivo no permitido (${extensionCruda(archivo.name)}).` };
    }

    const permitidas = opciones.extensiones ?? EXTENSIONES_ARCHIVO;
    if (!permitidas.includes(extension)) {
      return {
        valido: false,
        error: `Aquí solo se aceptan archivos ${permitidas.join(', ')}.`,
      };
    }

    if (archivo.size <= 0) {
      return { valido: false, error: 'El archivo está vacío.' };
    }
    const tamanoMaximo = opciones.tamanoMaximo ?? TAMANO_MAXIMO_ARCHIVO;
    if (archivo.size > tamanoMaximo) {
      return {
        valido: false,
        error: `El archivo supera el máximo de ${Math.floor(tamanoMaximo / 1024 / 1024)} MB.`,
      };
    }

    const tipos = TIPOS_PERMITIDOS[extension];
    if (archivo.type && !tipos.includes(archivo.type.toLowerCase())) {
      return {
        valido: false,
        error: 'El tipo de contenido no coincide con la extensión del archivo.',
      };
    }

    const firma = FIRMAS[extension];
    if (firma) {
      const primeros = new Uint8Array(await archivo.slice(0, firma.length).arrayBuffer());
      if (!firma.every((byte, indice) => primeros[indice] === byte)) {
        return {
          valido: false,
          error: 'El contenido del archivo no corresponde a su extensión.',
        };
      }
    }

    return {
      valido: true,
      archivo: {
        archivo,
        nombreOriginal: archivo.name,
        nombreSeguro: nombreSeguro(archivo.name),
        extension,
        tipoContenido: tipos[0],
        tamano: archivo.size,
      },
    };
  }

  /** Convierte a base64 solo al invocar endpoints congelados que lo exigen. */
  async base64(archivo: File): Promise<string> {
    const bytes = new Uint8Array(await archivo.arrayBuffer());
    let binario = '';
    const bloque = 0x8000;
    for (let inicio = 0; inicio < bytes.length; inicio += bloque) {
      binario += String.fromCharCode(...bytes.subarray(inicio, inicio + bloque));
    }
    return btoa(binario);
  }
}

function extensionDe(nombre: string): ExtensionArchivo | null {
  const extension = extensionCruda(nombre) as ExtensionArchivo;
  return EXTENSIONES_ARCHIVO.includes(extension) ? extension : null;
}

function extensionCruda(nombre: string): string {
  const ultimoPunto = nombre.lastIndexOf('.');
  return ultimoPunto >= 0 ? nombre.slice(ultimoPunto).toLowerCase() : '';
}

function nombreSeguro(nombre: string): string {
  const soloNombre = nombre.split(/[\\/]/).at(-1) ?? nombre;
  const extension = extensionCruda(soloNombre);
  const base = soloNombre.slice(0, soloNombre.length - extension.length);
  const limpio = Array.from(base)
    .map((caracter) => (/[\p{L}\p{N}_-]/u.test(caracter) ? caracter : '_'))
    .join('');
  return `${(limpio || 'documento').slice(0, 80)}${extension}`;
}
