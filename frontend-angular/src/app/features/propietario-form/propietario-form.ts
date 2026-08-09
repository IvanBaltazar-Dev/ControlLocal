import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { toSignal } from '@angular/core/rxjs-interop';

import { ApiError } from '../../core/api/api.types';
import {
  DatosPropietario,
  Propietario,
  PropietariosService,
} from '../../core/api/propietarios.service';
import { AuthService } from '../../core/auth/auth.service';
import { texto as textoDe } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

/** Largo exacto que el backend exige. Carné y pasaporte pasan tal cual. */
const LARGO_DOCUMENTO: Readonly<Record<string, number>> = { D: 8, R: 11 };

/**
 * Alta y edición del propietario.
 *
 * Misma regla que en clientes y por el mismo motivo: en **edición**, tipo de
 * persona, tipo de documento y número van **bloqueados**. `PUT /propietarios/{id}`
 * no los toca y descarta el resto en silencio, así que dejarlos editables
 * prometería un cambio que el backend no hace.
 *
 * Dos diferencias con el formulario de cliente que conviene no "unificar":
 *
 * - **Solo hay un consentimiento**, el de uso del dato, que es de la PERSONA.
 *   El de contacto comercial es del rol CLIENTE y aquí no existe.
 * - **No hay rubro**: el rubro es del negocio del inquilino, no del dueño del
 *   inmueble.
 *
 * Escribir es de **AGENTE**. El broker y el admin llegan a esta ruta por el
 * menú —el listado no lleva gate— y se encuentran el formulario en solo
 * lectura, no un 403 después de rellenarlo.
 */
@Component({
  selector: 'app-propietario-form',
  imports: [EstadoListado, ReactiveFormsModule],
  templateUrl: './propietario-form.html',
  styleUrl: './propietario-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PropietarioForm implements OnInit {
  private readonly api = inject(PropietariosService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly cargando = signal(false);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly errorGuardado = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly idPropietario = signal<number | null>(null);
  protected readonly original = signal<Propietario | null>(null);

  protected readonly formulario = this.fb.nonNullable.group(
    {
      tipoPersona: this.fb.nonNullable.control<'N' | 'J'>('N', Validators.required),
      tipoDocumento: this.fb.nonNullable.control<'D' | 'R' | 'C' | 'P'>('D', Validators.required),
      numeroDocumento: this.fb.nonNullable.control('', Validators.required),
      nombre: this.fb.nonNullable.control('', Validators.required),
      telefono: this.fb.nonNullable.control('', [Validators.required, Validators.pattern(/^\d{9}$/)]),
      correo: this.fb.nonNullable.control('', [Validators.required, Validators.email]),
      // D-27: en el alta esta es LA casilla de autorizacion, la misma que en
      // el formulario de cliente, y lo UNICO que el usuario aporta. Cubre los
      // cinco ambitos del aviso.
      consentimientoUsoDato: this.fb.nonNullable.control(false),
    },
    { validators: [documentoValido] },
  );

  protected readonly esEdicion = computed(() => this.idPropietario() !== null);
  protected readonly puedeEditar = computed(() => this.auth.sesion()?.rol === 'AGENTE');

  /**
   * Se refleja con toSignal(valueChanges) y NO con un computed() sobre
   * FormControl.value: ese no es reactivo, y el fallo ya costo un boton
   * bloqueado con el motivo escrito en F3.
   */
  private readonly valores = toSignal(this.formulario.valueChanges, {
    initialValue: this.formulario.getRawValue(),
  });

  /** En el alta, sin la casilla marcada el backend rechaza y no persiste nada. */
  protected readonly faltaAutorizacion = computed(
    () => !this.esEdicion() && this.valores()?.consentimientoUsoDato !== true,
  );
  protected readonly juridica = signal(false);
  protected readonly titulo = computed(() =>
    this.esEdicion() ? `Editar ${textoDe(this.original()?.nombre)}` : 'Registrar propietario',
  );
  protected readonly etiquetaNombre = computed(() =>
    this.juridica() ? 'Razón social' : 'Nombre completo',
  );
  protected readonly etiquetaDocumento = computed(() =>
    this.juridica() ? 'RUC' : 'Número de documento',
  );

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (Number.isSafeInteger(id) && id > 0) {
      this.idPropietario.set(id);
      void this.cargar(id);
    } else {
      this.sincronizarTipo('N');
    }
  }

  protected cambiarTipoPersona(tipo: 'N' | 'J'): void {
    if (this.esEdicion()) return;
    this.formulario.controls.tipoPersona.setValue(tipo);
    this.sincronizarTipo(tipo);
  }

  protected invalido(campo: keyof typeof this.formulario.controls): boolean {
    const control = this.formulario.controls[campo];
    return control.invalid && (control.dirty || control.touched);
  }

  protected get documentoIncompleto(): boolean {
    return (
      this.formulario.hasError('documento') &&
      (this.formulario.controls.numeroDocumento.dirty ||
        this.formulario.controls.numeroDocumento.touched)
    );
  }

  protected mensajeDocumento(): string {
    const largo = LARGO_DOCUMENTO[this.formulario.controls.tipoDocumento.value];
    return largo
      ? `${this.etiquetaDocumento()} debe tener ${largo} dígitos.`
      : 'El número de documento es obligatorio.';
  }

  protected async guardar(): Promise<void> {
    if (this.guardando() || !this.puedeEditar()) return;
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.errorGuardado.set('Revisa los campos obligatorios antes de guardar.');
      return;
    }
    // D-27: se corta aqui, no se manda para que el backend lo rechace. Sin
    // autorizacion no se guarda ningun dato identificable.
    if (this.faltaAutorizacion()) {
      this.errorGuardado.set(
        'Sin la autorización de la persona no se puede registrar: no se guardará ningún dato.',
      );
      return;
    }

    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      const id = this.idPropietario();
      if (id) {
        await this.api.actualizar(id, this.datosParaGuardar());
      } else {
        await this.api.registrar(this.datosParaGuardar());
      }
      await this.router.navigate(['/propietarios'], { replaceUrl: true });
    } catch (error) {
      this.errorGuardado.set(
        error instanceof ApiError ? error.message : 'No se pudo guardar el propietario.',
      );
    } finally {
      this.guardando.set(false);
    }
  }

  protected cancelar(): void {
    void this.router.navigate(['/propietarios']);
  }

  protected reintentar(): void {
    const id = this.idPropietario();
    if (id) void this.cargar(id);
  }

  private async cargar(id: number): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    try {
      const propietario = await this.api.obtener(id);
      this.original.set(propietario);
      const tipo = propietario.tipoPersona === 'J' ? 'J' : 'N';
      this.formulario.patchValue({
        tipoPersona: tipo,
        tipoDocumento: (propietario.tipoDocumento as 'D' | 'R' | 'C' | 'P') ?? 'D',
        numeroDocumento: propietario.numeroDocumento ?? '',
        nombre: propietario.nombre ?? '',
        telefono: propietario.telefono ?? '',
        correo: propietario.correo ?? '',
        consentimientoUsoDato: propietario.consentimientoUsoDato === true,
      });
      this.juridica.set(tipo === 'J');
      // La identidad queda bloqueada: el PUT no la toca.
      this.formulario.controls.tipoPersona.disable();
      this.formulario.controls.tipoDocumento.disable();
      this.formulario.controls.numeroDocumento.disable();
    } catch (error) {
      this.errorCarga.set(
        error instanceof ApiError ? error.message : 'No se pudo cargar el propietario.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  /** Una persona jurídica se identifica con RUC; no hay otra opción. */
  private sincronizarTipo(tipo: 'N' | 'J'): void {
    this.juridica.set(tipo === 'J');
    const documento = this.formulario.controls.tipoDocumento;
    if (tipo === 'J') {
      documento.setValue('R');
      documento.disable();
    } else {
      if (documento.value === 'R') documento.setValue('D');
      documento.enable();
    }
  }

  private datosParaGuardar(): DatosPropietario {
    // getRawValue incluye los controles deshabilitados: en edición viajan los
    // mismos valores que ya tenía, así el PUT no los ve cambiar.
    const v = this.formulario.getRawValue();
    return {
      tipoPersona: v.tipoPersona,
      tipoDocumento: v.tipoDocumento,
      numeroDocumento: v.numeroDocumento.trim(),
      nombre: v.nombre.trim(),
      telefono: v.telefono.trim(),
      correo: v.correo.trim(),
      consentimientoUsoDato: v.consentimientoUsoDato,
      // El estado no se toca aquí: la baja y la reactivación son decisiones de
      // la bandeja, con confirmación.
      estado: this.original()?.estado,
    };
  }
}

/**
 * Espejo de `Personas.validar` del backend: RUC 11 dígitos y DNI 8, ambos
 * numéricos; carné y pasaporte pasan tal cual. Se valida aquí para no
 * descubrirlo con un 400 después de escribir el formulario entero.
 */
function documentoValido(control: AbstractControl): ValidationErrors | null {
  const tipo = control.get('tipoDocumento')?.value as string | undefined;
  const numero = ((control.get('numeroDocumento')?.value as string | undefined) ?? '').trim();
  if (!numero) return null;
  const largo = tipo ? LARGO_DOCUMENTO[tipo] : undefined;
  if (!largo) return null;
  return /^\d+$/.test(numero) && numero.length === largo ? null : { documento: true };
}
