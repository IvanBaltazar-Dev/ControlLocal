import { Component, effect, inject, input, signal } from '@angular/core';

import { DocumentosService } from '../../core/api/documentos.service';

/**
 * Una imagen del almacén pedida **con el token de sesión**.
 *
 * Es el equivalente de `cl-visor-documento` para miniaturas: mismo servicio y
 * misma regla —nunca se pone `/documentos/contenido?clave=` en el `src`—, pero
 * pensado para rejillas de imágenes, donde el visor a pantalla completa no
 * encaja.
 *
 * Cada instancia es dueña de su object URL y lo revoca al cambiar de clave o
 * al destruirse; en una galería de seis fotos, olvidarlo es una fuga silenciosa.
 */
@Component({
  selector: 'cl-imagen-segura',
  templateUrl: './imagen-segura.html',
  styleUrl: './imagen-segura.scss',
})
export class ImagenSegura {
  private readonly documentos = inject(DocumentosService);

  readonly clave = input.required<string>();
  readonly alt = input('Imagen');

  protected readonly cargando = signal(true);
  protected readonly error = signal(false);
  protected readonly url = signal<string | null>(null);

  constructor() {
    effect((limpiar) => {
      const clave = this.clave()?.trim();
      this.cargando.set(!!clave);
      this.error.set(!clave);
      this.url.set(null);
      if (!clave) {
        return;
      }

      let objectUrl: string | null = null;
      const suscripcion = this.documentos.contenido$(clave).subscribe({
        next: (contenido) => {
          objectUrl = URL.createObjectURL(contenido);
          this.url.set(objectUrl);
          this.cargando.set(false);
        },
        error: () => {
          this.error.set(true);
          this.cargando.set(false);
        },
      });

      limpiar(() => {
        suscripcion.unsubscribe();
        this.documentos.liberar(objectUrl);
      });
    });
  }
}
