import {
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { DocumentosService } from '../../core/api/documentos.service';

@Component({
  selector: 'cl-visor-documento',
  templateUrl: './visor-documento.html',
  styleUrl: './visor-documento.scss',
})
export class VisorDocumento {
  private readonly documentos = inject(DocumentosService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly reintento = signal(0);

  readonly clave = input<string | null>(null);
  readonly nombreArchivo = input('documento');
  readonly alto = input('65vh');

  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly url = signal<string | null>(null);
  protected readonly tipoContenido = signal('application/octet-stream');
  protected readonly esImagen = computed(() => this.tipoContenido().startsWith('image/'));
  protected readonly esPdf = computed(() => this.tipoContenido() === 'application/pdf');
  protected readonly urlSegura = computed<SafeResourceUrl | null>(() => {
    const actual = this.url();
    return actual ? this.sanitizer.bypassSecurityTrustResourceUrl(actual) : null;
  });

  constructor() {
    effect((limpiar) => {
      this.reintento();
      const clave = this.clave()?.trim();
      this.cargando.set(!!clave);
      this.error.set(null);
      this.url.set(null);
      if (!clave) {
        return;
      }

      let objectUrl: string | null = null;
      const suscripcion = this.documentos.contenido$(clave).subscribe({
        next: (contenido) => {
          objectUrl = URL.createObjectURL(contenido);
          this.tipoContenido.set(
            contenido.type || this.documentos.tipoContenido(this.nombreArchivo() || clave),
          );
          this.url.set(objectUrl);
          this.cargando.set(false);
        },
        error: (error: unknown) => {
          this.error.set(
            error instanceof Error ? error.message : 'No se pudo abrir el documento.',
          );
          this.cargando.set(false);
        },
      });

      limpiar(() => {
        suscripcion.unsubscribe();
        if (objectUrl) {
          this.documentos.liberar(objectUrl);
        }
      });
    });
  }

  protected volverAIntentar(): void {
    this.reintento.update((valor) => valor + 1);
  }
}
