import { Component, input, model } from '@angular/core';

/**
 * Filtro secundario estándar (`FilterSelect.razor` del Blazor): un select con
 * una opción "todos" de valor vacío y el resto **derivado de los datos**.
 *
 * Que las opciones vengan de los datos no es un detalle de estilo: en el
 * legado, los selects escritos a mano se desincronizaban del catálogo real y
 * ofrecían filtros que ya no devolvían nada. La pantalla debe pasar los
 * valores distintos que realmente hay en la lista.
 *
 * ```html
 * <cl-filtro-select marcador="Distrito" [opciones]="distritos()" [(valor)]="distrito" />
 * ```
 */
@Component({
  selector: 'cl-filtro-select',
  template: `
    <select class="filtro" [value]="valor()" (change)="alCambiar($event)">
      <option value="" [selected]="valor() === ''">{{ marcador() }}</option>
      @for (opcion of opciones(); track opcion.valor) {
        <option [value]="opcion.valor" [selected]="valor() === opcion.valor">
          {{ opcion.etiqueta }}
        </option>
      }
    </select>
  `,
  styleUrl: './filtro-select.scss',
})
export class FiltroSelect {
  /** Texto de la opción "sin filtro". Su valor es la cadena vacía. */
  readonly marcador = input('Todos');

  /**
   * Opciones ya derivadas de los datos. Se aceptan cadenas sueltas cuando el
   * valor y la etiqueta coinciden, y pares cuando no —que es el caso de los
   * códigos de una letra del cable (`'A'` → "Activa")—.
   */
  readonly opciones = input<OpcionFiltro[], readonly (OpcionFiltro | string)[]>([], {
    transform: normalizar,
  });

  readonly valor = model<string>('');

  protected alCambiar(evento: Event): void {
    this.valor.set((evento.target as HTMLSelectElement).value);
  }
}

export interface OpcionFiltro {
  valor: string;
  etiqueta: string;
}

function normalizar(opciones: readonly (OpcionFiltro | string)[]): OpcionFiltro[] {
  return opciones.map((opcion) =>
    typeof opcion === 'string' ? { valor: opcion, etiqueta: opcion } : opcion,
  );
}
