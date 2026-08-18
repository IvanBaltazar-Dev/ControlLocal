import { Component, computed, input, model } from '@angular/core';
import { RESULTADOS_POR_PAGINA } from './tamano-pagina';

/**
 * Paginación de las listas: "Mostrando X–Y de Z" más la ventana de páginas
 * con elipsis. Porta `Pagination.razor` del Blazor, incluida su regla de
 * ventana (hasta 5 páginas se listan todas; a partir de ahí, primera, última
 * y las vecinas de la actual).
 *
 * <b>La página es 1-based</b>, como el `PageResponse` del contrato congelado
 * — no 0-based como suele ser en Angular—. Confundirlo es el error clásico:
 * se pide la página 0 y el backend devuelve la primera igual, así que no
 * falla, solo se salta una página al navegar.
 */
@Component({
  selector: 'cl-paginacion',
  templateUrl: './paginacion.html',
  styleUrl: './paginacion.scss',
})
export class Paginacion {
  readonly total = input.required<number>();
  readonly tamano = input(RESULTADOS_POR_PAGINA);
  /** Página actual, 1-based. */
  readonly pagina = model(1);

  protected readonly ultima = computed(() =>
    Math.max(1, Math.ceil(this.total() / Math.max(1, this.tamano()))),
  );
  protected readonly actual = computed(() =>
    Math.min(Math.max(1, this.pagina()), this.ultima()),
  );
  protected readonly desde = computed(() =>
    this.total() === 0 ? 0 : (this.actual() - 1) * this.tamano() + 1,
  );
  protected readonly hasta = computed(() =>
    Math.min(this.actual() * this.tamano(), this.total()),
  );

  /** Números a dibujar; `null` es una elipsis, no una página. */
  protected readonly paginas = computed<(number | null)[]>(() => ventana(this.actual(), this.ultima()));

  protected ir(destino: number): void {
    const valida = Math.min(Math.max(1, destino), this.ultima());
    if (valida !== this.pagina()) {
      this.pagina.set(valida);
    }
  }
}

/**
 * Ventana de páginas alrededor de la actual. Con 5 o menos, todas; si no,
 * primera + vecinas + última, rellenando con elipsis solo cuando hay hueco
 * real (si el hueco es de una sola página, se dibuja esa página en vez de
 * los puntos: ocupa lo mismo y es un clic menos).
 */
function ventana(actual: number, ultima: number): (number | null)[] {
  if (ultima <= 5) {
    return Array.from({ length: ultima }, (_, i) => i + 1);
  }

  let inicio = actual - 1;
  let fin = actual + 1;
  if (actual === 1) {
    fin = 3;
  } else if (actual === ultima) {
    inicio = ultima - 2;
  }
  inicio = Math.max(2, inicio);
  fin = Math.min(ultima - 1, fin);

  const paginas: (number | null)[] = [1];
  if (inicio === 3) {
    paginas.push(2);
  } else if (inicio > 3) {
    paginas.push(null);
  }
  for (let p = inicio; p <= fin; p++) {
    paginas.push(p);
  }
  if (fin === ultima - 2) {
    paginas.push(ultima - 1);
  } else if (fin < ultima - 2) {
    paginas.push(null);
  }
  paginas.push(ultima);
  return paginas;
}
