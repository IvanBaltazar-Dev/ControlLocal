import { PestanaRuta } from '../../shared/pestanas/pestanas-ruta';

/**
 * **Indicadores es una página con dos caras.**
 *
 * *Rendimiento* mide por **actor** —cómo va el agente, el equipo, la
 * corredora—; *Avance por propiedad* (RF-017) mide por **inmueble**: qué pasó
 * sobre cada propiedad captada, acumulado. Son la misma pregunta analítica
 * mirada por dos ejes, y por eso D-E3-1 §4.3 pedía unirlas: dos entradas de
 * menú prometían dos secciones donde hay una.
 *
 * Cada cara conserva su ruta —`/reportes` sigue abriendo la suya— para que los
 * enlaces guardados no se rompan. Lo que desaparece es la entrada de menú.
 */
export const PESTANAS_INDICADORES: readonly PestanaRuta[] = [
  { etiqueta: 'Rendimiento', ruta: '/indicadores' },
  { etiqueta: 'Avance por propiedad', ruta: '/reportes' },
];
