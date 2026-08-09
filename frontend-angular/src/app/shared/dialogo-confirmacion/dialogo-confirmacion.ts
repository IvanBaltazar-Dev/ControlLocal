import { Component, input, output } from '@angular/core';

/** Tono de la acción: define el color del botón que confirma. */
export type TonoAccion = 'azul' | 'verde' | 'ambar' | 'rojo' | 'gris';

/**
 * Confirmación de una acción que no se deshace: aprobar, observar, rechazar,
 * cerrar una captación, reasignar. Porta `ConfirmDialog.razor`.
 *
 * Dos estados que hay que distinguir y que en el legado se confundían:
 * - `ocupado`: la petición está en vuelo. El botón dice "Procesando…" y no se
 *   puede pulsar dos veces — el doble clic sobre un POST de decisión fue un
 *   bug real del Blazor.
 * - `bloqueado`: la regla de negocio no deja confirmar todavía (p. ej.
 *   observar exige escribir la observación). El botón está apagado, pero sin
 *   fingir que hay trabajo en curso.
 */
@Component({
  selector: 'cl-confirmacion',
  templateUrl: './dialogo-confirmacion.html',
  styleUrl: './dialogo-confirmacion.scss',
})
export class DialogoConfirmacion {
  readonly abierto = input(false);
  readonly titulo = input.required<string>();
  readonly descripcion = input<string>();
  readonly tono = input<TonoAccion>('azul');
  readonly etiquetaConfirmar = input('Confirmar');
  readonly etiquetaCancelar = input('Cancelar');
  readonly ocupado = input(false);
  readonly bloqueado = input(false);

  readonly confirmar = output<void>();
  readonly cerrar = output<void>();

  protected alConfirmar(): void {
    if (this.ocupado() || this.bloqueado()) {
      return;
    }
    this.confirmar.emit();
  }

  /** Cerrar con Escape o pulsando el fondo, salvo mientras hay algo en vuelo. */
  protected alCerrar(): void {
    if (!this.ocupado()) {
      this.cerrar.emit();
    }
  }
}
