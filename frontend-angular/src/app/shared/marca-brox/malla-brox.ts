import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * **La firma de BROX de fondo**: la misma malla del emblema, quieta y al 5–7 %.
 *
 * Se intuye, no se lee. Va detrás del pie de indicadores y detrás del bloque de
 * resolución del Radar, y el color lo pone quien la usa: sobre el lienzo va en
 * oro y sobre el Radar en azul.
 *
 * **El color entra por variables, no por selector.** Un `.pie cl-malla-brox
 * path { stroke: … }` escrito en el padre no llegaría: la encapsulación emulada
 * de Angular sella el `path` con el atributo de ESTE componente, y la regla del
 * padre se reescribe con el suyo. Las propiedades personalizadas sí cruzan la
 * frontera, porque heredan.
 *
 * No lleva `aria`: es decoración, y `aria-hidden` en el host la saca del árbol.
 */
@Component({
  selector: 'cl-malla-brox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'malla-fondo', 'aria-hidden': 'true' },
  template: `
    <svg viewBox="0 0 84 84">
      <path d="M73.4 55 L55 73.4 L29 73.4 L10.6 55 L10.6 29 L29 10.6 L55 10.6 L73.4 29 Z" />
      <path d="M73.4 55 L10.6 29 M55 73.4 L29 10.6 M29 73.4 L55 10.6 M10.6 55 L73.4 29" />
      <path
        d="M73.4 55 L10.6 55 M55 73.4 L10.6 29 M29 73.4 L29 10.6 M10.6 55 L55 10.6 M10.6 29 L73.4 29 M29 10.6 L73.4 55 M55 10.6 L55 73.4 M73.4 29 L29 73.4"
      />
      <circle cx="73.4" cy="55" r="1.8" />
      <circle cx="55" cy="73.4" r="1.8" />
      <circle cx="29" cy="73.4" r="1.8" />
      <circle cx="10.6" cy="55" r="1.8" />
      <circle cx="10.6" cy="29" r="1.8" />
      <circle cx="29" cy="10.6" r="1.8" />
      <circle cx="55" cy="10.6" r="1.8" />
      <circle cx="73.4" cy="29" r="1.8" />
    </svg>
  `,
  styles: `
    /* display:block explícito, y no confiado a position:absolute. Un elemento
       personalizado es inline por defecto, así que en cuanto algo de fuera le
       devuelve position:relative deja de aceptar width y el SVG se estira al
       ancho del contenedor. Eso abrió un hueco de 400 px en el bloque de
       resolución del Radar. */
    :host {
      display: block;
      position: absolute;
      z-index: 0;
      pointer-events: none;
    }

    svg {
      width: 100%;
      height: 100%;
      display: block;
    }

    path {
      fill: none;
      stroke: var(--malla-trazo, currentColor);
      stroke-width: var(--malla-grosor, 1);
    }

    circle {
      fill: var(--malla-nodo, currentColor);
    }
  `,
})
export class MallaBrox {}
