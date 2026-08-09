import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Una serie del gráfico: su nombre, sus valores y el color que la identifica. */
export interface SerieGrafico {
  nombre: string;
  valores: readonly number[];
  color: string;
}

interface Columna {
  x: number;
  y: number;
  ancho: number;
  alto: number;
  ruta: string;
  color: string;
  titulo: string;
}

interface Punto {
  x: number;
  y: number;
  titulo: string;
}

interface Marca {
  y: number;
  etiqueta: string;
}

interface EtiquetaEje {
  x: number;
  texto: string;
}

const ANCHO = 640;
const MARGEN = { arriba: 12, derecha: 8, abajo: 26, izquierda: 38 };
const RADIO = 4;

/**
 * Serie temporal: columnas agrupadas para conteos, línea para un porcentaje.
 *
 * Dibuja SVG a mano en vez de traer una librería de gráficos: son dos formas,
 * el resto del SPA no las necesita y una dependencia de gráficos pesa más que
 * este archivo.
 *
 * Tres reglas que están metidas en el componente y no se pueden saltar desde
 * fuera:
 *
 * - **Un solo eje.** El componente acepta series de UNA magnitud; para mezclar
 *   conteos y porcentajes hay que usar dos gráficos. Dos escalas en el mismo
 *   marco es la forma más fácil de mentir con un gráfico.
 * - **La escala arranca en cero.** Recortar la base exagera las diferencias.
 * - **Con dos o más series la leyenda es obligatoria**, y cada marca lleva su
 *   valor en el `title` — el color nunca es la única forma de leer el dato.
 *
 * Los colores se pasan desde fuera porque quien decide la identidad de la
 * serie es la pantalla; los que usa el bloque comercial están validados contra
 * daltonismo y contraste.
 */
@Component({
  selector: 'cl-grafico-serie',
  templateUrl: './grafico-serie.html',
  styleUrl: './grafico-serie.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraficoSerie {
  readonly etiquetas = input.required<readonly string[]>();
  readonly series = input.required<readonly SerieGrafico[]>();
  readonly tipo = input<'columnas' | 'linea'>('columnas');
  /** Sufijo del valor en los rótulos (`%` para tasas). */
  readonly sufijo = input('');
  readonly alto = input(180);
  /** Qué mide el eje; se usa en la descripción accesible del gráfico. */
  readonly descripcion = input('');

  protected readonly ancho = ANCHO;

  private readonly maximo = computed(() => {
    const valores = this.series().flatMap((s) => [...s.valores]);
    const mayor = valores.length > 0 ? Math.max(...valores) : 0;
    // Nunca 0: un gráfico plano en cero se dibuja igual, sin dividir por cero.
    return mayor > 0 ? mayor : 1;
  });

  private readonly areaAlto = computed(() => this.alto() - MARGEN.arriba - MARGEN.abajo);
  private readonly areaAncho = computed(() => ANCHO - MARGEN.izquierda - MARGEN.derecha);

  /**
   * Marcas de referencia: 0, la mitad y el máximo. **Se descartan las que
   * repetirían rótulo** — con un máximo de 1, la mitad también se redondea a 1
   * y el eje mostraría "0 1 1", que parece un error de cálculo.
   */
  protected readonly marcas = computed<Marca[]>(() => {
    const maximo = this.maximo();
    const vistas = new Set<string>();
    return [0, 0.5, 1]
      .map((fraccion) => ({
        y: MARGEN.arriba + this.areaAlto() * (1 - fraccion),
        etiqueta: `${Math.round(maximo * fraccion)}${this.sufijo()}`,
      }))
      .filter((marca) => {
        if (vistas.has(marca.etiqueta)) {
          return false;
        }
        vistas.add(marca.etiqueta);
        return true;
      });
  });

  protected readonly hayDatos = computed(
    () => this.etiquetas().length > 0 && this.series().length > 0,
  );

  protected readonly conLeyenda = computed(() => this.series().length > 1);

  /**
   * Con muchos cubos las etiquetas se pisan, así que se muestra una de cada n.
   * La primera y la última siempre salen: son las que sitúan la ventana.
   */
  protected readonly etiquetasEje = computed<EtiquetaEje[]>(() => {
    const total = this.etiquetas().length;
    if (total === 0) {
      return [];
    }
    const paso = Math.ceil(total / 12);
    const anchoCubo = this.areaAncho() / total;
    return this.etiquetas()
      .map((texto, indice) => ({ texto, indice }))
      .filter(({ indice }) => indice % paso === 0 || indice === total - 1)
      .map(({ texto, indice }) => ({
        x: MARGEN.izquierda + anchoCubo * (indice + 0.5),
        texto,
      }));
  });

  protected readonly columnas = computed<Columna[]>(() => {
    if (this.tipo() !== 'columnas' || !this.hayDatos()) {
      return [];
    }
    const cubos = this.etiquetas().length;
    const series = this.series();
    const anchoCubo = this.areaAncho() / cubos;
    // 2px de superficie entre columnas adyacentes: las separa sin engordarlas.
    const anchoBarra = Math.max(2, (anchoCubo * 0.7) / series.length - 2);
    const base = MARGEN.arriba + this.areaAlto();

    return series.flatMap((serie, iSerie) =>
      this.etiquetas().map((etiqueta, iCubo) => {
        const valor = serie.valores[iCubo] ?? 0;
        const alto = (valor / this.maximo()) * this.areaAlto();
        const grupo = anchoCubo * (iCubo + 0.5) - ((anchoBarra + 2) * series.length) / 2;
        const x = MARGEN.izquierda + grupo + (anchoBarra + 2) * iSerie;
        return {
          x,
          y: base - alto,
          ancho: anchoBarra,
          alto,
          ruta: columnaRedondeada(x, base - alto, anchoBarra, alto),
          color: serie.color,
          titulo: `${etiqueta} · ${serie.nombre}: ${valor}${this.sufijo()}`,
        };
      }),
    );
  });

  /** Un único trazo: la línea se reserva para una sola magnitud. */
  protected readonly linea = computed(() => {
    if (this.tipo() !== 'linea' || !this.hayDatos()) {
      return '';
    }
    return this.puntos()
      .map((punto, indice) => `${indice === 0 ? 'M' : 'L'}${punto.x},${punto.y}`)
      .join(' ');
  });

  protected readonly puntos = computed<Punto[]>(() => {
    if (this.tipo() !== 'linea' || !this.hayDatos()) {
      return [];
    }
    const serie = this.series()[0];
    const cubos = this.etiquetas().length;
    const anchoCubo = this.areaAncho() / cubos;
    const base = MARGEN.arriba + this.areaAlto();
    return this.etiquetas().map((etiqueta, indice) => {
      const valor = serie.valores[indice] ?? 0;
      return {
        x: MARGEN.izquierda + anchoCubo * (indice + 0.5),
        y: base - (valor / this.maximo()) * this.areaAlto(),
        titulo: `${etiqueta} · ${serie.nombre}: ${valor}${this.sufijo()}`,
      };
    });
  });

  protected readonly colorLinea = computed(() => this.series()[0]?.color ?? 'currentColor');

  protected readonly ejeIzquierdo = MARGEN.izquierda;

  /** Texto alternativo: lo que un lector de pantalla necesita del gráfico. */
  protected readonly alternativo = computed(() => {
    const partes = this.series().map((serie) => {
      const valores = [...serie.valores];
      const total = valores.reduce((suma, valor) => suma + valor, 0);
      return `${serie.nombre}: ${total}${this.sufijo()} en total, máximo ${Math.max(0, ...valores)}${this.sufijo()}`;
    });
    return `${this.descripcion()}. ${partes.join('. ')}`;
  });
}

/** Columna con las esquinas superiores redondeadas y la base anclada. */
function columnaRedondeada(x: number, y: number, ancho: number, alto: number): string {
  if (alto <= 0) {
    return '';
  }
  const radio = Math.min(RADIO, ancho / 2, alto);
  return [
    `M${x},${y + alto}`,
    `L${x},${y + radio}`,
    `Q${x},${y} ${x + radio},${y}`,
    `L${x + ancho - radio},${y}`,
    `Q${x + ancho},${y} ${x + ancho},${y + radio}`,
    `L${x + ancho},${y + alto}`,
    'Z',
  ].join(' ');
}
