import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import {
  ContrasteDelRenglon,
  RenglonExpediente,
  VentanaDelRenglon,
} from '../../../core/api/tareas.service';
import { textoDelContraste } from '../../../core/contraste';
import { Icono } from '../../../shared/icono/icono';

/**
 * **ANTECEDENTES** — una de las dos vistas del modo resolución del Radar.
 *
 * Cuatro renglones, no cuatro fechas. La `lectura` los sintetiza sin recitarlos
 * y viaja `null` cuando no hay nada que concluir: una lectura de relleno enseña
 * a no leerla, así que su ausencia se respeta.
 *
 * ## Lo que este componente NO hace
 *
 * No decide el estado de ningún renglón ni redacta el contraste. `estado`,
 * `ventana`, `serie` y `contraste` llegan resueltos por el dominio; aquí se
 * traducen a color, anchura y chispa. El texto del contraste sale de
 * `core/contraste`, la misma redacción que usa cualquier otra pantalla con
 * expediente.
 *
 * El anfitrión ES la tarjeta —lo viste `.radar-cuerpo > *` desde `radar.scss`—,
 * así que el componente no añade ningún nivel al DOM.
 */
@Component({
  selector: 'cl-radar-antecedentes',
  imports: [Icono],
  templateUrl: './radar-antecedentes.html',
  styleUrl: './radar-antecedentes.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RadarAntecedentes {
  readonly lectura = input<string | null>(null);
  readonly expediente = input<readonly RenglonExpediente[]>([]);
  /** La pantalla del expediente completo. Sin ella no se ofrece el pie. */
  readonly destino = input<string | null>(null);

  readonly abrir = output<string>();

  /** La misma redacción que usa cualquier otra pantalla con expediente. */
  protected textoDelContraste(contraste: ContrasteDelRenglon): string {
    return textoDelContraste(contraste);
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
}
