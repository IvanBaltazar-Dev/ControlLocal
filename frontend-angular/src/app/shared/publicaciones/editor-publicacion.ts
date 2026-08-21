import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';

import { ApiError } from '../../core/api/api.types';
import { CANAL_PUBLICACION, opcionesDe } from '../../core/api/codigos';
import {
  EncargosService,
  Publicacion,
  PublicacionRequest,
} from '../../core/api/encargos.service';

const CANAL_POR_DEFECTO = 'URBANIA';

/**
 * **Alta y edición de un anuncio DE UN ENCARGO.**
 *
 * Vivía dentro de `features/local-detail/` y publicaba «un local». Desde V70
 * publica un **encargo**: es lo único que puede decir si el importe del anuncio
 * es un precio de venta o una renta mensual, y con venta y alquiler simultáneos
 * es lo único que dice cuál de los dos se está publicando.
 *
 * Vive en `shared/` porque no es de ninguna pantalla: lo abre la ficha universal
 * desde dentro del bloque de su encargo, y podrá abrirlo cualquier otra.
 *
 * Guarda él mismo: el estado "guardando" y el error del API viven junto al
 * formulario que los produce, y quien lo abre solo se entera del resultado
 * (`guardado`).
 *
 * Dos detalles del contrato que condicionan el formulario:
 * - **el importe es obligatorio.** La columna es NOT NULL y el servicio lo
 *   escribe tal cual, así que un vacío no daría un 400 del contrato sino un
 *   error de la BD. No se rellena con `0`: publicar por 0 es un dato falso que
 *   además contaminaría los indicadores comerciales. El formulario lo exige y
 *   `guardar()` vuelve a comprobar que el valor es finito antes de armar el
 *   cuerpo, de modo que al servicio nunca llega `null`, `undefined` ni `NaN`.
 * - En la **edición**, el backend ignora el estado: pausar, publicar o cerrar
 *   son la operación aparte `POST …/estado`. Por eso aquí no hay ese campo.
 *
 * **Cómo se llama el importe no lo decide esta pantalla.** El rótulo llega en
 * `importeRotulo` desde el encargo; aquí sólo se pinta (D-A-1 §5).
 */
@Component({
  selector: 'cl-editor-publicacion',
  imports: [ReactiveFormsModule],
  templateUrl: './editor-publicacion.html',
  styleUrl: './editor-publicacion.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditorPublicacion {
  private readonly fb = inject(FormBuilder);
  private readonly encargos = inject(EncargosService);

  readonly abierto = input(false);
  /** El encargo que se anuncia. Es la identidad: nunca la operación. */
  readonly idEncargo = input.required<number>();
  /**
   * Cómo se llama el importe en ESTE encargo — «precio de venta» o «renta
   * mensual»—, tal como lo publica el backend.
   */
  readonly importeRotulo = input<string>("importe publicado");
  /** `null` = alta. */
  readonly publicacion = input<Publicacion | null>(null);

  readonly cerrar = output<void>();
  readonly guardado = output<void>();

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly canales = opcionesDe(CANAL_PUBLICACION);

  protected readonly formulario = this.fb.group({
    canal: this.fb.nonNullable.control(CANAL_POR_DEFECTO, Validators.required),
    tituloAnuncio: this.fb.nonNullable.control('', Validators.maxLength(200)),
    urlPublicacion: this.fb.nonNullable.control('', Validators.maxLength(500)),
    importePublicado: this.fb.control<number | null>(null, [
      Validators.required,
      Validators.min(0),
      montoFinito,
    ]),
    moneda: this.fb.nonNullable.control('', Validators.required),
  });

  constructor() {
    // Al abrirse vuelve a cargar la publicación que toca: reabrir en "nueva"
    // después de editar no debe conservar lo anterior.
    effect(() => {
      if (!this.abierto()) {
        return;
      }
      const actual = this.publicacion();
      this.error.set(null);
      this.formulario.reset({
        canal: actual?.canal || CANAL_POR_DEFECTO,
        tituloAnuncio: actual?.tituloAnuncio ?? '',
        urlPublicacion: actual?.urlPublicacion ?? '',
        importePublicado: actual?.importePublicado ?? null,
        moneda: actual?.moneda ?? '',
      });
    });
  }

  protected esEdicion(): boolean {
    return (this.publicacion()?.id ?? 0) > 0;
  }

  protected invalido(nombre: keyof typeof this.formulario.controls): boolean {
    const control = this.formulario.controls[nombre];
    return control.invalid && (control.touched || control.dirty);
  }

  protected alCerrar(): void {
    if (!this.guardando()) {
      this.cerrar.emit();
    }
  }

  protected async guardar(): Promise<void> {
    if (this.guardando()) {
      return;
    }
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.error.set('Revisa los campos marcados.');
      return;
    }

    // Segunda comprobación, deliberada: el formulario ya la exige, pero es lo
    // que garantiza —y hace evidente— que al servicio no llega un monto
    // ausente o no finito. La alternativa sería coaccionarlo, y coaccionar un
    // monto es inventar un dato.
    const importe = this.formulario.controls.importePublicado.value;
    if (importe === null || !Number.isFinite(importe)) {
      this.formulario.controls.importePublicado.markAsTouched();
      this.error.set(`Indica el ${this.importeRotulo()}.`);
      return;
    }

    this.guardando.set(true);
    this.error.set(null);
    try {
      const existente = this.publicacion();
      const datos = this.datosParaGuardar(existente, importe);
      if (existente && existente.id > 0) {
        await this.encargos.actualizarPublicacion(this.idEncargo(), existente.id, datos);
      } else {
        await this.encargos.crearPublicacion(this.idEncargo(), datos);
      }
      this.guardado.emit();
    } catch (error) {
      this.error.set(
        error instanceof ApiError ? error.message : 'No se pudo guardar la publicación.',
      );
    } finally {
      this.guardando.set(false);
    }
  }

  /** El importe llega ya validado como número finito: aquí no se coacciona nada. */
  private datosParaGuardar(
    existente: Publicacion | null,
    importe: number,
  ): PublicacionRequest {
    const valor = this.formulario.getRawValue();
    return {
      canal: valor.canal,
      urlPublicacion: textoOpcional(valor.urlPublicacion),
      importePublicado: importe,
      moneda: valor.moneda,
      tituloAnuncio: textoOpcional(valor.tituloAnuncio),
      // En blanco el backend lo genera; al editar se conserva.
      codigoOrigen: existente?.codigoOrigen ?? null,
      estado: existente?.estado ?? 'P',
    };
  }
}

function textoOpcional(valor: string): string | null {
  const limpio = valor.trim();
  return limpio || null;
}

/**
 * Rechaza `NaN` e infinitos. `Validators.required` no basta: un control
 * numérico puede quedarse con `NaN` si alguien le asigna un valor por código,
 * y `NaN` pasa `required` (no es null ni cadena vacía) y también `min` (toda
 * comparación con `NaN` es falsa, así que no viola el mínimo).
 */
export function montoFinito(control: AbstractControl<number | null>): ValidationErrors | null {
  const valor = control.value;
  if (valor === null || valor === undefined) {
    return null; // de la ausencia se ocupa `required`.
  }
  return Number.isFinite(valor) ? null : { montoNoFinito: true };
}
