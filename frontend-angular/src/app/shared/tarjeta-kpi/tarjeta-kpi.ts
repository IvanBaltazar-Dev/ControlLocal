import { Component, input, output } from '@angular/core';

/** Temperatura del indicador: verde positivo, ámbar precaución, rojo negativo. */
export type TonoKpi = 'azul' | 'verde' | 'ambar' | 'rojo' | 'gris' | 'info';

/**
 * Tarjeta de indicador. Porta `MetricCard.razor`, y sobre todo su rasgo
 * importante: **el KPI es clicable y actúa como atajo de filtro**.
 *
 * Esa es la convención del legado —pulsar "Pendientes" filtra la lista a
 * pendientes y la tarjeta queda resaltada—, y es lo que evita duplicar los
 * mismos números en una fila de tarjetas y en un select.
 *
 * `clicable` es explícito y no se deduce de si hay `(pulsar)`: a diferencia
 * del `EventCallback.HasDelegate` de Blazor, el `output()` de Angular no
 * expone si el padre se suscribió. Declararlo es además más claro — hay
 * tarjetas que son solo dato y no deben invitar al clic.
 */
@Component({
  selector: 'cl-kpi',
  templateUrl: './tarjeta-kpi.html',
  styleUrl: './tarjeta-kpi.scss',
  host: {
    '[class]': '"tono-" + tono()',
    '[class.clicable]': 'clicable()',
    '[class.activa]': 'activa()',
    '[attr.role]': 'clicable() ? "button" : null',
    '[attr.tabindex]': 'clicable() ? 0 : null',
    '[attr.aria-pressed]': 'clicable() ? activa() : null',
    '(click)': 'alPulsar()',
    '(keydown.enter)': 'alPulsar($event)',
    '(keydown.space)': 'alPulsar($event)',
  },
})
export class TarjetaKpi {
  readonly etiqueta = input.required<string>();
  readonly valor = input.required<string | number>();
  readonly tono = input<TonoKpi>('azul');
  /** Nota al pie: el "de qué" del número (periodo, alcance…). */
  readonly pie = input<string>();
  /** Actúa como atajo de filtro. Sin esto la tarjeta es solo dato. */
  readonly clicable = input(false);
  /** Resalta la tarjeta cuando su filtro está aplicado. */
  readonly activa = input(false);

  readonly pulsar = output<void>();

  protected alPulsar(evento?: Event): void {
    if (!this.clicable()) {
      return;
    }
    // La barra espaciadora hace scroll de la página si no se corta.
    evento?.preventDefault();
    this.pulsar.emit();
  }
}
