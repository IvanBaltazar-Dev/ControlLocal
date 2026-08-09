import { Component, input, output } from '@angular/core';

/**
 * Estados estándar de todas las bandejas: carga, error recuperable y vacío.
 * La tabla solo se dibuja cuando este componente no tiene nada que mostrar.
 */
@Component({
  selector: 'cl-estado-listado',
  templateUrl: './estado-listado.html',
  styleUrl: './estado-listado.scss',
})
export class EstadoListado {
  readonly cargando = input(false);
  readonly error = input<string | null>(null);
  readonly vacio = input(false);
  readonly mensajeCarga = input('Cargando resultados…');
  readonly mensajeVacio = input('No hay resultados.');
  readonly reintentar = output<void>();
}
