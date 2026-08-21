import { NombreIcono } from '../shared/icono/icono';

/**
 * **Cómo se ve cada paso de las dos cadenas del negocio** — color, icono y
 * rótulo.
 *
 * ## Qué decide esto y qué no
 *
 * Nada del dominio. El lado (`OFERTA`/`DEMANDA`) y el paso los decide el
 * backend (`LadoDeLaOperacion`), y esta tabla solo traduce ese paso a los tres
 * datos que la pantalla necesita para dibujarlo. Es el mismo reparto que E1
 * fijó para las señales: el dominio clasifica, la pantalla elige el color.
 *
 * ## Por qué la clave es el paso y no el tipo de entidad
 *
 * Porque el paso es lo que el dominio publica. Una tabla `entidadTipo → color`
 * sería una segunda clasificación en el cliente, y tendría que mantenerse en
 * paralelo con la del backend cada vez que aparezca una entidad nueva —
 * exactamente lo que `LadoDeLaOperacion` documenta que no debe pasar.
 *
 * Los ocho colores `--p-*` vienen del prototipo: el canto de la tarjeta dice de
 * qué habla sin que haya que leer el rótulo.
 */
export interface Proceso {
  /** Cómo se nombra el paso en pantalla. */
  rotulo: string;
  /** El token de color, tal cual se pasa a `--pc`. */
  color: string;
  icono: NombreIcono;
}

/** Los dos lados, con su rótulo y su cadena. Copia de `LadoDeLaOperacion`. */
export const CADENAS: Readonly<Record<string, { rotulo: string; pasos: readonly string[] }>> = {
  OFERTA: { rotulo: 'Propietario', pasos: ['PROSPECCION', 'CAPTACION', 'PUBLICACION'] },
  DEMANDA: { rotulo: 'Cliente', pasos: ['OPORTUNIDAD', 'VISITA', 'SOLICITUD', 'CONTRATO'] },
};

const PROCESOS: Readonly<Record<string, Proceso>> = {
  PROSPECCION: { rotulo: 'Prospección', color: 'var(--p-prospeccion)', icono: 'mapa' },
  CAPTACION: { rotulo: 'Captación', color: 'var(--p-captacion)', icono: 'firma' },
  PUBLICACION: { rotulo: 'Publicación', color: 'var(--p-publicacion)', icono: 'megafono' },
  OPORTUNIDAD: { rotulo: 'Oportunidad', color: 'var(--p-oportunidad)', icono: 'diana' },
  VISITA: { rotulo: 'Visita', color: 'var(--p-visita)', icono: 'cal' },
  SOLICITUD: { rotulo: 'Solicitud', color: 'var(--p-solicitud)', icono: 'doc' },
  CONTRATO: { rotulo: 'Comisión', color: 'var(--p-comision)', icono: 'moneda' },
};

/**
 * Lo que se dibuja cuando el asunto llega **sin paso**.
 *
 * No se adivina uno: un asunto cuyo tipo de entidad no está declarado en
 * `LadoDeLaOperacion` viaja con `lado` y `paso` nulos, y colgarlo del paso más
 * frecuente lo pintaría del color de un proceso al que no pertenece. Gris, sin
 * rótulo de proceso, y el hueco se ve.
 */
const SIN_PASO: Proceso = { rotulo: '', color: 'var(--ink-3)', icono: 'circulo' };

export function procesoDe(paso: string | null | undefined): Proceso {
  return (paso && PROCESOS[paso]) || SIN_PASO;
}

/** El rótulo del lado: «Propietario» o «Cliente». Vacío si no viene. */
export function rotuloDelLado(lado: string | null | undefined): string {
  return (lado && CADENAS[lado]?.rotulo) || '';
}

/**
 * Los segmentos de la cadena de SU lado, marcando en cuál está.
 *
 * Tres en oferta y cuatro en demanda: **no son una cadena de siete**. Devuelve
 * vacío sin lado — dibujar una cadena inventada situaría el asunto en un
 * proceso que nadie declaró.
 */
export function segmentosDe(
  lado: string | null | undefined,
  paso: string | null | undefined,
): { hecho: boolean; aqui: boolean }[] {
  const cadena = lado ? CADENAS[lado] : undefined;
  if (!cadena) {
    return [];
  }
  const aqui = paso ? cadena.pasos.indexOf(paso) : -1;
  return cadena.pasos.map((_, i) => ({ hecho: aqui >= 0 && i < aqui, aqui: i === aqui }));
}
