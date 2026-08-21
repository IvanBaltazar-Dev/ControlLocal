import { AsuntoDelBroker } from '../../core/api/dashboard.service';
import { InterpretacionDelAsunto, Tarea } from '../../core/api/tareas.service';
import { Proceso, procesoDe } from '../../core/procesos';
import { fechaCorta } from '../../core/formato';

/**
 * **Un asunto del foco, aplanado para dibujarlo.**
 *
 * La bandeja del agente y el foco del broker son dos colecciones distintas del
 * cable —y lo seguirán siendo, porque una dice «haz» y la otra «decide»—, pero
 * la fila que las pinta es la misma. Esto es esa fila: no añade ningún hecho,
 * solo elige de dónde sale cada uno.
 *
 * Todo lo que clasifica (lado, paso, estado de cada hecho, expediente, lectura)
 * llega ya resuelto. Aquí no se puntúa, no se ordena y no se compara con ningún
 * umbral.
 */
export interface AsuntoDelFoco {
  /** Identidad estable entre recargas. El del broker lleva el sufijo del rol. */
  id: string;
  titulo: string;
  /** La línea de hecho, bajo el título. Nunca una etiqueta de clasificación. */
  hecho: string;
  proceso: Proceso;
  lado: string | null;
  paso: string | null;
  /** Los trozos de identidad de la cabecera del Radar, en orden. */
  identidad: string[];
  interpretacion: InterpretacionDelAsunto | null;
  /**
   * Cuándo vuelve a moverse esto. `null` **se dice**, no se esconde: muchas
   * veces es el dato importante (D-E2-1 §7.0).
   */
  proximo: string | null;
  /** Ruta real del SPA donde se trabaja. */
  destino: string | null;
  /**
   * El tono del número de la fila.
   *
   * Sale de la prioridad que decide el dominio. `null` cuando el cable no la
   * trae —los asuntos del broker no la llevan— y entonces el número va en tinta
   * neutra: pintarlo del tono más frecuente sería inventar una severidad.
   */
  tono: 'alta' | 'media' | 'baja' | null;
  /** La tarea original, cuando la hay: solo ella se puede cancelar. */
  tarea: Tarea | null;
}

const TONO_POR_PRIORIDAD: Record<string, 'alta' | 'media' | 'baja'> = {
  ALTA: 'alta',
  MEDIA: 'media',
  BAJA: 'baja',
};

/** «12 días sin acción · vence 30 ago». Sin demora ni fecha, lo dice también. */
function hechoDeLaTarea(tarea: Tarea): string {
  const partes: string[] = [];
  const dias = tarea.diasSinAccion ?? 0;
  partes.push(dias <= 0 ? 'sin demora' : dias === 1 ? '1 día sin acción' : `${dias} días sin acción`);
  if (tarea.fechaVencimiento) {
    partes.push(`vence ${fechaCorta(tarea.fechaVencimiento)}`);
  }
  return partes.join(' · ');
}

export function desdeTarea(tarea: Tarea): AsuntoDelFoco {
  return {
    id: `tarea:${tarea.id}`,
    titulo: tarea.descripcion,
    hecho: hechoDeLaTarea(tarea),
    proceso: procesoDe(tarea.paso),
    lado: tarea.lado ?? null,
    paso: tarea.paso ?? null,
    identidad: identidadDe(procesoDe(tarea.paso).rotulo, tarea.entidadCodigo),
    interpretacion: tarea.interpretacion ?? null,
    proximo: tarea.fechaProgramada
      ? fechaCorta(tarea.fechaProgramada)
      : tarea.fechaVencimiento
        ? fechaCorta(tarea.fechaVencimiento)
        : null,
    destino: tarea.rutaResolver ?? null,
    tono: TONO_POR_PRIORIDAD[tarea.prioridad] ?? null,
    tarea,
  };
}

/**
 * El asunto del broker.
 *
 * **El título sale del primer hecho `FALTA`**, que es la frase que el dominio ya
 * escribe («Falta tu decisión sobre la captación»). No hay dirección ni nombre
 * propio en este lado del cable, y poner el código como título dejaría la fila
 * diciendo `CAP-0012` — que identifica el registro pero no dice qué pasa con él.
 * El código sigue estando: viaja en la identidad del Radar, que es donde sirve
 * para ir a buscarlo.
 */
export function desdeAsuntoDelBroker(asunto: AsuntoDelBroker): AsuntoDelFoco {
  const hechos = asunto.interpretacion?.comoEsta?.hechos ?? [];
  const falta = hechos.find((h) => h.estado === 'FALTA');
  const dias = asunto.diasEsperando;
  return {
    id: asunto.id,
    titulo: falta?.texto ?? hechos[0]?.texto ?? asunto.tipo,
    hecho:
      dias <= 0
        ? 'esperando tu decisión'
        : dias === 1
          ? '1 día esperando tu decisión'
          : `${dias} días esperando tu decisión`,
    proceso: procesoDe(asunto.paso),
    lado: asunto.lado,
    paso: asunto.paso,
    identidad: identidadDe(procesoDe(asunto.paso).rotulo, asunto.entidadCodigo),
    interpretacion: asunto.interpretacion,
    proximo: null,
    destino: asunto.destino,
    tono: null,
    tarea: null,
  };
}

/**
 * De qué habla el asunto, en la cabecera del Radar.
 *
 * El código del registro **sí** aparece aquí, y es deliberado: el prototipo lo
 * prohibía porque sus asuntos tenían dirección y nombre propio, y el cable real
 * todavía no los trae. Sin el código no queda ninguna forma de reconocer de qué
 * registro se habla, que es peor que enseñarlo.
 */
function identidadDe(rotuloProceso: string, codigo: string | null | undefined): string[] {
  return [rotuloProceso, codigo ?? ''].filter((x) => x.length > 0);
}
