import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';

/**
 * Los ocho códigos de respaldo, con la **confirmación de guardado**.
 *
 * Es un componente compartido y no dos plantillas parecidas porque los dos
 * sitios que lo usan —el enrolamiento y la regeneración— tienen exactamente el
 * mismo problema: **los códigos se muestran una vez y no vuelven**. Duplicar
 * la pantalla es cómo acaba una de las dos sin la casilla de confirmación, que
 * es lo único que distingue "los vi pasar" de "los tengo guardados".
 *
 * La casilla no es burocracia: sin ella el botón de continuar se pulsa por
 * inercia y el usuario descubre que no los tiene el día que pierde el
 * teléfono, cuando ya no hay forma de recuperarlos.
 */
@Component({
  selector: 'cl-codigos-respaldo',
  templateUrl: './codigos-respaldo.html',
  styleUrl: './codigos-respaldo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CodigosRespaldo {
  readonly codigos = input.required<readonly string[]>();
  readonly etiquetaContinuar = input('Continuar');

  readonly continuar = output<void>();

  protected readonly guardados = signal(false);
  protected readonly copiado = signal(false);

  protected async copiar(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.codigos().join('\n'));
      this.copiado.set(true);
    } catch {
      // Sin portapapeles (contexto no seguro o permiso denegado) queda
      // seleccionar a mano; no vale un mensaje de error por esto.
      this.copiado.set(false);
    }
  }

  protected descargar(): void {
    const contenido = [
      'ControlLocal — códigos de respaldo del segundo factor',
      'Cada código sirve UNA sola vez. Guárdalos fuera de este dispositivo.',
      '',
      ...this.codigos(),
      '',
    ].join('\r\n');
    const url = URL.createObjectURL(new Blob([contenido], { type: 'text/plain;charset=utf-8' }));
    try {
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = 'controllocal-codigos-respaldo.txt';
      enlace.click();
    } finally {
      // El object URL vive hasta que se revoca; olvidarlo es una fuga silenciosa.
      URL.revokeObjectURL(url);
    }
  }

  protected alContinuar(): void {
    if (this.guardados()) {
      this.continuar.emit();
    }
  }

  protected marcar(evento: Event): void {
    this.guardados.set((evento.target as HTMLInputElement).checked);
  }
}
