import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';

import { Alerta, AlertasService } from '../../core/api/alertas.service';
import { ApiError } from '../../core/api/api.types';
import { fechaHora } from '../../core/formato';
import { NavegacionLegado } from '../../core/navegacion-legado';

/** Cuántas alertas se traen de golpe: el defecto del recurso. */
const TAMANO = 20;

/**
 * La campana. Porta el `NotificacionStore` + campana del Topbar del Blazor,
 * pero **sin store global**: allí era un Singleton con vista por usuario
 * porque el estado vivía en el servidor; aquí las alertas son un recurso REST y
 * el componente las pide.
 *
 * Cuatro cosas del contrato F6 que explican cómo se comporta:
 *
 * - **`GET /alertas` solo devuelve las ACTIVAS**, ya ordenadas por fecha. El
 *   contador es su `totalRecords`; no hay que filtrar por estado en el cliente.
 * - **Ese GET escribe**: materializa el barrido de recontacto vencido, y lo
 *   hace como mucho una vez cada 5 minutos. Por eso se pide al entrar y al
 *   abrir, sin sondeo periódico: sondear no adelantaría ningún aviso, solo
 *   gastaría llamadas.
 * - **No hay destinatario en la tabla.** La alerta cuelga siempre de un AGENTE
 *   y el broker la ve por la supervisión, así que se muestra de quién es
 *   cuando el nombre viaja — para quien supervisa, es la mitad del dato.
 * - **`ruta` puede faltar** (la v1 no enruta algunos tipos, D-F6-4). Sin ella
 *   el aviso se lee y se atiende, pero no navega: no se inventa un destino.
 *
 * Atender comprueba **visibilidad, no propiedad**: un broker puede atender las
 * de su equipo. Si el backend responde `atendida: false` es que ya estaba
 * atendida, y la fila se retira igual porque ya no está activa.
 */
@Component({
  selector: 'cl-campana',
  templateUrl: './campana-alertas.html',
  styleUrl: './campana-alertas.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:click)': 'alClicFuera($event)',
    '(document:keydown.escape)': 'cerrar()',
  },
})
export class CampanaAlertas implements OnInit {
  private readonly api = inject(AlertasService);
  private readonly navegacion = inject(NavegacionLegado);
  private readonly anfitrion = inject(ElementRef<HTMLElement>);

  protected readonly abierta = signal(false);
  protected readonly alertas = signal<Alerta[]>([]);
  protected readonly total = signal(0);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly atendiendo = signal<number | null>(null);

  /** Lo que se pinta en el globo. Más de 9 se rotula `9+`. */
  protected readonly insignia = computed(() => (this.total() > 9 ? '9+' : String(this.total())));

  protected readonly hayMas = computed(() => this.total() > this.alertas().length);

  ngOnInit(): void {
    void this.cargar();
  }

  protected alternar(): void {
    const abriendo = !this.abierta();
    this.abierta.set(abriendo);
    if (abriendo) {
      void this.cargar();
    }
  }

  protected cerrar(): void {
    this.abierta.set(false);
  }

  protected alClicFuera(evento: Event): void {
    if (!this.abierta()) {
      return;
    }
    const destino = evento.target as Node | null;
    if (destino && !this.anfitrion.nativeElement.contains(destino)) {
      this.cerrar();
    }
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const pagina = await this.api.pagina(1, TAMANO);
      this.alertas.set(pagina.items ?? []);
      this.total.set(pagina.totalRecords ?? 0);
    } catch (fallo) {
      // La campana es chrome: si falla, no rompe la pantalla que hay debajo.
      // Se dice dentro del panel y el contador queda como estaba.
      this.error.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudieron cargar los avisos.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  protected puedeAbrir(alerta: Alerta): boolean {
    return this.navegacion.puedeAbrir(alerta.ruta);
  }

  protected async abrir(alerta: Alerta): Promise<void> {
    const navegada = await this.navegacion.abrir(alerta.ruta);
    if (navegada) {
      this.cerrar();
    } else {
      this.error.set('Ese aviso no tiene una pantalla a la que llevarte.');
    }
  }

  protected async atender(alerta: Alerta): Promise<void> {
    this.atendiendo.set(alerta.id);
    this.error.set(null);
    try {
      await this.api.atender(alerta.id);
      // Se retira de la lista aunque el backend responda `false`: ese `false`
      // significa que ya estaba atendida, o sea que tampoco sigue activa.
      this.alertas.update((lista) => lista.filter((a) => a.id !== alerta.id));
      this.total.update((valor) => Math.max(0, valor - 1));
    } catch (fallo) {
      this.error.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudo marcar el aviso como atendido.',
      );
    } finally {
      this.atendiendo.set(null);
    }
  }

  protected tono(severidad: string): string {
    if (severidad === 'ALTA') return 'mal';
    if (severidad === 'MEDIA') return 'aviso';
    return '';
  }

  protected cuando(alerta: Alerta): string {
    return fechaHora(alerta.fechaGeneracion);
  }
}
