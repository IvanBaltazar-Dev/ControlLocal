import { Component, effect, input, model, OnDestroy, output, signal } from '@angular/core';

/**
 * Barra de filtros estándar de las listas. Porta la convención del Blazor
 * (`FilterBar.razor`): búsqueda a la izquierda, filtros secundarios
 * proyectados, "Limpiar" solo cuando hay algo que limpiar, y el contador de
 * resultados a la derecha.
 *
 * La regla que hay que respetar al usarla: **todo sale de los datos**. Ni un
 * `<option>` escrito a mano ni un contador fijo — esa fue la lección del
 * legado, donde los selects hardcodeados se desincronizaban del catálogo real.
 *
 * <b>Sobre el antirebote</b>: `debounceMs = 0` (inmediato) sirve cuando el
 * filtrado es en memoria. En listas que paginan en el servidor hay que poner
 * 250–350 ms, o se llama al API en cada tecla.
 */
@Component({
  selector: 'cl-barra-filtros',
  templateUrl: './barra-filtros.html',
  styleUrl: './barra-filtros.scss',
})
export class BarraFiltros implements OnDestroy {
  /** Texto de búsqueda ya "comprometido" (después del antirebote). */
  readonly busqueda = model<string>('');

  readonly marcador = input('Buscar…');
  /** Nº de filas que quedan tras filtrar. `null` = no mostrar contador. */
  readonly resultados = input<number | null>(null);
  /** Habilita el botón "Limpiar". Lo decide la pantalla: solo ella sabe cuántos filtros tiene. */
  readonly hayFiltros = input(false);
  readonly debounceMs = input(0);

  readonly limpiar = output<void>();

  /**
   * Lo que se ve en la caja, actualizado en cada tecla. Va aparte de
   * `busqueda` a propósito: si el input se atara al valor con antirebote, el
   * texto se quedaría atrás y el cursor saltaría al final mientras se escribe.
   */
  protected readonly texto = signal('');

  private temporizador?: ReturnType<typeof setTimeout>;

  constructor() {
    // Sincroniza la caja cuando el valor cambia DESDE FUERA (p. ej. "Limpiar"
    // o un enlace con la búsqueda precargada).
    effect(() => {
      const valor = this.busqueda();
      if (valor !== this.texto()) {
        this.texto.set(valor);
      }
    });
  }

  protected alEscribir(evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.texto.set(valor);

    clearTimeout(this.temporizador);
    if (this.debounceMs() <= 0) {
      this.busqueda.set(valor);
      return;
    }
    this.temporizador = setTimeout(() => this.busqueda.set(valor), this.debounceMs());
  }

  protected alLimpiar(): void {
    clearTimeout(this.temporizador);
    this.texto.set('');
    // El padre limpia TODOS los filtros en una sola escritura de URL. Emitir
    // tambien `busquedaChange` aqui produciria dos navegaciones y dos cargas.
    this.limpiar.emit();
  }

  /** Sin esto, un antirebote pendiente escribe en un componente ya destruido. */
  ngOnDestroy(): void {
    clearTimeout(this.temporizador);
  }
}
