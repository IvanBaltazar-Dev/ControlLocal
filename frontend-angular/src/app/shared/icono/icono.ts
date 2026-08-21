import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Los iconos de BROX, portados del prototipo (`docs/ai/prototipos/inicio.html`).
 *
 * ## Por qué un `@switch` y no un sprite con `<use href="#i-mapa">`
 *
 * El prototipo declara los trazos una vez en un `<defs>` y los referencia con
 * `<use>`. En un artefacto de una sola página eso es lo correcto; en el SPA no,
 * porque `index.html` lleva `<base href="/">` y entonces `href="#i-mapa"`
 * resuelve contra la URL base —`/#i-mapa`— y `<use>` deja de encontrar nada en
 * cuanto la ruta no es la raíz. Es un fallo silencioso: no hay error, solo
 * huecos donde había iconos.
 *
 * Así que los trazos viven aquí, literales, y el componente es autocontenido.
 * Son los **mismos path** del prototipo, copiados: si uno cambia allí, cambia
 * aquí.
 *
 * `viewBox` es 0 0 20 20 en todos, y el trazo se hereda de `currentColor`: el
 * color lo pone quien lo usa, nunca el icono.
 */
export type NombreIcono =
  // Procesos: uno por cada eslabón de las dos cadenas del negocio.
  | 'mapa'
  | 'firma'
  | 'megafono'
  | 'persona'
  | 'diana'
  | 'cal'
  | 'doc'
  | 'moneda'
  // Interfaz.
  | 'flecha'
  | 'atras'
  | 'arriba'
  | 'chevron-d'
  | 'cerrar'
  | 'check'
  | 'rombo'
  | 'pulso'
  | 'chispa'
  | 'reloj'
  | 'freno'
  | 'circulo'
  | 'raya'
  | 'graf'
  | 'lupa'
  | 'mas';

@Component({
  selector: 'cl-icono',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host {
      display: inline-flex;
    }
  `,
  template: `
    <svg
      [attr.width]="tamano()"
      [attr.height]="tamano()"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="grosor()"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      @switch (nombre()) {
        @case ('mapa') {
          <path d="M10 17.4s5.2-4.6 5.2-8.2a5.2 5.2 0 1 0-10.4 0c0 3.6 5.2 8.2 5.2 8.2z" />
          <circle cx="10" cy="9.1" r="2" />
        }
        @case ('firma') {
          <path d="M6.2 3.6h5.2L14.8 7v9.4H6.2z" />
          <path d="M11.4 3.6V7h3.4" />
          <path d="M8.4 13.2s1-1.6 1.9-1.6 1 1.6 1.9 1.6" />
        }
        @case ('megafono') {
          <path d="M4.2 8.4h2.6l6-3.4v10l-6-3.4H4.2z" />
          <path d="M6.8 11.6v3.6h2.4v-2.2" />
          <path d="M15.4 8.2a2.6 2.6 0 0 1 0 3.6" />
        }
        @case ('persona') {
          <circle cx="10" cy="7.2" r="2.6" />
          <path d="M4.9 16.4a5.1 5.1 0 0 1 10.2 0" />
        }
        @case ('diana') {
          <circle cx="10" cy="10" r="6.2" />
          <circle cx="10" cy="10" r="2.3" />
        }
        @case ('cal') {
          <rect x="3.8" y="4.8" width="12.4" height="11.4" rx="1.4" />
          <path d="M3.8 8.2h12.4M7 3.4v2.6M13 3.4v2.6" />
        }
        @case ('doc') {
          <path d="M6.2 3.6h5.2L14.8 7v9.4H6.2z" />
          <path d="M11.4 3.6V7h3.4" />
          <path d="M8.6 11h4M8.6 13.4h3" />
        }
        @case ('moneda') {
          <circle cx="10" cy="10" r="6.4" />
          <path
            d="M10 6.2v7.6M8.1 8.1c0-.85.85-1.45 1.9-1.45s1.9.6 1.9 1.45-.95 1.25-1.9 1.7-1.9.85-1.9 1.7.85 1.45 1.9 1.45 1.9-.6 1.9-1.45"
          />
        }
        @case ('flecha') {
          <path d="M4.4 10h10.4M11.2 6.4l3.6 3.6-3.6 3.6" />
        }
        @case ('atras') {
          <path d="M15.6 10H5.2M9 6.4 5.4 10l3.6 3.6" />
        }
        @case ('arriba') {
          <path d="M10 15.4V4.8M5.8 9l4.2-4.2L14.2 9" />
        }
        @case ('chevron-d') {
          <path d="M8.4 6.5 11.9 10l-3.5 3.5" />
        }
        @case ('cerrar') {
          <path d="M5.8 5.8l8.4 8.4M14.2 5.8l-8.4 8.4" />
        }
        @case ('check') {
          <path d="M4.6 10.4 8 13.8l7.4-7.8" />
        }
        @case ('rombo') {
          <path d="M10 3.4 16.6 10 10 16.6 3.4 10z" />
        }
        @case ('pulso') {
          <path d="M2.8 10h3.1l2-4.6 2.6 9.2 2-4.6h4.7" />
        }
        @case ('chispa') {
          <path d="M10 3.2 11.7 8 16.5 9.7 11.7 11.4 10 16.2 8.3 11.4 3.5 9.7 8.3 8z" />
        }
        @case ('reloj') {
          <circle cx="10" cy="10" r="6.8" />
          <path d="M10 6.2V10l2.6 1.9" />
        }
        @case ('freno') {
          <circle cx="10" cy="10" r="6.8" />
          <path d="M5.4 5.4 14.6 14.6" />
        }
        @case ('circulo') {
          <circle cx="10" cy="10" r="5.4" />
        }
        @case ('raya') {
          <path d="M5.4 10h9.2" />
        }
        @case ('graf') {
          <path d="M4 16V9.4M8.6 16V5.2M13.2 16v-3.8M17 16V7.6" />
        }
        @case ('lupa') {
          <circle cx="9.1" cy="9.1" r="4.7" />
          <path d="M12.6 12.6 16.5 16.5" />
        }
        @case ('mas') {
          <path d="M10 5.4v9.2M5.4 10h9.2" />
        }
      }
    </svg>
  `,
})
export class Icono {
  readonly nombre = input.required<NombreIcono>();
  readonly tamano = input(15);
  readonly grosor = input(1.6);
}
