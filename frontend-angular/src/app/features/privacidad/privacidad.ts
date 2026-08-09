import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';

import { AvisoPrivacidad, AvisoPrivacidadService } from '../../core/api/aviso-privacidad.service';
import { comoFecha } from '../../core/formato';

/**
 * Formato largo ("5 de agosto de 2026"), que es como está redactado el aviso
 * corporativo. El `fechaCorta` del resto de la aplicación daría "05 ago. 2026",
 * correcto en una tabla y fuera de tono en un texto legal.
 */
const FECHA_LARGA = new Intl.DateTimeFormat('es-PE', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

/** Lo que se muestra si el API no responde: la página es pública y debe abrir igual. */
const VERSION_PUBLICADA = '1.0';
const VIGENCIA_PUBLICADA = '5 de agosto de 2026';

/**
 * Página pública **Privacidad y protección de datos** (D-27).
 *
 * Es **pública a propósito**: se lee **sin cuenta**, vive fuera del shell y el
 * enlace de la casilla de autorización apunta aquí.
 *
 * Dos reglas de contenido que conviene no volver a romper:
 *
 * 1. **No se publica el texto que el backend guarda como evidencia.** De la
 *    llamada solo se toman la **versión** y la **fecha de vigencia**, para que
 *    la cabecera no se desincronice del registro. El contenido de la página es
 *    el corporativo aprobado.
 * 2. **No hay revocación autoservicio**: ni pantalla, ni botón, ni endpoint
 *    público. Las solicitudes llegan al correo oficial y se atienden
 *    administrativamente. El versionado, `cambio_material`, actor, fecha, canal
 *    y tenant siguen registrándose **internamente**, que es donde deben estar.
 *
 * Si la llamada falla no se muestra ningún aviso de error: se publican la
 * versión y la fecha vigentes. Un banner rojo en una página pública asusta sin
 * aportar nada, y el contenido no depende del backend.
 */
@Component({
  selector: 'app-privacidad',
  templateUrl: './privacidad.html',
  styleUrl: './privacidad.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Privacidad implements OnInit {
  private readonly api = inject(AvisoPrivacidadService);

  private readonly aviso = signal<AvisoPrivacidad | null>(null);

  protected readonly version = computed(() => this.aviso()?.version ?? VERSION_PUBLICADA);
  protected readonly vigenteDesde = computed(() => {
    const desde = comoFecha(this.aviso()?.vigenteDesde);
    return desde ? FECHA_LARGA.format(desde) : VIGENCIA_PUBLICADA;
  });

  ngOnInit(): void {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    try {
      this.aviso.set(await this.api.vigente());
    } catch {
      // Degrada en silencio: la cabecera cae a la versión publicada.
    }
  }
}
