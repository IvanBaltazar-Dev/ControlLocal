/**
 * Generación y descarga de CSV. Porta el `ExportacionService` del Blazor.
 *
 * Dos reglas que no son opcionales:
 *
 * - **Se escapa siempre según RFC 4180**: comilla doble alrededor del campo y
 *   comillas internas duplicadas. Una dirección con coma —que es la norma, no
 *   la excepción— rompe el archivo si se concatena a pelo, y el daño no se ve
 *   hasta que alguien lo abre.
 * - **Se antepone el BOM de UTF-8.** Sin él, Excel en Windows abre el archivo
 *   como ANSI y "Miraflores" pasa a "Miraflores" en cuanto hay una tilde.
 *   Es el motivo real por el que los CSV exportados "salen mal".
 */

const BOM = '﻿';

/** Convierte cabeceras y filas en el texto de un CSV. */
export function generarCsv(
  cabeceras: readonly string[],
  filas: readonly (readonly (string | number | null | undefined)[])[],
): string {
  return [cabeceras, ...filas].map((fila) => fila.map(campo).join(',')).join('\r\n');
}

/** Dispara la descarga en el navegador. Devuelve el nombre real del archivo. */
export function descargarCsv(
  nombreBase: string,
  cabeceras: readonly string[],
  filas: readonly (readonly (string | number | null | undefined)[])[],
  hoy = new Date(),
): string {
  const nombre = `${nombreBase}_${sello(hoy)}.csv`;
  const blob = new Blob([BOM + generarCsv(cabeceras, filas)], {
    type: 'text/csv;charset=utf-8;',
  });
  const url = URL.createObjectURL(blob);
  try {
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombre;
    enlace.click();
  } finally {
    // El object URL vive hasta que se revoca; en una pantalla desde la que se
    // exporta varias veces, olvidarlo es una fuga silenciosa.
    URL.revokeObjectURL(url);
  }
  return nombre;
}

function campo(valor: string | number | null | undefined): string {
  if (valor === null || valor === undefined) {
    return '""';
  }
  return `"${String(valor).replace(/"/g, '""')}"`;
}

function sello(fecha: Date): string {
  const dos = (n: number) => String(n).padStart(2, '0');
  return `${fecha.getFullYear()}${dos(fecha.getMonth() + 1)}${dos(fecha.getDate())}`;
}
