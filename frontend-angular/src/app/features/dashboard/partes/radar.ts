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

import { Hallazgo } from '../../../core/api/dashboard.service';
import {
  ContrasteDelRenglon,
  EstadoDelHecho,
  RenglonExpediente,
  Tarea,
  VentanaDelRenglon,
} from '../../../core/api/tareas.service';
import { textoDelContraste } from '../../../core/contraste';
import { rotuloDelLado, segmentosDe } from '../../../core/procesos';
import { Icono, NombreIcono } from '../../../shared/icono/icono';
import { MallaBrox } from '../../../shared/marca-brox/malla-brox';
import { NucleoBrox } from '../../../shared/marca-brox/nucleo-brox';
import { AsuntoDelFoco } from '../asunto-del-foco';
import { AccionRapida } from './accion-rapida';

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

/** La marca de cada estado. La pone el dominio; aquí solo se elige el símbolo. */
const MARCA: Record<EstadoDelHecho, NombreIcono> = {
  HECHO: 'check',
  FALTA: 'circulo',
  PLAZO: 'reloj',
  FRENO: 'freno',
  DATO: 'raya',
};

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
 * ## Lo que este componente NO hace
 *
 * No clasifica ni un hecho. El estado de cada uno (`HECHO`/`FALTA`/`PLAZO`/
 * `FRENO`/`DATO`), el expediente, la ventana, el contraste y la lectura llegan
 * decididos por el dominio; aquí se traducen a marca, color y anchura. Deducir
 * el estado del tono del asunto devolvería el problema que D-E2-1 §10.1 cerró:
 * un asunto en rojo pintando de rojo también sus buenas noticias.
 *
 * ## Lo que el cable todavía no trae
 *
 * D-E2-1 dibuja una **recomendación** («qué hacer» + «para qué») redactada por
 * el dominio. `GET /dashboard` no la emite. En vez de escribirla aquí —que sería
 * inventar el criterio de BROX en el cliente— se usan las frases que el dominio
 * **sí** escribe: el hecho `FALTA` dice qué falta y el hecho `FRENO` dice qué
 * queda parado mientras tanto. Cuando el cable traiga la recomendación, este
 * bloque la muestra y estas dos líneas se retiran.
 */
@Component({
  selector: 'cl-radar',
  imports: [AccionRapida, Icono, MallaBrox, NucleoBrox],
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
  protected readonly ejecutando = signal<'quieto' | 'trabajando' | 'listo'>('quieto');

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
  /** Tres viñetas, sin párrafos: es el tope que fija D-E2-1 §10.1. */
  protected readonly hechosVisibles = computed(() => this.hechos().slice(0, 3));
  protected readonly avance = computed(() => this.asunto()?.interpretacion?.comoEsta?.avance ?? null);
  /** Un segmento por requisito, encendido o no. Sin `avance`, ninguno. */
  protected readonly barraDeAvance = computed(() => {
    const av = this.avance();
    return av ? Array.from({ length: av.total }, (_, i) => i < av.hechos) : [];
  });
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
  protected readonly restoDeHallazgos = computed(() =>
    this.hallazgos().slice(1, 1 + EN_EL_RADAR),
  );

  protected readonly hallazgosOcultos = computed(() =>
    Math.max(0, this.hallazgos().length - 1 - EN_EL_RADAR),
  );

  /** Cuántos locales encajaron, y con cuántos clientes en total. */
  protected readonly clientesQueEncajan = computed(() =>
    this.hallazgos().reduce((total, h) => total + h.clientes, 0),
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

  /** La misma redacción que usa cualquier otra pantalla con expediente. */
  protected textoDelContraste(contraste: ContrasteDelRenglon): string {
    return textoDelContraste(contraste);
  }

  protected marcaDe(estado: EstadoDelHecho): NombreIcono {
    return MARCA[estado] ?? 'raya';
  }

  protected claseDelHecho(estado: EstadoDelHecho): string {
    return estado.toLowerCase();
  }

  protected claseDelRenglon(renglon: RenglonExpediente): string {
    switch (renglon.estado) {
      case 'BIEN':
        return 'e-bien';
      case 'OJO':
        return 'e-ojo';
      case 'MAL':
        return 'e-mal';
      default:
        return '';
    }
  }

  protected cambiarVista(vista: 'resolver' | 'antecedentes'): void {
    this.vista.set(vista);
  }

  /** Ilumina los hechos que sostienen lo que se está diciendo. */
  protected senalar(): void {
    this.senalado.set(false);
    // Un fotograma de por medio: reasignar la clase sin soltarla no reinicia la
    // animación, y pulsar dos veces seguidas no haría nada.
    requestAnimationFrame(() => {
      this.senalado.set(true);
      setTimeout(() => this.senalado.set(false), 1600);
    });
  }

  protected ejecutar(): void {
    const asunto = this.asunto();
    if (!asunto || this.ejecutando() !== 'quieto') {
      return;
    }
    this.ejecutando.set('trabajando');
    this.resolver.emit(asunto);
    // La confirmación dura lo justo: si la navegación prospera, la pantalla ya
    // no está; si no, el botón vuelve a estar disponible en vez de quedarse
    // girando para siempre.
    setTimeout(() => {
      this.ejecutando.set('listo');
      setTimeout(() => this.ejecutando.set('quieto'), 900);
    }, 420);
  }

  /**
   * La proporción consumida de una ventana, acotada.
   *
   * Se acota porque pasarse del plazo es un hecho, no una barra que se sale de
   * su riel; el exceso lo dice el rótulo, que es donde se puede leer.
   */
  protected anchoDeVentana(v: VentanaDelRenglon): number {
    return v.total > 0 ? Math.min(100, Math.round((v.consumido * 100) / v.total)) : 0;
  }

  /** `168/180`, o `+12` cuando ya se pasó. Una barra sin cifra es un adorno. */
  protected rotuloDeVentana(v: VentanaDelRenglon): string {
    const exceso = v.consumido - v.total;
    return exceso > 0 ? `+${exceso}` : `${v.consumido}/${v.total}`;
  }

  protected ventanaPasada(v: VentanaDelRenglon): boolean {
    return v.consumido > v.total;
  }

  /**
   * La chispa de la serie, normalizada a su propia caja.
   *
   * Sale del histórico económico (E0): son hitos reales de renta, no una
   * tendencia dibujada. Con menos de dos puntos no hay línea que trazar.
   */
  protected puntosDeSerie(serie: number[]): string {
    if (serie.length < 2) {
      return '';
    }
    const min = Math.min(...serie);
    const max = Math.max(...serie);
    const rango = max - min || 1;
    return serie
      .map((v, i) => {
        const x = (1 + i * (46 / (serie.length - 1))).toFixed(1);
        const y = (12 - ((v - min) / rango) * 9.5).toFixed(1);
        return `${x},${y}`;
      })
      .join(' ');
  }

  protected ultimoY(serie: number[]): string {
    if (serie.length < 2) {
      return '7';
    }
    const min = Math.min(...serie);
    const max = Math.max(...serie);
    const rango = max - min || 1;
    return (12 - ((serie[serie.length - 1] - min) / rango) * 9.5).toFixed(1);
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
