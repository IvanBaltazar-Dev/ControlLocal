import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Hallazgo } from '../../../core/api/dashboard.service';
import { Icono } from '../../../shared/icono/icono';

/**
 * Un hallazgo listo para dibujar.
 *
 * **Está agrupado por local**, no por par cliente-local. El motor evalúa cada
 * cliente contra cada captación, así que un local que encaja con doce clientes
 * produce doce hallazgos con el mismo título: en pantalla eso era la misma
 * dirección repetida doce veces, y con dos locales así el Radar se estiraba
 * hasta deformar la página. El hecho no son doce hallazgos: es **un local que
 * encaja con doce clientes**.
 */
export interface HallazgoEnRadar extends Hallazgo {
  /** «03» cuando el mismo registro ya ocupa un puesto del foco; `null` si no. */
  posicion: string | null;
  /** Cuántos clientes distintos encajan con este local. Nunca menor que 1. */
  clientes: number;
  /** Los de dentro del grupo, para el panel. Uno por cliente. */
  detalle: readonly Hallazgo[];
}

/**
 * **EL HALLAZGO DESTACADO** — la tarjeta alta del modo general del Radar.
 *
 * Solo existe cuando hay algo que destacar; no se rellena. Y **no reclama
 * nada**: una tarea dice «resuelve esto», un hallazgo dice «encontré algo que
 * vale la pena mirar». De ahí que su pie sea un enlace y no un botón de acción.
 *
 * El anfitrión ES la tarjeta: lo viste `.radar-cuerpo > *` desde `radar.scss`,
 * así que el componente no añade ningún nivel al DOM y las pruebas que buscan
 * `.hallazgo .c` siguen encontrándolo donde estaba.
 */
@Component({
  selector: 'cl-radar-hallazgo',
  imports: [Icono],
  templateUrl: './radar-hallazgo.html',
  styleUrl: './radar-hallazgo.scss',
  host: { class: 'hallazgo' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RadarHallazgo {
  readonly hallazgo = input.required<HallazgoEnRadar>();

  /** El hallazgo que ya está en el foco: lleva a su número, no abre otra cosa. */
  readonly irAlAsunto = output<string>();
  /** Abrir la coincidencia. Lo enruta el padre. */
  readonly abrir = output<string>();
  /** «Ver los N»: la lista entera vive en el panel, no aquí. */
  readonly verHallazgos = output<void>();
}
