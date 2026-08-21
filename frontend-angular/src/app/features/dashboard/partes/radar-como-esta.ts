import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import {
  AvanceDelAsunto,
  EstadoDelHecho,
  HechoDelAsunto,
} from '../../../core/api/tareas.service';
import { Icono, NombreIcono } from '../../../shared/icono/icono';

/** La marca de cada estado. La pone el dominio; aquí solo se elige el símbolo. */
const MARCA: Record<EstadoDelHecho, NombreIcono> = {
  HECHO: 'check',
  FALTA: 'circulo',
  PLAZO: 'reloj',
  FRENO: 'freno',
  DATO: 'raya',
};

/**
 * **CÓMO ESTÁ** — cada hecho con SU estado, no con el del asunto.
 *
 * La marca y el color salen de `hecho.estado`, que decide el dominio: en el
 * mismo asunto conviven un ✓ verde y un ⊘ rojo (D-E2-1 §10.1). Deducir el
 * estado del tono del asunto devolvería el problema que esa decisión cerró: un
 * asunto en rojo pintando de rojo también sus buenas noticias.
 *
 * `proximo` a `null` **se dice**, no se esconde: muchas veces es el dato
 * importante.
 *
 * El anfitrión ES la tarjeta —lo viste `.radar-cuerpo > *` desde `radar.scss`—,
 * así que el componente no añade ningún nivel al DOM.
 */
@Component({
  selector: 'cl-radar-como-esta',
  imports: [Icono],
  templateUrl: './radar-como-esta.html',
  styleUrl: './radar-como-esta.scss',
  host: { class: 'ahora' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RadarComoEsta {
  readonly hechos = input<readonly HechoDelAsunto[]>([]);
  readonly avance = input<AvanceDelAsunto | null>(null);
  readonly proximo = input<string | null>(null);
  /** El destello de atribución. Lo enciende el botón de la tarjeta de al lado. */
  readonly senalado = input(false);

  /** Tres viñetas, sin párrafos: es el tope que fija D-E2-1 §10.1. */
  protected readonly hechosVisibles = computed(() => this.hechos().slice(0, 3));

  /** Un segmento por requisito, encendido o no. Sin `avance`, ninguno. */
  protected readonly barraDeAvance = computed(() => {
    const av = this.avance();
    return av ? Array.from({ length: av.total }, (_, i) => i < av.hechos) : [];
  });

  protected marcaDe(estado: EstadoDelHecho): NombreIcono {
    return MARCA[estado] ?? 'raya';
  }

  protected claseDelHecho(estado: EstadoDelHecho): string {
    return estado.toLowerCase();
  }
}
