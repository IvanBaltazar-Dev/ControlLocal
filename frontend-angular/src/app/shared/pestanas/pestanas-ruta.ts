import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** Una pestaña que es una ruta: rótulo y destino. */
export interface PestanaRuta {
  readonly etiqueta: string;
  /** Ruta absoluta, con la barra inicial. */
  readonly ruta: string;
}

/**
 * **Pestañas que son rutas.**
 *
 * Existe para las superficies que son **una sola página con dos caras** y no
 * dos secciones: Indicadores (rendimiento por actor) y su avance por propiedad
 * (RF-017). D-E3-1 §4.3 lo pedía así —«pestaña dentro de Indicadores»— y
 * mantenerlas como dos entradas de menú era justo lo que la auditoría vino a
 * quitar.
 *
 * Son enlaces y no botones a propósito: cada cara conserva su URL, así que un
 * enlace guardado sigue abriendo la que se guardó y el navegador puede volver.
 * El estado activo lo pone `routerLinkActive`, no un `@if` de la pantalla.
 */
@Component({
  selector: 'cl-pestanas-ruta',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="cl-pestanas" [attr.aria-label]="etiqueta()">
      @for (pestana of pestanas(); track pestana.ruta) {
        <a
          [routerLink]="pestana.ruta"
          routerLinkActive="activa"
          [routerLinkActiveOptions]="{ exact: true }"
          #activa="routerLinkActive"
          [attr.aria-current]="activa.isActive ? 'page' : null"
        >
          {{ pestana.etiqueta }}
        </a>
      }
    </nav>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
})
export class PestanasRuta {
  readonly pestanas = input.required<readonly PestanaRuta[]>();
  /** Lo que el lector de pantalla anuncia al entrar en el grupo. */
  readonly etiqueta = input('Vistas de la página');
}
