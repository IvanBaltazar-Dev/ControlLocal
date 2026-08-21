import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { KpiCanonico } from '../../../core/api/indicadores.service';

/** Una marca radial: un trazo corto que cruza el anillo en un ángulo. */
interface Marca {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

const CX = 34;
const CY = 34;
const RADIO = 26;
const GROSOR = 6;
/** El recorrido arranca abajo a la izquierda y barre 270°, no 360. */
const INICIO = 135;
const BARRIDO = 270;
const PERIMETRO = 2 * Math.PI * RADIO;
const ARCO = (PERIMETRO * BARRIDO) / 360;

/** El color del estado. El estado lo decide el dominio; el color, la pantalla. */
const COLOR_RITMO: Record<string, string> = {
  EN_RITMO: 'var(--positivo)',
  ATENCION: 'var(--atencion)',
  FUERA_DE_RITMO: 'var(--riesgo)',
  SIN_BASE: 'var(--ink-3)',
};

/**
 * **La esfera de un KPI: cinco cosas en un instrumento** (D-E2-2).
 *
 * Contesta, sin leer una palabra: cuánto llevas (el arco y su manija), hasta
 * dónde llegarás si mantienes el ritmo (la prolongación tenue), cuál es la meta
 * (la marca fina al final del recorrido), dónde tocaría estar hoy (la marca
 * discreta de dentro) y cómo vas (el color).
 *
 * ## Por qué 270° y no un círculo
 *
 * Con el círculo entero la meta cae en el mismo punto que el origen y la marca
 * de meta no se puede dibujar. Con 270 la meta es el final del recorrido y
 * queda sitio para la marca del ritmo esperado.
 *
 * ## El arco lleva el ESTADO, no la identidad del KPI
 *
 * La identidad vive en el filete de la cabecera de su columna. Un anillo azul
 * junto a un «fuera de ritmo» rojo a dos centímetros se contradice.
 *
 * ## Sin meta no se dibuja recorrido
 *
 * Ni arco, ni marcas, ni manija: solo la pista. Una esfera vacía dice «aquí no
 * hay nada que medir» mejor que un cero, que diría que el objetivo era cero.
 */
@Component({
  selector: 'cl-esfera',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[style.--e]': 'color()' },
  template: `
    <svg width="68" height="68" viewBox="0 0 68 68" aria-hidden="true">
      <g [attr.transform]="'rotate(' + inicio + ' ' + cx + ' ' + cy + ')'">
        <circle
          class="pista"
          [attr.cx]="cx"
          [attr.cy]="cy"
          [attr.r]="radio"
          fill="none"
          [attr.stroke-width]="grosor"
          stroke-linecap="round"
          [attr.stroke-dasharray]="arco.toFixed(1) + ' ' + perimetro.toFixed(1)"
        />
        @if (hayRecorrido()) {
          <!-- El fantasma: hasta dónde llegarás si mantienes el ritmo. -->
          <circle
            class="proy"
            [attr.cx]="cx"
            [attr.cy]="cy"
            [attr.r]="radio"
            fill="none"
            [attr.stroke-width]="grosor"
            stroke-linecap="round"
            [attr.stroke-dasharray]="trazoProyectado() + ' ' + perimetro.toFixed(1)"
          />
          <circle
            class="arco"
            [attr.cx]="cx"
            [attr.cy]="cy"
            [attr.r]="radio"
            fill="none"
            [attr.stroke-width]="grosor"
            stroke-linecap="round"
            [attr.stroke-dasharray]="trazoRecorrido() + ' ' + perimetro.toFixed(1)"
          />
        }
      </g>

      @if (hayRecorrido()) {
        <!-- La META, al final del recorrido. -->
        @if (marcaDeMeta(); as m) {
          <line
            class="mk-meta"
            [attr.x1]="m.x1"
            [attr.y1]="m.y1"
            [attr.x2]="m.x2"
            [attr.y2]="m.y2"
            stroke-width="1.7"
            stroke-linecap="round"
          />
        }
        <!-- Y dónde tocaría estar HOY. Sin cadencia diaria no hay marca. -->
        @if (marcaDeHoy(); as m) {
          <line
            class="mk-hoy"
            [attr.x1]="m.x1"
            [attr.y1]="m.y1"
            [attr.x2]="m.x2"
            [attr.y2]="m.y2"
            stroke-width="1.4"
            stroke-linecap="round"
          />
        }
        <circle
          class="manija"
          [attr.cx]="manija().x1"
          [attr.cy]="manija().y1"
          r="3.4"
          fill="#ffffff"
          stroke-width="2.4"
        />
      }
    </svg>
  `,
  styles: `
    :host {
      position: relative;
      flex: none;
      width: 62px;
      height: 62px;
      display: block;
    }

    svg {
      display: block;
      width: 100%;
      height: 100%;
    }

    .pista {
      stroke: #efefea;
    }

    .arco,
    .proy,
    .manija {
      stroke: var(--e, var(--ink-3));
    }

    /* La proyección es el mismo arco, apagado: se lee como continuación y no
       como un segundo dato. */
    .proy {
      opacity: 0.28;
    }

    .mk-meta {
      stroke: var(--ink);
      opacity: 0.95;
    }

    .mk-hoy {
      stroke: var(--ink-3);
      opacity: 0.7;
    }

    /* El arco se barre al aparecer, y la manija llega después: el instrumento
       se lee mientras se dibuja. */
    .arco {
      animation: esfera-barrer 0.8s cubic-bezier(0.32, 0.72, 0, 1) both;
    }

    .manija {
      animation: esfera-aparecer 0.3s 0.55s ease both;
    }

    @keyframes esfera-barrer {
      from {
        stroke-dasharray: 0 999;
      }
    }

    @keyframes esfera-aparecer {
      from {
        opacity: 0;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .arco,
      .manija {
        animation: none;
      }
    }
  `,
})
export class Esfera {
  readonly kpi = input.required<KpiCanonico>();

  protected readonly cx = CX;
  protected readonly cy = CY;
  protected readonly radio = RADIO;
  protected readonly grosor = GROSOR;
  protected readonly inicio = INICIO;
  protected readonly perimetro = PERIMETRO;
  protected readonly arco = ARCO;

  protected readonly color = computed(
    () => COLOR_RITMO[this.kpi().estadoRitmo] ?? 'var(--ink-3)',
  );

  /** Sin meta no hay regla contra la que dibujar nada. */
  protected readonly hayRecorrido = computed(() => (this.kpi().metaPeriodo ?? 0) > 0);

  /**
   * La regla sobre la que se dibuja: la meta.
   *
   * En el prototipo la escala es el **universo** del que sale la cifra —«22 de
   * 31 prospecciones abiertas»—, y la meta es solo una marca sobre esa regla. El
   * cable no publica ese universo, así que aquí la regla es la meta y lo que la
   * supera se acota: pasarse es una buena noticia, no un arco que se sale de la
   * caja. El exceso lo dice el texto, con su cifra.
   */
  private readonly escala = computed(() => Math.max(this.kpi().metaPeriodo ?? 0, 1));

  private fraccion(valor: number | null | undefined): number | null {
    if (valor == null) {
      return null;
    }
    return Math.max(0, Math.min(valor / this.escala(), 1));
  }

  protected readonly trazoRecorrido = computed(() =>
    (ARCO * (this.fraccion(this.kpi().actual) ?? 0)).toFixed(1),
  );

  protected readonly trazoProyectado = computed(() =>
    (ARCO * (this.fraccion(this.kpi().proyeccionCierre) ?? 0)).toFixed(1),
  );

  protected readonly manija = computed(() =>
    this.punto((this.fraccion(this.kpi().actual) ?? 0) * BARRIDO, RADIO),
  );

  protected readonly marcaDeMeta = computed(() => this.marca(BARRIDO, 2.5));

  protected readonly marcaDeHoy = computed(() => {
    const fraccion = this.fraccion(this.kpi().metaEsperadaAHoy);
    // Sin cadencia diaria el dominio no reparte la meta, así que no hay «hoy»
    // que marcar. Dibujarlo igual inventaría un ritmo que el negocio no tiene.
    if (fraccion === null || this.kpi().sinCadencia) {
      return null;
    }
    return this.marca(fraccion * BARRIDO, 1.5);
  });

  /** Un punto del anillo, en el ángulo dado y a la distancia dada del centro. */
  private punto(angulo: number, distancia: number): Marca {
    const radianes = ((INICIO + angulo) * Math.PI) / 180;
    return {
      x1: +(CX + distancia * Math.cos(radianes)).toFixed(1),
      y1: +(CY + distancia * Math.sin(radianes)).toFixed(1),
      x2: 0,
      y2: 0,
    };
  }

  /** Un trazo que cruza el anillo de dentro afuera, sobresaliendo `largo`. */
  private marca(angulo: number, largo: number): Marca {
    const dentro = this.punto(angulo, RADIO - GROSOR / 2 - largo);
    const fuera = this.punto(angulo, RADIO + GROSOR / 2 + largo);
    return { x1: dentro.x1, y1: dentro.y1, x2: fuera.x1, y2: fuera.y1 };
  }
}
