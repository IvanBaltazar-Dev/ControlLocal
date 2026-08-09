import { Component, computed, inject, input, output, signal } from '@angular/core';

import {
  ArchivoPreparado,
  ArchivosService,
  ExtensionArchivo,
  EXTENSIONES_ARCHIVO,
  TAMANO_MAXIMO_ARCHIVO,
} from '../../core/archivos/archivos.service';

@Component({
  selector: 'cl-subida-archivo',
  templateUrl: './subida-archivo.html',
  styleUrl: './subida-archivo.scss',
  host: {
    '[class.deshabilitado]': 'deshabilitado()',
  },
})
export class SubidaArchivo {
  private readonly archivos = inject(ArchivosService);
  private seleccionActual = 0;

  readonly etiqueta = input('Seleccionar archivo');
  readonly extensiones = input<readonly ExtensionArchivo[]>(EXTENSIONES_ARCHIVO);
  readonly multiple = input(false);
  readonly maximoArchivos = input(1);
  readonly tamanoMaximo = input(TAMANO_MAXIMO_ARCHIVO);
  readonly deshabilitado = input(false);

  readonly seleccion = output<readonly ArchivoPreparado[]>();
  readonly fallo = output<string>();

  protected readonly procesando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aceptar = computed(() => this.extensiones().join(','));
  protected readonly ayuda = computed(
    () =>
      `${this.extensiones().join(', ')} · máximo ${Math.floor(this.tamanoMaximo() / 1024 / 1024)} MB`,
  );

  protected async alSeleccionar(evento: Event): Promise<void> {
    const selector = evento.target as HTMLInputElement;
    const elegidos = Array.from(selector.files ?? []);
    selector.value = '';
    if (elegidos.length === 0 || this.deshabilitado()) {
      return;
    }

    if (elegidos.length > this.maximoArchivos()) {
      this.informarError(`Solo se permiten ${this.maximoArchivos()} archivo(s) por selección.`);
      return;
    }

    const version = ++this.seleccionActual;
    this.procesando.set(true);
    this.error.set(null);
    try {
      const resultados = await Promise.all(
        elegidos.map((archivo) =>
          this.archivos.validar(archivo, {
            extensiones: this.extensiones(),
            tamanoMaximo: this.tamanoMaximo(),
          }),
        ),
      );
      if (version !== this.seleccionActual) {
        return;
      }
      const invalido = resultados.find((resultado) => !resultado.valido);
      if (invalido && !invalido.valido) {
        this.informarError(invalido.error);
        return;
      }
      this.seleccion.emit(
        resultados
          .filter((resultado) => resultado.valido)
          .map((resultado) => resultado.archivo),
      );
    } finally {
      if (version === this.seleccionActual) {
        this.procesando.set(false);
      }
    }
  }

  private informarError(mensaje: string): void {
    this.error.set(mensaje);
    this.fallo.emit(mensaje);
  }
}
