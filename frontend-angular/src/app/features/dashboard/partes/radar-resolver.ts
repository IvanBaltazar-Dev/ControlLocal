import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';

import { Tarea } from '../../../core/api/tareas.service';
import { Icono } from '../../../shared/icono/icono';
import { MallaBrox } from '../../../shared/marca-brox/malla-brox';
import { AsuntoDelFoco } from '../asunto-del-foco';
import { AccionRapida } from './accion-rapida';

/**
 * **QUÉ HACER AHORA** — la vista por defecto del modo resolución del Radar.
 *
 * ## Lo que el cable todavía no trae
 *
 * D-E2-1 dibuja una **recomendación** («qué hacer» + «para qué») redactada por
 * el dominio. `GET /dashboard` no la emite. En vez de escribirla aquí —que
 * sería inventar el criterio de BROX en el cliente— se usan las frases que el
 * dominio **sí** escribe: el hecho `FALTA` dice qué falta y el hecho `FRENO`
 * dice qué queda parado mientras tanto. Cuando el cable traiga la
 * recomendación, este bloque la muestra y estas dos líneas se retiran.
 *
 * ## Por qué `senalar` sale y `ejecutando` se queda
 *
 * El destello de atribución ilumina los hechos, que se pintan en la tarjeta de
 * al lado: por eso el estado del destello vive en el Radar y aquí solo se
 * emite el evento. El estado del botón, en cambio, no sale de esta tarjeta.
 *
 * El anfitrión ES la tarjeta —lo viste `.radar-cuerpo > *` desde `radar.scss`—,
 * así que el componente no añade ningún nivel al DOM.
 */
@Component({
  selector: 'cl-radar-resolver',
  imports: [AccionRapida, Icono, MallaBrox],
  templateUrl: './radar-resolver.html',
  styleUrl: './radar-resolver.scss',
  host: { class: 'reco' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RadarResolver {
  readonly asunto = input.required<AsuntoDelFoco>();
  /** Sobre cuántos hechos se apoya esto. Lo cuenta quien los tiene. */
  readonly senales = input(0);
  /** Qué falta y qué queda parado, en las palabras del dominio. */
  readonly queFalta = input<string | null>(null);
  readonly queFrena = input<string | null>(null);

  /** Ilumina los hechos que sostienen lo que se está diciendo. */
  readonly senalar = output<void>();
  readonly resolver = output<AsuntoDelFoco>();
  readonly cancelar = output<Tarea>();
  /** Se resolvió en la ventana rápida: el padre recarga y lo dice. */
  readonly resuelto = output<string>();

  protected readonly ejecutando = signal<'quieto' | 'trabajando' | 'listo'>('quieto');

  protected ejecutar(): void {
    if (this.ejecutando() !== 'quieto') {
      return;
    }
    this.ejecutando.set('trabajando');
    this.resolver.emit(this.asunto());
    // La confirmación dura lo justo: si la navegación prospera, la pantalla ya
    // no está; si no, el botón vuelve a estar disponible en vez de quedarse
    // girando para siempre.
    setTimeout(() => {
      this.ejecutando.set('listo');
      setTimeout(() => this.ejecutando.set('quieto'), 900);
    }, 420);
  }
}
