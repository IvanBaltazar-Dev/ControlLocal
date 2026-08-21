import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  input,
  linkedSignal,
  output,
  signal,
  viewChild,
} from '@angular/core';

import { Tarea } from '../../../core/api/tareas.service';
import { rotuloDelLado, segmentosDe } from '../../../core/procesos';
import { Icono } from '../../../shared/icono/icono';
import { NucleoBrox } from '../../../shared/marca-brox/nucleo-brox';
import { AsuntoDelFoco } from '../asunto-del-foco';
import { RadarAntecedentes } from './radar-antecedentes';
import { RadarComoEsta } from './radar-como-esta';
import { HallazgoEnRadar, RadarHallazgo } from './radar-hallazgo';
import { RadarResolver } from './radar-resolver';

/** El halo de la superficie toma el tono del caso abierto. */
const AURA: Record<string, string> = { alta: 't-alta', media: 't-media', baja: '' };

/**
 * Cuántos hallazgos acompañan al destacado dentro del Radar.
 *
 * **Es layout, no política**: el Radar es una columna de alto acotado, y una
 * lista sin tope lo estira hasta romper la rejilla. El resto no se pierde — se
 * recorre en el panel.
 */
const EN_EL_RADAR = 3;

/**
 * **EL RADAR BROX** — la superficie de marca del Inicio (D-E2-1 §7).
 *
 * No es una tarjeta más del tablero: es el sitio donde BROX comprende y donde se
 * resuelve. Por eso tiene fondo, borde y cabecera propios, y por eso la fila del
 * foco **no lleva botón** — el botón vive aquí, y dos CTA iguales compiten.
 *
 * ## Dos modos, y solo dos
 *
 * - **General**, sin nada seleccionado: lo que BROX encontró.
 * - **Resolución**, con un asunto elegido: su identidad, su ruta en la cadena, y
 *   dos vistas —`Resolver` y `Antecedentes`— que se conmutan sin scrollear.
 *
 * ## Este componente es el armazón, no las tarjetas
 *
 * Aquí viven la superficie, la cabecera de los dos modos, el cuerpo, la ruta del
 * caso y el conmutador de vistas. Cada tarjeta del cuerpo es su propio
 * componente —`cl-radar-hallazgo`, `cl-radar-resolver`, `cl-radar-como-esta` y
 * `cl-radar-antecedentes`— y **el anfitrión de cada uno ES la tarjeta**: lo
 * viste `.radar-cuerpo > *` desde `radar.scss`, así que ninguno añade un nivel
 * al DOM ni pierde el fondo y la entrada escalonada.
 *
 * Lo que se queda aquí es lo que dos tarjetas comparten. `senalado` es el caso:
 * el botón que enciende el destello está en `Resolver` y los hechos que se
 * iluminan están en `Cómo está`, así que el estado no puede vivir en ninguna de
 * las dos.
 *
 * ## Lo que este componente NO hace
 *
 * No clasifica ni un hecho. El estado de cada uno (`HECHO`/`FALTA`/`PLAZO`/
 * `FRENO`/`DATO`), el expediente, la ventana, el contraste y la lectura llegan
 * decididos por el dominio; aquí solo se reparten entre las tarjetas. Deducir el
 * estado del tono del asunto devolvería el problema que D-E2-1 §10.1 cerró: un
 * asunto en rojo pintando de rojo también sus buenas noticias.
 */
@Component({
  selector: 'cl-radar',
  imports: [Icono, NucleoBrox, RadarAntecedentes, RadarComoEsta, RadarHallazgo, RadarResolver],
  templateUrl: './radar.html',
  styleUrl: './radar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Radar {
  /** El asunto abierto, o `null` para el modo general. */
  readonly asunto = input<AsuntoDelFoco | null>(null);
  readonly hallazgos = input<HallazgoEnRadar[]>([]);
  /** «Vigilando 18 operaciones abiertas». Lo compone quien tiene los números. */
  readonly vigila = input('');

  readonly volver = output<void>();
  /** El hallazgo que ya está en el foco: lleva a su número, no abre otra cosa. */
  readonly irAlAsunto = output<string>();
  /** Abrir el destino de un asunto o de un hallazgo. Lo enruta el padre. */
  readonly abrir = output<string>();
  /** «Ver los N»: la lista entera de hallazgos vive en el panel, no aquí. */
  readonly verHallazgos = output<void>();
  readonly resolver = output<AsuntoDelFoco>();
  readonly cancelar = output<Tarea>();
  /** Se resolvió en la ventana rápida: el padre recarga y lo dice. */
  readonly resuelto = output<string>();

  /** Vuelve a `Resolver` con cada asunto: la vista es del asunto, no del panel. */
  protected readonly vista = linkedSignal<AsuntoDelFoco | null, 'resolver' | 'antecedentes'>({
    source: this.asunto,
    computation: () => 'resolver',
  });

  /** El destello de atribución. Se apaga solo. */
  protected readonly senalado = signal(false);

  private readonly barra = viewChild<ElementRef<HTMLElement>>('vistas');

  constructor() {
    // El subrayado se MIDE, no se calcula: «Resolver» y «Antecedentes» no miden
    // lo mismo, y un ancho fijo dejaría el trazo corto en una de las dos.
    afterRenderEffect(() => {
      this.vista();
      this.asunto();
      this.medirPestanas();
    });
  }

  protected readonly clasesRadar = computed(() => {
    const tono = this.asunto()?.tono;
    return tono ? (AURA[tono] ?? '') : '';
  });

  protected readonly hechos = computed(() => this.asunto()?.interpretacion?.comoEsta?.hechos ?? []);
  protected readonly avance = computed(
    () => this.asunto()?.interpretacion?.comoEsta?.avance ?? null,
  );
  protected readonly expediente = computed(() => this.asunto()?.interpretacion?.expediente ?? []);
  protected readonly lectura = computed(() => this.asunto()?.interpretacion?.lectura ?? null);

  /**
   * `Antecedentes` solo existe cuando hay antecedentes.
   *
   * Una pestaña que abre sobre el vacío es peor que su ausencia: promete un
   * expediente que este asunto todavía no tiene (los del broker viajan sin él).
   */
  protected readonly hayAntecedentes = computed(
    () => this.expediente().length > 0 || this.lectura() !== null,
  );

  /** Qué falta y qué queda parado, en las palabras del dominio. */
  protected readonly queFalta = computed(
    () => this.hechos().find((h) => h.estado === 'FALTA')?.texto ?? null,
  );
  protected readonly queFrena = computed(
    () => this.hechos().find((h) => h.estado === 'FRENO')?.texto ?? null,
  );

  /**
   * Sobre cuántos hechos se apoya esto. **Se cuenta de lo que se enseña**, no de
   * un campo aparte: pulsarlo ilumina exactamente esos, así que el número y el
   * destello no pueden discrepar.
   */
  protected readonly senales = computed(
    () => this.hechos().length + this.expediente().filter((r) => r.estado !== null).length,
  );

  protected readonly primerHallazgo = computed(() => this.hallazgos()[0] ?? null);

  /**
   * Los que acompañan al destacado. **Con tope**, y el tope es de pantalla: el
   * Radar es una columna, no una lista sin fondo. Lo que no cabe se recorre en
   * el panel, que tiene su propio scroll y no estira la página.
   */
  protected readonly restoDeHallazgos = computed(() => this.hallazgos().slice(1, 1 + EN_EL_RADAR));

  protected readonly hallazgosOcultos = computed(() =>
    Math.max(0, this.hallazgos().length - 1 - EN_EL_RADAR),
  );

  protected readonly segmentos = computed(() => {
    const a = this.asunto();
    return a ? segmentosDe(a.lado, a.paso) : [];
  });

  protected readonly rotuloDelLado = computed(() => rotuloDelLado(this.asunto()?.lado));

  protected readonly claseDelLado = computed(() => {
    const lado = this.asunto()?.lado;
    return lado === 'OFERTA' ? 'l-oferta' : lado === 'DEMANDA' ? 'l-demanda' : '';
  });

  protected cambiarVista(vista: 'resolver' | 'antecedentes'): void {
    this.vista.set(vista);
  }

  /**
   * Ilumina los hechos que sostienen lo que se está diciendo.
   *
   * Lo pide `Resolver` y lo pinta `Cómo está`: por eso el estado vive aquí y no
   * dentro de ninguna de las dos tarjetas.
   */
  protected senalar(): void {
    this.senalado.set(false);
    // Un fotograma de por medio: reasignar la clase sin soltarla no reinicia la
    // animación, y pulsar dos veces seguidas no haría nada.
    requestAnimationFrame(() => {
      this.senalado.set(true);
      setTimeout(() => this.senalado.set(false), 1600);
    });
  }

  private medirPestanas(): void {
    const barra = this.barra()?.nativeElement;
    const activa = barra?.querySelector<HTMLElement>('button.on');
    if (!barra || !activa) {
      return;
    }
    barra.style.setProperty('--w', `${activa.offsetWidth}px`);
    barra.style.setProperty('--x', `${activa.offsetLeft - 3}px`);
  }
}
