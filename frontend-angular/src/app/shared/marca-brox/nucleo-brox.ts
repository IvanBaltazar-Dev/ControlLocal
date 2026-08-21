import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * **El emblema de BROX**: la malla octogonal con sus ocho nodos.
 *
 * Es la misma geometría del logotipo —octógono de radio 17 en un lienzo de 48,
 * ocho vértices y cuatro diámetros— y va **quieto**. En el prototipo se fueron,
 * por este orden, un cometa girando, dos octógonos en órbitas opuestas, tres
 * impulsos por *motion path*, los anillos de fondo, el corazón latiendo y el
 * halo: cada uno funcionaba y juntos se anulaban. La señal de vida vive en el
 * punto verde de la cabecera, que es una sola cosa moviéndose.
 *
 * El resplandor es un filtro SVG de verdad (desenfoque + merge), no una sombra:
 * a 46 px es lo que hace que se vea encendido y no dibujado.
 *
 * Los `id` de las `defs` llevan sufijo propio porque el documento puede tener
 * más de un emblema a la vez y los identificadores de SVG son globales.
 */
@Component({
  selector: 'cl-nucleo-brox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'nucleo', 'aria-hidden': 'true' },
  template: `
    <svg viewBox="0 0 48 48">
      <defs>
        <linearGradient id="brox-hilo" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#FFE9BE" />
          <stop offset=".5" stop-color="#E0A11B" />
          <stop offset="1" stop-color="#8FB6F0" />
        </linearGradient>
        <filter id="brox-bloom" x="-70%" y="-70%" width="240%" height="240%">
          <feGaussianBlur stdDeviation="1.5" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>
      <path
        class="malla cables"
        d="M39.71 30.51 L8.29 17.49 M30.51 39.71 L17.49 8.29 M17.49 39.71 L30.51 8.29 M8.29 30.51 L39.71 17.49"
      />
      <path
        class="malla"
        d="M39.71 30.51 L30.51 39.71 L17.49 39.71 L8.29 30.51 L8.29 17.49 L17.49 8.29 L30.51 8.29 L39.71 17.49 Z"
      />
      <path class="centro" d="M24 18.6 29.4 24 24 29.4 18.6 24Z" />
      <g class="nodos" filter="url(#brox-bloom)">
        <circle cx="39.71" cy="30.51" r="2.1" />
        <circle cx="30.51" cy="39.71" r="1.5" />
        <circle cx="17.49" cy="39.71" r="2.1" />
        <circle cx="8.29" cy="30.51" r="1.5" />
        <circle cx="8.29" cy="17.49" r="2.1" />
        <circle cx="17.49" cy="8.29" r="1.5" />
        <circle cx="30.51" cy="8.29" r="2.1" />
        <circle cx="39.71" cy="17.49" r="1.5" />
      </g>
    </svg>
  `,
  styles: `
    :host {
      flex: none;
      position: relative;
      width: 46px;
      height: 46px;
      display: grid;
      place-items: center;
    }

    svg {
      position: relative;
      z-index: 1;
      width: 46px;
      height: 46px;
      overflow: visible;
    }

    .malla {
      fill: none;
      stroke: url(#brox-hilo);
      stroke-width: 1.15;
      stroke-linejoin: round;
    }

    .cables {
      stroke: rgba(147, 197, 253, 0.3);
      stroke-width: 0.8;
      stroke-linecap: round;
    }

    .nodos circle {
      fill: #f7d68a;
    }

    .centro {
      fill: none;
      stroke: #ffe6b0;
      stroke-width: 1.3;
      stroke-linejoin: round;
    }
  `,
})
export class NucleoBrox {}
