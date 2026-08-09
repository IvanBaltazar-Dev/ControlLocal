/**
 * Formato de fechas y números para la interfaz, en español.
 *
 * Se usa `Intl` y no los pipes de Angular a propósito: `DatePipe`/`DecimalPipe`
 * formatean con el `LOCALE_ID` de la aplicación, que hoy es `en-US` y solo
 * trae sus propios datos de locale. Registrar otro locale es una decisión
 * global que aquí no hace falta — `Intl` ya vive en el navegador.
 *
 * Todo lo de este módulo es **presentación**: lo que se envía al API sigue
 * siendo el valor crudo del contrato congelado.
 */

const LOCALE = 'es-PE';

const FECHA_CORTA = new Intl.DateTimeFormat(LOCALE, {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
});

const FECHA_HORA = new Intl.DateTimeFormat(LOCALE, {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

/** Placeholder único para "no hay dato", igual que el `Txt()` del Blazor. */
export const SIN_DATO = '—';

/** Solo fecha (`YYYY-MM-DD`), sin hora ni zona. */
const SOLO_FECHA = /^\d{4}-\d{2}-\d{2}$/;

/**
 * ISO del cable -> `Date`.
 *
 * Una cadena **sin hora** se construye en hora local a propósito: pasarla por
 * `new Date('2026-07-30')` la interpreta como medianoche UTC y en Lima
 * (UTC-5) se muestra el día anterior. Ese desfase de un día es justo el tipo
 * de error que nadie ve hasta que un usuario reporta una fecha corrida.
 */
export function comoFecha(valor: string | null | undefined): Date | null {
  if (!valor) {
    return null;
  }
  if (SOLO_FECHA.test(valor)) {
    const [anio, mes, dia] = valor.split('-').map(Number);
    return new Date(anio, mes - 1, dia);
  }
  const fecha = new Date(valor);
  return Number.isNaN(fecha.getTime()) ? null : fecha;
}

/** `2026-07-30` -> `30 jul 2026`. Vacío o inválido: `—`. */
export function fechaCorta(valor: string | null | undefined): string {
  const fecha = comoFecha(valor);
  return fecha ? FECHA_CORTA.format(fecha) : SIN_DATO;
}

/** Con hora, para sellos de tiempo (publicaciones, auditoría). */
export function fechaHora(valor: string | null | undefined): string {
  const fecha = comoFecha(valor);
  return fecha ? FECHA_HORA.format(fecha) : SIN_DATO;
}

/**
 * Hora del cable (`LocalTime`) -> `HH:mm`.
 *
 * El backend emite los segundos cuando el dato los trae (`16:00:00`), y una
 * agenda comercial no se cita al segundo: mostrarlos solo añade ruido a la
 * columna. Se recorta en presentación, no al enviar — el `<input type="time">`
 * y el POST siguen viajando con lo que el contrato acepta.
 */
export function hora(valor: string | null | undefined): string {
  const limpio = (valor ?? '').trim();
  return limpio ? limpio.slice(0, 5) : SIN_DATO;
}

/** Número con separadores y hasta `decimales` cifras; `null` -> `—`. */
export function numero(valor: number | null | undefined, decimales = 2): string {
  if (valor === null || valor === undefined || Number.isNaN(valor)) {
    return SIN_DATO;
  }
  return new Intl.NumberFormat(LOCALE, {
    minimumFractionDigits: 0,
    maximumFractionDigits: decimales,
  }).format(valor);
}

/**
 * Porcentaje, **sin separador de millares**.
 *
 * Agruparlo es ambiguo justo donde más caro sale: `4,250 %` se lee como
 * "4,25 %" en cualquier convención que use la coma para decimales, y aquí
 * hablamos de la comisión pactada de una captación. Sin agrupación, `4250 %`
 * es feo pero inequívoco — y si el número asombra, el dato está mal, que es
 * exactamente lo que debe notarse.
 */
export function porcentaje(valor: number | null | undefined, decimales = 2): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) {
    return SIN_DATO;
  }
  const texto = new Intl.NumberFormat(LOCALE, {
    minimumFractionDigits: 0,
    maximumFractionDigits: decimales,
    useGrouping: false,
  }).format(valor);
  return `${texto} %`;
}

/**
 * Monto con su moneda **delante y como código**, no con el símbolo: el cable
 * maneja `PEN` y `USD` y confundir `S/` con `$` en una renta es caro.
 */
export function monto(
  valor: number | null | undefined,
  moneda?: string | null,
): string {
  if (valor === null || valor === undefined || Number.isNaN(valor)) {
    return SIN_DATO;
  }
  const codigo = (moneda ?? '').trim();
  return codigo ? `${codigo} ${numero(valor, 2)}` : SIN_DATO;
}

/** Texto con respaldo: en blanco -> `—`. */
export function texto(valor: string | null | undefined): string {
  const limpio = (valor ?? '').trim();
  return limpio || SIN_DATO;
}

/**
 * Iniciales para el avatar: dos letras como mucho.
 *
 * Vive aquí y no en cada componente porque la topbar y la pantalla de perfil
 * pintan el MISMO avatar cuando no hay foto, y dos copias de esta regla
 * acabarían enseñando iniciales distintas en la misma pantalla.
 */
export function iniciales(nombre: string | null | undefined): string {
  const limpio = (nombre ?? '').trim();
  if (!limpio) {
    return '·';
  }
  return limpio
    .split(/\s+/)
    .slice(0, 2)
    .map((parte) => parte.charAt(0).toUpperCase())
    .join('');
}

/** `true` -> `Sí`, `false` -> `No`, ausente -> `—`. */
export function siNo(valor: boolean | null | undefined): string {
  if (valor === null || valor === undefined) {
    return SIN_DATO;
  }
  return valor ? 'Sí' : 'No';
}
