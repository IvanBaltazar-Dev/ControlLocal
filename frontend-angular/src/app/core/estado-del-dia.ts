import { IndicadorSenal } from './api/indicadores.service';

/**
 * **Cómo está la jornada**, en tres estados y con las mismas palabras para todo
 * el mundo.
 *
 * ## Por qué tres y no cinco
 *
 * El prototipo tiene «Carga alta» y «Atención» además de «Al día» y «Día
 * cubierto». Dos de ésos son clasificaciones de la carga —cuánto es mucho— y el
 * dominio no las hace: decidirlas aquí sería devolver al cliente el umbral que
 * E1 le quitó. Lo que sí se sabe sin inventar ningún umbral es
 *
 * - si queda algo por atender o no queda nada, y
 * - si el dominio marcó **alguna señal como `ALTO`** — eso lo clasifica él, no
 *   esta función.
 *
 * ## Por qué el mismo vocabulario para agente y broker
 *
 * La pastilla describe **el día**, no a la persona. Un broker con tres
 * solicitudes por evaluar y un agente con tres visitas sin cerrar están en la
 * misma situación, y darles dos idiomas obligaría a traducir mentalmente al
 * pasar de una pantalla a otra. Lo que cambia por rol es de quién son los
 * asuntos, y eso ya lo dice el titular.
 *
 * ## Y por qué estas palabras
 *
 * Ninguna acusa. «Al día» y «En marcha» son estados de trabajo normal;
 * «Requiere atención» nombra el hecho —hay algo marcado como urgente— sin
 * llamarlo riesgo ni retraso, que serían un juicio sobre quien lo lee. Un
 * tablero que reprocha se deja de mirar.
 */
export type ClaveDelDia = 'AL_DIA' | 'EN_MARCHA' | 'REQUIERE_ATENCION';

export interface EstadoDelDia {
  clave: ClaveDelDia;
  /** Lo que se lee en la pastilla. */
  texto: string;
  /** El token de color, tal cual va al `style`. */
  color: string;
}

const ESTADOS: Record<ClaveDelDia, EstadoDelDia> = {
  AL_DIA: { clave: 'AL_DIA', texto: 'Al día', color: 'var(--positivo-voz)' },
  EN_MARCHA: { clave: 'EN_MARCHA', texto: 'En marcha', color: 'var(--ink-2)' },
  REQUIERE_ATENCION: {
    clave: 'REQUIERE_ATENCION',
    texto: 'Requiere atención',
    color: 'var(--atencion-voz)',
  },
};

/**
 * @param asuntos cuántos dependen de quien mira. Es la misma colección que
 * alimenta el foco y la cola, no el resumen de indicadores.
 * @param senales las del dominio, ya clasificadas. Aquí solo se mira si alguna
 * llegó en `ALTO`; **nunca se decide cuándo algo pasa a serlo**.
 */
export function estadoDelDia(
  asuntos: number,
  senales: readonly IndicadorSenal[] = [],
): EstadoDelDia {
  if (senales.some((senal) => senal.nivelAtencion === 'ALTO')) {
    return ESTADOS.REQUIERE_ATENCION;
  }
  return asuntos === 0 ? ESTADOS.AL_DIA : ESTADOS.EN_MARCHA;
}
