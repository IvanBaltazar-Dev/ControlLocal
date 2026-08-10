import {
  Component,
  effect,
  ElementRef,
  inject,
  input,
  OnDestroy,
  output,
  viewChild,
} from '@angular/core';

/**
 * Panel lateral deslizante: una lista larga que se recorre sin sacar a nadie de
 * la pantalla en la que está.
 *
 * **Por qué existe y no basta con `cl-confirmacion`.** El diálogo de
 * confirmación es un cuadro centrado de 30rem para *una* decisión. Esto es lo
 * contrario: una columna alta con su propio scroll para *recorrer* muchas filas
 * (la bandeja del agente, que desde que se retiró el tope de 10 puede traer 30 o
 * 50). Esa lista en el flujo de la página estira la tarjeta y descuadra la
 * rejilla de dos columnas; en un cuadro centrado, cabrían cuatro filas.
 *
 * Tres decisiones que hay que respetar al usarlo:
 *
 * - **No cambia de ruta.** Cerrar devuelve la pantalla intacta, sin recargarla.
 *   Importa donde la lectura escribe (`GET /tareas` reconcilia): navegar y
 *   volver costaría una reconciliación por cada vistazo.
 * - **El scroll es del panel, no del documento.** Mientras está abierto el
 *   `body` queda bloqueado, o la rueda del ratón mueve la página de detrás.
 * - **ESC escucha en el documento**, no en el fondo. Atarlo al `div` solo
 *   funciona si el foco cayó dentro, y el foco puede estar en cualquier sitio
 *   cuando se abre.
 */
@Component({
  selector: 'cl-panel-lateral',
  templateUrl: './panel-lateral.html',
  styleUrl: './panel-lateral.scss',
  host: {
    '(document:keydown.escape)': 'alCerrar()',
  },
})
export class PanelLateral implements OnDestroy {
  readonly abierto = input(false);
  readonly titulo = input.required<string>();
  readonly subtitulo = input<string>();
  /** Ancho del panel. Se acota al viewport, así que en móvil ocupa todo. */
  readonly ancho = input('36rem');

  readonly cerrar = output<void>();

  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  private readonly host = inject(ElementRef<HTMLElement>);

  /** Valor previo de `overflow` para no pisar el que traiga la página. */
  private overflowPrevio: string | null = null;
  /** A dónde devolver el foco al cerrar: quien abrió el panel. */
  private origenFoco: HTMLElement | null = null;

  constructor() {
    effect(() => (this.abierto() ? this.bloquearFondo() : this.liberarFondo()));

    // El foco entra cuando el nodo YA existe. `panel()` es una señal, así que
    // este efecto se vuelve a disparar solo en cuanto la vista lo crea: no hace
    // falta un `setTimeout` para esperar al pintado.
    effect(() => {
      if (this.abierto()) {
        this.panel()?.nativeElement.focus();
      }
    });
  }

  ngOnDestroy(): void {
    // Un panel destruido con el `body` bloqueado deja la página sin scroll para
    // siempre. Pasa de verdad: "Resolver" navega fuera con el panel abierto.
    this.liberarFondo();
  }

  protected alCerrar(): void {
    if (this.abierto()) {
      this.cerrar.emit();
    }
  }

  private bloquearFondo(): void {
    const cuerpo = this.documento()?.body;
    if (!cuerpo || this.overflowPrevio !== null) {
      return;
    }
    this.overflowPrevio = cuerpo.style.overflow;
    cuerpo.style.overflow = 'hidden';
    const activo = this.documento()?.activeElement;
    this.origenFoco = activo instanceof HTMLElement ? activo : null;
  }

  private liberarFondo(): void {
    const cuerpo = this.documento()?.body;
    if (!cuerpo || this.overflowPrevio === null) {
      return;
    }
    cuerpo.style.overflow = this.overflowPrevio;
    this.overflowPrevio = null;
    this.origenFoco?.focus();
    this.origenFoco = null;
  }

  private documento(): Document | null {
    return this.host.nativeElement.ownerDocument ?? null;
  }
}
